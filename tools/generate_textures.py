"""Generates every Music Box texture as pixel art.

Pure standard library - writes PNGs directly with zlib, so there is no Pillow dependency.
Run from the repo root:  python tools/generate_textures.py
"""

import math
import struct
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PORTS = ["ports/1.19.2", "ports/1.19.2-fabric"]
ASSETS = "src/main/resources/assets/musicbox/textures"
# Vanilla builds the worn-armour path in the minecraft namespace, so the layer texture has
# to live there rather than under assets/musicbox.
VANILLA_ASSETS = "src/main/resources/assets/minecraft/textures"


class Canvas:
    def __init__(self, width, height):
        self.w = width
        self.h = height
        self.px = [[(0, 0, 0, 0)] * width for _ in range(height)]

    def set(self, x, y, colour):
        if 0 <= x < self.w and 0 <= y < self.h and colour is not None:
            self.px[y][x] = colour

    def rect(self, x0, y0, x1, y1, colour):
        """Filled rectangle, exclusive of x1/y1."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, colour)

    def outline(self, x0, y0, x1, y1, colour):
        for x in range(x0, x1):
            self.set(x, y0, colour)
            self.set(x, y1 - 1, colour)
        for y in range(y0, y1):
            self.set(x0, y, colour)
            self.set(x1 - 1, y, colour)

    def bevel(self, x0, y0, x1, y1, light, dark):
        """Minecraft-style raised panel edge."""
        for x in range(x0, x1):
            self.set(x, y0, light)
            self.set(x, y1 - 1, dark)
        for y in range(y0, y1):
            self.set(x0, y, light)
            self.set(x1 - 1, y, dark)
        self.set(x1 - 1, y0, light)
        self.set(x0, y1 - 1, dark)

    def inset(self, x0, y0, x1, y1, fill, dark, light):
        """Recessed well: dark on the top/left, light on the bottom/right."""
        self.rect(x0, y0, x1, y1, fill)
        for x in range(x0, x1):
            self.set(x, y0, dark)
            self.set(x, y1 - 1, light)
        for y in range(y0, y1):
            self.set(x0, y, dark)
            self.set(x1 - 1, y, light)

    def disc(self, cx, cy, radius, colour):
        r2 = radius * radius
        for y in range(self.h):
            for x in range(self.w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r2:
                    self.set(x, y, colour)

    def ring(self, cx, cy, outer, inner, colour):
        for y in range(self.h):
            for x in range(self.w):
                d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if inner * inner <= d2 <= outer * outer:
                    self.set(x, y, colour)

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for r, g, b, a in row:
                raw += bytes((r, g, b, a))

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


# --- palette -----------------------------------------------------------------

WOOD_DARK = (58, 38, 24, 255)
WOOD = (92, 62, 40, 255)
WOOD_MID = (110, 76, 50, 255)
WOOD_LIGHT = (134, 96, 64, 255)
WOOD_HI = (158, 118, 82, 255)

METAL_DARK = (32, 32, 38, 255)
METAL = (74, 74, 84, 255)
METAL_HI = (128, 128, 140, 255)

GRILLE = (26, 26, 30, 255)
GRILLE_HI = (52, 52, 58, 255)
CONE = (18, 18, 22, 255)

LED_OFF = (86, 30, 26, 255)
LED_ON = (255, 96, 64, 255)
GLOW = (255, 176, 96, 255)
SCREEN_OFF = (28, 40, 34, 255)
SCREEN_ON = (86, 220, 130, 255)

LEATHER = (62, 48, 42, 255)
LEATHER_HI = (92, 72, 62, 255)
PAD = (38, 38, 44, 255)
PAD_HI = (66, 66, 76, 255)
STEEL = (168, 172, 180, 255)
STEEL_HI = (214, 218, 226, 255)
STEEL_DARK = (104, 108, 118, 255)


def _hash(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 1442695040888963407) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n ^ (n >> 16)) & 0xFFFFFFFF


def wood_grain(canvas, base, light, dark, seed=0):
    """Fills the canvas with horizontal plank grain rather than uniform noise."""
    canvas.rect(0, 0, canvas.w, canvas.h, base)
    for y in range(canvas.h):
        # Each row picks a tone, so streaks run with the grain instead of speckling.
        row = _hash(0, y, seed) % 10
        if row < 3:
            canvas.rect(0, y, canvas.w, y + 1, dark)
        elif row < 6:
            canvas.rect(0, y, canvas.w, y + 1, light)
        for x in range(canvas.w):
            if _hash(x, y, seed + 91) % 7 == 0:
                canvas.set(x, y, dark if row >= 3 else base)


# --- block faces -------------------------------------------------------------

def front_face(playing):
    c = Canvas(16, 16)
    wood_grain(c, WOOD, WOOD_LIGHT, WOOD_DARK, seed=1)
    c.bevel(0, 0, 16, 16, WOOD_HI, WOOD_DARK)

    # Speaker grille on the left two thirds.
    c.inset(2, 3, 11, 14, GRILLE, WOOD_DARK, WOOD_HI)
    for y in range(4, 13):
        for x in range(3, 10):
            if (x + y) % 2 == 0:
                c.set(x, y, GRILLE_HI)
    c.ring(6.5, 8.5, 3.6, 2.9, METAL)
    c.disc(6.5, 8.5, 2.9, CONE)
    c.ring(6.5, 8.5, 2.9, 2.2, GRILLE_HI)
    c.disc(6.5, 8.5, 1.3, GLOW if playing else METAL_HI)

    # Control column on the right.
    c.inset(12, 3, 15, 7, SCREEN_ON if playing else SCREEN_OFF, WOOD_DARK, WOOD_HI)
    if playing:
        c.set(13, 4, (24, 60, 36, 255))
        c.set(13, 5, (24, 60, 36, 255))

    c.set(13, 9, LED_ON if playing else LED_OFF)
    if playing:
        c.set(12, 9, GLOW)
        c.set(14, 9, GLOW)

    c.rect(12, 11, 15, 13, METAL)
    c.set(12, 11, METAL_HI)
    c.set(14, 12, METAL_DARK)
    return c


def side_face():
    c = Canvas(16, 16)
    wood_grain(c, WOOD, WOOD_LIGHT, WOOD_DARK, seed=2)
    c.bevel(0, 0, 16, 16, WOOD_HI, WOOD_DARK)
    # Metal corner straps.
    c.rect(2, 2, 4, 14, METAL)
    c.rect(12, 2, 14, 14, METAL)
    for y in range(2, 14, 3):
        c.set(2, y, METAL_HI)
        c.set(13, y, METAL_DARK)
    c.set(3, 7, METAL_HI)
    c.set(12, 8, METAL_HI)
    return c


def top_face():
    c = Canvas(16, 16)
    wood_grain(c, WOOD_MID, WOOD_HI, WOOD, seed=3)
    c.bevel(0, 0, 16, 16, WOOD_HI, WOOD_DARK)
    # Lid seam plus corner screws.
    c.rect(2, 7, 14, 8, WOOD_DARK)
    c.rect(2, 8, 14, 9, WOOD_HI)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, METAL_HI)
    return c


def bottom_face():
    c = Canvas(16, 16)
    wood_grain(c, WOOD_DARK, WOOD, (44, 28, 18, 255), seed=4)
    c.outline(0, 0, 16, 16, (40, 26, 16, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, METAL_DARK)
    return c


# --- items -------------------------------------------------------------------

def headphones_item():
    c = Canvas(16, 16)

    # Headband: an arc of steel across the top.
    for x in range(3, 13):
        t = (x - 7.5) / 4.5
        y = 3 + int(round(2.2 * t * t))
        c.set(x, y, STEEL_HI)
        c.set(x, y + 1, STEEL)
        c.set(x, y + 2, STEEL_DARK)

    # Sliders down to each cup.
    for y in range(6, 9):
        c.set(3, y, STEEL)
        c.set(12, y, STEEL)
        c.set(4, y, STEEL_DARK)
        c.set(11, y, STEEL_DARK)

    # Ear cups.
    for cx in (3, 11):
        c.rect(cx - 1, 8, cx + 3, 13, PAD)
        c.outline(cx - 1, 8, cx + 3, 13, LEATHER)
        c.set(cx, 9, PAD_HI)
        c.set(cx + 1, 9, PAD_HI)
        c.set(cx, 12, LEATHER_HI)

    # Cable.
    for y, x in ((13, 12), (14, 12), (15, 11)):
        c.set(x, y, (24, 24, 28, 255))
    return c


def armor_layer():
    """Head-slot armour texture: band on the top face, cups on the left/right faces."""
    c = Canvas(64, 32)

    # Head top face occupies (8,0)-(16,8); lay the band across it.
    for x in range(8, 16):
        c.set(x, 3, STEEL_DARK)
        c.set(x, 4, STEEL_HI)
        c.set(x, 5, STEEL)

    # Right face (0,8)-(8,16) and left face (16,8)-(24,16).
    for ox in (0, 16):
        c.rect(ox + 2, 10, ox + 6, 14, PAD)
        c.outline(ox + 2, 10, ox + 6, 14, LEATHER)
        c.set(ox + 3, 11, PAD_HI)
        c.set(ox + 4, 11, PAD_HI)
        # Band running down to the cup.
        c.set(ox + 3, 9, STEEL)
        c.set(ox + 4, 9, STEEL_DARK)

    # Back face (24,8)-(32,16): a sliver of band so it reads from behind.
    for x in range(26, 30):
        c.set(x, 9, STEEL)
    return c


# --- gui ---------------------------------------------------------------------

PANEL = (198, 198, 198, 255)
PANEL_HI = (255, 255, 255, 255)
PANEL_LO = (85, 85, 85, 255)
WELL = (43, 43, 43, 255)
WELL_DARK = (55, 55, 55, 255)
TRACK = (30, 30, 30, 255)


def gui():
    c = Canvas(256, 256)
    width, height = 176, 200

    c.rect(0, 0, width, height, PANEL)
    c.bevel(0, 0, width, height, PANEL_HI, PANEL_LO)
    # Inner shading so the panel does not read as flat.
    c.bevel(1, 1, width - 1, height - 1, (222, 222, 222, 255), (150, 150, 150, 255))

    # Station list well and its scrollbar track.
    c.inset(6, 17, 162, 145, WELL, WELL_DARK, PANEL_HI)
    c.inset(162, 17, 170, 145, TRACK, WELL_DARK, PANEL_HI)

    # Status strip under the list.
    c.inset(6, 146, 170, 164, WELL, WELL_DARK, PANEL_HI)

    return c


def main():
    outputs = {
        "block/music_box_front.png": front_face(False),
        "block/music_box_front_on.png": front_face(True),
        "block/music_box_side.png": side_face(),
        "block/music_box_top.png": top_face(),
        "block/music_box_bottom.png": bottom_face(),
        "item/headphones.png": headphones_item(),
        "gui/music_box.png": gui(),
    }
    vanilla_outputs = {
        "models/armor/musicbox_headphones_layer_1.png": armor_layer(),
    }

    written = 0
    for port in PORTS:
        if not (REPO / port).exists():
            continue
        for name, canvas in outputs.items():
            canvas.save(REPO / port / ASSETS / name)
            written += 1
        for name, canvas in vanilla_outputs.items():
            canvas.save(REPO / port / VANILLA_ASSETS / name)
            written += 1
    print(f"wrote {written} textures")


if __name__ == "__main__":
    main()
