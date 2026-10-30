---
navigation:
  title: Progression
  parent: guides/index.md
  position: 1
---

# Progression

*Every step is paid for with something the step before taught you to make.*

Quantimium climbs in four steps. Each needs a material that only the step before it can give, so if you're stuck, the
answer is almost always the material at the end of your current step. The [machine tiers](../concepts/tiers.md) page
lists every machine by step.

| Step | What you do there | What gets you to the next step | Where it comes from |
| --- | --- | --- | --- |
| **1. First steps** | Observe Matter, run a Basic Crafter, raise the field | <ItemLink id="quantimium:anomaly_fragment" />s and <ItemLink id="quantimium:anomalite_shard" />s | A siphon pulling anomaly; crystals that grow on your machines |
| **2. The mirror** | Mid-tier machines, lasers, enter the mirror | <ItemLink id="quantimium:rift_residue" /> | Closing a grown [flux rift](../mirror/flux-rifts.md) with a Lance |
| **3. Rifts and the Foundry** | Foundry, Tesseracts, Containment Hall, a held rift | <ItemLink id="quantimium:veil_thread" /> | A [Veiled](../mirror/veiled.md) held in a Containment Hall |
| **4. Endgame** | Top lasers, the Reactor's rings, the Pod | | |

## 1. First steps

1. Mine <ItemLink id="quantimium:unrealised_ore" /> for <ItemLink id="quantimium:unrealised_matter" />.
2. Build an <ItemLink id="quantimium:observation_chamber" /> and pulse it with redstone. Now and then it leaves a <ItemLink id="quantimium:quantimium_trace" />.
3. Make a <ItemLink id="quantimium:flux_meter" />, then a <ItemLink id="quantimium:basic_decoherence_matrix" /> and the <ItemLink id="quantimium:basic_quantum_crafter" />.
4. Run machines. Every craft adds flux to the chunks around it, and anomaly follows. A <ItemLink id="quantimium:quantum_exciter" /> raises
   flux faster.
5. Place a <ItemLink id="quantimium:basic_anomaly_siphon" />. It pulls anomaly down and turns it into <ItemLink id="quantimium:anomaly_fragment" />s.

**Start lasers early.** A <ItemLink id="quantimium:flash_emitter" /> burns coal into a laser, and a <ItemLink id="quantimium:laser_focus" /> is copper and glass.
<ItemLink id="quantimium:laser_port" />s, <ItemLink id="quantimium:laser_tank" />s and the <ItemLink id="quantimium:logistics_controller" /> need nothing from later steps. See
[Lasers](../lasers/overview.md).

## 2. The mirror

**You need:** a chunk at **Medium anomaly** that isn't contained, with powered machines in it.

- **Anomalite.** <ItemLink id="quantimium:anomalite_crystal" />s start growing on your powered machines. You can only see and break them
  from the mirror, and they grow fully only at **High** anomaly. A grown crystal gives <ItemLink id="quantimium:anomalite_shard" />s.
- **Getting into the mirror.** At Medium anomaly, tears open on their own nearby: walk into one. Later, build a
  <ItemLink id="quantimium:stabilised_portal_frame" /> portal and light it with a <ItemLink id="quantimium:rift_seed" />.
- **What shards build:** the <ItemLink id="quantimium:quantum_crafter" />, <ItemLink id="quantimium:quantum_simulator" />, <ItemLink id="quantimium:quantum_attuned_glass" />,
  the <ItemLink id="quantimium:mirror_lens" />, the <ItemLink id="quantimium:laser_emitter_1" /> and lenses, and the <ItemLink id="quantimium:decoherence_lance" />.

**To move on:** you need <ItemLink id="quantimium:rift_residue" />, from closing a flux rift.

1. Find a rift, or plant one: a <ItemLink id="quantimium:rift_seed" /> opens one on uncontained ground at Medium anomaly or higher.
2. Let it grow. A stage 1 rift gives nothing when it's closed; stage 2 or more gives residue, more the bigger it is.
3. From the mirror, hold a Decoherence Lance on its center until it collapses.

## 3. Rifts and the Foundry

Rift Residue opens the High tier:

- The <ItemLink id="quantimium:quantum_foundry_controller" /> grows what no table can, the <ItemLink id="quantimium:tesseract" /> among it.
- The <ItemLink id="quantimium:anomaly_containment_hall" /> lets a base run hot without the anomaly.
- A <ItemLink id="quantimium:rift_anchor" /> and <ItemLink id="quantimium:rift_stabiliser" />s hold a rift open for a steady supply of residue. See the
  [stabilized rift](../mirror/stabilised-rift.md).
- The <ItemLink id="quantimium:laser_emitter_2" />, the [Fold Chamber](../multiblocks/fold-chamber.md) and the
  [Superposition Pod](../multiblocks/superposition-pod.md).

**To move on:** you need <ItemLink id="quantimium:veil_thread" />, drawn from a [Veiled](../mirror/veiled.md).

1. Let a wild rift grow to **stage 4** in a chunk at **Critical flux**, uncontained. It can release a Veiled.
2. Drive it off with the Lance, wear it down, and hold it in a powered Containment Hall stocked with residue.
3. A laser with a Pickup Lens through the hall's glass draws Veil Thread from it.

## 4. Endgame

Veil Thread builds the <ItemLink id="quantimium:laser_emitter_3" /> and the <ItemLink id="quantimium:ring_emitter" />s that grow a
[Quantimium Reactor](../multiblocks/reactor.md)'s horizon. The Reactor itself needs a Singularity, folded from a
Tesseract in an empty Fold Chamber, and works with no rings at all.

## Where am I stuck?

| What you see | Why | What to do |
| --- | --- | --- |
| The Observation Chamber makes no Traces | It works on a redstone pulse, and leaves a Trace only some of the time | Give it a clock, and keep it fed with Matter |
| No Anomaly Fragments | A siphon only gets what it pulls: no anomaly, no fragments | Run machines or a <ItemLink id="quantimium:quantum_exciter" /> to heat the field |
| No crystals on my machines | They need Medium anomaly, an uncontained chunk, and a powered machine | Check the field with a <ItemLink id="quantimium:flux_meter" />; turn off containment nearby |
| I can't see any crystals | They're only visible from the mirror | Walk into a tear, or look through a <ItemLink id="quantimium:mirror_lens" /> for their ink |
| Crystals stay small | They grow fully only at High anomaly | Heat the field further |
| No tears to get into the mirror | Tears open only at Medium anomaly, uncontained | Heat the field, or build a portal |
| My Rift Seed fizzles | It needs Medium anomaly or more where it lands, uncontained | Use it somewhere hotter |
| Closing a rift gave nothing | A stage 1 rift gives no residue | Let it grow to stage 2 first |
| A Crafter ignores its catalyst | Some catalysts need a hotter band | Raise the field; the Crafter's screen says which band it needs |
| The Foundry is waiting | It needs a flux band, flux in its chunk, power and enough arms | Read its status line |
| I've never seen the Veiled | Only a stage 4 wild rift at Critical flux releases one | Let a rift grow uncontained in a Critical field |
