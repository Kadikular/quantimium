package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.client.ClientPhaseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Projector's flux orb, for everyone: dim while it watches, bright while it works. The beam is
 * the mirror's alone — a phased viewer sees it reach out to whatever it is holding, the real world
 * only sees the orb burn brighter and the block light up.
 */
public class DecoherenceProjectorBER extends SubmittingBER<DecoherenceProjectorBlockEntity> {

    private static final float ORB_RADIUS = 0.38f;
    /** The tesseract turning at the orb's heart, sized to sit inside it. */
    private static final float CORE_SCALE = 0.6f;
    private static final int BEAM_SEGMENTS = 18;

    public DecoherenceProjectorBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DecoherenceProjectorBlockEntity projector, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Something placed above it has shut it down: no orb, no beam.
        if (projector.obstructed()) return;
        float time = (projector.getLevel() == null ? 0.0f : projector.getLevel().getGameTime()) + partialTick;
        boolean active = projector.isActive();
        float intensity = active ? 1.0f + 0.1f * Mth.sin(time * 0.6f) : 0.55f;

        poses.pushPose();
        // The orb floats out from whatever it's mounted on, bobbing along the way it points.
        Vec3 orb = projector.orb().subtract(Vec3.atLowerCornerOf(projector.getBlockPos()))
                .add(Vec3.atLowerCornerOf(projector.facing().getUnitVec3i()).scale(Mth.sin(time * 0.05f) * 0.04));
        poses.translate(orb.x, orb.y, orb.z);
        VertexConsumer lines = buffers.getBuffer(QuantumRenderTypes.fieldEdge(QuantumRenderTypes.cameraDistance(poses)));
        FieldOrb.renderLines(poses, lines, time * (active ? 2.0f : 1.0f), TesseractShell.Tint.FLUX, ORB_RADIUS, intensity);
        // Its core glow is faint by design (it wraps items in the Foundry); here it is the light itself.
        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        FieldOrb.renderCore(poses, glow, TesseractShell.Tint.FLUX, ORB_RADIUS, intensity * 3.0f);
        poses.pushPose();
        poses.scale(CORE_SCALE, CORE_SCALE, CORE_SCALE);
        poses.mulPose(Axis.YP.rotationDegrees(time * (active ? 2.4f : 0.8f)));
        poses.mulPose(Axis.XP.rotationDegrees(18.0f));
        RenderType edges = TesseractShell.edgeTypeFor(poses);
        TesseractShell.render(poses, buffers, time * (active ? 2.0f : 1.0f), edges, TesseractShell.Tint.FLUX,
                active ? 1.8f : 1.0f);
        poses.popPose();
        poses.popPose();

        if (!active || !ClientPhaseState.isActive()) return;
        Vec3 end = projector.beamEnd(partialTick);
        if (end == null) return;
        // Drawn after the mirror's flora, which would otherwise paint over glow that writes no depth.
        Vec3 from = projector.orb();
        LateGlow.draw(buffers, partialTick, (late, shared, camera, middle, now) -> {
            Vec3[] points = BeamRibbon.wave(from.subtract(camera), end.subtract(camera), BEAM_SEGMENTS, now, 0.08f);
            // Same colours as the Lance: coherent azure core in a violet halo.
            // The halo writes no depth; the core does, so translucent blocks behind stay behind it.
            PoseStack.Pose pose = late.last();
            BeamRibbon.draw(shared.getBuffer(QuantumRenderTypes.FIELD_GLOW), pose, Vec3.ZERO, points, 0.22f, 0.55f, 0.35f, 1.0f, 0.22f);
            BeamRibbon.draw(shared.getBuffer(QuantumRenderTypes.BEAM_CORE), pose, Vec3.ZERO, points, 0.07f, 0.50f, 0.70f, 1.0f, 0.85f);
        });
    }

    /** The beam reaches up to the Projector's full range, so keep drawing when only the far end is in view. */
    @Override
    public AABB getRenderBoundingBox(DecoherenceProjectorBlockEntity projector) {
        return new AABB(projector.getBlockPos()).inflate(DecoherenceProjectorBlockEntity.RANGE + 1.0);
    }
}
