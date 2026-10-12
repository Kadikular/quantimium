package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.LaserRelayBlock;
import com.kadikular.quantimium.block.entity.LaserRelayBlockEntity;
import com.kadikular.quantimium.item.LensItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** A Laser Relay's light, and each emitter's tip in the colour of the lens in it. */
public class LaserRelayBER extends LaserBeamBER<LaserRelayBlockEntity> {

    public LaserRelayBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(LaserRelayBlockEntity relay, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(relay, partialTick, poses, buffers, packedLight, packedOverlay);
        PoseStack.Pose pose = poses.last();
        VertexConsumer tips = null;
        for (Direction face : Direction.values()) {
            if (!relay.getBlockState().getValue(LaserRelayBlock.OUTPUTS.get(face))) continue;
            ItemStack lens = relay.lens(face);
            if (!(lens.getItem() instanceof LensItem item)) continue;
            if (tips == null) tips = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
            tip(tips, pose, face, item.lens().colour);
        }
    }

    /** The tip on {@code face} (a 2x2x1 at the face's middle), drawn over just outside it in {@code colour}. */
    private static void tip(VertexConsumer buffer, PoseStack.Pose pose, Direction face, int colour) {
        float r = (colour >> 16 & 0xFF) / 255f;
        float g = (colour >> 8 & 0xFF) / 255f;
        float b = (colour & 0xFF) / 255f;
        float lo = 6.9f / 16;
        float hi = 9.1f / 16;
        float in = 0.95f / 16;
        float out = -0.05f / 16;
        // The box in the north tip's frame, then turned to the face: x across, y up, z into the block.
        float[][] box = {{lo, lo, out}, {hi, hi, in + 0.1f / 16}};
        float[] min = box[0];
        float[] max = box[1];
        float[][] corners = new float[8][];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? min[0] : max[0];
            float y = (i & 2) == 0 ? min[1] : max[1];
            float z = (i & 4) == 0 ? min[2] : max[2];
            corners[i] = turn(face, x, y, z);
        }
        int[][] quads = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] quad : quads) {
            for (int corner : quad) buffer.addVertex(pose, corners[corner][0], corners[corner][1], corners[corner][2]).setColor(r, g, b, 0.95f);
        }
    }

    /** A point in the north emitter's frame, turned to {@code face}'s emitter. */
    private static float[] turn(Direction face, float x, float y, float z) {
        return switch (face) {
            case NORTH -> new float[] {x, y, z};
            case SOUTH -> new float[] {1 - x, y, 1 - z};
            case WEST -> new float[] {z, y, 1 - x};
            case EAST -> new float[] {1 - z, y, x};
            case UP -> new float[] {x, 1 - z, y};
            case DOWN -> new float[] {x, z, 1 - y};
        };
    }
}
