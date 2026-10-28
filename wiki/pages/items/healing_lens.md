[[mechanic:laser.lens.heal]] Healing Lens adds the Healing action to a laser. The laser heals every living thing in it by {{c:LaserTuning.HEAL_PER_SWEEP}} health every {{c:LaserTuning.DAMAGE_INTERVAL|s}}, and hurts the undead instead, as Instant Health does.

## How it works

- Players are healed too. Anything at full health is left alone and costs nothing.
- The undead take {{c:LaserTuning.HEAL_HARM_PER_SWEEP}} damage per sweep.
- An [[item:entity_filter_lens]] picks who it works on.

## Energy

{{c:LaserTuning.HEAL_FE_PER_SWEEP}} FE per sweep for each one healed or hurt, charged as it goes. Each [[item:amplify_lens]] doubles the heal, the harm and the cost.

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | Each lens doubles the heal and the harm to the undead, and the cost, up to x8. |
| [[item:speed_lens]] | Sweeps twice as often per lens, up to four times (every 10, 5, then 2 ticks). |
| [[item:entity_filter_lens]] | Works only on the listed entities, for example just you. |
| [[item:pickup_lens]] | Drops of undead it kills go back up the laser. |
| [[item:void_lens]] | Destroys drops of undead it kills that no Laser Port has room for, or all of them. |
| [[item:smelting_lens]] | Smelts drops of undead it kills. |
| [[item:crushing_lens]] | Crushes drops of undead it kills. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | No effect. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Personal healing aura.** An [[block:area_prism]] with Healing and an Entity Filter Lens listing yourself heals only you.
