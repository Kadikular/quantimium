package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.fold.FoundryFoldAdapter;
import com.kadikular.quantimium.item.FoldedTesseractItem;
import com.kadikular.quantimium.recipe.foundry.FoundryIngredient;
import com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Quantum Foundry recipes, for a Folded Tesseract holding a Foundry. A recipe needs as many arms as it
 * says, so the arms folded in decide which recipes show. Priced at the energy the Foundry itself
 * would spend over the recipe's duration, taxed like any instant craft.
 */
public final class FoundryFamily {

    private FoundryFamily() {}

    /** Arms a catalyst brings to Foundry recipes: those folded with a Foundry, else none. */
    public static int arms(ItemStack catalyst) {
        FoldedStructure folded = FoldedTesseractItem.folded(catalyst);
        if (folded == null || !folded.adapter().equals(FoundryFoldAdapter.ID)) return 0;
        return FoundryFoldAdapter.arms(folded.state());
    }

    public static boolean supports(Recipe<?> recipe) {
        return recipe instanceof QuantumFoundryRecipe;
    }

    public static Optional<ResolvedCraft> match(RecipeHolder<?> holder, IngredientPool pool, int arms) {
        if (!(holder.value() instanceof QuantumFoundryRecipe recipe) || recipe.minimumPillars() > arms) {
            return Optional.empty();
        }
        IngredientPool.Allocator alloc = pool.allocator();
        List<IngredientPool.Withdrawal> withdrawals = new ArrayList<>();
        for (FoundryIngredient ingredient : recipe.ingredients()) {
            List<IngredientPool.Withdrawal> taken = alloc.takeMatching(ingredient.ingredient(), ingredient.count());
            if (taken == null) return Optional.empty();
            withdrawals.addAll(taken);
        }
        return Optional.of(new ResolvedCraft(holder.id().identifier(), withdrawals, List.of(recipe.result()),
                energy(recipe)));
    }

    public static Optional<RecipeShape> describe(RecipeHolder<?> holder, int arms) {
        if (!(holder.value() instanceof QuantumFoundryRecipe recipe) || recipe.minimumPillars() > arms) {
            return Optional.empty();
        }
        List<RecipeShape.Input> inputs = new ArrayList<>();
        for (FoundryIngredient ingredient : recipe.ingredients()) {
            inputs.add(new RecipeShape.Input(ingredient.ingredient(), ingredient.count()));
        }
        return Optional.of(new RecipeShape(holder.id().identifier(), inputs, List.of(recipe.result()), energy(recipe)));
    }

    private static int energy(QuantumFoundryRecipe recipe) {
        return CraftEnergy.flat((long) recipe.duration() * recipe.fePerTick());
    }
}
