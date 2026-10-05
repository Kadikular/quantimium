package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.block.entity.EntangledDockBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The item a dock holds in superposition: a copy of it turning slowly over the cradle, inside a small
 * tesseract cell. The cell is azure while the item is being charged, pale while it waits (full, or
 * away with its owner), and red with the dock out of power.
 */
public class EntangledDockBER extends SubmittingBER<EntangledDockBlockEntity> {

    private static final float ITEM_SCALE = 0.45f;
    private static final float SHELL_SCALE = 0.42f;

    public EntangledDockBER(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EntangledDockBlockEntity dock, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ItemStack shown = dock.shown();
        if (shown.isEmpty()) return;
        float time = (dock.getLevel() == null ? 0 : dock.getLevel().getGameTime()) + partialTick;
        poses.pushPose();
        poses.translate(0.5, 1.15 + Mth.sin(time * 0.08f) * 0.03, 0.5);

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
        poses.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        SubmitBuffers.item(buffers, poses, shown, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, dock.getLevel(), 0);
        poses.popPose();
        // Items are batched until the block entity pass ends; drawn now, the cell sits in front of it.

        poses.pushPose();
        poses.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poses.mulPose(Axis.YP.rotationDegrees(-time * 0.6f));
        TesseractShell.Tint tint = switch (dock.status()) {
            case EntangledDockBlockEntity.STATUS_CHARGING -> TesseractShell.Tint.FLUX;
            case EntangledDockBlockEntity.STATUS_NO_POWER -> TesseractShell.Tint.RED;
            default -> TesseractShell.Tint.PALE;
        };
        TesseractShell.render(poses, buffers, time, tint);
        poses.popPose();
        poses.popPose();
    }
}
