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
 * on, azure in a soft halo, wider and brighter the more it carries. A live beam carrying nothing is a
 * dim line, checking for somewhere to send power; a dark one shows only while aiming.
 */
public class LaserBeamBER<T extends BlockEntity & LaserBeamView> extends SubmittingBER<T> {

    public LaserBeamBER(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void render(T node, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        List<LaserBeam> beams = node.syncedBeams();
        if (beams.isEmpty()) return;
        Vec3 centre = node.getBlockPos().getCenter();
        boolean aiming = aiming();
        LateGlow.draw(buffers, partialTick, (late, lateBuffers, camera, middle, time) -> draw(late, lateBuffers, camera, time, centre, beams, aiming));
    }

    /** Holding the Laser Linker or a wrench shows even dark beams, faintly: where light would go. */
    private static boolean aiming() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return false;
        for (var stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(com.kadikular.quantimium.init.ModItems.LASER_LINKER.get())
                    || stack.is(com.kadikular.quantimium.init.ModTags.TOOLS_WRENCH)) {
                return true;
            }
        }
        return false;
    }

    private static void draw(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float time, Vec3 centre, List<LaserBeam> beams,
                             boolean aiming) {
        PoseStack.Pose pose = poses.last();
        VertexConsumer halo = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        for (LaserBeam beam : beams) {
            int bright = LaserNetworks.brightness(beam.flow());
            float[] tint = halo(beam);
            if (bright == 0) {
                // Live but carrying nothing: a dim line, checking. Dark, it shows only while aiming.
                if (beam.live()) BeamRibbon.draw(halo, pose, Vec3.ZERO, line(centre, beam, camera), 0.04f, tint[0], tint[1], tint[2], 0.10f);
                else if (aiming) BeamRibbon.draw(halo, pose, Vec3.ZERO, line(centre, beam, camera), 0.03f, 0.55f, 0.6f, 0.75f, 0.18f);
                continue;
            }
            Vec3[] line = line(centre, beam, camera);
            float flicker = 0.92f + 0.08f * Mth.sin(time * 0.7f + beam.face().ordinal());
            BeamRibbon.draw(halo, pose, Vec3.ZERO, line, 0.05f + 0.025f * bright, tint[0], tint[1], tint[2], 0.12f + 0.03f * bright * flicker);
        }
        // Cores after every halo: one render type at a time.
        VertexConsumer cores = buffers.getBuffer(QuantumRenderTypes.BEAM_CORE);
        for (LaserBeam beam : beams) {
            int bright = LaserNetworks.brightness(beam.flow());
            float[] tint = core(beam);
            if (bright == 0) {
                if (beam.live()) BeamRibbon.draw(cores, pose, Vec3.ZERO, line(centre, beam, camera), 0.008f, tint[0], tint[1], tint[2], 0.30f);
                continue;
            }
            BeamRibbon.draw(cores, pose, Vec3.ZERO, line(centre, beam, camera), 0.012f + 0.006f * bright,
                    tint[0], tint[1], tint[2], 0.55f + 0.06f * bright);
        }
    }

    private static final float[] AZURE_HALO = {0.40f, 0.62f, 1.0f};
    private static final float[] AZURE_CORE = {0.70f, 0.85f, 1.0f};

    /** The halo's colour: azure, or what its lenses tint it. */
    private static float[] halo(LaserBeam beam) {
        int colour = com.kadikular.quantimium.laser.Lens.colour(beam.lenses());
        if (colour == 0) return AZURE_HALO;
        return new float[] {(colour >> 16 & 0xFF) / 255f, (colour >> 8 & 0xFF) / 255f, (colour & 0xFF) / 255f};
    }

    /** The core's: the halo's, most of the way to white. */
    private static float[] core(LaserBeam beam) {
        if (com.kadikular.quantimium.laser.Lens.colour(beam.lenses()) == 0) return AZURE_CORE;
        float[] halo = halo(beam);
        return new float[] {0.45f + 0.55f * halo[0], 0.45f + 0.55f * halo[1], 0.45f + 0.55f * halo[2]};
    }

    /** From the node's side to what the beam ends on, or to where it fades; camera-relative. */
    private static Vec3[] line(Vec3 centre, LaserBeam beam, Vec3 camera) {
        Vec3[] ends = beam.ends();
        return new Vec3[] {ends[0].subtract(camera), ends[1].subtract(camera)};
    }

    /** Beams reach as far as they go. */
    @Override
    public AABB getRenderBoundingBox(T node) {
        AABB box = new AABB(node.getBlockPos());
        for (LaserBeam beam : node.syncedBeams()) {
            Vec3[] ends = beam.ends();
            box = box.minmax(new AABB(ends[1], ends[1]));
        }
        return box.inflate(0.5);
    }
}
