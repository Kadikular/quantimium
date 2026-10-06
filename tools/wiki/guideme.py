#!/usr/bin/env python3
"""
Builds the in-game guide from the wiki: the same pages, converted for GuideME, written to
src/main/resources/assets/quantimium/guides/quantimium/guide/. GuideME is optional; the guide is
data-driven (assets/quantimium/guideme_guides/guide.json), so the mod needs no code for it.

    .venv-wiki/bin/python tools/wiki/guideme.py

Run it after changing the wiki and commit what it writes. What changes on the way:
  * {{c:...}} constants are filled in, [[mechanic:...]] badges dropped (the guide is for players),
    [[item:x]] and [[block:x]] become GuideME item links, [[render:...]] and [[recipes:...]] go;
  * admonitions become quotes, tabs become headings, the info boxes a table with the item's image;
  * block and item pages are generated as on the wiki, and show their recipes with GuideME's own
    <RecipesFor>, so they can be opened by holding the guide key over the item;
  * links to pages the guide leaves out (the reference section) keep their text only.
"""

from __future__ import annotations

import os
import re
import shutil
from typing import Dict, List, Optional

import yaml

import build
from build import CODE, INTERNAL, PAGES, ROOT, Site, format_constant

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "guides", "quantimium", "guide")
SKIPPED = ("reference/", "guides/writing-the-wiki.md", "blocks/", "items/")
CATEGORIES = {"Concepts": "concepts", "Mechanics": "mechanics", "Machines": "machines", "Multiblocks": "multiblocks"}


# Multiblock pages and their parts: the guide key over a part opens the multiblock's page, and the
# part's own page points there. Parts shared between multiblocks belong to the one that introduces them.
MULTIBLOCK_PARTS = {
    "multiblocks/quantum-foundry.md": ["quantum_foundry_controller", "quantum_foundry_plinth", "quantum_foundry_pillar",
                                       "quantum_foundry_conduit", "quantum_foundry_attunement_tank"],
    "multiblocks/containment-hall.md": ["anomaly_containment_hall"],
    "multiblocks/stabilised-rift.md": ["rift_anchor", "rift_stabiliser"],
    "multiblocks/fold-chamber.md": ["fold_core", "fold_pylon", "fold_rail"],
    "multiblocks/superposition-pod.md": ["superposition_pod", "pod_cradle", "pod_plating", "pod_charge_module",
                                         "pod_hardening_module", "pod_recovery_module", "pod_regeneration_module",
                                         "pod_relay_module", "pod_rescue_module", "pod_stash_module", "pod_ward_module",
                                         "unfolding_array", "array_pylon"],
    "multiblocks/reactor.md": ["horizon_core", "reactor_plinth", "ring_emitter", "catalyst_bay", "reactor_input_port",
                               "reactor_output_port", "reactor_energy_port", "reactor_materialiser_port",
                               "reactor_me_port"],
}
PART_OF = {part: page for page, parts in MULTIBLOCK_PARTS.items() for part in parts}


def structure_scene(name: str, here: str) -> str:
    """A multiblock in 3D that can be turned and zoomed, scaled to fit, from the exported structure."""
    path = os.path.join(build.STRUCTURES, name + ".snbt")
    with open(path) as handle:
        size = [int(n) for n in re.search(r"size: \[(\d+), (\d+), (\d+)\]", handle.read()).groups()]
    zoom = round(min(6.0, max(2.0, 20.0 / max(size))), 1)
    src = "../" * here.count("/") + f"assets/structures/{name}.snbt"
    return (f'<GameScene zoom="{zoom}" interactive={{true}}>\n  <ImportStructure src="{src}" />\n'
            f'  <IsometricCamera yaw="195" pitch="30" />\n</GameScene>')


def front(title: str, parent: Optional[str], position: int, icon: Optional[str] = None,
          item_ids: Optional[List[str]] = None) -> str:
    nav: Dict[str, object] = {"title": title}
    if parent:
        nav["parent"] = parent
    nav["position"] = position
    if icon:
        nav["icon"] = icon
    data: Dict[str, object] = {"navigation": nav}
    if item_ids:
        data["item_ids"] = item_ids
    return "---\n" + yaml.safe_dump(data, sort_keys=False, allow_unicode=True) + "---\n\n"


class Guide:
    def __init__(self) -> None:
        self.site = Site()
        self.pages: Dict[str, str] = {}      # path → text
        self.images: Dict[str, str] = {}     # guide path → source file
        self.catalogue = [n for n in self.site.blocks + self.site.items if n not in INTERNAL]
        self.catalogue = sorted(set(self.catalogue))

    # ---- markup ----

    def convert(self, text: str, here: str) -> str:
        text = self.blocks(text, here)
        out, last = [], 0
        for match in CODE.finditer(text):
            out.append(self.inline(text[last:match.start()], here))
            out.append(match.group(0))
            last = match.end()
        out.append(self.inline(text[last:], here))
        return re.sub(r"\n{3,}", "\n\n", "".join(out)).strip() + "\n"

    def blocks(self, text: str, here: str) -> str:
        """Admonitions, tabs, info boxes and card grids: the block-level markup GuideME doesn't know."""
        lines = text.split("\n")
        out: List[str] = []
        i = 0
        while i < len(lines):
            line = lines[i]
            admonition = re.match(r'^(!!!|\?\?\?\+?)\s+(\w+)(?:\s+"([^"]*)")?\s*$', line)
            tab = re.match(r'^===\s+"([^"]*)"\s*$', line)
            if admonition or tab:
                body, i = self.indented(lines, i + 1)
                if tab:
                    out += [f"#### {tab.group(1)}", ""] + body
                else:
                    title = admonition.group(3) or admonition.group(2).capitalize()
                    quoted = [f"> {b}" if b else ">" for b in body]
                    out += [f"> **{title}**", ">"] + quoted
                continue
            if line.strip() == '<div class="qinfo">':
                end = next(j for j in range(i, len(lines)) if lines[j].strip() == "</div>")
                out += self.info_box(lines[i + 1:end], here)
                i = end + 1
                continue
            if re.match(r"^\s*</?div\b[^>]*>\s*$", line):
                i += 1
                continue
            out.append(line)
            i += 1
        return "\n".join(out)

    @staticmethod
    def indented(lines: List[str], i: int):
        body: List[str] = []
        while i < len(lines) and (lines[i].startswith("    ") or not lines[i].strip()):
            if not lines[i].strip() and (i + 1 >= len(lines) or not lines[i + 1].startswith("    ")):
                break
            body.append(lines[i][4:])
            i += 1
        return body, i

    def info_box(self, lines: List[str], here: str) -> List[str]:
        out: List[str] = []
        for line in lines:
            icon = re.search(r'src="[^"]*/icons/quantimium/([a-z0-9_]+)\.png"', line)
            if icon:
                out += [f'<ItemImage id="quantimium:{icon.group(1)}" scale="2" />', ""]
        rows = [re.match(r"<tr><th>(.*?)</th><td>(.*?)</td></tr>", l.strip()) for l in lines]
        rows = [r for r in rows if r]
        if rows:
            out += ["| | |", "| --- | --- |"] + [f"| **{r.group(1)}** | {r.group(2)} |" for r in rows] + [""]
        return out

    def inline(self, text: str, here: str) -> str:
        site = self.site

        def constant(match: re.Match) -> str:
            key, unit = match.group(1), match.group(2) or ""
            if key not in site.constants:
                build.error(f"{here}: unknown constant {key}")
                return key
            return format_constant(site.constants[key], unit)

        text = re.sub(r"\{\{c:([A-Za-z0-9_]+\.[A-Z0-9_]+)(?:\|(\w+))?\}\}", constant, text)
        text = re.sub(r"\[\[mechanic:[a-z0-9_.\-]+\]\] ?", "", text)
        text = re.sub(r"\[\[(?:item|block):([a-z0-9_]+)\]\]", r'<ItemLink id="quantimium:\1" />', text)
        text = re.sub(r"\[\[structure:([a-z_]+)\]\]", lambda m: structure_scene(m.group(1), here), text)
        text = re.sub(r"\[\[(?:render|recipes):[a-z_:]+\]\]", "", text)
        text = re.sub(r"\{ ?\.[a-z-]+ ?\}", "", text)
        text = re.sub(r'<span class="[^"]*">(.*?)</span>', r"\1", text)
        text = re.sub(r'<a [^>]*href="([^"]+)"[^>]*>(.*?)</a>', lambda m: f"[{m.group(2)}]({m.group(1)})", text)

        def link(match: re.Match) -> str:
            label, target = match.group(1), match.group(2)
            if re.match(r"^[a-z]+:", target):
                return match.group(0)
            path = target.split("#")[0]
            if not path:
                return label
            path = re.sub(r"\.html$", ".md", path)
            resolved = os.path.normpath(os.path.join(os.path.dirname(here), path))
            if path.endswith(".md"):
                return f"[{label}]({path})" if resolved in self.pages else label
            source = os.path.join(PAGES, os.path.dirname(here), path)
            if not os.path.exists(source):
                return label
            self.images[resolved] = source
            return f"[{label}]({path})"

        return re.sub(r"(?<!!)\[([^\]]+)\]\(([^)\s]+)\)", link, re.sub(
            r"!\[([^\]]*)\]\(([^)\s]+)\)", lambda m: "!" + link(m) if "(" in link(m) else "", text))

    # ---- pages ----

    def written_pages(self) -> List[str]:
        with open(os.path.join(ROOT, "wiki", "mkdocs.yml")) as handle:
            # Only the nav: the rest of mkdocs.yml has tags safe_load refuses.
            nav = yaml.safe_load("nav:\n" + handle.read().split("\nnav:\n", 1)[1])["nav"]
        order: List[str] = []

        def walk(entries) -> None:
            for entry in entries:
                for value in entry.values():
                    if isinstance(value, list):
                        walk(value)
                    elif value.endswith(".md") and not value.startswith(SKIPPED) and value != "index.md":
                        order.append(value)
        walk(nav)
        return order

    def generate(self) -> None:
        written = self.written_pages()
        # Every page the guide will have, before any is converted, so links can be checked.
        for path in written:
            self.pages[path] = ""
        for name in self.catalogue:
            self.pages[self.catalogue_path(name)] = ""
        for folder in CATEGORIES.values():
            self.pages[f"{folder}/index.md"] = ""
        self.pages["blocks/index.md"] = self.pages["items/index.md"] = self.pages["guides/index.md"] = ""
        self.pages["index.md"] = ""

        with open(os.path.join(PAGES, "index.md")) as handle:
            self.pages["index.md"] = front("Quantimium", None, 0, "quantimium:unrealised_ore") + \
                self.convert(handle.read(), "index.md")

        parents = {folder: f"{folder}/index.md" for folder in CATEGORIES.values()}
        parents["guides"] = "guides/index.md"
        for position, path in enumerate(written):
            with open(os.path.join(PAGES, path)) as handle:
                text = handle.read()
            title = re.search(r"^# (.+)$", text, re.M)
            icon = re.search(r'src="[^"]*/icons/quantimium/([a-z0-9_]+)\.png"', text)
            parts = [f"quantimium:{part}" for part in MULTIBLOCK_PARTS.get(path, [])]
            self.pages[path] = front(title.group(1) if title else path, parents[path.split("/")[0]], position,
                                     f"quantimium:{icon.group(1)}" if icon else None, parts or None) + \
                self.convert(text, path)

        icons = {"concepts": "flux_meter", "mechanics": "rift_seed", "machines": "quantum_crafter",
                 "multiblocks": "quantum_foundry_controller", "guides": "quantimium_trace"}
        for position, (title, folder) in enumerate([("Guides", "guides")] + list(CATEGORIES.items())):
            children = [p for p in written if p.startswith(folder + "/")]
            links = [f"- [{self.title_of(p)}]({os.path.basename(p)})" for p in children]
            self.pages[f"{folder}/index.md"] = front(title, "index.md", position + 1, f"quantimium:{icons[folder]}") + \
                f"# {title}\n\n" + "\n".join(links) + "\n"

        for position, (kind, title, names) in enumerate([
                ("blocks", "Blocks", [n for n in self.catalogue if n in self.site.blocks]),
                ("items", "Items", [n for n in self.catalogue if n not in self.site.blocks])]):
            links = [f'- <ItemLink id="quantimium:{n}" />' for n in names]
            self.pages[f"{kind}/index.md"] = front(title, "index.md", 10 + position,
                                                   "quantimium:quantum_crafter" if kind == "blocks" else "quantimium:flux_meter") + \
                f"# {title}\n\n" + "\n".join(links) + "\n"
            for name_position, name in enumerate(names):
                self.pages[f"{kind}/{name}.md"] = self.catalogue_page(kind, name, name_position)

    def catalogue_path(self, name: str) -> str:
        return f"{'blocks' if name in self.site.blocks else 'items'}/{name}.md"

    def title_of(self, path: str) -> str:
        with open(os.path.join(PAGES, path)) as handle:
            match = re.search(r"^# (.+)$", handle.read(), re.M)
        return match.group(1) if match else path

    def catalogue_page(self, kind: str, name: str, position: int) -> str:
        here = f"{kind}/{name}.md"
        resource = f"quantimium:{name}"
        title = self.site.display(resource)
        description, _ = self.site.rows.get(name, ("", ""))
        lines = [f"# {title}", "", f'<ItemImage id="{resource}" scale="2" />', ""]
        whole = PART_OF.get(name)
        if whole:
            lines += [f"Part of the [{self.title_of(whole)}](../{whole}).", ""]
        # A hand-written page says it better; the one-line description is for pages without one.
        if description and not os.path.exists(os.path.join(PAGES, kind, name + ".md")):
            lines += [self.convert(description, here), ""]
        tooltip = self.site.names.get(f"item.quantimium.{name}.tooltip")
        if tooltip:
            lines += [f"> *In game:* {tooltip}", ""]
        overlay = os.path.join(PAGES, kind, name + ".md")
        if os.path.exists(overlay):
            with open(overlay) as handle:
                lines += [self.convert(handle.read(), here), ""]
        lines += ["## Recipes", "", f'<RecipesFor id="{resource}" />', ""]
        body = re.sub(r"\n{3,}", "\n\n", "\n".join(lines))
        # A multiblock's part opens the multiblock's page instead.
        return front(title, f"{kind}/index.md", position, resource, None if whole else [resource]) + body

    def write(self) -> None:
        if os.path.exists(OUT):
            shutil.rmtree(OUT)
        for path, text in self.pages.items():
            target = os.path.join(OUT, path)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "w") as handle:
                handle.write(text)
        for path, source in self.images.items():
            target = os.path.join(OUT, path)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copy(source, target)
        # The multiblocks in 3D (wiki/structures, exported by GuideStructures).
        if os.path.isdir(build.STRUCTURES):
            target = os.path.join(OUT, "assets", "structures")
            os.makedirs(target, exist_ok=True)
            for name in os.listdir(build.STRUCTURES):
                if name.endswith(".snbt"):
                    shutil.copy(os.path.join(build.STRUCTURES, name), os.path.join(target, name))


def main() -> None:
    guide = Guide()
    guide.generate()
    if build.ERRORS:
        print("\n".join("error: " + e for e in build.ERRORS))
        raise SystemExit(1)
    guide.write()
    print(f"guide: {len(guide.pages)} pages, {len(guide.images)} images in {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
