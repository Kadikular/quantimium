package com.kadikular.quantimium.client.renderer;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.kadikular.quantimium.phase.MirrorRift;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Ragged marquise of end-portal void. Callers supply a 0–1 width so the same tear can open,
 * hold, and close — used today for the return rift, later for anomaly-spawned entry rifts.
 */
public final class MirrorRiftRenderer {

    private static final int BANDS = 11;
    private static final float BOTTOM = 0.10f;
    private static final float TOP = 2.25f;
    private static final float HEIGHT = TOP - BOTTOM;
    private static final float HALF_WIDTH = 0.62f;
    private static final float OVERHEAD_SQUASH = 0.5f;
    /** Below this sine of elevation (about 25°) the tear keeps its full height. */
    private static final float SQUASH_DEAD_ZONE = 0.42f;
    private static final float RAGGED = 0.20f;
    private static final float TAPER_CURVE = 0.75f;

    private MirrorRiftRenderer() {}

    public static void render(MirrorRift rift, float width, PoseStack poseStack, MultiBufferSource buffers,
                              Vec3 camera, Vec3 viewerMiddle, float time) {
        if (rift == null) return;
        render(rift.centre(), rift.shapeSeed(), width, 1.0f, 0.88f, 0.28f, 1.0f, poseStack, buffers, camera,
                viewerMiddle, time);
    }

    /**
     * The same ragged void at any size and outline tint. Flux rifts are drawn through here as a
     * scaled-up tear, so the two read as one family: the door and the wound it came from.
     */
    public static void render(Vec3 centre, int shapeSeed, float width, float scale,
                              float red, float green, float blue, PoseStack poseStack, MultiBufferSource buffers,
                              Vec3 camera, Vec3 viewerMiddle, float time) {
        render(centre, shapeSeed, width, scale, red, green, blue, null, 0.0f, poseStack, buffers, camera,
                viewerMiddle, time);
    }

    /**
     * A translucent wash laid over the void, heavier at the rim than the middle so the void still
     * shows through. It is what stops a flux rift reading as just a big portal.
     */
    public record Tint(float red, float green, float blue, float rimAlpha, float coreAlpha) {}

    /**
     * @param jitter 0 for a clean tear; above that, extra outlines that twitch around the real one,
     *               more of them and further out as it rises. A destabilised edge, not a second shape.
     */
    public static void render(Vec3 centre, int shapeSeed, float width, float scale,
                              float red, float green, float blue, @Nullable Tint tint, float jitter,
                              PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, Vec3 viewerMiddle,
                              float time) {
        if (width <= 0.01f) return;

        Vector3f forward = new Vector3f((float) (camera.x - centre.x), (float) (camera.y - centre.y),
                (float) (camera.z - centre.z));
        if (forward.lengthSquared() < 1.0E-6f) forward.set(0.0f, 0.0f, 1.0f);
        forward.normalize();

        Vector3f across = new Vector3f(0.0f, 1.0f, 0.0f).cross(forward);
        if (across.lengthSquared() < 1.0E-6f) across.set(1.0f, 0.0f, 0.0f);
        across.normalize();
        Vector3f up = new Vector3f(forward).cross(across).normalize();

        float[] left = new float[BANDS];
        float[] right = new float[BANDS];
        silhouette(shapeSeed, time, width, left, right);

        poseStack.pushPose();
        poseStack.translate(centre.x - camera.x, centre.y - camera.y, centre.z - camera.z);
        poseStack.scale(scale, scale, scale);
        PoseStack.Pose pose = poseStack.last();
        float squash = Mth.lerp(elevation(viewerMiddle, centre, HEIGHT * 0.5f * scale), 1.0f, OVERHEAD_SQUASH);

        // Void, then the translucent wash over it, then every line.
        drawVoid(buffers.getBuffer(RenderTypes.endPortal()), pose, across, up, left, right, squash);
        if (tint != null) {
            drawTint(buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW), pose, across, up, forward,
                    left, right, squash, tint);
        }
        RenderType edges = QuantumRenderTypes.holoEdge(centre.distanceTo(camera) / scale);
        VertexConsumer lines = buffers.getBuffer(edges);
        drawOutline(lines, pose, across, up, forward, left, right, squash, red, green, blue, 0.92f, 0.0f,
                0.0f, 0.0f);
        if (jitter > 0.0f) drawJitter(lines, pose, across, up, forward, left, right, squash,
                red, green, blue, shapeSeed, jitter, time);
        poseStack.popPose();
    }

    private static void drawTint(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across, Vector3f up,
                                 Vector3f forward, float[] left, float[] right, float squash, Tint tint) {
        float lift = 0.015f;
        for (int band = 0; band < BANDS - 1; band++) {
            float lowY = bandHeight(band, squash);
            float highY = bandHeight(band + 1, squash);
            float lowMid = (left[band] + right[band]) * 0.5f;
            float highMid = (left[band + 1] + right[band + 1]) * 0.5f;
            tintQuad(buffer, pose, across, up, forward, lift, tint,
                    left[band], lowY, lowMid, left[band + 1], highY, highMid);
            tintQuad(buffer, pose, across, up, forward, lift, tint,
                    right[band], lowY, lowMid, right[band + 1], highY, highMid);
        }
    }

    /** Rim edge to the midline across one band; alpha fades from rim to middle. */
    private static void tintQuad(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across, Vector3f up,
                                 Vector3f forward, float lift, Tint tint, float lowRim, float lowY,
                                 float lowMid, float highRim, float highY, float highMid) {
        tintVertex(buffer, pose, across, up, forward, lift, lowRim, lowY, tint, tint.rimAlpha());
        tintVertex(buffer, pose, across, up, forward, lift, lowMid, lowY, tint, tint.coreAlpha());
        tintVertex(buffer, pose, across, up, forward, lift, highMid, highY, tint, tint.coreAlpha());
        tintVertex(buffer, pose, across, up, forward, lift, highRim, highY, tint, tint.rimAlpha());
    }

    private static void tintVertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across, Vector3f up,
                                   Vector3f forward, float lift, float sideways, float upwards,
                                   Tint tint, float alpha) {
        buffer.addVertex(pose,
                across.x * sideways + up.x * upwards + forward.x * lift,
                across.y * sideways + up.y * upwards + forward.y * lift,
                across.z * sideways + up.z * upwards + forward.z * lift)
                .setColor(tint.red(), tint.green(), tint.blue(), alpha);
    }

    /**
     * Ghost outlines re-rolled every other tick, so the edge twitches rather than drifts. Pushed
     * outwards only: a jitter that cut into the void would read as the shape changing.
     */
    private static void drawJitter(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across,
                                   Vector3f up, Vector3f forward, float[] left, float[] right, float squash,
                                   float red, float green, float blue, int seed, float jitter, float time) {
        int copies = 1 + Math.min(4, Math.round(jitter * 2.0f));
        float reach = 0.04f + 0.10f * jitter;
        int beat = Mth.floor(time * 0.5f);
        float[] jitterLeft = new float[BANDS];
        float[] jitterRight = new float[BANDS];
        for (int copy = 0; copy < copies; copy++) {
            Random random = new Random(seed * 31L + copy * 977L + beat * 7919L);
            for (int band = 0; band < BANDS; band++) {
                jitterLeft[band] = left[band] - random.nextFloat() * reach;
                jitterRight[band] = right[band] + random.nextFloat() * reach;
            }
            // Both sides meet again at a point just past each tip, so every ghost is a closed shape
            // rather than two loose strands.
            int last = BANDS - 1;
            jitterLeft[0] = jitterRight[0] = 0.0f;
            jitterLeft[last] = jitterRight[last] = 0.0f;
            float bottomReach = random.nextFloat() * reach * 1.5f;
            float topReach = random.nextFloat() * reach * 1.5f;
            float alpha = 0.55f - 0.08f * copy;
            drawOutline(buffer, pose, across, up, forward, jitterLeft, jitterRight, squash,
                    Mth.lerp(0.35f, red, 1.0f), Mth.lerp(0.35f, green, 1.0f), Mth.lerp(0.35f, blue, 1.0f),
                    alpha, 0.01f * (copy + 1), bottomReach, topReach);
        }
    }

    /**
     * How far overhead / underfoot the viewer is, 0–1, for flattening the billboard seen from
     * steeply above or below. Measured from the nearest point of the tear's height rather than its
     * middle, and with a dead zone: a tall rift is naturally looked up at from close by, and
     * squashing it for that made it shrink as you approached.
     */
    private static float elevation(Vec3 viewer, Vec3 centre, float halfHeight) {
        double rise = Math.max(0.0, Math.abs(viewer.y - centre.y) - halfHeight * 0.8);
        double runX = viewer.x - centre.x;
        double runZ = viewer.z - centre.z;
        double reach = Math.sqrt(rise * rise + runX * runX + runZ * runZ);
        if (reach < 1.0E-4) return 0.0f;
        float sine = (float) (rise / reach);
        return Mth.clamp((sine - SQUASH_DEAD_ZONE) / (1.0f - SQUASH_DEAD_ZONE), 0.0f, 1.0f);
    }

    private static void silhouette(int seed, float time, float width, float[] left, float[] right) {
        Random random = new Random(seed);
        for (int band = 0; band < BANDS; band++) {
            float along = band / (float) (BANDS - 1);
            float profile = (float) Math.pow(Mth.sin(Mth.PI * along), TAPER_CURVE) * width;
            float leftBite = random.nextFloat() * RAGGED;
            float rightBite = random.nextFloat() * RAGGED;
            float leftWobble = Mth.sin(time * 0.07f + band * 1.7f) * 0.045f;
            float rightWobble = Mth.sin(time * 0.06f + band * 2.3f + 1.1f) * 0.045f;
            left[band] = (-HALF_WIDTH + leftBite + leftWobble) * profile;
            right[band] = (HALF_WIDTH - rightBite + rightWobble) * profile;
        }
    }

    private static float bandHeight(int band, float squash) {
        return (-HEIGHT * 0.5f + band / (float) (BANDS - 1) * HEIGHT) * squash;
    }

    private static void drawVoid(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across,
                                 Vector3f up, float[] left, float[] right, float squash) {
        for (int band = 0; band < BANDS - 1; band++) {
            float lowY = bandHeight(band, squash);
            float highY = bandHeight(band + 1, squash);
            voidQuad(buffer, pose, across, up, left[band], right[band], lowY,
                    left[band + 1], right[band + 1], highY, false);
            voidQuad(buffer, pose, across, up, left[band], right[band], lowY,
                    left[band + 1], right[band + 1], highY, true);
        }
    }

    private static void voidQuad(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across,
                                 Vector3f up, float lowLeft, float lowRight, float lowY,
                                 float highLeft, float highRight, float highY, boolean flipped) {
        if (flipped) {
            voidVertex(buffer, pose, across, up, lowLeft, lowY);
            voidVertex(buffer, pose, across, up, highLeft, highY);
            voidVertex(buffer, pose, across, up, highRight, highY);
            voidVertex(buffer, pose, across, up, lowRight, lowY);
            return;
        }
        voidVertex(buffer, pose, across, up, lowLeft, lowY);
        voidVertex(buffer, pose, across, up, lowRight, lowY);
        voidVertex(buffer, pose, across, up, highRight, highY);
        voidVertex(buffer, pose, across, up, highLeft, highY);
    }

    private static void voidVertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across,
                                   Vector3f up, float sideways, float upwards) {
        buffer.addVertex(pose,
                across.x * sideways + up.x * upwards,
                across.y * sideways + up.y * upwards,
                across.z * sideways + up.z * upwards);
    }

    private static void drawOutline(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across,
                                    Vector3f up, Vector3f forward, float[] left, float[] right,
                                    float squash, float red, float green, float blue, float alpha,
                                    float extraLift, float bottomReach, float topReach) {
        for (int band = 0; band < BANDS - 1; band++) {
            float lowY = bandHeight(band, squash) - (band == 0 ? bottomReach : 0.0f);
            float highY = bandHeight(band + 1, squash) + (band + 1 == BANDS - 1 ? topReach : 0.0f);
            line(buffer, pose, across, up, forward, left[band], lowY, left[band + 1], highY,
                    red, green, blue, alpha, extraLift);
            line(buffer, pose, across, up, forward, right[band], lowY, right[band + 1], highY,
                    red, green, blue, alpha, extraLift);
        }
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across, Vector3f up,
                             Vector3f forward, float fromSideways, float fromUp,
                             float toSideways, float toUp, float red, float green, float blue,
                             float alpha, float extraLift) {
        float lift = 0.02f + extraLift;
        float fromX = across.x * fromSideways + up.x * fromUp + forward.x * lift;
        float fromY = across.y * fromSideways + up.y * fromUp + forward.y * lift;
        float fromZ = across.z * fromSideways + up.z * fromUp + forward.z * lift;
        float toX = across.x * toSideways + up.x * toUp + forward.x * lift;
        float toY = across.y * toSideways + up.y * toUp + forward.y * lift;
        float toZ = across.z * toSideways + up.z * toUp + forward.z * lift;

        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-5f) return;
        dx /= length;
        dy /= length;
        dz /= length;

        buffer.addVertex(pose, fromX, fromY, fromZ).setColor(red, green, blue, alpha)
                .setNormal(pose, dx, dy, dz);
        buffer.addVertex(pose, toX, toY, toZ).setColor(red, green, blue, alpha)
                .setNormal(pose, dx, dy, dz);
    }
}
