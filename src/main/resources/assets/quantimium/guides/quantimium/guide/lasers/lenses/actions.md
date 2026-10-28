---
navigation:
  title: Action lenses
  parent: lasers/index.md
  position: 26
---

# Action lenses

An action lens gives a laser something to do. A laser can carry several actions, and an action stays on the laser until it ends.

| Lens | What it does |
| --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | Breaks the block the laser ends on. |
| <ItemLink id="quantimium:damage_lens" /> | Hurts what stands in the laser. |
| <ItemLink id="quantimium:healing_lens" /> | Heals what is living in the laser, and hurts the undead. |
| <ItemLink id="quantimium:freezing_lens" /> | Freezes machines, mobs, players and water. |
| <ItemLink id="quantimium:interact_lens" /> | Uses an item or an empty hand on what the laser ends on. |
| <ItemLink id="quantimium:acceleration_lens" /> | Speeds up the machine the laser ends on. |
| <ItemLink id="quantimium:place_lens" /> | Places blocks a Laser Port sends. |
| <ItemLink id="quantimium:insert_lens" /> | Puts items from a Laser Port into the inventory it ends on. |
| <ItemLink id="quantimium:extract_lens" /> | Takes items from the inventory it ends on. |
| <ItemLink id="quantimium:redstone_lens" /> | Gives the block it ends on a redstone signal. |

Click an action for what it does, what it costs and which modifiers change it. For every action and modifier at once, see the matrix below.

## Which modifiers work with which action

| Action | Amplify | Speed | Filter | Pickup | Void | Smelting | Crushing | Precision | Harmonic | Unrealizing |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | yes | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| <ItemLink id="quantimium:damage_lens" /> | yes | - | yes | yes | yes | yes | yes | - | yes | - |
| <ItemLink id="quantimium:healing_lens" /> | yes | yes | yes | yes | yes | yes | yes | - | - | - |
| <ItemLink id="quantimium:freezing_lens" /> | - | yes | yes | - | - | - | - | - | - | - |
| <ItemLink id="quantimium:interact_lens" /> | - | yes | yes | - | yes | - | - | - | - | - |
| <ItemLink id="quantimium:acceleration_lens" /> | - | yes | - | - | - | - | - | - | - | - |
| <ItemLink id="quantimium:place_lens" /> | - | yes | yes | - | - | - | - | - | - | - |
| <ItemLink id="quantimium:insert_lens" /> | - | yes | yes | - | yes | yes | yes | - | - | - |
| <ItemLink id="quantimium:extract_lens" /> | - | yes | yes | - | yes | yes | yes | - | - | - |
| <ItemLink id="quantimium:redstone_lens" /> | - | - | - | - | - | - | - | - | - | - |

"yes" means the modifier changes the action; "-" means it has no effect. Range, Phantom, UV and Isolator are not in the table because they work on every action. Open a lens for exactly what each one does.
