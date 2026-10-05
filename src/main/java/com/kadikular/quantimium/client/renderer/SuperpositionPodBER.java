package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.superposition.Relay;
import com.kadikular.quantimium.superposition.Superposition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Superposition Pod as it is meant to be seen. A glass capsule that catches the light at its
 * edges, with azure rings turning at its foot and crown and a pool of light on its floor; the double
 * asleep inside it; and when someone comes or goes, a scan sweeping through the glass.
 *
 * <p>The front of the glass is a door: it slides round and open as you come near an empty pod, and
 * closes behind you once you are inside. A pod holding a double stays sealed.
 */
public class SuperpositionPodBER extends SubmittingBER<SuperpositionPodBlockEntity> {

    private static final int SEGMENTS = 28;
    private static final int BANDS = 6;
    private static final float RADIUS = 0.44f;
    private static final float BOTTOM = 0.07f;
    private static final float TOP = 1.93f;
    /** Half the width of the sliding front, in radians. */
    private static final float DOOR_HALF = Mth.DEG_TO_RAD * 62.0f;
    /** How far each half of the front slides round when fully open. */
    private static final float DOOR_TRAVEL = Mth.DEG_TO_RAD * 105.0f;
    private static final int EFFECT_TICKS = 30;

    private static final float GLASS_R = 0.70f;
    private static final float GLASS_G = 0.85f;
    private static final float GLASS_B = 1.00f;
    /** Flux azure, #3485ff. */
    private static final float FLUX_R = 0.204f;
    private static final float FLUX_G = 0.522f;
    private static final float FLUX_B = 1.00f;

    public SuperpositionPodBER(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SuperpositionPodBlockEntity pod, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (pod.getLevel() == null) return;
        BlockPos pos = pod.getBlockPos();
        float time = pod.getLevel().getGameTime() + partialTick;
        Direction facing = pod.getBlockState().getValue(SuperpositionPodBlock.FACING);
        float front = (float) Math.atan2(facing.getStepZ(), facing.getStepX());
        boolean formed = pod.isFormed();
        float power = formed && pod.isPowered() ? 1.0f : formed ? 0.35f : 0.15f;
        var player = Minecraft.getInstance().player;
        boolean armed = player != null && !pod.hasDouble() && pod.contains(player);

        float effect = effectProgress(pod, time);
        int kind = pod.effectKind();

        poses.pushPose();
        poses.translate(0.5, 0, 0.5);

        // The double first: the glass and light go over it.
        if (pod.hasDouble()) {
            float presence = 1.0f;
            if (effect < 1.0f && (kind == Superposition.EFFECT_DEPART || kind == Superposition.EFFECT_UNFOLD)) {
                presence = Mth.clamp(effect * 1.4f, 0.0f, 1.0f);
            } else if (effect < 1.0f && kind == Superposition.EFFECT_FORMING) {
                // A sent double builds up slowly, flickering in as it comes.
                presence = Mth.clamp(effect * effect + (Mth.sin(time * 0.9f) * 0.08f * effect), 0.0f, 1.0f);
            }
            poses.pushPose();
            poses.translate(0, BOTTOM - 0.005, 0);
            DoubleRenderer.render(poses, buffers, pod.owner(), facing.toYRot(), time, presence,
                    DoubleRenderer.health(pod.owner()), pos.hashCode());
            poses.popPose();
        }

        Matrix4f matrix = poses.last().pose();
        VertexConsumer light = buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW);
        float spin = armed ? 0.22f : 0.06f;
        ring(light, matrix, BOTTOM + 0.01f, time * spin, power);
        ring(light, matrix, TOP - 0.01f, -time * spin * 0.8f, power);
        if (pod.hasDouble() || armed) {
            float pulse = 0.75f + 0.25f * Mth.sin(time * (armed ? 0.25f : 0.07f));
            pool(light, matrix, BOTTOM + 0.004f, RADIUS * 0.95f, 0.30f * pulse * power);
            pool(light, matrix, TOP - 0.004f, RADIUS * 0.8f, 0.12f * pulse * power);
        }
        if (effect < 1.0f && kind == Superposition.EFFECT_FORMING) {
            // Scans running up the glass over and over while the double forms.
            sweep(light, matrix, Superposition.EFFECT_UNFOLD, (effect * 3.0f) % 1.0f);
        } else if (effect < 1.0f) {
            sweep(light, matrix, kind, effect);
        }

        float flash = effect < 1.0f && kind != Superposition.EFFECT_FORMING ? (1.0f - effect) * (kind == Superposition.EFFECT_ARRIVE ? 0.35f : 0.18f) : 0.0f;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        float view = (float) Math.atan2(camera.z - (pos.getZ() + 0.5), camera.x - (pos.getX() + 0.5));
        // Asking for another buffer ends the one before it, so the glow is fetched again after the panes.
        glass(buffers, matrix, front, view, pod.doorOpen(partialTick), power, flash);
        poses.popPose();
    }

    private static float effectProgress(SuperpositionPodBlockEntity pod, float time) {
        if (pod.effectStart() == Long.MIN_VALUE) return 1.0f;
        float length = pod.effectKind() == Superposition.EFFECT_FORMING ? Relay.FORMING_TICKS : EFFECT_TICKS;
        return Mth.clamp((time - pod.effectStart()) / length, 0.0f, 1.0f);
    }

    /**
     * The glass: a ring of panes whose edges catch the light (the less a pane faces you, the brighter),
     * brighter still at the foot and crown, with a highlight running down the side nearest the light.
     */
    private static void glass(MultiBufferSource buffers, Matrix4f matrix, float front, float view,
                              float door, float power, float flash) {
        VertexConsumer panes = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        float step = Mth.TWO_PI / SEGMENTS;
        float[] gleams = new float[SEGMENTS * 3];
        int gleamCount = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * step;
            float a1 = a0 + step;
            float mid = a0 + step * 0.5f;
            float relative = Mth.wrapDegrees((mid - front) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            float radius = RADIUS;
            if (Math.abs(relative) < DOOR_HALF) {
                // The front slides round, each half its own way, just outside the fixed glass.
                float shift = Math.signum(relative) * door * DOOR_TRAVEL;
                a0 += shift;
                a1 += shift;
                mid += shift;
                radius += 0.018f * door;
            }
            float facing = Mth.cos(mid - view);
            float fresnel = 1.0f - Math.abs(facing);
            float alpha = (0.035f + 0.30f * fresnel * fresnel * fresnel) * (0.4f + 0.6f * power) + flash;
            for (int band = 0; band < BANDS; band++) {
                float y0 = Mth.lerp(band / (float) BANDS, BOTTOM, TOP);
                float y1 = Mth.lerp((band + 1) / (float) BANDS, BOTTOM, TOP);
                float lift0 = edgeLift(y0);
                float lift1 = edgeLift(y1);
                quad(panes, matrix, a0, a1, radius, y0, y1, GLASS_R, GLASS_G, GLASS_B, alpha * lift0, alpha * lift1);
            }
            // A highlight down the side the light catches, as on a glass tube.
            float gleam = Mth.cos(mid - (view + 0.62f));
            if (gleam > 0.985f && facing > 0.0f) {
                gleams[gleamCount++] = a0;
                gleams[gleamCount++] = a1;
                gleams[gleamCount++] = radius + 0.002f;
            }
        }
        VertexConsumer light = buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW);
        for (int i = 0; i < gleamCount; i += 3) {
            quad(light, matrix, gleams[i], gleams[i + 1], gleams[i + 2], BOTTOM + 0.1f, TOP - 0.1f, 0.55f, 0.70f, 0.90f,
                    0.10f * power, 0.10f * power);
        }
    }

    /** Glass is thicker, and so brighter, where it meets the foot and the crown. */
    private static float edgeLift(float y) {
        float foot = Math.max(0.0f, 1.0f - (y - BOTTOM) / 0.3f);
        float crown = Math.max(0.0f, 1.0f - (TOP - y) / 0.3f);
        return 1.0f + 1.4f * (foot + crown);
    }

    /** An azure band round the glass with bright dashes chasing round it. */
    private static void ring(VertexConsumer light, Matrix4f matrix, float y, float turn, float power) {
        int segments = 48;
        float step = Mth.TWO_PI / segments;
        float radius = RADIUS + 0.012f;
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = a0 + step;
            float dash = Mth.sin(a0 * 6.0f + turn) > 0.55f ? 1.0f : 0.25f;
            float alpha = 0.55f * dash * power;
            quad(light, matrix, a0, a1, radius, y - 0.028f, y + 0.028f, FLUX_R, FLUX_G, FLUX_B, alpha, alpha);
            // A soft halo above and below the band.
            quad(light, matrix, a0, a1, radius + 0.004f, y + 0.028f, y + 0.09f, FLUX_R, FLUX_G, FLUX_B, alpha * 0.35f, 0.0f);
            quad(light, matrix, a0, a1, radius + 0.004f, y - 0.09f, y - 0.028f, FLUX_R, FLUX_G, FLUX_B, 0.0f, alpha * 0.35f);
        }
    }

    /** A disc of light, bright in the middle and fading to its edge. */
    private static void pool(VertexConsumer light, Matrix4f matrix, float y, float radius, float alpha) {
        int segments = 24;
        float step = Mth.TWO_PI / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = a0 + step;
            light.addVertex(matrix, 0, y, 0).setColor(FLUX_R, FLUX_G, FLUX_B, alpha);
            light.addVertex(matrix, 0, y, 0).setColor(FLUX_R, FLUX_G, FLUX_B, alpha);
            light.addVertex(matrix, Mth.cos(a1) * radius, y, Mth.sin(a1) * radius).setColor(FLUX_R, FLUX_G, FLUX_B, 0.0f);
            light.addVertex(matrix, Mth.cos(a0) * radius, y, Mth.sin(a0) * radius).setColor(FLUX_R, FLUX_G, FLUX_B, 0.0f);
        }
    }

    /**
     * A scan sweeping the capsule: up as a body is laid down in it (someone left, or a double was
     * unfolded), down as one is taken up (someone arrived, or a double was folded). An arrival also
     * sends a ring out across the floor.
     */
    private static void sweep(VertexConsumer light, Matrix4f matrix, int kind, float t) {
        boolean up = kind == Superposition.EFFECT_DEPART || kind == Superposition.EFFECT_UNFOLD;
        float eased = 1.0f - (1.0f - t) * (1.0f - t);
        float y = up ? Mth.lerp(eased, BOTTOM, TOP) : Mth.lerp(eased, TOP, BOTTOM);
        float fade = 1.0f - t;
        pool(light, matrix, y, RADIUS, 0.55f * fade);
        int segments = 48;
        float step = Mth.TWO_PI / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = a0 + step;
            quad(light, matrix, a0, a1, RADIUS + 0.01f, y - 0.05f, y + 0.05f, 0.75f, 0.88f, 1.0f, 0.0f, 0.9f * fade);
            quad(light, matrix, a0, a1, RADIUS + 0.01f, y + 0.05f, y + 0.15f, 0.75f, 0.88f, 1.0f, 0.9f * fade, 0.0f);
        }
        if (kind == Superposition.EFFECT_ARRIVE) {
            float radius = Mth.lerp(eased, RADIUS, 2.4f);
            for (int i = 0; i < segments; i++) {
                float a0 = i * step;
                float a1 = a0 + step;
                float inner = radius - 0.25f;
                light.addVertex(matrix, Mth.cos(a0) * inner, 0.02f, Mth.sin(a0) * inner).setColor(FLUX_R, FLUX_G, FLUX_B, 0.0f);
                light.addVertex(matrix, Mth.cos(a1) * inner, 0.02f, Mth.sin(a1) * inner).setColor(FLUX_R, FLUX_G, FLUX_B, 0.0f);
                light.addVertex(matrix, Mth.cos(a1) * radius, 0.02f, Mth.sin(a1) * radius).setColor(FLUX_R, FLUX_G, FLUX_B, 0.6f * fade);
                light.addVertex(matrix, Mth.cos(a0) * radius, 0.02f, Mth.sin(a0) * radius).setColor(FLUX_R, FLUX_G, FLUX_B, 0.6f * fade);
            }
        }
    }

    /** A curved pane between two angles on a circle, {@code a0} and {@code a1}, with its alpha ramped by height. */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, float a0, float a1, float radius, float y0, float y1,
                             float r, float g, float b, float alphaLow, float alphaHigh) {
        float x0 = Mth.cos(a0) * radius;
        float z0 = Mth.sin(a0) * radius;
        float x1 = Mth.cos(a1) * radius;
        float z1 = Mth.sin(a1) * radius;
        buffer.addVertex(matrix, x0, y0, z0).setColor(r, g, b, alphaLow);
        buffer.addVertex(matrix, x1, y0, z1).setColor(r, g, b, alphaLow);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, alphaHigh);
        buffer.addVertex(matrix, x0, y1, z0).setColor(r, g, b, alphaHigh);
    }

    @Override
    public AABB getRenderBoundingBox(SuperpositionPodBlockEntity pod) {
        // Two blocks tall, and an arrival's ring runs out across the cradle.
        return new AABB(pod.getBlockPos()).expandTowards(0, 1, 0).inflate(2.5, 0.2, 2.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
