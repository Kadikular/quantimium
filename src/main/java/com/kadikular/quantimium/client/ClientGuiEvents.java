package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** The HUD layers and key mappings, on the mod bus. */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public class ClientGuiEvents {

    private ClientGuiEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.SUBTITLE_OVERLAY,
                Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_hud"), new FieldHud());
        event.registerAbove(VanillaGuiLayers.SUBTITLE_OVERLAY,
                Identifier.fromNamespaceAndPath(Quantimium.MODID, "field_map"), new FieldMapHud());
        // Over everything vanilla draws (the debug screen is no longer a layer): it is what the new body's eyes see first.
        event.registerAboveAll(Identifier.fromNamespaceAndPath(Quantimium.MODID, "superposition"),
                new SuperpositionHud());
    }

    @SubscribeEvent
    public static void registerKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        SuperpositionKeys.register(event);
        event.register(FieldMapHud.TOGGLE);
        event.register(FieldMapClient.CYCLE);
    }
}
