package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Unfolding Array at work. Idle, a ring of light turns slowly on the pad. With someone being
 * unfolded, each pylon reaches into them with a beam, a proton unfolds around them as a tesseract
 * that swells as it goes, and their double peels away from them, faint at first, until it stands
 * beside them whole and is folded up into the Sophon.
 */
public class UnfoldingArrayBER extends SubmittingBER<UnfoldingArrayBlockEntity> {

    private static final float PAD_TOP = 6.0f / 16.0f;

    public UnfoldingArrayBER(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(UnfoldingArrayBlockEntity array, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (array.getLevel() == null) return;
        float time = array.getLevel().getGameTime() + partialTick;
        BlockPos pos = array.getBlockPos();
        boolean working = array.status() == UnfoldingArrayBlockEntity.STATUS_UNFOLDING;
        float progress = Mth.clamp((array.progress() + (working ? partialTick : 0)) / UnfoldingArrayBlockEntity.UNFOLD_TICKS,
                0.0f, 1.0f);
        Player subject = array.subject() == null ? null : array.getLevel().getPlayerByUUID(array.subject());
        boolean onPad = subject != null && array.onPad(subject);

        VertexConsumer light = buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW);
        Matrix4f matrix = poses.last().pose();
        float intensity = onPad ? 0.45f + 0.55f * progress : 0.25f;
        runes(light, matrix, time, intensity, onPad ? 0.08f + progress * 0.25f : 0.02f);
        if (!onPad || array.progress() <= 0) return;

        // Where the subject is, in the pad's own space.
        Vec3 feet = subject.getPosition(partialTick).subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 chest = feet.add(0, 1.1, 0);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position()
                .subtract(pos.getX(), pos.getY(), pos.getZ());

        // Each pylon's crystal reaches into them.
        for (BlockPos pylon : UnfoldingArrayBlockEntity.pylons(BlockPos.ZERO)) {
            Vec3 tip = new Vec3(pylon.getX() + 0.5, 0.95, pylon.getZ() + 0.5);
            Vec3[] points = BeamRibbon.wave(tip, chest, 14, time, 0.02f + 0.08f * progress);
            BeamRibbon.draw(light, poses.last(), camera, points, 0.07f + 0.08f * progress, 0.20f, 0.45f, 1.0f,
                    0.20f + 0.25f * progress);
            BeamRibbon.draw(light, poses.last(), camera, points, 0.022f, 0.75f, 0.87f, 1.0f, 0.55f + 0.35f * progress);
        }

        // Their double steps out of them, forward, and turns to look back at them, steadying as it comes.
        // Drawn first, and flushed, so the proton's glass lies over it rather than hiding it.
        float peel = Mth.clamp((progress - 0.15f) / 0.85f, 0.0f, 1.0f);
        if (peel > 0.0f) {
            float yaw = subject.getViewYRot(partialTick);
            float ahead = Mth.DEG_TO_RAD * yaw;
            double drift = 0.2 + 1.0 * peel;
            float turn = Mth.clamp((peel - 0.2f) / 0.5f, 0.0f, 1.0f);
            poses.pushPose();
            poses.translate(feet.x - Mth.sin(ahead) * drift, feet.y, feet.z + Mth.cos(ahead) * drift);
            DoubleRenderer.render(poses, buffers, subject.getUUID(), yaw + 180.0f * turn, time, peel,
                    1.0f - 0.6f * (1.0f - peel), pos.hashCode());
            poses.popPose();
        }

        // The proton unfolding round them: a tesseract that swells from a point, then thins away as the
        // double comes clear of it, so by the end nothing stands between you and yourself.
        float fade = 1.0f - Mth.clamp((progress - 0.55f) / 0.35f, 0.0f, 1.0f);
        if (fade > 0.01f) {
            poses.pushPose();
            poses.translate(chest.x, chest.y - 0.1, chest.z);
            float scale = 0.25f + 1.35f * Mth.sqrt(progress);
            poses.scale(scale, scale, scale);
            poses.mulPose(Axis.YP.rotationDegrees(time * (1.0f + progress * 4.0f)));
            TesseractShell.render(poses, buffers, time * (1.0f + progress * 2.0f), TesseractShell.edgeTypeFor(poses),
                    TesseractShell.Tint.FLUX, fade);
            poses.popPose();
        }
    }

    /** A turning ring of light on the pad, with spokes, brighter as the work goes on. */
    private static void runes(VertexConsumer light, Matrix4f matrix, float time, float intensity, float speed) {
        int segments = 40;
        float step = Mth.TWO_PI / segments;
        float y = PAD_TOP + 0.003f;
        float turn = time * speed;
        for (int i = 0; i < segments; i++) {
            float a0 = i * step + turn;
            float a1 = a0 + step * 0.7f;
            float lit = (i % 5 == 0) ? 1.0f : 0.45f;
            annulus(light, matrix, a0, a1, 0.30f, 0.40f, y, 0.60f * intensity * lit);
            annulus(light, matrix, -a1 * 0.6f, -a0 * 0.6f, 0.43f, 0.47f, y, 0.35f * intensity);
        }
    }

    private static void annulus(VertexConsumer light, Matrix4f matrix, float a0, float a1, float inner, float outer,
                                float y, float alpha) {
        float cx = 0.5f;
        float cz = 0.5f;
        light.addVertex(matrix, cx + Mth.cos(a0) * inner, y, cz + Mth.sin(a0) * inner).setColor(0.204f, 0.522f, 1.0f, alpha);
        light.addVertex(matrix, cx + Mth.cos(a1) * inner, y, cz + Mth.sin(a1) * inner).setColor(0.204f, 0.522f, 1.0f, alpha);
        light.addVertex(matrix, cx + Mth.cos(a1) * outer, y, cz + Mth.sin(a1) * outer).setColor(0.204f, 0.522f, 1.0f, alpha);
        light.addVertex(matrix, cx + Mth.cos(a0) * outer, y, cz + Mth.sin(a0) * outer).setColor(0.204f, 0.522f, 1.0f, alpha);
    }

    @Override
    public AABB getRenderBoundingBox(UnfoldingArrayBlockEntity array) {
        return new AABB(array.getBlockPos()).inflate(2.0, 0.0, 2.0).expandTowards(0, 3.0, 0);
    }
}
