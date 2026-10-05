package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.network.FieldMapPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sends the field to world maps. A client with JourneyMap asks for it
 * ({@link com.kadikular.quantimium.network.FieldMapRequestPayload}), and gets every chunk holding a
 * field within {@link #RADIUS} chunks every few seconds. Only active chunks are read, so it costs
 * next to nothing away from a base.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class FieldMapSync {

    /** Chunks out from the player: a large base and the land round it, at a map's usual zoom. */
    public static final int RADIUS = 64;
    /** Chunks sent at most, nearest first. */
    static final int MAX_CHUNKS = 4096;
    private static final int INTERVAL = 100;

    private static final Set<UUID> WANTED = new HashSet<>();

    private FieldMapSync() {}

    public static void request(ServerPlayer player, boolean wanted) {
        if (wanted) {
            WANTED.add(player.getUUID());
            PacketDistributor.sendToPlayer(player, snapshot(player.level(), player.chunkPosition()));
        } else {
            WANTED.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !WANTED.contains(player.getUUID())) return;
        if ((player.level().getGameTime() + player.getId()) % INTERVAL != 0) return;
        PacketDistributor.sendToPlayer(player, snapshot(player.level(), player.chunkPosition()));
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && WANTED.contains(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, snapshot(player.level(), player.chunkPosition()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WANTED.remove(event.getEntity().getUUID());
    }

    /** The field in {@code level} within {@link #RADIUS} chunks of {@code centre}, as it would be sent. */
    public static FieldMapPayload snapshot(ServerLevel level, ChunkPos centre) {
        List<ChunkPos> near = new ArrayList<>();
        for (ChunkPos pos : FluxEvents.activeIn(level)) {
            if (Math.abs(pos.x() - centre.x()) <= RADIUS && Math.abs(pos.z() - centre.z()) <= RADIUS) near.add(pos);
        }
        if (near.size() > MAX_CHUNKS) {
            near.sort((a, b) -> Integer.compare(a.distanceSquared(centre), b.distanceSquared(centre)));
            near = near.subList(0, MAX_CHUNKS);
        }
        long[] chunks = new long[near.size()];
        float[] flux = new float[near.size()];
        float[] anomaly = new float[near.size()];
        float[] load = new float[near.size()];
        boolean[] contained = new boolean[near.size()];
        int count = 0;
        for (ChunkPos pos : near) {
            BlockPos middle = pos.getMiddleBlockPosition(level.getSeaLevel());
            QuantumFlux.Neighbourhood field = QuantumFlux.chunk(level, middle);
            if (field.flux() < FieldModel.SPREAD_FLOOR && field.anomaly() < FieldModel.SPREAD_FLOOR) continue;
            chunks[count] = pos.pack();
            flux[count] = (float) field.flux();
            anomaly[count] = (float) field.anomaly();
            load[count] = QuantumFlux.chunkShielded(level, middle) ? (float) QuantumFlux.chunkLoad(level, middle) : -1.0f;
            contained[count] = QuantumFlux.chunkContained(level, middle);
            count++;
        }
        return new FieldMapPayload(Arrays.copyOf(chunks, count), Arrays.copyOf(flux, count),
                Arrays.copyOf(anomaly, count), Arrays.copyOf(load, count),
                Arrays.copyOf(contained, count));
    }
}
