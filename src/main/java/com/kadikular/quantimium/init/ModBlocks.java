// Path: src/main/java/com/kadikular/quantimium/init/ModBlocks.java
package com.kadikular.quantimium.init;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.AnomalyContainmentHallBlock;
import com.kadikular.quantimium.block.ArrayPylonBlock;
import com.kadikular.quantimium.block.BasicQuantumCrafterBlock;
import com.kadikular.quantimium.block.BuddingAnomaliteBlock;
import com.kadikular.quantimium.block.CatalystBayBlock;
import com.kadikular.quantimium.block.HorizonCoreBlock;
import com.kadikular.quantimium.block.ReactorPartBlock;
import com.kadikular.quantimium.block.ReactorPlinthBlock;
import com.kadikular.quantimium.block.ReactorPortBlock;
import com.kadikular.quantimium.block.CreativeEnergyCellBlock;
import com.kadikular.quantimium.block.DebugFieldEmitterBlock;
import com.kadikular.quantimium.block.DecoherenceProjectorBlock;
import com.kadikular.quantimium.block.EntangledDockBlock;
import com.kadikular.quantimium.block.FieldControlBlock;
import com.kadikular.quantimium.block.FieldMonitorBlock;
import com.kadikular.quantimium.block.FluxDetectorBlock;
import com.kadikular.quantimium.block.FoldCoreBlock;
import com.kadikular.quantimium.block.FoldPylonBlock;
import com.kadikular.quantimium.block.FoldRailBlock;
import com.kadikular.quantimium.block.HarvestLaserBlock;
import com.kadikular.quantimium.block.MaterialiserBlock;
import com.kadikular.quantimium.block.MirrorHangingRootsBlock;
import com.kadikular.quantimium.block.MirrorPlantBlock;
import com.kadikular.quantimium.block.MirrorTallGrassBlock;
import com.kadikular.quantimium.block.MirrorVineBlock;
import com.kadikular.quantimium.block.ObservationChamberBlock;
import com.kadikular.quantimium.block.PodCradleBlock;
import com.kadikular.quantimium.block.PodModuleBlock;
import com.kadikular.quantimium.block.QuantumAttunedGlassBlock;
import com.kadikular.quantimium.block.QuantumContainmentBlock;
import com.kadikular.quantimium.block.QuantumCrafterBlock;
import com.kadikular.quantimium.block.QuantumExciterBlock;
import com.kadikular.quantimium.block.QuantumFoundryConduitBlock;
import com.kadikular.quantimium.block.QuantumFoundryControllerBlock;
import com.kadikular.quantimium.block.QuantumFoundryPlinthBlock;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.block.QuantumFoundryStructurePartBlock;
import com.kadikular.quantimium.block.QuantumObservationChamberBlock;
import com.kadikular.quantimium.block.QuantumSimulatorBlock;
import com.kadikular.quantimium.block.RelayModuleBlock;
import com.kadikular.quantimium.block.RiftAnchorBlock;
import com.kadikular.quantimium.block.RiftLensBlock;
import com.kadikular.quantimium.block.RiftStabiliserBlock;
import com.kadikular.quantimium.block.StabilisedPortalBlock;
import com.kadikular.quantimium.block.StabilisedPortalFrameBlock;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.block.TesseractStabilizerBlock;
import com.kadikular.quantimium.block.UnfoldingArrayBlock;
import com.kadikular.quantimium.block.UnrealisedOreBlock;
import com.kadikular.quantimium.block.ZenoFieldControllerBlock;
import com.kadikular.quantimium.superposition.PodModule;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.ToIntFunction;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Quantimium.MODID);

    /** Foundry parts are dead stone until the controller reports a formed structure. */
    private static ToIntFunction<BlockState> formedLight(int lit) {
        return state -> state.getValue(QuantumFoundryStructure.FORMED) ? lit : 0;
    }

    public static final DeferredBlock<Block> QUANTUM_SIMULATOR = BLOCKS.register(
            "quantum_simulator",
            id -> new QuantumSimulatorBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    // The chassis is recessed between its corner pylons, so neighbours must keep drawing
                    // the faces they would otherwise cull against a full cube.
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(QuantumSimulatorBlock.ACTIVE) ? 8 : 4))
    );

    public static final DeferredBlock<Block> QUANTUM_CONTAINMENT_BLOCK = BLOCKS.register(
            "quantum_containment",
            id -> new QuantumContainmentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .destroyTime(-1.0f)
                    .explosionResistance(3600000.0f)
                    .lightLevel((state) -> 12) // Emits light level 12 into surrounding blocks!
                    .noLootTable())
    );

    public static final DeferredBlock<Block> QUANTUM_CRAFTER = BLOCKS.register(
            "quantum_crafter",
            id -> new QuantumCrafterBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    // The cage is open between its corner pylons so the void inside can be seen, which
                    // means neighbours must keep drawing the faces they would otherwise cull against a full cube.
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    // Whatever is in there is always shining out through the gaps.
                    .lightLevel(state -> state.getValue(QuantumCrafterBlock.ACTIVE) ? 9 : 5))
    );

    public static final DeferredBlock<Block> BASIC_QUANTUM_CRAFTER = BLOCKS.register(
            "basic_quantum_crafter",
            id -> new BasicQuantumCrafterBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(QuantumCrafterBlock.ACTIVE) ? 7 : 2))
    );

    public static final DeferredBlock<Block> QUANTUM_FOUNDRY_CONTROLLER = BLOCKS.register(
            "quantum_foundry_controller",
            id -> new QuantumFoundryControllerBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(formedLight(8))));

    public static final DeferredBlock<Block> QUANTUM_FOUNDRY_PLINTH = BLOCKS.register(
            "quantum_foundry_plinth",
            id -> new QuantumFoundryPlinthBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(formedLight(5))));

    public static final DeferredBlock<Block> QUANTUM_FOUNDRY_CONDUIT = BLOCKS.register(
            "quantum_foundry_conduit", id -> new QuantumFoundryConduitBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(formedLight(7))));

    public static final DeferredBlock<Block> QUANTUM_FOUNDRY_PILLAR = BLOCKS.register(
            "quantum_foundry_pillar", id -> new QuantumFoundryStructurePartBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(formedLight(6))));

    public static final DeferredBlock<Block> QUANTUM_FOUNDRY_ATTUNEMENT_TANK = BLOCKS.register(
            "quantum_foundry_attunement_tank", id -> new QuantumFoundryStructurePartBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .lightLevel(formedLight(12))));

    public static final DeferredBlock<Block> TESSERACT_STABILIZER = BLOCKS.register(
            "tesseract_stabilizer",
            id -> new TesseractStabilizerBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .lightLevel(state -> 6))
    );

    public static final DeferredBlock<Block> UNREALISED_ORE = BLOCKS.register(
            "unrealised_ore",
            id -> new UnrealisedOreBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    public static final DeferredBlock<Block> OBSERVATION_CHAMBER = BLOCKS.register(
            "observation_chamber",
            id -> new ObservationChamberBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ObservationChamberBlock.LIT) ? 7 : 0)));

    public static final DeferredBlock<Block> MATERIALISER = BLOCKS.register(
            "materialiser",
            id -> new MaterialiserBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(MaterialiserBlock.LIT) ? 9 : 0)));

    public static final DeferredBlock<Block> QUANTUM_OBSERVATION_CHAMBER = BLOCKS.register(
            "quantum_observation_chamber",
            id -> new QuantumObservationChamberBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(QuantumObservationChamberBlock.LIT) ? 9 : 0)));

    public static final DeferredBlock<Block> ENTANGLED_DOCK = BLOCKS.register(
            "entangled_dock",
            id -> new EntangledDockBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    public static final DeferredBlock<Block> FLUX_MAINTAINER = BLOCKS.register(
            "flux_maintainer",
            id -> new FieldControlBlock(FieldControlBlock.Kind.MAINTAINER, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FieldControlBlock.ACTIVE) ? 9 : 2)));

    public static final DeferredBlock<Block> ANOMALY_SIPHON = BLOCKS.register(
            "anomaly_siphon",
            id -> new FieldControlBlock(FieldControlBlock.Kind.SIPHON, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FieldControlBlock.ACTIVE) ? 9 : 2)));

    /** Early: holds anomaly at the top of Low and makes Anomaly Fragments. Low tier: light stone. */
    public static final DeferredBlock<Block> BASIC_ANOMALY_SIPHON = BLOCKS.register(
            "basic_anomaly_siphon",
            id -> new FieldControlBlock(FieldControlBlock.Kind.BASIC_SIPHON, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FieldControlBlock.ACTIVE) ? 9 : 2)));

    /** Early: holds flux, and only flux, under a ceiling. */
    public static final DeferredBlock<Block> FLUX_SUPPRESSOR = BLOCKS.register(
            "flux_suppressor",
            id -> new FieldControlBlock(FieldControlBlock.Kind.SUPPRESSOR, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FieldControlBlock.ACTIVE) ? 7 : 2)));

    /** Watches the field in the chunks round it; see FieldMonitorBlock. */
    public static final DeferredBlock<Block> FIELD_MONITOR = BLOCKS.register(
            "field_monitor",
            id -> new FieldMonitorBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FieldMonitorBlock.ALERT) ? 7 : 3)));

    public static final DeferredBlock<Block> FIELD_REGULATOR = BLOCKS.register(
            "field_regulator",
            id -> new FieldControlBlock(FieldControlBlock.Kind.REGULATOR, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FieldControlBlock.ACTIVE) ? 9 : 2)));

    public static final DeferredBlock<Block> SUPERPOSITION_POD = BLOCKS.register(
            "superposition_pod",
            id -> new SuperpositionPodBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(SuperpositionPodBlock.FORMED) ? 10 : 3)));

    public static final DeferredBlock<Block> POD_CRADLE = BLOCKS.register(
            "pod_cradle",
            id -> new PodCradleBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 6 : 0)));

    public static final DeferredBlock<Block> POD_PLATING = BLOCKS.register(
            "pod_plating",
            id -> new PodModuleBlock(PodModule.PLATING, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 4 : 0)));

    public static final DeferredBlock<Block> POD_RESCUE_MODULE = BLOCKS.register(
            "pod_rescue_module",
            id -> new PodModuleBlock(PodModule.RESCUE, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> POD_REGENERATION_MODULE = BLOCKS.register(
            "pod_regeneration_module",
            id -> new PodModuleBlock(PodModule.REGENERATION, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> POD_HARDENING_MODULE = BLOCKS.register(
            "pod_hardening_module",
            id -> new PodModuleBlock(PodModule.HARDENING, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> POD_WARD_MODULE = BLOCKS.register(
            "pod_ward_module",
            id -> new PodModuleBlock(PodModule.WARD, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> POD_STASH_MODULE = BLOCKS.register(
            "pod_stash_module",
            id -> new PodModuleBlock(PodModule.STASH, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 6 : 0)));

    public static final DeferredBlock<Block> POD_CHARGE_MODULE = BLOCKS.register(
            "pod_charge_module",
            id -> new PodModuleBlock(PodModule.CHARGE, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> POD_RELAY_MODULE = BLOCKS.register(
            "pod_relay_module",
            id -> new RelayModuleBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> POD_RECOVERY_MODULE = BLOCKS.register(
            "pod_recovery_module",
            id -> new PodModuleBlock(PodModule.RECOVERY, BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(PodCradleBlock.FORMED) ? 8 : 0)));

    public static final DeferredBlock<Block> UNFOLDING_ARRAY = BLOCKS.register(
            "unfolding_array",
            id -> new UnfoldingArrayBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 7)));

    public static final DeferredBlock<Block> ARRAY_PYLON = BLOCKS.register(
            "array_pylon",
            id -> new ArrayPylonBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(6.0f, 1200.0f)
                    .sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 9)));

    public static final DeferredBlock<Block> QUANTUM_EXCITER = BLOCKS.register(
            "quantum_exciter",
            id -> new QuantumExciterBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.METAL)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(QuantumExciterBlock.ACTIVE) ? 9 : 2)));

    public static final DeferredBlock<Block> ZENO_FIELD_CONTROLLER = BLOCKS.register(
            "zeno_field_controller",
            id -> new ZenoFieldControllerBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(ZenoFieldControllerBlock.ACTIVE) ? 7 : 2)));

    /** Placeholder name. Mid tier: a polished deepslate pedestal with its orb floating above. */
    public static final DeferredBlock<Block> DECOHERENCE_PROJECTOR = BLOCKS.register(
            "decoherence_projector",
            id -> new DecoherenceProjectorBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
                    // A pedestal, not a full cube: neighbours keep drawing the faces it leaves open.
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(DecoherenceProjectorBlock.ACTIVE) ? 7 : 1)));

    /** Late tier: obsidian footing for a stabilised rift. */
    public static final DeferredBlock<Block> RIFT_ANCHOR = BLOCKS.register(
            "rift_anchor",
            id -> new RiftAnchorBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(6.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 4)));

    /** Late tier: one pillar of a rift-holding array, a stepped post tapering into its emitter. */
    public static final DeferredBlock<Block> RIFT_STABILISER = BLOCKS.register(
            "rift_stabiliser",
            id -> new RiftStabiliserBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(6.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    // Not a full cube: neighbours must keep drawing the faces it no longer covers.
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(RiftStabiliserBlock.BEAMING) ? 10 : 3)));

    public static final DeferredBlock<Block> ANOMALY_CONTAINMENT_HALL = BLOCKS.register(
            "anomaly_containment_hall",
            id -> new AnomalyContainmentHallBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(AnomalyContainmentHallBlock.ACTIVE) ? 10 : 2)));

    public static final DeferredBlock<Block> FLUX_DETECTOR = BLOCKS.register(
            "flux_detector",
            id -> new FluxDetectorBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FluxDetectorBlock.POWERED) ? 7 : 0)));

    /** Creative testing only; see DebugFieldEmitterBlock. */
    public static final DeferredBlock<Block> DEBUG_FIELD_EMITTER = BLOCKS.register(
            "debug_field_emitter",
            id -> new DebugFieldEmitterBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()
                    .lightLevel(state -> Math.min(15, state.getValue(DebugFieldEmitterBlock.SETTING) * 2))));

    /** Creative testing only; see CreativeEnergyCellBlock. */
    public static final DeferredBlock<Block> CREATIVE_ENERGY_CELL = BLOCKS.register(
            "creative_energy_cell",
            id -> new CreativeEnergyCellBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(-1.0f, 3_600_000.0f)
                    .noLootTable()));

    public static final DeferredBlock<Block> ANOMALITE_CRYSTAL = BLOCKS.register(
            "anomalite_crystal",
            id -> new AnomaliteCrystalBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(1.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .noOcclusion()
                    .randomTicks()
                    .pushReaction(PushReaction.BLOCK)
                    // For placers that ask only the flag, as AE2 cable parts do; the block's own
                    // canBeReplaced overrides still decide for everyone that passes a context.
                    .replaceable()
                    .lightLevel(state -> state.getValue(AnomaliteCrystalBlock.AGE) + 2)));

    public static final DeferredBlock<MirrorVineBlock> MIRROR_VINE = BLOCKS.register(
            "mirror_vine",
            id -> new MirrorVineBlock(mirrorFlora(id)));

    public static final DeferredBlock<MirrorPlantBlock> MIRROR_SHORT_GRASS = BLOCKS.register(
            "mirror_short_grass",
            id -> new MirrorPlantBlock(mirrorFlora(id).offsetType(BlockBehaviour.OffsetType.XYZ)));

    public static final DeferredBlock<MirrorTallGrassBlock> MIRROR_TALL_GRASS = BLOCKS.register(
            "mirror_tall_grass",
            id -> new MirrorTallGrassBlock(mirrorFlora(id).offsetType(BlockBehaviour.OffsetType.XYZ)));

    public static final DeferredBlock<MirrorPlantBlock> MIRROR_DEAD_BUSH = BLOCKS.register(
            "mirror_dead_bush",
            id -> new MirrorPlantBlock(mirrorFlora(id).offsetType(BlockBehaviour.OffsetType.XZ)));

    public static final DeferredBlock<MirrorHangingRootsBlock> MIRROR_HANGING_ROOTS = BLOCKS.register(
            "mirror_hanging_roots",
            id -> new MirrorHangingRootsBlock(mirrorFlora(id).offsetType(BlockBehaviour.OffsetType.XZ)));

    public static final DeferredBlock<StabilisedPortalFrameBlock> STABILISED_PORTAL_FRAME = BLOCKS.register(
            "stabilised_portal_frame",
            id -> new StabilisedPortalFrameBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)));

    public static final DeferredBlock<StabilisedPortalBlock> STABILISED_PORTAL = BLOCKS.register(
            "stabilised_portal",
            id -> new StabilisedPortalBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollision()
                    .noOcclusion()
                    .noLootTable()
                    .strength(-1.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * Copies tinted glass for its heft and sound, but not its light blocking — see
     * {@link QuantumAttunedGlassBlock}.
     */
    public static final DeferredBlock<Block> QUANTUM_ATTUNED_GLASS = BLOCKS.register(
            "quantum_attuned_glass",
            id -> new QuantumAttunedGlassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    // Lit a little under a torch so a formed cell is legible in the dark without the
                    // shell doubling as a lamp when the panes are used as plain building glass.
                    .lightLevel(state ->
                            state.getValue(QuantumFoundryStructure.FORMED) ? 11 : 4)));

    /** A Fold Chamber's corner. Lit once its frame is whole. */
    public static final DeferredBlock<Block> FOLD_PYLON = BLOCKS.register(
            "fold_pylon",
            id -> new FoldPylonBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(formedLight(7))));

    /** A Fold Chamber's edge: attuned glass, like the Containment Hall's walls. */
    public static final DeferredBlock<Block> FOLD_RAIL = BLOCKS.register(
            "fold_rail",
            id -> new FoldRailBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .lightLevel(formedLight(9))));

    /** Holds the Tesseract a Fold Chamber folds into, in the middle of its bottom front edge. */
    public static final DeferredBlock<Block> FOLD_CORE = BLOCKS.register(
            "fold_core",
            id -> new FoldCoreBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 8.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(formedLight(10))));

    /** The Quantimium Reactor's controller: a caged Singularity on its dais. */
    public static final DeferredBlock<Block> HORIZON_CORE = BLOCKS.register(
            "horizon_core",
            id -> new HorizonCoreBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(8.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(formedLight(12))));

    /** The Reactor's floor: a disc 11 across under the Horizon Core. */
    public static final DeferredBlock<Block> REACTOR_PLINTH = BLOCKS.register(
            "reactor_plinth",
            id -> new ReactorPlinthBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(5.0f, 12.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(formedLight(3))));

    /** Stands on the plinth; a facing pair drives one of the horizon's rings. */
    public static final DeferredBlock<Block> RING_EMITTER = BLOCKS.register(
            "ring_emitter",
            id -> new ReactorPartBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0f, 12.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(formedLight(10))));

    /** A glass case on the plinth holding one of the Reactor's catalysts. */
    public static final DeferredBlock<Block> CATALYST_BAY = BLOCKS.register(
            "catalyst_bay",
            id -> new CatalystBayBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS)
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .lightLevel(state -> 6)));

    public static final DeferredBlock<Block> REACTOR_INPUT_PORT = BLOCKS.register(
            "reactor_input_port", id -> new ReactorPortBlock(reactorPort(id), ReactorPortBlock.Kind.INPUT));
    public static final DeferredBlock<Block> REACTOR_OUTPUT_PORT = BLOCKS.register(
            "reactor_output_port", id -> new ReactorPortBlock(reactorPort(id), ReactorPortBlock.Kind.OUTPUT));
    public static final DeferredBlock<Block> REACTOR_ENERGY_PORT = BLOCKS.register(
            "reactor_energy_port", id -> new ReactorPortBlock(reactorPort(id), ReactorPortBlock.Kind.ENERGY));
    public static final DeferredBlock<Block> REACTOR_MATERIALISER_PORT = BLOCKS.register(
            "reactor_materialiser_port", id -> new ReactorPortBlock(reactorPort(id), ReactorPortBlock.Kind.MATERIALISER));

    private static BlockBehaviour.Properties reactorPort(Identifier id) {
        return BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .mapColor(MapColor.DEEPSLATE)
                .strength(5.0f, 12.0f)
                .requiresCorrectToolForDrops()
                .lightLevel(formedLight(6));
    }

    public static final DeferredBlock<Block> ANOMALITE_LATTICE = BLOCKS.register(
            "anomalite_lattice",
            id -> new Block(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 4)));

    public static final DeferredBlock<Block> BUDDING_ANOMALITE = BLOCKS.register(
            "budding_anomalite",
            id -> new BuddingAnomaliteBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> state.getValue(BuddingAnomaliteBlock.ACTIVE) ? 8 : 3)));

    /** Mid tier: the ring a Harvest Laser fires through. Lit while the tear in it is open. */
    public static final DeferredBlock<Block> RIFT_LENS = BLOCKS.register(
            "rift_lens",
            id -> new RiftLensBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(RiftLensBlock.OPEN) ? 9 : 0)));

    /** Mid tier: fires through a Rift Lens to harvest the Anomalite crystal behind it. */
    public static final DeferredBlock<Block> HARVEST_LASER = BLOCKS.register(
            "harvest_laser",
            id -> new HarvestLaserBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(HarvestLaserBlock.ACTIVE) ? 7 : 1)));

    public static void register(IEventBus eventBus) {
        // Renamed before release; worlds from then keep their lenses.
        BLOCKS.addAlias(Identifier.fromNamespaceAndPath(Quantimium.MODID, "rift_ring"),
                Identifier.fromNamespaceAndPath(Quantimium.MODID, "rift_lens"));
        BLOCKS.register(eventBus);
    }

    private static BlockBehaviour.Properties mirrorFlora(Identifier id) {
        return BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .mapColor(MapColor.COLOR_GRAY)
                .replaceable()
                .noCollision()
                .noOcclusion()
                .sound(SoundType.EMPTY)
                .noLootTable();
    }
}
