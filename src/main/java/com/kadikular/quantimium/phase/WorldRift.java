package com.kadikular.quantimium.phase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One world-owned tear. Geometry is shared; visibility and use are per player. */
public final class WorldRift {

    private final UUID id;
    private final MirrorRiftKind kind;
    @Nullable
    private UUID owner;
    private final BlockPos anchor;
    private final Direction facing;
    private final int shapeSeed;
    private final long spawnedTick;
    private long expireTick;
    private long invalidSince;
    /** Opened by the field itself. Only these can fail to close into a {@link FluxRift}. */
    private final boolean anomalous;

    public WorldRift(UUID id, MirrorRiftKind kind, @Nullable UUID owner, BlockPos anchor,
                     Direction facing, int shapeSeed, long spawnedTick, long expireTick) {
        this(id, kind, owner, anchor, facing, shapeSeed, spawnedTick, expireTick, false);
    }

    public WorldRift(UUID id, MirrorRiftKind kind, @Nullable UUID owner, BlockPos anchor,
                     Direction facing, int shapeSeed, long spawnedTick, long expireTick,
                     boolean anomalous) {
        this.anomalous = anomalous;
        this.id = id;
        this.kind = kind;
        this.owner = owner;
        this.anchor = anchor;
        this.facing = facing;
        this.shapeSeed = shapeSeed;
        this.spawnedTick = spawnedTick;
        this.expireTick = expireTick;
    }

    public UUID id() {
        return id;
    }

    public MirrorRiftKind kind() {
        return kind;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public Direction facing() {
        return facing;
    }

    public int shapeSeed() {
        return shapeSeed;
    }

    public long spawnedTick() {
        return spawnedTick;
    }

    public long expireTick() {
        return expireTick;
    }

    public boolean anomalous() {
        return anomalous;
    }

    public MirrorRift geometry() {
        return new MirrorRift(anchor, facing, shapeSeed);
    }

    public boolean expired(long now) {
        return now >= expireTick;
    }

    public boolean opened(long now) {
        return now - spawnedTick >= MirrorRift.OPEN_TICKS;
    }

    /** Leave the tear in place for other phased players after the owner exits. */
    public void releaseOwner() {
        owner = null;
    }

    public void refreshExpire(long tick) {
        expireTick = tick;
    }

    public long invalidSince() {
        return invalidSince;
    }

    public void setInvalidSince(long tick) {
        invalidSince = tick;
    }

    public boolean visibleTo(boolean phased, UUID player) {
        return switch (kind) {
            case RETURN -> phased;
            case ENTRY -> !phased;
        };
    }

    public boolean usableBy(boolean phased, UUID player) {
        return visibleTo(phased, player);
    }
}
