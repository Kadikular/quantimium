package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The pocket under a bay's window: a room of void sunk into the floor, and each catalyst hanging in its
 * quarter of it, turning slowly. The frame and traces around the window are the block's model.
 */
public class CatalystBayBER extends SubmittingBER<CatalystBayBlockEntity> {

    /**
     * The window's opening in the frame and how deep the pocket under it goes, pulled in by a hair
     * from the model's own pocket walls, which are what an item in the hand shows instead.
     */
    private static final float WINDOW_MIN = 3.0f / 16.0f + 0.002f;
    private static final float WINDOW_MAX = 13.0f / 16.0f - 0.002f;
    private static final float FLOOR = 1.0f / 16.0f + 0.002f;
    private static final float TOP = 1.0f - 0.002f;

    private static final float SCALE = 0.24f;

    public CatalystBayBER(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void render(CatalystBayBlockEntity bay, float partialTick, PoseStack poses, MultiBufferSource buffers,
                          int packedLight, int packedOverlay) {
        VoidWindow.render(poses, buffers, WINDOW_MIN, FLOOR, WINDOW_MIN, WINDOW_MAX, TOP, WINDOW_MAX);
        float time = (bay.getLevel() == null ? 0 : bay.getLevel().getGameTime()) + partialTick;
        for (int slot = 0; slot < CatalystBayBlockEntity.SLOTS; slot++) {
            ItemStack catalyst = bay.getCatalyst(slot);
            if (catalyst.isEmpty()) continue;
            poses.pushPose();
            poses.translate(CatalystBayBlockEntity.slotX(slot), 0.68 + Math.sin(time * 0.06 + slot * 1.7) * 0.03,
                    CatalystBayBlockEntity.slotZ(slot));
            poses.mulPose(Axis.YP.rotationDegrees(time * 1.2f + slot * 90.0f));
            poses.scale(SCALE, SCALE, SCALE);
            // Lit by the void, not the room: a pocket under the floor would otherwise be pitch dark.
            SubmitBuffers.item(buffers, poses, catalyst, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT,
                    packedOverlay, bay.getLevel(), slot);
            poses.popPose();
        }
    }
}
