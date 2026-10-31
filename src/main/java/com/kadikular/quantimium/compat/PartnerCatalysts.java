package com.kadikular.quantimium.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Other mods' machines whose names don't say which recipe types they run, as Quantum Crafter catalysts. The
 * {@link com.kadikular.quantimium.recipe.CatalystResolver} asks here after the recipe adapters and before matching
 * by name. Each mod is found by its id alone, so none need be on the classpath.
 */
public final class PartnerCatalysts {

    /** Mods whose plain furnaces do vanilla smelting: MI's electric furnace, FTB Industrial Contraptions' powered and iron ones. */
    private static final Set<String> SMELTING_FURNACE_MODS = Set.of("modern_industrialization", "ftbic");

    private PartnerCatalysts() {}

    /**
     * Adds the recipe types the machine with {@code itemId} (placing {@code blockId}) runs, by what its mod is known
     * to call them. {@code blockPath} and {@code itemPath} are its ids' paths, lower case.
     */
    public static void addTypes(Identifier itemId, @Nullable Identifier blockId, String blockPath, String itemPath,
                                Set<RecipeType<?>> types) {
        addMekanismTypes(itemId, blockId, blockPath, itemPath, types);
        if (blockPath.contains("furnace") && !blockPath.contains("blast") && SMELTING_FURNACE_MODS.contains(itemId.getNamespace())) {
            types.add(RecipeType.SMELTING);
        }
    }

    /**
     * Mekanism machine ids do not share a string with their recipe types ({@code enrichment_chamber}
     * vs {@code enriching}). Factories do ({@code basic_enriching_factory}), but the basic blocks
     * need an explicit map. Energized smelters also read vanilla smelting.
     */
    private static void addMekanismTypes(Identifier itemId, @Nullable Identifier blockId,
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

    private static boolean isMekanism(@Nullable Identifier id) {
        return id != null && id.getNamespace().equals("mekanism");
    }

    private static void addType(Set<RecipeType<?>> types, String id) {
        Identifier loc = Identifier.parse(id);
        if (!BuiltInRegistries.RECIPE_TYPE.containsKey(loc)) return;
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(loc);
        if (type != null) types.add(type);
    }
}
