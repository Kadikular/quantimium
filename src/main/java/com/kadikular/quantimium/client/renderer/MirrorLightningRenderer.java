package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.init.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-only Stranger-Things bolt. Geometry lives for a handful of ticks; nothing is spawned as an
 * entity, so multiplayer stays quiet. Several bolts can overlap (portal open plus close, or weather).
 */
public final class MirrorLightningRenderer {

    private static final List<Bolt> BOLTS = new ArrayList<>();

    private MirrorLightningRenderer() {}

    public static void strike(Level level, Vec3 near, RandomSource random) {
        double x = near.x + (random.nextDouble() - 0.5) * 28.0;
        double z = near.z + (random.nextDouble() - 0.5) * 28.0;
        strikeAt(level, new Vec3(x, near.y, z), random);
    }

    /** Bolt and thunder at a world point — used when a tear opens or closes, including in the overworld. */
    public static void strikeAt(Level level, Vec3 impact, RandomSource random) {
        double x = impact.x;
        double z = impact.z;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        if (Math.abs(ground - impact.y) > 8) ground = Mth.floor(impact.y);
        if (!level.isLoaded(BlockPos.containing(x, ground, z))) return;

        double originY = ground + 18.0 + random.nextDouble() * 8.0;
        int points = 9;
        float[] segments = new float[points * 3];
        float endY = ground + 0.05f;
        for (int i = 0; i < points; i++) {
            float t = i / (float) (points - 1);
            float jitter = (i == 0 || i == points - 1) ? 0.0f : (random.nextFloat() - 0.5f) * 1.8f;
            segments[i * 3] = jitter;
            segments[i * 3 + 1] = Mth.lerp(t, (float) originY, endY) - (float) originY;
            segments[i * 3 + 2] = (i == 0 || i == points - 1) ? 0.0f
                    : (random.nextFloat() - 0.5f) * 1.8f;
        }
        BOLTS.add(new Bolt(segments, 6 + random.nextInt(4), x, originY, z));

        level.playLocalSound(x, ground + 1.0, z, ModSounds.MIRROR_THUNDER.get(),
                SoundSource.WEATHER, 0.7f, 1.0f, false);
        level.playLocalSound(x, ground, z, ModSounds.MIRROR_LIGHTNING_IMPACT.get(),
                SoundSource.WEATHER, 0.4f, 1.0f, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        if (BOLTS.isEmpty()) return;
        VertexConsumer buffer = buffers.getBuffer(QuantumRenderTypes.HOLO_EDGE);
        for (Bolt bolt : BOLTS) {
            float fade = Mth.clamp((bolt.life - partialTick) / 6.0f, 0.0f, 1.0f);
            poseStack.pushPose();
            poseStack.translate(bolt.originX - camera.x, bolt.originY - camera.y, bolt.originZ - camera.z);
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            for (int i = 0; i < bolt.segments.length / 3 - 1; i++) {
                float ax = bolt.segments[i * 3];
                float ay = bolt.segments[i * 3 + 1];
                float az = bolt.segments[i * 3 + 2];
                float bx = bolt.segments[(i + 1) * 3];
                float by = bolt.segments[(i + 1) * 3 + 1];
                float bz = bolt.segments[(i + 1) * 3 + 2];
                float dx = bx - ax;
                float dy = by - ay;
                float dz = bz - az;
                float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1.0E-4f) continue;
                dx /= length;
                dy /= length;
                dz /= length;
                buffer.addVertex(matrix, ax, ay, az)
                        .setColor(0.78f, 0.55f, 1.0f, 0.55f + 0.4f * fade)
                        .setNormal(pose, dx, dy, dz);
                buffer.addVertex(matrix, bx, by, bz)
                        .setColor(0.92f, 0.82f, 1.0f, 0.35f + 0.5f * fade)
                        .setNormal(pose, dx, dy, dz);
            }
            poseStack.popPose();
        }
    }

    /**
     * A short jagged arc between two points — a flux rift lashing out. Sideways kinks scale with
     * length so a two-block arc crackles rather than zigzagging wildly.
     */
    public static void arc(Level level, Vec3 from, Vec3 to, RandomSource random) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        if (length < 0.1) return;
        int points = Math.max(4, Mth.ceil(length * 1.3));
        float kink = (float) Math.min(0.45, 0.12 + length * 0.04);
        float[] segments = new float[points * 3];
        for (int i = 0; i < points; i++) {
            float t = i / (float) (points - 1);
            boolean end = i == 0 || i == points - 1;
            segments[i * 3] = (float) (span.x * t) + (end ? 0.0f : (random.nextFloat() - 0.5f) * 2.0f * kink);
            segments[i * 3 + 1] = (float) (span.y * t) + (end ? 0.0f : (random.nextFloat() - 0.5f) * 2.0f * kink);
            segments[i * 3 + 2] = (float) (span.z * t) + (end ? 0.0f : (random.nextFloat() - 0.5f) * 2.0f * kink);
        }
        BOLTS.add(new Bolt(segments, 4 + random.nextInt(3), from.x, from.y, from.z));
        level.playLocalSound(to.x, to.y, to.z, ModSounds.RIFT_ZAP.get(), SoundSource.HOSTILE, 0.8f,
                0.9f + random.nextFloat() * 0.3f, false);
    }

    public static void tick() {
        Iterator<Bolt> iterator = BOLTS.iterator();
        while (iterator.hasNext()) {
            Bolt bolt = iterator.next();
            bolt.life--;
            if (bolt.life <= 0) iterator.remove();
        }
    }

    private static final class Bolt {
        private final float[] segments;
        private int life;
        private final double originX;
        private final double originY;
        private final double originZ;

        private Bolt(float[] segments, int life, double originX, double originY, double originZ) {
            this.segments = segments;
            this.life = life;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }
    }
}
