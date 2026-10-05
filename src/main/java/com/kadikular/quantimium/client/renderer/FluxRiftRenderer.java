package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.client.ClientPhaseState;
import com.kadikular.quantimium.client.FluxRiftClientCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Flux rifts, drawn as a tear grown past the point of closing. From the real world you see only its
 * shadow — a dull, bruised outline around the void. From the mirror you see what it really is: a
 * bright wound with the anchor burning in the middle, dimming as the lance decoheres it.
 */
public final class FluxRiftRenderer {

    /** Violet wash in the mirror. Heavy at the rim, thin in the middle so the void still reads as deep. */
    private static final MirrorRiftRenderer.Tint MIRROR_TINT =
            new MirrorRiftRenderer.Tint(0.62f, 0.16f, 0.82f, 0.55f, 0.16f);
    /** The shadow: a dull bruise, darker and less saturated than the real thing. */
    private static final MirrorRiftRenderer.Tint SHADOW_TINT =
            new MirrorRiftRenderer.Tint(0.30f, 0.06f, 0.24f, 0.45f, 0.12f);

    private FluxRiftRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, Vec3 viewerMiddle, float partialTick,
                              float time) {
        List<FluxRiftClientCache.Entry> rifts = FluxRiftClientCache.visible();
        if (rifts.isEmpty()) return;
        boolean mirror = ClientPhaseState.isActive();

        // The tear renderer flushes its own buffers per call, so every void goes first and the
        // anchors share one glow pass after them.
        for (FluxRiftClientCache.Entry rift : rifts) {
            float presence = rift.presence(partialTick);
            if (presence <= 0.01f) continue;
            float shot = rift.shot(partialTick);
            float breathe = 0.92f + 0.08f * Mth.sin(time * 0.08f + rift.shapeSeed());
            // Being decohered, the wound convulses: a fast shudder on top of the slow breath.
            float shudder = 1.0f + shot * 0.07f * Mth.sin(time * 2.3f) * Mth.sin(time * 0.9f + 1.3f);
            // Drained, the wound narrows: a nearly closed rift is a thin slit.
            float narrowing = 0.3f + 0.7f * rift.coherence();
            float width = presence * breathe * shudder * narrowing;
            // Jitter grows with stage, with damage taken, and sharply while the beam is on it.
            float jitter = 0.35f + 0.1f * rift.stage() + (1.0f - rift.coherence()) * 0.8f + shot * 1.2f;
            // Held by stabilisers, the edge settles to a fraction of its thrash.
            if (rift.stabilised()) jitter *= 0.4f;
            if (mirror) {
                // The outline flashes towards white on the beat while it is being shot.
                float flash = shot * (0.5f + 0.5f * Mth.sin(time * 1.9f));
                MirrorRiftRenderer.render(rift.centre(partialTick), rift.shapeSeed(), width,
                        rift.scale(partialTick), Mth.lerp(flash, 0.70f, 0.95f), Mth.lerp(flash, 0.42f, 0.9f), 1.0f,
                        MIRROR_TINT, jitter, poseStack, buffers, camera, viewerMiddle, time);
            } else {
                MirrorRiftRenderer.render(rift.centre(partialTick), rift.shapeSeed(), width,
                        rift.scale(partialTick), 0.50f, 0.10f, 0.32f, SHADOW_TINT, jitter * 0.6f,
                        poseStack, buffers, camera, viewerMiddle, time);
            }
        }
        if (!mirror) return;

        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        for (FluxRiftClientCache.Entry rift : rifts) {
            float presence = rift.presence(partialTick);
            if (presence <= 0.01f) continue;
            anchor(glow, poseStack, camera, rift, presence, partialTick, time);
        }
    }

    /** A camera-facing star at the centre. Pulled towards the viewer so it never sinks into the void plane. */
    private static void anchor(VertexConsumer glow, PoseStack poseStack, Vec3 camera,
                               FluxRiftClientCache.Entry rift, float presence, float partialTick, float time) {
        Vec3 centre = rift.centre(partialTick);
        float scale = rift.scale(partialTick);
        float coherence = rift.coherence();
        Vector3f toCamera = new Vector3f((float) (camera.x - centre.x), (float) (camera.y - centre.y),
                (float) (camera.z - centre.z));
        if (toCamera.lengthSquared() < 1.0E-6f) return;
        toCamera.normalize();
        Vector3f across = new Vector3f(0.0f, 1.0f, 0.0f).cross(toCamera);
        if (across.lengthSquared() < 1.0E-6f) across.set(1.0f, 0.0f, 0.0f);
        across.normalize();
        Vector3f up = new Vector3f(toCamera).cross(across).normalize();

        // A drained anchor stutters: the closer it is to giving, the more it flickers.
        float flicker = coherence >= 0.999f ? 1.0f
                : 1.0f - (1.0f - coherence) * 0.6f * (0.5f + 0.5f * Mth.sin(time * 1.7f));
        // Kept deliberately faint: it marks the target, it should not be a light source.
        float size = scale * (0.10f + 0.12f * coherence) * presence;
        float alpha = Mth.clamp((0.12f + 0.26f * coherence) * flicker * presence, 0.0f, 1.0f);

        poseStack.pushPose();
        poseStack.translate(centre.x - camera.x + toCamera.x * 0.12f * scale,
                centre.y - camera.y + toCamera.y * 0.12f * scale,
                centre.z - camera.z + toCamera.z * 0.12f * scale);
        PoseStack.Pose pose = poseStack.last();
        float spin = time * 0.03f;
        for (int arm = 0; arm < 4; arm++) {
            float angle = spin + arm * Mth.HALF_PI;
            Vector3f tip = rotate(across, up, angle).mul(size * 2.0f);
            Vector3f side = rotate(across, up, angle + Mth.HALF_PI).mul(size * 0.18f);
            spike(glow, pose, tip, side, 0.66f, 0.53f, 1.0f, alpha * 0.6f);
        }
        disc(glow, pose, across, up, size, 0.82f, 0.73f, 0.99f, alpha);
        poseStack.popPose();
    }

    private static Vector3f rotate(Vector3f across, Vector3f up, float angle) {
        return new Vector3f(across).mul(Mth.cos(angle)).add(new Vector3f(up).mul(Mth.sin(angle)));
    }

    /** A kite from the centre out to {@code tip}: bright at the core, gone at every edge. */
    private static void spike(VertexConsumer buffer, PoseStack.Pose pose, Vector3f tip, Vector3f side,
                              float red, float green, float blue, float alpha) {
        buffer.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, side.x, side.y, side.z).setColor(red, green, blue, 0.0f);
        buffer.addVertex(pose, tip.x, tip.y, tip.z).setColor(red, green, blue, 0.0f);
        buffer.addVertex(pose, -side.x, -side.y, -side.z).setColor(red, green, blue, 0.0f);
    }

    /** Soft round glow as a fan of degenerate quads, so it fades radially rather than along a diagonal. */
    private static void disc(VertexConsumer buffer, PoseStack.Pose pose, Vector3f across, Vector3f up,
                             float radius, float red, float green, float blue, float alpha) {
        int segments = 10;
        for (int i = 0; i < segments; i++) {
            Vector3f from = rotate(across, up, Mth.TWO_PI * i / segments).mul(radius);
            Vector3f to = rotate(across, up, Mth.TWO_PI * (i + 1) / segments).mul(radius);
            buffer.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(red, green, blue, alpha);
            buffer.addVertex(pose, from.x, from.y, from.z).setColor(red, green, blue, 0.0f);
            buffer.addVertex(pose, to.x, to.y, to.z).setColor(red, green, blue, 0.0f);
            buffer.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(red, green, blue, alpha);
        }
    }
}
