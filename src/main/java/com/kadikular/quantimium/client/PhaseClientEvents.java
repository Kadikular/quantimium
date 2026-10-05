package com.kadikular.quantimium.client;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.RenderPipelines;
import com.kadikular.quantimium.client.renderer.SubmitBuffers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.client.renderer.DecoherenceBeamRenderer;
import com.kadikular.quantimium.client.renderer.FluxRiftRenderer;
import com.kadikular.quantimium.client.renderer.RiftStabiliserBeams;
import com.kadikular.quantimium.client.renderer.MirrorFloraRenderer;
import com.kadikular.quantimium.client.renderer.MirrorLightningRenderer;
import com.kadikular.quantimium.client.renderer.MirrorRiftRenderer;
import com.kadikular.quantimium.client.renderer.MirrorWispRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.List;

@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class PhaseClientEvents {

    /**
     * Full-screen HUD texture stretched over the viewport. Edit
     * {@code assets/quantimium/textures/misc/mirror_overlay.png} (256×256, RGBA) in Paint;
     * transparent pixels leave the wash and world visible.
     */
    private static final Identifier OVERLAY =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/misc/mirror_overlay.png");
    private static final int OVERLAY_SIZE = 256;

    private PhaseClientEvents() {}

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientPhaseState.isActive()) return;
        float intensity = MirrorAtmosphereClient.intensity();
        float blend = Mth.lerp(intensity, 0.48f, 0.72f);
        float grey = Mth.lerp(intensity, 0.22f, 0.34f);
        event.setRed(Mth.lerp(blend, event.getRed(), grey * 0.95f));
        event.setGreen(Mth.lerp(blend, event.getGreen(), grey * 0.90f));
        event.setBlue(Mth.lerp(blend, event.getBlue(), grey * 1.05f));
    }

    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog event) {
        // Open air is ATMOSPHERIC since 1.21.6 (NONE before).
        if (!ClientPhaseState.isActive() || event.getType() != FogType.ATMOSPHERIC) return;
        float intensity = MirrorAtmosphereClient.intensity();
        float far = Math.min(event.getFarPlaneDistance(), Mth.lerp(intensity, 56.0f, 38.0f));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(),
                Mth.lerp(intensity, 8.0f, 4.0f)));
        event.setFarPlaneDistance(far);
        // The sky and clouds fog to their own distances now; pull them in too, as vanilla's boss fog does.
        FogData fog = event.getFogData();
        fog.skyEnd = Math.min(fog.skyEnd, far);
        fog.cloudEnd = Math.min(fog.cloudEnd, far);
    }

    @SubscribeEvent
    public static void screenWash(RenderGuiEvent.Pre event) {
        if (!ClientPhaseState.isActive()) return;
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float intensity = MirrorAtmosphereClient.intensity();
        float flash = MirrorAtmosphereClient.flashStrength();

        int washAlpha = Mth.floor(Mth.lerp(intensity, 0x15, 0x28));
        graphics.fill(0, 0, width, height, (washAlpha << 24) | 0x1A1620);
        // The textured GUI pipeline blends, so the PNG's transparent pixels leave the wash showing.
        graphics.blit(RenderPipelines.GUI_TEXTURED, OVERLAY, 0, 0, 0.0f, 0.0f, width, height,
                OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_SIZE, OVERLAY_SIZE);
        if (flash > 0.0f) {
            int flashAlpha = Mth.floor(flash * 0x55);
            graphics.fill(0, 0, width, height, (flashAlpha << 24) | 0xC8A0FF);
        }
    }

    /**
     * The mirror's world-space drawing: wisps, rifts, beams, sightings, lightning and flora. Submitted
     * with the block entities and entities, so it sorts with them: custom geometry draws after block
     * models (flora under a beam's glow) and before translucent terrain (glass over a beam).
     */
    @SubscribeEvent
    public static void submitAtmosphere(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        hideBackingWeather(minecraft);

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = minecraft.level.getGameTime() + partialTick;
        Camera view = minecraft.gameRenderer.getMainCamera();
        Vec3 camera = view.position();
        Entity viewer = view.entity();
        Vec3 middle = viewer == null
                ? camera
                : viewer.getPosition(partialTick).add(0.0, viewer.getBbHeight() * 0.5, 0.0);
        PoseStack poses = event.getPoseStack();
        SubmitBuffers buffers = new SubmitBuffers(event.getSubmitNodeCollector());

        renderBleed(poses, buffers, camera);
        if (ClientPhaseState.isActive()) MirrorFloraRenderer.render(poses, buffers, camera);
        MirrorWispRenderer.render(poses, buffers, camera, partialTick, time);
        for (MirrorRiftAnimator rift : MirrorRiftClientCache.visible()) {
            if (!rift.visible()) continue;
            MirrorRiftRenderer.render(rift.rift(), rift.width(partialTick), poses, buffers, camera, middle, time);
        }
        FluxRiftRenderer.render(poses, buffers, camera, middle, partialTick, time);
        RiftStabiliserBeams.render(poses, buffers, camera, time);
        DecoherenceBeamRenderer.render(poses, buffers, camera, partialTick, time);
        VeiledSightingClient.render(poses, buffers, camera, partialTick);
        MirrorLightningRenderer.render(poses, buffers, camera, partialTick);
        buffers.flush();
    }

    /**
     * Mirror flora bleeding through around flux rifts, drawn for everyone. A phased viewer already
     * has the chunk flora, so any position that snapshot covers is skipped rather than drawn twice.
     */
    private static void renderBleed(PoseStack poses, SubmitBuffers buffers, Vec3 camera) {
        List<MirrorFloraData.Entry> bleed = FluxRiftClientCache.bleed();
        if (bleed.isEmpty()) return;
        if (ClientPhaseState.isActive()) bleed.removeIf(entry -> MirrorFloraClientCache.contains(entry.packedPos()));
        MirrorFloraRenderer.render(poses, buffers, camera, bleed);
    }

    private static boolean weatherHidden;
    /** What the server last set the rain and thunder to, kept while the overlay holds them at zero. */
    private static float heldRain;
    private static float heldThunder;

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ClientPhaseState.set(false);
            ClientPhaseState.clearRemotes();
            MirrorRiftClientCache.clear();
            FluxRiftClientCache.clear();
            RiftStabiliserBeams.clear();
            VeiledSightingClient.clear();
            VeiledFlickerClient.clear();
            MirrorAtmosphereClient.clear();
            MirrorFloraClientCache.clear();
            MirrorLensClient.clear();
            restoreBackingWeather(minecraft);
            return;
        }

        hideBackingWeather(minecraft);
        MirrorRiftClientCache.tick();
        FluxRiftClientCache.tick();
        DecoherenceBeamRenderer.tick();
        VeiledSightingClient.tick();
        VeiledFlickerClient.tick();
        MirrorAtmosphereEffects.tick(minecraft);
        MirrorLensClient.tick(minecraft);
        if (!MirrorAtmosphereEffects.effectsAllowed(minecraft)) return;
        if (minecraft.level.getGameTime() % 3L != 0L) return;

        for (MirrorRiftAnimator rift : MirrorRiftClientCache.visible()) {
            if (!rift.openEnoughForParticles()) continue;
            var geometry = rift.rift();
            Direction tangent = geometry.facing().getClockWise();
            double centreX = geometry.anchor().getX() + 0.5 + tangent.getStepX() * 0.5;
            double centreZ = geometry.anchor().getZ() + 0.5 + tangent.getStepZ() * 0.5;
            double x = centreX + (minecraft.level.getRandom().nextDouble() - 0.5) * 1.4;
            double y = geometry.anchor().getY() + 0.3 + minecraft.level.getRandom().nextDouble() * 1.6;
            double z = centreZ + (minecraft.level.getRandom().nextDouble() - 0.5) * 1.4;
            minecraft.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                    (centreX - x) * 0.06, 0.01, (centreZ - z) * 0.06);
        }
    }

    /**
     * The overlay has its own weather. Zeroing rain/thunder levels hides vanilla streaks, splashes,
     * and looping rain audio for this client only; the server still rains (crops, cauldrons).
     */
    private static void hideBackingWeather(Minecraft minecraft) {
        if (minecraft.level == null) {
            weatherHidden = false;
            return;
        }
        if (ClientPhaseState.isActive()) {
            // The client has no weather flag of its own since 26.1, only these levels, which the
            // server moves by packet. Keep what it last sent: anything above zero is new from it.
            float rain = minecraft.level.getRainLevel(1.0f);
            float thunder = minecraft.level.getThunderLevel(1.0f);
            if (!weatherHidden || rain > 0.0f) heldRain = rain;
            if (!weatherHidden || thunder > 0.0f) heldThunder = thunder;
            minecraft.level.setRainLevel(0.0f);
            minecraft.level.setThunderLevel(0.0f);
            weatherHidden = true;
        } else {
            restoreBackingWeather(minecraft);
        }
    }

    private static void restoreBackingWeather(Minecraft minecraft) {
        if (!weatherHidden) return;
        weatherHidden = false;
        if (minecraft.level == null) return;
        minecraft.level.setRainLevel(heldRain);
        minecraft.level.setThunderLevel(heldThunder);
    }

    @SubscribeEvent
    public static void muteBackingWeather(PlaySoundEvent event) {
        if (!ClientPhaseState.isActive() || event.getSound() == null) return;
        String path = event.getSound().getIdentifier().getPath();
        if (path.startsWith("weather.")
                || path.equals("entity.lightning_bolt.thunder")
                || path.equals("entity.lightning_bolt.impact")) {
            event.setSound(null);
        }
    }
}
