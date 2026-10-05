package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.unrealised.MatterSteps;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.recipe.adapter.ConfiguredRecipeFamily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import com.kadikular.quantimium.util.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the list of crafts currently available from a catalyst + input grid, EMI-style over
 * {@link net.minecraft.world.item.crafting.RecipeManager} contents without depending on EMI.
 */
public final class CrafterPreview {

    /** Maximum number of individual output stacks advertised by one crafter. */
    public static final int MAX_OUTPUTS = 54;

    public List<ResolvedCraft> preview(Level level, BlockPos source, ItemStack catalyst, ItemStackHandler grid,
                                       int inputSlots, int availableFe) {
        return preview(level, source, catalyst, grid, inputSlots, availableFe, 1.0);
    }

    public List<ResolvedCraft> preview(Level level, BlockPos source, ItemStack catalyst, ItemStackHandler grid,
                                       int inputSlots, int availableFe, double feMultiplier) {
        if (level == null || catalyst.isEmpty()) return List.of();
        if (!CatalystResolver.isCatalystAllowed(catalyst)) return List.of();

        for (int i = 0; i < inputSlots; i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (EntangledLinks.isLink(stack)) continue;
            if (stack.is(ModTags.QUANTUM_CRAFTER_BLACKLIST)) return List.of();
        }

        IngredientPool pool = IngredientPool.build(level, source, grid, inputSlots);
        if (pool.isEmpty()) return List.of();

        RecipeIndex index = RecipeIndex.shared();
        index.bind(level);
        HolderLookup.Provider registries = level.registryAccess();
        Map<Identifier, ResolvedCraft> found = new LinkedHashMap<>();
        // Hostile Neural Networks has no recipes to index; its Fabricator is matched on its own.
        if (HnnFabricatorFamily.handles(catalyst)) {
            for (ResolvedCraft craft : HnnFabricatorFamily.match(pool)) {
                found.putIfAbsent(craft.recipeId(), priced(craft, pool, availableFe, feMultiplier));
            }
        }

        List<RecipeType<?>> types = CatalystResolver.resolve(catalyst);
        if (types.isEmpty() && found.isEmpty()) return List.of();

        boolean craftingCatalyst = CatalystResolver.isCraftingCatalyst(catalyst);

        int foundryArms = FoundryFamily.arms(catalyst);
        for (RecipeType<?> type : types) {
            matchType(level, registries, index, type, pool, craftingCatalyst, foundryArms, availableFe, feMultiplier,
                    found);
        }
        // Unrealised Matter goes through a catalyst unrealised, the step recorded rather than applied.
        for (ResolvedCraft craft : MatterSteps.match(level, source, types, pool)) {
            found.putIfAbsent(craft.recipeId(), priced(craft, pool, availableFe, feMultiplier));
        }

        List<ResolvedCraft> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparingInt(CrafterPreview::displayPriority).reversed()
                .thenComparing(Comparator.comparingInt(ResolvedCraft::totalConsumed).reversed()));
        List<ResolvedCraft> capped = new ArrayList<>();
        int usedOutputs = 0;
        for (ResolvedCraft craft : result) {
            int outputs = 0;
            for (ItemStack output : craft.outputs()) {
                if (!output.isEmpty()) outputs++;
            }
            if (outputs == 0 || outputs > MAX_OUTPUTS) continue;
            // Keep multi-output recipes atomic rather than cutting their byproducts off at the cap.
            if (usedOutputs + outputs > MAX_OUTPUTS) continue;
            capped.add(craft);
            usedOutputs += outputs;
            if (usedOutputs == MAX_OUTPUTS) break;
        }
        return capped;
    }

    /** Every craft of recipe type {@code type} the pool can make, into {@code found}. */
    private static void matchType(Level level, HolderLookup.Provider registries, RecipeIndex index, RecipeType<?> type,
                                  IngredientPool pool, boolean craftingCatalyst, int foundryArms, int availableFe,
                                  double feMultiplier, Map<Identifier, ResolvedCraft> found) {
        for (RecipeHolder<?> holder : index.candidatesFor(type, pool.items())) {
            Recipe<?> recipe = holder.value();

            if (craftingCatalyst && CraftingFamily.supports(recipe)) {
                CraftingFamily.match(level, registries, holder, pool)
                        .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                        .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
                continue;
            }

            if (VanillaSingleInputFamily.supports(recipe)) {
                for (ResolvedCraft craft : VanillaSingleInputFamily.match(level, registries, holder, pool)) {
                    found.putIfAbsent(craft.recipeId(), priced(craft, pool, availableFe, feMultiplier));
                }
                continue;
            }

            if (FoundryFamily.supports(recipe)) {
                FoundryFamily.match(holder, pool, foundryArms)
                        .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                        .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
                continue;
            }

            if (MiMachineFamily.supports(recipe)) {
                MiMachineFamily.match(holder, pool)
                        .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                        .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
                continue;
            }

            ConfiguredRecipeFamily.match(registries, holder, pool)
                    .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                    .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
            if (found.containsKey(holder.id().identifier())) continue;

            if (GenericItemFamily.supports(recipe)) {
                GenericItemFamily.match(registries, holder, pool)
                        .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                        .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
            }
        }
    }

    /**
     * What recipe type {@code type} makes of one {@code input}, as a Quantum Crafter would: every
     * craft that uses nothing but that one item. For working out a step of Unrealised Matter's history.
     */
    public static List<ResolvedCraft> craftsOfOne(Level level, BlockPos source, RecipeType<?> type, ItemStack input) {
        ItemStackHandler grid = new ItemStackHandler(1);
        grid.setStackInSlot(0, input.copyWithCount(1));
        IngredientPool pool = IngredientPool.build(level, source, grid, 1);
        RecipeIndex index = RecipeIndex.shared();
        index.bind(level);
        Map<Identifier, ResolvedCraft> found = new LinkedHashMap<>();
        matchType(level, level.registryAccess(), index, type, pool, false, 4, Integer.MAX_VALUE, 1.0, found);
        List<ResolvedCraft> ofOne = new ArrayList<>();
        for (ResolvedCraft craft : found.values()) {
            ResolvedCraft single = craft.batchSize() > 1 ? craft.withRuns(1) : craft;
            if (single.totalConsumed() == 1) ofOne.add(single);
        }
        return ofOne;
    }

    private static ResolvedCraft priced(ResolvedCraft craft, IngredientPool pool, int availableFe,
                                        double feMultiplier) {
        return AnomalyEffects.tax(craft, feMultiplier).largestBatch(pool, availableFe);
    }

    private static int displayPriority(ResolvedCraft craft) {
        ItemStack output = craft.primaryOutput();
        if (output.isEmpty()) return 0;
        String path = BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
        return path.endsWith("_block") || path.endsWith("_nugget") ? 1 : 0;
    }
}
