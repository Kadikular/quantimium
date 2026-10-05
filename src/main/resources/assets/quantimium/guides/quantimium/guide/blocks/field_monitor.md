---
navigation:
  title: Field Monitor
  parent: blocks/index.md
  position: 12
  icon: quantimium:field_monitor
item_ids:
- quantimium:field_monitor
---

# Field Monitor

<ItemImage id="quantimium:field_monitor" scale="2" />

Watches the field in the 9×9 chunks round it and raises the alarm. No power needed.

Its screen shows a map of those chunks, north up, with its own outlined:
flux in azure, anomaly washed violet over it, and chunks under containment marked in the corner, pale
when held and amber when overloaded. Beside the map it says what it's watching for, how many chunks
are alerting, and the peak flux and anomaly it can see.

Set what it watches for with the **W** tab:

| Watch | Alerts on a chunk that is… |
| --- | --- |
| Overloaded containment | covered by containment but past 100% load |
| Anomaly, Medium+ | not contained, with anomaly at Medium or more: tears, mites and the surcharge |
| Anomaly, High+ | not contained, with anomaly at High or more: crystals and failing tears too |

While any chunk alerts, it lights up and gives a full **redstone** signal on every side; a
**comparator** reads how many chunks are alerting, up to 15. Wire it to a lamp, an alarm, or to switch
on a Siphon when a hot district spills over. It reads the field once a second.

The <ItemLink id="quantimium:flux_meter" /> and [Jade](../concepts/partner-mods.md) show what it's watching for and how
many chunks are alerting.

## Recipes

<RecipesFor id="quantimium:field_monitor" />
