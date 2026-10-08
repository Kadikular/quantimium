package com.kadikular.quantimium.compat.ae2.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.compat.ae2.Ae2Content;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The AE2 screens. The check comes first, so nothing of AE2's is touched when it is not installed. */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class Ae2ContentClient {

    private Ae2ContentClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        if (!ModList.get().isLoaded("ae2")) return;
        event.register(Ae2Content.SUPERPOSITION_CRAFTER_MENU.get(), SuperpositionCrafterScreen::new);
        event.register(Ae2Content.REACTOR_ME_PORT_MENU.get(), ReactorMePortScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!ModList.get().isLoaded("ae2")) return;
        event.registerBlockEntityRenderer(Ae2Content.SUPERPOSITION_CRAFTER_BE.get(), SuperpositionCrafterBER::new);
    }
}
