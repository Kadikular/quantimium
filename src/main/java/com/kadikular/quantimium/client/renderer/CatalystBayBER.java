package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The catalyst in a bay, turning slowly in its glass case. */
public class CatalystBayBER extends SubmittingBER<CatalystBayBlockEntity> {

    private static final float SCALE = 0.6f;

    public CatalystBayBER(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void render(CatalystBayBlockEntity bay, float partialTick, PoseStack poses, MultiBufferSource buffers,
                          int packedLight, int packedOverlay) {
        ItemStack catalyst = bay.getCatalyst();
        if (catalyst.isEmpty()) return;
        float time = (bay.getLevel() == null ? 0 : bay.getLevel().getGameTime()) + partialTick;
        poses.pushPose();
        poses.translate(0.5, 0.55, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(time * 1.5f));
        poses.scale(SCALE, SCALE, SCALE);
        SubmitBuffers.item(buffers, poses, catalyst, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                bay.getLevel(), 0);
        poses.popPose();
    }
}
