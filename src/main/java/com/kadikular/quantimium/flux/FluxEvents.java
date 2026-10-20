package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Drains flux on loaded chunks that currently hold a field. {@link ChunkMap#getChunks()} is not
 * public, so we track active positions on emit / chunk load instead of scanning the whole map.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class FluxEvents {

    private record ChunkKey(ResourceKey<Level> dimension, long pos) {}

    private static final Map<ChunkKey, Boolean> ACTIVE = new ConcurrentHashMap<>();
    private static final Queue<ChunkKey> PENDING_FLORA_MIGRATION = new ConcurrentLinkedQueue<>();

    private FluxEvents() {}

    /** Puts a chunk in the set the field is stepped over; it drops out again once empty. */
    public static void markActive(ServerLevel level, ChunkPos pos) {
        ACTIVE.put(new ChunkKey(level.dimension(), pos.pack()), Boolean.TRUE);
    }

    /** The active chunks in {@code level}: every chunk holding a field there that is loaded. */
    public static List<ChunkPos> activeIn(ServerLevel level) {
        List<ChunkPos> positions = new ArrayList<>();
        for (ChunkKey key : ACTIVE.keySet()) {
            if (key.dimension() == level.dimension()) positions.add(ChunkPos.unpack(key.pos()));
        }
        return positions;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!event.isNewChunk() && hasLegacyFlora(chunk)) {
            PENDING_FLORA_MIGRATION.add(new ChunkKey(level.dimension(), chunk.getPos().pack()));
        }
        ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
        boolean seeded = MirrorFloraSpawner.seedBaseline(level, chunk);
        MirrorFloraData flora = chunk.getExistingData(ModAttachments.MIRROR_FLORA).orElse(null);
        if (data != null && !data.isEmpty() || flora != null && !flora.isEmpty() || seeded) {
            markActive(level, chunk.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ACTIVE.remove(new ChunkKey(level.dimension(), event.getChunk().getPos().pack()));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        PENDING_FLORA_MIGRATION.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        drainFloraMigration(server);
        if (ACTIVE.isEmpty()) return;
        // The field moves once a second, all together; each chunk's own work (spawns, flora) is spread over the
        // second, a twentieth of the chunks a tick, so it's never all in one tick.
        int slice = server.getTickCount() % 20;
        if (slice == 0) stepFields(server);

        Map<ResourceKey<Level>, List<ChunkPos>> phased = phasedPlayers(server);
        Iterator<Map.Entry<ChunkKey, Boolean>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            ChunkKey key = iterator.next().getKey();
            if (Math.floorMod(key.hashCode(), 20) != slice) continue;
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            ChunkPos pos = ChunkPos.unpack(key.pos());
            if (!level.hasChunk(pos.x(), pos.z())) {
                iterator.remove();
                continue;
            }
            LevelChunk chunk = level.getChunk(pos.x(), pos.z());
            QuantumFlux.catchUpDrain(level, chunk);
            ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
            MirrorFloraData flora = chunk.getExistingData(ModAttachments.MIRROR_FLORA).orElse(null);
            boolean fieldEmpty = data == null || data.isEmpty();
            boolean floraEmpty = flora == null || flora.isEmpty();
            if (fieldEmpty && floraEmpty) {
                iterator.remove();
            } else {
                if (!fieldEmpty) {
                    AnomaliteSpawner.tick(level, chunk, data);
                    AnomalyEntrySpawner.consider(level, chunk, data);
                }
                tickFlora(server, level, chunk, key, fieldEmpty ? new ChunkFlux() : data, phased.getOrDefault(key.dimension(), List.of()));
                flora = chunk.getExistingData(ModAttachments.MIRROR_FLORA).orElse(null);
                if (fieldEmpty && (flora == null || flora.isEmpty())) iterator.remove();
            }
        }
        // The second's entry candidates, from every slice, rolled together at its end.
        if (slice == 19) AnomalyEntrySpawner.flush();
    }

    /** How near a player in the mirror flora grows at its own pace, in chunks; farther out, slowly, in the background. */
    private static final int FLORA_NEAR_CHUNKS = 4;
    /** How often a chunk's flora grows with no one in the mirror near it, in seconds. */
    private static final int FLORA_SLOW_SECONDS = 30;

    /** Where the players in the mirror are, by dimension, as chunks. */
    private static Map<ResourceKey<Level>, List<ChunkPos>> phasedPlayers(MinecraftServer server) {
        Map<ResourceKey<Level>, List<ChunkPos>> phased = new HashMap<>();
        for (var player : server.getPlayerList().getPlayers()) {
            if (com.kadikular.quantimium.phase.MirrorPhase.isPhased(player)) {
                phased.computeIfAbsent(player.level().dimension(), d -> new ArrayList<>()).add(player.chunkPosition());
            }
        }
        return phased;
    }

    /**
     * A chunk's mirror flora, seen only from the mirror: near a player in it, every second as it always was; with no
     * one there, every half minute, in the background ({@link com.kadikular.quantimium.work.WorkQueue}), last of all.
     */
    private static void tickFlora(MinecraftServer server, ServerLevel level, LevelChunk chunk, ChunkKey key, ChunkFlux data, List<ChunkPos> phased) {
        ChunkPos pos = chunk.getPos();
        for (ChunkPos near : phased) {
            if (Math.max(Math.abs(near.x() - pos.x()), Math.abs(near.z() - pos.z())) <= FLORA_NEAR_CHUNKS) {
                MirrorFloraSpawner.tick(level, chunk, data);
                return;
            }
        }
        if (server.getTickCount() / 20 % FLORA_SLOW_SECONDS != Math.floorMod(key.hashCode() / 20, FLORA_SLOW_SECONDS)) return;
        com.kadikular.quantimium.work.WorkQueue.submit(key, com.kadikular.quantimium.work.WorkQueue.BACKGROUND, () -> {
            if (!level.hasChunk(pos.x(), pos.z())) return true;
            LevelChunk now = level.getChunk(pos.x(), pos.z());
            ChunkFlux field = now.getExistingData(ModAttachments.CHUNK_FLUX).orElse(new ChunkFlux());
            MirrorFloraSpawner.tick(level, now, field);
            return true;
        });
    }

    /** Field Model 2.0: one second of release, coupling, decay and diffusion, dimension by dimension. */
    private static void stepFields(MinecraftServer server) {
        Map<ResourceKey<Level>, List<ChunkPos>> byDimension = new HashMap<>();
        for (ChunkKey key : ACTIVE.keySet()) {
            byDimension.computeIfAbsent(key.dimension(), d -> new ArrayList<>()).add(ChunkPos.unpack(key.pos()));
        }
        byDimension.forEach((dimension, positions) -> {
            ServerLevel level = server.getLevel(dimension);
            if (level != null) FieldModel.step(level, positions);
        });
    }

    private static boolean hasLegacyFlora(LevelChunk chunk) {
        for (var section : chunk.getSections()) {
            if (!section.hasOnlyAir() && section.maybeHas(state -> state.is(ModTags.MIRROR_FLORA))) {
                return true;
            }
        }
        return false;
    }

    private static void drainFloraMigration(MinecraftServer server) {
        ChunkKey key;
        int budget = 8;
        while (budget-- > 0 && (key = PENDING_FLORA_MIGRATION.poll()) != null) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            ChunkPos pos = ChunkPos.unpack(key.pos());
            if (!level.hasChunk(pos.x(), pos.z())) continue;
            LevelChunk chunk = level.getChunk(pos.x(), pos.z());
            if (MirrorFloraMigration.migrate(level, chunk)) markActive(level, pos);
        }
    }
}
