---
navigation:
  title: Anomalite
  parent: mirror/index.md
  position: 37
---

# Anomalite

*Parasites with excellent taste in machines.*

Anomalite is the crystal that grows on powered machines in a field with too much anomaly. You can see and mine it only from the [mirror](mirror-phase.md). It breaks into Anomalite Shards, which most Mid-tier machines are built from.

| Item | How you get it | Used for |
| --- | --- | --- |
| <ItemLink id="quantimium:anomalite_crystal" /> | Grows on powered machines | Source of everything below |
| <ItemLink id="quantimium:anomalite_shard" /> | A grown crystal breaks into 3. Also from dust or a lattice. | Lattice, cells, emitters, recipes |
| <ItemLink id="quantimium:anomalite_dust" /> | A crystal broken before it is grown | Refined into shards in the Foundry |
| <ItemLink id="quantimium:anomalite_lattice" /> | 4 shards in a 2x2 grid | Emitters, Budding Anomalite. Crafts back into 4 shards |
| <ItemLink id="quantimium:anomalite_cell" /> | Quantum Foundry: 2 shards, iron, glass | Fuel for the <ItemLink id="quantimium:decoherence_projector" /> |
| <ItemLink id="quantimium:anomalite_emitter" /> | Crafting, or the Foundry | Decoherence Lance, lasers, Reactor parts |
| <ItemLink id="quantimium:budding_anomalite" /> | Crafting | Grows crystals on demand |

## Where crystals grow

A crystal seeds on a powered block entity (a machine) in a chunk that is **not contained**, from Medium anomaly up.

- **Rolls.** A High chunk rolls about once a minute on average (`spawnIntervalSecondsHigh`, 60). Medium rolls a quarter as often, Critical twice as often and Singularity five times (all configurable).
- **Cap.** A chunk grows at most 2 crystals at Medium, 8 at High, 16 at Critical and any number at Singularity. Crystals grown by a Budding Anomalite don't count.
- **Host.** It grows on a face of the machine. If you break or move the machine, its crystals go with it.
- **Visible?** Only from the mirror. Through a <ItemLink id="quantimium:mirror_lens" /> in the real world you see only the ink a crystal broods in, not the crystal. Outside the mirror a crystal is not solid to a player.

## Stages and drain

A crystal has four stages. While it feeds, a random tick has a 20% chance to advance it a stage, but only while the chunk's anomaly is **High or more**. At Medium a crystal seeds but stays a bud.

- It eats the host's stored energy every tick: a small percentage of what is stored, up to a cap per stage.
- A machine that holds a charge but gives none up (many mods' are fill-only) feeds a crystal without losing any.
- The drain cap rises with anomaly: x2 at Critical, x5 at Singularity.

| Stage | Share of stored FE per tick | Cap at High |
| --- | --- | --- |
| 1 | 0.02% | 40 FE/t |
| 2 | 0.04% | 70 FE/t |
| 3 | 0.07% | 110 FE/t |
| 4 | 0.10% | 160 FE/t |

**Containment** doesn't remove crystals at once. In a contained chunk, a random tick has a 20% chance to shrink a crystal a stage. A bud that shrinks vanishes.

## Harvesting

Three ways to take a crystal apart.

### From the mirror, by hand

Enter the [mirror](mirror-phase.md) and break the crystal with any item in your hand. What drops depends on its stage:

| Stage | Drops |
| --- | --- |
| 4 (grown) | 3 Anomalite Shards. Fortune adds more. Silk Touch drops the crystal itself. |
| 1 to 3 | 1 Anomalite Dust |

Breaking a crystal in the real world, with a fake player (an annihilation plane, say) or by water or a piston drops nothing.

### From the real world, with a laser

A <ItemLink id="quantimium:harvest_lens" /> laser sent through a <ItemLink id="quantimium:rift_lens" /> reaches into the mirror and takes a **fully grown** crystal apart. See [Networks and rules](../lasers/networks.md).

- It takes 15 s per crystal at 60 FE/t, for 3 shards.
- Without <ItemLink id="quantimium:pickup_lens" /> the shards fall on the mirror's side, where only a phased player can take them. Mirrored Pickup carries them off to a <ItemLink id="quantimium:laser_port" />.
- **The tear leaks.** Each crystal lets 15 anomaly into the chunk. It may also let a mirror mite through:

| Anomaly band | Chance per crystal |
| --- | --- |
| Low | 0% |
| Medium | 3% |
| High | 11.5% |
| Critical | 27% |
| Singularity | 49% |

  No more mites get out while 4 are already within 32 blocks, so an unwatched farm can't fill up.

### From a Budding Anomalite

A <ItemLink id="quantimium:budding_anomalite" /> grows crystals in the mirror while it is powered. It ignores flux, anomaly and containment, so you can farm in a quiet chunk. The Budding Anomalite itself pays a flat FE/t, and its crystals never drain another machine or shrink. Craft it from gold, a lattice, shards and a block of amethyst.

## Refining

- **Dust to shards.** The <ItemLink id="quantimium:quantum_foundry_controller" /> refines 1 dust into 1 shard (100 ticks at 60 FE/t, at Medium flux or more, with at least one pillar).
- **Shards to lattice and back.** 4 shards craft 1 lattice. A lattice crafts back into 4 shards.
- **Cell and emitter.** 2 shards, iron and glass make a cell in the Foundry. An emitter is crafted from shards, a lattice, Traces and copper; the Foundry makes 2 at a time.

## Uses

- <ItemLink id="quantimium:anomalite_cell" />: fuel for the <ItemLink id="quantimium:decoherence_projector" />.
- <ItemLink id="quantimium:anomalite_emitter" />: the <ItemLink id="quantimium:decoherence_lance" />, the <ItemLink id="quantimium:ring_emitter" />, and lasers.
- Shards and lattices: Mid-tier machine recipes.
