// Path: src/main/java/com/kadikular/quantimium/client/ClientModEvents.java
package com.kadikular.quantimium.client;

import com.kadikular.quantimium.client.renderer.AnimatedItemModel;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.entity.ClientAvatarEntity;
import com.kadikular.quantimium.item.MirrorLensItem;
import com.google.common.reflect.TypeToken;
import com.kadikular.quantimium.client.renderer.MirrorPhasePlayerRenderer;
import java.lang.reflect.Field;
import java.util.EnumMap;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.bus.api.EventPriority;
import com.kadikular.quantimium.client.model.MirrorLensModel;
import com.kadikular.quantimium.client.renderer.MirrorLensLayer;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.model.VeiledModel;
import com.kadikular.quantimium.client.renderer.QuantumSimulatorBER;
import com.kadikular.quantimium.client.renderer.QuantumCrafterBER;
import com.kadikular.quantimium.client.renderer.QuantumFoundryBER;
import com.kadikular.quantimium.client.renderer.ContainmentHallBER;
import com.kadikular.quantimium.client.renderer.TesseractStabilizerBER;
import com.kadikular.quantimium.client.renderer.StabilisedPortalBER;
import com.kadikular.quantimium.client.renderer.DecoherenceProjectorBER;
import com.kadikular.quantimium.client.renderer.HarvestLaserBER;
import com.kadikular.quantimium.client.renderer.RiftStabiliserBER;
import com.kadikular.quantimium.client.renderer.EntangledDockBER;
import com.kadikular.quantimium.client.renderer.SuperpositionPodBER;
import com.kadikular.quantimium.client.renderer.CatalystBayBER;
import com.kadikular.quantimium.client.renderer.FoldCoreBER;
import com.kadikular.quantimium.client.renderer.HorizonCoreBER;
import com.kadikular.quantimium.client.renderer.UnfoldingArrayBER;
import com.kadikular.quantimium.client.renderer.FieldDoubleRenderer;
import com.kadikular.quantimium.client.renderer.MirrorEndermiteRenderer;
import com.kadikular.quantimium.client.renderer.QuantumRenderTypes;
import com.kadikular.quantimium.client.renderer.VeiledRenderer;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModParticles;
import com.kadikular.quantimium.client.particle.FieldParticle;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.client.renderer.PhaseOnlyModel;
import com.kadikular.quantimium.client.renderer.UnrealisedOreModel;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.minecraft.resources.Identifier;
import com.kadikular.quantimium.client.renderer.SingularityItemRenderer;
import com.kadikular.quantimium.client.renderer.SophonItemRenderer;
import com.kadikular.quantimium.client.renderer.TesseractItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    /** Compiled up front rather than on first use, so a beam's first frame doesn't hitch. */
    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        QuantumRenderTypes.PIPELINES.forEach(event::registerPipeline);
    }

    /** Shader packs draw our pipelines only once Iris is told what they are. */
    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(com.kadikular.quantimium.compat.iris.IrisPipelines::assign);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Every entity needs a renderer: one without crashes the client the moment it comes into view.
        event.registerEntityRenderer(ModEntities.MIRROR_ENDERMITE.get(), MirrorEndermiteRenderer::new);
        event.registerEntityRenderer(ModEntities.VEILED.get(), VeiledRenderer::new);
        event.registerEntityRenderer(ModEntities.FIELD_DOUBLE.get(), FieldDoubleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_SIMULATOR_BE.get(), QuantumSimulatorBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_CRAFTER_BE.get(), QuantumCrafterBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_FOUNDRY_BE.get(), QuantumFoundryBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ANOMALY_CONTAINMENT_HALL_BE.get(), ContainmentHallBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TESSERACT_STABILIZER_BE.get(), TesseractStabilizerBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STABILISED_PORTAL_BE.get(), StabilisedPortalBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECOHERENCE_PROJECTOR_BE.get(), DecoherenceProjectorBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HARVEST_LASER_BE.get(), HarvestLaserBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RIFT_STABILISER_BE.get(), RiftStabiliserBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENTANGLED_DOCK_BE.get(), EntangledDockBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUPERPOSITION_POD_BE.get(), SuperpositionPodBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.UNFOLDING_ARRAY_BE.get(), UnfoldingArrayBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FOLD_CORE_BE.get(), FoldCoreBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HORIZON_CORE_BE.get(), HorizonCoreBER::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CATALYST_BAY_BE.get(), CatalystBayBER::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIELD_GLOW.get(), FieldParticle.Provider::glow);
        event.registerSpriteSet(ModParticles.FIELD_SOFT.get(), FieldParticle.Provider::soft);
    }

    /** The Tesseract and Sophon draw themselves; their item definitions name these types. */
    @SubscribeEvent
    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Quantimium.MODID, "animated"), AnimatedItemModel.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerItemRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Quantimium.MODID, "tesseract"), TesseractItemRenderer.Unbaked.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(Quantimium.MODID, "sophon"), SophonItemRenderer.Unbaked.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(Quantimium.MODID, "singularity"), SingularityItemRenderer.Unbaked.MAP_CODEC);
    }

    /**
     * Swaps the ore's model for one that resolves a facade per position. Done here rather than with a
     * block-entity renderer so the facade is baked into the chunk mesh: it gets real ambient occlusion
     * and face culling, and cannot vanish when no block entity exists.
     */
    @SubscribeEvent
    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (BlockState state : ModBlocks.UNREALISED_ORE.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(state, (key, original) -> new UnrealisedOreModel(original));
        }
        for (BlockState state : ModBlocks.ANOMALITE_CRYSTAL.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(state, (key, original) -> new PhaseOnlyModel(original));
        }
    }

    /**
     * The simulator's panels, one layer per tint index, and the mirror palette over vanilla's plants.
     * Item tints are data now (the item definition's {@code tints}), so held plants keep their colours.
     */
    @SubscribeEvent
    public static void registerTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(QuantumPanelColours.source(QuantumPanelColours.BAND_TINT),
                QuantumPanelColours.source(QuantumPanelColours.EMITTER_TINT)), ModBlocks.QUANTUM_SIMULATOR.get());
        MirrorPhaseColours.register(event);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VeiledModel.LAYER, VeiledModel::createLayer);
        event.registerLayerDefinition(MirrorLensModel.LAYER, MirrorLensModel::createLayer);
    }

    /**
     * Vanilla builds player renderers from an immutable map; swap in our subclass once layers exist.
     *
     * <p>The new renderer takes over the original's model and layers. Building it fresh dropped every
     * layer added before this runs (last): other mods' capes and cosmetics, and the worn Mirror Lens.
     * Those layers draw from the original model, which the new renderer now poses, so they stay put.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void wrapPlayerRenderers(EntityRenderersEvent.AddLayers event) {
        EntityRendererProvider.Context context = event.getContext();
        Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>> wrapped = new EnumMap<>(PlayerModelType.class);
        for (PlayerModelType model : event.getSkins()) {
            MirrorPhasePlayerRenderer renderer = new MirrorPhasePlayerRenderer(context, model == PlayerModelType.SLIM);
            AvatarRenderer<AbstractClientPlayer> original = event.getPlayerRenderer(model);
            if (original != null) adopt(renderer, original);
            wrapped.put(model, renderer);
        }
        try {
            Field field = EntityRenderDispatcher.class.getDeclaredField("playerRenderers");
            field.setAccessible(true);
            field.set(context.getEntityRenderDispatcher(), wrapped);
        } catch (ReflectiveOperationException e) {
            Quantimium.LOGGER.error("Could not install mirror-phase player renderer", e);
        }
    }

    /** Gives {@code renderer} the model and layer list of {@code original}. */
    private static void adopt(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderer<?, ?, ?> original) {
        try {
            for (String name : new String[] {"model", "layers"}) {
                Field field = LivingEntityRenderer.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(renderer, field.get(original));
            }
        } catch (ReflectiveOperationException e) {
            Quantimium.LOGGER.error("Could not carry the player renderer's layers over; mod layers on players are lost", e);
        }
    }

    /** Whether a Mirror Lens is worn, for {@link MirrorLensLayer}: the humanoid render state drops non-armour head items. */
    @SubscribeEvent
    public static void markLensWearers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                (entity, state) -> state.setRenderData(MirrorLensLayer.WORN, MirrorLensItem.isWorn(entity)));
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                state.setRenderData(MirrorLensLayer.WORN, MirrorLensItem.isWorn(avatar));
            }
        });
    }

    /** A worn Mirror Lens shows on players (either arm width), mannequins and armour stands. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> player = event.getPlayerRenderer(skin);
            if (player != null) player.addLayer(new MirrorLensLayer<>(player, event.getEntityModels()));
            AvatarRenderer<ClientMannequin> mannequin = event.getMannequinRenderer(skin);
            if (mannequin != null) mannequin.addLayer(new MirrorLensLayer<>(mannequin, event.getEntityModels()));
        }
        if (event.getRenderer(EntityType.ARMOR_STAND) instanceof ArmorStandRenderer stand) {
            stand.addLayer(new MirrorLensLayer<>(stand, event.getEntityModels()));
        }
    }
}
