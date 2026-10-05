package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.item.FoldedTesseractItem;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.kadikular.quantimium.recipe.adapter.RecipeAdapter;
import com.kadikular.quantimium.recipe.adapter.RecipeAdapterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a catalyst item (usually a machine block) to the {@link RecipeType}s it can drive.
 *
 * <p>Explicit profiles cover the common vanilla workstations; everything else falls back to matching
 * the block/item registry path against registered recipe-type paths — the same heuristic EMI plugins
 * use informally when associating workstations with categories.
 *
 * <p>Whether a catalyst is allowed at all is datapack-driven: it must be on
 * {@link ModTags#QUANTUM_CRAFTER_WHITELIST} and not on {@link ModTags#QUANTUM_CRAFTER_BLACKLIST}.
 */
public final class CatalystResolver {

    private static final Map<Item, List<RecipeType<?>>> CACHE = new ConcurrentHashMap<>();

    private CatalystResolver() {}

    /**
     * Basic-tier (and pack) gate: whitelist required, blacklist always wins. Packs append to the
     * whitelist tag; other mods can ship their own tag and ask packs to {@code #include} it later.
     */
    public static boolean isCatalystAllowed(ItemStack catalyst) {
        if (catalyst.isEmpty()) return false;
        // A folded multiblock is the whole structure, so the structureless switch and the whitelist,
        // which is about loose blocks, don't apply. The blacklist still does, to the machine inside.
        if (catalyst.getItem() instanceof FoldedTesseractItem) {
            return FoldedTesseractItem.adapter(catalyst).isPresent()
                    && !represented(catalyst).is(ModTags.QUANTUM_CRAFTER_BLACKLIST);
        }
        if (catalyst.is(ModTags.QUANTUM_CRAFTER_BLACKLIST)) return false;
        // A multiblock's controller without its structure, only if the pack allows it.
        if (!Config.structurelessMultiblocks() && catalyst.is(ModTags.MULTIBLOCK_CONTROLLERS)) return false;
        return catalyst.is(ModTags.QUANTUM_CRAFTER_WHITELIST);
    }

    /**
     * The flux band the Quantum Crafter needs to use {@code catalyst} (running hot pays): by its
     * family, from vanilla workstations at Low to the Fusion Reactor at Singularity. A multiblock's
     * controller, standing in for the whole structure, needs Critical until folding comes (0.3).
     */
    public static FluxBand requiredBand(ItemStack catalyst) {
        if (catalyst.getItem() instanceof FoldedTesseractItem) {
            return Config.crafterCatalystBand(Math.max(MULTIBLOCK_FAMILY, family(represented(catalyst))));
        }
        return Config.crafterCatalystBand(family(catalyst));
    }

    /** What a catalyst stands for: the machine folded in a Folded Tesseract, or the catalyst itself. */
    public static ItemStack represented(ItemStack catalyst) {
        FoldedStructure folded = FoldedTesseractItem.folded(catalyst);
        if (folded == null) return catalyst;
        return folded.adapterOrEmpty().map(adapter -> adapter.catalyst(folded.state())).orElse(ItemStack.EMPTY);
    }

    private static final int MULTIBLOCK_FAMILY = 3;

    /** 0 basic, 1 advanced, 2 industrial, 3 a multiblock controller, 4 singularity: the highest that applies. */
    private static int family(ItemStack catalyst) {
        if (catalyst.is(ModTags.SINGULARITY_CRAFTER_CATALYSTS)) return 4;
        if (catalyst.is(ModTags.MULTIBLOCK_CONTROLLERS)) return 3;
        if (catalyst.is(ModTags.INDUSTRIAL_CRAFTER_CATALYSTS)) return 2;
        if (catalyst.is(ModTags.ADVANCED_CRAFTER_CATALYSTS)) return 1;
        return 0;
    }

    /** Forgets what every machine runs: recipe adapters were reloaded. */
    public static void clearCache() {
        CACHE.clear();
    }

    public static List<RecipeType<?>> resolve(ItemStack catalyst) {
        if (catalyst.isEmpty()) return List.of();
        // Folded Tesseracts are one item for every machine, so they can't share the per-item cache.
        if (catalyst.getItem() instanceof FoldedTesseractItem) {
            FoldedStructure folded = FoldedTesseractItem.folded(catalyst);
            if (folded == null) return List.of();
            return folded.adapterOrEmpty().map(adapter -> adapter.recipeTypes(folded.state())).orElse(List.of());
        }
        return CACHE.computeIfAbsent(catalyst.getItem(), item -> resolveUncached(new ItemStack(item)));
    }

    private static List<RecipeType<?>> resolveUncached(ItemStack catalyst) {
        Set<RecipeType<?>> types = new LinkedHashSet<>();

        // Explicit vanilla workstations.
        if (catalyst.is(Items.CRAFTING_TABLE)) {
            types.add(RecipeType.CRAFTING);
            return List.copyOf(types);
        }
        if (catalyst.is(Items.FURNACE)) {
            types.add(RecipeType.SMELTING);
            return List.copyOf(types);
        }
        if (catalyst.is(Items.BLAST_FURNACE)) {
            types.add(RecipeType.BLASTING);
            return List.copyOf(types);
        }
        if (catalyst.is(Items.SMOKER)) {
            types.add(RecipeType.SMOKING);
            return List.copyOf(types);
        }
        if (catalyst.is(Items.STONECUTTER)) {
            types.add(RecipeType.STONECUTTING);
            return List.copyOf(types);
        }
        if (catalyst.is(Items.CAMPFIRE) || catalyst.is(Items.SOUL_CAMPFIRE)) {
            types.add(RecipeType.CAMPFIRE_COOKING);
            return List.copyOf(types);
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(catalyst.getItem());
        String itemPath = itemId.getPath().toLowerCase();

        // Machines a recipe adapter names, for those whose names don't say what they run.
        for (RecipeAdapter adapter : RecipeAdapterRegistry.INSTANCE.all()) {
            if (!adapter.catalysts().contains(itemId)) continue;
            for (Identifier type : adapter.recipeTypes()) addType(types, type.toString());
            for (Identifier type : adapter.catalystAlso()) addType(types, type.toString());
        }
        if (!types.isEmpty()) return List.copyOf(types);

        Block block = catalyst.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
        Identifier blockId = block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
        String blockPath = blockId == null ? itemPath : blockId.getPath().toLowerCase();

        addMekanismTypes(itemId, blockId, blockPath, itemPath, types);

        // MI electric furnace also does vanilla smelting.
        if (blockPath.contains("furnace") && !blockPath.contains("blast")
                && itemId.getNamespace().equals("modern_industrialization")) {
            types.add(RecipeType.SMELTING);
        }

        // By name: the machine's own mod's types, and vanilla's. Another mod's type that shares a word
        // (an Energized Power charger, an AE2 charger) isn't the machine's.
        String namespace = itemId.getNamespace();
        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            Identifier typeId = BuiltInRegistries.RECIPE_TYPE.getKey(type);
            if (typeId == null) continue;
            if (!typeId.getNamespace().equals(namespace) && !typeId.getNamespace().equals("minecraft")) continue;
            if (matches(blockPath, itemPath, typeId.getPath().toLowerCase())) {
                types.add(type);
            }
        }

        return List.copyOf(types);
    }

    /**
     * Mekanism machine ids do not share a string with their recipe types ({@code enrichment_chamber}
     * vs {@code enriching}). Factories do ({@code basic_enriching_factory}), but the basic blocks
     * need an explicit map. Energized smelters also read vanilla smelting.
     */
    private static void addMekanismTypes(Identifier itemId, Identifier blockId,
                                         String blockPath, String itemPath, Set<RecipeType<?>> types) {
        if (!isMekanism(itemId) && !isMekanism(blockId)) return;
        String path = blockPath + " " + itemPath;
        if (path.contains("enrich")) addType(types, "mekanism:enriching");
        if (path.contains("crush")) addType(types, "mekanism:crushing");
        if (path.contains("combin")) addType(types, "mekanism:combining");
        if (path.contains("energized_smelter") || path.contains("smelting")) {
            types.add(RecipeType.SMELTING);
            addType(types, "mekanism:smelting");
        }
    }

    private static boolean isMekanism(Identifier id) {
        return id != null && id.getNamespace().equals("mekanism");
    }

    private static void addType(Set<RecipeType<?>> types, String id) {
        Identifier loc = Identifier.parse(id);
        if (!BuiltInRegistries.RECIPE_TYPE.containsKey(loc)) return;
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(loc);
        if (type != null) types.add(type);
    }

    private static boolean matches(String blockPath, String itemPath, String typePath) {
        if (blockPath.contains("blast_furnace") || itemPath.contains("blast_furnace")) {
            return typePath.contains("blast") && !typePath.equals("crafting");
        }
        if (blockPath.contains("smoker") || itemPath.contains("smoker")) {
            return typePath.contains("smoke");
        }
        if (blockPath.contains("alloy") || itemPath.contains("alloy")) {
            return typePath.contains("alloy");
        }
        if (blockPath.contains("furnace") || itemPath.contains("furnace")) {
            // Prefer smelting / mi furnace; avoid matching unrelated "furnace" substrings in crafting.
            return typePath.contains("smelt") || typePath.equals("furnace");
        }
        if (blockPath.contains("cutter") || itemPath.contains("cutter")
                || blockPath.contains("stonecutter") || itemPath.contains("stonecutter")) {
            return typePath.contains("cut");
        }
        // Strip common machine prefixes so electric_macerator → macerator.
        String normalised = stripMachinePrefix(blockPath);
        String itemNorm = stripMachinePrefix(itemPath);
        return typePath.equals(normalised) || typePath.equals(itemNorm)
                || normalised.contains(typePath) || typePath.contains(normalised)
                || itemNorm.contains(typePath) || typePath.contains(itemNorm);
    }

    private static String stripMachinePrefix(String path) {
        String[] prefixes = {
                "electric_", "steam_", "bronze_", "steel_", "digital_", "advanced_",
                "turbo_", "highly_advanced_", "lv_", "mv_", "hv_", "ev_", "superconductor_"
        };
        String result = path;
        for (String prefix : prefixes) {
            if (result.startsWith(prefix)) {
                result = result.substring(prefix.length());
                break;
            }
        }
        return result;
    }

    /** Whether this catalyst is treated as a crafting-table style 3×3 grid matcher. */
    public static boolean isCraftingCatalyst(ItemStack catalyst) {
        if (catalyst.isEmpty()) return false;
        if (catalyst.is(Items.CRAFTING_TABLE)) return true;
        Identifier id = BuiltInRegistries.ITEM.getKey(catalyst.getItem());
        String path = id.getPath().toLowerCase();
        return path.contains("crafting_table") || path.equals("crafter");
    }
}
