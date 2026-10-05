package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.entity.FieldDouble;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jspecify.annotations.Nullable;

/**
 * A field double: the same hologram as a double asleep in its pod, standing out in the open. Its tint
 * follows its own health rather than its owner's, so you can see from across a field that it is being hurt.
 */
public class FieldDoubleRenderer extends EntityRenderer<FieldDouble, FieldDoubleRenderer.State> {

    public static class State extends EntityRenderState {
        @Nullable UUID owner;
        float yaw;
        float health;
        int seed;
    }

    public FieldDoubleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35f;
        shadowStrength = 0.4f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FieldDouble entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.owner = entity.owner();
        state.yaw = entity.getYRot();
        float health = entity.getHealth() / Math.max(1.0f, entity.getMaxHealth());
        // A fresh hit flashes it towards violet for a moment.
        state.health = entity.hurtTime > 0 ? Math.min(health, 0.2f) : health;
        state.seed = entity.getId();
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        SubmitBuffers buffers = new SubmitBuffers(collector);
        DoubleRenderer.render(poses, buffers, state.owner, state.yaw, state.ageInTicks, 1.0f, state.health, state.seed);
        buffers.flush();
        super.submit(state, poses, collector, camera);
    }
}
