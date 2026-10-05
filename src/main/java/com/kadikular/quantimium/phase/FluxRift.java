package com.kadikular.quantimium.phase;

import com.kadikular.quantimium.Config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A persistent wound between the realms — unlike a tear, which is a door, this one stays and grows.
 * The real world only sees its shadow; the anchor that can be decohered lives in the mirror.
 *
 * <p>Growth and stage are saved, so logging out does not heal it. Coherence damage is not: it
 * regenerates within seconds of the lance stopping anyway, and saving it would let a rift be chipped
 * down across sessions, which is exactly what regeneration exists to prevent.
 */
public final class FluxRift {

    public static final int MAX_STAGE = 4;

    public static final Codec<FluxRift> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(FluxRift::id),
            BlockPos.CODEC.fieldOf("anchor").forGetter(FluxRift::anchor),
            Codec.INT.fieldOf("seed").forGetter(FluxRift::shapeSeed),
            Codec.INT.fieldOf("stage").forGetter(FluxRift::stage),
            Codec.DOUBLE.fieldOf("growth").forGetter(FluxRift::growth)
    ).apply(instance, FluxRift::new));

    private final UUID id;
    private final BlockPos anchor;
    private final int shapeSeed;
    private int stage;
    /** 0–1 progress towards the next stage. */
    private double growth;

    private double damage;
    private long lastDrainTick = Long.MIN_VALUE / 2;
    @Nullable
    private UUID tear;
    private long nextTearTick;
    /** The Decoherence Projector last draining it, and when. Transient. */
    @Nullable
    private BlockPos projector;
    private long projectorTick = Long.MIN_VALUE / 2;
    /**
     * Last tick a Rift Anchor held it with enough stabilisers, and how many. Transient: an anchor
     * re-asserts it every tick, and it lapses a few seconds after the power goes.
     */
    private long stabilisedTick = Long.MIN_VALUE / 2;
    private int stabilisers;
    /** Next lightning tick per entity id in range. Transient: timers restart harmlessly after a load. */
    private final Map<Integer, Long> strikeTimers = new HashMap<>();

    public FluxRift(UUID id, BlockPos anchor, int shapeSeed, int stage, double growth) {
        this.id = id;
        this.anchor = anchor.immutable();
        this.shapeSeed = shapeSeed;
        this.stage = Math.max(1, Math.min(MAX_STAGE, stage));
        this.growth = growth;
    }

    public UUID id() {
        return id;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public int shapeSeed() {
        return shapeSeed;
    }

    public int stage() {
        return stage;
    }

    public double growth() {
        return growth;
    }

    public void setGrowth(double growth) {
        this.growth = growth;
    }

    public void setStage(int stage) {
        this.stage = Math.max(1, Math.min(MAX_STAGE, stage));
        this.growth = 0.0;
        this.damage = Math.min(damage, maxCoherence(this.stage));
    }

    /** Visual and hit size relative to a tear. Shared by server targeting and client rendering. */
    public static float scale(int stage) {
        return 1.0f + 0.35f * (stage - 1);
    }

    /** Middle of the wound. The tear shape is drawn about its centre, so grow it upwards off the floor. */
    public static Vec3 centre(BlockPos anchor, int stage) {
        return new Vec3(anchor.getX() + 0.5, anchor.getY() + 0.1 + 1.075 * scale(stage), anchor.getZ() + 0.5);
    }

    /** Radius of the sphere the lance has to hit. Generous: the silhouette is ragged and wobbles. */
    public static double hitRadius(int stage) {
        return 0.95 * scale(stage);
    }

    /**
     * The wound is solid: a vertical cylinder about its axis that nothing walks through. Narrower
     * than the drawn silhouette's widest point so its waist, not its tips, is what you bump into.
     */
    public static double solidRadius(int stage) {
        return 0.5 * scale(stage);
    }

    public static double bottom(BlockPos anchor) {
        return anchor.getY();
    }

    public static double top(BlockPos anchor, int stage) {
        return anchor.getY() + 0.2 + 2.15 * scale(stage);
    }

    /**
     * Whether a block cell is inside the wound's solid cylinder: from its footing to its top, and
     * any part of the cell's footprint within the solid radius of its axis. One column at stage 1,
     * the 3×3 around it by stage 3.
     */
    public static boolean occupies(BlockPos anchor, int stage, BlockPos cell) {
        if (cell.getY() < anchor.getY() || cell.getY() >= top(anchor, stage)) return false;
        // The axis runs through the middle of the anchor cell; this is the gap to the cell's nearest edge.
        double dx = Math.max(0.0, Math.abs(cell.getX() - anchor.getX()) - 0.5);
        double dz = Math.max(0.0, Math.abs(cell.getZ() - anchor.getZ()) - 0.5);
        return dx * dx + dz * dz < solidRadius(stage) * solidRadius(stage);
    }

    /**
     * How far from its surface a rift throws lightning: 2 blocks at stage 1, 8 at stage 4. Half the
     * lance's reach at the top, so a big rift has to be channelled from a respectful distance.
     */
    public static double strikeRange(int stage) {
        return solidRadius(stage) + 2.0 * stage;
    }

    public Vec3 centre() {
        return centre(anchor, stage);
    }

    public static double maxCoherence(int stage) {
        return Config.fluxRiftCoherencePerStage() * stage;
    }

    public double damage() {
        return damage;
    }

    /** 1 at full strength, 0 when it gives. */
    public float coherence() {
        return (float) Math.max(0.0, 1.0 - damage / maxCoherence(stage));
    }

    public void drain(double amount, long now) {
        damage = Math.min(maxCoherence(stage), damage + amount);
        lastDrainTick = now;
    }

    public void regenerate(double amount) {
        damage = Math.max(0.0, damage - amount);
    }

    public boolean collapsed() {
        return damage >= maxCoherence(stage);
    }

    public long lastDrainTick() {
        return lastDrainTick;
    }

    public boolean beingDrained(long now, long window) {
        return now - lastDrainTick <= window;
    }

    @Nullable
    public UUID tear() {
        return tear;
    }

    public void setTear(@Nullable UUID tear) {
        this.tear = tear;
    }

    public void drainedBy(BlockPos projector, long now) {
        this.projector = projector.immutable();
        this.projectorTick = now;
    }

    /** The Projector draining it, if one has in the last {@code window} ticks. */
    @Nullable
    public BlockPos projector(long now, long window) {
        return now - projectorTick <= window ? projector : null;
    }

    /** How long a held rift stays held after the last stabiliser drops out: a flicker is not a failure. */
    public static final long STABILISED_WINDOW = 60L;

    public void stabilise(int count, long now) {
        this.stabilisers = count;
        this.stabilisedTick = now;
    }

    /** Held by a Rift Anchor: grows steadily, and everything but its short-range shock is switched off. */
    public boolean stabilised(long now) {
        return now - stabilisedTick <= STABILISED_WINDOW;
    }

    /** What clients were last told, so a change of state triggers a sync. */
    private boolean wasStabilised;

    public boolean wasStabilised() {
        return wasStabilised;
    }

    public void setWasStabilised(boolean stabilised) {
        this.wasStabilised = stabilised;
    }

    public int stabilisers() {
        return stabilisers;
    }

    public Map<Integer, Long> strikeTimers() {
        return strikeTimers;
    }

    public long nextTearTick() {
        return nextTearTick;
    }

    public void setNextTearTick(long tick) {
        this.nextTearTick = tick;
    }
}
