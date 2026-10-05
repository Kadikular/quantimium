package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.MirrorVineBlock;
import com.kadikular.quantimium.client.MirrorFloraClientCache;
import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Draws synchronized flora records in camera-relative space. Chunk {@code cutout} shaders read
 * leftover {@code CHUNK_OFFSET} uniforms, so this uses the item/entity cutout sheet instead.
 */
public final class MirrorFloraRenderer {

    private static final double MAX_DISTANCE_SQUARED = 80.0 * 80.0;

    private MirrorFloraRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera) {
        render(poseStack, buffers, camera, MirrorFloraClientCache.entries());
    }

    /** Any set of flora records — the phased snapshot, or a rift's bleed into the real world. */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, Iterable<MirrorFloraData.Entry> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !entries.iterator().hasNext()) return;

        for (MirrorFloraData.Entry entry : entries) {
            BlockPos pos = entry.pos();
            if (pos.distToCenterSqr(camera) > MAX_DISTANCE_SQUARED
                    || !minecraft.level.isLoaded(pos)) {
                continue;
            }

            if (entry.kind() == MirrorFloraData.Kind.TALL_GRASS) {
                BlockState base = ModBlocks.MIRROR_TALL_GRASS.get().defaultBlockState()
                        .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
                renderBlock(buffers, poseStack, camera, pos, base);
                renderBlock(buffers, poseStack, camera, pos.above(),
                        base.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                continue;
            }

            BlockState state = switch (entry.kind()) {
                case VINE -> vineState(entry);
                case SHORT_GRASS -> ModBlocks.MIRROR_SHORT_GRASS.get().defaultBlockState();
                case DEAD_BUSH -> ModBlocks.MIRROR_DEAD_BUSH.get().defaultBlockState();
                case HANGING_ROOTS -> ModBlocks.MIRROR_HANGING_ROOTS.get().defaultBlockState();
                case TALL_GRASS -> throw new IllegalStateException("Handled above");
            };
            renderBlock(buffers, poseStack, camera, pos, state);
        }
    }

    private static BlockState vineState(MirrorFloraData.Entry entry) {
        BlockState state = ModBlocks.MIRROR_VINE.get().defaultBlockState();
        for (Direction face : Direction.Plane.HORIZONTAL) {
            state = state.setValue(MirrorVineBlock.getFaceProperty(face), entry.hasFace(face));
        }
        return state;
    }

    private static void renderBlock(MultiBufferSource buffers,
                                    PoseStack poseStack, Vec3 camera, BlockPos pos,
                                    BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.isLoaded(pos)) return;
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        SubmitBuffers.block(buffers, poseStack, state, LevelRenderer.getLightCoords(minecraft.level, pos), OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
