package com.kadikular.quantimium.reactor;

import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.recipe.RecipeShape;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans how a Reactor makes something from what it holds, through every catalyst it has.
 *
 * <p>Depth first: take what's in stock, and make the rest with a recipe whose inputs can in turn be
 * taken or made, trying each recipe until one works and undoing the ones that don't. Leftovers from
 * one step (the surplus of a recipe that makes four when two were wanted, or a byproduct) are used
 * before stock by later steps.
 *
 * <p>An item is never made from itself: while a plan is making something, recipes needing that same
 * thing are skipped. So a processing loop (ingot to dust to ingot) can't be used to make more than
 * went in, and a loop that would gain is never found at all. Plans are bounded in depth and in work,
 * so an enormous recipe web refuses rather than stalling the server.
 */
public final class ReactorPlanner {

    public static final int MAX_DEPTH = 24;
    public static final int MAX_WORK = 20_000;

    /** One recipe run {@code runs} times, through the catalyst in bay {@code bay}. */
    public record Step(RecipeShape shape, int bay, long runs) {
        public long energy() {
            return shape.baseFe() * runs;
        }
    }

    /**
     * A plan that works: its steps in the order they run (inputs first), what it takes from stock,
     * what's left over to go back in, and its energy before tax.
     */
    public record Plan(ItemResource target, long count, List<Step> steps, Map<ItemResource, Long> consumed,
                       Map<ItemResource, Long> leftovers, long energy) {}

    public record Result(Plan plan, Component problem) {
        public boolean planned() {
            return plan != null;
        }
    }

    private final ReactorRecipes recipes;
    /** What's left of the ledger as the plan takes from it. */
    private final Map<ItemResource, Long> stock;
    /** Made by earlier steps and not used yet: spent before stock. */
    private final Map<ItemResource, Long> made = new LinkedHashMap<>();
    private final Map<ItemResource, Long> consumed = new LinkedHashMap<>();
    private final List<Step> steps = new ArrayList<>();
    /** Every change to the three maps, so a failed attempt is undone without copying them. */
    private final List<Change> undo = new ArrayList<>();
    private final Deque<ItemResource> making = new ArrayDeque<>();
    private int work;
    private ItemResource missing;
    private long missingCount;

    private ReactorPlanner(ReactorRecipes recipes, Map<ItemResource, Long> stock) {
        this.recipes = recipes;
        this.stock = new LinkedHashMap<>(stock);
    }

    /** Plans {@code count} of {@code target} from {@code stock}. Changes nothing. */
    public static Result plan(ReactorRecipes recipes, Map<ItemResource, Long> stock, ItemResource target, long count) {
        if (target.isEmpty() || count <= 0) return new Result(null, Component.translatable("message.quantimium.reactor.nothing"));
        ReactorPlanner planner = new ReactorPlanner(recipes, stock);
        boolean ok = planner.need(target, count, 0);
        if (planner.work > MAX_WORK) {
            return new Result(null, Component.translatable("message.quantimium.reactor.too_complex"));
        }
        if (!ok) {
            ItemResource short_ = planner.missing == null ? target : planner.missing;
            long shortBy = planner.missing == null ? count : planner.missingCount;
            return new Result(null, Component.translatable("message.quantimium.reactor.missing", shortBy,
                    short_.toStack(1).getHoverName()));
        }
        long energy = 0;
        for (Step step : planner.steps) energy += step.energy();
        Map<ItemResource, Long> leftovers = new LinkedHashMap<>();
        planner.made.forEach((item, amount) -> {
            if (amount > 0) leftovers.put(item, amount);
        });
        return new Result(new Plan(target, count, List.copyOf(planner.steps), planner.consumed, leftovers, energy), null);
    }

    /** Takes or makes {@code n} of {@code item}. On failure the planner's state is as it was. */
    private boolean need(ItemResource item, long n, int depth) {
        if (++work > MAX_WORK) return false;
        n -= takeFrom(made, item, n, false);
        n -= takeFrom(stock, item, n, true);
        if (n <= 0) return true;
        if (depth >= MAX_DEPTH || making.contains(item)) return shortOf(item, n);

        List<ReactorRecipes.Producer> producers = new ArrayList<>(recipes.producersOf(item));
        if (producers.isEmpty()) return shortOf(item, n);
        // The recipe that uses least per item first, so six stone never go into four stairs when a
        // stonecutter makes one from each; energy only breaks ties.
        producers.sort(Comparator.<ReactorRecipes.Producer>comparingDouble(p -> materialPerItem(p, item))
                .thenComparingDouble(p -> (double) p.shape().baseFe() / Math.max(1, p.yieldOf(item))));
        for (ReactorRecipes.Producer producer : producers) {
            long per = producer.yieldOf(item);
            if (per <= 0) continue;
            long runs = (n + per - 1) / per;
            State saved = save();
            making.push(item);
            boolean ok = true;
            for (RecipeShape.Input input : producer.shape().inputs()) {
                if (!needIngredient(input.ingredient(), input.count() * runs, depth + 1)) {
                    ok = false;
                    break;
                }
            }
            for (Ingredient tool : producer.shape().tools()) {
                if (!ok) break;
                ok = wears(producer.shape(), tool) ? needUses(tool, runs, depth + 1) : haveTool(tool, depth + 1);
            }
            making.pop();
            if (ok) {
                steps.add(new Step(producer.shape(), producer.bay(), runs));
                for (ItemStack output : producer.shape().outputs()) {
                    if (output.isEmpty()) continue;
                    long amount = output.getCount() * runs;
                    if (item.matches(output)) amount -= n;
                    if (amount > 0) add(made, ItemResource.of(output), amount);
                }
                return true;
            }
            restore(saved);
            if (work > MAX_WORK) return false;
        }
        return false;
    }

    /**
     * Takes or makes {@code n} items matching {@code ingredient}: from what's on hand first, across
     * every matching item, then by making whichever matching item can be made.
     */
    private boolean needIngredient(Ingredient ingredient, long n, int depth) {
        Map<ItemResource, Long> taken = new LinkedHashMap<>();
        n -= takeMatching(made, ingredient, n, false, taken);
        n -= takeMatching(stock, ingredient, n, true, taken);
        if (n <= 0) {
            leaveRemainders(taken);
            return true;
        }
        List<ItemStack> options = RecipeCompat.stacks(ingredient);
        for (ItemStack option : options) {
            ItemResource item = ItemResource.of(option);
            if (recipes.producersOf(item).isEmpty()) continue;
            State saved = save();
            if (need(item, n, depth)) {
                taken.merge(item, n, Long::sum);
                leaveRemainders(taken);
                return true;
            }
            restore(saved);
            if (work > MAX_WORK) return false;
        }
        if (!options.isEmpty()) shortOf(ItemResource.of(options.getFirst()), n);
        return false;
    }

    /**
     * What a craft leaves of what it took goes back: an empty bucket for a filled one. A tool a
     * recipe keeps, worn or not, is a tool of its shape instead ({@link #needUses}, {@link #haveTool}).
     */
    private void leaveRemainders(Map<ItemResource, Long> taken) {
        taken.forEach((item, count) -> {
            ItemStack left = remainder(item.toStack(1));
            if (!left.isEmpty()) add(made, ItemResource.of(left), left.getCount() * count);
        });
    }

    /** What crafting with one {@code stack} leaves behind, if anything. */
    public static ItemStack remainder(ItemStack stack) {
        net.minecraft.world.item.ItemStackTemplate left = stack.getItem().getCraftingRemainder(stack);
        return left == null ? ItemStack.EMPTY : left.create();
    }

    private static boolean wears(RecipeShape shape, Ingredient tool) {
        for (Ingredient worn : shape.wears()) {
            if (worn == tool) return true;
        }
        return false;
    }

    /**
     * {@code uses} crafts' worth of a tool that wears, one durability a craft: worn tools on hand first,
     * each worn as far as it goes and put back worn if anything is left of it, then as few new ones
     * made as the rest needs.
     */
    private boolean needUses(Ingredient tool, long uses, int depth) {
        for (Map<ItemResource, Long> pool : List.of(made, stock)) {
            for (ItemResource item : List.copyOf(pool.keySet())) {
                if (uses <= 0) break;
                if (!tool.test(item.toStack(1))) continue;
                while (uses > 0 && pool.getOrDefault(item, 0L) > 0) {
                    takeFrom(pool, item, 1, pool == stock);
                    uses = wear(item.toStack(1), uses);
                }
            }
        }
        if (uses <= 0) return true;
        for (ItemStack option : RecipeCompat.stacks(tool)) {
            ItemResource item = ItemResource.of(option);
            if (recipes.producersOf(item).isEmpty()) continue;
            long life = Math.max(1, option.getMaxDamage() - option.getDamageValue());
            long fresh = (uses + life - 1) / life;
            State saved = save();
            if (need(item, fresh, depth)) {
                for (long i = 0; i < fresh && uses > 0; i++) uses = wear(option.copy(), uses);
                return true;
            }
            restore(saved);
            if (work > MAX_WORK) return false;
        }
        return shortOf(ItemResource.of(RecipeCompat.stacks(tool).getFirst()), uses);
    }

    /** Wears {@code tool} by as many of {@code uses} as it has left; puts it back if it lasts. Returns the uses still owed. */
    private long wear(ItemStack tool, long uses) {
        long left = tool.getMaxDamage() - tool.getDamageValue();
        long used = Math.min(left, uses);
        if (used < left) {
            ItemStack worn = tool.copy();
            worn.setDamageValue(tool.getDamageValue() + (int) used);
            add(made, ItemResource.of(worn), 1);
        }
        return uses - used;
    }

    /** Items a recipe takes for each one of {@code item} it makes: how wasteful it is. */
    private static double materialPerItem(ReactorRecipes.Producer producer, ItemResource item) {
        long taken = 0;
        for (RecipeShape.Input input : producer.shape().inputs()) taken += input.count();
        return (double) taken / Math.max(1, producer.yieldOf(item));
    }

    /**
     * A tool, such as a press, on hand: in stock or made earlier, and left where it is. If there is
     * none, one is made and kept with the leftovers, to go back into the horizon after.
     */
    private boolean haveTool(Ingredient tool, int depth) {
        for (Map<ItemResource, Long> pool : List.of(made, stock)) {
            for (Map.Entry<ItemResource, Long> entry : pool.entrySet()) {
                if (entry.getValue() > 0 && tool.test(entry.getKey().toStack(1))) return true;
            }
        }
        for (ItemStack option : RecipeCompat.stacks(tool)) {
            ItemResource item = ItemResource.of(option);
            if (recipes.producersOf(item).isEmpty()) continue;
            State saved = save();
            if (need(item, 1, depth)) {
                add(made, item, 1);
                return true;
            }
            restore(saved);
        }
        return false;
    }

    private long takeFrom(Map<ItemResource, Long> pool, ItemResource item, long n, boolean fromStock) {
        if (n <= 0) return 0;
        long have = pool.getOrDefault(item, 0L);
        long take = Math.min(have, n);
        if (take <= 0) return 0;
        set(pool, item, have - take);
        if (fromStock) add(consumed, item, take);
        return take;
    }

    private long takeMatching(Map<ItemResource, Long> pool, Ingredient ingredient, long n, boolean fromStock,
                              Map<ItemResource, Long> taken) {
        long total = 0;
        for (ItemResource item : List.copyOf(pool.keySet())) {
            if (total >= n) break;
            if (!ingredient.test(item.toStack(1))) continue;
            long took = takeFrom(pool, item, n - total, fromStock);
            if (took > 0) taken.merge(item, took, Long::sum);
            total += took;
        }
        return total;
    }

    private boolean shortOf(ItemResource item, long n) {
        missing = item;
        missingCount = n;
        return false;
    }

    /** One entry's value before a change: null when it wasn't there. */
    private record Change(Map<ItemResource, Long> map, ItemResource key, Long before) {}

    private void set(Map<ItemResource, Long> map, ItemResource key, long value) {
        undo.add(new Change(map, key, map.get(key)));
        if (value <= 0) map.remove(key);
        else map.put(key, value);
    }

    private void add(Map<ItemResource, Long> map, ItemResource key, long amount) {
        set(map, key, map.getOrDefault(key, 0L) + amount);
    }

    /** How far to undo to: the change log and the steps, as they stood. */
    private record State(int changes, int steps) {}

    private State save() {
        return new State(undo.size(), steps.size());
    }

    private void restore(State state) {
        while (undo.size() > state.changes()) {
            Change change = undo.removeLast();
            if (change.before() == null) change.map().remove(change.key());
            else change.map().put(change.key(), change.before());
        }
        while (steps.size() > state.steps()) steps.removeLast();
    }
}
