---
navigation:
  title: Reactor Materializer Port
  parent: blocks/index.md
  position: 68
  icon: quantimium:reactor_materialiser_port
---

# Reactor Materializer Port

<ItemImage id="quantimium:reactor_materialiser_port" scale="2" />

Part of the [Quantimium Reactor](../multiblocks/reactor.md).

The Reactor Materializer Port shows pipes and storage buses everything the [Reactor](../multiblocks/reactor.md) holds or could make, and makes what they take.

- A pipe or storage bus on it sees everything in the core's list, with its counted amount, as if it were all there.
- Taking something makes it exactly: the Reactor plans it, gives as many as can really be made and paid for, and uses nothing until the transfer goes through.
- Items can be put in through it too, unless a pack turns that off (`reactor.materialiserPortAcceptsItems`).
- On an AE2 network, one storage bus on it shows the whole list in the terminal as stock. Counts are estimates, so asking for 64 can bring fewer.
- It goes dark while an ME Superposition Port is linked on the same network.

## Recipes

<RecipesFor id="quantimium:reactor_materialiser_port" />
