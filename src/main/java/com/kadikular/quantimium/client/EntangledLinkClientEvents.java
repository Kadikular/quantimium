// Path: src/main/java/com/kadikular/quantimium/client/EntangledLinkClientEvents.java
package com.kadikular.quantimium.client;

import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Frames the block an entangled link is bound to while the player holds it, so it is obvious in the
 * world which inventory a link points at. Only draws for a target in the current dimension.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class EntangledLinkClientEvents {

    /** Nudged just off the block faces so the near edges are not hidden inside them. */
    private static final float INFLATE = 0.0025f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private EntangledLinkClientEvents() {}

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) return;

        GlobalPos target = heldTargetInDimension(player, level);
        if (target == null) return;
        BlockPos pos = target.pos();

        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        float pulse = 0.5f + 0.5f * Mth.sin((Util.getMillis() % 2000L) / 2000.0f * TWO_PI);
        float alpha = 0.35f + 0.45f * pulse;
        int color = ARGB.colorFromFloat(alpha, 0.2f, 0.9f, 1.0f);
        float width = minecraft.getWindow().getAppropriateLineWidth();

        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(),
                (pose, lines) -> {
                    PoseStack poses = new PoseStack();
                    poses.last().set(pose);
                    ShapeRenderer.renderShape(poses, lines, BOX, pos.getX() - camera.x, pos.getY() - camera.y,
                            pos.getZ() - camera.z, color, width);
                });
    }

    private static final VoxelShape BOX = Shapes.box(-INFLATE, -INFLATE, -INFLATE, 1 + INFLATE, 1 + INFLATE, 1 + INFLATE);

    @Nullable
    private static GlobalPos heldTargetInDimension(Player player, Level level) {
        GlobalPos main = boundInDimension(player.getMainHandItem(), level);
        return main != null ? main : boundInDimension(player.getOffhandItem(), level);
    }

    @Nullable
    private static GlobalPos boundInDimension(ItemStack stack, Level level) {
        GlobalPos bound = EntangledLinks.boundPos(stack);
        if (bound == null || !bound.dimension().equals(level.dimension())) return null;
        return bound;
    }
}
