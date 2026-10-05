package com.kadikular.quantimium.event;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.SideConfigurable;
import com.kadikular.quantimium.init.ModTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Vanilla skips block interaction whenever a player sneaks while holding an item, so a sneak+wrench
 * click never reaches the block at all. This forces the block through for every
 * {@link SideConfigurable} machine so their own interaction ladder can cycle the fluid mode, and
 * suppresses the wrench's own sneak behaviour so a third-party tool cannot dismantle or rotate the
 * machine out from under us.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class WrenchInteractions {

    private WrenchInteractions() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) return;
        if (!event.getItemStack().is(ModTags.TOOLS_WRENCH)) return;
        if (event.getFace() == null) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof SideConfigurable)) return;

        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.FALSE);
    }
}
