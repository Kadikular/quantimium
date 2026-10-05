package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.AnomaliteCrystalBlockEntity;
import com.kadikular.quantimium.block.entity.BuddingAnomaliteBlockEntity;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.entity.CreativeEnergyCellBlockEntity;
import com.kadikular.quantimium.block.entity.DebugFieldEmitterBlockEntity;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.block.entity.EntangledDockBlockEntity;
import com.kadikular.quantimium.block.entity.FieldControlBlockEntity;
import com.kadikular.quantimium.block.entity.FieldMonitorBlockEntity;
import com.kadikular.quantimium.block.entity.FluxDetectorBlockEntity;
import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.block.entity.ReactorPortBlockEntity;
import com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity;
import com.kadikular.quantimium.block.entity.MaterialiserBlockEntity;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumExciterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumFoundryPartBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.RelayModuleBlockEntity;
import com.kadikular.quantimium.block.entity.RiftAnchorBlockEntity;
import com.kadikular.quantimium.block.entity.RiftStabiliserBlockEntity;
import com.kadikular.quantimium.block.entity.StabilisedPortalBlockEntity;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Quantimium.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumSimulatorBlockEntity>> QUANTUM_SIMULATOR_BE =
            BLOCK_ENTITIES.register("quantum_simulator_be", () ->
                    new BlockEntityType<>(QuantumSimulatorBlockEntity::new, ModBlocks.QUANTUM_SIMULATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumCrafterBlockEntity>> QUANTUM_CRAFTER_BE =
            BLOCK_ENTITIES.register("quantum_crafter_be", () ->
                    new BlockEntityType<>(QuantumCrafterBlockEntity::new,
                                    ModBlocks.QUANTUM_CRAFTER.get(),
                                    ModBlocks.BASIC_QUANTUM_CRAFTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumFoundryBlockEntity>>
            QUANTUM_FOUNDRY_BE = BLOCK_ENTITIES.register("quantum_foundry_be", () ->
            new BlockEntityType<>(QuantumFoundryBlockEntity::new,
                    ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumFoundryPartBlockEntity>>
            QUANTUM_FOUNDRY_PART_BE = BLOCK_ENTITIES.register("quantum_foundry_part_be", () ->
            new BlockEntityType<>(QuantumFoundryPartBlockEntity::new,
                    ModBlocks.QUANTUM_FOUNDRY_PLINTH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TesseractStabilizerBlockEntity>> TESSERACT_STABILIZER_BE =
            BLOCK_ENTITIES.register("tesseract_stabilizer_be", () ->
                    new BlockEntityType<>(TesseractStabilizerBlockEntity::new, ModBlocks.TESSERACT_STABILIZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaterialiserBlockEntity>> MATERIALISER_BE =
            BLOCK_ENTITIES.register("materialiser_be", () ->
                    new BlockEntityType<>(MaterialiserBlockEntity::new, ModBlocks.MATERIALISER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumObservationChamberBlockEntity>> QUANTUM_OBSERVATION_CHAMBER_BE =
            BLOCK_ENTITIES.register("quantum_observation_chamber_be", () ->
                    new BlockEntityType<>(QuantumObservationChamberBlockEntity::new, ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EntangledDockBlockEntity>> ENTANGLED_DOCK_BE =
            BLOCK_ENTITIES.register("entangled_dock_be", () ->
                    new BlockEntityType<>(EntangledDockBlockEntity::new, ModBlocks.ENTANGLED_DOCK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperpositionPodBlockEntity>> SUPERPOSITION_POD_BE =
            BLOCK_ENTITIES.register("superposition_pod_be", () ->
                    new BlockEntityType<>(SuperpositionPodBlockEntity::new, ModBlocks.SUPERPOSITION_POD.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelayModuleBlockEntity>> RELAY_MODULE_BE =
            BLOCK_ENTITIES.register("relay_module_be", () ->
                    new BlockEntityType<>(RelayModuleBlockEntity::new, ModBlocks.POD_RELAY_MODULE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UnfoldingArrayBlockEntity>> UNFOLDING_ARRAY_BE =
            BLOCK_ENTITIES.register("unfolding_array_be", () ->
                    new BlockEntityType<>(UnfoldingArrayBlockEntity::new, ModBlocks.UNFOLDING_ARRAY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HorizonCoreBlockEntity>> HORIZON_CORE_BE =
            BLOCK_ENTITIES.register("horizon_core_be", () ->
                    new BlockEntityType<>(HorizonCoreBlockEntity::new, ModBlocks.HORIZON_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatalystBayBlockEntity>> CATALYST_BAY_BE =
            BLOCK_ENTITIES.register("catalyst_bay_be", () ->
                    new BlockEntityType<>(CatalystBayBlockEntity::new, ModBlocks.CATALYST_BAY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorPortBlockEntity>> REACTOR_PORT_BE =
            BLOCK_ENTITIES.register("reactor_port_be", () ->
                    new BlockEntityType<>(ReactorPortBlockEntity::new, ModBlocks.REACTOR_INPUT_PORT.get(),
                            ModBlocks.REACTOR_OUTPUT_PORT.get(), ModBlocks.REACTOR_ENERGY_PORT.get(),
                            ModBlocks.REACTOR_MATERIALISER_PORT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoldCoreBlockEntity>> FOLD_CORE_BE =
            BLOCK_ENTITIES.register("fold_core_be", () ->
                    new BlockEntityType<>(FoldCoreBlockEntity::new, ModBlocks.FOLD_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FieldMonitorBlockEntity>> FIELD_MONITOR_BE =
            BLOCK_ENTITIES.register("field_monitor_be", () ->
                    new BlockEntityType<>(FieldMonitorBlockEntity::new, ModBlocks.FIELD_MONITOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FieldControlBlockEntity>> FIELD_CONTROL_BE =
            BLOCK_ENTITIES.register("field_control_be", () ->
                    new BlockEntityType<>(FieldControlBlockEntity::new, ModBlocks.FLUX_MAINTAINER.get(),
                                    ModBlocks.ANOMALY_SIPHON.get(), ModBlocks.FIELD_REGULATOR.get(),
                                    ModBlocks.BASIC_ANOMALY_SIPHON.get(), ModBlocks.FLUX_SUPPRESSOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ObservationChamberBlockEntity>> OBSERVATION_CHAMBER_BE =
            BLOCK_ENTITIES.register("observation_chamber_be", () ->
                    new BlockEntityType<>(ObservationChamberBlockEntity::new, ModBlocks.OBSERVATION_CHAMBER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumExciterBlockEntity>> QUANTUM_EXCITER_BE =
            BLOCK_ENTITIES.register("quantum_exciter_be", () ->
                    new BlockEntityType<>(QuantumExciterBlockEntity::new, ModBlocks.QUANTUM_EXCITER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ZenoFieldControllerBlockEntity>> ZENO_FIELD_CONTROLLER_BE =
            BLOCK_ENTITIES.register("zeno_field_controller_be", () ->
                    new BlockEntityType<>(ZenoFieldControllerBlockEntity::new, ModBlocks.ZENO_FIELD_CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DecoherenceProjectorBlockEntity>> DECOHERENCE_PROJECTOR_BE =
            BLOCK_ENTITIES.register("decoherence_projector_be", () ->
                    new BlockEntityType<>(DecoherenceProjectorBlockEntity::new, ModBlocks.DECOHERENCE_PROJECTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HarvestLaserBlockEntity>> HARVEST_LASER_BE =
            BLOCK_ENTITIES.register("harvest_laser_be", () ->
                    new BlockEntityType<>(HarvestLaserBlockEntity::new, ModBlocks.HARVEST_LASER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftAnchorBlockEntity>> RIFT_ANCHOR_BE =
            BLOCK_ENTITIES.register("rift_anchor_be", () ->
                    new BlockEntityType<>(RiftAnchorBlockEntity::new, ModBlocks.RIFT_ANCHOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftStabiliserBlockEntity>> RIFT_STABILISER_BE =
            BLOCK_ENTITIES.register("rift_stabiliser_be", () ->
                    new BlockEntityType<>(RiftStabiliserBlockEntity::new, ModBlocks.RIFT_STABILISER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContainmentHallBlockEntity>>
            ANOMALY_CONTAINMENT_HALL_BE = BLOCK_ENTITIES.register("anomaly_containment_hall_be", () ->
            new BlockEntityType<>(ContainmentHallBlockEntity::new,
                    ModBlocks.ANOMALY_CONTAINMENT_HALL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluxDetectorBlockEntity>> FLUX_DETECTOR_BE =
            BLOCK_ENTITIES.register("flux_detector_be", () ->
                    new BlockEntityType<>(FluxDetectorBlockEntity::new, ModBlocks.FLUX_DETECTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeEnergyCellBlockEntity>> CREATIVE_ENERGY_CELL_BE =
            BLOCK_ENTITIES.register("creative_energy_cell_be", () ->
                    new BlockEntityType<>(CreativeEnergyCellBlockEntity::new, ModBlocks.CREATIVE_ENERGY_CELL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DebugFieldEmitterBlockEntity>> DEBUG_FIELD_EMITTER_BE =
            BLOCK_ENTITIES.register("debug_field_emitter_be", () ->
                    new BlockEntityType<>(DebugFieldEmitterBlockEntity::new, ModBlocks.DEBUG_FIELD_EMITTER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnomaliteCrystalBlockEntity>> ANOMALITE_CRYSTAL_BE =
            BLOCK_ENTITIES.register("anomalite_crystal_be", () ->
                    new BlockEntityType<>(AnomaliteCrystalBlockEntity::new, ModBlocks.ANOMALITE_CRYSTAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BuddingAnomaliteBlockEntity>> BUDDING_ANOMALITE_BE =
            BLOCK_ENTITIES.register("budding_anomalite_be", () ->
                    new BlockEntityType<>(BuddingAnomaliteBlockEntity::new, ModBlocks.BUDDING_ANOMALITE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StabilisedPortalBlockEntity>> STABILISED_PORTAL_BE =
            BLOCK_ENTITIES.register("stabilised_portal_be", () ->
                    new BlockEntityType<>(StabilisedPortalBlockEntity::new, ModBlocks.STABILISED_PORTAL.get()));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
