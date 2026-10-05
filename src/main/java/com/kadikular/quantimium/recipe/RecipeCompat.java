package com.kadikular.quantimium.recipe;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * What {@code Recipe#getIngredients}, {@code Recipe#getResultItem} and {@code Ingredient#getItems}
 * used to answer, from the ways 26.1 still exposes them: the placement info, the recipe display and the ingredient's items.
 */
public final class RecipeCompat {
    private RecipeCompat() {}

    /** The recipe's non-empty ingredients; empty when the recipe cannot be placed in a grid. */
    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        return recipe.placementInfo().ingredients();
    }

    /** The first stack any of the recipe's displays shows as its result. */
    public static ItemStack result(Recipe<?> recipe, HolderLookup.Provider registries) {
        ContextMap context = new ContextMap.Builder()
                .withParameter(SlotDisplayContext.REGISTRIES, registries)
                .create(SlotDisplayContext.CONTEXT);
        for (RecipeDisplay display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(context);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** One stack of every item the ingredient accepts. */
    @SuppressWarnings("deprecation")
    public static List<ItemStack> stacks(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            // A custom ingredient can want components, as Productive Bees' honeycombs want their kind:
            // its display shows the stacks it really accepts.
            try {
                ContextMap context = new ContextMap.Builder().create(SlotDisplayContext.CONTEXT);
                List<ItemStack> shown = ingredient.display().resolveForStacks(context).stream()
                        .filter(stack -> !stack.isEmpty() && ingredient.test(stack))
                        .map(stack -> stack.copyWithCount(1)).toList();
                if (!shown.isEmpty()) return shown;
            } catch (RuntimeException ignored) {
                // its items, then
            }
        }
        return ingredient.items().map(holder -> new ItemStack(holder.value())).toList();
    }
}
