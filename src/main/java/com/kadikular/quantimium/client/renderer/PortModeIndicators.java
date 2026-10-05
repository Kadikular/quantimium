package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Flow arrows on each face of a {@code SideConfigurable} machine. Items sit above and below in
 * orange, fluids left and right in blue. One arrow points inwards for input, one outwards for
 * output, and both modes draw the two side by side. Disabled media (or faces the profile has
 * turned off) draw nothing.
 */
public final class PortModeIndicators {

    /**
     * Distances from the centre of the face. Each medium keeps to its own band and never strays
     * further sideways than the other band starts, so the two never meet in a corner of the face.
     */
    private static final float ALONG_INNER = 0.125f;
    private static final float ALONG_OUTER = 0.1875f;
    private static final float SINGLE_HALF_WIDTH = 0.035f;
    private static final float PAIR_OFFSET = 0.032f;
    private static final float PAIR_HALF_WIDTH = 0.035f;

    /** Barely clear of the block face so the overlay does not z-fight the chassis. */
    private static final float OUTSIDE = 0.003f;

    private static final float ITEM_R = 1.00f;
    private static final float ITEM_G = 0.55f;
    private static final float ITEM_B = 0.15f;
    private static final float FLUID_R = 0.30f;
    private static final float FLUID_G = 0.62f;
    private static final float FLUID_B = 1.00f;
    private static final float ALPHA = 0.95f;

    private PortModeIndicators() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, List<SideConfig> configs) {
        VertexConsumer buffer = buffers.getBuffer(QuantumRenderTypes.HOLO_PANE);
        Matrix4f pose = poseStack.last().pose();
        for (Direction side : Direction.values()) {
            SideConfig config = configs.get(side.get3DDataValue());
            arrows(buffer, pose, side, config.itemMode(), true, ITEM_R, ITEM_G, ITEM_B);
            arrows(buffer, pose, side, config.fluidMode(), false, FLUID_R, FLUID_G, FLUID_B);
        }
    }

    /**
     * @param upright true to lay the pair out along the face's vertical axis, false for its horizontal
     *                axis, which is what separates items from fluids
     */
    private static void arrows(VertexConsumer buffer, Matrix4f pose, Direction side, SideMode mode,
                               boolean upright, float r, float g, float b) {
        if (mode == SideMode.DISABLED) return;
        for (int sign = -1; sign <= 1; sign += 2) {
            switch (mode) {
                case INPUT -> arrow(buffer, pose, side, upright, sign,
                        ALONG_OUTER, ALONG_INNER, 0.0f, SINGLE_HALF_WIDTH, r, g, b);
                case OUTPUT -> arrow(buffer, pose, side, upright, sign,
                        ALONG_INNER, ALONG_OUTER, 0.0f, SINGLE_HALF_WIDTH, r, g, b);
                default -> {
                    arrow(buffer, pose, side, upright, sign,
                            ALONG_OUTER, ALONG_INNER, -PAIR_OFFSET, PAIR_HALF_WIDTH, r, g, b);
                    arrow(buffer, pose, side, upright, sign,
                            ALONG_INNER, ALONG_OUTER, PAIR_OFFSET, PAIR_HALF_WIDTH, r, g, b);
                }
            }
        }
    }

    private static void arrow(VertexConsumer buffer, Matrix4f pose, Direction side, boolean upright,
                              int sign, float baseDistance, float tipDistance, float lateral,
                              float halfWidth, float r, float g, float b) {
        float base = sign * baseDistance;
        float tip = sign * tipDistance;
        vertex(buffer, pose, side, upright, base, lateral - halfWidth, r, g, b);
        vertex(buffer, pose, side, upright, base, lateral + halfWidth, r, g, b);
        // The pane pass draws quads, so the tip is emitted twice to collapse that edge to a point.
        vertex(buffer, pose, side, upright, tip, lateral, r, g, b);
        vertex(buffer, pose, side, upright, tip, lateral, r, g, b);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Direction side, boolean upright,
                               float along, float across, float r, float g, float b) {
        float horizontal = upright ? across : along;
        float vertical = upright ? along : across;
        float x;
        float y;
        float z;
        switch (side) {
            case NORTH -> {
                x = 0.5f + horizontal;
                y = 0.5f + vertical;
                z = -OUTSIDE;
            }
            case SOUTH -> {
                x = 0.5f + horizontal;
                y = 0.5f + vertical;
                z = 1.0f + OUTSIDE;
            }
            case WEST -> {
                x = -OUTSIDE;
                y = 0.5f + vertical;
                z = 0.5f + horizontal;
            }
            case EAST -> {
                x = 1.0f + OUTSIDE;
                y = 0.5f + vertical;
                z = 0.5f + horizontal;
            }
            case UP -> {
                x = 0.5f + horizontal;
                y = 1.0f + OUTSIDE;
                z = 0.5f + vertical;
            }
            // Underside pairs run north-south for items and east-west for fluids.
            default -> {
                x = 0.5f + horizontal;
                y = -OUTSIDE;
                z = 0.5f + vertical;
            }
        }
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, ALPHA);
    }
}
