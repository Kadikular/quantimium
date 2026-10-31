package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.compat.mi.MiMachineFamily;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Last-resort matcher for item-only recipes that expose {@link Recipe#getIngredients()} and
 * {@link Recipe#getResultItem(HolderLookup.Provider)}. Covers many small-mod machines without a
 * dedicated family; specialised families and JSON adapters always win first.
 */
public final class GenericItemFamily {

    /** Convenience fee when the recipe declares no cook time of its own. */
    private static final int DEFAULT_BASE_FE = 200;

    private GenericItemFamily() {}

    public static boolean supports(Recipe<?> recipe) {
        if (recipe == null) return false;
        // Leave known shapes to their own families even if they also expose ingredients.
        if (recipe instanceof CraftingRecipe) return false;
        if (VanillaSingleInputFamily.supports(recipe)) return false;
        if (MiMachineFamily.supports(recipe)) return false;
        try {
            List<Ingredient> ingredients = RecipeCompat.ingredients(recipe);
            if (ingredients == null || ingredients.isEmpty()) return false;
            for (Ingredient ingredient : ingredients) {
                if (ingredient != null && !ingredient.isEmpty()) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Optional<ResolvedCraft> match(HolderLookup.Provider registries, RecipeHolder<?> holder,
                                                IngredientPool pool) {
        Recipe<?> recipe = holder.value();
        if (!supports(recipe) || pool.isEmpty()) return Optional.empty();

        try {
            FluidParts.Parts fluids = FluidParts.of(recipe);
            if (!fluids.inputs().isEmpty() && !pool.hasFluids()) return Optional.empty();
            ItemStack result = RecipeCompat.result(recipe, registries);
            List<ItemStack> outputs = result == null || result.isEmpty() ? List.of() : List.of(result.copy());
            if (outputs.isEmpty() && fluids.outputs().isEmpty()) return Optional.empty();

            IngredientPool.Allocator alloc = pool.allocator();
            List<IngredientPool.Withdrawal> withdrawals = new ArrayList<>();
            for (Ingredient ingredient : RecipeCompat.ingredients(recipe)) {
                if (ingredient == null || ingredient.isEmpty()) continue;
                List<IngredientPool.Withdrawal> taken = alloc.takeMatching(ingredient, 1);
                if (taken == null) return Optional.empty();
                withdrawals.addAll(taken);
            }
            if (withdrawals.isEmpty()) return Optional.empty();
            // Its fluids, from the Crafter's tanks or a linked one's.
            for (FluidParts.Input fluid : fluids.inputs()) {
                List<IngredientPool.Withdrawal> taken = alloc.takeFluid(fluid);
                if (taken == null) return Optional.empty();
                withdrawals.addAll(taken);
            }

            int cost = energyCost(recipe);
            return Optional.of(new ResolvedCraft(holder.id().identifier(), withdrawals, outputs, cost)
                    .withFluidOutputs(fluids.outputs()));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** Every non-empty ingredient once, and the result. Also serves crafting recipes, shape ignored. */
    public static Optional<RecipeShape> describe(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                                 int baseFe) {
        Recipe<?> recipe = holder.value();
        try {
            FluidParts.Parts fluids = FluidParts.of(recipe);
            ItemStack result = RecipeCompat.result(recipe, registries);
            List<ItemStack> outputs = result == null || result.isEmpty() ? List.of() : List.of(result);
            if (outputs.isEmpty() && fluids.outputs().isEmpty()) return Optional.empty();
            List<RecipeShape.Input> inputs = new ArrayList<>();
            for (Ingredient ingredient : RecipeCompat.ingredients(recipe)) {
                if (ingredient == null || ingredient.isEmpty()) continue;
                inputs.add(new RecipeShape.Input(ingredient, 1));
            }
            if (inputs.isEmpty() && fluids.inputs().isEmpty()) return Optional.empty();
            return Optional.of(new RecipeShape(holder.id().identifier(), inputs, outputs, baseFe).withFluids(fluids));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public static Optional<RecipeShape> describe(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        if (!supports(holder.value())) return Optional.empty();
        return describe(holder, registries, energyCost(holder.value()));
    }

    private static int energyCost(Recipe<?> recipe) {
        try {
            // AbstractCookingRecipe and friends; duck-typed so we do not re-list every subclass.
            Object ticks = recipe.getClass().getMethod("cookingTime").invoke(recipe);
            if (ticks instanceof Number number && number.intValue() > 0) {
                return CraftEnergy.fromBurnTicks(number.intValue());
            }
        } catch (Throwable ignored) {
        }
        return CraftEnergy.flat(DEFAULT_BASE_FE);
    }
}
