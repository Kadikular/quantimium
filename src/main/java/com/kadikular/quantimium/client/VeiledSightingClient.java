package com.kadikular.quantimium.client;

import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.model.VeiledModel;
import com.kadikular.quantimium.network.VeiledGlimpsePayload;
import com.kadikular.quantimium.network.VeiledSightingEndPayload;
import com.kadikular.quantimium.network.VeiledSightingPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * This player's real-world sighting of the Veiled, if they have one: a dark silhouette on the
 * landscape with one lit point for a gaze. Nothing else in the world knows it is there.
 *
 * <p>Hold the crosshair on it for about three seconds and it fades — hiding, not leaving. Once you
 * have seen it, look away and it is gone when you look back; ignored for long enough it goes the same
 * way. Walking towards it, it thins from 30 blocks and is gone by 18. Whichever way it
 * ends, the server is told, and the true body in the mirror takes a step closer.
 */
public final class VeiledSightingClient {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/veiled.png");
    private static final Identifier GAZE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/veiled_gaze.png");
    /** Near-black with the anomaly body's violet cast: a shape, not a creature. */
    private static final int SILHOUETTE = 0x0C0714;
    private static final int DWELL_TO_FADE = 60;
    private static final int FADE_TICKS = 20;
    private static final int APPEAR_TICKS = 10;
    /** After this long unwatched, it goes as soon as it is out of view. */
    private static final int IGNORED_TICKS = 900;
    /** Walking in: it starts to thin at the first distance and is gone by the second. */
    private static final double FADE_FROM = 30.0;
    private static final double TOO_CLOSE = 18.0;
    /** Enough steady attention that the player has certainly seen it. */
    private static final int SEEN_TICKS = 10;
    private static final double VIEW_COS = Math.cos(Math.toRadians(35.0));

    /** A glimpse lasts this long at most, and quickly folds away when looked at. */
    private static final int GLIMPSE_TICKS = 50;
    private static final int GLIMPSE_FADE = 5;

    private static final class Active {
        final int id;
        final Vec3 feet;
        int age;
        int dwell;
        int fade = -1;
        boolean seen;

        Active(int id, Vec3 feet) {
            this.id = id;
            this.feet = feet;
        }
    }

    @Nullable
    private static Active current;
    /** At the edge of view while it follows you. Separate from a sighting, and never reported. */
    @Nullable
    private static Active glimpse;
    @Nullable
    private static VeiledModel model;

    private VeiledSightingClient() {}

    public static void show(VeiledSightingPayload payload) {
        current = new Active(payload.id(), new Vec3(payload.x(), payload.y(), payload.z()));
    }

    public static void glimpse(VeiledGlimpsePayload payload) {
        glimpse = new Active(-1, new Vec3(payload.x(), payload.y(), payload.z()));
    }

    public static void clear() {
        current = null;
        glimpse = null;
    }

    /**
     * Gone as soon as the player turns towards it — it was only ever at the edge of their view — or
     * after a couple of seconds whatever they do.
     */
    private static void tickGlimpse(LocalPlayer player) {
        Active seen = glimpse;
        if (seen == null) return;
        seen.age++;
        if (seen.fade >= 0) {
            if (++seen.fade >= GLIMPSE_FADE) glimpse = null;
            return;
        }
        Vec3 toward = seen.feet.add(0.0, 1.5, 0.0).subtract(player.getEyePosition()).normalize();
        if (player.getViewVector(1.0f).dot(toward) >= VIEW_COS || seen.age >= GLIMPSE_TICKS) seen.fade = 0;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;
        // Sightings belong to the real world. Phasing drops them; the server lets a sighting time out.
        if (ClientPhaseState.isActive()) {
            clear();
            return;
        }
        tickGlimpse(player);
        Active sighting = current;
        if (sighting == null) return;
        sighting.age++;
        Vec3 centre = sighting.feet.add(0.0, 1.5, 0.0);
        Vec3 eye = player.getEyePosition();
        Vec3 toward = centre.subtract(eye);
        double distance = toward.length();
        if (distance < TOO_CLOSE) {
            end(sighting);
            return;
        }
        Vec3 look = player.getViewVector(1.0f);
        double facing = look.dot(toward.scale(1.0 / distance));

        if (sighting.fade >= 0) {
            if (++sighting.fade >= FADE_TICKS) end(sighting);
            return;
        }
        // The crosshair counts as on it within its own angular half-width, but never tighter than
        // about 2°, so a far figure is not a pixel hunt.
        double halfWidth = Math.max(Math.toRadians(2.0), Math.atan2(0.9, distance));
        boolean onIt = facing >= Math.cos(halfWidth) && clear(minecraft, eye, centre);
        sighting.dwell = onIt ? sighting.dwell + 1 : Math.max(0, sighting.dwell - 1);
        if (sighting.dwell >= SEEN_TICKS) sighting.seen = true;
        if (sighting.dwell >= DWELL_TO_FADE) {
            sighting.fade = 0;
        } else if ((sighting.seen || sighting.age > IGNORED_TICKS) && facing < VIEW_COS) {
            // Seen, then looked away from: when you look back, it is gone.
            end(sighting);
        }
    }

    private static boolean clear(Minecraft minecraft, Vec3 from, Vec3 to) {
        return minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                minecraft.player)).getType() == HitResult.Type.MISS;
    }

    private static void end(Active sighting) {
        current = null;
        ClientPacketDistributor.sendToServer(new VeiledSightingEndPayload(sighting.id));
    }

    public static void render(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        if (ClientPhaseState.isActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (model == null) model = new VeiledModel(minecraft.getEntityModels().bakeLayer(VeiledModel.LAYER));

        Active sighting = current;
        if (sighting != null) {
            float alpha = sighting.fade >= 0
                    ? 1.0f - Mth.clamp((sighting.fade + partialTick) / FADE_TICKS, 0.0f, 1.0f)
                    : Mth.clamp((sighting.age + partialTick) / APPEAR_TICKS, 0.0f, 1.0f);
            double distance = camera.distanceTo(sighting.feet.add(0.0, 1.5, 0.0));
            alpha *= (float) Mth.clamp((distance - TOO_CLOSE) / (FADE_FROM - TOO_CLOSE), 0.0, 1.0);
            renderFigure(minecraft, sighting, alpha, poses, buffers, camera, partialTick);
        }
        Active edge = glimpse;
        if (edge != null) {
            // Close by and brief: no distance fade, just a fast fold-away.
            float alpha = edge.fade >= 0
                    ? 1.0f - Mth.clamp((edge.fade + partialTick) / GLIMPSE_FADE, 0.0f, 1.0f)
                    : Mth.clamp((edge.age + partialTick) / 4.0f, 0.0f, 1.0f);
            renderFigure(minecraft, edge, alpha, poses, buffers, camera, partialTick);
        }
    }

    private static void renderFigure(Minecraft minecraft, Active sighting, float alpha, PoseStack poses,
                                     MultiBufferSource buffers, Vec3 camera,
                                     float partialTick) {
        if (alpha <= 0.01f) return;
        float age = sighting.age + partialTick;
        int a = Mth.floor(alpha * 255.0f);

        // It turns to keep facing you wherever you move, and tilts its hood to meet your eyes.
        Vec3 head = sighting.feet.add(0.0, 2.6, 0.0);
        Vec3 toCamera = camera.subtract(head);
        float pitch = (float) -Math.toDegrees(Math.atan2(toCamera.y, toCamera.horizontalDistance()));
        float yaw = (float) Math.toDegrees(Math.atan2(toCamera.z, toCamera.x)) - 90.0f;

        poses.pushPose();
        poses.translate(sighting.feet.x - camera.x, sighting.feet.y - camera.y, sighting.feet.z - camera.z);
        poses.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        poses.scale(-1.0f, -1.0f, 1.0f);
        poses.translate(0.0f, -1.501f, 0.0f);
        model.pose(age, 0.0f, pitch, 1.0f);

        RenderType body = RenderTypes.entityTranslucent(TEXTURE);
        model.renderToBuffer(poses, buffers.getBuffer(body), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ARGB.color(a, (SILHOUETTE >> 16) & 0xFF, (SILHOUETTE >> 8) & 0xFF, SILHOUETTE & 0xFF));
        RenderType gaze = RenderTypes.entityTranslucentEmissive(GAZE);
        model.renderToBuffer(poses, buffers.getBuffer(gaze), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ARGB.color(a, 255, 255, 255));
        poses.popPose();
    }
}
