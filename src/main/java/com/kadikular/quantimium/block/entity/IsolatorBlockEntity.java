package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An Isolator: a mirror that lets only power through. Light leaving it carries none of what it did
 * (actions, Pickup, UV), and nothing passes back up through it: Pickup's items stop at it, and later a
 * logistics network ends at it.
 */
public class IsolatorBlockEntity extends LaserRelayBlockEntity {

    public IsolatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ISOLATOR_BE.get(), pos, state);
    }

    @Override
    public boolean isolates() {
        return true;
    }
}
