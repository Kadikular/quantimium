#!/usr/bin/env python3
"""
Generates the Reactor plinth's circuit traces: textures, models and blockstates.

Each plinth block (and each port, which sits in the plinth) has four edge values, north, east, south
and west: 0 for no trace, 1 or 2 for a trace crossing that edge at one of two points. The game sets
them from a hash of each edge's world position (ReactorTraces.java), so the blocks either side of an
edge always agree and the traces run on from block to block, wherever the floor is built.

Traces are 2 px wide. Opposite edges both carrying a trace make a straight bus through the block,
jogging 45 degrees mid-block if their lanes differ; one vertical and one horizontal edge make a
chamfered corner; a trace meeting a bus joins it on a via; buses crossing in line share a via; a lone
trace ends on a pad. Edge values are correlated along each column and row in runs of blocks, so buses
run straight for several blocks at a time. Lit when the reactor is formed, dark otherwise.

    ./tools/reactor_traces.py
"""

from __future__ import annotations

import itertools
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium")
TEX = os.path.join(ASSETS, "textures", "block", "reactor_traces")
MODELS = os.path.join(ASSETS, "models", "block", "reactor_traces")

def hx(c, a=255): return (int(c[0:2],16),int(c[2:4],16),int(c[4:6],16),a)
LANE = {1: 3, 2: 11}

def palette(lit):
    if lit:
        return dict(bg=hx('0e1120'), tr=hx('3485ff'), sh=hx('0a1a44'), via=hx('7fb2ff'), hole=hx('0b0d18'))
    return dict(bg=hx('0e1120'), tr=hx('1f2742'), sh=hx('0a0c18'), via=hx('2c3552'), hole=hx('0b0d18'))

def tile(n, e, s, w, lit=True):
    P = palette(lit)
    img = Image.new('RGBA', (16, 16), P['bg'])
    rnd = random.Random(7)
    for _ in range(30):
        img.putpixel((rnd.randrange(16), rnd.randrange(16)), rnd.choice([hx('131626'), hx('0b0d18'), hx('161a2e')]))
    trace = set()
    vias = []

    def px(x, y):
        if 0 <= x < 16 and 0 <= y < 16:
            trace.add((x, y))

    def block(x, y):  # one 2x2 step of a trace
        for dx in (0, 1):
            for dy in (0, 1):
                px(x + dx, y + dy)

    def vbus(a, b):
        """Top lane a to bottom lane b; returns the x of the bus at each row."""
        xa, xb = LANE[a], LANE[b]
        at = {}
        if xa == xb:
            for y in range(16): at[y] = xa
        else:
            span = abs(xb - xa); y0 = 8 - span // 2; step = 1 if xb > xa else -1
            for y in range(16):
                if y < y0: at[y] = xa
                elif y >= y0 + span: at[y] = xb
                else: at[y] = xa + (y - y0) * step
        for y, x in at.items(): block(x, y if y < 15 else 14)
        return at

    def hbus(a, b):
        ya, yb = LANE[a], LANE[b]
        at = {}
        if ya == yb:
            for x in range(16): at[x] = ya
        else:
            span = abs(yb - ya); x0 = 8 - span // 2; step = 1 if yb > ya else -1
            for x in range(16):
                if x < x0: at[x] = ya
                elif x >= x0 + span: at[x] = yb
                else: at[x] = ya + (x - x0) * step
        for x, y in at.items(): block(x if x < 15 else 14, y)
        return at

    def stub_to(edge, lane, stop):
        """From an edge along its lane to coordinate `stop` (the bus it joins, or a pad)."""
        p = LANE[lane]
        if edge == 'n':
            for y in range(0, stop + 1): block(p, min(y, 14))
        if edge == 's':
            for y in range(stop, 16): block(p, min(y, 14))
        if edge == 'w':
            for x in range(0, stop + 1): block(min(x, 14), p)
        if edge == 'e':
            for x in range(stop, 16): block(min(x, 14), p)

    val = {'n': n, 'e': e, 's': s, 'w': w}
    vertical = n and s
    horizontal = w and e
    if vertical and horizontal:
        vat = vbus(n, s); hat = hbus(w, e)
        if n == s and w == e:
            vias.append((LANE[n], LANE[w]))
    elif vertical:
        vat = vbus(n, s)
        for side in ('w', 'e'):
            if val[side]:
                y = LANE[val[side]]; x = vat[y]
                stub_to(side, val[side], x)
                vias.append((x, y))
    elif horizontal:
        hat = hbus(w, e)
        for side in ('n', 's'):
            if val[side]:
                x = LANE[val[side]]; y = hat[x]
                stub_to(side, val[side], y)
                vias.append((x, y))
    else:
        sides = [k for k in 'nesw' if val[k]]
        if len(sides) == 2:
            # A corner: down one lane, a 45 degree chamfer, along the other.
            v = next(k for k in sides if k in 'ns')
            hz = next(k for k in sides if k in 'we')
            x = LANE[val[v]]; y = LANE[val[hz]]; c = 2
            sx = 1 if hz == 'e' else -1
            if v == 'n':
                for yy in range(0, y - c + 1): block(x, yy)
                for i in range(c + 1): block(x + sx * i, y - c + i)
            else:
                for yy in range(y + c, 15): block(x, yy)
                for i in range(c + 1): block(x + sx * i, y + c - i)
            if hz == 'e':
                for xx in range(x + c, 15): block(xx, y)
            else:
                for xx in range(0, x - c + 1): block(xx, y)
        elif len(sides) == 1:
            k = sides[0]; p = LANE[val[k]]
            stop = {'n': 7, 's': 7, 'w': 7, 'e': 7}[k]
            stub_to(k, val[k], stop)
            pad = {'n': (p, 7), 's': (p, 7), 'w': (7, p), 'e': (7, p)}[k]
            vias.append(pad)
    # shadow: one pixel down and right of the copper, then the copper, then vias
    for (x, y) in trace:
        for sx, sy in ((1, 1),):
            if (x + sx, y + sy) not in trace and 0 <= x + sx < 16 and 0 <= y + sy < 16:
                img.putpixel((x + sx, y + sy), P['sh'])
    for (x, y) in trace:
        img.putpixel((x, y), P['tr'])
    for (x, y) in vias:
        for dx in range(-1, 3):
            for dy in range(-1, 3):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                    img.putpixel((x + dx, y + dy), P['via'])
        for dx in (0, 1):
            for dy in (0, 1):
                if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                    img.putpixel((x + dx, y + dy), P['hole'])
    return img


def draw(edges: dict, lit: bool) -> Image.Image:
    return tile(edges["n"], edges["e"], edges["s"], edges["w"], lit)


PULSE = hx("c8dcff")
PULSE_FRAMETIME = 2


def animate(image: Image.Image) -> Image.Image:
    """Sixteen frames of signals running along the copper: a short bright dash a diagonal band
    crosses, one pixel on each frame. The band repeats every 16 pixels, so on a long bus the dashes
    run on from block to block, down and to the east."""
    copper = palette(True)["tr"]
    frames = Image.new("RGBA", (16, 16 * 16))
    for frame in range(16):
        tile_frame = image.copy()
        for x in range(16):
            for y in range(16):
                if image.getpixel((x, y)) == copper and (x + y - frame) % 16 in (0, 1):
                    tile_frame.putpixel((x, y), PULSE)
        frames.paste(tile_frame, (0, 16 * frame))
    return frames


def name(edges: dict) -> str:
    return "".join(str(edges[e]) for e in "nesw")


def main() -> None:
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    variants = {}
    parts = []
    for values in itertools.product(range(3), repeat=4):
        edges = dict(zip("nesw", values))
        key = name(edges)
        for formed, tag in ((True, "_lit"), (False, "")):
            image = draw(edges, formed)
            path_png = os.path.join(TEX, f"{key}{tag}.png")
            if formed:
                animate(image).save(path_png)
                with open(path_png + ".mcmeta", "w") as handle:
                    json.dump({"animation": {"frametime": PULSE_FRAMETIME, "interpolate": False}}, handle)
            else:
                image.save(path_png)
            model = {
                "parent": "minecraft:block/block",
                "textures": {
                    "top": f"quantimium:block/reactor_traces/{key}{tag}",
                    "side": "quantimium:block/reactor_plinth_side" + ("_active" if formed else ""),
                    "bottom": "quantimium:block/quantum_foundry_base",
                    "particle": "quantimium:block/reactor_plinth",
                },
                "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                    **{d: {"texture": "#side", "cullface": d} for d in ("north", "south", "east", "west")},
                    "up": {"texture": "#top", "cullface": "up"},
                    "down": {"texture": "#bottom", "cullface": "down"},
                }}],
            }
            with open(os.path.join(MODELS, f"{key}{tag}.json"), "w") as handle:
                json.dump(model, handle, indent=2)
            condition = {"formed": str(formed).lower(), "trace_north": str(edges["n"]), "trace_east": str(edges["e"]),
                         "trace_south": str(edges["s"]), "trace_west": str(edges["w"])}
            model_id = f"quantimium:block/reactor_traces/{key}{tag}"
            variants[",".join(f"{k}={v}" for k, v in sorted(condition.items()))] = {"model": model_id}
            parts.append({"when": condition, "apply": {"model": model_id}})

    with open(os.path.join(ASSETS, "blockstates", "reactor_plinth.json"), "w") as handle:
        json.dump({"variants": variants}, handle, indent=1)
    for port in ("input", "output", "energy", "materialiser"):
        # A port is the plinth with a flat socket laid over every face (tools/reactor_art.py draws it).
        port_parts = list(parts)
        for formed in (False, True):
            port_parts.append({"when": {"formed": str(formed).lower()},
                               "apply": {"model": f"quantimium:block/reactor_{port}_port_overlay" + ("_active" if formed else "")}})
        with open(os.path.join(ASSETS, "blockstates", f"reactor_{port}_port.json"), "w") as handle:
            json.dump({"multipart": port_parts}, handle, indent=1)
        for formed in (False, True):
            tag = "_active" if formed else ""
            texture = f"quantimium:block/reactor_{port}_port_overlay{tag}"
            overlay = {
                "parent": "minecraft:block/block",
                "render_type": "minecraft:cutout",
                "textures": {"overlay": texture, "particle": "quantimium:block/reactor_plinth_side" + tag},
                # A hair outside the block, so the socket sits on its faces without fighting them.
                "elements": [{"from": [-0.01, -0.01, -0.01], "to": [16.01, 16.01, 16.01], "shade": False,
                              # Explicit UVs: worked out from a box this size they'd run off the texture.
                              "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#overlay", "cullface": d}
                                        for d in ("north", "south", "east", "west", "up", "down")}}],
            }
            with open(os.path.join(ASSETS, "models", "block", f"reactor_{port}_port_overlay{tag}.json"), "w") as handle:
                json.dump(overlay, handle, indent=2)
    print(f"wrote {len(variants)} plinth states and their traces")


if __name__ == "__main__":
    main()
