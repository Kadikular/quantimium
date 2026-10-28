---
navigation:
  title: Damage Lens
  parent: items/index.md
  position: 12
  icon: quantimium:damage_lens
item_ids:
- quantimium:damage_lens
---

# Damage Lens

<ItemImage id="quantimium:damage_lens" scale="2" />

Damage Lens adds the Damage action to a laser. The laser hurts every living thing standing in it, players included.

## How it works

- Hits every 0.5 s for 4 damage.
- It charges up for its hits while there is something to hit, and keeps what it has charged. A hit can take more than a tick of power.
- Without an Entity Filter Lens, it hits anything alive in the laser, including you.

## Energy

1,000 FE per hit on each target, paid as the hit lands. Each <ItemLink id="quantimium:amplify_lens" /> doubles the hit and its cost.

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | Each lens doubles the hit and its cost, up to x8. |
| <ItemLink id="quantimium:speed_lens" /> | No effect. |
| <ItemLink id="quantimium:entity_filter_lens" /> | Hits only the listed entities. It can also spare the young, or keep a breeding count. |
| <ItemLink id="quantimium:pickup_lens" /> | What it kills drops straight back up the laser to a collecting Laser Port. |
| <ItemLink id="quantimium:void_lens" /> | Destroys drops no Laser Port has room for, or all of them. |
| <ItemLink id="quantimium:smelting_lens" /> | Smelts what it kills drops, where they fall or on the way back. |
| <ItemLink id="quantimium:crushing_lens" /> | Crushes what it kills drops, where they fall or on the way back. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | Hits as a hand holding Looting, one level per lens, up to 3. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Mob grinder.** Damage, Pickup and an Entity Filter Lens set to hostile mobs, fired across a kill zone. What dies drops straight back to a collecting <ItemLink id="quantimium:laser_port" />.
- **Safe pen.** An Entity Filter Lens that keeps a breeding pair, so a pen is culled down to that count.

## Recipes

<RecipesFor id="quantimium:damage_lens" />
