package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity;
import com.kadikular.quantimium.client.ClientPhaseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Harvest Laser's work, seen from both sides. Everyone sees the small orb in its window and the
 * tear it holds open in the lens, and the beam leaving the orb and going into the tear. Only the
 * mirror sees what comes out of it: the same light, violet now, poured onto the crystal, or onto a
 * held Veiled with a pale thread being drawn back out of it.
 */
public class HarvestLaserBER extends SubmittingBER<HarvestLaserBlockEntity> {

    private static final float ORB_RADIUS = 0.16f;
    /** The orb sits a pixel behind the housing's middle, in the window between its posts. */
    private static final double ORB_OFFSET = -1.0 / 16.0;
    private static final int BEAM_SEGMENTS = 12;
    /** The tear is the Mirror Rift's shape, cut down to fit in the ring. */
    private static final float TEAR_SCALE = 0.24f;
    private static final MirrorRiftRenderer.Tint TEAR_TINT =
            new MirrorRiftRenderer.Tint(0.62f, 0.16f, 0.82f, 0.55f, 0.16f);

    public HarvestLaserBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HarvestLaserBlockEntity laser, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float time = (laser.getLevel() == null ? 0.0f : laser.getLevel().getGameTime()) + partialTick;
        boolean active = laser.isActive();
        Vec3 normal = Vec3.atLowerCornerOf(laser.facing().getUnitVec3i());

        poses.pushPose();
        Vec3 orb = new Vec3(0.5, 0.5, 0.5).add(normal.scale(ORB_OFFSET));
        poses.translate(orb.x, orb.y, orb.z);
        float intensity = active ? 1.0f + 0.15f * Mth.sin(time * 0.9f) : 0.4f;
        VertexConsumer lines = buffers.getBuffer(QuantumRenderTypes.fieldEdge(QuantumRenderTypes.cameraDistance(poses)));
        FieldOrb.renderLines(poses, lines, time * (active ? 3.0f : 1.0f), TesseractShell.Tint.FLUX, ORB_RADIUS, intensity);
        FieldOrb.renderCore(poses, buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW), TesseractShell.Tint.FLUX,
                ORB_RADIUS, intensity * 3.0f);
        poses.popPose();
        if (!active || !laser.hasLine()) return;

        // Drawn after the mirror's flora, which would otherwise paint over glow that writes no depth.
        Vec3 ring = Vec3.atCenterOf(laser.ringPos());
        Vec3 core = laser.getBlockPos().getCenter().add(normal.scale(ORB_OFFSET));
        Vec3 crystal = Vec3.atCenterOf(laser.crystalPos());
        // The tear frays as the crystal comes apart, and thrashes when something gets out.
        float leak = Math.max(laser.leak(partialTick), 0.4f * laser.work(partialTick));
        int seed = laser.getBlockPos().hashCode();
        boolean mirror = ClientPhaseState.isActive();
        boolean thread = laser.isDrawing();
        LateGlow.draw(buffers, partialTick, (late, lateBuffers, camera, middle, now) -> draw(late, lateBuffers, camera,
                middle, now, ring, core, crystal, leak, seed, mirror, thread));
    }

    private static void draw(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 middle, float time,
                             Vec3 ring, Vec3 core,
                             Vec3 crystal, float leak, int seed, boolean mirror, boolean thread) {
        float breathe = 0.9f + 0.1f * Mth.sin(time * 0.15f);
        MirrorRiftRenderer.render(ring, seed, breathe, TEAR_SCALE, 0.70f, 0.42f, 1.0f, TEAR_TINT,
                0.15f + leak * 1.4f, poses, buffers, camera, middle, time);

        PoseStack.Pose pose = poses.last();
        // From the core, out through the muzzle and into the lens, straight: coherent azure in a violet
        // halo, like the Lance. Both sides see this. The mirror sees it come out onto the crystal.
        Vec3[] in = BeamRibbon.wave(core.subtract(camera), ring.subtract(camera), BEAM_SEGMENTS, time, 0.0f);
        Vec3[] out = mirror ? BeamRibbon.wave(ring.subtract(camera), crystal.subtract(camera), BEAM_SEGMENTS, time,
                0.03f + leak * 0.1f) : null;
        // The thread being drawn back out of the Veiled along the light: a pale strand that wavers.
        Vec3[] strand = mirror && thread ? BeamRibbon.wave(crystal.subtract(camera).add(0.0, -0.15, 0.0),
                ring.subtract(camera), BEAM_SEGMENTS * 2, time * 0.7f, 0.12f) : null;

        // Halos first, writing no depth; then the cores, which do. One render type at a time.
        VertexConsumer halo = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        BeamRibbon.draw(halo, pose, Vec3.ZERO, in, 0.12f, 0.55f, 0.35f, 1.0f, 0.22f);
        if (out != null) BeamRibbon.draw(halo, pose, Vec3.ZERO, out, 0.16f, 0.62f, 0.28f, 0.95f, 0.25f);
        VertexConsumer cores = buffers.getBuffer(QuantumRenderTypes.BEAM_CORE);
        BeamRibbon.draw(cores, pose, Vec3.ZERO, in, 0.04f, 0.50f, 0.70f, 1.0f, 0.85f);
        if (out != null) BeamRibbon.draw(cores, pose, Vec3.ZERO, out, 0.05f, 0.85f, 0.65f, 1.0f, 0.85f);
        if (strand != null) BeamRibbon.draw(cores, pose, Vec3.ZERO, strand, 0.035f, 1.0f, 0.94f, 0.78f, 0.9f);
    }

    /** The tear and the beams reach as far as the laser does. */
    @Override
    public AABB getRenderBoundingBox(HarvestLaserBlockEntity laser) {
        return new AABB(laser.getBlockPos())
                .minmax(new AABB(laser.getBlockPos().relative(laser.facing(), HarvestLaserBlockEntity.REACH))).inflate(1.0);
    }
}
