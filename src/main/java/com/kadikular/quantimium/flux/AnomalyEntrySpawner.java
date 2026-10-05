package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.phase.WorldRiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rare real-world entry tears in uncontained Medium+ fields. Each band rolls once per dimension
 * per second among its own loaded chunks, so a Singularity hall does not starve a Medium outpost
 * (and a 5×5 factory still does not spawn 25× as often). Mean wait is 30 min at Medium, then
 * halves each band. Contained chunks stay quiet.
 */
public final class AnomalyEntrySpawner {

    private record Candidate(ServerLevel level, BlockPos origin, FluxBand band) {}

    private static final List<Candidate> PENDING = new ArrayList<>();

    private AnomalyEntrySpawner() {}

    public static void consider(ServerLevel level, LevelChunk chunk, ChunkFlux data) {
        if (!Config.entryRiftsEnabled() || data == null) return;
        FluxBand band = FluxBand.of(data.anomaly());
        if (band.ordinal() < FluxBand.MEDIUM.ordinal() || data.isContained(level.getGameTime())) {
            return;
        }
        PENDING.add(new Candidate(level, originInChunk(level, chunk), band));
    }

    public static void flush() {
        if (PENDING.isEmpty()) return;
        Map<ServerLevel, List<Candidate>> byLevel = new IdentityHashMap<>();
        for (Candidate candidate : PENDING) {
            byLevel.computeIfAbsent(candidate.level(), ignored -> new ArrayList<>()).add(candidate);
        }
        PENDING.clear();
        for (Map.Entry<ServerLevel, List<Candidate>> entry : byLevel.entrySet()) {
            roll(entry.getKey(), entry.getValue());
        }
    }

    private static void roll(ServerLevel level, List<Candidate> candidates) {
        Map<FluxBand, List<Candidate>> byBand = new EnumMap<>(FluxBand.class);
        for (Candidate candidate : candidates) {
            byBand.computeIfAbsent(candidate.band(), ignored -> new ArrayList<>()).add(candidate);
        }
        for (Map.Entry<FluxBand, List<Candidate>> entry : byBand.entrySet()) {
            if (level.getRandom().nextDouble() >= Config.entryRiftSpawnChance(entry.getKey())) continue;
            List<Candidate> pool = entry.getValue();
            Candidate pick = pool.get(level.getRandom().nextInt(pool.size()));
            WorldRiftManager.spawnRandomEntry(level, pick.origin());
        }
    }

    private static BlockPos originInChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        for (ServerPlayer player : level.players()) {
            if (player.chunkPosition().equals(pos)) return player.blockPosition();
        }
        int x = pos.getMinBlockX() + level.getRandom().nextInt(16);
        int z = pos.getMinBlockZ() + level.getRandom().nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
