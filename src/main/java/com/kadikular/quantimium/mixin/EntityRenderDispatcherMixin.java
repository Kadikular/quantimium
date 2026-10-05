package com.kadikular.quantimium.mixin;

import com.kadikular.quantimium.client.renderer.MirrorWispRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skip body, shadow, fire, and nametag for other-realm creatures this client replaces with a soul.
 * Culled here, before their render state is extracted, so nothing of them is submitted at all.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void quantimium$hideOtherRealm(Entity entity, Frustum culler, double camX, double camY, double camZ,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && MirrorWispRenderer.hidesBody(living)) {
            cir.setReturnValue(false);
        }
    }
}
