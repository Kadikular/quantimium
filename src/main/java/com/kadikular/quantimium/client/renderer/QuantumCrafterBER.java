// Path: src/main/java/com/kadikular/quantimium/client/renderer/QuantumCrafterBER.java
package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.block.QuantumCrafterBlock;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
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
 * Draws what the crafter's cage contains: a room of void, and the catalyst hanging in it inside a
 * small containment cell. Nothing here is a real block — the catalyst is only an item in a slot, so
 * the void is the honest way to show a machine that exists just far enough away to be used.
 */
public class QuantumCrafterBER extends SubmittingBER<QuantumCrafterBlockEntity> {

    /** The void room, sized to sit just inside the corner pylons, plinth and cap of the cage model. */
    private static final float WALL_MIN = 0.1877f;
    private static final float WALL_MAX = 0.8123f;

    /** The facing bar hangs just inside the void room's near wall, above its floor. */
    private static final float MARKER_DEPTH = WALL_MIN + 0.004f;
    private static final float MARKER_BOTTOM = WALL_MIN + 0.035f;

    /** Small enough that the catalyst's corners stay inside the cell as both turn. */
    private static final float CATALYST_SCALE = 0.26f;
    private static final float SHELL_SCALE = 0.57f;
    private static final float SHELL_TILT_DEGREES = 12.0f;

    public QuantumCrafterBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuantumCrafterBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!blockEntity.isBasic() && WrenchView.isActive()) {
            PortModeIndicators.render(poseStack, bufferSource, blockEntity.sideConfigsView());
        }

        VoidWindow.render(poseStack, bufferSource, WALL_MIN, WALL_MIN, WALL_MIN, WALL_MAX, WALL_MAX, WALL_MAX);
        FrontMarker.render(poseStack, bufferSource,
                blockEntity.getBlockState().getValue(QuantumCrafterBlock.FACING),
                MARKER_DEPTH, MARKER_BOTTOM);

        ItemStack catalyst = blockEntity.getInventory().getStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT);
        if (catalyst.isEmpty()) return;

        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;
        float time = gameTime + partialTick;

        poseStack.pushPose();
        // The catalyst and the cell around it share this pivot, so both ride the same floating bob.
        poseStack.translate(0.5, 0.5 + Math.sin(time * 0.08) * 0.025, 0.5);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.8f));
        poseStack.scale(CATALYST_SCALE, CATALYST_SCALE, CATALYST_SCALE);
        renderCatalyst(blockEntity, catalyst, poseStack, bufferSource, packedOverlay);
        poseStack.popPose();

        // Block and item models are batched and drawn once the whole block entity pass ends, which
        // would put the catalyst on top of the cell that is meant to be in front of it.

        poseStack.pushPose();
        poseStack.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.6f));
        poseStack.mulPose(Axis.XP.rotationDegrees(SHELL_TILT_DEGREES));
        TesseractShell.render(poseStack, bufferSource, time);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderCatalyst(QuantumCrafterBlockEntity blockEntity, ItemStack catalyst,
                                       PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        if (catalyst.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            if (state.getRenderShape() == RenderShape.MODEL) {
                poseStack.pushPose();
                poseStack.translate(-0.5, -0.5, -0.5);
                SubmitBuffers.block(bufferSource, poseStack, state, LightCoordsUtil.FULL_BRIGHT, packedOverlay);
                poseStack.popPose();
                return;
            }
        }
        // Chests, shulkers and plain items have no standalone block model, so fall back to the item
        // model, which centres itself and so needs no offset of its own.
        SubmitBuffers.item(bufferSource, poseStack, catalyst, ItemDisplayContext.NONE, LightCoordsUtil.FULL_BRIGHT, packedOverlay, blockEntity.getLevel(), 0);
    }

}
