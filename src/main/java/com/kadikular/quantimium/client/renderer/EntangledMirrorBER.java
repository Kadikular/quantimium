package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.EntangledMirrorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * An Entangled Mirror's light out of its muzzle, and its Tesseract floating in the window of its housing: turning
 * slowly, glowing the field's colour while light it draws leaves it, pale while none does.
 */
public class EntangledMirrorBER extends LaserBeamBER<EntangledMirrorBlockEntity> {

    private static final float SHELL_SCALE = 0.22f;
    /** It sits a pixel behind the housing's middle, in the window between its posts. */
    private static final double OFFSET = -1.0 / 16.0;

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
        var normal = mirror.facing().getUnitVec3i();
        poses.translate(0.5 + normal.getX() * OFFSET, 0.5 + normal.getY() * OFFSET + Mth.sin(time * 0.08f) * 0.015, 0.5 + normal.getZ() * OFFSET);
        poses.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poses.mulPose(Axis.YP.rotationDegrees(time * (mirror.jumping() ? 1.6f : 0.5f)));
        TesseractShell.render(poses, buffers, time, mirror.jumping() ? TesseractShell.Tint.FLUX : TesseractShell.Tint.PALE);
        poses.popPose();
    }
}
