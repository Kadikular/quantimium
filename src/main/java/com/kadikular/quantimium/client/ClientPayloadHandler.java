package com.kadikular.quantimium.client;

import com.kadikular.quantimium.client.renderer.MirrorLightningRenderer;
import com.kadikular.quantimium.client.screen.QuantumCrafterScreen;
import com.kadikular.quantimium.client.screen.QuantumObservationChamberScreen;
import com.kadikular.quantimium.client.screen.QuantumSimulatorScreen;
import com.kadikular.quantimium.client.screen.TesseractStabilizerScreen;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.network.CollapseOutcomesPayload;
import com.kadikular.quantimium.network.MirrorFloraPayload;
import com.kadikular.quantimium.network.MirrorPhaseSyncPayload;
import com.kadikular.quantimium.network.MirrorRiftSyncPayload;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.OpenSlotConfigPayload;
import com.kadikular.quantimium.phase.MirrorPhaseState;
import com.kadikular.quantimium.phase.MirrorRiftKind;
import net.minecraft.client.Minecraft;

/** Client side of the mod's packets. Only ever loaded on a physical client. */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    public static void handleOpenPodScreen(com.kadikular.quantimium.network.OpenPodScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.kadikular.quantimium.client.screen.PodScreen screen
                && screen.pos().equals(payload.pos())) {
            screen.update(payload);
        } else {
            minecraft.setScreen(new com.kadikular.quantimium.client.screen.PodScreen(payload));
        }
    }

    public static void handlePodEffect(com.kadikular.quantimium.network.PodEffectPayload payload) {
        SuperpositionEffects.play(payload.pos(), payload.kind());
    }

    public static void handleSuperpositionFlash(com.kadikular.quantimium.network.SuperpositionFlashPayload payload) {
        SuperpositionHud.flash(payload.kind());
    }

    public static void handleCollapseOutcomes(CollapseOutcomesPayload payload) {
        ClientCollapseOutcomes.set(payload.outcomes());
    }

    public static void handleOpenSlotConfig(OpenSlotConfigPayload payload) {
        if (Minecraft.getInstance().screen instanceof QuantumSimulatorScreen screen) {
            screen.openSlotConfig(payload);
        }
    }

    public static void handleOpenSideConfig(OpenSideConfigPayload payload) {
        if (Minecraft.getInstance().screen instanceof QuantumSimulatorScreen screen) {
            screen.openSideConfig(payload);
        } else if (Minecraft.getInstance().screen instanceof QuantumCrafterScreen screen) {
            screen.openSideConfig(payload);
        } else if (Minecraft.getInstance().screen instanceof TesseractStabilizerScreen screen) {
            screen.openSideConfig(payload);
        } else if (Minecraft.getInstance().screen instanceof QuantumObservationChamberScreen screen) {
            screen.openSideConfig(payload);
        } else if (Minecraft.getInstance().screen instanceof com.kadikular.quantimium.client.screen.MaterialiserScreen screen) {
            screen.openSideConfig(payload);
        }
    }

    public static void handleMirrorPhase(MirrorPhaseSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !minecraft.player.getUUID().equals(payload.player())) {
            ClientPhaseState.setRemote(payload.player(), payload.active());
            return;
        }
        boolean changed = ClientPhaseState.isActive() != payload.active();
        ClientPhaseState.set(payload.active());
        if (minecraft.player != null) {
            minecraft.player.setData(ModAttachments.MIRROR_PHASE, payload.active()
                    ? new MirrorPhaseState(true,
                            minecraft.level == null ? 0L : minecraft.level.getGameTime(),
                            minecraft.player.level().dimension(), minecraft.player.position())
                    : MirrorPhaseState.inactive());
        }
        if (changed) {
            minecraft.getSoundManager().play(new MirrorTravelSound(1.0f, 0.85f));
        }
        if (!payload.active()) {
            MirrorRiftClientCache.hideKindInstant(MirrorRiftKind.RETURN);
            MirrorAtmosphereClient.clear();
            MirrorFloraClientCache.clear();
        } else {
            MirrorRiftClientCache.hideKindInstant(MirrorRiftKind.ENTRY);
            MirrorAtmosphereClient.set(0.0f, 0.0f);
        }
    }

    public static void handleMirrorAtmosphere(com.kadikular.quantimium.network.MirrorAtmospherePayload payload) {
        MirrorAtmosphereClient.set(payload.flux(), payload.anomaly());
    }

    public static void handleFieldSurvey(com.kadikular.quantimium.network.FieldSurveyPayload payload) {
        MirrorLensClient.set(payload);
    }

    public static void handleHorizonView(com.kadikular.quantimium.network.HorizonViewPayload payload) {
        com.kadikular.quantimium.client.screen.HorizonCoreScreen.setView(payload);
    }

    public static void handleMaterialiserOptions(com.kadikular.quantimium.network.MaterialiserOptionsPayload payload) {
        com.kadikular.quantimium.client.screen.MaterialiserScreen.setOptions(payload);
    }

    public static void handleFieldMap(com.kadikular.quantimium.network.FieldMapPayload payload) {
        FieldMapClient.handle(payload);
    }

    public static void handleFieldMonitor(com.kadikular.quantimium.network.FieldMonitorPayload payload) {
        com.kadikular.quantimium.client.screen.FieldMonitorScreen.setMap(payload.survey());
    }

    public static void handleMirrorFlora(MirrorFloraPayload payload) {
        MirrorFloraClientCache.set(payload);
    }

    public static void handleVeiledGlimpse(com.kadikular.quantimium.network.VeiledGlimpsePayload payload) {
        VeiledSightingClient.glimpse(payload);
    }

    public static void handleVeiledFlicker(com.kadikular.quantimium.network.VeiledFlickerPayload payload) {
        VeiledFlickerClient.pulse(payload.strength());
    }

    public static void handleVeiledSighting(com.kadikular.quantimium.network.VeiledSightingPayload payload) {
        VeiledSightingClient.show(payload);
    }

    public static void handleFluxRiftStrike(com.kadikular.quantimium.network.FluxRiftStrikePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        MirrorLightningRenderer.arc(minecraft.level, payload.from(), payload.to(), minecraft.level.getRandom());
    }

    public static void handleFluxRifts(com.kadikular.quantimium.network.FluxRiftSyncPayload payload) {
        FluxRiftClientCache.apply(payload);
    }

    public static void handleMirrorRifts(MirrorRiftSyncPayload payload) {
        MirrorRiftClientCache.apply(payload);
    }
}
