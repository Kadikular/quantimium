package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * A lit bar across the bottom of one face, marking which way a machine is turned.
 *
 * <p>The chassis is symmetrical, so without this the side configuration screen would be naming faces
 * the player cannot tell apart. Drawn just inside the void window rather than on the outside of the
 * block, so it reads as part of the machine's interior instead of a decal.
 */
public final class FrontMarker {

    private static final float HALF_WIDTH = 0.10f;
    private static final float HEIGHT = 0.035f;

    private FrontMarker() {}

    /**
     * @param depth  how far in from the facing side the bar sits, in block units; pick a plane the
     *               chassis leaves clear so the bar neither hides inside it nor fights it for depth
     * @param bottom height of the bar's lower edge, in block units
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Direction facing,
                              float depth, float bottom) {
        if (facing.getAxis().isVertical()) return;

        VertexConsumer buffer = bufferSource.getBuffer(QuantumRenderTypes.HOLO_PANE);
        Matrix4f pose = poseStack.last().pose();

        boolean alongX = facing.getAxis() == Direction.Axis.X;
        int step = alongX ? facing.getStepX() : facing.getStepZ();
        float plane = step > 0 ? 1.0f - depth : depth;

        float near = 0.5f - HALF_WIDTH;
        float far = 0.5f + HALF_WIDTH;
        float top = bottom + HEIGHT;

        if (alongX) {
            quad(buffer, pose, plane, bottom, near, plane, bottom, far, plane, top, far, plane, top, near);
        } else {
            quad(buffer, pose, near, bottom, plane, far, bottom, plane, far, top, plane, near, top, plane);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(buffer, pose, x0, y0, z0);
        vertex(buffer, pose, x1, y1, z1);
        vertex(buffer, pose, x2, y2, z2);
        vertex(buffer, pose, x3, y3, z3);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z) {
        buffer.addVertex(pose, x, y, z).setColor(0.31f, 0.86f, 1.0f, 0.85f);
    }
}
