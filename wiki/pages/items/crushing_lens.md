[[mechanic:laser.lens.crushing]] Crushing Lens crushes what the actions before it free or move, everywhere Smelting smelts.

## How it works

- Stone becomes cobblestone, cobblestone and deepslate become gravel, gravel and sandstone become sand. Anything else is crushed as the pack's crushers would, using their sure outputs only (raw ore to dust, say).
- Which crushers, and in what order, is `crushingRecipeTypes` in the `lasers` config: Energized Power's pulverizer and crusher, Ender IO's SAG Mill, Mekanism, Create, Thermal and others.
- Each Crushing or Smelting lens is a step, in the lenses' order (up to {{c:LaserTuning.PROCESS_STEPS}}). Crushing then Smelting makes raw ore dust, then ingots. Crushing, Crushing, Smelting makes cobblestone gravel, then sand, then glass.
- A step that changes nothing is free, and so is a run of steps that ends as it began.
- Where nothing says which came first (a crushing laser and a smelting laser meeting at a mirror), it crushes first.
- Where they change no action before them (alone, or before one: Crushing, Smelting, Harvest), they act on the world themselves, as one, in their order. A Harvest after them waits until they are done: sandstone becomes glass, then it's cut.
- Unrealized Matter gets the first of those crushers that gives it something new in its history.
- Alone it crushes what lies in the laser, else the block it ends on, in place: stone becomes cobblestone, then gravel, then sand. With [[item:phantom_lens]], it goes through what it can't change to the first block on its way it can (Smelting too).

## Energy

{{c:LaserTuning.CRUSH_FE_PER_ITEM}} FE per item crushed.

## Applies to

| Action | Effect |
| --- | --- |
| [[item:harvest_lens]] | Crushes the drops. |
| [[item:damage_lens]] | Crushes what it kills drops, where they fall or on the way back. |
| [[item:healing_lens]] | Crushes drops of undead it kills. |
| [[item:freezing_lens]] | No effect. |
| [[item:interact_lens]] | No effect. |
| [[item:acceleration_lens]] | No effect. |
| [[item:place_lens]] | No effect. |
| [[item:insert_lens]] | Crushes items on the way in. |
| [[item:extract_lens]] | Crushes items on the way out. |
| [[item:redstone_lens]] | No effect. |
