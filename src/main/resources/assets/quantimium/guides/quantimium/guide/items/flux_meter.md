---
navigation:
  title: Flux Meter
  parent: items/index.md
  position: 7
  icon: quantimium:flux_meter
item_ids:
- quantimium:flux_meter
---

# Flux Meter

<ItemImage id="quantimium:flux_meter" scale="2" />

> *In game:* Reports the field where you stand

Reads the field. Use it in the air for the chunk you stand in, or on a block for that block's chunk:
its [flux and anomaly](../concepts/flux-and-anomaly.md), each with its band, on the action bar.

Used on a block that has something to say, it adds a line about it:

| Block | What it says |
| --- | --- |
| <ItemLink id="quantimium:quantum_crafter" />, <ItemLink id="quantimium:quantum_simulator" />, <ItemLink id="quantimium:quantum_observation_chamber" />, <ItemLink id="quantimium:quantum_foundry_controller" /> (or any Foundry part), ME Superposition Crafter | Its average FE/t, and the flux a second that puts into this chunk |
| <ItemLink id="quantimium:quantum_exciter" /> | The same, at its ten-fold flux |
| <ItemLink id="quantimium:zeno_field_controller" /> | Holding or accelerating, its radius and factor, then its FE/t and flux |
| <ItemLink id="quantimium:rift_anchor" /> | The rift's stage, how many stabilisers hold it and the residue an hour; or that it is loose, or that there is no rift |
| <ItemLink id="quantimium:rift_stabiliser" /> | Beaming at a rift (and at what FE/t), claimed but idle, or free |
| <ItemLink id="quantimium:decoherence_projector" /> | Firing, watching, or out of power, and its FE/t |
| <ItemLink id="quantimium:harvest_laser" /> | Firing (its FE/t and how near the next shard is), or what's wrong with its line, and the shards it holds |
| <ItemLink id="quantimium:flux_suppressor" />, <ItemLink id="quantimium:basic_anomaly_siphon" />, and the other field control blocks | Its setpoints and its FE/t |
| <ItemLink id="quantimium:anomaly_containment_hall" /> | Its FE/t, its pull and its rating |
| <ItemLink id="quantimium:budding_anomalite" />, <ItemLink id="quantimium:anomalite_crystal" /> | What it feeds on, and a crystal's stage |
| <ItemLink id="quantimium:flux_detector" /> | What it watches and the signal it gives |
| <ItemLink id="quantimium:entangled_dock" /> | The item it is charging, and at what FE/t |
| <ItemLink id="quantimium:flux_maintainer" />, <ItemLink id="quantimium:anomaly_siphon" />, <ItemLink id="quantimium:field_regulator" /> | Its floor and ceiling, and its FE/t |
| <ItemLink id="quantimium:superposition_pod" /> | Its name, whose double it holds (or that it is empty), and its stored FE |
| <ItemLink id="quantimium:unfolding_array" /> | How far along it is with unfolding someone, or that it is idle |

The flux a machine puts into its own chunk is a fifth of what it emits: the rest goes to the eight chunks round it.

## Recipes

<RecipesFor id="quantimium:flux_meter" />
