package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.recipe.adapter.ConfiguredRecipeFamily;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every recipe a catalyst can run, as {@link RecipeShape}s: the Crafter's preview, turned round. The
 * preview asks "what can these ingredients make?"; this asks "what could this machine ever make?",
 * which is what an ME network needs to plan with.
 *
 * <p>The same families answer, in the same order, so a recipe is described the way the Crafter would
 * price and run it. Recipes a family cannot put in fixed terms (fluids, tools that are not used up,
 * chance inputs) are left out.
 */
public final class RecipeShapes {

    /** Base FE for a crafting-table craft, as {@link CraftingFamily} prices it. */
    private static final int CRAFTING_BASE_FE = 100;

    private RecipeShapes() {}

    public static List<RecipeShape> forCatalyst(Level level, ItemStack catalyst) {
        List<RecipeShape> shapes = new ArrayList<>();
        if (level == null || catalyst.isEmpty() || !CatalystResolver.isCatalystAllowed(catalyst)) return shapes;
        RecipeIndex index = RecipeIndex.shared();
        index.bind(level);
        HolderLookup.Provider registries = level.registryAccess();
        boolean crafting = CatalystResolver.isCraftingCatalyst(catalyst);
        int foundryArms = FoundryFamily.arms(catalyst);
        for (RecipeType<?> type : CatalystResolver.resolve(catalyst)) {
            for (RecipeHolder<?> holder : index.recipesOf(type)) {
                if (FoundryFamily.supports(holder.value())) {
                    FoundryFamily.describe(holder, foundryArms).ifPresent(shapes::add);
                    continue;
                }
                describe(holder, registries, crafting).ifPresent(shapes::add);
            }
        }
        return shapes;
    }

    private static Optional<RecipeShape> describe(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                                  boolean crafting) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof CraftingRecipe) {
            if (!crafting || recipe.isSpecial()) return Optional.empty();
            return CraftingFamily.describe(holder, registries, CraftEnergy.flat(CRAFTING_BASE_FE));
        }
        if (VanillaSingleInputFamily.supports(recipe)) return VanillaSingleInputFamily.describe(holder, registries);
        if (MiMachineFamily.supports(recipe)) return MiMachineFamily.describe(holder);
        Optional<RecipeShape> configured = ConfiguredRecipeFamily.describe(registries, holder);
        if (configured.isPresent()) return configured;
        return GenericItemFamily.describe(holder, registries);
    }
}
