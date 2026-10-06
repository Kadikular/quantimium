package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

import java.util.List;

/**
 * The Reactor's horizon, hanging over its core: a black sphere, an accretion disk round it, a
 * photon ring facing whoever looks, the gimbal rings that hold it, and the catalysts orbiting as
 * moons.
 *
 * <ul>
 *   <li><b>Mass makes it bigger.</b> The horizon's radius grows with the log of what it holds, so it
 *   never swallows the base: about 0.6 blocks at a thousand items, 1.6 at a million, 2 at sixteen.</li>
 *   <li><b>Power makes it bright.</b> Running, the disk blazes and spins; unpowered it's dim and slow.</li>
 *   <li><b>Rings are its tier,</b> one per Ring Emitter pair, each turning in its own plane.</li>
 *   <li><b>A moon flares</b> while a craft that used its catalyst is fresh, in the order they ran.</li>
 * </ul>
 *
 * <p>No ray tracing: the bent light over the top is faked by the camera-facing glow round the shadow.
 */
public class HorizonCoreBER extends SubmittingBER<HorizonCoreBlockEntity> {

    /** Where the horizon hangs, over the core block. */
    private static final float HEIGHT = 4.0f;
    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;
    private static final int DISK_SEGMENTS = 72;
    private static final int RING_SEGMENTS = 96;
    private static final float[] RING_RADII = {3.3f, 3.6f, 3.95f};
    private static final int FLASH_TICKS = 60;

    public HorizonCoreBER(BlockEntityRendererProvider.Context context) {}

    /** The Singularity in its cage: the horizon in miniature, turning between the posts. */
    private static void caged(PoseStack poses, MultiBufferSource buffers, float time, boolean active) {
        poses.pushPose();
        poses.translate(0.5, 0.56, 0.5);
        poses.mulPose(Axis.XP.rotationDegrees(-14.0f));
        poses.scale(0.1f, 0.1f, 0.1f);
        float r = 0.9f;
        sphere(poses, buffers.getBuffer(QuantumRenderTypes.BEAM_CORE), r);
        disk(poses, buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW), r, time, active ? 1.0f : 0.5f, active ? 1.0f : 0.3f);
        poses.mulPose(Axis.YP.rotationDegrees(time * 0.7f));
        annulus(buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW), poses.last(), r * 1.02f, r * 1.14f, 0xE8F2FF, 220, 220);
        poses.popPose();
    }

    /** Radius in blocks for {@code mass} items. */
    public static float radius(long mass) {
        if (mass < 1000) return 0.35f + 0.25f * (float) Math.max(0, Math.log10(Math.max(1, mass))) / 3.0f;
        return 0.6f + (float) (Math.log10(mass) - 3.0) / 3.0f;
    }

    /**
     * What each core's horizon is drawn at, eased towards what it should be: a Singularity seated
     * grows its horizon from a point, and mass taken in or out swells or shrinks it smoothly.
     */
    private static final java.util.Map<HorizonCoreBlockEntity, float[]> SHOWN = new java.util.WeakHashMap<>();
    /** Ticks for the eased radius to close most of the way to its target. */
    private static final float EASE_TICKS = 18.0f;

    @Override
    protected void render(HorizonCoreBlockEntity core, float partialTick, PoseStack poses, MultiBufferSource buffers,
                          int packedLight, int packedOverlay) {
        float time = (core.getLevel() == null ? 0 : core.getLevel().getGameTime()) + partialTick;
        if (core.getBlockState().hasProperty(com.kadikular.quantimium.block.HorizonCoreBlock.SEATED)
                && core.getBlockState().getValue(com.kadikular.quantimium.block.HorizonCoreBlock.SEATED)) {
            caged(poses, buffers, time, core.syncedActive());
        }
        float target = core.syncedRings() > 0 ? radius(core.syncedMass()) : 0.0f;
        float[] shown = SHOWN.computeIfAbsent(core, key -> new float[] {0.0f, time});
        float dt = Math.max(0.0f, Math.min(20.0f, time - shown[1]));
        shown[1] = time;
        shown[0] += (target - shown[0]) * (1.0f - (float) Math.exp(-dt / EASE_TICKS));
        if (shown[0] < 0.01f) return;
        float r = shown[0];
        // Rings and moons fade in with the horizon.
        float grown = target <= 0 ? 0.0f : Math.min(1.0f, r / target);
        boolean active = core.syncedActive();
        float bright = (active ? 1.0f : 0.3f) * grown;
        float spin = active ? 1.0f : 0.15f;

        poses.pushPose();
        poses.translate(0.5, HEIGHT, 0.5);
        // The black horizon goes first: it writes depth, so disk and glow behind it stay hidden.
        sphere(poses, buffers.getBuffer(QuantumRenderTypes.BEAM_CORE), r);
        disk(poses, buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW), r, time, bright, spin);
        photonRing(poses, buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW), r, bright);
        if (grown > 0.6f) {
            rings(poses, buffers, core.syncedRings(), time, spin);
            moons(core, poses, buffers, r, time, spin, packedOverlay);
        }
        poses.popPose();
    }

    static void sphere(PoseStack poses, VertexConsumer buffer, float r) {
        PoseStack.Pose pose = poses.last();
        for (int lat = 0; lat < SPHERE_LAT; lat++) {
            float t0 = Mth.PI * lat / SPHERE_LAT - Mth.HALF_PI;
            float t1 = Mth.PI * (lat + 1) / SPHERE_LAT - Mth.HALF_PI;
            for (int lon = 0; lon < SPHERE_LON; lon++) {
                float p0 = Mth.TWO_PI * lon / SPHERE_LON;
                float p1 = Mth.TWO_PI * (lon + 1) / SPHERE_LON;
                spherePoint(buffer, pose, r, t0, p0);
                spherePoint(buffer, pose, r, t0, p1);
                spherePoint(buffer, pose, r, t1, p1);
                spherePoint(buffer, pose, r, t1, p0);
            }
        }
    }

    private static void spherePoint(VertexConsumer buffer, PoseStack.Pose pose, float r, float lat, float lon) {
        float c = Mth.cos(lat);
        buffer.addVertex(pose, r * c * Mth.cos(lon), r * Mth.sin(lat), r * c * Mth.sin(lon)).setColor(0, 0, 0, 255);
    }

    /**
     * The accretion disk: hot and white at its inner edge, violet and faint at its outer, banded so
     * its turning shows, tilted a little so it reads from the side.
     */
    static void disk(PoseStack poses, VertexConsumer buffer, float r, float time, float bright, float spin) {
        poses.pushPose();
        poses.mulPose(Axis.XP.rotationDegrees(12.0f));
        PoseStack.Pose pose = poses.last();
        float inner = r * 1.5f;
        float outer = r * 1.5f + 1.2f + r * 0.9f;
        float[] radii = {inner, inner + (outer - inner) * 0.35f, outer};
        for (int band = 0; band < radii.length - 1; band++) {
            float a = radii[band];
            float b = radii[band + 1];
            for (int seg = 0; seg < DISK_SEGMENTS; seg++) {
                float s0 = Mth.TWO_PI * seg / DISK_SEGMENTS;
                float s1 = Mth.TWO_PI * (seg + 1) / DISK_SEGMENTS;
                diskPoint(buffer, pose, a, s0, band, time, bright, spin);
                diskPoint(buffer, pose, a, s1, band, time, bright, spin);
                diskPoint(buffer, pose, b, s1, band + 1, time, bright, spin);
                diskPoint(buffer, pose, b, s0, band + 1, time, bright, spin);
            }
        }
        poses.popPose();
    }

    private static void diskPoint(VertexConsumer buffer, PoseStack.Pose pose, float radius, float angle, int ring,
                                  float time, float bright, float spin) {
        // Inner material turns faster, so the bands shear as the disk spins.
        float swirl = 0.75f + 0.25f * Mth.sin(angle * 3.0f - time * 0.12f * spin * (3 - ring));
        float i = bright * swirl;
        int red, green, blue, alpha;
        switch (ring) {
            case 0 -> { red = 230; green = 240; blue = 255; alpha = (int) (220 * i); }
            case 1 -> { red = 52; green = 133; blue = 255; alpha = (int) (170 * i); }
            default -> { red = 138; green = 96; blue = 240; alpha = 0; }
        }
        buffer.addVertex(pose, radius * Mth.cos(angle), 0.0f, radius * Mth.sin(angle))
                .setColor((int) (red * i), (int) (green * i), (int) (blue * i), Mth.clamp(alpha, 0, 255));
    }

    /** The bright line hugging the shadow, and the glow of bent light round it, always facing the viewer. */
    private static void photonRing(PoseStack poses, VertexConsumer buffer, float r, float bright) {
        poses.pushPose();
        Quaternionf facing = new Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        poses.mulPose(facing);
        PoseStack.Pose pose = poses.last();
        annulus(buffer, pose, r * 1.02f, r * 1.12f, 0xE8F2FF, (int) (230 * bright), (int) (230 * bright));
        annulus(buffer, pose, r * 1.12f, r * 1.9f, 0x6A4CC8, (int) (120 * bright), 0);
        poses.popPose();
    }

    /** A flat ring in the pose's XY plane, its colour fading from {@code innerAlpha} to {@code outerAlpha}. */
    static void annulus(VertexConsumer buffer, PoseStack.Pose pose, float a, float b, int rgb,
                                int innerAlpha, int outerAlpha) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        for (int seg = 0; seg < DISK_SEGMENTS; seg++) {
            float s0 = Mth.TWO_PI * seg / DISK_SEGMENTS;
            float s1 = Mth.TWO_PI * (seg + 1) / DISK_SEGMENTS;
            buffer.addVertex(pose, a * Mth.cos(s0), a * Mth.sin(s0), 0).setColor(red, green, blue, innerAlpha);
            buffer.addVertex(pose, a * Mth.cos(s1), a * Mth.sin(s1), 0).setColor(red, green, blue, innerAlpha);
            buffer.addVertex(pose, b * Mth.cos(s1), b * Mth.sin(s1), 0).setColor(red, green, blue, outerAlpha);
            buffer.addVertex(pose, b * Mth.cos(s0), b * Mth.sin(s0), 0).setColor(red, green, blue, outerAlpha);
        }
    }

    /** The gimbal rings: one per emitter pair, each in its own plane, turning at its own speed. */
    private static void rings(PoseStack poses, MultiBufferSource buffers, int count, float time, float spin) {
        RenderType type = QuantumRenderTypes.holoEdge(QuantumRenderTypes.cameraDistance(poses));
        VertexConsumer lines = QuantumRenderTypes.buffer(buffers, type);
        for (int ring = 0; ring < Math.min(count, RING_RADII.length); ring++) {
            poses.pushPose();
            switch (ring) {
                case 0 -> poses.mulPose(Axis.YP.rotationDegrees(time * 1.4f * spin));
                case 1 -> {
                    poses.mulPose(Axis.YP.rotationDegrees(90.0f));
                    poses.mulPose(Axis.XP.rotationDegrees(90.0f));
                    poses.mulPose(Axis.ZP.rotationDegrees(-time * 1.0f * spin));
                }
                default -> {
                    poses.mulPose(Axis.ZP.rotationDegrees(80.0f));
                    poses.mulPose(Axis.YP.rotationDegrees(time * 0.7f * spin));
                }
            }
            circle(lines, poses.last(), RING_RADII[ring]);
            poses.popPose();
        }
    }

    private static void circle(VertexConsumer lines, PoseStack.Pose pose, float radius) {
        for (int seg = 0; seg < RING_SEGMENTS; seg++) {
            float a0 = Mth.TWO_PI * seg / RING_SEGMENTS;
            float a1 = Mth.TWO_PI * (seg + 1) / RING_SEGMENTS;
            float x0 = radius * Mth.cos(a0), z0 = radius * Mth.sin(a0);
            float x1 = radius * Mth.cos(a1), z1 = radius * Mth.sin(a1);
            float dx = x1 - x0, dz = z1 - z0;
            float length = Mth.sqrt(dx * dx + dz * dz);
            // Four beads on each ring, brighter, so its turning is easy to see.
            boolean bead = seg % (RING_SEGMENTS / 4) == 0;
            int alpha = bead ? 255 : 170;
            int red = bead ? 210 : 127, green = bead ? 186 : 178, blue = 255;
            lines.addVertex(pose, x0, 0, z0).setColor(red, green, blue, alpha).setNormal(pose, dx / length, 0, dz / length);
            lines.addVertex(pose, x1, 0, z1).setColor(red, green, blue, alpha).setNormal(pose, dx / length, 0, dz / length);
        }
    }

    /**
     * Each catalyst in the Catalyst Bays, in orbit just outside the disk at a third of its size. A moon whose
     * catalyst ran in the last craft flares, and threads light into the disk, in the order they ran.
     */
    private static void moons(HorizonCoreBlockEntity core, PoseStack poses, MultiBufferSource buffers, float r,
                              float time, float spin, int overlay) {
        List<BlockPos> bays = core.syncedBays();
        if (bays.isEmpty() || core.getLevel() == null) return;
        float orbit = r * 1.5f + 1.6f + r * 0.9f;
        long since = core.getLevel().getGameTime() - core.flashTime();
        int flaring = -1;
        if (since >= 0 && since < FLASH_TICKS && !core.flashBays().isEmpty()) {
            int step = (int) (since * core.flashBays().size() / FLASH_TICKS);
            flaring = core.flashBays().get(Math.min(step, core.flashBays().size() - 1));
        }
        // Every catalyst in every bay, numbered as the core numbers them: bay * SLOTS + slot.
        List<Integer> indices = new java.util.ArrayList<>();
        List<ItemStack> catalysts = new java.util.ArrayList<>();
        for (int b = 0; b < bays.size(); b++) {
            if (!(core.getLevel().getBlockEntity(bays.get(b)) instanceof CatalystBayBlockEntity bay)) continue;
            for (int slot = 0; slot < CatalystBayBlockEntity.SLOTS; slot++) {
                if (bay.getCatalyst(slot).isEmpty()) continue;
                indices.add(b * CatalystBayBlockEntity.SLOTS + slot);
                catalysts.add(bay.getCatalyst(slot));
            }
        }
        VertexConsumer threads = null;
        for (int i = 0; i < catalysts.size(); i++) {
            ItemStack catalyst = catalysts.get(i);
            float angle = Mth.TWO_PI * i / catalysts.size() + time * 0.006f * spin;
            float x = orbit * Mth.cos(angle);
            float z = orbit * Mth.sin(angle);
            float bob = 0.15f * Mth.sin(time * 0.05f + i);
            poses.pushPose();
            poses.translate(x, bob, z);
            boolean flare = indices.get(i) == flaring;
            // Each moon is held in a Tesseract, as a docked one is: the catalyst turning inside its shell.
            poses.pushPose();
            float shell = flare ? 0.63f : 0.54f;
            poses.scale(shell, shell, shell);
            poses.mulPose(Axis.YP.rotationDegrees(-time * 0.6f + i * 40.0f));
            poses.mulPose(Axis.XP.rotationDegrees(12.0f));
            TesseractShell.render(poses, buffers, time + i * 17.0f, QuantumRenderTypes.holoEdge(QuantumRenderTypes.cameraDistance(poses)),
                    flare ? TesseractShell.Tint.PALE : TesseractShell.Tint.MOON);
            poses.popPose();
            float scale = flare ? 0.30f : 0.25f;
            poses.scale(scale, scale, scale);
            poses.mulPose(Axis.YP.rotationDegrees(time * 2.0f));
            SubmitBuffers.item(buffers, poses, catalyst, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT,
                    overlay, core.getLevel(), i);
            poses.popPose();
            if (flare) {
                if (threads == null) threads = buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW);
                thread(threads, poses.last(), x, bob, z, r * 1.5f);
            }
        }
    }

    /** A thin band of light from a working moon to the disk's inner edge. */
    private static void thread(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float inner) {
        float length = Mth.sqrt(x * x + z * z);
        float tx = x / length * inner, tz = z / length * inner;
        float w = 0.05f;
        buffer.addVertex(pose, x, y - w, z).setColor(210, 186, 252, 200);
        buffer.addVertex(pose, x, y + w, z).setColor(210, 186, 252, 200);
        buffer.addVertex(pose, tx, w, tz).setColor(127, 178, 255, 0);
        buffer.addVertex(pose, tx, -w, tz).setColor(127, 178, 255, 0);
    }

    @Override
    public AABB getRenderBoundingBox(HorizonCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(7.0).expandTowards(0.0, HEIGHT + 4.0, 0.0);
    }
}
