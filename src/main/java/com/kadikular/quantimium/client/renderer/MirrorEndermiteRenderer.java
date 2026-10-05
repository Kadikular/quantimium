package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.entity.MirrorEndermite;
import net.minecraft.client.model.monster.endermite.EndermiteModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Vanilla endermite model; texture is our copy so it can be painted without a renderer edit. */
public final class MirrorEndermiteRenderer extends MobRenderer<MirrorEndermite, LivingEntityRenderState, EndermiteModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/mirror_endermite.png");

    public MirrorEndermiteRenderer(EntityRendererProvider.Context context) {
        super(context, new EndermiteModel(context.bakeLayer(ModelLayers.ENDERMITE)), 0.3F);
    }

    @Override
    protected float getFlipDegrees() {
        return 180.0F;
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}
