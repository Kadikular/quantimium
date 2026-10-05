package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.RiftStabiliserBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * A Rift Stabiliser's emitter, a small crossed core over its cap that spins and bobs, murky while
 * idle and burning while it beams; and its beams, queued for {@link RiftStabiliserBeams}, which
 * draws them later in the frame.
 */
public class RiftStabiliserBER extends SubmittingBER<RiftStabiliserBlockEntity> {

    private static final Identifier CORE_IDLE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "block/rift_stabiliser_core_idle");
    private static final Identifier CORE_LIT =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "block/rift_stabiliser_core_lit");
    private static final float CORE_SIZE = 4 / 16f;
    /** Blocks up and down. */
    private static final float BOB = 0.025f;

    public RiftStabiliserBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RiftStabiliserBlockEntity stabiliser, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float time = (stabiliser.getLevel() == null ? 0 : stabiliser.getLevel().getGameTime()) + partialTick;
        BlockPos rift = stabiliser.beamRift();
        boolean beaming = rift != null;

        poses.pushPose();
        poses.translate(0.5, RiftStabiliserBlockEntity.EMITTER_HEIGHT + Mth.sin(time * 0.08f) * BOB, 0.5);
        CrossedCore.render(poses, buffers, beaming ? CORE_LIT : CORE_IDLE, CORE_SIZE, beaming ? time * 2 : time,
                beaming ? LightCoordsUtil.FULL_BRIGHT : packedLight);
        poses.popPose();
        // The beams go in with the rifts, after flora and block entities: drawn here, anything
        // translucent that renders later paints straight over them.
        if (beaming) RiftStabiliserBeams.queue(stabiliser.emitter(), rift, stabiliser.beamStage());
    }

    /** The beams reach up to six blocks out and several above, so keep drawing while only the rift is in view. */
    @Override
    public AABB getRenderBoundingBox(RiftStabiliserBlockEntity stabiliser) {
        return new AABB(stabiliser.getBlockPos()).inflate(9.0);
    }
}
