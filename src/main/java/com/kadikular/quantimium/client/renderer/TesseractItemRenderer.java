// Path: src/main/java/com/kadikular/quantimium/client/renderer/TesseractItemRenderer.java
package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.FoldedTesseractItem;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Draws the entangled link as a small projected tesseract with the block it is bound to floating
 * inside it. Unbound links show an empty cell, so the item always reads as a piece of quantum kit
 * rather than a flat icon.
 *
 * <p>A special model renderer since 26.1. It is not told where the item is drawn, so the item
 * definition picks a {@code gui} variant for inventory slots by display context.
 */
public class TesseractItemRenderer implements SpecialModelRenderer<TesseractItemRenderer.Look> {

    /** Fills most of the item's block without the widest fold of the shell reaching past the edges. */
    private static final float SHELL_SCALE = 1.365f;
    private static final float SEMI_STABLE_SHELL_SCALE = 0.92f;
    /** The bound block has to carry a whole slot, so it is drawn larger there than in the hand. */
    private static final float INNER_SCALE_GUI = 0.70f;
    private static final float INNER_SCALE_HELD = 0.45f;
    private static final float SEMI_STABLE_INNER_SCALE_GUI = 0.47f;
    private static final float SEMI_STABLE_INNER_SCALE_HELD = 0.30f;
    private static final float SHELL_TILT_DEGREES = 12.0f;
    /** How far right of and below the slot's centre the shell sits, in GUI pixels. */
    static final float GUI_SHELL_NUDGE = 1.5f;

    /** What of the stack shows: the bound block, if any, how stable the link is, and its tint. */
    public record Look(@Nullable Block bound, boolean semiStable, TesseractShell.Tint tint) {}

    /** {@code gui} for the variant drawn in inventory slots. */
    public record Unbaked(boolean gui) implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("gui", false).forGetter(Unbaked::gui)).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<Look> bake(SpecialModelRenderer.BakingContext context) {
            return new TesseractItemRenderer(gui);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private final boolean inGui;

    private TesseractItemRenderer(boolean inGui) {
        this.inGui = inGui;
    }

    @Override
    public Look extractArgument(ItemStack stack) {
        // A folded machine turns inside its shell as a bound block does, in the chamber's violet.
        if (stack.getItem() instanceof FoldedTesseractItem) {
            Identifier id = stack.get(ModDataComponents.BOUND_BLOCK.get());
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
            return new Look(block == Blocks.AIR ? null : block, false, TesseractShell.Tint.FOLDED);
        }
        return new Look(EntangledLinks.boundBlock(stack), EntangledLinks.isSemiStable(stack), shellTint(stack));
    }

    @Override
    public void submit(@Nullable Look look, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        if (look == null) return;
        SubmitBuffers buffers = new SubmitBuffers(collector);
        // Wall-clock time so the cell keeps folding smoothly in menus and on the ground, where no game
        // tick is guaranteed to advance.
        float time = Util.getMillis() / 50.0f;

        poseStack.pushPose();
        // Model transforms are calibrated for geometry filling a unit block; the shell is centred on
        // the origin, so move it to the block's middle first.
        poseStack.translate(0.5, 0.5, 0.5);

        boolean semiStable = look.semiStable();
        if (look.bound() != null) {
            float innerScale = semiStable
                    ? (inGui ? SEMI_STABLE_INNER_SCALE_GUI : SEMI_STABLE_INNER_SCALE_HELD)
                    : (inGui ? INNER_SCALE_GUI : INNER_SCALE_HELD);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.6f));
            poseStack.scale(innerScale, innerScale, innerScale);
            renderInner(look.bound(), poseStack, buffers, overlayCoords);
            poseStack.popPose();
        }

        // Line widths are in screen pixels, so a shell filling an inventory slot needs thinner edges
        // than one held at arm's length to read with the same weight.
        RenderType edges = inGui ? QuantumRenderTypes.HOLO_EDGE_FINE : QuantumRenderTypes.HOLO_EDGE;

        poseStack.pushPose();
        // The shell alone is nudged; the bound block is already centred and must stay put.
        if (inGui) {
            nudgeOnScreen(poseStack, GUI_SHELL_NUDGE, GUI_SHELL_NUDGE);
        }
        float shellScale = semiStable ? SEMI_STABLE_SHELL_SCALE : SHELL_SCALE;
        poseStack.scale(shellScale, shellScale, shellScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * (semiStable ? 0.35f : 0.6f)));
        poseStack.mulPose(Axis.XP.rotationDegrees(SHELL_TILT_DEGREES));
        if (semiStable) {
            TesseractShell.renderSemiStable(poseStack, buffers, time, edges, look.tint());
        } else {
            TesseractShell.render(poseStack, buffers, time, edges, look.tint());
        }
        poseStack.popPose();

        poseStack.popPose();
        buffers.flush();
    }

    /** The unit block the shell is drawn in. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
        }
    }

    private static TesseractShell.Tint shellTint(ItemStack stack) {
        if (!EntangledLinks.isBound(stack)) return TesseractShell.Tint.FLUX;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return TesseractShell.Tint.FLUX;
        return EntangledLinks.isBoundReachable(minecraft.level,
                minecraft.player == null ? null : minecraft.player.blockPosition(), stack)
                ? TesseractShell.Tint.FLUX
                : TesseractShell.Tint.RED;
    }

    /**
     * Shifts the pose by a fixed amount in the space the item is finally drawn in, which for a slot is
     * GUI pixels with y running down the screen. The model's own orientation is undone first, so the
     * shift lands the same way no matter how the item model is turned.
     */
    static void nudgeOnScreen(PoseStack poseStack, float right, float down) {
        Matrix3f orientation = new Matrix3f(poseStack.last().pose()).invert();
        Vector3f offset = new Vector3f(right, down, 0.0f).mul(orientation);
        poseStack.translate(offset.x, offset.y, offset.z);
    }

    private static void renderInner(Block bound, PoseStack poseStack, SubmitBuffers buffers, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = bound.defaultBlockState();
        if (state.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(-0.5, -0.5, -0.5);
            SubmitBuffers.block(buffers, poseStack, state, LightCoordsUtil.FULL_BRIGHT, overlay);
            poseStack.popPose();
            return;
        }
        // Blocks drawn by their own block entity renderer, such as chests, have no standalone block
        // model; their item form carries the right renderer and centres itself.
        SubmitBuffers.item(buffers, poseStack, new ItemStack(bound), ItemDisplayContext.NONE,
                LightCoordsUtil.FULL_BRIGHT, overlay, minecraft.level, 0);
    }
}
