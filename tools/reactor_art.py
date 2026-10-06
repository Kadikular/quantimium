#!/usr/bin/env python3
"""
Generates the Reactor's non-trace art: the plinth's sides, the Horizon Core's cage, and the ports' flat
overlays. The plinth's tops are tools/reactor_traces.py.

    ./tools/reactor_art.py            # write the textures
    ./tools/reactor_art.py --preview  # also a contact sheet in tools/out
"""

from __future__ import annotations

import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "textures", "block")


def hx(code: str, alpha: int = 255) -> tuple:
    return int(code[0:2], 16), int(code[2:4], 16), int(code[4:6], 16), alpha


BOARD = hx("0e1120")
SPECKS = [hx("131626"), hx("0b0d18"), hx("161a2e")]
BEVEL = hx("1c2238")
LAMINATE = hx("0a0c18")
COPPER_LIT, COPPER_DARK = hx("3485ff"), hx("1f2742")
GLOW, DIM = hx("7fb2ff"), hx("262d42")
STONE, STONE_DARK, STONE_LIGHT = hx("161a2c"), hx("0b0d18"), hx("232a42")

KINDS = {  # port colour (lit, dark)
    "input": (hx("3485ff"), hx("1b2a52")),
    "output": (hx("b196ff"), hx("3a2c5e")),
    "energy": (hx("f0b429"), hx("5a4510")),
    "materialiser": (hx("e8f2ff"), hx("3a4058")),
    "me": (hx("3fe0c8"), hx("164a44")),
}


def board(seed: int = 7) -> Image.Image:
    image = Image.new("RGBA", (16, 16), BOARD)
    rnd = random.Random(seed)
    for _ in range(30):
        image.putpixel((rnd.randrange(16), rnd.randrange(16)), rnd.choice(SPECKS))
    return image


def plinth_side(lit: bool) -> Image.Image:
    """The edge of the board: a bevel at the top, the copper layer with its traces' ends, laminate below."""
    image = board(11)
    for x in range(16):
        image.putpixel((x, 0), BEVEL)
        image.putpixel((x, 1), BEVEL if x % 5 else STONE_LIGHT)
        image.putpixel((x, 5), COPPER_LIT if lit else COPPER_DARK)
        image.putpixel((x, 10), LAMINATE)
        image.putpixel((x, 15), LAMINATE)
    # Where a lane's trace would leave the block: the same two lanes the tops use.
    for lane in (3, 11):
        for x in (lane, lane + 1):
            for y in (4, 6):
                image.putpixel((x, y), GLOW if lit else DIM)
    return image


def dais_top(lit: bool) -> Image.Image:
    """The core's dais: board, with a ring that lights when the Reactor forms."""
    image = board(13)
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d < 6.4:
                image.putpixel((x, y), COPPER_LIT if lit else COPPER_DARK)
            elif 6.4 <= d < 7.0:
                image.putpixel((x, y), hx("0a1a44") if lit else LAMINATE)
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        image.putpixel((x, y), GLOW if lit else DIM)
    return image


def post(lit: bool) -> Image.Image:
    """The cage's posts and frame: dark stone, with a groove that glows when formed."""
    image = Image.new("RGBA", (16, 16), STONE)
    rnd = random.Random(17)
    for _ in range(24):
        image.putpixel((rnd.randrange(16), rnd.randrange(16)), rnd.choice([STONE_DARK, STONE_LIGHT]))
    for y in range(16):
        image.putpixel((7, y), GLOW if lit else DIM)
        image.putpixel((8, y), COPPER_LIT if lit else STONE_DARK)
    for x in range(16):
        image.putpixel((x, 0), STONE_LIGHT)
        image.putpixel((x, 15), STONE_DARK)
    return image


def glyph_pixels(kind: str) -> list:
    if kind == "input":   # an arrow pointing in (down)
        return [(7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (5, 8), (6, 9), (7, 10), (8, 10), (9, 9), (10, 8)]
    if kind == "output":  # an arrow pointing out (up)
        return [(7, 10), (8, 10), (7, 9), (8, 9), (7, 8), (8, 8), (7, 7), (8, 7), (5, 7), (6, 6), (7, 5), (8, 5), (9, 6), (10, 7)]
    if kind == "energy":  # a bolt
        return [(9, 4), (8, 5), (8, 6), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (7, 10), (6, 11)]
    if kind == "me":  # a hub with four linked nodes: a network
        nodes = [(7, 7), (4, 4), (10, 4), (4, 10), (10, 10)]
        pixels = [(x + dx, y + dy) for x, y in nodes for dx in (0, 1) for dy in (0, 1)]
        return pixels + [(6, 6), (9, 6), (6, 9), (9, 9)]
    # materialiser: a ring with a dot, an eye that observes
    ring = [(x, y) for x in range(16) for y in range(16) if 2.2 <= math.hypot(x - 7.5, y - 7.5) < 3.3]
    return ring + [(7, 7), (8, 7), (7, 8), (8, 8)]


def port_overlay(kind: str, lit: bool) -> Image.Image:
    """A flat socket laid over the board, on every face: a frame in the port's colour and its glyph."""
    colour = KINDS[kind][0 if lit else 1]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # The socket's recess, then its frame with notched corners.
    for x in range(3, 13):
        for y in range(3, 13):
            image.putpixel((x, y), hx("07080f", 235))
    for i in range(3, 13):
        for x, y in ((i, 2), (i, 13), (2, i), (13, i)):
            image.putpixel((x, y), colour)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        image.putpixel((x, y), (0, 0, 0, 0))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        image.putpixel((x, y), colour)
    for x, y in glyph_pixels(kind):
        image.putpixel((x, y), colour)
    return image


def main() -> None:
    written = {}
    for lit, tag in ((False, ""), (True, "_active")):
        written[f"reactor_plinth_side{tag}"] = plinth_side(lit)
        written[f"horizon_core_dais{tag}"] = dais_top(lit)
        written[f"horizon_core_post{tag}"] = post(lit)
        for kind in KINDS:
            written[f"reactor_{kind}_port_overlay{tag}"] = port_overlay(kind, lit)
    for name, image in written.items():
        image.save(os.path.join(TEX, f"{name}.png"))
    print(f"wrote {len(written)} textures")
    if "--preview" in sys.argv:
        out = os.path.join(ROOT, "tools", "out")
        os.makedirs(out, exist_ok=True)
        names = sorted(written)
        sheet = Image.new("RGBA", (len(names) * 72, 72), (60, 60, 60, 255))
        for i, name in enumerate(names):
            tile = Image.new("RGBA", (16, 16), (40, 40, 40, 255))
            if "overlay" in name:
                tile = board(11).copy()
            tile.alpha_composite(written[name])
            sheet.paste(tile.resize((64, 64), Image.NEAREST), (i * 72 + 4, 4))
        sheet.save(os.path.join(out, "reactor_art.png"))


if __name__ == "__main__":
    main()
