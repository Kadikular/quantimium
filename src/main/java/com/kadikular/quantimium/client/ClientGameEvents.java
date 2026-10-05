package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.renderer.UnrealisedOreFacadeCache;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.DockBinding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Game-bus client ticks (facade cache expiry, etc.). */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {

    private ClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            UnrealisedOreFacadeCache.clear();
            return;
        }
        UnrealisedOreFacadeCache.onClientTick();
    }

    /** Anything superposed with an Entangled Dock says where its dock is. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        DockBinding binding = event.getItemStack().get(ModDataComponents.DOCK_BINDING.get());
        if (binding == null) return;
        BlockPos pos = binding.dock().pos();
        event.getToolTip().add(Component.translatable("item.quantimium.dock_binding.tooltip", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
