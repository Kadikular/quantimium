#!/usr/bin/env python3
"""
Quantimium's own versions of the art that was derived from vanilla's: painted here from shapes and
palettes, so no vanilla pixels ship in the mod. Deterministic, like tools/texture_pass.py, whose
palettes and stone painters it uses.

    .venv-wiki/bin/python tools/original_art.py            # write the textures
    .venv-wiki/bin/python tools/original_art.py --preview  # also tools/out/original_art.png

What it paints:
  * Anomalite crystal growth stages: violet shards, from a nub to a cluster;
  * the mirror's grey flora: short and tall grass, vine, dead bush, hanging roots;
  * the Quantimium Trace: a heap of azure dust with a few motes over it;
  * Unrealised Matter: an animation that slowly morphs between a gem, a lapis chunk, a coal lump and
    a raw ore nugget, never settling on one;
  * the Mirror Endermite: vanilla's endermite only gives where its model's faces sit on the sheet and
    where its eyes are; every pixel is painted here;
  * the Unrealised Ore's own speckles, laid over vanilla's stone by the model: only the pixels
    painted for Quantimium are kept, separated from the vanilla texture they were painted onto;
  * the Quantum Crafter's crafting grid, laid over vanilla's polished deepslate by its model.
"""

from __future__ import annotations

import glob
import io
import json
import math
import os
import random
import sys
import zipfile
from typing import Dict, List, Sequence, Tuple

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import texture_pass as tp  # noqa: E402

RGBA = Tuple[int, int, int, int]
ROOT = tp.ROOT
TEXTURES = tp.TEXTURES
OUT = os.path.join(ROOT, "tools", "out")


def vanilla(path: str) -> Image.Image:
    """A vanilla texture, read from the game jar in the Gradle cache (only to measure against)."""
    jars = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar")))
    if not jars:
        raise SystemExit("needs the 26.1.2 client jar in the Gradle cache (run any Gradle task once)")
    with zipfile.ZipFile(jars[0]) as jar:
        return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def shade(colour: RGBA, factor: float, alpha: int | None = None) -> RGBA:
    r, g, b, a = colour
    return (max(0, min(255, int(r * factor))), max(0, min(255, int(g * factor))),
            max(0, min(255, int(b * factor))), a if alpha is None else alpha)


# ---- Anomalite crystals ----

CRYSTAL_RAMP = [tp.hexc("#2a1a52"), tp.hexc("#4a2f8f"), tp.VIOLET, tp.CRYSTAL, tp.HIGHLIGHT]


def shard(img: Image.Image, cx: float, height: int, width: float, lean: float) -> None:
    """One prism standing on the bottom edge: straight-sided, then a point. A lit left face, a dark
    right face and a bright ridge between them, outlined in the darkest violet."""
    base = 15
    body = max(1, int(height * 0.6))
    for step in range(height):
        y = base - step
        t = step / max(height - 1, 1)
        half = width if step < body else width * (height - step) / max(height - body, 1)
        centre = cx + lean * step
        left, right = math.floor(centre - half + 0.5), math.floor(centre + half + 0.5)
        for x in range(left, right + 1):
            if x == left or x == right:
                colour = CRYSTAL_RAMP[0]
            elif abs(x - centre) < 0.6:
                colour = CRYSTAL_RAMP[4] if t > 0.25 else CRYSTAL_RAMP[3]
            elif x < centre:
                colour = CRYSTAL_RAMP[3] if (x + step) % 4 else CRYSTAL_RAMP[2]
            else:
                colour = CRYSTAL_RAMP[1] if (x - step) % 5 else CRYSTAL_RAMP[2]
            tp.put(img, x, y, colour)
    tp.put(img, round(cx + lean * (height - 1)), base - height + 1, CRYSTAL_RAMP[4])


CRYSTAL_STAGES = [
    [(7.5, 4, 1.6, 0.0), (5.0, 3, 1.2, -0.3)],
    [(7.5, 7, 2.0, 0.0), (4.5, 5, 1.5, -0.35), (10.8, 4, 1.4, 0.35)],
    [(7.5, 11, 2.4, 0.0), (4.0, 8, 1.8, -0.35), (11.2, 7, 1.7, 0.35), (9.2, 4, 1.2, 0.15)],
    [(7.5, 16, 2.6, 0.0), (3.6, 11, 2.1, -0.3), (11.6, 12, 2.1, 0.28), (5.6, 6, 1.5, -0.15), (10.2, 6, 1.5, 0.15)],
]


def crystal(stage: int) -> Image.Image:
    img = tp.canvas()
    shards = CRYSTAL_STAGES[stage]
    # The tallest stands at the back, so the side shards cross in front of it.
    for cx, height, width, lean in sorted(shards, key=lambda s: -s[1]):
        shard(img, cx, height, width, lean)
    return img


# ---- The mirror's grey flora ----

GREYS = [tp.MIRROR_DARK, tp.hexc("#404046"), tp.MIRROR_GREY, tp.hexc("#68686f"), tp.MIRROR_LIGHT]


def blade(img: Image.Image, rng: random.Random, x: float, bottom: int, height: int, sway: float) -> None:
    for step in range(height):
        y = bottom - step
        t = step / max(height - 1, 1)
        px = round(x + sway * t * t * height * 0.25)
        colour = GREYS[min(4, 1 + int(t * 3.5) + rng.choice([0, 0, 1]))] if step else GREYS[0]
        tp.put(img, px, y, colour)


def grass(name: str, count: int, low: int, high: int, bottom: int = 15) -> Image.Image:
    rng = tp.rng_for(name)
    img = tp.canvas()
    for i in range(count):
        x = 1 + (14 * i + rng.uniform(0, 1.5)) / max(count - 1, 1) * 1.0
        blade(img, rng, x, bottom, rng.randint(low, high), rng.uniform(-1.2, 1.2))
    return img


def tall_grass_bottom() -> Image.Image:
    rng = tp.rng_for("mirror_tall_grass_bottom")
    img = tp.canvas()
    for i in range(10):
        x = 1 + i * 1.5 + rng.uniform(-0.4, 0.4)
        for y in range(16):
            tp.put(img, round(x + math.sin(y * 0.35 + i) * 0.6), y, GREYS[1 + (i + y // 5) % 3])
    return img


def tall_grass_top() -> Image.Image:
    rng = tp.rng_for("mirror_tall_grass_top")
    img = tp.canvas()
    for i in range(10):
        x = 1 + i * 1.5 + rng.uniform(-0.4, 0.4)
        blade(img, rng, x + math.sin(15 * 0.35 + i) * 0.6, 15, rng.randint(5, 15), rng.uniform(-1.4, 1.4))
    return img


def vine() -> Image.Image:
    rng = tp.rng_for("mirror_vine")
    img = tp.canvas()
    for strand in range(4):
        x = rng.uniform(1, 14)
        for y in range(16):
            x += rng.uniform(-0.7, 0.7)
            x = min(14.5, max(0.5, x))
            tp.put(img, round(x), y, GREYS[1])
            if rng.random() < 0.35:
                # A leaf: a small grey cluster beside the stem.
                side = rng.choice([-1, 1])
                for dx, dy in ((side, 0), (side, -1), (2 * side, 0)):
                    tp.put(img, round(x) + dx, y + dy, rng.choice(GREYS[2:]))
    return img


def dead_bush() -> Image.Image:
    rng = tp.rng_for("mirror_dead_bush")
    img = tp.canvas()

    def branch(x: float, y: float, angle: float, length: float, depth: int) -> None:
        for _ in range(int(length)):
            x += math.cos(angle)
            y -= math.sin(angle)
            tp.put(img, round(x), round(y), GREYS[1 if depth < 2 else 2])
        if depth < 3:
            for turn in (-0.55, 0.5):
                branch(x, y, angle + turn + rng.uniform(-0.15, 0.15), length * 0.62, depth + 1)

    for start in (-0.35, 0.0, 0.35):
        branch(7.5, 15.5, math.pi / 2 + start + rng.uniform(-0.1, 0.1), rng.uniform(4.5, 6.0), 0)
    return img


def hanging_roots() -> Image.Image:
    rng = tp.rng_for("mirror_hanging_roots")
    img = tp.canvas()
    for i in range(7):
        x = 1.5 + i * 2.0 + rng.uniform(-0.5, 0.5)
        length = rng.randint(6, 15)
        for y in range(length):
            x += math.sin(y * 0.6 + i * 1.7) * 0.35
            tp.put(img, round(x), y, GREYS[2] if y < length - 2 else GREYS[3])
        tp.put(img, round(x), length, GREYS[4])
    return img


# ---- The Quantimium Trace ----

def trace() -> Image.Image:
    """A small heap of measured-out dust, azure and grainy, faintly lit, rounded underneath as well
    as on top so no edge of it is a hard line; a few motes still settling above it."""
    rng = tp.rng_for("quantimium_trace")
    img = tp.canvas()
    ramp = [tp.FLUX_OFF, tp.FLUX_DIM, tp.FLUX, tp.FLUX_CORE]
    cx, cy, rx, top, bottom = 7.5, 11.0, 5.6, 3.6, 2.2
    for y in range(16):
        for x in range(16):
            dx = (x - cx) / rx
            dy = (y - cy) / (top if y < cy else bottom)
            reach = dx * dx + dy * dy
            if reach > 1.0 or (reach > 0.82 and rng.random() < 0.45):
                continue  # outside, or a grain off its ragged rim
            lit = (cy - y) / top * 0.55 + (cx - x) / 14.0 + rng.uniform(-0.45, 0.45)
            if reach > 0.7:
                lit -= 0.6  # its rim falls into shadow, all the way round
            tp.put(img, x, y, ramp[max(0, min(3, int(1.3 + lit * 2.2)))])
    for _ in range(4):
        tp.put(img, rng.randint(5, 10), rng.randint(9, 11), tp.FLUX_WHITE)
    for x, y in ((4, 5), (11, 4), (8, 2)):
        tp.put(img, x, y, tp.FLUX_CORE if (x + y) % 2 else tp.FLUX)
    return img


# ---- Unrealised Matter ----

# The forms it hovers between, as polygons on its 16x16, each with its colour and an inner mark.
FORMS = {
    "gem": ([(4, 4), (11, 4), (14.5, 7.5), (7.5, 14.5), (0.5, 7.5)], tp.hexc("#5fd8e8")),
    "lapis": ([(3, 5), (8, 2), (13, 4), (14, 10), (10, 14), (4, 13), (2, 9)], tp.hexc("#3555c8")),
    "coal": ([(2, 8), (4, 4), (9, 3), (13, 6), (14, 11), (9, 14), (4, 13)], tp.hexc("#2a2a34")),
    "nugget": ([(2, 9), (3, 5), (7, 4), (9, 6), (12, 5), (14, 8), (13, 12), (8, 13), (3, 13)], tp.hexc("#c9a27a")),
}
FORM_ORDER = ["gem", "lapis", "coal", "nugget"]
# How far each form pulls Matter's own colour towards its ore's: coal has to read dark.
FORM_PULL = {"gem": 0.45, "lapis": 0.5, "coal": 0.7, "nugget": 0.5}


def form_mark(form: str, x: int, y: int) -> float:
    """What sets each form apart inside its outline, as a brightness change at (x, y)."""
    if form == "gem":
        if y == 7:
            return 0.35  # the girdle
        if abs((x - 7.5) - (y - 7)) < 0.6 or abs((x - 7.5) + (y - 7)) < 0.6:
            return -0.25  # facets running down to the point
        return 0.0
    if form == "lapis":
        return 0.55 if (x * 7 + y * 13) % 11 == 0 else 0.0  # bright flecks
    if form == "coal":
        return 0.45 if (x * 5 + y * 3) % 9 == 0 else -0.05  # dull grain, the odd glint
    return 0.25 * math.sin(x * 1.3) * math.sin(y * 1.1)  # a lumpy nugget
HOLD_FRAMES = 6
MORPH_FRAMES = 18
MATTER_FRAMETIME = 3
MATTER_BASE = tp.hexc("#7b6cf0")
SUPER = 4


def signed_distance(points: Sequence[Tuple[float, float]]) -> List[List[float]]:
    """Distance to the polygon's edge on a 64x64 grid, negative inside."""
    size = 16 * SUPER
    mask = Image.new("L", (size, size), 0)
    # A touch bigger than drawn, so each form fills the slot.
    scaled = [((x - 7.5) * 1.12 + 7.5, (y - 8) * 1.12 + 8) for x, y in points]
    ImageDraw.Draw(mask).polygon([(x * SUPER, y * SUPER) for x, y in scaled], fill=255)
    inside = [[mask.getpixel((x, y)) > 127 for x in range(size)] for y in range(size)]
    edges = [(x, y) for y in range(size) for x in range(size)
             if any(0 <= x + dx < size and 0 <= y + dy < size and inside[y + dy][x + dx] != inside[y][x]
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    field = []
    for y in range(size):
        row = []
        for x in range(size):
            nearest = min(math.hypot(x - ex, y - ey) for ex, ey in edges) if edges else size
            row.append(-nearest if inside[y][x] else nearest)
        field.append(row)
    return field


def matter_frame(fields: Dict[str, List[List[float]]], a: str, b: str, t: float, phase: float) -> Image.Image:
    """One frame of the morph from form {@code a} to {@code b}, {@code t} of the way, smoothed."""
    t = t * t * (3 - 2 * t)
    img = tp.canvas()
    # Matter's own blue-violet, leaning towards what it might become.
    body = tp.lerp(tp.lerp(MATTER_BASE, FORMS[a][1], FORM_PULL[a]), tp.lerp(MATTER_BASE, FORMS[b][1], FORM_PULL[b]), t)
    for y in range(16):
        for x in range(16):
            samples = []
            for sy in range(SUPER):
                for sx in range(SUPER):
                    gx, gy = x * SUPER + sx, y * SUPER + sy
                    samples.append(fields[a][gy][gx] * (1 - t) + fields[b][gy][gx] * t)
            inside = sum(1 for d in samples if d < 0)
            if inside < SUPER * SUPER // 2:
                continue
            depth = -sum(samples) / len(samples) / SUPER
            # Lit from the top left, darker towards the edge, with a slow glint crossing it.
            light = 0.55 + min(depth, 3.0) * 0.12 + (15 - x - y) / 30.0 * 0.35
            glint = math.exp(-((x + y) / 2.0 - phase) ** 2 / 1.5) * 0.35
            if depth < 0.8:
                light *= 0.62
            light += form_mark(a, x, y) * (1 - t) + form_mark(b, x, y) * t
            pixel = shade(body, light + glint, 225)
            tp.put(img, x, y, pixel)
    return img


def matter_frames() -> Image.Image:
    fields = {name: signed_distance(points) for name, (points, _) in FORMS.items()}
    frames: List[Image.Image] = []
    count = len(FORM_ORDER) * (HOLD_FRAMES + MORPH_FRAMES)
    for i, a in enumerate(FORM_ORDER):
        b = FORM_ORDER[(i + 1) % len(FORM_ORDER)]
        for _ in range(HOLD_FRAMES):
            frames.append(matter_frame(fields, a, a, 0.0, 22.0 * len(frames) / count - 3))
        for step in range(MORPH_FRAMES):
            frames.append(matter_frame(fields, a, b, (step + 1) / MORPH_FRAMES, 22.0 * len(frames) / count - 3))
    strip = tp.canvas((16, 16 * len(frames)))
    for i, frame in enumerate(frames):
        strip.paste(frame, (0, 16 * i))
    return strip


# ---- The Mirror Endermite ----

def mirror_endermite() -> Image.Image:
    """Vanilla's sheet says only where faces and eyes sit; the colours are painted here."""
    layout = vanilla("entity/endermite/endermite")
    rng = tp.rng_for("mirror_endermite")
    ramp = [tp.VOID, tp.MITE, tp.MITE, tp.BRIDGE, tp.hexc("#5a4480")]
    img = tp.canvas(layout.size)
    for y in range(layout.height):
        for x in range(layout.width):
            r, g, b, a = layout.getpixel((x, y))
            if a == 0:
                continue
            if r > 150 and g < 90 and b < 90:
                tp.put(img, x, y, tp.HIGHLIGHT)  # an eye
                continue
            band = (y // 2) % 3 == 0
            colour = ramp[min(4, rng.choice([0, 1, 1, 2, 3]) + (1 if band else 0))]
            tp.put(img, x, y, colour)
    return img


# ---- Painted over vanilla: keep only what was painted for Quantimium ----

def own_pixels(painted: Image.Image, under: Image.Image, tolerance: int = 24) -> Image.Image:
    """The pixels of {@code painted} that differ from the vanilla texture they were painted onto."""
    out = tp.canvas(painted.size)
    for y in range(painted.height):
        for x in range(painted.width):
            p, v = painted.getpixel((x, y)), under.getpixel((x, y))
            if p[3] and sum(abs(p[i] - v[i]) for i in range(3)) > tolerance:
                out.putpixel((x, y), p)
    return out


def unrealised_ore_overlay(original: Image.Image) -> Image.Image:
    return own_pixels(original, vanilla("block/iron_ore"))


def quantum_crafter_grid() -> Image.Image:
    """The Quantum Crafter's top: a crafting table's small grid, three 2x2 cells a side in dark lines
    across the middle of the face, laid by its model over vanilla's polished deepslate."""
    img = tp.canvas()
    for line in (3, 6, 9, 12):
        tp.hline(img, 3, 12, line, tp.DEEP_SEAM)
        tp.vline(img, line, 3, 12, tp.DEEP_SEAM)
    # Each cell's top-left pixel a shade lighter, so the cells read as sunk into the slab.
    for cx in (4, 7, 10):
        for cy in (4, 7, 10):
            tp.put(img, cx, cy, (0x5a, 0x5a, 0x62, 120))
    return img


# ---- Writing ----

def outputs(originals: Dict[str, Image.Image]) -> Dict[str, Image.Image]:
    out = {f"block/anomalite_crystal_stage{stage}": crystal(stage) for stage in range(4)}
    out.update({
        "block/mirror_short_grass": grass("mirror_short_grass", 9, 4, 11),
        "block/mirror_tall_grass_bottom": tall_grass_bottom(),
        "block/mirror_tall_grass_top": tall_grass_top(),
        "block/mirror_vine": vine(),
        "block/mirror_dead_bush": dead_bush(),
        "block/mirror_hanging_roots": hanging_roots(),
        "item/quantimium_trace": trace(),
        "item/unrealised_matter": matter_frames(),
        "entity/mirror_endermite": mirror_endermite(),
    })
    if "block/unrealised_ore" in originals:
        out["block/unrealised_ore_overlay"] = unrealised_ore_overlay(originals["block/unrealised_ore"])
    out["block/quantum_crafter_grid"] = quantum_crafter_grid()
    return out


def main() -> None:
    # The ore's speckles are separated from its original in private/art_sources (not published:
    # it was painted on vanilla's iron ore). Without it the overlay already written stays as it is.
    sources = os.path.join(ROOT, "private", "art_sources")
    originals = {}
    for name in ("block/unrealised_ore",):
        path = os.path.join(sources, name + ".png")
        if os.path.exists(path):
            originals[name] = Image.open(path).convert("RGBA")
    written = outputs(originals)
    for name, image in written.items():
        path = os.path.join(TEXTURES, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        image.save(path)
    with open(os.path.join(TEXTURES, "item", "unrealised_matter.png.mcmeta"), "w") as handle:
        json.dump({"animation": {"frametime": MATTER_FRAMETIME, "interpolate": True}}, handle)
    print(f"wrote {len(written)} textures")
    if "--preview" in sys.argv:
        os.makedirs(OUT, exist_ok=True)
        names = sorted(written)
        sheet = Image.new("RGBA", (len(names) * 72, 72), (60, 60, 70, 255))
        for i, name in enumerate(names):
            image = written[name]
            if image.height > image.width * 2:
                image = image.crop((0, 0, 16, 16))
            scale = 64 / max(image.size)
            tile = image.resize((max(1, int(image.width * scale)), max(1, int(image.height * scale))), Image.NEAREST)
            sheet.alpha_composite(tile, (i * 72 + 4, 4))
        sheet.save(os.path.join(OUT, "original_art.png"))


if __name__ == "__main__":
    main()
