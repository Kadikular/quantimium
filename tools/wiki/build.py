#!/usr/bin/env python3
"""
Builds the Quantimium wiki: hand-written pages from wiki/pages/ plus pages generated from the mod
itself, assembled into wiki/build/docs/ and then built by MkDocs into wiki/build/site/.

    ./tools/wiki/build.py            # generate and build
    ./tools/wiki/build.py --serve    # generate, then serve with live reload at http://127.0.0.1:8000
    ./tools/wiki/build.py --check    # generate only; fail on unknown constants or broken links

Run inside the docs virtualenv (see wiki/README.md), which has MkDocs Material and Pillow.

What is generated, so it can never drift from the mod:
  * a page per block and item, with its icon (rendered from the real model), name, description and
    status (from CONTENT.md), every recipe that makes it and every recipe that uses it;
  * recipe grids for crafting and Quantum Foundry recipes;
  * a constants reference, read out of the Java source;
  * the mechanics coverage report, from [[mechanic:...]] markers in pages and `covers:` comments in
    tests;
  * the tick-timing report, from wiki/data/tick-timing.json, which the game tests write.

Hand-written pages can use:
  {{c:Class.NAME}}         a constant from the Java source, e.g. {{c:Veiled.AREA}}
  {{c:Class.NAME|s}}       ticks shown as seconds ("8 s"); |min for minutes; |x20 for per-second
  [[mechanic:some.id]]     declares a documented mechanic; shows whether a test covers it
  [[item:id]] [[block:id]] a link to a catalogue page, with its icon
  [[render:scene]]         an isometric render from tools/block_preview.py (a scene name)
  [[structure:name]]       a multiblock in 3D, in the in-game guide only (GuideStructures exports it)
  [[recipes:ns:type]]      every recipe of one type, drawn
A page in wiki/pages/blocks/<id>.md or wiki/pages/items/<id>.md is merged into that generated page.
"""

from __future__ import annotations

import argparse
import glob
import html
import io
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from typing import Dict, List, Optional, Tuple

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STRUCTURES = os.path.join(ROOT, "wiki", "structures")
WIKI = os.path.join(ROOT, "wiki")
PAGES = os.path.join(WIKI, "pages")
BUILD = os.path.join(WIKI, "build")
DOCS = os.path.join(BUILD, "docs")
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "quantimium")
DATA = os.path.join(RES, "data", "quantimium")
JAVA = os.path.join(ROOT, "src", "main", "java")

sys.path.insert(0, os.path.join(ROOT, "tools"))
from PIL import Image, ImageDraw  # noqa: E402

import block_preview  # noqa: E402

ERRORS: List[str] = []


def error(message: str) -> None:
    ERRORS.append(message)


# ---- Mod data ----

def lang() -> Dict[str, str]:
    with open(os.path.join(ASSETS, "lang", "en_us.json")) as handle:
        return json.load(handle)


# Not real content: template leftovers, and blocks that only exist at runtime.
HIDDEN = {"example_block", "example_item", "veiled_spawn_egg", "debug_field_emitter"}
INTERNAL = {"quantum_containment", "stabilised_portal", "mirror_vine", "mirror_short_grass",
            "mirror_tall_grass", "mirror_dead_bush", "mirror_hanging_roots"}


def catalogue_entries(names: Dict[str, str]) -> Tuple[List[str], List[str]]:
    blocks = sorted(k.split(".")[2] for k in names if k.startswith("block.quantimium.") and k.count(".") == 2)
    items = sorted(k.split(".")[2] for k in names if k.startswith("item.quantimium.") and k.count(".") == 2)
    return [b for b in blocks if b not in HIDDEN], [i for i in items if i not in HIDDEN]


def content_rows() -> Dict[str, Tuple[str, str]]:
    """id → (description, status) from CONTENT.md's tables."""
    rows: Dict[str, Tuple[str, str]] = {}
    with open(os.path.join(ROOT, "CONTENT.md")) as handle:
        for line in handle:
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 4 or not cells[1].startswith("`"):
                continue
            ids = re.findall(r"`([a-z0-9_]+)`", cells[1])
            for block_id in ids:
                rows.setdefault(block_id, (cells[2], cells[3]))
    return rows


def recipes() -> List[dict]:
    out = []
    for path in sorted(glob.glob(os.path.join(DATA, "recipe", "*.json"))):
        with open(path) as handle:
            data = json.load(handle)
        data["_id"] = os.path.basename(path)[:-5]
        out.append(data)
    return out


def recipe_result(recipe: dict) -> Tuple[Optional[str], int]:
    result = recipe.get("result", {})
    return result.get("id") or result.get("item"), result.get("count", 1)


def recipe_inputs(recipe: dict) -> List[str]:
    found: List[str] = []

    def walk(node):
        if isinstance(node, dict):
            if "item" in node:
                found.append(node["item"])
            if "tag" in node:
                found.append("#" + node["tag"])
            for key, value in node.items():
                if key not in ("result",):
                    walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    for key in ("key", "ingredients"):
        walk(recipe.get(key))
    return found


# ---- Constants ----

CONSTANT = re.compile(r"static\s+final\s+(int|long|double|float)\s+([A-Z][A-Z0-9_]*)\s*=\s*([^;]+);")


def constants() -> Dict[str, float]:
    """Class.NAME → value for every numeric literal constant in the mod, arithmetic included."""
    values: Dict[str, float] = {}
    for path in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True):
        cls = os.path.basename(path)[:-5]
        with open(path) as handle:
            source = handle.read()
        for kind, name, expression in CONSTANT.findall(source):
            text = re.sub(r"(?<=\d)_(?=\d)", "", expression.strip())
            text = re.sub(r"(?<=[\d.])[LlFfDd]\b", "", text)
            if not re.fullmatch(r"[\d.\s+\-*/()]+", text):
                continue
            try:
                value = eval(text, {"__builtins__": {}})  # arithmetic on literals only, checked above
            except Exception:
                continue
            key = f"{cls}.{name}"
            values[key] = float(value) if kind in ("double", "float") else int(value)
    return values


def format_constant(value: float, unit: str) -> str:
    def trim(number: float) -> str:
        text = f"{number:,.2f}".rstrip("0").rstrip(".")
        return text
    if unit == "s":
        return f"{trim(value / 20)} s"
    if unit == "min":
        return f"{trim(value / 1200)} min"
    if unit == "x20":
        return trim(value * 20)
    if unit == "pct":
        return f"{trim(value * 100)}%"
    return trim(value) if isinstance(value, float) else f"{value:,}"


# ---- Coverage ----

MECHANIC = re.compile(r"\[\[mechanic:([a-z0-9_.\-]+)\]\]")


def covered_mechanics() -> Dict[str, List[str]]:
    """mechanic id → tests that declare `covers: id` in a comment."""
    covered: Dict[str, List[str]] = {}
    for path in glob.glob(os.path.join(ROOT, "src", "**", "*.java"), recursive=True):
        with open(path) as handle:
            source = handle.read()
        # One line only, so a covers: comment cannot run on into the code under it.
        for match in re.finditer(r"covers:[ \t]*([a-z0-9_.,\- \t]+)", source):
            test = os.path.relpath(path, ROOT)
            for mechanic in re.split(r"[,\s]+", match.group(1).strip()):
                if mechanic:
                    covered.setdefault(mechanic, []).append(test)
    return covered


# ---- Icons ----

ICON = 64


def _client_jar() -> Optional[zipfile.ZipFile]:
    """The game's own jar, for vanilla's textures: the one ./gradlew createMinecraftArtifacts makes
    for the Minecraft version in gradle.properties."""
    with open(os.path.join(ROOT, "gradle.properties")) as handle:
        version = re.search(r"^minecraft_version=(.+)$", handle.read(), re.M).group(1).strip()
    jars = sorted(glob.glob(os.path.join(ROOT, "build", "moddev", "artifacts", f"minecraft-patched-{version}.*.jar")))
    jars = [j for j in jars if not j.endswith("-sources.jar")]
    if not jars:
        error(f"no Minecraft {version} jar: run ./gradlew createMinecraftArtifacts first")
    return zipfile.ZipFile(jars[0])


VANILLA = _client_jar()


def _fit(img: Image.Image, size: int = ICON) -> Image.Image:
    img = img.convert("RGBA")
    box = img.getbbox()
    if box:
        img = img.crop(box)
    scale = min(size / img.width, size / img.height)
    if img.width <= 16 and img.height <= 16:
        scale = size // 16
    resized = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.NEAREST)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(resized, ((size - resized.width) // 2, (size - resized.height) // 2))
    return out


def _render_block(block: str) -> Optional[Image.Image]:
    state = {"formed": "true", "active": "true", "lit": "true", "facing": "north", "age": "3", "mode": "flux",
             "powered": "true", "link": "active"}
    if block == "anomalite_crystal":
        state["facing"] = "up"
    if block in ("harvest_laser", "decoherence_projector"):
        # Pointing at the viewer: the muzzle or orb end is what reads.
        state["facing"] = "south" if block == "harvest_laser" else "up"
    if block == "rift_lens":
        # Open, standing on a floor.
        state.update({"axis": "z", "open": "true", "down": "true"})
    try:
        faces = block_preview.block_faces(block, state, (0, 0, 0))
    except (FileNotFoundError, KeyError):
        return None
    if not faces:
        return None
    img = block_preview.draw_faces(faces, 40, margin=2)
    # The renderer paints a background; drop it so icons sit on the page.
    background = block_preview.BACKGROUND[:3]
    pixels = img.load()
    for y in range(img.height):
        for x in range(img.width):
            if pixels[x, y][:3] == background:
                pixels[x, y] = (0, 0, 0, 0)
    return img


def _tesseract_icon() -> Image.Image:
    """Tesseract items are drawn by an item renderer, so they get a simple wire cube here."""
    img = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    azure, core = (52, 133, 255, 255), (127, 178, 255, 255)
    for inset, colour in ((8, azure), (22, core)):
        a, b = inset, ICON - inset
        draw.rectangle([a, a, b, b], outline=colour, width=2)
    for x, y in ((8, 8), (56, 8), (8, 56), (56, 56)):
        draw.line([x, y, 22 if x < 32 else 42, 22 if y < 32 else 42], fill=azure, width=2)
    return img


def icon_for(resource: str) -> Optional[Image.Image]:
    """An icon for quantimium:x or minecraft:x, from its model or texture."""
    namespace, _, name = resource.partition(":")
    if namespace == "quantimium":
        model_path = os.path.join(ASSETS, "models", "item", name + ".json")
        if os.path.exists(model_path):
            with open(model_path) as handle:
                model = json.load(handle)
            parent = model.get("parent", "")
            # Follow item-model parents (the Semi-Stable Tesseract inherits the Tesseract's).
            while parent.startswith("quantimium:item/") and "layer0" not in model.get("textures", {}):
                with open(os.path.join(ASSETS, "models", "item", parent.split("/", 1)[1] + ".json")) as handle:
                    model = json.load(handle)
                parent = model.get("parent", "")
            if parent == "builtin/entity":
                return _tesseract_icon()
            layer = model.get("textures", {}).get("layer0")
            if layer:
                texture = os.path.join(ASSETS, "textures", layer.split(":")[1] + ".png")
                if os.path.exists(texture):
                    return _fit(Image.open(texture).crop((0, 0, 16, 16)))
        if os.path.exists(os.path.join(ASSETS, "blockstates", name + ".json")):
            rendered = _render_block(name)
            if rendered:
                return _fit(rendered)
        return None
    if namespace == "minecraft" and VANILLA:
        for folder in ("item", "block"):
            entry = f"assets/minecraft/textures/{folder}/{name}.png"
            if entry in VANILLA.namelist():
                img = Image.open(io.BytesIO(VANILLA.read(entry)))
                return _fit(img.crop((0, 0, 16, 16)))
    return None


ICON_CACHE: Dict[str, Optional[str]] = {}


def icon_path(resource: str) -> Optional[str]:
    """Writes the icon once and returns its path under docs/, or None when there is no art."""
    if resource in ICON_CACHE:
        return ICON_CACHE[resource]
    img = icon_for(resource)
    rel = None
    if img:
        rel = f"assets/icons/{resource.replace(':', '/')}.png"
        target = os.path.join(DOCS, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        img.save(target)
    ICON_CACHE[resource] = rel
    return rel


# ---- Markdown helpers ----

class Site:
    def __init__(self) -> None:
        self.names = lang()
        self.blocks, self.items = catalogue_entries(self.names)
        self.rows = content_rows()
        self.recipes = recipes()
        self.constants = constants()
        self.mechanics: Dict[str, List[str]] = {}   # id → pages that document it
        self.covered = covered_mechanics()

    def display(self, resource: str) -> str:
        namespace, _, name = resource.partition(":")
        if resource.startswith("#"):
            return "any " + resource[1:]
        if namespace == "quantimium":
            return self.names.get(f"block.quantimium.{name}") or self.names.get(f"item.quantimium.{name}") or name
        return name.replace("_", " ").title()

    def page_for(self, resource: str) -> Optional[str]:
        namespace, _, name = resource.partition(":")
        if namespace != "quantimium":
            return None
        if name in self.blocks:
            return f"blocks/{name}.md"
        if name in self.items:
            return f"items/{name}.md"
        return None

    def link(self, resource: str, here: str, size: int = 24) -> str:
        """An icon and a name, linked to its page when it has one. `here` is the linking page's path."""
        icon = None if resource.startswith("#") else icon_path(resource)
        label = html.escape(self.display(resource))
        img = f'<img class="qicon" src="{rel(here, icon)}" width="{size}" height="{size}" alt="">' if icon else ""
        page = self.page_for(resource)
        if page:
            return f'<a class="qlink" href="{rel(here, page.replace(".md", ".html"))}">{img}{label}</a>'
        return f'<span class="qlink">{img}{label}</span>'


def rel(here: str, target: str) -> str:
    return os.path.relpath(target, os.path.dirname(here) or ".")


def slot(site: Site, ingredient, here: str, count: int = 1) -> str:
    if not ingredient:
        return '<div class="qslot"></div>'
    # Since 1.21.2 an ingredient is usually just its id, "#tag", or a list of either: show the first.
    if isinstance(ingredient, list):
        ingredient = ingredient[0]
    if isinstance(ingredient, str):
        resource = ingredient
    else:
        resource = ingredient.get("item") or ("#" + ingredient["tag"] if "tag" in ingredient else "")
    icon = None if resource.startswith("#") else icon_path(resource)
    title = html.escape(site.display(resource))
    inner = f'<img src="{rel(here, icon)}" alt="{title}">' if icon else f'<span class="qtext">{title}</span>'
    page = site.page_for(resource)
    if page:
        inner = f'<a href="{rel(here, page.replace(".md", ".html"))}">{inner}</a>'
    badge = f'<span class="qcount">{count}</span>' if count > 1 else ""
    return f'<div class="qslot" title="{title}">{inner}{badge}</div>'


def recipe_html(site: Site, recipe: dict, here: str) -> str:
    kind = recipe["type"]
    result, count = recipe_result(recipe)
    out = slot(site, {"item": result}, here, count) if result else ""
    if kind == "minecraft:crafting_shaped":
        pattern = recipe["pattern"]
        width = max(len(row) for row in pattern)
        cells = []
        for row in pattern:
            for ch in row.ljust(width):
                cells.append(slot(site, recipe["key"].get(ch) if ch != " " else None, here))
        grid = f'<div class="qgrid" style="grid-template-columns: repeat({width}, 40px)">{"".join(cells)}</div>'
        return f'<div class="qrecipe">{grid}<span class="qarrow">→</span>{out}</div>\n\n*Crafting table*'
    if kind == "minecraft:crafting_shapeless":
        cells = "".join(slot(site, i, here) for i in recipe["ingredients"])
        return f'<div class="qrecipe"><div class="qgrid qflow">{cells}</div><span class="qarrow">→</span>{out}</div>\n\n*Crafting table, shapeless*'
    if kind == "quantimium:quantum_foundry":
        cells = "".join(slot(site, e["ingredient"], here, e.get("count", 1)) for e in recipe["ingredients"])
        facts = (f'{recipe.get("minimum_pillars", 1)}+ attunement pillars · {recipe.get("minimum_flux_band", "low").title()}'
                 f' flux or higher · {recipe.get("duration", 0)} ticks ({recipe.get("duration", 0) / 20:g} s) at'
                 f' {recipe.get("fe_per_tick", 0)} FE/t · uses {recipe.get("flux_cost", 0):g} flux · adds'
                 f' {recipe.get("anomaly", 0):g} anomaly')
        return (f'<div class="qrecipe"><div class="qgrid qflow">{cells}</div><span class="qarrow">→</span>{out}</div>\n\n'
                f'*[Quantum Foundry](../multiblocks/quantum-foundry.md)* · {facts}')
    return f"*{kind} recipe `{recipe['_id']}`: not drawn yet*"


def render_scene(name: str) -> Optional[str]:
    build = block_preview.SCENES.get(name)
    if not build:
        error(f"unknown scene [[render:{name}]]")
        return None
    rel_path = f"assets/renders/{name}.png"
    target = os.path.join(DOCS, rel_path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    block_preview.render_scene(build(), 48).save(target)
    return rel_path


CODE = re.compile(r"```.*?```|`[^`\n]+`", re.S)


def expand(site: Site, text: str, here: str) -> str:
    """Substitutes markup outside code, so a page can show the markup itself in backticks."""
    out, last = [], 0
    for match in CODE.finditer(text):
        out.append(_expand(site, text[last:match.start()], here))
        out.append(match.group(0))
        last = match.end()
    out.append(_expand(site, text[last:], here))
    return "".join(out)


def _expand(site: Site, text: str, here: str) -> str:
    """Substitutes constants, mechanic badges, links and renders in a hand-written page."""
    def constant(match: re.Match) -> str:
        key, unit = match.group(1), match.group(2) or ""
        if key not in site.constants:
            error(f"{here}: unknown constant {key}")
            return f"**??{key}??**"
        return format_constant(site.constants[key], unit)

    text = re.sub(r"\{\{c:([A-Za-z0-9_]+\.[A-Z0-9_]+)(?:\|(\w+))?\}\}", constant, text)

    def mechanic(match: re.Match) -> str:
        mechanic_id = match.group(1)
        site.mechanics.setdefault(mechanic_id, []).append(here)
        tests = site.covered.get(mechanic_id)
        state = "tested" if tests else "untested"
        link = rel(here, "reference/coverage.html")
        return f'<a class="qmech qmech-{state}" href="{link}#{mechanic_id.replace(".", "-")}" title="{mechanic_id}">{state}</a>'

    text = MECHANIC.sub(mechanic, text)
    text = re.sub(r"\[\[(item|block):([a-z0-9_]+)\]\]",
                  lambda m: site.link("quantimium:" + m.group(2), here), text)

    def scene(match: re.Match) -> str:
        path = render_scene(match.group(1))
        return f'![{match.group(1)}]({rel(here, path)}){{ .qrender }}' if path else ""

    text = re.sub(r"\[\[render:([a-z_]+)\]\]", scene, text)

    def structure(match: re.Match) -> str:
        if not os.path.exists(os.path.join(STRUCTURES, match.group(1) + ".snbt")):
            error(f"{here}: no structure {match.group(1)}; run ./gradlew runGameTestServer -PexportStructures")
        return ""

    # The in-game guide shows these in 3D; the website has its renders.
    text = re.sub(r"\[\[structure:([a-z_]+)\]\]\n?", structure, text)

    def recipe_list(match: re.Match) -> str:
        kind = match.group(1)
        found = [r for r in site.recipes if r["type"] == kind]
        if not found:
            error(f"{here}: no recipes of type {kind}")
        return "\n\n".join(recipe_html(site, r, here) for r in found)

    return re.sub(r"\[\[recipes:([a-z_]+:[a-z_]+)\]\]", recipe_list, text)


# ---- Pages ----

def write(path: str, text: str) -> None:
    target = os.path.join(DOCS, path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w") as handle:
        handle.write(text)


def overlay(site: Site, kind: str, name: str, here: str) -> str:
    path = os.path.join(PAGES, kind, name + ".md")
    if not os.path.exists(path):
        return ""
    with open(path) as handle:
        return expand(site, handle.read(), here)


def status_badge(status: str) -> str:
    plain = re.sub(r"[*`]", "", status)
    word = plain.split()[0].lower() if plain else "unknown"
    return f'<span class="qstatus qstatus-{html.escape(word)}">{html.escape(plain)}</span>'


def catalogue_page(site: Site, kind: str, name: str) -> None:
    here = f"{kind}/{name}.md"
    resource = f"quantimium:{name}"
    title = site.display(resource)
    description, status = site.rows.get(name, ("", ""))
    icon = icon_path(resource)
    lines = [f"# {title}", ""]
    info = [f'<div class="qinfo">']
    if icon:
        info.append(f'<img class="qinfo-icon" src="{rel(here, icon)}" alt="">')
    info.append('<table>')
    info.append(f"<tr><th>ID</th><td><code>quantimium:{name}</code></td></tr>")
    info.append(f"<tr><th>Type</th><td>{'Block' if kind == 'blocks' else 'Item'}</td></tr>")
    if name in INTERNAL:
        info.append("<tr><th>Obtainable</th><td>No (placed by the game)</td></tr>")
    if status:
        info.append(f"<tr><th>Status</th><td>{status_badge(status)}</td></tr>")
    info.append("</table></div>")
    lines.append("\n".join(info))
    lines.append("")
    tooltip = site.names.get(f"item.quantimium.{name}.tooltip")
    if description:
        lines += [re.sub(r"\*\*", "**", description), ""]
    if tooltip:
        lines += [f"> *In game:* {tooltip}", ""]
    extra = overlay(site, kind, name, here)
    if extra:
        lines += [extra, ""]
    made = [r for r in site.recipes if recipe_result(r)[0] == resource]
    used = [r for r in site.recipes if resource in recipe_inputs(r)]
    if made:
        lines += ["## Recipe" + ("s" if len(made) > 1 else ""), ""]
        for recipe in made:
            lines += [recipe_html(site, recipe, here), ""]
    if used:
        lines += ["## Used in", ""]
        seen = set()
        for recipe in used:
            result = recipe_result(recipe)[0]
            if result and result not in seen:
                seen.add(result)
                lines.append(f"- {site.link(result, here)}")
        lines.append("")
    write(here, "\n".join(lines))


GROUPS = [
    ("Low-tier machines", ["observation_chamber", "flux_detector", "basic_quantum_crafter", "quantum_exciter",
                           "anomaly_containment"]),
    ("Mid-tier machines", ["quantum_crafter", "quantum_simulator", "tesseract_stabilizer", "flux_suppressor",
                           "decoherence_projector", "stabilised_portal_frame"]),
    ("Multiblocks and high tier", ["quantum_foundry_controller", "quantum_foundry_plinth", "quantum_foundry_conduit",
                                   "quantum_foundry_pillar", "quantum_foundry_attunement_tank",
                                   "anomaly_containment_hall", "quantum_attuned_glass", "rift_anchor",
                                   "rift_stabiliser"]),
    ("World and anomaly", ["unrealised_ore", "anomalite_crystal", "budding_anomalite", "anomalite_lattice"]),
    ("Internal", sorted(INTERNAL)),
]


def catalogue_index(site: Site, kind: str) -> None:
    here = f"{kind}/index.md"
    names = site.blocks if kind == "blocks" else site.items
    title = "Blocks" if kind == "blocks" else "Items"
    lines = [f"# {title}", "", f"Every {title.lower()[:-1]} in Quantimium, generated from the mod's own files.", ""]
    groups = GROUPS if kind == "blocks" else [("Items", names)]
    listed = set()
    for label, members in groups:
        members = [m for m in members if m in names]
        if not members:
            continue
        listed.update(members)
        lines += [f"## {label}", "", '<div class="qcards">']
        for name in members:
            lines.append(_card(site, name, here))
        lines += ["</div>", ""]
    rest = [n for n in names if n not in listed]
    if rest:
        lines += ["## Other", "", '<div class="qcards">'] + [_card(site, n, here) for n in rest] + ["</div>", ""]
    write(here, "\n".join(lines))


def _card(site: Site, name: str, here: str) -> str:
    resource = f"quantimium:{name}"
    icon = icon_path(resource)
    page = site.page_for(resource)
    img = f'<img src="{rel(here, icon)}" alt="">' if icon else '<span class="qnoicon"></span>'
    description = re.sub(r"[*`]", "", site.rows.get(name, ("", ""))[0])
    short = description.split(". ")[0][:110]
    return (f'<a class="qcard" href="{rel(here, page.replace(".md", ".html"))}">{img}'
            f'<span class="qcard-name">{html.escape(site.display(resource))}</span>'
            f'<span class="qcard-text">{html.escape(short)}</span></a>')


def constants_page(site: Site) -> None:
    lines = ["# Constants", "",
             "Every numeric constant in the mod's source, read at build time. Pages quote these with",
             "`{{c:Class.NAME}}`, so a balance change updates the wiki without anyone touching it.", "",
             "| Constant | Value |", "| --- | --- |"]
    for key in sorted(site.constants):
        lines.append(f"| `{key}` | {format_constant(site.constants[key], '')} |")
    write("reference/constants.md", "\n".join(lines) + "\n")


def coverage_page(site: Site) -> None:
    documented = sorted(site.mechanics)
    tested = [m for m in documented if m in site.covered]
    lines = ["# Mechanics coverage", "",
             "Every mechanic the wiki documents carries an id. A GameTest covers one by naming it in a",
             "comment: `// covers: veiled.release, veiled.area`. This page is regenerated each build;",
             "anything untested here is the test backlog.", "",
             f"**{len(tested)} of {len(documented)} documented mechanics are tested.**", "",
             "| Mechanic | Documented on | Tested by |", "| --- | --- | --- |"]
    for mechanic in documented:
        pages = ", ".join(sorted({f"[{p.split('/')[-1][:-3]}](../{p})" for p in site.mechanics[mechanic]}))
        tests = ", ".join(f"`{t.split('/')[-1]}`" for t in site.covered.get(mechanic, [])) or "—"
        anchor = mechanic.replace(".", "-")
        lines.append(f'| <span id="{anchor}"></span>`{mechanic}` | {pages} | {tests} |')
    orphans = sorted(set(site.covered) - set(documented))
    if orphans:
        lines += ["", "## Tests naming undocumented mechanics", ""] + [f"- `{m}`" for m in orphans]
    write("reference/coverage.md", "\n".join(lines) + "\n")


def tick_timing_page() -> None:
    """The tick timings the last game-test run measured (TickTimingTests), from wiki/data."""
    lines = ["# Tick timing", "",
             "How long each setup's block entity takes to tick, measured by the tick-timing game tests: the",
             "whole of the level's call into its ticker, as Spark or Observable would show it. Each scenario",
             "warms up, then records every tick; the median is the figure to watch, since a stray GC pause",
             "moves only the tail. A test fails if its median goes over its budget.", "",
             "Regenerated by `./gradlew runGameTestServer`; the numbers are from the machine that last ran",
             "it, so compare rows with each other rather than with another machine's.", ""]
    path = os.path.join(WIKI, "data", "tick-timing.json")
    if not os.path.exists(path):
        lines.append("No report yet: run the game tests.")
        write("reference/tick-timing.md", "\n".join(lines) + "\n")
        return
    with open(path) as handle:
        report = json.load(handle)
    lines += [f"Measured {report['date']} on {report['os']}, {report['cores']} cores, Java {report['java']}; "
              f"{report['warmupTicks']:,} ticks of warm-up, then {report['sampleTicks']:,} recorded.", ""]
    groups: Dict[str, List[dict]] = {}
    for result in report["results"]:
        groups.setdefault(result["group"], []).append(result)
    for group in sorted(groups):
        lines += [f"## {group}", "", "| Setup | Median µs/t | p90 µs/t | Budget µs/t | State |",
                  "| --- | ---: | ---: | ---: | --- |"]
        for result in sorted(groups[group], key=lambda r: r["id"]):
            lines.append(f"| {result['description']} <br>`{result['id']}` | **{result['medianMicros']:.1f}** | "
                         f"{result['p90Micros']:.1f} | {result['budgetMicros']:.0f} | {result['state']} |")
        lines.append("")
    write("reference/tick-timing.md", "\n".join(lines))


def copy_pages(site: Site) -> None:
    for path in glob.glob(os.path.join(PAGES, "**", "*"), recursive=True):
        if os.path.isdir(path):
            continue
        rel_path = os.path.relpath(path, PAGES)
        parts = rel_path.split(os.sep)
        if parts[0] in ("blocks", "items") and len(parts) == 2 and parts[1] != "index.md":
            continue  # merged into the generated page instead
        if path.endswith(".md"):
            with open(path) as handle:
                write(rel_path, expand(site, handle.read(), rel_path))
        else:
            target = os.path.join(DOCS, rel_path)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copy(path, target)


def veiled_portrait() -> None:
    """
    A front view of the Veiled, cut from its texture sheet by the model's own boxes (see
    VeiledModel): hood, body, robe and arms, with the glow layer on top.
    """
    textures = os.path.join(ASSETS, "textures", "entity")
    body = Image.open(os.path.join(textures, "veiled.png")).convert("RGBA")
    glow = Image.open(os.path.join(textures, "veiled_glow.png")).convert("RGBA")
    sheet = body.copy()
    sheet.alpha_composite(glow)
    # (texture x, y, width, height) of each front face, and where it sits (x from centre, y from top).
    parts = [((91, 3, 3, 26), (-9, 12)), ((103, 3, 3, 26), (6, 12)), ((50, 10, 14, 16), (-7, 30)),
             ((8, 29, 12, 19), (-6, 11)), ((10, 10, 10, 11), (-5, 0))]
    out = Image.new("RGBA", (22, 47), (0, 0, 0, 0))
    for (u, v, w, h), (x, y) in parts:
        out.alpha_composite(sheet.crop((u, v, u + w, v + h)), (11 + x, y))
    out = out.resize((out.width * 5, out.height * 5), Image.NEAREST)
    target = os.path.join(DOCS, "assets", "veiled.png")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    out.save(target)


def generate() -> Site:
    if os.path.exists(DOCS):
        shutil.rmtree(DOCS)
    os.makedirs(DOCS)
    site = Site()
    copy_pages(site)
    veiled_portrait()
    for name in site.blocks:
        catalogue_page(site, "blocks", name)
    for name in site.items:
        if name not in site.blocks:
            catalogue_page(site, "items", name)
    site.items = [i for i in site.items if i not in site.blocks]
    catalogue_index(site, "blocks")
    catalogue_index(site, "items")
    constants_page(site)
    tick_timing_page()
    coverage_page(site)   # last: every page has registered its mechanics by now
    return site


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the Quantimium wiki")
    parser.add_argument("--serve", action="store_true", help="serve with live reload after generating")
    parser.add_argument("--check", action="store_true", help="generate only, and fail on errors")
    args = parser.parse_args()
    site = generate()
    print(f"generated {len(site.blocks)} block and {len(site.items)} item pages, "
          f"{len(site.mechanics)} documented mechanics ({sum(1 for m in site.mechanics if m in site.covered)} tested), "
          f"{len(site.constants)} constants")
    if ERRORS:
        print("\n".join("error: " + e for e in ERRORS))
        raise SystemExit(1)
    if args.check:
        return
    mkdocs = [sys.executable, "-m", "mkdocs"]
    config = ["-f", os.path.join(WIKI, "mkdocs.yml")]
    if args.serve:
        subprocess.run(mkdocs + ["serve"] + config, check=True)
    else:
        subprocess.run(mkdocs + ["build", "--strict"] + config, check=True)
        print(f"site: {os.path.relpath(os.path.join(BUILD, 'site', 'index.html'), ROOT)}")


if __name__ == "__main__":
    main()
