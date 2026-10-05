package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.init.ModTags;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Gate for the port overlays our machines draw. They only appear while the local player holds a
 * wrench, keeping builds clean until someone is actually configuring sides.
 */
public final class WrenchView {

    private WrenchView() {}

    public static boolean isActive() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getMainHandItem().is(ModTags.TOOLS_WRENCH)
                || player.getOffhandItem().is(ModTags.TOOLS_WRENCH);
    }
}
