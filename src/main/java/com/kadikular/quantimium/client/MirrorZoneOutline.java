package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.client.renderer.QuantumRenderTypes;
import com.kadikular.quantimium.laser.MirrorPlate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Looking at a face of a Laser Mirror: the parts a click on it lands in, outlined as the block's own outline is (the
 * middle square, the two rings round it, split four ways by the diagonals), and a ghost of the plate where a click
 * there would turn it. Sneaking, a click moves the stand instead: the two rings are one, and there's no ghost. The face
 * it stands on only shows while sneaking.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class MirrorZoneOutline {
    private MirrorZoneOutline() {}

    /** Half the ghost across, and how far it reaches in front of and behind the plate's face, in blocks: just round the plate. */
    private static final double GHOST = 5.25 / 16, FRONT = 0.25 / 16, BACK = 1.25 / 16;

    @SubscribeEvent
    public static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof LaserMirrorBlock)) return;
        Player player = Minecraft.getInstance().player;
        // By hand or wrench only: anything else does its own thing.
        if (player == null || !player.getMainHandItem().isEmpty() && !player.getMainHandItem().is(com.kadikular.quantimium.init.ModTags.TOOLS_WRENCH)) return;
        BlockPos pos = event.getBlockPos();
        LaserMirrorBlock.Zone zone = LaserMirrorBlock.zone(pos, event.getHitResult());
        Direction mount = event.getBlockState().getValue(LaserMirrorBlock.MOUNT);
        if (!LaserMirrorBlock.onGrid(pos, event.getHitResult(), mount)) return;
        boolean sneaking = player.isShiftKeyDown();
        // A plain click on the face it stands on does nothing: nothing to show.
        if (!sneaking && zone.face() == mount) return;
        MirrorPlate plate = sneaking ? null : LaserMirrorBlock.plateFor(zone, mount);
        // On the face itself, as the block's own outline is.
        double depth = event.getHitResult().getLocation().subtract(pos.getCenter()).dot(Vec3.atLowerCornerOf(zone.face().getUnitVec3i()));
        List<Vec3[]> grid = grid(zone.face(), depth, sneaking);
        List<Vec3[]> ghost = plate != null ? ghost(plate) : List.of();
        event.addCustomRenderer((outline, buffers, poses, translucent, level) -> {
            // Called for the solid and the translucent pass: drawn in the one the block's own outline is.
            if (outline.isTranslucent() != translucent) return false;
            Vec3 camera = level.cameraRenderState.pos;
            poses.pushPose();
            poses.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y + 0.5, pos.getZ() - camera.z + 0.5);
            PoseStack.Pose pose = poses.last();
            if (!ghost.isEmpty()) {
                VertexConsumer panes = buffers.getBuffer(QuantumRenderTypes.SHELL_PANE);
                for (Vec3[] quad : ghost) for (Vec3 corner : quad) panes.addVertex(pose, (float) corner.x, (float) corner.y, (float) corner.z).setColor(1f, 1f, 1f, 0.3f);
            }
            VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
            int color = outline.highContrast() ? -11010079 : ARGB.black(102);
            float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
            for (Vec3[] line : grid) {
                Vec3 a = line[0], b = line[1], d = b.subtract(a).normalize();
                lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color).setNormal(pose, (float) d.x, (float) d.y, (float) d.z).setLineWidth(width);
                lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color).setNormal(pose, (float) d.x, (float) d.y, (float) d.z).setLineWidth(width);
            }
            poses.popPose();
            // The block's own outline still drawn: it's the grid's edge.
            return false;
        });
    }

    /**
     * The parts of {@code face}, as lines relative to the block's middle: the middle square, the inner ring's edge, and
     * the diagonals splitting the rings four ways, out to the mirror's edge (the block's own outline). Merged, the rings
     * are one.
     */
    private static List<Vec3[]> grid(Direction face, double depth, boolean merged) {
        Vec3 out = Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(depth);
        Direction[] axes = across(face);
        Vec3 u = Vec3.atLowerCornerOf(axes[0].getUnitVec3i()), v = Vec3.atLowerCornerOf(axes[1].getUnitVec3i());
        List<Vec3[]> lines = new ArrayList<>();
        for (double size : merged ? new double[] {LaserMirrorBlock.SQUARE} : new double[] {LaserMirrorBlock.SQUARE, LaserMirrorBlock.RING}) {
            Vec3[] corners = {out.add(u.scale(-size)).add(v.scale(-size)), out.add(u.scale(size)).add(v.scale(-size)),
                    out.add(u.scale(size)).add(v.scale(size)), out.add(u.scale(-size)).add(v.scale(size))};
            for (int i = 0; i < 4; i++) lines.add(new Vec3[] {corners[i], corners[(i + 1) % 4]});
        }
        double edge = LaserMirrorBlock.EDGE, square = LaserMirrorBlock.SQUARE;
        for (int su = -1; su <= 1; su += 2) {
            for (int sv = -1; sv <= 1; sv += 2) {
                lines.add(new Vec3[] {out.add(u.scale(su * square)).add(v.scale(sv * square)), out.add(u.scale(su * edge)).add(v.scale(sv * edge))});
            }
        }
        return lines;
    }

    /**
     * A thin white slab where the plate would turn, relative to the block's middle, as quads: a little larger than the
     * plate all round, so set the way it already faces it wraps the plate rather than fighting it.
     */
    private static List<Vec3[]> ghost(MirrorPlate plate) {
        Vec3 n = plate.normal();
        // Along the plate: the axis it's turned about, and the way across that.
        Vec3 u = plate.toward() == null ? Vec3.atLowerCornerOf(across(plate.face())[0].getUnitVec3i())
                : Vec3.atLowerCornerOf(plate.face().getUnitVec3i()).cross(Vec3.atLowerCornerOf(plate.toward().getUnitVec3i()));
        Vec3 v = n.cross(u).normalize();
        Vec3[] corner = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = u.scale((i & 1) == 0 ? -GHOST : GHOST).add(v.scale((i & 2) == 0 ? -GHOST : GHOST)).add(n.scale((i & 4) == 0 ? -BACK : FRONT));
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        List<Vec3[]> quads = new ArrayList<>(6);
        for (int[] f : faces) quads.add(new Vec3[] {corner[f[0]], corner[f[1]], corner[f[2]], corner[f[3]]});
        return quads;
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
