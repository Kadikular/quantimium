# For pack makers

Everything a pack can change without touching code: one config file, a handful of tags, and the
datapack folders. Four presets at the end cover the usual kinds of pack.

## The config

Quantimium has one config, `config/quantimium-common.toml`. It's a common config, so it applies to
the whole instance, not to one world, and a server's copy governs play on it. Restart after editing
to be sure every value is picked up.

### `[anomaly]`: what anomaly costs

| Key | Default | What it does |
| --- | --- | --- |
| `craftSurcharge` | `true` | Crafter and Simulator work costs extra FE in uncontained anomaly above Low. |
| `surchargePercentMedium` / `High` / `Critical` / `Singularity` | 25 / 50 / 100 / 200 | The extra, in percent, by band. |
| `passiveDrain` | `true` | Quantimium machine buffers leak FE in uncontained anomaly above Low. |
| `passiveDrainFePerTickMedium` / `High` / `Critical` / `Singularity` | 16 / 40 / 80 / 160 | The leak, in FE/t per machine, by band. |

### `[anomalite]`: crystals

| Key | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Crystals seed on powered blocks in uncontained High+ anomaly. |
| `spawnIntervalSecondsHigh` | 120 | Mean seconds between spawn rolls per High chunk. |
| `criticalFrequencyMultiplier` / `singularityFrequencyMultiplier` | 2 / 5 | How much more often they roll above High. |
| `hostSamplesPerRoll` | 3 | Block entities tried per successful roll. |
| `growthChancePercentPerRandomTick` | 20 | Chance a feeding crystal grows a stage. |
| `decayChancePercentPerRandomTick` | 20 | Chance a crystal in a contained chunk shrinks a stage. |
| `drainPercentStage0`…`3` | 0.02 / 0.04 / 0.07 / 0.10 | Percent of the host's stored FE eaten a tick, by stage. |
| `drainCapStage0`…`3` | 40 / 70 / 110 / 160 | The most FE/t each stage eats at High. |
| `criticalDrainMultiplier` / `singularityDrainMultiplier` | 2 / 5 | That cap, raised above High. |

### `[crafter]` and `[basicCrafter]`

| Key | Default | What it does |
| --- | --- | --- |
| `structurelessMultiblocks` | `false` | A multiblock's controller alone is a catalyst, so the Crafter makes its recipes without the structure. Controllers are those in `#quantimium:multiblock_controllers`. Off by default since the Fold Chamber: a folded machine is the catalyst instead. |
| `taxPercentByBand` | 100, 100, 80, 65, 55 | The Crafter's tax in each band (Low to Singularity), in percent of `instantCraftMultiplier`. |
| `catalystBands` | low, medium, high, critical, singularity | The band each catalyst tier needs: basic, advanced, industrial, multiblock controllers, singularity. |
| `instantCraftMultiplier` | 2 | The tax on skipping a machine's wait, on burn time, MI EU and flat fees. |
| `coherenceCapacity` | 32 | Runs the Basic Quantum Crafter holds before it must refill. |
| `coherenceRefillTicks` | 100 | Ticks to refill it from empty. |

### `[simulator]`, `[zenoField]` and `[superposition]`: multipliers

| Key | Default | What it does |
| --- | --- | --- |
| `simulator.maxBatchByBand` | 2, 4, 8, 16, 32 | The largest batch that runs in each band. |
| `simulator.maxNestingDepth` | 2 | Most Simulators in a chain, counting the outer one. 1 allows no nesting. |
| `zenoField.maxExtraTicksPerTick` | 256 | Extra random ticks one Zeno Field Controller may add a tick, counting every simulated copy. 0 is unlimited. |
| `zenoField.maxFactorByBand` | 2, 4, 8, 16, 16 | The fastest a controller accelerates in each band. |
| `zenoField.singularityCostPercent` | 75 | What accelerating costs at Singularity, in percent. |
| `zenoField.slowedTickChance` | 0.125 | Chance an extra tick on a `#quantimium:zeno_slowed` block goes ahead. |
| `superposition.maxDoubles` | 4 | Sophons (body doubles) per player. |
| `superposition.chunkLoadPods` | `true` | Pods holding an online player's doubles keep their chunks loaded. |

### `[mirrorPhase]`: tears and the mirror

| Key | Default | What it does |
| --- | --- | --- |
| `timeoutSeconds` | 300 | Longest one trip into the mirror can last. |
| `riftMinDistance` / `riftMaxDistance` | 10 / 20 | How far from a phased player their way back opens. |
| `riftRelocateDistance` | 32 | Move the way back once they're this far from it. |
| `returnRiftLifetimeSeconds` | 30 | How long a way back stays for others to use. |
| `entryRifts` | `true` | Uncontained Medium+ anomaly tears into the mirror on its own. |
| `entryRiftLifetimeSeconds` | 120 | How long such a tear stays open. |
| `entryRiftIntervalSecondsMedium` | 450 | Mean seconds between tears at Medium, per dimension; each band above halves it. |
| `entryRiftMaxPerDimension` | 8 | Most tears open at once. |
| `entryRiftSpacing` | 32 | Fewest blocks between tears. |

### `[fluxRift]` and `[decoherenceLance]`

| Key | Default | What it does |
| --- | --- | --- |
| `fluxRift.enabled` | `true` | Tears in uncontained High+ anomaly can fail to close, leaving a flux rift. |
| `fluxRift.failedTearChance` | 0.25 | Chance they do. |
| `fluxRift.maxNearby` | 3 | Most rifts within `nearbyRadius` of each other. |
| `fluxRift.nearbyRadius` | 256 | How far `maxNearby` counts, in blocks. |
| `fluxRift.maxInDimension` | 64 | A safety net on the whole dimension. |
| `fluxRift.spacing` | 64 | Fewest blocks between rifts. |
| `fluxRift.stageSecondsHigh` | 600 | Seconds to grow a stage at High; Medium doubles it, Critical halves it. |
| `fluxRift.anomalyLeakPerStage` | 5 | Anomaly a rift leaks round it each second, per stage. |
| `fluxRift.coherencePerStage` | 400 | How much Lance work a stage takes to close. |
| `decoherenceLance.fePerTick` | 80 | What the Lance burns while firing. |
| `decoherenceLance.drainPerTick` | 5 | Coherence it takes from a rift each tick. |

### `[unrealised]`: what Matter becomes

| Key | Default | What it does |
| --- | --- | --- |
| `collapseSource` | `PACK_ORES` | `PACK_ORES`: every ore in the pack, by rarity. `LOOT_TABLE`: the hand-made loot table `quantimium:gameplay/unrealised_matter` instead. |
| `rarityShares{Low,Medium,High,Critical,Singularity}` | 100/0/0/0 … 35/30/20/15 | The shares of common, uncommon, rare and very rare ores a collapse lands on in each band. |
| `stepHoldPercentByBand` | 50, 65, 80, 90, 95 | How likely each step of processed Matter's history holds when a Chamber observes it, in each band. |
| `chamberFluxPerBand` | 5 | Flux a Chamber takes for each collapse above Low, per band step. |
| `materialiserPremiumPercent` | 120 | What choosing costs over rolling, in percent. |
| `materialiserFluxPerValue` | 25 | Flux the Materialiser takes per Matter, per point of the ore's value. |

### `[fieldVision]`: each player's own

These only matter on a player's own install: a server's values don't reach them.

| Key | Default | What it does |
| --- | --- | --- |
| `density` | 1 | How much of the field the Mirror Lens draws: 0 to 2. |
| `hud` | `true` | The corner readout while you can see the field. |
| `hudCorner` | `TOP_LEFT` | Which corner. |
| `mapOverlay` | `FIELD` | What JourneyMap draws: `FIELD`, `LOAD` or `OFF`. |
| `mapRifts` | `true` | Mark flux rifts on JourneyMap, with their stage. |

## Tags

| Tag | What it's for |
| --- | --- |
| `#quantimium:quantum_crafter_catalysts/basic`, `/advanced`, `/industrial`, `/singularity` | The Quantum Crafter's catalysts, by tier. `basic` is also all the Basic Crafter takes. See the [Quantum Crafter](../machines/quantum-crafter.md). |
| `#quantimium:quantum_crafter_whitelist` | What the Crafter accepts: the four tier tags. |
| `#quantimium:quantum_crafter_blacklist` | What it refuses, whatever else says. |
| `#quantimium:multiblock_controllers` | Multiblock controllers, refused as catalysts unless `structurelessMultiblocks` is on. |
| `#quantimium:unfoldable` | Blocks a Fold Chamber refuses to fold, on top of anything unbreakable. |
| `#quantimium:superposition_crafter_catalysts` | Machines the [ME Superposition Crafter](../machines/me-superposition-crafter.md) can drive. |
| `#quantimium:zeno_ignored` (block) | Blocks the [Zeno Field Controller](../machines/zeno-field-controller.md) never speeds up. Farmland by default. |
| `#quantimium:zeno_slowed` (block) | Blocks it speeds up only sometimes (`slowedTickChance`). Grass and mycelium, so they don't carpet a field. |
| `#quantimium:unrealised/common`, `/uncommon`, `/rare`, `/very_rare` (block) | The rarity of each of the pack's ores (`#c:ores/*`) for Unrealised Matter. An ore in none is very rare: tag your pack's ores here. |
| `#quantimium:unrealised/excluded` (block) | Ores Matter never becomes. All the Mods' Allthemodium, Vibranium and Unobtainium are in it by default: they're its endgame. |
| `#quantimium:unrealised_ore_facades/stone`, `/deepslate` (block) | The ores Unrealised Ore can look like. Add your pack's ores here. |
| `#quantimium:mirror_flora`, `#quantimium:anomalite` (block) | The mirror's plants and the crystal. Mostly for other mods to refer to. |

Add to a tag with `"replace": false` in your datapack's copy; replace it with `"replace": true`.

## Datapacks

- **Recipes.** Every recipe is in `data/quantimium/recipe/`, and a same-named file in your datapack
  replaces it. The Foundry's recipes are there too.
- **Recipe adapters.** `data/<namespace>/recipe_adapters/*.json` teach the Crafter another mod's
  recipe types and what they cost. The built-in ones cover the AE2 Charger and Inscriber and
  Mekanism's machines; see the [Quantum Crafter](../machines/quantum-crafter.md) for the format.
- **Unrealised Ore.** It generates through the biome modifier
  `data/quantimium/neoforge/biome_modifier/add_unrealised_ore.json`. Override it with an empty
  `neoforge:none` modifier to turn the ore off, or change its biomes; the vein itself is in
  `worldgen/configured_feature/unrealised_ore.json`.
- **Advancements** are in `data/quantimium/advancement/` and can be replaced the same way.

## Advancements

[[mechanic:advancements.path]] Quantimium's advancements have their own tab, laid out as the path
through the mod, so they work as a guide and as the skeleton of a quest book. Their ids are stable:
build quests on them.

```text
quantimium:root                              (granted on joining; opens the tab)
└ not_what_you_expected                      Unrealised Matter
  └ observed                                 a Quantimium Trace
    ├ reading_the_field                      a Flux Meter
    │ ├ seeing_things                        a Mirror Lens
    │ └ keeping_watch                        a Field Monitor
    ├ coherent                               a Basic Quantum Crafter
    │ ├ something_left_over                  an Anomaly Fragment
    │ │ └ held                               an Anomaly Containment Hall
    │ └ every_machine_at_once                a Quantum Crafter
    │   ├ it_thinks_its_running              a Quantum Simulator
    │   │ └ simulation_within_simulation     simulate an engaged Simulator
    │   ├ it_grows_on_you                    an Anomalite Shard
    │   ├ forged_in_the_field                a Quantum Foundry Controller
    │   └ a_watched_pot                      a Zeno Field Controller
    ├ what_is_this_place                     enter the mirror
    │ ├ these_locals_arent_friendly          take a hit from a Mirror Endermite
    │ ├ a_way_to_come_and_go                 light a Stabilised Portal
    │ └ stitching_the_sky                    a Decoherence Lance
    └ unfolded                               fold a Sophon
      ├ here_and_there                       swap into a double
      ├ out_of_body                          tether away
      ├ who_was_that                         die and wake in a double
      └ fourfold                             four doubles at once
```

## Presets

=== "Kitchen sink"

    The defaults. Everything's on, and the Crafter takes a multiblock's controller without its
    structure, since a kitchen-sink pack has plenty else to build.

=== "Casual"

    Keeps the field's danger visible but gentle: no crystals eating machines, no rifts left behind,
    the Crafter's bigger machines a band sooner, and multiblock controllers working without their
    structure.

    ```toml
    [anomaly]
    surchargePercentMedium = 10
    surchargePercentHigh = 25
    surchargePercentCritical = 50
    surchargePercentSingularity = 100
    passiveDrain = false

    [anomalite]
    enabled = false

    [crafter]
    catalystBands = ["low", "low", "medium", "high", "high"]
    structurelessMultiblocks = true

    [mirrorPhase]
    entryRiftIntervalSecondsMedium = 900

    [fluxRift]
    enabled = false
    ```

=== "Expert"

    Simulators don't nest, instant crafts cost more, and anomaly bites harder.

    ```toml
    [anomaly]
    surchargePercentMedium = 50
    surchargePercentHigh = 100
    surchargePercentCritical = 200
    surchargePercentSingularity = 400

    [crafter]
    instantCraftMultiplier = 3

    [simulator]
    maxNestingDepth = 1

    [fluxRift]
    failedTearChance = 0.4
    ```

=== "Server"

    Caps what one player can make the server do, and leaves chunk loading to your claims mod.

    ```toml
    [zenoField]
    maxExtraTicksPerTick = 128

    [simulator]
    maxNestingDepth = 1

    [superposition]
    maxDoubles = 2
    chunkLoadPods = false

    [mirrorPhase]
    entryRiftMaxPerDimension = 4

    [fluxRift]
    maxNearby = 2
    ```
