// Path: src/main/java/com/kadikular/quantimium/client/renderer/TesseractShell.java
package com.kadikular.quantimium.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Draws the containment field as a four-dimensional cube projected into the world. The projection is
 * what gives the shell its life: as the cell turns through the fourth axis its corners slide nearer
 * to and further from the 4D viewpoint, so the visible shape swells, folds and turns itself inside
 * out rather than merely spinning.
 */
public final class TesseractShell {

    private static final int AXES = 4;
    private static final int CORNERS = 16;
    private static final int EDGE_COUNT = 32;
    private static final int PANE_COUNT = 24;

    /** Corner coordinates of a unit 4-cube, four per corner. */
    private static final float[] CELL = buildCell();
    /** Corner index pairs, two per edge. */
    private static final int[] EDGES = buildEdges();
    /** Corner indices in winding order, four per square face. */
    private static final int[] PANES = buildPanes();

    /** Distance of the 4D viewpoint from the cell's centre. Smaller values exaggerate the folding. */
    private static final float W_EYE = 2.6f;
    /** Half-width of the widest projection, in blocks. Kept clear of the simulator below when tilted. */
    private static final float SPAN = 0.32f;

    /** Stride of the projected corner buffer: three coordinates plus a brightness key. */
    private static final int STRIDE = 4;

    /** Cyan for a live link; red when the bound target cannot be reached; pale for overlay souls. */
    public record Tint(float r, float g, float b) {
        /** Flux azure, #3485ff: the one colour for coherent Quantimium light. */
        public static final Tint FLUX = new Tint(0.204f, 0.522f, 1.00f);
        public static final Tint RED = new Tint(1.00f, 0.22f, 0.18f);
        public static final Tint PALE = new Tint(0.90f, 0.88f, 0.96f);
        /** A Folded Tesseract: the violet of the Fold Chamber's rails. */
        public static final Tint FOLDED = new Tint(0.69f, 0.50f, 0.98f);
        /** A Reactor's moons: the flux azure, washed most of the way to white. */
        public static final Tint MOON = new Tint(0.80f, 0.88f, 1.00f);
    }

    private TesseractShell() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float time) {
        render(poseStack, bufferSource, time, Tint.FLUX);
    }

    /** World-space draw that thins its own edges with distance. */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float time, Tint tint) {
        render(poseStack, bufferSource, time, edgeTypeFor(poseStack), tint);
    }

    /**
     * The wireframe width for geometry at the pose's current origin. World poses are camera-relative,
     * so that origin's distance from zero is its distance from the eye.
     */
    public static RenderType edgeTypeFor(PoseStack poseStack) {
        return QuantumRenderTypes.holoEdge(QuantumRenderTypes.cameraDistance(poseStack));
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float time, RenderType edgeType) {
        render(poseStack, bufferSource, time, edgeType, Tint.FLUX);
    }

    /**
     * Draws the shell with a caller-chosen wireframe and tint, so inventory shells can use thinner
     * edges and an unreachable link can show red instead of cyan.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float time,
                              RenderType edgeType, Tint tint) {
        render(poseStack, bufferSource, time, edgeType, tint, 1.0f);
    }

    /**
     * {@code alphaScale} lifts pane and edge opacity without changing colour. Overlay souls sit in
     * daylight, so they need a stronger pass than a stabilizer in a dark recess.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float time,
                              RenderType edgeType, Tint tint, float alphaScale) {
        float[] corners = project(time);
        float phase = time * 0.11f;
        // Both passes write depth, so the wireframe goes down first: an edge only ever costs a pane the
        // thin line of pixels beneath it, whereas a pane laid down first would swallow the edges behind it.
        drawEdges(poseStack, bufferSource, corners, phase, edgeType, tint, alphaScale);
        drawPanes(poseStack, bufferSource, corners, phase, tint, alphaScale);
    }

    /**
     * The whole cell as a bare wireframe. A semi-stable pin folds like any other tesseract; it is the
     * missing panes, not missing edges, that mark it as the lesser one.
     */
    public static void renderSemiStable(PoseStack poseStack, MultiBufferSource bufferSource, float time,
                                        RenderType edgeType, Tint tint) {
        float[] corners = project(time);
        drawEdges(poseStack, bufferSource, corners, time * 0.11f, edgeType, tint, 1.0f);
    }

    private static float[] project(float time) {
        float sinXw = Mth.sin(time * 0.021f), cosXw = Mth.cos(time * 0.021f);
        float sinYz = Mth.sin(time * 0.013f), cosYz = Mth.cos(time * 0.013f);
        float sinZw = Mth.sin(time * 0.008f), cosZw = Mth.cos(time * 0.008f);
        float breathe = 1.0f + Mth.sin(time * 0.05f) * 0.035f;

        float[] corners = new float[CORNERS * STRIDE];
        float centreX = 0.0f, centreY = 0.0f, centreZ = 0.0f;
        for (int corner = 0; corner < CORNERS; corner++) {
            float x = CELL[corner * AXES];
            float y = CELL[corner * AXES + 1];
            float z = CELL[corner * AXES + 2];
            float w = CELL[corner * AXES + 3];

            float turnedX = x * cosXw - w * sinXw;
            w = x * sinXw + w * cosXw;
            x = turnedX;

            float turnedY = y * cosYz - z * sinYz;
            z = y * sinYz + z * cosYz;
            y = turnedY;

            float turnedZ = z * cosZw - w * sinZw;
            w = z * sinZw + w * cosZw;
            z = turnedZ;

            float reach = breathe * SPAN * (W_EYE - 1.0f) / (W_EYE - w);
            corners[corner * STRIDE] = x * reach;
            corners[corner * STRIDE + 1] = y * reach;
            corners[corner * STRIDE + 2] = z * reach;
            corners[corner * STRIDE + 3] = Mth.clamp((w + 1.0f) * 0.5f, 0.0f, 1.0f);

            centreX += corners[corner * STRIDE];
            centreY += corners[corner * STRIDE + 1];
            centreZ += corners[corner * STRIDE + 2];
        }

        // A corner's reach depends on how near it sits to the 4D viewpoint, so opposite corners are
        // pushed out by different amounts and the projection is not balanced about the origin. Left
        // alone the shell would hang off to one side and wander as the cell turns, so it is pulled
        // back onto its own centre.
        centreX /= CORNERS;
        centreY /= CORNERS;
        centreZ /= CORNERS;
        for (int corner = 0; corner < CORNERS; corner++) {
            corners[corner * STRIDE] -= centreX;
            corners[corner * STRIDE + 1] -= centreY;
            corners[corner * STRIDE + 2] -= centreZ;
        }
        return corners;
    }

    private static void drawPanes(PoseStack poseStack, MultiBufferSource bufferSource, float[] corners,
                                  float phase, Tint tint, float alphaScale) {
        VertexConsumer buffer = bufferSource.getBuffer(QuantumRenderTypes.SHELL_PANE);
        PoseStack.Pose pose = poseStack.last();
        int[] order = sortFarToNear(pose, corners);

        for (int slot = 0; slot < PANE_COUNT; slot++) {
            int pane = order[slot] * 4;
            float depth = 0.0f;
            for (int step = 0; step < 4; step++) {
                depth += corners[PANES[pane + step] * STRIDE + 3];
            }
            depth *= 0.25f;

            float glow = flicker(depth, phase);
            float alpha = Math.min(1.0f, (0.030f + 0.070f * depth) * glow * alphaScale);
            for (int step = 0; step < 4; step++) {
                int at = PANES[pane + step] * STRIDE;
                buffer.addVertex(pose, corners[at], corners[at + 1], corners[at + 2])
                        .setColor(tint.r() * glow, tint.g() * glow, tint.b() * glow, alpha);
            }
        }
    }

    /**
     * Orders the panes furthest-first. Because each pane writes depth, drawing them in any other order
     * would let a near pane erase the ones behind it and hollow the shell out.
     */
    private static int[] sortFarToNear(PoseStack.Pose pose, float[] corners) {
        float[] distances = new float[PANE_COUNT];
        int[] order = new int[PANE_COUNT];
        Vector3f centre = new Vector3f();

        for (int pane = 0; pane < PANE_COUNT; pane++) {
            centre.set(0.0f, 0.0f, 0.0f);
            for (int step = 0; step < 4; step++) {
                int at = PANES[pane * 4 + step] * STRIDE;
                centre.add(corners[at], corners[at + 1], corners[at + 2]);
            }
            centre.mul(0.25f);

            // The pose maps into camera-relative space, so the transformed centre's own length is its
            // distance from the eye whichever way the camera happens to be facing.
            pose.pose().transformPosition(centre);
            distances[pane] = centre.lengthSquared();
            order[pane] = pane;
        }

        for (int placed = 1; placed < PANE_COUNT; placed++) {
            int pane = order[placed];
            float distance = distances[pane];
            int scan = placed - 1;
            while (scan >= 0 && distances[order[scan]] < distance) {
                order[scan + 1] = order[scan];
                scan--;
            }
            order[scan + 1] = pane;
        }
        return order;
    }

    private static void drawEdges(PoseStack poseStack, MultiBufferSource bufferSource, float[] corners,
                                  float phase, RenderType edgeType, Tint tint, float alphaScale) {
        VertexConsumer buffer = bufferSource.getBuffer(edgeType);
        PoseStack.Pose pose = poseStack.last();

        for (int edge = 0; edge < EDGES.length; edge += 2) {
            int from = EDGES[edge] * STRIDE;
            int to = EDGES[edge + 1] * STRIDE;

            // The line shader expands each segment around its own direction, so that has to be the normal.
            float dx = corners[to] - corners[from];
            float dy = corners[to + 1] - corners[from + 1];
            float dz = corners[to + 2] - corners[from + 2];
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-5f) continue;
            dx /= length;
            dy /= length;
            dz /= length;

            edgeVertex(buffer, pose, corners, from, dx, dy, dz, phase, tint, alphaScale);
            edgeVertex(buffer, pose, corners, to, dx, dy, dz, phase, tint, alphaScale);
        }
    }

    private static void edgeVertex(VertexConsumer buffer, PoseStack.Pose pose, float[] corners, int at,
                                  float dirX, float dirY, float dirZ, float phase, Tint tint,
                                  float alphaScale) {
        float depth = corners[at + 3];
        float glow = flicker(depth, phase);
        float shade = Mth.lerp(depth, 0.55f, 1.00f) * glow;
        buffer.addVertex(pose, corners[at], corners[at + 1], corners[at + 2])
                .setColor(tint.r() * shade, tint.g() * shade, tint.b() * shade,
                        Math.min(1.0f, (0.30f + 0.55f * depth) * glow * alphaScale))
                .setNormal(pose, dirX, dirY, dirZ);
    }

    /** A pulse that travels along the fourth axis, so the shell shimmers as it folds. */
    private static float flicker(float depth, float phase) {
        return 0.78f + 0.22f * Mth.sin(phase + depth * 6.0f);
    }

    private static float[] buildCell() {
        float[] cell = new float[CORNERS * AXES];
        for (int corner = 0; corner < CORNERS; corner++) {
            for (int axis = 0; axis < AXES; axis++) {
                cell[corner * AXES + axis] = ((corner >> axis) & 1) == 0 ? -1.0f : 1.0f;
            }
        }
        return cell;
    }

    private static int[] buildEdges() {
        int[] edges = new int[EDGE_COUNT * 2];
        int written = 0;
        for (int corner = 0; corner < CORNERS; corner++) {
            for (int axis = 0; axis < AXES; axis++) {
                int bit = 1 << axis;
                // Walk each edge from its low corner only, or every one would be emitted twice.
                if ((corner & bit) != 0) continue;
                edges[written++] = corner;
                edges[written++] = corner | bit;
            }
        }
        return edges;
    }

    private static int[] buildPanes() {
        int[] panes = new int[PANE_COUNT * 4];
        int written = 0;
        for (int first = 0; first < AXES; first++) {
            for (int second = first + 1; second < AXES; second++) {
                int firstBit = 1 << first;
                int secondBit = 1 << second;
                for (int corner = 0; corner < CORNERS; corner++) {
                    if ((corner & (firstBit | secondBit)) != 0) continue;
                    panes[written++] = corner;
                    panes[written++] = corner | firstBit;
                    panes[written++] = corner | firstBit | secondBit;
                    panes[written++] = corner | secondBit;
                }
            }
        }
        return panes;
    }
}
