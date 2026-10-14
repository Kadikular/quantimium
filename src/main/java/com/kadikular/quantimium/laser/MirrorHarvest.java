package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.MirrorMiteSpawner;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * What light in the mirror takes, and what it costs: a grown Anomalite crystal cut by a Harvest beam, Veil Thread
 * drawn out of a held Veiled by Pickup.
 */
public final class MirrorHarvest {

    /** FE a tick while a crystal is cut. */
    public static final int CRYSTAL_FE_PER_TICK = 60;
    /** Cutting time for one full crystal: fifteen seconds. */
    public static final int CRYSTAL_TICKS = 300;
    /** FE a tick while thread is drawn out of a held Veiled. */
    public static final int THREAD_FE_PER_TICK = 120;
    /** Drawing time for one thread: three minutes. */
    public static final int THREAD_TICKS = 3_600;
    /** Shards from one full crystal, as breaking it gives. */
    public static final int SHARDS = 3;
    /** Anomaly each crystal lets into the chunk. */
    public static final double ANOMALY_PER_CRYSTAL = 15.0;
    /** Chance in a thousand that a crystal lets a mite through, by the chunk's anomaly band. */
    private static final int[] MITE_PER_MILLE = {0, 30, 115, 270, 490};
    /** No more mites get out while this many are already about, so an unwatched farm can't fill up. */
    public static final int MITE_CAP = 4;
    private static final double MITE_CAP_RADIUS = 32.0;

    private MirrorHarvest() {}

    /** The hall whose cell {@code cell} is, if it's holding a Veiled. */
    @Nullable
    public static ContainmentHallBlockEntity hallHolding(Level level, BlockPos cell) {
        for (int below = 1; below <= ContainmentHallStructure.WALL_HEIGHT; below++) {
            if (level.getBlockEntity(cell.below(below)) instanceof ContainmentHallBlockEntity hall) {
                return hall.isOccupied() ? hall : null;
            }
        }
        return null;
    }

    /**
     * What a crystal taken apart through the mirror at {@code at} lets back through: anomaly, and now and then a
     * mirror mite, by the chunk's anomaly, never in a contained chunk or with too many about. Whether a mite got out.
     */
    public static boolean leak(ServerLevel level, BlockPos at) {
        if (QuantumFlux.chunkContained(level, at)) return false;
        QuantumFlux.emit(level, at, 0.0, ANOMALY_PER_CRYSTAL);
        FluxBand band = QuantumFlux.chunkAnomalyBand(level, at);
        if (level.getRandom().nextInt(1000) >= MITE_PER_MILLE[band.ordinal()]) return false;
        if (level.getEntitiesOfClass(MirrorEndermite.class, new AABB(at).inflate(MITE_CAP_RADIUS)).size() >= MITE_CAP) return false;
        if (MirrorMiteSpawner.spawnNear(level, at, mite -> mite.setBreached(true)) == null) return false;
        level.playSound(null, at, SoundEvents.ENDERMITE_AMBIENT, SoundSource.HOSTILE, 0.8f, 0.7f);
        return true;
    }
}
