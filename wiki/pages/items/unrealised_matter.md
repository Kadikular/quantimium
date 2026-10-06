Mined from [[block:unrealised_ore]], it becomes real in an
[[block:observation_chamber]] or a [[block:quantum_observation_chamber]].

[[mechanic:unrealised.silk_touch]] **Silk Touch keeps the ore block,** and every machine that takes
Matter takes the block as well: one block is one Matter with no history yet.

## What it collapses into

[[mechanic:unrealised.pack_ores]] **Any ore in the pack.** A collapse picks one of the pack's ores (every
`#c:ores/*` tag, so every mod's ores with no extra work) and gives what that ore drops mined with a
plain pickaxe: raw iron, four or five redstone, a diamond. One Matter is one ore block's worth.

Each ore has a **rarity**: common (coal, copper, iron, tin…), uncommon (gold, redstone, lapis,
quartz…), rare (diamond, emerald, platinum…) or very rare (netherite scrap, iridium…). An ore the
tiers don't name counts as **very rare**, so an unknown ore never comes out as often as coal.

[[mechanic:unrealised.excluded]] **Some ores it never becomes:** those in
`#quantimium:unrealised/excluded`. By default that's All the Mods' Allthemodium, Vibranium and
Unobtainium, the ores its endgame is built on.

[[mechanic:unrealised.band_bias]] **A hotter field turns up rarer ores.** The flux band where the
chamber stands sets each rarity's share of the rolls, split evenly among that rarity's ores:

| Band | Common | Uncommon | Rare | Very rare |
| --- | --- | --- | --- | --- |
| Low | 100% | | | |
| Medium | 70% | 30% | | |
| High | 55% | 30% | 15% | |
| Critical | 45% | 30% | 17% | 8% |
| Singularity | 35% | 30% | 20% | 15% |

JEI and EMI show each ore with its rarity and the lowest band it comes out in; hover for every band.
Pack makers set rarities with the `#quantimium:unrealised/*` block tags and the shares in the config
(see [For pack makers](../guides/pack-makers.md)).

## Processing it without looking

[[mechanic:unrealised.history]] Put Matter through a catalyst in a [[block:quantum_crafter]] and nothing
real comes out: it stays Matter, and remembers what was done to it. *Unobserved, but ground → smelted.*
Up to five steps, in order, each costing 2,000 FE (at the default tax). A step is the catalyst's own
kind of work (an MI macerator records grinding, a furnace smelting), and it's only offered if it gives
at least one of the pack's ores a form it didn't have. Matter only stacks with Matter of the same
history.

[[mechanic:unrealised.forms]] **Matter holds every form its processing could have made.** Each step
works on every form the Matter could already be, and keeps them all: raw iron, macerated, could be raw
iron or iron dust; smelted after that, raw iron, dust or ingots; put through a wiremill after a
hammer, plates or wire as well. So one stack of processed Matter can become whichever form you need
when you need it.

- **Order matters.** A step only reaches the forms made before it: wire-milling before hammering
  finds no plates.
- **The latest route counts.** Where a step makes an item the Matter could already become, its route
  replaces the old: macerated-then-smelted iron gives the ingots of its dust, one per dust.
- **Recipes are the pack's own.** Where one kind of work has several recipes for the same item, the
  least generous is used.

## Observing processed Matter

[[mechanic:unrealised.partial_collapse]] A Chamber walks processed Matter's history step by step, each
step holding only with a chance that rises with the flux band: half the time at Low, 65% at Medium,
80% at High, 90% at Critical and 95% at Singularity. Then it collapses into **one of the forms the
steps that held could make, at random**: the raw ore, the dust, the ingot. A step that doesn't hold
stops it there and leaves a [[item:quantimium_trace]].

## Choosing what it becomes

[[mechanic:unrealised.materialise]] The [[block:materialiser]] makes Matter into the ore and the form
you choose: every form of every ore the Matter could become is on its screen, with a search box.

- **The same count every time.** An ore's drops vary (copper ore drops two to five raw copper), so
  the Materialiser gives what one ore gives **on average, rounded down**, and that's the number on
  its screen: three raw copper a Matter, every time. A Chamber's roll is the real thing, more or
  less.

- **The band caps the choice.** Nothing at Low, commons at Medium, uncommons at High, rares from
  Critical. Very rare ores are never choosable: only a roll turns them up.
- **Choosing costs Trace**, 1.2 × the ore's value over the average value of a roll in that band
  (common 1, uncommon 3, rare 10), whatever the form: so choosing always costs a little more than
  rolling would on average, and a hotter field makes it cheaper. A common at Medium is 0.75 Trace, at
  Critical 0.22. Trace goes in whole; what a Matter doesn't use is kept as credit for the next.
- **And flux from its own chunk**, 25 × the ore's value (25 for a common, 250 for a rare), with
  4,000 FE for each Matter.

[[mechanic:unrealised.on_demand]] **On demand.** With no output chosen, a Materialiser holding Matter
offers every form it could make now (every one the band can choose) to automation: an item pipe, a
hopper, an AE2 import bus or storage bus pulling iron plates gets iron plates, made there and then and
paid for as usual. Each form shows as many as it could make now, as far as its Matter, Trace, FE and
the field go. A pull for less than one Matter makes (two raw copper, when a Matter makes three) gets
what it asked for, and the rest waits in the output slots. Choose an output and automation sees only
the output slots again, so a hopper under it collects rather than making things.

[[mechanic:unrealised.materialiser_tesseract]] **Through a Tesseract.** A bound [[item:tesseract]] in
the Materialiser's Matter slot reads the inventory it links to: the first Matter there, and every
Matter of the same history (hover the slot to see what it's reading). One in its Trace slot draws
Trace the same way. Feed it from a chest, a drawer or your storage network.

A Chamber's better odds are paid for too: above Low, each collapse takes 5 flux per band step from
its chunk (5 at Medium, 20 at Singularity), and with too little it rolls at Low's odds.
