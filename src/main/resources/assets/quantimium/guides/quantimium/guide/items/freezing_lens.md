---
navigation:
  title: Freezing Lens
  parent: items/index.md
  position: 22
  icon: quantimium:freezing_lens
item_ids:
- quantimium:freezing_lens
---

# Freezing Lens

<ItemImage id="quantimium:freezing_lens" scale="2" />

Freezing Lens adds the Freezing action to a laser. The laser freezes what it ends on: water to ice, machines stopped, mobs held still, players chilled.

## How it works

- **Machines** stop ticking while the laser holds them and start again the moment it lets go. Blocks in the `quantimium:not_freezable` tag never stop, and get no ice or snow either.
- **Water** it ends on turns to ice. Now and then snow settles on top of the block it ends on.
- **Mobs** in the laser stop thinking for 10 s, and are frozen again once half of that has passed. Each point of damage thaws them half a second sooner. Mobs in `quantimium:not_freezable` (the Ender Dragon) are never frozen. The Wither and the Warden can be.
- **Players** get colder a tick at a time while they stand in it, as in powder snow. They never get so cold it hurts.
- Through an <ItemLink id="quantimium:area_prism" />: every machine and every living thing in the area, and a few random blocks each sweep.

## Energy

| What | Cost |
| --- | --- |
| A machine | 20 FE/t each |
| A mob | 25 FE per point of its health (a zombie 500, a Warden 12,500) |
| A player | 5 FE/t |
| Ice or snow | 200 FE per block |

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | No effect. |
| <ItemLink id="quantimium:speed_lens" /> | Through an Area Prism, looks at more blocks each sweep. |
| <ItemLink id="quantimium:entity_filter_lens" /> | Freezes only the listed entities. |
| <ItemLink id="quantimium:pickup_lens" /> | No effect. |
| <ItemLink id="quantimium:void_lens" /> | No effect. |
| <ItemLink id="quantimium:smelting_lens" /> | No effect. |
| <ItemLink id="quantimium:crushing_lens" /> | No effect. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Pause a machine.** Aim a Freezing laser at the machine. It stops while the laser is lit.

## Recipes

<RecipesFor id="quantimium:freezing_lens" />
