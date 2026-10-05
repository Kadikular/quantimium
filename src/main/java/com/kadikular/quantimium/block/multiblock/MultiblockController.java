package com.kadikular.quantimium.block.multiblock;

import net.minecraft.core.BlockPos;

/**
 * A block entity that owns a hand-built structure and lights its parts.
 *
 * <p>Exists so more than one multiblock can share the same part blocks. Without it, part placement
 * would only ever notify the Foundry, and two controllers reaching the same block would fight over
 * its {@code formed} state every revalidation.
 */
public interface MultiblockController {

    /** Position of the controller block itself. */
    BlockPos controllerPos();

    /** Re-check the structure and update part blockstates. Server side only. */
    void revalidateStructure();

    /**
     * True when a block at this position is part of this controller's layout, whether or not the
     * right block is actually there. Purely geometric: used both to decide who needs waking up
     * when a block changes, and to resolve ownership of a shared part.
     */
    boolean coversPart(BlockPos part);
}
