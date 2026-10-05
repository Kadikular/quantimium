package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.init.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * How the flux field moves (the game plan's Field Model 2.0).
 *
 * <p>Once a second, for every chunk holding a field:
 * <ol>
 *   <li><b>Release.</b> A slice of the pending pool eases into the field, so a burst of crafting reads
 *       as a hump rather than a spike. Machines emit into the pools of their own chunk and the eight
 *       round it (see {@link QuantumFlux#share}), so a source makes a plateau rather than a spike.</li>
 *   <li><b>Coupling.</b> Anomaly climbs towards a target: the flux over the containment capacity
 *       covering the chunk, plus a small {@link #LEAK} of what is contained. Uncovered, the target is
 *       all the flux. Contained, it is only the leak, which drives atmosphere and nothing
 *       destructive (see {@link ChunkFlux#isContained}).</li>
 *   <li><b>Decay</b> on a smooth curve: the rate rises with the value but has no steps, so every steady
 *       source has exactly one level to settle at. The old per-band rates made a field fed at 10 to 20
 *       flux a second sit on the Medium/High edge, crossing it every second.</li>
 *   <li><b>Diffusion.</b> Each chunk shares with its four loaded neighbours in proportion to the
 *       difference, which gives the field a shape: hot at a source, fading over a few chunks. Nothing
 *       flows to or from an unloaded chunk, so the field never drains away at the edge of the loaded
 *       world and does not depend on anyone's view distance. A chunk below {@link #SPREAD_FLOOR} does
 *       not spread, so a field ends in a bounded ring round its sources.</li>
 * </ol>
 * The two lags in a row (the pool, then the field) ease the field to a new level on an S-curve with
 * no overshoot. The constants are starting points, several
 * set by simulation (see each one).
 */
public final class FieldModel {

    /** Time constant of the pending pool, in seconds. */
    public static final double RELEASE_SECONDS = 10.0;
    /** Share of a pending pool released each second: 1 − e^(−1/τ). */
    public static final double RELEASE_PER_SECOND = 1.0 - Math.exp(-1.0 / RELEASE_SECONDS);
    /** What is left in a pool below this goes in at once. */
    static final double POOL_EPSILON = 0.01;
    /**
     * Share of the difference that flows to each neighbour a second. Simulated against the smooth
     * decay: at 0.01 a source fed 1,500 flux a second settles near 19k (Critical), with High one chunk
     * out, Medium at two and three and Low from four, which is the plan's "fades over 2–4 chunks".
     * 0.08 (the plan's first guess) spread a single source over a 21×21 of chunks.
     */
    public static final double DIFFUSION = 0.01;
    /**
     * Flux per FE relative to the classic model, which shared each emission over a 5×5. Calibrated
     * (with the plateau above) so the 3×3 round a source climbs a whole band for each ×10 in FE:
     * 1k FE/t Medium, 10k High, 100k Critical, 1M Singularity at the centre and sides.
     */
    public static final double EMISSION_GAIN = 3.0;
    /**
     * Share of contained flux that still couples into anomaly, for containment that doesn't set its
     * own (the hall). It keeps a hot district alive, with plumes, flora and glimpses, while
     * containment keeps anything destructive away. Basic containment leaks more.
     */
    public static final double LEAK = 0.03;
    /** How quickly a chunk's record of its inflow follows the latest second: a few seconds' memory. */
    static final double INFLOW_SMOOTHING = 0.2;
    /** A chunk holding less than this does not spread any further. */
    public static final double SPREAD_FLOOR = 1.0;
    /** Floor and ceiling of the smooth decay rate, per second. */
    private static final double DECAY_BASE = 0.005;
    private static final double DECAY_SLOPE = 0.015;

    private FieldModel() {}

    /** Fraction of {@code value} that decays in a second: 0.95% at 100, 2.1% at 1k, 3.5% at 10k, 5% at 100k. */
    public static double decayRate(double value) {
        return DECAY_BASE + DECAY_SLOPE * Math.log10(1.0 + Math.max(0.0, value) / 100.0);
    }

    /** {@code value} after {@code seconds} of decay, never below 0; the floor lets a small field finish clearing. */
    public static double decayed(double value, int seconds) {
        for (int i = 0; i < seconds && value > 0.0; i++) {
            value = Math.max(0.0, value - Math.max(ChunkFlux.DRAIN_FLOOR, value * decayRate(value)));
        }
        return value;
    }

    /** What anomaly climbs towards: the flux over {@code capacity}, plus {@code leak} of what it holds. */
    public static double anomalyTarget(double flux, double capacity, double leak) {
        double held = Math.min(flux, Math.max(0.0, capacity));
        return (flux - held) + held * leak;
    }

    /**
     * The level at which a second's decay takes exactly {@code inflow}: where a field fed that much a
     * second settles. Decay is monotonic in the value, so a bisection finds it.
     */
    public static double levelFor(double inflow) {
        if (inflow <= ChunkFlux.DRAIN_FLOOR) return 0.0;
        double low = 0.0;
        double high = 1.0;
        while (loss(high) < inflow && high < 1.0E12) high *= 10.0;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2.0;
            if (loss(mid) < inflow) low = mid;
            else high = mid;
        }
        return (low + high) / 2.0;
    }

    /** What a second's decay takes from {@code value}. */
    private static double loss(double value) {
        return Math.min(value, Math.max(ChunkFlux.DRAIN_FLOOR, value * decayRate(value)));
    }

    /**
     * Catches up a chunk that missed its steps (it was unloaded, or had no field): decay only. The
     * pool is let in first, since it would have been released long before. Diffusion is never caught
     * up: by now it is too late to share.
     */
    static boolean catchUp(ChunkFlux data, int seconds) {
        if (seconds <= 0 || data.isEmpty()) return false;
        data.releasePending(1.0);
        data.setField(decayed(data.flux(), seconds), decayed(data.anomaly(), seconds));
        return true;
    }

    /**
     * One second of the field for {@code positions} in {@code level}: those not loaded are skipped.
     * Neighbours that receive a share are marked active, so the next step includes them.
     */
    public static void step(ServerLevel level, Collection<ChunkPos> positions) {
        long now = level.getGameTime();
        Map<Long, LevelChunk> chunks = new HashMap<>();
        Map<Long, ChunkFlux> fields = new HashMap<>();
        for (ChunkPos pos : positions) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk == null) continue;
            ChunkFlux data = chunk.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
            if (data == null || data.isEmpty() && !data.hasPullRequests()) continue;
            chunks.put(pos.pack(), chunk);
            fields.put(pos.pack(), data);
        }

        // Release, coupling and decay: each chunk on its own. What each had at the start, and what decay
        // took, give its inflow once diffusion has settled too.
        Map<Long, double[]> ledger = new HashMap<>();
        for (Map.Entry<Long, ChunkFlux> entry : fields.entrySet()) {
            ChunkFlux data = entry.getValue();
            double atStart = data.flux();
            data.releasePending(RELEASE_PER_SECOND);
            double flux = data.flux();
            double anomaly = data.anomaly();
            double target = anomalyTarget(flux, data.capacity(now), data.leak(now));
            if (target > anomaly) anomaly += (target - anomaly) * ChunkFlux.ANOMALY_COUPLING;
            // Pulls asked this second land in the same step as the coupling they answer.
            data.setField(flux, anomaly);
            data.applyPulls(now);
            flux = data.flux();
            anomaly = data.anomaly();
            double afterDecay = decayed(flux, 1);
            ledger.put(entry.getKey(), new double[] {atStart, flux - afterDecay});
            data.setField(afterDecay, decayed(anomaly, 1));
            data.setLastDrainTick(now);
        }

        // Diffusion, from a snapshot so the order chunks are visited in does not matter. Each pair is
        // settled by its higher side, so no flow is counted twice.
        Map<Long, double[]> deltas = new HashMap<>();
        for (Map.Entry<Long, ChunkFlux> entry : fields.entrySet()) {
            ChunkPos pos = ChunkPos.unpack(entry.getKey());
            double flux = entry.getValue().flux();
            double anomaly = entry.getValue().anomaly();
            if (flux < SPREAD_FLOOR && anomaly < SPREAD_FLOOR) continue;
            for (int side = 0; side < 4; side++) {
                int nx = pos.x() + (side == 0 ? 1 : side == 1 ? -1 : 0);
                int nz = pos.z() + (side == 2 ? 1 : side == 3 ? -1 : 0);
                long key = ChunkPos.pack(nx, nz);
                ChunkFlux neighbour = fields.get(key);
                LevelChunk loaded = null;
                if (neighbour == null) {
                    loaded = level.getChunkSource().getChunkNow(nx, nz);
                    if (loaded == null) continue;
                    neighbour = loaded.getExistingData(ModAttachments.CHUNK_FLUX).orElse(null);
                }
                double theirFlux = neighbour == null ? 0.0 : neighbour.flux();
                double theirAnomaly = neighbour == null ? 0.0 : neighbour.anomaly();
                double fluxFlow = flux >= SPREAD_FLOOR && flux > theirFlux ? DIFFUSION * (flux - theirFlux) : 0.0;
                double anomalyFlow = anomaly >= SPREAD_FLOOR && anomaly > theirAnomaly ? DIFFUSION * (anomaly - theirAnomaly) : 0.0;
                if (fluxFlow <= 0.0 && anomalyFlow <= 0.0) continue;
                double[] mine = deltas.computeIfAbsent(entry.getKey(), k -> new double[2]);
                mine[0] -= fluxFlow;
                mine[1] -= anomalyFlow;
                double[] theirs = deltas.computeIfAbsent(key, k -> new double[2]);
                theirs[0] += fluxFlow;
                theirs[1] += anomalyFlow;
                if (loaded != null) chunks.putIfAbsent(key, loaded);
            }
        }
        for (Map.Entry<Long, double[]> entry : deltas.entrySet()) {
            LevelChunk chunk = chunks.get(entry.getKey());
            if (chunk == null) continue;
            ChunkFlux data = chunk.getData(ModAttachments.CHUNK_FLUX);
            if (data.lastDrainTick() <= 0L) data.setLastDrainTick(now);
            double[] delta = entry.getValue();
            data.setField(data.flux() + delta[0], data.anomaly() + delta[1]);
            chunk.markUnsaved();
            if (!data.isEmpty()) FluxEvents.markActive(level, chunk.getPos());
        }
        for (LevelChunk chunk : chunks.values()) chunk.markUnsaved();
        for (Map.Entry<Long, double[]> entry : ledger.entrySet()) {
            ChunkFlux data = fields.get(entry.getKey());
            double[] line = entry.getValue();
            data.recordInflow(data.flux() - line[0] + line[1]);
        }
    }
}
