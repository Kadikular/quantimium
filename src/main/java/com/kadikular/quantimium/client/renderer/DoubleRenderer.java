package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A body double: its owner's own skin, drawn as a pale azure hologram with a glow round it, standing
 * asleep with its head bowed. It tracks its owner's health: the healthier they are the steadier and
 * paler it is; as they are hurt it runs to violet and glitches more, so a pod at home shows how its
 * owner is doing far away.
 */
public final class DoubleRenderer {

    private static PlayerModel wide;
    private static PlayerModel slim;

    private DoubleRenderer() {}

    private static PlayerModel model(boolean slimArms) {
        if (wide == null) {
            var models = Minecraft.getInstance().getEntityModels();
            wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        return slimArms ? slim : wide;
    }

    public static PlayerSkin skin(@Nullable UUID owner) {
        if (owner == null) return DefaultPlayerSkin.get(net.minecraft.util.Util.NIL_UUID);
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    /** The owner's health as a share of their maximum, if they are about; 1 otherwise. */
    public static float health(@Nullable UUID owner) {
        Minecraft minecraft = Minecraft.getInstance();
        if (owner == null || minecraft.level == null) return 1.0f;
        Player player = minecraft.level.getPlayerByUUID(owner);
        if (player == null) return 1.0f;
        return Mth.clamp(player.getHealth() / Math.max(1.0f, player.getMaxHealth()), 0.0f, 1.0f);
    }

    /**
     * Draws the double with its feet at the pose's origin, facing {@code yaw}.
     *
     * @param presence how much of it is there, 0 to 1: it fades in as it is unfolded or swapped into
     * @param seed     keeps two doubles from glitching in step
     */
    public static void render(PoseStack poses, MultiBufferSource buffers, @Nullable UUID owner, float yaw,
                              float time, float presence, float health, int seed) {
        if (presence <= 0.01f) return;
        PlayerSkin skin = skin(owner);
        PlayerModel model = model(skin.model() == PlayerModelType.SLIM);
        pose(model, time);

        // Hurt runs the hologram from pale azure to violet, and makes it glitch more often.
        float hurt = 1.0f - health;
        float glitch = glitch(time, seed, 0.02f + hurt * 0.25f);
        float breathe = Mth.sin(time * 0.05f) * 0.012f;
        int body = argb(0.46f * presence * (1.0f - glitch * 0.4f), Mth.lerp(hurt, 0.50f, 0.62f),
                Mth.lerp(hurt, 0.74f, 0.40f), Mth.lerp(hurt, 1.0f, 0.98f));
        int glow = argb(1.0f, Mth.lerp(hurt, 0.12f, 0.34f) * presence, Mth.lerp(hurt, 0.34f, 0.14f) * presence,
                Mth.lerp(hurt, 0.72f, 0.62f) * presence);

        poses.pushPose();
        // Floating a little off the floor, and a touch smaller than life, so it sleeps inside the glass.
        poses.translate(0, 0.06 + breathe, 0);
        poses.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        poses.scale(0.84f, 0.84f, 0.84f);
        poses.scale(-1.0f, -1.0f, 1.0f);
        poses.translate(0.0f, -1.501f, 0.0f);

        model.renderToBuffer(poses, buffers.getBuffer(RenderTypes.entityTranslucent(skin.body().texturePath())),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, body);

        // The glow: the same body again, a touch larger and additive, so it reads as light.
        poses.pushPose();
        poses.translate(0, 0.75f, 0);
        poses.scale(1.035f, 1.02f, 1.035f);
        poses.translate(0, -0.75f, 0);
        model.renderToBuffer(poses, buffers.getBuffer(QuantumRenderTypes.glow(skin.body().texturePath())),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow);
        poses.popPose();

        // Glitching, it tears into two offset copies, violet one way and azure the other.
        if (glitch > 0.0f) {
            float offset = 0.05f + glitch * 0.06f;
            int violet = argb(1.0f, 0.34f * glitch * presence, 0.12f * glitch * presence, 0.62f * glitch * presence);
            int azure = argb(1.0f, 0.08f * glitch * presence, 0.30f * glitch * presence, 0.62f * glitch * presence);
            poses.pushPose();
            poses.translate(offset, 0, 0);
            model.renderToBuffer(poses, buffers.getBuffer(QuantumRenderTypes.glow(skin.body().texturePath())),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, violet);
            poses.translate(-2 * offset, 0, 0);
            model.renderToBuffer(poses, buffers.getBuffer(QuantumRenderTypes.glow(skin.body().texturePath())),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, azure);
            poses.popPose();
        }
        poses.popPose();
    }

    /** Asleep on its feet: head bowed, arms loose, a slow sway. */
    private static void pose(PlayerModel model, float time) {
        model.resetPose();
        float sway = Mth.sin(time * 0.03f) * 0.03f;
        model.head.setRotation(0.32f, sway, 0.0f);
        model.body.setRotation(0.02f, 0.0f, 0.0f);
        model.rightArm.setRotation(0.04f, 0.0f, 0.10f + sway * 0.5f);
        model.leftArm.setRotation(0.04f, 0.0f, -0.10f - sway * 0.5f);
        model.rightLeg.setRotation(0.0f, 0.0f, 0.02f);
        model.leftLeg.setRotation(0.0f, 0.0f, -0.02f);
    }

    /**
     * How hard it is glitching now, 0 most of the time. Every so often, for a few ticks, it tears:
     * {@code chance} is how often a window of time has a glitch in it.
     */
    private static float glitch(float time, int seed, float chance) {
        int window = (int) (time / 7.0f);
        float roll = hash(window * 31 + seed);
        if (roll > chance) return 0.0f;
        float within = (time / 7.0f) - window;
        return within < 0.45f ? Mth.sin(within / 0.45f * Mth.PI) : 0.0f;
    }

    private static float hash(int value) {
        int h = value * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x165667b1;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535.0f;
    }

    private static int argb(float a, float r, float g, float b) {
        return ARGB.color(Mth.clamp((int) (a * 255), 0, 255), Mth.clamp((int) (r * 255), 0, 255),
                Mth.clamp((int) (g * 255), 0, 255), Mth.clamp((int) (b * 255), 0, 255));
    }
}
