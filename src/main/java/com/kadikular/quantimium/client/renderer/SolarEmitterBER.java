package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.SolarEmitterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A Solar Emitter's light, and while the sky gives it any, a soft funnel of light from its glass down into
 * its focus and a halo round the focus: brighter the brighter the sky.
 */
public class SolarEmitterBER extends LaserBeamBER<SolarEmitterBlockEntity> {

    public SolarEmitterBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(SolarEmitterBlockEntity solar, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(solar, partialTick, poses, buffers, packedLight, packedOverlay);
        float sun = solar.sun() / 15.0f;
        if (sun <= 0) return;
        Vec3 focus = solar.getBlockPos().getCenter();
        Vec3 glass = focus.add(0, 13.5 / 16 - 0.5, 0);
        LateGlow.draw(buffers, partialTick, (late, lateBuffers, camera, middle, time) -> {
            PoseStack.Pose pose = late.last();
            VertexConsumer glow = lateBuffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
            float breathe = 0.92f + 0.08f * Mth.sin(time * 0.05f);
            Vec3[] funnel = {glass.subtract(camera), focus.subtract(camera)};
            // Wide and faint at the glass, a narrower core into the focus: a funnel, near enough.
            BeamRibbon.draw(glow, pose, Vec3.ZERO, funnel, 0.30f, 1.0f, 0.95f, 0.80f, 0.05f * sun);
            BeamRibbon.draw(glow, pose, Vec3.ZERO, funnel, 0.14f, 1.0f, 0.97f, 0.88f, 0.08f * sun);
            Vec3[] halo = {focus.add(0, -0.09, 0).subtract(camera), focus.add(0, 0.09, 0).subtract(camera)};
            BeamRibbon.draw(glow, pose, Vec3.ZERO, halo, 0.17f * breathe, 0.75f, 0.88f, 1.0f, 0.22f * sun);
        });
    }
}
