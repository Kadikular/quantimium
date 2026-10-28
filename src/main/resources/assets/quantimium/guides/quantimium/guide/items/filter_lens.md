---
navigation:
  title: Item Filter Lens
  parent: items/index.md
  position: 16
  icon: quantimium:filter_lens
item_ids:
- quantimium:filter_lens
---

# Item Filter Lens

<ItemImage id="quantimium:filter_lens" scale="2" />

Item Filter Lens limits the item actions before it to the items it lets through.

## How it works

- Insert, Extract, Pickup and Placement read it.
- Nine entries, set as allow or deny, with tags (shift-click an entry).
- A filled container (a water bucket) stands for its fluid, for Insert and Extract. Fluid entries apply only to fluids and the rest only to items: a list of items lets every fluid through.

## Settings

Use the lens in the air to set it.

## Energy

Free.

## Applies to

The three filter lenses split the work: this one for items, <ItemLink id="quantimium:block_filter_lens" /> for Harvest, <ItemLink id="quantimium:entity_filter_lens" /> for hits and interactions.

| Action | Filter that applies |
| --- | --- |
| <ItemLink id="quantimium:place_lens" /> | Item Filter Lens: places only the listed items. |
| <ItemLink id="quantimium:insert_lens" /> | Item Filter Lens: moves only the listed items. |
| <ItemLink id="quantimium:extract_lens" /> | Item Filter Lens: takes only the listed items. |
| <ItemLink id="quantimium:pickup_lens" /> | Item Filter Lens: carries only the listed items. |

## Recipes

<RecipesFor id="quantimium:filter_lens" />
