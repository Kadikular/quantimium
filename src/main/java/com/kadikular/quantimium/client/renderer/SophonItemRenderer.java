package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.SophonBinding;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A Sophon: a proton folded up with somebody inside it. Drawn as a small tesseract, folding through
 * itself, with its owner's head turning slowly in the middle, their own face, faint and glowing.
 *
 * <p>As {@link TesseractItemRenderer}, the item definition picks the {@code gui} variant for slots.
 */
public class SophonItemRenderer implements SpecialModelRenderer<SophonItemRenderer.Look> {

    private static final float SHELL_SCALE = 1.3f;
    private static final float HEAD_SCALE_GUI = 0.62f;
    private static final float HEAD_SCALE_HELD = 0.42f;

    /** Whose Sophon it is; an unbound one is {@code owner} null and shows an empty, pale cell. */
    public record Look(@Nullable UUID owner, boolean bound) {}

    public record Unbaked(boolean gui) implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("gui", false).forGetter(Unbaked::gui)).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<Look> bake(SpecialModelRenderer.BakingContext context) {
            return new SophonItemRenderer(gui, new PlayerModel(context.entityModelSet().bakeLayer(ModelLayers.PLAYER), false));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private final boolean inGui;
    private final PlayerModel model;

    private SophonItemRenderer(boolean inGui, PlayerModel model) {
        this.inGui = inGui;
        this.model = model;
    }

    @Override
    public Look extractArgument(ItemStack stack) {
        SophonBinding binding = stack.get(ModDataComponents.SOPHON.get());
        return new Look(binding == null ? null : binding.owner(), binding != null);
    }

    @Override
    public void submit(@Nullable Look look, PoseStack poses, SubmitNodeCollector collector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        if (look == null) return;
        SubmitBuffers buffers = new SubmitBuffers(collector);
        float time = Util.getMillis() / 50.0f;

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);

        if (look.bound()) {
            PlayerSkin skin = DoubleRenderer.skin(look.owner());
            float scale = inGui ? HEAD_SCALE_GUI : HEAD_SCALE_HELD;
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(time * 1.4f));
            poses.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 0.05f) * 8.0f));
            poses.scale(scale, -scale, -scale);
            poses.translate(0, 0.25, 0);
            // The hat layer is a child of the head since 1.21.2, so the head draws both.
            model.head.setRotation(0, 0, 0);
            model.head.render(poses, buffers.getBuffer(RenderTypes.entityTranslucent(skin.body().texturePath())),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xCCB8D8FF);
            poses.scale(1.06f, 1.06f, 1.06f);
            model.head.render(poses, buffers.getBuffer(QuantumRenderTypes.glow(skin.body().texturePath())),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF1A4499);
            poses.popPose();
        }

        poses.pushPose();
        // The same nudge as a Tesseract's shell, so it sits centred in the slot round the head.
        if (inGui) {
            TesseractItemRenderer.nudgeOnScreen(poses, TesseractItemRenderer.GUI_SHELL_NUDGE,
                    TesseractItemRenderer.GUI_SHELL_NUDGE);
        }
        poses.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
        poses.mulPose(Axis.YP.rotationDegrees(-time * 0.8f));
        poses.mulPose(Axis.XP.rotationDegrees(14.0f));
        TesseractShell.render(poses, buffers, time * 1.3f,
                inGui ? QuantumRenderTypes.HOLO_EDGE_FINE : QuantumRenderTypes.HOLO_EDGE,
                look.bound() ? TesseractShell.Tint.FLUX : TesseractShell.Tint.PALE);
        poses.popPose();
        poses.popPose();
        buffers.flush();
    }

    /** The unit block the shell is drawn in. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
        }
    }
}
