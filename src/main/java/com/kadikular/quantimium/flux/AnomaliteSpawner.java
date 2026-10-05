package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rare once-per-second rolls over a few existing block entities in an active anomaly chunk. */
public final class AnomaliteSpawner {

    private record Candidate(BlockPos host, long storedFe, List<Direction> openFaces) {}

    private AnomaliteSpawner() {}

    public static void tick(ServerLevel level, LevelChunk chunk, ChunkFlux data) {
        if (!Config.anomaliteEnabled() || data == null) return;
        FluxBand band = FluxBand.of(data.anomaly());
        if (band.ordinal() < FluxBand.HIGH.ordinal() || data.isContained(level.getGameTime())) return;

        RandomSource random = level.getRandom();
        if (random.nextDouble() >= Config.anomaliteSpawnChance(band)) return;

        List<BlockEntity> blockEntities = List.copyOf(chunk.getBlockEntities().values());
        if (blockEntities.isEmpty()) return;

        int samples = Math.min(Config.anomaliteHostSamples(), blockEntities.size());
        Set<Integer> sampledIndices = new HashSet<>(samples);
        List<Candidate> candidates = new ArrayList<>(samples);
        while (sampledIndices.size() < samples) {
            int index = random.nextInt(blockEntities.size());
            if (!sampledIndices.add(index)) continue;
            BlockEntity blockEntity = blockEntities.get(index);
            Candidate candidate = inspect(level, blockEntity.getBlockPos());
            if (candidate != null) candidates.add(candidate);
        }
        if (candidates.isEmpty()) return;

        Candidate chosen = weightedChoice(candidates, random);
        Direction outward = chosen.openFaces().get(random.nextInt(chosen.openFaces().size()));
        BlockPos crystalPos = chosen.host().relative(outward);
        level.setBlock(crystalPos, ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(AnomaliteCrystalBlock.FACING, outward)
                .setValue(AnomaliteCrystalBlock.AGE, 0), 3);
    }

    private static Candidate inspect(ServerLevel level, BlockPos host) {
        if (level.getBlockState(host).is(ModBlocks.BUDDING_ANOMALITE.get())) return null;
        // A Projector works in exactly the anomaly that grows crystals, and one on its face stops it.
        if (level.getBlockState(host).is(ModBlocks.DECOHERENCE_PROJECTOR.get())) return null;
        AnomaliteEnergy.Access energy = AnomaliteEnergy.find(level, host);
        if (energy == null || !energy.canExtract()) return null;
        long stored = energy.storedFe();
        if (stored <= 0) return null;

        List<Direction> open = new ArrayList<>(6);
        for (Direction face : Direction.values()) {
            BlockPos crystalPos = host.relative(face);
            if (level.isLoaded(crystalPos) && canReplaceWithCrystal(level, crystalPos)) {
                open.add(face);
            }
        }
        return open.isEmpty() ? null : new Candidate(host.immutable(), stored, List.copyOf(open));
    }

    public static boolean canReplaceWithCrystal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(ModTags.MIRROR_FLORA);
    }

    private static Candidate weightedChoice(List<Candidate> candidates, RandomSource random) {
        double total = 0.0;
        for (Candidate candidate : candidates) total += candidate.storedFe();
        double roll = random.nextDouble() * total;
        for (Candidate candidate : candidates) {
            roll -= candidate.storedFe();
            if (roll < 0.0) return candidate;
        }
        return candidates.getLast();
    }
}
