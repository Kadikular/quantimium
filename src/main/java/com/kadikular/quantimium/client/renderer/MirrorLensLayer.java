package com.kadikular.quantimium.client.renderer;

import net.minecraft.util.context.ContextKey;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.model.MirrorLensModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a worn Mirror Lens on its wearer's head, turning with it: the goggles, then their lenses again
 * in the eyes' glow, so they catch a little light in the dark as a spider's eyes do.
 *
 * <p>Vanilla's head layer draws any non-armour item worn on the head as its flat icon, like a carved
 * pumpkin; the lens's item model scales its {@code head} display to nothing so only these goggles show.
 */
public class MirrorLensLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/mirror_lens.png");
    private static final Identifier GLOW =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/mirror_lens_glow.png");

    /** Set on the render state as it is extracted: the head slot only reaches it for real armour. */
    public static final ContextKey<Boolean> WORN =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror_lens_worn"));

    private final MirrorLensModel model;

    public MirrorLensLayer(RenderLayerParent<S, M> parent, EntityModelSet models) {
        super(parent);
        this.model = new MirrorLensModel(models.bakeLayer(MirrorLensModel.LAYER));
    }

    @Override
    public void submit(PoseStack poses, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        if (!state.getRenderDataOrDefault(WORN, false) || state.isInvisible) return;
        poses.pushPose();
        getParentModel().head.translateAndRotate(poses);
        collector.submitModelPart(model.root(), poses, RenderTypes.entityCutout(TEXTURE), light,
                OverlayTexture.NO_OVERLAY, null);
        collector.submitModelPart(model.root(), poses, QuantumRenderTypes.glow(GLOW), light,
                OverlayTexture.NO_OVERLAY, null);
        poses.popPose();
    }
}
