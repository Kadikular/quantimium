package com.kadikular.quantimium.phase;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.network.VeiledFlickerPayload;
import com.kadikular.quantimium.network.VeiledGlimpsePayload;
import com.kadikular.quantimium.network.VeiledSightingPayload;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import com.kadikular.quantimium.item.MirrorLensItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything about the Veiled that is not its body: release from rifts, the one-per-area rule,
 * personal real-world sightings, and which Containment Halls are holding one.
 *
 * <p>Sightings are per player. The server chooses where one appears and tells only that player;
 * their client draws the silhouette, judges the gaze, and reports when it is over. Every ended
 * sighting — any player's — steps the true body closer, so a group draws it in faster.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class VeiledManager {

    /** Held Veiled, by hall controller position. Saved, so a held one stays held across loads. */
    public static final class HeldHalls {
        public static final Codec<HeldHalls> CODEC = BlockPos.CODEC.listOf()
                .xmap(list -> new HeldHalls(new HashSet<>(list)), halls -> List.copyOf(halls.positions));
        private final Set<BlockPos> positions;

        public HeldHalls() {
            this(new HashSet<>());
        }

        private HeldHalls(Set<BlockPos> positions) {
            this.positions = positions;
        }

        public boolean isEmpty() {
            return positions.isEmpty();
        }
    }

    private record Sighting(int id, int entityId, long expires) {}

    /** Mean seconds between release rolls on a stage 4 rift at Critical flux or above. */
    private static final int RELEASE_MEAN_SECONDS = 300;
    private static final double SIGHT_MIN = 20.0;
    /** Lights falter for real-world players this close to its true position. */
    private static final double FLICKER_RANGE = 40.0;
    private static final double SIGHT_MAX = 64.0;
    /** Server-side limit on a sighting the client never reports back. */
    private static final long SIGHTING_TIMEOUT = 2400L;

    private static final Map<UUID, Sighting> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    /** When each shadowed player may next catch a glimpse. */
    private static final Map<UUID, Long> NEXT_GLIMPSE = new HashMap<>();
    private static int nextId = 1;

    private VeiledManager() {}

    // ---- Release and the one-per-area rule ----

    /** Called each second for every uncontained rift; only a stage 4 wound at Critical flux can do it. */
    public static void considerRelease(ServerLevel level, FluxRift rift) {
        if (rift.stage() < FluxRift.MAX_STAGE) return;
        if (!FluxBand.CRITICAL.covers(QuantumFlux.chunk(level, rift.anchor()).fluxBand())) return;
        if (level.getRandom().nextInt(RELEASE_MEAN_SECONDS) != 0) return;
        if (!canRelease(level, rift.anchor())) return;
        release(level, BlockPos.containing(rift.centre()).below());
    }

    /**
     * How far a held Veiled keeps the peace: no rift this close to an occupied hall releases another,
     * and a free one this close comes apart. Smaller than {@link Veiled#AREA}, its hunting range, so a
     * hall guards its base and the land round it, not a whole region.
     */
    public static final double HELD_PEACE = 128.0;

    /** No free Veiled within {@link Veiled#AREA}, and no held one within {@link #HELD_PEACE}. */
    public static boolean canRelease(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(Veiled.AREA);
        if (!level.getEntities(ModEntities.VEILED.get(), area, entity -> true).isEmpty()) return false;
        return !nearHeldOne(level, pos);
    }

    /** Whether an occupied hall stands within {@link #HELD_PEACE} of {@code pos}. */
    public static boolean nearHeldOne(ServerLevel level, BlockPos pos) {
        double areaSq = HELD_PEACE * HELD_PEACE;
        for (BlockPos hall : halls(level).positions) {
            if (hall.distSqr(pos) < areaSq) return true;
        }
        return false;
    }

    /**
     * A held Veiled tolerates no other near it: a free one inside its area comes apart where it
     * stands and is gone, as if it had never been released. Whoever is fighting it loses the fight.
     */
    public static void dispel(ServerLevel level, Veiled veiled) {
        veiled.playSound(ModSounds.VEILED_UNRAVEL.get(), 1.4f, 1.0f);
        mirrorFlash(level, veiled.position());
        veiled.discard();
    }

    @Nullable
    public static Veiled release(ServerLevel level, BlockPos pos) {
        Veiled veiled = ModEntities.VEILED.get().create(level, EntitySpawnReason.EVENT);
        if (veiled == null) return null;
        veiled.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        veiled.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(veiled);
        mirrorFlash(level, veiled.position());
        return veiled;
    }

    // ---- Containment ----

    /** Taken into the hall's cell. The body goes; the hall holds it from here. */
    public static void contain(ServerLevel level, Veiled veiled, ContainmentHallBlockEntity hall) {
        mirrorFlash(level, veiled.position());
        veiled.discard();
        hall.contain();
        halls(level).positions.add(hall.getBlockPos().immutable());
        // The hall is real, so everyone hears it close and sees the cell flare, phased or not.
        Vec3 cell = Vec3.atCenterOf(hall.getBlockPos().above(2));
        level.playSound(null, cell.x, cell.y, cell.z, ModSounds.VEILED_CONTAINED.get(), SoundSource.BLOCKS, 1.6f, 1.0f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cell.x, cell.y, cell.z, 80, 0.25, 1.2, 0.25, 0.15);
        level.sendParticles(ParticleTypes.WITCH, cell.x, cell.y, cell.z, 24, 0.3, 1.0, 0.3, 0.05);
    }

    /** The nearest formed, powered hall with room, within {@code range} and its own capture reach. */
    @Nullable
    public static ContainmentHallBlockEntity findHall(ServerLevel level, BlockPos near, double range) {
        int chunkRadius = SectionPos.blockToSectionCoord((int) range) + 1;
        int cx = SectionPos.blockToSectionCoord(near.getX());
        int cz = SectionPos.blockToSectionCoord(near.getZ());
        double rangeSq = range * range;
        ContainmentHallBlockEntity best = null;
        double bestSq = rangeSq;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ContainmentHallBlockEntity hall) || !hall.canHold()) continue;
                    double distanceSq = hall.getBlockPos().distSqr(near);
                    if (distanceSq > hall.captureRange() * hall.captureRange()) continue;
                    if (distanceSq < bestSq) {
                        best = hall;
                        bestSq = distanceSq;
                    }
                }
            }
        }
        return best;
    }

    /**
     * The nearest hall within {@code range}, whether or not it could hold anything: for saying why
     * one didn't.
     */
    @Nullable
    public static ContainmentHallBlockEntity nearestHall(ServerLevel level, BlockPos near, double range) {
        int chunkRadius = SectionPos.blockToSectionCoord((int) range) + 1;
        int cx = SectionPos.blockToSectionCoord(near.getX());
        int cz = SectionPos.blockToSectionCoord(near.getZ());
        ContainmentHallBlockEntity best = null;
        double bestSq = range * range;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                if (!level.hasChunk(x, z)) continue;
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                    if (!(be instanceof ContainmentHallBlockEntity hall)) continue;
                    double distanceSq = hall.getBlockPos().distSqr(near);
                    if (distanceSq < bestSq) {
                        best = hall;
                        bestSq = distanceSq;
                    }
                }
            }
        }
        return best;
    }

    /** The hall lost its power or its structure: out it comes, already feeding on the hall. */
    public static void releaseFromHall(ServerLevel level, BlockPos hallPos) {
        halls(level).positions.remove(hallPos);
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.getRandom().nextDouble() * Mth.TWO_PI;
            int x = hallPos.getX() + Mth.floor(Math.cos(angle) * 3.5);
            int z = hallPos.getZ() + Mth.floor(Math.sin(angle) * 3.5);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - hallPos.getY()) > 6) y = hallPos.getY();
            Veiled veiled = release(level, new BlockPos(x, y, z));
            if (veiled != null) {
                veiled.releasedFrom(hallPos);
                return;
            }
        }
    }

    public static void forgetHall(ServerLevel level, BlockPos hallPos) {
        halls(level).positions.remove(hallPos);
    }

    private static HeldHalls halls(ServerLevel level) {
        return level.getData(ModAttachments.HELD_VEILED);
    }

    // ---- Sightings ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            ACTIVE.values().removeIf(sighting -> now > sighting.expires);
            if (level.players().isEmpty()) continue;
            for (Veiled veiled : level.getEntities(ModEntities.VEILED.get(), entity -> true)) {
                if (nearHeldOne(level, veiled.blockPosition())) {
                    dispel(level, veiled);
                    continue;
                }
                if (!veiled.onCooldown()) flickerNear(level, veiled);
                if (veiled.state() == Veiled.State.SHADOWING) glimpse(level, veiled, now);
                if (veiled.state() != Veiled.State.WANDER || veiled.onCooldown()) continue;
                for (ServerPlayer player : level.players()) {
                    if (MirrorPhase.isPhased(player) || player.isSpectator()) continue;
                    if (ACTIVE.containsKey(player.getUUID())) continue;
                    if (now < NEXT.getOrDefault(player.getUUID(), 0L)) continue;
                    double distance = player.distanceTo(veiled);
                    if (distance > Veiled.AREA || distance < 16.0) continue;
                    trySighting(level, veiled, player, now);
                }
            }
        }
    }

    /**
     * Lamps near its true position falter for anyone in the real world: strongest right beside it,
     * nothing past {@link #FLICKER_RANGE}. Phased players are in the mirror, where there are no lamps
     * to falter.
     */
    private static void flickerNear(ServerLevel level, Veiled veiled) {
        for (ServerPlayer player : level.players()) {
            if (MirrorPhase.isPhased(player) || player.isSpectator()) continue;
            double distance = player.distanceTo(veiled);
            if (distance > FLICKER_RANGE) continue;
            float strength = (float) (1.0 - distance / FLICKER_RANGE);
            PacketDistributor.sendToPlayer(player, new VeiledFlickerPayload(strength));
        }
    }

    /**
     * While it follows a player with no base nearby, that player now and then catches it at the edge
     * of their view: on screen, but well off where they are looking, and never where they turn to.
     * A quiet sound from the same spot. It does not move its true position; it is only the tell.
     */
    private static void glimpse(ServerLevel level, Veiled veiled, long now) {
        UUID target = veiled.shadowTarget();
        if (target == null) return;
        if (!(level.getPlayerByUUID(target) instanceof ServerPlayer player)) return;
        if (MirrorPhase.isPhased(player) || player.isSpectator()) return;
        if (now < NEXT_GLIMPSE.getOrDefault(target, 0L)) return;
        NEXT_GLIMPSE.put(target, now + 300L + level.getRandom().nextInt(300));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double lookYaw = Math.atan2(look.z, look.x);
        for (int attempt = 0; attempt < 12; attempt++) {
            double off = Math.toRadians(55.0 + level.getRandom().nextDouble() * 20.0) * (level.getRandom().nextBoolean() ? 1 : -1);
            double d = 8.0 + level.getRandom().nextDouble() * 6.0;
            int x = Mth.floor(player.getX() + Math.cos(lookYaw + off) * d);
            int z = Mth.floor(player.getZ() + Math.sin(lookYaw + off) * d);
            int y = standY(level, x, z, player.getBlockY());
            if (y == Integer.MIN_VALUE) continue;
            Vec3 head = new Vec3(x + 0.5, y + 2.4, z + 0.5);
            if (level.clip(new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new VeiledGlimpsePayload(x + 0.5, y, z + 0.5));
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.VEILED_STARE.get()), SoundSource.HOSTILE,
                    head.x, head.y, head.z, 0.35f, 0.8f, level.getRandom().nextLong()));
            return;
        }
    }

    /** For testing: a sighting now, from the nearest Veiled in the area. */
    public static boolean forceSighting(ServerPlayer player) {
        ServerLevel level = player.level();
        List<Veiled> near = level.getEntities(ModEntities.VEILED.get(),
                player.getBoundingBox().inflate(Veiled.AREA), entity -> true);
        if (near.isEmpty()) return false;
        ACTIVE.remove(player.getUUID());
        return trySighting(level, near.get(0), player, level.getGameTime());
    }

    /**
     * Somewhere the player will see it: ahead of them, biased towards where it really is, at a
     * distance that tracks how close it really is, on open ground with a clear line from their eyes.
     */
    private static boolean trySighting(ServerLevel level, Veiled veiled, ServerPlayer player, long now) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double lookYaw = Math.atan2(look.z, look.x);
        double trueYaw = Math.atan2(veiled.getZ() - player.getZ(), veiled.getX() - player.getX());
        double diff = Mth.wrapDegrees(Math.toDegrees(trueYaw - lookYaw));
        double base = Math.abs(diff) < 60.0 ? trueYaw : lookYaw + Math.toRadians(Math.signum(diff) * 35.0);
        double distance = Mth.clamp(player.distanceTo(veiled), SIGHT_MIN, SIGHT_MAX);

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = base + Math.toRadians((level.getRandom().nextDouble() - 0.5) * 50.0);
            double d = Math.max(16.0, distance + (level.getRandom().nextDouble() - 0.5) * 12.0);
            int x = Mth.floor(player.getX() + Math.cos(angle) * d);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * d);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
            int y = standY(level, x, z, player.getBlockY());
            if (y == Integer.MIN_VALUE) continue;
            Vec3 head = new Vec3(x + 0.5, y + 2.4, z + 0.5);
            if (level.clip(new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(eye.z - head.z, eye.x - head.x)) - 90.0f;
            int id = nextId++;
            ACTIVE.put(player.getUUID(), new Sighting(id, veiled.getId(), now + SIGHTING_TIMEOUT));
            PacketDistributor.sendToPlayer(player, new VeiledSightingPayload(id, x + 0.5, y, z + 0.5, yaw));
            return true;
        }
        NEXT.put(player.getUUID(), now + 200L);
        return false;
    }

    /** Ground with three blocks of air, preferring the surface, else a floor near the player's height. */
    private static int standY(ServerLevel level, int x, int z, int near) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(surface - near) <= 20 && standable(level, x, surface, z)) return surface;
        for (int dy = 0; dy <= 12; dy++) {
            if (standable(level, x, near + dy, z)) return near + dy;
            if (standable(level, x, near - dy, z)) return near - dy;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean standable(ServerLevel level, int x, int y, int z) {
        BlockPos floor = new BlockPos(x, y - 1, z);
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        for (int up = 0; up < 3; up++) {
            if (!level.getBlockState(new BlockPos(x, y + up, z)).isAir()) return false;
        }
        return true;
    }

    /** The client reports its sighting over: faded under a steady gaze, timed out, or walked up to. */
    public static void sightingEnded(ServerPlayer player, int id) {
        Sighting sighting = ACTIVE.get(player.getUUID());
        if (sighting == null || sighting.id != id) return;
        ACTIVE.remove(player.getUUID());
        ServerLevel level = player.level();
        NEXT.put(player.getUUID(), level.getGameTime() + nextSightingGap(player, level.getRandom()));
        Entity entity = level.getEntity(sighting.entityId);
        if (entity instanceof Veiled veiled) veiled.advance();
    }

    /**
     * Ticks until the next sighting may come: 45 to 120 seconds, and half that through a Mirror Lens,
     * which lets the mirror show through more readily, in both directions.
     */
    public static long nextSightingGap(Player player, RandomSource random) {
        long gap = 900L + random.nextInt(1500);
        return MirrorLensItem.isWorn(player) ? gap / 2 : gap;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        NEXT.clear();
        NEXT_GLIMPSE.clear();
    }

    private static void mirrorFlash(ServerLevel level, Vec3 at) {
        for (ServerPlayer player : level.players()) {
            if (!MirrorPhase.isPhased(player)) continue;
            level.sendParticles(player, ParticleTypes.REVERSE_PORTAL, false, false, at.x, at.y + 1.4, at.z,
                    40, 0.4, 1.0, 0.4, 0.08);
        }
    }
}
