package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity;
import com.kadikular.quantimium.laser.MirrorPlate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A Laser Mirror's plate, and the hub behind it, at the plate's true angle: its block model is only the stand, since a
 * plate turns in 22.5° steps no block model can. The plate's face's middle is the block's middle, where beams bounce.
 */
public class LaserMirrorBER extends SubmittingBER<LaserMirrorBlockEntity> {

    private static final Identifier STEEL = Identifier.fromNamespaceAndPath(Quantimium.MODID, "block/laser_mirror_steel");
    private static final Identifier MIRROR = Identifier.fromNamespaceAndPath(Quantimium.MODID, "block/laser_mirror_plate");
    private static final Identifier SPLITTER = Identifier.fromNamespaceAndPath(Quantimium.MODID, "block/laser_splitter_plate");
    private static final float PX = 1 / 16f;

    public LaserMirrorBER(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void render(LaserMirrorBlockEntity mirror, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        draw(mirror.plate(), mirror.getBlockState().getBlock() instanceof LaserMirrorBlock block && block.splits(), poses, buffers, packedLight, 1f);
    }

    /**
     * The plate facing {@code plate}, centred on the block the pose is at; {@code alpha} below 1 for a ghost of where it
     * would turn.
     */
    public static void draw(MirrorPlate plate, boolean splits, PoseStack poses, MultiBufferSource buffers, int light, float alpha) {
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite steel = atlas.getSprite(STEEL);
        TextureAtlasSprite face = atlas.getSprite(splits ? SPLITTER : MIRROR);
        VertexConsumer buffer = buffers.getBuffer(alpha < 1f ? RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)
                : RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        Vec3 n = plate.normal();
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        // Drawn facing +z: turned so its face faces the plate's way.
        poses.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float) n.x, (float) n.y, (float) n.z));
        // The plate: 10 x 10, a pixel thick, its face at the block's middle. A splitter's is silver both sides.
        box(poses, buffer, -5, -5, -1, 5, 5, 0, steel, face, splits ? face : steel, light, alpha);
        // The hub just behind it.
        box(poses, buffer, -1, -1, -3, 1, 1, -1, steel, steel, steel, light, alpha);
        poses.popPose();
    }

    /** A box in pixels: {@code front} on its +z face, {@code back} on its -z face, {@code side} on the rest. */
    private static void box(PoseStack poses, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                            TextureAtlasSprite side, TextureAtlasSprite front, TextureAtlasSprite back, int light, float alpha) {
        PoseStack.Pose pose = poses.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normals = pose.normal();
        float a = x0 * PX, b = y0 * PX, c = z0 * PX, d = x1 * PX, e = y1 * PX, f = z1 * PX;
        float[][][] faces = {
                {{0, 0, 1}, {a, e, f}, {a, b, f}, {d, b, f}, {d, e, f}},
                {{0, 0, -1}, {d, e, c}, {d, b, c}, {a, b, c}, {a, e, c}},
                {{0, 1, 0}, {a, e, c}, {a, e, f}, {d, e, f}, {d, e, c}},
                {{0, -1, 0}, {a, b, f}, {a, b, c}, {d, b, c}, {d, b, f}},
                {{-1, 0, 0}, {a, e, c}, {a, b, c}, {a, b, f}, {a, e, f}},
                {{1, 0, 0}, {d, e, f}, {d, b, f}, {d, b, c}, {d, e, c}},
        };
        Vector3f normal = new Vector3f();
        for (int i = 0; i < faces.length; i++) {
            TextureAtlasSprite sprite = i == 0 ? front : i == 1 ? back : side;
            // The plate's own 10 x 10 of its texture (from 3 to 13) on its faces; a strip of steel on its edges.
            float u0 = sprite.getU(3 / 16f), u1 = sprite.getU(13 / 16f), v0 = sprite.getV(3 / 16f), v1 = sprite.getV(13 / 16f);
            float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
            float[][] face = faces[i];
            normals.transform(normal.set(face[0][0], face[0][1], face[0][2]));
            for (int k = 0; k < 4; k++) {
                float[] corner = face[k + 1];
                buffer.addVertex(matrix, corner[0], corner[1], corner[2])
                        .setColor(1f, 1f, 1f, alpha)
                        .setUv(uv[k][0], uv[k][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(normal.x, normal.y, normal.z);
            }
        }
    }
}
