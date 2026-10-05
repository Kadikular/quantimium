package com.kadikular.quantimium.reactor;

import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.recipe.RecipeShape;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Reactor's recipes as a graph the counter can walk quickly: every distinct ingredient once, every
 * recipe as indices into them, and an index from each item to the ingredients that might take it.
 *
 * <p>Built once whenever a catalyst changes and never changed after, so a recount can read it from
 * another thread.
 */
public final class ReactorGraph {

    /**
     * One recipe: its ingredients (by index, merged when one appears twice) and what it makes. A
     * count of 0 is a tool, such as a press: it must be there, and is never used up.
     */
    record Recipe(RecipeShape shape, int bay, int[] ingredients, long[] counts, ItemResource[] outputs,
                  long[] yields) {}

    final List<Recipe> recipes;
    final List<Ingredient> ingredients;
    /** For each ingredient, the recipes that use it. */
    final int[][] users;
    /** The ingredients that might take each item: the counter still tests each one against the stack. */
    final Map<Item, int[]> ingredientsByItem;

    private ReactorGraph(List<Recipe> recipes, List<Ingredient> ingredients, int[][] users,
                         Map<Item, int[]> ingredientsByItem) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.users = users;
        this.ingredientsByItem = ingredientsByItem;
    }

    public int size() {
        return recipes.size();
    }

    private static int index(Ingredient ingredient, Map<Ingredient, Integer> byValue, Map<Ingredient, Integer> byIdentity,
                             List<Ingredient> ingredients, List<List<Integer>> users) {
        Integer index = byIdentity.get(ingredient);
        if (index == null) {
            index = byValue.computeIfAbsent(ingredient, key -> {
                ingredients.add(key);
                users.add(new ArrayList<>());
                return ingredients.size() - 1;
            });
            byIdentity.put(ingredient, index);
        }
        return index;
    }

    static ReactorGraph build(List<ReactorRecipes.Producer> producers) {
        // The same Ingredient object is shared by every recipe built from one source, and value equality
        // is cheap where it isn't; either way an ingredient is tested once per item, not once per use.
        Map<Ingredient, Integer> ingredientIndex = new HashMap<>();
        Map<Ingredient, Integer> byIdentity = new IdentityHashMap<>();
        List<Ingredient> ingredients = new ArrayList<>();
        List<Recipe> recipes = new ArrayList<>(producers.size());
        List<List<Integer>> users = new ArrayList<>();
        for (ReactorRecipes.Producer producer : producers) {
            RecipeShape shape = producer.shape();
            Map<Integer, Long> merged = new java.util.LinkedHashMap<>();
            for (RecipeShape.Input input : shape.inputs()) {
                merged.merge(index(input.ingredient(), ingredientIndex, byIdentity, ingredients, users),
                        (long) input.count(), Long::sum);
            }
            for (Ingredient tool : shape.tools()) {
                merged.putIfAbsent(index(tool, ingredientIndex, byIdentity, ingredients, users), 0L);
            }
            int[] used = new int[merged.size()];
            long[] counts = new long[merged.size()];
            int at = 0;
            for (Map.Entry<Integer, Long> entry : merged.entrySet()) {
                used[at] = entry.getKey();
                counts[at++] = entry.getValue();
            }
            Map<ItemResource, Long> made = new java.util.LinkedHashMap<>();
            for (ItemStack output : shape.outputs()) {
                if (!output.isEmpty()) made.merge(ItemResource.of(output), (long) output.getCount(), Long::sum);
            }
            ItemResource[] outputs = made.keySet().toArray(new ItemResource[0]);
            long[] yields = made.values().stream().mapToLong(Long::longValue).toArray();
            int recipeIndex = recipes.size();
            recipes.add(new Recipe(shape, producer.bay(), used, counts, outputs, yields));
            for (int ingredient : used) users.get(ingredient).add(recipeIndex);
        }

        Map<Item, List<Integer>> byItem = new HashMap<>();
        for (int i = 0; i < ingredients.size(); i++) {
            for (ItemStack option : RecipeCompat.stacks(ingredients.get(i))) {
                List<Integer> list = byItem.computeIfAbsent(option.getItem(), key -> new ArrayList<>());
                if (list.isEmpty() || list.getLast() != i) list.add(i);
            }
        }
        int[][] userArrays = new int[users.size()][];
        for (int i = 0; i < users.size(); i++) userArrays[i] = users.get(i).stream().mapToInt(Integer::intValue).toArray();
        Map<Item, int[]> byItemArrays = new HashMap<>();
        byItem.forEach((item, list) -> byItemArrays.put(item, list.stream().mapToInt(Integer::intValue).toArray()));
        return new ReactorGraph(List.copyOf(recipes), List.copyOf(ingredients), userArrays, byItemArrays);
    }
}
