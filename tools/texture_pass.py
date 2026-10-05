#!/usr/bin/env python3
"""
Quantimium texture pass: generates every texture that was a vanilla placeholder or a shared copy,
and recolours the painted art the style guide flags as off-palette. See TEXTURE_PASS.md.

    ./tools/texture_pass.py              # write textures into src/main/resources
    ./tools/texture_pass.py --preview    # also write a labelled contact sheet to tools/out
    ./tools/texture_pass.py --only a,b   # regenerate just these outputs (names as in the table)
    ./tools/texture_pass.py --compare main   # also write TEXTURE_PASS.png, before and after

Everything is deterministic: each texture seeds its own RNG from its name, so a rerun only changes
what the code changed. Hand-painted textures are never written unless they are recoloured here, and
even then only their colours move; their pixels stay where they were painted. Recolours start from
the untouched copies in tools/texture_sources/, never from their own last output.

Palettes are the style guide's (STYLE_GUIDE.html). Grammar, in short:
  * Flux is azure, built, straight 1px lines with a lighter pixel at junctions. Steady.
  * Anomaly is blue-violet, grown, jagged and asymmetric. Never pink.
  * Tier is read from the stone: cobble (low), deepslate (mid), navy obsidian (high).
  * Low tier carries one azure pip per face, mid tier edge trim, high tier full circuits.
  * No soft gradients: the in-game glow render types add bloom, the texture should not.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import random
from typing import Callable, Dict, Iterable, Sequence, Tuple

from PIL import Image, ImageDraw

RGBA = Tuple[int, int, int, int]


def hexc(value: str, alpha: int = 255) -> RGBA:
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


# ---- Palettes (STYLE_GUIDE.html) ----

FLUX_OFF = hexc("#0f2a66")      # unpowered trace
FLUX_DIM = hexc("#1b4fb8")      # dim trace, secondary lines
FLUX = hexc("#3485ff")          # anchor: primary trim and circuit lines
FLUX_CORE = hexc("#7fb2ff")     # junctions, the brightest pixel of a trace
FLUX_WHITE = hexc("#d6e6ff")    # tiny hot centres only

VOID = hexc("#1e122a")
MITE = hexc("#42305a")
VIOLET = hexc("#8a60f0")        # anchor: hazard line and crystal facet
CRYSTAL = hexc("#a887ff")
HIGHLIGHT = hexc("#d2bafc")
RIFT_EDGE = hexc("#b36cff")

# High tier: obsidian as painted on the Foundry and tanks. The style guide's navy read too blue next
# to them, so the ramp is sampled from the painted art instead: purple-black with blue panel lines.
SEAM = hexc("#060710")
OBSIDIAN_DARK = hexc("#0b0d18")
OBSIDIAN_LOW = hexc("#0f0e1b")
NAVY = hexc("#131626")          # anchor: high-tier stone
OBSIDIAN_LEAN = hexc("#1b192f")
PANEL = hexc("#1f243a")
PANEL_LINE = hexc("#262d42")
RAISED = hexc("#363f58")
BRIDGE = hexc("#2d294c")        # where late tier lets azure and violet meet

DEEP_SEAM = hexc("#1c1c22")
DEEPSLATE = hexc("#2e2e36")     # anchor: mid-tier stone
TILE = hexc("#48484e")
EDGE = hexc("#6c6c74")


MIRROR_DARK = hexc("#303036")
MIRROR_GREY = hexc("#54545a")
MIRROR_LIGHT = hexc("#7e7e8a")

FAILURE = hexc("#ff382e")

CLEAR = (0, 0, 0, 0)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "textures")
# Untouched copies of the painted art this pass recolours, so a recolour always starts from what was
# painted rather than from its own last output.
SOURCES = os.path.join(ROOT, "tools", "texture_sources")


def source(path: str) -> Image.Image:
    return Image.open(os.path.join(SOURCES, path + ".png")).convert("RGBA")


# ---- Canvas helpers ----

def rng_for(name: str) -> random.Random:
    return random.Random(int(hashlib.sha1(name.encode()).hexdigest()[:8], 16))


def canvas(size: Tuple[int, int] = (16, 16), fill: RGBA = CLEAR) -> Image.Image:
    return Image.new("RGBA", size, fill)


def put(img: Image.Image, x: int, y: int, colour: RGBA) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), colour)


def hline(img: Image.Image, x0: int, x1: int, y: int, colour: RGBA) -> None:
    for x in range(min(x0, x1), max(x0, x1) + 1):
        put(img, x, y, colour)


def vline(img: Image.Image, x: int, y0: int, y1: int, colour: RGBA) -> None:
    for y in range(min(y0, y1), max(y0, y1) + 1):
        put(img, x, y, colour)


def rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, colour: RGBA) -> None:
    hline(img, x0, x1, y0, colour)
    hline(img, x0, x1, y1, colour)
    vline(img, x0, y0, y1, colour)
    vline(img, x1, y0, y1, colour)


def fill(img: Image.Image, x0: int, y0: int, x1: int, y1: int, colour: RGBA) -> None:
    for y in range(y0, y1 + 1):
        hline(img, x0, x1, y, colour)


def bevel(img: Image.Image, x0: int, y0: int, x1: int, y1: int, light: RGBA, dark: RGBA) -> None:
    """Raised panel edge: light along the top and left, dark along the bottom and right."""
    hline(img, x0, x1, y0, light)
    vline(img, x0, y0, y1, light)
    hline(img, x0, x1, y1, dark)
    vline(img, x1, y0, y1, dark)


def trace(img: Image.Image, points: Sequence[Tuple[int, int]], colour: RGBA,
          junction: RGBA | None = None) -> None:
    """A 1px circuit line through right-angled waypoints, with a lighter pixel at each bend."""
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        if ax == bx:
            vline(img, ax, ay, by, colour)
        else:
            hline(img, ax, bx, ay, colour)
    if junction:
        for x, y in points[1:-1]:
            put(img, x, y, junction)


def mirror_x(img: Image.Image) -> Image.Image:
    return img.transpose(Image.FLIP_LEFT_RIGHT)


def rotate(img: Image.Image, quarter_turns: int) -> Image.Image:
    """Clockwise quarter turns, the way a blockstate's y rotation turns a top face."""
    return img.rotate(-90 * quarter_turns)


def symmetric4(img: Image.Image) -> Image.Image:
    """Mirrors the top-left quadrant into the other three."""
    out = img.copy()
    quad = img.crop((0, 0, 8, 8))
    out.paste(quad, (0, 0))
    out.paste(quad.transpose(Image.FLIP_LEFT_RIGHT), (8, 0))
    out.paste(quad.transpose(Image.FLIP_TOP_BOTTOM), (0, 8))
    out.paste(quad.transpose(Image.ROTATE_180), (8, 8))
    return out


def darken(colour: RGBA, factor: float) -> RGBA:
    return (int(colour[0] * factor), int(colour[1] * factor), int(colour[2] * factor), colour[3])


def lerp(a: RGBA, b: RGBA, t: float) -> RGBA:
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))  # type: ignore[return-value]


# ---- Stone ----

def speckle(img: Image.Image, rng: random.Random, ramp: Sequence[RGBA], weights: Sequence[float],
            box: Tuple[int, int, int, int] | None = None) -> None:
    """Fills with single-pixel noise drawn from a ramp: vanilla stone's grain, not a gradient."""
    x0, y0, x1, y1 = box or (0, 0, img.width - 1, img.height - 1)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, rng.choices(ramp, weights)[0])


def navy_stone(name: str) -> Image.Image:
    img = canvas()
    speckle(img, rng_for(name), [OBSIDIAN_DARK, OBSIDIAN_LOW, NAVY, OBSIDIAN_LEAN, PANEL],
            [3, 4, 9, 3, 1])
    return img


def deepslate_stone(name: str) -> Image.Image:
    img = canvas()
    speckle(img, rng_for(name), [DEEP_SEAM, DEEPSLATE, DEEPSLATE, TILE], [1, 9, 5, 2])
    return img


def deepslate_tiles(name: str) -> Image.Image:
    """Four tiles with dark seams and a lit top-left bevel, cooler than vanilla's."""
    rng = rng_for(name)
    img = deepslate_stone(name)
    for ox, oy in ((0, 0), (8, 0), (0, 8), (8, 8)):
        shift = 4 if oy == 8 else 0
        x0 = (ox + shift) % 16
        for dx in range(8):
            put(img, (x0 + dx) % 16, oy, TILE if rng.random() < 0.8 else EDGE)
        for dy in range(8):
            put(img, x0, oy + dy, TILE if rng.random() < 0.7 else DEEPSLATE)
        for dx in range(8):
            put(img, (x0 + dx) % 16, oy + 7, DEEP_SEAM)
        for dy in range(8):
            put(img, (x0 + 7) % 16, oy + dy, DEEP_SEAM)
    return img




# ---- Output table ----

OUTPUTS: Dict[str, Callable[[], Image.Image]] = {}


def texture(path: str) -> Callable[[Callable[[], Image.Image]], Callable[[], Image.Image]]:
    def register(fn: Callable[[], Image.Image]) -> Callable[[], Image.Image]:
        OUTPUTS[path] = fn
        return fn
    return register


# ---- Shared tier grammar ----


def outer_seam(img: Image.Image, colour: RGBA) -> None:
    rect(img, 0, 0, 15, 15, colour)



def high_face(name: str) -> Image.Image:
    img = navy_stone(name)
    outer_seam(img, SEAM)
    return img


def unpower(img: Image.Image) -> Image.Image:
    """
    The idle version of a lit high-tier face: every azure or cyan pixel becomes an embossed line in
    the chassis' own raised tones, so the circuit still reads, dark, when the machine is off.
    """
    out = img.copy()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = out.getpixel((x, y))
            if a == 0:
                continue
            hue, sat, val = _hue_sat((r, g, b, a))
            if 170.0 <= hue <= 235.0 and sat > 0.12 and val > 0.3:
                luminance = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                out.putpixel((x, y), RAISED if luminance > 0.55 else PANEL)
    return out


# Rune glyphs for high-tier friezes: 3×5, drawn as 1px strokes. The mood reference's floor marks.
# Kept symmetric or near it, and away from Latin letter shapes, so a frieze never spells a word.
RUNES = [
    [".#.", "###", "#.#", "#.#", ".#."],
    ["#.#", ".#.", "###", ".#.", "#.#"],
    ["#.#", "#.#", ".#.", "###", ".#."],
    [".#.", ".#.", "###", "#.#", "#.#"],
    ["###", "#.#", ".#.", "#.#", "###"],
    ["#..", "#.#", "###", "#.#", "..#"],
]


def rune(img: Image.Image, x: int, y: int, index: int, colour: RGBA) -> None:
    for dy, row in enumerate(RUNES[index % len(RUNES)]):
        for dx, cell in enumerate(row):
            if cell == "#":
                put(img, x + dx, y + dy, colour)


# ---- Low tier: stone, furnace-like, one pip ----

# Lighter than the style guide's cobble swatches: in game those read as deepslate. These sit near
# vanilla stone, a touch warm, so a low-tier machine looks like it came out of a furnace recipe.
STONE_SHADOW = hexc("#4f4d4b")
STONE_DARK = hexc("#646260")
STONE_BASE = hexc("#777573")
STONE_FACE = hexc("#878582")
STONE_HI = hexc("#9c9a96")
LENS_DARK = hexc("#1c1f26")


def stone_noise(name: str) -> Image.Image:
    """Vanilla-stone grain: mostly mid tones, with a few short dark streaks."""
    rng = rng_for(name)
    img = canvas()
    speckle(img, rng, [STONE_DARK, STONE_BASE, STONE_BASE, STONE_FACE, STONE_HI], [2, 7, 6, 4, 1])
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for dx in range(rng.randrange(2, 4)):
            put(img, (x + dx) % 16, y, STONE_DARK)
    return img


def smooth_stone(name: str) -> Image.Image:
    """Vanilla smooth stone: a flat light face inside a darker rim."""
    img = canvas()
    speckle(img, rng_for(name), [STONE_BASE, STONE_FACE, STONE_FACE, STONE_HI], [2, 8, 6, 1])
    rect(img, 0, 0, 15, 15, STONE_DARK)
    return img


def low_side(name: str) -> Image.Image:
    """A furnace-like side: stone grain in a dark rim, lit along the inner top and left."""
    img = stone_noise(name)
    rect(img, 0, 0, 15, 15, STONE_SHADOW)
    hline(img, 1, 14, 1, STONE_HI)
    vline(img, 1, 1, 14, STONE_FACE)
    return img


def stone_cobble(name: str) -> Image.Image:
    """Irregular cobbles in the lighter stone tones, grown from random seeds, each lit top-left."""
    rng = rng_for(name)
    seeds = [(rng.randrange(16), rng.randrange(16)) for _ in range(9)]
    def owner(x: int, y: int) -> int:
        return min(range(len(seeds)), key=lambda i: min((x - seeds[i][0]) % 16, (seeds[i][0] - x) % 16) ** 2
                   + min((y - seeds[i][1]) % 16, (seeds[i][1] - y) % 16) ** 2)
    cells = {(x, y): owner(x, y) for y in range(16) for x in range(16)}
    img = canvas()
    for (x, y), cell in cells.items():
        if cells[((x + 1) % 16, y)] != cell or cells[(x, (y + 1) % 16)] != cell:
            colour = STONE_SHADOW
        elif cells[((x - 1) % 16, y)] != cell or cells[(x, (y - 1) % 16)] != cell:
            colour = STONE_HI if rng.random() < 0.6 else STONE_FACE
        else:
            colour = rng.choices([STONE_DARK, STONE_BASE, STONE_FACE], [2, 6, 3])[0]
        put(img, x, y, colour)
    return img


def window(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    """A recessed dark window: shadowed rim above and left, lit sill below and right."""
    fill(img, x0, y0, x1, y1, LENS_DARK)
    hline(img, x0 - 1, x1 + 1, y0 - 1, STONE_SHADOW)
    vline(img, x0 - 1, y0 - 1, y1 + 1, STONE_SHADOW)
    hline(img, x0 - 1, x1 + 1, y1 + 1, STONE_HI)
    vline(img, x1 + 1, y0 - 1, y1 + 1, STONE_HI)


@texture("block/machine_bottom_low")
def machine_bottom_low() -> Image.Image:
    return smooth_stone("machine_bottom_low")


def _observation_chamber(lit: bool) -> Image.Image:
    """A viewing port. The pip is the lens: dim at rest, lit with a hot pixel while observing."""
    img = low_side("observation_chamber")
    window(img, 6, 6, 9, 9)
    fill(img, 7, 7, 8, 8, FLUX if lit else FLUX_OFF)
    put(img, 7, 7, FLUX_CORE if lit else FLUX_DIM)
    return img


@texture("block/observation_chamber")
def observation_chamber() -> Image.Image:
    return _observation_chamber(False)


@texture("block/observation_chamber_lit")
def observation_chamber_lit() -> Image.Image:
    return _observation_chamber(True)


def _flux_detector(lit: bool) -> Image.Image:
    """A slot gauge whose single pip rises when flux is read."""
    img = low_side("flux_detector")
    window(img, 7, 4, 8, 11)
    if lit:
        vline(img, 7, 5, 6, FLUX)
        vline(img, 8, 5, 6, FLUX)
        put(img, 7, 5, FLUX_CORE)
    else:
        hline(img, 7, 8, 11, FLUX_OFF)
    return img


@texture("block/flux_detector")
def flux_detector() -> Image.Image:
    return _flux_detector(False)


@texture("block/flux_detector_active")
def flux_detector_active() -> Image.Image:
    return _flux_detector(True)


@texture("block/basic_quantum_crafter_frame")
def basic_quantum_crafter_frame() -> Image.Image:
    """
    The deepslate crafter's frame, in stone: a rim and bevel that land on every slab and pylon, and
    on top a 3×3 grid of recessed cells, the crafting grid, with dividers on rows and columns 3, 6,
    9 and 12, as painted on the Quantum Crafter.
    """
    img = stone_noise("basic_quantum_crafter_frame")
    rect(img, 0, 0, 15, 15, STONE_SHADOW)
    bevel(img, 1, 1, 14, 14, STONE_HI, STONE_DARK)
    for i in (3, 6, 9, 12):
        hline(img, 3, 12, i, STONE_SHADOW)
        vline(img, i, 3, 12, STONE_SHADOW)
    for cx in (4, 7, 10):
        for cy in (4, 7, 10):
            fill(img, cx, cy, cx + 1, cy + 1, STONE_DARK)
            put(img, cx, cy, STONE_BASE)
    return img


@texture("block/basic_quantum_crafter_chassis")
def basic_quantum_crafter_chassis() -> Image.Image:
    """The floor and ceiling inside: plain cobble."""
    return stone_cobble("basic_quantum_crafter_chassis")


@texture("block/basic_quantum_crafter_core")
def basic_quantum_crafter_core() -> Image.Image:
    """
    Only rows 3 and 12 are ever seen: the bands between the slabs. One azure pip on each; the
    violet it wore read as contamination on a basic machine.
    """
    img = stone_noise("basic_quantum_crafter_core")
    for row in (3, 12):
        hline(img, 0, 15, row, STONE_SHADOW)
        put(img, 7, row, FLUX)
        put(img, 8, row, FLUX_CORE)
    return img


def _exciter_side(lit: bool) -> Image.Image:
    """A furnace-like face with a small coil window: two windings, hot at the centre when running."""
    img = low_side("quantum_exciter")
    window(img, 5, 5, 10, 10)
    for x in (6, 9):
        vline(img, x, 6, 9, FLUX_DIM if lit else FLUX_OFF)
    vline(img, 7, 6, 9, FLUX if lit else FLUX_OFF)
    vline(img, 8, 6, 9, FLUX if lit else FLUX_OFF)
    if lit:
        put(img, 7, 7, FLUX_CORE)
        put(img, 8, 8, FLUX_CORE)
    return img


def _low_top(name: str, pip: RGBA | None, core: RGBA | None) -> Image.Image:
    img = smooth_stone(name)
    fill(img, 6, 6, 9, 9, STONE_SHADOW)
    fill(img, 7, 7, 8, 8, LENS_DARK)
    if pip:
        fill(img, 7, 7, 8, 8, pip)
    if core:
        put(img, 7, 7, core)
    return img


@texture("block/quantum_exciter")
def quantum_exciter() -> Image.Image:
    return _exciter_side(False)


@texture("block/quantum_exciter_active")
def quantum_exciter_active() -> Image.Image:
    return _exciter_side(True)


@texture("block/quantum_exciter_top")
def quantum_exciter_top() -> Image.Image:
    return _low_top("quantum_exciter_top", FLUX_OFF, None)


@texture("block/quantum_exciter_top_active")
def quantum_exciter_top_active() -> Image.Image:
    return _low_top("quantum_exciter_top", FLUX, FLUX_CORE)


def _containment_side(lit: bool) -> Image.Image:
    """A small cell window with a violet crystal held in it: the payload, not the machine."""
    img = low_side("basic_anomaly_siphon")
    window(img, 5, 4, 10, 11)
    for x, y, colour in ((7, 9, VIOLET), (8, 9, VIOLET), (7, 8, VIOLET), (8, 7, CRYSTAL), (8, 8, VIOLET),
                         (6, 10, MITE), (9, 10, MITE), (7, 10, VIOLET), (8, 10, VIOLET), (8, 6, HIGHLIGHT)):
        put(img, x, y, colour if lit else lerp(colour, VOID, 0.5))
    if lit:
        hline(img, 6, 9, 4, FLUX_DIM)
    return img


@texture("block/basic_anomaly_siphon")
def basic_anomaly_siphon() -> Image.Image:
    return _containment_side(False)


@texture("block/basic_anomaly_siphon_active")
def basic_anomaly_siphon_active() -> Image.Image:
    return _containment_side(True)


@texture("block/basic_anomaly_siphon_top")
def basic_anomaly_siphon_top() -> Image.Image:
    return _low_top("basic_anomaly_siphon_top", MITE, None)


@texture("block/basic_anomaly_siphon_top_active")
def basic_anomaly_siphon_top_active() -> Image.Image:
    return _low_top("basic_anomaly_siphon_top", VIOLET, HIGHLIGHT)


# ---- Mid tier: deepslate, accents inside the face ----

DEEPSLATE_MID = hexc("#3a3a42")


def deepslate_grain(name: str) -> Image.Image:
    """Vanilla deepslate's horizontal grain: short runs of tone along each row."""
    rng = rng_for(name)
    img = canvas()
    for y in range(16):
        x = 0
        while x < 16:
            run = rng.randrange(2, 6)
            colour = rng.choices([DEEP_SEAM, DEEPSLATE, DEEPSLATE_MID, TILE], [2, 7, 5, 2])[0]
            hline(img, x, min(15, x + run - 1), y, colour)
            x += run
    return img


def polished_deepslate(name: str) -> Image.Image:
    """Polished deepslate: a flat face, lit rim on top and left, dark rim below and right."""
    img = canvas()
    speckle(img, rng_for(name), [DEEPSLATE, DEEPSLATE_MID, DEEPSLATE_MID, TILE], [3, 8, 6, 1])
    rect(img, 0, 0, 15, 15, DEEP_SEAM)
    hline(img, 1, 14, 1, TILE)
    vline(img, 1, 1, 14, TILE)
    return img


def mid_face(name: str) -> Image.Image:
    """Deepslate grain in a dark rim. The azure goes inside the face, never on the frame."""
    img = deepslate_grain(name)
    rect(img, 0, 0, 15, 15, DEEP_SEAM)
    return img


@texture("block/machine_bottom_mid")
def machine_bottom_mid() -> Image.Image:
    return polished_deepslate("machine_bottom_mid")


@texture("block/quantum_crafter_chassis")
def quantum_crafter_chassis() -> Image.Image:
    """The floor and ceiling inside the crafter: tiles with an unpowered cross of trace."""
    img = deepslate_tiles("quantum_crafter_chassis")
    trace(img, [(1, 8), (14, 8)], FLUX_OFF)
    trace(img, [(8, 1), (8, 14)], FLUX_OFF)
    put(img, 8, 8, FLUX_DIM)
    return img


@texture("block/quantum_crafter_core")
def quantum_crafter_core() -> Image.Image:
    """The two lit bands between the slabs (rows 3 and 12): azure with a hot pip every four pixels."""
    img = deepslate_grain("quantum_crafter_core")
    for row in (3, 12):
        hline(img, 0, 15, row, FLUX)
        for x in range(1, 16, 4):
            put(img, x, row, FLUX_CORE)
    return img


def _suppressor_side(lit: bool) -> Image.Image:
    """Two small chevrons pressing in on a slot: the field being pushed down."""
    img = mid_face("flux_suppressor")
    colour = FLUX if lit else FLUX_OFF
    for step in range(2):
        put(img, 4 + step, 6 + step, colour)
        put(img, 4 + step, 9 - step, colour)
        put(img, 11 - step, 6 + step, colour)
        put(img, 11 - step, 9 - step, colour)
    fill(img, 7, 5, 8, 10, DEEP_SEAM)
    vline(img, 7, 7, 8, FLUX_CORE if lit else FLUX_DIM)
    return img


def _suppressor_top(lit: bool) -> Image.Image:
    """A damper grille over the fragment chamber."""
    img = polished_deepslate("flux_suppressor_top")
    fill(img, 5, 5, 10, 10, DEEP_SEAM)
    for x in (6, 8, 10):
        vline(img, x - 0, 6, 9, DEEPSLATE_MID)
    put(img, 7, 7, FLUX_CORE if lit else FLUX_OFF)
    return img


@texture("block/flux_suppressor")
def flux_suppressor() -> Image.Image:
    return _suppressor_side(False)


@texture("block/flux_suppressor_active")
def flux_suppressor_active() -> Image.Image:
    return _suppressor_side(True)


@texture("block/flux_suppressor_top")
def flux_suppressor_top() -> Image.Image:
    return _suppressor_top(False)


@texture("block/flux_suppressor_top_active")
def flux_suppressor_top_active() -> Image.Image:
    return _suppressor_top(True)


def _quantum_observation_chamber(lit: bool) -> Image.Image:
    """The low chamber's viewing port grown into a lens: a ring of azure round dark glass, with a pupil
    that burns while it observes."""
    img = mid_face("quantum_observation_chamber")
    ring = FLUX if lit else FLUX_OFF
    fill(img, 5, 5, 10, 10, LENS_DARK)
    hline(img, 6, 9, 4, ring)
    hline(img, 6, 9, 11, ring)
    vline(img, 4, 6, 9, ring)
    vline(img, 11, 6, 9, ring)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        put(img, x, y, ring)
    fill(img, 7, 7, 8, 8, FLUX if lit else FLUX_OFF)
    put(img, 7, 7, FLUX_WHITE if lit else FLUX_DIM)
    return img


@texture("block/quantum_observation_chamber")
def quantum_observation_chamber() -> Image.Image:
    return _quantum_observation_chamber(False)


@texture("block/quantum_observation_chamber_lit")
def quantum_observation_chamber_lit() -> Image.Image:
    return _quantum_observation_chamber(True)


def _materialiser(lit: bool) -> Image.Image:
    """A dark crucible where a violet cloud of unrealised matter closes in on an azure core: what it
    will be, chosen. The core burns while it works."""
    img = mid_face("materialiser")
    fill(img, 4, 4, 11, 11, LENS_DARK)
    # The cloud: violet specks round the rim, drawn in towards the middle.
    for x, y in ((4, 5), (5, 4), (10, 4), (11, 6), (11, 10), (9, 11), (5, 11), (4, 9)):
        put(img, x, y, VIOLET if lit else MITE)
    for x, y in ((6, 5), (9, 6), (10, 9), (6, 10), (5, 7)):
        put(img, x, y, CRYSTAL if lit else VOID)
    # The core: a small azure diamond, bright when it's making something.
    core = FLUX if lit else FLUX_OFF
    for x, y in ((7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)):
        put(img, x, y, core)
    fill(img, 7, 7, 8, 8, FLUX_CORE if lit else FLUX_DIM)
    if lit:
        put(img, 7, 7, FLUX_WHITE)
    return img


@texture("block/materialiser")
def materialiser() -> Image.Image:
    return _materialiser(False)


@texture("block/materialiser_lit")
def materialiser_lit() -> Image.Image:
    return _materialiser(True)


@texture("block/entangled_dock_side")
def entangled_dock_side() -> Image.Image:
    """A trace climbing the column to the cradle, where the entangled item floats."""
    img = mid_face("entangled_dock_side")
    trace(img, [(8, 14), (8, 3)], FLUX_DIM)
    put(img, 8, 3, FLUX_CORE)
    put(img, 8, 9, FLUX)
    return img


@texture("block/entangled_dock_top")
def entangled_dock_top() -> Image.Image:
    """The cradle: a ring of azure the entangled item turns over."""
    img = polished_deepslate("entangled_dock_top")
    for x, y in ((6, 4), (7, 4), (8, 4), (9, 4), (6, 11), (7, 11), (8, 11), (9, 11),
                 (4, 6), (4, 7), (4, 8), (4, 9), (11, 6), (11, 7), (11, 8), (11, 9),
                 (5, 5), (10, 5), (5, 10), (10, 10)):
        put(img, x, y, FLUX)
    fill(img, 7, 7, 8, 8, FLUX_CORE)
    return img


def _chevrons_up(img: Image.Image, top: int, rows: int, lit: bool) -> None:
    """Azure chevrons pointing up, stacked from {@code top}: flux being raised."""
    colour = FLUX if lit else FLUX_OFF
    for row in range(rows):
        y = top + row * 3
        for step in range(4):
            put(img, 7 - step, y + step, colour)
            put(img, 8 + step, y + step, colour)
    if lit:
        put(img, 7, top, FLUX_CORE)
        put(img, 8, top, FLUX_CORE)


def _funnel_down(img: Image.Image, top: int, lit: bool) -> None:
    """A violet funnel narrowing downward to a point: anomaly being drawn in."""
    colour = VIOLET if lit else MITE
    hline(img, 3, 12, top, colour)
    for row in range(1, 4):
        put(img, 3 + row, top + row, colour)
        put(img, 12 - row, top + row, colour)
    fill(img, 7, top + 4, 8, top + 5, CRYSTAL if lit else MITE)


def _flux_maintainer(lit: bool) -> Image.Image:
    img = mid_face("flux_maintainer")
    _chevrons_up(img, 3, 3, lit)
    return img


def _anomaly_siphon(lit: bool) -> Image.Image:
    img = mid_face("anomaly_siphon")
    _funnel_down(img, 4, lit)
    put(img, 7, 11, VOID)
    put(img, 8, 11, VOID)
    return img


def _field_regulator(lit: bool) -> Image.Image:
    img = mid_face("field_regulator")
    _chevrons_up(img, 2, 1, lit)
    hline(img, 3, 12, 7, FLUX_DIM if lit else DEEP_SEAM)
    _funnel_down(img, 9, lit)
    return img


@texture("block/flux_maintainer")
def flux_maintainer() -> Image.Image:
    return _flux_maintainer(False)


@texture("block/flux_maintainer_active")
def flux_maintainer_active() -> Image.Image:
    return _flux_maintainer(True)


@texture("block/anomaly_siphon")
def anomaly_siphon() -> Image.Image:
    return _anomaly_siphon(False)


@texture("block/anomaly_siphon_active")
def anomaly_siphon_active() -> Image.Image:
    return _anomaly_siphon(True)


@texture("block/field_regulator")
def field_regulator() -> Image.Image:
    return _field_regulator(False)


@texture("block/field_regulator_active")
def field_regulator_active() -> Image.Image:
    return _field_regulator(True)


AMBER = hexc("#f0b429")


def _field_monitor(alert: bool) -> Image.Image:
    """A little map: a 3×3 of chunks in a dark screen, the middle one lit, one corner alarmed."""
    img = mid_face("field_monitor")
    fill(img, 3, 3, 12, 12, LENS_DARK)
    for row in range(3):
        for col in range(3):
            x, y = 4 + col * 3, 4 + row * 3
            colour = FLUX if (row, col) == (1, 1) else FLUX_DIM if (row + col) % 2 == 0 else FLUX_OFF
            fill(img, x, y, x + 1, y + 1, colour)
    put(img, 5, 5, FLUX_CORE)
    if alert:
        fill(img, 10, 4, 11, 5, AMBER)
    hline(img, 3, 12, 13, DEEP_SEAM)
    return img


@texture("block/field_monitor")
def field_monitor() -> Image.Image:
    return _field_monitor(False)


@texture("block/field_monitor_alert")
def field_monitor_alert() -> Image.Image:
    return _field_monitor(True)


def _zeno_side(lit: bool) -> Image.Image:
    """An hourglass whose sand has stopped: the watched pot that never boils."""
    img = mid_face("zeno_field_controller")
    glass = FLUX if lit else FLUX_OFF
    sand = FLUX_CORE if lit else FLUX_DIM
    hline(img, 4, 11, 3, glass)
    hline(img, 4, 11, 12, glass)
    for step in range(4):
        put(img, 4 + step, 4 + step, glass)
        put(img, 11 - step, 4 + step, glass)
        put(img, 4 + step, 11 - step, glass)
        put(img, 11 - step, 11 - step, glass)
    # Sand held in the top bulb and the neck, none falling.
    hline(img, 6, 9, 5, sand)
    hline(img, 7, 8, 6, sand)
    hline(img, 6, 9, 11, DEEPSLATE_MID)
    return img


def _zeno_top(lit: bool) -> Image.Image:
    """A dial with a single hand, stopped at twelve."""
    img = polished_deepslate("zeno_field_controller_top")
    ring = FLUX if lit else FLUX_OFF
    for x, y in ((6, 3), (7, 3), (8, 3), (9, 3), (6, 12), (7, 12), (8, 12), (9, 12),
                 (3, 6), (3, 7), (3, 8), (3, 9), (12, 6), (12, 7), (12, 8), (12, 9),
                 (4, 5), (5, 4), (10, 4), (11, 5), (4, 10), (5, 11), (10, 11), (11, 10)):
        put(img, x, y, ring)
    vline(img, 7, 5, 8, FLUX_CORE if lit else FLUX_DIM)
    put(img, 7, 8, TILE)
    return img


@texture("block/zeno_field_controller")
def zeno_field_controller() -> Image.Image:
    return _zeno_side(False)


@texture("block/zeno_field_controller_active")
def zeno_field_controller_active() -> Image.Image:
    return _zeno_side(True)


@texture("block/zeno_field_controller_top")
def zeno_field_controller_top() -> Image.Image:
    return _zeno_top(False)


@texture("block/zeno_field_controller_top_active")
def zeno_field_controller_top_active() -> Image.Image:
    return _zeno_top(True)


def _projector_side(lit: bool) -> Image.Image:
    """
    Laid out for the pedestal model, whose faces read this by height: rows 13–15 the base slab,
    4–12 the column (columns 3–12) and 0–3 the capital (1–14). One emitter slit runs up the column.
    """
    img = deepslate_grain("decoherence_projector_side")
    # Base slab: a polished band.
    fill(img, 0, 13, 15, 15, DEEPSLATE_MID)
    hline(img, 0, 15, 13, TILE)
    hline(img, 0, 15, 15, DEEP_SEAM)
    # Column: grooved edges.
    vline(img, 3, 4, 12, DEEP_SEAM)
    vline(img, 12, 4, 12, DEEP_SEAM)
    vline(img, 4, 4, 12, TILE)
    # Capital: polished, lit along its top, shadowed underneath.
    fill(img, 0, 0, 15, 3, DEEPSLATE_MID)
    hline(img, 0, 15, 0, TILE)
    hline(img, 0, 15, 3, DEEP_SEAM)
    # The slit.
    fill(img, 7, 5, 8, 11, DEEP_SEAM)
    vline(img, 7, 6, 10, FLUX_DIM if lit else FLUX_OFF)
    vline(img, 8, 6, 10, FLUX if lit else FLUX_OFF)
    if lit:
        put(img, 8, 6, FLUX_CORE)
    return img


def _projector_top(lit: bool) -> Image.Image:
    """The capital's top (1–14) with the orb's socket; the base slab's rim shows round the edge."""
    img = polished_deepslate("decoherence_projector_top")
    fill(img, 5, 5, 10, 10, DEEP_SEAM)
    rect(img, 6, 6, 9, 9, FLUX_DIM if lit else FLUX_OFF)
    fill(img, 7, 7, 8, 8, FLUX_CORE if lit else FLUX_DIM)
    return img


@texture("block/decoherence_projector_side")
def decoherence_projector_side() -> Image.Image:
    return _projector_side(False)


@texture("block/decoherence_projector_side_active")
def decoherence_projector_side_active() -> Image.Image:
    return _projector_side(True)


@texture("block/decoherence_projector_top")
def decoherence_projector_top() -> Image.Image:
    return _projector_top(False)


@texture("block/decoherence_projector_top_active")
def decoherence_projector_top_active() -> Image.Image:
    return _projector_top(True)


@texture("block/decoherence_projector_bottom")
def decoherence_projector_bottom() -> Image.Image:
    return polished_deepslate("decoherence_projector_bottom")


def _laser_side(lit: bool) -> Image.Image:
    """
    Laid out for the laser housing pointing up, whose faces read this by height: rows 0–1 the muzzle
    (columns 5–10), 2–4 the top plate, 5–12 the corner posts (1–3 and 12–14) round the window, 13–15
    the base plate. Seen sideways the muzzle is at the left or right; the texture only cares for height.
    """
    img = deepslate_grain("harvest_laser_side")
    # Muzzle collar: polished, with an azure band round the lens.
    fill(img, 5, 0, 10, 1, DEEPSLATE_MID)
    hline(img, 5, 10, 1, FLUX if lit else FLUX_OFF)
    # Top and base plates: polished bands, lit along their top edge.
    for top, bottom in ((2, 4), (13, 15)):
        fill(img, 0, top, 15, bottom, DEEPSLATE_MID)
        hline(img, 0, 15, top, TILE)
        hline(img, 0, 15, bottom, DEEP_SEAM)
    # Posts: grooved, with an azure pip each.
    for x in (1, 12):
        fill(img, x, 5, x + 2, 12, DEEPSLATE)
        vline(img, x, 5, 12, TILE)
        vline(img, x + 2, 5, 12, DEEP_SEAM)
        put(img, x + 1, 8, FLUX_CORE if lit else FLUX_DIM)
    return img


def _laser_lens(lit: bool) -> Image.Image:
    """The muzzle's face (columns 4–11): a steel rim round an azure ring, the bore (6–9) cut through."""
    img = polished_deepslate("harvest_laser_lens")
    fill(img, 4, 4, 11, 11, EDGE)
    rect(img, 5, 5, 10, 10, FLUX if lit else FLUX_OFF)
    rect(img, 4, 4, 11, 11, TILE)
    if lit:
        for x, y in ((5, 5), (10, 10)):
            put(img, x, y, FLUX_CORE)
    return img


def _laser_top() -> Image.Image:
    """The top plate round the muzzle, and the base plate's top seen through the window."""
    img = polished_deepslate("harvest_laser_top")
    rect(img, 4, 4, 11, 11, DEEP_SEAM)
    return img


@texture("block/harvest_laser_side")
def harvest_laser_side() -> Image.Image:
    return _laser_side(False)


@texture("block/harvest_laser_side_active")
def harvest_laser_side_active() -> Image.Image:
    return _laser_side(True)


@texture("block/harvest_laser_lens")
def harvest_laser_lens() -> Image.Image:
    return _laser_lens(False)


@texture("block/harvest_laser_lens_active")
def harvest_laser_lens_active() -> Image.Image:
    return _laser_lens(True)


@texture("block/harvest_laser_top")
def harvest_laser_top() -> Image.Image:
    return _laser_top()


@texture("block/harvest_laser_inner")
def harvest_laser_inner() -> Image.Image:
    """Inside the window: dark, so the orb carries it."""
    img = canvas(fill=DEEP_SEAM)
    speckle(img, rng_for("harvest_laser_inner"), [DEEP_SEAM, DEEPSLATE], [5, 1])
    return img


def _ring_face(lit: bool) -> Image.Image:
    """
    The ring seen along its axis: an octagon of bars (outer 2–13, opening 4–11, the rest cut away) in polished deepslate,
    azure studs at the middle of each bar and at its corners. Read by the bars' faces only.
    """
    img = polished_deepslate("rift_lens")
    stud = FLUX_CORE if lit else FLUX_DIM
    hot = FLUX_WHITE if lit else FLUX
    # The opening's lip, on the bars' inner edges.
    rect(img, 3, 3, 12, 12, DEEP_SEAM)
    # Middle studs, a hot pixel and a lit one.
    for a, b in (((7, 2), (8, 2)), ((7, 13), (8, 13)), ((2, 7), (2, 8)), ((13, 7), (13, 8))):
        put(img, *a, hot)
        put(img, *b, stud)
    # Corner studs where the bars meet.
    for x, y in ((4, 2), (11, 2), (4, 13), (11, 13), (2, 4), (13, 4), (2, 11), (13, 11)):
        put(img, x, y, stud)
    return img


@texture("block/rift_lens")
def rift_lens() -> Image.Image:
    return _ring_face(False)


@texture("block/rift_lens_open")
def rift_lens_open() -> Image.Image:
    return _ring_face(True)


@texture("block/rift_lens_side")
def rift_lens_side() -> Image.Image:
    """The bars' edges and the foot: deepslate grain with a polished band."""
    img = deepslate_grain("rift_lens_side")
    hline(img, 0, 15, 6, TILE)
    hline(img, 0, 15, 9, DEEP_SEAM)
    return img


@texture("block/rift_lens_collar")
def rift_lens_collar() -> Image.Image:
    """The collar a lens seats against hall glass with: polished, azure studs where it meets the pane."""
    img = polished_deepslate("rift_lens_collar")
    for x, y in ((1, 1), (7, 1), (14, 1), (1, 7), (14, 7), (1, 14), (7, 14), (14, 14)):
        put(img, x, y, FLUX)
    put(img, 7, 1, FLUX_CORE)
    put(img, 7, 14, FLUX_CORE)
    return img


@texture("block/quantum_placeholder")
def quantum_placeholder() -> Image.Image:
    """Stand-in for a machine without art yet: a dim glyph on deepslate."""
    img = mid_face("quantum_placeholder")
    for x, y in ((6, 4), (7, 4), (8, 4), (9, 5), (9, 6), (8, 7), (7, 8), (7, 9), (7, 11)):
        put(img, x, y, FLUX_DIM)
    return img


@texture("block/quantum_containment")
def quantum_containment() -> Image.Image:
    """
    The containment field block (cutout): clear, with an azure edge and corner brackets, so a wall
    of it reads as a lattice of field rather than as glass.
    """
    img = canvas()
    rect(img, 0, 0, 15, 15, FLUX_DIM)
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(img, x, y, FLUX_CORE)
    for cx, cy, sx, sy in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        put(img, cx + sx, cy, FLUX)
        put(img, cx, cy + sy, FLUX)
    put(img, 7, 7, FLUX_DIM)
    put(img, 8, 8, FLUX_DIM)
    return img


# ---- High tier: navy obsidian and full circuits ----

@texture("block/quantum_foundry_base")
def quantum_foundry_base() -> Image.Image:
    """The underside of every high-tier part: a plain raised panel, no light."""
    img = high_face("quantum_foundry_base")
    bevel(img, 1, 1, 14, 14, PANEL, SEAM)
    return img


def _plinth_frieze(name: str, corner: bool) -> Image.Image:
    """
    The outer face of a ground ring block. Painted as the north face: seen from outside, the right
    edge of the image is the west end, which is where a north-west corner turns. The model mirrors
    it onto the corner's west face, so the turn lands on the corner from both sides.
    """
    img = high_face(name)
    hline(img, 0, 15, 1, FLUX)
    hline(img, 0, 15, 13, FLUX_OFF)
    slots = (1, 6) if corner else (1, 6, 11)
    for i, x in enumerate(slots):
        rune(img, x + 1, 5, i + (3 if corner else 0), FLUX if x == 6 and not corner else FLUX_DIM)
    if corner:
        vline(img, 14, 1, 13, FLUX)
        put(img, 14, 1, FLUX_CORE)
        put(img, 14, 13, FLUX_DIM)
        rune(img, 10, 5, 5, BRIDGE)
    return img


@texture("block/quantum_foundry_plinth_side_edge_active")
def plinth_side_edge_active() -> Image.Image:
    return _plinth_frieze("plinth_side_edge", False)


@texture("block/quantum_foundry_plinth_side_corner_active")
def plinth_side_corner_active() -> Image.Image:
    return _plinth_frieze("plinth_side_corner", True)


@texture("block/quantum_foundry_plinth_side_active")
def plinth_side_active() -> Image.Image:
    """Inner faces, and a formed plinth's plain sides: one dim line along the top, so rings tile."""
    img = high_face("plinth_side_active")
    hline(img, 0, 15, 1, FLUX_DIM)
    return img


@texture("block/quantum_foundry_plinth_cap_side_edge_active")
def plinth_cap_side_edge_active() -> Image.Image:
    """The cap's frieze is the ground's turned upside down, so the hall is bracketed top and bottom."""
    return _plinth_frieze("plinth_cap_side_edge", False).transpose(Image.FLIP_TOP_BOTTOM)


@texture("block/quantum_foundry_plinth_cap_side_corner_active")
def plinth_cap_side_corner_active() -> Image.Image:
    return _plinth_frieze("plinth_cap_side_corner", True).transpose(Image.FLIP_TOP_BOTTOM)


@texture("block/quantum_foundry_plinth_cap_top_edge_active")
def plinth_cap_top_edge_active() -> Image.Image:
    """Painted north: trim along the outer (top) edge, a violet-bridge rune; the inner edge stays navy."""
    img = high_face("plinth_cap_top_edge")
    hline(img, 0, 15, 1, FLUX)
    hline(img, 0, 15, 3, FLUX_OFF)
    rune(img, 6, 6, 1, BRIDGE)
    put(img, 7, 3, FLUX_DIM)
    return img


@texture("block/quantum_foundry_plinth_cap_top_corner_active")
def plinth_cap_top_corner_active() -> Image.Image:
    """Painted north-west: trims along the top and left, meeting in a lit junction."""
    img = high_face("plinth_cap_top_corner")
    hline(img, 1, 15, 1, FLUX)
    vline(img, 1, 1, 15, FLUX)
    hline(img, 3, 15, 3, FLUX_OFF)
    vline(img, 3, 3, 15, FLUX_OFF)
    put(img, 1, 1, FLUX_CORE)
    put(img, 3, 3, FLUX_DIM)
    rune(img, 8, 8, 3, BRIDGE)
    return img


@texture("block/quantum_foundry_plinth_cap_top_centre_active")
def plinth_cap_top_centre_active() -> Image.Image:
    """The aperture the mirror beacon leaves through: void at the heart, violet bridge, azure ring."""
    img = high_face("plinth_cap_top_centre")
    rect(img, 3, 3, 12, 12, FLUX_DIM)
    for x, y in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):
        put(img, x, y, FLUX)
    fill(img, 5, 5, 10, 10, BRIDGE)
    fill(img, 6, 6, 9, 9, VOID)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, x, y, FLUX_CORE)
    return img


def _cap_bottom(name: str) -> Image.Image:
    img = high_face(name)
    bevel(img, 1, 1, 14, 14, SEAM, PANEL)
    return img


@texture("block/quantum_foundry_plinth_cap_bottom_edge_active")
def plinth_cap_bottom_edge_active() -> Image.Image:
    return _cap_bottom("plinth_cap_bottom_edge")


@texture("block/quantum_foundry_plinth_cap_bottom_corner_active")
def plinth_cap_bottom_corner_active() -> Image.Image:
    return _cap_bottom("plinth_cap_bottom_corner")


@texture("block/quantum_foundry_plinth_cap_bottom_centre_active")
def plinth_cap_bottom_centre_active() -> Image.Image:
    """The cell's ceiling: the field emitter that holds the occupant, a violet ring on navy."""
    img = _cap_bottom("plinth_cap_bottom_centre")
    rect(img, 4, 4, 11, 11, BRIDGE)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(img, x, y, VIOLET)
    fill(img, 7, 7, 8, 8, MITE)
    return img


@texture("block/quantum_attuned_glass_pillar")
def quantum_attuned_glass_pillar() -> Image.Image:
    """
    The 4px corner pillar of a formed glass shell reads columns 0–3 and 12–15. A navy post with a
    dim azure line up each visible face, no longer a copy of the Foundry pillar.
    """
    img = navy_stone("quantum_attuned_glass_pillar")
    for x0 in (0, 12):
        vline(img, x0, 0, 15, SEAM)
        vline(img, x0 + 3, 0, 15, SEAM)
        vline(img, x0 + 1, 0, 15, FLUX_DIM)
        vline(img, x0 + 2, 0, 15, PANEL)
    return img


@texture("block/quantum_foundry_pillar_side")
def quantum_foundry_pillar_side() -> Image.Image:
    """The idle pillar: the lit pillar's circuit, embossed rather than lit."""
    return unpower(source("block/quantum_foundry_pillar_side_active"))


# The stabilised portal frame connects like chisel's connected textures: each face picks one of
# sixteen pieces by which of its four in-plane neighbours are frame. Bits: top 1, right 2, bottom 4,
# left 8, in the face's own texture orientation.
FRAME_TOP, FRAME_RIGHT, FRAME_BOTTOM, FRAME_LEFT = 1, 2, 4, 8


def frame_piece(mask: int) -> Image.Image:
    """
    One polished deepslate piece. Unconnected edges get a rim (seam outside, lit bevel on the top
    and left, shade on the bottom and right); connected edges run straight on into the neighbour.
    A 2px azure vein runs from the centre socket out to every connected edge, pulsing every four
    pixels at the same places on every piece, so it reads continuous round a frame and through its
    corners, T-junctions and crossings.
    """
    img = canvas()
    rng = rng_for("stabilised_portal_frame")
    for y in range(16):
        for x in range(16):
            put(img, x, y, rng.choices([DEEPSLATE, DEEPSLATE_MID, TILE], [3, 8, 1])[0])
    top, right, bottom, left = (bool(mask & bit) for bit in (FRAME_TOP, FRAME_RIGHT, FRAME_BOTTOM, FRAME_LEFT))

    # Veins first, so the rims draw over their ends at unconnected edges.
    if top:
        for x in (7, 8):
            vline(img, x, 0, 8, FLUX_OFF)
    if bottom:
        for x in (7, 8):
            vline(img, x, 7, 15, FLUX_OFF)
    if left:
        for y in (7, 8):
            hline(img, 0, 8, y, FLUX_OFF)
    if right:
        for y in (7, 8):
            hline(img, 7, 15, y, FLUX_OFF)
    for p in (1, 5, 11, 15):
        if top and p < 7:
            put(img, 7, p, FLUX_DIM)
        if bottom and p > 8:
            put(img, 8, p, FLUX_DIM)
        if left and p < 7:
            put(img, p, 8, FLUX_DIM)
        if right and p > 8:
            put(img, p, 7, FLUX_DIM)

    if not top:
        hline(img, 0, 15, 0, DEEP_SEAM)
        hline(img, 1 if not left else 0, 14 if not right else 15, 1, TILE)
    if not bottom:
        hline(img, 0, 15, 15, DEEP_SEAM)
        hline(img, 1 if not left else 0, 14 if not right else 15, 14, DEEPSLATE)
    if not left:
        vline(img, 0, 0, 15, DEEP_SEAM)
        vline(img, 1, 1 if not top else 0, 14 if not bottom else 15, TILE)
    if not right:
        vline(img, 15, 0, 15, DEEP_SEAM)
        vline(img, 14, 1 if not top else 0, 14 if not bottom else 15, DEEPSLATE)
    # Where two connected edges meet, the diagonal is usually not frame (a portal's inside corner):
    # a single seam pixel marks the joint.
    for connected, (x, y) in ((top and left, (0, 0)), (top and right, (15, 0)),
                              (bottom and left, (0, 15)), (bottom and right, (15, 15))):
        if connected:
            put(img, x, y, DEEP_SEAM)

    # The centre: a socket where the veins meet, ringed when nothing leaves it.
    if mask == 0:
        rect(img, 4, 4, 11, 11, DEEP_SEAM)
        rect(img, 5, 5, 10, 10, TILE)
    fill(img, 7, 7, 8, 8, FLUX_DIM)
    put(img, 7, 7, FLUX)
    return img


for _mask in range(16):
    OUTPUTS[f"block/stabilised_portal_frame_{_mask}"] = (lambda m: (lambda: frame_piece(m)))(_mask)


# Which neighbour each face's texture edges point at: (top, right, bottom, left). Follows the
# automatic UVs: north's left is east, south's is west, west's is north, east's is south; up is
# read with north at the top, down with south at the top.
FRAME_FACE_NEIGHBOURS = {
    "north": ("up", "west", "down", "east"),
    "south": ("up", "east", "down", "west"),
    "west": ("up", "south", "down", "north"),
    "east": ("up", "north", "down", "south"),
    "up": ("north", "east", "south", "west"),
    "down": ("south", "east", "north", "west"),
}
FRAME_DIRECTIONS = ("north", "east", "south", "west", "up", "down")


# ---- Superposition Pod and Unfolding Array (high tier) ----

def _circle(img: Image.Image, cx: float, cy: float, radius: float, colour: RGBA) -> None:
    """A 1px ring, drawn by stepping round it."""
    import math
    seen = set()
    for step in range(96):
        angle = step / 96 * math.tau
        x = int(round(cx + math.cos(angle) * radius))
        y = int(round(cy + math.sin(angle) * radius))
        if (x, y) not in seen and 0 <= x < 16 and 0 <= y < 16:
            seen.add((x, y))
            put(img, x, y, colour)


def _line(img: Image.Image, x0: int, y0: int, x1: int, y1: int, colour: RGBA) -> None:
    """A 1px line at any angle (Bresenham's)."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    error = dx + dy
    while True:
        put(img, x0, y0, colour)
        if x0 == x1 and y0 == y1:
            return
        doubled = 2 * error
        if doubled >= dy:
            error += dy
            x0 += sx
        if doubled <= dx:
            error += dx
            y0 += sy


def _cradle_top() -> Image.Image:
    """A square circuit round the middle, fed from each edge: the cradle carries the pod's power."""
    img = high_face("pod_cradle_top")
    rect(img, 4, 4, 11, 11, FLUX_DIM)
    for x, y in ((7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)):
        put(img, x, y, FLUX_CORE)
    trace(img, [(7, 1), (7, 3)], FLUX)
    trace(img, [(8, 12), (8, 14)], FLUX)
    trace(img, [(1, 8), (3, 8)], FLUX)
    trace(img, [(12, 7), (14, 7)], FLUX)
    fill(img, 6, 6, 9, 9, PANEL)
    fill(img, 7, 7, 8, 8, RAISED)
    return img


def _cradle_side() -> Image.Image:
    """Azure trim along the top edge, and a frieze of runes: the cradle's lettering."""
    img = high_face("pod_cradle_side")
    hline(img, 1, 14, 1, FLUX)
    put(img, 1, 1, FLUX_CORE)
    put(img, 14, 1, FLUX_CORE)
    hline(img, 1, 14, 4, PANEL_LINE)
    hline(img, 1, 14, 12, PANEL_LINE)
    for index, x in enumerate((2, 7, 11)):
        rune(img, x, 6, index + 1, BRIDGE if index != 1 else FLUX_DIM)
    return img


@texture("block/pod_cradle_top")
def pod_cradle_top() -> Image.Image:
    return _cradle_top()


@texture("block/pod_cradle_top_off")
def pod_cradle_top_off() -> Image.Image:
    return unpower(_cradle_top())


@texture("block/pod_cradle_side")
def pod_cradle_side() -> Image.Image:
    return _cradle_side()


@texture("block/pod_cradle_side_off")
def pod_cradle_side_off() -> Image.Image:
    return unpower(_cradle_side())


def _plating_top() -> Image.Image:
    """A step grille: where you step up into the pod. One azure bar across it."""
    img = high_face("pod_plating_top")
    for y in (3, 5, 10, 12):
        hline(img, 3, 12, y, PANEL_LINE)
        hline(img, 3, 12, y + 1, SEAM)
    hline(img, 2, 13, 7, FLUX_DIM)
    hline(img, 2, 13, 8, FLUX)
    put(img, 2, 8, FLUX_CORE)
    put(img, 13, 8, FLUX_CORE)
    return img


def _plating_side() -> Image.Image:
    img = high_face("pod_plating_side")
    hline(img, 1, 14, 1, FLUX)
    for x in (5, 10):
        vline(img, x, 3, 14, PANEL_LINE)
    return img


@texture("block/pod_plating_top")
def pod_plating_top() -> Image.Image:
    return _plating_top()


@texture("block/pod_plating_top_off")
def pod_plating_top_off() -> Image.Image:
    return unpower(_plating_top())


@texture("block/pod_plating_side")
def pod_plating_side() -> Image.Image:
    return _plating_side()


@texture("block/pod_plating_side_off")
def pod_plating_side_off() -> Image.Image:
    return unpower(_plating_side())


def _rescue_top(lit: bool) -> Image.Image:
    """A body held in a ring: the double that is kept for you."""
    img = high_face("pod_rescue_module_top")
    _circle(img, 7.5, 7.5, 5.5, FLUX if lit else PANEL)
    figure = CRYSTAL if lit else RAISED
    fill(img, 7, 4, 8, 5, HIGHLIGHT if lit else RAISED)
    fill(img, 7, 6, 8, 9, figure)
    put(img, 6, 7, figure)
    put(img, 9, 7, figure)
    put(img, 6, 10, figure)
    put(img, 9, 10, figure)
    put(img, 6, 11, VIOLET if lit else PANEL)
    put(img, 9, 11, VIOLET if lit else PANEL)
    return img


def _rescue_side(lit: bool) -> Image.Image:
    """A heartbeat running across it, violet: the one thing this module watches for."""
    img = high_face("pod_rescue_module_side")
    hline(img, 1, 14, 1, FLUX if lit else PANEL)
    beat = [(1, 9), (4, 9), (5, 7), (6, 12), (7, 4), (8, 11), (9, 9), (14, 9)]
    for (x0, y0), (x1, y1) in zip(beat, beat[1:]):
        _line(img, x0, y0, x1, y1, VIOLET if lit else PANEL_LINE)
    if lit:
        put(img, 7, 4, HIGHLIGHT)
        put(img, 6, 12, CRYSTAL)
    return img


@texture("block/pod_rescue_module_top")
def pod_rescue_module_top() -> Image.Image:
    return _rescue_top(True)


@texture("block/pod_rescue_module_top_off")
def pod_rescue_module_top_off() -> Image.Image:
    return _rescue_top(False)


@texture("block/pod_rescue_module_side")
def pod_rescue_module_side() -> Image.Image:
    return _rescue_side(True)


@texture("block/pod_rescue_module_side_off")
def pod_rescue_module_side_off() -> Image.Image:
    return _rescue_side(False)


def _module_top(name: str, lit: bool, emblem) -> Image.Image:
    """A module's top: its emblem in a ring, like the Rescue module's."""
    img = high_face(name)
    _circle(img, 7.5, 7.5, 5.5, FLUX if lit else PANEL)
    emblem(img, lit)
    return img


def _module_side(name: str, lit: bool, pattern) -> Image.Image:
    img = high_face(name)
    hline(img, 1, 14, 1, FLUX if lit else PANEL)
    pattern(img, lit)
    return img


def _plus(img: Image.Image, lit: bool) -> None:
    """Regeneration: a cross, hot in the middle."""
    colour = FLUX_CORE if lit else RAISED
    fill(img, 7, 4, 8, 11, colour)
    fill(img, 4, 7, 11, 8, colour)
    fill(img, 7, 7, 8, 8, FLUX_WHITE if lit else RAISED)


def _rising(img: Image.Image, lit: bool) -> None:
    """Regeneration's sides: chevrons rising, the body mending."""
    colour = FLUX if lit else PANEL_LINE
    for top in (4, 9):
        for step in range(4):
            put(img, 7 - step, top + step, colour)
            put(img, 8 + step, top + step, colour)
    if lit:
        put(img, 7, 4, FLUX_CORE)
        put(img, 8, 4, FLUX_CORE)


def _shield(img: Image.Image, lit: bool) -> None:
    """Hardening: a shield."""
    colour = FLUX_CORE if lit else RAISED
    hline(img, 5, 10, 4, colour)
    vline(img, 5, 4, 8, colour)
    vline(img, 10, 4, 8, colour)
    _line(img, 5, 8, 7, 11, colour)
    _line(img, 10, 8, 8, 11, colour)
    fill(img, 7, 6, 8, 8, FLUX if lit else PANEL)


def _lattice(img: Image.Image, lit: bool) -> None:
    """Hardening's sides: a close lattice, laid like plate armour."""
    for y in (4, 8, 12):
        hline(img, 1, 14, y, PANEL_LINE)
        for x in range(2 + (y // 4) % 2 * 2, 14, 4):
            put(img, x, y - 1, FLUX_DIM if lit else PANEL)


def _eye(img: Image.Image, lit: bool) -> None:
    """Ward: a watching eye, violet at its pupil."""
    colour = FLUX_CORE if lit else RAISED
    hline(img, 6, 9, 5, colour)
    hline(img, 6, 9, 10, colour)
    put(img, 5, 6, colour)
    put(img, 10, 6, colour)
    put(img, 5, 9, colour)
    put(img, 10, 9, colour)
    vline(img, 4, 7, 8, colour)
    vline(img, 11, 7, 8, colour)
    fill(img, 7, 7, 8, 8, VIOLET if lit else PANEL)


def _bars(img: Image.Image, lit: bool) -> None:
    """Ward's sides: bars, a fence of light."""
    for x in (3, 6, 9, 12):
        vline(img, x, 3, 14, FLUX_DIM if lit else PANEL_LINE)
        put(img, x, 3, FLUX_CORE if lit else PANEL)


def _grid(img: Image.Image, lit: bool) -> None:
    """Stash: a grid of held things."""
    for gx in (5, 7, 9):
        for gy in (5, 7, 9):
            put(img, gx + (gx > 7), gy + (gy > 7), (FLUX_CORE if (gx, gy) == (7, 7) else FLUX) if lit else RAISED)


def _hatch(img: Image.Image, lit: bool) -> None:
    """Stash's sides: a hatch with its latch."""
    rect(img, 3, 4, 12, 12, PANEL_LINE)
    hline(img, 6, 9, 8, FLUX if lit else PANEL)
    put(img, 7, 9, FLUX_CORE if lit else RAISED)
    put(img, 8, 9, FLUX_CORE if lit else RAISED)


def _bolt(img: Image.Image, lit: bool) -> None:
    """Charge: a lightning bolt."""
    colour = FLUX_CORE if lit else RAISED
    for x, y in ((9, 3), (8, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (7, 10), (6, 11), (6, 12)):
        put(img, x, y, colour)
    if lit:
        put(img, 7, 7, FLUX_WHITE)


def _cells(img: Image.Image, lit: bool) -> None:
    """Charge's sides: a battery's cells, filling from the bottom."""
    rect(img, 4, 3, 11, 13, PANEL_LINE)
    for index, y in enumerate((11, 9, 7, 5)):
        hline(img, 6, 9, y, (FLUX if index < 3 else FLUX_DIM) if lit else PANEL)


def _nodes(img: Image.Image, lit: bool) -> None:
    """Relay: a hub with three spokes out to its pods."""
    line = FLUX if lit else PANEL
    node = FLUX_CORE if lit else RAISED
    _line(img, 7, 7, 7, 3, line)
    _line(img, 7, 7, 4, 10, line)
    _line(img, 8, 8, 11, 10, line)
    for x, y in ((7, 3), (4, 10), (11, 10)):
        put(img, x, y, node)
    fill(img, 7, 7, 8, 8, FLUX_WHITE if lit else RAISED)


def _signal(img: Image.Image, lit: bool) -> None:
    """Relay's sides: arcs of a signal going out."""
    colour = FLUX if lit else PANEL_LINE
    put(img, 7, 12, FLUX_CORE if lit else RAISED)
    put(img, 8, 12, FLUX_CORE if lit else RAISED)
    for radius in (3, 6):
        for dx in range(-radius, radius + 1):
            dy = int(round((radius * radius - dx * dx) ** 0.5))
            if dy >= radius // 2:
                put(img, 7 + dx + (dx > 0), 12 - dy, colour)


def _homeward(img: Image.Image, lit: bool) -> None:
    """Recovery: an arrow curling back round into the middle, bringing something home."""
    line = FLUX if lit else PANEL
    for x, y in ((5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 5), (11, 6), (11, 7), (11, 8), (10, 9), (9, 10), (8, 10)):
        put(img, x, y, line)
    for x, y in ((4, 3), (4, 4), (4, 5), (5, 5)):
        put(img, x, y, FLUX_CORE if lit else RAISED)
    fill(img, 7, 7, 8, 8, VIOLET if lit else RAISED)


def _tracks(img: Image.Image, lit: bool) -> None:
    """Recovery's sides: a line running in from the edge to a hollow waiting for it."""
    hline(img, 1, 9, 8, FLUX_DIM if lit else PANEL_LINE)
    rect(img, 10, 6, 13, 10, FLUX if lit else PANEL_LINE)
    put(img, 9, 7, FLUX_DIM if lit else PANEL_LINE)
    put(img, 9, 9, FLUX_DIM if lit else PANEL_LINE)


for _name, _emblem, _pattern in (("regeneration", _plus, _rising), ("hardening", _shield, _lattice),
                                 ("ward", _eye, _bars), ("stash", _grid, _hatch), ("charge", _bolt, _cells),
                                 ("relay", _nodes, _signal), ("recovery", _homeward, _tracks)):
    for _lit in (True, False):
        _suffix = "" if _lit else "_off"
        texture(f"block/pod_{_name}_module_top{_suffix}")(
            (lambda n, e, l: lambda: _module_top(f"pod_{n}_module_top", l, e))(_name, _emblem, _lit))
        texture(f"block/pod_{_name}_module_side{_suffix}")(
            (lambda n, p, l: lambda: _module_side(f"pod_{n}_module_side", l, p))(_name, _pattern, _lit))


def _pod_disc(name: str, lit: bool) -> Image.Image:
    """The capsule's foot and crown: rings round a hot centre."""
    img = navy_stone(name)
    _circle(img, 7.5, 7.5, 6.5, FLUX_DIM if lit else PANEL_LINE)
    _circle(img, 7.5, 7.5, 3.5, FLUX if lit else PANEL)
    fill(img, 7, 7, 8, 8, FLUX_WHITE if lit else RAISED)
    return img


@texture("block/superposition_pod_disc")
def superposition_pod_disc() -> Image.Image:
    return _pod_disc("superposition_pod_disc", True)


@texture("block/superposition_pod_disc_off")
def superposition_pod_disc_off() -> Image.Image:
    return _pod_disc("superposition_pod_disc_off", False)


@texture("block/superposition_pod_frame")
def superposition_pod_frame() -> Image.Image:
    """The struts and rims: obsidian with a fine azure line running along them."""
    img = navy_stone("superposition_pod_frame")
    vline(img, 7, 0, 15, FLUX_DIM)
    vline(img, 8, 0, 15, FLUX)
    hline(img, 0, 15, 0, PANEL_LINE)
    hline(img, 0, 15, 15, SEAM)
    return img


@texture("block/superposition_pod_frame_off")
def superposition_pod_frame_off() -> Image.Image:
    return unpower(superposition_pod_frame())


@texture("block/superposition_pod_glass")
def superposition_pod_glass() -> Image.Image:
    """For the item only: the capsule's glass, faint, brighter at its edges."""
    img = canvas(fill=(180, 214, 255, 46))
    rect(img, 0, 0, 15, 15, (200, 226, 255, 120))
    vline(img, 3, 1, 14, (230, 240, 255, 110))
    return img


def _array_top(lit: bool) -> Image.Image:
    """The pad: a ring to stand in, with a rune at each quarter."""
    img = high_face("unfolding_array_top")
    _circle(img, 7.5, 7.5, 6.5, FLUX if lit else PANEL)
    _circle(img, 7.5, 7.5, 4.0, FLUX_DIM if lit else PANEL_LINE)
    for index, (x, y) in enumerate(((7, 1), (7, 10))):
        rune(img, x - 1, y, index + 3, BRIDGE)
    fill(img, 7, 7, 8, 8, FLUX_CORE if lit else RAISED)
    return img


@texture("block/unfolding_array_top")
def unfolding_array_top() -> Image.Image:
    return _array_top(True)


@texture("block/unfolding_array_side")
def unfolding_array_side() -> Image.Image:
    """The pad is 6px tall, so its sides show rows 10–15: a rune frieze under azure trim."""
    img = high_face("unfolding_array_side")
    hline(img, 1, 14, 10, FLUX)
    for index, x in enumerate((2, 6, 10)):
        rune(img, x, 11, index, BRIDGE if index % 2 == 0 else FLUX_DIM)
    return img


@texture("block/array_pylon")
def array_pylon() -> Image.Image:
    """The pylon's shaft: obsidian with a conduit climbing to its crystal."""
    img = navy_stone("array_pylon")
    vline(img, 7, 0, 15, FLUX_DIM)
    vline(img, 8, 0, 15, FLUX)
    for y in (3, 8, 13):
        put(img, 8, y, FLUX_CORE)
    return img


@texture("block/array_pylon_crystal")
def array_pylon_crystal() -> Image.Image:
    """An azure crystal, faceted: what reaches into the person on the pad."""
    rng = rng_for("array_pylon_crystal")
    img = canvas()
    speckle(img, rng, [FLUX_DIM, FLUX, FLUX, FLUX_CORE], [2, 5, 3, 2])
    for x, y in ((3, 2), (4, 3), (5, 4), (10, 9), (11, 10), (12, 11)):
        put(img, x, y, FLUX_WHITE)
    trace(img, [(0, 12), (6, 6), (6, 0)], FLUX_CORE)
    return img


@texture("item/tether")
def tether() -> Image.Image:
    """The Tether: a loop of azure cord caught at a violet knot, one end trailing off."""
    img = canvas()
    for x, y in ((6, 2), (7, 2), (8, 2), (9, 3), (10, 4), (10, 5), (10, 6), (9, 7), (8, 8), (7, 8), (6, 8),
                 (5, 7), (4, 6), (4, 5), (4, 4), (5, 3)):
        put(img, x, y, FLUX)
    for x, y in ((6, 3), (7, 3), (8, 3), (9, 4), (9, 5), (9, 6), (8, 7), (7, 7), (6, 7), (5, 6), (5, 5), (5, 4)):
        put(img, x, y, FLUX_DIM)
    fill(img, 6, 9, 8, 10, VIOLET)
    put(img, 7, 9, HIGHLIGHT)
    for step, (x, y) in enumerate(((7, 11), (6, 12), (6, 13), (5, 14), (4, 15))):
        put(img, x, y, FLUX if step % 2 == 0 else FLUX_DIM)
    put(img, 7, 2, FLUX_WHITE)
    return img


def write_frame_models() -> None:
    """The 64 connection states' models, and the blockstate that picks between them."""
    import json

    models = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "models", "block",
                          "stabilised_portal_frame")
    os.makedirs(models, exist_ok=True)
    variants = {}
    for state in range(64):
        connected = {d: bool(state >> i & 1) for i, d in enumerate(FRAME_DIRECTIONS)}
        textures = {}
        for face, (top, right, bottom, left) in FRAME_FACE_NEIGHBOURS.items():
            mask = ((FRAME_TOP if connected[top] else 0) | (FRAME_RIGHT if connected[right] else 0)
                    | (FRAME_BOTTOM if connected[bottom] else 0) | (FRAME_LEFT if connected[left] else 0))
            textures[face] = f"quantimium:block/stabilised_portal_frame_{mask}"
        textures["particle"] = "quantimium:block/stabilised_portal_frame_0"
        with open(os.path.join(models, f"c{state}.json"), "w") as handle:
            json.dump({"parent": "minecraft:block/cube", "textures": textures}, handle, indent=2)
            handle.write("\n")
        key = ",".join(f"{d}={str(connected[d]).lower()}" for d in FRAME_DIRECTIONS)
        variants[key] = {"model": f"quantimium:block/stabilised_portal_frame/c{state}"}
    blockstate = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "blockstates",
                              "stabilised_portal_frame.json")
    with open(blockstate, "w") as handle:
        json.dump({"variants": variants}, handle, indent=2)
        handle.write("\n")


def _superposition_top() -> Image.Image:
    """A lens over the catalyst, and the four points the ME network meets it."""
    img = high_face("me_superposition_crafter_top")
    fill(img, 5, 5, 10, 10, VOID)
    for x, y in ((6, 5), (9, 5), (5, 6), (10, 6), (5, 9), (10, 9), (6, 10), (9, 10)):
        put(img, x, y, FLUX)
    fill(img, 7, 7, 8, 8, FLUX_CORE)
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        put(img, x, y, FLUX_DIM)
    return img


@texture("block/me_superposition_crafter_top_active")
def me_superposition_crafter_top_active() -> Image.Image:
    return _superposition_top()


@texture("block/me_superposition_crafter_top")
def me_superposition_crafter_top() -> Image.Image:
    return unpower(_superposition_top())


def _superposition_frame() -> Image.Image:
    """Slab edges (rows 0-2 and 13-15) and pillar faces (columns 0-2 and 13-15), with a faint trace each."""
    img = high_face("me_superposition_crafter_frame")
    hline(img, 3, 12, 1, FLUX_DIM)
    hline(img, 3, 12, 14, FLUX_DIM)
    vline(img, 1, 3, 12, FLUX_DIM)
    vline(img, 14, 3, 12, FLUX_DIM)
    return img


@texture("block/me_superposition_crafter_frame_active")
def me_superposition_crafter_frame_active() -> Image.Image:
    return _superposition_frame()


@texture("block/me_superposition_crafter_frame")
def me_superposition_crafter_frame() -> Image.Image:
    """Off the network the traces go dark, so a glance tells a connected crafter from a loose one."""
    return unpower(_superposition_frame())


def _superposition_core(level: int) -> Image.Image:
    """
    A crossed core's face (CrossedCore, over each Rift Stabiliser): dark at level 0, a soft azure at
    1, bright at 2. Its edges are nearly solid and its faces thin, so it reads as a glassy crystal
    rather than a fog.
    """
    img = canvas()
    fills = [(VOID, SEAM, PANEL), (FLUX_OFF, FLUX_DIM, FLUX), (FLUX, FLUX_CORE, FLUX_WHITE)][level]
    face_alpha, edge_alpha = [(150, 230), (95, 220), (120, 240)][level]
    fill(img, 0, 0, 15, 15, fills[0])
    rect(img, 0, 0, 15, 15, fills[1])
    rect(img, 3, 3, 12, 12, fills[1])
    fill(img, 6, 6, 9, 9, fills[2])
    for y in range(16):
        for x in range(16):
            r, g, b, _ = img.getpixel((x, y))
            edge = x in (0, 15) or y in (0, 15)
            img.putpixel((x, y), (r, g, b, edge_alpha if edge else face_alpha))
    return img


@texture("block/me_superposition_crafter_port")
def me_superposition_crafter_port() -> Image.Image:
    """
    The plate on a side something plugs into (drawn from its middle 8x8): a dark socket with an azure
    rim, like the face of an ME device, so a cable meeting it looks connected.
    """
    img = high_face("me_superposition_crafter_port")
    fill(img, 4, 4, 11, 11, PANEL)
    rect(img, 4, 4, 11, 11, SEAM)
    rect(img, 5, 5, 10, 10, FLUX_DIM)
    fill(img, 6, 6, 9, 9, VOID)
    fill(img, 7, 7, 8, 8, FLUX)
    return img


@texture("block/rift_stabiliser_core_idle")
def rift_stabiliser_core_idle() -> Image.Image:
    """The tesseract over a stabiliser's cap: the Superposition Crafter's core, murky while idle."""
    return _superposition_core(0)


@texture("block/rift_stabiliser_core_lit")
def rift_stabiliser_core_lit() -> Image.Image:
    """... and burning while it beams at a rift."""
    return _superposition_core(2)


@texture("block/rift_anchor_top")
def rift_anchor_top() -> Image.Image:
    """The footing a rift stands on: a void socket with a violet seed, bracketed and fed in azure."""
    img = high_face("rift_anchor_top")
    fill(img, 5, 5, 10, 10, VOID)
    for x, y, colour in ((7, 8, VIOLET), (8, 7, VIOLET), (8, 8, CRYSTAL), (7, 7, MITE), (9, 6, MITE),
                         (6, 9, MITE), (8, 6, VIOLET)):
        put(img, x, y, colour)
    for cx, cy, sx, sy in ((4, 4, 1, 1), (11, 4, -1, 1), (4, 11, 1, -1), (11, 11, -1, -1)):
        put(img, cx, cy, FLUX_CORE)
        put(img, cx + sx, cy, FLUX)
        put(img, cx, cy + sy, FLUX)
    trace(img, [(8, 1), (8, 3)], FLUX_DIM)
    trace(img, [(7, 12), (7, 14)], FLUX_DIM)
    trace(img, [(1, 7), (3, 7)], FLUX_DIM)
    trace(img, [(12, 8), (14, 8)], FLUX_DIM)
    return img


@texture("block/rift_anchor_side")
def rift_anchor_side() -> Image.Image:
    """Traces climbing from the base and converging under the top edge, where the rift stands."""
    img = high_face("rift_anchor_side")
    trace(img, [(3, 14), (3, 6), (6, 6), (6, 2)], FLUX_DIM, FLUX)
    trace(img, [(12, 14), (12, 6), (9, 6), (9, 2)], FLUX_DIM, FLUX)
    hline(img, 5, 10, 1, BRIDGE)
    put(img, 7, 1, VIOLET)
    put(img, 8, 1, VIOLET)
    return img


@texture("block/rift_stabiliser_side")
def rift_stabiliser_side() -> Image.Image:
    """
    Laid out for the tapered model, whose faces read this by height: rows 13–15 are the base slab,
    8–12 the body (columns 2–13), 3–7 the neck (4–11) and 0–2 the cap (5–10). One conduit climbs the
    middle of every step, so it reads as a single line rising into the orb.
    """
    img = navy_stone("rift_stabiliser_side")
    hline(img, 0, 15, 13, PANEL_LINE)
    hline(img, 0, 15, 15, SEAM)
    for row in (8, 3):
        hline(img, 0, 15, row, PANEL_LINE)
    vline(img, 7, 1, 14, FLUX_DIM)
    vline(img, 8, 1, 14, FLUX)
    for y in (14, 10, 5):
        put(img, 8, y, FLUX_CORE)
    put(img, 8, 1, FLUX_WHITE)
    return img


@texture("block/rift_stabiliser_top")
def rift_stabiliser_top() -> Image.Image:
    """
    Up faces by footprint: the base ring (0–15), the body's shoulder (2–13), the neck's (4–11) and the
    cap (5–10), which holds the emitter socket under the orb.
    """
    img = navy_stone("rift_stabiliser_top")
    rect(img, 2, 2, 13, 13, PANEL_LINE)
    rect(img, 4, 4, 11, 11, PANEL_LINE)
    fill(img, 5, 5, 10, 10, hexc("#101a36"))
    rect(img, 5, 5, 10, 10, FLUX)
    fill(img, 7, 7, 8, 8, FLUX_CORE)
    put(img, 7, 7, FLUX_WHITE)
    return img


# ---- Anomaly: grown, jagged, oblivion purple ----

# Darker than the style guide's crystal swatches, which sat too close to amethyst in game. The
# bright end is kept for tips and glints only.
OBLIVION = [hexc("#120a1e"), hexc("#1f1236"), hexc("#2f1c52"), hexc("#452a78"), hexc("#5f3fa8")]


def crystal_block(name: str, cells: int = 8) -> Image.Image:
    """
    Amethyst-block structure in anomaly tones: angular crystal patches, each lit along its top and
    left and shadowed along its bottom and right, so the face reads as packed facets.
    """
    rng = rng_for(name)
    seeds = [(rng.randrange(16), rng.randrange(16), rng.choice([2, 3, 3, 4])) for _ in range(cells)]
    def owner(x: int, y: int) -> int:
        # Chebyshev distance gives the angular, faceted cell edges amethyst has.
        return min(range(len(seeds)), key=lambda i: max(min((x - seeds[i][0]) % 16, (seeds[i][0] - x) % 16),
                                                         min((y - seeds[i][1]) % 16, (seeds[i][1] - y) % 16)))
    cellmap = {(x, y): owner(x, y) for y in range(16) for x in range(16)}
    img = canvas()
    for (x, y), cell in cellmap.items():
        tone = seeds[cell][2]
        if cellmap[((x + 1) % 16, y)] != cell or cellmap[(x, (y + 1) % 16)] != cell:
            colour = OBLIVION[max(0, tone - 2)]
        elif cellmap[((x - 1) % 16, y)] != cell or cellmap[(x, (y - 1) % 16)] != cell:
            colour = OBLIVION[min(4, tone + 1)] if tone < 4 else VIOLET
        else:
            colour = OBLIVION[tone] if rng.random() < 0.85 else OBLIVION[tone - 1]
        put(img, x, y, colour)
    return img


@texture("block/anomalite_lattice")
def anomalite_lattice() -> Image.Image:
    """Four shards packed into a block: the crystal block, split into a 2×2 by void seams."""
    img = crystal_block("anomalite_lattice", 10)
    for i in (0, 8):
        hline(img, 0, 15, i, OBLIVION[0])
        vline(img, i, 0, 15, OBLIVION[0])
    for x, y in ((5, 2), (13, 3), (3, 11), (12, 10)):
        put(img, x, y, VIOLET)
    return img


# Budding amethyst's sockets are small dark crosses; these are the same, in void.
_SOCKETS = ((3, 3), (11, 4), (6, 9), (12, 12), (2, 13))


def _budding(lit: bool) -> Image.Image:
    img = crystal_block("budding_anomalite")
    for x, y in _SOCKETS:
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, x + dx, y + dy, OBLIVION[0])
        for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
            put(img, x + dx, y + dy, OBLIVION[1])
        if lit:
            put(img, x, y, VIOLET)
            put(img, x + 1, y, OBLIVION[4])
    return img


@texture("block/budding_anomalite")
def budding_anomalite() -> Image.Image:
    return _budding(False)


@texture("block/budding_anomalite_active")
def budding_anomalite_active() -> Image.Image:
    return _budding(True)


# ---- Items ----

def sprite(rows: Sequence[str], legend: Dict[str, RGBA]) -> Image.Image:
    img = canvas()
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            if cell != ".":
                put(img, x, y, legend[cell])
    return img


ANOMALY_LEGEND = {"o": VOID, "m": MITE, "b": BRIDGE, "v": VIOLET, "c": CRYSTAL, "h": HIGHLIGHT}


@texture("item/anomalite_shard")
def anomalite_shard() -> Image.Image:
    return sprite([
        "................",
        "................",
        "...........oo...",
        "..........ohco..",
        ".........ohcvo..",
        "........ohcvvo..",
        ".......ohcvvmo..",
        "......ohcvvmo...",
        ".....ohcvvmo....",
        "....ohcvvmo.....",
        "...ocvvvmo......",
        "...ovvmmo.......",
        "...ommmo........",
        "....ooo.........",
        "................",
        "................",
    ], ANOMALY_LEGEND)


@texture("item/anomalite_dust")
def anomalite_dust() -> Image.Image:
    return sprite([
        "................",
        "................",
        "................",
        "................",
        "........c.......",
        ".....h......c...",
        ".........v......",
        "...c...vcv..h...",
        "......vcvvv...c.",
        ".....vvmvcvv....",
        "....ovvmvvmvvo..",
        "...omvmmvmmvmmo.",
        "...ommmmmmmmmmo.",
        "....oooooooooo..",
        "................",
        "................",
    ], ANOMALY_LEGEND)


CELL_LEGEND = {"d": DEEP_SEAM, "t": TILE, "e": EDGE, "o": VOID, "v": VIOLET, "c": CRYSTAL, "h": HIGHLIGHT, "m": MITE}


@texture("item/anomalite_cell")
def anomalite_cell() -> Image.Image:
    """Anomalite shards sealed like fuel in a rod: steel caps, a glass window glowing violet."""
    return sprite([
        "................",
        ".....dddddd.....",
        ".....dteetd.....",
        ".....dttttd.....",
        ".....dddddd.....",
        ".....dmvchd.....",
        ".....dvvcvd.....",
        ".....dmvvcd.....",
        ".....dvcvvd.....",
        ".....dmvvhd.....",
        ".....dvhcvd.....",
        ".....dddddd.....",
        ".....dttttd.....",
        ".....dteetd.....",
        ".....dddddd.....",
        "................",
    ], CELL_LEGEND)


EMITTER_LEGEND = {"d": DEEP_SEAM, "t": TILE, "e": EDGE, "f": FLUX, "g": FLUX_CORE, "w": FLUX_WHITE,
                  "o": VOID, "v": VIOLET, "c": CRYSTAL, "h": HIGHLIGHT}


@texture("item/anomalite_emitter")
def anomalite_emitter() -> Image.Image:
    """A shard of Anomalite set in an azure-trimmed mount, point outwards: the Lance's source."""
    return sprite([
        "................",
        "................",
        "..........oo....",
        ".........ohco...",
        "........ohcvo...",
        ".......ohcvo....",
        "......ohcvo.....",
        ".....dhcvod.....",
        "....dfgcofd.....",
        "...dtfwgfftd....",
        "...dtefffetd....",
        "....dteeetd.....",
        ".....dtttd......",
        "......ddd.......",
        "................",
        "................",
    ], EMITTER_LEGEND)


THREAD_LEGEND = {"d": VOID, "v": VIOLET, "c": CRYSTAL, "h": HIGHLIGHT, "w": FLUX_WHITE}


@texture("item/veil_thread")
def veil_thread() -> Image.Image:
    """A pale strand wound loosely on itself, violet where it catches: part of the Veiled, drawn out."""
    return sprite([
        "................",
        "..........hw....",
        ".........h..h...",
        "....cchh.c..h...",
        "...c....hc..c...",
        "..c....c.h.c....",
        "..c...c...hc....",
        "..v..c...ch.....",
        "...v.c..ch..v...",
        "....vcch...v....",
        "......hhvvv.....",
        ".....h..........",
        "....c...........",
        "...v............",
        "..v.............",
        "................",
    ], THREAD_LEGEND)


@texture("item/anomaly_fragment")
def anomaly_fragment() -> Image.Image:
    """Something that grew, then broke: asymmetric, dark body, violet cracks, one glint."""
    return sprite([
        "................",
        ".........o......",
        "........omo.....",
        ".......ommvo....",
        "......ommvmo....",
        ".....omvmmbmo...",
        "....ommbvmhmo...",
        "...ommvmmvmmo...",
        "...omvmmbmvmmo..",
        "..ommbmvmmmmo...",
        "..omvmmmmvmo....",
        "...ommvmbmo.....",
        "....ommmvmo.....",
        ".....oommo......",
        ".......oo.......",
        "................",
    ], ANOMALY_LEGEND)


RIFT_LEGEND = {"e": RIFT_EDGE, "k": hexc("#0c0714"), "v": VIOLET, "o": VOID}


@texture("item/rift_residue")
def rift_residue() -> Image.Image:
    """A sliver of a closed rift: void inside a ragged violet edge, the wound's own outline."""
    return sprite([
        "................",
        "........e.......",
        ".......eke......",
        "......ekkve.....",
        ".....ekkkke.....",
        ".....ekkkkve....",
        "....ekkvkkke....",
        "....ekkkkkkke...",
        "...evkkkkkke....",
        "....ekkkvkke....",
        ".....ekkkkke....",
        ".....evkkke.....",
        "......ekkke.....",
        ".......eke......",
        "........e.......",
        "................",
    ], RIFT_LEGEND)


@texture("item/rift_seed")
def rift_seed() -> Image.Image:
    """A rift kernel held in azure brackets: residue made to stay put until planted."""
    legend = dict(RIFT_LEGEND)
    legend.update({"a": FLUX, "w": FLUX_CORE})
    return sprite([
        "................",
        "................",
        "..waa......aaw..",
        "..a..........a..",
        "..a....e.....a..",
        "......eke.......",
        ".....ekvke......",
        ".....ekkke......",
        ".....ekvke......",
        "......eke.......",
        "..a....e.....a..",
        "..a..........a..",
        "..waa......aaw..",
        "................",
        "................",
        "................",
    ], legend)


@texture("item/decoherence_lance")
def decoherence_lance() -> Image.Image:
    """A handheld beam tool: deepslate grip, navy shaft with an azure trace, a forked emitter."""
    legend = {"o": DEEP_SEAM, "g": TILE, "t": EDGE, "n": NAVY, "p": PANEL, "a": FLUX,
              "w": FLUX_CORE, "W": FLUX_WHITE, "d": FLUX_DIM}
    return sprite([
        "................",
        "............a...",
        "..........a.aw..",
        "...........wWa..",
        "..........aWwa..",
        ".........pnaa...",
        "........pan.....",
        ".......pan......",
        "......pdn.......",
        ".....pan........",
        "....ogp.........",
        "...ogto.........",
        "..ogto..........",
        ".ogto...........",
        ".oo.............",
        "................",
    ], legend)


@texture("item/flux_meter")
def flux_meter() -> Image.Image:
    """A handheld gauge: deepslate casing, a dark dial with azure ticks and needle."""
    legend = {"o": DEEP_SEAM, "e": EDGE, "t": TILE, "k": hexc("#101a36"), "d": FLUX_DIM,
              "a": FLUX, "w": FLUX_WHITE}
    return sprite([
        "................",
        "................",
        ".....oooooo.....",
        "...ooeeeeeeoo...",
        "..oeekkkkkkeeo..",
        "..oekkdkkdkkeo..",
        ".oekdkkkkkkdkeo.",
        ".oekkkkkkakkkeo.",
        ".oekkkkkakkkkeo.",
        ".oekkkkwkkkkkeo.",
        ".oeeeeeeeeeeeeo.",
        ".oetttaattttteo.",
        ".oeeeeeeeeeeeeo.",
        "..oooooooooooo..",
        "................",
        "................",
    ], legend)


@texture("particle/field_soft")
def field_soft() -> Image.Image:
    """A soft white disc for the Mirror Lens's haze, ink and plume glow, tinted per use: a smooth falloff,
    so a speck grown to a block or two across reads as a wisp of air rather than a square."""
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    centre = (size - 1) / 2.0
    for y in range(size):
        for x in range(size):
            d = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5 / (size / 2.0)
            a = max(0.0, 1.0 - d) ** 1.8
            img.putpixel((x, y), (255, 255, 255, int(round(a * 255))))
    return img


def _mirror_lens_worn(glow: bool) -> Image.Image:
    """
    The worn goggles (MirrorLensModel, 32x16): a leather strap round the head, and two lenses in dark
    rims. The strap's top and bottom faces sit inside the head and are left clear. With glow, only
    the glass: drawn again in the eyes' light so the lenses catch a little in the dark.
    """
    img = canvas((32, 16))
    leather, dark, stitch = hexc("#5a3c28"), hexc("#3a2618"), hexc("#8a6a4a")
    rim, glass, deep, glint = DEEP_SEAM, VIOLET, MITE, FLUX_CORE
    if not glow:
        # The strap's four sides, y 8-9: dark edge above, leather with a stitch below.
        for x in range(32):
            put(img, x, 8, dark)
            put(img, x, 9, stitch if x % 3 == 1 else leather)
        # A buckle at the back of the head.
        for x in (27, 28):
            put(img, x, 8, hexc("#9aa0ac"))
            put(img, x, 9, hexc("#6a707c"))
    for u in (0, 8):
        if not glow:
            # Rims: every face of the lens box, then the glass is laid over the front.
            fill(img, u + 1, 10, u + 3, 10, rim)       # top
            fill(img, u + 4, 10, u + 6, 10, rim)       # bottom
            fill(img, u + 0, 11, u + 7, 13, rim)       # the four sides round
        front = [(1, 0, glass), (0, 1, deep), (1, 1, glint), (2, 1, glass), (1, 2, deep)]
        for dx, dy, colour in front:
            put(img, u + 1 + dx, 11 + dy, colour if not glow else (hexc("#6a4ac8") if colour != glint else hexc("#5a8ad8")))
    return img


@texture("entity/mirror_lens")
def mirror_lens_worn() -> Image.Image:
    return _mirror_lens_worn(False)


@texture("entity/mirror_lens_glow")
def mirror_lens_worn_glow() -> Image.Image:
    return _mirror_lens_worn(True)


@texture("item/mirror_lens")
def mirror_lens() -> Image.Image:
    """Goggles: a leather strap, deepslate rims, and lenses that show the mirror, violet with an azure glint."""
    legend = {"s": hexc("#4a3222"), "S": hexc("#6b4a30"), "o": DEEP_SEAM, "e": EDGE, "v": VOID,
              "m": MITE, "c": VIOLET, "a": FLUX, "w": FLUX_WHITE, "b": CRYSTAL}
    return sprite([
        "................",
        "................",
        "................",
        "................",
        "SS............SS",
        "ssoooo....ooooss",
        "soeeeeo..oeeeeos",
        ".oevcmeoboevcmeo",
        ".oemcwebbbemcweo",
        ".oevmaeoboevmaeo",
        ".oeeeeeo..oeeeeo",
        "..ooooo....ooooo",
        "................",
        "................",
        "................",
        "................",
    ], legend)


# ---- Recolours of painted art ----

def exact_map(path: str, mapping: Dict[str, str]) -> Callable[[], Image.Image]:
    """Swaps listed colours one for one, leaving everything else as painted."""
    table = {hexc(k)[:3]: hexc(v) for k, v in mapping.items()}

    def build() -> Image.Image:
        img = source(path)
        for y in range(img.height):
            for x in range(img.width):
                colour = img.getpixel((x, y))
                if colour[3] and colour[:3] in table:
                    target = table[colour[:3]]
                    img.putpixel((x, y), (target[0], target[1], target[2], colour[3]))
        return img
    return build


TANK_FRAME_TO_PILLAR = {
    "1b192f": "131626",   # body, purple lean -> the pillar's body
    "0f0e1b": "0b0d18",   # seams
    "2d294c": "1f243a",   # raised edges
    "2a1a45": "2f1c52",   # the lit accent's halo, onto the oblivion ramp
    "25163d": "1f1236",
    "312150": "452a78",
    "ce80ff": "a887ff",   # the lit accent itself: violet, not pink
}

for _path in ("block/quantum_foundry_tank_frame", "block/quantum_foundry_tank_frame_active"):
    OUTPUTS[_path] = exact_map(_path, TANK_FRAME_TO_PILLAR)


def _hue_sat(colour: RGBA) -> Tuple[float, float, float]:
    import colorsys
    h, s, v = colorsys.rgb_to_hsv(colour[0] / 255, colour[1] / 255, colour[2] / 255)
    return h * 360.0, s, v


def _luma(colour: RGBA) -> float:
    return (0.2126 * colour[0] + 0.7152 * colour[1] + 0.0722 * colour[2]) / 255


def remap(img: Image.Image, match: Callable[[RGBA], bool], anchor: RGBA,
          ramp: Sequence[Tuple[float, RGBA]]) -> Image.Image:
    """
    Moves matching pixels onto a ramp by their brightness relative to the source's anchor colour,
    so a painted line keeps its shading steps while changing family.
    """
    out = img.copy()
    base = max(_luma(anchor), 1e-3)
    for y in range(out.height):
        for x in range(out.width):
            colour = out.getpixel((x, y))
            if colour[3] == 0 or not match(colour):
                continue
            ratio = _luma(colour) / base
            target = ramp[-1][1]
            for limit, candidate in ramp:
                if ratio < limit:
                    target = candidate
                    break
            out.putpixel((x, y), (target[0], target[1], target[2], colour[3]))
    return out


def is_cyan(colour: RGBA) -> bool:
    """
    Cyan lines, their pale hot pixels, and the dark teal halo painted round them (which leans a
    little bluer, up to about 215°). The navy stone sits at 225° and up, so it is left alone.
    """
    hue, sat, val = _hue_sat(colour)
    return 170.0 <= hue <= 215.0 and sat > 0.12 and val > 0.18


CYAN_ANCHOR = hexc("#48d2fc")
AZURE_RAMP = [(0.45, FLUX_OFF), (0.75, FLUX_DIM), (1.12, FLUX), (1.35, FLUX_CORE), (9.0, FLUX_WHITE)]


def recolour(path: str, match: Callable[[RGBA], bool], anchor: RGBA,
             ramp: Sequence[Tuple[float, RGBA]]) -> Callable[[], Image.Image]:
    def build() -> Image.Image:
        return remap(source(path), match, anchor, ramp)
    return build


# The older Foundry faces wear cyan (#48d2fc), from before the azure decision. The renderer already
# draws azure, so these come across to match; only cyan pixels move.
for _path in [
    "block/quantum_foundry_conduit_side_active", "block/quantum_foundry_conduit_top_active",
    "block/quantum_foundry_controller_side_active", "block/quantum_foundry_pillar_side_active",
    "block/quantum_foundry_pillar_top_active",
    # The ground ring's centre (only there because every blockstate needs a model) keeps your cross,
    # in azure; the cap's centre, which it used to share, gets its own aperture.
    "block/quantum_foundry_plinth_top_centre_active",
]:
    OUTPUTS[_path] = recolour(_path, is_cyan, CYAN_ANCHOR, AZURE_RAMP)

# Anomalite crystals and the Mirror Endermite are painted from scratch in tools/original_art.py.


# ---- Driver ----

def preview(images: Dict[str, Image.Image], path: str) -> None:
    scale, cols = 6, 10
    cell_w, cell_h = 16 * scale + 10, 16 * scale + 22
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (84, 84, 90, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(images.items()):
        tile = img.crop((0, 0, min(img.width, 64), min(img.height, 16) if img.width == 16 else min(img.height, 32)))
        if tile.width != 16:
            tile = tile.resize((16 * scale, tile.height * 16 * scale // tile.width), Image.NEAREST)
        else:
            tile = tile.resize((16 * scale, 16 * scale), Image.NEAREST)
        x, y = (i % cols) * cell_w, (i // cols) * cell_h
        sheet.alpha_composite(tile, (x + 5, y + 2))
        draw.text((x + 3, y + 16 * scale + 6), name.split("/")[-1][:17], fill=(240, 240, 240, 255))
    sheet.save(path)


def compare(paths: Iterable[str], against: str, target: str) -> None:
    """Before (from a git ref) and after, side by side: the review sheet committed with the pass."""
    import io
    import subprocess

    def tile(data: bytes, scale: int) -> Image.Image:
        img = Image.open(io.BytesIO(data)).convert("RGBA")
        if img.width == 16:
            img = img.crop((0, 0, 16, 16))
        else:
            # Entity sheets: the top-left block, where the head and body live.
            small = img.crop((0, 0, 32, 16)).resize((16, 8), Image.NEAREST)
            img = canvas()
            img.alpha_composite(small, (0, 4))
        return img.resize((16 * scale, 16 * scale), Image.NEAREST)

    paths = list(paths)
    scale, cols = 4, 5
    cell_w, cell_h = 16 * scale * 2 + 14, 16 * scale + 18
    rows = (len(paths) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h + 24), (84, 84, 90, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((6, 6), f"before ({against}) -> after", fill=(255, 255, 255, 255))
    for i, path in enumerate(paths):
        file = os.path.relpath(os.path.join(TEXTURES, path + ".png"), ROOT)
        old = subprocess.run(["git", "show", f"{against}:{file}"], capture_output=True, cwd=ROOT)
        x, y = (i % cols) * cell_w, (i // cols) * cell_h + 24
        if old.returncode == 0:
            sheet.alpha_composite(tile(old.stdout, scale), (x + 4, y))
        else:
            draw.text((x + 14, y + 26), "new", fill=(200, 200, 200, 255))
        with open(os.path.join(ROOT, file), "rb") as handle:
            sheet.alpha_composite(tile(handle.read(), scale), (x + 8 + 16 * scale, y))
        draw.text((x + 4, y + 16 * scale + 3), path.split("/")[-1][:26], fill=(240, 240, 240, 255))
    sheet.save(target)


def main() -> None:
    parser = argparse.ArgumentParser(description="Quantimium texture pass")
    parser.add_argument("--preview", action="store_true", help="write a contact sheet to tools/out")
    parser.add_argument("--only", default="", help="comma-separated output names to regenerate")
    parser.add_argument("--dry-run", action="store_true", help="build and preview without writing")
    parser.add_argument("--compare", metavar="REF", help="write TEXTURE_PASS.png: before (at REF) and after")
    args = parser.parse_args()

    wanted = {name.strip() for name in args.only.split(",") if name.strip()}
    built: Dict[str, Image.Image] = {}
    for path, build in OUTPUTS.items():
        if wanted and path not in wanted and path.split("/")[-1] not in wanted:
            continue
        built[path] = build()

    if not args.dry_run:
        if not wanted:
            write_frame_models()
        for path, img in built.items():
            target = os.path.join(TEXTURES, path + ".png")
            os.makedirs(os.path.dirname(target), exist_ok=True)
            img.save(target)
            print(f"wrote {os.path.relpath(target, ROOT)}")

    if args.preview:
        out_dir = os.path.join(ROOT, "tools", "out")
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, "texture_pass.png")
        preview(built, path)
        print(f"wrote {os.path.relpath(path, ROOT)}")

    if args.compare:
        path = os.path.join(ROOT, "private", "TEXTURE_PASS.png")
        compare(built.keys(), args.compare, path)
        print(f"wrote {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    main()
