# Flux and anomaly

Every chunk carries two meters. **Flux** is how active it is; **anomaly** is how aggressive. Flux
unlocks recipes, and anomaly is what they cost you, unless you contain it.

## Bands

Both meters are read as bands, spaced tenfold apart. The bands are labels on a smooth value: nothing
jumps when a field crosses from one to the next.

| Band | From | Anomaly surcharge on crafts | Buffer leak |
| --- | --- | --- | --- |
| Low | 0 | none | none |
| Medium | 100 | +25% | 16 FE/t |
| High | 1,000 | +50% | 40 FE/t |
| Critical | 10,000 | +100% | 80 FE/t |
| Singularity | 100,000 | +200% | 160 FE/t |

[[mechanic:flux.bands]] Machines and meters read the field **where they stand**, blended between the
chunks round them, so it changes smoothly as you walk and never jumps at a chunk line. Anything that
switches on a band (a Foundry recipe, a [[block:flux_detector]]) switches into it at the band's start
but only drops out below 85% of it, so a field hovering at an edge switches it once. The
[[item:flux_meter]] shows the field where you stand on the action bar, and how full the containment
over it is.

## Running hot pays

Flux is capability. The hotter the field where a machine stands, the more it can do:

| Machine | Low | Medium | High | Critical | Singularity |
| --- | --- | --- | --- | --- | --- |
| [Quantum Simulator](../machines/quantum-simulator.md): largest batch | 2× | 4× | 8× | 16× | 32× |
| [Quantum Crafter](../machines/quantum-crafter.md): catalysts | workstations | + electric machines | + heavy machines | + multiblock controllers | + Fusion Reactor |
| Quantum Crafter: instant-craft tax | 2.0× | 2.0× | 1.6× | 1.3× | 1.1× |
| [Zeno Field Controller](../machines/zeno-field-controller.md): fastest rate | 2× | 4× | 8× | 16× | 16×, 25% cheaper |
| [Observation Chamber](../items/unrealised_matter.md): ores | common | + uncommon | + rare | + very rare | very rare more often |
| [Quantum Foundry](../multiblocks/quantum-foundry.md) | each recipe names its band | | | | |

A machine reads the band where it stands, as meters do, with the same hysteresis. Below a band, it
says what it needs and where the field is heading: *Needs High flux: here Medium · heading for
High*. Running hot costs nothing by itself: flux beyond the containment over it turns into anomaly,
and that's the gamble. Pack makers can flatten any row (see [For pack makers](../guides/pack-makers.md)).

## Where flux comes from

[[mechanic:flux.emit]] Quantimium work emits flux: {{c:FieldModel.EMISSION_GAIN}} flux per
{{c:QuantumFlux.FE_PER_FLUX}} FE spent.
It lands on the 3×3 of chunks round the machine, a fifth on the machine's own and a tenth on each
of the other eight. Non-electric crafts emit a small flat amount; idle machines and power arriving in
buffers emit nothing. Work adds no anomaly of its own: flux drives it. The [[block:quantum_exciter]]
raises flux on purpose.

- [[mechanic:field2.pool]] **Emission eases in.** It goes into a pool on each chunk first, and each
  pool lets about a tenth of what it holds into the field every second
  ({{c:FieldModel.RELEASE_SECONDS}} s time constant). A burst of crafting reads as a hump, not a
  spike.
- [[mechanic:field2.plateau]] **A source makes a plateau, a band for each tenfold.** The 3×3 round a
  source settles close to level, and each ×10 in FE lifts it a whole band: 1,000 FE/t of work holds
  it at Medium, 10,000 at High, 100,000 at Critical, and 1,000,000 puts the centre and sides at
  Singularity.

## How the field moves

The field updates once a second, for every chunk holding one.

- [[mechanic:flux.drain]] [[mechanic:field2.decay]] **It decays** on a smooth curve that rises with
  the value: 0.95% a second at 100, 2.1% at 1,000, 3.5% at 10,000, 5% at 100,000, never less than
  0.05. Every steady source has exactly one level to settle at.
- [[mechanic:field2.shape]] **It spreads.** Each second a chunk shares a fraction
  ({{c:FieldModel.DIFFUSION}}) of the difference with each loaded neighbour, so a plateau falls away
  at its edges: at 100,000 FE/t it's still High two chunks out and Medium at three and four. A chunk
  below {{c:FieldModel.SPREAD_FLOOR}} flux spreads no further, so a field ends a few chunks out.
  Nothing flows to or from an unloaded chunk: an unloaded chunk keeps its field, catching up only its
  decay when it loads, so the field doesn't depend on anyone's view distance.
- [[mechanic:field2.steady]] **A steady source settles without flicker.** It eases up to its level
  over a minute or two, on an S-curve, and never overshoots: it crosses a band once and stays.
- [[mechanic:field2.fall]] **It falls the same way.** When the source stops, the field only ever
  falls; it doesn't rebound.

## Anomaly

[[mechanic:anomaly.coupling]] Anomaly climbs towards a target each second, closing 4% of the gap. With
nothing containing it, the target is all the flux. Under containment, the target is the flux its
capacity can't hold, plus a small leak (3%) of what it does hold. Anomaly decays
on the same curve as flux, but separately, so it lingers after flux drops.

What anomaly does in a chunk that isn't contained, by band:

- [[mechanic:anomaly.surcharge]] **Surcharge.** Crafter and Simulator work costs extra FE (the table above).
- [[mechanic:anomaly.leak]] **Leak.** Quantimium machine buffers bleed a flat amount of FE every tick.
- **Crystals.** From High, Anomalite Crystals seed on powered machines and eat their buffers.
  [[mechanic:anomalite.host_removed]] Break or move the machine and its crystals go with it.
- **Tears and mites.** From Medium, the field tears into the [mirror](mirror-phase.md) on its own,
  and mirror mites hunt.
- **Rifts.** At High, a tear can fail to close and leave a [flux rift](../mechanics/flux-rifts.md).

## Containment

Containment doesn't lower the meters. It gives a chunk **capacity**: how much flux it can hold
without that flux turning into anomaly. The flux stays capability, so a contained base can run hot.

**Load** is how full that capacity is: the flux in the chunk over the capacity covering it. At 80% all
of it is held. Past 100%, the flux beyond capacity (the overflow) couples into anomaly in full, so a
little over is a trickle and far over is a flood. Nothing flips. The [[item:flux_meter]], the Mirror
Lens readout and the containment's own screen all show the load.

[[mechanic:containment.shield]] A chunk is **contained** when it's covered and its load is at most 100%.
Containment is the [Containment Hall](../multiblocks/containment-hall.md)'s: it holds all of its rated
band, High to Singularity, and leaks 3%. Capacity from several halls adds up, and a chunk under
several leaks their mix. Before a hall, you keep anomaly down by pulling it: a
[[block:basic_anomaly_siphon]] early, an [Anomaly Siphon](../machines/field-control.md) later.

[[mechanic:containment.edge]] **Containment has an edge.** Its strength goes by straight-line distance
from the machine: nearly full across its core (98% at the sides of a 3×3, 91% at the corners), half
two chunks out, and next to nothing a chunk further. The field doesn't stop there, so past the edge a
hot base's flux is uncontained, and the land round it turns dangerous, with the tears and mites that
come with it. Plan where the edge goes, or build wider.

[[mechanic:containment.leak]] **Contained anomaly is atmosphere only.** The leak keeps a contained hot
district alive, with plumes, flora and glimpses, but in a contained chunk anomaly costs nothing: no
surcharge, no buffer leak, no new crystals, no tears, no hunting mites, and rifts stop growing. Only
an overloaded chunk gets hurt.

| Tool | What it does |
| --- | --- |
| [[block:basic_anomaly_siphon]] | Holds anomaly at the top of Low across the 3×3 of a small workshop, and turns what it pulls into Anomaly Fragments. |
| [[block:flux_suppressor]] | Holds flux (only flux) under a ceiling across the 3×3: firebreaks, starving rifts, quiet zones. |
| [Containment Hall](../multiblocks/containment-hall.md) | Holds up to all of Singularity with enough arms, over the 3×3 or 5×5, and pulls anomaly (only anomaly) down. |
| [Flux Maintainer, Anomaly Siphon, Field Regulator](../machines/field-control.md) | Hold the field at a floor, or anomaly under a settable ceiling. |
| [[block:flux_detector]] | Turns a band into redstone, for automation. |
| [[block:field_monitor]] | Watches the 9×9 chunks round it: a map, and an alarm (redstone and comparator) for overloaded containment or uncontained anomaly. |

## Seeing the field

[[mechanic:field2.settling]] **Where it's heading.** A field takes a minute or two to settle, so every
reading says where it's going as well as where it is: *Medium · heading for High*, *High · steady*,
*Critical · falling to High*. Each chunk keeps track of what feeds it, and the band it gives is the
level where decay would take exactly that. Forty seconds into a climb it already names the band the
climb will end in.

The [[item:flux_meter]] and [Jade](partner-mods.md#jade) both show it. Wear a [[item:mirror_lens]]
(or be in the mirror) and the corner readout shows flux with its trend, anomaly and, under
containment, its load. Press **N** for the field map: the chunks round you, north up, flux in
azure with anomaly washed violet over it, your chunk outlined, and chunks under containment marked in
the corner, pale when held and amber when overloaded. With JourneyMap, the same
colours are [on your world map](partner-mods.md#world-maps). `/quantimium field clear` empties the
field within 8 chunks.

[[mechanic:field2.debug_emitter]] **A known source.** The **Debug Field Emitter** (creative tab, no
recipe) emits as if a machine on it were spending a fixed FE/t, with no power needed. Use steps it
up through 100, 1,000, 10,000, 100,000, 1,000,000 and 100,000,000 FE/t; sneak-use steps it down, and
round to off. Its light rises with its setting.
