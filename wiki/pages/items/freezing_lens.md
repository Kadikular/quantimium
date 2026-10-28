[[mechanic:laser.lens.freeze]] Freezing Lens adds the Freezing action to a laser. The laser freezes what it ends on: water to ice, machines stopped, mobs held still, players chilled.

## How it works

- **Machines** stop ticking while the laser holds them and start again the moment it lets go. Blocks in the `quantimium:not_freezable` tag never stop, and get no ice or snow either.
- **Water** it ends on turns to ice. Now and then snow settles on top of the block it ends on.
- **Mobs** in the laser stop thinking for {{c:LaserTuning.FREEZE_MOB_TICKS|s}}, and are frozen again once half of that has passed. Each point of damage thaws them half a second sooner. Mobs in `quantimium:not_freezable` (the Ender Dragon) are never frozen. The Wither and the Warden can be.
- **Players** get colder a tick at a time while they stand in it, as in powder snow. They never get so cold it hurts.
- Through an [[block:area_prism]]: every machine and every living thing in the area, and a few random blocks each sweep.

## Energy

| What | Cost |
| --- | --- |
| A machine | {{c:LaserTuning.FREEZE_MACHINE_FE}} FE/t each |
| A mob | {{c:LaserTuning.FREEZE_FE_PER_HEALTH}} FE per point of its health (a zombie 500, a Warden 12,500) |
| A player | {{c:LaserTuning.FREEZE_CHILL_FE}} FE/t |
| Ice or snow | {{c:LaserTuning.FREEZE_BLOCK_FE}} FE per block |

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | No effect. |
| [[item:speed_lens]] | Through an Area Prism, looks at more blocks each sweep. |
| [[item:entity_filter_lens]] | Freezes only the listed entities. |
| [[item:pickup_lens]] | No effect. |
| [[item:void_lens]] | No effect. |
| [[item:smelting_lens]] | No effect. |
| [[item:crushing_lens]] | No effect. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | No effect. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Pause a machine.** Aim a Freezing laser at the machine. It stops while the laser is lit.
