package com.kadikular.quantimium.phase;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.network.MirrorRiftSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dimension-owned tears. Return rifts are mirror-only; entry rifts are real-world only. */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class WorldRiftManager {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final long INVALID_RELOCATE_TICKS = 30L * 20L;

    private WorldRiftManager() {}

    public static boolean spawnReturn(ServerPlayer player) {
        ServerLevel level = player.level();
        Site site = findSite(level, player.blockPosition(), level.getRandom());
        if (site == null) site = findSkySite(level, player.blockPosition(), level.getRandom());
        if (site == null) return false;
        long now = level.getGameTime();
        WorldRift rift = new WorldRift(UUID.randomUUID(), MirrorRiftKind.RETURN, player.getUUID(),
                site.anchor(), site.facing(), level.getRandom().nextInt(), now,
                now + Config.returnRiftLifetimeTicks());
        data(level).add(rift);
        syncLevel(level);
        return true;
    }

    /** Reuse a leftover return in range instead of stacking another follower. */
    public static void ensureReturn(ServerPlayer player) {
        if (nearbyReturn(player) != null) return;
        spawnReturn(player);
    }

    public static boolean spawnEntry(ServerLevel level, BlockPos anchor, Direction facing,
                                     int durationTicks) {
        spawnEntry(level, anchor, facing, durationTicks, false);
        return true;
    }

    private static UUID spawnEntry(ServerLevel level, BlockPos anchor, Direction facing,
                                   int durationTicks, boolean anomalous) {
        long now = level.getGameTime();
        WorldRift rift = new WorldRift(UUID.randomUUID(), MirrorRiftKind.ENTRY, null,
                anchor, facing, level.getRandom().nextInt(), now, now + Math.max(1, durationTicks),
                anomalous);
        data(level).add(rift);
        syncLevel(level);
        return rift.id();
    }

    /**
     * A tear in the ring around a flux rift: the safer way in. Skips the dimension cap and spacing,
     * which exist to ration the field's own tears, and is never anomalous, so a rift's doors cannot
     * fail into more rifts.
     */
    @Nullable
    public static UUID spawnEntryNear(ServerLevel level, BlockPos origin) {
        Site site = findSite(level, origin, level.getRandom());
        if (site == null) site = findSkySite(level, origin, level.getRandom());
        if (site == null) return null;
        return spawnEntry(level, site.anchor(), site.facing(), Config.entryRiftLifetimeTicks(), false);
    }

    public static boolean isOpen(ServerLevel level, UUID id) {
        for (WorldRift rift : data(level).rifts()) {
            if (rift.id().equals(id)) return !rift.expired(level.getGameTime());
        }
        return false;
    }

    /** Anomaly-driven real-world tear. No-ops if the dimension is at cap or another entry is too close. */
    public static boolean spawnRandomEntry(ServerLevel level, BlockPos origin) {
        if (countEntries(level) >= Config.entryRiftMax()) return false;
        Site site = findSite(level, origin, level.getRandom());
        if (site == null) site = findSkySite(level, origin, level.getRandom());
        if (site == null) return false;
        if (hasNearbyEntry(level, site.anchor())) return false;
        spawnEntry(level, site.anchor(), site.facing(), Config.entryRiftLifetimeTicks(), true);
        return true;
    }

    public static void maintainOwnedReturn(ServerPlayer player, long now) {
        if (!MirrorPhase.isPhased(player)) return;
        if (nearbyReturn(player) != null) return;
        ServerLevel level = player.level();
        WorldRift owned = data(level).ownedReturn(player.getUUID());
        if (owned == null || owned.expired(now)) {
            spawnReturn(player);
            return;
        }

        double limit = Config.riftRelocateDistance();
        boolean tooFar = player.distanceToSqr(owned.geometry().centre()) > limit * limit;
        boolean loaded = isLoaded(level, owned.anchor());
        boolean valid = loaded && isUsable(level, owned.anchor(), owned.facing());
        if (tooFar || !loaded) {
            relocateOwnedReturn(player, owned);
            return;
        }
        if (valid) {
            owned.setInvalidSince(0L);
            return;
        }
        if (owned.invalidSince() == 0L) {
            owned.setInvalidSince(now);
        } else if (now - owned.invalidSince() >= INVALID_RELOCATE_TICKS) {
            relocateOwnedReturn(player, owned);
        }
    }

    public static boolean tryUseVisible(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        boolean phased = MirrorPhase.isPhased(player);
        for (WorldRift rift : List.copyOf(data(level).rifts())) {
            if (!rift.usableBy(phased, player.getUUID()) || !rift.opened(now) || rift.expired(now)) {
                continue;
            }
            if (!player.getBoundingBox().deflate(0.05).intersects(rift.geometry().bounds(1.0f))) {
                continue;
            }
            if (rift.kind() == MirrorRiftKind.RETURN) {
                rift.refreshExpire(now + Config.returnRiftLifetimeTicks());
                return MirrorPhase.exit(player, MirrorPhase.ExitReason.RIFT);
            }
            boolean entered = MirrorPhase.enter(player);
            syncLevel(level);
            return entered;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (expire(level)) syncLevel(level);
        }
    }

    public static void releaseOwnedReturn(ServerPlayer player) {
        WorldRift owned = data(player.level()).ownedReturn(player.getUUID());
        if (owned != null) owned.releaseOwner();
    }

    public static void syncPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot(player));
    }

    public static void syncLevel(ServerLevel level) {
        for (ServerPlayer player : level.players()) syncPlayer(player);
    }

    private static boolean expire(ServerLevel level) {
        WorldRiftData data = data(level);
        long now = level.getGameTime();
        List<WorldRift> stale = new ArrayList<>();
        for (WorldRift rift : data.rifts()) {
            if (rift.expired(now)) stale.add(rift);
        }
        if (stale.isEmpty()) return false;
        for (WorldRift rift : stale) {
            data.remove(rift.id());
            if (rift.kind() == MirrorRiftKind.ENTRY && rift.anomalous()) {
                FluxRiftManager.considerFailedTear(level, rift.anchor());
            }
        }
        return true;
    }

    private static void relocateOwnedReturn(ServerPlayer player, WorldRift old) {
        ServerLevel level = player.level();
        Site site = findSite(level, player.blockPosition(), level.getRandom());
        if (site == null) site = findSkySite(level, player.blockPosition(), level.getRandom());
        if (site == null) return;
        data(level).remove(old.id());
        long now = level.getGameTime();
        data(level).add(new WorldRift(UUID.randomUUID(), MirrorRiftKind.RETURN, player.getUUID(),
                site.anchor(), site.facing(), old.shapeSeed(), now,
                now + Config.returnRiftLifetimeTicks()));
        syncLevel(level);
    }

    private static MirrorRiftSyncPayload snapshot(ServerPlayer player) {
        ServerLevel level = player.level();
        boolean phased = MirrorPhase.isPhased(player);
        long now = level.getGameTime();
        List<MirrorRiftSyncPayload.Snapshot> visible = new ArrayList<>();
        for (WorldRift rift : data(level).rifts()) {
            if (!rift.visibleTo(phased, player.getUUID()) || rift.expired(now)) continue;
            visible.add(new MirrorRiftSyncPayload.Snapshot(
                    rift.id(), rift.kind(), rift.anchor(), rift.facing(), rift.shapeSeed(),
                    !rift.opened(now)));
        }
        return new MirrorRiftSyncPayload(List.copyOf(visible));
    }

    @Nullable
    private static WorldRift nearbyReturn(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        double limit = Config.riftRelocateDistance();
        double limitSq = limit * limit;
        WorldRift nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (WorldRift rift : data(level).rifts()) {
            if (rift.kind() != MirrorRiftKind.RETURN || rift.expired(now)) continue;
            double distanceSq = player.distanceToSqr(rift.geometry().centre());
            if (distanceSq > limitSq || distanceSq >= nearestSq) continue;
            nearest = rift;
            nearestSq = distanceSq;
        }
        return nearest;
    }

    private static int countEntries(ServerLevel level) {
        long now = level.getGameTime();
        int count = 0;
        for (WorldRift rift : data(level).rifts()) {
            if (rift.kind() == MirrorRiftKind.ENTRY && !rift.expired(now)) count++;
        }
        return count;
    }

    private static boolean hasNearbyEntry(ServerLevel level, BlockPos origin) {
        long now = level.getGameTime();
        double limitSq = (double) Config.entryRiftSpacing() * Config.entryRiftSpacing();
        for (WorldRift rift : data(level).rifts()) {
            if (rift.kind() != MirrorRiftKind.ENTRY || rift.expired(now)) continue;
            if (rift.anchor().distSqr(origin) < limitSq) return true;
        }
        return false;
    }

    private static WorldRiftData data(ServerLevel level) {
        return level.getData(ModAttachments.WORLD_RIFTS);
    }

    @Nullable
    private static Site findSite(ServerLevel level, BlockPos origin, RandomSource random) {
        boolean buried = isBuried(level, origin);
        int min = buried ? 4 : Config.riftMinDistance();
        int max = buried ? Math.max(min + 1, Math.min(14, Config.riftMaxDistance())) : Config.riftMaxDistance();
        double reachSq = (double) Config.riftRelocateDistance() * Config.riftRelocateDistance();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = min + random.nextDouble() * Math.max(1, max - min);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!isLoaded(level, new BlockPos(x, origin.getY(), z))) continue;
            Direction facing = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
            int y = findStandY(level, x, z, origin.getY(), facing);
            if (y == Integer.MIN_VALUE) continue;
            BlockPos anchor = new BlockPos(x, y, z);
            if (origin.distSqr(anchor) > reachSq) continue;
            return new Site(anchor, facing);
        }
        return null;
    }

    /** True when the heightmap is a different storey — caves, mines, and hanging buildings. */
    private static boolean isBuried(ServerLevel level, BlockPos origin) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        return surface - origin.getY() > 8;
    }

    /**
     * Pick a floor near {@code preferY}. The heightmap is only trusted when it is already close;
     * otherwise a vertical window around the player is scanned so a cave does not snap to the roof.
     */
    private static int findStandY(ServerLevel level, int x, int z, int preferY, Direction facing) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(surface - preferY) <= 8 && isValid(level, new BlockPos(x, surface, z), facing)) {
            return surface;
        }
        int minY = level.getMinY() + 1;
        int maxY = level.getMaxY() - 4;
        int from = Math.max(minY, preferY - 12);
        int to = Math.min(maxY, preferY + 8);
        int span = Math.max(preferY - from, to - preferY);
        for (int delta = 0; delta <= span; delta++) {
            int up = preferY + delta;
            if (up <= to && isValid(level, new BlockPos(x, up, z), facing)) return up;
            int down = preferY - delta;
            if (delta != 0 && down >= from && isValid(level, new BlockPos(x, down, z), facing)) {
                return down;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Last resort: a 2×3 air pocket near the player, no floor. Used when caves are too tight
     * for a standing tear; if even that fails the tear is skipped.
     */
    @Nullable
    private static Site findSkySite(ServerLevel level, BlockPos origin, RandomSource random) {
        boolean buried = isBuried(level, origin);
        int min = buried ? 3 : Config.riftMinDistance();
        int max = buried ? Math.max(min + 1, Math.min(10, Config.riftMaxDistance())) : Config.riftMaxDistance();
        double reachSq = (double) Config.riftRelocateDistance() * Config.riftRelocateDistance();
        int[] dy = {0, 1, 2, 3, 4, 5, -1, -2};
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = min + random.nextDouble() * Math.max(1, max - min);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
            if (!isLoaded(level, new BlockPos(x, origin.getY(), z))) continue;
            Direction facing = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
            for (int offset : dy) {
                int y = origin.getY() + offset;
                if (y < level.getMinY() + 1 || y > level.getMaxY() - 4) continue;
                BlockPos anchor = new BlockPos(x, y, z);
                if (origin.distSqr(anchor) > reachSq) continue;
                if (isAirStand(level, anchor, facing)) return new Site(anchor, facing);
            }
        }
        return null;
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static boolean isValid(ServerLevel level, BlockPos anchor, Direction facing) {
        Direction tangent = facing.getClockWise();
        for (int side = 0; side < 2; side++) {
            BlockPos base = anchor.relative(tangent, side);
            BlockPos floor = base.below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
            for (int y = 0; y < 3; y++) {
                if (!level.getBlockState(base.above(y)).isAir()) return false;
            }
        }
        return true;
    }

    private static boolean isAirStand(ServerLevel level, BlockPos anchor, Direction facing) {
        Direction tangent = facing.getClockWise();
        for (int side = 0; side < 2; side++) {
            BlockPos base = anchor.relative(tangent, side);
            for (int y = 0; y < 3; y++) {
                if (!level.getBlockState(base.above(y)).isAir()) return false;
            }
        }
        return true;
    }

    private static boolean isUsable(ServerLevel level, BlockPos anchor, Direction facing) {
        return isValid(level, anchor, facing) || isAirStand(level, anchor, facing);
    }

    private record Site(BlockPos anchor, Direction facing) {}
}
