package com.kadikular.quantimium.client.renderer;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.kadikular.quantimium.block.StabilisedPortalBlock;
import com.kadikular.quantimium.block.entity.StabilisedPortalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Two inward-facing void skins around a shallow pocket, same shader as the rifts.
 * Back-face culling shows the far skin from outside, so the gate reads as depth; walking
 * through puts the camera between them instead of on a single plane.
 */
public final class StabilisedPortalBER extends SubmittingBER<StabilisedPortalBlockEntity> {

    /** Wider than the 4-pixel trigger so a swap happens in the pocket, not on a face. */
    // private static final float SKIN_MIN = 8.0f / 16.0f;
    // private static final float SKIN_MAX = 8.0f / 16.0f;
    private static final float SKIN_MIN = 4.0f / 16.0f;
    private static final float SKIN_MAX = 12.0f / 16.0f;

    public StabilisedPortalBER(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StabilisedPortalBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(StabilisedPortalBlock.AXIS)) return;
        VertexConsumer buffer = buffers.getBuffer(RenderTypes.endPortal());
        Matrix4f pose = poseStack.last().pose();
        if (state.getValue(StabilisedPortalBlock.AXIS) == Direction.Axis.X) {
            quad(buffer, pose, 0.0f, 0.0f, SKIN_MIN, 1.0f, 0.0f, SKIN_MIN, 1.0f, 1.0f, SKIN_MIN, 0.0f, 1.0f, SKIN_MIN);
            quad(buffer, pose, 0.0f, 0.0f, SKIN_MAX, 0.0f, 1.0f, SKIN_MAX, 1.0f, 1.0f, SKIN_MAX, 1.0f, 0.0f, SKIN_MAX);
        } else {
            quad(buffer, pose, SKIN_MIN, 0.0f, 0.0f, SKIN_MIN, 0.0f, 1.0f, SKIN_MIN, 1.0f, 1.0f, SKIN_MIN, 1.0f, 0.0f);
            quad(buffer, pose, SKIN_MAX, 0.0f, 0.0f, SKIN_MAX, 1.0f, 0.0f, SKIN_MAX, 1.0f, 1.0f, SKIN_MAX, 0.0f, 1.0f);
        }
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
