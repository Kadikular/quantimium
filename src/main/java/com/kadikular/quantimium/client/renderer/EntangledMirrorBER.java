package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.EntangledMirrorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * An Entangled Mirror's light out of its face, and its Tesseract floating in the core: turning slowly, glowing the
 * field's colour while light crosses to the partner it's bound to, pale while none does.
 */
public class EntangledMirrorBER extends LaserBeamBER<EntangledMirrorBlockEntity> {

    private static final float SHELL_SCALE = 0.30f;

    public EntangledMirrorBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(EntangledMirrorBlockEntity mirror, float partialTick, PoseStack poses, MultiBufferSource buffers,
                          int packedLight, int packedOverlay) {
        super.render(mirror, partialTick, poses, buffers, packedLight, packedOverlay);
        if (mirror.tesseract().isEmpty()) return;
        float time = (mirror.getLevel() == null ? 0 : mirror.getLevel().getGameTime()) + partialTick;
        poses.pushPose();
        poses.translate(0.5, 0.5 + Mth.sin(time * 0.08f) * 0.02, 0.5);
        poses.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poses.mulPose(Axis.YP.rotationDegrees(time * (mirror.jumping() ? 1.6f : 0.5f)));
        TesseractShell.render(poses, buffers, time, mirror.jumping() ? TesseractShell.Tint.FLUX : TesseractShell.Tint.PALE);
        poses.popPose();
    }
}
