package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A compound of two cubes, translucent and spinning together, each one's corners poking through the
 * other's faces (Escher's "Stars" has one): the emitter over each Rift Stabiliser. Drawn by its block
 * entity renderer, since a block model can only turn an element about one axis and cannot move.
 */
public final class CrossedCore {

    /** Degrees a tick. */
    private static final float SPIN = 1.5f;
    /** The compound of two cubes: 60° about the (1, 1, 1) diagonal, a half step of its 3-fold symmetry. */
    private static final Quaternionf COMPOUND_TURN =
            new Quaternionf().rotationAxis((float) Math.toRadians(60), new Vector3f(1, 1, 1).normalize());
    /**
     * Stands the compound on that diagonal, so the two corners the cubes share are at the top and
     * bottom and the spin turns it about its own axis.
     */
    private static final Quaternionf STAND_ON_CORNER =
            new Quaternionf().rotationTo(new Vector3f(1, 1, 1).normalize(), new Vector3f(0, 1, 0));

    private CrossedCore() {}

    /**
     * Draws the core centred on the pose's origin. Faces are not culled, so the far side of each cube
     * shows through the near one.
     *
     * @param size    edge of each cube, in blocks
     * @param texture a block texture, e.g. {@code quantimium:block/rift_stabiliser_core_lit}
     * @param light   packed light; full bright for a glowing core
     */
    public static void render(PoseStack poses, MultiBufferSource buffers, Identifier texture, float size,
                              float time, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
        VertexConsumer buffer = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float half = size / 2;

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(time * SPIN));
        poses.mulPose(STAND_ON_CORNER);
        cube(poses, buffer, sprite, half, light);
        // The second cube, turned 60° about a space diagonal (corner to opposite corner), spinning
        // with the first: each cube's corners poke out through the middle of the other's faces.
        poses.mulPose(COMPOUND_TURN);
        cube(poses, buffer, sprite, half, light);
        poses.popPose();
    }

    private static void cube(PoseStack poses, VertexConsumer buffer, TextureAtlasSprite sprite, float h, int light) {
        PoseStack.Pose pose = poses.last();
        Matrix4f matrix = pose.pose();
        // Each face: normal, then its corners counter-clockwise seen from outside.
        float[][][] faces = {
                {{0, 1, 0}, {-h, h, -h}, {-h, h, h}, {h, h, h}, {h, h, -h}},
                {{0, -1, 0}, {-h, -h, h}, {-h, -h, -h}, {h, -h, -h}, {h, -h, h}},
                {{0, 0, -1}, {h, h, -h}, {h, -h, -h}, {-h, -h, -h}, {-h, h, -h}},
                {{0, 0, 1}, {-h, h, h}, {-h, -h, h}, {h, -h, h}, {h, h, h}},
                {{-1, 0, 0}, {-h, h, -h}, {-h, -h, -h}, {-h, -h, h}, {-h, h, h}},
                {{1, 0, 0}, {h, h, h}, {h, -h, h}, {h, -h, -h}, {h, h, -h}},
        };
        float[][] uv = {{sprite.getU0(), sprite.getV0()}, {sprite.getU0(), sprite.getV1()},
                {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()}};
        Vector3f normal = new Vector3f();
        Matrix3f normals = pose.normal();
        for (float[][] face : faces) {
            normals.transform(normal.set(face[0][0], face[0][1], face[0][2]));
            for (int i = 0; i < 4; i++) {
                float[] corner = face[i + 1];
                buffer.addVertex(matrix, corner[0], corner[1], corner[2])
                        .setColor(1f, 1f, 1f, 1f)
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(normal.x, normal.y, normal.z);
            }
        }
    }
}
