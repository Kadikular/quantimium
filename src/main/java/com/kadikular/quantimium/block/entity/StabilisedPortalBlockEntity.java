package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Render-only holder so the gate interior can be an end-portal void plane. */
public final class StabilisedPortalBlockEntity extends BlockEntity {

    public StabilisedPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STABILISED_PORTAL_BE.get(), pos, state);
    }
}
