package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.SolarEmitterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * A Solar Emitter's light, and while the sky gives it any, a funnel of sunlight: four flat panes drawn in
 * from the edge of its glass to the top of its focus, faint at the glass and brighter at the focus, all the
 * brighter the brighter the sky.
 */
public class SolarEmitterBER extends LaserBeamBER<SolarEmitterBlockEntity> {

    /** The funnel's mouth, under the glass, and its neck, on the focus: half-widths and heights, in blocks. */
    private static final float MOUTH = 5 / 16f;
    private static final float MOUTH_Y = 13 / 16f;
    private static final float NECK = 2 / 16f;
    private static final float NECK_Y = 10 / 16f;

    public SolarEmitterBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(SolarEmitterBlockEntity solar, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(solar, partialTick, poses, buffers, packedLight, packedOverlay);
        float sun = solar.sun() / 15.0f;
        if (sun <= 0) return;
        PoseStack.Pose pose = poses.last();
        VertexConsumer funnel = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        float top = 0.10f * sun;
        float bottom = 0.24f * sun;
        float o = 0.5f;
        // Each side: the mouth's edge at the glass, down and in to the neck's edge on the focus.
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            funnel.addVertex(pose, o + a[0] * MOUTH, MOUTH_Y, o + a[1] * MOUTH).setColor(1.0f, 0.97f, 0.86f, top);
            funnel.addVertex(pose, o + b[0] * MOUTH, MOUTH_Y, o + b[1] * MOUTH).setColor(1.0f, 0.97f, 0.86f, top);
            funnel.addVertex(pose, o + b[0] * NECK, NECK_Y, o + b[1] * NECK).setColor(1.0f, 0.98f, 0.92f, bottom);
            funnel.addVertex(pose, o + a[0] * NECK, NECK_Y, o + a[1] * NECK).setColor(1.0f, 0.98f, 0.92f, bottom);
        }
    }
}
