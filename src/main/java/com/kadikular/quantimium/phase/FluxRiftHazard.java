package com.kadikular.quantimium.phase;

import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.init.ModDamageTypes;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.network.FluxRiftStrikePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a flux rift does to things near it. Touching it throws you off — and, from the real world,
 * through: the shadow is thin, and you come out the other side. Anything in range gets lightning
 * arcing out of the wound, more often the closer it stands. Both work in both realms, since the
 * rift is in both; mirror mites, being of the rift, are left alone.
 */
public final class FluxRiftHazard {

    /** Game time of an entity's last launch, so one touch is one throw rather than a juggle. */
    private static final String LAUNCH_TAG = "quantimium:flux_rift_launch";
    private static final long LAUNCH_COOLDOWN = 20L;

    private FluxRiftHazard() {}

    /** Lightning timers are checked this often. */
    public static final int STRIKE_CHECK = 5;
    private static final int MIN_INTERVAL = 10;

    /** Last time each player was thrown for swinging at a rift, so one swing is one throw. */
    private static final Map<UUID, Long> TOUCHED = new HashMap<>();

    /**
     * Swung at with a weapon — before the Lance, the only way to reach it. It can be touched, not
     * beaten: it throws the player back, stings, and blinds them for two seconds. Reach and sight are
     * checked here, not trusted from the client.
     */
    public static void touched(ServerPlayer player, FluxRift rift) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        if (now - TOUCHED.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < 10L) return;
        Vec3 eye = player.getEyePosition();
        Vec3 centre = rift.centre();
        if (eye.distanceTo(centre) > 5.0 + FluxRift.hitRadius(rift.stage())) return;
        if (level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() != HitResult.Type.MISS) {
            return;
        }
        TOUCHED.put(player.getUUID(), now);
        player.hurt(level.damageSources().source(ModDamageTypes.FLUX_RIFT), 2.0f);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
        Vec3 away = new Vec3(player.getX() - centre.x, 0.0, player.getZ() - centre.z);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        player.setDeltaMovement(away.x * 0.9, 0.35, away.z * 0.9);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        level.playSound(null, centre.x, centre.y, centre.z, ModSounds.RIFT_ZAP.get(), SoundSource.HOSTILE, 1.0f, 0.7f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 30, 0.3, 0.6, 0.3, 0.1);
    }

    /**
     * A stabilised rift keeps only its short-range bite: touching it still throws you off, but not
     * through into the mirror, and it arcs only at whatever stands within a couple of blocks.
     */
    public static void tick(ServerLevel level, FluxRift rift, long now, boolean strikeTick) {
        boolean held = rift.stabilised(now);
        contact(level, rift, now, held);
        if (strikeTick) strike(level, rift, now, held);
    }

    /** Held rifts only arc at close quarters. */
    private static final double HELD_STRIKE_REACH = 1.5;

    private static void contact(ServerLevel level, FluxRift rift, long now, boolean held) {
        int stage = rift.stage();
        double solid = FluxRift.solidRadius(stage);
        double bottom = FluxRift.bottom(rift.anchor());
        double top = FluxRift.top(rift.anchor(), stage);
        Vec3 axis = rift.centre();
        AABB box = new AABB(axis.x - solid - 1.0, bottom, axis.z - solid - 1.0,
                axis.x + solid + 1.0, top, axis.z + solid + 1.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, FluxRiftHazard::affected)) {
            double dx = entity.getX() - axis.x;
            double dz = entity.getZ() - axis.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= solid + entity.getBbWidth() * 0.5) continue;
            launch(level, stage, entity, dx, dz, distance, now, held);
        }
    }

    /**
     * Burned and thrown clear in a random direction, biased away from the rift so the throw cannot land you
     * straight back in it. An unphased player is dragged through into the mirror on the way.
     */
    private static void launch(ServerLevel level, int stage, LivingEntity entity, double dx, double dz,
                               double distance, long now, boolean held) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(LAUNCH_TAG) && now - data.getLongOr(LAUNCH_TAG, 0L) < LAUNCH_COOLDOWN) return;
        data.putLong(LAUNCH_TAG, now);

        if (!held && entity instanceof ServerPlayer player && !MirrorPhase.isPhased(player)) MirrorPhase.enter(player);
        // Hurt first: the throw's velocity has to be the last word, or the hurt could overwrite it.
        entity.hurt(level.damageSources().source(ModDamageTypes.FLUX_RIFT), 1.0f + stage);

        double outward = distance < 1.0E-4 ? level.getRandom().nextDouble() * Mth.TWO_PI : Math.atan2(dz, dx);
        double angle = outward + (level.getRandom().nextDouble() - 0.5) * Math.PI * 1.2;
        double speed = 1.0 + 0.15 * stage;
        // Kept under a three-block arc so the landing does not add fall damage on top.
        double lift = 0.5 + 0.03 * stage;
        entity.setDeltaMovement(Math.cos(angle) * speed, lift, Math.sin(angle) * speed);
        entity.resetFallDistance();
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.RIFT_LAUNCH.get(),
                SoundSource.HOSTILE, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                entity.getZ(), 30, 0.3, 0.5, 0.3, 0.12);
    }

    /**
     * Lightning on a timer per target rather than a dice roll per check: rolls clump, and read as
     * bursts of rapid fire between long silences. Each thing in range gets its next strike scheduled
     * from how close it stands — every 2.5 s at the edge down to every 0.5 s point blank — with a
     * little jitter so it does not feel mechanical. Line of sight is only traced when a strike is due.
     * Bolts leave from the heart of the wound, so every arc visibly comes out of the rift itself.
     */
    private static void strike(ServerLevel level, FluxRift rift, long now, boolean held) {
        int stage = rift.stage();
        double range = held ? FluxRift.solidRadius(stage) + HELD_STRIKE_REACH : FluxRift.strikeRange(stage);
        double bottom = FluxRift.bottom(rift.anchor());
        double top = FluxRift.top(rift.anchor(), stage);
        Vec3 axis = rift.centre();
        AABB box = new AABB(axis.x - range, bottom - range, axis.z - range,
                axis.x + range, top + range, axis.z + range);
        Map<Integer, Long> timers = rift.strikeTimers();
        Set<Integer> present = new HashSet<>();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, FluxRiftHazard::affected)) {
            Vec3 body = entity.getBoundingBox().getCenter();
            Vec3 nearest = new Vec3(axis.x, Mth.clamp(body.y, bottom, top), axis.z);
            double distance = body.distanceTo(nearest);
            if (distance > range) continue;
            present.add(entity.getId());
            double closeness = Mth.clamp(1.0 - distance / range, 0.0, 1.0);
            Long due = timers.get(entity.getId());
            if (due == null) {
                // First sight: a short grace so stepping into range is a warning, not an instant hit.
                timers.put(entity.getId(), now + interval(level, closeness) / 2);
                continue;
            }
            if (now < due) continue;
            timers.put(entity.getId(), now + interval(level, closeness));

            Vec3 origin = rift.centre();
            HitResult sight = level.clip(new ClipContext(origin, body, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (sight.getType() != HitResult.Type.MISS) continue;

            entity.hurt(level.damageSources().source(ModDamageTypes.FLUX_RIFT), 2.5f + 0.5f * stage);
            showBolt(level, origin, body, entity);
        }
        timers.keySet().retainAll(present);
    }

    /**
     * 50 ticks at the edge of range to 10 point blank, ±15%. Never under 10: that is vanilla's hurt
     * immunity window, and a faster bolt would be swallowed by it (or only land the difference).
     */
    private static long interval(ServerLevel level, double closeness) {
        double ticks = Mth.lerp(closeness, 50.0, MIN_INTERVAL) * (0.85 + level.getRandom().nextDouble() * 0.3);
        return Math.max(MIN_INTERVAL, Math.round(ticks));
    }

    /** Bolts belong to the struck thing's realm: a phased player's is drawn in the mirror, anything else's in the world. */
    private static void showBolt(ServerLevel level, Vec3 from, Vec3 to, LivingEntity struck) {
        boolean mirror = struck instanceof Player player && MirrorPhase.isPhased(player);
        FluxRiftStrikePayload payload = new FluxRiftStrikePayload(from, to);
        for (ServerPlayer viewer : level.players()) {
            if (MirrorPhase.isPhased(viewer) != mirror) continue;
            if (viewer.distanceToSqr(to) > 64.0 * 64.0) continue;
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static boolean affected(LivingEntity entity) {
        if (!entity.isAlive() || entity instanceof MirrorEndermite) return false;
        return !(entity instanceof Player player) || !(player.isCreative() || player.isSpectator());
    }
}
