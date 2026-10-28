[[mechanic:laser.lens.insert]] Insert Lens adds the Insert action to a laser. The laser puts what the sending Laser Ports up the laser send into the inventory it ends on.

## How it works

- Moves up to {{c:LaserTuning.MOVE_ITEMS}} items every {{c:LaserTuning.PLACE_COOLDOWN|s}}. Each [[item:speed_lens]] doubles that, up to 64.
- It ramps up and down: each successful move halves its wait (from 4 s down to its own pace), and each look that finds nothing doubles it (up to 4 s, the `laserIdleSeconds` config option).
- Through an [[block:area_prism]], each inventory in the area in turn.
- It does nothing in the mirror.

## Energy

{{c:LaserTuning.PICKUP_FE_PER_STACK}} FE per stack moved, plus {{c:LaserTuning.SMELT_FE_PER_ITEM}} FE per item for each Smelting or Crushing lens.

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | No effect. |
| [[item:speed_lens]] | Moves 8 items per 10 ticks at base; each lens doubles that (16, 32, 64) and halves the wait. |
| [[item:filter_lens]] | Moves only the listed items. |
| [[item:pickup_lens]] | No effect. |
| [[item:void_lens]] | Void excess destroys items that don't fit. Void all destroys every item sent, so the target works as a trash can. |
| [[item:smelting_lens]] | Smelts items on the way in. |
| [[item:crushing_lens]] | Crushes items on the way in. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | No effect. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Feed a machine.** A sending [[block:laser_port]] holding the input items, Insert, and the laser ending on the machine.
- **Trash can.** Insert with a Void Lens set to Void all destroys everything a sending Laser Port sends.
