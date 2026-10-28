---
navigation:
  title: Lens energy
  parent: lasers/index.md
  position: 30
---

# Lens energy

Lenses draw power from the sources whose lasers reached them. Work is paid for as it happens: a cut as it is made, a hit when it lands, an item as it is carried. Work that happens at once (a hit, an item carried, a block placed) is paid for first each tick, so machines on the same laser can't starve it.

## Base costs

| Lens | Cost |
| --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | 400 FE per point of hardness (at least one point) |
| <ItemLink id="quantimium:damage_lens" /> | 1,000 FE per hit, per target |
| <ItemLink id="quantimium:healing_lens" /> | 500 FE per sweep, per target healed or hurt |
| <ItemLink id="quantimium:freezing_lens" /> | 20 FE/t per machine; 25 FE per mob health point; 5 FE/t per chilled player; 200 FE per ice or snow block |
| <ItemLink id="quantimium:interact_lens" /> | 200 FE per use (mining with Hit costs Harvest's) |
| <ItemLink id="quantimium:acceleration_lens" /> | 500 FE per extra tick, so (multiplier - 1) x 500 FE/t |
| <ItemLink id="quantimium:place_lens" /> | 200 FE per block |
| <ItemLink id="quantimium:insert_lens" /> / <ItemLink id="quantimium:extract_lens" /> | 200 FE per stack |
| <ItemLink id="quantimium:pickup_lens" /> | 200 FE per stack carried |
| <ItemLink id="quantimium:smelting_lens" /> | 25 FE per item |
| <ItemLink id="quantimium:crushing_lens" /> | 25 FE per item |
| <ItemLink id="quantimium:redstone_lens" /> | Free |

Modifiers not listed (Range, Speed, Filter, Void, Precision, Harmonic, Unrealizing, Isolator, UV) cost nothing themselves.

## How modifiers change the cost

| Modifier | Effect on cost |
| --- | --- |
| <ItemLink id="quantimium:amplify_lens" /> | Harvest: one more share of the base cost per lens (x2, x3, x4). Damage and Healing: doubles per lens (x2, x4, x8), with the hit or heal. |
| <ItemLink id="quantimium:speed_lens" /> | The cost per action is the same, but actions happen more often, so power per second rises. Acceleration's multiplier also rises, and its cost with it. |
| <ItemLink id="quantimium:phantom_lens" /> | Every action costs twice as much. Plus a configurable upkeep (20 FE by default) each tick for each block the laser passes through. |
| <ItemLink id="quantimium:smelting_lens" /> / <ItemLink id="quantimium:crushing_lens" /> | Add their per-item cost to Pickup, Insert and Extract; a step that changes nothing is free. |
| <ItemLink id="quantimium:area_prism" /> | A cut or a placement costs x1.5. |
| <ItemLink id="quantimium:pickup_lens" /> | Adds the per-stack carry cost to Harvest and Damage drops. |

## Worked examples

| Setup | Cost |
| --- | --- |
| Harvest on stone (hardness 1.5), no modifiers | 600 FE per block; at the 10-tick minimum, 60 FE/t |
| The same with one Amplify Lens | 1,200 FE per block |
| The same through an Area Prism | 900 FE per block |
| Damage on one mob | 1,000 FE per hit, one hit every 0.5 s: 2,000 FE/s |
| Damage with two Amplify Lenses | 4,000 FE per hit, for 16 damage |
| Healing one player | 500 FE per sweep |
| Acceleration at 2x / 4x / 16x | 500 / 1,500 / 7,500 FE/t |
| Extract, one stack | 200 FE |
| Extract with Smelting, 8 items | 200 + 8 x 25 = 400 FE |

## Where the power comes from

Power comes from the laser's emitters: an emitter only ever pays for the blocks its own laser reaches. See [Emitters](../emitters.md) and [Networks and rules](../networks.md). An action is paid for by the sources whose laser reached the lens that gave it.
