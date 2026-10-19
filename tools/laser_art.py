#!/usr/bin/env python3
"""
Lasers' block art: the emitters (flash, I, II, III), the mirror, the Lens Mount and Laser Port, and the
lenses, with their models and blockstates.

Follows the style guide's grammar: tier is read from the stone (cobble for low, deepslate for mid,
navy obsidian for high), flux is azure in straight 1px lines, and the lens is the one bright thing on
the block. Each emitter has a front (its lens, unlit and lit), a back (a power port) and sides. The
mirror is a framed silver plate on its face.

    ./tools/laser_art.py
"""

from __future__ import annotations

import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium")
TEX = os.path.join(ASSETS, "textures", "block")
MODELS = os.path.join(ASSETS, "models", "block")
STATES = os.path.join(ASSETS, "blockstates")
ITEMS = os.path.join(ASSETS, "items")


def hx(c: str, a: int = 255) -> tuple:
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


# Casing stones by tier, three tones each (dark, mid, light), and the lens colour.
TIERS = {
    "flash_emitter": dict(stone=("4f4f52", "6b6b70", "85858a"), lens="f0b429", trim=None, seed=11),
    "laser_emitter_1": dict(stone=("2b2b33", "3b3b45", "4d4d59"), lens="3485ff", trim="3485ff", seed=12),
    "laser_emitter_2": dict(stone=("12142a", "1b1e38", "262a4a"), lens="7fb2ff", trim="3485ff", seed=13),
    "laser_emitter_3": dict(stone=("0c0d1c", "141631", "1f2244"), lens="c8dcff", trim="9a7bff", seed=14),
}


def stone(tones, seed: int) -> Image.Image:
    dark, mid, light = (hx(t) for t in tones)
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), mid)
    for _ in range(70):
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), rnd.choice([dark, dark, light]))
    # A one-pixel bevel: light top and left, dark bottom and right.
    for i in range(16):
        img.putpixel((i, 0), light)
        img.putpixel((0, i), light)
        img.putpixel((i, 15), dark)
        img.putpixel((15, i), dark)
    return img


def steel(seed: int) -> Image.Image:
    """Dark brushed steel, for the Mirror's stand: streaks along its length."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), hx("3a3d44"))
    for y in range(16):
        tone = rnd.choice(["34373e", "3a3d44", "3a3d44", "41444c"])
        for x in range(16):
            img.putpixel((x, y), hx(tone) if rnd.random() < 0.85 else hx(rnd.choice(["2c2f35", "4a4e57"])))
    return img


def mirror_plate() -> Image.Image:
    """The Mirror's silver plate, in the middle 10x10: a darker silver rim, a soft diagonal sheen across it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        for y in range(3, 13):
            if x in (3, 12) or y in (3, 12):
                img.putpixel((x, y), hx("8d96a6"))
                continue
            shade = (x + (15 - y)) / 30
            base = tuple(int(a + (b - a) * shade) for a, b in zip((0xb8, 0xc2, 0xd0), (0xec, 0xf1, 0xf8)))
            img.putpixel((x, y), base + (255,))
    for i in range(4, 12):
        y = 15 - i
        if 4 <= y <= 11:
            img.putpixel((i, y), hx("fbfdff"))
    return img


def splitter_plate() -> Image.Image:
    """
    The Laser Splitter's plate (and the Cavity Mirror's): the Mirror's, with a 4x4 mark of the focus's light blue in
    the middle and a 2x2 window through it at its heart, where the light gets through.
    """
    img = mirror_plate()
    for x in range(6, 10):
        for y in range(6, 10):
            img.putpixel((x, y), (0, 0, 0, 0) if 7 <= x <= 8 and 7 <= y <= 8 else hx("a8d0ff"))
    return img


def entangled_plate() -> Image.Image:
    """The Entangled Mirror's plate: the Mirror's silver gone violet, with a 4x4 window in the middle onto its Tesseract."""
    img = mirror_plate()
    for x in range(3, 13):
        for y in range(3, 13):
            r, g, b, a = img.getpixel((x, y))
            img.putpixel((x, y), (int(r * 0.78 + 0xb4 * 0.22), int(g * 0.70 + 0x9c * 0.10), int(b * 0.9 + 0xff * 0.1), a))
    for x in range(5, 11):
        for y in range(5, 11):
            inside = 6 <= x <= 9 and 6 <= y <= 9
            img.putpixel((x, y), (0, 0, 0, 0) if inside else hx("b49cff"))
    return img


def laser_focus() -> Image.Image:
    """The focus, where light gathers: white, edged in the beam's light blue. Its 4x4 face is the middle of the texture."""
    img = Image.new("RGBA", (16, 16), hx("f4f8ff", 200))
    for x in range(16):
        for y in range(16):
            edge = x in (6, 9) or y in (6, 9)
            inside = 6 <= x <= 9 and 6 <= y <= 9
            if inside and edge:
                img.putpixel((x, y), hx("a8d0ff", 215))
            elif not inside:
                img.putpixel((x, y), hx("cfe4ff", 200))
    img.putpixel((7, 7), hx("ffffff", 230))
    return img


def solar_glass() -> Image.Image:
    """The Solar Emitter's lens: pale, faintly blue glass, firmer at its rim, with a highlight across it."""
    img = Image.new("RGBA", (16, 16), (210, 232, 255, 70))
    for i in range(16):
        for edge in ((i, 1), (i, 14), (1, i), (14, i)):
            img.putpixel(edge, (190, 215, 245, 150))
    for i in range(3, 10):
        img.putpixel((i, 12 - i), (255, 255, 255, 150))
        img.putpixel((i + 1, 12 - i), (255, 255, 255, 90))
    return img


def prism_face() -> Image.Image:
    """Facets of pale crystal in a dark frame: light split along clean lines."""
    img = stone(("12142a", "1b1e38", "262a4a"), 31)
    facets = [hx("7fb2ff"), hx("a9c9ff"), hx("c8dcff"), hx("5d8fe0")]
    for x in range(2, 14):
        for y in range(2, 14):
            img.putpixel((x, y), facets[((x // 3) + (y // 3) + (1 if x > y else 0)) % len(facets)])
    for i in range(2, 14):
        img.putpixel((i, i), hx("f4f8ff"))
        img.putpixel((i, 15 - i), hx("e8f2ff"))
    return img


def linker() -> Image.Image:
    """The Laser Linker: a dark rod with a lens at its tip and an azure line down it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(3, 13):
        img.putpixel((i, 15 - i), hx("3b3b45"))
        img.putpixel((i + 1, 15 - i), hx("2b2b33"))
    for i in range(5, 11):
        img.putpixel((i, 15 - i), hx("3485ff"))
    for x, y in ((12, 2), (13, 2), (12, 3), (13, 3), (11, 3), (12, 1)):
        img.putpixel((x, y), hx("c8dcff"))
    img.putpixel((13, 1), hx("ffffff"))
    return img


def detector_face(lit: bool) -> Image.Image:
    """A photodetector's face: a square sensor with a redstone pip in each corner."""
    img = stone(("2b2b33", "3b3b45", "4d4d59"), 51)
    for x in range(4, 12):
        for y in range(4, 12):
            img.putpixel((x, y), hx("7fb2ff") if lit else hx("10131f"))
    for x in range(5, 11):
        img.putpixel((x, 7), hx("c8dcff") if lit else hx("1f2742"))
        img.putpixel((x, 8), hx("c8dcff") if lit else hx("1f2742"))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), hx("ff3b3b") if lit else hx("6b1414"))
    return img


# A small mark etched in each lens, so they're told apart in a mount at a glance (5x5, '#' set).
GLYPHS = {
    "harvest_lens": ["###..", "..#..", ".#.#.", "#...#", "....."],   # a pick's head
    "damage_lens": ["....#", "...#.", "#.#..", ".#...", "#.#.."],    # a blade
    "pickup_lens": ["..#..", ".###.", "#.#.#", "..#..", "..#.."],    # coming back up
    "range_lens": ["..#..", "...#.", "#####", "...#.", "..#.."],     # going out, farther
    "filter_lens": ["#####", ".###.", "..#..", "..#..", "..#.."],    # a funnel: the Item Filter Lens
    "entity_filter_lens": [".###.", "#.#.#", "#####", ".###.", "#.#.#"],  # a face
    "block_filter_lens": ["#####", "#.#.#", "#####", "#.#.#", "#####"],   # a block, in its grain
    "isolator_lens": ["#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#"],       # bars: only power through
    "uv_lens": [".###.", "#...#", "#.#.#", "#...#", ".###."],        # an eye, shut to most
    "amplify_lens": ["..#..", "..#..", "#####", "..#..", "..#.."],   # more
    "place_lens": ["#####", "#...#", "#...#", "#...#", "#####"],     # a block
    "speed_lens": ["#..#.", ".#..#", "..#..", ".#..#", "#..#."],     # quicker
    "phantom_lens": ["#.#.#", ".....", "#.#.#", ".....", "#.#.#"],   # there and not there
    "insert_lens": ["..#..", "..#..", "#.#.#", ".###.", "#####"],    # into a box
    "extract_lens": ["#####", ".###.", "#.#.#", "..#..", "..#.."],   # out of a box
    "interact_lens": [".##..", "#.#..", "..###", "..#.#", "..###"],  # a hand on a lever
    "precision_lens": [".....", "..#..", ".#.#.", "..#..", "....."],  # a fine point
    "harmonic_lens": ["#...#", "#.#.#", "#.#.#", "#.#.#", "#...#"],   # many overtones
    "unrealising_lens": ["#.#.#", ".#.#.", "#.#.#", ".#.#.", "#.#.#"],  # not yet one thing
    "smelting_lens": ["..#..", ".#.#.", ".#.#.", "#...#", "#####"],  # a flame
    "crushing_lens": ["#...#", "##.##", ".....", "##.##", "#...#"],  # two jaws closing
}


def lens_item(colour: str, glyph=None) -> Image.Image:
    """A lens: a ground-glass disc in a dark rim, tinted by what it does, with a bright glint and its mark."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tint = hx(colour)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.6:
                img.putpixel((x, y), hx("1b1e38"))
            if d < 5.4:
                shade = 0.65 + 0.35 * (1 - d / 5.4)
                img.putpixel((x, y), tuple(min(255, int(c * shade)) for c in tint[:3]) + (230,))
    for x, y in ((5, 4), (4, 5), (5, 5), (6, 4)):
        img.putpixel((x, y), hx("ffffff"))
    if glyph:
        # Etched darker into the glass, a little low and right of the glint.
        mark = tuple(int(c * 0.35) for c in tint[:3]) + (255,)
        for row, line in enumerate(glyph):
            for col, cell in enumerate(line):
                if cell == "#":
                    img.putpixel((6 + col, 6 + row), mark)
    return img


def spectroscope() -> Image.Image:
    """A brass tube with a prism at its far end, splitting a line of light into three colours."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(3, 12):
        for w in range(-1, 2):
            img.putpixel((i, 15 - i + w), hx("b5893a") if w else hx("e0b45c"))
    for x, y in ((11, 3), (12, 3), (12, 4), (13, 4), (12, 2), (13, 3)):
        img.putpixel((x, y), hx("c8dcff"))
    for i, colour in enumerate(("ff4a4a", "5be38a", "7fb2ff")):
        img.putpixel((14, 1 + i), hx(colour))
        img.putpixel((15, i), hx(colour))
    img.putpixel((3, 12), hx("3a2a14"))
    return img


LENSES = {"harvest_lens": "f0a040", "damage_lens": "ff4a4a", "pickup_lens": "5be38a", "range_lens": "7fb2ff",
          "filter_lens": "e8e0c0", "entity_filter_lens": "f0a8a0", "block_filter_lens": "a8d090", "isolator_lens": "ff6a5a", "uv_lens": "9a7bff", "amplify_lens": "ffe070", "place_lens": "4ac8c8",
          "speed_lens": "ffa0d0", "phantom_lens": "b8c4d8",
          "insert_lens": "6fa8ff", "extract_lens": "ffc46f", "interact_lens": "c0ff7f",
          "precision_lens": "e0f0ff", "harmonic_lens": "ffd0f0", "unrealising_lens": "b070ff", "smelting_lens": "ff8040", "crushing_lens": "b09070"}


def lens_item(colour: str, glyph=None) -> Image.Image:
    """A lens: a ground-glass disc in a dark rim, tinted by what it does, with a bright glint and its mark."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tint = hx(colour)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.6:
                img.putpixel((x, y), hx("1b1e38"))
            if d < 5.4:
                shade = 0.65 + 0.35 * (1 - d / 5.4)
                img.putpixel((x, y), tuple(min(255, int(c * shade)) for c in tint[:3]) + (230,))
    for x, y in ((5, 4), (4, 5), (5, 5), (6, 4)):
        img.putpixel((x, y), hx("ffffff"))
    if glyph:
        # Etched darker into the glass, a little low and right of the glint.
        mark = tuple(int(c * 0.35) for c in tint[:3]) + (255,)
        for row, line in enumerate(glyph):
            for col, cell in enumerate(line):
                if cell == "#":
                    img.putpixel((6 + col, 6 + row), mark)
    return img


def spectroscope() -> Image.Image:
    """A brass tube with a prism at its far end, splitting a line of light into three colours."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(3, 12):
        for w in range(-1, 2):
            img.putpixel((i, 15 - i + w), hx("b5893a") if w else hx("e0b45c"))
    for x, y in ((11, 3), (12, 3), (12, 4), (13, 4), (12, 2), (13, 3)):
        img.putpixel((x, y), hx("c8dcff"))
    for i, colour in enumerate(("ff4a4a", "5be38a", "7fb2ff")):
        img.putpixel((14, 1 + i), hx(colour))
        img.putpixel((15, i), hx(colour))
    img.putpixel((3, 12), hx("3a2a14"))
    return img


LENSES = {"harvest_lens": "f0a040", "damage_lens": "ff4a4a", "pickup_lens": "5be38a", "range_lens": "7fb2ff",
          "filter_lens": "e8e0c0", "entity_filter_lens": "f0a8a0", "block_filter_lens": "a8d090", "isolator_lens": "ff6a5a", "uv_lens": "9a7bff", "amplify_lens": "ffe070", "place_lens": "4ac8c8",
          "speed_lens": "ffa0d0", "phantom_lens": "b8c4d8",
          "insert_lens": "6fa8ff", "extract_lens": "ffc46f", "interact_lens": "c0ff7f",
          "precision_lens": "e0f0ff", "harmonic_lens": "ffd0f0", "unrealising_lens": "b070ff", "smelting_lens": "ff8040", "crushing_lens": "b09070"}


def area_prism_face() -> Image.Image:
    """The prism's facets under a fine square grid: light spread over an area."""
    img = prism_face()
    for i in range(2, 14, 4):
        for j in range(2, 14):
            img.putpixel((i, j), hx("9a7bff"))
            img.putpixel((j, i), hx("9a7bff"))
    return img


def creative_void() -> Image.Image:
    """The Creative Energy Void: the Cell's magenta frame round a dark well that light falls into."""
    img = Image.new("RGBA", (16, 16), hx("1b0f24"))
    frame = hx("c040d0")
    for i in range(16):
        for j in (0, 15):
            img.putpixel((i, j), frame)
            img.putpixel((j, i), frame)
    for x in range(1, 15):
        for y in range(1, 15):
            d = max(abs(x - 7.5), abs(y - 7.5))
            shade = max(0.0, min(1.0, (d - 1.5) / 5.0))
            img.putpixel((x, y), tuple(int(a + (b - a) * shade) for a, b in zip((4, 0, 8), (70, 30, 90))) + (255,))
    for x, y in ((7, 7), (8, 8), (7, 8), (8, 7)):
        img.putpixel((x, y), hx("000000"))
    return img


def cube(textures: dict, front_key: str) -> dict:
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        key = "front" if face == "north" else "back" if face == "south" else "side"
        faces[face] = {"texture": "#" + key, "cullface": face}
    return {"parent": "minecraft:block/block", "textures": dict(textures, particle=textures["side"]),
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


def _rot_x90(v):
    x, y, z = v
    return (x, z, -y)


def _rot_y90(v):
    x, y, z = v
    return (-z, y, x)


def _rotated(v, rx: int, ry: int):
    for _ in range(rx // 90):
        v = _rot_x90(v)
    for _ in range(ry // 90):
        v = _rot_y90(v)
    return v


DIR_INDEX = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}
DIRS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def laser_mirror(name: str = "laser_mirror", splits: bool = False) -> None:
    """
    The Laser Mirror: a 10x10 silver plate whose face's middle is the block's middle, so beams meet it
    there and run straight; behind the plate a 2x2x2 hub, and a 2x2 post from the hub down to a boss and
    a 12x12 base on the block it stands on. The hub sits behind the plate whichever way it faces, so the
    post stands under the hub, not always in the middle. Each way the plate can face is drawn for a
    mirror standing on the floor, and the whole of it turned to each mount.
    """
    steel(31).save(os.path.join(TEX, "laser_mirror_steel.png"))
    (splitter_plate() if splits else mirror_plate()).save(os.path.join(TEX, f"{name}_plate.png"))
    t = {"steel": "quantimium:block/laser_mirror_steel", "plate": f"quantimium:block/{name}_plate",
         "particle": "quantimium:block/laser_mirror_steel"}
    # A half-silvered plate is silver both sides.
    back = "#plate" if splits else "#steel"
    opposite = {"up": "down", "down": "up", "north": "south", "south": "north", "east": "west", "west": "east"}

    def faced(element, face):
        element["faces"][face] = {"texture": "#plate", "uv": [3, 3, 13, 13]}
        element["faces"][opposite[face]] = {"texture": back, "uv": [3, 3, 13, 13]}
        return element

    def box(start, end, cull=None):
        faces = {f: {"texture": "#steel", "uv": [start[0], start[2], end[0], end[2]] if f in ("up", "down") else
                     [start[0] if f in ("north", "south") else start[2], 16 - end[1], end[0] if f in ("north", "south") else end[2], 16 - start[1]]}
                 for f in ("north", "south", "east", "west", "up", "down")}
        if cull:
            faces[cull]["cullface"] = cull
        return {"from": [round(v, 4) for v in start], "to": [round(v, 4) for v in end], "faces": faces}

    root = 2 ** 0.5

    def plate(normal):
        """The plate, its face's middle at the block's middle, facing {normal} (a floor mirror's, never down)."""
        nx, ny, nz = normal
        length = (nx * nx + ny * ny + nz * nz) ** 0.5
        ux, uy, uz = nx / length, ny / length, nz / length
        # The plate's middle, half its thickness behind the face.
        cx, cy, cz = 8 - ux * 0.5, 8 - uy * 0.5, 8 - uz * 0.5
        if ny and not nx and not nz:
            return faced(box([3, cy - 0.5, 3], [13, cy + 0.5, 13]), "up")
        if not ny and (nx == 0 or nz == 0):
            # Flat, facing a side: drawn facing north, the face on the plate's north side.
            if nz:
                element = box([3, 3, cz - 0.5], [13, 13, cz + 0.5])
            else:
                element = box([cx - 0.5, 3, 3], [cx + 0.5, 13, 13])
            face = {(0, 0, -1): "north", (0, 0, 1): "south", (-1, 0, 0): "west", (1, 0, 0): "east"}[(nx, ny, nz)]
            return faced(element, face)
        if ny:
            # Tilted between up and a side: a lying plate turned 45 degrees about the side's axis.
            if nz:
                element = box([3, cy - 0.5, cz - 5], [13, cy + 0.5, cz + 5])
                # A positive turn about x faces up and south.
                element["rotation"] = {"origin": [cx, cy, cz], "axis": "x", "angle": 45 if nz > 0 else -45, "rescale": False}
            else:
                element = box([cx - 5, cy - 0.5, 3], [cx + 5, cy + 0.5, 13])
                # A positive turn about z faces up and west.
                element["rotation"] = {"origin": [cx, cy, cz], "axis": "z", "angle": 45 if nx < 0 else -45, "rescale": False}
            return faced(element, "up")
        # Upright between two sides: a standing plate facing north, turned about y.
        element = box([cx - 5, 3, cz - 0.5], [cx + 5, 13, cz + 0.5])
        faced(element, "north" if nz < 0 else "south")
        # A positive turn about y takes north to north-west (and south to south-east).
        if nz < 0:
            angle = 45 if nx < 0 else -45
        else:
            angle = 45 if nx > 0 else -45
        element["rotation"] = {"origin": [cx, cy, cz], "axis": "y", "angle": angle, "rescale": False}
        return element

    def stand(normal):
        """The base and boss on the floor, the hub behind the plate, and a post from the boss up under the hub."""
        nx, ny, nz = normal
        length = (nx * nx + ny * ny + nz * nz) ** 0.5
        # Just inside the plate's back, so no faces meet flush.
        hx, hy, hz = 8 - nx / length * 1.9, 8 - ny / length * 1.9, 8 - nz / length * 1.9
        parts = [box([2, 0, 2], [14, 1, 14], "down"), box([4, 1, 4], [12, 2, 12])]
        parts.append(box([hx - 1, hy - 1, hz - 1], [hx + 1, hy + 1, hz + 1]))
        if hy - 1 > 2:
            parts.append(box([hx - 1, 2, hz - 1], [hx + 1, hy - 1, hz + 1]))
        return parts

    def write(model, elements):
        with open(os.path.join(MODELS, f"{model}.json"), "w") as handle:
            body = {"parent": "minecraft:block/block", "textures": t, "elements": elements}
            json.dump(body, handle, indent=2)

    # The ways a floor mirror's plate can face: flat, to up or a side; tilted, up and a side; upright, two sides.
    floor_normals = {}
    for a in DIRS:
        for b in DIRS:
            if "down" in (a, b):
                continue
            va, vb = DIRS[a], DIRS[b]
            if a != b and sum(p * q for p, q in zip(va, vb)) != 0:
                continue
            key = "_".join(sorted({a, b}))
            floor_normals[key] = tuple(p + q for p, q in zip(va, vb)) if a != b else va
    def marker(into, out):
        """On a splitter, a notch on the plate's rim by its output (turn) side, the rim's own darker silver."""
        n = [p + q for p, q in zip(DIRS[into], DIRS[out])]
        along = [q - p for p, q in zip(DIRS[into], DIRS[out])]
        nl, al = sum(v * v for v in n) ** 0.5, sum(v * v for v in along) ** 0.5
        middle = [8 + along[k] / al * 4.625 + n[k] / nl * 0.025 for k in range(3)]
        notch = box([v - 0.75 for v in middle], [v + 0.75 for v in middle])
        for face in notch["faces"].values():
            face["texture"] = "#plate"
            face["uv"] = [3, 6, 4, 7]
        return notch

    if splits:
        # Each way round: the dot shows which of the two sides light goes in by.
        for a in DIRS:
            for b in DIRS:
                if "down" in (a, b) or a == b or sum(p * q for p, q in zip(DIRS[a], DIRS[b])) != 0:
                    continue
                normal = tuple(p + q for p, q in zip(DIRS[a], DIRS[b]))
                write(f"{name}_{a}_to_{b}", stand(normal) + [plate(normal), marker(a, b)])
    else:
        for key, normal in floor_normals.items():
            write(f"{name}_{key}", stand(normal) + [plate(normal)])
    # Facing north, the way an inventory icon looks at it.
    item_normal = tuple(p + q for p, q in zip(DIRS["north"], DIRS["up"])) if splits else DIRS["north"]
    write(f"{name}_item", stand(item_normal) + [plate(item_normal)] + ([marker("north", "up")] if splits else []))

    mounts = {"down": (0, 0), "up": (180, 0), "south": (90, 0), "west": (90, 90), "north": (90, 180), "east": (90, 270)}
    names = {v: k for k, v in DIRS.items()}

    def back_to_floor(direction, rx, ry):
        # The direction on a floor mirror that turning by (rx, ry) takes to {direction}.
        for name, vector in DIRS.items():
            if names[_rotated(vector, rx, ry)] == direction:
                return name
        raise AssertionError(direction)

    parts = []
    for mount, (rx, ry) in mounts.items():
        for a in DIRS:
            for b in DIRS:
                if mount in (a, b) or (a != b and sum(p * q for p, q in zip(DIRS[a], DIRS[b])) != 0) or (splits and a == b):
                    continue
                if splits:
                    key = f"{back_to_floor(a, rx, ry)}_to_{back_to_floor(b, rx, ry)}"
                else:
                    key = "_".join(sorted({back_to_floor(a, rx, ry), back_to_floor(b, rx, ry)}))
                    assert key in floor_normals, (mount, a, b, key)
                parts.append({"when": {"mount": mount, "input": a, "output": b},
                              "apply": dict({"model": f"quantimium:block/{name}_{key}"}, **{k: v for k, v in (("x", rx), ("y", ry)) if v})})
    with open(os.path.join(STATES, f"{name}.json"), "w") as handle:
        json.dump({"multipart": parts}, handle, indent=1)
    with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:block/{name}_item"}}, handle, indent=2)
    for stale in ("laser_mirror_stand", "laser_mirror_plate", "laser_mirror_plate_upright", "laser_mirror_plate_flat"):
        path = os.path.join(MODELS, f"{stale}.json")
        if os.path.exists(path):
            os.remove(path)


def emitter_nozzle(texture_steel: str = "#steel", texture_focus: str = "#focus") -> list:
    """
    A simple emitter, pointing north from a focus at the block's middle: a 2x2 rod, a 6x6 collar and a 4x4
    collar on it, and a bright 2x2 tip at the face, where the beam leaves. Turn it to any side.
    """
    def cube(start, end, texture):
        faces = {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}
        return {"from": start, "to": end, "faces": faces}

    # The rod, and two collars on it, stepping down towards the tip: solid, so they sit on the rod.
    parts = [cube([7, 7, 1], [9, 9, 6], texture_steel), cube([5, 5, 3], [11, 11, 4], texture_steel), cube([6, 6, 1], [10, 10, 2], texture_steel)]
    tip = cube([7, 7, 0], [9, 9, 1], texture_focus)
    for face in tip["faces"].values():
        face["uv"] = [7, 7, 9, 9]
    parts.append(tip)
    return parts


def solar_emitter() -> None:
    """
    The Solar Emitter: a steel base, four corner posts holding a glass lens up top, a 4x4x4 focus in the
    middle, and a simple emitter on its output side (any but up).
    """
    laser_focus().save(os.path.join(TEX, "laser_focus.png"))
    solar_glass().save(os.path.join(TEX, "solar_emitter_glass.png"))
    t = {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus",
         "glass": "quantimium:block/solar_emitter_glass", "particle": "quantimium:block/laser_mirror_steel"}

    def cube(start, end, texture, cull=None):
        faces = {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}
        if cull:
            faces[cull]["cullface"] = cull
        return {"from": start, "to": end, "faces": faces}

    body = []
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        body.append(cube([x, 1, z], [x + 2, 13, z + 2], "#steel"))
    body.append(cube([1, 13, 1], [15, 14, 15], "#glass"))
    focus = cube([6, 6, 6], [10, 10, 10], "#focus")
    for face in focus["faces"].values():
        face["uv"] = [6, 6, 10, 10]
    body.append(focus)
    with open(os.path.join(MODELS, "solar_emitter.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t, "elements": body}, handle, indent=2)
    # The base, and the post the focus stands on; shining down, the emitter takes the post's place and pokes
    # through a hole in the base.
    base = [cube([1, 0, 1], [15, 1, 15], "#steel", "down")]
    holed = [cube([1, 0, 1], [15, 1, 6], "#steel", "down"), cube([1, 0, 10], [15, 1, 15], "#steel", "down"),
             cube([1, 0, 6], [6, 1, 10], "#steel", "down"), cube([10, 0, 6], [15, 1, 10], "#steel", "down")]
    post = [cube([7, 1, 7], [9, 6, 9], "#steel")]
    for name, elements in (("solar_emitter_base", base + post), ("solar_emitter_base_open", holed)):
        with open(os.path.join(MODELS, f"{name}.json"), "w") as handle:
            json.dump({"textures": t, "elements": elements}, handle, indent=2)
    with open(os.path.join(MODELS, "laser_emitter_nozzle.json"), "w") as handle:
        json.dump({"textures": t, "elements": emitter_nozzle()}, handle, indent=2)
    with open(os.path.join(MODELS, "solar_emitter_item.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t,
                   "elements": body + base + post + emitter_nozzle()}, handle, indent=2)
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": "quantimium:block/solar_emitter"}},
             {"when": {"output": "north|east|south|west"}, "apply": {"model": "quantimium:block/solar_emitter_base"}},
             {"when": {"output": "down"}, "apply": {"model": "quantimium:block/solar_emitter_base_open"}}]
    parts += [{"when": {"output": side}, "apply": dict({"model": "quantimium:block/laser_emitter_nozzle"}, **r)} for side, r in turns.items()]
    with open(os.path.join(STATES, "solar_emitter.json"), "w") as handle:
        json.dump({"multipart": parts}, handle, indent=1)
    with open(os.path.join(ITEMS, "solar_emitter.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/solar_emitter_item"}}, handle, indent=2)


def lens_slot() -> Image.Image:
    """A Lens Mount's slot panel: plain near-white, so its tint is the colour it shows (dark grey empty, the lens's when fitted)."""
    return Image.new("RGBA", (16, 16), hx("f2f2f2"))


def lens_mount() -> None:
    """
    The Lens Mount: a solid grey block, a 2px recess at each end, with half a focus set in the one light comes in
    by and half an emitter in the one it leaves by; on each of its four sides, four flat slot panels along the
    light, tinted dark when empty and the lens's colour when fitted (tint 0 to 3, LensTints). Drawn shining north,
    turned to each way it shines.
    """
    lens_slot().save(os.path.join(TEX, "lens_mount_slot.png"))
    t = {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus", "slot": "quantimium:block/lens_mount_slot",
         "particle": "quantimium:block/laser_mirror_steel"}

    def box(start, end, texture="#steel", uv=None, faces=("north", "south", "east", "west", "up", "down")):
        element = {"from": start, "to": end, "faces": {}}
        for f in faces:
            face = {"texture": texture}
            if uv:
                face["uv"] = uv
            element["faces"][f] = face
        return element

    parts = [box([0, 0, 2], [16, 16, 14])]
    # Each end: a frame round an 8x8 recess, 2 deep.
    for z0, z1 in ((0, 2), (14, 16)):
        parts += [box([0, 0, z0], [16, 4, z1]), box([0, 12, z0], [16, 16, z1]), box([0, 4, z0], [4, 12, z1]), box([12, 4, z0], [16, 12, z1])]
    # The way out: half an emitter, its collar and its tip; the way in: half a focus.
    parts += [box([6, 6, 1], [10, 10, 2]), box([7, 7, 0], [9, 9, 1], "#focus", [7, 7, 9, 9]), box([6, 6, 14], [10, 10, 16], "#focus", [6, 6, 10, 10])]
    # The slot panels, a hair proud of each side: a pixel wide, two apart, on the pixel grid; first lens nearest the way in.
    for index, z0 in enumerate((12, 9, 6, 3)):
        z1 = z0 + 1
        panels = [box([16.01, 4, z0], [16.01, 12, z1], "#slot", None, ("east",)), box([-0.01, 4, z0], [-0.01, 12, z1], "#slot", None, ("west",)),
                  box([4, 16.01, z0], [12, 16.01, z1], "#slot", None, ("up",)), box([4, -0.01, z0], [12, -0.01, z1], "#slot", None, ("down",))]
        for panel in panels:
            for face in panel["faces"].values():
                face["tintindex"] = index
        parts += panels
    with open(os.path.join(MODELS, "lens_mount.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "textures": t, "elements": parts}, handle, indent=2)
    with open(os.path.join(STATES, "lens_mount.json"), "w") as handle:
        json.dump({"variants": {f"facing={f}": dict({"model": "quantimium:block/lens_mount"}, **r) for f, r in ROTATIONS.items()}}, handle, indent=1)
    empty = 0x2A2B2F - 0x1000000
    with open(os.path.join(ITEMS, "lens_mount.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/lens_mount",
                             "tints": [{"type": "minecraft:constant", "value": empty} for _ in range(4)]}}, handle, indent=2)
    for stale in ("lens_mount_front.png",):
        path = os.path.join(TEX, stale)
        if os.path.exists(path):
            os.remove(path)


def laser_relay() -> None:
    """
    The Laser Relay: a 12x12 base and an 8x8 boss on the block it was placed against, a 2x2 post up to the
    Solar Emitter's focus in the middle, and the simple emitter on each side light leaves by. The stand is
    drawn for a floor and turned to each mount; the emitters turn to their faces.
    """
    t = {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus",
         "particle": "quantimium:block/laser_mirror_steel"}

    def cube(start, end, texture, cull=None):
        faces = {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}
        if cull:
            faces[cull]["cullface"] = cull
        return {"from": start, "to": end, "faces": faces}

    focus = cube([6, 6, 6], [10, 10, 10], "#focus")
    for face in focus["faces"].values():
        face["uv"] = [6, 6, 10, 10]
    stand = [cube([2, 0, 2], [14, 1, 14], "#steel", "down"), cube([4, 1, 4], [12, 2, 12], "#steel"), cube([7, 2, 7], [9, 6, 9], "#steel"), focus]
    with open(os.path.join(MODELS, "laser_relay.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t, "elements": stand}, handle, indent=2)
    with open(os.path.join(MODELS, "laser_relay_item.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t,
                   "elements": stand + emitter_nozzle()}, handle, indent=2)
    mounts = {"down": {}, "up": {"x": 180}, "south": {"x": 90}, "west": {"x": 90, "y": 90}, "north": {"x": 90, "y": 180},
              "east": {"x": 90, "y": 270}}
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"when": {"mount": m}, "apply": dict({"model": "quantimium:block/laser_relay"}, **r)} for m, r in mounts.items()]
    # Each face's nozzle tints its tip by that face (LensTints: the lens in that emitter).
    for side, r in turns.items():
        nozzle = emitter_nozzle()
        for face in nozzle[-1]["faces"].values():
            face["tintindex"] = DIR_INDEX[side]
        with open(os.path.join(MODELS, f"laser_relay_emitter_{side}.json"), "w") as handle:
            json.dump({"textures": t, "elements": nozzle}, handle, indent=2)
        parts.append({"when": {side: "true"}, "apply": dict({"model": f"quantimium:block/laser_relay_emitter_{side}"}, **r)})
    with open(os.path.join(STATES, "laser_relay.json"), "w") as handle:
        json.dump({"multipart": parts}, handle, indent=1)
    with open(os.path.join(ITEMS, "laser_relay.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/laser_relay_item"}}, handle, indent=2)
    for stale in [f"laser_relay_out_{d}" for d in ("north", "south", "east", "west", "up", "down")]:
        path = os.path.join(MODELS, stale + ".json")
        if os.path.exists(path):
            os.remove(path)


def outputs_block(name: str, face: str, side: str) -> None:
    """
    A block light leaves by any faces you set (the Laser Relay, the Isolator): its plain casing, and its face
    plate on each face light leaves by. Its item shows the plate on its front.
    """
    with open(os.path.join(MODELS, name + ".json"), "w") as handle:
        json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": f"quantimium:block/{side}", "particle": f"quantimium:block/{side}"}},
                  handle, indent=2)
    planes = {"north": ([0, 0, -0.01], [16, 16, -0.01]), "south": ([0, 0, 16.01], [16, 16, 16.01]),
              "west": ([-0.01, 0, 0], [-0.01, 16, 16]), "east": ([16.01, 0, 0], [16.01, 16, 16]),
              "down": ([0, -0.01, 0], [16, -0.01, 16]), "up": ([0, 16.01, 0], [16, 16.01, 16])}
    for direction, (start, end) in planes.items():
        with open(os.path.join(MODELS, f"{name}_out_{direction}.json"), "w") as handle:
            json.dump({"textures": {"plate": f"quantimium:block/{face}", "particle": f"quantimium:block/{side}"},
                       "elements": [{"from": start, "to": end, "faces": {direction: {"uv": [0, 0, 16, 16], "texture": "#plate"}}}]},
                      handle, indent=2)
    multipart = [{"apply": {"model": f"quantimium:block/{name}"}}]
    multipart += [{"when": {direction: "true"}, "apply": {"model": f"quantimium:block/{name}_out_{direction}"}} for direction in planes]
    with open(os.path.join(STATES, name + ".json"), "w") as handle:
        json.dump({"multipart": multipart}, handle, indent=1)
    with open(os.path.join(MODELS, f"{name}_item.json"), "w") as handle:
        json.dump(cube({"front": f"quantimium:block/{face}", "back": f"quantimium:block/{side}", "side": f"quantimium:block/{side}"}, "front"),
                  handle, indent=2)
    with open(os.path.join(ITEMS, name + ".json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:block/{name}_item"}}, handle, indent=2)


def emitter_panel(spec) -> Image.Image:
    """A Laser Emitter's plain face: the steel, with its tier set in the middle (the flash's vent, or one to three lens-coloured pips)."""
    img = steel(31)
    dark = hx("1b1b1e")
    if not spec["trim"]:
        for y in (5, 8, 11):
            for x in range(4, 12):
                img.putpixel((x, y), dark)
        return img
    pips = {"3485ff": (7,), "7fb2ff": (5, 9), "c8dcff": (4, 7, 10)}[spec["lens"]]
    for x0 in pips:
        for x in range(x0 - 1, x0 + 3):
            for y in range(6, 10):
                img.putpixel((x, y), dark)
    for x0 in pips:
        for x in range(x0, x0 + 2):
            for y in range(7, 9):
                img.putpixel((x, y), hx(spec["lens"]))
    return img


def emitter_tip(spec, lit: bool) -> Image.Image:
    """A Laser Emitter's tip, in its tier's colour: dim when idle, bright when it shines."""
    r, g, b, _ = hx(spec["lens"])
    colour = tuple(int(c + (255 - c) * 0.35) for c in (r, g, b)) if lit else tuple(int(c * 0.4) for c in (r, g, b))
    return Image.new("RGBA", (16, 16), colour + (255,))


# Each face of a block, as a map from (u, v, w) on it (w the depth in from the face) to the block's (x, y, z).
FACE_FRAMES = {
    "north": lambda u, v, w: (u, v, w),
    "south": lambda u, v, w: (16 - u, v, 16 - w),
    "west": lambda u, v, w: (w, v, 16 - u),
    "east": lambda u, v, w: (16 - w, v, u),
    "up": lambda u, v, w: (u, 16 - w, 16 - v),
    "down": lambda u, v, w: (u, w, v),
}
_NORMALS = {(0, 0, -1): "north", (0, 0, 1): "south", (-1, 0, 0): "west", (1, 0, 0): "east", (0, 1, 0): "up", (0, -1, 0): "down"}


def on_face(direction: str, start, end, faces: dict) -> dict:
    """A box drawn on a north face, (u, v, w) from start to end, put on {@code direction}'s face; faces keyed out/in/+u/-u/+v/-v."""
    frame = FACE_FRAMES[direction]
    a, b = frame(*start), frame(*end)
    origin = frame(0, 0, 0)
    axes = {"+u": frame(1, 0, 0), "-u": frame(-1, 0, 0), "+v": frame(0, 1, 0), "-v": frame(0, -1, 0), "out": frame(0, 0, -1), "in": frame(0, 0, 1)}
    element = {"from": [min(a[i], b[i]) for i in range(3)], "to": [max(a[i], b[i]) for i in range(3)], "faces": {}}
    for key, face in faces.items():
        world = _NORMALS[tuple(axes[key][i] - origin[i] for i in range(3))]
        element["faces"][world] = dict(face)
        if key == "out" and start[2] == 0:
            element["faces"][world]["cullface"] = world
    return element


def laser_emitters() -> None:
    """
    The Laser Emitters: a solid steel block, like the Lens Mount, its twelve edges shared, each face either plain (with its
    tier set in it) or, where light leaves, an 8x8 recess 2 deep holding half an emitter whose tip lights in the tier's
    colour while it shines. Faces come and go by the block's outputs.
    """
    def edges() -> list:
        parts = []
        spans = ((0, 2), (14, 16))
        for a in spans:
            for b in spans:
                parts += [([0, a[0], b[0]], [16, a[1], b[1]]), ([a[0], 2, b[0]], [a[1], 14, b[1]]), ([a[0], b[0], 2], [a[1], b[1], 14])]
        elements = []
        for start, end in parts:
            faces = {}
            for axis, (low, high) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
                if start[axis] == 0:
                    faces[low] = {"texture": "#steel", "cullface": low}
                if end[axis] == 16:
                    faces[high] = {"texture": "#steel", "cullface": high}
            elements.append({"from": start, "to": end, "faces": faces})
        return elements

    steel_face = {"texture": "#steel"}
    for direction in FACE_FRAMES:
        plain = [on_face(direction, (2, 2, 0), (14, 14, 2), {"out": {"texture": "#panel", "uv": [2, 2, 14, 14]}})]
        recess = [on_face(direction, (2, 12, 0), (14, 14, 2), {"out": steel_face, "-v": steel_face}),
                  on_face(direction, (2, 2, 0), (14, 4, 2), {"out": steel_face, "+v": steel_face}),
                  on_face(direction, (2, 4, 0), (4, 12, 2), {"out": steel_face, "+u": steel_face}),
                  on_face(direction, (12, 4, 0), (14, 12, 2), {"out": steel_face, "-u": steel_face}),
                  on_face(direction, (4, 4, 2), (12, 12, 2), {"out": steel_face}),
                  on_face(direction, (6, 6, 1), (10, 10, 2), {k: steel_face for k in ("out", "+u", "-u", "+v", "-v")}),
                  on_face(direction, (7, 7, 0), (9, 9, 1), {k: {"texture": "#tip", "uv": [7, 7, 9, 9]} for k in ("out", "+u", "-u", "+v", "-v")})]
        for name, elements in ((f"laser_emitter_plain_{direction}", plain), (f"laser_emitter_out_{direction}", recess)):
            with open(os.path.join(MODELS, name + ".json"), "w") as handle:
                json.dump({"textures": {"particle": "#steel"}, "elements": elements}, handle, indent=2)
        # The way in: the same recess with half a focus set in it, as the Laser Port's.
        focus = {"texture": "#focus", "uv": [6, 6, 10, 10]}
        inward = recess[:5] + [on_face(direction, (6, 6, 0), (10, 10, 2), {k: focus for k in ("out", "+u", "-u", "+v", "-v")})]
        with open(os.path.join(MODELS, f"laser_emitter_in_{direction}.json"), "w") as handle:
            json.dump({"textures": {"particle": "#steel", "focus": "quantimium:block/laser_focus"}, "elements": inward}, handle, indent=2)
        if direction in ("up", "down"):
            # Turned a quarter, for a block facing north or south: its tier marks run front to back, as facing east or west.
            turned = [on_face(direction, (2, 2, 0), (14, 14, 2), {"out": {"texture": "#panel", "uv": [2, 2, 14, 14], "rotation": 90}})]
            with open(os.path.join(MODELS, f"laser_emitter_plain_{direction}_turned.json"), "w") as handle:
                json.dump({"textures": {"particle": "#steel"}, "elements": turned}, handle, indent=2)
    with open(os.path.join(MODELS, "laser_emitter_edges.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "textures": {"steel": "quantimium:block/laser_mirror_steel", "particle": "quantimium:block/laser_mirror_steel"},
                   "elements": edges()}, handle, indent=2)
    with open(os.path.join(MODELS, "laser_emitter_item_shape.json"), "w") as handle:
        elements = edges()
        for direction in FACE_FRAMES:
            source = "out" if direction == "north" else "plain"
            with open(os.path.join(MODELS, f"laser_emitter_{source}_{direction}.json")) as part:
                elements += json.load(part)["elements"]
        json.dump({"parent": "minecraft:block/block", "elements": elements}, handle, indent=2)

    for name, spec in TIERS.items():
        solid_emitter(name, emitter_panel(spec), spec)
    solid_emitter("logistics_controller", controller_panel(), {"lens": LOGISTICS_VIOLET}, back=True)


FACINGS_ALL = ("north", "south", "east", "west", "up", "down")
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def solid_emitter(name: str, panel: Image.Image, spec, back: bool = False) -> None:
    """
    One solid emitter: its plain face, its tip in {@code spec}'s lens colour, and its multipart blockstate over the shared
    shapes. With {@code back}, the face behind its facing is a recessed focus, the way light comes in (the Logistics Controller).
    """
    panel.save(os.path.join(TEX, f"{name}_panel.png"))
    emitter_tip(spec, False).save(os.path.join(TEX, f"{name}_tip.png"))
    emitter_tip(spec, True).save(os.path.join(TEX, f"{name}_tip_lit.png"))
    steel_tex = "quantimium:block/laser_mirror_steel"

    def child(model, parent, **textures):
        with open(os.path.join(MODELS, model + ".json"), "w") as handle:
            json.dump({"parent": f"quantimium:block/{parent}", "textures": dict(steel=steel_tex, particle=steel_tex, **textures)}, handle, indent=2)
    parts = [{"apply": {"model": "quantimium:block/laser_emitter_edges"}}]
    for direction in FACE_FRAMES:
        child(f"{name}_plain_{direction}", f"laser_emitter_plain_{direction}", panel=f"quantimium:block/{name}_panel")
        for lit in (False, True):
            tag = "_lit" if lit else ""
            child(f"{name}_out{tag}_{direction}", f"laser_emitter_out_{direction}", tip=f"quantimium:block/{name}_tip{tag}")
            parts.append({"when": {direction: "true", "lit": str(lit).lower()}, "apply": {"model": f"quantimium:block/{name}_out{tag}_{direction}"}})
        # Facings it shows plain on: all, but with a back, not the one it's the back of.
        facings = [f for f in FACINGS_ALL if not (back and f == OPPOSITE[direction])]
        if back:
            child(f"{name}_in_{direction}", f"laser_emitter_in_{direction}")
            parts.append({"when": {"facing": OPPOSITE[direction]}, "apply": {"model": f"quantimium:block/{name}_in_{direction}"}})
        if direction in ("up", "down"):
            child(f"{name}_plain_{direction}_turned", f"laser_emitter_plain_{direction}_turned", panel=f"quantimium:block/{name}_panel")
            straight = [f for f in facings if f not in ("north", "south")]
            turned = [f for f in facings if f in ("north", "south")]
            parts.append({"when": {direction: "false", "facing": "|".join(straight)}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}"}})
            if turned:
                parts.append({"when": {direction: "false", "facing": "|".join(turned)}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}_turned"}})
        elif back:
            parts.append({"when": {direction: "false", "facing": "|".join(facings)}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}"}})
        else:
            parts.append({"when": {direction: "false"}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}"}})
    child(f"{name}_item", "laser_emitter_item_shape", panel=f"quantimium:block/{name}_panel", tip=f"quantimium:block/{name}_tip_lit")
    with open(os.path.join(STATES, f"{name}.json"), "w") as handle:
        json.dump({"multipart": parts}, handle, indent=1)
    with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:block/{name}_item"}}, handle, indent=2)
    for stale in (f"textures/block/{name}_side.png", f"textures/block/{name}_back.png", f"textures/block/{name}_front.png",
                  f"textures/block/{name}_front_lit.png", f"models/block/{name}.json", f"models/block/{name}_lit.json"):
        path = os.path.join(ASSETS, stale)
        if os.path.exists(path):
            os.remove(path)


def isolator_panel() -> Image.Image:
    """The Isolator's plain face: the steel, with dark bars set in it, red between: light through, nothing else."""
    img = steel(31)
    for x in range(4, 12):
        for y in range(4, 12):
            img.putpixel((x, y), hx("16171b"))
    for x in (5, 7, 9):
        for y in range(5, 11):
            img.putpixel((x, y), hx("8a8a96"))
            img.putpixel((x + 1, y), hx("6b1414") if y % 2 else hx("b03a33"))
    return img


def solid_outputs(name: str, panel: Image.Image, spec) -> None:
    """
    A solid block light leaves by any faces you set (the Isolator): the solid emitters' frame and shapes, its plain
    face where light doesn't leave, and an emitter, its tip in {@code spec}'s colour, where it does. Never lit.
    """
    panel.save(os.path.join(TEX, f"{name}_panel.png"))
    emitter_tip(spec, True).save(os.path.join(TEX, f"{name}_tip.png"))
    steel_tex = "quantimium:block/laser_mirror_steel"

    def child(model, parent, **textures):
        with open(os.path.join(MODELS, model + ".json"), "w") as handle:
            json.dump({"parent": f"quantimium:block/{parent}", "textures": dict(steel=steel_tex, particle=steel_tex, **textures)}, handle, indent=2)
    parts = [{"apply": {"model": "quantimium:block/laser_emitter_edges"}}]
    for direction in FACE_FRAMES:
        child(f"{name}_plain_{direction}", f"laser_emitter_plain_{direction}", panel=f"quantimium:block/{name}_panel")
        child(f"{name}_out_{direction}", f"laser_emitter_out_{direction}", tip=f"quantimium:block/{name}_tip")
        parts.append({"when": {direction: "true"}, "apply": {"model": f"quantimium:block/{name}_out_{direction}"}})
        if direction in ("up", "down"):
            child(f"{name}_plain_{direction}_turned", f"laser_emitter_plain_{direction}_turned", panel=f"quantimium:block/{name}_panel")
            parts.append({"when": {direction: "false", "facing": "east|west|up|down"}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}"}})
            parts.append({"when": {direction: "false", "facing": "north|south"}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}_turned"}})
        else:
            parts.append({"when": {direction: "false"}, "apply": {"model": f"quantimium:block/{name}_plain_{direction}"}})
    child(f"{name}_item", "laser_emitter_item_shape", panel=f"quantimium:block/{name}_panel", tip=f"quantimium:block/{name}_tip")
    with open(os.path.join(STATES, f"{name}.json"), "w") as handle:
        json.dump({"multipart": parts}, handle, indent=1)
    with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:block/{name}_item"}}, handle, indent=2)
    for stale in (f"models/block/{name}.json", f"textures/block/{name}_face.png", f"textures/block/{name}_side.png"):
        path = os.path.join(ASSETS, stale)
        if os.path.exists(path):
            os.remove(path)


LOGISTICS_VIOLET = "b49cff"
ROLE_COLOURS = {"storage": "3485ff", "provider": "f0a040", "requester": "5be38a"}


def controller_panel() -> Image.Image:
    """A Logistics Controller's plain face: the steel, with a violet network set in it, four nodes round a hub, joined."""
    img = steel(31)
    dark = hx("16171b")
    violet = hx(LOGISTICS_VIOLET)
    for x in range(4, 12):
        for y in range(4, 12):
            img.putpixel((x, y), dark)
    for i in range(5, 11):
        img.putpixel((i, 7), hx("6b5aa8"))
        img.putpixel((i, 8), hx("6b5aa8"))
        img.putpixel((7, i), hx("6b5aa8"))
        img.putpixel((8, i), hx("6b5aa8"))
    for x0, y0 in ((7, 7), (5, 5), (9, 5), (5, 9), (9, 9)):
        for x in range(x0, x0 + 2):
            for y in range(y0, y0 + 2):
                img.putpixel((x, y), violet)
    return img


def role_outline(colour: str, lit: bool) -> Image.Image:
    """A Logistics Receptacle's role mark: a 1px square outline (pixels 4 to 11) in its role's colour, bright while a controller's light reaches it; clear elsewhere."""
    r, g, b, _ = hx(colour)
    scale = 1.0 if lit else 0.4
    ring = tuple(int(c * scale) for c in (r, g, b)) + (255,)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(4, 12):
        for x, y in ((i, 4), (i, 11), (4, i), (11, i)):
            img.putpixel((x, y), ring)
    return img


def logistics_receptacle() -> None:
    """
    The Logistics Receptacle: the Relay's stand on the inventory it serves (base, a plate in its role's colour, a post) and
    the see-through focus in the middle light passes through. Drawn for a floor and turned to each face.
    """
    t = {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus", "particle": "quantimium:block/laser_mirror_steel"}

    def cube(start, end, texture, cull=None, uv=None):
        faces = {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}
        for face in faces.values():
            if uv:
                face["uv"] = uv
        if cull:
            faces[cull]["cullface"] = cull
        return {"from": start, "to": end, "faces": faces}

    # The plate is steel; its role shows as a square outline a pixel in from its edge, a hair above it.
    outline = {"from": [4, 2.01, 4], "to": [12, 2.01, 12], "faces": {"up": {"texture": "#mark", "uv": [4, 4, 12, 12]}}}
    shape = [cube([2, 0, 2], [14, 1, 14], "#steel", "down"), cube([3, 1, 3], [13, 2, 13], "#steel"), outline, cube([7, 2, 7], [9, 6, 9], "#steel"),
             cube([6, 6, 6], [10, 10, 10], "#focus", uv=[6, 6, 10, 10])]
    with open(os.path.join(MODELS, "logistics_receptacle.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t, "elements": shape}, handle, indent=2)
    mounts = {"down": {}, "up": {"x": 180}, "south": {"x": 90}, "west": {"x": 90, "y": 90}, "north": {"x": 90, "y": 180}, "east": {"x": 90, "y": 270}}
    variants = {}
    for role, colour in ROLE_COLOURS.items():
        for lit in (False, True):
            tag = "_lit" if lit else ""
            role_outline(colour, lit).save(os.path.join(TEX, f"logistics_receptacle_{role}{tag}.png"))
            with open(os.path.join(MODELS, f"logistics_receptacle_{role}{tag}.json"), "w") as handle:
                json.dump({"parent": "quantimium:block/logistics_receptacle", "textures": {"mark": f"quantimium:block/logistics_receptacle_{role}{tag}"}}, handle, indent=2)
            for facing, r in mounts.items():
                variants[f"facing={facing},lit={str(lit).lower()},role={role}"] = dict({"model": f"quantimium:block/logistics_receptacle_{role}{tag}"}, **r)
    with open(os.path.join(STATES, "logistics_receptacle.json"), "w") as handle:
        json.dump({"variants": variants}, handle, indent=1)
    with open(os.path.join(ITEMS, "logistics_receptacle.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/logistics_receptacle_storage_lit"}}, handle, indent=2)

PORT_MODES = {"collect": ("5be38a",), "send": ("f0a040",), "both": ("5be38a", "f0a040")}


def port_side(colours) -> Image.Image:
    """A Laser Port's side: the steel, with a 3x3 grille in the middle lit in its mode's colour (both: the two in a checker)."""
    img = steel(31)
    dark = hx("16171b")
    for x in range(3, 13):
        for y in range(3, 13):
            img.putpixel((x, y), dark)
    for i in range(3):
        for j in range(3):
            colour = hx(colours[(i + j) % len(colours)])
            for x in range(4 + i * 3, 6 + i * 3):
                for y in range(4 + j * 3, 6 + j * 3):
                    img.putpixel((x, y), colour)
    return img


def laser_port() -> None:
    """
    The Laser Port: a solid steel block like the Lens Mount, half a focus in the 8x8 recess light comes in by and half
    an emitter in the one it leaves by, and on each of its four sides a grille lit in its mode's colour: green to
    collect, amber to send, both in a checker. Drawn shining north, turned to each way it shines.
    """
    def box(start, end):
        element = {"from": start, "to": end, "faces": {}}
        for axis, (low, high) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
            for name, edge in ((low, start[axis] == 0), (high, end[axis] == 16)):
                side = edge and axis != 2
                element["faces"][name] = {"texture": "#side" if side else "#steel"}
                if edge:
                    element["faces"][name]["cullface"] = name
        return element

    parts = [box([0, 0, 2], [16, 16, 14])]
    for z0, z1 in ((0, 2), (14, 16)):
        parts += [box([0, 0, z0], [16, 4, z1]), box([0, 12, z0], [16, 16, z1]), box([0, 4, z0], [4, 12, z1]), box([12, 4, z0], [16, 12, z1])]
    collar = box([6, 6, 1], [10, 10, 2])
    tip = box([7, 7, 0], [9, 9, 1])
    for face in tip["faces"].values():
        face.update({"texture": "#focus", "uv": [7, 7, 9, 9]})
    focus = box([6, 6, 14], [10, 10, 16])
    for face in focus["faces"].values():
        face.update({"texture": "#focus", "uv": [6, 6, 10, 10]})
    parts += [collar, tip, focus]
    with open(os.path.join(MODELS, "laser_port.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "textures": {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus",
                                                                   "particle": "quantimium:block/laser_mirror_steel"}, "elements": parts}, handle, indent=2)
    variants = {}
    for mode, colours in PORT_MODES.items():
        port_side(colours).save(os.path.join(TEX, f"laser_port_side_{mode}.png"))
        with open(os.path.join(MODELS, f"laser_port_{mode}.json"), "w") as handle:
            json.dump({"parent": "quantimium:block/laser_port", "textures": {"side": f"quantimium:block/laser_port_side_{mode}"}}, handle, indent=2)
        for facing, r in ROTATIONS.items():
            variants[f"facing={facing},mode={mode}"] = dict({"model": f"quantimium:block/laser_port_{mode}"}, **r)
    with open(os.path.join(STATES, "laser_port.json"), "w") as handle:
        json.dump({"variants": variants}, handle, indent=1)
    with open(os.path.join(ITEMS, "laser_port.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/laser_port_collect"}}, handle, indent=2)
    for stale in ("textures/block/collector_face.png", "textures/block/collector_side.png", "textures/block/sender_face.png", "textures/block/sender_side.png",
                  "models/block/collector.json", "models/block/sender.json", "blockstates/collector.json", "blockstates/sender.json",
                  "items/collector.json", "items/sender.json"):
        path = os.path.join(ASSETS, stale)
        if os.path.exists(path):
            os.remove(path)


def terminal_front() -> Image.Image:
    """A Logistics Terminal's screen: a steel bezel round dark glass, with a faint grid of item cells in the network's blue and one lit."""
    img = steel(31)
    for x in range(2, 14):
        for y in range(2, 14):
            img.putpixel((x, y), hx("0d1020"))
    for i in range(3):
        for j in range(3):
            colour = hx("7fb2ff") if (i, j) == (0, 0) else hx("2a3f8f")
            for x in range(4 + i * 3, 6 + i * 3):
                for y in range(4 + j * 3, 6 + j * 3):
                    img.putpixel((x, y), colour)
    return img


def logistics_terminal() -> None:
    """The Logistics Terminal: a steel block, its screen on the front; turned to face whoever placed it."""
    terminal_front().save(os.path.join(TEX, "logistics_terminal_front.png"))
    model = cube({"front": "quantimium:block/logistics_terminal_front", "back": "quantimium:block/laser_mirror_steel",
                  "side": "quantimium:block/laser_mirror_steel"}, "front")
    with open(os.path.join(MODELS, "logistics_terminal.json"), "w") as handle:
        json.dump(model, handle, indent=2)
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
    with open(os.path.join(STATES, "logistics_terminal.json"), "w") as handle:
        json.dump({"variants": {f"facing={f}": dict({"model": "quantimium:block/logistics_terminal"}, **r) for f, r in turns.items()}}, handle, indent=1)
    with open(os.path.join(ITEMS, "logistics_terminal.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/logistics_terminal"}}, handle, indent=2)

def logistics_lectern() -> None:
    """
    The Logistics Lectern: the terminal as a stand, not a block. A base and a post up to a focus the light comes in
    to, and over it the screen on a short neck, tilted towards whoever reads it; drawn facing north, turned to face
    whoever placed it.
    """
    t = {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus",
         "screen": "quantimium:block/logistics_terminal_front", "particle": "quantimium:block/laser_mirror_steel"}

    def cube(start, end, faces_texture="#steel", **extra):
        faces = {f: {"texture": faces_texture} for f in ("north", "south", "east", "west", "up", "down")}
        element = {"from": start, "to": end, "faces": faces}
        element.update(extra)
        return element

    # The focus in the block's middle, where light meets it, so beams through it run straight.
    focus = cube([6, 6, 6], [10, 10, 10], "#focus")
    for face in focus["faces"].values():
        face["uv"] = [6, 6, 10, 10]
    base = cube([2, 0, 2], [14, 1, 14])
    base["faces"]["down"]["cullface"] = "down"
    # Square, so its texture's pixels stay square, and centred over the focus.
    screen = cube([2, 11, 2], [14, 13, 14], rotation={"origin": [8, 12, 8], "axis": "x", "angle": -30})
    screen["faces"]["up"] = {"texture": "#screen", "uv": [0, 0, 16, 16]}
    elements = [base, cube([4, 1, 4], [12, 2, 12]), cube([7, 2, 7], [9, 6, 9]), focus, cube([7, 10, 7], [9, 12, 9]), screen]
    with open(os.path.join(MODELS, "logistics_lectern.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "textures": t, "elements": elements}, handle, indent=2)
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
    with open(os.path.join(STATES, "logistics_lectern.json"), "w") as handle:
        json.dump({"variants": {f"facing={f}": dict({"model": "quantimium:block/logistics_lectern"}, **r) for f, r in turns.items()}}, handle, indent=1)
    with open(os.path.join(ITEMS, "logistics_lectern.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/logistics_lectern"}}, handle, indent=2)


def materialiser_face() -> Image.Image:
    """A Logistics Materialiser's face: the steel, a socket framed in the network's blue, and the Materialiser's eye in it."""
    import math
    img = steel(31)
    blue = hx("2a52ff")
    for x in range(3, 13):
        for y in range(3, 13):
            img.putpixel((x, y), hx("07080f"))
    for i in range(3, 13):
        for x, y in ((i, 2), (i, 13), (2, i), (13, i)):
            img.putpixel((x, y), blue)
    for x in range(16):
        for y in range(16):
            if 2.2 <= math.hypot(x - 7.5, y - 7.5) < 3.3:
                img.putpixel((x, y), hx("7fb2ff"))
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        img.putpixel((x, y), hx("e8f2ff"))
    return img


def logistics_materialiser() -> None:
    """The Logistics Materialiser: a steel block with its socket on every face, open to pipes on all of them."""
    materialiser_face().save(os.path.join(TEX, "logistics_materialiser.png"))
    with open(os.path.join(MODELS, "logistics_materialiser.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "quantimium:block/logistics_materialiser"}}, handle, indent=2)
    with open(os.path.join(STATES, "logistics_materialiser.json"), "w") as handle:
        json.dump({"variants": {"": {"model": "quantimium:block/logistics_materialiser"}}}, handle, indent=1)
    with open(os.path.join(ITEMS, "logistics_materialiser.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/logistics_materialiser"}}, handle, indent=2)

def cavity_mirror() -> None:
    """
    The Cavity Mirror: a Relay with a flat Splitter mirror for its front. The plate on its mirror face (north, drawn),
    a rod back to the see-through focus in the middle, and the simple emitter on each face light leaves by.
    """
    t = {"steel": "quantimium:block/laser_mirror_steel", "focus": "quantimium:block/laser_focus",
         "plate": "quantimium:block/laser_splitter_plate", "particle": "quantimium:block/laser_mirror_steel"}

    def cube(start, end, texture, uv=None):
        faces = {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}
        for face in faces.values():
            if uv:
                face["uv"] = uv
        return {"from": start, "to": end, "faces": faces}

    plate = cube([3, 3, 0], [13, 13, 1], "#steel")
    plate["faces"]["north"] = {"texture": "#plate", "uv": [3, 3, 13, 13]}
    plate["faces"]["south"] = {"texture": "#plate", "uv": [3, 3, 13, 13]}
    core = [plate, cube([7, 7, 1], [9, 9, 6], "#steel"), cube([6, 6, 6], [10, 10, 10], "#focus", [6, 6, 10, 10])]
    # Its stand, as a Relay's (base, boss and post up to the focus), drawn for a floor and turned to each mount.
    stand = [{"from": [2, 0, 2], "to": [14, 1, 14], "faces": {f: {"texture": "#steel", **({"cullface": "down"} if f == "down" else {})}
                                                             for f in ("north", "south", "east", "west", "up", "down")}},
             cube([4, 1, 4], [12, 2, 12], "#steel"), cube([7, 2, 7], [9, 6, 9], "#steel")]
    with open(os.path.join(MODELS, "cavity_mirror_stand.json"), "w") as handle:
        json.dump({"textures": t, "elements": stand}, handle, indent=2)
    with open(os.path.join(MODELS, "cavity_mirror.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t, "elements": core}, handle, indent=2)
    nozzle_south = []
    for element in emitter_nozzle():
        a, b = FACE_FRAMES["south"](*element["from"]), FACE_FRAMES["south"](*element["to"])
        nozzle_south.append(dict(element, **{"from": [min(a[i], b[i]) for i in range(3)], "to": [max(a[i], b[i]) for i in range(3)]}))
    with open(os.path.join(MODELS, "cavity_mirror_item.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t,
                   "elements": core + nozzle_south + stand}, handle, indent=2)
    mounts = {"down": {}, "up": {"x": 180}, "south": {"x": 90}, "west": {"x": 90, "y": 90}, "north": {"x": 90, "y": 180}, "east": {"x": 90, "y": 270}}
    turns = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"when": {"facing": facing}, "apply": dict({"model": "quantimium:block/cavity_mirror"}, **r)} for facing, r in ROTATIONS.items()]
    parts += [{"when": {"mount": m}, "apply": dict({"model": "quantimium:block/cavity_mirror_stand"}, **r)} for m, r in mounts.items()]
    parts += [{"when": {side: "true"}, "apply": dict({"model": "quantimium:block/laser_emitter_nozzle"}, **r)} for side, r in turns.items()]
    with open(os.path.join(STATES, "cavity_mirror.json"), "w") as handle:
        json.dump({"multipart": parts}, handle, indent=1)
    with open(os.path.join(ITEMS, "cavity_mirror.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/cavity_mirror_item"}}, handle, indent=2)
    for stale in ("textures/block/cavity_mirror_front.png", "textures/block/cavity_mirror_front_lit.png", "textures/block/cavity_mirror_back.png",
                  "textures/block/cavity_mirror_back_lit.png", "textures/block/cavity_mirror_side.png", "models/block/cavity_mirror_lit.json"):
        path = os.path.join(ASSETS, stale)
        if os.path.exists(path):
            os.remove(path)

ROTATIONS = {"north": {}, "south": {"y": 180}, "west": {"y": 270}, "east": {"y": 90}, "up": {"x": 270}, "down": {"x": 90}}


def entangled_mirror() -> None:
    """
    The Entangled Mirror: a steel cage, a violet plate with a window on its face (north, drawn) and a steel back; its
    Tesseract floats in the middle (EntangledMirrorBER).
    """
    entangled_plate().save(os.path.join(TEX, "entangled_mirror_plate.png"))
    t = {"steel": "quantimium:block/laser_mirror_steel", "plate": "quantimium:block/entangled_mirror_plate",
         "particle": "quantimium:block/laser_mirror_steel"}

    def box(start, end, texture="#steel"):
        return {"from": start, "to": end, "faces": {f: {"texture": texture} for f in ("north", "south", "east", "west", "up", "down")}}

    plate = box([3, 3, 0], [13, 13, 1])
    plate["faces"]["north"] = {"texture": "#plate", "uv": [3, 3, 13, 13]}
    plate["faces"]["south"] = {"texture": "#plate", "uv": [3, 3, 13, 13]}
    ring_front = [box([1, 13, 0], [15, 15, 2]), box([1, 1, 0], [15, 3, 2]), box([1, 3, 0], [3, 13, 2]), box([13, 3, 0], [15, 13, 2])]
    ring_back = [box([1, 13, 14], [15, 15, 16]), box([1, 1, 14], [15, 3, 16]), box([1, 3, 14], [3, 13, 16]), box([13, 3, 14], [15, 13, 16])]
    posts = [box([x, y, 2], [x + 2, y + 2, 14]) for x in (1, 13) for y in (1, 13)]
    back = box([3, 3, 15], [13, 13, 16])
    elements = [plate] + ring_front + ring_back + posts + [back]
    with open(os.path.join(MODELS, "entangled_mirror.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": t, "elements": elements}, handle, indent=2)
    variants = {}
    for facing, rot in ROTATIONS.items():
        for powered in ("false", "true"):
            variants[f"facing={facing},powered={powered}"] = dict({"model": "quantimium:block/entangled_mirror"}, **rot)
    with open(os.path.join(STATES, "entangled_mirror.json"), "w") as handle:
        json.dump({"variants": variants}, handle, indent=1)
    with open(os.path.join(ITEMS, "entangled_mirror.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/entangled_mirror"}}, handle, indent=2)


def main() -> None:
    written = 0
    laser_emitters()

    laser_relay()
    laser_mirror()
    laser_mirror("laser_splitter", splits=True)
    solar_emitter()
    cavity_mirror()
    entangled_mirror()
    for name, face, back_of, side_seed in (("photodetector", detector_face, None, 52),):
        stone(("1b1e38", "262a4a", "323760") if name == "cavity_mirror" else ("2b2b33", "3b3b45", "4d4d59"), side_seed).save(
            os.path.join(TEX, f"{name}_side.png"))
        for lit in (False, True):
            tag = "_lit" if lit else ""
            face(lit).save(os.path.join(TEX, f"{name}_front{tag}.png"))
            if back_of:
                back_of(lit).save(os.path.join(TEX, f"{name}_back{tag}.png"))
            model = cube({"front": f"quantimium:block/{name}_front{tag}",
                          "back": f"quantimium:block/{name}_back{tag}" if back_of else f"quantimium:block/{name}_front{tag}",
                          "side": f"quantimium:block/{name}_side"}, "front")
            with open(os.path.join(MODELS, f"{name}{tag}.json"), "w") as handle:
                json.dump(model, handle, indent=2)
        variants = {}
        for facing, rot in ROTATIONS.items():
            for lit in (False, True):
                variants[f"facing={facing},lit={str(lit).lower()}"] = dict({"model": f"quantimium:block/{name}{'_lit' if lit else ''}"}, **rot)
        with open(os.path.join(STATES, f"{name}.json"), "w") as handle:
            json.dump({"variants": variants}, handle, indent=1)
        with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
            json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:block/{name}_lit"}}, handle, indent=2)
    solid_outputs("isolator", isolator_panel(), {"lens": "ff5050"})
    lens_mount()
    laser_port()
    logistics_receptacle()
    logistics_terminal()
    logistics_lectern()
    logistics_materialiser()
    for name, colour in LENSES.items():
        lens_item(colour, GLYPHS.get(name)).save(os.path.join(ASSETS, "textures", "item", f"{name}.png"))
        with open(os.path.join(ASSETS, "models", "item", f"{name}.json"), "w") as handle:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"quantimium:item/{name}"}}, handle, indent=2)
        with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
            json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:item/{name}"}}, handle, indent=2)
    area_prism_face().save(os.path.join(TEX, "area_prism.png"))
    with open(os.path.join(MODELS, "area_prism.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "quantimium:block/area_prism"}}, handle, indent=2)
    with open(os.path.join(STATES, "area_prism.json"), "w") as handle:
        json.dump({"variants": {"": {"model": "quantimium:block/area_prism"}}}, handle, indent=1)
    with open(os.path.join(ITEMS, "area_prism.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/area_prism"}}, handle, indent=2)
    prism_face().save(os.path.join(TEX, "laser_prism.png"))
    with open(os.path.join(MODELS, "laser_prism.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "quantimium:block/laser_prism"}}, handle, indent=2)
    with open(os.path.join(STATES, "laser_prism.json"), "w") as handle:
        json.dump({"variants": {"": {"model": "quantimium:block/laser_prism"}}}, handle, indent=1)
    with open(os.path.join(ITEMS, "laser_prism.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/laser_prism"}}, handle, indent=2)
    spectroscope().save(os.path.join(ASSETS, "textures", "item", "spectroscope.png"))
    with open(os.path.join(ASSETS, "models", "item", "spectroscope.json"), "w") as handle:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "quantimium:item/spectroscope"}}, handle, indent=2)
    with open(os.path.join(ITEMS, "spectroscope.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:item/spectroscope"}}, handle, indent=2)
    creative_void().save(os.path.join(TEX, "creative_energy_void.png"))
    linker().save(os.path.join(ASSETS, "textures", "item", "laser_linker.png"))
    with open(os.path.join(ASSETS, "models", "item", "laser_linker.json"), "w") as handle:
        json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "quantimium:item/laser_linker"}}, handle, indent=2)
    with open(os.path.join(ITEMS, "laser_linker.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:item/laser_linker"}}, handle, indent=2)
    print(f"wrote {written + 4} textures for the emitters and the mirror, with their models")


if __name__ == "__main__":
    main()
