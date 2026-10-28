---
navigation:
  title: Insert Lens
  parent: items/index.md
  position: 26
  icon: quantimium:insert_lens
item_ids:
- quantimium:insert_lens
---

# Insert Lens

<ItemImage id="quantimium:insert_lens" scale="2" />

Insert Lens adds the Insert action to a laser. The laser puts what the sending Laser Ports up the laser send into the inventory it ends on.

## How it works

- Moves up to 8 items every 0.5 s. Each <ItemLink id="quantimium:speed_lens" /> doubles that, up to 64.
- It ramps up and down: each successful move halves its wait (from 4 s down to its own pace), and each look that finds nothing doubles it (up to 2 s).
- Through an <ItemLink id="quantimium:area_prism" />, each inventory in the area in turn.
- It does nothing in the mirror.

## Energy

200 FE per stack moved, plus 25 FE per item for each Smelting or Crushing lens.

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | No effect. |
| <ItemLink id="quantimium:speed_lens" /> | Moves 8 items per 10 ticks at base; each lens doubles that (16, 32, 64) and halves the wait. |
| <ItemLink id="quantimium:filter_lens" /> | Moves only the listed items. |
| <ItemLink id="quantimium:pickup_lens" /> | No effect. |
| <ItemLink id="quantimium:void_lens" /> | Void excess destroys items that don't fit. Void all destroys every item sent, so the target works as a trash can. |
| <ItemLink id="quantimium:smelting_lens" /> | Smelts items on the way in. |
| <ItemLink id="quantimium:crushing_lens" /> | Crushes items on the way in. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Feed a machine.** A sending <ItemLink id="quantimium:laser_port" /> holding the input items, Insert, and the laser ending on the machine.
- **Trash can.** Insert with a Void Lens set to Void all destroys everything a sending Laser Port sends.

## Recipes

<RecipesFor id="quantimium:insert_lens" />
