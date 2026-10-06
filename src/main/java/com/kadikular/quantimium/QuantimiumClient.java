package com.kadikular.quantimium;

import com.kadikular.quantimium.client.screen.ContainmentHallScreen;
import com.kadikular.quantimium.client.screen.FieldControlScreen;
import com.kadikular.quantimium.client.screen.MaterialiserScreen;
import com.kadikular.quantimium.client.screen.ObservationChamberScreen;
import com.kadikular.quantimium.client.screen.QuantumCrafterScreen;
import com.kadikular.quantimium.client.screen.QuantumFoundryScreen;
import com.kadikular.quantimium.client.screen.QuantumObservationChamberScreen;
import com.kadikular.quantimium.client.screen.QuantumSimulatorScreen;
import com.kadikular.quantimium.client.screen.RelayModuleScreen;
import com.kadikular.quantimium.client.screen.RiftAnchorScreen;
import com.kadikular.quantimium.client.screen.TesseractStabilizerScreen;
import com.kadikular.quantimium.client.screen.FoldCoreScreen;
import com.kadikular.quantimium.client.screen.HorizonCoreScreen;
import com.kadikular.quantimium.client.screen.UnfoldingArrayScreen;
import com.kadikular.quantimium.client.screen.ZenoFieldControllerScreen;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Quantimium.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public class QuantimiumClient {
    public QuantimiumClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        Quantimium.LOGGER.info("HELLO FROM CLIENT SETUP");
        Quantimium.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.QUANTUM_SIMULATOR_MENU.get(), QuantumSimulatorScreen::new);
        event.register(ModMenuTypes.UNFOLDING_ARRAY_MENU.get(), UnfoldingArrayScreen::new);
        event.register(ModMenuTypes.FOLD_CORE_MENU.get(), FoldCoreScreen::new);
        event.register(ModMenuTypes.CATALYST_BAY_MENU.get(), com.kadikular.quantimium.client.screen.CatalystBayScreen::new);
        event.register(ModMenuTypes.HORIZON_CORE_MENU.get(), HorizonCoreScreen::new);
        event.register(ModMenuTypes.RELAY_MODULE_MENU.get(), RelayModuleScreen::new);
        event.register(ModMenuTypes.QUANTUM_CRAFTER_MENU.get(), QuantumCrafterScreen::new);
        event.register(ModMenuTypes.QUANTUM_FOUNDRY_MENU.get(), QuantumFoundryScreen::new);
        event.register(ModMenuTypes.TESSERACT_STABILIZER_MENU.get(), TesseractStabilizerScreen::new);
        event.register(ModMenuTypes.OBSERVATION_CHAMBER_MENU.get(), ObservationChamberScreen::new);
        event.register(ModMenuTypes.QUANTUM_OBSERVATION_CHAMBER_MENU.get(), QuantumObservationChamberScreen::new);
        event.register(ModMenuTypes.MATERIALISER_MENU.get(), MaterialiserScreen::new);
        event.register(ModMenuTypes.FIELD_CONTROL_MENU.get(), FieldControlScreen::new);
        event.register(ModMenuTypes.FIELD_MONITOR_MENU.get(), com.kadikular.quantimium.client.screen.FieldMonitorScreen::new);
        event.register(ModMenuTypes.ZENO_FIELD_CONTROLLER_MENU.get(), ZenoFieldControllerScreen::new);
        event.register(ModMenuTypes.RIFT_ANCHOR_MENU.get(), RiftAnchorScreen::new);
        event.register(ModMenuTypes.CONTAINMENT_HALL_MENU.get(), ContainmentHallScreen::new);
    }
}
