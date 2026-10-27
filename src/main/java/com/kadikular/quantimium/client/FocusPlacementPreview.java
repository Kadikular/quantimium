package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.LaserFocusBlock;
import com.kadikular.quantimium.item.FocusPieceItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

import java.util.List;

/** Holding a focus or an attachment: an outline of where using it would put it, and which way it faces. */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class FocusPlacementPreview {
    private FocusPlacementPreview() {}

    @SubscribeEvent
    public static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof FocusPieceItem piece)) return;
        FocusPieceItem.Spot spot = piece.spot(player.level(), event.getHitResult());
        if (spot == null) return;
        List<AABB> boxes = piece.core() != null ? List.of(LaserFocusBlock.FOCUS) : LaserFocusBlock.boxes(piece.part(), spot.face());
        VoxelShape shape = Shapes.empty();
        for (AABB box : boxes) shape = Shapes.or(shape, Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        VoxelShape outline = shape;
        event.addCustomRenderer((state, buffers, poses, translucent, level) -> {
            if (state.isTranslucent() != translucent) return false;
            Vec3 camera = level.cameraRenderState.pos;
            float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
            ShapeRenderer.renderShape(poses, buffers.getBuffer(RenderTypes.lines()), outline, spot.pos().getX() - camera.x,
                    spot.pos().getY() - camera.y, spot.pos().getZ() - camera.z, ARGB.color(160, 255, 255, 255), width);
            // The block's own outline still drawn.
            return false;
        });
    }
}
