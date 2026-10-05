package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.AnomalyContainmentHallBlock;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.client.ClientPhaseState;
import com.kadikular.quantimium.client.model.VeiledModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntConsumer;

/**
 * What the glass is for: the cell is a hole in the world with something held in it.
 *
 * <p>The interior is filled with end-portal void so looking through the shell reads as depth rather
 * than as a lit room, and the field is drawn as rings top and bottom joined by chasing corner bars.
 * A held Veiled is a pale wisp from the real world and its whole Veiled form from the mirror. When
 * the power fails the field goes slack and the occupant starts fighting the walls — the tell that the
 * bill has not been paid — and ten seconds later it walks out. A hall out of Rift Residue fails the
 * same way, but slower and with the cage stuttering, since the power is still there.
 */
public class ContainmentHallBER extends SubmittingBER<ContainmentHallBlockEntity> {

    /** Just clear of the base plinth's top face. */
    private static final float CELL_BOTTOM = 1.02f;
    private static final float CELL_TOP = 1.0f + ContainmentHallStructure.WALL_HEIGHT - 0.02f;
    private static final float CELL_MID = (CELL_BOTTOM + CELL_TOP) * 0.5f;
    /**
     * The void fills the bore exactly, pulled in by a hair.
     *
     * <p>Its four vertical edges land on the corner panes' pillars, which is what the pillars are
     * for: an edge you can follow the full height is what made the void read as a painted cube, and
     * a solid column hides it far better than growing the box into the glass ever did. The hair of
     * inset is the Crafter's trick — enough to keep the box off the pillar faces, small enough that
     * the corners still look like they meet.
     */
    private static final float VOID_INSET = 0.002f;
    /** Tank geometry copied from the Foundry: the arms are the same blocks in the same places. */
    private static final float ARM_OUTER = 3.0f;
    private static final float TANK_Y = 1.44f;
    /** Mid-way up the Foundry's flux-dependent range: the hall's draw on an arm is constant. */
    private static final float TANK_RADIUS = 0.19f;
    private static final float BAR_OFFSET = 0.34f;
    private static final int BAR_STEPS = 14;
    private static final int RING_SEGMENTS = 26;

    private static final TesseractShell.Tint FIELD = new TesseractShell.Tint(0.62f, 0.42f, 0.96f);
    private static final TesseractShell.Tint LOOSE = new TesseractShell.Tint(1.0f, 0.36f, 0.30f);
    /** A soul sized to sit inside the one-block cell with room to turn. */
    private static final float SOUL_SCALE = 1.15f;
    /** As strong as a creature's soul in daylight, so it reads through the glass. */
    private static final float SOUL_ALPHA = 2.4f;

    /** ARGB, as the beacon renderer expects. Thinner and dimmer when the field is slack. */
    private static final int BEAM_GOLD = 0xFFFFC63A;
    private static final int BEAM_GOLD_SLACK = 0x99C08A2A;

    private static final Identifier OCCUPANT_TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/veiled.png");
    private static final Identifier OCCUPANT_GLOW =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/veiled_glow.png");
    /** The Veiled is three blocks tall; the cell is a hair under that, and a little narrower than it. */
    private static final float OCCUPANT_SCALE = 0.82f;

    private final VeiledModel occupant;

    public ContainmentHallBER(BlockEntityRendererProvider.Context context) {
        this.occupant = new VeiledModel(context.bakeLayer(VeiledModel.LAYER));
    }

    @Override
    public void render(ContainmentHallBlockEntity hall, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!hall.isFormed()) return;

        float time = (hall.getLevel() == null ? 0.0f : hall.getLevel().getGameTime()) + partialTick;
        boolean held = hall.getBlockState().hasProperty(AnomalyContainmentHallBlock.ACTIVE)
                && hall.getBlockState().getValue(AnomalyContainmentHallBlock.ACTIVE);
        TesseractShell.Tint tint = held ? FIELD : LOOSE;
        // Arms buy field density, so a one-armed hall reads as barely holding on.
        // Starved of residue the field keeps its colour but stutters, and loses its grip on the occupant.
        boolean starving = held && hall.isOccupied() && hall.isStarving();
        float stutter = starving && Mth.sin(time * 1.9f) * Mth.sin(time * 0.53f + 1.3f) > 0.35f ? 0.25f
                : starving ? 0.8f : 1.0f;
        float intensity = stutter * ((held ? 0.55f : 0.24f)
                + (held ? 0.45f : 0.10f) * hall.armCount() / ContainmentHallStructure.MAX_ARMS);
        boolean grip = held && !starving;

        // The void goes down first: everything else is meant to be seen against it.
        VoidWindow.render(poses, buffers,
                VOID_INSET, CELL_BOTTOM, VOID_INSET,
                1.0f - VOID_INSET, CELL_TOP, 1.0f - VOID_INSET);

        // A held Veiled is the wisp from the real world and its true form from the mirror. An
        // empty cell holds only the void.
        boolean mirror = ClientPhaseState.isActive();
        boolean wisp = hall.isOccupied() && !mirror;

        double distance = QuantumRenderTypes.cameraDistance(poses);
        VertexConsumer lines = buffers.getBuffer(QuantumRenderTypes.fieldEdge(distance));
        renderCage(poses, lines, time, tint, intensity, held);
        forEachTank(hall, poses, (slot) ->
                FieldOrb.renderLines(poses, lines, time + slot * 37.0f, tint, TANK_RADIUS, intensity));

        // Every write to `lines` has to happen before this: BufferSource only keeps one buffer
        // building, so fetching another render type leaves the earlier consumer dead.
        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        forEachTank(hall, poses, (slot) ->
                FieldOrb.renderCore(poses, glow, tint, TANK_RADIUS, intensity));

        // Fetches its own edge and pane buffers, so it goes after every write to lines and glow.
        if (wisp) renderSoul(poses, buffers, time, grip);

        if (hall.isOccupied() && mirror) renderOccupant(hall, poses, buffers, time, packedLight, grip);

        // Last, for the same reason: this fetches buffers of its own. The beam marks a hall that is
        // holding one, so an empty hall has none.
        if (mirror && hall.isOccupied()) {
            renderMirrorBeam(poses, buffers, partialTick, hall.getLevel() == null
                    ? 0L : hall.getLevel().getGameTime(), grip);
        }
    }

    /**
     * The held Veiled as the mirror sees it: the whole creature standing in the void, turned to
     * face the viewer. A slack field lets it press against the glass.
     */
    private void renderOccupant(ContainmentHallBlockEntity hall, PoseStack poses, MultiBufferSource buffers,
                                float time, int packedLight, boolean held) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Vec3 cell = Vec3.atBottomCenterOf(hall.getBlockPos());
        float yaw = (float) Math.toDegrees(Math.atan2(camera.z - cell.z, camera.x - cell.x)) - 90.0f;
        float lurch = held ? 0.0f : Mth.sin(time * 0.5f) * 0.06f;

        poses.pushPose();
        poses.translate(0.5f + lurch, CELL_BOTTOM, 0.5f);
        poses.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        poses.scale(-OCCUPANT_SCALE, -OCCUPANT_SCALE, OCCUPANT_SCALE);
        poses.translate(0.0f, -1.501f, 0.0f);
        occupant.pose(time, 0.0f, 0.0f, 1.0f);
        occupant.renderToBuffer(poses, buffers.getBuffer(RenderTypes.entityCutout(OCCUPANT_TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        occupant.renderToBuffer(poses, buffers.getBuffer(QuantumRenderTypes.glow(OCCUPANT_GLOW)),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        VeiledFace.render(occupant, poses, buffers, time, 1.0f);
        poses.popPose();
    }

    /**
     * A gold beam standing over the hall, drawn only for a phased viewer.
     *
     * <p>From the mirror side the hall is the thing holding one of its own, so it should be findable
     * from across the landscape — and being invisible in the real world keeps that a mirror-only
     * tell rather than a beacon every passing player can see.
     */
    private static void renderMirrorBeam(PoseStack poses, MultiBufferSource buffers,
                                         float partialTick, long gameTime, boolean held) {
        if (!(buffers instanceof SubmitBuffers submit)) return;
        BeaconRenderer.submitBeaconBeam(poses, submit.collector(), BeaconRenderer.BEAM_LOCATION,
                1.0f, Math.floorMod(gameTime, 40) + partialTick,
                // Starts on the cap's top face and runs to the render ceiling.
                ContainmentHallStructure.CAP_OFFSET + 1, BeaconRenderer.MAX_RENDER_Y,
                held ? BEAM_GOLD : BEAM_GOLD_SLACK,
                held ? 0.2f : 0.12f, held ? 0.25f : 0.15f);
    }

    /**
     * Runs {@code draw} at each claimed tank, already translated onto it.
     *
     * <p>Tanks glow whether or not they hold anything: here they are anchors for the field rather
     * than crafting stations, so the sphere is the arm doing its job, not an item being worked.
     */
    private static void forEachTank(ContainmentHallBlockEntity hall, PoseStack poses,
                                    IntConsumer draw) {
        for (int slot = 0; slot < ContainmentHallStructure.MAX_ARMS; slot++) {
            if ((hall.getArmMask() & 1 << slot) == 0) continue;
            Direction arm = ContainmentHallStructure.armDirection(slot);
            poses.pushPose();
            poses.translate(0.5 + arm.getStepX() * ARM_OUTER, TANK_Y, 0.5 + arm.getStepZ() * ARM_OUTER);
            draw.accept(slot);
            poses.popPose();
        }
    }

    /** Rings across the floor and ceiling of the cell, joined by four chasing corner bars. */
    private static void renderCage(PoseStack poses, VertexConsumer lines, float time,
                                   TesseractShell.Tint tint, float intensity, boolean held) {
        poses.pushPose();
        poses.translate(0.5, 0.0, 0.5);
        PoseStack.Pose pose = poses.last();

        float spin = time * (held ? 0.6f : 0.18f);
        ring(lines, pose, CELL_BOTTOM, 0.40f, spin, tint, 0.85f * intensity);
        ring(lines, pose, CELL_TOP, 0.40f, -spin, tint, 0.85f * intensity);

        for (int corner = 0; corner < 4; corner++) {
            float x = (corner == 0 || corner == 3) ? -BAR_OFFSET : BAR_OFFSET;
            float z = (corner < 2) ? -BAR_OFFSET : BAR_OFFSET;
            bar(lines, pose, x, z, time, corner, tint, intensity, held);
        }
        poses.popPose();
    }

    private static void ring(VertexConsumer lines, PoseStack.Pose pose, float y, float radius,
                             float phase, TesseractShell.Tint tint, float alpha) {
        float fromX = radius;
        float fromZ = 0.0f;
        for (int step = 1; step <= RING_SEGMENTS; step++) {
            float angle = step * Mth.TWO_PI / RING_SEGMENTS;
            float toX = radius * Mth.cos(angle);
            float toZ = radius * Mth.sin(angle);
            float chase = 0.30f + 0.70f * Math.max(0.0f, Mth.sin(angle * 2.0f - phase * 0.1f));
            FieldOrb.segment(lines, pose, fromX, y, fromZ, toX, y, toZ,
                    tint.r(), tint.g(), tint.b(), alpha * chase);
            fromX = toX;
            fromZ = toZ;
        }
    }

    /** A pulse of light climbing the corner, so the cell looks like it is being actively held. */
    private static void bar(VertexConsumer lines, PoseStack.Pose pose, float x, float z, float time,
                            int corner, TesseractShell.Tint tint, float intensity, boolean held) {
        float height = CELL_TOP - CELL_BOTTOM;
        float head = held
                ? Mth.frac(time * 0.006f + corner * 0.25f)
                : Mth.frac(time * 0.0015f + corner * 0.5f);
        for (int step = 0; step < BAR_STEPS; step++) {
            float from = (float) step / BAR_STEPS;
            float to = (float) (step + 1) / BAR_STEPS;
            float gap = Math.abs(Mth.frac((from + to) * 0.5f - head + 0.5f) - 0.5f) * 2.0f;
            float glow = 0.18f + 0.82f * (1.0f - gap) * (1.0f - gap);
            FieldOrb.segment(lines, pose,
                    x, CELL_BOTTOM + height * from, z,
                    x, CELL_BOTTOM + height * to, z,
                    tint.r(), tint.g(), tint.b(), intensity * glow * 0.8f);
        }
    }

    /**
     * The held Veiled as the real world sees it: a pale folding tesseract soul, the same shape
     * every other-realm creature takes when seen across the membrane. Held, it turns slowly in the
     * middle of the cell; loose, it lurches around it.
     */
    private static void renderSoul(PoseStack poses, MultiBufferSource buffers, float time, boolean held) {
        poses.pushPose();
        wispTransform(poses, time, held);
        poses.scale(SOUL_SCALE, SOUL_SCALE, SOUL_SCALE);
        poses.mulPose(Axis.YP.rotationDegrees(time * (held ? 0.35f : 1.4f)));
        poses.mulPose(Axis.XP.rotationDegrees(12.0f));
        RenderType edges = TesseractShell.edgeTypeFor(poses);
        TesseractShell.render(poses, buffers, time * (held ? 1.0f : 2.4f), edges, TesseractShell.Tint.PALE, SOUL_ALPHA);
        poses.popPose();
    }

    private static void wispTransform(PoseStack poses, float time, boolean held) {
        float drift = held ? 0.06f : 0.22f;
        float rate = held ? 0.018f : 0.075f;
        poses.translate(
                0.5 + Mth.sin(time * rate) * drift,
                CELL_MID + Mth.sin(time * rate * 1.37f + 1.1f) * (held ? 0.10f : 0.55f),
                0.5 + Mth.cos(time * rate * 0.83f) * drift);
        poses.mulPose(Axis.YP.rotationDegrees(time * (held ? 0.5f : 1.8f)));
    }

    @Override
    public AABB getRenderBoundingBox(ContainmentHallBlockEntity hall) {
        return new AABB(hall.getBlockPos().offset(-4, 0, -4).getCenter(),
                hall.getBlockPos().offset(5, ContainmentHallStructure.CAP_OFFSET + 1, 5).getCenter());
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** The mirror beam runs to the render ceiling, so it has to survive the hall leaving frame. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
