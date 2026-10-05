# Quantimium content catalog

Living inventory of **blocks** and **items**: display name, short role, implementation status.

Status key:


| Tag          | Meaning                                                                        |
| ------------ | ------------------------------------------------------------------------------ |
| **Done**     | Shipping / playable                                                            |
| **Partial**  | Exists but missing planned features                                            |
| **Planned**  | Design only (`CONTAINMENT.md`, `QUANTUM_CORE.md`, `UNREALISED.md`, `IDEAS.md`) |
| **Internal** | Not a normal creative item (runtime / system block)                            |


Update this when adding or renaming content. Design depth stays in the other docs.

---

## Blocks


| Display name                          | Id                     | Description                                                                    | Status                                                                     |
| ------------------------------------- | ---------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| Quantum Simulator                     | `quantum_simulator`    | Parallel phantom simulation of a contained machine (FE + fluids + batch, up to 32x by flux band). | **Done**                                                                   |
| Quantum Containment Field             | `quantum_containment`  | Temporary glass-like field above an engaged simulator; not player-placed loot. | **Internal** (Done)                                                        |
| Basic Quantum Crafter                 | `basic_quantum_crafter` | Early vanilla crafts (table / furnace / stonecutter); normal FE costs plus a separate 32-run coherence reserve regenerated linearly over 100 ticks. All-face hopper I/O, no auto-push or side config. | **Done** |
| Quantum Foundry                      | `quantum_foundry_controller` + structure parts | 7×7 cross: shared 3×3 plinth and 1–4 field-attunement pillars. Timed native recipes consume FE and local flux; first four-pillar High recipe grows a full Tesseract. | **Done** (more products **Planned**) |
| Quantum Crafter                       | `quantum_crafter`      | Instant electric / mod crafts from catalyst + inputs / Links; FE + flux emit. Catalyst tiers need a flux band; the tax falls in a hotter field. | **Done** (AE2 pattern attach **Planned**)                                  |
| Lattice Crafter                       | —                      | Multiblock: many catalysts, internal recursion / multiprocess.                 | **Planned**                                                                |
| Tesseract Stabilizer                  | `tesseract_stabilizer` | Dock for a full Tesseract; proxies items/fluids with side config + wrench.      | **Done**                                                                   |
| Unrealised Ore                        | `unrealised_ore`       | World ore with client facade; dig → Matter (Fortune); silk → block.            | **Done** (textures polishable)                                             |
| Observation Chamber                   | `observation_chamber`  | Redstone-pulse collapse of Matter → loot + Trace chance.                       | **Done** (FE, automation and sides: see Quantum Observation Chamber; history-aware observe **Planned**) |
| Quantum Observation Chamber           | `quantum_observation_chamber` | Mid-tier chamber: collapses Matter on its own for FE (4 per half second), side configs with auto push/pull, flux emit. | **Done** |
| Materialiser                          | `materialiser`         | Makes Unrealised Matter into the ore you choose, its whole history applied; capped by band (commons at Medium … rares from Critical, very rare never), for Trace (1.2× a roll's value), flux from its chunk and FE. | **Done** |
| Entangled Dock                        | `entangled_dock`       | Superposes an FE item: it stays with the player and charges from the dock anywhere (dock chunk loaded); decoheres at Critical anomaly. | **Done** |
| Quantum Exciter                       | `quantum_exciter`       | Burns FE at 10× flux efficiency; holds Medium (proportional, aiming at 400); emits into the 3×3.| **Done**                                                                   |
| Flux Suppressor                       | `flux_suppressor`      | Early: holds flux, and only flux, under a ceiling (Low / Medium / High / Clear) across the 3×3, up to 1,500 flux/s at 40 FE/t: firebreaks, starving rifts, quiet zones. No fragments. | **Done** |
| Basic Anomaly Siphon                  | `basic_anomaly_siphon` | Early: holds anomaly at the top of Low (80) across the 3×3 of a small workshop (about 1–5k FE/t); up to 500 anomaly/s and 40 FE/t, in proportion to what it takes; the first source of Anomaly Fragments. Replaced Anomaly Containment. | **Done** |
| Anomaly Containment Hall (multiblock)  | `anomaly_containment_hall` | Late 5-tall cell: 3×3 plinth base and cap, three layers of hollow Quantum Attuned Glass, 1–4 reused Foundry arms. `120 + 60×arms` FE/t; capacity holding all of High / Critical / Critical / Singularity over the 3×3 / 3×3 / 5×5 / 5×5; pulls 600 anomaly a second per arm but never touches flux. Holds the Veiled; burns 1 Rift Residue per 5 minutes while holding, fed by hand or through any arm (16 stored). | **Done**                                    |
| Quantum Attuned Glass                 | `quantum_attuned_glass` | Anomaly-resistant glass: seams culled against its own kind, and emissive (11 formed / 4 loose) rather than light-blocking, since blocking skylight shadowed the shell's corners. A `formed` state swaps in a borderless pane so a containment shell reads as one continuous sheet, with a 4px pillar in the corner panes. Table recipe from glass + 4 Anomalite Shards (echoes tinted glass); the Foundry does the same for 1 shard by siphoning local flux. Used by the attunement tank and the containment hall shell. | **Done**                                                                   |
| Anomalite Crystal                         | `anomalite_crystal`    | High+ uncontained anomaly parasite; grows on powered blocks, drains bounded % FE/t, recedes under containment. | **Done**                                                                   |
| Anomalite Lattice                         | `anomalite_lattice`    | Compacted 2×2 Anomalite Shards; used in Budding Anomalite. Magenta–violet placeholder art. | **Done**                                                                   |
| Budding Anomalite                         | `budding_anomalite`    | Powered no-UI host; grows Anomalite Crystals in the mirror regardless of flux/anomaly; 40 FE/t while a face has room. | **Done**                                                                   |
| Flux Detector                         | `flux_detector`        | This-chunk flux/anomaly as redstone + comparator; right-click cycles Flux / Anomaly / Both. | **Done**                                                                   |
| Field Monitor                         | `field_monitor`        | Watches the 9×9 chunks round it: a map screen, and an alarm (redstone, comparator counts chunks) for overloaded containment or uncontained Medium+ / High+ anomaly. No power. | **Done** |
| Debug Field Emitter                   | `debug_field_emitter`  | Creative testing only: emits as if spending 100 FE/t up to 100M FE/t, for watching the field settle. No recipe, no drops. | **Internal** (Done) |
| Flux Maintainer                       | `flux_maintainer`      | Holds flux at or above a floor band (Medium/High/Critical) with pure flux at the Exciter's yield; proportional, counting flux still easing in. | **Done** |
| Anomaly Siphon                        | `anomaly_siphon`       | Keeps anomaly at or below a ceiling band (Low/Medium/High); proportional anomaly-only pull, fragments. | **Done** |
| Field Regulator                       | `field_regulator`      | Late mid: both setpoints in one block at 80% of the FE. | **Done** |
| Superposition Pod (multiblock)        | `superposition_pod` + `pod_cradle`, `pod_plating`, `pod_rescue_module` | Two-tall capsule on a 3×3 cradle with four module slots. Holds one body: swap from an empty pod into any of your doubles (FE by distance, flat 1M across dimensions); the body you leave stays. Rescue module + Anchor = death save, spending that Sophon. | **Done** |
| Pod modules                           | `pod_regeneration_module`, `pod_hardening_module`, `pod_ward_module`, `pod_stash_module`, `pod_charge_module`, `pod_relay_module`, `pod_recovery_module` | Cradle edges: Regeneration I / Resistance I on the owner and Charge for FE items (dimension-wide, pods chunk-loaded while the owner is online, no stacking), Ward (unguarded doubles draw hostiles, which knock them out after 10 s), Stash (27-slot per-player stash, key B), Relay (hub holding six Tesseracts bound to pods: sends a spare double from its network to an empty one, 5 s to form, then swaps you in; Recall/Send Sophons remotely), Recovery (brings field doubles and knocked-out Sophons home, by hand or by itself; not once someone has picked the Sophon up). | **Done** |
| Unfolding Array (multiblock)          | `unfolding_array` + `array_pylon` | Pad with four pylons: stand on it for 30 s with a Totem, 4 Anomaly Fragments and 16 Matter to fold a Sophon of yourself. Four Sophons per player. | **Done** |
| Fold Chamber (multiblock) | `fold_core` + `fold_pylon`, `fold_rail` | A box frame, each side 3–17 (width odd): pylons on the corners, rails on the edges, the core in the middle of the bottom front edge. A Tesseract bound to a multiblock's controller folds the whole volume into a Folded Tesseract from the core's screen; 2,000 FE a block; contents never cross a fold. Per-mod fold adapters; our Quantum Foundry first (needs 9 × 3 × 9). | **Proof of concept** |
| Quantimium Reactor (multiblock) | `horizon_core` + `reactor_plinth`, `ring_emitter`, `catalyst_bay`, `reactor_input_port`, `reactor_output_port`, `reactor_energy_port`, `reactor_materialiser_port` | Fixed 11-wide plinth disc; a Singularity seated in the Horizon Core holds an exact ledger (1M items, x4 per ring pair, up to 3); up to 8 Catalyst Bays. One list of everything held or makeable, counted loop-safe off-thread; taking plans the whole tree through every catalyst, all or nothing. Output ports for the screen, a Materialiser Port for pipes and storage buses. No dangers yet. | **First version** |
| Rift Anchor | `rift_anchor` | Footing for a stabilised rift: seeded with a Rift Seed or slipped under a natural rift. Claims up to six Rift Stabilisers 2–6 blocks out; with 3+ powered and in sight the rift is held, grows 10 min a stage, loses its hazards bar a short-range shock, and makes residue (stage × stabilisers). Array fails, it is a wild rift again. | **Done** |
| Rift Stabiliser | `rift_stabiliser` | Powered pillar for a Rift Anchor: 60 + 40 × stage FE/t while holding. Azure beams to the rift's top and bottom with a sheet of field between. | **Done** |
| Decoherence Projector | `decoherence_projector` | The Lance's beam on a pedestal: holds a beam on the Veiled, mites or rift anchors within 16 blocks. 8 FE/t watching, 120 FE/t beaming. Real world sees it light up; only the mirror sees the beam. Beside a hall it is a Veiled trap. | **Done** (placeholder art) |
| Harvest Laser | `harvest_laser` | Fires through a Rift Lens (anywhere between) at a full-grown Anomalite crystal up to 6 blocks out, head-on or side-on; faces the player when placed; mites capped at 4 within 32 blocks; through a Rift Lens and a hall's glass it draws Veil Thread from a held Veiled instead: 15 s at 60 FE/t, then it shatters for 3 shards and the host regrows it. The tear leaks 15 anomaly a crystal and sometimes a mite (none contained). Both sides see the azure beam into the tear; the mirror also sees it reach the crystal. | **Done** |
| Rift Lens | `rift_lens` | The stone ring a Harvest Laser fires through; holds a small tear open while it does. Grows struts to solid faces and same-way lenses beside it; turns to a laser's line; a wrench turns it. Seats onto hall glass with a collar when right against it. | **Done** |
| Advanced Flux Regulator               | —                      | Late multiblock: flux window + anomaly target.                                 | **Planned**                                                                |
| Quantum Core (multiblock)             | —                      | Late cryogenic expansion: advanced pins, artificial unrealisation, sophons, and Core-only products. | **Planned**                                                                |
| Cloud Aligner (or cloud processors)   | —                      | Process Unrealised Matter without collapsing (append history).                 | **Planned**                                                                |
| Advanced Observer                     | —                      | Pick among legal collapse outcomes for Matter with history.                    | **Planned**                                                                |
| Artificial Unrealiser                 | —                      | Mid: turn classical items/ore into Unrealised Matter (or Core mode).           | **Planned**                                                                |
| Matter Deatomizer                     | —                      | Optional early FE burner (coal / Matter); weak on-theme power.                 | **Planned**                                                                |
| ME Pattern Crafter (optional)         | —                      | Or provider-attach only; exact pattern lock. See `IDEAS.md`.                   | **Planned**                                                                |


---



## Items


| Display name      | Id                  | Description                                                                   | Status                                          |
| ----------------- | ------------------- | ----------------------------------------------------------------------------- | ----------------------------------------------- |
| Semi-Stable Tesseract | `semi_stable_tesseract` | Early item-only link; same dimension and within 16 blocks of its crafter. Cannot dock in a Stabilizer. | **Done** |
| Tether | `tether` | A pod's screen in your hand: swap into your doubles from anywhere, from its own 1M FE buffer. Leaves your body standing where you were as a field double (frozen, 20 health, hunted by mobs and PvP players); knocked out, it drops its Sophon. | **Done** |
| Sophon | `sophon` | A body double folded up, bound to its owner; unfolds into a Superposition Pod. Cannot burn, explode or despawn; only the void (or a rescue) spends one. | **Done** |
| Tesseract | `tesseract` | Stable spacetime pin; bind inventory; crafter ingredient or Stabilizer dock. | **Done** (Foundry-grown) |
| Folded Tesseract | `folded_tesseract` | A multiblock folded by a Fold Chamber: can't tick, portable, a Quantum Crafter catalyst for everything the machine makes; unfolds in a chamber at least as big, facing the same way, and gives the Tesseract back bound to the controller. | **Proof of concept** (Foundry only) |
| Singularity | `singularity` | A Tesseract collapsed in an empty Fold Chamber of 9 or more every way; seated in a Horizon Core it holds that Reactor's whole horizon, and carries it when taken out. "It's safe, I promise." | **First version** |
| Unrealised Matter | `unrealised_matter` | Unresolved loot; glitch name. Collapses into any of the pack's ores by rarity, rarer in a hotter field. | **Done** (histories, Materialiser, Flask: M4, in progress) |
| Quantimium Trace  | `quantimium_trace`  | Collapse byproduct; early feedstock.                                          | **Done**                                        |
| Basic Decoherence Matrix | `basic_decoherence_matrix` | Ender Eye held in four Quantimium Traces; core component of the Basic Quantum Crafter. | **Done** |
| Flux Meter        | `flux_meter`        | This chunk’s flux + anomaly on the action bar; on machines also shows FE/t. | **Done**                                        |
| Mirror Lens | `mirror_lens` | Head-slot goggles: the field made visible (plumes, motes, feeders and drains) and the Veiled's true body, faint; it notices. | **Done** (Curios slot **Planned**) |
| Anomaly Fragments | `anomaly_fragment`  | Suppressor byproduct; mid/late crafting resource. Surplus voids if output is full. | **Done**                                        |
| Anomalite Shard | `anomalite_shard` | Plain / Fortune harvest of a full crystal; 2×2 → Lattice. | **Done** |
| Anomalite Dust  | `anomalite_dust`  | Crumbles from an immature crystal. Reprocessed 1:1 into Anomalite Shards by the Foundry (100 ticks, 25 flux, Medium band). | **Done** |
| Anomalite Cell  | `anomalite_cell`  | A prism of Anomalite shards sealed in glass (Foundry, Medium). What a Decoherence Projector burns: about one Cell per Veiled fight, less for mites and rifts; used up whole. | **Done** |
| Anomalite Emitter | `anomalite_emitter` | A shard in a mount (Foundry, Medium): the Decoherence Lance's light, in its recipe. | **Done** |
| Decoherence Lance | `decoherence_lance` | Channelled FE tool: closes flux rifts from the mirror side, unmakes mirror mites; harmless to anything not between realms. | **Done** (stalker deterrent **Planned**) |
| Rift Residue      | `rift_residue`      | Payout for collapsing a flux rift (1 / 3 / 6 / 10 by stage), and made steadily by a stabilised rift. Upkeep for a Containment Hall holding a Veiled: 1 per 5 minutes, fed through the arms or a bound Tesseract. | **Done** |
| Veil Thread       | `veil_thread`       | Drawn out of a Veiled held in a Containment Hall by a Harvest Laser firing through a Rift Lens and the hall's glass: one per 3 minutes at 120 FE/t, one laser per hall. For upgrades and capstones; no uses yet. | **Done** |
| Rift Seed         | `rift_seed`         | Fragments, Anomalite and an eye of ender (Mid). Plants a wild stage 1 rift where anomaly is Medium+ (fizzles elsewhere), opens a held one on a Rift Anchor, lights the Stabilised Portal. | **Done** |
| Sophon Fragment   | —                   | Late probe/catalyst; remote “observe” fantasy.                                | **Planned**                                     |
| Qubit Die         | —                   | Core craft part.                                                              | **Planned**                                     |
| Cryostat Shell    | —                   | Core craft part.                                                              | **Planned**                                     |
| Control Waveguide | —                   | Core craft part.                                                              | **Planned**                                     |
| Syndrome Lattice  | —                   | Core craft part.                                                              | **Planned**                                     |
| Classical Coupler | —                   | Core craft part.                                                              | **Planned**                                     |
| Entanglement Port | —                   | Core craft part / hatch.                                                      | **Planned**                                     |


Block items exist for every placeable block above (same id as the block). They are omitted from the item table to avoid duplication.

---



## Systems (not items, but content milestones)


| System                         | Description                                                              | Status                                              |
| ------------------------------ | ------------------------------------------------------------------------ | --------------------------------------------------- |
| Chunk Quantum Flux             | Field Model 2.0: per-chunk field with pending pools; emission into the 3×3, eased in; smooth decay; diffusion between loaded chunks; containment as capacity with a load %; readings blended between chunks. | **Done**                                            |
| The Veiled | Mirror-world stalker: a hooded 3-block figure with a rift for a face. Released by stage 4 rifts; hides when watched from the real world, curious in the mirror, feeds on machines or the player's charge, aggravated by the Lance, held by the Containment Hall. See `VEILED_BRIEF.html`. | **Partial** (behaviour, rendered rift face, flickering lights and edge-of-view glimpses Done) |
| Flux Rift                      | A failed anomaly tear left as a persistent, saved wound. Grows four stages (anomaly = speed, flux = ceiling), leaks anomaly, keeps an entry tear open nearby, frozen by containment, closed only from the mirror with the Decoherence Lance while it spits mites; pays Rift Residue. | **Partial** (slices 1–2 Done: flora bleed, mite breach; stalker **Planned**) |
| Anomaly                        | Separate danger meter; uncontained flux couples in; FE surcharge; suppressors chew it; containment shields; High+ grows Anomalite Crystals; Medium+ can tear open short-lived real-world entry rifts. | **Partial** (v1 + suppressor + basic shield + anomalite + entry tears Done) |
| Mirror Phase                   | Server-authoritative world overlay; phase-only Anomalite, Unrealised Ore harvest in both realms, interaction isolation, shared 30s return tears, real-world entry tears, crafted 4×5 stabilised portal, purple-grey client treatment, flux-scaled mirror mites, other-realm tesseract souls. | **Partial** (MVP + mites + gate + anomaly entry + wisps Done; vine dressing **Planned**) |
| Recipe / machine flux gating   | Basic Crafter admits basic catalysts; later machines also require this-chunk bands and may cascade when under-shielded. | **Partial** (machine-family gate started; world-band gate planned) |
| Resource triangle              | Exciter prints · Core spends · Suppressor spends both · Containment shields. | **Partial** (Exciter + suppressor + basic containment Done) |
| Deferred measurement           | Ordered process history on Matter; Crafter as cloud workshop.            | **Planned**                                         |
| AE2 Pattern Provider → Crafter | Exact recipe lock like Molecular Assembler.                              | **Planned**                                         |
| Recipe viewer integration      | JEI plugin (optional dep): Foundry category showing time, FE/t, flux band, flux cost and required arms, plus an Observation Chamber category built from the collapse loot table synced to clients. EMI reads it through JEMI. | **Done** (native EMI / REI plugins **Planned**) |


---



## Snapshot counts


|                      | Done | Partial | Planned |
| -------------------- | ---- | ------- | ------- |
| Player-facing blocks | 15   | 1       | ~8      |
| Standalone items     | 9    | 1       | ~7      |
| Systems              | 2    | 4       | several |


*(Quantum Containment Field excluded from “player-facing.”)*

---



## Related docs

- `CONTAINMENT.md` — flux / anomaly / suppressors / regulators  
- `QUANTUM_CORE.md` — Core multiblock + craft parts + sophons  
- `UNREALISED.md` — Matter / deferred measurement  
- `IDEAS.md` — parking lot (AE2 patterns, clones, etc.)

