package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.RiftLensBlock;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The Rift Lens as a block: how it holds on to what's beside it and turns. See wiki: blocks/rift_lens. */
public final class RiftLensTests {

    private RiftLensTests() {}

    // covers: rift_lens.struts
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_lens", timeoutTicks = 20)
    public static void theLensHoldsOnToWhatsBesideIt(GameTestHelper helper) {
        BlockPos low = new BlockPos(8, 2, 4);
        BlockPos high = low.above();
        BlockState lens = ModBlocks.RIFT_LENS.get().defaultBlockState();
        helper.setBlock(low, RiftLensBlock.turnedTo(lens, Direction.Axis.X, helper.getLevel(), helper.absolutePos(low)));
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.DOWN), true);
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.NORTH), false);
        // Stacked the same way, they hold each other.
        helper.setBlock(high, RiftLensBlock.turnedTo(lens, Direction.Axis.X, helper.getLevel(), helper.absolutePos(high)));
        helper.assertBlockProperty(high, RiftLensBlock.STRUTS.get(Direction.DOWN), true);
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.UP), true);
        // Never a strut along the beam, even to something solid.
        helper.setBlock(low.east(), Blocks.STONE);
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.EAST), false);
        helper.succeed();
    }

    // covers: rift_lens.wrench
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_lens", timeoutTicks = 20)
    public static void aWrenchTurnsTheLens(GameTestHelper helper) {
        BlockPos lens = new BlockPos(10, 2, 4);
        helper.setBlock(lens, RiftLensBlock.turnedTo(ModBlocks.RIFT_LENS.get().defaultBlockState(), Direction.Axis.X,
                helper.getLevel(), helper.absolutePos(lens)));
        RiftLensBlock.turn(helper.getLevel(), helper.absolutePos(lens), helper.getBlockState(lens));
        helper.assertBlockProperty(lens, RiftLensBlock.AXIS, Direction.Axis.Y);
        // Lying flat, it has nothing below to hold on to any more.
        helper.assertBlockProperty(lens, RiftLensBlock.STRUTS.get(Direction.DOWN), false);
        RiftLensBlock.turn(helper.getLevel(), helper.absolutePos(lens), helper.getBlockState(lens));
        helper.assertBlockProperty(lens, RiftLensBlock.AXIS, Direction.Axis.Z);
        helper.assertBlockProperty(lens, RiftLensBlock.STRUTS.get(Direction.DOWN), true);
        helper.succeed();
    }
}
