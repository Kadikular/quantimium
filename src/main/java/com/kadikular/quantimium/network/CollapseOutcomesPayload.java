package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.recipe.observation.CollapseOutcome;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The Observation Chamber's weighted collapse outputs. Loot tables never reach the client, so
 * recipe viewers would otherwise have nothing to show for the chamber.
 */
public record CollapseOutcomesPayload(List<CollapseOutcome> outcomes) implements CustomPacketPayload {
    public static final Type<CollapseOutcomesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "collapse_outcomes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CollapseOutcomesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    CollapseOutcome.LIST_STREAM_CODEC, CollapseOutcomesPayload::outcomes,
                    CollapseOutcomesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
