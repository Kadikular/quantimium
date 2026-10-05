package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import com.kadikular.quantimium.item.TetherItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Quantimium.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> QUANTIMIUM_TAB = 
            CREATIVE_TABS.register("quantimium_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.quantimium"))
                    .icon(() -> new ItemStack(ModItems.QUANTUM_SIMULATOR_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.QUANTUM_SIMULATOR_ITEM.get());
                        output.accept(ModItems.BASIC_QUANTUM_CRAFTER_ITEM.get());
                        output.accept(ModItems.QUANTUM_FOUNDRY_CONTROLLER_ITEM.get());
                        output.accept(ModItems.QUANTUM_FOUNDRY_PLINTH_ITEM.get());
                        output.accept(ModItems.QUANTUM_FOUNDRY_CONDUIT_ITEM.get());
                        output.accept(ModItems.QUANTUM_FOUNDRY_PILLAR_ITEM.get());
                        output.accept(ModItems.QUANTUM_FOUNDRY_ATTUNEMENT_TANK_ITEM.get());
                        output.accept(ModItems.QUANTUM_CRAFTER_ITEM.get());
                        output.accept(ModItems.TESSERACT_STABILIZER_ITEM.get());
                        output.accept(ModItems.SEMI_STABLE_TESSERACT.get());
                        output.accept(ModItems.TESSERACT.get());
                        output.accept(ModItems.UNREALISED_ORE_ITEM.get());
                        output.accept(ModItems.UNREALISED_MATTER.get());
                        output.accept(ModItems.QUANTIMIUM_TRACE.get());
                        output.accept(ModItems.BASIC_DECOHERENCE_MATRIX.get());
                        output.accept(ModItems.FLUX_METER.get());
                        output.accept(ModItems.MIRROR_LENS.get());
                        output.accept(ModItems.FLUX_DETECTOR_ITEM.get());
                        output.accept(ModItems.DEBUG_FIELD_EMITTER_ITEM.get());
                        output.accept(ModItems.CREATIVE_ENERGY_CELL_ITEM.get());
                        output.accept(ModItems.QUANTUM_EXCITER_ITEM.get());
                        output.accept(ModItems.FLUX_SUPPRESSOR_ITEM.get());
                        output.accept(ModItems.ZENO_FIELD_CONTROLLER_ITEM.get());
                        output.accept(ModItems.BASIC_ANOMALY_SIPHON_ITEM.get());
                        output.accept(ModItems.ANOMALY_CONTAINMENT_HALL_ITEM.get());
                        output.accept(ModItems.ANOMALY_FRAGMENT.get());
                        output.accept(ModItems.ANOMALITE_CRYSTAL_ITEM.get());
                        output.accept(ModItems.ANOMALITE_LATTICE_ITEM.get());
                        output.accept(ModItems.QUANTUM_ATTUNED_GLASS_ITEM.get());
                        output.accept(ModItems.FOLD_PYLON_ITEM.get());
                        output.accept(ModItems.FOLD_RAIL_ITEM.get());
                        output.accept(ModItems.FOLD_CORE_ITEM.get());
                        output.accept(ModItems.SINGULARITY.get());
                        output.accept(ModItems.HORIZON_CORE_ITEM.get());
                        output.accept(ModItems.REACTOR_PLINTH_ITEM.get());
                        output.accept(ModItems.RING_EMITTER_ITEM.get());
                        output.accept(ModItems.CATALYST_BAY_ITEM.get());
                        output.accept(ModItems.REACTOR_INPUT_PORT_ITEM.get());
                        output.accept(ModItems.REACTOR_OUTPUT_PORT_ITEM.get());
                        output.accept(ModItems.REACTOR_ENERGY_PORT_ITEM.get());
                        output.accept(ModItems.REACTOR_MATERIALISER_PORT_ITEM.get());
                        output.accept(ModItems.BUDDING_ANOMALITE_ITEM.get());
                        output.accept(ModItems.STABILISED_PORTAL_FRAME_ITEM.get());
                        output.accept(ModItems.ANOMALITE_SHARD.get());
                        output.accept(ModItems.ANOMALITE_DUST.get());
                        output.accept(ModItems.ANOMALITE_CELL.get());
                        output.accept(ModItems.ANOMALITE_EMITTER.get());
                        output.accept(ModItems.DECOHERENCE_LANCE.get());
                        ItemStack chargedLance = new ItemStack(ModItems.DECOHERENCE_LANCE.get());
                        DecoherenceLanceItem.setEnergy(chargedLance, DecoherenceLanceItem.CAPACITY);
                        output.accept(chargedLance);
                        output.accept(ModItems.RIFT_RESIDUE.get());
                        output.accept(ModItems.VEIL_THREAD.get());
                        output.accept(ModItems.DECOHERENCE_PROJECTOR_ITEM.get());
                        output.accept(ModItems.HARVEST_LASER_ITEM.get());
                        output.accept(ModItems.RIFT_LENS_ITEM.get());
                        output.accept(ModItems.RIFT_SEED.get());
                        output.accept(ModItems.RIFT_ANCHOR_ITEM.get());
                        output.accept(ModItems.RIFT_STABILISER_ITEM.get());
                        output.accept(ModItems.VEILED_SPAWN_EGG.get());
                        output.accept(ModItems.OBSERVATION_CHAMBER_ITEM.get());
                        output.accept(ModItems.QUANTUM_OBSERVATION_CHAMBER_ITEM.get());
                        output.accept(ModItems.MATERIALISER_ITEM.get());
                        output.accept(ModItems.ENTANGLED_DOCK_ITEM.get());
                        output.accept(ModItems.FLUX_MAINTAINER_ITEM.get());
                        output.accept(ModItems.ANOMALY_SIPHON_ITEM.get());
                        output.accept(ModItems.FIELD_REGULATOR_ITEM.get());
                        output.accept(ModItems.FIELD_MONITOR_ITEM.get());
                        output.accept(ModItems.UNFOLDING_ARRAY_ITEM.get());
                        output.accept(ModItems.ARRAY_PYLON_ITEM.get());
                        output.accept(ModItems.SUPERPOSITION_POD_ITEM.get());
                        output.accept(ModItems.POD_CRADLE_ITEM.get());
                        output.accept(ModItems.POD_PLATING_ITEM.get());
                        output.accept(ModItems.POD_RESCUE_MODULE_ITEM.get());
                        output.accept(ModItems.POD_REGENERATION_MODULE_ITEM.get());
                        output.accept(ModItems.POD_HARDENING_MODULE_ITEM.get());
                        output.accept(ModItems.POD_WARD_MODULE_ITEM.get());
                        output.accept(ModItems.POD_STASH_MODULE_ITEM.get());
                        output.accept(ModItems.POD_CHARGE_MODULE_ITEM.get());
                        output.accept(ModItems.POD_RELAY_MODULE_ITEM.get());
                        output.accept(ModItems.POD_RECOVERY_MODULE_ITEM.get());
                        ItemStack tether = new ItemStack(ModItems.TETHER.get());
                        output.accept(tether.copy());
                        TetherItem.setEnergy(tether, TetherItem.CAPACITY);
                        output.accept(tether);
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}