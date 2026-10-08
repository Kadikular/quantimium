#!/usr/bin/env python3
"""
Lasers' block art: the emitters (flash, I, II, III) and the mirror, with their models and blockstates.

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
    print(f"wrote {written + 2} textures for the emitters and the mirror, with their models")


if __name__ == "__main__":
    main()
