#!/usr/bin/env python3
"""
Lasers' block art: the emitters (flash, I, II, III), the mirror, the Lens Mount and Collector, and the
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


def side(spec) -> Image.Image:
    img = stone(spec["stone"], spec["seed"])
    if spec["trim"]:
        # Flux trim: a straight azure line across the middle with a lighter pixel where it meets the edge.
        trim = hx(spec["trim"])
        for x in range(2, 14):
            img.putpixel((x, 8), trim)
        img.putpixel((2, 8), hx("c8dcff"))
        img.putpixel((13, 8), hx("c8dcff"))
    else:
        # The flash emitter: a vent of three slots, for the fire inside.
        for y in (5, 8, 11):
            for x in range(4, 12):
                img.putpixel((x, y), hx("1b1b1e"))
    return img


def front(spec, lit: bool) -> Image.Image:
    img = stone(spec["stone"], spec["seed"] + 1)
    lens = hx(spec["lens"])
    ring = hx("0b0d18")
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 5.6:
                img.putpixel((x, y), ring)
            if d < 4.2:
                img.putpixel((x, y), lens if lit else tuple(int(c * 0.35) for c in lens[:3]) + (255,))
            if lit and d < 1.8:
                img.putpixel((x, y), hx("ffffff"))
    return img


def back(spec) -> Image.Image:
    img = stone(spec["stone"], spec["seed"] + 2)
    port = hx("0b0d18")
    for x in range(5, 11):
        for y in range(5, 11):
            img.putpixel((x, y), port)
    accent = hx(spec["trim"] or spec["lens"])
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        img.putpixel((x, y), accent)
    return img


def mirror_face() -> Image.Image:
    img = stone(("2b2b33", "3b3b45", "4d4d59"), 21)
    for x in range(2, 14):
        for y in range(2, 14):
            img.putpixel((x, y), hx("9fb4d8") if (x + y) % 5 else hx("dfe8f8"))
    # Two bright sheen lines across the plate.
    for i in range(2, 14):
        if 2 <= 15 - i - 3 < 14:
            img.putpixel((i, 15 - i - 3), hx("f4f8ff"))
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


def cavity_face(lit: bool) -> Image.Image:
    """A half-silvered plate: silver with a mesh of darker pixels, rimmed in azure, glowing when charged."""
    img = stone(("1b1e38", "262a4a", "323760"), 41)
    for x in range(2, 14):
        for y in range(2, 14):
            silver = hx("c8dcff") if lit else hx("8a9ab8")
            img.putpixel((x, y), silver if (x + y) % 2 else (hx("7fb2ff") if lit else hx("5c6884")))
    for i in range(1, 15):
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            img.putpixel((x, y), hx("3485ff") if lit else hx("1f2742"))
    return img


def coupler(lit: bool) -> Image.Image:
    """The cavity mirror's back: a small output lens."""
    img = stone(("1b1e38", "262a4a", "323760"), 42)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 3.6:
                img.putpixel((x, y), hx("0b0d18"))
            if d < 2.4:
                img.putpixel((x, y), hx("7fb2ff") if lit else hx("1f2742"))
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


def lens_mount_face() -> Image.Image:
    """A violet-rimmed window onto four lens slots: violet is for what changes a beam."""
    img = stone(("1e1630", "2a2042", "3a2d5a"), 61)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 4.6 < d < 6.2:
                img.putpixel((x, y), hx("9a7bff"))
            elif d <= 4.6:
                img.putpixel((x, y), hx("120c22"))
    # The four lenses, seen edge-on as a stack: four pale bars.
    for i, y in enumerate((5, 7, 9, 11)):
        for x in range(5, 11):
            img.putpixel((x, y - 1 + (i % 2 == 0)), hx("c8b8ff") if x in (5, 10) else hx("e4dcff"))
    return img


def collector_face() -> Image.Image:
    """A grille that light passes and items catch on: a green-trimmed mesh."""
    img = stone(("1c2a22", "26382e", "32493c"), 71)
    for x in range(2, 14):
        for y in range(2, 14):
            img.putpixel((x, y), hx("0d1712"))
    for i in range(2, 14, 3):
        for j in range(2, 14):
            img.putpixel((i, j), hx("5be38a"))
            img.putpixel((j, i), hx("5be38a"))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), hx("c8f5d8"))
    return img


def lens_item(colour: str) -> Image.Image:
    """A lens: a ground-glass disc in a dark rim, tinted by what it does, with a bright glint."""
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
          "filter_lens": "e8e0c0", "uv_lens": "9a7bff", "amplify_lens": "ffe070", "place_lens": "4ac8c8",
          "speed_lens": "ffa0d0"}


def sender_face() -> Image.Image:
    """The Collector's grille in teal, with an arrow out through it: things leave down the beam."""
    img = stone(("162a2a", "1f3838", "294949"), 75)
    for x in range(2, 14):
        for y in range(2, 14):
            img.putpixel((x, y), hx("0d1717"))
    for i in range(2, 14, 3):
        for j in range(2, 14):
            img.putpixel((i, j), hx("2f8f8f"))
            img.putpixel((j, i), hx("2f8f8f"))
    for i in range(4):
        for j in range(-i, i + 1):
            img.putpixel((7 + j, 4 + i), hx("4ac8c8"))
    for y in range(8, 12):
        img.putpixel((7, y), hx("4ac8c8"))
        img.putpixel((8, y), hx("4ac8c8"))
    return img


def area_prism_face() -> Image.Image:
    """The prism's facets under a fine square grid: light spread over an area."""
    img = prism_face()
    for i in range(2, 14, 4):
        for j in range(2, 14):
            img.putpixel((i, j), hx("9a7bff"))
            img.putpixel((j, i), hx("9a7bff"))
    return img


def splitter_face() -> Image.Image:
    """A silver plate cut into four by a cross: light leaving every way it's set to."""
    img = mirror_face()
    for i in range(2, 14):
        img.putpixel((i, 7), hx("2b2b33"))
        img.putpixel((i, 8), hx("2b2b33"))
        img.putpixel((7, i), hx("2b2b33"))
        img.putpixel((8, i), hx("2b2b33"))
    for x, y in ((7, 7), (8, 8), (7, 8), (8, 7)):
        img.putpixel((x, y), hx("c8dcff"))
    return img


def isolator_face() -> Image.Image:
    """A dark plate behind a grey bar: light through, nothing else."""
    img = stone(("1b1b1e", "26262b", "33333a"), 81)
    for x in range(2, 14):
        for y in range(2, 14):
            img.putpixel((x, y), hx("3b3b45") if (x + y) % 4 else hx("4d4d59"))
    for x in range(2, 14):
        for y in (7, 8):
            img.putpixel((x, y), hx("8a8a96"))
    return img


def cube(textures: dict, front_key: str) -> dict:
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        key = "front" if face == "north" else "back" if face == "south" else "side"
        faces[face] = {"texture": "#" + key, "cullface": face}
    return {"parent": "minecraft:block/block", "textures": dict(textures, particle=textures["side"]),
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}


ROTATIONS = {"north": {}, "south": {"y": 180}, "west": {"y": 270}, "east": {"y": 90}, "up": {"x": 270}, "down": {"x": 90}}


def main() -> None:
    written = 0
    for name, spec in TIERS.items():
        side(spec).save(os.path.join(TEX, f"{name}_side.png"))
        back(spec).save(os.path.join(TEX, f"{name}_back.png"))
        front(spec, False).save(os.path.join(TEX, f"{name}_front.png"))
        front(spec, True).save(os.path.join(TEX, f"{name}_front_lit.png"))
        for lit in (False, True):
            tag = "_lit" if lit else ""
            model = cube({"front": f"quantimium:block/{name}_front{tag}", "back": f"quantimium:block/{name}_back",
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
        written += 4

    mirror_face().save(os.path.join(TEX, "laser_mirror_face.png"))
    stone(("2b2b33", "3b3b45", "4d4d59"), 22).save(os.path.join(TEX, "laser_mirror_side.png"))
    model = cube({"front": "quantimium:block/laser_mirror_face", "back": "quantimium:block/laser_mirror_side",
                  "side": "quantimium:block/laser_mirror_side"}, "front")
    with open(os.path.join(MODELS, "laser_mirror.json"), "w") as handle:
        json.dump(model, handle, indent=2)
    with open(os.path.join(STATES, "laser_mirror.json"), "w") as handle:
        json.dump({"variants": {f"facing={f}": dict({"model": "quantimium:block/laser_mirror"}, **r) for f, r in ROTATIONS.items()}},
                  handle, indent=1)
    with open(os.path.join(ITEMS, "laser_mirror.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/laser_mirror"}}, handle, indent=2)
    for name, face, back_of, side_seed in (("cavity_mirror", cavity_face, coupler, 43), ("photodetector", detector_face, None, 52)):
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
    for name, face, tones, seed in (("lens_mount", lens_mount_face, ("1e1630", "2a2042", "3a2d5a"), 62),
                                    ("collector", collector_face, ("1c2a22", "26382e", "32493c"), 72),
                                    ("isolator", isolator_face, ("1b1b1e", "26262b", "33333a"), 92),
                                    ("sender", sender_face, ("162a2a", "1f3838", "294949"), 76)):
        face().save(os.path.join(TEX, f"{name}_face.png"))
        stone(tones, seed).save(os.path.join(TEX, f"{name}_side.png"))
        model = cube({"front": f"quantimium:block/{name}_face", "back": f"quantimium:block/{name}_side",
                      "side": f"quantimium:block/{name}_side"}, "front")
        with open(os.path.join(MODELS, f"{name}.json"), "w") as handle:
            json.dump(model, handle, indent=2)
        with open(os.path.join(STATES, f"{name}.json"), "w") as handle:
            json.dump({"variants": {f"facing={f}": dict({"model": f"quantimium:block/{name}"}, **r) for f, r in ROTATIONS.items()}},
                      handle, indent=1)
        with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
            json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:block/{name}"}}, handle, indent=2)
    for name, colour in LENSES.items():
        lens_item(colour).save(os.path.join(ASSETS, "textures", "item", f"{name}.png"))
        with open(os.path.join(ASSETS, "models", "item", f"{name}.json"), "w") as handle:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"quantimium:item/{name}"}}, handle, indent=2)
        with open(os.path.join(ITEMS, f"{name}.json"), "w") as handle:
            json.dump({"model": {"type": "minecraft:model", "model": f"quantimium:item/{name}"}}, handle, indent=2)
    # The splitter: plain casing, with the split plate on each face light leaves by.
    splitter_face().save(os.path.join(TEX, "splitter_face.png"))
    stone(("2b2b33", "3b3b45", "4d4d59"), 82).save(os.path.join(TEX, "splitter_side.png"))
    with open(os.path.join(MODELS, "splitter.json"), "w") as handle:
        json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "quantimium:block/splitter_side", "particle": "quantimium:block/splitter_side"}}, handle, indent=2)
    planes = {"north": ([0, 0, -0.01], [16, 16, -0.01]), "south": ([0, 0, 16.01], [16, 16, 16.01]),
              "west": ([-0.01, 0, 0], [-0.01, 16, 16]), "east": ([16.01, 0, 0], [16.01, 16, 16]),
              "down": ([0, -0.01, 0], [16, -0.01, 16]), "up": ([0, 16.01, 0], [16, 16.01, 16])}
    for face, (start, end) in planes.items():
        with open(os.path.join(MODELS, f"splitter_out_{face}.json"), "w") as handle:
            json.dump({"textures": {"plate": "quantimium:block/splitter_face", "particle": "quantimium:block/splitter_side"},
                       "elements": [{"from": start, "to": end,
                                     "faces": {face: {"uv": [0, 0, 16, 16], "texture": "#plate"}}}]}, handle, indent=2)
    multipart = [{"apply": {"model": "quantimium:block/splitter"}}]
    multipart += [{"when": {face: "true"}, "apply": {"model": f"quantimium:block/splitter_out_{face}"}} for face in planes]
    with open(os.path.join(STATES, "splitter.json"), "w") as handle:
        json.dump({"multipart": multipart}, handle, indent=1)
    with open(os.path.join(MODELS, "splitter_item.json"), "w") as handle:
        json.dump(cube({"front": "quantimium:block/splitter_face", "back": "quantimium:block/splitter_side",
                        "side": "quantimium:block/splitter_face"}, "front"), handle, indent=2)
    with open(os.path.join(ITEMS, "splitter.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:block/splitter_item"}}, handle, indent=2)
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
    linker().save(os.path.join(ASSETS, "textures", "item", "laser_linker.png"))
    with open(os.path.join(ASSETS, "models", "item", "laser_linker.json"), "w") as handle:
        json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "quantimium:item/laser_linker"}}, handle, indent=2)
    with open(os.path.join(ITEMS, "laser_linker.json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:model", "model": "quantimium:item/laser_linker"}}, handle, indent=2)
    print(f"wrote {written + 4} textures for the emitters and the mirror, with their models")


if __name__ == "__main__":
    main()
