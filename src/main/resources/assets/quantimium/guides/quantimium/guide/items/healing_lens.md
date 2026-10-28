---
navigation:
  title: Healing Lens
  parent: items/index.md
  position: 25
  icon: quantimium:healing_lens
item_ids:
- quantimium:healing_lens
---

# Healing Lens

<ItemImage id="quantimium:healing_lens" scale="2" />

Healing Lens adds the Healing action to a laser. The laser heals every living thing in it by 2 health every 0.5 s, and hurts the undead instead, as Instant Health does.

## How it works

- Players are healed too. Anything at full health is left alone and costs nothing.
- The undead take 3 damage per sweep.
- An <ItemLink id="quantimium:entity_filter_lens" /> picks who it works on.

## Energy

500 FE per sweep for each one healed or hurt, charged as it goes. Each <ItemLink id="quantimium:amplify_lens" /> doubles the heal, the harm and the cost.

## Works with

| Modifier | Effect |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | Each lens doubles the heal and the harm to the undead, and the cost, up to x8. |
| <ItemLink id="quantimium:speed_lens" /> | Sweeps twice as often per lens, up to four times (every 10, 5, then 2 ticks). |
| <ItemLink id="quantimium:entity_filter_lens" /> | Works only on the listed entities, for example just you. |
| <ItemLink id="quantimium:pickup_lens" /> | Drops of undead it kills go back up the laser. |
| <ItemLink id="quantimium:void_lens" /> | Destroys drops of undead it kills that no Laser Port has room for, or all of them. |
| <ItemLink id="quantimium:smelting_lens" /> | Smelts drops of undead it kills. |
| <ItemLink id="quantimium:crushing_lens" /> | Crushes drops of undead it kills. |
| <ItemLink id="quantimium:precision_lens" /> | No effect. |
| <ItemLink id="quantimium:harmonic_lens" /> | No effect. |
| <ItemLink id="quantimium:unrealising_lens" /> | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md).

## Examples

- **Personal healing aura.** An <ItemLink id="quantimium:area_prism" /> with Healing and an Entity Filter Lens listing yourself heals only you.

## Recipes

<RecipesFor id="quantimium:healing_lens" />
