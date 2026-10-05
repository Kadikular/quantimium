package com.kadikular.quantimium.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * One controller's answer to "may I use this block?", resolved once per structure check.
 *
 * <p>Built with a single scan for rival controllers, because the alternative — asking per part —
 * multiplies the scan by every block in the layout. With no rivals nearby, which is the normal
 * case, every question short-circuits to yes.
 */
public record PartClaim(List<MultiblockController> rivals, BlockPos self) {

    public static PartClaim of(Level level, BlockPos self) {
        return new PartClaim(MultiblockParts.rivals(level, self), self);
    }

    /** A claim that concedes nothing, for callers that only need geometry. */
    public static PartClaim unchallenged(BlockPos self) {
        return new PartClaim(List.of(), self);
    }

    public boolean mine(BlockPos part) {
        return rivals.isEmpty() || !MultiblockParts.claimedByRival(rivals, self, part);
    }
}
