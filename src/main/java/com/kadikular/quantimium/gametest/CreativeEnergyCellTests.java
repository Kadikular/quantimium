package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.entity.RiftStabiliserBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** The creative testing block that stands in for a partner mod's creative battery. */
public final class CreativeEnergyCellTests {
    private CreativeEnergyCellTests() {}

    @GameTest(template = TestSupport.FLOOR_9, batch = "creative_energy_cell", timeoutTicks = 40)
    public static void fillsTheMachineBesideIt(GameTestHelper helper) {
        // A stabiliser with no anchor draws nothing itself, so all it holds came from the cell.
        BlockPos machinePos = new BlockPos(4, 2, 4);
        helper.setBlock(machinePos, ModBlocks.RIFT_STABILISER.get());
        RiftStabiliserBlockEntity machine = helper.getBlockEntity(machinePos, RiftStabiliserBlockEntity.class);
        helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), 0, "FE before the cell");
        helper.setBlock(machinePos.east(), ModBlocks.CREATIVE_ENERGY_CELL.get());
        // It takes as much as it will each tick; the cell never runs short, so it only climbs.
        helper.runAfterDelay(10, () -> {
            int early = machine.getEnergyStorage().getEnergyStored();
            helper.assertTrue(early > 0, "the cell should have fed the machine by now");
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(machine.getEnergyStorage().getEnergyStored() > early,
                        "the machine should still be filling, at " + early + " FE");
                helper.succeed();
            });
        });
    }
}
