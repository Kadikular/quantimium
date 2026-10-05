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
        if (server.getTickCount() % 20 != 0 || ACTIVE.isEmpty()) return;
        stepFields(server);

        Iterator<Map.Entry<ChunkKey, Boolean>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            ChunkKey key = iterator.next().getKey();
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
                MirrorFloraSpawner.tick(level, chunk, fieldEmpty ? new ChunkFlux() : data);
                flora = chunk.getExistingData(ModAttachments.MIRROR_FLORA).orElse(null);
                if (fieldEmpty && (flora == null || flora.isEmpty())) iterator.remove();
            }
        }
        AnomalyEntrySpawner.flush();
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
