package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A laser block whose beams meet a point inside it rather than its face: a Rift Lens's tear, a Mirror's plate. */
public interface BeamAnchor {

    /** Where beams into and out of the block at {@code pos} meet it. */
    Vec3 beamPoint(BlockState state, BlockPos pos);
}
