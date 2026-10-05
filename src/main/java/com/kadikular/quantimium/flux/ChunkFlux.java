package com.kadikular.quantimium.flux;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Per-chunk quantum field. Mutable so the field can update without reallocating the attachment.
 * Flux is capability; anomaly is danger. How the two move is {@link FieldModel}'s job; this holds
 * them, the pool of emission still easing in, and the containment capacity covering the chunk.
 */
public final class ChunkFlux {

    public static final Codec<ChunkFlux> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("flux", 0.0).forGetter(ChunkFlux::flux),
            Codec.DOUBLE.optionalFieldOf("anomaly", 0.0).forGetter(ChunkFlux::anomaly),
            Codec.LONG.optionalFieldOf("last_drain_tick", 0L).forGetter(ChunkFlux::lastDrainTick),
            Codec.DOUBLE.optionalFieldOf("pending_flux", 0.0).forGetter(ChunkFlux::pendingFlux),
            Codec.DOUBLE.optionalFieldOf("pending_anomaly", 0.0).forGetter(ChunkFlux::pendingAnomaly)
    ).apply(instance, ChunkFlux::new));

    /** Below this a field has finished clearing: decay never takes less than it a second. */
    public static final double DRAIN_FLOOR = 0.05;

    /**
     * Fraction of (target − anomaly) closed each second while anomaly is below its target. The
     * target is the flux over capacity, plus the containment's leak (see {@link FieldModel#anomalyTarget}).
     */
    public static final double ANOMALY_COUPLING = 0.04;

    private double flux;
    private double anomaly;
    private long lastDrainTick;
    /**
     * What the field gained each second before decay (from its pool and its neighbours), smoothed; NaN
     * until first stepped. Decay balancing it is where the field is heading. Not saved.
     */
    private double inflow = Double.NaN;
    /** Emitted but not yet in the field: it eases in over {@link FieldModel#RELEASE_SECONDS}. */
    private double pendingFlux;
    private double pendingAnomaly;
    /**
     * Containment capacity, summed over a second of game time. Containment machines add theirs once
     * a second; {@link #capacitySecond} is the second being added to, {@link #capacityAdding} what has
     * come in so far, and {@link #capacityLast} the whole of the second before. Not saved: a machine
     * that is still there adds again within a second.
     */
    private long capacitySecond = Long.MIN_VALUE / 2;
    private double capacityAdding;
    private double capacityLast;
    /** Capacity × leak, summed alongside, so a chunk under several machines leaks their weighted mix. */
    private double leakAdding;
    private double leakLast;
    /**
     * Pulls asked of this chunk since the field last stepped, by puller: {flux, anomaly, flux to leave,
     * anomaly to leave}. The step
     * takes them after the coupling they answer, so a reading between steps never sees anomaly climb
     * and fall back within a second, and a puller can hold a chunk right down, not a second behind.
     * Not saved; null until first used.
     */
    private Long2ObjectLinkedOpenHashMap<double[]> pullRequests;
    /** What each puller's last pull took here, by puller: {anomaly taken, game second, flux taken}. Collected at its next ask. */
    private Long2ObjectOpenHashMap<double[]> pullReceipts;

    public ChunkFlux() {
        this(0.0, 0.0, 0L);
    }

    public ChunkFlux(double flux, double anomaly, long lastDrainTick) {
        this(flux, anomaly, lastDrainTick, 0.0, 0.0);
    }

    public ChunkFlux(double flux, double anomaly, long lastDrainTick, double pendingFlux, double pendingAnomaly) {
        this.flux = Math.max(0.0, flux);
        this.anomaly = Math.max(0.0, anomaly);
        this.lastDrainTick = lastDrainTick;
        this.pendingFlux = Math.max(0.0, pendingFlux);
        this.pendingAnomaly = Math.max(0.0, pendingAnomaly);
    }

    public double flux() {
        return flux;
    }

    public double anomaly() {
        return anomaly;
    }

    public double pendingFlux() {
        return pendingFlux;
    }

    public double pendingAnomaly() {
        return pendingAnomaly;
    }

    public long lastDrainTick() {
        return lastDrainTick;
    }

    public void setLastDrainTick(long tick) {
        this.lastDrainTick = tick;
    }

    /** Adds to the pool that eases into the field. */
    public void addPending(double flux, double anomaly) {
        if (flux > 0.0) pendingFlux += flux;
        if (anomaly > 0.0) pendingAnomaly += anomaly;
    }

    /** Moves {@code fraction} of the pending pool into the field. */
    void releasePending(double fraction) {
        double f = pendingFlux * fraction;
        double a = pendingAnomaly * fraction;
        // A pool this small would take forever to finish: let it all in.
        if (pendingFlux - f < FieldModel.POOL_EPSILON) f = pendingFlux;
        if (pendingAnomaly - a < FieldModel.POOL_EPSILON) a = pendingAnomaly;
        pendingFlux -= f;
        pendingAnomaly -= a;
        flux += f;
        anomaly += a;
    }

    /** Empties the field and its pending pool. */
    public void clear() {
        flux = 0.0;
        anomaly = 0.0;
        pendingFlux = 0.0;
        pendingAnomaly = 0.0;
    }

    /**
     * Forgets the containment covering it, which otherwise lasts a second or two after the machine
     * that gave it is gone. For tests: the next one on the same ground starts uncontained.
     */
    public void clearContainment() {
        capacitySecond = Long.MIN_VALUE / 2;
        capacityAdding = 0.0;
        capacityLast = 0.0;
        leakAdding = 0.0;
        leakLast = 0.0;
    }

    /** Sets the field outright. */
    public void setField(double flux, double anomaly) {
        this.flux = Math.max(0.0, flux);
        this.anomaly = Math.max(0.0, anomaly);
    }

    public void addFlux(double amount) {
        if (amount > 0.0) flux += amount;
    }

    public void addAnomaly(double amount) {
        if (amount > 0.0) anomaly += amount;
    }

    /** Returns how much was actually removed (clamped to what was there). */
    public double removeFlux(double amount) {
        if (amount <= 0.0 || flux <= 0.0) return 0.0;
        double taken = Math.min(flux, amount);
        flux -= taken;
        return taken;
    }

    /** Returns how much was actually removed (clamped to what was there). */
    public double removeAnomaly(double amount) {
        if (amount <= 0.0 || anomaly <= 0.0) return 0.0;
        double taken = Math.min(anomaly, amount);
        anomaly -= taken;
        return taken;
    }

    // ---- containment ----

    /**
     * A containment machine covers this chunk with {@code amount} capacity this second, leaking
     * {@code leak} of the flux it holds (see {@link FieldModel#anomalyTarget}).
     */
    public void addCapacity(long gameTime, double amount, double leak) {
        if (amount <= 0.0) return;
        long second = Math.floorDiv(gameTime, 20L);
        if (second != capacitySecond) {
            boolean next = second == capacitySecond + 1;
            capacityLast = next ? capacityAdding : 0.0;
            leakLast = next ? leakAdding : 0.0;
            capacityAdding = 0.0;
            leakAdding = 0.0;
            capacitySecond = second;
        }
        capacityAdding += amount;
        leakAdding += amount * leak;
    }

    /** The leak of the containment covering this chunk: its machines' leaks, weighted by capacity. */
    public double leak(long gameTime) {
        long second = Math.floorDiv(gameTime, 20L);
        double capacity;
        double weighted;
        if (second == capacitySecond && capacityAdding >= capacityLast) {
            capacity = capacityAdding;
            weighted = leakAdding;
        } else if (second == capacitySecond) {
            capacity = capacityLast;
            weighted = leakLast;
        } else if (second == capacitySecond + 1) {
            capacity = capacityAdding;
            weighted = leakAdding;
        } else {
            return 0.0;
        }
        return capacity <= 0.0 ? 0.0 : weighted / capacity;
    }

    /**
     * Puller {@code puller} asks the field's next step for up to {@code flux} and {@code anomaly}, but
     * to leave at least {@code leaveFlux} and {@code leaveAnomaly}: it takes only the excess over those,
     * so a chunk is brought down to exactly its mark and held there, not overshot. Returns what its last
     * pull here took, {flux, anomaly}, since a pull is only settled at the step.
     */
    double[] requestPull(long puller, double flux, double anomaly, double leaveFlux, double leaveAnomaly) {
        if (pullRequests == null) pullRequests = new Long2ObjectLinkedOpenHashMap<>();
        double[] asked = pullRequests.computeIfAbsent(puller, k -> new double[4]);
        asked[0] += flux;
        asked[1] += anomaly;
        asked[2] = leaveFlux;
        asked[3] = leaveAnomaly;
        double[] receipt = pullReceipts == null ? null : pullReceipts.remove(puller);
        return receipt == null ? new double[2] : new double[] {receipt[2], receipt[0]};
    }

    /** Settles the pulls asked since the last step, in the order asked, leaving each puller a receipt. */
    void applyPulls(long gameTime) {
        long second = Math.floorDiv(gameTime, 20L);
        if (pullReceipts != null) {
            // Receipts nobody came back for: the puller is gone.
            pullReceipts.values().removeIf(receipt -> second - receipt[1] > 2);
        }
        if (pullRequests == null || pullRequests.isEmpty()) return;
        if (pullReceipts == null) pullReceipts = new Long2ObjectOpenHashMap<>();
        for (var entry : pullRequests.long2ObjectEntrySet()) {
            double[] asked = entry.getValue();
            double fluxTaken = removeFlux(Math.min(asked[0], Math.max(0.0, flux - asked[2])));
            double taken = removeAnomaly(Math.min(asked[1], Math.max(0.0, anomaly - asked[3])));
            double[] receipt = pullReceipts.computeIfAbsent(entry.getLongKey(), k -> new double[] {0.0, second, 0.0});
            receipt[0] += taken;
            receipt[1] = second;
            receipt[2] += fluxTaken;
        }
        pullRequests.clear();
    }

    boolean hasPullRequests() {
        return pullRequests != null && !pullRequests.isEmpty();
    }

    /**
     * The capacity covering this chunk: what was added over the last whole second, or over this one
     * so far if more has already come in (a machine just placed counts at once).
     */
    public double capacity(long gameTime) {
        long second = Math.floorDiv(gameTime, 20L);
        if (second == capacitySecond) return Math.max(capacityLast, capacityAdding);
        if (second == capacitySecond + 1) return capacityAdding;
        return 0.0;
    }

    /** Whether any containment covers this chunk, however loaded. */
    public boolean isShielded(long gameTime) {
        return capacity(gameTime) > 0.0;
    }

    /**
     * Whether containment holds this chunk: covered, with the flux within its capacity. A contained
     * chunk's anomaly is atmosphere only; nothing destructive happens in it.
     */
    public boolean isContained(long gameTime) {
        double capacity = capacity(gameTime);
        return capacity > 0.0 && flux <= capacity;
    }

    /** Flux over capacity: 0.5 is half loaded, above 1 overloaded. Infinite with no containment. */
    public double load(long gameTime) {
        double capacity = capacity(gameTime);
        if (capacity <= 0.0) return flux > 0.0 ? Double.POSITIVE_INFINITY : 0.0;
        return flux / capacity;
    }

    /** Records a second's gain before decay; see {@link #settling}. */
    void recordInflow(double gain) {
        inflow = Double.isNaN(inflow) ? gain : inflow + FieldModel.INFLOW_SMOOTHING * (gain - inflow);
    }

    /**
     * The flux this chunk is heading for, if what feeds it carries on as it has: the level at which
     * decay would take exactly what comes in. It is the current flux while the field is steady, and
     * refines itself as the field climbs or falls. NaN before the chunk has been stepped.
     */
    public double settling() {
        if (Double.isNaN(inflow)) return Double.NaN;
        return FieldModel.levelFor(Math.max(0.0, inflow));
    }

    public boolean isEmpty() {
        return flux <= 0.0 && anomaly <= 0.0 && pendingFlux <= 0.0 && pendingAnomaly <= 0.0;
    }
}
