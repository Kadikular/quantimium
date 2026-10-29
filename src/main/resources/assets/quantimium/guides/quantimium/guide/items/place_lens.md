---
navigation:
  title: Placement Lens
  parent: items/index.md
  position: 35
  icon: quantimium:place_lens
item_ids:
- quantimium:place_lens
---

# Placement Lens

<ItemImage id="quantimium:place_lens" scale="2" />

Placement Lens adds the Placement action to a laser. The laser places a block a Laser Port up the laser sends, in the space where it ends, as you would against the block it ends on.

## How it works

- Places at most one block every 0.5 s.
- Seeds count: on farmland they are sown. A bucket of water or lava empties there, and the empty bucket goes back to the port.
- An Filter Lens in the mount says what it places.
- Through an <ItemLink id="quantimium:area_prism" /> it fills each free space resting on a block.
- It does nothing in the mirror.

## Energy

200 FE per block placed (x1.5 through an Area Prism).

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | No effect. |
| <ItemLink id="quantimium:speed_lens" /> | Places twice as often per lens (10 ticks at base). |
| <ItemLink id="quantimium:filter_lens" /> | Places only the listed items. |
| <ItemLink id="quantimium:pickup_lens" /> | No effect. |
| <ItemLink id="quantimium:void_lens" /> | No effect. Placement never destroys what it can't place. |
| <ItemLink id="quantimium:smelting_lens" /> | No effect. |
| <ItemLink id="quantimium:crushing_lens" /> | No effect. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Wheat farm.** Placement re-sows what Harvest cuts. See [Examples](../lasers/lenses/examples.md).

## Recipes

<RecipesFor id="quantimium:place_lens" />
