#!/usr/bin/env python3
"""Upload MusicBox Radio jars to CurseForge with the right Minecraft version,
modloader, Java version, and Client+Server environment tags.

Prerequisites:
  1. pip install -r requirements.txt          (just `requests`)
  2. An API token from https://authors.curseforge.com/account/api-tokens
     -> put it in the CURSEFORGE_TOKEN environment variable (or --token)
  3. Jars collected in releases/<mod_version>/. Pass --collect to copy them
     out of each port's build/libs for you.

The project ID defaults to MusicBox Radio's own (1661840); pass --project-id
only if you are uploading somewhere else.

Usage, from the project root:
  python scripts/upload_curseforge.py --collect --dry-run
  python scripts/upload_curseforge.py --collect
  python scripts/upload_curseforge.py --loaders fabric
"""

import argparse
import json
import os
import re
import shutil
import sys
from pathlib import Path

import requests

PROJECT_ROOT = Path(__file__).resolve().parent.parent
UPLOAD_API = "https://minecraft.curseforge.com/api"

MOD_ID = "musicboxradio"
MOD_NAME = "MusicBox Radio"

# Baked in rather than read from CURSEFORGE_PROJECT_ID, because that variable is
# shared with the other mod projects and the wrong value uploads to the wrong page.
DEFAULT_PROJECT_ID = "1661840"

# Minecraft version + loader + Java the jar targets. Client + server for all.
# `loader` must match the CurseForge Modloader tag name exactly. `dir` is the
# port directory the jar is built in.
PORTS = [
    {"mc": "1.19.2", "loader": "Forge",  "java": "Java 17", "dir": "1.19.2"},
    {"mc": "1.19.2", "loader": "Fabric", "java": "Java 17", "dir": "1.19.2-fabric"},
]

ENV_TAGS = [("environment", "Client"), ("environment", "Server")]


def port_key(port: dict) -> str:
    return f"{port['mc']}-{port['loader'].lower()}"


def jar_filename(port: dict, mod_version: str) -> str:
    if port["loader"] == "Forge":
        return f"{MOD_ID}-{port['mc']}-{mod_version}.jar"
    return f"{MOD_ID}-{port['mc']}-{port['loader'].lower()}-{mod_version}.jar"


def read_mod_version() -> str:
    """Each port carries its own gradle.properties; they are kept in step."""
    properties = PROJECT_ROOT / "ports" / PORTS[0]["dir"] / "gradle.properties"
    for line in properties.read_text().splitlines():
        if line.strip().startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"mod_version not found in {properties}")


def collect_jars(ports: list, mod_version: str, release_dir: Path) -> None:
    """Copies freshly built jars out of each port's build/libs."""
    release_dir.mkdir(parents=True, exist_ok=True)
    for port in ports:
        name = jar_filename(port, mod_version)
        built = PROJECT_ROOT / "ports" / port["dir"] / "build" / "libs" / name
        if not built.is_file():
            sys.exit(f"Not built yet: {built}\nRun gradlew build in ports/{port['dir']} first.")
        shutil.copy2(built, release_dir / name)
        print(f"  collected {name}")


def fetch_version_ids(token: str) -> dict:
    """Map (category, display name) to numeric game-version IDs.

    The same display name can exist under several version types (e.g.
    "1.19.2" as a Minecraft version and under an addon category), and the
    upload API rejects IDs from the wrong type, so the type must be part of
    the lookup key.
    """
    headers = {"X-Api-Token": token}
    types_resp = requests.get(f"{UPLOAD_API}/game/version-types",
                              headers=headers, timeout=30)
    types_resp.raise_for_status()
    type_category = {}
    for vt in types_resp.json():
        name = vt["name"]
        # Minecraft version types are usually "Minecraft 1.19", but some are
        # bare version numbers like "26.2"
        if name.startswith("Minecraft") or re.fullmatch(r"\d+(\.\d+)*", name):
            type_category[vt["id"]] = "minecraft"
        elif name == "Java":
            type_category[vt["id"]] = "java"
        elif name in ("Modloader", "ModLoader"):
            type_category[vt["id"]] = "modloader"
        elif name == "Environment":
            type_category[vt["id"]] = "environment"

    resp = requests.get(f"{UPLOAD_API}/game/versions", headers=headers, timeout=30)
    resp.raise_for_status()
    ids = {}
    for entry in resp.json():
        category = type_category.get(entry["gameVersionTypeID"])
        if category is not None:
            ids[(category, entry["name"])] = entry["id"]
    return ids


def resolve_tags(tags: list, version_ids: dict) -> list:
    """tags is a list of (category, name) pairs."""
    resolved = []
    for category, name in tags:
        key = (category, name)
        if key not in version_ids:
            close = sorted(n for c, n in version_ids
                           if c == category and name.split()[0] in n)[:10]
            sys.exit(f"CurseForge has no {category} version named {name!r}.\n"
                     f"Closest available names in that category: {close}")
        resolved.append(version_ids[key])
    return resolved


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--project-id", default=DEFAULT_PROJECT_ID,
                        help=f"numeric CurseForge project id (default {DEFAULT_PROJECT_ID})")
    parser.add_argument("--token", default=os.environ.get("CURSEFORGE_TOKEN"),
                        help="CurseForge author API token")
    parser.add_argument("--mod-version", default=None,
                        help="defaults to mod_version from gradle.properties")
    parser.add_argument("--changelog-file", default=str(PROJECT_ROOT / "CHANGELOG.md"))
    parser.add_argument("--release-type", default="release",
                        choices=["release", "beta", "alpha"])
    parser.add_argument("--only", default=None,
                        help="comma-separated keys (1.19.2 or 1.19.2-fabric). Default: all")
    parser.add_argument("--loaders", default=None,
                        help="comma-separated loaders to include (forge,fabric)")
    parser.add_argument("--collect", action="store_true",
                        help="copy jars out of each port's build/libs first")
    parser.add_argument("--dry-run", action="store_true",
                        help="resolve everything and show what would be uploaded")
    args = parser.parse_args()

    if not args.token:
        sys.exit("No API token: set CURSEFORGE_TOKEN or pass --token")
    if not args.project_id and not args.dry_run:
        sys.exit("No project id: set CURSEFORGE_PROJECT_ID or pass --project-id")

    mod_version = args.mod_version or read_mod_version()
    changelog = Path(args.changelog_file).read_text(encoding="utf-8")
    only = {s.strip().lower() for s in args.only.split(",")} if args.only else None
    loaders = ({s.strip().lower() for s in args.loaders.split(",")}
               if args.loaders else None)

    release_dir = PROJECT_ROOT / "releases" / mod_version
    ports = []
    for port in PORTS:
        if loaders is not None and port["loader"].lower() not in loaders:
            continue
        key = port_key(port)
        if only is not None and port["mc"] not in only and key not in only:
            continue
        ports.append(port)

    if not ports:
        sys.exit("No ports matched --only / --loaders")

    if args.collect:
        print(f"Collecting jars into releases/{mod_version}/")
        collect_jars(ports, mod_version, release_dir)

    # Verify all jars exist before touching the network
    for port in ports:
        port["jar"] = release_dir / jar_filename(port, mod_version)
        if not port["jar"].is_file():
            sys.exit(f"Missing jar: {port['jar']}\n"
                     f"Build and collect the release jars first (--collect).")

    print("Resolving CurseForge game version IDs...")
    version_ids = fetch_version_ids(args.token)

    uploaded = []
    for port in ports:
        tags = [("minecraft", port["mc"]),
                ("java", port["java"]),
                ("modloader", port["loader"])] + ENV_TAGS
        game_versions = resolve_tags(tags, version_ids)
        metadata = {
            "changelog": changelog,
            "changelogType": "markdown",
            "displayName": f"{MOD_NAME} {mod_version} (MC {port['mc']} {port['loader']})",
            "gameVersions": game_versions,
            "releaseType": args.release_type,
        }
        print(f"\n{port['jar'].name}")
        print(f"  tags: {', '.join(n for _, n in tags)}  ->  ids {game_versions}")
        if args.dry_run:
            continue

        with open(port["jar"], "rb") as fh:
            resp = requests.post(
                f"{UPLOAD_API}/projects/{args.project_id}/upload-file",
                headers={"X-Api-Token": args.token},
                data={"metadata": json.dumps(metadata)},
                files={"file": (port["jar"].name, fh, "application/java-archive")},
                timeout=300,
            )
        if resp.status_code != 200:
            sys.exit(f"  UPLOAD FAILED ({resp.status_code}): {resp.text}")
        file_id = resp.json()["id"]
        uploaded.append((port_key(port), file_id))
        print(f"  uploaded -> file id {file_id}")

    if args.dry_run:
        print("\nDry run complete - nothing uploaded.")
    else:
        print(f"\nDone: {len(uploaded)} files uploaded. "
              f"They'll appear once CurseForge approval finishes.")


if __name__ == "__main__":
    main()
