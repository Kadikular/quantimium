package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.client.MirrorLensClient;
import com.kadikular.quantimium.client.ClientPhaseState;
import com.kadikular.quantimium.entity.FieldDouble;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.entity.Veiled;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Other-realm living things render as a pale folding cube, sized like a sphere around the body
 * rather than a silhouette of it. Look-only: the real hitbox is unchanged and still isolated.
 */
public final class MirrorWispRenderer {

    /** Player width 0.6 maps to this diameter. */
    private static final float PLAYER_DIAMETER = 0.6f;
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float MIN_DIAMETER = 0.165f;
    private static final float MAX_DIAMETER = 1.35f;
    /** {@link TesseractShell} projects about this far across at scale 1. */
    private static final float SHELL_DIAMETER = 0.64f;
    private static final float ALPHA = 2.4f;
    private static final double MAX_DISTANCE_SQUARED = 96.0 * 96.0;

    private MirrorWispRenderer() {}

    /** True when this client should not draw the real body, shadow, or fire. */
    public static boolean hidesBody(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player) return false;
        // A field double stands in both worlds at once: the same body from either side.
        if (entity instanceof FieldDouble) return false;
        // Mites and the Veiled are native to the mirror: a phased viewer sees their real bodies.
        boolean mite = entity instanceof MirrorEndermite || entity instanceof Veiled;
        boolean overlayPlayer = entity instanceof Player && ClientPhaseState.isPhased(entity);
        if (ClientPhaseState.isActive()) return !mite && !overlayPlayer;
        // Through a Mirror Lens the Veiled's true body shows, faintly (see VeiledModel).
        if (entity instanceof Veiled && MirrorLensClient.seesTrueBodies()) return false;
        // A breached mite is in the real world now and draws as itself.
        boolean breached = entity instanceof MirrorEndermite endermite && endermite.isBreached();
        return (mite && !breached) || overlayPlayer;
    }

    public static boolean showsWisp(LivingEntity entity) {
        return hidesBody(entity)
                && !(entity instanceof MirrorEndermite)
                && !(entity instanceof Veiled)
                && !(entity instanceof ArmorStand);
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick, float time) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !showsWisp(living)) continue;
            if (!living.isAlive() || living.isSpectator()) continue;

            Vec3 centre = living.getPosition(partialTick).add(0.0, living.getBbHeight() * 0.5, 0.0);
            if (centre.distanceToSqr(camera) > MAX_DISTANCE_SQUARED) continue;

            float seed = seed(living);
            float bob = Mth.sin(time * 0.09f + seed * 6.28318f) * 0.04f;
            float diameter = Mth.clamp(
                    living.getBbWidth() * (PLAYER_DIAMETER / PLAYER_WIDTH),
                    MIN_DIAMETER, MAX_DIAMETER);
            float scale = diameter / SHELL_DIAMETER;

            poseStack.pushPose();
            poseStack.translate(centre.x - camera.x, centre.y - camera.y + bob, centre.z - camera.z);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.35f + seed * 220.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(12.0f));
            // Each wisp sits at its own distance, so its edge width is its own; flush before the next
            // one can claim the shared line builder at a different width.
            RenderType edges = TesseractShell.edgeTypeFor(poseStack);
            TesseractShell.render(poseStack, buffers, time + seed * 90.0f,
                    edges, TesseractShell.Tint.PALE, ALPHA);
            poseStack.popPose();
        }
    }

    private static float seed(LivingEntity entity) {
        return (entity.getUUID().getLeastSignificantBits() & 0xFFFFL) / 65535.0f;
    }
}
