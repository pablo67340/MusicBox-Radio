#!/usr/bin/env python3
"""Upload MusicBox Radio jars to CurseForge with the right Minecraft version,
modloader, Java version, and Client+Server environment tags.

Prerequisites:
  1. pip install -r requirements.txt          (just `requests`)
  2. An API token from https://authors.curseforge.com/account/api-tokens
     -> put it in the CURSEFORGE_TOKEN environment variable (or --token)
  3. Jars built into build/libs/<mod_version>/ by `gradlew build collectJar`.

Every jar in that folder is uploaded. Which Minecraft versions a jar is tagged
with comes from stonecutter.properties.toml: the version's mod.mc_releases,
narrowed to the ones that loader's mod.mc_compat range accepts.

The project ID defaults to MusicBox Radio's own (1661840); pass --project-id
only if you are uploading somewhere else.

Usage, from the project root:
  python scripts/upload_curseforge.py --dry-run
  python scripts/upload_curseforge.py
  python scripts/upload_curseforge.py --loaders fabric --only 1.21.1
"""

import argparse
import json
import os
import re
import sys
import tomllib
from pathlib import Path

import requests

PROJECT_ROOT = Path(__file__).resolve().parent.parent
UPLOAD_API = "https://minecraft.curseforge.com/api"

MOD_ID = "musicboxradio"
MOD_NAME = "MusicBox Radio"

# Baked in rather than read from CURSEFORGE_PROJECT_ID, because that variable is
# shared with the other mod projects and the wrong value uploads to the wrong page.
DEFAULT_PROJECT_ID = "1661840"

LOADER_TAGS = {"forge": "Forge", "neoforge": "NeoForge", "fabric": "Fabric"}

ENV_TAGS = [("environment", "Client"), ("environment", "Server")]

JAR_PATTERN = re.compile(rf"^{MOD_ID}-(?P<mc>[\d.]+)-(?P<loader>fabric|forge|neoforge)-(?P<version>.+)\.jar$")


def version_tuple(version: str) -> tuple:
    return tuple(int(part) for part in version.split("."))


def in_maven_range(version: str, spec: str) -> bool:
    """Checks a Forge style range such as [1.20,1.20.2)."""
    match = re.fullmatch(r"([\[(])([^,]*),([^\])]*)([\])])", spec.strip())
    if not match:
        sys.exit(f"Cannot read version range {spec!r}")
    low_inclusive, low, high, high_inclusive = match.groups()
    v = version_tuple(version)
    if low:
        if v < version_tuple(low) or (v == version_tuple(low) and low_inclusive == "("):
            return False
    if high:
        if v > version_tuple(high) or (v == version_tuple(high) and high_inclusive == ")"):
            return False
    return True


def minecraft_versions(config: dict, mc: str, loader: str) -> list:
    releases = config[mc]["mod"]["mc_releases"]
    spec = config[loader][mc]["mod"]["mc_compat"]
    if loader == "fabric":
        # Fabric ranges are written to cover exactly the listed releases.
        return releases
    return [r for r in releases if in_maven_range(r, spec)]


def java_tag(mc: str) -> str:
    v = version_tuple(mc)
    if v >= (26, 1):
        return "Java 25"
    if v >= (1, 20, 5):
        return "Java 21"
    if v >= (1, 18):
        return "Java 17"
    if v >= (1, 17):
        return "Java 16"
    return "Java 8"


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
    parser.add_argument("--changelog-file", default=str(PROJECT_ROOT / "CHANGELOG.md"))
    parser.add_argument("--release-type", default="release",
                        choices=["release", "beta", "alpha"])
    parser.add_argument("--only", default=None,
                        help="comma-separated Minecraft versions or version-loader keys "
                             "(1.21.1 or 1.21.1-neoforge). Default: all")
    parser.add_argument("--loaders", default=None,
                        help="comma-separated loaders to include (forge,fabric,neoforge)")
    parser.add_argument("--dry-run", action="store_true",
                        help="resolve everything and show what would be uploaded")
    args = parser.parse_args()

    if not args.token:
        sys.exit("No API token: set CURSEFORGE_TOKEN or pass --token")

    with open(PROJECT_ROOT / "stonecutter.properties.toml", "rb") as fh:
        config = tomllib.load(fh)
    mod_version = config["mod"]["version"]
    changelog = Path(args.changelog_file).read_text(encoding="utf-8")
    only = {s.strip().lower() for s in args.only.split(",")} if args.only else None
    loaders = {s.strip().lower() for s in args.loaders.split(",")} if args.loaders else None

    jar_dir = PROJECT_ROOT / "build" / "libs" / mod_version
    jars = []
    for jar in sorted(jar_dir.glob("*.jar"), key=lambda p: (version_tuple(JAR_PATTERN.match(p.name)["mc"]), p.name)
                      if JAR_PATTERN.match(p.name) else ((0,), p.name)):
        match = JAR_PATTERN.match(jar.name)
        if not match or match["version"] != mod_version:
            continue
        mc, loader = match["mc"], match["loader"]
        if loaders is not None and loader not in loaders:
            continue
        if only is not None and mc not in only and f"{mc}-{loader}" not in only:
            continue
        jars.append((jar, mc, loader))

    if not jars:
        sys.exit(f"No {mod_version} jars matched in {jar_dir}. Run gradlew build collectJar first.")

    print("Resolving CurseForge game version IDs...")
    version_ids = fetch_version_ids(args.token)

    uploaded = []
    for jar, mc, loader in jars:
        mc_tags = minecraft_versions(config, mc, loader)
        loader_tags = [LOADER_TAGS[loader]]
        if loader == "forge" and mc == "1.20.1":
            # NeoForge for 1.20.1 is still Forge underneath and loads Forge mods.
            loader_tags.append("NeoForge")
        tags = ([("minecraft", v) for v in mc_tags]
                + [("java", java_tag(mc))]
                + [("modloader", t) for t in loader_tags]
                + ENV_TAGS)
        game_versions = resolve_tags(tags, version_ids)
        metadata = {
            "changelog": changelog,
            "changelogType": "markdown",
            "displayName": f"{MOD_NAME} {mod_version} (MC {mc} {LOADER_TAGS[loader]})",
            "gameVersions": game_versions,
            "releaseType": args.release_type,
        }
        print(f"\n{jar.name}")
        print(f"  tags: {', '.join(n for _, n in tags)}")
        if args.dry_run:
            continue

        with open(jar, "rb") as fh:
            resp = requests.post(
                f"{UPLOAD_API}/projects/{args.project_id}/upload-file",
                headers={"X-Api-Token": args.token},
                data={"metadata": json.dumps(metadata)},
                files={"file": (jar.name, fh, "application/java-archive")},
                timeout=300,
            )
        if resp.status_code != 200:
            sys.exit(f"  UPLOAD FAILED ({resp.status_code}): {resp.text}")
        file_id = resp.json()["id"]
        uploaded.append((jar.name, file_id))
        print(f"  uploaded -> file id {file_id}")

    if args.dry_run:
        print("\nDry run complete - nothing uploaded.")
    else:
        print(f"\nDone: {len(uploaded)} files uploaded. "
              f"They'll appear once CurseForge approval finishes.")


if __name__ == "__main__":
    main()
