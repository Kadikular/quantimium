---
navigation:
  title: Range Lens
  parent: items/index.md
  position: 38
  icon: quantimium:range_lens
item_ids:
- quantimium:range_lens
---

# Range Lens

<ItemImage id="quantimium:range_lens" scale="2" />

Range Lens doubles how far each run of a laser reaches.

## How it works

- A run is as long as its source's range. A laser with an action reaches 8 blocks a run.
- Each Range Lens doubles a run, up to 64 blocks by default (`maxRange` in the `lasers` config): 8, 16, 32, 64.
- It works on every action and stays on the laser.

## Energy

Free.

## Examples

- **Long quarry shaft.** A Harvest laser with two Range Lenses reaches 32 blocks a run.

## Recipes

<RecipesFor id="quantimium:range_lens" />
