---
navigation:
  title: Extract Lens
  parent: items/index.md
  position: 15
  icon: quantimium:extract_lens
item_ids:
- quantimium:extract_lens
---

# Extract Lens

<ItemImage id="quantimium:extract_lens" scale="2" />

Extract Lens adds the Extract action to a laser. The laser takes items from the inventory it ends on, back up the laser to the collecting Laser Ports.

## How it works

- Moves up to 8 items every 0.5 s, as many as the ports have room for. Each <ItemLink id="quantimium:speed_lens" /> doubles that, up to 64.
- Through an <ItemLink id="quantimium:area_prism" />, from each inventory in the area in turn.
- Fluids too. With no items to take, it drains the fluid handler it ends on into the collecting <ItemLink id="quantimium:laser_tank" />s up the laser. It moves 1,000 mB at a time, doubled for each Speed Lens.
- With nothing to take from, it pumps: the first fluid source on its way (the laser runs on through water) goes up to the collecting Laser Tanks, a bucket at a time, if they have room for it.
- It does nothing in the mirror.

## Energy

200 FE per stack moved, plus 25 FE per item for each Smelting or Crushing lens.

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | No effect. |
| <ItemLink id="quantimium:speed_lens" /> | Moves 8 items per 10 ticks at base; each lens doubles that (16, 32, 64) and halves the wait. |
| <ItemLink id="quantimium:filter_lens" /> | Takes only the listed items. |
| <ItemLink id="quantimium:pickup_lens" /> | No effect. |
| <ItemLink id="quantimium:void_lens" /> | With a list on the Void Lens, takes the listed items and destroys them. |
| <ItemLink id="quantimium:smelting_lens" /> | Smelts items on the way out. |
| <ItemLink id="quantimium:crushing_lens" /> | Crushes items on the way out. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Chest to pipes.** Extract with the laser ending on a chest, and a collecting <ItemLink id="quantimium:laser_port" /> that pipes pull from.

## Recipes

<RecipesFor id="quantimium:extract_lens" />
