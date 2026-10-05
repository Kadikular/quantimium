package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * The live half of the foundry: a field orb in every attunement tank, pulses running the floor
 * rails inwards, a ring of light over the well, and the product assembling above it. Everything
 * here is keyed off state the block entity already syncs, so it is safe to draw on any client.
 */
public class QuantumFoundryBER extends SubmittingBER<QuantumFoundryBlockEntity> {

    /** Just clear of the rail's raised centre bar, which tops out at 4/16. */
    private static final float RAIL_Y = 0.28f;
    /** Just clear of the plinth's top face. */
    private static final float WELL_Y = 1.02f;
    /** Centre of the tank's glass cell, one block above the pillar. */
    private static final float TANK_Y = 1.44f;

    private static final float ARM_INNER = 1.5f;
    private static final float ARM_OUTER = 3.0f;
    private static final int RAIL_PULSES = 3;
    private static final int WELL_MOTES = 7;

    public QuantumFoundryBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuantumFoundryBlockEntity foundry, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!foundry.isFormed()) return;

        float time = (foundry.getLevel() == null ? 0.0f : foundry.getLevel().getGameTime()) + partialTick;
        FluxBand flux = band(foundry.getFluxBandOrdinal());
        FluxBand anomaly = band(foundry.getAnomalyBandOrdinal());
        TesseractShell.Tint tint = fieldTint(flux, anomaly);
        boolean working = foundry.isWorking();
        float intensity = working ? 1.0f : 0.55f;

        renderItems(foundry, poses, buffers, packedOverlay, time, working);

        // One pass per render type, and never a write to a consumer from an earlier pass:
        // BufferSource closes the shared builder as soon as a different type is requested, which is
        // what the Not building! crash was. The floor geometry keeps its depth writes so terrain
        // occludes it properly; the tank field drops them so it cannot hide the item it surrounds.
        double distance = QuantumRenderTypes.cameraDistance(poses);

        VertexConsumer floorLines = buffers.getBuffer(QuantumRenderTypes.holoEdge(distance));
        renderWell(foundry, poses, floorLines, time, tint, intensity, working);
        for (int slot = 0; slot < 4; slot++) {
            if (!foundry.hasPillar(slot)) continue;
            renderRail(poses, floorLines, time, tint, intensity, working, arm(foundry, slot), slot);
        }

        VertexConsumer fieldLines = buffers.getBuffer(QuantumRenderTypes.fieldEdge(distance));
        for (int slot = 0; slot < 4; slot++) {
            if (!foundry.hasPillar(slot)) continue;
            renderTankLines(poses, fieldLines, time, tint, intensity, arm(foundry, slot), slot, flux);
        }

        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        for (int slot = 0; slot < 4; slot++) {
            if (!foundry.hasPillar(slot)) continue;
            renderTankCore(poses, glow, tint, intensity, arm(foundry, slot), slot, flux);
        }

        renderProduct(foundry, poses, buffers, packedOverlay, time);
    }

    private static Direction arm(QuantumFoundryBlockEntity foundry, int slot) {
        return QuantumFoundryStructure.pillarDirection(foundry.getBlockState(), slot);
    }

    /** Ingredients hover inside their tanks; a non-tesseract product hovers over the well. */
    private static void renderItems(QuantumFoundryBlockEntity foundry, PoseStack poses,
                                    MultiBufferSource buffers, int overlay, float time,
                                    boolean working) {
        for (int slot = 0; slot < 4; slot++) {
            if (!foundry.hasPillar(slot)) continue;
            ItemStack ingredient = foundry.getInventory().getStackInSlot(slot);
            if (ingredient.isEmpty()) continue;

            Direction arm = QuantumFoundryStructure.pillarDirection(foundry.getBlockState(), slot);
            poses.pushPose();
            poses.translate(0.5 + arm.getStepX() * ARM_OUTER,
                    TANK_Y + Mth.sin(time * 0.05f + slot * 1.6f) * 0.035f,
                    0.5 + arm.getStepZ() * ARM_OUTER);
            poses.mulPose(Axis.YP.rotationDegrees(time * (working ? 1.8f : 0.7f) + slot * 90.0f));
            poses.scale(0.30f, 0.30f, 0.30f);
            SubmitBuffers.item(buffers, poses, ingredient, ItemDisplayContext.NONE, LightCoordsUtil.FULL_BRIGHT, overlay, foundry.getLevel(), slot);
            poses.popPose();
        }
    }

    private static void renderWell(QuantumFoundryBlockEntity foundry, PoseStack poses,
                                   VertexConsumer lines, float time, TesseractShell.Tint tint,
                                   float intensity, boolean working) {
        poses.pushPose();
        poses.translate(0.5, WELL_Y, 0.5);
        PoseStack.Pose pose = poses.last();

        circle(lines, pose, 0.40f, time * 0.05f, tint, 0.55f * intensity);
        circle(lines, pose, 0.26f, -time * 0.08f, tint, 0.70f * intensity);
        for (int spoke = 0; spoke < 4; spoke++) {
            float angle = spoke * Mth.HALF_PI + time * 0.02f;
            FieldOrb.segment(lines, pose,
                    0.26f * Mth.cos(angle), 0.0f, 0.26f * Mth.sin(angle),
                    0.40f * Mth.cos(angle), 0.0f, 0.40f * Mth.sin(angle),
                    tint.r(), tint.g(), tint.b(), 0.45f * intensity);
        }

        if (working) {
            float progress = foundry.getDuration() <= 0 ? 0.0f
                    : Mth.clamp((float) foundry.getProgress() / foundry.getDuration(), 0.0f, 1.0f);
            for (int mote = 0; mote < WELL_MOTES; mote++) {
                float climb = Mth.frac(time * 0.020f + mote / (float) WELL_MOTES);
                float angle = mote * 2.2f + time * 0.03f;
                float radius = Mth.lerp(climb, 0.34f, 0.06f);
                float from = climb * 0.42f;
                FieldOrb.segment(lines, pose,
                        radius * Mth.cos(angle), from, radius * Mth.sin(angle),
                        radius * Mth.cos(angle), from + 0.07f, radius * Mth.sin(angle),
                        1.0f, 1.0f, 1.0f, (0.30f + 0.55f * progress) * (1.0f - climb));
            }
        }
        poses.popPose();
    }

    /** Dashes running from the pillar to the plinth, so the rail reads as feeding the well. */
    private static void renderRail(PoseStack poses, VertexConsumer lines, float time,
                                   TesseractShell.Tint tint, float intensity, boolean working,
                                   Direction arm, int slot) {
        poses.pushPose();
        poses.translate(0.5, RAIL_Y, 0.5);
        PoseStack.Pose pose = poses.last();
        float stepX = arm.getStepX();
        float stepZ = arm.getStepZ();

        FieldOrb.segment(lines, pose, stepX * ARM_INNER, 0.0f, stepZ * ARM_INNER,
                stepX * ARM_OUTER, 0.0f, stepZ * ARM_OUTER,
                tint.r(), tint.g(), tint.b(), 0.22f * intensity);

        float speed = working ? 0.030f : 0.010f;
        for (int pulse = 0; pulse < RAIL_PULSES; pulse++) {
            float travel = Mth.frac(time * speed + pulse / (float) RAIL_PULSES + slot * 0.13f);
            float head = Mth.lerp(travel, ARM_OUTER, ARM_INNER);
            float tail = Math.min(ARM_OUTER, head + 0.34f);
            float fade = Mth.sin(travel * Mth.PI);
            FieldOrb.segment(lines, pose, stepX * tail, 0.0f, stepZ * tail,
                    stepX * head, 0.0f, stepZ * head,
                    Math.min(1.0f, tint.r() + 0.3f), Math.min(1.0f, tint.g() + 0.3f),
                    Math.min(1.0f, tint.b() + 0.3f), (0.35f + 0.60f * fade) * intensity);
        }
        poses.popPose();
    }

    private static void renderTankLines(PoseStack poses, VertexConsumer lines, float time,
                                        TesseractShell.Tint tint, float intensity,
                                        Direction arm, int slot, FluxBand flux) {
        // A tank shows how far the local field is drawn out, so a stronger band fills more glass.
        float radius = 0.15f + 0.028f * flux.ordinal();
        poses.pushPose();
        poses.translate(0.5 + arm.getStepX() * ARM_OUTER, TANK_Y, 0.5 + arm.getStepZ() * ARM_OUTER);
        FieldOrb.renderLines(poses, lines, time + slot * 37.0f, tint, radius, intensity);
        poses.popPose();
    }

    private static void renderTankCore(PoseStack poses, VertexConsumer panes,
                                       TesseractShell.Tint tint, float intensity,
                                       Direction arm, int slot, FluxBand flux) {
        float radius = 0.15f + 0.028f * flux.ordinal();
        poses.pushPose();
        poses.translate(0.5 + arm.getStepX() * ARM_OUTER, TANK_Y, 0.5 + arm.getStepZ() * ARM_OUTER);
        FieldOrb.renderCore(poses, panes, tint, radius, intensity);
        poses.popPose();
    }

    private static void renderProduct(QuantumFoundryBlockEntity foundry, PoseStack poses,
                                      MultiBufferSource buffers, int overlay, float time) {
        ItemStack result = foundry.getRenderResult();
        if (result.isEmpty()) {
            result = foundry.getInventory().getStackInSlot(QuantumFoundryBlockEntity.OUTPUT_SLOT);
        }
        if (result.isEmpty()) return;

        float growth = foundry.getDuration() <= 0 ? 1.0f
                : Mth.clamp((float) foundry.getProgress() / foundry.getDuration(), 0.12f, 1.0f);
        poses.pushPose();
        poses.translate(0.5, 1.42 + Mth.sin(time * 0.05f) * 0.04f, 0.5);
        poses.scale(growth, growth, growth);
        if (result.is(ModItems.TESSERACT.get())) {
            poses.mulPose(Axis.YP.rotationDegrees(time * 0.6f));
            TesseractShell.render(poses, buffers, time, TesseractShell.Tint.FLUX);
        } else {
            poses.mulPose(Axis.YP.rotationDegrees(time * 1.2f));
            poses.scale(0.55f, 0.55f, 0.55f);
            SubmitBuffers.item(buffers, poses, result, ItemDisplayContext.NONE, LightCoordsUtil.FULL_BRIGHT, overlay, foundry.getLevel(), 0);
        }
        poses.popPose();
    }

    private static void circle(VertexConsumer lines, PoseStack.Pose pose, float radius, float phase,
                               TesseractShell.Tint tint, float alpha) {
        int segments = 28;
        float fromX = radius;
        float fromZ = 0.0f;
        for (int step = 1; step <= segments; step++) {
            float angle = step * Mth.TWO_PI / segments;
            float toX = radius * Mth.cos(angle);
            float toZ = radius * Mth.sin(angle);
            float chase = 0.35f + 0.65f * Math.max(0.0f, Mth.sin(angle * 2.0f - phase * Mth.TWO_PI));
            FieldOrb.segment(lines, pose, fromX, 0.0f, fromZ, toX, 0.0f, toZ,
                    tint.r(), tint.g(), tint.b(), alpha * chase);
            fromX = toX;
            fromZ = toZ;
        }
    }

    private static FluxBand band(int ordinal) {
        FluxBand[] bands = FluxBand.values();
        return bands[Math.clamp(ordinal, 0, bands.length - 1)];
    }

    private static TesseractShell.Tint fieldTint(FluxBand flux, FluxBand anomaly) {
        if (anomaly.ordinal() > flux.ordinal()) return new TesseractShell.Tint(1.0f, 0.28f, 0.22f);
        // The flux ramp from the style guide, climbing with the band: a muted blue, azure, its glow
        // core, then violet as the field gets hot enough for the two forces to meet, and a pale
        // violet-white at Singularity.
        return switch (flux) {
            case LOW -> new TesseractShell.Tint(0.38f, 0.55f, 0.75f);
            case MEDIUM -> TesseractShell.Tint.FLUX;
            case HIGH -> new TesseractShell.Tint(0.50f, 0.70f, 1.0f);
            case CRITICAL -> new TesseractShell.Tint(0.66f, 0.42f, 1.0f);
            case SINGULARITY -> new TesseractShell.Tint(0.82f, 0.73f, 0.99f);
        };
    }

    @Override
    public AABB getRenderBoundingBox(QuantumFoundryBlockEntity foundry) {
        return new AABB(foundry.getBlockPos().offset(-4, 0, -4).getCenter(),
                foundry.getBlockPos().offset(5, 3, 5).getCenter());
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
