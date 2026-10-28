# Lenses

*A laser is just power until something gives it a job.*

A lens gives a laser a job or changes how it does one. Lenses sit in a [[block:lens_mount]] (four slots) or a Focus Emitter (one slot), and the laser passes through them in order.

[[mechanic:laser.lens.mount]] A Lens Mount takes the laser straight in by its back, and sends it out of its front through its lenses, left to right. A laser from any other side, or at an angle, stops on it. Open it with an empty hand. Sneak-click or wrench a side to point it there. Down each side, four slot panels show what is fitted: dark when empty, the lens's color when there is one. A button under each lens opens that lens's settings, lit when it has any. A lensed laser is tinted by what it does. The laser still carries power to whatever it ends on.

## Two kinds

- **[Actions](actions.md)** give the laser something to do: break a block, hurt a mob, move items. A laser can carry several, and an action stays on the laser until it ends.
- **[Modifiers](modifiers.md)** change the actions the laser already carries.

[[mechanic:laser.lens.order]] **Order matters.** A modifier changes the actions the laser already carries when it meets them: those in slots to its left, and those from mounts before. Never one that comes after. Harvest then Filter cuts only what the filter lets through. Filter then Harvest cuts anything.

### Every action

Four modifiers work on every action: [[item:range_lens]], [[item:phantom_lens]], [[item:uv_lens]] and [[item:isolator_lens]]. The rest change specific actions, shown in each lens's *Works with* table.

## Pickup and Void

Pickup and Void are about items, not about one action. A collecting [[block:laser_port]] up the laser is where freed items go back to; a sending one is where items used come from.

- **Pickup** says "carry what's freed back to the ports". Placed after an action it brings back what that action frees. Before any action, or alone, it gathers whatever lies in the laser.
- **Void** says "destroy what the ports won't take" (or, set to Void all, everything).

Both stay on the laser, so once the laser has met one, every action after it gets it. Pickup, Harvest, Pickup does both: a tree farm's logs come back as they are cut, and its leaves as they fall.

## Choosing lenses

| I want to | Use |
| --- | --- |
| Mine blocks | [[item:harvest_lens]], with [[item:pickup_lens]] to collect |
| Mine a crop and replant | Harvest, Pickup, [[item:place_lens]], [[item:block_filter_lens]] (ripe only) |
| Mine only ores through rock | Harvest, Pickup, [[item:phantom_lens]], Block Filter set to ores |
| Kill mobs | [[item:damage_lens]], Pickup, [[item:entity_filter_lens]] |
| Heal myself | [[item:healing_lens]] with an Entity Filter listing me |
| Move items | [[item:insert_lens]] or [[item:extract_lens]], with [[block:laser_port]] |
| Cook or crush items on the way | [[item:smelting_lens]] or [[item:crushing_lens]] after Extract or Insert |
| Delete items | Insert or Pickup with [[item:void_lens]] |
| Speed up a machine | [[item:acceleration_lens]] with [[item:speed_lens]] |
| Pause a machine | [[item:freezing_lens]] |
| Send a redstone signal | [[item:redstone_lens]] |
| Mine harder blocks | [[item:amplify_lens]] after Harvest |
| Collect blocks whole | [[item:precision_lens]] |
| Get more drops | [[item:harmonic_lens]] |
| Reach farther | [[item:range_lens]] |
| Hide the laser | [[item:uv_lens]] |
| Keep a laser from carrying anything but power | [[item:isolator_lens]] |

More: [Energy](energy.md) · [Examples](examples.md).
