[[mechanic:laser.lens.filter]] Filter Lens limits the item and fluid actions before it to the items and fluids it lets through.

## How it works

- Insert, Extract, Pickup and Placement read it.
- Nine entries, set as allow or deny, with tags (shift-click an entry).
- [[mechanic:laser.lens.filter_fluid]] Click an entry with a bucket to list the bucket; right-click to list the fluid in it, drawn as that fluid. A fluid can also be dragged in from JEI.
- Fluid entries apply only to fluids (for Insert and Extract) and the rest only to items, so a list of items lets every fluid through.

## Settings

Use the lens in the air to set it.

## Energy

Free.

## Applies to

The three filter lenses split the work: this one for items, [[item:block_filter_lens]] for Harvest, [[item:entity_filter_lens]] for hits and interactions.

| Action | Filter that applies |
| --- | --- |
| [[item:place_lens]] | Filter Lens: places only the listed items. |
| [[item:insert_lens]] | Filter Lens: moves only the listed items. |
| [[item:extract_lens]] | Filter Lens: takes only the listed items. |
| [[item:pickup_lens]] | Filter Lens: carries only the listed items. |
