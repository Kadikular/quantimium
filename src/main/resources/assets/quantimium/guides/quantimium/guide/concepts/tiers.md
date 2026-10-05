---
navigation:
  title: Machine tiers
  parent: concepts/index.md
  position: 4
---

# Machine tiers

Quantimium's machines come in three tiers, and you can tell which by the stone they are built from.

| Tier | Stone | Light | Machines |
| --- | --- | --- | --- |
| Low | Light stone, like a furnace | One accent, in a recessed window | Observation Chamber, Flux Detector, Basic Quantum Crafter, Quantum Exciter, Basic Anomaly Siphon |
| Mid | Deepslate | Thin azure lines inside the face | Quantum Crafter, Simulator, Tesseract Stabiliser, Flux Suppressor, Decoherence Projector, Zeno Field Controller |
| High | Obsidian | Full circuits, runes, violet beside azure | Quantum Foundry, Containment Hall, Attuned Glass, Rift Anchor, Rift Stabiliser, ME Superposition Crafter (with AE2) |

Azure means built: anything Quantimium made on purpose. Violet means grown: anomaly, crystals,
rifts. Where you see violet inside a machine, it is the thing the machine is holding.

## What each tier costs

Each tier's recipes need a material from the loop before it, so you
climb by doing what the mod is about, not by mining more:

| Tier | Needs | From | Machines |
| --- | --- | --- | --- |
| Low | <ItemLink id="quantimium:quantimium_trace" /> | Observing <ItemLink id="quantimium:unrealised_matter" /> | Observation Chamber, Basic Quantum Crafter, Flux Meter and Detector, Field Monitor, Quantum Exciter, Basic Anomaly Siphon, Flux Suppressor |
| Mid | <ItemLink id="quantimium:anomaly_fragment" />, Anomalite | Siphoning anomaly; crystals that grew on your machines | Rift Seed, Stabilised Portal, Quantum Crafter, Simulator, Tesseract Stabiliser, Flux Maintainer, Anomaly Siphon, Zeno Field Controller, Decoherence Lance and Projector, Harvest Laser and Rift Lens, Mirror Lens, Tether, the Pod's modules |
| High | <ItemLink id="quantimium:rift_residue" />, Tesseracts | Closing [flux rifts](../mechanics/flux-rifts.md); the Foundry | Quantum Foundry, Containment Hall, Field Regulator, Rift Anchor and Stabiliser, Superposition Pod and Unfolding Array, Entangled Dock, ME Superposition Crafter |

High starts with your first rift, found or planted with a <ItemLink id="quantimium:rift_seed" />: Residue builds the Foundry, which makes Tesseracts, and the
Containment Hall that lets a base run hot. The tier a machine looks (the stone above) and the tier
its recipe sits in mostly agree; the Flux Suppressor looks Mid but is made at Low, as an early
firebreak. Materials that aren't Quantimium's are asked for by their common tags (`#c:ingots/iron`
and so on), so any mod's iron will do.
