---
navigation:
  title: Quantum Foundry
  parent: multiblocks/index.md
  position: 16
item_ids:
- quantimium:quantum_foundry_controller
- quantimium:quantum_foundry_plinth
- quantimium:quantum_foundry_pillar
- quantimium:quantum_foundry_conduit
- quantimium:quantum_foundry_attunement_tank
---

# Quantum Foundry

> **Stub**
>
> The structure and its recipes. Its field and status rules will be written up next.

The Foundry is Quantimium's first multiblock: a timed crafter that spends FE **and local flux**, and
grows things no table can make, the full Tesseract among them.

<GameScene zoom="2.9" interactive={true}>
  <ImportStructure src="../assets/structures/foundry.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Structure

A 7×7 cross, one layer high plus the tanks:

- a <ItemLink id="quantimium:quantum_foundry_controller" /> in the middle of a 3×3 ring of <ItemLink id="quantimium:quantum_foundry_plinth" />;
- one to four **attunement arms** out of the ring's sides, each a <ItemLink id="quantimium:quantum_foundry_conduit" />,
  then a <ItemLink id="quantimium:quantum_foundry_pillar" /> with a <ItemLink id="quantimium:quantum_foundry_attunement_tank" /> on top.

Each arm is one recipe socket, so a recipe with more ingredients needs more arms. Power goes in
through any plinth. The arms are shared with the
[Containment Hall](containment-hall.md).

![The Quantum Foundry's screen](../assets/gui/quantum_foundry.png)

## Recipes
