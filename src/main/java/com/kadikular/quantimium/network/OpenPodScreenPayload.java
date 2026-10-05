package com.kadikular.quantimium.network;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything a Superposition Pod's screen shows: the pod itself, and every double its owner has
 * elsewhere, with what a swap there would cost. Sent fresh each time the screen opens or changes.
 */
public record OpenPodScreenPayload(BlockPos pos, String name, int energy, int capacity, boolean formed, int modules,
                                   boolean anchor, boolean inside, boolean occupied, int sophons, int maxSophons,
                                   boolean stash, boolean listed, boolean tether, List<Destination> destinations) implements CustomPacketPayload {

    public static final int FLAG_FOLDED = 1;
    public static final int FLAG_ANCHOR = 2;
    public static final int FLAG_RESCUE = 4;
    /** An empty pod reached through this hub's Relay. */
    public static final int FLAG_RELAY = 8;
    /** ... with no spare double to send there. */
    public static final int FLAG_NO_SPARE = 16;
    /** A field double: standing out in the world where its owner tethered away from. */
    public static final int FLAG_FIELD = 32;
    /** A Sophon lying where its double was knocked out: to recover, not to swap into. */
    public static final int FLAG_DROPPED = 64;
    /** Taken by the Veiled: out of reach until it is driven off or held. */
    public static final int FLAG_TAKEN = 128;

    /** One of the owner's doubles. {@code distance} is -1 in another dimension. */
    public record Destination(UUID id, String name, String dimension, BlockPos pos, int distance, int cost, int flags) {
        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        private void write(FriendlyByteBuf buf) {
            UUIDUtil.STREAM_CODEC.encode(buf, id);
            buf.writeUtf(name);
            buf.writeUtf(dimension);
            buf.writeBlockPos(pos);
            buf.writeVarInt(distance + 1);
            buf.writeVarInt(cost);
            buf.writeVarInt(flags);
        }

        private static Destination read(FriendlyByteBuf buf) {
            return new Destination(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readBlockPos(),
                    buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt());
        }
    }

    public static final Type<OpenPodScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "open_pod_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenPodScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeUtf(payload.name);
                buf.writeVarInt(payload.energy);
                buf.writeVarInt(payload.capacity);
                buf.writeBoolean(payload.formed);
                buf.writeVarInt(payload.modules);
                buf.writeBoolean(payload.anchor);
                buf.writeBoolean(payload.inside);
                buf.writeBoolean(payload.occupied);
                buf.writeVarInt(payload.sophons);
                buf.writeVarInt(payload.maxSophons);
                buf.writeBoolean(payload.stash);
                buf.writeBoolean(payload.listed);
                buf.writeBoolean(payload.tether);
                buf.writeVarInt(payload.destinations.size());
                for (Destination destination : payload.destinations) destination.write(buf);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                String name = buf.readUtf();
                int energy = buf.readVarInt();
                int capacity = buf.readVarInt();
                boolean formed = buf.readBoolean();
                int modules = buf.readVarInt();
                boolean anchor = buf.readBoolean();
                boolean inside = buf.readBoolean();
                boolean occupied = buf.readBoolean();
                int sophons = buf.readVarInt();
                int maxSophons = buf.readVarInt();
                boolean stash = buf.readBoolean();
                boolean listed = buf.readBoolean();
                boolean tether = buf.readBoolean();
                int count = buf.readVarInt();
                List<Destination> destinations = new ArrayList<>(count);
                for (int i = 0; i < count; i++) destinations.add(Destination.read(buf));
                return new OpenPodScreenPayload(pos, name, energy, capacity, formed, modules, anchor, inside, occupied,
                        sophons, maxSophons, stash, listed, tether, destinations);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
