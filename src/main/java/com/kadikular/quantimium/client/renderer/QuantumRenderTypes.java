// Path: src/main/java/com/kadikular/quantimium/client/renderer/QuantumRenderTypes.java
package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.Quantimium;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Untextured, unlit render types for the containment hologram, beams and glows.
 *
 * <p>The panes and cores write depth. Skipping it would be the usual choice for translucent geometry,
 * but anything drawn later in the frame — other block entities, translucent terrain — would then paint
 * over the shell no matter how far behind it stood. {@link TesseractShell} keeps the shell readable
 * despite the depth writes by drawing the machine first, then the wireframe, then the panes from back
 * to front.
 *
 * <p>Line width is a vertex attribute since 1.21.11 rather than render state. Each edge type still
 * stands for one width, and {@link #buffer} (or {@link SubmitBuffers}) gives its vertices that width.
 */
public final class QuantumRenderTypes {

    /** Flat colour, translucent, both faces. Depth test on; whether it writes depth varies. */
    private static RenderPipeline flat(String name, BlendFunction blend, boolean writeDepth) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Quantimium.MODID, "pipeline/" + name))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(blend))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writeDepth))
                .build();
    }

    /** Vanilla's translucent line pipeline, with or without depth writes. */
    private static RenderPipeline lines(String name, boolean writeDepth) {
        return RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Quantimium.MODID, "pipeline/" + name))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writeDepth))
                .build();
    }

    private static final RenderPipeline FLAT_DEPTH = flat("flat_depth", BlendFunction.TRANSLUCENT, true);
    private static final RenderPipeline FLAT_NO_DEPTH = flat("flat_no_depth", BlendFunction.TRANSLUCENT, false);
    private static final RenderPipeline ADDITIVE = flat("additive", BlendFunction.LIGHTNING, false);
    private static final RenderPipeline LINES_DEPTH = lines("lines_depth", true);
    private static final RenderPipeline LINES_NO_DEPTH = lines("lines_no_depth", false);
    /**
     * Vanilla's eyes pipeline as it was before 1.21.11: textured, full bright, and added to what is
     * behind it. Vanilla's own eyes blend as translucent now, which paints a dim copy over the body.
     */
    private static final RenderPipeline ADDITIVE_ENTITY = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Quantimium.MODID, "pipeline/additive_entity"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    /** Vanilla's translucent particles, without depth writes: see {@code FieldParticle#SOFT_LAYER}. */
    public static final RenderPipeline SOFT_PARTICLE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Quantimium.MODID, "pipeline/soft_particle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    /**
     * What a shader pack should draw each pipeline as, by Iris's program names: Iris doesn't know our
     * pipelines, and skips them or draws them black without being told ({@code compat/iris}).
     */
    public static final java.util.Map<RenderPipeline, String> SHADER_PROGRAMS = java.util.Map.of(
            FLAT_DEPTH, "BASIC", FLAT_NO_DEPTH, "BASIC", ADDITIVE, "BASIC",
            LINES_DEPTH, "LINES", LINES_NO_DEPTH, "LINES",
            ADDITIVE_ENTITY, "EMISSIVE_ENTITIES", SOFT_PARTICLE, "PARTICLES_TRANSLUCENT");

    /** Every pipeline above, for {@code RegisterRenderPipelinesEvent} to compile up front. */
    public static final List<RenderPipeline> PIPELINES = List.of(FLAT_DEPTH, FLAT_NO_DEPTH, ADDITIVE, LINES_DEPTH, LINES_NO_DEPTH,
            ADDITIVE_ENTITY, SOFT_PARTICLE);

    private static RenderType quads(String name, RenderPipeline pipeline) {
        return RenderType.create("quantimium_" + name, RenderSetup.builder(pipeline).createRenderSetup());
    }

    private static RenderType edges(String name, RenderPipeline pipeline) {
        return RenderType.create("quantimium_" + name, RenderSetup.builder(pipeline)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .createRenderSetup());
    }

    public static final RenderType HOLO_PANE = quads("holo_pane", FLAT_DEPTH);

    /**
     * A Tesseract shell's faint panes. They write no depth: the shell floats in front of glass (a Fold
     * Core sits among its rails), and translucent blocks drawn after it would otherwise vanish behind
     * every pane.
     */
    public static final RenderType SHELL_PANE = quads("shell_pane", FLAT_NO_DEPTH);

    /**
     * A beam's bright core: field glow that writes depth, so translucent blocks behind it (glass,
     * Foundry tanks) drawn later stay behind it, and glass in front still lays over it. Halos stay
     * on {@link #FIELD_GLOW}, so their faint edges never cut holes in what's behind.
     */
    public static final RenderType BEAM_CORE = quads("beam_core", FLAT_DEPTH);

    /**
     * Glow quads that never write depth. A field wrapped around an item has to leave the item
     * visible, so nothing this pass writes may reject the item behind it.
     */
    public static final RenderType FIELD_GLOW = quads("field_glow", FLAT_NO_DEPTH);

    /**
     * Additive light that writes no depth: rings, scan sweeps and flashes that should brighten what is
     * behind them rather than cover it.
     */
    public static final RenderType ADDITIVE_GLOW = quads("additive_glow", ADDITIVE);

    private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderType.create(
            "quantimium_glow", RenderSetup.builder(ADDITIVE_ENTITY).withTexture("Sampler0", texture).createRenderSetup()));

    /** An entity texture drawn as light: added to the scene, as vanilla's eyes were before 1.21.11. */
    public static RenderType glow(Identifier texture) {
        return GLOW.apply(texture);
    }

    /** Widest edge, used close up and for anything drawn outside the world. */
    private static final float EDGE_BASE_WIDTH = 2.5f;
    /** Thinnest edge. A wireframe that keeps shrinking eventually stops reading as a shape at all. */
    private static final float EDGE_MIN_WIDTH = 1.0f;
    /** Quantisation of the width, so a moving camera cannot mint an unbounded set of render types. */
    private static final float EDGE_WIDTH_STEP = 0.25f;
    /** Nearer than this, edges keep their full width. */
    private static final float EDGE_FULL_WIDTH_DISTANCE = 12.0f;

    /** The width, in pixels at 1080p, each edge type's vertices carry. */
    private static final Map<RenderType, Float> EDGE_WIDTH = new IdentityHashMap<>();

    private static RenderType edgeType(String name, RenderPipeline pipeline, float width) {
        RenderType type = edges(name, pipeline);
        EDGE_WIDTH.put(type, width);
        return type;
    }

    private static RenderType[] edgeWidths(String namePrefix, RenderPipeline pipeline) {
        int steps = Math.round((EDGE_BASE_WIDTH - EDGE_MIN_WIDTH) / EDGE_WIDTH_STEP) + 1;
        RenderType[] widths = new RenderType[steps];
        for (int step = 0; step < steps; step++) {
            widths[step] = edgeType(namePrefix + step, pipeline, EDGE_BASE_WIDTH - step * EDGE_WIDTH_STEP);
        }
        return widths;
    }

    private static final RenderType[] EDGE_WIDTHS = edgeWidths("holo_edge_", LINES_DEPTH);
    private static final RenderType[] FIELD_EDGE_WIDTHS = edgeWidths("field_edge_", LINES_NO_DEPTH);

    /** The hologram wireframe at full width; writes depth. */
    public static final RenderType HOLO_EDGE = EDGE_WIDTHS[0];

    /** The wireframe for a shell shrunk into an inventory slot, where the full width reads as heavy. */
    public static final RenderType HOLO_EDGE_FINE = edgeType("holo_edge_fine", LINES_DEPTH, 1.275f);

    /**
     * The wireframe for geometry {@code distanceToCamera} blocks away. Line widths are in screen
     * pixels, so a shell that is not scaled with distance keeps full-width edges as it shrinks until
     * the whole projection collapses into a blob. Apparent size falls with 1/distance, so the width
     * follows it, floored so a far shell thins out rather than disappearing.
     */
    public static RenderType holoEdge(double distanceToCamera) {
        return EDGE_WIDTHS[widthStep(distanceToCamera)];
    }

    /** As {@link #holoEdge}, but without depth writes, for a field drawn around an item. */
    public static RenderType fieldEdge(double distanceToCamera) {
        return FIELD_EDGE_WIDTHS[widthStep(distanceToCamera)];
    }

    private static int widthStep(double distanceToCamera) {
        float width = distanceToCamera <= EDGE_FULL_WIDTH_DISTANCE
                ? EDGE_BASE_WIDTH
                : (float) (EDGE_BASE_WIDTH * EDGE_FULL_WIDTH_DISTANCE / distanceToCamera);
        int step = Math.round((EDGE_BASE_WIDTH - width) / EDGE_WIDTH_STEP);
        return Math.clamp(step, 0, EDGE_WIDTHS.length - 1);
    }

    /**
     * {@code buffers}' consumer for {@code type}; for one of the edge types, one that gives each vertex
     * that type's line width. Line width is a vertex attribute since 1.21.11, not render state.
     */
    public static VertexConsumer buffer(MultiBufferSource buffers, RenderType type) {
        return widthed(type, buffers.getBuffer(type));
    }

    /** {@code consumer}, giving each vertex {@code type}'s line width if it is one of the edge types. */
    static VertexConsumer widthed(RenderType type, VertexConsumer consumer) {
        Float width = EDGE_WIDTH.get(type);
        return width == null ? consumer : new WidthedLines(consumer, windowScaled(width));
    }

    /** Vanilla's window scaling for a line {@code width} pixels wide at 1080p. */
    private static float windowScaled(float width) {
        return Math.max(width, Minecraft.getInstance().getWindow().getWidth() / 1920.0f * width);
    }

    /** Camera distance of the pose's origin. World poses are camera-relative, so this is length. */
    public static double cameraDistance(PoseStack poseStack) {
        return poseStack.last().pose().transformPosition(new Vector3f()).length();
    }

    /** Adds the line width to each vertex as it is started. */
    private record WidthedLines(VertexConsumer delegate, float width) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z).setLineWidth(width);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setColor(int argb) { delegate.setColor(argb); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float lineWidth) { delegate.setLineWidth(lineWidth); return this; }
    }

    private QuantumRenderTypes() {
    }
}
