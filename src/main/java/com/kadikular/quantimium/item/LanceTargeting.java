package com.kadikular.quantimium.item;

import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.entity.Veiled;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a Decoherence Lance beam is touching. Shared by the server, which acts on it, and the client,
 * which draws the beam, so both agree on where it ends without the target being synced.
 */
public final class LanceTargeting {

    public static final double RANGE = 16.0;
    private static final double ENTITY_HALO = 0.4;

    public enum Kind {
        /** Nothing to decohere; the beam just ends on a block or at full range. */
        NONE,
        /** A rift's anchor, reachable only from the mirror. */
        RIFT,
        /** A rift seen from the real world: only its shadow, so the beam passes into nothing. */
        SHADOW,
        ENTITY
    }

    public record RiftSphere(UUID id, Vec3 centre, double radius) {}

    public record Target(Kind kind, Vec3 point, @Nullable UUID rift, @Nullable Entity entity) {}

    private LanceTargeting() {}

    /**
     * Only things in superposition are affected. Ordinary creatures have nothing to decohere, which
     * is what keeps the lance a tool that happens to fight rather than a better sword.
     */
    public static boolean affects(Entity entity, boolean phased) {
        if (!entity.isAlive()) return false;
        if (entity instanceof Veiled) return phased;
        return entity instanceof MirrorEndermite mite && (phased || mite.isBreached());
    }

    public static Target find(Player player, float partialTick, Iterable<RiftSphere> rifts, boolean phased) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(RANGE));

        Vec3 blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        double best = blockHit.distanceTo(eye);
        Target target = new Target(Kind.NONE, blockHit, null, null);

        for (RiftSphere rift : rifts) {
            double distance = raySphere(eye, look, rift.centre(), rift.radius());
            if (distance < 0.0 || distance >= best) continue;
            best = distance;
            target = new Target(phased ? Kind.RIFT : Kind.SHADOW, eye.add(look.scale(distance)),
                    rift.id(), null);
        }

        Vec3 reach = eye.add(look.scale(best));
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(best)).inflate(1.0);
        Entity closest = null;
        Vec3 closestPoint = null;
        double closestDistance = best;
        for (Entity entity : player.level().getEntities(player, sweep, entity -> affects(entity, phased))) {
            // Mites are tiny; a beam has width, so give them a generous halo rather than demanding a pixel hit.
            AABB halo = entity.getBoundingBox().inflate(ENTITY_HALO);
            Vec3 point = halo.contains(eye) ? eye : halo.clip(eye, reach).orElse(null);
            if (point == null) continue;
            double distance = eye.distanceTo(point);
            if (distance >= closestDistance) continue;
            closest = entity;
            closestPoint = entity.getBoundingBox().getCenter();
            closestDistance = distance;
        }
        if (closest != null) {
            target = new Target(Kind.ENTITY, closestPoint, null, closest);
        }
        return target;
    }

    /** Distance along a unit ray to the sphere, 0 from inside, or -1 on a miss. */
    private static double raySphere(Vec3 origin, Vec3 direction, Vec3 centre, double radius) {
        Vec3 offset = origin.subtract(centre);
        double along = offset.dot(direction);
        double outside = offset.lengthSqr() - radius * radius;
        if (outside <= 0.0) return 0.0;
        double discriminant = along * along - outside;
        if (discriminant < 0.0) return -1.0;
        double distance = -along - Math.sqrt(discriminant);
        return distance >= 0.0 ? distance : -1.0;
    }
}
