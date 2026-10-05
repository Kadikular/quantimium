// Path: src/main/java/com/kadikular/quantimium/init/ModMenuTypes.java
package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.menu.ContainmentHallMenu;
import com.kadikular.quantimium.menu.FieldControlMenu;
import com.kadikular.quantimium.menu.FieldMonitorMenu;
import com.kadikular.quantimium.menu.MaterialiserMenu;
import com.kadikular.quantimium.menu.ObservationChamberMenu;
import com.kadikular.quantimium.menu.QuantumCrafterMenu;
import com.kadikular.quantimium.menu.QuantumFoundryMenu;
import com.kadikular.quantimium.menu.QuantumObservationChamberMenu;
import com.kadikular.quantimium.menu.QuantumSimulatorMenu;
import com.kadikular.quantimium.menu.RelayModuleMenu;
import com.kadikular.quantimium.menu.RiftAnchorMenu;
import com.kadikular.quantimium.menu.TesseractStabilizerMenu;
import com.kadikular.quantimium.menu.FoldCoreMenu;
import com.kadikular.quantimium.menu.HorizonCoreMenu;
import com.kadikular.quantimium.menu.UnfoldingArrayMenu;
import com.kadikular.quantimium.menu.ZenoFieldControllerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Quantimium.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<QuantumSimulatorMenu>> QUANTUM_SIMULATOR_MENU =
            MENUS.register("quantum_simulator_menu", () ->
                    IMenuTypeExtension.create(QuantumSimulatorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<QuantumCrafterMenu>> QUANTUM_CRAFTER_MENU =
            MENUS.register("quantum_crafter_menu", () ->
                    IMenuTypeExtension.create(QuantumCrafterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<QuantumFoundryMenu>> QUANTUM_FOUNDRY_MENU =
            MENUS.register("quantum_foundry_menu", () ->
                    IMenuTypeExtension.create(QuantumFoundryMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TesseractStabilizerMenu>> TESSERACT_STABILIZER_MENU =
            MENUS.register("tesseract_stabilizer_menu", () ->
                    IMenuTypeExtension.create(TesseractStabilizerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<RelayModuleMenu>> RELAY_MODULE_MENU =
            MENUS.register("relay_module_menu", () ->
                    IMenuTypeExtension.create(RelayModuleMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<HorizonCoreMenu>> HORIZON_CORE_MENU =
            MENUS.register("horizon_core_menu", () ->
                    IMenuTypeExtension.create(HorizonCoreMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FoldCoreMenu>> FOLD_CORE_MENU =
            MENUS.register("fold_core_menu", () ->
                    IMenuTypeExtension.create(FoldCoreMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<UnfoldingArrayMenu>> UNFOLDING_ARRAY_MENU =
            MENUS.register("unfolding_array_menu", () ->
                    IMenuTypeExtension.create(UnfoldingArrayMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FieldMonitorMenu>> FIELD_MONITOR_MENU =
            MENUS.register("field_monitor_menu", () ->
                    IMenuTypeExtension.create(FieldMonitorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FieldControlMenu>> FIELD_CONTROL_MENU =
            MENUS.register("field_control_menu", () ->
                    IMenuTypeExtension.create(FieldControlMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MaterialiserMenu>> MATERIALISER_MENU =
            MENUS.register("materialiser_menu", () ->
                    IMenuTypeExtension.create(MaterialiserMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<QuantumObservationChamberMenu>> QUANTUM_OBSERVATION_CHAMBER_MENU =
            MENUS.register("quantum_observation_chamber_menu", () ->
                    IMenuTypeExtension.create(QuantumObservationChamberMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ObservationChamberMenu>> OBSERVATION_CHAMBER_MENU =
            MENUS.register("observation_chamber_menu", () ->
                    IMenuTypeExtension.create(ObservationChamberMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ZenoFieldControllerMenu>> ZENO_FIELD_CONTROLLER_MENU =
            MENUS.register("zeno_field_controller_menu", () ->
                    IMenuTypeExtension.create(ZenoFieldControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<RiftAnchorMenu>> RIFT_ANCHOR_MENU =
            MENUS.register("rift_anchor_menu", () ->
                    IMenuTypeExtension.create(RiftAnchorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ContainmentHallMenu>> CONTAINMENT_HALL_MENU =
            MENUS.register("containment_hall_menu", () ->
                    IMenuTypeExtension.create(ContainmentHallMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
