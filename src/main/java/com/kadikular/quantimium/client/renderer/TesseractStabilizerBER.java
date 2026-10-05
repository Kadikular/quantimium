package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

/**
 * Draws the docked link as a spinning tesseract in the stabilizer's open centre. Red when the bound
 * target cannot be reached; cyan when the link is live.
 */
public class TesseractStabilizerBER extends SubmittingBER<TesseractStabilizerBlockEntity> {

    private static final float SHELL_SCALE = 0.55f;
    private static final float INNER_SCALE = 0.22f;
    private static final float SHELL_TILT_DEGREES = 12.0f;

    public TesseractStabilizerBER(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TesseractStabilizerBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (WrenchView.isActive()) {
            PortModeIndicators.render(poseStack, buffers, be.sideConfigsView());
        }

        ItemStack link = be.getLink();
        if (!EntangledLinks.isBound(link)) return;

        float time = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + partialTick;
        TesseractShell.Tint tint = EntangledLinks.isBoundReachable(be.getLevel(), link)
                ? TesseractShell.Tint.FLUX
                : TesseractShell.Tint.RED;

        poseStack.pushPose();
        // Nudged toward the open face, so the tesseract sits just off the base whichever way the
        // Stabilizer points; only moved, never turned, so the linked block inside stays upright.
        Direction open = be.openFace();
        poseStack.translate(0.5 + 0.05 * open.getStepX(), 0.5 + 0.05 * open.getStepY(), 0.5 + 0.05 * open.getStepZ());

        Block bound = EntangledLinks.boundBlock(link);
        if (bound != null) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.6f));
            poseStack.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);
            renderInner(bound, poseStack, buffers, packedOverlay);
            poseStack.popPose();
            if (buffers instanceof MultiBufferSource.BufferSource batched) {
            }
        }

        poseStack.pushPose();
        poseStack.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.6f));
        poseStack.mulPose(Axis.XP.rotationDegrees(SHELL_TILT_DEGREES));
        TesseractShell.render(poseStack, buffers, time, tint);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderInner(Block bound, PoseStack poseStack, MultiBufferSource buffers, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = bound.defaultBlockState();
        if (state.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(-0.5, -0.5, -0.5);
            SubmitBuffers.block(buffers, poseStack, state, LightCoordsUtil.FULL_BRIGHT, overlay);
            poseStack.popPose();
            return;
        }
        SubmitBuffers.item(buffers, poseStack, new ItemStack(bound), ItemDisplayContext.NONE, LightCoordsUtil.FULL_BRIGHT, overlay, minecraft.level, 0);
    }
}
