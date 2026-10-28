---
navigation:
  title: Flux Detector
  parent: blocks/index.md
  position: 18
  icon: quantimium:flux_detector
item_ids:
- quantimium:flux_detector
---

# Flux Detector

<ItemImage id="quantimium:flux_detector" scale="2" />

The Flux Detector turns the flux or anomaly of its chunk into a redstone signal.

- Right-click to cycle what it reads: **flux**, **anomaly**, or the **higher of both**.
- It reads once a second. The signal is a comparator-style strength by band:

| Band | Signal |
| --- | --- |
| Low | 0 |
| Medium | 3 |
| High | 7 |
| Critical | 11 |
| Singularity | 15 |

- It switches into a band at the band's start but drops out only below 85% of it, so a field hovering at an edge switches once.
- The <ItemLink id="quantimium:flux_meter" /> shows what it is watching and its current signal.

Use it to switch on a Siphon, a lamp or an alarm when a district heats up. To watch many chunks at once, use the <ItemLink id="quantimium:field_monitor" />.

## Recipes

<RecipesFor id="quantimium:flux_detector" />
