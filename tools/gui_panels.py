#!/usr/bin/env python3
"""
Generates the Quantimium container panel textures.

Every panel is drawn in the style of the hand-painted Quantum Foundry panel (the only one this file
does not write): a flat face with a one-pixel edge, a title strip ruled off at the top, dark slot
wells lit along their bottom and right edges, accent-rimmed sockets for the slot that matters, and
faint engraved lines. The colours follow the machine's tier, as the blocks do (see STYLE_GUIDE.html):
light stone for low tier, deepslate for mid tier, obsidian for high tier. The text colours that go
with each palette live in PanelTheme.java; keep the two in step.

This file is the layout spec: every coordinate below matches a slot position declared in a menu, or
a region a screen draws into. Repainting a panel by hand is fine as long as the regions stay put.

    ./tools/gui_panels.py            # write the panels into src/main/resources
    ./tools/gui_panels.py --guides   # also write labelled region overlays into tools/out

Slot coordinates are menu coordinates, i.e. the top-left of the 16x16 item area. The 18x18 well is
drawn one pixel up and left of that, exactly as vanilla does.
"""

from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from typing import List, Tuple

from PIL import Image, ImageDraw

CANVAS = 256
PANEL_W = 176
PANEL_H = 166
SLOT = 18
ITEM = 16

Region = Tuple[str, Tuple[int, int, int, int]]


@dataclass(frozen=True)
class Palette:
    edge: tuple      # the one-pixel outline and the rules
    panel: tuple     # the face
    well: tuple      # slot and gauge wells
    rim: tuple       # the lit bottom and right edge of a well
    deco: tuple      # engraved lines, brackets, arrows
    accent: tuple    # the rim of a socket


# Sampled from the painted Foundry panel, and from the chassis ramps in STYLE_GUIDE.html.
HIGH = Palette(edge=(38, 44, 72), panel=(18, 20, 36), well=(9, 10, 20), rim=(52, 58, 94),
               deco=(44, 50, 82), accent=(54, 140, 178))
MID = Palette(edge=(72, 72, 80), panel=(46, 46, 54), well=(24, 24, 30), rim=(98, 98, 108),
              deco=(62, 62, 72), accent=(52, 120, 220))
LOW = Palette(edge=(79, 77, 75), panel=(135, 133, 130), well=(66, 64, 62), rim=(172, 170, 166),
              deco=(112, 110, 106), accent=(40, 150, 190))

# Shared positions.
ENERGY = (6, 18, 4, 52)          # QuantumMachineScreen.ENERGY_*
TITLE_RULE_Y = 14
INPUT_ORIGIN = (12, 18)
OUTPUT_ORIGIN = (116, 18)
CENTRE_SLOT = (80, 36)
PLAYER_X = 8


def rgba(colour) -> tuple:
    return (*colour, 255)


class Panel:
    def __init__(self, palette: Palette, height: int = PANEL_H):
        self.p = palette
        self.h = height
        self.image = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.image)
        self.regions: List[Region] = []
        self.box(0, 0, PANEL_W, height, palette.panel)
        self.draw.rectangle([0, 0, PANEL_W - 1, height - 1], outline=rgba(palette.edge))
        self.rule(TITLE_RULE_Y)

    # ---- primitives ----

    def box(self, x, y, w, h, colour) -> None:
        self.draw.rectangle([x, y, x + w - 1, y + h - 1], fill=rgba(colour))

    def line(self, points, colour) -> None:
        self.draw.line(points, fill=rgba(colour))

    def rule(self, y: int, x0: int = 3, x1: int = PANEL_W - 4) -> None:
        self.line([x0, y, x1, y], self.p.edge)

    def well(self, x, y, w, h, rim=None) -> None:
        """A recess: dark fill, lit along the bottom and right as the Foundry's are."""
        self.box(x, y, w, h, self.p.well)
        rim = rim or self.p.rim
        self.line([x, y + h - 1, x + w - 1, y + h - 1], rim)
        self.line([x + w - 1, y, x + w - 1, y + h - 1], rim)

    def slot(self, item_x: int, item_y: int) -> None:
        self.well(item_x - 1, item_y - 1, SLOT, SLOT)

    def grid(self, origin, cols: int, rows: int, name: str = "") -> None:
        for row in range(rows):
            for col in range(cols):
                self.slot(origin[0] + col * SLOT, origin[1] + row * SLOT)
        if name:
            self.regions.append((name, (origin[0], origin[1], cols * SLOT - 2, rows * SLOT - 2)))

    def socket(self, item_x: int, item_y: int, name: str) -> None:
        """The slot a machine turns on: an accent-rimmed well inside an engraved frame."""
        x, y = item_x - 1, item_y - 1
        self.draw.rectangle([x - 3, y - 3, x + SLOT + 2, y + SLOT + 2], outline=rgba(self.p.edge))
        self.well(x, y, SLOT, SLOT, rim=self.p.accent)
        self.regions.append((name, (item_x, item_y, ITEM, ITEM)))

    def gauge(self, region, name: str) -> None:
        x, y, w, h = region
        self.well(x - 1, y - 1, w + 2, h + 2)
        self.regions.append((name, region))

    def energy(self) -> None:
        self.gauge(ENERGY, "energy")

    def arrow(self, x: int, y: int, length: int) -> None:
        """An engraved arrow pointing right, centred on y."""
        self.line([x, y, x + length - 1, y], self.p.deco)
        for step in range(1, 4):
            self.line([x + length - 1 - step, y - step, x + length - 1 - step, y + step], self.p.deco)

    def brackets(self, x: int, y: int, w: int, h: int, arm: int = 4) -> None:
        """Corner brackets, as the Foundry frames its diamond."""
        c = self.p.deco
        right, bottom = x + w - 1, y + h - 1
        for cx, cy, dx, dy in ((x, y, 1, 1), (right, y, -1, 1), (x, bottom, 1, -1), (right, bottom, -1, -1)):
            self.line([cx, cy, cx + dx * (arm - 1), cy], c)
            self.line([cx, cy, cx, cy + dy * (arm - 1)], c)

    def player(self, top: int = 84) -> None:
        """Player inventory and hotbar, ruled off from the machine above them."""
        self.rule(top - 5)
        self.grid((PLAYER_X, top), 9, 3, "player")
        self.grid((PLAYER_X, top + 58), 9, 1, "hotbar")

    def region(self, name: str, region) -> None:
        self.regions.append((name, region))


# ---- Panels ----

def crafter(palette: Palette, basic: bool) -> Panel:
    panel = Panel(palette)
    panel.energy()
    panel.grid(INPUT_ORIGIN, 3, 3, "inputs")
    panel.grid(OUTPUT_ORIGIN, 3, 3, "outputs")
    panel.socket(*CENTRE_SLOT, "catalyst")
    panel.arrow(66, 44, 8)
    panel.arrow(100, 44, 12)
    # The page bar sits under the output grid, in the gap above the inventory rule.
    panel.well(113, 72, 60, 11)
    panel.region("page", (113, 72, 60, 11))
    if basic:
        # Coherence reserve, a flat gauge under the catalyst (QuantumCrafterScreen.COHERENCE_*).
        panel.gauge((72, 60, 32, 4), "coherence")
    panel.player()
    panel.rule(79, 3, 108)
    return panel


SIM_HEIGHT = 186
SIM_TANK_Y = 76
SIM_TANK = (16, 12)


def simulator() -> Panel:
    panel = Panel(MID, SIM_HEIGHT)
    panel.energy()
    panel.grid(INPUT_ORIGIN, 3, 3, "inputs")
    panel.grid(OUTPUT_ORIGIN, 3, 3, "outputs")
    panel.socket(*CENTRE_SLOT, "machine")
    panel.arrow(66, 44, 8)
    panel.region("progress", (99, 37, 15, 14))
    panel.region("engage", (72, 58, 32, 14))
    # Fluid tanks under each grid, one per column (QuantumSimulatorScreen.TANK_*).
    for i in range(3):
        panel.gauge((INPUT_ORIGIN[0] + i * SLOT, SIM_TANK_Y, *SIM_TANK), f"in tank {i + 1}")
        panel.gauge((OUTPUT_ORIGIN[0] + i * SLOT, SIM_TANK_Y, *SIM_TANK), f"out tank {i + 1}")
    panel.player(104)
    return panel


def stabilizer() -> Panel:
    panel = Panel(MID)
    panel.socket(80, 35, "tesseract")
    panel.brackets(62, 26, 52, 32)
    # Where the bound block is, written under the socket (TesseractStabilizerScreen).
    panel.region("link text", (12, 56, 152, 20))
    panel.player()
    return panel


def observation_chamber() -> Panel:
    panel = Panel(LOW)
    panel.slot(44, 35)
    panel.region("input", (44, 35, ITEM, ITEM))
    panel.socket(80, 35, "trace")
    panel.grid(OUTPUT_ORIGIN, 3, 3, "outputs")
    panel.arrow(63, 43, 10)
    panel.arrow(100, 43, 12)
    panel.player()
    return panel


def quantum_observation_chamber() -> Panel:
    """Mid tier: three Matter slots, a progress arrow, eight results and the Trace socket."""
    panel = Panel(MID)
    panel.energy()
    panel.grid((26, 17), 1, 3, "inputs")
    panel.arrow(48, 43, 20)
    panel.region("progress", (48, 40, 20, 7))
    panel.grid((74, 26), 4, 2, "outputs")
    panel.socket(152, 35, "trace")
    panel.player()
    return panel


def materialiser() -> Panel:
    """Mid tier: Matter and the Trace socket on the left; a search box over a well of choosable
    targets in the middle (drawn by the screen, 5×2 of 18px buttons); three outputs on the right."""
    panel = Panel(MID)
    panel.energy()
    panel.slot(26, 17)
    panel.region("matter", (26, 17, ITEM, ITEM))
    panel.socket(26, 53, "trace")
    panel.well(47, 16, 92, 13)
    panel.region("search", (48, 17, 90, 11))
    panel.well(47, 31, 92, 38)
    panel.region("choices", (48, 32, 90, 36))
    panel.grid((145, 17), 1, 3, "outputs")
    panel.player()
    return panel


def field_control(fragments: bool) -> Panel:
    """Flux Maintainer (no fragments), Anomaly Siphon and Field Regulator: a readout, and for the two
    that pull anomaly a fragment socket with its progress bar beneath."""
    panel = Panel(MID)
    panel.energy()
    panel.region("info", (16, 20, 108 if fragments else 150, 56))
    if fragments:
        panel.socket(152, 20, "fragment")
        panel.gauge((132, 46, 38, 4), "fragment progress")
    panel.player()
    return panel




def field_monitor() -> Panel:
    """Field Monitor: a 9×9 map of the chunks round it on the left, what it watches for on the right.
    No slots and no player inventory: it only shows."""
    panel = Panel(MID, height=106)
    panel.well(7, 19, 82, 82)
    panel.region("map", (8, 20, 80, 80))
    panel.region("info", (96, 20, 74, 64))
    return panel


def containment_hall() -> Panel:
    panel = Panel(HIGH)
    panel.energy()
    panel.socket(152, 20, "residue")
    panel.region("residue time", (128, 44, 42, 9))
    panel.region("info", (16, 20, 108, 52))
    panel.player()
    return panel


def rift_anchor() -> Panel:
    """No energy bar: the anchor is unpowered, its stabilisers pay."""
    panel = Panel(HIGH)
    panel.socket(152, 20, "residue")
    panel.region("next residue time", (128, 44, 42, 9))
    panel.region("info", (10, 20, 132, 44))
    # Progress to the next residue (RiftAnchorScreen.PROGRESS_*).
    panel.gauge((10, 68, 132, 4), "progress")
    panel.player()
    return panel


def zeno_field_controller() -> Panel:
    panel = Panel(MID)
    panel.energy()
    panel.region("info", (16, 20, 108, 36))
    # The field diagram (ZenoFieldControllerScreen.FIELD_*), drawn to scale by the screen.
    panel.well(127, 19, 46, 46)
    panel.region("field", (128, 20, 44, 44))
    panel.player()
    return panel


def superposition_crafter() -> Panel:
    panel = Panel(HIGH)
    panel.energy()
    panel.socket(26, 36, "catalyst")
    panel.brackets(14, 22, 42, 44)
    panel.region("info", (56, 20, 114, 50))
    panel.player()
    return panel


def superposition_crafter_filter() -> Panel:
    """The filter view: 27 ghost slots where the catalyst and readout were."""
    panel = Panel(HIGH)
    panel.grid((8, 18), 9, 3, "filter")
    panel.player()
    return panel


def unfolding_array() -> Panel:
    """The Unfolding Array: three reagents down the left, the Sophon on the right in brackets, the
    readout and the Unfold button between them, and the progress along the bottom."""
    panel = Panel(HIGH)
    panel.energy()
    for i in range(3):
        panel.socket(26, 17 + i * 18, "reagent")
    panel.socket(134, 35, "sophon")
    panel.brackets(124, 25, 36, 36)
    panel.region("info", (48, 17, 72, 32))
    panel.region("button", (50, 51, 68, 14))
    panel.gauge((48, 70, 112, 4), "progress")
    panel.player()
    return panel


def relay_module() -> Panel:
    """The Relay module: six Tesseracts in two columns, each with a Recall/Send button beside it, and
    the Sophon slot on the right in brackets."""
    panel = Panel(HIGH)
    for column, x in enumerate((26, 84)):
        for row in range(3):
            panel.slot(x, 17 + row * 18)
            panel.region("button", (x + 18, 17 + row * 18 + 2, 36, 12))
    panel.socket(152, 35, "sophon")
    panel.brackets(142, 25, 36, 36)
    panel.player()
    return panel


def fold_core() -> Panel:
    """The Fold Core: the Tesseract's socket on the left in brackets; the chamber's size and what it
    makes of the socket written beside it; the Fold button and its cost under that; the seal's
    progress along the bottom."""
    panel = Panel(HIGH)
    panel.energy()
    panel.socket(26, 35, "tesseract")
    panel.brackets(16, 25, 36, 36)
    panel.region("chamber", (48, 17, 120, 9))
    panel.region("status", (48, 28, 120, 27))
    panel.region("button", (48, 56, 56, 13))
    panel.region("cost", (108, 58, 60, 9))
    panel.gauge((48, 72, 120, 3), "seal")
    panel.player()
    return panel


def horizon_core() -> Panel:
    """The Horizon Core: no slots. The horizon's load across the top, its rings and mass under it,
    the sort toggle and a search box, then a scrolling grid of items and the last result."""
    panel = Panel(HIGH, height=176)
    panel.energy()
    panel.gauge((16, 18, 152, 3), "load")
    panel.region("info", (16, 24, 152, 9))
    panel.region("sort", (16, 35, 74, 12))
    panel.well(93, 35, 76, 12)
    panel.region("search", (95, 37, 72, 9))
    panel.grid((16, 51), 8, 6, "items")
    panel.region("message", (16, 162, 152, 9))
    return panel


PANELS = {
    "quantum_crafter": lambda: crafter(MID, basic=False),
    "basic_quantum_crafter": lambda: crafter(LOW, basic=True),
    "quantum_simulator": simulator,
    "tesseract_stabilizer": stabilizer,
    "observation_chamber": observation_chamber,
    "quantum_observation_chamber": quantum_observation_chamber,
    "materialiser": materialiser,
    "field_control": lambda: field_control(False),
    "field_control_fragments": lambda: field_control(True),
    "field_monitor": field_monitor,
    "containment_hall": containment_hall,
    "zeno_field_controller": zeno_field_controller,
    "rift_anchor": rift_anchor,
    "me_superposition_crafter": superposition_crafter,
    "me_superposition_crafter_filter": superposition_crafter_filter,
    "unfolding_array": unfolding_array,
    "relay_module": relay_module,
    "fold_core": fold_core,
    "horizon_core": horizon_core,
}

GUIDE = (255, 0, 220, 255)


def guide_overlay(panel: Panel) -> Image.Image:
    guides = panel.image.copy()
    draw = ImageDraw.Draw(guides)
    for name, (x, y, w, h) in panel.regions:
        draw.rectangle([x, y, x + w - 1, y + h - 1], outline=GUIDE)
        draw.text((x + 2, y + 1), name, fill=(0, 0, 0, 255))
        draw.text((x + 1, y + 1), name, fill=GUIDE)
    return guides


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Quantimium GUI panels")
    parser.add_argument("--guides", action="store_true",
                        help="also write labelled region overlays to tools/out")
    args = parser.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(root, "src", "main", "resources", "assets", "quantimium",
                           "textures", "gui", "container")
    os.makedirs(out_dir, exist_ok=True)
    guide_dir = os.path.join(root, "tools", "out")

    for name, build in PANELS.items():
        panel = build()
        path = os.path.join(out_dir, f"{name}.png")
        panel.image.save(path)
        print(f"wrote {os.path.relpath(path, root)}")
        if args.guides:
            os.makedirs(guide_dir, exist_ok=True)
            guide = os.path.join(guide_dir, f"{name}_guides.png")
            guide_overlay(panel).crop((0, 0, PANEL_W, panel.h)).resize(
                (PANEL_W * 3, panel.h * 3), Image.NEAREST).save(guide)


if __name__ == "__main__":
    main()
