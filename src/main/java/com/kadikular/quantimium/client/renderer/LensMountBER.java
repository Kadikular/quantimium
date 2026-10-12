package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** A Lens Mount's light, and its lenses standing in their cradles, as themselves, square across the light. */
public class LensMountBER extends LaserBeamBER<LensMountBlockEntity> {

    /** A lens as drawn: six sixteenths tall, its middle on the beam. */
    private static final float SCALE = 0.375f;

    public LensMountBER(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(LensMountBlockEntity mount, float partialTick, PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(mount, partialTick, poses, buffers, packedLight, packedOverlay);
        BlockState state = mount.getBlockState();
        if (!state.hasProperty(LensMountBlock.MOUNT)) return;
        Direction base = state.getValue(LensMountBlock.MOUNT);
        Direction facing = state.getValue(LensMountBlock.FACING);
        Quaternionf square = square(base, facing);
        for (int slot = 0; slot < LensMountBlock.SLOT_ALONG.length; slot++) {
            ItemStack lens = mount.getLensSlots().getStackInSlot(slot);
            if (lens.isEmpty()) continue;
            Vec3 at = LensMountBlock.turn(8, 8, LensMountBlock.SLOT_ALONG[slot], base, facing).scale(1 / 16.0);
            poses.pushPose();
            poses.translate(at.x, at.y, at.z);
            poses.mulPose(square);
            poses.scale(SCALE, SCALE, SCALE);
            SubmitBuffers.item(buffers, poses, lens, ItemDisplayContext.FIXED, packedLight, packedOverlay, mount.getLevel(), slot);
            poses.popPose();
        }
    }

    /** Turns an item (flat, facing south, upright) to face along the light, upright away from the base. */
    private static Quaternionf square(Direction base, Direction facing) {
        Vec3 up = Vec3.atLowerCornerOf(base.getOpposite().getUnitVec3i());
        Vec3 forward = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 across = up.cross(forward);
        Matrix3f turn = new Matrix3f((float) across.x, (float) across.y, (float) across.z, (float) up.x, (float) up.y, (float) up.z,
                (float) forward.x, (float) forward.y, (float) forward.z);
        return new Quaternionf().setFromNormalized(turn);
    }
}
