package com.kadikular.quantimium.phase;

import com.kadikular.quantimium.QuantimiumAdvancements;
import com.kadikular.quantimium.block.StabilisedPortalBlock;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.network.MirrorFloraPayload;
import com.kadikular.quantimium.network.MirrorPhaseSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority for entering and leaving the mirror overlay. */
public final class MirrorPhase {

    public enum ExitReason {
        RIFT, TIMEOUT, MANUAL, DEATH, LOGOUT, DIMENSION_CHANGE, GATE
    }

    private MirrorPhase() {}

    public static boolean isPhased(net.minecraft.world.entity.player.Player player) {
        return player.getData(ModAttachments.MIRROR_PHASE).active();
    }

    /**
     * Overlay blocks (Anomalite Crystals) may only be mined by a real player who is actually in
     * the phase. Fake players used by annihilation planes and similar count as the backing world.
     */
    public static boolean canHarvestOverlay(net.minecraft.world.entity.player.Player player) {
        return player != null && player.getClass() == ServerPlayer.class && isPhased(player);
    }

    private static final String GATE_INSIDE = "quantimium:gate_inside";
    private static final String GATE_LAST_X = "quantimium:gate_lx";
    private static final String GATE_LAST_Y = "quantimium:gate_ly";
    private static final String GATE_LAST_Z = "quantimium:gate_lz";
    private static final String GATE_HAS_LAST = "quantimium:gate_lp";
    /** Must walk this far past the doorway blocks before the gate will take you again. */
    private static final double GATE_LEAVE_MARGIN = 1.0;
    /** Ignore chorus-length teleports; elytra / creative flight still fits. */
    private static final double GATE_SWEEP_MAX = 32.0;

    public static boolean enter(ServerPlayer player) {
        return enter(player, true);
    }

    public static boolean enter(ServerPlayer player, boolean spawnReturn) {
        if (isPhased(player)) return false;
        long now = player.level().getGameTime();
        player.setData(ModAttachments.MIRROR_PHASE, new MirrorPhaseState(
                true, now, player.level().dimension(), player.position()));
        PacketDistributor.sendToPlayer(player, MirrorFloraPayload.around(player));
        if (spawnReturn) WorldRiftManager.ensureReturn(player);
        WorldRiftManager.syncPlayer(player);
        player.level().getChunkSource().move(player);
        broadcast(player, true);
        QuantimiumAdvancements.award(player, "what_is_this_place", "entered_mirror");
        return true;
    }

    public static boolean exit(ServerPlayer player, ExitReason reason) {
        boolean wasPhased = isPhased(player);
        if (wasPhased) {
            player.setData(ModAttachments.MIRROR_PHASE, MirrorPhaseState.inactive());
            WorldRiftManager.releaseOwnedReturn(player);
            player.level().getChunkSource().move(player);
        }
        broadcast(player, false);
        PacketDistributor.sendToPlayer(player, MirrorFloraPayload.clear());
        WorldRiftManager.syncPlayer(player);
        return wasPhased;
    }

    /** Nearby clients need this player's flag so overlay bodies hide or ghost correctly. */
    public static void broadcast(ServerPlayer player, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new MirrorPhaseSyncPayload(player.getUUID(), active));
    }

    public static boolean toggle(ServerPlayer player) {
        if (isPhased(player)) return exit(player, ExitReason.MANUAL);
        return enter(player);
    }

    /**
     * Built gate: both sides, no follower tear. Fires once you cross the midplane; you must walk
     * out before it will take you back.
     */
    public static boolean tryStabilisedGate(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBooleanOr(GATE_INSIDE, false)) return false;
        boolean entered = !isPhased(player);
        boolean ok = entered ? enter(player, false) : exit(player, ExitReason.GATE);
        if (!ok) return false;
        data.putBoolean(GATE_INSIDE, true);
        player.sendOverlayMessage(Component.translatable(entered
                ? "message.quantimium.mirror_phase.entered"
                : "message.quantimium.mirror_phase.returned"));
        return true;
    }

    public static void tickStabilisedGate(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        Vec3 now = player.position();
        boolean haveLast = data.getBooleanOr(GATE_HAS_LAST, false);
        Vec3 prev = haveLast
                ? new Vec3(data.getDoubleOr(GATE_LAST_X, 0.0), data.getDoubleOr(GATE_LAST_Y, 0.0), data.getDoubleOr(GATE_LAST_Z, 0.0))
                : now;
        if (data.getBooleanOr(GATE_INSIDE, false)) {
            if (!touchingStabilisedGate(player)) data.remove(GATE_INSIDE);
        } else if (haveLast && crossedStabilisedGate(player, prev, now)) {
            tryStabilisedGate(player);
        }
        data.putBoolean(GATE_HAS_LAST, true);
        data.putDouble(GATE_LAST_X, now.x);
        data.putDouble(GATE_LAST_Y, now.y);
        data.putDouble(GATE_LAST_Z, now.z);
    }

    /**
     * True when this tick's movement segment crosses a gate midplane through the doorway.
     * Catches elytra / creative flight that would skip a thin "stand in the middle" band.
     */
    private static boolean crossedStabilisedGate(ServerPlayer player, Vec3 prev, Vec3 now) {
        if (prev.distanceToSqr(now) > GATE_SWEEP_MAX * GATE_SWEEP_MAX) return false;
        AABB currBox = player.getBoundingBox();
        AABB prevBox = currBox.move(prev.subtract(now));
        AABB swept = new AABB(
                Math.min(prevBox.minX, currBox.minX), Math.min(prevBox.minY, currBox.minY),
                Math.min(prevBox.minZ, currBox.minZ), Math.max(prevBox.maxX, currBox.maxX),
                Math.max(prevBox.maxY, currBox.maxY), Math.max(prevBox.maxZ, currBox.maxZ));
        return BlockPos.betweenClosedStream(
                        BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                        BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                .anyMatch(pos -> crossedPortalPlane(player, pos, prev, now, prevBox, currBox));
    }

    private static boolean crossedPortalPlane(ServerPlayer player, BlockPos pos,
                                             Vec3 prev, Vec3 now, AABB prevBox, AABB currBox) {
        var state = player.level().getBlockState(pos);
        if (!state.is(ModBlocks.STABILISED_PORTAL.get())) return false;
        boolean thinZ = state.getValue(StabilisedPortalBlock.AXIS) == Direction.Axis.X;
        double mid = (thinZ ? pos.getZ() : pos.getX()) + 0.5;
        double from = thinZ ? prev.z : prev.x;
        double to = thinZ ? now.z : now.x;
        if ((from - mid) * (to - mid) > 0.0) return false;
        double span = to - from;
        double t = Math.abs(span) < 1.0E-6 ? 0.0 : (mid - from) / span;
        if (t < 0.0 || t > 1.0) return false;
        return lerpAabb(prevBox, currBox, t).intersects(new AABB(pos));
    }

    private static AABB lerpAabb(AABB from, AABB to, double t) {
        return new AABB(
                Mth.lerp(t, from.minX, to.minX), Mth.lerp(t, from.minY, to.minY),
                Mth.lerp(t, from.minZ, to.minZ), Mth.lerp(t, from.maxX, to.maxX),
                Mth.lerp(t, from.maxY, to.maxY), Mth.lerp(t, from.maxZ, to.maxZ));
    }

    private static boolean touchingStabilisedGate(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(GATE_LEAVE_MARGIN);
        return BlockPos.betweenClosedStream(
                        BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                .anyMatch(pos -> player.level().getBlockState(pos).is(ModBlocks.STABILISED_PORTAL.get()));
    }
}
