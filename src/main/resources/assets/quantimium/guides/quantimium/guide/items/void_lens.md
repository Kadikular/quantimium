---
navigation:
  title: Void Lens
  parent: items/index.md
  position: 54
  icon: quantimium:void_lens
item_ids:
- quantimium:void_lens
---

# Void Lens

<ItemImage id="quantimium:void_lens" scale="2" />

Void Lens destroys what the laser's Laser Ports won't take. Like Pickup, it stays on the laser, so actions added after it get it too.

## Settings

Use the lens in the air (or its button in a mount) to set it.

- **Void excess** (default): with Pickup, what the Laser Ports or network up the laser have room for goes to them and the rest is destroyed, so Harvest never waits for room. With no ports, or without Pickup, everything the actions free is destroyed.
- **Void all**: everything the actions free or Pickup gathers is destroyed, ports or not (Harvest's drops, what Damage kills, items lying in the laser).
- **List** (nine entries, allow or deny, as the Filter Lens): destroys only those (or all but those). The rest is carried, or waits for room, as if the lens weren't there. Seeds listed with Void excess: a wheat farm keeps its wheat and burns the seeds it has no room for.
- Fluids too, with Insert and Extract: list them by right-clicking an entry with a filled bucket.

## What each setting does to items on the move

| Items | Void excess | Void all |
| --- | --- | --- |
| Freed (Harvest's drops, what Damage kills, Pickup's gathering) | What no port has room for is destroyed | Destroyed |
| Sent down to Insert | Put in where there is room, the rest destroyed | Destroyed, whatever the laser ends on: a trash can |
| Taken out by Extract (only with a list) | Listed items taken, what no port has room for destroyed | Listed items taken and destroyed |
| Handed back by Interact (an empty bucket, a worn tool) | Destroyed if no port takes it | The same |

Placement never destroys what it can't place.

## Energy

Free.

## Applies to

| Action | Effect |
| --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | Destroys drops no Laser Port has room for (Void excess) or every drop (Void all). The cut never waits for room. |
| <ItemLink id="quantimium:damage_lens" /> | Destroys drops no Laser Port has room for, or all of them. |
| <ItemLink id="quantimium:healing_lens" /> | Destroys drops of undead it kills that no Laser Port has room for, or all of them. |
| <ItemLink id="quantimium:freezing_lens" /> | No effect. |
| <ItemLink id="quantimium:interact_lens" /> | Destroys what it hands back (an empty bucket, a worn tool) if no Laser Port takes it. |
| <ItemLink id="quantimium:acceleration_lens" /> | No effect. |
| <ItemLink id="quantimium:place_lens" /> | No effect. Placement never destroys what it can't place. |
| <ItemLink id="quantimium:insert_lens" /> | Void excess destroys items that don't fit. Void all destroys every item sent, so the target works as a trash can. |
| <ItemLink id="quantimium:extract_lens" /> | With a list on the Void Lens, takes the listed items and destroys them. |
| <ItemLink id="quantimium:redstone_lens" /> | No effect. |

## Examples

- **Trash can.** Insert with Void all.
- **Seed burner.** A wheat farm's Void excess with seeds listed.

## Recipes

<RecipesFor id="quantimium:void_lens" />
