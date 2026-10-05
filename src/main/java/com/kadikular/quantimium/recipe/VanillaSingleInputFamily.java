package com.kadikular.quantimium.recipe;

import net.minecraft.world.item.crafting.Ingredient;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Smelting / blasting / smoking / campfire / stonecutting via {@link SingleRecipeInput}. */
public final class VanillaSingleInputFamily {

    /** Cutting a block has no fuel cost in the world, so it is priced as a flat convenience fee. */
    private static final int STONECUTTING_BASE_FE = 100;

    private VanillaSingleInputFamily() {}

    public static boolean supports(Recipe<?> recipe) {
        return recipe instanceof AbstractCookingRecipe || recipe instanceof StonecutterRecipe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<ResolvedCraft> match(Level level, HolderLookup.Provider registries,
                                            RecipeHolder<?> holder, IngredientPool pool) {
        Recipe<?> recipe = holder.value();
        if (!supports(recipe)) return List.of();

        List<ResolvedCraft> results = new ArrayList<>();
        Recipe raw = recipe;

        // Prefer larger stacks first so putIfAbsent in the preview keeps the best batch source.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < pool.offers().size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> pool.offers().get(i).stack().getCount()).reversed());

        for (int index : order) {
            IngredientPool.Offer offer = pool.offers().get(index);
            ItemStack stack = offer.stack();
            if (stack.isEmpty()) continue;

            SingleRecipeInput input = new SingleRecipeInput(stack);
            try {
                if (!raw.matches(input, level)) continue;
                ItemStack out = raw.assemble(input);
                if (out.isEmpty()) continue;

                results.add(new ResolvedCraft(holder.id().identifier(),
                        List.of(new IngredientPool.Withdrawal(offer.provenance(), 1, stack.getItem())),
                        List.of(out.copy()), energyCost(recipe)));
            } catch (Throwable ignored) {
                // Mod recipes that reject SingleRecipeInput.
            }
        }
        return results;
    }

    /** The recipe in the abstract: its one ingredient and its result. */
    public static Optional<RecipeShape> describe(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        if (!supports(recipe)) return Optional.empty();
        try {
            if (!(recipe instanceof SingleItemRecipe single)) return Optional.empty();
            Ingredient ingredient = single.input();
            if (ingredient.isEmpty()) return Optional.empty();
            // A single-input recipe always yields the same stack, so any accepted item shows it.
            ItemStack sample = ingredient.items().findFirst().map(item -> new ItemStack(item.value())).orElse(ItemStack.EMPTY);
            ItemStack result = sample.isEmpty() ? ItemStack.EMPTY : single.assemble(new SingleRecipeInput(sample));
            if (result.isEmpty()) return Optional.empty();
            return Optional.of(new RecipeShape(holder.id().identifier(), List.of(new RecipeShape.Input(ingredient, 1)),
                    List.of(result), energyCost(recipe)));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public static int energyCost(Recipe<?> recipe) {
        if (recipe instanceof AbstractCookingRecipe cooking) {
            return CraftEnergy.fromBurnTicks(Math.max(1, cooking.cookingTime()));
        }
        return CraftEnergy.flat(STONECUTTING_BASE_FE);
    }
}
