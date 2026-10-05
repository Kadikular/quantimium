package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.ClientPhaseState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import org.jspecify.annotations.Nullable;

/**
 * Overlay players read as ghosts while you are also phased — translucent body with a grey-violet
 * tint, without touching server-side entity flags.
 *
 * <p>Since the render-state rework the living renderer asks its subclass for the tint and render type
 * as it submits, so a ghost is those two answers changed rather than a whole render of its own.
 */
public final class MirrorPhasePlayerRenderer extends AvatarRenderer<AbstractClientPlayer> {

    /** ARGB ~58% alpha with a cool grey wash — more visible than vanilla invisibility. */
    private static final int PHASED_COLOR = 0x94B8ADD1;
    private static final ContextKey<Boolean> GHOST =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Quantimium.MODID, "phased_ghost"));

    public MirrorPhasePlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
    }

    @Override
    public void extractRenderState(AbstractClientPlayer player, AvatarRenderState state, float partialTick) {
        super.extractRenderState(player, state, partialTick);
        state.setRenderData(GHOST, ClientPhaseState.isActive() && ClientPhaseState.isPhased(player));
    }

    private static boolean ghost(AvatarRenderState state) {
        return state.getRenderDataOrDefault(GHOST, false);
    }

    @Override
    protected int getModelTint(AvatarRenderState state) {
        return ghost(state) ? PHASED_COLOR : super.getModelTint(state);
    }

    /** A ghost draws as a see-through body would, translucent, however visible it really is. */
    @Override
    protected @Nullable RenderType getRenderType(AvatarRenderState state, boolean bodyVisible, boolean forceTransparent,
                                                 boolean appearGlowing) {
        return ghost(state)
                ? super.getRenderType(state, false, true, appearGlowing)
                : super.getRenderType(state, bodyVisible, forceTransparent, appearGlowing);
    }
}
