package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.AreaPrismBlockEntity;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.laser.LinkerArea;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Frames an area while the Laser Linker is in hand: in area mode, from the first corner picked to the
 * second (or to the block being looked at, until it's picked); and the area of an Area Prism being looked at.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class LinkerAreaClientEvents {

    private static final float INFLATE = 0.0025f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private LinkerAreaClientEvents() {}

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        ItemStack linker = held(player);
        if (linker.isEmpty()) return;
        BoundingBox box = picking(minecraft, linker);
        boolean picking = box != null;
        if (box == null) box = lookedAtPrism(minecraft);
        if (box == null) return;

        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        float pulse = 0.5f + 0.5f * Mth.sin((Util.getMillis() % 2000L) / 2000.0f * TWO_PI);
        int color = picking ? ARGB.colorFromFloat(0.45f + 0.4f * pulse, 0.6f, 0.48f, 1.0f) : ARGB.colorFromFloat(0.5f, 0.4f, 0.62f, 1.0f);
        float width = minecraft.getWindow().getAppropriateLineWidth();
        var shape = Shapes.box(-INFLATE, -INFLATE, -INFLATE, box.getXSpan() + INFLATE, box.getYSpan() + INFLATE, box.getZSpan() + INFLATE);
        BoundingBox framed = box;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lines(), (pose, lines) -> {
            PoseStack poses = new PoseStack();
            poses.last().set(pose);
            ShapeRenderer.renderShape(poses, lines, shape, framed.minX() - camera.x, framed.minY() - camera.y, framed.minZ() - camera.z, color, width);
        });
    }

    private static ItemStack held(Player player) {
        if (player.getMainHandItem().is(ModItems.LASER_LINKER.get())) return player.getMainHandItem();
        if (player.getOffhandItem().is(ModItems.LASER_LINKER.get())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** In area mode with a first corner picked: from it to the second, or to the block looked at. */
    @Nullable
    private static BoundingBox picking(Minecraft minecraft, ItemStack linker) {
        LinkerArea area = linker.getOrDefault(ModDataComponents.LINKER_AREA.get(), LinkerArea.OFF);
        if (!area.on() || area.first().isEmpty()) return null;
        BlockPos other = area.second().orElse(null);
        if (other == null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) other = hit.getBlockPos();
        if (other == null) other = area.first().get();
        return BoundingBox.fromCorners(area.first().get(), other);
    }

    @Nullable
    private static BoundingBox lookedAtPrism(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof AreaPrismBlockEntity prism ? prism.area() : null;
    }
}
