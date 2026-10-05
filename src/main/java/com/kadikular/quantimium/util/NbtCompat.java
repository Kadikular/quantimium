package com.kadikular.quantimium.util;

import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the block entities' and entities' {@link CompoundTag} save code to {@link ValueInput} /
 * {@link ValueOutput}. The keys and layout are unchanged, so worlds written before the port load as they were.
 */
public final class NbtCompat {
    private NbtCompat() {}

    public static CompoundTag read(ValueInput in) {
        return in.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
    }

    public static void write(ValueOutput out, CompoundTag tag) {
        out.store(tag);
    }

    @SuppressWarnings("deprecation")
    public static HolderLookup.Provider lookup(ValueInput in) {
        return in.lookup();
    }

    public static HolderLookup.Provider registries(@Nullable Level level) {
        return level != null ? level.registryAccess() : RegistryAccess.EMPTY;
    }

    /** An item stack as the pre-port {@code ItemStack#save} wrote it; empty stacks become an empty tag. */
    public static CompoundTag saveStack(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) return new CompoundTag();
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .result().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast)
                .orElseGet(CompoundTag::new);
    }

    /** The inverse of {@link #saveStack}: an empty or unreadable tag is the empty stack. */
    public static ItemStack parseStack(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }

    public static void putUUID(CompoundTag tag, String key, UUID id) {
        tag.store(key, UUIDUtil.CODEC, id);
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).isPresent();
    }

    @Nullable
    public static UUID getUUID(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC).orElse(null);
    }

    public static void putPos(CompoundTag tag, String key, BlockPos pos) {
        tag.store(key, BlockPos.CODEC, pos);
    }

    public static Optional<BlockPos> getPos(CompoundTag tag, String key) {
        return tag.read(key, BlockPos.CODEC);
    }

    public static <T> void put(CompoundTag tag, String key, Codec<T> codec, T value) {
        tag.store(key, codec, value);
    }

    public static Tag uuidTag(UUID id) {
        return UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, id).result().orElseThrow();
    }

    @Nullable
    public static UUID uuidFrom(Tag tag) {
        return UUIDUtil.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    /** A value as a compound tag through its codec; an unencodable value is an empty tag. */
    public static <T> CompoundTag saveWith(Codec<T> codec, HolderLookup.Provider registries, T value) {
        return codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value)
                .result().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast)
                .orElseGet(CompoundTag::new);
    }

    public static <T> Optional<T> parseWith(Codec<T> codec, HolderLookup.Provider registries, CompoundTag tag) {
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result();
    }

    /** A component as the JSON string the pre-port {@code Component.Serializer.toJson} wrote. */
    public static String componentToJson(Component component, HolderLookup.Provider registries) {
        return ComponentSerialization.CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), component)
                .getOrThrow().toString();
    }

    @Nullable
    public static Component componentFromJson(String json, HolderLookup.Provider registries) {
        try {
            return ComponentSerialization.CODEC
                    .parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json))
                    .result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
