// Path: src/main/java/com/kadikular/quantimium/client/renderer/QuantumSimulatorBER.java
package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.LightCoordsUtil;
import com.kadikular.quantimium.block.QuantumSimulatorBlock;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity.ContainedVisualization;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

public class QuantumSimulatorBER extends SubmittingBER<QuantumSimulatorBlockEntity> {

    /** Small enough that the machine's corners stay inside the shell as both turn. */
    private static final float MACHINE_SCALE = 0.36f;

    /** Just clear of the chassis body (1/16), below the energy band (5/16) and above the plinth. */
    private static final float MARKER_DEPTH = 1.0f / 16.0f - 0.006f;
    private static final float MARKER_BOTTOM = 2.6f / 16.0f;

    /** Enough of a lean to show the shell's depth, shallow enough that it cannot reach the simulator below. */
    private static final float SHELL_TILT_DEGREES = 14.0f;
    private static final float NESTED_SIMULATOR_SCALE = 0.22f;
    private static final float NESTED_MACHINE_SCALE = 0.16f;
    private static final float NESTED_FIELD_SCALE = 0.48f;
    private static final float NESTED_SIMULATOR_Y = -0.16f;
    private static final float NESTED_MACHINE_Y = 0.17f;

    public QuantumSimulatorBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuantumSimulatorBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (WrenchView.isActive()) {
            PortModeIndicators.render(poseStack, bufferSource, blockEntity.sideConfigsView());
        }

        // Drawn whether or not the field is up: the side configuration screen names faces from it.
        FrontMarker.render(poseStack, bufferSource,
                blockEntity.getBlockState().getValue(QuantumSimulatorBlock.FACING),
                MARKER_DEPTH, MARKER_BOTTOM);

        if (!blockEntity.isEngaged()) return;

        BlockState containedState = blockEntity.getContainedBlockState();
        if (containedState == null || containedState.isAir()) return;

        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;
        float time = gameTime + partialTick;

        poseStack.pushPose();

        // The machine and the shell around it share this pivot, so both ride the same floating bob.
        double yOffset = 1.50 + Math.sin(time * 0.08) * 0.04;
        poseStack.translate(0.5, yOffset, 0.5);

        ContainedVisualization visualization = blockEntity.getContainedVisualization();
        if (visualization != null && visualization.nested() != null) {
            renderBlock(visualization.state(), poseStack, bufferSource, packedOverlay,
                    time * 1.6f, NESTED_SIMULATOR_SCALE, NESTED_SIMULATOR_Y);
            renderBlock(visualization.nested().state(), poseStack, bufferSource, packedOverlay,
                    -time * 2.2f, NESTED_MACHINE_SCALE, NESTED_MACHINE_Y);
        } else {
            renderBlock(containedState, poseStack, bufferSource, packedOverlay,
                    time * 2.2f, MACHINE_SCALE, 0.0f);
        }
        // Block models draw before custom geometry, so the machine sits behind its shell.

        if (visualization != null && visualization.nested() != null) {
            poseStack.pushPose();
            poseStack.translate(0.0f, NESTED_MACHINE_Y, 0.0f);
            poseStack.scale(NESTED_FIELD_SCALE, NESTED_FIELD_SCALE, NESTED_FIELD_SCALE);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.9f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-SHELL_TILT_DEGREES));
            TesseractShell.render(poseStack, bufferSource, time * 1.35f);
            poseStack.popPose();
        }

        // Drifting the shell the other way, off-axis, keeps it from reading as a box glued to the machine.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.55f));
        poseStack.mulPose(Axis.XP.rotationDegrees(SHELL_TILT_DEGREES));
        TesseractShell.render(poseStack, bufferSource, time);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderBlock(
            BlockState state,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedOverlay,
            float rotation,
            float scale,
            float yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0f, yOffset, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5, -0.5, -0.5);
        SubmitBuffers.block(bufferSource, poseStack, state, LightCoordsUtil.FULL_BRIGHT, packedOverlay);
        poseStack.popPose();
    }

}