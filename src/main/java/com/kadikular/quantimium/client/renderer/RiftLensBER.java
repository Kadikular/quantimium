package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.RiftLensBlock;
import com.kadikular.quantimium.block.entity.RiftLensBlockEntity;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A Rift Lens's light, and the tear in its middle while laser light goes through it: the Harvest Laser's
 * tear, the brighter the more the light carries.
 */
public class RiftLensBER extends LaserBeamBER<RiftLensBlockEntity> {

    public RiftLensBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(RiftLensBlockEntity lens, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(lens, partialTick, poses, buffers, packedLight, packedOverlay);
        int bright = 0;
        boolean lit = lens.getBlockState().getValue(RiftLensBlock.OPEN);
        for (LaserBeam beam : lens.syncedBeams()) {
            lit |= beam.live();
            bright = Math.max(bright, LaserNetworks.brightness(beam.flow()));
        }
        if (!lit) return;
        Vec3 ring = lens.getBlockPos().getCenter();
        int seed = lens.getBlockPos().hashCode();
        float leak = 0.15f + 0.08f * bright;
        LateGlow.draw(buffers, partialTick, (late, lateBuffers, camera, middle, time) -> {
            float breathe = 0.9f + 0.1f * Mth.sin(time * 0.15f);
            MirrorRiftRenderer.render(ring, seed, breathe, HarvestLaserBER.TEAR_SCALE, 0.70f, 0.42f, 1.0f, HarvestLaserBER.TEAR_TINT,
                    0.15f + leak * 1.4f, late, lateBuffers, camera, middle, time);
        });
    }
}
