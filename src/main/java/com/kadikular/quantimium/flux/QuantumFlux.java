package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.init.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Static API for Quantum Flux and Anomaly: emit on Quantimium work, pull, contain, and read.
 *
 * <p>Everything that acts on the field acts on a neighbourhood through one falloff, {@link #share}: a
 * machine emits into, pulls from and contains the chunks round it, heaviest in its own. How the field
 * then moves is {@link FieldModel}'s job. Pulls and containment happen once a second, on the same
 * clock as the field.
 *
 * <p>Reading: {@link #sample} is the field at a block, interpolated between the chunks round it, for
 * machines and meters. {@link #chunk} is the chunk's own value, for effects that belong to a chunk
 * (crystals, tears, the surcharge), which also go by whether the chunk is {@linkplain #chunkContained
 * contained}.
 */
public final class QuantumFlux {

    /** FE of Quantimium work per flux emitted, before {@link FieldModel#EMISSION_GAIN}. */
    public static final double FE_PER_FLUX = 1000.0;
    /** Flat flux from non-electric crafts so busy early chains still nudge the field. */
    public static final double NONELECTRIC_EMIT = 1.0;
    /** Chunks out that emission and pulls reach: the 3×3 round a machine. */
    public static final int SOURCE_RADIUS = 1;

    public record Neighbourhood(double flux, double anomaly) {
        public FluxBand fluxBand() {
            return FluxBand.of(flux);
        }

        public FluxBand anomalyBand() {
            return FluxBand.of(anomaly);
        }
    }

    private QuantumFlux() {}

    // ---- the falloff ----

    /**
     * The share of something spread over the square {@code radius} chunks round a machine that lands
     * {@code (dx, dz)} from it: each ring weighs half the one inside it, normalised to 1. Over a 3×3
     * that is 0.2 for the machine's own chunk and 0.1 for each of the other eight.
     */
    public static double share(int dx, int dz, int radius) {
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring > radius) return 0.0;
        double total = 0.0;
        for (int r = 0; r <= radius; r++) total += weight(r) * (r == 0 ? 1 : 8 * r);
        return weight(ring) / total;
    }

    private static double weight(int ring) {
        return 1.0 / (1 << ring);
    }

    // ---- emission ----

    public static void emitFromEnergy(ServerLevel level, BlockPos pos, long feSpent) {
        emitFromEnergy(level, pos, feSpent, 1.0);
    }

    /** As {@link #emitFromEnergy(ServerLevel, BlockPos, long)}, for hardware making {@code efficiency} times the flux. */
    public static void emitFromEnergy(ServerLevel level, BlockPos pos, long feSpent, double efficiency) {
        if (feSpent <= 0 || efficiency <= 0.0) return;
        emit(level, pos, feSpent / FE_PER_FLUX * efficiency, 0.0);
    }

    /** A non-electric craft: a small flat emission. */
    public static void emitNonelectric(ServerLevel level, BlockPos pos) {
        emit(level, pos, NONELECTRIC_EMIT, 0.0);
    }

    public static void emit(ServerLevel level, BlockPos pos, double totalFlux) {
        emit(level, pos, totalFlux, 0.0);
    }

    /**
     * Emits {@code totalFlux} (scaled by {@link FieldModel#EMISSION_GAIN}) and {@code totalAnomaly}
     * (not scaled: a rift's leak, a Foundry's residue) round {@code pos}, and marks it as a source for
     * the Mirror Lens.
     */
    public static void emit(ServerLevel level, BlockPos pos, double totalFlux, double totalAnomaly) {
        emitUnseen(level, pos, totalFlux, totalAnomaly);
        FluxSources.emitted(level, pos, totalFlux, totalAnomaly);
    }

    /**
     * As {@link #emit}, without marking {@code pos} as a source for the Mirror Lens: for emitters that
     * show themselves somewhere better (a rift's leak comes from its tear, not its anchor). It goes
     * into the pending pools of the 3×3 round {@code pos}, to ease in from there.
     */
    public static void emitUnseen(ServerLevel level, BlockPos pos, double totalFlux, double totalAnomaly) {
        if (totalFlux <= 0.0 && totalAnomaly <= 0.0) return;
        double flux = totalFlux * FieldModel.EMISSION_GAIN;
        ChunkPos centre = ChunkPos.containing(pos);
        forEachLoadedChunk(level, pos, SOURCE_RADIUS, chunk -> {
            double share = share(chunk.getPos().x() - centre.x(), chunk.getPos().z() - centre.z(), SOURCE_RADIUS);
            catchUpDrain(level, chunk);
            ChunkFlux data = chunk.getData(ModAttachments.CHUNK_FLUX);
            if (data.lastDrainTick() <= 0L) data.setLastDrainTick(level.getGameTime());
            data.addPending(flux * share, totalAnomaly * share);
            chunk.markUnsaved();
            FluxEvents.markActive(level, chunk.getPos());
        });
    }

    /**
     * Atomically spends flux from the machine's own chunk and optionally leaves anomaly behind.
     * Foundry charge is not a fluid or capability: this is a direct debit from the field.
     */
    public static boolean tryConsumeChunkFlux(ServerLevel level, BlockPos pos,
                                              double requestedFlux, double anomalyProduced) {
        if (requestedFlux < 0.0 || anomalyProduced < 0.0) return false;
        ChunkPos chunkPos = ChunkPos.containing(pos);
        LevelChunk chunk = loaded(level, chunkPos.x(), chunkPos.z());
        if (chunk == null) return false;
        catchUpDrain(level, chunk);
        ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
        if (data == null || data.flux() + 1.0E-9 < requestedFlux) return false;
        data.removeFlux(requestedFlux);
        data.addAnomaly(anomalyProduced);
        chunk.markUnsaved();
        if (!data.isEmpty()) FluxEvents.markActive(level, chunkPos);
        return true;
    }

    // ---- pulls ----

    /**
     * Pulls up to {@code totalPerSecond} flux and as much anomaly from round {@code pos}, spread by
     * {@link #share} over {@code radius}: a second's work, so call it once a second. The field's next
     * step takes it, after the coupling it answers, so it can hold a chunk right down. Returns the
     * anomaly the previous second's pull took (for fragment scavenging): a pull is settled at the
     * step, so it is known a second late, but exactly.
     */
    public static double suppress(ServerLevel level, BlockPos pos, double totalPerSecond, int radius) {
        return pull(level, pos, totalPerSecond, totalPerSecond, radius, 0.0, 0.0)[1];
    }

    /**
     * As {@link #suppress}, but anomaly only. The distinction is the whole point of containment: a base
     * you have made hot stays hot, and only its anomaly goes.
     */
    public static double suppressAnomaly(ServerLevel level, BlockPos pos, double totalPerSecond, int radius) {
        return pull(level, pos, 0.0, totalPerSecond, radius, 0.0, 0.0)[1];
    }

    /**
     * As {@link #suppressAnomaly}, but it brings each chunk down to {@code under} and no further, taking
     * only the excess (up to that chunk's share of {@code totalPerSecond}). It lands on {@code under}
     * and holds there, with no swing past it.
     */
    public static double holdAnomalyUnder(ServerLevel level, BlockPos pos, double totalPerSecond, int radius, double under) {
        return pull(level, pos, 0.0, totalPerSecond, radius, 0.0, under)[1];
    }

    /**
     * As {@link #holdAnomalyUnder}, for flux, and only flux: brings each chunk's flux down to
     * {@code under} and holds it there. Returns the flux the previous second's pull took.
     */
    public static double holdFluxUnder(ServerLevel level, BlockPos pos, double totalPerSecond, int radius, double under) {
        return pull(level, pos, totalPerSecond, 0.0, radius, under, 0.0)[0];
    }

    /** Asks the next step for a second's pull; returns what the last one took, {flux, anomaly}. */
    private static double[] pull(ServerLevel level, BlockPos pos, double fluxTotal, double anomalyTotal, int radius,
                                 double leaveFlux, double leaveAnomaly) {
        double[] took = {0.0, 0.0};
        if (fluxTotal <= 0.0 && anomalyTotal <= 0.0) return took;
        double[] shown = {0.0, 0.0};
        ChunkPos centre = ChunkPos.containing(pos);
        long puller = pos.asLong();
        forEachLoadedChunk(level, pos, radius, chunk -> {
            ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
            if (data == null) return;
            catchUpDrain(level, chunk);
            double share = share(chunk.getPos().x() - centre.x(), chunk.getPos().z() - centre.z(), radius);
            // For the Mirror Lens: roughly what it is pulling now.
            shown[0] += Math.min(fluxTotal * share, Math.max(0.0, data.flux() - leaveFlux));
            shown[1] += Math.min(anomalyTotal * share, Math.max(0.0, data.anomaly() - leaveAnomaly));
            double[] receipt = data.requestPull(puller, fluxTotal * share, anomalyTotal * share, leaveFlux, leaveAnomaly);
            took[0] += receipt[0];
            took[1] += receipt[1];
            FluxEvents.markActive(level, chunk.getPos());
        });
        FluxSources.drew(level, pos, shown[0], shown[1]);
        return took;
    }

    /** The highest flux among the chunks {@code radius} out from {@code pos}. */
    public static double peakFlux(Level level, BlockPos pos, int radius) {
        double peak = 0.0;
        ChunkPos centre = ChunkPos.containing(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                peak = Math.max(peak, chunk(level, new ChunkPos(centre.x() + dx, centre.z() + dz).getMiddleBlockPosition(pos.getY())).flux());
            }
        }
        return peak;
    }

    /** The highest anomaly among the chunks {@code radius} out from {@code pos}: the worst a puller covers. */
    public static double peakAnomaly(Level level, BlockPos pos, int radius) {
        double peak = 0.0;
        ChunkPos centre = ChunkPos.containing(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                peak = Math.max(peak, chunk(level, new ChunkPos(centre.x() + dx, centre.z() + dz).getMiddleBlockPosition(pos.getY())).anomaly());
            }
        }
        return peak;
    }

    // ---- containment ----

    /** The capacity of containment rated for {@code band}: all of that band. */
    public static double capacityFor(FluxBand band) {
        return band.ceiling();
    }

    /** As {@link #contain(ServerLevel, BlockPos, double, double, int)}, leaking the usual {@link FieldModel#LEAK}. */
    public static void contain(ServerLevel level, BlockPos pos, double capacity, int radius) {
        contain(level, pos, capacity, FieldModel.LEAK, radius);
    }

    /** How sharply containment falls off past its core, in chunks: smaller is sharper. */
    public static final double CONTAINMENT_EDGE = 0.25;

    /**
     * Covers the chunks round {@code pos} with {@code capacity}, by straight-line chunk distance on a
     * steep S-curve (see {@link #containmentStrength}): nearly all of it across the square
     * {@code radius} chunks out, half a chunk past that, and next to nothing a chunk further. Past its
     * edge a hot base's field is uncontained, and dangerous. Capacity from several machines adds up.
     * {@code leak} is the share of the flux it holds that still couples into anomaly. Call it once a
     * second while powered.
     */
    public static void contain(ServerLevel level, BlockPos pos, double capacity, double leak, int radius) {
        if (capacity <= 0.0) return;
        long now = level.getGameTime();
        ChunkPos centre = ChunkPos.containing(pos);
        forEachLoadedChunk(level, pos, radius + 2, chunk -> {
            double strength = containmentStrength(chunk.getPos().x() - centre.x(), chunk.getPos().z() - centre.z(), radius);
            if (strength >= 0.005) chunk.getData(ModAttachments.CHUNK_FLUX).addCapacity(now, capacity * strength, leak);
        });
    }

    /**
     * The share of its capacity containment gives a chunk {@code (dx, dz)} from it, with a core
     * {@code radius} chunks out: a logistic curve on straight-line distance, half strength at
     * {@code radius + 1}, normalised to 1 at its own chunk. For the 3×3: 98% at the sides, 91% at the
     * corners, 50% two chunks out, 3.5% at the far corners of the 5×5.
     */
    public static double containmentStrength(int dx, int dz, int radius) {
        return logistic(Math.hypot(dx, dz), radius + 1.0) / logistic(0.0, radius + 1.0);
    }

    private static double logistic(double distance, double half) {
        return 1.0 / (1.0 + Math.exp((distance - half) / CONTAINMENT_EDGE));
    }

    /** Coverage present, however loaded. */
    public static boolean chunkShielded(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        long now = gameTime(level);
        return data != null && now > 0L && data.isShielded(now);
    }

    /** Covered, with the flux within capacity: nothing destructive happens here. */
    public static boolean chunkContained(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        long now = gameTime(level);
        return data != null && now > 0L && data.isContained(now);
    }

    /** The containment capacity covering {@code pos}'s chunk, 0 if none. */
    public static double chunkCapacity(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        long now = gameTime(level);
        return data == null || now <= 0L ? 0.0 : data.capacity(now);
    }

    /** Flux over capacity in {@code pos}'s chunk (see {@link ChunkFlux#load}). */
    public static double chunkLoad(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        long now = gameTime(level);
        return data == null || now <= 0L ? 0.0 : data.load(now);
    }

    /** The flux in {@code pos}'s chunk that its containment does not hold. */
    public static double chunkOverflow(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        long now = gameTime(level);
        return data == null ? 0.0 : Math.max(0.0, data.flux() - (now <= 0L ? 0.0 : data.capacity(now)));
    }

    // ---- reading ----

    /** This chunk's own values. */
    public static Neighbourhood chunk(Level level, BlockPos pos) {
        ChunkPos chunkPos = ChunkPos.containing(pos);
        LevelChunk chunk = loaded(level, chunkPos.x(), chunkPos.z());
        if (chunk == null) return new Neighbourhood(0.0, 0.0);
        if (level instanceof ServerLevel serverLevel) catchUpDrain(serverLevel, chunk);
        ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
        if (data == null) return new Neighbourhood(0.0, 0.0);
        return new Neighbourhood(data.flux(), data.anomaly());
    }

    /**
     * The field at {@code pos}: interpolated between the centres of the four chunks nearest it, so it
     * changes smoothly as you walk and never jumps at a chunk line. For machines and meters.
     */
    public static Neighbourhood sample(Level level, BlockPos pos) {
        double fx = (pos.getX() + 0.5) / 16.0 - 0.5;
        double fz = (pos.getZ() + 0.5) / 16.0 - 0.5;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        double flux = 0.0;
        double anomaly = 0.0;
        for (int i = 0; i < 4; i++) {
            int dx = i & 1;
            int dz = i >> 1;
            double weight = (dx == 1 ? tx : 1.0 - tx) * (dz == 1 ? tz : 1.0 - tz);
            if (weight <= 0.0) continue;
            Neighbourhood here = chunk(level, new BlockPos((x0 + dx) * 16, pos.getY(), (z0 + dz) * 16));
            flux += here.flux() * weight;
            anomaly += here.anomaly() * weight;
        }
        return new Neighbourhood(flux, anomaly);
    }

    /**
     * This chunk's flux plus what is still easing in from its pool: where the field is heading. For
     * controllers holding the field at a level, so they ease off before the flux they have already
     * emitted arrives, rather than overshooting.
     */
    public static double chunkCommittedFlux(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        return data == null ? 0.0 : data.flux() + data.pendingFlux();
    }

    /** The flux {@code pos}'s chunk is settling at (see {@link ChunkFlux#settling}); NaN if not yet known. */
    public static double chunkSettling(Level level, BlockPos pos) {
        ChunkFlux data = chunkData(level, pos);
        return data == null ? Double.NaN : data.settling();
    }

    public static double chunkFlux(Level level, BlockPos pos) {
        return chunk(level, pos).flux();
    }

    public static double chunkAnomaly(Level level, BlockPos pos) {
        return chunk(level, pos).anomaly();
    }

    public static FluxBand chunkBand(Level level, BlockPos pos) {
        return chunk(level, pos).fluxBand();
    }

    public static FluxBand chunkAnomalyBand(Level level, BlockPos pos) {
        return chunk(level, pos).anomalyBand();
    }

    // ---- upkeep ----

    /**
     * Active chunks are stepped once a second by {@link FieldModel}; a chunk that missed steps
     * (unloaded, or without a field until now) catches up its decay here, on first touch.
     */
    public static void catchUpDrain(ServerLevel level, LevelChunk chunk) {
        ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
        if (data == null || data.isEmpty()) return;
        long now = level.getGameTime();
        long last = data.lastDrainTick();
        if (last <= 0L) {
            data.setLastDrainTick(now);
            return;
        }
        long elapsed = now - last;
        if (elapsed < 40L) return;
        int seconds = (int) Math.min(Integer.MAX_VALUE, elapsed / 20L);
        data.setLastDrainTick(now - (elapsed % 20L));
        if (FieldModel.catchUp(data, seconds)) chunk.markUnsaved();
    }

    /**
     * The chunk if it is loaded, in one lookup. {@code hasChunk} then {@code getChunk} looked it up
     * twice, and on the server thread {@code getChunk} goes through the chunk cache and ticket
     * system; machines read their chunk every tick, so this was a real share of their cost.
     */
    @Nullable
    private static LevelChunk loaded(Level level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ);
    }

    @Nullable
    private static ChunkFlux chunkData(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        ChunkPos chunkPos = ChunkPos.containing(pos);
        LevelChunk chunk = loaded(level, chunkPos.x(), chunkPos.z());
        return chunk == null ? null : chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
    }

    private static long gameTime(Level level) {
        return level instanceof ServerLevel serverLevel ? serverLevel.getGameTime() : 0L;
    }

    private static void forEachLoadedChunk(ServerLevel level, BlockPos pos, int radius, Consumer<LevelChunk> visitor) {
        ChunkPos centre = ChunkPos.containing(pos);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = loaded(level, centre.x() + dx, centre.z() + dz);
                if (chunk != null) visitor.accept(chunk);
            }
        }
    }
}
