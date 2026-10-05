package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * A ball of contained field: nested rings turning on their own axes, a soft core, and a few motes
 * on wider orbits. Each ring carries a bright arc that chases around it, which is what sells the
 * thing as a swirling volume rather than a spinning wireframe.
 */
public final class FieldOrb {

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int RING_SEGMENTS = 22;
    private static final int RINGS = 3;
    private static final int MOTES = 5;

    private FieldOrb() {
    }

    /**
     * @param intensity scales opacity only, so an idle cell dims without changing size
     */
    /** Draw only the line pass. Call {@link #renderCore} after all line geometry is complete. */
    public static void renderLines(PoseStack poses, VertexConsumer lines,
                                   float time, TesseractShell.Tint tint, float radius,
                                   float intensity) {
        for (int ring = 0; ring < RINGS; ring++) {
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(time * (0.7f + ring * 0.31f) + ring * 47.0f));
            poses.mulPose(Axis.XP.rotationDegrees(26.0f + ring * 54.0f
                    + Mth.sin(time * 0.016f + ring) * 14.0f));
            ring(lines, poses.last(), radius * (1.0f - ring * 0.14f),
                    time * (0.10f + 0.035f * ring), tint, 0.60f * intensity);
            poses.popPose();
        }

        motes(lines, poses.last(), time, radius, tint, intensity);
    }

    /** Draw the translucent core in a separate pass, after the caller has finished drawing lines. */
    public static void renderCore(PoseStack poses, VertexConsumer panes, TesseractShell.Tint tint,
                                  float radius, float intensity) {
        core(panes, poses.last(), radius * 0.46f, tint, 0.16f * intensity);
        core(panes, poses.last(), radius * 0.24f, tint, 0.34f * intensity);
    }

    private static void ring(VertexConsumer lines, PoseStack.Pose pose, float radius, float phase,
                             TesseractShell.Tint tint, float alpha) {
        float fromX = radius;
        float fromZ = 0.0f;
        for (int step = 1; step <= RING_SEGMENTS; step++) {
            float angle = step * TWO_PI / RING_SEGMENTS;
            float toX = radius * Mth.cos(angle);
            float toZ = radius * Mth.sin(angle);
            float chase = chase(angle - TWO_PI / RING_SEGMENTS * 0.5f - phase);
            float glow = 0.55f + 0.45f * chase;
            segment(lines, pose, fromX, 0.0f, fromZ, toX, 0.0f, toZ,
                    tint.r() * glow, tint.g() * glow, tint.b() * glow,
                    alpha * (0.22f + 0.78f * chase));
            fromX = toX;
            fromZ = toZ;
        }
    }

    /** One bright arc per revolution, tightened by the cube so most of the ring stays faint. */
    private static float chase(float angle) {
        float wave = 0.5f + 0.5f * Mth.sin(angle);
        return wave * wave * wave;
    }

    /** Three crossed panes: from any angle at least one faces the camera, so the middle glows. */
    private static void core(VertexConsumer panes, PoseStack.Pose pose, float half,
                             TesseractShell.Tint tint, float alpha) {
        quad(panes, pose, tint, alpha,
                -half, -half, 0.0f, half, -half, 0.0f, half, half, 0.0f, -half, half, 0.0f);
        quad(panes, pose, tint, alpha,
                -half, 0.0f, -half, half, 0.0f, -half, half, 0.0f, half, -half, 0.0f, half);
        quad(panes, pose, tint, alpha,
                0.0f, -half, -half, 0.0f, half, -half, 0.0f, half, half, 0.0f, -half, half);
    }

    private static void motes(VertexConsumer lines, PoseStack.Pose pose, float time, float radius,
                              TesseractShell.Tint tint, float intensity) {
        for (int mote = 0; mote < MOTES; mote++) {
            float orbit = radius * (0.72f + 0.26f * Mth.sin(time * 0.021f + mote * 1.7f));
            float head = time * (0.06f + 0.012f * mote) + mote * 2.1f;
            float lift = orbit * 0.55f * Mth.sin(time * 0.028f + mote * 2.4f);
            float tail = head - 0.30f;
            segment(lines, pose,
                    orbit * Mth.cos(tail), lift, orbit * Mth.sin(tail),
                    orbit * Mth.cos(head), lift, orbit * Mth.sin(head),
                    Math.min(1.0f, tint.r() + 0.35f), Math.min(1.0f, tint.g() + 0.35f),
                    Math.min(1.0f, tint.b() + 0.35f), 0.85f * intensity);
        }
    }

    /** Shared by the foundry's rails, so both use the same line format and normal convention. */
    public static void segment(VertexConsumer lines, PoseStack.Pose pose,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               float r, float g, float b, float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-5f) return;
        dx /= length;
        dy /= length;
        dz /= length;
        lines.addVertex(pose, x0, y0, z0).setColor(r, g, b, alpha).setNormal(pose, dx, dy, dz);
        lines.addVertex(pose, x1, y1, z1).setColor(r, g, b, alpha).setNormal(pose, dx, dy, dz);
    }

    private static void quad(VertexConsumer panes, PoseStack.Pose pose, TesseractShell.Tint tint,
                             float alpha, float... corners) {
        for (int corner = 0; corner < 4; corner++) {
            panes.addVertex(pose, corners[corner * 3], corners[corner * 3 + 1], corners[corner * 3 + 2])
                    .setColor(tint.r(), tint.g(), tint.b(), alpha);
        }
    }
}
