---
navigation:
  title: Decoherence Projector
  parent: blocks/index.md
  position: 10
  icon: quantimium:decoherence_projector
item_ids:
- quantimium:decoherence_projector
---

# Decoherence Projector

<ItemImage id="quantimium:decoherence_projector" scale="2" />

The Lance's beam on a pedestal. Every half-second it picks the most pressing target within
16 blocks in clear sight of its orb: the
[Veiled](../mechanics/veiled.md) first, then mirror mites
(3 damage each half-second), then a
[rift](../mechanics/flux-rifts.md) (2
coherence a tick). Stabilised rifts are left alone.

| | |
| --- | --- |
| Watching | 8 FE/t |
| Beaming | 120 FE/t |
| Storage | 100,000 FE, up to 1,000 FE/t in |

Like any Quantimium machine it emits **1 flux per 1,000 FE** it spends, a quarter of that as anomaly,
once a second.

**Mount it anywhere.** It stands on a floor, hangs from a ceiling or
sticks out of a wall, pointing away from what it's on, and it only looks out that way: it never
beams back through its own mount. Anything placed in front of it shuts it down.

**Its light comes from <ItemLink id="quantimium:anomalite_cell" />s.** Watching costs no
charge; beaming does. The Veiled burns a Cell fastest (about one Cell for a full pin, 960 of its
1,000 charge), mites 5 a shot and rifts 1 a tick. When a Cell runs out the next loads, used up whole,
so there are no empties to collect. With no Cell it still watches, but can't beam. Put Cells in by
hand (use one on it; sneak with an empty hand to take them out), by pipe or hopper, or put a bound
<ItemLink id="quantimium:tesseract" /> in to draw Cells from the inventory it links to. The <ItemLink id="quantimium:flux_meter" /> and Jade
show the Cell's charge and how many are spare. The real world sees only the block light up; the beam
itself is drawn for phased players.

## Recipes

<RecipesFor id="quantimium:decoherence_projector" />
