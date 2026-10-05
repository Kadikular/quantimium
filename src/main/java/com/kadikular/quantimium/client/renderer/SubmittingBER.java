package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A block-entity renderer that draws the way renderers did before 1.21.9: from the block entity itself,
 * into buffers. Its render state carries the block entity and the partial tick over to the submit
 * phase, where {@link #render} draws into {@link SubmitBuffers}.
 *
 * <p>Both phases run on the render thread in the same frame, so reading the block entity at submit
 * time sees what extraction would have copied.
 */
public abstract class SubmittingBER<T extends BlockEntity> implements BlockEntityRenderer<T, SubmittingBER.State<T>> {

    public static class State<T extends BlockEntity> extends BlockEntityRenderState {
        @Nullable T blockEntity;
        float partialTick;
    }

    /** Draws {@code blockEntity} as the pre-1.21.9 {@code render} did. */
    protected abstract void render(T blockEntity, float partialTick, PoseStack poses, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay);

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T blockEntity, State<T> state, float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.blockEntity == null) return;
        SubmitBuffers buffers = new SubmitBuffers(collector);
        render(state.blockEntity, state.partialTick, poses, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        buffers.flush();
        // Don't hold the block entity past the frame.
        state.blockEntity = null;
    }
}
