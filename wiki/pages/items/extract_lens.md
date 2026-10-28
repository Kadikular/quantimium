[[mechanic:laser.lens.extract]] Extract Lens adds the Extract action to a laser. The laser takes items from the inventory it ends on, back up the laser to the collecting Laser Ports.

## How it works

- Moves up to {{c:LaserTuning.MOVE_ITEMS}} items every {{c:LaserTuning.PLACE_COOLDOWN|s}}, as many as the ports have room for. Each [[item:speed_lens]] doubles that, up to 64.
- Through an [[block:area_prism]], from each inventory in the area in turn.
- [[mechanic:laser.lens.extract_fluid]] Fluids too. With no items to take, it drains the fluid handler it ends on into the collecting [[block:laser_tank]]s up the laser. It moves {{c:LaserTuning.MOVE_FLUID_MB}} mB at a time, doubled for each Speed Lens.
- It does nothing in the mirror.

## Energy

{{c:LaserTuning.PICKUP_FE_PER_STACK}} FE per stack moved, plus {{c:LaserTuning.CRUSH_FE_PER_ITEM}} FE per item for each Smelting or Crushing lens.

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | No effect. |
| [[item:speed_lens]] | Moves 8 items per 10 ticks at base; each lens doubles that (16, 32, 64) and halves the wait. |
| [[item:filter_lens]] | Takes only the listed items. |
| [[item:pickup_lens]] | No effect. |
| [[item:void_lens]] | With a list on the Void Lens, takes the listed items and destroys them. |
| [[item:smelting_lens]] | Smelts items on the way out. |
| [[item:crushing_lens]] | Crushes items on the way out. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | No effect. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Chest to pipes.** Extract with the laser ending on a chest, and a collecting [[block:laser_port]] that pipes pull from.
