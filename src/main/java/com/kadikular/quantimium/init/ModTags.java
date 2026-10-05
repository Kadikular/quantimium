package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /**
     * Catalysts the Quantum Crafter may use. Empty / missing means nothing is allowed — Basic tier
     * ships a vanilla workstation set; packs and other mods add via datapack tag append.
     */
    public static final TagKey<Item> QUANTUM_CRAFTER_WHITELIST = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_crafter_whitelist"));

    /**
     * Hard deny, even if also present on the whitelist. Safety net for broken or intentionally
     * banned catalysts / ingredients (e.g. MI forge hammer).
     */
    /** Multiblock controllers: catalysts only with their structure, unless the config allows otherwise. */
    public static final TagKey<Item> MULTIBLOCK_CONTROLLERS = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(Quantimium.MODID, "multiblock_controllers"));

    public static final TagKey<Item> QUANTUM_CRAFTER_BLACKLIST = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_crafter_blacklist"));

    /** Vanilla/non-electric catalysts understood by the Basic Quantum Crafter. */
    public static final TagKey<Item> BASIC_CRAFTER_CATALYSTS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_crafter_catalysts/basic"));

    /** The other catalyst tiers, each needing a hotter field (Config.crafterCatalystBand). */
    public static final TagKey<Item> ADVANCED_CRAFTER_CATALYSTS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_crafter_catalysts/advanced"));
    public static final TagKey<Item> INDUSTRIAL_CRAFTER_CATALYSTS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_crafter_catalysts/industrial"));
    public static final TagKey<Item> SINGULARITY_CRAFTER_CATALYSTS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_crafter_catalysts/singularity"));

    /** Convention tag every tech mod populates, so any of their wrenches reveals our port arrows. */
    public static final TagKey<Item> TOOLS_WRENCH = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/wrench"));

    /** Stone-backed blocks Unrealised Ore may imitate at Y >= 0. */
    public static final TagKey<Block> UNREALISED_ORE_FACADES_STONE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "unrealised_ore_facades/stone"));

    /** Deepslate-backed blocks Unrealised Ore may imitate below Y=0. */
    public static final TagKey<Block> UNREALISED_ORE_FACADES_DEEPSLATE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "unrealised_ore_facades/deepslate"));

    /** Matter that remains visible and interactive inside the Mirror Phase. */
    public static final TagKey<Block> ANOMALITE = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(Quantimium.MODID, "anomalite"));

    /** Blocks a phased player can still use from the mirror: doors, gates, buttons, levers. They act on the real world. */
    public static final TagKey<Block> MIRROR_USABLE = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_usable"));

    /** Ambient phase-only flora: hidden, replaceable, and never harvested. */
    public static final TagKey<Block> MIRROR_FLORA = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_flora"));

    /**
     * Blocks a Zeno field never gives extra random ticks: their tick is upkeep, not growth, and costly.
     * Farmland's looks nine blocks across for water, and grown wheat on it needs none of it.
     */
    public static final TagKey<Block> ZENO_IGNORED = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(Quantimium.MODID, "zeno_ignored"));

    /** Blocks a Fold Chamber refuses to fold, on top of anything unbreakable. For pack makers. */
    public static final TagKey<Block> UNFOLDABLE = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(Quantimium.MODID, "unfoldable"));

    /** Blocks a Zeno field gives only a share of its extra ticks (Config slowedTickChance): spreading, mostly. */
    public static final TagKey<Block> ZENO_SLOWED = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(Quantimium.MODID, "zeno_slowed"));

    private ModTags() {}
}
