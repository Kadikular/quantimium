package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.unrealised.MatterSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What Crushing and Smelting lenses make of things on their way: a step for each lens, in their order
 * (Crushing, Crushing, Smelting makes cobblestone glass). Smelting is the furnace's. Crushing is a short stone chain of its own
 * (stone to cobblestone to gravel to sand), then whatever the pack's crushers do, in the order the
 * config lists their recipe types: their sure outputs only, never the chance ones. Unrealised Matter
 * isn't made anything: it gets the step in its history, as a Quantum Crafter would give it.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class Processing {

    /** What one of a thing becomes, and the FE that took: none, when nothing happened to it. */
    public record Made(List<ItemStack> ofOne, long fe) {
        public boolean changed() {
            return fe > 0;
        }
    }

    /** Crushing for any pack: the stone family down to sand. A pack's crushers do the rest. */
    private static final Map<Item, Item> CRUSHED = Map.of(
            Items.STONE, Items.COBBLESTONE,
            Items.COBBLESTONE, Items.GRAVEL,
            Items.GRAVEL, Items.SAND,
            Items.DEEPSLATE, Items.COBBLED_DEEPSLATE,
            Items.COBBLED_DEEPSLATE, Items.GRAVEL,
            Items.SANDSTONE, Items.SAND,
            Items.RED_SANDSTONE, Items.RED_SAND);

    private record Key(Item item, net.minecraft.core.component.DataComponentPatch components) {
        static Key of(ItemStack stack) {
            return new Key(stack.getItem(), stack.getComponentsPatch());
        }
    }

    /** What a furnace makes of one of each thing: recipes don't change until a reload. */
    private static final Map<Key, Optional<ItemStack>> SMELTED = new HashMap<>();

    private Processing() {}

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        SMELTED.clear();
    }

    /** What one of {@code stack} becomes, through each of {@code steps} in turn: those that change nothing are free, and so are all of them if it ends as it began. */
    public static Made ofOne(ServerLevel level, BlockPos at, ItemStack stack, List<Lens> steps) {
        List<ItemStack> forms = List.of(stack.copyWithCount(1));
        long fe = 0;
        for (Lens step : steps) {
            boolean smelting = step == Lens.SMELT;
            List<ItemStack> made = each(forms, form -> smelting ? smelted(level, at, form) : crushed(level, at, form));
            if (made == null) continue;
            forms = made;
            fe += smelting ? LaserTuning.SMELT_FE_PER_ITEM : LaserTuning.CRUSH_FE_PER_ITEM;
        }
        // Back where it started (glass crushed to sand and smelted again): nothing happened, and nothing's paid.
        if (forms.size() == 1 && ItemStack.matches(forms.getFirst(), stack.copyWithCount(1))) return new Made(forms, 0);
        return new Made(forms, fe);
    }

    /** {@code count} of what one makes, in stacks no bigger than they may be. */
    public static List<ItemStack> times(List<ItemStack> ofOne, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack one : ofOne) {
            for (int total = one.getCount() * count; total > 0; ) {
                int take = Math.min(total, one.getMaxStackSize());
                out.add(one.copyWithCount(take));
                total -= take;
            }
        }
        return out;
    }

    /** Each form through {@code step}, those it does nothing to kept as they are; null if it did nothing to any. */
    @Nullable
    private static List<ItemStack> each(List<ItemStack> forms, java.util.function.Function<ItemStack, List<ItemStack>> step) {
        List<ItemStack> out = new ArrayList<>();
        boolean any = false;
        for (ItemStack form : forms) {
            List<ItemStack> made = step.apply(form);
            if (made == null) {
                out.add(form);
            } else {
                // A form of several made several of each.
                for (ItemStack each : made) out.add(each.copyWithCount(each.getCount() * form.getCount()));
                any = true;
            }
        }
        return any ? out : null;
    }

    /** What crushing one {@code form} makes, or null if nothing crushes it. */
    @Nullable
    private static List<ItemStack> crushed(ServerLevel level, BlockPos at, ItemStack form) {
        if (form.is(ModItems.UNREALISED_MATTER.get())) {
            for (RecipeType<?> type : crushers()) {
                ItemStack stepped = stepped(level, at, form, type);
                if (stepped != null) return List.of(stepped);
            }
            return null;
        }
        Item plain = CRUSHED.get(form.getItem());
        if (plain != null) return List.of(new ItemStack(plain));
        for (RecipeType<?> type : crushers()) {
            List<ItemStack> made = MatterSteps.step(level, at, type, form);
            if (made != null && !made.isEmpty()) return made;
        }
        return null;
    }

    /** What smelting one {@code form} makes, or null if a furnace can't. */
    @Nullable
    private static List<ItemStack> smelted(ServerLevel level, BlockPos at, ItemStack form) {
        if (form.is(ModItems.UNREALISED_MATTER.get())) {
            ItemStack stepped = stepped(level, at, form, RecipeType.SMELTING);
            return stepped == null ? null : List.of(stepped);
        }
        ItemStack made = SMELTED.computeIfAbsent(Key.of(form), key -> {
            SingleRecipeInput input = new SingleRecipeInput(form.copyWithCount(1));
            return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                    .map(holder -> holder.value().assemble(input)).filter(stack -> !stack.isEmpty());
        }).orElse(null);
        return made == null ? null : List.of(made.copy());
    }

    /** Matter with {@code type}'s step after its history, if there's room and the step gives some ore a new form. */
    @Nullable
    private static ItemStack stepped(ServerLevel level, BlockPos at, ItemStack matter, RecipeType<?> type) {
        MatterHistory history = MatterHistory.of(matter);
        Identifier step = MatterSteps.idOf(type);
        if (history.full() || step == null) return null;
        MatterHistory next = history.then(step);
        return MatterSteps.lastStepAdds(level, at, next) ? next.applyTo(matter.copyWithCount(1)) : null;
    }

    /** The pack's crushers' recipe types, in the config's order, those it has. */
    private static List<RecipeType<?>> crushers() {
        List<RecipeType<?>> types = new ArrayList<>();
        for (String id : Config.crushingRecipeTypes()) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) continue;
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(parsed);
            if (type != null && BuiltInRegistries.RECIPE_TYPE.containsKey(parsed)) types.add(type);
        }
        return types;
    }
}
