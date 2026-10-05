// Path: src/main/java/com/kadikular/quantimium/block/entity/simulation/ItemStateDelta.java
package com.kadikular.quantimium.block.entity.simulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Copies the state a machine wrote onto a phantom stack back onto the real stack in the input grid.
 *
 * <p>Only components the machine actually touched during the cycle are carried over, found by
 * comparing the phantom as it was projected against the phantom as the machine left it. Everything
 * else on the real stack is left alone.
 *
 * <p>Progress counters are numbers, so a component whose value serializes to a number (or to a tree
 * of them) has its difference multiplied by the batch size: a data model that gained 1 experience per
 * simulation gains N for a batch of N. Components that are not numeric cannot be multiplied
 * meaningfully, so the machine's end state is adopted verbatim.
 */
public final class ItemStateDelta {

    private ItemStateDelta() {}

    /**
     * @param real     the stack in the simulator grid, mutated in place
     * @param baseline the phantom as it was handed to the machine, or {@code null} if unknown
     * @param phantom  the phantom copy as the machine left it
     * @param crafts   how many cycles the batch stands for
     */
    public static void apply(ItemStack real, @Nullable ItemStack baseline, ItemStack phantom, int crafts,
                             @Nullable HolderLookup.Provider registries) {
        Set<DataComponentType<?>> touched = new LinkedHashSet<>();
        collectTypes(touched, phantom);
        if (baseline != null) collectTypes(touched, baseline);

        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        boolean any = false;

        for (DataComponentType<?> type : touched) {
            // Durability is scaled by the engine, which also has to break the tool at zero.
            if (type == DataComponents.DAMAGE) continue;

            Object before = baseline == null ? null : baseline.get(type);
            Object after = phantom.get(type);
            if (Objects.equals(before, after)) continue;

            if (after == null) {
                remove(builder, type);
            } else {
                set(builder, type, scale(type, real.get(type), before, after, crafts, registries));
            }
            any = true;
        }

        if (any) real.applyComponents(builder.build());
    }

    private static void collectTypes(Set<DataComponentType<?>> types, ItemStack stack) {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            types.add(entry.getKey());
        }
    }

    /**
     * Multiplies the change the machine made by the batch size, applied on top of whatever the real
     * stack holds now. Falls back to the machine's value whenever the component cannot be treated as
     * a number.
     */
    private static Object scale(DataComponentType<?> type, @Nullable Object realValue, @Nullable Object before,
                                Object after, int crafts, @Nullable HolderLookup.Provider registries) {
        if (crafts <= 1 || before == null) return after;

        Codec<?> codec = type.codec();
        if (codec == null) return after;

        DynamicOps<Tag> ops = registries == null
                ? NbtOps.INSTANCE
                : registries.createSerializationContext(NbtOps.INSTANCE);

        Tag beforeTag = encode(codec, before, ops);
        Tag afterTag = encode(codec, after, ops);
        Tag realTag = realValue == null ? beforeTag : encode(codec, realValue, ops);
        if (beforeTag == null || afterTag == null || realTag == null) return after;

        Tag scaled = scaleTag(realTag, beforeTag, afterTag, crafts);
        if (scaled == null) return after;

        Object decoded = decode(codec, scaled, ops);
        return decoded == null ? after : decoded;
    }

    /**
     * Numeric leaves become {@code real + (after - before) * crafts}; compounds are walked key by
     * key. Returns {@code null} when the shape holds nothing that can be scaled.
     */
    @Nullable
    private static Tag scaleTag(Tag real, Tag before, Tag after, int crafts) {
        if (real instanceof NumericTag realNumber && before instanceof NumericTag beforeNumber
                && after instanceof NumericTag afterNumber) {
            long value = realNumber.longValue() + (afterNumber.longValue() - beforeNumber.longValue()) * crafts;
            return numberLike(after, value);
        }

        if (real instanceof CompoundTag realCompound && before instanceof CompoundTag beforeCompound
                && after instanceof CompoundTag afterCompound) {
            CompoundTag result = realCompound.copy();
            boolean scaledAnything = false;

            for (String key : afterCompound.keySet()) {
                Tag afterChild = afterCompound.get(key);
                Tag beforeChild = beforeCompound.get(key);
                Tag realChild = result.get(key);
                if (afterChild == null) continue;

                Tag child = beforeChild == null || realChild == null
                        ? null
                        : scaleTag(realChild, beforeChild, afterChild, crafts);
                if (child == null) {
                    result.put(key, afterChild.copy());
                } else {
                    result.put(key, child);
                    scaledAnything = true;
                }
            }
            return scaledAnything ? result : null;
        }

        return null;
    }

    private static Tag numberLike(Tag template, long value) {
        return switch (template.getId()) {
            case Tag.TAG_BYTE -> ByteTag.valueOf((byte) value);
            case Tag.TAG_SHORT -> ShortTag.valueOf((short) value);
            case Tag.TAG_INT -> IntTag.valueOf((int) value);
            case Tag.TAG_LONG -> LongTag.valueOf(value);
            case Tag.TAG_FLOAT -> FloatTag.valueOf(value);
            case Tag.TAG_DOUBLE -> DoubleTag.valueOf(value);
            default -> IntTag.valueOf((int) value);
        };
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Tag encode(Codec<?> codec, Object value, DynamicOps<Tag> ops) {
        try {
            Object encoded = ((Codec) codec).encodeStart(ops, value).result().orElse(null);
            return encoded instanceof Tag tag ? tag : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Object decode(Codec<?> codec, Tag tag, DynamicOps<Tag> ops) {
        try {
            return codec.parse(ops, tag).result().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(DataComponentPatch.Builder builder, DataComponentType<?> type, Object value) {
        builder.set((DataComponentType) type, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void remove(DataComponentPatch.Builder builder, DataComponentType<?> type) {
        builder.remove((DataComponentType) type);
    }
}
