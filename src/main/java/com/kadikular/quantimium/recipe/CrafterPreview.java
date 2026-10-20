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

    /**
     * Most output stacks one crafter previews: its screen scrolls and its previews aren't slots, so only a bound
     * for safety (a crafting table on a big network).
     */
    public static final int MAX_OUTPUTS = 1024;

    public List<ResolvedCraft> preview(Level level, BlockPos source, ItemStack catalyst, ItemStackHandler grid,
                                       int inputSlots, int availableFe) {
        return preview(level, source, catalyst, grid, inputSlots, availableFe, 1.0);
    }

    public List<ResolvedCraft> preview(Level level, BlockPos source, ItemStack catalyst, ItemStackHandler grid,
                                       int inputSlots, int availableFe, double feMultiplier) {
        return preview(level, source, catalyst, grid, inputSlots, availableFe, feMultiplier, MAX_OUTPUTS);
    }

    /**
     * As {@link #preview(Level, BlockPos, ItemStack, ItemStackHandler, int, int, double)}, up to {@code maxOutputs}
     * output stacks: a network working out what a crafter could make wants all of it, not one screenful.
     */
    public List<ResolvedCraft> preview(Level level, BlockPos source, ItemStack catalyst, ItemStackHandler grid,
                                       int inputSlots, int availableFe, double feMultiplier, int maxOutputs) {
        Run run = new Run(level, source, catalyst, grid, inputSlots, availableFe, feMultiplier, maxOutputs);
        while (!run.step(Long.MAX_VALUE)) {
            // all at once
        }
        return run.result();
    }

    /**
     * A preview worked out a little at a time: what's in the grid (and through its links) first, then the recipes
     * that might use it, as many a step as fit the time given. A crafting table on a big network matches a thousand
     * recipes; done in steps, the server carries on between them. What it reads through links is read in the first
     * step; the rest only matches recipes against that.
     */
    public static final class Run {
        private final Level level;
        private final BlockPos source;
        private final ItemStack catalyst;
        private final ItemStackHandler grid;
        private final int inputSlots;
        private final int availableFe;
        private final double feMultiplier;
        private final int maxOutputs;
        private boolean started;
        @org.jetbrains.annotations.Nullable
        private IngredientPool pool;
        private final List<RecipeHolder<?>> candidates = new ArrayList<>();
        private final List<RecipeType<?>> types = new ArrayList<>();
        private boolean craftingCatalyst;
        private int foundryArms;
        private int next;
        private final Map<Identifier, ResolvedCraft> found = new LinkedHashMap<>();
        @org.jetbrains.annotations.Nullable
        private List<ResolvedCraft> result;

        public Run(Level level, BlockPos source, ItemStack catalyst, ItemStackHandler grid, int inputSlots, int availableFe,
                   double feMultiplier, int maxOutputs) {
            this.level = level;
            this.source = source;
            this.catalyst = catalyst.copy();
            this.grid = grid;
            this.inputSlots = inputSlots;
            this.availableFe = availableFe;
            this.feMultiplier = feMultiplier;
            this.maxOutputs = maxOutputs;
        }

        /** The crafts, once worked out; empty till then. */
        public List<ResolvedCraft> result() {
            return result == null ? List.of() : result;
        }

        public boolean done() {
            return result != null;
        }

        /** Works for about {@code nanos}; whether it's done. */
        public boolean step(long nanos) {
            if (result != null) return true;
            long until = nanos == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + nanos;
            if (!started) {
                started = true;
                if (!start()) {
                    result = List.of();
                    return true;
                }
            }
            HolderLookup.Provider registries = level.registryAccess();
            while (next < candidates.size()) {
                matchOne(level, registries, candidates.get(next++), pool, craftingCatalyst, foundryArms, availableFe, feMultiplier, found);
                if (System.nanoTime() >= until && next < candidates.size()) return false;
            }
            finish();
            return true;
        }

        /** What's in the grid, and the recipes that might use it; false for nothing to preview. */
        private boolean start() {
            if (level == null || catalyst.isEmpty()) return false;
            if (!CatalystResolver.isCatalystAllowed(catalyst)) return false;
            for (int i = 0; i < inputSlots; i++) {
                ItemStack stack = grid.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (EntangledLinks.isLink(stack)) continue;
                if (stack.is(ModTags.QUANTUM_CRAFTER_BLACKLIST)) return false;
            }
            pool = IngredientPool.build(level, source, grid, inputSlots);
            if (pool.isEmpty()) return false;
            RecipeIndex index = RecipeIndex.shared();
            index.bind(level);
            // Hostile Neural Networks has no recipes to index; its Fabricator is matched on its own.
            if (HnnFabricatorFamily.handles(catalyst)) {
                for (ResolvedCraft craft : HnnFabricatorFamily.match(pool)) {
                    found.putIfAbsent(craft.recipeId(), priced(craft, pool, availableFe, feMultiplier));
                }
            }
            types.addAll(CatalystResolver.resolve(catalyst));
            if (types.isEmpty() && found.isEmpty()) return false;
            craftingCatalyst = CatalystResolver.isCraftingCatalyst(catalyst);
            foundryArms = FoundryFamily.arms(catalyst);
            java.util.Set<Item> items = pool.items();
            for (RecipeType<?> type : types) candidates.addAll(index.candidatesFor(type, items));
            return true;
        }

        private void finish() {
            // Unrealised Matter goes through a catalyst unrealised, the step recorded rather than applied.
            for (ResolvedCraft craft : MatterSteps.match(level, source, types, pool)) {
                found.putIfAbsent(craft.recipeId(), priced(craft, pool, availableFe, feMultiplier));
            }
            List<ResolvedCraft> all = new ArrayList<>(found.values());
            all.sort(Comparator.comparingInt(CrafterPreview::displayPriority).reversed()
                    .thenComparing(Comparator.comparingInt(ResolvedCraft::totalConsumed).reversed()));
            List<ResolvedCraft> capped = new ArrayList<>();
            int usedOutputs = 0;
            for (ResolvedCraft craft : all) {
                int outputs = 0;
                for (ItemStack output : craft.outputs()) {
                    if (!output.isEmpty()) outputs++;
                }
                if (outputs == 0 || outputs > maxOutputs) continue;
                // Keep multi-output recipes atomic rather than cutting their byproducts off at the cap.
                if (usedOutputs + outputs > maxOutputs) continue;
                capped.add(craft);
                usedOutputs += outputs;
                if (usedOutputs == maxOutputs) break;
            }
            result = capped;
        }
    }

    /** Every craft of recipe type {@code type} the pool can make, into {@code found}. */
    private static void matchType(Level level, HolderLookup.Provider registries, RecipeIndex index, RecipeType<?> type,
                                  IngredientPool pool, boolean craftingCatalyst, int foundryArms, int availableFe,
                                  double feMultiplier, Map<Identifier, ResolvedCraft> found) {
        for (RecipeHolder<?> holder : index.candidatesFor(type, pool.items())) {
            matchOne(level, registries, holder, pool, craftingCatalyst, foundryArms, availableFe, feMultiplier, found);
        }
    }

    /** The craft recipe {@code holder} makes from the pool, if any, into {@code found}. */
    private static void matchOne(Level level, HolderLookup.Provider registries, RecipeHolder<?> holder, IngredientPool pool,
                                 boolean craftingCatalyst, int foundryArms, int availableFe, double feMultiplier,
                                 Map<Identifier, ResolvedCraft> found) {
        Recipe<?> recipe = holder.value();

        if (craftingCatalyst && CraftingFamily.supports(recipe)) {
            CraftingFamily.match(level, registries, holder, pool)
                    .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                    .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
            return;
        }

        if (VanillaSingleInputFamily.supports(recipe)) {
            for (ResolvedCraft craft : VanillaSingleInputFamily.match(level, registries, holder, pool)) {
                found.putIfAbsent(craft.recipeId(), priced(craft, pool, availableFe, feMultiplier));
            }
            return;
        }

        if (FoundryFamily.supports(recipe)) {
            FoundryFamily.match(holder, pool, foundryArms)
                    .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                    .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
            return;
        }

        if (MiMachineFamily.supports(recipe)) {
            MiMachineFamily.match(holder, pool)
                    .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                    .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
            return;
        }

        ConfiguredRecipeFamily.match(registries, holder, pool)
                .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
        if (found.containsKey(holder.id().identifier())) return;

        if (GenericItemFamily.supports(recipe)) {
            GenericItemFamily.match(registries, holder, pool)
                    .map(craft -> priced(craft, pool, availableFe, feMultiplier))
                    .ifPresent(craft -> found.putIfAbsent(craft.recipeId(), craft));
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
