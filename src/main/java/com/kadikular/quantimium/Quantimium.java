// Path: src/main/java/com/kadikular/quantimium/Quantimium.java
package com.kadikular.quantimium;

import com.kadikular.quantimium.util.LegacyFluids;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.energy.InfiniteEnergyHandler;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.entity.MaterialiserBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.SideConfigurable;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.client.ClientPayloadHandler;
import com.kadikular.quantimium.compat.ae2.Ae2Content;
import com.kadikular.quantimium.flux.FieldMapSync;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModCreativeTabs;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.init.ModParticles;
import com.kadikular.quantimium.init.ModRecipeTypes;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import com.kadikular.quantimium.item.TetherItem;
import com.kadikular.quantimium.menu.GhostFilterMenu;
import com.kadikular.quantimium.menu.QuantumCrafterMenu;
import com.kadikular.quantimium.network.CollapseOutcomesPayload;
import com.kadikular.quantimium.network.FieldMapPayload;
import com.kadikular.quantimium.network.FieldMapRequestPayload;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import com.kadikular.quantimium.network.FluxRiftStrikePayload;
import com.kadikular.quantimium.network.FluxRiftSyncPayload;
import com.kadikular.quantimium.network.FluxRiftTouchPayload;
import com.kadikular.quantimium.network.HorizonRequestPayload;
import com.kadikular.quantimium.network.HorizonViewPayload;
import com.kadikular.quantimium.network.MaterialiserOptionsPayload;
import com.kadikular.quantimium.menu.HorizonCoreMenu;
import com.kadikular.quantimium.network.MirrorAtmospherePayload;
import com.kadikular.quantimium.network.MirrorFloraPayload;
import com.kadikular.quantimium.network.MirrorPhaseSyncPayload;
import com.kadikular.quantimium.network.MirrorRiftSyncPayload;
import com.kadikular.quantimium.network.OpenPodScreenPayload;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.OpenSlotConfigPayload;
import com.kadikular.quantimium.network.OpenStashPayload;
import com.kadikular.quantimium.network.PodActionPayload;
import com.kadikular.quantimium.network.PodEffectPayload;
import com.kadikular.quantimium.network.RenamePodPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import com.kadikular.quantimium.network.RequestSlotConfigPayload;
import com.kadikular.quantimium.network.SetBatchSizePayload;
import com.kadikular.quantimium.network.SetCrafterLockPayload;
import com.kadikular.quantimium.network.SetCrafterPagePayload;
import com.kadikular.quantimium.network.SetGhostFilterPayload;
import com.kadikular.quantimium.network.SetMaterialiserTargetPayload;
import com.kadikular.quantimium.network.SuperpositionFlashPayload;
import com.kadikular.quantimium.network.ToggleEngagePayload;
import com.kadikular.quantimium.network.UpdateSideConfigPayload;
import com.kadikular.quantimium.network.UpdateSlotMappingPayload;
import com.kadikular.quantimium.network.VeiledFlickerPayload;
import com.kadikular.quantimium.network.VeiledGlimpsePayload;
import com.kadikular.quantimium.network.VeiledSightingEndPayload;
import com.kadikular.quantimium.network.VeiledSightingPayload;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.VeiledManager;
import com.kadikular.quantimium.superposition.PodChunkLoading;
import com.kadikular.quantimium.superposition.PodStructure;
import com.kadikular.quantimium.superposition.Recovery;
import com.kadikular.quantimium.superposition.Relay;
import com.kadikular.quantimium.superposition.Superposition;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(Quantimium.MODID)
public class Quantimium {
    public static final String MODID = "quantimium";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Quantimium(IEventBus modEventBus, ModContainer container) {
        ModBlocks.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModRecipeTypes.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntities.register(modEventBus);
        ModParticles.register(modEventBus);
        // Only with AE2 installed: Ae2Content uses AE2's API, so it must not even load without it.
        if (ModList.get().isLoaded("ae2")) Ae2Content.init(modEventBus);

        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPackets);
        modEventBus.addListener(PodChunkLoading::register);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Receive-only, so a charged lance cannot double as a portable battery.
        event.registerItem(
                Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(),
                        DecoherenceLanceItem.CAPACITY, DecoherenceLanceItem.MAX_RECEIVE, 0),
                ModItems.DECOHERENCE_LANCE.get()
        );
        event.registerItem(
                Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(),
                        TetherItem.CAPACITY, TetherItem.MAX_RECEIVE, 0),
                ModItems.TETHER.get()
        );

        // Endless, for any cable that pulls; it also pushes into its neighbours each tick.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.CREATIVE_ENERGY_CELL_BE.get(),
                (be, context) -> InfiniteEnergyHandler.INSTANCE
        );

        // Quantum Simulator automation capabilities
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.QUANTUM_SIMULATOR_BE.get(),
                (be, context) -> LegacyItems.of(be.getAutomationItemHandler(context), be.getInventory())
        );

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.QUANTUM_SIMULATOR_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModBlockEntities.QUANTUM_SIMULATOR_BE.get(),
                (be, context) -> LegacyFluids.of(be.getAutomationFluidHandler(context),
                        be.getFluidTanks()::snapshot, be.getFluidTanks()::restore)
        );

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.FIELD_CONTROL_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.FIELD_CONTROL_BE.get(),
                (be, context) -> be.getAutomationItemHandler()
        );

        // A pod takes power at the capsule or through any block of its cradle.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.SUPERPOSITION_POD_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );
        event.registerBlock(
                Capabilities.Energy.BLOCK,
                (level, pos, state, be, context) -> {
                    SuperpositionPodBlockEntity pod = PodStructure.podOver(level, pos);
                    return pod == null ? null : pod.getEnergyStorage();
                },
                ModBlocks.POD_CRADLE.get(), ModBlocks.POD_PLATING.get(), ModBlocks.POD_RESCUE_MODULE.get(),
                ModBlocks.POD_REGENERATION_MODULE.get(), ModBlocks.POD_HARDENING_MODULE.get(),
                ModBlocks.POD_WARD_MODULE.get(), ModBlocks.POD_STASH_MODULE.get(), ModBlocks.POD_CHARGE_MODULE.get(),
                ModBlocks.POD_RELAY_MODULE.get(), ModBlocks.POD_RECOVERY_MODULE.get()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.HORIZON_CORE_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.REACTOR_PORT_BE.get(),
                (be, context) -> be.energyHandler()
        );
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.REACTOR_PORT_BE.get(),
                (be, context) -> be.itemHandler()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.FOLD_CORE_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.UNFOLDING_ARRAY_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.UNFOLDING_ARRAY_BE.get(),
                (be, context) -> be.getInventory()
        );

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.ENTANGLED_DOCK_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.MATERIALISER_BE.get(),
                (be, context) -> LegacyItems.of(be.getAutomationItemHandler(context), be.getInventory())
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.MATERIALISER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.QUANTUM_OBSERVATION_CHAMBER_BE.get(),
                (be, context) -> LegacyItems.of(be.getAutomationItemHandler(context), be.getInventory())
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.QUANTUM_OBSERVATION_CHAMBER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.QUANTUM_CRAFTER_BE.get(),
                (be, context) -> LegacyItems.of(be.getAutomationItemHandler(context), be.getInventory())
        );

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.QUANTUM_CRAFTER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.QUANTUM_FOUNDRY_BE.get(),
                (be, context) -> be.isFormed() ? be.getAutomationItemHandler() : null
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.QUANTUM_FOUNDRY_BE.get(),
                (be, context) -> be.isFormed() ? be.getEnergyStorage() : null
        );
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.QUANTUM_FOUNDRY_PART_BE.get(),
                (be, context) -> be.host() == null ? null : be.host().itemPort()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.QUANTUM_FOUNDRY_PART_BE.get(),
                (be, context) -> be.host() == null ? null : be.host().energyPort()
        );

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.QUANTUM_EXCITER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );


        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.ZENO_FIELD_CONTROLLER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.DECOHERENCE_PROJECTOR_BE.get(),
                (be, context) -> be.getCellInput()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.DECOHERENCE_PROJECTOR_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );


        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.ANOMALY_CONTAINMENT_HALL_BE.get(),
                (be, context) -> be.energyPort()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.RIFT_STABILISER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.RIFT_ANCHOR_BE.get(),
                (be, context) -> be.getAutomationItemHandler()
        );

        // Hall arms are plain blocks: each one feeds residue to whichever hall counts it.
        event.registerBlock(
                Capabilities.Item.BLOCK,
                (level, pos, state, be, context) -> ContainmentHallStructure.residuePortAt(level, pos, state),
                ModBlocks.QUANTUM_FOUNDRY_CONDUIT.get(),
                ModBlocks.QUANTUM_FOUNDRY_PILLAR.get(),
                ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK.get()
        );

        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.HARVEST_LASER_BE.get(),
                (be, context) -> be.getOutputPort()
        );
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.HARVEST_LASER_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                ModBlockEntities.BUDDING_ANOMALITE_BE.get(),
                (be, context) -> be.getEnergyStorage()
        );


        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                ModBlockEntities.TESSERACT_STABILIZER_BE.get(),
                (be, context) -> be.getAutomationItemHandler(context)
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                ModBlockEntities.TESSERACT_STABILIZER_BE.get(),
                (be, context) -> be.getAutomationFluidHandler(context)
        );
    }

    private void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                ToggleEngagePayload.TYPE,
                ToggleEngagePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.player() instanceof ServerPlayer player
                                && player.level().getBlockEntity(payload.pos()) instanceof QuantumSimulatorBlockEntity be
                                && be.isUsableBy(player)) {
                            be.toggleEngage(player);
                        }
                    });
                }
        );

        registrar.playToServer(
            SetBatchSizePayload.TYPE,
            SetBatchSizePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player().level().getBlockEntity(payload.pos()) instanceof QuantumSimulatorBlockEntity be
                        && be.isUsableBy(context.player())) {
                    be.setBatchSize(payload.size());
                }
            })
        );

        registrar.playToServer(
            SetCrafterLockPayload.TYPE,
            SetCrafterLockPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player().level().getBlockEntity(payload.pos()) instanceof QuantumCrafterBlockEntity be
                        && be.isUsableBy(context.player())) {
                    be.setRecipeLocked(payload.locked());
                    be.rebuildPreview();
                }
            })
        );

        registrar.playToServer(
            SetGhostFilterPayload.TYPE,
            SetGhostFilterPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player
                        && player.containerMenu.containerId == payload.containerId()
                        && player.containerMenu instanceof GhostFilterMenu menu) {
                    menu.setGhost(payload.index(), payload.stack());
                }
            })
        );

        registrar.playToServer(
            SetCrafterPagePayload.TYPE,
            SetCrafterPagePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player
                        && player.containerMenu.containerId == payload.containerId()
                        && player.containerMenu instanceof QuantumCrafterMenu menu) {
                    menu.setOutputPage(payload.page());
                }
            })
        );

        registrar.playToServer(
            RequestSlotConfigPayload.TYPE,
            RequestSlotConfigPayload.STREAM_CODEC,
            (payload, context) -> {
                context.enqueueWork(() -> {
                    if (!(context.player().level().getBlockEntity(payload.pos()) instanceof QuantumSimulatorBlockEntity be)) return;
                    if (!be.isUsableBy(context.player())) return;
                    if (payload.autoDetect()) {
                        be.autoDetectSlotMappings();
                    }
                    context.reply(new OpenSlotConfigPayload(payload.pos(), be.describeMachineSlots(),
                            be.getSlotMappings(), be.describeMachineTanks(), be.getFluidMappings()));
                });
            }
        );

        registrar.playToServer(
            UpdateSlotMappingPayload.TYPE,
            UpdateSlotMappingPayload.STREAM_CODEC,
            (payload, context) -> {
                context.enqueueWork(() -> {
                    if (!(context.player().level().getBlockEntity(payload.pos()) instanceof QuantumSimulatorBlockEntity be)) return;
                    if (!be.isUsableBy(context.player())) return;
                    be.applyAllMappings(payload.mappings(), payload.fluidMappings());
                });
            }
        );

        registrar.playToClient(
            OpenSlotConfigPayload.TYPE,
            OpenSlotConfigPayload.STREAM_CODEC,
            (payload, context) -> {
                context.enqueueWork(() -> ClientPayloadHandler.handleOpenSlotConfig(payload));
            }
        );

        registrar.playToServer(
            RequestSideConfigPayload.TYPE,
            RequestSideConfigPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (!(context.player().level().getBlockEntity(payload.pos()) instanceof SideConfigurable be)) return;
                if (!be.isUsableBy(context.player())) return;
                context.reply(new OpenSideConfigPayload(
                        payload.pos(), be.getSideConfigs(), be.sideAutomationProfile()));
            })
        );

        registrar.playToServer(
            UpdateSideConfigPayload.TYPE,
            UpdateSideConfigPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (!(context.player().level().getBlockEntity(payload.pos()) instanceof SideConfigurable be)) return;
                if (!be.isUsableBy(context.player())) return;
                be.applySideConfigs(payload.configs());
            })
        );

        registrar.playToClient(
            OpenSideConfigPayload.TYPE,
            OpenSideConfigPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleOpenSideConfig(payload))
        );

        registrar.playToServer(
                PodActionPayload.TYPE,
                PodActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (payload.action() == PodActionPayload.TETHER_SWAP) {
                        Superposition.tetherSwap(player, payload.target());
                        return;
                    }
                    if (player.distanceToSqr(payload.pos().getCenter()) > 64.0) return;
                    if (payload.action() == PodActionPayload.RECOVER) {
                        Recovery.recover(player, payload.pos(), payload.target());
                    } else if (payload.action() == PodActionPayload.SWAP) {
                        Superposition.swap(player, payload.pos(), payload.target());
                    } else if (payload.action() == PodActionPayload.RELAY) {
                        Relay.startTrip(player, payload.pos(), payload.target());
                    } else {
                        Superposition.act(player, payload.pos(), payload.action());
                    }
                })
        );
        registrar.playToServer(
                RenamePodPayload.TYPE,
                RenamePodPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.distanceToSqr(payload.pos().getCenter()) <= 64.0) {
                        Superposition.rename(player, payload.pos(), payload.name());
                    }
                })
        );
        registrar.playToServer(
                OpenStashPayload.TYPE,
                OpenStashPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) Superposition.openStash(player);
                })
        );
        registrar.playToClient(
                OpenPodScreenPayload.TYPE,
                OpenPodScreenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleOpenPodScreen(payload))
        );
        registrar.playToClient(
                PodEffectPayload.TYPE,
                PodEffectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handlePodEffect(payload))
        );
        registrar.playToClient(
                SuperpositionFlashPayload.TYPE,
                SuperpositionFlashPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleSuperpositionFlash(payload))
        );

        registrar.playToClient(
                MirrorPhaseSyncPayload.TYPE,
                MirrorPhaseSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMirrorPhase(payload))
        );
        registrar.playToClient(
                FieldSurveyPayload.TYPE,
                FieldSurveyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleFieldSurvey(payload))
        );
        registrar.playToClient(
                HorizonViewPayload.TYPE,
                HorizonViewPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleHorizonView(payload))
        );
        registrar.playToServer(
                HorizonRequestPayload.TYPE,
                HorizonRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof HorizonCoreMenu menu
                            && menu.getBlockEntity().getBlockPos().equals(payload.pos())
                            && menu.stillValid(context.player())) {
                        menu.request(payload.item(), payload.count());
                    }
                })
        );
        registrar.playToClient(
                MaterialiserOptionsPayload.TYPE,
                MaterialiserOptionsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMaterialiserOptions(payload))
        );
        registrar.playToServer(
                SetMaterialiserTargetPayload.TYPE,
                SetMaterialiserTargetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getBlockEntity(payload.pos()) instanceof MaterialiserBlockEntity be
                            && be.isUsableBy(context.player())) {
                        be.setTarget(payload.target().orElse(null));
                    }
                })
        );
        registrar.playToClient(
                FieldMapPayload.TYPE,
                FieldMapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleFieldMap(payload))
        );
        registrar.playToServer(
                FieldMapRequestPayload.TYPE,
                FieldMapRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) FieldMapSync.request(player, payload.wanted());
                })
        );
        registrar.playToClient(
                com.kadikular.quantimium.network.FieldMonitorPayload.TYPE,
                com.kadikular.quantimium.network.FieldMonitorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleFieldMonitor(payload))
        );
        registrar.playToClient(
                MirrorAtmospherePayload.TYPE,
                MirrorAtmospherePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMirrorAtmosphere(payload))
        );
        registrar.playToClient(
                MirrorFloraPayload.TYPE,
                MirrorFloraPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMirrorFlora(payload))
        );
        registrar.playToClient(
                MirrorRiftSyncPayload.TYPE,
                MirrorRiftSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMirrorRifts(payload))
        );
        registrar.playToClient(
                FluxRiftSyncPayload.TYPE,
                FluxRiftSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleFluxRifts(payload))
        );
        registrar.playToClient(
                FluxRiftStrikePayload.TYPE,
                FluxRiftStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleFluxRiftStrike(payload))
        );
        registrar.playToClient(
                VeiledSightingPayload.TYPE,
                VeiledSightingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleVeiledSighting(payload))
        );
        registrar.playToClient(
                VeiledGlimpsePayload.TYPE,
                VeiledGlimpsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleVeiledGlimpse(payload))
        );
        registrar.playToClient(
                VeiledFlickerPayload.TYPE,
                VeiledFlickerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleVeiledFlicker(payload))
        );
        registrar.playToServer(
                FluxRiftTouchPayload.TYPE,
                FluxRiftTouchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) FluxRiftManager.touched(player, payload.rift());
                })
        );
        registrar.playToServer(
                VeiledSightingEndPayload.TYPE,
                VeiledSightingEndPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        VeiledManager.sightingEnded(player, payload.id());
                    }
                })
        );
        registrar.playToClient(
                CollapseOutcomesPayload.TYPE,
                CollapseOutcomesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientPayloadHandler.handleCollapseOutcomes(payload))
        );
    }
}