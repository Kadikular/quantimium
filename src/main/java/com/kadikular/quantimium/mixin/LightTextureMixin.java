package com.kadikular.quantimium.mixin;

import com.kadikular.quantimium.client.VeiledFlickerClient;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the Veiled make lamps falter. Vanilla scales every block-light level by its torch flicker plus
 * 1.4 when it extracts the lightmap's state; taking our dip off that one read dims block light and
 * nothing else. (The lightmap itself is built on the GPU since 1.21.5; its inputs are extracted here.)
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightTextureMixin {

    @Shadow
    private float blockLightFlicker;

    @Redirect(method = "extract",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LightmapRenderStateExtractor;blockLightFlicker:F",
                    opcode = Opcodes.GETFIELD))
    private float quantimium$falter(LightmapRenderStateExtractor self) {
        return blockLightFlicker - VeiledFlickerClient.dim();
    }
}
