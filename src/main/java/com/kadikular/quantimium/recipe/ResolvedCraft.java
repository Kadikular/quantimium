package com.kadikular.quantimium.recipe;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One craft the Quantum Crafter can commit: which recipe, what to withdraw (local slots and/or remote
 * link inventories), what is produced (guaranteed outputs only), and how much FE it costs.
 */
public record ResolvedCraft(
        Identifier recipeId,
        List<IngredientPool.Withdrawal> withdrawals,
        List<ItemStack> outputs,
        /** Price of the whole batch, not of one run of the recipe. */
        int feCost,
        /** How many runs of the recipe this craft bundles. */
        int batchSize,
        /** Whether stored energy, rather than ingredients, is what kept this batch small. */
        boolean energyCapped,
        /** Runs currently payable before the visible output is capped to one stack. */
        int availableRuns) {

    public ResolvedCraft {
        withdrawals = withdrawals == null ? List.of() : IngredientPool.merge(withdrawals);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        feCost = Math.max(0, feCost);
        batchSize = Math.max(1, batchSize);
        availableRuns = Math.max(0, availableRuns);
    }

    public ResolvedCraft(Identifier recipeId, List<IngredientPool.Withdrawal> withdrawals,
                         List<ItemStack> outputs, int feCost, int batchSize, boolean energyCapped) {
        this(recipeId, withdrawals, outputs, feCost, batchSize, energyCapped, batchSize);
    }

    public ResolvedCraft(Identifier recipeId, List<IngredientPool.Withdrawal> withdrawals,
                         List<ItemStack> outputs, int feCost) {
        this(recipeId, withdrawals, outputs, feCost, 1, false, 1);
    }

    public ItemStack primaryOutput() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().copy();
    }

    /** One ingredient demand of a single run, aggregated over every slot of the same source. */
    private record Demand(int sourceKey, Item item, @Nullable DataComponentPatch components) {}

    /**
     * Collapses the per-slot withdrawals of one run into a demand per (source, item). Two withdrawals
     * naming different slots of the same chest for the same item become one demand, so batch sizing
     * counts the item in total rather than treating whichever slot was matched as the whole supply.
     */
    private Map<Demand, Integer> demandsPerRun() {
        Map<Demand, Integer> demands = new LinkedHashMap<>();
        for (IngredientPool.Withdrawal withdrawal : withdrawals) {
            if (withdrawal.count() <= 0 || withdrawal.expected() == null) continue;
            int perRun = Math.ceilDiv(withdrawal.count(), batchSize);
            Demand demand = new Demand(IngredientPool.sourceKey(withdrawal.provenance()), withdrawal.expected(),
                    withdrawal.components());
            demands.merge(demand, perRun, Integer::sum);
        }
        return demands;
    }

    /**
     * Expands a one-craft match to the largest whole batch that fits the available ingredients, one
     * stack of every output, and the energy on hand. Ghosts therefore describe exactly what one click
     * will consume and return, rather than always pretending the recipe only ran once.
     */
    public ResolvedCraft largestBatch(IngredientPool pool, int availableFe) {
        Map<Demand, Integer> demands = demandsPerRun();

        int supply = Integer.MAX_VALUE;
        for (Map.Entry<Demand, Integer> entry : demands.entrySet()) {
            int perRun = entry.getValue();
            if (perRun <= 0) continue;
            int have = pool.totalAvailable(entry.getKey().sourceKey(), entry.getKey().item(), entry.getKey().components());
            supply = Math.min(supply, have / perRun);
        }

        int byOutputStack = Integer.MAX_VALUE;
        for (ItemStack output : outputs) {
            if (output.isEmpty() || output.getCount() <= 0) continue;
            byOutputStack = Math.min(byOutputStack, output.getMaxStackSize() / output.getCount());
        }

        int affordable = feCost > 0 ? availableFe / feCost : Integer.MAX_VALUE;

        int available = Math.min(supply, affordable);
        if (available == Integer.MAX_VALUE) available = 1;
        available = Math.max(0, available);

        int copies = Math.min(available, byOutputStack);
        if (copies == Integer.MAX_VALUE) copies = 1;
        boolean energyCapped = affordable < Math.min(supply, byOutputStack);
        copies = Math.max(1, copies);

        List<IngredientPool.Withdrawal> batched = new ArrayList<>(withdrawals.size());
        for (IngredientPool.Withdrawal withdrawal : withdrawals) {
            int perRun = Math.ceilDiv(withdrawal.count(), batchSize);
            batched.add(withdrawal.withCount(Math.multiplyExact(perRun, copies)));
        }
        List<ItemStack> batchedOutputs = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            int perRun = output.getCount() / batchSize;
            batchedOutputs.add(output.copyWithCount(Math.max(output.isEmpty() ? 0 : 1, perRun * copies)));
        }
        long energy = (long) (feCost / batchSize) * copies;
        return new ResolvedCraft(recipeId, batched, batchedOutputs,
                (int) Math.min(Integer.MAX_VALUE, energy), copies, energyCapped, available);
    }

    /**
     * A slice of this batch running {@code runs} times instead of {@link #batchSize()}. Automation asks
     * for a couple of items at a time, and a pull should not spend a whole stack of ingredients to
     * answer it. Costs and withdrawals round up so a partial run is never handed out free.
     */
    public ResolvedCraft withRuns(int runs) {
        int clamped = Math.max(1, Math.min(runs, batchSize));
        if (clamped == batchSize) return this;

        List<IngredientPool.Withdrawal> scaled = new ArrayList<>(withdrawals.size());
        for (IngredientPool.Withdrawal withdrawal : withdrawals) {
            long count = Math.ceilDiv((long) withdrawal.count() * clamped, batchSize);
            scaled.add(withdrawal.withCount((int) count));
        }
        List<ItemStack> scaledOutputs = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            if (output.isEmpty()) {
                scaledOutputs.add(ItemStack.EMPTY);
                continue;
            }
            scaledOutputs.add(output.copyWithCount(Math.max(1, output.getCount() * clamped / batchSize)));
        }
        long energy = Math.ceilDiv((long) feCost * clamped, batchSize);
        return new ResolvedCraft(recipeId, scaled, scaledOutputs,
                (int) Math.min(Integer.MAX_VALUE, energy), clamped, energyCapped,
                Math.min(availableRuns, clamped));
    }

    /** How much of one output a single run of the recipe yields. */
    public int perRunOutputCount(int outputIndex) {
        if (outputIndex < 0 || outputIndex >= outputs.size()) return 0;
        return outputs.get(outputIndex).getCount() / batchSize;
    }

    /** Total output currently payable, independent of the one-stack visible batch cap. */
    public long availableOutputCount(int outputIndex) {
        return (long) perRunOutputCount(outputIndex) * availableRuns;
    }

    /** Whether the ingredients on hand can still cover one run, summed across each source. */
    public boolean runSatisfiedBy(IngredientPool pool) {
        for (Map.Entry<Demand, Integer> entry : withRuns(1).demandsPerRun().entrySet()) {
            int have = pool.totalAvailable(entry.getKey().sourceKey(), entry.getKey().item());
            if (have < entry.getValue()) return false;
        }
        return true;
    }

    public int totalConsumed() {
        int total = 0;
        for (IngredientPool.Withdrawal withdrawal : withdrawals) total += withdrawal.count();
        return total;
    }
}
