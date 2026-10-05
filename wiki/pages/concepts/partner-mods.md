# Partner mods

Quantimium has no hard dependencies, but its crafting machines are built to work with the big tech
mods. The Quantum Crafter reads their recipes, and the Quantum Simulator runs their machines. This page
lists what is supported and what is known not to work yet.

Every rule here is checked by an in-game test that runs only when the partner mod is installed, so
updating a partner mod and rerunning the tests shows at once whether anything broke. See
[Running the tests](../guides/writing-the-wiki.md#running-the-tests).

## Modern Industrialization

### In the Quantum Crafter

- [[mechanic:compat.mi.crafter_recipes]] **MI machines are catalysts.** An electric macerator makes
  macerator recipes, a chemical reactor chemical reactor recipes, and so on. Each craft costs the
  recipe's total EU, converted at MI's own `forgeEnergyPerEu` (10 by default), times the instant-craft
  multiplier. Grinding raw iron (2 EU/t for 100 ticks) costs 200 EU × 10 × 2 = **4,000 FE**.
- **Chance outputs are left out.** Only the guaranteed output is made: raw iron gives one iron dust,
  never the 50% second one.
- [[mechanic:compat.mi.crafter_fluids]] **Recipes with fluid inputs are not offered.** The Crafter
  has no tanks. A chemical reactor catalyst waxes copper (items only) but won't oxidise it (needs
  oxygen).
- [[mechanic:compat.mi.crafter_structureless_switch]] **Multiblock controllers need their
  structure.** A controller in `#quantimium:multiblock_controllers` (MI's multiblocks by default) is
  refused as a catalyst: build the machine and fold it in a [Fold Chamber](../multiblocks/fold-chamber.md)
  once MI has a fold adapter.
  [[mechanic:compat.mi.crafter_multiblock]] Packs that would rather skip the structure can turn
  `structurelessMultiblocks` on in the `crafter` section of the config: an Electric Blast Furnace
  controller alone then makes blast furnace recipes as if the furnace were built, steel from
  uncooked steel dust (2 EU/t for 600 ticks) for **24,000 FE**.
- [[mechanic:compat.mi.crafter_gate]] The **Forge Hammer** is blacklisted. **Boilers** are
  allowed but make nothing: they have no recipes to make instantly.

### In the Quantum Simulator

- [[mechanic:compat.mi.simulator_power]] **Electric machines are powered by the Simulator.** Its FE
  reaches an MI machine as EU through MI's own energy port, and is billed as usual.
- [[mechanic:compat.mi.simulator_steam]] **Boilers boil.** Water in an input tank and fuel in the
  grid make steam in an output tank, once the boiler has heated up.
- [[mechanic:compat.mi.simulator_fluid_in]] **Fluid inputs are drawn from the input tanks:** a
  chemical reactor oxidises copper with oxygen from them.
- [[mechanic:compat.mi.simulator_fluid_out]] **Fluid outputs fill the output tanks:** a mixer turns
  water and sugar into sugar solution, and an electrolyzer splits water into hydrogen and oxygen.
- [[mechanic:compat.mi.simulator_multiblock]] **Multiblocks can't be simulated.** A controller taken
  into the field has no structure around it, so it never forms and makes nothing. A larger
  Simulator that surrounds a whole multiblock is an idea under consideration.

## Applied Energistics 2

### In the Quantum Crafter

AE2's machines are supported through the recipe adapters in `data/quantimium/recipe_adapters`.

- [[mechanic:compat.ae2.inscriber]] **Inscriber.** Presses are tools, not ingredients: they stay in
  the grid. Printing silicon costs 1,600 × 2 = **3,200 FE**.
- [[mechanic:compat.ae2.charger]] **Charger.** Charging a certus quartz crystal costs **3,200 FE**.

### ME Superposition Crafter

With AE2 installed there is one more machine: the [ME Superposition Crafter](../machines/me-superposition-crafter.md),
which offers every recipe of its catalyst to an ME network as patterns nobody had to encode.

### In the Quantum Simulator

- [[mechanic:compat.ae2.simulator]] **AE2 machines can't be simulated.** AE2 runs its machines from
  its own network ticker rather than from block ticks, so there is nothing for the Simulator to
  drive. It shows **No tick**.

### Cables and crystals

- [[mechanic:compat.ae2.cables_through_crystals]] **Cables go through crystals.** Anomalite crystals
  that have grown on a machine don't stop you placing an ME cable or part there: like any block you
  place from the backing world, the cable replaces the crystal.

## Hostile Neural Networks

HNN has no recipes (its data models are a registry of their own), so the Crafter reads its Loot
Fabricator directly and everything else goes through the Simulator.

### In the Quantum Crafter

- [[mechanic:compat.hnn.crafter_fabricator]] **Loot Fabricator.** With a Loot Fabricator as the
  catalyst, every drop of every mob you have predictions for shows in the preview as its own ghost:
  chicken predictions offer both raw chicken and feathers. Take the one you want; there is nothing to
  select first. Each costs what the Fabricator would draw, 256 FE/t (HNN's `fabPowerCost`) for its
  60-tick run, times 2: **30,720 FE**.
- The **Simulation Chamber** is not a catalyst: whether a simulation succeeds depends on the model's
  accuracy, a roll the Crafter doesn't make. Simulate it instead.

### In the Quantum Simulator

- [[mechanic:compat.hnn.sim_chamber]] **Simulation Chamber.** Put the data model in the grid, and
  prediction matrices in another grid slot. The model goes into the chamber and stays there, and
  every cycle turns matrices into the base drop and mob predictions, times the batch.
- [[mechanic:compat.hnn.loot_fabricator]] **Loot Fabricator.** Choose its drop once, in the
  Fabricator's own screen, before you engage the field; the choice is kept. Predictions in the grid
  then come out as that drop.

## Mekanism

Mekanism's enriching, crushing, combining and smelting recipes have adapters, and the Simulator can
charge a Mekanism machine in Joules. Neither is tested yet: Mekanism isn't installed in the
development setup.

## More machines

[[mechanic:compat.partner_machines]] The machines of the mods All the Mods 11 plays with are
catalysts for the Quantum Crafter, the ME Superposition Crafter and the Reactor, read through recipe
adapters. Only what a recipe always gives is made; chances are left out, and a recipe that only might
give anything isn't offered.

| Mod | Machines | Tier |
|---|---|---|
| EnderIO | Alloy Smelter (alloys and vanilla smelting), SAG Mill | advanced |
| EnderIO | Slice'n'Splice | industrial |
| Energized Power | Crusher, Pulverizer, Sawmill (sawdust too), Compressor, Alloy Furnace, Induction Smelter, Metal Press (the mold stays), Charger, Powered Furnace | advanced |
| Energized Power | Assembling Machine, Elite Charger, Elite Powered Furnace | industrial |
| Powah | Energizing Orb | advanced |
| Productive Bees | Centrifuge, Powered and Heated Centrifuge (honey is left out) | advanced |
| Mystical Agriculture | Seed Reprocessor | advanced |
| Mystical Agriculture | Infusion Altar, Awakening Altar | industrial |
| Iron Furnaces | the furnaces, copper to netherite | basic |
| Iron Furnaces | Allthemodium, Vibranium and Unobtainium furnaces | advanced |

[[mechanic:compat.bees_simulator]] **Productive Bees hives in the Simulator.** An advanced beehive,
expansion box and all, can be simulated, but only with a **Simulator Upgrade** (or Productivity III
or IV) in it: without one its bees fly out of a hive that's no longer there and get lost, so the
Simulator refuses it and says why. Put the feeding slab or flowers in front of the hive as usual.

Left out on purpose:

- the **Crystal Growth Chamber**, which grows one amethyst shard into two: with instant crafting it
  would be an endless amethyst;
- the **Soul Binder** and **Soul Extractor**, which work with mobs' souls rather than items;
- the **Painting Machine**, the **Plant Growth Chamber** and the **Bottler**, which need a paint
  source, soil and water, or fluids;
- the **Energizer**, until its recipes are checked.

## Jade

Look at any Quantimium block with [Jade](https://modrinth.com/mod/jade) installed and its tooltip shows
the field where it stands:

- **Flux**, with where it's heading: *Flux 2,870 · High · steady*;
- **anomaly**, and the load of any containment over the chunk;
- what anomaly is costing machines there, if anything: the surcharge on crafts and the FE/t leak;
- the block's own readout, the same line the [[item:flux_meter]] gives: a crafter's draw, a
  Siphon's setpoints, a pod's owner and the double it holds.

Other mods' blocks are left alone. It's a Jade plugin that Jade finds for itself; without Jade the mod
runs as before. The **Quantimium field** entry in Jade's plugin settings turns it off.

## GuideME

With [GuideME](https://modrinth.com/mod/guideme) installed, this wiki is also in the game, as the
**Quantimium Field Guide**: a book crafted from a Book and Unrealised Matter. Hold the guide key
(**G**) over any Quantimium item to open its page, with its recipes drawn by the game itself. Without
GuideME, the recipe isn't there and nothing else changes.

## Shaders (Iris)

With [Iris](https://modrinth.com/mod/iris) and a shader pack, Quantimium tells Iris how to draw its own
effects, so the Reactor's horizon, Tesseract shells, beams and glows all show. Support is partial for
now:

- glows and fades can show hard, triangular edges instead of fading out smoothly;
- rifts look dark, without their violet fade;
- the mirror world's colours and fog aren't kept: shader packs replace them.

For the best look, turn shaders off near rifts and in the mirror. It's tested with Complementary
Reimagined; a pack that draws something badly can be steered with `config/quantimium/iris.properties`,
one line per render pipeline naming an Iris program, such as `additive=BEACON_BEAM`. The pipelines
are `flat_depth`, `flat_no_depth`, `additive`, `lines_depth`, `lines_no_depth`, `additive_entity` and
`soft_particle`.

## World maps

[[mechanic:field.map_overlay]] With [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap),
the field is on your map: every chunk holding one within 64 chunks of you, tinted as the
[[item:mirror_lens]]'s map draws it, with flux in azure and anomaly washed violet over it. Hover over
a chunk for its flux, anomaly and load. It's how you watch a big base's field, and see where its heat
spills past the containment. The map is refreshed every 5 seconds; no lens is needed. Chunks
that unload keep the field they last showed, as the map keeps the terrain you've explored: an
unloaded chunk's field is frozen until it loads again, so it's still there.

[[mechanic:map.rifts]] **Rifts are marked** on the map too, with their stage: *Rift · stage 2*, or
*Held rift* for one on an anchor. The **Quantimium rifts** button on the fullscreen toolbar shows or
hides them (`fieldVision.mapRifts`).

To change it, click **Quantimium field** on JourneyMap's fullscreen toolbar, or bind **World map:
field, load or off** in the controls (Quantimium section): each press goes from the field to
**load**, which shows only containment (pale where it's held, amber where it's overloaded), then to
off. Your choice is kept in `fieldVision.mapOverlay` in the config.
