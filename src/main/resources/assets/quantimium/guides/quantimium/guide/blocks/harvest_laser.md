---
navigation:
  title: Harvest Laser
  parent: blocks/index.md
  position: 20
  icon: quantimium:harvest_laser
item_ids:
- quantimium:harvest_laser
---

# Harvest Laser

<ItemImage id="quantimium:harvest_laser" scale="2" />

Fires through a Rift Lens (anywhere between) at a full-grown Anomalite crystal up to 6 blocks out, head-on or side-on; faces the player when placed; mites capped at 4 within 32 blocks; through a Rift Lens and a hall's glass it draws Veil Thread from a held Veiled instead: 15 s at 60 FE/t, then it shatters for 3 shards and the host regrows it. The tear leaks 15 anomaly a crystal and sometimes a mite (none contained). Both sides see the azure beam into the tear; the mirror also sees it reach the crystal.

Harvests Anomalite crystals from the real world. It fires through a <ItemLink id="quantimium:rift_lens" />, and the
beam tears a small hole in the world there; through the hole, on the mirror's side, it reaches the
crystal behind the lens and takes it apart a stage at a time.

<GameScene zoom="4.0" interactive={true}>
  <ImportStructure src="../assets/structures/harvester.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

**The line.** An <ItemLink id="quantimium:anomalite_crystal" /> up to
6 blocks out, on whatever it grew on and whichever way it grew:
the beam reaches it side-on as well as head-on. That's usually a <ItemLink id="quantimium:budding_anomalite" />, but a
wild crystal on a machine will do. The lens goes anywhere between them, turned along the beam;
nothing else solid may be in the way. The laser faces you when you place it, so stand where the
crystal will be. A lens already on its line is turned to take the beam, and a lens placed on the
line of a laser turns itself. Use any wrench on the laser to turn it to the next of its six
directions. If the line is wrong, it doesn't fire, and the
<ItemLink id="quantimium:flux_meter" /> says why.

**The harvest.** It waits for the crystal to grow full, then fires for
300 ticks (fifteen seconds). The crystal shatters and is gone,
and the laser keeps its 3 <ItemLink id="quantimium:anomalite_shard" />s, as many as
breaking it gives. The host grows a new one: a Budding Anomalite with its other faces full takes
about twenty seconds, so one laser gets a crystal every thirty-five seconds or so. Losing power
pauses the work; losing the crystal or the line starts it over. Take shards out by pipe or hopper,
or sneak with an empty hand (thread too); it stops when there's no room for another crystal's worth.

## Veil Thread

Fired through a lens and one wall of an
[Anomaly Containment Hall](../multiblocks/containment-hall.md)'s glass, the beam reaches the Veiled the
hall holds instead of a crystal: the lens, then a pane of the hall's glass, then the cell behind it,
all within 6 blocks. It draws one <ItemLink id="quantimium:veil_thread" /> out of it every
3,600 ticks (three minutes) at
120 FE/t, kept in its own slot beside the shards. Only one
laser draws from a hall at a time; a second waits its turn, and the Flux Meter says so. An empty hall's
glass is just a wall. From the real world the wisp in the cell stirs; from the mirror the violet light
falls on the Veiled and a pale thread is drawn back along it into the tear.

| | |
| --- | --- |
| Firing | 60 FE/t on a crystal, 120 FE/t drawing thread |
| Storage | 50,000 FE, up to 1,000 FE/t in |
| Anomaly | 15 into the chunk per crystal |

Like any Quantimium machine it emits **1 flux per 1,000 FE** it spends, a quarter of that as anomaly,
once a second.

**The tear leaks.** Each crystal lets some anomaly through, and now and then a mirror mite comes
through with it, into the real world: never at Low anomaly, 3 crystals in 100 at Medium, about 1 in
9 at High, 1 in 4 at Critical and 1 in 2 at Singularity. The tear frays as the crystal comes apart
and thrashes when a mite gets out. Inside a [contained](../concepts/flux-and-anomaly.md) chunk nothing
gets through at all, and no more get out while 4 or more mites
are already within 32 blocks of the lens, so an unwatched farm can't fill up with them.

**What you see.** From either side: an azure beam from the laser's core into a tear in the lens.
From the real world, a shimmer where the crystal you can't see is, growing as the work goes on.
From the mirror, the same light coming out of the tear, violet now, onto the crystal, and the
crystal drifting back into the tear faster and faster until it shatters.

## Recipes

<RecipesFor id="quantimium:harvest_laser" />
