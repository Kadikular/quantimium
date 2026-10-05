// Path: src/main/java/com/kadikular/quantimium/client/renderer/VoidWindow.java
package com.kadikular.quantimium.client.renderer;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

/**
 * A hollow room of end-portal void, drawn inside a block so that looking through the shell around it
 * is like looking into somewhere the world does not reach.
 *
 * <p>Every wall faces inwards, so back-face culling drops whichever ones lie between the camera and
 * the room. What survives is always the far side of the box: the void reads as depth rather than as a
 * painted cube, and anything floating in the middle is seen against it.
 */
public final class VoidWindow {

    private VoidWindow() {}

    /** Draws the interior of the box spanning the given block-local corners. */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.endPortal());
        Matrix4f pose = poseStack.last().pose();

        quad(buffer, pose,
                minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ);
        quad(buffer, pose,
                maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        quad(buffer, pose,
                minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        quad(buffer, pose,
                minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        quad(buffer, pose,
                minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ);
        quad(buffer, pose,
                minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        buffer.addVertex(pose, x0, y0, z0);
        buffer.addVertex(pose, x1, y1, z1);
        buffer.addVertex(pose, x2, y2, z2);
        buffer.addVertex(pose, x3, y3, z3);
    }
}
