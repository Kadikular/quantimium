package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Glow a block entity draws in world space, relative to the camera: beams and tears that reach past
 * its own block. Before 1.21.9 these waited for the mirror's flora to go down, since glow writes no
 * depth and flora drawn after it would paint over it. Features now draw custom geometry after block
 * models and before translucent terrain, which is the order that queue existed to get, so a draw
 * goes straight into the renderer's buffers.
 */
public final class LateGlow {

    /** Draws in camera space: world positions minus {@code camera}. */
    @FunctionalInterface
    public interface Draw {
        void draw(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 viewerMiddle, float time);
    }

    private LateGlow() {}

    /** Runs {@code draw} now, into {@code buffers}, with an untransformed pose at the camera. */
    public static void draw(MultiBufferSource buffers, float partialTick, Draw draw) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Entity viewer = camera.entity();
        Vec3 middle = viewer == null
                ? eye
                : viewer.getPosition(partialTick).add(0.0, viewer.getBbHeight() * 0.5, 0.0);
        draw.draw(new PoseStack(), buffers, eye, middle, minecraft.level.getGameTime() + partialTick);
    }
}
