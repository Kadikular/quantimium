package com.kadikular.quantimium.unrealised;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.recipe.CraftEnergy;
import com.kadikular.quantimium.recipe.CrafterPreview;
import com.kadikular.quantimium.recipe.IngredientPool;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Processing Unrealised Matter without observing it (game plan E3). Put through a catalyst in a
 * Quantum Crafter, Matter stays unrealised and records the step: the catalyst's recipe type, at most
 * {@link MatterHistory#MAX_STEPS}. Nothing is worked out until the Matter becomes real.
 *
 * <p>Matter holds every form its processing could have made. Starting from an ore's drops, each step
 * works on every form the Matter could already be, one recipe of its type each (the least generous,
 * where the type has several for the same item), and keeps them all: raw iron macerated is raw iron
 * or dust; smelted after that, raw iron, dust or ingots. Order matters, since a step only reaches the
 * forms made before it. Where a step makes an item the Matter could already become, its route replaces
 * the old one: steps go through the forms in the order they were made, so the latest processing counts,
 * and macerated-then-smelted iron gives the ingots of its dust.
 *
 * <p>A step the Crafter offers must add a form to at least one of the pack's ores.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class MatterSteps {

    /** FE for one step of one Matter, before the instant-craft tax: 2,000 at the default. */
    public static final int STEP_FE = 1_000;

    private record Key(MatterHistory history, Identifier ore) {}

    private record StepKey(Identifier type, Item item, DataComponentPatch components) {}

    /** What each history makes of each ore, worked out once: recipes don't change until a reload. */
    private static final Map<Key, List<Form>> CACHE = new HashMap<>();
    /** What each step makes of one of each item, for Chambers observing step by step. */
    private static final Map<StepKey, Optional<List<ItemStack>>> STEPS = new HashMap<>();

    private MatterSteps() {}

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        CACHE.clear();
        STEPS.clear();
    }

    /** A recipe type's id, which is what a history records. */
    @Nullable
    public static Identifier idOf(RecipeType<?> type) {
        return BuiltInRegistries.RECIPE_TYPE.getKey(type);
    }

    /**
     * Every form {@code history} lets {@code start} (an ore's drops) become, in the order they were made,
     * each with how much of it one ore's worth gives. See the class comment for how steps build them.
     */
    public static List<Form> forms(Level level, BlockPos pos, MatterHistory history, List<Form> start) {
        Map<FormKey, Form> forms = new LinkedHashMap<>();
        for (Form form : start) forms.merge(FormKey.of(form.item()), form, (a, b) -> new Form(a.item(), a.amount() + b.amount()));
        for (Identifier step : history.steps()) {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(step);
            if (type == null) continue;
            for (Form form : List.copyOf(forms.values())) {
                List<ItemStack> made = oneStep(level, pos, type, form.item());
                if (made == null) continue;
                // One run per item: two dust smelt into two ingots. The latest route to an item wins.
                for (ItemStack output : merge(made)) {
                    FormKey key = FormKey.of(output);
                    forms.remove(key);
                    forms.put(key, new Form(output, output.getCount() * form.amount()));
                }
            }
        }
        return List.copyOf(forms.values());
    }

    /** As {@link #forms(Level, BlockPos, MatterHistory, List)}, from a real roll of an ore's drops. */
    public static List<Form> formsOfDrops(Level level, BlockPos pos, MatterHistory history, List<ItemStack> drops) {
        return forms(level, pos, history, drops.stream().map(Form::of).toList());
    }

    /** An item and its components, which is what makes two forms the same form. */
    private record FormKey(Item item, DataComponentPatch components) {
        static FormKey of(ItemStack stack) {
            return new FormKey(stack.getItem(), stack.getComponentsPatch());
        }
    }

    /** What {@code type} makes of one {@code item}, the least generous where there's a choice; null if nothing. */
    @Nullable
    private static List<ItemStack> oneStep(Level level, BlockPos pos, RecipeType<?> type, ItemStack item) {
        StepKey key = new StepKey(idOf(type), item.getItem(), item.getComponentsPatch());
        Optional<List<ItemStack>> cached = STEPS.get(key);
        if (cached == null) {
            cached = Optional.ofNullable(leastGenerous(level, pos, type, item));
            STEPS.put(key, cached);
        }
        return cached.orElse(null);
    }

    @Nullable
    private static List<ItemStack> leastGenerous(Level level, BlockPos pos, RecipeType<?> type, ItemStack item) {
        List<ItemStack> best = null;
        int bestCount = Integer.MAX_VALUE;
        for (ResolvedCraft craft : CrafterPreview.craftsOfOne(level, pos, type, item)) {
            int count = 0;
            for (ItemStack output : craft.outputs()) count += output.getCount();
            if (count <= 0 || count >= bestCount) continue;
            best = craft.outputs();
            bestCount = count;
        }
        return best;
    }

    /** Stacks of the same item and components added together, in first-seen order. */
    private static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            boolean added = false;
            for (int i = 0; i < merged.size(); i++) {
                if (ItemStack.isSameItemSameComponents(merged.get(i), stack)) {
                    merged.set(i, merged.get(i).copyWithCount(merged.get(i).getCount() + stack.getCount()));
                    added = true;
                    break;
                }
            }
            if (!added) merged.add(stack.copy());
        }
        return merged;
    }

    /**
     * Every form Matter with {@code history} could take as {@code ore}, from its average drops, with how
     * much of each one Matter makes on average; cached. Empty if the ore drops nothing.
     */
    public static List<Form> of(ServerLevel level, BlockPos pos, MatterHistory history, PackOres.Ore ore) {
        Key key = new Key(history, ore.tag().location());
        List<Form> cached = CACHE.get(key);
        if (cached == null) {
            List<Form> drops = PackOres.expectedDrops(level, ore);
            cached = drops.isEmpty() ? List.of() : forms(level, pos, history, drops);
            CACHE.put(key, cached);
        }
        return cached;
    }

    /** Whether the last step of {@code history} gives some ore of the pack a form it didn't have before. */
    public static boolean lastStepAdds(ServerLevel level, BlockPos pos, MatterHistory history) {
        if (history.isEmpty()) return false;
        MatterHistory before = new MatterHistory(history.steps().subList(0, history.steps().size() - 1));
        for (PackOres.Ore ore : PackOres.ores()) {
            Set<FormKey> had = new HashSet<>();
            for (Form form : of(level, pos, before, ore)) had.add(FormKey.of(form.item()));
            for (Form form : of(level, pos, history, ore)) {
                if (!had.contains(FormKey.of(form.item()))) return true;
            }
        }
        return false;
    }

    /**
     * The Crafter's offers for Matter in its grid: for each history there and each recipe type the
     * catalyst runs, the same Matter with that step after its history, if it's room for one more and
     * the step gives some ore a new form. One Matter a run, at {@link #STEP_FE} before the tax.
     */
    public static List<ResolvedCraft> match(Level level, BlockPos source, List<RecipeType<?>> types, IngredientPool pool) {
        if (!(level instanceof ServerLevel server) || types.isEmpty() || !PackOres.inUse()) return List.of();
        Map<DataComponentPatch, IngredientPool.Offer> histories = new LinkedHashMap<>();
        for (IngredientPool.Offer offer : pool.offers()) {
            if (offer.stack().is(ModItems.UNREALISED_MATTER.get())) {
                histories.putIfAbsent(offer.stack().getComponentsPatch(), offer);
            }
        }
        if (histories.isEmpty()) return List.of();
        Set<Identifier> steps = new LinkedHashSet<>();
        for (RecipeType<?> type : types) {
            Identifier id = idOf(type);
            if (id != null) steps.add(id);
        }
        List<ResolvedCraft> crafts = new ArrayList<>();
        for (Map.Entry<DataComponentPatch, IngredientPool.Offer> entry : histories.entrySet()) {
            ItemStack matter = entry.getValue().stack();
            MatterHistory history = MatterHistory.of(matter);
            if (history.full()) continue;
            for (Identifier step : steps) {
                MatterHistory next = history.then(step);
                if (!lastStepAdds(server, source, next)) continue;
                ItemStack out = next.applyTo(new ItemStack(ModItems.UNREALISED_MATTER.get()));
                IngredientPool.Withdrawal take = new IngredientPool.Withdrawal(entry.getValue().provenance(), 1,
                        matter.getItem(), entry.getKey());
                Identifier id = Identifier.fromNamespaceAndPath(Quantimium.MODID, "matter/"
                        + step.getNamespace() + "/" + step.getPath() + "/" + Integer.toHexString(history.hashCode()));
                crafts.add(new ResolvedCraft(id, List.of(take), List.of(out), CraftEnergy.flat(STEP_FE)));
            }
        }
        return crafts;
    }
}
