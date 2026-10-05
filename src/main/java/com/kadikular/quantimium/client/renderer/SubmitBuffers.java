package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A {@link MultiBufferSource} over a {@link SubmitNodeCollector}, so drawing code written against
 * buffers (the shells, orbs, ribbons and beams) runs unchanged in the submit phase that replaced
 * immediate rendering in 1.21.9.
 *
 * <p>Vertices go to a recorder per render type as they are written, already posed; {@link #flush}
 * hands each recording to the collector as custom geometry, replayed as is when the frame renders.
 * Blocks and items have no buffer path any more, so {@link #block} and {@link #item} submit them
 * straight to the collector instead.
 */
public final class SubmitBuffers implements MultiBufferSource {

    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
    /** The recordings are already in camera space; the submission itself adds nothing. */
    private static final PoseStack IDENTITY = new PoseStack();

    private final SubmitNodeCollector collector;
    private final Map<RenderType, Recording> recordings = new LinkedHashMap<>();

    public SubmitBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return QuantumRenderTypes.widthed(renderType, recordings.computeIfAbsent(renderType, type -> new Recording()));
    }

    /**
     * Submits everything recorded so far and starts afresh: one custom geometry per render type, each
     * on its own order in the sequence the types were first asked for. Custom geometry of one order is
     * batched by render type in no fixed sequence, and the drawing code relies on its sequence: a beam's
     * halo, which writes no depth, goes down before the core on the same strip, which does.
     */
    public void flush() {
        int order = 0;
        for (Map.Entry<RenderType, Recording> entry : recordings.entrySet()) {
            Recording recording = entry.getValue();
            if (recording.count == 0) continue;
            collector.order(order++).submitCustomGeometry(IDENTITY, entry.getKey(), (pose, buffer) -> recording.replay(buffer));
        }
        recordings.clear();
    }

    /** {@code state}'s block model at the pose's origin, as {@code renderSingleBlock} drew it. */
    public static void block(MultiBufferSource buffers, PoseStack poses, BlockState state, int light, int overlay) {
        if (!(buffers instanceof SubmitBuffers submit)) return;
        BlockModelRenderState model = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(model, state, BLOCK_DISPLAY_CONTEXT);
        model.submitMultiLayer(poses, submit.collector, light, overlay, 0);
    }

    /** {@code stack} at the pose's origin, as {@code ItemRenderer#renderStatic} drew it. */
    public static void item(MultiBufferSource buffers, PoseStack poses, ItemStack stack, ItemDisplayContext context,
                            int light, int overlay, @Nullable Level level, int seed) {
        if (!(buffers instanceof SubmitBuffers submit) || stack.isEmpty()) return;
        ItemStackRenderState item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, stack, context, level, null, seed);
        item.submit(poses, submit.collector, light, overlay, 0);
    }

    /** Vertices as written: every attribute any render type here uses, and which of them were set. */
    private static final class Recording implements VertexConsumer {
        private static final int POSITION = 1, COLOR = 2, UV = 4, UV1 = 8, UV2 = 16, NORMAL = 32, WIDTH = 64;
        /** x, y, z, u, v, nx, ny, nz, width. */
        private static final int FLOATS = 9;
        /** set, color, uv1, uv2. */
        private static final int INTS = 4;

        private float[] floats = new float[FLOATS * 64];
        private int[] ints = new int[INTS * 64];
        private int count;

        private int f(int field) {
            return (count - 1) * FLOATS + field;
        }

        private int i(int field) {
            return (count - 1) * INTS + field;
        }

        private void set(int flag) {
            ints[i(0)] |= flag;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (count * FLOATS >= floats.length) {
                floats = Arrays.copyOf(floats, floats.length * 2);
                ints = Arrays.copyOf(ints, ints.length * 2);
            }
            count++;
            floats[f(0)] = x;
            floats[f(1)] = y;
            floats[f(2)] = z;
            ints[i(0)] = POSITION;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            ints[i(1)] = argb;
            set(COLOR);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            floats[f(3)] = u;
            floats[f(4)] = v;
            set(UV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            ints[i(2)] = u & 0xFFFF | v << 16;
            set(UV1);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            ints[i(3)] = u & 0xFFFF | v << 16;
            set(UV2);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            floats[f(5)] = x;
            floats[f(6)] = y;
            floats[f(7)] = z;
            set(NORMAL);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            floats[f(8)] = width;
            set(WIDTH);
            return this;
        }

        void replay(VertexConsumer out) {
            for (int v = 0; v < count; v++) {
                int fb = v * FLOATS, ib = v * INTS, set = ints[ib];
                out.addVertex(floats[fb], floats[fb + 1], floats[fb + 2]);
                if ((set & COLOR) != 0) out.setColor(ints[ib + 1]);
                if ((set & UV) != 0) out.setUv(floats[fb + 3], floats[fb + 4]);
                if ((set & UV1) != 0) out.setUv1(ints[ib + 2] & 0xFFFF, ints[ib + 2] >>> 16);
                if ((set & UV2) != 0) out.setUv2(ints[ib + 3] & 0xFFFF, ints[ib + 3] >>> 16);
                if ((set & NORMAL) != 0) out.setNormal(floats[fb + 5], floats[fb + 6], floats[fb + 7]);
                if ((set & WIDTH) != 0) out.setLineWidth(floats[fb + 8]);
            }
        }
    }
}
