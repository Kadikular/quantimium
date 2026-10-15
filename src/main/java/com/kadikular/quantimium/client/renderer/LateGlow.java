package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
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

    /**
     * Runs {@code draw} now, into {@code buffers}, in a frame at the eye: {@code poses} is the block entity
     * renderer's, at {@code origin}, so a guide's scene, with its own camera, draws it in place too.
     */
    public static void draw(PoseStack poses, BlockPos origin, MultiBufferSource buffers, float partialTick, Draw draw) {
        Minecraft minecraft = Minecraft.getInstance();
        org.joml.Vector3f eye = new org.joml.Matrix4f(poses.last().pose()).invert().transformPosition(new org.joml.Vector3f());
        if (!Float.isFinite(eye.x) || !Float.isFinite(eye.y) || !Float.isFinite(eye.z)) return;
        Vec3 camera = Vec3.atLowerCornerOf(origin).add(eye.x, eye.y, eye.z);
        Entity viewer = minecraft.level == null ? null : minecraft.gameRenderer.getMainCamera().entity();
        Vec3 middle = viewer == null
                ? camera
                : viewer.getPosition(partialTick).add(0.0, viewer.getBbHeight() * 0.5, 0.0);
        float time = minecraft.level != null ? minecraft.level.getGameTime() + partialTick : net.minecraft.util.Util.getMillis() / 50.0f;
        poses.pushPose();
        poses.translate(eye.x, eye.y, eye.z);
        draw.draw(poses, buffers, camera, middle, time);
        poses.popPose();
    }
}
