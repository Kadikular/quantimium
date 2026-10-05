package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.FoldCoreBlock;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Whatever sits in the Fold Core's socket, floating over its top: a Tesseract with its bound block
 * turning inside, or a Folded Tesseract with its machine. It bobs while the chamber seals, as if the
 * field were drawing on it.
 */
public class FoldCoreBER extends SubmittingBER<FoldCoreBlockEntity> {

    private static final float SCALE = 0.6f;
    /** From the block's bottom to the middle of the floating item. */
    private static final float HEIGHT = 1.35f;

    public FoldCoreBER(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void render(FoldCoreBlockEntity core, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                          int packedLight, int packedOverlay) {
        ItemStack held = core.getHeld();
        if (held.isEmpty()) return;
        Direction front = core.getBlockState().getValue(FoldCoreBlock.FACING);
        float time = (core.getLevel() != null ? core.getLevel().getGameTime() : 0) + partialTick;
        float height = HEIGHT + (core.isSealing() ? 0.08f * (float) Math.sin(time * 0.4f) : 0.0f);

        poseStack.pushPose();
        poseStack.translate(0.5, height, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - front.toYRot()));
        poseStack.scale(SCALE, SCALE, SCALE);
        SubmitBuffers.item(buffers, poseStack, held, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT,
                packedOverlay, core.getLevel(), 0);
        poseStack.popPose();
    }

    /** The block and the space over it, so the floating item isn't culled while it's still in view. */
    @Override
    public AABB getRenderBoundingBox(FoldCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }
}
