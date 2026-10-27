package com.kadikular.quantimium.laser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * A Redstone Lens's settings, as an AE2 level emitter's: when it gives its signal ({@link Mode}), how strong (1 to
 * 15), whether strongly (through the block it powers, as a lever) or weakly (the block only, as dust), and for a count,
 * of which item ({@code item}) and how many ({@code count}), counted in the Laser Ports up the light.
 */
public record RedstoneSettings(Mode mode, int level, ItemStack item, int count, boolean strong) {

    public static final int MAX_COUNT = 1_000_000_000;
    public static final RedstoneSettings DEFAULT = new RedstoneSettings(Mode.ALWAYS, 15, ItemStack.EMPTY, 1, true);

    public RedstoneSettings {
        level = Math.clamp(level, 1, 15);
        count = Math.clamp(count, 0, MAX_COUNT);
        item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
    }

    /** When it gives its signal. */
    public enum Mode implements StringRepresentable {
        /** While up the light holds fewer than the count. */
        BELOW,
        /** While up the light holds the count or more. */
        AT_LEAST,
        /** Always. */
        ALWAYS,
        /** The signal its own block gets, as strong, sent on down the light. */
        FORWARD;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** Whether it counts an item. */
        public boolean counts() {
            return this == BELOW || this == AT_LEAST;
        }
    }

    public static final Codec<RedstoneSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Mode.CODEC.optionalFieldOf("mode", Mode.ALWAYS).forGetter(RedstoneSettings::mode),
            Codec.INT.optionalFieldOf("level", 15).forGetter(RedstoneSettings::level),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(RedstoneSettings::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(RedstoneSettings::count),
            Codec.BOOL.optionalFieldOf("strong", true).forGetter(RedstoneSettings::strong)
    ).apply(instance, RedstoneSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RedstoneSettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public RedstoneSettings withMode(Mode mode) {
        return new RedstoneSettings(mode, level, item, count, strong);
    }

    public RedstoneSettings withLevel(int level) {
        return new RedstoneSettings(mode, level, item, count, strong);
    }

    public RedstoneSettings withItem(ItemStack item) {
        return new RedstoneSettings(mode, level, item, count, strong);
    }

    public RedstoneSettings withCount(int count) {
        return new RedstoneSettings(mode, level, item, count, strong);
    }

    public RedstoneSettings withStrong(boolean strong) {
        return new RedstoneSettings(mode, level, item, count, strong);
    }

    /** The signal for {@code held} of its item up the light: its level, or 0. */
    public int signalFor(long held) {
        return switch (mode) {
            case BELOW -> held < count ? level : 0;
            case AT_LEAST -> held >= count ? level : 0;
            default -> level;
        };
    }

    /** By value: an {@link ItemStack} only equals itself. */
    @Override
    public boolean equals(Object other) {
        return other instanceof RedstoneSettings that && mode == that.mode && level == that.level && count == that.count && strong == that.strong
                && ItemStack.isSameItemSameComponents(item, that.item);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(mode, level, count, strong, ItemStack.hashItemAndComponents(item));
    }
}
