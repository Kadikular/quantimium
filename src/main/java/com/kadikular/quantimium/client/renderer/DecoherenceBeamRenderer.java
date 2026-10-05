package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.client.ClientPhaseState;
import com.kadikular.quantimium.client.FluxRiftClientCache;
import com.kadikular.quantimium.client.MirrorAtmosphereEffects;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.item.LanceTargeting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decoherence Lance beams for every player channelling one in the viewer's realm. The beam is azure
 * — coherent Quantimium light — while what it tears loose streams back along it in anomaly violet,
 * so the target looks like it is being drawn into the lance. Targets are recomputed here with the
 * shared {@link LanceTargeting}, so nothing about a beam has to be synced.
 *
 * <p>The stream is drawn motes rather than vanilla particles: particles fly to where the lance
 * <em>was</em>, so a moving player left them trailing behind. Motes home on the lance's current tip
 * every tick and fade out if the player has moved away faster than they can follow.
 */
public final class DecoherenceBeamRenderer {

    private static final int SEGMENTS = 20;
    private static final int MOTE_LIFE = 60;
    private static final float MOTE_START_SPEED = 0.10f;
    private static final float MOTE_ACCELERATION = 0.025f;
    /** A mote this much further from the tip than it started has lost the player and fades. */
    private static final double MOTE_LOST_SLACK = 2.5;
    private static final int MOTE_CAP = 600;

    private static final class Mote {
        private final UUID owner;
        private Vec3 position;
        private Vec3 previous;
        private final double startDistance;
        private int age;
        private float speed = MOTE_START_SPEED;
        private float alpha = 1.0f;
        private float previousAlpha = 1.0f;
        private boolean fading;

        private Mote(UUID owner, Vec3 position, double startDistance) {
            this.owner = owner;
            this.position = position;
            this.previous = position;
            this.startDistance = startDistance;
        }
    }

    private static final List<Mote> MOTES = new ArrayList<>();
    /** Current tip of each channelling player. A mote whose owner stopped fades where it is. */
    private static final Map<UUID, Vec3> TIPS = new HashMap<>();

    private DecoherenceBeamRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick, float time) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        VertexConsumer glow = null;
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (!channelling(player)) continue;
            LanceTargeting.Target target = target(player, partialTick);
            Vec3 from = tip(player, partialTick);
            if (glow == null) glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
            beam(glow, poseStack, camera, from, target.point(), time, target.kind());
        }
        if (!MOTES.isEmpty()) {
            if (glow == null) glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
            motes(glow, poseStack, camera, partialTick);
        }
    }

    /** Frozen while paused, like the rifts, so nothing piles up to burst out on unpause. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            MOTES.clear();
            TIPS.clear();
            return;
        }
        if (minecraft.isPaused()) return;
        boolean effects = MirrorAtmosphereEffects.effectsAllowed(minecraft);
        RandomSource random = minecraft.level.getRandom();
        List<UUID> active = new ArrayList<>();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (!channelling(player)) continue;
            active.add(player.getUUID());
            LanceTargeting.Target target = target(player, 1.0f);
            Vec3 tip = tip(player, 1.0f);
            TIPS.put(player.getUUID(), tip);
            boolean biting = target.kind() == LanceTargeting.Kind.RIFT
                    || target.kind() == LanceTargeting.Kind.ENTITY;
            int held = player.getTicksUsingItem();
            if (held % 40 == 1) {
                minecraft.level.playLocalSound(tip.x, tip.y, tip.z, ModSounds.LANCE_BEAM.get(),
                        SoundSource.PLAYERS, 0.5f, 1.0f, false);
            }
            if (biting && held % 10 == 0) {
                Vec3 at = target.point();
                float pitch = target.kind() == LanceTargeting.Kind.ENTITY ? 1.5f : 1.0f;
                minecraft.level.playLocalSound(at.x, at.y, at.z, ModSounds.RIFT_DRAIN.get(),
                        SoundSource.PLAYERS, 0.7f, pitch, false);
            }
            if (effects) spawnMotes(player.getUUID(), tip, target.point(), biting, random);
        }
        TIPS.keySet().retainAll(active);
        tickMotes(active);
    }

    private static void spawnMotes(UUID owner, Vec3 tip, Vec3 target, boolean biting, RandomSource random) {
        if (MOTES.size() >= MOTE_CAP) return;
        int count = biting ? 4 : 1;
        for (int i = 0; i < count; i++) {
            // Biased towards the far end, so most of the stream visibly leaves the target.
            double along = biting ? 1.0 - random.nextDouble() * random.nextDouble() : random.nextDouble();
            Vec3 start = tip.lerp(target, along).add(
                    (random.nextDouble() - 0.5) * 0.35, (random.nextDouble() - 0.5) * 0.35,
                    (random.nextDouble() - 0.5) * 0.35);
            MOTES.add(new Mote(owner, start, start.distanceTo(tip)));
        }
    }

    private static void tickMotes(List<UUID> active) {
        Iterator<Mote> iterator = MOTES.iterator();
        while (iterator.hasNext()) {
            Mote mote = iterator.next();
            mote.previous = mote.position;
            mote.previousAlpha = mote.alpha;
            mote.age++;
            Vec3 tip = TIPS.get(mote.owner);
            if (tip == null || !active.contains(mote.owner) || mote.age > MOTE_LIFE) mote.fading = true;
            if (tip != null) {
                Vec3 toward = tip.subtract(mote.position);
                double distance = toward.length();
                if (distance <= mote.speed) {
                    iterator.remove();
                    continue;
                }
                if (distance > mote.startDistance + MOTE_LOST_SLACK) mote.fading = true;
                mote.position = mote.position.add(toward.scale(mote.speed / distance));
                mote.speed += MOTE_ACCELERATION;
            }
            if (mote.fading) {
                mote.alpha -= 0.15f;
                if (mote.alpha <= 0.0f) iterator.remove();
            }
        }
    }

    private static void motes(VertexConsumer glow, PoseStack poseStack, Vec3 camera, float partialTick) {
        PoseStack.Pose pose = poseStack.last();
        for (Mote mote : MOTES) {
            Vec3 at = mote.previous.lerp(mote.position, partialTick);
            float alpha = Mth.lerp(partialTick, mote.previousAlpha, mote.alpha) * 0.8f;
            if (alpha <= 0.01f) continue;
            Vector3f toCamera = new Vector3f((float) (camera.x - at.x), (float) (camera.y - at.y),
                    (float) (camera.z - at.z));
            if (toCamera.lengthSquared() < 1.0E-6f) continue;
            toCamera.normalize();
            Vector3f across = new Vector3f(0.0f, 1.0f, 0.0f).cross(toCamera);
            if (across.lengthSquared() < 1.0E-6f) across.set(1.0f, 0.0f, 0.0f);
            across.normalize().mul(0.06f);
            Vector3f up = new Vector3f(toCamera).cross(across).normalize().mul(0.06f);
            float x = (float) (at.x - camera.x);
            float y = (float) (at.y - camera.y);
            float z = (float) (at.z - camera.z);
            // A soft diamond: bright centre, transparent points.
            glow.addVertex(pose, x, y, z).setColor(0.85f, 0.35f, 1.0f, alpha);
            glow.addVertex(pose, x + across.x, y + across.y, z + across.z).setColor(0.85f, 0.35f, 1.0f, 0.0f);
            glow.addVertex(pose, x + up.x, y + up.y, z + up.z).setColor(0.85f, 0.35f, 1.0f, 0.0f);
            glow.addVertex(pose, x - across.x, y - across.y, z - across.z).setColor(0.85f, 0.35f, 1.0f, 0.0f);
            glow.addVertex(pose, x, y, z).setColor(0.85f, 0.35f, 1.0f, alpha);
            glow.addVertex(pose, x - across.x, y - across.y, z - across.z).setColor(0.85f, 0.35f, 1.0f, 0.0f);
            glow.addVertex(pose, x - up.x, y - up.y, z - up.z).setColor(0.85f, 0.35f, 1.0f, 0.0f);
            glow.addVertex(pose, x + across.x, y + across.y, z + across.z).setColor(0.85f, 0.35f, 1.0f, 0.0f);
        }
    }

    /** Beams stay in their realm: a phased player's lance is as invisible to the real world as they are. */
    private static boolean channelling(Player player) {
        if (!player.isUsingItem() || !player.getUseItem().is(ModItems.DECOHERENCE_LANCE.get())) return false;
        return phased(player) == ClientPhaseState.isActive();
    }

    private static boolean phased(Player player) {
        return player == Minecraft.getInstance().player ? ClientPhaseState.isActive() : ClientPhaseState.isPhased(player);
    }

    private static LanceTargeting.Target target(Player player, float partialTick) {
        return LanceTargeting.find(player, partialTick, FluxRiftClientCache.spheres(), phased(player));
    }

    /**
     * Roughly where the lance's head sits. First person hangs it just below and beside the view so
     * the beam leaves the held item; everyone else's comes from their hand at chest height. Tuned by
     * eye against the bow-style hold at default FOV.
     */
    private static Vec3 tip(Player player, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        boolean rightHand = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                == (player.getMainArm() == HumanoidArm.RIGHT);
        double side = rightHand ? 1.0 : -1.0;
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            Vec3 eye = player.getEyePosition(partialTick);
            Vec3 down = right.cross(look).normalize();
            return eye.add(look.scale(0.55)).add(right.scale(0.46 * side)).add(down.scale(0.22));
        }
        // The lance is held up at the shoulder: its head sits above the hand and not far out in front.
        Vec3 body = player.getPosition(partialTick).add(0.0, player.getBbHeight() * 0.82, 0.0);
        return body.add(look.scale(0.6)).add(right.scale(0.36 * side));
    }

    private static void beam(VertexConsumer glow, PoseStack poseStack, Vec3 camera, Vec3 from, Vec3 to,
                             float time, LanceTargeting.Kind kind) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        if (length < 0.05) return;
        Vec3 direction = span.scale(1.0 / length);
        Vec3 bend = direction.cross(new Vec3(0.0, 1.0, 0.0));
        bend = bend.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : bend.normalize();
        Vec3 lift = bend.cross(direction).normalize();

        // Biting beams are brighter and thrash harder than one just lighting up a wall.
        boolean biting = kind == LanceTargeting.Kind.RIFT || kind == LanceTargeting.Kind.ENTITY;
        float thrash = biting ? 0.09f : 0.035f;
        float brightness = biting ? 1.0f : 0.55f;

        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float along = i / (float) SEGMENTS;
            // Pinned at both ends, loose in the middle.
            float slack = Mth.sin(along * Mth.PI);
            float wave = Mth.sin(time * 0.9f - along * 11.0f) * thrash * slack;
            float wave2 = Mth.cos(time * 0.7f - along * 7.0f) * thrash * slack;
            points[i] = from.add(span.scale(along)).add(bend.scale(wave)).add(lift.scale(wave2));
        }

        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        ribbon(glow, pose, camera, points, 0.16f, 0.55f, 0.35f, 1.0f, 0.22f * brightness);
        ribbon(glow, pose, camera, points, 0.05f, 0.50f, 0.70f, 1.0f, 0.85f * brightness);
        poseStack.popPose();
    }

    /** Camera-facing strip through {@code points}. Positions are world space, offset by the camera. */
    private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Vec3[] points,
                               float halfWidth, float red, float green, float blue, float alpha) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            Vector3f sideA = side(camera, a, b, halfWidth);
            Vector3f sideB = side(camera, b, i + 2 < points.length ? points[i + 2] : b.add(b.subtract(a)),
                    halfWidth);
            float ax = (float) (a.x - camera.x);
            float ay = (float) (a.y - camera.y);
            float az = (float) (a.z - camera.z);
            float bx = (float) (b.x - camera.x);
            float by = (float) (b.y - camera.y);
            float bz = (float) (b.z - camera.z);
            buffer.addVertex(pose, ax - sideA.x, ay - sideA.y, az - sideA.z).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, ax + sideA.x, ay + sideA.y, az + sideA.z).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, bx + sideB.x, by + sideB.y, bz + sideB.z).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, bx - sideB.x, by - sideB.y, bz - sideB.z).setColor(red, green, blue, alpha);
        }
    }

    private static Vector3f side(Vec3 camera, Vec3 at, Vec3 next, float halfWidth) {
        Vec3 along = next.subtract(at);
        Vec3 view = at.subtract(camera);
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0E-8) return new Vector3f();
        side = side.normalize().scale(halfWidth);
        return new Vector3f((float) side.x, (float) side.y, (float) side.z);
    }
}
