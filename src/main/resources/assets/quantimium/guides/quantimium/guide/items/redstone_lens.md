---
navigation:
  title: Redstone Lens
  parent: items/index.md
  position: 39
  icon: quantimium:redstone_lens
item_ids:
- quantimium:redstone_lens
---

# Redstone Lens

<ItemImage id="quantimium:redstone_lens" scale="2" />

Redstone Lens adds the Redstone action to a laser. The laser gives the block it ends on a redstone signal of 1 to 15.

## How it works

- The signal is **strong** (as a lever on the block would, it powers through the block) or **weak** (as dust would, that block only).
- It comes from the air in front of the block, or from the laser block the laser leaves if there is no gap.
- A <ItemLink id="quantimium:laser_focus" /> it ends on is switched by it, as by a wired signal. Each focus it switches passes the lens's laser on to the next.
- It does nothing in the mirror.

## Settings

Use the lens in the air to set it:

- **Always**: gives its signal all the time.
- **Send on**: gives the signal its own block gets (wireless redstone).
- **Level emitter**: gives the signal while the Laser Ports up the laser, and the inventories they move items to and from, hold fewer than a count of one item, or that many or more (counted once a second). On a Logistics Controller's laser, it counts what that network holds instead.
- **Strength**: 1 to 15.

## Energy

Free.

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | No effect. |
| <ItemLink id="quantimium:speed_lens" /> | No effect. |
| <ItemLink id="quantimium:filter_lens" /> | No effect. |
| <ItemLink id="quantimium:pickup_lens" /> | No effect. |
| <ItemLink id="quantimium:void_lens" /> | No effect. |
| <ItemLink id="quantimium:smelting_lens" /> | No effect. |
| <ItemLink id="quantimium:crushing_lens" /> | No effect. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

An <ItemLink id="quantimium:isolator_lens" /> stops the signal unless it is set to pass redstone.

## Examples

- **Stock signal.** Set as a level emitter, it signals while the Laser Ports' inventories hold fewer than a count of an item, as an AE2 level emitter does.

## Recipes

<RecipesFor id="quantimium:redstone_lens" />
