---
navigation:
  title: Harvest Lens
  parent: items/index.md
  position: 24
  icon: quantimium:harvest_lens
item_ids:
- quantimium:harvest_lens
---

# Harvest Lens

<ItemImage id="quantimium:harvest_lens" scale="2" />

Harvest Lens adds the Harvest action to a laser. The laser breaks the block it ends on, mining as an iron pickaxe would. Add an <ItemLink id="quantimium:amplify_lens" /> to mine as a diamond pickaxe.

## How it works

- Breaks the block the laser ends on, or one the laser passes just before it (a crop on farmland, grass). The cracks show how far the cut has got. A block that breaks early takes the rest of the cut with it.
- Breaks at most one block every 0.5 s, however much power it has.
- Once the block is gone, the laser reaches on to the next.
- It never breaks a block with a block entity (a machine, a chest), Anomalite, or a block in the `quantimium:laser_unbreakable` tag.
- In the mirror it cuts grown Anomalite crystals instead: 15 s per crystal at 60 FE/t, for 3 Anomalite Shards. See [Rift Lens](../blocks/rift_lens.md).

## Energy

400 FE per point of hardness (at least one point), paid as it cuts. Stone (hardness 1.5) costs 600 FE, which at one block per 10 ticks is 60 FE/t.

The cost goes up with an <ItemLink id="quantimium:amplify_lens" /> (one more share per lens), through an <ItemLink id="quantimium:area_prism" /> (x1.5) and with <ItemLink id="quantimium:phantom_lens" /> (x2). See [Lens energy](../lasers/lenses/energy.md).

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | Mines as a diamond pickaxe instead of iron. Each lens adds one share to a cut's cost (x2, x3, x4). |
| <ItemLink id="quantimium:speed_lens" /> | Halves the wait between blocks, once per lens (10 ticks at base). |
| <ItemLink id="quantimium:block_filter_lens" /> | Cuts only the listed blocks, matched by the block or anything it drops. |
| <ItemLink id="quantimium:pickup_lens" /> | Sends the drops up the laser to a collecting Laser Port. A cut waits until the ports can take all its drops. |
| <ItemLink id="quantimium:void_lens" /> | Destroys drops no Laser Port has room for (Void excess) or every drop (Void all). The cut never waits for room. |
| <ItemLink id="quantimium:smelting_lens" /> | Smelts the drops. |
| <ItemLink id="quantimium:crushing_lens" /> | Crushes the drops. |
| <ItemLink id="quantimium:precision_lens" /> | Mines with Silk Touch. |
| <ItemLink id="quantimium:harmonic_lens" /> | Mines with Fortune, one level per lens, up to 3. |
| <ItemLink id="quantimium:unrealising_lens" /> | Ore comes away as Unrealized Matter, one per block (the config can turn this off). |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Wheat farm.** See [Examples](../lasers/lenses/examples.md).
- **Ore miner.** An <ItemLink id="quantimium:area_prism" /> with Harvest, Pickup, Phantom and a Block Filter Lens set to ores cuts only the ore in the ground and sends it to a collecting <ItemLink id="quantimium:laser_port" />.

## Recipes

<RecipesFor id="quantimium:harvest_lens" />
