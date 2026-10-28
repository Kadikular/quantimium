[[mechanic:laser.lens.smelting]] Smelting Lens smelts, as a furnace would, what the actions before it free or move.

## How it works

- Smelts what a Harvest laser cuts and what Damage kills (picked up or left lying), what Pickup gathers, and what Insert puts in and Extract takes out. Sand becomes glass, ore ingots, a pig's drops cooked.
- Unrealized Matter gets smelting added to its history, as a Quantum Crafter with a furnace gives it.
- What a furnace can't smelt stays as it is, at no cost.
- With no other action on the laser, it is an action of its own: it smelts what lies in the laser, else the block it ends on, every {{c:LaserTuning.PLACE_COOLDOWN|s}}. A block it smelts into takes its place (sand becomes glass, and the laser goes on through to the next). Anything else drops there.
- Each Crushing or Smelting lens is a step, in the lenses' order (up to {{c:LaserTuning.PROCESS_STEPS}}). See [Crushing Lens](crushing_lens.md).

## Energy

{{c:LaserTuning.SMELT_FE_PER_ITEM}} FE per item smelted.

## Applies to

| Action | Effect |
| --- | --- |
| [[item:harvest_lens]] | Smelts the drops. |
| [[item:damage_lens]] | Smelts what it kills drops, where they fall or on the way back. |
| [[item:healing_lens]] | Smelts drops of undead it kills. |
| [[item:freezing_lens]] | No effect. |
| [[item:interact_lens]] | No effect. |
| [[item:acceleration_lens]] | No effect. |
| [[item:place_lens]] | No effect. |
| [[item:insert_lens]] | Smelts items on the way in. |
| [[item:extract_lens]] | Smelts items on the way out. |
| [[item:redstone_lens]] | No effect. |

## Examples

- **Auto-smelter.** Extract, Smelting and a collecting Laser Port: items leave a chest smelted.
