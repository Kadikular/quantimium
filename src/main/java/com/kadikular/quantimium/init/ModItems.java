package com.kadikular.quantimium.init;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.item.TooltipBlockItem;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.item.AnomaliteCellItem;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import com.kadikular.quantimium.item.FluxMeterItem;
import com.kadikular.quantimium.item.MirrorLensItem;
import com.kadikular.quantimium.item.RiftSeedItem;
import com.kadikular.quantimium.item.SophonItem;
import com.kadikular.quantimium.item.FoldedTesseractItem;
import com.kadikular.quantimium.item.SingularityItem;
import com.kadikular.quantimium.item.TesseractItem;
import com.kadikular.quantimium.item.TetherItem;
import com.kadikular.quantimium.item.UnrealisedMatterItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Quantimium.MODID);

    // Register the item variant for our block so it can be held in inventory
    public static final DeferredItem<BlockItem> QUANTUM_SIMULATOR_ITEM = ITEMS.registerSimpleBlockItem(
            "quantum_simulator", ModBlocks.QUANTUM_SIMULATOR);

    public static final DeferredItem<BlockItem> QUANTUM_CRAFTER_ITEM = ITEMS.registerSimpleBlockItem(
            "quantum_crafter", ModBlocks.QUANTUM_CRAFTER);

    public static final DeferredItem<BlockItem> BASIC_QUANTUM_CRAFTER_ITEM = ITEMS.registerSimpleBlockItem(
            "basic_quantum_crafter", ModBlocks.BASIC_QUANTUM_CRAFTER);

    public static final DeferredItem<BlockItem> QUANTUM_FOUNDRY_CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem("quantum_foundry_controller", ModBlocks.QUANTUM_FOUNDRY_CONTROLLER);
    public static final DeferredItem<BlockItem> QUANTUM_FOUNDRY_PLINTH_ITEM =
            ITEMS.registerSimpleBlockItem("quantum_foundry_plinth", ModBlocks.QUANTUM_FOUNDRY_PLINTH);
    public static final DeferredItem<BlockItem> QUANTUM_FOUNDRY_CONDUIT_ITEM =
            ITEMS.registerSimpleBlockItem("quantum_foundry_conduit", ModBlocks.QUANTUM_FOUNDRY_CONDUIT);
    public static final DeferredItem<BlockItem> QUANTUM_FOUNDRY_PILLAR_ITEM =
            ITEMS.registerSimpleBlockItem("quantum_foundry_pillar", ModBlocks.QUANTUM_FOUNDRY_PILLAR);
    public static final DeferredItem<BlockItem> QUANTUM_FOUNDRY_ATTUNEMENT_TANK_ITEM =
            ITEMS.registerSimpleBlockItem("quantum_foundry_attunement_tank",
                    ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK);

    public static final DeferredItem<BlockItem> TESSERACT_STABILIZER_ITEM = ITEMS.registerSimpleBlockItem(
            "tesseract_stabilizer", ModBlocks.TESSERACT_STABILIZER);

    public static final DeferredItem<BlockItem> UNREALISED_ORE_ITEM = ITEMS.registerSimpleBlockItem(
            "unrealised_ore", ModBlocks.UNREALISED_ORE);

    public static final DeferredItem<BlockItem> OBSERVATION_CHAMBER_ITEM = ITEMS.registerSimpleBlockItem(
            "observation_chamber", ModBlocks.OBSERVATION_CHAMBER);
    public static final DeferredItem<BlockItem> MATERIALISER_ITEM = ITEMS.registerSimpleBlockItem(
            "materialiser", ModBlocks.MATERIALISER);
    public static final DeferredItem<BlockItem> QUANTUM_OBSERVATION_CHAMBER_ITEM = ITEMS.registerSimpleBlockItem(
            "quantum_observation_chamber", ModBlocks.QUANTUM_OBSERVATION_CHAMBER);
    public static final DeferredItem<BlockItem> ENTANGLED_DOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "entangled_dock", ModBlocks.ENTANGLED_DOCK);
    public static final DeferredItem<BlockItem> FLUX_MAINTAINER_ITEM = ITEMS.registerSimpleBlockItem(
            "flux_maintainer", ModBlocks.FLUX_MAINTAINER);
    public static final DeferredItem<BlockItem> ANOMALY_SIPHON_ITEM = ITEMS.registerSimpleBlockItem(
            "anomaly_siphon", ModBlocks.ANOMALY_SIPHON);
    public static final DeferredItem<BlockItem> BASIC_ANOMALY_SIPHON_ITEM = ITEMS.registerSimpleBlockItem(
            "basic_anomaly_siphon", ModBlocks.BASIC_ANOMALY_SIPHON);
    public static final DeferredItem<BlockItem> FIELD_MONITOR_ITEM = ITEMS.registerSimpleBlockItem(
            "field_monitor", ModBlocks.FIELD_MONITOR);
    public static final DeferredItem<BlockItem> FIELD_REGULATOR_ITEM = ITEMS.registerSimpleBlockItem(
            "field_regulator", ModBlocks.FIELD_REGULATOR);

    public static final DeferredItem<BlockItem> SUPERPOSITION_POD_ITEM = ITEMS.registerSimpleBlockItem(
            "superposition_pod", ModBlocks.SUPERPOSITION_POD, props -> props.rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> POD_CRADLE_ITEM = ITEMS.registerSimpleBlockItem(
            "pod_cradle", ModBlocks.POD_CRADLE);
    public static final DeferredItem<BlockItem> POD_PLATING_ITEM = ITEMS.registerSimpleBlockItem(
            "pod_plating", ModBlocks.POD_PLATING);
    public static final DeferredItem<BlockItem> POD_RESCUE_MODULE_ITEM = ITEMS.registerItem(
            "pod_rescue_module", props -> new TooltipBlockItem(ModBlocks.POD_RESCUE_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> POD_REGENERATION_MODULE_ITEM = ITEMS.registerItem(
            "pod_regeneration_module", props -> new TooltipBlockItem(ModBlocks.POD_REGENERATION_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> POD_HARDENING_MODULE_ITEM = ITEMS.registerItem(
            "pod_hardening_module", props -> new TooltipBlockItem(ModBlocks.POD_HARDENING_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> POD_WARD_MODULE_ITEM = ITEMS.registerItem(
            "pod_ward_module", props -> new TooltipBlockItem(ModBlocks.POD_WARD_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> POD_STASH_MODULE_ITEM = ITEMS.registerItem(
            "pod_stash_module", props -> new TooltipBlockItem(ModBlocks.POD_STASH_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> POD_CHARGE_MODULE_ITEM = ITEMS.registerItem(
            "pod_charge_module", props -> new TooltipBlockItem(ModBlocks.POD_CHARGE_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> POD_RELAY_MODULE_ITEM = ITEMS.registerItem(
            "pod_relay_module", props -> new TooltipBlockItem(ModBlocks.POD_RELAY_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
    public static final DeferredItem<BlockItem> UNFOLDING_ARRAY_ITEM = ITEMS.registerSimpleBlockItem(
            "unfolding_array", ModBlocks.UNFOLDING_ARRAY, props -> props.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> ARRAY_PYLON_ITEM = ITEMS.registerSimpleBlockItem(
            "array_pylon", ModBlocks.ARRAY_PYLON);
    public static final DeferredItem<TetherItem> TETHER = ITEMS.registerItem("tether",
            props -> new TetherItem(props.stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<BlockItem> POD_RECOVERY_MODULE_ITEM = ITEMS.registerItem(
            "pod_recovery_module", props -> new TooltipBlockItem(ModBlocks.POD_RECOVERY_MODULE.get(), props.useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
    public static final DeferredItem<SophonItem> SOPHON = ITEMS.registerItem("sophon",
            props -> new SophonItem(props.stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<BlockItem> QUANTUM_EXCITER_ITEM = ITEMS.registerSimpleBlockItem(
            "quantum_exciter", ModBlocks.QUANTUM_EXCITER);

    public static final DeferredItem<BlockItem> FLUX_SUPPRESSOR_ITEM = ITEMS.registerSimpleBlockItem(
            "flux_suppressor", ModBlocks.FLUX_SUPPRESSOR);
    public static final DeferredItem<BlockItem> ZENO_FIELD_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(
            "zeno_field_controller", ModBlocks.ZENO_FIELD_CONTROLLER);

    public static final DeferredItem<BlockItem> DECOHERENCE_PROJECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "decoherence_projector", ModBlocks.DECOHERENCE_PROJECTOR);
    public static final DeferredItem<BlockItem> HARVEST_LASER_ITEM = ITEMS.registerSimpleBlockItem(
            "harvest_laser", ModBlocks.HARVEST_LASER);
    public static final DeferredItem<BlockItem> RIFT_LENS_ITEM = ITEMS.registerSimpleBlockItem(
            "rift_lens", ModBlocks.RIFT_LENS);
    public static final DeferredItem<BlockItem> RIFT_ANCHOR_ITEM = ITEMS.registerSimpleBlockItem(
            "rift_anchor", ModBlocks.RIFT_ANCHOR);
    public static final DeferredItem<BlockItem> RIFT_STABILISER_ITEM = ITEMS.registerSimpleBlockItem(
            "rift_stabiliser", ModBlocks.RIFT_STABILISER);

    public static final DeferredItem<BlockItem> ANOMALY_CONTAINMENT_HALL_ITEM = ITEMS.registerSimpleBlockItem(
            "anomaly_containment_hall", ModBlocks.ANOMALY_CONTAINMENT_HALL);

    public static final DeferredItem<BlockItem> FLUX_DETECTOR_ITEM = ITEMS.registerSimpleBlockItem(
            "flux_detector", ModBlocks.FLUX_DETECTOR);
    public static final DeferredItem<BlockItem> CREATIVE_ENERGY_CELL_ITEM = ITEMS.registerItem(
            "creative_energy_cell", props -> new TooltipBlockItem(ModBlocks.CREATIVE_ENERGY_CELL.get(), props.useBlockDescriptionPrefix()));
    public static final DeferredItem<BlockItem> DEBUG_FIELD_EMITTER_ITEM = ITEMS.registerItem(
            "debug_field_emitter", props -> new TooltipBlockItem(ModBlocks.DEBUG_FIELD_EMITTER.get(), props.useBlockDescriptionPrefix()));

    public static final DeferredItem<BlockItem> ANOMALITE_CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem(
            "anomalite_crystal", ModBlocks.ANOMALITE_CRYSTAL);

    public static final DeferredItem<BlockItem> ANOMALITE_LATTICE_ITEM = ITEMS.registerSimpleBlockItem(
            "anomalite_lattice", ModBlocks.ANOMALITE_LATTICE);

    public static final DeferredItem<BlockItem> QUANTUM_ATTUNED_GLASS_ITEM = ITEMS.registerSimpleBlockItem(
            "quantum_attuned_glass", ModBlocks.QUANTUM_ATTUNED_GLASS);

    public static final DeferredItem<BlockItem> BUDDING_ANOMALITE_ITEM = ITEMS.registerItem(
            "budding_anomalite",
            props -> new BlockItem(ModBlocks.BUDDING_ANOMALITE.get(), props.useBlockDescriptionPrefix()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                            Consumer<Component> tip, TooltipFlag flag) {
                    tip.accept(Component.translatable("block.quantimium.budding_anomalite.tooltip")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    public static final DeferredItem<BlockItem> STABILISED_PORTAL_FRAME_ITEM = ITEMS.registerSimpleBlockItem(
            "stabilised_portal_frame", ModBlocks.STABILISED_PORTAL_FRAME);

    public static final DeferredItem<Item> ANOMALITE_SHARD = ITEMS.registerItem("anomalite_shard",
            props -> new Item(props));

    public static final DeferredItem<Item> ANOMALITE_DUST = ITEMS.registerItem("anomalite_dust",
            props -> new Item(props));

    /** Anomalite shards sealed and charged: what a Decoherence Projector burns. */
    public static final DeferredItem<AnomaliteCellItem> ANOMALITE_CELL = ITEMS.registerItem("anomalite_cell",
            props -> new AnomaliteCellItem(props.stacksTo(16)));

    /** A shard of Anomalite set in a mount: the Decoherence Lance's light, built into it. */
    public static final DeferredItem<Item> ANOMALITE_EMITTER = ITEMS.registerItem("anomalite_emitter",
            props -> new Item(props) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                            Consumer<Component> tip, TooltipFlag flag) {
                    tip.accept(Component.translatable("item.quantimium.anomalite_emitter.tooltip")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    public static final DeferredItem<TesseractItem> TESSERACT = ITEMS.registerItem("tesseract",
            props -> new TesseractItem(props.stacksTo(1), false));

    public static final DeferredItem<TesseractItem> SEMI_STABLE_TESSERACT =
            ITEMS.registerItem("semi_stable_tesseract",
                    props -> new TesseractItem(props.stacksTo(1), true));

    public static final DeferredItem<FoldedTesseractItem> FOLDED_TESSERACT = ITEMS.registerItem("folded_tesseract",
            props -> new FoldedTesseractItem(props.stacksTo(1)));

    public static final DeferredItem<BlockItem> FOLD_PYLON_ITEM = ITEMS.registerSimpleBlockItem(
            "fold_pylon", ModBlocks.FOLD_PYLON);
    public static final DeferredItem<BlockItem> FOLD_RAIL_ITEM = ITEMS.registerSimpleBlockItem(
            "fold_rail", ModBlocks.FOLD_RAIL);
    public static final DeferredItem<BlockItem> FOLD_CORE_ITEM = ITEMS.registerSimpleBlockItem(
            "fold_core", ModBlocks.FOLD_CORE);

    public static final DeferredItem<SingularityItem> SINGULARITY = ITEMS.registerItem("singularity",
            props -> new SingularityItem(props.stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final DeferredItem<BlockItem> HORIZON_CORE_ITEM = ITEMS.registerSimpleBlockItem(
            "horizon_core", ModBlocks.HORIZON_CORE);
    public static final DeferredItem<BlockItem> REACTOR_PLINTH_ITEM = ITEMS.registerSimpleBlockItem(
            "reactor_plinth", ModBlocks.REACTOR_PLINTH);
    public static final DeferredItem<BlockItem> LIT_REACTOR_PLINTH_ITEM = ITEMS.registerSimpleBlockItem(
            "lit_reactor_plinth", ModBlocks.LIT_REACTOR_PLINTH);
    public static final DeferredItem<BlockItem> RING_EMITTER_ITEM = ITEMS.registerSimpleBlockItem(
            "ring_emitter", ModBlocks.RING_EMITTER);
    public static final DeferredItem<BlockItem> CATALYST_BAY_ITEM = ITEMS.registerSimpleBlockItem(
            "catalyst_bay", ModBlocks.CATALYST_BAY);
    public static final DeferredItem<BlockItem> REACTOR_INPUT_PORT_ITEM = ITEMS.registerSimpleBlockItem(
            "reactor_input_port", ModBlocks.REACTOR_INPUT_PORT);
    public static final DeferredItem<BlockItem> REACTOR_OUTPUT_PORT_ITEM = ITEMS.registerSimpleBlockItem(
            "reactor_output_port", ModBlocks.REACTOR_OUTPUT_PORT);
    public static final DeferredItem<BlockItem> REACTOR_ENERGY_PORT_ITEM = ITEMS.registerSimpleBlockItem(
            "reactor_energy_port", ModBlocks.REACTOR_ENERGY_PORT);
    public static final DeferredItem<BlockItem> REACTOR_MATERIALISER_PORT_ITEM = ITEMS.registerSimpleBlockItem(
            "reactor_materialiser_port", ModBlocks.REACTOR_MATERIALISER_PORT);

    public static final DeferredItem<UnrealisedMatterItem> UNREALISED_MATTER = ITEMS.registerItem("unrealised_matter",
            props -> new UnrealisedMatterItem(props));

    public static final DeferredItem<Item> QUANTIMIUM_TRACE = ITEMS.registerItem("quantimium_trace",
            props -> new Item(props));

    public static final DeferredItem<Item> BASIC_DECOHERENCE_MATRIX = ITEMS.registerItem(
            "basic_decoherence_matrix", props -> new Item(props));

    public static final DeferredItem<FluxMeterItem> FLUX_METER = ITEMS.registerItem("flux_meter",
            props -> new FluxMeterItem(props.stacksTo(1)));

    public static final DeferredItem<MirrorLensItem> MIRROR_LENS = ITEMS.registerItem("mirror_lens",
            props -> new MirrorLensItem(props.stacksTo(1)));

    public static final DeferredItem<Item> ANOMALY_FRAGMENT = ITEMS.registerItem("anomaly_fragment",
            props -> new Item(props) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                            Consumer<Component> tip, TooltipFlag flag) {
                    tip.accept(Component.translatable("item.quantimium.anomaly_fragment.tooltip")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    public static final DeferredItem<DecoherenceLanceItem> DECOHERENCE_LANCE = ITEMS.registerItem("decoherence_lance",
            props -> new DecoherenceLanceItem(props.stacksTo(1)));

    public static final DeferredItem<Item> RIFT_RESIDUE = ITEMS.registerItem("rift_residue",
            props -> new Item(props) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                            Consumer<Component> tip, TooltipFlag flag) {
                    tip.accept(Component.translatable("item.quantimium.rift_residue.tooltip")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    /** Drawn out of a held Veiled by a Harvest Laser. For upgrades and capstones. */
    public static final DeferredItem<Item> VEIL_THREAD = ITEMS.registerItem("veil_thread",
            props -> new Item(props) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                            Consumer<Component> tip, TooltipFlag flag) {
                    tip.accept(Component.translatable("item.quantimium.veil_thread.tooltip")
                            .withStyle(ChatFormatting.GRAY));
                }
            });

    /** Opens a rift: held on a Rift Anchor, wild on the ground where anomaly can feed it. Lights a Stabilised Portal. */
    public static final DeferredItem<RiftSeedItem> RIFT_SEED = ITEMS.registerItem("rift_seed",
            props -> new RiftSeedItem(props.stacksTo(16)));

    /** Testing only while the Veiled has no release or behaviour: body tone and rift edge. */
    public static final DeferredItem<SpawnEggItem> VEILED_SPAWN_EGG = ITEMS.registerItem("veiled_spawn_egg",
            // Its own texture, painted by tools/original_art.py (26.1 has no tinted egg template).
            props -> new SpawnEggItem(props.spawnEgg(ModEntities.VEILED.get())));

    public static void register(IEventBus eventBus) {
        // Renamed before release; worlds from then keep their lenses.
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Quantimium.MODID, "rift_ring"),
                Identifier.fromNamespaceAndPath(Quantimium.MODID, "rift_lens"));
        ITEMS.register(eventBus);
    }
}
