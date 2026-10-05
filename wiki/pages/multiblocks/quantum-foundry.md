# Quantum Foundry

The Foundry is Quantimium's first multiblock: a timed crafter that spends FE **and local flux**, and
grows things no table can make, the full Tesseract among them.

[[render:foundry]]

[[structure:foundry]]

## Structure

A 7×7 cross, one layer high plus the tanks:

- a [[block:quantum_foundry_controller]] in the middle of a 3×3 ring of [[block:quantum_foundry_plinth]];
- one to four **attunement arms** out of the ring's sides, each a [[block:quantum_foundry_conduit]],
  then a [[block:quantum_foundry_pillar]] with a [[block:quantum_foundry_attunement_tank]] on top.

Each arm is one recipe socket, so a recipe with more ingredients needs more arms. Power goes in
through any plinth. The arms are shared with the
[Containment Hall](containment-hall.md).

![The Quantum Foundry's screen](../assets/gui/quantum_foundry.png){ .qgui }

## The field it draws on

Every Foundry recipe needs the field as well as its ingredients:

- **A flux band.** Each recipe names the lowest band it works in, read where the Foundry stands. A
  field hovering at a band's edge doesn't switch it on and off: once working, it holds until the field
  has clearly dropped.
- **Flux to spend.** When a recipe completes, the Foundry takes its flux cost straight out of its own
  chunk, and leaves some anomaly behind there. If the chunk doesn't hold enough flux, it waits.
- **Power,** drawn every tick the recipe runs, through any block of the plinth. It stores
  {{c:QuantumFoundryBlockEntity.ENERGY_CAPACITY}} FE and takes up to
  {{c:QuantumFoundryBlockEntity.MAX_RECEIVE}} FE/t.
- **Enough arms.** Each ingredient sits on its own pillar, and some recipes need a minimum number of
  arms: the full Tesseract needs all four.

Its screen shows the field where it stands, the band the recipe needs and the flux it will take, and
says what it's waiting for:

| Status | Meaning |
| --- | --- |
| Unformed | The plinth or every arm is incomplete. |
| Attuned | Formed, waiting for ingredients on the pillars. |
| No recipe | What's on the pillars isn't a Foundry recipe. |
| No power | It needs FE through the plinth. |
| Low flux | The field is below the recipe's band, or the chunk can't pay its flux cost. |
| Output full | Take out what it made. |
| Pinning | Working: the pillars are drawing the result out of the field. |

## Recipes

[[recipes:quantimium:quantum_foundry]]
