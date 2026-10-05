package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.MirrorLensClient;
import com.kadikular.quantimium.client.model.VeiledModel;
import com.kadikular.quantimium.entity.Veiled;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Cloth under ordinary light, plus a full-bright layer carrying only what glows: the rift ring round
 * the hood's opening, the small tesseract inside it, the gaze, the fingertips and the drips on the
 * hem. Both fade together as the Lance wears it down. Both textures are placeholders to paint over.
 * The real rift face (void and folding tesseract) is a renderer job for later; the painted one
 * stands in until then.
 */
public final class VeiledRenderer extends MobRenderer<Veiled, VeiledRenderer.State, VeiledModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/veiled.png");

    private static final int HOLD_SEGMENTS = 18;

    private static final Identifier GLOW =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/entity/veiled_glow.png");

    public static class State extends VeiledModel.State {
        /** The hall holding it, if one is. */
        @Nullable BlockPos hall;
        float captureProgress;
        int carried;
    }

    public VeiledRenderer(EntityRendererProvider.Context context) {
        super(context, new VeiledModel(context.bakeLayer(VeiledModel.LAYER)), 0.6F);
        // Translucent and emissive rather than vanilla's additive eyes, so the glow fades with the body.
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void submit(PoseStack poses, SubmitNodeCollector collector, int lightCoords, State state,
                               float yRot, float xRot) {
                collector.submitModel(getParentModel(), state, poses, RenderTypes.entityTranslucentEmissive(GLOW),
                        LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, getParentModel().tint(0xFFFFFFFF),
                        null, state.outlineColor, null);
            }
        });
        // The rift where its face should be, drawn in the hood's space so it turns with the stare.
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void submit(PoseStack poses, SubmitNodeCollector collector, int lightCoords, State state,
                               float yRot, float xRot) {
                SubmitBuffers buffers = new SubmitBuffers(collector);
                VeiledFace.render(getParentModel(), poses, buffers, state.ageInTicks, state.opacity);
                buffers.flush();
            }
        });
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Veiled entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // Faint through a Mirror Lens: a slice of the mirror, not the mirror itself.
        state.opacity = (0.3f + 0.7f * entity.coherence()) * (1.0f - 0.9f * entity.captureProgress())
                * MirrorLensClient.ghostOpacity();
        state.hall = entity.captureHall().orElse(null);
        state.captureProgress = entity.captureProgress();
        state.carried = entity.carriedCount();
    }

    /** The beam reaches back to the hall, so keep drawing while either end is on screen. */
    @Override
    public boolean shouldRender(Veiled entity, Frustum frustum, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) return true;
        return entity.captureHall()
                .map(hall -> frustum.isVisible(entity.getBoundingBox().minmax(new AABB(hall).inflate(1.0, 3.0, 1.0))))
                .orElse(false);
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poses, collector, camera);
        SubmitBuffers buffers = new SubmitBuffers(collector);
        if (state.hall != null) renderHold(state, state.hall, camera, poses, buffers);
        renderCarried(state, poses, buffers);
        buffers.flush();
    }

    /** The whole body fades with the model. */
    @Override
    protected int getModelTint(State state) {
        return getModel().tint(0xFFFFFFFF);
    }

    /**
     * Each Sophon it has taken, circling its chest as a small azure cell: somebody's double, held in its
     * violet, until it is made to let go.
     */
    private void renderCarried(State state, PoseStack poses, MultiBufferSource buffers) {
        int count = state.carried;
        if (count <= 0) return;
        float time = state.ageInTicks;
        float chest = state.boundingBoxHeight * 0.62f;
        for (int i = 0; i < count; i++) {
            float angle = time * 0.035f + i * Mth.TWO_PI / count;
            poses.pushPose();
            poses.translate(Mth.cos(angle) * 0.68f, chest + Mth.sin(time * 0.07f + i) * 0.08f, Mth.sin(angle) * 0.68f);
            poses.scale(0.32f, 0.32f, 0.32f);
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 2.0f + i * 40.0f));
            TesseractShell.render(poses, buffers, time + i * 37.0f, TesseractShell.Tint.FLUX);
            poses.popPose();
        }
    }

    /**
     * The hall's field reaching out to take it: a ribbon in the hall's violet from the middle of the
     * cell to its chest, thickening and brightening as the hold tightens. Particles streaming along
     * it come from the entity itself.
     */
    private void renderHold(State state, BlockPos hall, CameraRenderState camera, PoseStack poses,
                            MultiBufferSource buffers) {
        Vec3 origin = new Vec3(state.x, state.y, state.z);
        Vec3 from = new Vec3(0.0, state.boundingBoxHeight * 0.55, 0.0);
        Vec3 to = Vec3.atCenterOf(hall.above(2)).subtract(origin);
        Vec3 eye = camera.pos.subtract(origin);
        float progress = state.captureProgress;
        float time = state.ageInTicks;
        // Straining at first, pulled taut as the hold completes.
        Vec3[] points = BeamRibbon.wave(from, to, HOLD_SEGMENTS, time, 0.12f * (1.0f - progress) + 0.02f);
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.5f);
        VertexConsumer glow = buffers.getBuffer(QuantumRenderTypes.FIELD_GLOW);
        PoseStack.Pose pose = poses.last();
        BeamRibbon.draw(glow, pose, eye, points, 0.18f + 0.12f * progress, 0.62f, 0.42f, 0.96f,
                (0.18f + 0.2f * progress) * pulse);
        BeamRibbon.draw(glow, pose, eye, points, 0.05f + 0.04f * progress, 0.90f, 0.86f, 1.0f,
                (0.55f + 0.4f * progress) * pulse);
    }

    /** Translucent, so the model's coherence fade shows. */
    @Override
    protected @Nullable RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible ? RenderTypes.entityTranslucent(TEXTURE) : super.getRenderType(state, bodyVisible, translucent, glowing);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURE;
    }
}
