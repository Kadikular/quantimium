package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.client.renderer.LaserMirrorBER;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.MirrorPlate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Looking at a Laser Mirror with an empty hand or a wrench: the part of the face a click would land in, outlined (its
 * middle square, or which side of the ring round it), and with a wrench, a ghost of the plate where that click would turn
 * it. A sneaking click moves the stand instead: then the square or side of the face it would move to.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class MirrorZoneOutline {
    private MirrorZoneOutline() {}

    /** How far the rings reach from the face's middle, in blocks: the middle part of the mirror is 10 pixels across. */
    private static final double SQUARE = 0.11, RING = 0.22, EDGE = 0.3125;

    @SubscribeEvent
    public static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof LaserMirrorBlock block)) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        boolean wrench = held.is(ModTags.TOOLS_WRENCH);
        if (!held.isEmpty() && !wrench) return;
        BlockPos pos = event.getBlockPos();
        LaserMirrorBlock.Zone zone = LaserMirrorBlock.zone(pos, event.getHitResult());
        Direction mount = event.getBlockState().getValue(LaserMirrorBlock.MOUNT);
        boolean sneaking = player.isShiftKeyDown();
        // A plain click on the face it stands on does nothing: nothing to show.
        MirrorPlate plate = sneaking ? null : LaserMirrorBlock.plateFor(zone, mount);
        if (!sneaking && plate == null) return;
        double depth = event.getHitResult().getLocation().subtract(pos.getCenter()).dot(Vec3.atLowerCornerOf(zone.face().getUnitVec3i())) + 0.003;
        // Sneaking, the two rings are one: the side it would stand on.
        List<Vec3> outline = outline(zone, depth, sneaking);
        boolean splits = block.splits();
        event.addCustomRenderer((state, buffers, poses, translucent, level) -> {
            Vec3 camera = level.cameraRenderState.pos;
            poses.pushPose();
            poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
            PoseStack.Pose pose = poses.last();
            for (int i = 0; i < outline.size(); i++) {
                Vec3 a = outline.get(i).add(0.5, 0.5, 0.5), b = outline.get((i + 1) % outline.size()).add(0.5, 0.5, 0.5);
                Vec3 d = b.subtract(a).normalize();
                lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(0.55f, 0.85f, 1f, 0.9f).setNormal(pose, (float) d.x, (float) d.y, (float) d.z).setLineWidth(2.5f);
                lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(0.55f, 0.85f, 1f, 0.9f).setNormal(pose, (float) d.x, (float) d.y, (float) d.z).setLineWidth(2.5f);
            }
            if (wrench && plate != null) LaserMirrorBER.draw(plate, splits, poses, buffers, LightCoordsUtil.FULL_BRIGHT, 0.45f);
            poses.popPose();
            // The block's own outline still drawn.
            return false;
        });
    }

    /** The part of the face, as corners relative to the block's middle. */
    private static List<Vec3> outline(LaserMirrorBlock.Zone zone, double depth, boolean merged) {
        Vec3 out = Vec3.atLowerCornerOf(zone.face().getUnitVec3i()).scale(depth);
        List<Vec3> corners = new ArrayList<>(4);
        if (zone.toward() == null) {
            Direction[] axes = across(zone.face());
            Vec3 u = Vec3.atLowerCornerOf(axes[0].getUnitVec3i()), v = Vec3.atLowerCornerOf(axes[1].getUnitVec3i());
            corners.add(out.add(u.scale(-SQUARE)).add(v.scale(-SQUARE)));
            corners.add(out.add(u.scale(SQUARE)).add(v.scale(-SQUARE)));
            corners.add(out.add(u.scale(SQUARE)).add(v.scale(SQUARE)));
            corners.add(out.add(u.scale(-SQUARE)).add(v.scale(SQUARE)));
            return corners;
        }
        double inner = merged || zone.ring() == 1 ? SQUARE : RING;
        double outer = merged || zone.ring() == 2 ? EDGE : RING;
        Vec3 d = Vec3.atLowerCornerOf(zone.toward().getUnitVec3i());
        // The other way across the face.
        Direction otherAxis = null;
        for (Direction side : Direction.values()) {
            if (side.getAxis() != zone.face().getAxis() && side.getAxis() != zone.toward().getAxis()) {
                otherAxis = side;
                break;
            }
        }
        Vec3 e = Vec3.atLowerCornerOf(otherAxis.getUnitVec3i());
        corners.add(out.add(d.scale(inner)).add(e.scale(-inner)));
        corners.add(out.add(d.scale(outer)).add(e.scale(-outer)));
        corners.add(out.add(d.scale(outer)).add(e.scale(outer)));
        corners.add(out.add(d.scale(inner)).add(e.scale(inner)));
        return corners;
    }

    /** Two directions across {@code face}: along its plane. */
    private static Direction[] across(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Direction[] {Direction.UP, Direction.SOUTH};
            case Y -> new Direction[] {Direction.EAST, Direction.SOUTH};
            case Z -> new Direction[] {Direction.EAST, Direction.UP};
        };
    }
}
