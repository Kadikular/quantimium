package com.kadikular.quantimium.reactor;

import com.kadikular.quantimium.recipe.RecipeFilter;
import com.kadikular.quantimium.recipe.RecipeShape;
import com.kadikular.quantimium.recipe.RecipeShapes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every recipe a Reactor's catalysts can run, indexed by what each makes. Built from the same
 * {@link RecipeShapes} the ME Superposition Crafter offers as patterns, so the Reactor prices and
 * describes a recipe the way a Quantum Crafter would. Rebuilt only when a catalyst changes.
 */
public final class ReactorRecipes {

    /** One recipe and the bay whose catalyst runs it. */
    public record Producer(RecipeShape shape, int bay) {
        /** How many of {@code item} one run makes. */
        public long yieldOf(ItemResource item) {
            long total = 0;
            for (ItemStack output : shape.outputs()) {
                if (item.matches(output)) total += output.getCount();
            }
            return total;
        }
    }

    public static final ReactorRecipes NONE = of(List.of(), List.of());

    private final List<ItemStack> catalysts;
    /** Each catalyst's bay's filter, by catalyst. */
    private final List<RecipeFilter> filters;
    /** The catalysts' own recipes, kept so Matter can be added without working them out again. */
    private final List<Producer> fromCatalysts;
    /** The kinds of Unrealised Matter (each history) whose observations are among the recipes. */
    private final java.util.Set<ItemResource> matter;
    private final List<Producer> producers;
    private final Map<ItemResource, List<Producer>> byOutput;
    /** Built on first use, from whichever thread asks first; never changes after. */
    private volatile ReactorGraph graph;

    private ReactorRecipes(List<ItemStack> catalysts, List<RecipeFilter> filters, List<Producer> fromCatalysts, java.util.Set<ItemResource> matter,
                           List<Producer> producers, Map<ItemResource, List<Producer>> byOutput) {
        this.catalysts = catalysts;
        this.filters = filters;
        this.fromCatalysts = fromCatalysts;
        this.matter = matter;
        this.producers = producers;
        this.byOutput = byOutput;
    }

    /** Recipes as given, for tests and benchmarks: no catalysts behind them. */
    public static ReactorRecipes of(List<ItemStack> catalysts, List<Producer> producers) {
        return of(catalysts, List.of(), producers, java.util.Set.of(), List.of());
    }

    private static ReactorRecipes of(List<ItemStack> catalysts, List<RecipeFilter> filters, List<Producer> fromCatalysts, java.util.Set<ItemResource> matter,
                                     List<Producer> observed) {
        List<Producer> producers = new ArrayList<>(fromCatalysts);
        producers.addAll(observed);
        Map<ItemResource, List<Producer>> byOutput = new LinkedHashMap<>();
        for (Producer producer : producers) {
            for (ItemStack output : producer.shape().outputs()) {
                if (output.isEmpty()) continue;
                List<Producer> list = byOutput.computeIfAbsent(ItemResource.of(output), key -> new ArrayList<>());
                if (!list.contains(producer)) list.add(producer);
            }
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack catalyst : catalysts) copies.add(catalyst.copy());
        return new ReactorRecipes(List.copyOf(copies), List.copyOf(filters), List.copyOf(fromCatalysts), java.util.Set.copyOf(matter),
                List.copyOf(producers), byOutput);
    }

    /** The bay of a recipe no catalyst runs: observing Unrealised Matter. */
    public static final int NO_BAY = -1;

    /**
     * These recipes with Unrealised Matter observed in the horizon, needing no catalyst: for each kind
     * of Matter held (each history), one recipe per form of each ore it could become, as the Materialiser
     * offers them, from commons to rares and never very rare, whatever field the Reactor stands in. Each
     * takes one Matter of exactly that history, makes what one Matter makes, and costs FE only.
     */
    public ReactorRecipes withMatter(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                     java.util.Set<ItemResource> kinds) {
        if (kinds.equals(matter)) return this;
        List<Producer> observed = new ArrayList<>();
        for (ItemResource kind : kinds) {
            ItemStack stack = kind.toStack(1);
            com.kadikular.quantimium.unrealised.MatterHistory history = com.kadikular.quantimium.unrealised.MatterHistory.of(stack);
            net.minecraft.world.item.crafting.Ingredient exactly =
                    net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(true, stack);
            for (com.kadikular.quantimium.unrealised.Materialising.Option option : com.kadikular.quantimium.unrealised.Materialising
                    .options(level, pos, history, com.kadikular.quantimium.flux.FluxBand.SINGULARITY)) {
                if (!option.allowed()) continue;
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        com.kadikular.quantimium.Quantimium.MODID, "reactor/observe/" + Integer.toHexString(kind.hashCode())
                                + "/" + option.ore().getPath().replace('/', '_') + "/"
                                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(option.display().getItem()).getPath());
                observed.add(new Producer(new RecipeShape(id, List.of(new RecipeShape.Input(exactly, 1)),
                        List.of(option.display().copy()),
                        com.kadikular.quantimium.block.entity.MaterialiserBlockEntity.FE_PER_MATTER), NO_BAY));
            }
        }
        return of(catalysts, filters, fromCatalysts, kinds, observed);
    }

    public ReactorGraph graph() {
        ReactorGraph built = graph;
        if (built == null) {
            synchronized (this) {
                if (graph == null) graph = ReactorGraph.build(producers);
                built = graph;
            }
        }
        return built;
    }

    public static ReactorRecipes build(Level level, List<ItemStack> catalysts) {
        return build(level, catalysts, java.util.Collections.nCopies(catalysts.size(), RecipeFilter.NONE));
    }

    /** The recipes of {@code catalysts}, each as the filter of its bay, at the same index, lets it run. */
    public static ReactorRecipes build(Level level, List<ItemStack> catalysts, List<RecipeFilter> filters) {
        List<Producer> producers = new ArrayList<>();
        for (int bay = 0; bay < catalysts.size(); bay++) {
            ItemStack catalyst = catalysts.get(bay);
            if (catalyst.isEmpty()) continue;
            for (RecipeShape shape : RecipeShapes.forCatalyst(level, catalyst)) {
                RecipeShape allowed = filters.get(bay).apply(shape);
                if (allowed != null) producers.add(new Producer(allowed, bay));
            }
        }
        return of(catalysts, filters, producers, java.util.Set.of(), List.of());
    }

    /** Whether these were built from exactly {@code catalysts}, components and all, through the same filters. */
    public boolean builtFrom(List<ItemStack> catalysts, List<RecipeFilter> filters) {
        if (catalysts.size() != this.catalysts.size() || filters.size() != this.filters.size()) return false;
        for (int i = 0; i < catalysts.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(catalysts.get(i), this.catalysts.get(i))) return false;
            if (!filters.get(i).same(this.filters.get(i))) return false;
        }
        return true;
    }

    public List<Producer> producersOf(ItemResource item) {
        return byOutput.getOrDefault(item, List.of());
    }

    /** Everything some catalyst can make: the Reactor's reachable outputs, before counting stock. */
    public List<ItemResource> outputs() {
        return List.copyOf(byOutput.keySet());
    }

    public ItemStack catalyst(int bay) {
        return bay >= 0 && bay < catalysts.size() ? catalysts.get(bay) : ItemStack.EMPTY;
    }
}
