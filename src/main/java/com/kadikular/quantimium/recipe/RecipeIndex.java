package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.recipe.adapter.ConfiguredRecipeFamily;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cache over {@link RecipeManager#getAllRecipesFor(RecipeType)} plus a reverse index from input item
 * to the recipes that can consume it, so a preview only tests the handful of recipes reachable from
 * the ingredients actually present instead of every recipe of every applicable type.
 *
 * <p>Shared by all crafters: the tables are large and identical, and previews only run server-side.
 */
public final class RecipeIndex {

    private static final RecipeIndex SHARED = new RecipeIndex();

    public static RecipeIndex shared() {
        return SHARED;
    }

    private RecipeManager manager;
    private final Map<RecipeType<?>, List<RecipeHolder<?>>> byType = new HashMap<>();
    private final Map<RecipeType<?>, TypeIndex> indexByType = new HashMap<>();

    /** Recipes bucketed by accepted input item; {@code unindexed} holds those we could not read. */
    private record TypeIndex(Map<Item, List<RecipeHolder<?>>> byItem, List<RecipeHolder<?>> unindexed) {}

    /** Binds the server's recipes; a client level has none to index. */
    public void bind(Level level) {
        if (level.recipeAccess() instanceof RecipeManager recipeManager) bind(recipeManager);
    }

    public void bind(RecipeManager recipeManager) {
        if (this.manager != recipeManager) {
            this.manager = recipeManager;
            byType.clear();
            indexByType.clear();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<RecipeHolder<?>> recipesOf(RecipeType<?> type) {
        if (manager == null || type == null) return List.of();
        return byType.computeIfAbsent(type, t -> {
            List holders = List.copyOf(manager.recipeMap().byType((RecipeType) t));
            return Collections.unmodifiableList(holders);
        });
    }

    /**
     * Recipes of {@code type} worth testing given the items on hand. A recipe is returned when any
     * of its inputs accepts one of {@code available}, so the result is a superset of the matches.
     */
    public List<RecipeHolder<?>> candidatesFor(RecipeType<?> type, Set<Item> available) {
        if (manager == null || type == null) return List.of();
        if (available.isEmpty()) return List.of();

        TypeIndex index = indexByType.computeIfAbsent(type, this::buildIndex);
        LinkedHashSet<RecipeHolder<?>> candidates = new LinkedHashSet<>(index.unindexed());
        for (Item item : available) {
            List<RecipeHolder<?>> bucket = index.byItem().get(item);
            if (bucket != null) candidates.addAll(bucket);
        }

        List<RecipeHolder<?>> result = new ArrayList<>(candidates);
        // Stable order regardless of ingredient iteration order, so preview ordering does not flicker.
        result.sort((a, b) -> a.id().identifier().compareTo(b.id().identifier()));
        return result;
    }

    private TypeIndex buildIndex(RecipeType<?> type) {
        Map<Item, List<RecipeHolder<?>>> byItem = new HashMap<>();
        List<RecipeHolder<?>> unindexed = new ArrayList<>();
        for (RecipeHolder<?> holder : recipesOf(type)) {
            Set<Item> items = inputItems(holder.value());
            if (items.isEmpty()) {
                unindexed.add(holder);
                continue;
            }
            for (Item item : items) {
                byItem.computeIfAbsent(item, i -> new ArrayList<>()).add(holder);
            }
        }
        return new TypeIndex(byItem, List.copyOf(unindexed));
    }

    private static Set<Item> inputItems(Recipe<?> recipe) {
        Set<Item> items = new HashSet<>();
        try {
            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                if (ingredient.isEmpty()) continue;
                ingredient.items().forEach(item -> items.add(item.value()));
            }
        } catch (Throwable ignored) {
            // Recipes that cannot describe their inputs fall back to always being tested.
        }
        if (items.isEmpty()) items.addAll(MiMachineFamily.inputItems(recipe));
        if (items.isEmpty()) items.addAll(ConfiguredRecipeFamily.inputItems(recipe));
        return items;
    }
}
