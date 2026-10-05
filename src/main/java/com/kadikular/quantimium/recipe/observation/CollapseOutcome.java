package com.kadikular.quantimium.recipe.observation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One thing a Chamber can collapse Unrealised Matter into, for a recipe viewer: what it drops, its
 * rarity (a {@link com.kadikular.quantimium.unrealised.Rarity} ordinal, or -1 from the hand-made loot
 * table) and its chance in each flux band, Low to Singularity.
 */
public record CollapseOutcome(ItemStack stack, int rarity, List<Float> chances) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CollapseOutcome> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, CollapseOutcome::stack,
                    ByteBufCodecs.VAR_INT, CollapseOutcome::rarity,
                    ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), CollapseOutcome::chances,
                    CollapseOutcome::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<CollapseOutcome>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    /** The lowest band (an ordinal) it can come out in, or -1 if none. */
    public int firstBand() {
        for (int band = 0; band < chances.size(); band++) {
            if (chances.get(band) > 0.0f) return band;
        }
        return -1;
    }
}
