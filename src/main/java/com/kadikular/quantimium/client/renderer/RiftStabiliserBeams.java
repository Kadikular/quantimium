package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.phase.FluxRift;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The beams of holding Rift Stabilisers: two from each emitter to the wound's top and bottom, with
 * a faint sheet of field stretched between them. Seen from both realms, since the rift is in both.
 *
 * <p>Queued by the stabiliser's renderer, which is frustum-culled for free, and drawn with the rifts
 * after particles. Drawn in the block-entity pass, they came out under the bleed flora and the rift
 * itself: neither the beams nor those write depth, so whatever is drawn last wins.
 *
 * <p>Coherent azure throughout, the Projector's colours without its violet halo: this is Quantimium
 * holding a rift open, not tearing one down.
 */
public final class RiftStabiliserBeams {

    private static final int BEAM_SEGMENTS = 14;
    /** Strips in the sheet: enough for the shimmer to travel, few enough to stay cheap. */
    private static final int SHEET_STRIPS = 10;
    /** Beams land a little inside the wound's tips, where the silhouette is still wide enough to hit. */
    private static final double TIP_INSET = 0.25;

    private record Beam(Vec3 emitter, BlockPos rift, int stage) {}

    private static final List<Beam> QUEUE = new ArrayList<>();

    private RiftStabiliserBeams() {}

    public static void queue(Vec3 emitter, BlockPos rift, int stage) {
        QUEUE.add(new Beam(emitter, rift, stage));
    }

    public static void render(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float time) {
        if (QUEUE.isEmpty()) return;
        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        PoseStack.Pose pose = poses.last();
        for (Beam beam : QUEUE) {
            Vec3 from = beam.emitter().subtract(camera);
            double axisX = beam.rift().getX() + 0.5 - camera.x;
            double axisZ = beam.rift().getZ() + 0.5 - camera.z;
            Vec3 top = new Vec3(axisX, FluxRift.top(beam.rift(), beam.stage()) - TIP_INSET - camera.y, axisZ);
            Vec3 bottom = new Vec3(axisX, FluxRift.bottom(beam.rift()) + TIP_INSET - camera.y, axisZ);
            sheet(glow, pose, from, bottom, top, time);
            for (Vec3 end : new Vec3[] {top, bottom}) {
                Vec3[] points = BeamRibbon.wave(from, end, BEAM_SEGMENTS, time, 0.03f);
                BeamRibbon.draw(glow, pose, Vec3.ZERO, points, 0.09f, 0.20f, 0.45f, 1.0f, 0.25f);
                BeamRibbon.draw(glow, pose, Vec3.ZERO, points, 0.03f, 0.75f, 0.87f, 1.0f, 0.85f);
            }
        }
        QUEUE.clear();
    }

    /** Anything left from a frame that never reached the draw, e.g. on leaving the world. */
    public static void clear() {
        QUEUE.clear();
    }

    /**
     * A fan from the emitter to the rift's axis, faint, with a band of brightness climbing it so the
     * field reads as working rather than as a pane of glass. Both windings, since it is seen from
     * either side.
     */
    private static void sheet(VertexConsumer glow, PoseStack.Pose pose, Vec3 from, Vec3 bottom, Vec3 top, float time) {
        for (int i = 0; i < SHEET_STRIPS; i++) {
            float t0 = (float) i / SHEET_STRIPS;
            float t1 = (float) (i + 1) / SHEET_STRIPS;
            Vec3 a = bottom.lerp(top, t0);
            Vec3 b = bottom.lerp(top, t1);
            float band = 0.5f + 0.5f * Mth.sin(time * 0.15f - (t0 + t1) * 4.0f);
            float edge = 0.05f + 0.07f * band;
            vertex(glow, pose, from, 0.0f);
            vertex(glow, pose, a, edge);
            vertex(glow, pose, b, edge);
            vertex(glow, pose, from, 0.0f);
            vertex(glow, pose, from, 0.0f);
            vertex(glow, pose, b, edge);
            vertex(glow, pose, a, edge);
            vertex(glow, pose, from, 0.0f);
        }
    }

    /** Fades to nothing at the emitter, so the sheet grows out of the beam rather than hanging off it. */
    private static void vertex(VertexConsumer glow, PoseStack.Pose pose, Vec3 at, float alpha) {
        glow.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(0.20f, 0.52f, 1.0f, alpha);
    }
}
