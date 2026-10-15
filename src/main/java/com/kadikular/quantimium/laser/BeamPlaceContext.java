package com.kadikular.quantimium.laser;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Placing with no player, looking the way the beam goes: a block that faces whoever placed it faces back up the beam,
 * and one that asks where the player looks gets an answer instead of a crash.
 */
final class BeamPlaceContext extends BlockPlaceContext {
    private final Direction looking;

    BeamPlaceContext(Level level, ItemStack stack, BlockHitResult at) {
        super(level, null, InteractionHand.MAIN_HAND, stack, at);
        this.looking = at.getDirection().getOpposite();
    }

    @Override
    public Direction getNearestLookingDirection() {
        return looking;
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        return looking.getAxis() == Direction.Axis.Y ? looking : Direction.DOWN;
    }

    @Override
    public Direction[] getNearestLookingDirections() {
        Direction[] order = new Direction[6];
        order[0] = looking;
        int i = 1;
        for (Direction d : Direction.values()) if (d != looking && d != looking.getOpposite()) order[i++] = d;
        order[5] = looking.getOpposite();
        return order;
    }

    @Override
    public Direction getHorizontalDirection() {
        return looking.getAxis().isHorizontal() ? looking : Direction.NORTH;
    }

    @Override
    public float getRotation() {
        return getHorizontalDirection().toYRot();
    }
}
