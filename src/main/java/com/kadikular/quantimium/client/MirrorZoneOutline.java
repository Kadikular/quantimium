package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.MirrorPlate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Looking at a face of a Laser Mirror: the parts a click on it lands in, outlined in grey (the middle square, and the
 * two rings round it split four ways by the diagonals), the one under the crosshair in blue. With a wrench, the outline
 * of the plate where that click would turn it. Sneaking, a click moves the stand instead: the two rings are one, the
 * side of the face it would move to; the face it stands on only shows while sneaking.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class MirrorZoneOutline {
    private MirrorZoneOutline() {}

    /** How far the rings reach from the face's middle, in blocks: the middle part of the mirror is 10 pixels across. */
    private static final double SQUARE = 0.11, RING = 0.22, EDGE = 0.3125;
    /** Half the plate, in blocks: it's 10 pixels across. */
    private static final double PLATE = 5 / 16.0;

    @SubscribeEvent
    public static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof LaserMirrorBlock)) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean wrench = player.getMainHandItem().is(ModTags.TOOLS_WRENCH);
        BlockPos pos = event.getBlockPos();
        LaserMirrorBlock.Zone zone = LaserMirrorBlock.zone(pos, event.getHitResult());
        Direction mount = event.getBlockState().getValue(LaserMirrorBlock.MOUNT);
        boolean sneaking = player.isShiftKeyDown();
        // A plain click on the face it stands on does nothing: nothing to show.
        if (!sneaking && zone.face() == mount) return;
        MirrorPlate plate = sneaking ? null : LaserMirrorBlock.plateFor(zone, mount);
        double depth = event.getHitResult().getLocation().subtract(pos.getCenter()).dot(Vec3.atLowerCornerOf(zone.face().getUnitVec3i())) + 0.003;
        List<Vec3[]> grid = grid(zone.face(), depth, sneaking);
        // Sneaking, the two rings are one: the side it would stand on. A plate that would face its stand isn't set.
        List<Vec3> outline = sneaking || plate != null ? outline(zone, depth, sneaking) : List.of();
        List<Vec3> ghost = wrench && plate != null ? ghost(plate) : List.of();
        event.addCustomRenderer((state, buffers, poses, translucent, level) -> {
            Vec3 camera = level.cameraRenderState.pos;
            poses.pushPose();
            poses.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y + 0.5, pos.getZ() - camera.z + 0.5);
            VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
            PoseStack.Pose pose = poses.last();
            for (Vec3[] line : grid) line(lines, pose, line[0], line[1], 0f, 0f, 0f, 0.45f, 1.5f);
            loop(lines, pose, outline, 0.55f, 0.85f, 1f, 0.9f, 2.5f);
            loop(lines, pose, ghost, 1f, 1f, 1f, 0.8f, 2f);
            poses.popPose();
            // The block's own outline still drawn.
            return false;
        });
    }

    private static void loop(VertexConsumer lines, PoseStack.Pose pose, List<Vec3> corners, float r, float g, float b, float a, float width) {
        for (int i = 0; i < corners.size(); i++) line(lines, pose, corners.get(i), corners.get((i + 1) % corners.size()), r, g, b, a, width);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, Vec3 a, Vec3 b, float red, float green, float blue, float alpha, float width) {
        Vec3 d = b.subtract(a).normalize();
        lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha).setNormal(pose, (float) d.x, (float) d.y, (float) d.z).setLineWidth(width);
        lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha).setNormal(pose, (float) d.x, (float) d.y, (float) d.z).setLineWidth(width);
    }

    /**
     * Every part of {@code face}, as lines relative to the block's middle: the middle square, the rings' edges, and the
     * diagonals splitting the rings four ways. Merged, the rings are one.
     */
    private static List<Vec3[]> grid(Direction face, double depth, boolean merged) {
        Vec3 out = Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(depth);
        Direction[] axes = across(face);
        Vec3 u = Vec3.atLowerCornerOf(axes[0].getUnitVec3i()), v = Vec3.atLowerCornerOf(axes[1].getUnitVec3i());
        List<Vec3[]> lines = new ArrayList<>();
        for (double size : merged ? new double[] {SQUARE, EDGE} : new double[] {SQUARE, RING, EDGE}) {
            Vec3[] corners = {out.add(u.scale(-size)).add(v.scale(-size)), out.add(u.scale(size)).add(v.scale(-size)),
                    out.add(u.scale(size)).add(v.scale(size)), out.add(u.scale(-size)).add(v.scale(size))};
            for (int i = 0; i < 4; i++) lines.add(new Vec3[] {corners[i], corners[(i + 1) % 4]});
        }
        for (int su = -1; su <= 1; su += 2) {
            for (int sv = -1; sv <= 1; sv += 2) {
                lines.add(new Vec3[] {out.add(u.scale(su * SQUARE)).add(v.scale(sv * SQUARE)), out.add(u.scale(su * EDGE)).add(v.scale(sv * EDGE))});
            }
        }
        return lines;
    }

    /** The plate's outline, turned as {@code plate} is, relative to the block's middle: its face's middle is there. */
    private static List<Vec3> ghost(MirrorPlate plate) {
        Vec3 n = plate.normal();
        // Along the plate: the axis it's turned about, and the way across that.
        Vec3 u;
        if (plate.toward() == null) {
            u = Vec3.atLowerCornerOf(across(plate.face())[0].getUnitVec3i());
        } else {
            u = Vec3.atLowerCornerOf(plate.face().getUnitVec3i()).cross(Vec3.atLowerCornerOf(plate.toward().getUnitVec3i()));
        }
        Vec3 v = n.cross(u).normalize();
        Vec3 front = n.scale(0.004);
        return List.of(front.add(u.scale(-PLATE)).add(v.scale(-PLATE)), front.add(u.scale(PLATE)).add(v.scale(-PLATE)),
                front.add(u.scale(PLATE)).add(v.scale(PLATE)), front.add(u.scale(-PLATE)).add(v.scale(PLATE)));
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
