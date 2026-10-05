package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Camera-facing beams through a list of points, for field glow. Points and camera are in the same
 * space as the pose's origin — block- or entity-relative in a renderer — so the ribbon faces the eye
 * wherever it is drawn from.
 */
public final class BeamRibbon {

    private BeamRibbon() {}

    /** A line from {@code from} to {@code to} that thrashes in the middle and is pinned at both ends. */
    public static Vec3[] wave(Vec3 from, Vec3 to, int segments, float time, float thrash) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        Vec3 direction = length < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : span.scale(1.0 / length);
        Vec3 bend = direction.cross(new Vec3(0.0, 1.0, 0.0));
        bend = bend.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : bend.normalize();
        Vec3 lift = bend.cross(direction).normalize();
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float along = i / (float) segments;
            float slack = Mth.sin(along * Mth.PI) * thrash;
            points[i] = from.add(span.scale(along))
                    .add(bend.scale(Mth.sin(time * 0.8f - along * 9.0f) * slack))
                    .add(lift.scale(Mth.cos(time * 0.6f - along * 6.0f) * slack));
        }
        return points;
    }

    public static void draw(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Vec3[] points,
                            float halfWidth, float red, float green, float blue, float alpha) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            Vec3 sideA = side(camera, a, b, halfWidth);
            Vec3 sideB = side(camera, b, i + 2 < points.length ? points[i + 2] : b.add(b.subtract(a)), halfWidth);
            vertex(buffer, pose, a.subtract(sideA), red, green, blue, alpha);
            vertex(buffer, pose, a.add(sideA), red, green, blue, alpha);
            vertex(buffer, pose, b.add(sideB), red, green, blue, alpha);
            vertex(buffer, pose, b.subtract(sideB), red, green, blue, alpha);
        }
    }

    private static Vec3 side(Vec3 camera, Vec3 at, Vec3 next, float halfWidth) {
        Vec3 cross = next.subtract(at).cross(at.subtract(camera));
        return cross.lengthSqr() < 1.0E-8 ? Vec3.ZERO : cross.normalize().scale(halfWidth);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float red, float green,
                               float blue, float alpha) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(red, green, blue, alpha);
    }
}
