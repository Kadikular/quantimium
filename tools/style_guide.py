#!/usr/bin/env python3
"""
Refreshes STYLE_GUIDE.html from the repo: palettes, tiers, open findings, and the texture status
table with every texture's current image embedded.

    ./tools/style_guide.py

The page's layout and script stay as they are; this rewrites its data blocks and a few lines of copy.
Rerun it after any texture change so the guide never drifts from what ships.

Texture status is worked out, not maintained by hand:
  vanilla     byte-identical to a vanilla texture (compared with the game's own resources jar)
  copy        byte-identical to another of ours
  generated   written by tools/texture_pass.py
  recoloured  painted, then recoloured by tools/texture_pass.py (its original is in texture_sources)
  custom      painted for Quantimium
"""

from __future__ import annotations

import base64
import glob
import hashlib
import json
import os
import re
import sys
import zipfile
from typing import Dict, List

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUIDE = os.path.join(ROOT, "private", "STYLE_GUIDE.html")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "textures")
SOURCES = os.path.join(ROOT, "tools", "texture_sources")

sys.path.insert(0, os.path.join(ROOT, "tools"))
import texture_pass  # noqa: E402  (for the list of generated outputs)


PALETTES = [
    {"title": "Flux", "note": "Coherent energy. Everything bright in a Flux machine comes from this ramp.", "swatches": [
        ["Unpowered trace", "#0f2a66", "Lines on an idle or unpowered block"],
        ["Dim trace", "#1b4fb8", "Low-tier pips, secondary lines"],
        ["Flux azure", "#3485ff", "Primary trim and circuit lines", True],
        ["Glow core", "#7fb2ff", "Line junctions, the brightest pixel of a trace"],
        ["Bloom white", "#d6e6ff", "Tiny hot centres only; a pixel or two"],
    ]},
    {"title": "Anomaly", "note": "Hazard and hunger: the mite body, rift edges, effect colours. The whole ramp stays blue-violet and never drifts into pink.", "swatches": [
        ["Void", "#1e122a", "Deepest shadow in anomaly art"],
        ["Mite body", "#42305a", "Creature body tone"],
        ["Anomaly violet", "#8a60f0", "Primary hazard line; brightest crystal faces", True],
        ["Crystal", "#a887ff", "Crystal glints, lit accents held in machines"],
        ["Highlight", "#d2bafc", "Tips and eyes, a pixel or two"],
        ["Rift edge", "#b36cff", "The hottest anomaly: rift outlines in the mirror"],
    ]},
    {"title": "Anomalite (oblivion)", "note": "Anomalite crystal and its blocks. Darker than the violet ramp, which sat too close to amethyst in game: violet is only for the brightest faces.", "swatches": [
        ["Oblivion 0", "#120a1e", "Sockets, seams, the darkest facet"],
        ["Oblivion 1", "#1f1236", "Facet shadows"],
        ["Oblivion 2", "#2f1c52", "Body"],
        ["Oblivion 3", "#452a78", "Body, lit", True],
        ["Oblivion 4", "#5f3fa8", "Facet edges catching light"],
    ]},
    {"title": "High-tier chassis", "note": "Obsidian, sampled from the painted Foundry and its tanks: purple-black with blue-grey panel lines. Late multiblocks and top-end single blocks.", "swatches": [
        ["Seam", "#060710", "Gaps between panels, outlines"],
        ["Deep", "#0b0d18", "Grain shadows"],
        ["Obsidian", "#131626", "The stone itself", True],
        ["Lean", "#1b192f", "Grain, leaning violet"],
        ["Panel", "#1f243a", "Raised panelling"],
        ["Panel line", "#262d42", "Engraved lines, rims"],
        ["Raised", "#363f58", "Bevels, embossed idle circuits"],
        ["Violet bridge", "#2d294c", "Runes and marks where azure and violet meet"],
    ]},
    {"title": "Mid-tier chassis", "note": "Deepslate, with its horizontal grain, and polished deepslate for rims and panels. Biased slightly cool so it sits next to obsidian.", "swatches": [
        ["Deep seam", "#1c1c22", "Gaps and outlines"],
        ["Deepslate", "#2e2e36", "Base stone", True],
        ["Polished", "#3a3a42", "Polished faces"],
        ["Tile", "#48484e", "Lit rims and tile edges"],
        ["Edge", "#6c6c74", "Rare highlights"],
    ]},
    {"title": "Low-tier chassis", "note": "Light stone, near vanilla stone and a touch warm, so a low-tier machine looks like it came out of a furnace recipe. The first dark cobble swatches read as deepslate in game.", "swatches": [
        ["Shadow", "#4f4d4b", "Rims, gaps, cobble seams"],
        ["Dark", "#646260", "Grain streaks"],
        ["Stone", "#777573", "Base stone", True],
        ["Face", "#878582", "Smooth stone faces"],
        ["Light", "#9c9a96", "Bevels and highlights"],
        ["Lens", "#1c1f26", "Recessed windows that hold the accent"],
    ]},
    {"title": "Mirror", "note": "The other realm. Cold, desaturated grey with a faint violet cast. Corruption is what the real world turns towards near a rift.", "swatches": [
        ["Mirror dark", "#303036", "Flora shadows"],
        ["Mirror grey", "#54545a", "Flora base", True],
        ["Mirror light", "#7e7e8a", "Flora highlights"],
        ["Corruption", "#5e4a6e", "Real grass and leaves near a rift"],
        ["Fog wash", "#1a1620", "HUD tint while phased"],
    ]},
    {"title": "Failure", "note": "Only for things going wrong: a slack containment field, a broken tesseract link. Never decorative.", "swatches": [
        ["Bruise", "#4d0f3d", "Rift shadow wash in the real world"],
        ["Shadow edge", "#801a52", "Rift outline as seen from the real world"],
        ["Failure red", "#ff382e", "Unpowered or broken state", True],
    ]},
]

TIERS = [
    {"name": "Low", "stone": "Light stone, furnace-like", "faces": ["basic_quantum_crafter_frame", "quantum_exciter_active"], "glow": 1,
     "rule": "One accent, recessed", "light": "A single azure (or held violet) element in a recessed dark window. The rim is stone, never light.",
     "examples": "Observation Chamber, Flux Detector, Basic Quantum Crafter, Quantum Exciter, Anomaly Containment"},
    {"name": "Mid", "stone": "Deepslate", "faces": ["quantum_crafter_chassis", "decoherence_projector_side_active"], "glow": 3,
     "rule": "Accents inside the face", "light": "Thin azure lines and slits inside the face; the rim stays deepslate. Shaped models where it helps.",
     "examples": "Quantum Crafter, Simulator, Tesseract Stabilizer, Flux Suppressor, Decoherence Projector, Stabilised Portal Frame"},
    {"name": "High", "stone": "Obsidian", "faces": ["quantum_foundry_controller_top_active", "quantum_foundry_pillar_side_active"], "glow": 5,
     "rule": "Full circuit", "light": "Circuit patterns across faces; runes in the violet bridge beside azure. Multiblocks may run light across block edges.",
     "examples": "Quantum Foundry, Containment Hall, Attuned Glass, Budding Anomalite, Rift Anchor, Rift Stabiliser"},
]

# Open issues only. Resolved by the texture pass (PR #1): anomalite in Flux cyan, cyan Foundry trim,
# the violet Basic Crafter core, shared plinth copies, and the vanilla mid tier.
FINDINGS = [
    ["quantum_exciter_active", "Low-tier machines look alike.",
     "Same stone, same recessed window. Front faces for the machines that face a direction would separate them cheaply."],
    ["flux_suppressor_active", "Facing machines have no front.",
     "The Exciter, Suppressor and Anomaly Containment carry a facing but show the same face on every side."],
    ["quantum_placeholder", "An unused placeholder remains.",
     "quantum_placeholder has a model but no block. Remove it, or keep it as the stand-in for new machines."],
    ["quantum_simulator_frame", "Two mid-tier machines still wear vanilla deepslate.",
     "The Simulator and Tesseract Stabilizer keep vanilla deepslate on purpose: their shaped models carry them. Worth their own art once the mid tier gets a second pass."],
    ["veiled", "The Veiled is still placeholder art.",
     "Body, glow and gaze are first-pass paints; see VEILED_BRIEF.html."],
]

COPY_EDITS = [
    ("Every tier keeps the same flux azure, so a cobble crafter still reads as Quantimium.",
     "Every tier keeps the same flux azure, so a stone crafter still reads as Quantimium."),
    ("<li>Near-black navy stone, never pure black: it reads as deep rather than empty.</li>",
     "<li>Near-black obsidian with a purple lean, never pure black: it reads as deep rather than empty.</li>"),
    ("<li>Rune and glyph marks on the floor ring are a good motif for plinth sides and caps, which are still copies.</li>",
     "<li>Rune and glyph marks on the floor ring: now used on the plinth friezes and the hall's cap.</li>"),
    ("Where a ring meets a controller, keep the boundary in base navy.",
     "Where a ring meets a controller, keep the boundary in chassis obsidian."),
    ("Custom means painted for Quantimium, not necessarily final. Shared copy means the file is byte-identical to another of ours and needs its own art. Vanilla means an untouched vanilla texture copied in as a placeholder.",
     "Custom means painted for Quantimium, not necessarily final. Generated means written by <code>tools/texture_pass.py</code>, and recoloured means painted, then moved onto the palette by it. Shared copy means the file is byte-identical to another of ours. Vanilla means an untouched vanilla texture copied in as a placeholder. This table is rebuilt by <code>tools/style_guide.py</code>."),
    ('<button type="button" id="filter-copy" data-filter="copy" aria-pressed="false">Shared copy</button>',
     '<button type="button" id="filter-generated" data-filter="generated" aria-pressed="false">Generated</button>\n'
     '      <button type="button" id="filter-recoloured" data-filter="recoloured" aria-pressed="false">Recoloured</button>\n'
     '      <button type="button" id="filter-copy" data-filter="copy" aria-pressed="false">Shared copy</button>'),
    ("  .tex.vanilla .s { color: #ff8a7a; }",
     "  .tex.vanilla .s { color: #ff8a7a; }\n  .tex.generated .s { color: #7fb2ff; }\n  .tex.recoloured .s { color: #a887ff; }"),
    ("""  // Hand corrections: the scan only recognises byte-identical vanilla copies, so an edited vanilla
  // texture shows up as custom.
  const MANUAL = { flux_meter: ["vanilla", "edited vanilla texture, not yet custom art"] };
  DATA.tex.forEach((t) => { if (MANUAL[t.name]) { t.status = MANUAL[t.name][0]; t.note = MANUAL[t.name][1]; } });
  const labels = { custom: "Custom", copy: "Shared copy", vanilla: "Vanilla placeholder" };
  const counts = { custom: 0, copy: 0, vanilla: 0 };""",
     """  const labels = { custom: "Custom", generated: "Generated", recoloured: "Recoloured", copy: "Shared copy", vanilla: "Vanilla placeholder" };
  const counts = { custom: 0, generated: 0, recoloured: 0, copy: 0, vanilla: 0 };"""),
    ("""    <span style="color:#6fd39a"><b>${counts.custom}</b> custom</span>""",
     """    <span style="color:#6fd39a"><b>${counts.custom}</b> custom</span>
    <span style="color:#7fb2ff"><b>${counts.generated}</b> generated</span>
    <span style="color:#a887ff"><b>${counts.recoloured}</b> recoloured</span>"""),
    ('Palettes are taken from the\n        textures already painted, and the colours the game draws effects with are read from the code.',
     'Palettes are taken from the\n        textures already painted and from what reads well in game, and the colours the game draws\n        effects with are read from the code.'),
]


def tier_of(kind: str, name: str) -> str:
    if kind == "item":
        return "items"
    if kind == "entity" or name.startswith(("anomalite", "budding_anomalite", "mirror_", "crystal_leech")):
        return "anomaly"
    if name.startswith(("observation_chamber", "flux_detector", "basic_quantum_crafter", "quantum_exciter",
                        "machine_bottom_low", "unrealised_ore")):
        return "low"
    if name.startswith("basic_anomaly_siphon") and not name.startswith("anomaly_containment_hall"):
        return "low"
    if name.startswith(("quantum_foundry", "anomaly_containment_hall", "quantum_attuned_glass", "rift_")):
        return "high"
    return "mid"


def vanilla_hashes() -> Dict[str, str]:
    """md5 → vanilla texture name, from the game's resources jar the build already downloaded."""
    jars = glob.glob(os.path.join(ROOT, "build", "moddev", "artifacts", "*client-extra*.jar"))
    found: Dict[str, str] = {}
    if not jars:
        return found
    with zipfile.ZipFile(jars[0]) as jar:
        for entry in jar.namelist():
            if entry.startswith("assets/minecraft/textures/") and entry.endswith(".png"):
                found.setdefault(hashlib.md5(jar.read(entry)).hexdigest(), entry.split("textures/")[1][:-4])
    return found


def textures() -> List[dict]:
    vanilla = vanilla_hashes()
    generated = set(texture_pass.OUTPUTS)
    files = []
    for kind in ("block", "item", "entity"):
        for path in sorted(glob.glob(os.path.join(TEXTURES, kind, "*.png"))):
            name = os.path.basename(path)[:-4]
            with open(path, "rb") as handle:
                data = handle.read()
            files.append((kind, name, data, hashlib.md5(data).hexdigest()))
    by_hash: Dict[str, List[str]] = {}
    for kind, name, _, digest in files:
        by_hash.setdefault(digest, []).append(name)

    out = []
    for kind, name, data, digest in files:
        key = f"{kind}/{name}"
        note = ""
        if digest in vanilla:
            status, note = "vanilla", f"copy of vanilla {vanilla[digest].split('/')[-1]}"
        elif os.path.exists(os.path.join(SOURCES, key + ".png")):
            status = "recoloured"
        elif key in generated:
            status = "generated"
        elif len(by_hash[digest]) > 1:
            others = [n for n in by_hash[digest] if n != name]
            status = "copy"
            note = f"same file as {others[0]}" + (f" +{len(others) - 1} more" if len(others) > 1 else "")
        else:
            status = "custom"
        if kind == "block" and data and _is_strip(data):
            note = (note + "; " if note else "") + "animated"
        out.append({"name": name, "tier": tier_of(kind, name), "status": status, "note": note,
                    "src": "data:image/png;base64," + base64.b64encode(_first_frame(data)).decode()})
    return out


def _is_strip(data: bytes) -> bool:
    import io
    from PIL import Image
    img = Image.open(io.BytesIO(data))
    return img.height > img.width


def _first_frame(data: bytes) -> bytes:
    """Animated strips show their first frame; everything else is embedded as it ships."""
    import io
    from PIL import Image
    img = Image.open(io.BytesIO(data))
    if img.height <= img.width:
        return data
    buffer = io.BytesIO()
    img.crop((0, 0, img.width, img.width)).save(buffer, "PNG")
    return buffer.getvalue()


def replace_const(html: str, name: str, value: str) -> str:
    """Swaps `const NAME = ...;` for a new value, matched up to the next top-level const."""
    pattern = re.compile(r"const %s = .*?;\n(?=\n*(?:const |\(function|document\.))" % name, re.S)
    if not pattern.search(html):
        raise SystemExit(f"could not find const {name}")
    return pattern.sub(lambda _: f"const {name} = {value};\n", html, count=1)


def main() -> None:
    with open(GUIDE) as handle:
        html = handle.read()

    old = json.loads(re.search(r"const DATA = (\{.*?\});\n", html, re.S).group(1))
    tex = textures()
    current = {t["name"]: t["src"] for t in tex}
    # Featured images: refreshed from the current files; anything else (the mood render) kept.
    key = {name: current.get(name, src) for name, src in old["key"].items()}
    for name in ("basic_quantum_crafter_frame", "quantum_exciter_active", "decoherence_projector_side_active",
                 "flux_suppressor_active", "quantum_placeholder", "veiled"):
        if name in current:
            key[name] = current[name]
    html = re.sub(r"const DATA = \{.*?\};\n",
                  lambda _: "const DATA = " + json.dumps({"tex": tex, "key": key}) + ";\n", html, count=1, flags=re.S)

    html = replace_const(html, "PALETTES", json.dumps(PALETTES, indent=2))
    html = replace_const(html, "TIERS", json.dumps(TIERS, indent=2))
    html = replace_const(html, "FINDINGS", json.dumps(FINDINGS, indent=2))
    for before, after in COPY_EDITS:
        if after in html:
            continue
        if before not in html:
            raise SystemExit(f"copy edit anchor missing: {before[:60]}")
        html = html.replace(before, after, 1)

    with open(GUIDE, "w") as handle:
        handle.write(html)
    counts: Dict[str, int] = {}
    for t in tex:
        counts[t["status"]] = counts.get(t["status"], 0) + 1
    print(f"wrote STYLE_GUIDE.html: {len(tex)} textures, " + ", ".join(f"{v} {k}" for k, v in sorted(counts.items())))


if __name__ == "__main__":
    main()
