package com.kadikular.quantimium.compat.ae2.client;

import com.kadikular.quantimium.client.renderer.SubmitBuffers;
import com.kadikular.quantimium.client.renderer.SubmittingBER;
import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.client.renderer.TesseractShell;
import com.kadikular.quantimium.client.renderer.VoidWindow;
import com.kadikular.quantimium.compat.ae2.SuperpositionCrafterBlock;
import com.kadikular.quantimium.compat.ae2.SuperpositionCrafterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

/**
 * The Quantum Crafter's room of void and folding tesseract, in the open frame, with the catalyst
 * turning inside the cell. The cell shows the link: a bare red wireframe offline, the azure cell on a
 * network, and a brighter, faster one while crafting.
 */
public class SuperpositionCrafterBER extends SubmittingBER<SuperpositionCrafterBlockEntity> {

    /** The void room, between the corner posts, plinth and cap of the frame model. */
    private static final float WALL_MIN = 0.1877f;
    private static final float WALL_MAX = 0.8123f;
    /** As the Quantum Crafter: the catalyst's corners stay inside the cell as both turn. */
    private static final float CATALYST_SCALE = 0.26f;
    private static final float SHELL_SCALE = 0.57f;
    private static final float SHELL_TILT_DEGREES = 12.0f;
    private static final float ACTIVE_ALPHA = 1.5f;

    public SuperpositionCrafterBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SuperpositionCrafterBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        VoidWindow.render(poseStack, bufferSource, WALL_MIN, WALL_MIN, WALL_MIN, WALL_MAX, WALL_MAX, WALL_MAX);

        float time = (blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0) + partialTick;
        BlockState state = blockEntity.getBlockState();
        SuperpositionCrafterBlock.Link link = state.hasProperty(SuperpositionCrafterBlock.LINK)
                ? state.getValue(SuperpositionCrafterBlock.LINK) : SuperpositionCrafterBlock.Link.OFFLINE;

        poseStack.pushPose();
        // The catalyst and the cell share this pivot, so both ride the same floating bob.
        poseStack.translate(0.5, 0.5 + Math.sin(time * 0.08) * 0.025, 0.5);

        ItemStack catalyst = blockEntity.getCatalyst().getStackInSlot(0);
        if (!catalyst.isEmpty()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.8f));
            poseStack.scale(CATALYST_SCALE, CATALYST_SCALE, CATALYST_SCALE);
            renderCatalyst(blockEntity, catalyst, poseStack, bufferSource, packedOverlay);
            poseStack.popPose();
            // Block and item models are batched until the block entity pass ends, which would put
            // the catalyst on top of the cell that is meant to be in front of it.
        }

        poseStack.pushPose();
        poseStack.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.6f));
        poseStack.mulPose(Axis.XP.rotationDegrees(SHELL_TILT_DEGREES));
        switch (link) {
            case OFFLINE -> TesseractShell.renderSemiStable(poseStack, bufferSource, time,
                    TesseractShell.edgeTypeFor(poseStack), TesseractShell.Tint.RED);
            case ONLINE -> TesseractShell.render(poseStack, bufferSource, time);
            case ACTIVE -> TesseractShell.render(poseStack, bufferSource, time * 2,
                    TesseractShell.edgeTypeFor(poseStack), TesseractShell.Tint.FLUX, ACTIVE_ALPHA);
        }
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderCatalyst(SuperpositionCrafterBlockEntity blockEntity, ItemStack catalyst,
                                       PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        if (catalyst.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().getRenderShape() == RenderShape.MODEL) {
            poseStack.translate(-0.5, -0.5, -0.5);
            SubmitBuffers.block(bufferSource, poseStack, blockItem.getBlock().defaultBlockState(), LightCoordsUtil.FULL_BRIGHT, packedOverlay);
            return;
        }
        SubmitBuffers.item(bufferSource, poseStack, catalyst, ItemDisplayContext.NONE, LightCoordsUtil.FULL_BRIGHT, packedOverlay, blockEntity.getLevel(), 0);
    }

}
