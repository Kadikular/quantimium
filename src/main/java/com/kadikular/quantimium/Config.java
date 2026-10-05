package com.kadikular.quantimium;

import com.kadikular.quantimium.flux.FluxBand;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Common gameplay knobs. Anomaly ill effects are on by default so a hot field has teeth; packs or
 * players who want a gentler factory can switch either tax off without a rebuild.
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("anomaly");
        BUILDER.comment("Ill effects scale with this chunk's anomaly, not flux and not a neighbour's peak.");
    }

    public static final ModConfigSpec.BooleanValue CRAFT_SURCHARGE = BUILDER
            .comment("Charge extra FE on Quantum Crafter crafts and Quantum Simulator work when anomaly is above Low.")
            .define("craftSurcharge", true);

    public static final ModConfigSpec.IntValue SURCHARGE_MEDIUM = BUILDER
            .comment("Extra percent at Medium anomaly (25 = +25%).")
            .defineInRange("surchargePercentMedium", 25, 0, 1000);
    public static final ModConfigSpec.IntValue SURCHARGE_HIGH = BUILDER
            .comment("Extra percent at High anomaly.")
            .defineInRange("surchargePercentHigh", 50, 0, 1000);
    public static final ModConfigSpec.IntValue SURCHARGE_CRITICAL = BUILDER
            .comment("Extra percent at Critical anomaly.")
            .defineInRange("surchargePercentCritical", 100, 0, 1000);
    public static final ModConfigSpec.IntValue SURCHARGE_SINGULARITY = BUILDER
            .comment("Extra percent at Singularity anomaly.")
            .defineInRange("surchargePercentSingularity", 200, 0, 1000);

    public static final ModConfigSpec.BooleanValue PASSIVE_DRAIN = BUILDER
            .comment("Leak a flat FE/t from each Quantimium machine buffer while anomaly is above Low. Does not emit flux.")
            .define("passiveDrain", true);

    public static final ModConfigSpec.IntValue DRAIN_MEDIUM = BUILDER
            .comment("FE/t leaked per machine at Medium anomaly. A small existence tax, not a cable sink.")
            .defineInRange("passiveDrainFePerTickMedium", 16, 0, 1_000_000);
    public static final ModConfigSpec.IntValue DRAIN_HIGH = BUILDER
            .comment("FE/t leaked per machine at High anomaly.")
            .defineInRange("passiveDrainFePerTickHigh", 40, 0, 1_000_000);
    public static final ModConfigSpec.IntValue DRAIN_CRITICAL = BUILDER
            .comment("FE/t leaked per machine at Critical anomaly.")
            .defineInRange("passiveDrainFePerTickCritical", 80, 0, 1_000_000);
    public static final ModConfigSpec.IntValue DRAIN_SINGULARITY = BUILDER
            .comment("FE/t leaked per machine at Singularity anomaly.")
            .defineInRange("passiveDrainFePerTickSingularity", 160, 0, 1_000_000);

    static {
        BUILDER.pop();
        BUILDER.push("anomalite");
    }

    public static final ModConfigSpec.BooleanValue ANOMALITE = BUILDER
            .comment("Allow Anomalite Crystals to appear on powered blocks in uncontained High+ anomaly chunks.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue ANOMALITE_SPAWN_INTERVAL_SECONDS = BUILDER
            .comment("Mean seconds between spawn rolls per eligible High-anomaly chunk.")
            .defineInRange("spawnIntervalSecondsHigh", 120, 1, 86_400);
    public static final ModConfigSpec.DoubleValue ANOMALITE_CRITICAL_FREQUENCY = BUILDER
            .comment("Spawn-frequency multiplier at Critical anomaly.")
            .defineInRange("criticalFrequencyMultiplier", 2.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue ANOMALITE_SINGULARITY_FREQUENCY = BUILDER
            .comment("Spawn-frequency multiplier at Singularity anomaly.")
            .defineInRange("singularityFrequencyMultiplier", 5.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue ANOMALITE_HOST_SAMPLES = BUILDER
            .comment("Maximum random block entities capability-probed after a successful spawn roll.")
            .defineInRange("hostSamplesPerRoll", 3, 1, 32);
    public static final ModConfigSpec.IntValue ANOMALITE_GROWTH_CHANCE = BUILDER
            .comment("Percent of random ticks that advance a crystal one stage while it is feeding.")
            .defineInRange("growthChancePercentPerRandomTick", 20, 0, 100);
    public static final ModConfigSpec.IntValue ANOMALITE_DECAY_CHANCE = BUILDER
            .comment("Percent of random ticks that pull a crystal back one stage while its chunk is contained. A bud receding vanishes.")
            .defineInRange("decayChancePercentPerRandomTick", 20, 0, 100);

    public static final ModConfigSpec.DoubleValue ANOMALITE_DRAIN_STAGE_0 = drainPercent("drainPercentStage0", 0.02);
    public static final ModConfigSpec.DoubleValue ANOMALITE_DRAIN_STAGE_1 = drainPercent("drainPercentStage1", 0.04);
    public static final ModConfigSpec.DoubleValue ANOMALITE_DRAIN_STAGE_2 = drainPercent("drainPercentStage2", 0.07);
    public static final ModConfigSpec.DoubleValue ANOMALITE_DRAIN_STAGE_3 = drainPercent("drainPercentStage3", 0.10);
    public static final ModConfigSpec.IntValue ANOMALITE_CAP_STAGE_0 = drainCap("drainCapStage0", 40);
    public static final ModConfigSpec.IntValue ANOMALITE_CAP_STAGE_1 = drainCap("drainCapStage1", 70);
    public static final ModConfigSpec.IntValue ANOMALITE_CAP_STAGE_2 = drainCap("drainCapStage2", 110);
    public static final ModConfigSpec.IntValue ANOMALITE_CAP_STAGE_3 = drainCap("drainCapStage3", 160);
    public static final ModConfigSpec.DoubleValue ANOMALITE_CRITICAL_DRAIN = BUILDER
            .comment("Crystal drain-cap multiplier at Critical anomaly.")
            .defineInRange("criticalDrainMultiplier", 2.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue ANOMALITE_SINGULARITY_DRAIN = BUILDER
            .comment("Crystal drain-cap multiplier at Singularity anomaly.")
            .defineInRange("singularityDrainMultiplier", 5.0, 0.0, 100.0);

    static {
        BUILDER.pop();
        BUILDER.push("crafter");
    }

    public static final ModConfigSpec.BooleanValue STRUCTURELESS_MULTIBLOCKS = BUILDER
            .comment("Whether a multiblock's controller alone works as a Quantum Crafter catalyst, making the multiblock's recipes without its structure being built. Controllers are those in #quantimium:multiblock_controllers. Off by default: fold the built structure in a Fold Chamber instead. Casual packs may want it on.")
            .define("structurelessMultiblocks", false);

    public static final ModConfigSpec.IntValue INSTANT_CRAFT_MULTIPLIER = BUILDER
            .comment("Tax on skipping the real machine's wait. Applied to furnace burn time, MI EU, and flat convenience fees. Default 2: one vanilla smelt is 4,000 FE.")
            .defineInRange("instantCraftMultiplier", 2, 1, 100);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CRAFTER_TAX_BY_BAND = BUILDER
            .comment("Running hot pays: the instant-craft tax the Quantum Crafter charges in each flux band (Low, Medium, High, Critical, Singularity), in percent of instantCraftMultiplier. Default 100, 100, 80, 65, 55: at the default multiplier of 2, that's 2.0x, 2.0x, 1.6x, 1.3x and 1.1x. All 100 flattens it.")
            .defineList("taxPercentByBand", List.of(100, 100, 80, 65, 55), () -> 100, o -> o instanceof Integer i && i >= 1 && i <= 1000);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CRAFTER_CATALYST_BANDS = BUILDER
            .comment("Running hot pays: the flux band the Quantum Crafter needs for each family of catalyst, in order: #quantimium:quantum_crafter_catalysts/basic, /advanced, /industrial, #quantimium:multiblock_controllers (without their structure), /singularity. Default low, medium, high, critical, singularity. All low flattens it.")
            .defineList("catalystBands", List.of("low", "medium", "high", "critical", "singularity"), () -> "low",
                    o -> o instanceof String band && FluxBand.byName(band) != null);

    static {
        BUILDER.pop();
        BUILDER.push("basicCrafter");
    }

    public static final ModConfigSpec.IntValue BASIC_CRAFTER_CAPACITY = BUILDER
            .comment("Maximum recipe runs the Basic Quantum Crafter can collapse before replenishing.")
            .defineInRange("coherenceCapacity", 32, 1, 4096);
    public static final ModConfigSpec.IntValue BASIC_CRAFTER_REFILL_TICKS = BUILDER
            .comment("Ticks for the Basic Quantum Crafter to regenerate one full coherence reserve at a steady rate.")
            .defineInRange("coherenceRefillTicks", 100, 1, 72_000);

    static {
        BUILDER.pop();
        BUILDER.push("unrealised");
    }

    public static final ModConfigSpec.EnumValue<CollapseSource> COLLAPSE_SOURCE = BUILDER
            .comment("What Unrealised Matter collapses into. PACK_ORES: every ore in the pack's #c:ores tags, by rarity (#quantimium:unrealised/common … /very_rare; an untagged ore counts as very rare). LOOT_TABLE: the hand-made loot table quantimium:gameplay/unrealised_matter instead.")
            .defineEnum("collapseSource", CollapseSource.PACK_ORES);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RARITY_SHARES_LOW = raritySharesFor("low", List.of(100, 0, 0, 0));
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RARITY_SHARES_MEDIUM = raritySharesFor("medium", List.of(70, 30, 0, 0));
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RARITY_SHARES_HIGH = raritySharesFor("high", List.of(55, 30, 15, 0));
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RARITY_SHARES_CRITICAL = raritySharesFor("critical", List.of(45, 30, 17, 8));
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RARITY_SHARES_SINGULARITY = raritySharesFor("singularity", List.of(35, 30, 20, 15));

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> STEP_HOLD_BY_BAND = BUILDER
            .comment("How likely each step of processed Matter's history holds when a Chamber observes it, in percent, in each flux band (Low to Singularity). A step that doesn't hold collapses it there, as what it had become so far, and leaves a Trace. Default 50, 65, 80, 90, 95.")
            .defineList("stepHoldPercentByBand", List.of(50, 65, 80, 90, 95), () -> 50, o -> o instanceof Integer i && i >= 0 && i <= 100);

    public static final ModConfigSpec.IntValue CHAMBER_FLUX_PER_BAND = BUILDER
            .comment("Flux an Observation Chamber takes from its chunk for each collapse above Low, per band step: the price of the better odds. With too little flux, it rolls at Low's odds. Default 5: 5 at Medium, 20 at Singularity.")
            .defineInRange("chamberFluxPerBand", 5, 0, 10_000);
    public static final ModConfigSpec.IntValue MATERIALISER_PREMIUM = BUILDER
            .comment("What choosing an ore at the Materialiser costs over rolling for it, in percent: the Trace price is this share of the ore's value over the average value of a roll in that band (common 1, uncommon 3, rare 10, very rare 30). Default 120.")
            .defineInRange("materialiserPremiumPercent", 120, 1, 10_000);
    public static final ModConfigSpec.IntValue MATERIALISER_FLUX = BUILDER
            .comment("Flux the Materialiser debits from its chunk for each Matter, per point of the ore's value. Default 25: 25 for a common, 250 for a rare.")
            .defineInRange("materialiserFluxPerValue", 25, 0, 100_000);

    public enum CollapseSource { PACK_ORES, LOOT_TABLE }

    static {
        BUILDER.pop();
        BUILDER.push("mirrorPhase");
    }

    public static final ModConfigSpec.IntValue MIRROR_TIMEOUT_SECONDS = BUILDER
            .comment("Hard failsafe duration for one Mirror Phase session.")
            .defineInRange("timeoutSeconds", 300, 30, 3600);
    public static final ModConfigSpec.IntValue RIFT_MIN_DISTANCE = BUILDER
            .comment("Minimum horizontal distance from a phased player when placing their return rift. Caves use a closer ring so the tear stays on this storey.")
            .defineInRange("riftMinDistance", 10, 4, 64);
    public static final ModConfigSpec.IntValue RIFT_MAX_DISTANCE = BUILDER
            .comment("Maximum horizontal distance from a phased player when placing their return rift.")
            .defineInRange("riftMaxDistance", 20, 5, 128);
    public static final ModConfigSpec.IntValue RIFT_RELOCATE_DISTANCE = BUILDER
            .comment("Relocate the owner's return rift when they move farther than this.")
            .defineInRange("riftRelocateDistance", 32, 8, 256);
    public static final ModConfigSpec.IntValue RETURN_RIFT_LIFETIME_SECONDS = BUILDER
            .comment("How long a return tear stays so other phased players can use it.")
            .defineInRange("returnRiftLifetimeSeconds", 30, 5, 600);
    public static final ModConfigSpec.BooleanValue ENTRY_RIFTS = BUILDER
            .comment("Spawn short-lived real-world entry tears in Medium+ anomaly chunks.")
            .define("entryRifts", true);
    public static final ModConfigSpec.IntValue ENTRY_RIFT_LIFETIME_SECONDS = BUILDER
            .comment("How long an anomaly-spawned entry tear stays open.")
            .defineInRange("entryRiftLifetimeSeconds", 120, 5, 600);
    public static final ModConfigSpec.IntValue ENTRY_RIFT_INTERVAL_MEDIUM = BUILDER
            .comment("Mean seconds between entry-tear rolls for one band in one dimension. High / Critical / Singularity halve this each step (15 / 7.5 / 3.75 min at the default). Each band rolls among its own loaded chunks, so a hot hall does not starve a Medium outpost.")
            .defineInRange("entryRiftIntervalSecondsMedium", 450, 20, 86_400);
    public static final ModConfigSpec.IntValue ENTRY_RIFT_MAX = BUILDER
            .comment("Maximum concurrent anomaly-spawned entry tears in one dimension.")
            .defineInRange("entryRiftMaxPerDimension", 8, 1, 64);
    public static final ModConfigSpec.IntValue ENTRY_RIFT_SPACING = BUILDER
            .comment("Minimum blocks between anomaly-spawned entry tears.")
            .defineInRange("entryRiftSpacing", 32, 8, 128);

    static {
        BUILDER.pop();
        BUILDER.push("fluxRift");
    }

    public static final ModConfigSpec.BooleanValue FLUX_RIFTS = BUILDER
            .comment("Let anomaly entry tears fail to close in uncontained High+ fields, leaving a persistent flux rift.")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue FLUX_RIFT_CHANCE = BUILDER
            .comment("Chance that an anomaly entry tear expiring in an uncontained High+ chunk fails to close.")
            .defineInRange("failedTearChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue FLUX_RIFT_MAX = BUILDER
            .comment("Maximum flux rifts in one dimension: a safety net. What limits rifts in play is maxNearby, so one player's rifts don't stop another's across the world.")
            .defineInRange("maxInDimension", 64, 1, 1024);
    public static final ModConfigSpec.IntValue FLUX_RIFT_MAX_NEARBY = BUILDER
            .comment("Most flux rifts within nearbyRadius blocks of each other. A tear won't fail, and a Rift Seed won't take, where there are already this many.")
            .defineInRange("maxNearby", 3, 1, 64);
    public static final ModConfigSpec.IntValue FLUX_RIFT_NEARBY_RADIUS = BUILDER
            .comment("How far maxNearby counts rifts, in blocks.")
            .defineInRange("nearbyRadius", 256, 16, 4096);
    public static final ModConfigSpec.IntValue FLUX_RIFT_SPACING = BUILDER
            .comment("Minimum blocks between flux rifts.")
            .defineInRange("spacing", 64, 8, 512);
    public static final ModConfigSpec.IntValue FLUX_RIFT_STAGE_SECONDS = BUILDER
            .comment("Seconds for a rift to grow one stage under High anomaly. Medium doubles it, Critical halves it, Singularity quarters it; Low and contained chunks freeze it.")
            .defineInRange("stageSecondsHigh", 600, 10, 86_400);
    public static final ModConfigSpec.DoubleValue FLUX_RIFT_LEAK = BUILDER
            .comment("Anomaly an uncontained rift leaks into its 5×5 neighbourhood each second, per stage (split across the chunks). At 5, a stage 3–4 rift alone holds its surroundings near the Low/Medium boundary: enough to keep mites and tears around, not enough to feed its own growth.")
            .defineInRange("anomalyLeakPerStage", 5.0, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue FLUX_RIFT_COHERENCE = BUILDER
            .comment("Coherence a rift holds per stage. The Decoherence Lance drains it; it regenerates once the beam stops.")
            .defineInRange("coherencePerStage", 400, 1, 100_000);

    static {
        BUILDER.pop();
        BUILDER.push("decoherenceLance");
    }

    public static final ModConfigSpec.IntValue LANCE_FE_PER_TICK = BUILDER
            .comment("FE the Decoherence Lance burns per tick while channelling.")
            .defineInRange("fePerTick", 80, 0, 100_000);
    public static final ModConfigSpec.DoubleValue LANCE_DRAIN = BUILDER
            .comment("Rift coherence drained per tick of beam contact.")
            .defineInRange("drainPerTick", 5.0, 0.1, 10_000.0);

    static {
        BUILDER.pop();
        BUILDER.push("zenoField");
    }

    public static final ModConfigSpec.IntValue ZENO_MAX_EXTRA_TICKS = BUILDER
            .comment("Most extra random ticks one accelerating Zeno Field Controller may add per game tick, counting every copy when it runs in a Quantum Simulator. 0 is unlimited. The FE cost is unchanged by the cap. The default 256 is about five of the biggest, fastest fields (radius 8 at 16x adds about 54 a tick): room for a Simulator to multiply one, but not for nested Simulators to multiply it 64 times.")
            .defineInRange("maxExtraTicksPerTick", 256, 0, 1_000_000);
    public static final ModConfigSpec.DoubleValue ZENO_SLOWED_CHANCE = BUILDER
            .comment("Chance an extra random tick landing on a block in #quantimium:zeno_slowed goes ahead. Blocks in #quantimium:zeno_ignored get no extra ticks at all; both still get their normal ones.")
            .defineInRange("slowedTickChance", 0.125, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> ZENO_FACTOR_BY_BAND = BUILDER
            .comment("Running hot pays: the fastest a Zeno Field Controller accelerates in each flux band (Low, Medium, High, Critical, Singularity): 2, 4, 8 or 16. Default 2, 4, 8, 16, 16. Holding is never gated.")
            .defineList("maxFactorByBand", List.of(2, 4, 8, 16, 16), () -> 2, o -> o instanceof Integer i && (i == 2 || i == 4 || i == 8 || i == 16));
    public static final ModConfigSpec.IntValue ZENO_SINGULARITY_COST = BUILDER
            .comment("Running hot pays: what accelerating costs at Singularity flux, in percent of its usual FE. Default 75.")
            .defineInRange("singularityCostPercent", 75, 1, 100);

    static {
        BUILDER.pop();
        BUILDER.push("fieldVision");
    }

    public static final ModConfigSpec.DoubleValue FIELD_VISION_DENSITY = BUILDER
            .comment("How much of the field the Mirror Lens (and the mirror) draws: plumes, motes and threads. 1 is the intended amount, 0 draws nothing.")
            .defineInRange("density", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.BooleanValue FIELD_HUD = BUILDER
            .comment("Show the Mirror Lens's readout of the chunk you stand in (flux, anomaly, containment) while you can see the field.")
            .define("hud", true);
    public static final ModConfigSpec.EnumValue<HudCorner> FIELD_HUD_CORNER = BUILDER
            .comment("Which corner of the screen the readout sits in.")
            .defineEnum("hudCorner", HudCorner.TOP_LEFT);

    public static final ModConfigSpec.EnumValue<MapOverlay> MAP_OVERLAY = BUILDER
            .comment("What JourneyMap draws over each chunk: FIELD (flux in azure, anomaly washed violet over it, as the Mirror Lens's map), LOAD (containment: pale when held, amber when overloaded) or OFF.")
            .defineEnum("mapOverlay", MapOverlay.FIELD);
    public static final ModConfigSpec.BooleanValue MAP_RIFTS = BUILDER
            .comment("Mark flux rifts on JourneyMap, with their stage.")
            .define("mapRifts", true);

    public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public enum MapOverlay { FIELD, LOAD, OFF }

    static {
        BUILDER.pop();
        BUILDER.push("simulator");
    }

    public static final ModConfigSpec.IntValue SIMULATOR_MAX_NESTING = BUILDER
            .comment("Most Quantum Simulators in one chain, counting the outer one: 2 lets a Simulator hold one engaged Simulator (a machine simulated twice over, and the advancement for it), 1 allows no nesting. Nesting multiplies the batches, so 8x within 8x is 64 copies.")
            .defineInRange("maxNestingDepth", 2, 1, 8);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> SIMULATOR_BATCH_BY_BAND = BUILDER
            .comment("Running hot pays: the largest batch a Quantum Simulator runs in each flux band (Low, Medium, High, Critical, Singularity). Default 2, 4, 8, 16, 32. A larger batch chosen in the GUI runs at this size until the field is hot enough.")
            .defineList("maxBatchByBand", List.of(2, 4, 8, 16, 32), () -> 2, o -> o instanceof Integer i && i >= 1 && i <= 32);

    static {
        BUILDER.pop();
        BUILDER.push("superposition");
    }

    public static final ModConfigSpec.IntValue MAX_DOUBLES = BUILDER
            .comment("Most Sophons (body doubles) one player can have at once, folded or in a pod. The soul can only be split so many times.")
            .defineInRange("maxDoubles", 4, 1, 16);
    public static final ModConfigSpec.BooleanValue CHUNK_LOAD_PODS = BUILDER
            .comment("Keep the chunks of pods holding a player's doubles loaded while that player is online, so their modules (Regeneration, Charge, Ward...) work however far away they are.")
            .define("chunkLoadPods", true);

    static {
        BUILDER.pop();
        BUILDER.push("reactor");
    }

    public static final ModConfigSpec.BooleanValue MATERIALISER_PORT_ACCEPTS_ITEMS = BUILDER
            .comment("Whether a Reactor's Materialiser Port also takes items in, as its Input Port does, which makes the Reactor full storage through one storage bus. Off keeps it extract-only.")
            .define("materialiserPortAcceptsItems", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean materialiserPortAcceptsItems() {
        return !SPEC.isLoaded() || MATERIALISER_PORT_ACCEPTS_ITEMS.get();
    }

    private Config() {}

    public static int surchargePercent(FluxBand band) {
        if (!SPEC.isLoaded()) return band.defaultSurchargePercent();
        if (!CRAFT_SURCHARGE.get()) return 0;
        return switch (band) {
            case LOW -> 0;
            case MEDIUM -> SURCHARGE_MEDIUM.get();
            case HIGH -> SURCHARGE_HIGH.get();
            case CRITICAL -> SURCHARGE_CRITICAL.get();
            case SINGULARITY -> SURCHARGE_SINGULARITY.get();
        };
    }

    public static int passiveDrainFePerTick(FluxBand band) {
        if (!SPEC.isLoaded()) {
            return switch (band) {
                case LOW -> 0;
                case MEDIUM -> 16;
                case HIGH -> 40;
                case CRITICAL -> 80;
                case SINGULARITY -> 160;
            };
        }
        if (!PASSIVE_DRAIN.get()) return 0;
        return switch (band) {
            case LOW -> 0;
            case MEDIUM -> DRAIN_MEDIUM.get();
            case HIGH -> DRAIN_HIGH.get();
            case CRITICAL -> DRAIN_CRITICAL.get();
            case SINGULARITY -> DRAIN_SINGULARITY.get();
        };
    }

    public static int mirrorTimeoutTicks() {
        return (SPEC.isLoaded() ? MIRROR_TIMEOUT_SECONDS.get() : 300) * 20;
    }

    public static int instantCraftMultiplier() {
        return SPEC.isLoaded() ? INSTANT_CRAFT_MULTIPLIER.get() : 2;
    }

    /** The Crafter's instant-craft tax in {@code band}, as a fraction of {@link #instantCraftMultiplier()}. */
    public static double crafterTaxFraction(FluxBand band) {
        return byBand(SPEC.isLoaded() ? CRAFTER_TAX_BY_BAND.get() : null, CRAFTER_TAX_BY_BAND.getDefault(), band) / 100.0;
    }

    /** The band the Crafter needs for catalyst family {@code family}: 0 basic … 4 singularity. */
    public static FluxBand crafterCatalystBand(int family) {
        List<? extends String> bands = SPEC.isLoaded() ? CRAFTER_CATALYST_BANDS.get() : CRAFTER_CATALYST_BANDS.getDefault();
        FluxBand band = family < bands.size() ? FluxBand.byName(bands.get(family)) : null;
        return band != null ? band : FluxBand.byName(CRAFTER_CATALYST_BANDS.getDefault().get(family));
    }

    public static int simulatorMaxBatch(FluxBand band) {
        return byBand(SPEC.isLoaded() ? SIMULATOR_BATCH_BY_BAND.get() : null, SIMULATOR_BATCH_BY_BAND.getDefault(), band);
    }

    public static int zenoMaxFactor(FluxBand band) {
        return byBand(SPEC.isLoaded() ? ZENO_FACTOR_BY_BAND.get() : null, ZENO_FACTOR_BY_BAND.getDefault(), band);
    }

    public static int zenoSingularityCostPercent() {
        return SPEC.isLoaded() ? ZENO_SINGULARITY_COST.get() : ZENO_SINGULARITY_COST.getDefault();
    }

    /** The entry for {@code band} in a list of five, falling back to the default when the list is short. */
    private static int byBand(List<? extends Integer> values, List<? extends Integer> defaults, FluxBand band) {
        if (values != null && band.ordinal() < values.size()) return values.get(band.ordinal());
        return defaults.get(band.ordinal());
    }

    public static int basicCrafterCapacity() {
        return SPEC.isLoaded() ? BASIC_CRAFTER_CAPACITY.get() : 32;
    }

    public static int basicCrafterRefillTicks() {
        return SPEC.isLoaded() ? BASIC_CRAFTER_REFILL_TICKS.get() : 100;
    }

    public static int riftMinDistance() {
        return SPEC.isLoaded() ? RIFT_MIN_DISTANCE.get() : 10;
    }

    public static int riftMaxDistance() {
        int min = riftMinDistance();
        return Math.max(min, SPEC.isLoaded() ? RIFT_MAX_DISTANCE.get() : 20);
    }

    public static int riftRelocateDistance() {
        return SPEC.isLoaded() ? RIFT_RELOCATE_DISTANCE.get() : 32;
    }

    public static int returnRiftLifetimeTicks() {
        return (SPEC.isLoaded() ? RETURN_RIFT_LIFETIME_SECONDS.get() : 30) * 20;
    }

    public static boolean entryRiftsEnabled() {
        return !SPEC.isLoaded() || ENTRY_RIFTS.get();
    }

    public static int entryRiftLifetimeTicks() {
        return (SPEC.isLoaded() ? ENTRY_RIFT_LIFETIME_SECONDS.get() : 60) * 20;
    }

    public static int entryRiftMax() {
        return SPEC.isLoaded() ? ENTRY_RIFT_MAX.get() : 8;
    }

    public static int entryRiftSpacing() {
        return SPEC.isLoaded() ? ENTRY_RIFT_SPACING.get() : 32;
    }

    /** Once-per-second roll probability for one band in one dimension. */
    public static double entryRiftSpawnChance(FluxBand band) {
        if (!entryRiftsEnabled() || band.ordinal() < FluxBand.MEDIUM.ordinal()) return 0.0;
        int medium = SPEC.isLoaded() ? ENTRY_RIFT_INTERVAL_MEDIUM.get() : 1800;
        int steps = band.ordinal() - FluxBand.MEDIUM.ordinal();
        double interval = medium / Math.pow(2.0, steps);
        return Math.min(1.0, 1.0 / Math.max(1.0, interval));
    }

    public static boolean fluxRiftsEnabled() {
        return !SPEC.isLoaded() || FLUX_RIFTS.get();
    }

    public static double fluxRiftChance() {
        return SPEC.isLoaded() ? FLUX_RIFT_CHANCE.get() : 0.25;
    }

    public static int fluxRiftMax() {
        return SPEC.isLoaded() ? FLUX_RIFT_MAX.get() : FLUX_RIFT_MAX.getDefault();
    }

    public static int fluxRiftMaxNearby() {
        return SPEC.isLoaded() ? FLUX_RIFT_MAX_NEARBY.get() : FLUX_RIFT_MAX_NEARBY.getDefault();
    }

    public static int fluxRiftNearbyRadius() {
        return SPEC.isLoaded() ? FLUX_RIFT_NEARBY_RADIUS.get() : FLUX_RIFT_NEARBY_RADIUS.getDefault();
    }

    public static int fluxRiftSpacing() {
        return SPEC.isLoaded() ? FLUX_RIFT_SPACING.get() : 64;
    }

    /** Seconds per stage for this anomaly band; 0 means frozen. */
    public static double fluxRiftStageSeconds(FluxBand anomaly) {
        double high = SPEC.isLoaded() ? FLUX_RIFT_STAGE_SECONDS.get() : 600;
        return switch (anomaly) {
            case LOW -> 0.0;
            case MEDIUM -> high * 2.0;
            case HIGH -> high;
            case CRITICAL -> high / 2.0;
            case SINGULARITY -> high / 4.0;
        };
    }

    public static double fluxRiftLeakPerStage() {
        return SPEC.isLoaded() ? FLUX_RIFT_LEAK.get() : 5.0;
    }

    public static double fluxRiftCoherencePerStage() {
        return SPEC.isLoaded() ? FLUX_RIFT_COHERENCE.get() : 400;
    }

    public static int lanceFePerTick() {
        return SPEC.isLoaded() ? LANCE_FE_PER_TICK.get() : 80;
    }

    public static double lanceDrainPerTick() {
        return SPEC.isLoaded() ? LANCE_DRAIN.get() : 5.0;
    }

    public static boolean anomaliteEnabled() {
        return !SPEC.isLoaded() || ANOMALITE.get();
    }

    /** Once-per-second roll probability for one eligible active chunk. */
    public static double anomaliteSpawnChance(FluxBand band) {
        if (!anomaliteEnabled() || band.ordinal() < FluxBand.HIGH.ordinal()) return 0.0;
        int interval = SPEC.isLoaded() ? ANOMALITE_SPAWN_INTERVAL_SECONDS.get() : 120;
        double multiplier = switch (band) {
            case HIGH -> 1.0;
            case CRITICAL -> SPEC.isLoaded() ? ANOMALITE_CRITICAL_FREQUENCY.get() : 2.0;
            case SINGULARITY -> SPEC.isLoaded() ? ANOMALITE_SINGULARITY_FREQUENCY.get() : 5.0;
            default -> 0.0;
        };
        return Math.min(1.0, multiplier / Math.max(1, interval));
    }

    public static int anomaliteHostSamples() {
        return SPEC.isLoaded() ? ANOMALITE_HOST_SAMPLES.get() : 3;
    }

    public static int anomaliteGrowthChance() {
        return SPEC.isLoaded() ? ANOMALITE_GROWTH_CHANCE.get() : 20;
    }

    public static int anomaliteDecayChance() {
        return SPEC.isLoaded() ? ANOMALITE_DECAY_CHANCE.get() : 20;
    }

    /**
     * A crystal that already exists keeps eating at any band: the anomaly only decides how hard the
     * cap bites. Spawning is what the High+ gate guards.
     */
    public static long anomaliteDrain(long storedFe, int stage, FluxBand band) {
        if (storedFe <= 0 || stage < 0 || stage > 3) return 0;
        double percent = switch (stage) {
            case 0 -> SPEC.isLoaded() ? ANOMALITE_DRAIN_STAGE_0.get() : 0.02;
            case 1 -> SPEC.isLoaded() ? ANOMALITE_DRAIN_STAGE_1.get() : 0.04;
            case 2 -> SPEC.isLoaded() ? ANOMALITE_DRAIN_STAGE_2.get() : 0.07;
            default -> SPEC.isLoaded() ? ANOMALITE_DRAIN_STAGE_3.get() : 0.10;
        };
        int baseCap = switch (stage) {
            case 0 -> SPEC.isLoaded() ? ANOMALITE_CAP_STAGE_0.get() : 40;
            case 1 -> SPEC.isLoaded() ? ANOMALITE_CAP_STAGE_1.get() : 70;
            case 2 -> SPEC.isLoaded() ? ANOMALITE_CAP_STAGE_2.get() : 110;
            default -> SPEC.isLoaded() ? ANOMALITE_CAP_STAGE_3.get() : 160;
        };
        double capMultiplier = switch (band) {
            case CRITICAL -> SPEC.isLoaded() ? ANOMALITE_CRITICAL_DRAIN.get() : 2.0;
            case SINGULARITY -> SPEC.isLoaded() ? ANOMALITE_SINGULARITY_DRAIN.get() : 5.0;
            default -> 1.0;
        };
        long proportional = (long) Math.ceil(storedFe * (percent / 100.0));
        long cap = (long) Math.ceil(baseCap * capMultiplier);
        return Math.min(storedFe, Math.min(proportional, cap));
    }

    public static boolean fieldHud() {
        return !SPEC.isLoaded() || FIELD_HUD.get();
    }

    public static boolean mapRifts() {
        return !SPEC.isLoaded() || MAP_RIFTS.get();
    }

    public static MapOverlay mapOverlay() {
        return SPEC.isLoaded() ? MAP_OVERLAY.get() : MapOverlay.FIELD;
    }

    public static HudCorner fieldHudCorner() {
        return SPEC.isLoaded() ? FIELD_HUD_CORNER.get() : HudCorner.TOP_LEFT;
    }

    public static double fieldVisionDensity() {
        return SPEC.isLoaded() ? FIELD_VISION_DENSITY.get() : 1.0;
    }

    public static boolean chunkLoadPods() {
        return !SPEC.isLoaded() || CHUNK_LOAD_PODS.get();
    }

    public static int maxDoubles() {
        return SPEC.isLoaded() ? MAX_DOUBLES.get() : 4;
    }

    public static boolean structurelessMultiblocks() {
        return SPEC.isLoaded() && STRUCTURELESS_MULTIBLOCKS.get();
    }

    public static int simulatorMaxNesting() {
        return SPEC.isLoaded() ? SIMULATOR_MAX_NESTING.get() : SIMULATOR_MAX_NESTING.getDefault();
    }

    /** 0 for unlimited. */
    public static int zenoMaxExtraTicks() {
        return SPEC.isLoaded() ? ZENO_MAX_EXTRA_TICKS.get() : ZENO_MAX_EXTRA_TICKS.getDefault();
    }

    public static double zenoSlowedChance() {
        return SPEC.isLoaded() ? ZENO_SLOWED_CHANCE.get() : 0.125;
    }

    private static ModConfigSpec.ConfigValue<List<? extends Integer>> raritySharesFor(String band, List<Integer> shares) {
        return BUILDER.comment("Running hot pays: an observation in " + band + " flux lands on a common, uncommon, rare or very rare ore in these shares; each share is split evenly among the ores of its rarity.")
                .defineList("rarityShares" + Character.toUpperCase(band.charAt(0)) + band.substring(1), shares, () -> 0,
                        o -> o instanceof Integer i && i >= 0 && i <= 1000);
    }

    /** The shares of common, uncommon, rare and very rare ores an observation in {@code band} lands on. */
    public static List<? extends Integer> rarityShares(FluxBand band) {
        ModConfigSpec.ConfigValue<List<? extends Integer>> value = switch (band) {
            case LOW -> RARITY_SHARES_LOW;
            case MEDIUM -> RARITY_SHARES_MEDIUM;
            case HIGH -> RARITY_SHARES_HIGH;
            case CRITICAL -> RARITY_SHARES_CRITICAL;
            case SINGULARITY -> RARITY_SHARES_SINGULARITY;
        };
        List<? extends Integer> shares = SPEC.isLoaded() ? value.get() : value.getDefault();
        return shares.size() == 4 ? shares : value.getDefault();
    }

    /** How likely a step of Matter's history holds when a Chamber observes it in {@code band}, 0 to 1. */
    public static double stepHoldChance(FluxBand band) {
        return byBand(SPEC.isLoaded() ? STEP_HOLD_BY_BAND.get() : null, STEP_HOLD_BY_BAND.getDefault(), band) / 100.0;
    }

    public static int chamberFluxPerBand() {
        return SPEC.isLoaded() ? CHAMBER_FLUX_PER_BAND.get() : CHAMBER_FLUX_PER_BAND.getDefault();
    }

    public static double materialiserPremium() {
        return (SPEC.isLoaded() ? MATERIALISER_PREMIUM.get() : MATERIALISER_PREMIUM.getDefault()) / 100.0;
    }

    public static int materialiserFluxPerValue() {
        return SPEC.isLoaded() ? MATERIALISER_FLUX.get() : MATERIALISER_FLUX.getDefault();
    }

    public static CollapseSource collapseSource() {
        return SPEC.isLoaded() ? COLLAPSE_SOURCE.get() : CollapseSource.PACK_ORES;
    }

    private static ModConfigSpec.DoubleValue drainPercent(String key, double value) {
        return BUILDER.comment("Percent of currently stored host energy drained per tick at this growth stage.")
                .defineInRange(key, value, 0.0, 100.0);
    }

    private static ModConfigSpec.IntValue drainCap(String key, int value) {
        return BUILDER.comment("High-anomaly FE/t cap for this growth stage.")
                .defineInRange(key, value, 0, 1_000_000);
    }
}
