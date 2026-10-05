# Changelog

## 0.4.0-alpha: the Quantimium Reactor

**The Quantimium Reactor**, a first version, and the machines of All the Mods 11. Same Minecraft
26.1.2 and NeoForge 26.1.2.109. Worlds from 0.3.x carry on.

### The Quantimium Reactor

- **A captive black hole.** Collapse an unbound Tesseract in an empty Fold Chamber (9 or more a side)
  into a **Singularity**, and seat it in a **Horizon Core** on an 11-wide disc of **Reactor Plinth**.
  **Ring Emitters** in facing pairs drive its rings: each ring quadruples what it holds, up to sixteen
  million items. It holds exactly what goes in, data and all, and nothing is ever at risk: a full
  horizon refuses more, an unpowered one waits. The Singularity *is* the horizon: take it out and
  everything goes with it.
- **One list of everything it holds or could make**, like an ME terminal: through up to eight
  **Catalyst Bays** (a machine, a crafting table or a Folded Tesseract each), counted off the server
  thread, sortable and searchable. Recipe loops are never counted back into themselves, and things made
  from the same source share it.
- **Take anything on the list** and it plans the whole tree through its catalysts, all or nothing:
  held items and leftovers first, the recipe that uses least, presses and other tools made once and
  kept, cutting knives worn rather than used up.
- **Ports** on the disc's rim: Input, Output, Energy, and the **Materialiser Port**, which offers the
  whole list to pipes and to an AE2 storage bus as if it were all there.
- **Unrealised Matter in the horizon** is observed into whatever it could become, with no catalyst.

### More machines

- **The machines of All the Mods 11 are catalysts** for the Quantum Crafter, the ME Superposition
  Crafter and the Reactor: **EnderIO** (Alloy Smelter, SAG Mill, Slice'n'Splice), **Energized Power**
  (Crusher, Pulverizer, Sawmill, Compressor, Alloy Furnace, Induction Smelter, Metal Press, Charger,
  Assembling Machine, Powered Furnace), **Powah** (Energizing Orb), **Productive Bees** (Centrifuges),
  **Mystical Agriculture** (Seed Reprocessor, Infusion and Awakening Altars) and **Iron Furnaces**.
  Only guaranteed outputs are made. The Crystal Growth Chamber is left out on purpose: with instant
  crafting it would be endless amethyst.
- **Productive Bees hives in the Simulator**, expansion boxes and all, with a Simulator Upgrade (or
  Productivity III or IV) in the hive so its bees stay home.
- **Recipe adapters** can read more: arrays and counted ingredients, several outputs with chances
  left out, a recipe's own energy, and the machines that run each recipe type.

### The mirror and Matter

- **Mirror mites leave Quantimium Traces**: a quarter of those killed by a player drop one, in the
  mirror, for whoever killed them.
- **Doors, trapdoors, fence gates, buttons and levers work from the mirror**, on the real world
  (`#quantimium:mirror_usable`).
- **Unrealised Matter** slowly morphs between a gem, lapis, coal and a raw ore nugget, and JEI and ME
  terminals now find it by name.
- Matter never becomes **Allthemodium, Vibranium or Unobtainium**: they're All the Mods' endgame
  (`#quantimium:unrealised/excluded`).
- **A Sophon** takes a Semi-Stable Tesseract, 4 Anomaly Fragments and a Rift Residue at the Unfolding
  Array, instead of a Totem of Undying and 16 Unrealised Matter.

### The guide and the wiki

- **Multiblocks in 3D** in the in-game guide: turn and zoom the Reactor, Foundry, Fold Chamber,
  Containment Hall, Superposition Pod, Unfolding Array and Harvester. The guide key over any part of a
  multiblock opens its page. The Superposition Pod moves to Multiblocks.
- **The wiki is public**, at <https://kadikular.github.io/quantimium/>.

### Shaders

- **Iris**: shader packs now draw the Reactor, Tesseracts, beams and glows, where they skipped them or
  drew them black. Support is partial: some fades show hard edges, rifts stay dark and the mirror loses
  its colours. `config/quantimium/iris.properties` can steer a pipeline for a pack that draws one badly.

### Fixes

- A world could hang on "Preparing for world creation" with **Potions Master** installed.
- Counts on the Reactor's list could promise more than could be made, and crafting cable anchors used
  up a cutting knife each time.
- The Decoherence Lance's beam starts at the lance, in first and third person.
- Tesseract **Stabiliser**, spelled like everything else.

### Art and license

- **All textures and sounds are Quantimium's own**, or vanilla's referenced rather than copied: new
  Anomalite crystals, Trace, mirror flora and Mirror Endermite, and vanilla's own sounds re-pitched.
- **Quantimium is licensed LGPL-3.0.**

## 0.3.1-alpha: the Fold Chamber

**The Fold Chamber**, a first version that folds our own Quantum Foundry. Worlds from 0.3.0 carry on.

- **Build a frame round the machine:** a Fold Pylon on each corner, Fold Rails along the edges, and the
  Fold Core in the middle of any bottom edge. Each side is 3 to 17 blocks; a Foundry takes 9 × 3 × 9.
- **Bind a Tesseract to the controller**, put it in the core and press **Fold**, for 2,000 FE a block.
  **Unfolding is free**, in any chamber with room for the machine. Contents never cross a fold.
- **The Folded Tesseract** is a catalyst in a Quantum Crafter or ME Superposition Crafter for the
  machine's recipes, at a Critical field.
- **`crafter.structurelessMultiblocks` is now off by default**: fold the built machine instead. The
  Casual preset turns it back on. An existing config keeps the value it was written with.

## 0.3.0-alpha: Minecraft 26.1

**Quantimium moves to Minecraft 26.1.2 and NeoForge 26.1.2.109**, the versions All the Mods 11 is built
on, and needs Java 25. There is no 26.1 build of 0.2.0 and no 1.21.1 build of this one: 1.21.1 ends at
0.2.0 plus the milestones below, on the `1.21.1` branch. **Alpha: start a new world.** 1.21.1 worlds
are not supported.

### Unrealised Matter becomes the pack's own ores (M4)

- **A collapse picks one of the pack's ores**, from every `#c:ores/*` tag, and gives what it drops
  mined with a plain pickaxe, so every mod's ores work with no per-mod code. Rarity comes from
  `#quantimium:unrealised/*` block tags, and the band where the Chamber stands decides the odds:
  commons only at Low, very rare from Critical. JEI shows each ore's chance in every band.
- **Process it unobserved.** Matter put through a catalyst in a Quantum Crafter stays Matter and
  remembers the step, up to five; each step keeps every form it could make (raw, dust, ingot, plate,
  wire...).
- **Observe it step by step.** A Chamber walks the steps that hold, each with a chance that rises with
  the band, then collapses into one of the forms reached. Above Low it takes flux for better odds.
- **The Materialiser** makes Matter into the ore and form you choose, capped by its band, for Trace,
  flux and FE. With no output chosen it **works on demand**: pipes and import buses see every form it
  could make and have it made as they pull. A bound Tesseract in its Matter or Trace slot reads a
  linked inventory.

### Anomalite, harvested (M5)

- **The Anomalite Cell** (Foundry, Medium) carries charge, and **the Emitter** (Foundry, Medium) is now
  the Lance's light.
- **The Decoherence Projector burns Anomalite Cells**: watching is free, beaming costs charge. It mounts
  on any face, and Anomalite no longer grows on it.
- **The Harvest Laser and Rift Lens** fire through a tear onto a crystal up to six blocks out, wait for
  it to grow full and shatter it whole for its shards. The tear leaks anomaly and, rarely, a mite.
  Wrenches turn both.
- **Veil Thread:** fired through a Rift Lens into a hall, the laser draws a thread from the Veiled it
  holds, every three minutes.

### Fixes from the move

- A Tesseract Stabilizer exporting from a linked Crafter crafts again; pipes and buses asking a Crafter
  or Materialiser what it offers no longer spend energy (or Matter read through a Tesseract).
- A breaching Tier 4 rift stops at four mites again, and closing a rift recalls its mites.
- The Quantum Crafter runs AE2's Charger and Inscriber recipes again, and JEI shows the Foundry's
  recipes on servers.
- Works with Jade, AE2 and JourneyMap as All the Mods 11 ships them.

### For pack makers

- A creative-only **Creative Energy Cell** powers machines without a partner mod's battery.
- Recipe adapters can read inputs by field (`"input_mode": "field"`), including optional ones, for
  mods whose recipes don't expose them to the recipe book.

## 0.2.0-alpha: the spine

Flux is now stable, visible and worth running hot. **Alpha: this version breaks 0.1 worlds.** Registry
ids were renamed and the field model was rewritten; start a new world.

### The field (Field Model 2.0)

- **A steady factory gives a steady reading.** Emission eases in through a pool, the field decays on a
  smooth curve and spreads to its neighbours, so it settles once, on an S-curve, and never flickers at a
  band edge or a chunk line.
- **A source makes a plateau**, a band for each tenfold of FE: 1,000 FE/t holds the 3×3 round it at
  Medium, 10,000 at High, 100,000 at Critical.
- **Containment is capacity.** A chunk under containment shows its load; flux past the capacity turns
  into anomaly in proportion, so a little over is a trickle and far over is a flood. Contained anomaly
  is atmosphere only. Containment fades by distance past its core, so the land round a hot base is
  dangerous.
- **One job per early field block:** the Quantum Exciter raises flux, the Flux Suppressor holds it
  under a ceiling, and the new **Basic Anomaly Siphon** (replacing Anomaly Containment) holds anomaly
  down and makes Anomaly Fragments. Siphons land on their mark and stay; a Clear setting holds at 0.
- **Where it's heading:** every reading says where the field is going, not just where it is:
  *Medium · heading for High*.
- A **Debug Field Emitter** (creative) for watching the field settle.

### Running hot pays

Machines do more in a hotter field, read where they stand:

- **Quantum Simulator:** largest batch 2× / 4× / 8× / 16× / 32× from Low to Singularity (up from 8×).
- **Quantum Crafter** and **ME Superposition Crafter:** each catalyst tier needs a band (electric
  machines Medium, heavy machines High, multiblock controllers Critical, the Fusion Reactor
  Singularity), and the instant-craft tax falls from 2.0× to 1.1× as the field heats up.
- **Zeno Field Controller:** fastest rate 2× to 16× by band, 25% cheaper at Singularity.
- Below a band, a machine says what it needs and where the field is heading.

### Tiers and recipes

- Each tier needs the material of the loop before it: Trace at Low, Anomaly Fragments and Anomalite at
  Mid, Rift Residue and Tesseracts at High. **High starts with your first rift.**
- The **Quantum Crafter**, **Quantum Simulator** and **Tesseract Stabilizer** have recipes.
- Recipes ask for common tags (`#c:ingots/iron` and so on), so any mod's materials work.

### Rifts and the Veiled

- The Unobserved is now called **the Veiled**.
- **Rift Seeds** are a Mid-tier craft. Planted in an uncontained chunk at Medium anomaly or more, a
  seed opens a wild rift; anywhere else it fizzles and is kept. On a Rift Anchor it opens a held rift
  anywhere. Only a Rift Seed lights the Stabilised Portal.
- A stage 1 rift pays no Residue: let it grow first (3 / 6 / 10 after).
- Rifts are limited by neighbourhood (at most 3 within 256 blocks, 64 apart), not by dimension.
- A held Veiled keeps the peace for 128 blocks. The Containment Hall's screen shows whether its cell
  can take a Veiled, and a Veiled that gets away tells you why the hall nearby couldn't hold it.
- Fixed: the Veiled's blink could land out of reach, or be kept out by a shut door.

### Seeing the field

- **Field Monitor:** a map of the 9×9 chunks round it, and an alarm (redstone, and a comparator
  counting chunks) for overloaded containment or uncontained anomaly.
- **Field map** on the Mirror Lens (N).
- **Jade:** flux, anomaly, where it's heading, load and what anomaly costs, for Quantimium blocks.
- **JourneyMap:** the field and rifts on your map, each with a toolbar button to show or hide them.
- **In-game guide:** the whole wiki, through GuideME. The Quantimium Field Guide is a Book and
  Unrealised Matter; hold G over an item for its page.
- **Advancements** on their own tab, laid out as the path through the mod.

### For pack makers

- A pack-maker page on the wiki: every config value, tag and datapack hook, with Kitchen sink,
  Casual, Expert and Server presets.
- New switches: structureless multiblock catalysts, Simulator nesting depth, every band reward, rift
  limits, map overlays.
- Registry ids renamed: `entangled_link` → `tesseract`, `flux_generator` → `quantum_exciter`,
  `unobserved` → `veiled`.

## 0.1.0-alpha

The first tagged alpha.
