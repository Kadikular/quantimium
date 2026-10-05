package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.client.model.VeiledModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The Veiled's face: where a face should be, the hood opens onto a rift. End-portal void fills the
 * opening, a rift-edge outline twitches around it, and inside a small tesseract folds around one
 * point of light — its gaze. Drawn over the painted face, which stays underneath as the rim.
 *
 * <p>Works in the hood's own space: callers pose the stack at the model root, and this applies the
 * root and hood transforms itself, so the face turns and tilts with the hood.
 */
public final class VeiledFace {

    /** The opening on the hood's front, in model pixels: the painted ring sits just outside it. */
    private static final float LEFT = -3.0f / 16.0f;
    private static final float RIGHT = 3.0f / 16.0f;
    private static final float TOP = -9.0f / 16.0f;
    private static final float BOTTOM = -2.0f / 16.0f;
    /** Just proud of the hood's front face (z = -5 px), so the void is not buried in it. */
    private static final float FRONT = -5.03f / 16.0f;
    private static final float CENTRE_Y = (TOP + BOTTOM) * 0.5f;
    private static final TesseractShell.Tint CORE = new TesseractShell.Tint(0.66f, 0.53f, 1.0f);

    private VeiledFace() {}

    /**
     * @param opacity 1 whole; the void cannot fade, so below half it is left out and only the
     *                outline, tesseract and gaze remain, thinning with it.
     */
    public static void render(VeiledModel model, PoseStack poses, MultiBufferSource buffers, float time,
                              float opacity) {
        if (opacity <= 0.02f) return;
        poses.pushPose();
        model.root().translateAndRotate(poses);
        model.hood().translateAndRotate(poses);
        PoseStack.Pose pose = poses.last();

        if (opacity > 0.5f) {
            VertexConsumer voidBuffer = buffers.getBuffer(RenderTypes.endPortal());
            quad(voidBuffer, pose, LEFT, TOP, RIGHT, BOTTOM, FRONT);
        }

        VertexConsumer lines = buffers.getBuffer(QuantumRenderTypes.fieldEdge(QuantumRenderTypes.cameraDistance(poses)));
        outline(lines, pose, 0.0f, 0.0f, 0.95f * opacity);
        int beat = Mth.floor(time * 0.5f);
        for (int copy = 0; copy < 2; copy++) {
            Random random = new Random(beat * 7919L + copy * 977L);
            float reach = (0.5f + random.nextFloat()) / 16.0f;
            outline(lines, pose, reach, (copy + 1) * 0.002f, 0.45f * opacity);
        }

        poses.pushPose();
        poses.translate(0.0f, CENTRE_Y, FRONT - 0.03f);
        poses.scale(0.32f, 0.32f, 0.32f);
        RenderType edges = TesseractShell.edgeTypeFor(poses);
        TesseractShell.render(poses, buffers, time * 1.6f, edges, CORE, 1.6f * opacity);
        poses.popPose();

        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        gaze(glow, pose, 0.9f * opacity);
        poses.popPose();
    }

    /** Double-sided, since the model space is mirrored and the viewer may be either side of it. */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z) {
        buffer.addVertex(pose, x0, y0, z);
        buffer.addVertex(pose, x1, y0, z);
        buffer.addVertex(pose, x1, y1, z);
        buffer.addVertex(pose, x0, y1, z);
        buffer.addVertex(pose, x0, y0, z);
        buffer.addVertex(pose, x0, y1, z);
        buffer.addVertex(pose, x1, y1, z);
        buffer.addVertex(pose, x1, y0, z);
    }

    /** The opening's rim in rift edge; a non-zero {@code reach} pushes a ghost copy outwards. */
    private static void outline(VertexConsumer lines, PoseStack.Pose pose, float reach, float lift, float alpha) {
        float l = LEFT - reach;
        float r = RIGHT + reach;
        float t = TOP - reach;
        float b = BOTTOM + reach;
        float z = FRONT - 0.004f - lift;
        FieldOrb.segment(lines, pose, l, t, z, r, t, z, 0.70f, 0.42f, 1.0f, alpha);
        FieldOrb.segment(lines, pose, r, t, z, r, b, z, 0.70f, 0.42f, 1.0f, alpha);
        FieldOrb.segment(lines, pose, r, b, z, l, b, z, 0.70f, 0.42f, 1.0f, alpha);
        FieldOrb.segment(lines, pose, l, b, z, l, t, z, 0.70f, 0.42f, 1.0f, alpha);
    }

    /** One soft point of light in the middle of the tesseract. */
    private static void gaze(VertexConsumer glow, PoseStack.Pose pose, float alpha) {
        float z = FRONT - 0.06f;
        float radius = 0.035f;
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            glow.addVertex(pose, 0.0f, CENTRE_Y, z).setColor(0.82f, 0.73f, 0.99f, alpha);
            glow.addVertex(pose, Mth.cos(a0) * radius, CENTRE_Y + Mth.sin(a0) * radius, z).setColor(0.82f, 0.73f, 0.99f, 0.0f);
            glow.addVertex(pose, Mth.cos(a1) * radius, CENTRE_Y + Mth.sin(a1) * radius, z).setColor(0.82f, 0.73f, 0.99f, 0.0f);
            glow.addVertex(pose, 0.0f, CENTRE_Y, z).setColor(0.82f, 0.73f, 0.99f, alpha);
        }
    }
}
