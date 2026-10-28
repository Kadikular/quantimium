[[mechanic:laser.lens.damage]] Damage Lens adds the Damage action to a laser. The laser hurts every living thing standing in it, players included.

## How it works

- Hits every {{c:LaserTuning.DAMAGE_INTERVAL|s}} for {{c:LaserTuning.DAMAGE_PER_HIT}} damage.
- It charges up for its hits while there is something to hit, and keeps what it has charged. A hit can take more than a tick of power.
- Without an Entity Filter Lens, it hits anything alive in the laser, including you.

## Energy

{{c:LaserTuning.DAMAGE_FE_PER_HIT}} FE per hit on each target, paid as the hit lands. Each [[item:amplify_lens]] doubles the hit and its cost.

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | Each lens doubles the hit and its cost, up to x8. |
| [[item:speed_lens]] | No effect. |
| [[item:entity_filter_lens]] | Hits only the listed entities. It can also spare the young, or keep a breeding count. |
| [[item:pickup_lens]] | What it kills drops straight back up the laser to a collecting Laser Port. |
| [[item:void_lens]] | Destroys drops no Laser Port has room for, or all of them. |
| [[item:smelting_lens]] | Smelts what it kills drops, where they fall or on the way back. |
| [[item:crushing_lens]] | Crushes what it kills drops, where they fall or on the way back. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | Hits as a hand holding Looting, one level per lens, up to 3. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Mob grinder.** Damage, Pickup and an Entity Filter Lens set to hostile mobs, fired across a kill zone. What dies drops straight back to a collecting [[block:laser_port]].
- **Safe pen.** An Entity Filter Lens that keeps a breeding pair, so a pen is culled down to that count.
