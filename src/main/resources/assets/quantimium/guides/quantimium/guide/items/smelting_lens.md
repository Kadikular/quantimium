---
navigation:
  title: Smelting Lens
  parent: items/index.md
  position: 44
  icon: quantimium:smelting_lens
item_ids:
- quantimium:smelting_lens
---

# Smelting Lens

<ItemImage id="quantimium:smelting_lens" scale="2" />

Smelting Lens smelts, as a furnace would, what the actions before it free or move.

## How it works

- Smelts what a Harvest laser cuts and what Damage kills (picked up or left lying), what Pickup gathers, and what Insert puts in and Extract takes out. Sand becomes glass, ore ingots, a pig's drops cooked.
- Unrealized Matter gets smelting added to its history, as a Quantum Crafter with a furnace gives it.
- What a furnace can't smelt stays as it is, at no cost.
- With no other action on the laser, it is an action of its own: it smelts what lies in the laser, else the block it ends on, every 0.5 s. A block it smelts into takes its place (sand becomes glass, and the laser goes on through to the next). Anything else drops there.
- Each Crushing or Smelting lens is a step, in the lenses' order (up to 8). See [Crushing Lens](crushing_lens.md).

## Energy

25 FE per item smelted.

## Applies to

| Action | Effect |
| --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | Smelts the drops. |
| <ItemLink id="quantimium:damage_lens" /> | Smelts what it kills drops, where they fall or on the way back. |
| <ItemLink id="quantimium:healing_lens" /> | Smelts drops of undead it kills. |
| <ItemLink id="quantimium:freezing_lens" /> | No effect. |
| <ItemLink id="quantimium:interact_lens" /> | No effect. |
| <ItemLink id="quantimium:acceleration_lens" /> | No effect. |
| <ItemLink id="quantimium:place_lens" /> | No effect. |
| <ItemLink id="quantimium:insert_lens" /> | Smelts items on the way in. |
| <ItemLink id="quantimium:extract_lens" /> | Smelts items on the way out. |
| <ItemLink id="quantimium:redstone_lens" /> | No effect. |

## Examples

- **Auto-smelter.** Extract, Smelting and a collecting Laser Port: items leave a chest smelted.

## Recipes

<RecipesFor id="quantimium:smelting_lens" />
