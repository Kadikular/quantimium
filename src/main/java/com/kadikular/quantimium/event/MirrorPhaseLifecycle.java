package com.kadikular.quantimium.event;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.flux.MirrorMiteSpawner;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.network.MirrorAtmospherePayload;
import com.kadikular.quantimium.network.MirrorFloraPayload;
import com.kadikular.quantimium.network.MirrorPhaseSyncPayload;
import com.kadikular.quantimium.phase.MirrorPhase;
import com.kadikular.quantimium.phase.MirrorPhaseState;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.WorldRiftManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Quantimium.MODID)
public final class MirrorPhaseLifecycle {

    private MirrorPhaseLifecycle() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MirrorPhase.tickStabilisedGate(player);
        long now = player.level().getGameTime();

        if (!MirrorPhase.isPhased(player)) {
            if (WorldRiftManager.tryUseVisible(player)) {
                player.sendOverlayMessage(
                        Component.translatable("message.quantimium.mirror_phase.entered"));
            }
            return;
        }

        MirrorPhaseState state = player.getData(ModAttachments.MIRROR_PHASE);
        if (now - state.entryTick() >= Config.mirrorTimeoutTicks()) {
            MirrorPhase.exit(player, MirrorPhase.ExitReason.TIMEOUT);
            player.sendOverlayMessage(Component.translatable("message.quantimium.mirror_phase.timeout"));
            return;
        }
        if (WorldRiftManager.tryUseVisible(player)) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.mirror_phase.returned"));
            return;
        }
        if (now % 20L == 0L) {
            WorldRiftManager.maintainOwnedReturn(player, now);
            MirrorMiteSpawner.tick(player);
            QuantumFlux.Neighbourhood field = QuantumFlux.chunk(player.level(), player.blockPosition());
            PacketDistributor.sendToPlayer(player,
                    new MirrorAtmospherePayload((float) field.flux(), (float) field.anomaly()));
            PacketDistributor.sendToPlayer(player, MirrorFloraPayload.around(player));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setData(ModAttachments.MIRROR_PHASE, MirrorPhaseState.inactive());
            MirrorPhase.broadcast(player, false);
            PacketDistributor.sendToPlayer(player, MirrorFloraPayload.clear());
            WorldRiftManager.syncPlayer(player);
            FluxRiftManager.syncPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) return;
        if (!(event.getTarget() instanceof ServerPlayer other)) return;
        PacketDistributor.sendToPlayer(watcher,
                new MirrorPhaseSyncPayload(other.getUUID(), MirrorPhase.isPhased(other)));
        PacketDistributor.sendToPlayer(other,
                new MirrorPhaseSyncPayload(watcher.getUUID(), MirrorPhase.isPhased(watcher)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MirrorPhase.exit(player, MirrorPhase.ExitReason.LOGOUT);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MirrorPhase.exit(player, MirrorPhase.ExitReason.DIMENSION_CHANGE);
            WorldRiftManager.syncPlayer(player);
            FluxRiftManager.syncPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MirrorPhase.exit(player, MirrorPhase.ExitReason.DEATH);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MirrorPhase.exit(player, MirrorPhase.ExitReason.DEATH);
        }
    }
}
