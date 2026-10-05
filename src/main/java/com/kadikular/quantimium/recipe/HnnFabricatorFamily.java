package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hostile Neural Networks' Loot Fabricator as a catalyst. HNN has no recipes, only data models, each
 * with a fixed list of drops a prediction can be fabricated into; the Fabricator asks you to choose
 * one in its own screen. Here every drop of every model in the grid is its own craft in the preview,
 * so choosing is simply taking the ghost you want: one prediction in, that drop out.
 *
 * <p>Priced as the Fabricator itself works: HNN's {@code fabPowerCost} FE/t (256 by default, read
 * live) for its {@value #FAB_TICKS}-tick run, times the instant-craft multiplier. Everything is
 * reached by id and reflection, so there is no compile-time dependency on HNN; if HNN changes shape,
 * this offers nothing rather than failing.
 */
public final class HnnFabricatorFamily {

    private static final Identifier FABRICATOR = Identifier.parse("hostilenetworks:loot_fabricator");
    private static final Identifier PREDICTION = Identifier.parse("hostilenetworks:prediction");
    private static final Identifier MODEL_COMPONENT = Identifier.parse("hostilenetworks:data_model");
    private static final String CONFIG = "dev.shadowsoffire.hostilenetworks.HostileConfig";
    /** Ticks the Loot Fabricator takes per prediction (LootFabTileEntity). */
    public static final int FAB_TICKS = 60;
    /** HNN's default {@code fabPowerCost}, used if its config cannot be read. */
    public static final int DEFAULT_FAB_FE_PER_TICK = 256;

    private HnnFabricatorFamily() {}

    public static boolean handles(ItemStack catalyst) {
        return !catalyst.isEmpty() && FABRICATOR.equals(BuiltInRegistries.ITEM.getKey(catalyst.getItem()));
    }

    /** One craft per drop of every data model a prediction in the pool belongs to. */
    public static List<ResolvedCraft> match(IngredientPool pool) {
        List<ResolvedCraft> crafts = new ArrayList<>();
        Item prediction = BuiltInRegistries.ITEM.getValue(PREDICTION);
        DataComponentType<?> modelType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(MODEL_COMPONENT);
        if (modelType == null) return crafts;

        // The distinct models on offer, each with the drops it can be fabricated into.
        Map<Identifier, Object> models = new LinkedHashMap<>();
        for (IngredientPool.Offer offer : pool.offers()) {
            ItemStack stack = offer.stack();
            if (!stack.is(prediction)) continue;
            Object holder = stack.get(modelType);
            Identifier id = modelId(holder);
            if (id != null) models.putIfAbsent(id, holder);
        }

        int cost = CraftEnergy.flat((long) fabFePerTick() * FAB_TICKS);
        for (Map.Entry<Identifier, Object> entry : models.entrySet()) {
            Object holder = entry.getValue();
            List<ItemStack> drops = fabDrops(holder);
            for (int index = 0; index < drops.size(); index++) {
                ItemStack drop = drops.get(index);
                if (drop.isEmpty()) continue;
                List<IngredientPool.Withdrawal> taken = pool.allocator().takeMatching(
                        stack -> stack.is(prediction) && Objects.equals(stack.get(modelType), holder), 1);
                if (taken == null) continue;
                Identifier id = Identifier.fromNamespaceAndPath(Quantimium.MODID,
                        "hnn_loot_fabricator/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath() + "/" + index);
                crafts.add(new ResolvedCraft(id, taken, List.of(drop.copy()), cost));
            }
        }
        return crafts;
    }

    @Nullable
    private static Identifier modelId(@Nullable Object holder) {
        if (holder == null) return null;
        try {
            Method bound = holder.getClass().getMethod("isBound");
            if (!(boolean) bound.invoke(holder)) return null;
            return (Identifier) holder.getClass().getMethod("getId").invoke(holder);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> fabDrops(Object holder) {
        try {
            Object model = holder.getClass().getMethod("get").invoke(holder);
            Object drops = model.getClass().getMethod("fabDrops").invoke(model);
            return drops instanceof List<?> list ? (List<ItemStack>) list : List.of();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return List.of();
        }
    }

    /** HNN's configured Fabricator draw, or its default. */
    public static int fabFePerTick() {
        try {
            Field field = Class.forName(CONFIG).getField("fabPowerCost");
            int value = field.getInt(null);
            return value > 0 ? value : DEFAULT_FAB_FE_PER_TICK;
        } catch (ReflectiveOperationException e) {
            return DEFAULT_FAB_FE_PER_TICK;
        }
    }
}
