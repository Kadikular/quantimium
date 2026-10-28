---
navigation:
  title: Acceleration Lens
  parent: items/index.md
  position: 0
  icon: quantimium:acceleration_lens
item_ids:
- quantimium:acceleration_lens
---

# Acceleration Lens

<ItemImage id="quantimium:acceleration_lens" scale="2" />

Acceleration Lens adds the Acceleration action to a laser. The laser speeds up the machine it ends on by giving it extra ticks of its own: 2x.

## How it works

- Add <ItemLink id="quantimium:speed_lens" /> lenses before it to double the speed-up each time: 4x, 8x, 16x. The config sets the most (16 by default).
- Through an <ItemLink id="quantimium:area_prism" />, every machine in the area is sped up.
- Each machine's extra ticks may take at most 100 microseconds a tick, and all machines together 5,000 microseconds (both configurable). A machine that would take longer is given fewer ticks. Jade shows the speed and when the tick budget held it back ("Accelerated 5x of 8x (tick budget)").
- Machines that don't tick, Quantimium's own machines, and blocks in `quantimium:not_accelerated` are not sped up.
- It does nothing in the mirror.

## Energy

500 FE per extra tick (configurable): (multiplier - 1) x 500 FE/t. That is 500 FE/t at 2x, 1,500 FE/t at 4x and 7,500 FE/t at 16x.

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | No effect. |
| <ItemLink id="quantimium:speed_lens" /> | Each lens doubles the speed-up: 2x, 4x, 8x, 16x (the config sets the most). |
| <ItemLink id="quantimium:filter_lens" /> | No effect. |
| <ItemLink id="quantimium:pickup_lens" /> | No effect. |
| <ItemLink id="quantimium:void_lens" /> | No effect. |
| <ItemLink id="quantimium:smelting_lens" /> | No effect. |
| <ItemLink id="quantimium:crushing_lens" /> | No effect. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Faster furnace.** Acceleration with one Speed Lens runs the machine at the end of the laser at 4x for 1,500 FE/t.

## Recipes

<RecipesFor id="quantimium:acceleration_lens" />
