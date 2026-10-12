package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** A Lens Mount's light, and its lenses standing in their cradles, as themselves, square across the light. */
public class LensMountBER extends LaserBeamBER<LensMountBlockEntity> {

    /** A lens as drawn: ten sixteenths across, its middle on the beam. */
    private static final float SCALE = 0.625f;

    public LensMountBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(LensMountBlockEntity mount, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(mount, partialTick, poses, buffers, packedLight, packedOverlay);
        Direction facing = mount.getBlockState().getValue(LensMountBlock.FACING);
        Quaternionf square = square(facing);
        for (int slot = 0; slot < LensMountBlock.SLOT_ALONG.length; slot++) {
            ItemStack lens = mount.getLensSlots().getStackInSlot(slot);
            if (lens.isEmpty()) continue;
            Vec3 at = LensMountBlock.slotAt(slot, facing).scale(1 / 16.0);
            poses.pushPose();
            poses.translate(at.x, at.y, at.z);
            poses.mulPose(square);
            poses.scale(SCALE, SCALE, SCALE);
            SubmitBuffers.item(buffers, poses, lens, ItemDisplayContext.FIXED, packedLight, packedOverlay, mount.getLevel(), slot);
            poses.popPose();
        }
    }

    /** Turns an item (flat, facing south, upright) to face along the light: upright, or facing north when it shines up or down. */
    private static Quaternionf square(Direction facing) {
        Vec3 up = Vec3.atLowerCornerOf((facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP).getUnitVec3i());
        Vec3 forward = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 across = up.cross(forward);
        Matrix3f turn = new Matrix3f((float) across.x, (float) across.y, (float) across.z, (float) up.x, (float) up.y, (float) up.z,
                (float) forward.x, (float) forward.y, (float) forward.z);
        return new Quaternionf().setFromNormalized(turn);
    }
}
