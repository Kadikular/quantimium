---
navigation:
  title: Quantum Observation Chamber
  parent: machines/index.md
  position: 11
  icon: quantimium:quantum_observation_chamber
---

# Quantum Observation Chamber

<ItemImage id="quantimium:quantum_observation_chamber" scale="2" />

| | |
| --- | --- |
| **Type** | Automated collapse |
| **Tier** | [Mid](../concepts/tiers.md) |
| **Energy buffer** | 200,000 FE |
| **Max input** | 4,000 FE/t |
| **Speed** | 4 Matter every 0.5 s |
| **Cost** | 400 FE a collapse |
| **Automation** | Every face, configurable |

The <ItemLink id="quantimium:observation_chamber" /> grown up. Where the stone chamber waits for a redstone pulse, this
one observes on its own: give it <ItemLink id="quantimium:unrealised_matter" /> and power, and it collapses it into real
ores as fast as it is fed, handing the results to whatever pipes you give it.

![The Quantum Observation Chamber's screen](../assets/gui/quantum_observation_chamber.png)

## Collapsing

Every 0.5 s
it collapses up to 4 Matter from its three input slots,
each rolled as the stone chamber rolls it: one of the pack's ores, rarer in a hotter field (see
<ItemLink id="quantimium:unrealised_matter" />), into
its eight result slots. Each collapse costs
**400 FE**, raised by the chunk's anomaly
surcharge like every machine: 160 FE/t running flat out.

Without the power for a collapse it waits, Matter untouched, and says
**No power**.

Like any Quantimium machine it emits **1 flux per 1,000 FE** it spends,
once a second.

## Trace

Each collapse has a 0.5 chance of a <ItemLink id="quantimium:quantimium_trace" />
as well, into its own slot. The **T** tab decides what happens when that slot is full:

- **Void** (lava bucket, the default): the extra Trace is destroyed and collapsing carries on.
- **Keep** (chest): it stops and says **Full** until there is room,
  so no Trace is ever lost. The stone chamber keeps the same rule.

## Faces

Open the **S** tab (or use a wrench) to set each face, as on the
[Quantum Crafter](quantum-crafter.md): **In** takes Matter, **Out** hands results and Trace out, **Both**
does either, **Off** does nothing. Every face starts as **Both**. See
[Side configuration](../concepts/side-configuration.md).

A face can also move things by itself: once a second, an **auto in**
face pulls a stack of Matter from what it touches, and an **auto out** face pushes a stack of results
into it. Chest of Matter on one side, chest for ores on the other, and it runs unattended.

## In a Quantum Simulator

Taken into a [Quantum Simulator](quantum-simulator.md), it
collapses a batch at once, like any machine: Matter from the grid, results and Trace to the grid's
outputs, each copy rolled from what the Simulator's chunk would give.

## Status

| Status | Meaning |
| --- | --- |
| Observing | Collapsing |
| Empty | No Matter |
| No power | Not enough FE for a collapse |
| Full | No room for results, or for Trace while it is kept |

## Recipe

The <ItemLink id="quantimium:observation_chamber" /> ringed in polished deepslate, with two <ItemLink id="quantimium:quantimium_trace" />, an
<ItemLink id="quantimium:anomaly_fragment" /> above and a block of redstone below.
