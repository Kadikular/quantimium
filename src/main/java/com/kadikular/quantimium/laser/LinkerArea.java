package com.kadikular.quantimium.laser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * The Laser Linker's area mode: whether it's on, and the two corners picked so far. Use the Linker on an
 * area prism once both are picked to give it that area.
 */
public record LinkerArea(boolean on, Optional<BlockPos> first, Optional<BlockPos> second) {

    public static final LinkerArea OFF = new LinkerArea(false, Optional.empty(), Optional.empty());

    public static final Codec<LinkerArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("on").forGetter(LinkerArea::on),
            BlockPos.CODEC.optionalFieldOf("first").forGetter(LinkerArea::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(LinkerArea::second)
    ).apply(instance, LinkerArea::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerArea> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LinkerArea::on,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), LinkerArea::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), LinkerArea::second,
            LinkerArea::new);

    /** The next corner picked: the first again once both are. */
    public LinkerArea pick(BlockPos pos) {
        if (first.isEmpty() || second.isPresent()) return new LinkerArea(true, Optional.of(pos.immutable()), Optional.empty());
        return new LinkerArea(true, first, Optional.of(pos.immutable()));
    }
}
