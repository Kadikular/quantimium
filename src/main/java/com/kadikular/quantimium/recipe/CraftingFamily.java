package com.kadikular.quantimium.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Crafting-table recipes matched against the crafter ingredient pool (local stacks + linked remotes).
 *
 * <p>The block is a processor rather than a literal crafting table: nine ingots in one input stack
 * can therefore satisfy the nine positions of an iron-block recipe. A synthetic 3×3 grid is still
 * assembled and passed to the recipe, preserving shaped recipe output and component behaviour.
 */
public final class CraftingFamily {

    /** A crafting table burns nothing, so shaping is billed as a flat convenience fee. */
    private static final int BASE_FE = 100;

    public static int energyCost() {
        return CraftEnergy.flat(BASE_FE);
    }

    private CraftingFamily() {}

    public static boolean supports(Recipe<?> recipe) {
        return recipe instanceof CraftingRecipe;
    }

    public static Optional<ResolvedCraft> match(Level level, HolderLookup.Provider registries,
                                                RecipeHolder<?> holder, IngredientPool pool) {
        if (!(holder.value() instanceof CraftingRecipe crafting)) return Optional.empty();
        if (pool.isEmpty()) return Optional.empty();

        try {
            Match match = buildPooledInput(crafting, pool);
            if (match == null) return Optional.empty();
            CraftingInput input = match.input();
            if (!crafting.matches(input, level)) return Optional.empty();
            ItemStack result = crafting.assemble(input);
            if (result.isEmpty()) return Optional.empty();

            List<ItemStack> outputs = new ArrayList<>();
            outputs.add(result.copy());
            NonNullList<ItemStack> remaining = crafting.getRemainingItems(input);
            for (ItemStack rem : remaining) {
                if (!rem.isEmpty()) outputs.add(rem.copy());
            }

            return Optional.of(new ResolvedCraft(holder.id().identifier(), match.withdrawals(), outputs, energyCost()));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static Match buildPooledInput(CraftingRecipe recipe, IngredientPool pool) {
        List<Optional<Ingredient>> ingredients = ingredientsOf(recipe);
        if (ingredients.isEmpty() || ingredients.size() > 9) return null;

        IngredientPool.Allocator alloc = pool.allocator();
        List<ItemStack> chosen = new ArrayList<>(ingredients.size());
        for (Optional<Ingredient> slot : ingredients) {
            if (slot.isEmpty() || slot.get().isEmpty()) {
                chosen.add(ItemStack.EMPTY);
                continue;
            }
            Ingredient ingredient = slot.get();

            ItemStack representative = ItemStack.EMPTY;
            for (int i = 0; i < alloc.size(); i++) {
                if (alloc.remaining(i) <= 0) continue;
                ItemStack offered = alloc.stack(i);
                if (!ingredient.test(offered)) continue;
                if (alloc.take(i, 1) == null) continue;
                representative = offered.copyWithCount(1);
                break;
            }
            if (representative.isEmpty()) return null;
            chosen.add(representative);
        }

        List<ItemStack> cells = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) cells.add(ItemStack.EMPTY);

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width > 3 || height > 3 || chosen.size() < width * height) return null;
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    cells.set(row * 3 + col, chosen.get(row * width + col));
                }
            }
        } else {
            int cell = 0;
            for (ItemStack stack : chosen) {
                if (!stack.isEmpty()) cells.set(cell++, stack);
            }
        }

        return new Match(CraftingInput.of(3, 3, cells), alloc.snapshot());
    }

    /**
     * The recipe in fixed terms, with any ingredient it gives back as itself as a tool: it asks the
     * recipe what one craft leaves, from the first item each ingredient accepts. One that comes back
     * more worn, as AE2's cutting knives do, wears; one that comes back as it was lasts forever.
     */
    public static Optional<RecipeShape> describe(RecipeHolder<?> holder, HolderLookup.Provider registries, int baseFe) {
        if (!(holder.value() instanceof CraftingRecipe crafting)) return Optional.empty();
        try {
            ItemStack result = RecipeCompat.result(crafting, registries);
            if (result == null || result.isEmpty()) return Optional.empty();
            List<Optional<Ingredient>> ingredients = ingredientsOf(crafting);
            if (ingredients.isEmpty() || ingredients.size() > 9) return Optional.empty();
            List<ItemStack> cells = new ArrayList<>(ingredients.size());
            for (Optional<Ingredient> slot : ingredients) {
                List<ItemStack> options = slot.filter(i -> !i.isEmpty()).map(RecipeCompat::stacks).orElse(List.of());
                cells.add(options.isEmpty() ? ItemStack.EMPTY : options.getFirst().copyWithCount(1));
            }
            List<ItemStack> left = remaining(crafting, cells);
            List<RecipeShape.Input> inputs = new ArrayList<>();
            List<Ingredient> tools = new ArrayList<>();
            List<Ingredient> wears = new ArrayList<>();
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack cell = cells.get(i);
                if (cell.isEmpty()) continue;
                Ingredient ingredient = ingredients.get(i).get();
                ItemStack back = i < left.size() ? left.get(i) : ItemStack.EMPTY;
                if (!back.isEmpty() && back.is(cell.getItem())) {
                    tools.add(ingredient);
                    if (back.getDamageValue() > cell.getDamageValue()) wears.add(ingredient);
                } else {
                    inputs.add(new RecipeShape.Input(ingredient, 1));
                }
            }
            if (inputs.isEmpty() && tools.isEmpty()) return Optional.empty();
            return Optional.of(new RecipeShape(holder.id().identifier(), inputs, List.of(result), baseFe, tools, wears));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** What one craft from {@code cells} (the recipe's own grid, already trimmed) leaves in each. Nothing if it can't say. */
    private static List<ItemStack> remaining(CraftingRecipe recipe, List<ItemStack> cells) {
        try {
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : cells.size();
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
            CraftingInput input = CraftingInput.of(width, height, cells);
            if (input.size() != cells.size()) return List.of();
            return recipe.getRemainingItems(input);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** The recipe's ingredients in grid order, empty cells included for shaped recipes. */
    private static List<Optional<Ingredient>> ingredientsOf(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) return shaped.getIngredients();
        List<Optional<Ingredient>> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) ingredients.add(Optional.of(ingredient));
        return ingredients;
    }

    private record Match(CraftingInput input, List<IngredientPool.Withdrawal> withdrawals) {}
}
