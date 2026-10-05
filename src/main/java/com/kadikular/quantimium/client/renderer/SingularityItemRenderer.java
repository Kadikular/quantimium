package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.item.SingularityItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A Singularity in hand or in a slot: the horizon in miniature. A black sphere, its disk turning
 * round it and the photon ring, all sized by how much it holds, as the Reactor's horizon is. In a
 * slot the ring is drawn flat-on rather than turned to the camera.
 */
public class SingularityItemRenderer implements SpecialModelRenderer<Long> {

    /** The disk's outer radius in the item's unit block: just inside its edge. */
    private static final float FIT = 0.48f;

    public record Unbaked(boolean gui) implements SpecialModelRenderer.Unbaked<Long> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("gui", false).forGetter(Unbaked::gui)).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<Long> bake(SpecialModelRenderer.BakingContext context) {
            return new SingularityItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public Long extractArgument(ItemStack stack) {
        return SingularityItem.mass(stack);
    }

    @Override
    public void submit(@Nullable Long mass, PoseStack poses, SubmitNodeCollector collector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        SubmitBuffers buffers = new SubmitBuffers(collector);
        float time = Util.getMillis() / 50.0f;
        // An empty one is a pearl; one that holds something swells like the horizon it would be.
        // Scaled so the disk's outer edge always fits the item; the more it holds, the more of that
        // the black sphere fills.
        float r = HorizonCoreBER.radius(mass == null ? 0 : mass);
        float scale = FIT / (r * 1.5f + 1.2f + r * 0.9f);
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.XP.rotationDegrees(-18.0f));
        poses.scale(scale, scale, scale);
        HorizonCoreBER.sphere(poses, buffers.getBuffer(QuantumRenderTypes.BEAM_CORE), r);
        HorizonCoreBER.disk(poses, buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW), r, time, 1.0f, 1.0f);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(time * 0.5f));
        HorizonCoreBER.annulus(buffers.getBuffer(QuantumRenderTypes.ADDITIVE_GLOW), poses.last(), r * 1.02f, r * 1.12f,
                0xE8F2FF, 220, 220);
        poses.popPose();
        poses.popPose();
        buffers.flush();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
        }
    }
}
