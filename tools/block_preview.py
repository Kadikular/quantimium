#!/usr/bin/env python3
"""
Offline block preview: renders the mod's real blockstates, models and textures isometrically, one
block at a time and as assembled scenes, so textures can be judged together without starting the
game.

    ./tools/block_preview.py                  # every sheet into tools/out/preview/
    ./tools/block_preview.py --scene foundry  # just one scene (see SCENES)
    ./tools/block_preview.py --list           # the scene names

What it reads is what the game reads: blockstate variants (and multipart parts), model parents,
texture references, elements with automatic or explicit UVs, and blockstate x and y rotations. What it
simplifies: element rotations are drawn unrotated, cross models are drawn as a flat card facing the
camera, tint and ambient occlusion are skipped, and a face is shaded by its direction only (top 1.0,
south 0.8, east 0.6, as the game does with its default lighting). The camera looks from the
south-east, so the up, south and east faces show.
"""

from __future__ import annotations

import argparse
import json
import math
import os
from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence, Tuple

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium")
OUT = os.path.join(ROOT, "tools", "out", "preview")

BACKGROUND = (38, 40, 48, 255)
LABEL = (230, 230, 236, 255)

# ---- Vanilla parents we do not have on disk, as the game defines them ----

def _cube(textures: Dict[str, str]) -> dict:
    return {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        face: {"texture": textures[face], "cullface": face}
        for face in ("down", "up", "north", "south", "west", "east")}}]}


BUILTIN = {
    "minecraft:block/block": {},
    "minecraft:block/cube": _cube({f: "#" + f for f in ("down", "up", "north", "south", "west", "east")}),
    "minecraft:block/cube_all": {"parent": "minecraft:block/cube", "textures": {
        "particle": "#all", "down": "#all", "up": "#all", "north": "#all", "south": "#all", "west": "#all",
        "east": "#all"}},
    "minecraft:block/cube_bottom_top": {"parent": "minecraft:block/cube", "textures": {
        "particle": "#side", "down": "#bottom", "up": "#top", "north": "#side", "south": "#side",
        "west": "#side", "east": "#side"}},
    "minecraft:block/cube_column": {"parent": "minecraft:block/cube", "textures": {
        "particle": "#side", "down": "#end", "up": "#end", "north": "#side", "south": "#side",
        "west": "#side", "east": "#side"}},
    "minecraft:block/cross": {"cross": True},
}

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}


def _path(resource: str, kind: str, ext: str) -> str:
    namespace, _, name = resource.partition(":")
    if not name:
        namespace, name = "quantimium", namespace
    if namespace != "quantimium":
        raise FileNotFoundError(resource)
    return os.path.join(ASSETS, kind, name + ext)


_TEXTURE_CACHE: Dict[str, Image.Image] = {}


def texture(resource: str) -> Image.Image:
    if resource not in _TEXTURE_CACHE:
        try:
            img = Image.open(_path(resource, "textures", ".png")).convert("RGBA")
            if img.height > img.width:  # animated strip: first frame
                img = img.crop((0, 0, img.width, img.width))
        except FileNotFoundError:
            img = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
        _TEXTURE_CACHE[resource] = img
    return _TEXTURE_CACHE[resource]


def load_model(resource: str) -> dict:
    """The model with its parents folded in and texture references resolved."""
    chain = []
    current: Optional[str] = resource
    while current:
        if current in BUILTIN:
            data = BUILTIN[current]
        else:
            with open(_path(current, "models", ".json")) as handle:
                data = json.load(handle)
        chain.append(data)
        current = data.get("parent")
        if current and ":" not in current:
            current = "minecraft:" + current
    textures: Dict[str, str] = {}
    elements = None
    cross = False
    for data in reversed(chain):
        textures.update(data.get("textures", {}))
        if "elements" in data:
            elements = data["elements"]
        cross = cross or data.get("cross", False)

    def resolve(ref: str) -> Optional[str]:
        seen = 0
        while ref.startswith("#") and seen < 10:
            ref = textures.get(ref[1:], ref)
            seen += 1
        return None if ref.startswith("#") else ref

    return {"elements": elements or [], "resolve": resolve, "cross": cross,
            "cross_texture": resolve("#cross") if cross else None}


def _crossed_core(texture: str, centre: float, size: float) -> dict:
    """A still of CrossedCore: two cubes crossed about y and x, as close as a block model gets."""
    lo, hi = 8 - size / 2, 8 + size / 2
    faces = {face: {"texture": "#core", "uv": [0, 0, 16, 16]}
             for face in ("north", "south", "east", "west", "up", "down")}
    elements = [{"from": [lo, centre - size / 2, lo], "to": [hi, centre + size / 2, hi],
                 "rotation": {"origin": [8, centre, 8], "axis": axis, "angle": 45}, "faces": faces}
                for axis in ("y", "x")]
    return {"textures": {"core": texture}, "elements": elements}


# Cores the game draws in a block entity renderer (CrossedCore), stood in here as model parts:
# block -> (texture for the state, centre height and cube size in pixels).
BER_CORES = {
    "rift_stabiliser": (lambda props: "quantimium:block/rift_stabiliser_core_"
                        + ("lit" if props.get("beaming") == "true" else "idle"), 16 + 16 / 3, 4),
}


def models_for(block: str, props: Dict[str, str]) -> List[Tuple[str, int, int]]:
    """(model, x rotation, y rotation) for a block in a given state, from its blockstate file."""
    parts = _state_models(block, props)
    if block in BER_CORES:
        texture_for, centre, size = BER_CORES[block]
        texture = texture_for(props)
        name = "quantimium:preview/" + texture.split("/")[-1]
        BUILTIN[name] = _crossed_core(texture, centre, size)
        parts.append((name, 0, 0))
    return parts


def _state_models(block: str, props: Dict[str, str]) -> List[Tuple[str, int, int]]:
    with open(os.path.join(ASSETS, "blockstates", block + ".json")) as handle:
        state = json.load(handle)
    if "variants" in state:
        best = None
        for key, value in state["variants"].items():
            wanted = dict(part.split("=") for part in key.split(",") if part)
            if all(props.get(k) == v for k, v in wanted.items()):
                best = value
                break
        if best is None:
            best = next(iter(state["variants"].values()))
        if isinstance(best, list):
            best = best[0]
        return [(best["model"], best.get("x", 0), best.get("y", 0))]
    parts = []
    for part in state["multipart"]:
        when = part.get("when")
        if when is None or all(str(props.get(k, "")) in str(v).split("|") for k, v in when.items()):
            apply = part["apply"][0] if isinstance(part["apply"], list) else part["apply"]
            parts.append((apply["model"], apply.get("x", 0), apply.get("y", 0)))
    return parts


# ---- Geometry ----

@dataclass
class Face:
    corners: Tuple[Tuple[float, float, float], ...]   # world-space, in texture order: (u0v0, u1v0, u1v1, u0v1)
    texture: Image.Image
    uv: Tuple[float, float, float, float]
    shade: float
    depth: float


def _auto_uv(face: str, f: Sequence[float], t: Sequence[float]) -> Tuple[float, float, float, float]:
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "up": (x0, z0, x1, z1),
        "down": (x0, 16 - z1, x1, 16 - z0),
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
        "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0),
        "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
    }[face]


def _face_corners(face: str, f: Sequence[float], t: Sequence[float]):
    """Corners in texture order (top-left, top-right, bottom-right, bottom-left), in model pixels."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "up": ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)),
        "down": ((x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)),
        "north": ((x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)),
        "south": ((x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)),
        "west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)),
        "east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)),
    }[face]


_TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}
_TIP = {"up": "north", "north": "down", "down": "south", "south": "up", "east": "east", "west": "west"}


def _rotate_x(point: Tuple[float, float, float], degrees: int) -> Tuple[float, float, float]:
    x, y, z = point
    for _ in range((degrees // 90) % 4):
        # A blockstate x rotation tips the model over about the x axis: its top ends up facing north,
        # as the game does it (a Decoherence Projector facing north is its upright model at x 90).
        y, z = z, 16 - y
    return (x, y, z)


def _rotate_y(point: Tuple[float, float, float], degrees: int) -> Tuple[float, float, float]:
    x, y, z = point
    for _ in range((degrees // 90) % 4):
        # A blockstate y rotation turns the model clockwise seen from above: north goes to east.
        x, z = 16 - z, x
    return (x, y, z)


def block_faces(block: str, props: Dict[str, str], at: Tuple[int, int, int]) -> List[Face]:
    faces: List[Face] = []
    for model_name, tip, turn in models_for(block, props):
        model = load_model(model_name)
        if model["cross"]:
            ref = model["cross_texture"]
            if ref:
                # A card through the middle, turned to face the camera.
                corners = ((0, 16, 16), (16, 16, 0), (16, 0, 0), (0, 0, 16))
                faces.append(_face(corners, texture(ref), (0, 0, 16, 16), 0.9, at))
            continue
        for element in model["elements"]:
            f, t = element["from"], element["to"]
            for name, spec in element.get("faces", {}).items():
                ref = model["resolve"](spec["texture"])
                if not ref:
                    continue
                uv = tuple(spec["uv"]) if "uv" in spec else _auto_uv(name, f, t)
                corners = tuple(_rotate_y(_rotate_x(c, tip), turn) for c in _face_corners(name, f, t))
                direction = name
                for _ in range((tip // 90) % 4):
                    direction = _TIP[direction]
                for _ in range((turn // 90) % 4):
                    direction = _TURN[direction]
                if direction in ("north", "west", "down"):
                    continue  # never faces the camera
                faces.append(_face(corners, texture(ref), uv, SHADE[direction], at))
    return faces


def _face(corners, tex, uv, shade, at) -> Face:
    world = tuple((at[0] + c[0] / 16, at[1] + c[1] / 16, at[2] + c[2] / 16) for c in corners)
    cx = sum(c[0] for c in world) / 4
    cy = sum(c[1] for c in world) / 4
    cz = sum(c[2] for c in world) / 4
    return Face(world, tex, uv, shade, cx + cz + cy * 0.9)


# ---- Projection and drawing ----

COS30 = math.cos(math.radians(30))


def project(point: Tuple[float, float, float], scale: float) -> Tuple[float, float]:
    x, y, z = point
    return ((x - z) * COS30 * scale, (x + z) * 0.5 * scale - y * scale)


def draw_faces(faces: List[Face], scale: float, margin: int = 24) -> Image.Image:
    if not faces:
        return Image.new("RGBA", (64, 64), BACKGROUND)
    points = [project(c, scale) for face in faces for c in face.corners]
    min_x = min(p[0] for p in points)
    min_y = min(p[1] for p in points)
    max_x = max(p[0] for p in points)
    max_y = max(p[1] for p in points)
    width = int(max_x - min_x) + margin * 2
    height = int(max_y - min_y) + margin * 2
    img = Image.new("RGBA", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(img, "RGBA")
    offset = (margin - min_x, margin - min_y)
    for face in sorted(faces, key=lambda f: f.depth):
        _draw_face(draw, face, scale, offset)
    return img


def _draw_face(draw: ImageDraw.ImageDraw, face: Face, scale: float, offset: Tuple[float, float]) -> None:
    """One quad per texel of the face's UV rectangle, bilinearly placed between its corners."""
    tex = face.texture
    u0, v0, u1, v1 = face.uv
    su = tex.width / 16
    sv = tex.height / 16
    columns = max(1, int(round(abs(u1 - u0))))
    rows = max(1, int(round(abs(v1 - v0))))
    c = [project(p, scale) for p in face.corners]
    c = [(p[0] + offset[0], p[1] + offset[1]) for p in c]

    def at(s: float, t: float) -> Tuple[float, float]:
        top = (c[0][0] + (c[1][0] - c[0][0]) * s, c[0][1] + (c[1][1] - c[0][1]) * s)
        bottom = (c[3][0] + (c[2][0] - c[3][0]) * s, c[3][1] + (c[2][1] - c[3][1]) * s)
        return (top[0] + (bottom[0] - top[0]) * t, top[1] + (bottom[1] - top[1]) * t)

    for j in range(rows):
        for i in range(columns):
            u = u0 + (u1 - u0) * (i + 0.5) / columns
            v = v0 + (v1 - v0) * (j + 0.5) / rows
            px = min(tex.width - 1, max(0, int(u * su)))
            py = min(tex.height - 1, max(0, int(v * sv)))
            r, g, b, a = tex.getpixel((px, py))
            if a < 16:
                continue
            colour = (int(r * face.shade), int(g * face.shade), int(b * face.shade), a)
            s0, s1 = i / columns, (i + 1) / columns
            t0, t1 = j / rows, (j + 1) / rows
            quad = [at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)]
            draw.polygon(quad, fill=colour, outline=colour)


# ---- Sheets ----

Placement = Tuple[Tuple[int, int, int], str, Dict[str, str]]


def render_scene(blocks: Sequence[Placement], scale: float = 40) -> Image.Image:
    faces: List[Face] = []
    for pos, block, props in blocks:
        faces.extend(block_faces(block, props, pos))
    return draw_faces(faces, scale)


def labelled(img: Image.Image, text: str) -> Image.Image:
    out = Image.new("RGBA", (img.width, img.height + 16), BACKGROUND)
    out.paste(img, (0, 0))
    ImageDraw.Draw(out).text((6, img.height), text, fill=LABEL)
    return out


def grid(tiles: Sequence[Image.Image], columns: int, title: str) -> Image.Image:
    cell_w = max(t.width for t in tiles)
    cell_h = max(t.height for t in tiles)
    rows = (len(tiles) + columns - 1) // columns
    sheet = Image.new("RGBA", (cell_w * columns, cell_h * rows + 28), BACKGROUND)
    ImageDraw.Draw(sheet).text((8, 8), title, fill=LABEL)
    for i, tile in enumerate(tiles):
        sheet.paste(tile, ((i % columns) * cell_w, (i // columns) * cell_h + 28))
    return sheet


# Single blocks, by tier. (block, state, label)
CATALOGUE = {
    "low": [
        ("observation_chamber", {"facing": "north", "lit": "false"}, "observation"),
        ("observation_chamber", {"facing": "north", "lit": "true"}, "observation lit"),
        ("flux_detector", {"mode": "flux", "facing": "north", "powered": "false"}, "detector"),
        ("flux_detector", {"mode": "flux", "facing": "north", "powered": "true"}, "detector active"),
        ("basic_quantum_crafter", {}, "basic crafter"),
        ("quantum_exciter", {"facing": "north", "active": "false"}, "exciter"),
        ("quantum_exciter", {"facing": "north", "active": "true"}, "exciter active"),
        ("basic_anomaly_siphon", {"active": "false"}, "basic siphon"),
        ("basic_anomaly_siphon", {"active": "true"}, "basic siphon active"),
    ],
    "mid": [
        ("quantum_crafter", {}, "crafter"),
        ("quantum_simulator", {}, "simulator"),
        ("tesseract_stabilizer", {}, "tesseract stabilizer"),
        ("flux_suppressor", {"active": "false"}, "suppressor"),
        ("flux_suppressor", {"active": "true"}, "suppressor active"),
        ("decoherence_projector", {"active": "false"}, "projector"),
        ("decoherence_projector", {"active": "true"}, "projector active"),
    ],
    "high": [
        ("quantum_foundry_controller", {"formed": "true"}, "foundry controller"),
        ("anomaly_containment_hall", {"active": "true"}, "hall controller"),
        ("quantum_foundry_attunement_tank", {"formed": "true"}, "attunement tank"),
        ("rift_anchor", {}, "rift anchor"),
        ("rift_stabiliser", {"beaming": "true"}, "rift stabiliser"),
        ("stabilised_portal_frame", {}, "portal frame"),
        ("quantum_attuned_glass", {"formed": "false"}, "attuned glass"),
    ],
    "anomaly": [
        ("anomalite_lattice", {}, "lattice"),
        ("budding_anomalite", {"active": "false"}, "budding"),
        ("budding_anomalite", {"active": "true"}, "budding active"),
        ("anomalite_crystal", {"age": "0", "facing": "up"}, "crystal 0"),
        ("anomalite_crystal", {"age": "1", "facing": "up"}, "crystal 1"),
        ("anomalite_crystal", {"age": "2", "facing": "up"}, "crystal 2"),
        ("anomalite_crystal", {"age": "3", "facing": "up"}, "crystal 3"),
    ],
}


def _ring(y: int, cap: bool, centre: Optional[Tuple[str, Dict[str, str]]]) -> List[Placement]:
    """A formed 3×3 plinth ring around (0, y, 0): edges face outwards, corners by their north-west turn."""
    out: List[Placement] = []
    edges = {(0, -1): "north", (1, 0): "east", (0, 1): "south", (-1, 0): "west"}
    corners = {(-1, -1): "north", (1, -1): "east", (1, 1): "south", (-1, 1): "west"}
    base = {"formed": "true", "cap": str(cap).lower()}
    for (dx, dz), facing in edges.items():
        out.append(((dx, y, dz), "quantum_foundry_plinth", dict(base, role="edge", facing=facing)))
    for (dx, dz), facing in corners.items():
        out.append(((dx, y, dz), "quantum_foundry_plinth", dict(base, role="corner", facing=facing)))
    if centre:
        out.append(((0, y, 0), centre[0], centre[1]))
    else:
        out.append(((0, y, 0), "quantum_foundry_plinth", dict(base, role="centre")))
    return out


def _arms(y: int) -> List[Placement]:
    out: List[Placement] = []
    for dx, dz in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        axis = "z" if dx == 0 else "x"
        out.append(((dx * 2, y, dz * 2), "quantum_foundry_conduit", {"axis": axis, "formed": "true"}))
        out.append(((dx * 3, y, dz * 3), "quantum_foundry_pillar", {"formed": "true"}))
        out.append(((dx * 3, y + 1, dz * 3), "quantum_foundry_attunement_tank", {"formed": "true"}))
    return out


def scene_foundry() -> List[Placement]:
    return _ring(0, False, ("quantum_foundry_controller", {"formed": "true"})) + _arms(0)


def scene_hall() -> List[Placement]:
    blocks = _ring(0, False, ("anomaly_containment_hall", {"active": "true"}))
    for y in (1, 2, 3):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                corner = dx != 0 and dz != 0
                facing = {(0, -1): "south", (0, 1): "north", (-1, 0): "east", (1, 0): "west"}.get((dx, dz))
                if corner:
                    facing = {(-1, -1): "east", (1, -1): "south", (1, 1): "west", (-1, 1): "north"}[(dx, dz)]
                blocks.append(((dx, y, dz), "quantum_attuned_glass",
                               {"formed": "true", "corner": str(corner).lower(), "open_face": facing}))
    blocks += _ring(4, True, None)
    return blocks + _arms(0)


def scene_rift_array() -> List[Placement]:
    blocks: List[Placement] = [((0, 0, 0), "rift_anchor", {})]
    for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3), (2, 2), (-2, -2)):
        blocks.append(((dx, 0, dz), "rift_stabiliser", {"beaming": "true"}))
    return blocks


def connect_frames(blocks: List[Placement]) -> List[Placement]:
    """Sets each portal frame's six connection flags from its neighbours, as the block does in game."""
    frames = {pos for pos, block, _ in blocks if block == "stabilised_portal_frame"}
    steps = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
             "up": (0, 1, 0), "down": (0, -1, 0)}
    out: List[Placement] = []
    for pos, block, props in blocks:
        if block == "stabilised_portal_frame":
            props = {d: str((pos[0] + s[0], pos[1] + s[1], pos[2] + s[2]) in frames).lower()
                     for d, s in steps.items()}
        out.append((pos, block, props))
    return out


def scene_portal() -> List[Placement]:
    """
    A 4×5 frame standing in the x-y plane, facing south, the way a stabilised portal is built; and
    beside it a plus and a T laid on the ground, to show the pieces a portal never uses.
    """
    blocks: List[Placement] = []
    for x in range(4):
        for y in range(5):
            if x in (0, 3) or y in (0, 4):
                blocks.append(((x, y, 0), "stabilised_portal_frame", {}))
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        blocks.append(((6 + dx, 0, 3 + dz), "stabilised_portal_frame", {}))
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1)):
        blocks.append(((6 + dx, 0, 7 + dz), "stabilised_portal_frame", {}))
    return connect_frames(blocks)


def scene_tiers() -> List[Placement]:
    """One row per tier, low at the front: the stone should step from light stone to obsidian."""
    rows = [
        ["observation_chamber", "flux_detector", "quantum_exciter", "basic_anomaly_siphon", "basic_quantum_crafter"],
        ["quantum_crafter", "quantum_simulator", "tesseract_stabilizer", "flux_suppressor", "decoherence_projector"],
        ["quantum_foundry_controller", "anomaly_containment_hall", "rift_anchor", "rift_stabiliser",
         "stabilised_portal_frame"],
    ]
    active = {"active": "true", "lit": "true", "formed": "true", "facing": "north", "powered": "true",
              "mode": "flux"}
    blocks: List[Placement] = []
    for row, names in enumerate(rows):
        for column, name in enumerate(names):
            blocks.append(((column * 2, 0, (2 - row) * 2), name, active))
    return blocks


def scene_superposition() -> List[Placement]:
    """The ME Superposition Crafter offline, online and working."""
    return [((0, 0, 0), "me_superposition_crafter", {"link": "offline"}),
            ((2, 0, 0), "me_superposition_crafter", {"link": "online"}),
            ((4, 0, 0), "me_superposition_crafter", {"link": "active"})]


def scene_crafters() -> List[Placement]:
    """The Basic Quantum Crafter beside the Quantum Crafter."""
    return [((0, 0, 0), "basic_quantum_crafter", {}), ((2, 0, 0), "quantum_crafter", {})]


def scene_simulator() -> List[Placement]:
    """An engaged simulator: the containment field stands where the machine was."""
    return [((0, 0, 0), "quantum_simulator", {}), ((0, 1, 0), "quantum_containment", {})]


def scene_fold_chamber() -> List[Placement]:
    """A formed 9 x 3 x 9 Fold Chamber round a Foundry standing on the ground: core in the middle of
    the bottom front (north) edge, pylons on the corners, rails along the edges."""
    width, height, depth = 9, 3, 9
    half = (width - 1) // 2
    front, back = -half, half  # z of the front (north) and back faces, centred on the Foundry
    blocks: List[Placement] = []
    for y in range(height):
        for z in range(front, back + 1):
            for x in range(-half, half + 1):
                on_x = abs(x) == half
                on_y = y in (0, height - 1)
                on_z = z in (front, back)
                count = on_x + on_y + on_z
                if count < 2:
                    continue
                if count == 3:
                    blocks.append(((x, y, z), "fold_pylon", {"formed": "true"}))
                elif (x, y, z) == (0, 0, front):
                    blocks.append(((x, y, z), "fold_core", {"facing": "north", "formed": "true"}))
                else:
                    axis = "x" if not on_x else ("y" if not on_y else "z")
                    blocks.append(((x, y, z), "fold_rail", {"axis": axis, "formed": "true",
                                                            "cap_negative": "false", "cap_positive": "false"}))
    # Collars where a run keys into a pylon or the core.
    solid = {pos for pos, block, _ in blocks if block != "fold_rail"}
    out: List[Placement] = []
    for pos, block, props in blocks:
        if block == "fold_rail":
            i = "xyz".index(props["axis"])
            neg = tuple(c - (1 if k == i else 0) for k, c in enumerate(pos))
            plus = tuple(c + (1 if k == i else 0) for k, c in enumerate(pos))
            props = dict(props, cap_negative=str(neg in solid).lower(), cap_positive=str(plus in solid).lower())
        out.append((pos, block, props))
    return out + scene_foundry()


def scene_reactor_parts() -> List[Placement]:
    """A corner of a formed Reactor: plinth, a port of each kind, and the Horizon Core's cage."""
    blocks: List[Placement] = []
    def traces(x, z):
        return {"trace_north": str(trace_edge(x, 64, z, 0)), "trace_south": str(trace_edge(x, 64, z + 1, 0)),
                "trace_west": str(trace_edge(x, 64, z, 1)), "trace_east": str(trace_edge(x + 1, 64, z, 1))}
    ports = {(3, 0): "reactor_input_port", (3, 1): "reactor_output_port", (3, 2): "reactor_energy_port",
             (3, 3): "reactor_materialiser_port"}
    for x in range(4):
        for z in range(4):
            block = ports.get((x, z), "reactor_plinth")
            blocks.append(((x, 0, z), block, dict(traces(x, z), formed="true")))
    blocks.append(((1, 1, 1), "horizon_core", {"formed": "true", "seated": "true"}))
    return blocks


def trace_edge(x: int, y: int, z: int, orientation: int) -> int:
    """ReactorTraces' edge function, for previews."""
    def h(*values):
        v = 0x9e3779b9
        for value in values:
            v = ((v ^ (value & 0xffffffff)) * 0x85ebca6b) & 0xffffffff
            v ^= v >> 13
        v = (v * 0xc2b2ae35) & 0xffffffff
        return v ^ (v >> 16)
    def pick(r):
        roll = r % 1000
        return 0 if roll < 650 else (1 if roll < 825 else 2)
    if orientation == 0:
        return pick(h(x, y, (z + h(x, y, 11) % 6) // 6, 0))
    return pick(h(z, y, (x + h(z, y, 13) % 6) // 6, 1))


def scene_stabilizer() -> List[Placement]:
    return [((0, 0, 0), "tesseract_stabilizer", {})]


SCENES = {
    "foundry": scene_foundry,
    "crafters": scene_crafters,
    "superposition": scene_superposition,
    "simulator": scene_simulator,
    "stabilizer": scene_stabilizer,
    "hall": scene_hall,
    "fold_chamber": scene_fold_chamber,
    "reactor_parts": scene_reactor_parts,
    "rift_array": scene_rift_array,
    "portal": scene_portal,
    "tiers": scene_tiers,
}


def main() -> None:
    parser = argparse.ArgumentParser(description="Isometric preview of Quantimium blocks")
    parser.add_argument("--scene", help="render only this scene")
    parser.add_argument("--list", action="store_true", help="list scene names")
    args = parser.parse_args()
    if args.list:
        print("\n".join(["catalogue"] + sorted(SCENES)))
        return
    os.makedirs(OUT, exist_ok=True)
    if not args.scene or args.scene == "catalogue":
        for tier, entries in CATALOGUE.items():
            tiles = [labelled(render_scene([((0, 0, 0), block, props)], 96), label)
                     for block, props, label in entries]
            path = os.path.join(OUT, f"catalogue_{tier}.png")
            grid(tiles, 5, f"{tier} tier").save(path)
            print(f"wrote {os.path.relpath(path, ROOT)}")
    for name, build in SCENES.items():
        if args.scene and args.scene != name:
            continue
        path = os.path.join(OUT, f"scene_{name}.png")
        render_scene(build(), 72).save(path)
        print(f"wrote {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    main()
