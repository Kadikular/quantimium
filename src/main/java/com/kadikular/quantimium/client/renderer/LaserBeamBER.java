package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The light leaving a laser emitter or mirror: a straight coherent beam from its face to what it ends
 * on, azure in a soft halo, wider and brighter the more it carries. A beam that carries nothing isn't
 * drawn: dark light is no light.
 */
public class LaserBeamBER<T extends BlockEntity & LaserBeamView> extends SubmittingBER<T> {

    public LaserBeamBER(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void render(T node, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        List<LaserBeam> beams = node.syncedBeams();
        if (beams.isEmpty()) return;
        Vec3 centre = node.getBlockPos().getCenter();
        LateGlow.draw(buffers, partialTick, (late, lateBuffers, camera, middle, time) -> draw(late, lateBuffers, camera, time, centre, beams));
    }

    private static void draw(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float time, Vec3 centre, List<LaserBeam> beams) {
        PoseStack.Pose pose = poses.last();
        VertexConsumer halo = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        for (LaserBeam beam : beams) {
            int bright = LaserNetworks.brightness(beam.flow());
            if (bright == 0) continue;
            Vec3[] line = line(centre, beam, camera);
            float flicker = 0.92f + 0.08f * Mth.sin(time * 0.7f + beam.direction().ordinal());
            BeamRibbon.draw(halo, pose, Vec3.ZERO, line, 0.05f + 0.025f * bright, 0.40f, 0.62f, 1.0f, 0.12f + 0.03f * bright * flicker);
        }
        // Cores after every halo: one render type at a time.
        VertexConsumer cores = buffers.getBuffer(QuantumRenderTypes.BEAM_CORE);
        for (LaserBeam beam : beams) {
            int bright = LaserNetworks.brightness(beam.flow());
            if (bright == 0) continue;
            BeamRibbon.draw(cores, pose, Vec3.ZERO, line(centre, beam, camera), 0.012f + 0.006f * bright,
                    0.70f, 0.85f, 1.0f, 0.55f + 0.06f * bright);
        }
    }

    /** From the node's face to the face of what the beam ends on, or to where it fades; camera-relative. */
    private static Vec3[] line(Vec3 centre, LaserBeam beam, Vec3 camera) {
        Vec3 dir = Vec3.atLowerCornerOf(beam.direction().getUnitVec3i());
        Vec3 from = centre.add(dir.scale(0.5));
        Vec3 to = centre.add(dir.scale(beam.length() + 0.5));
        return new Vec3[] {from.subtract(camera), to.subtract(camera)};
    }

    /** Beams reach as far as they go. */
    @Override
    public AABB getRenderBoundingBox(T node) {
        AABB box = new AABB(node.getBlockPos());
        for (LaserBeam beam : node.syncedBeams()) {
            box = box.minmax(new AABB(node.getBlockPos().relative(beam.direction(), beam.length() + 1)));
        }
        return box.inflate(0.5);
    }
}
