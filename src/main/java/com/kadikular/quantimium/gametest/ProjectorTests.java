package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.DecoherenceProjectorBlock;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.AnomaliteCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * The Decoherence Projector's light comes from Anomalite Cells, and it points out from whatever it's
 * mounted on. See wiki: blocks/decoherence_projector.
 */
public final class ProjectorTests {

    private ProjectorTests() {}

    // covers: projector.cells
    @GameTest(template = TestSupport.FLOOR_17, batch = "projector_cells", timeoutTicks = 120)
    public static void itBeamsOnlyWithACellAndBurnsIt(GameTestHelper helper) {
        BlockPos at = new BlockPos(8, 2, 8);
        TestSupport.track(helper);
        helper.setBlock(at, ModBlocks.DECOHERENCE_PROJECTOR.get());
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(at, DecoherenceProjectorBlockEntity.class);
        TestSupport.fill(projector.getEnergyStorage());
        MirrorEndermite mite = helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(8, 2, 13));
        // Come through into the real world, so it stays with nobody in the mirror to see it.
        mite.setBreached(true);
        mite.setNoAi(true);
        float full = mite.getHealth();
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(mite.getHealth(), full, "no Cell, no beam");
            projector.getCells().setStackInSlot(0, new ItemStack(ModItems.ANOMALITE_CELL.get(), 2));
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(mite.getHealth() < full || mite.isDeadOrDying(), "with a Cell it shoots the mite");
            helper.assertValueEqual(projector.getCells().getStackInSlot(0).getCount(), 1, "one Cell loaded");
            helper.assertTrue(projector.getCharge() < AnomaliteCellItem.CAPACITY, "and burning: " + projector.getCharge());
            if (!mite.isRemoved()) mite.discard();
            helper.succeed();
        });
    }

    // covers: projector.mounting
    @GameTest(template = TestSupport.FLOOR_17, batch = "projector_mounting", timeoutTicks = 60)
    public static void itOnlyLooksOutFromWhatItsMountedOn(GameTestHelper helper) {
        // Hung from a ceiling, pointing down: a mite above it is behind its mount, out of its sight.
        BlockPos at = new BlockPos(8, 5, 8);
        TestSupport.track(helper);
        helper.setBlock(at, ModBlocks.DECOHERENCE_PROJECTOR.get().defaultBlockState()
                .setValue(DecoherenceProjectorBlock.FACING, Direction.DOWN));
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(at, DecoherenceProjectorBlockEntity.class);
        TestSupport.fill(projector.getEnergyStorage());
        projector.getCells().setStackInSlot(0, new ItemStack(ModItems.ANOMALITE_CELL.get(), 2));
        helper.assertTrue(projector.orb().y < helper.absolutePos(at).getY(), "the orb hangs below it: " + projector.orb());
        MirrorEndermite above = helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(8, 8, 10));
        above.setBreached(true);
        above.setNoAi(true);
        above.setNoGravity(true);
        float full = above.getHealth();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!above.isRemoved(), "the mite should still be there to test against");
            helper.assertValueEqual(above.getHealth(), full, "a mite behind its mount is left alone");
            above.discard();
            helper.succeed();
        });
    }
}
