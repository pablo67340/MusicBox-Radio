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
ASSETS = "src/main/resources/assets/musicboxradio/textures"
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


# --- animated parts ----------------------------------------------------------

# Drawn by the block renderers rather than baked into a face, so these are tinted at
# runtime. Anything meant to pick up the neon colour is left white or near-white here,
# because tinting multiplies: black grooves stay black, white labels take the full hue.

VINYL = (16, 16, 20, 255)
VINYL_GROOVE = (42, 42, 50, 255)
VINYL_SHEEN = (96, 96, 112, 255)


def _streaks(canvas, cx, cy, inner, outer, angles, half_width, colour):
    """Narrow radial highlights. Without these a spinning disc looks stationary."""
    for y in range(canvas.h):
        for x in range(canvas.w):
            dx = x + 0.5 - cx
            dy = y + 0.5 - cy
            distance = math.hypot(dx, dy)
            if not inner <= distance <= outer:
                continue
            theta = math.atan2(dy, dx)
            for base in angles:
                offset = abs(((theta - base + math.pi) % (2 * math.pi)) - math.pi)
                if offset < half_width:
                    canvas.set(x, y, colour)
                    break


def vinyl_disc():
    """Spinning record for the music box front. Transparent outside the disc.

    Drawn at 32x32 rather than the usual 16: this is a renderer overlay, not a block
    face, and concentric grooves alias into squares at block resolution.
    """
    c = Canvas(32, 32)
    cx = cy = 16.0

    c.disc(cx, cy, 15.4, VINYL)
    c.ring(cx, cy, 15.4, 14.5, (34, 34, 41, 255))

    radius = 13.7
    while radius > 7.2:
        c.ring(cx, cy, radius, radius - 0.5, VINYL_GROOVE)
        radius -= 1.15

    _streaks(c, cx, cy, 6.6, 15.2, (0.55, 0.55 + math.pi), 0.19, VINYL_SHEEN)

    # Label takes the neon tint, spindle hole punches back through to dark.
    c.disc(cx, cy, 6.3, (255, 255, 255, 255))
    c.ring(cx, cy, 6.3, 5.5, (202, 202, 212, 255))
    c.ring(cx, cy, 4.2, 3.8, (226, 226, 234, 255))
    c.disc(cx, cy, 1.3, VINYL)
    return c


def white_pixel():
    """Flat source for the tinted quads the equalizer is built from."""
    c = Canvas(2, 2)
    c.rect(0, 0, 2, 2, (255, 255, 255, 255))
    return c


# --- speaker -----------------------------------------------------------------

# Black, but not one flat black. Without a few steps of separation the cabinet reads as
# a hole in the world rather than an object.
CAB = (28, 28, 33, 255)
CAB_HI = (62, 62, 71, 255)
CAB_LO = (11, 11, 14, 255)
CAB_PANEL = (36, 36, 42, 255)
GRILLE_WEAVE = (20, 20, 24, 255)
WELL = (14, 14, 17, 255)
WELL_DEEP = (7, 7, 9, 255)
FABRIC = (40, 40, 47, 255)
FABRIC_HI = (60, 60, 70, 255)
FABRIC_LO = (22, 22, 27, 255)
DUSTCAP = (78, 78, 90, 255)


def _cabinet(seed):
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, CAB)
    # Faint vertical brushing so the black is not a dead flat fill.
    for x in range(16):
        if _hash(x, 0, seed) % 5 == 0:
            for y in range(16):
                c.set(x, y, CAB_PANEL)
        if _hash(x, 1, seed) % 7 == 0:
            for y in range(16):
                c.set(x, y, GRILLE_WEAVE)
    c.bevel(0, 0, 16, 16, CAB_HI, CAB_LO)
    return c


def speaker_front():
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, CAB)
    # Woven grille cloth over the whole baffle.
    for y in range(16):
        for x in range(16):
            if (x + y) % 2 == 0:
                c.set(x, y, GRILLE_WEAVE)
    c.bevel(0, 0, 16, 16, CAB_HI, CAB_LO)

    # Recessed well the cone sits in; the renderer floats the cone above this.
    c.disc(8.0, 9.5, 5.7, WELL)
    c.ring(8.0, 9.5, 5.7, 5.1, CAB_HI)
    c.disc(8.0, 9.5, 4.9, WELL_DEEP)

    # Tweeter and bass port along the top strip.
    c.disc(3.5, 3.0, 1.7, WELL)
    c.ring(3.5, 3.0, 1.7, 1.2, CAB_HI)
    c.rect(11, 2, 14, 4, WELL_DEEP)
    c.outline(11, 2, 14, 4, CAB_HI)
    return c


def speaker_cone():
    """Fabric driver drawn by the renderer, pushed out by the bass.

    32x32 for the same reason as the vinyl: it is an overlay, and the surround roll
    needs more than a pixel to read as a curve.
    """
    c = Canvas(32, 32)
    cx = cy = 16.0

    # Mounting ring, surround roll, cone, then the dust cap.
    c.disc(cx, cy, 15.4, FABRIC_LO)
    c.ring(cx, cy, 15.4, 14.0, CAB_HI)
    c.ring(cx, cy, 14.0, 11.6, FABRIC_HI)
    c.ring(cx, cy, 12.8, 11.6, FABRIC_LO)
    c.disc(cx, cy, 11.6, FABRIC)

    radius = 10.6
    while radius > 5.0:
        c.ring(cx, cy, radius, radius - 0.45, FABRIC_LO)
        radius -= 1.9

    _streaks(c, cx, cy, 5.0, 11.4, (2.4,), 0.30, FABRIC_HI)

    c.disc(cx, cy, 4.6, DUSTCAP)
    c.ring(cx, cy, 4.6, 4.0, FABRIC_LO)
    _streaks(c, cx, cy, 0.0, 4.0, (2.4,), 0.42, (104, 104, 118, 255))
    return c


def speaker_side():
    return _cabinet(12)


def speaker_top():
    c = _cabinet(13)
    c.rect(2, 2, 14, 3, CAB_PANEL)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, CAB_HI)
    return c


def speaker_bottom():
    c = _cabinet(14)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, CAB_LO)
    return c


def speaker_item():
    c = Canvas(16, 16)
    c.rect(3, 1, 13, 15, CAB)
    c.bevel(3, 1, 13, 15, CAB_HI, CAB_LO)
    # Woofer and tweeter, matching the block front.
    c.disc(8.0, 9.5, 3.6, WELL)
    c.ring(8.0, 9.5, 3.6, 3.0, CAB_HI)
    c.disc(8.0, 9.5, 2.6, FABRIC)
    c.disc(8.0, 9.5, 1.1, DUSTCAP)
    c.disc(8.0, 4.0, 1.5, WELL)
    c.ring(8.0, 4.0, 1.5, 1.0, CAB_HI)
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
        "block/vinyl.png": vinyl_disc(),
        "block/white.png": white_pixel(),
        "block/speaker_front.png": speaker_front(),
        "block/speaker_side.png": speaker_side(),
        "block/speaker_top.png": speaker_top(),
        "block/speaker_bottom.png": speaker_bottom(),
        "block/speaker_cone.png": speaker_cone(),
        "item/headphones.png": headphones_item(),
        "item/speaker.png": speaker_item(),
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
