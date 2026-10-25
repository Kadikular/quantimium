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
        LateGlow.draw(poses, node.getBlockPos(), buffers, partialTick, (late, lateBuffers, camera, middle, time) -> draw(late, lateBuffers, camera, time, centre, node, beams, aiming));
    }

    /** Beams run far: drawn as far off as one can reach, and then some. */
    @Override
    public int getViewDistance() {
        return Math.max(96, com.kadikular.quantimium.Config.maxLaserRange() + 64);
    }

    /**
     * Drawn even when its own block's section is out of sight: a beam from a relay behind you still crosses the
     * screen. Whether any of it is in view is still checked, against the box round its beams.
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
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

    private static void draw(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float time, Vec3 centre, LaserBeamView view,
                             List<LaserBeam> beams, boolean aiming) {
        PoseStack.Pose pose = poses.last();
        VertexConsumer halo = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        for (LaserBeam beam : beams) {
            if (hidden(beam, aiming)) continue;
            int bright = brightness(beam);
            float[] tint = halo(beam);
            if (bright == 0) {
                // Live but carrying nothing: a dim line, checking. Dark, it shows only while aiming.
                if (beam.live()) BeamRibbon.draw(halo, pose, Vec3.ZERO, line(view, beam, camera), 0.04f, tint[0], tint[1], tint[2], 0.10f);
                else if (aiming) BeamRibbon.draw(halo, pose, Vec3.ZERO, line(view, beam, camera), 0.03f, 0.55f, 0.6f, 0.75f, 0.18f);
                continue;
            }
            Vec3[] line = line(view, beam, camera);
            float flicker = 0.92f + 0.08f * Mth.sin(time * 0.7f + beam.face().ordinal());
            BeamRibbon.draw(halo, pose, Vec3.ZERO, line, (0.05f + 0.025f * bright) * view.widthScale(beam), tint[0], tint[1], tint[2],
                    0.12f + 0.03f * bright * flicker);
        }
        // Cores after every halo: one render type at a time.
        VertexConsumer cores = buffers.getBuffer(QuantumRenderTypes.BEAM_CORE);
        for (LaserBeam beam : beams) {
            if (hidden(beam, aiming)) continue;
            int bright = brightness(beam);
            float[] tint = core(beam);
            if (bright == 0) {
                if (beam.live()) BeamRibbon.draw(cores, pose, Vec3.ZERO, line(view, beam, camera), 0.008f, tint[0], tint[1], tint[2], 0.30f);
                continue;
            }
            BeamRibbon.draw(cores, pose, Vec3.ZERO, line(view, beam, camera), (0.012f + 0.006f * bright) * view.widthScale(beam),
                    tint[0], tint[1], tint[2], 0.55f + 0.06f * bright);
        }
    }

    /** UV light shows only to someone holding a wrench or the Linker; light in the mirror, only in it or through a Mirror Lens. */
    private static boolean hidden(LaserBeam beam, boolean aiming) {
        if ((beam.marks() & com.kadikular.quantimium.laser.Light.MIRRORED) != 0 && !com.kadikular.quantimium.client.ClientPhaseState.isActive()) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || !com.kadikular.quantimium.item.MirrorLensItem.isWorn(player)) return true;
        }
        return !aiming && com.kadikular.quantimium.laser.Lens.has(beam.lenses(), com.kadikular.quantimium.laser.Lens.UV);
    }

    private static final float[] AZURE_HALO = {0.40f, 0.62f, 1.0f};
    private static final float[] AZURE_CORE = {0.70f, 0.85f, 1.0f};
    private static final float[] LOGISTICS_HALO = {0.16f, 0.32f, 1.0f};
    private static final float[] LOGISTICS_CORE = {0.50f, 0.64f, 1.0f};

    /** How bright to draw it: by what it carries; a logistics network's light, while lit, as if at work. Out of power and unlit, off. */
    private static int brightness(LaserBeam beam) {
        int bright = LaserNetworks.brightness(beam.flow());
        return beam.live() && com.kadikular.quantimium.laser.Lens.logistics(beam.lenses()) ? Math.max(bright, 2) : bright;
    }

    /** The halo's colour: azure, a logistics network's deeper blue, or what its lenses tint it. */
    private static float[] halo(LaserBeam beam) {
        int colour = com.kadikular.quantimium.laser.Lens.colour(beam.lenses());
        if (colour == 0) return com.kadikular.quantimium.laser.Lens.logistics(beam.lenses()) ? LOGISTICS_HALO : AZURE_HALO;
        return new float[] {(colour >> 16 & 0xFF) / 255f, (colour >> 8 & 0xFF) / 255f, (colour & 0xFF) / 255f};
    }

    /** The core's: the halo's, most of the way to white. */
    private static float[] core(LaserBeam beam) {
        if (com.kadikular.quantimium.laser.Lens.colour(beam.lenses()) == 0) {
            return com.kadikular.quantimium.laser.Lens.logistics(beam.lenses()) ? LOGISTICS_CORE : AZURE_CORE;
        }
        float[] halo = halo(beam);
        return new float[] {0.45f + 0.55f * halo[0], 0.45f + 0.55f * halo[1], 0.45f + 0.55f * halo[2]};
    }

    /** From the node's side to what the beam ends on, or to where it fades; camera-relative. Light into a Rift Lens or Mirror goes on to its tear or plate, if it gets in. */
    private static Vec3[] line(LaserBeamView view, LaserBeam beam, Vec3 camera) {
        Vec3[] ends = view.endsOf(beam);
        Vec3 end = ends[1];
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (beam.hit() != null && level != null) {
            var state = level.getBlockState(beam.hit());
            // Light it won't take (into a base, onto a plate's back) stops at its side.
            var last = beam.lastRay();
            boolean refused = level.getBlockEntity(beam.hit()) instanceof com.kadikular.quantimium.laser.LaserNode node
                    && !(last != null ? node.accepts(last) : node.accepts(beam.face()));
            if (!refused && state.getBlock() instanceof com.kadikular.quantimium.laser.BeamAnchor anchor) end = anchor.beamPoint(state, beam.hit());
        }
        // Bent by mirrors: through the middle of each it bounces off.
        java.util.List<Vec3> points = beam.points();
        Vec3[] line = new Vec3[points.size()];
        line[0] = ends[0].subtract(camera);
        for (int i = 1; i < points.size() - 1; i++) line[i] = points.get(i).subtract(camera);
        line[line.length - 1] = end.subtract(camera);
        return line;
    }

    /** Beams reach as far as they go. */
    @Override
    public AABB getRenderBoundingBox(T node) {
        AABB box = new AABB(node.getBlockPos());
        for (LaserBeam beam : node.syncedBeams()) {
            for (Vec3 point : beam.points()) box = box.minmax(new AABB(point, point));
            Vec3[] ends = node.endsOf(beam);
            box = box.minmax(new AABB(ends[1], ends[1]));
        }
        return box.inflate(0.5);
    }
}
