---
navigation:
  title: Pickup Lens
  parent: items/index.md
  position: 34
  icon: quantimium:pickup_lens
item_ids:
- quantimium:pickup_lens
---

# Pickup Lens

<ItemImage id="quantimium:pickup_lens" scale="2" />

Pickup Lens makes a laser carry items back up it. After an action, it returns what that action frees to a collecting Laser Port. Before any action, or alone, it gathers items lying in the laser.

## How it works

| Where it is | What it does |
| --- | --- |
| After an action (Harvest then Pickup) | Brings back what that action frees. The action waits while there is nowhere to put it. |
| Before any action, or alone | Gathers items lying within about a block of the laser: what Harvest cuts, leaves decaying, anything dropped there. |

- Items go to the nearest collecting <ItemLink id="quantimium:laser_port" />. What that one can't take goes to the next. Back past a cavity, it reaches the ports up the laser that filled it.
- With no port, drops fall where they are.
- A Harvest laser with Pickup only cuts a block whose drops the ports would take, all of them (room, and filters that let them in). Otherwise it waits and leaves nothing lying.
- In the mirror, Pickup draws Veil Thread from a held Veiled. See [Veil Thread](veil_thread.md).
- Pickup stays on the laser: actions added after it get it too. Both on one laser (Pickup, Harvest, Pickup) do both: a tree farm's logs come back as they are cut, and its leaves as they fall.

## Energy

200 FE per stack carried.

## Applies to

| Action | Effect |
| --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | Sends the drops up the laser to a collecting Laser Port. A cut waits until the ports can take all its drops. |
| <ItemLink id="quantimium:damage_lens" /> | What it kills drops straight back up the laser to a collecting Laser Port. |
| <ItemLink id="quantimium:healing_lens" /> | Drops of undead it kills go back up the laser. |
| <ItemLink id="quantimium:freezing_lens" /> | No effect. |
| <ItemLink id="quantimium:interact_lens" /> | No effect. |
| <ItemLink id="quantimium:acceleration_lens" /> | No effect. |
| <ItemLink id="quantimium:place_lens" /> | No effect. |
| <ItemLink id="quantimium:insert_lens" /> | No effect. |
| <ItemLink id="quantimium:extract_lens" /> | No effect. |
| <ItemLink id="quantimium:redstone_lens" /> | No effect. |

## Examples

- **Tree farm.** Harvest, Pickup and Placement, with saplings in a sending Laser Port.

## Recipes

<RecipesFor id="quantimium:pickup_lens" />
