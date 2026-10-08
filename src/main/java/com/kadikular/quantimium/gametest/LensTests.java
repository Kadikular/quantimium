package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.CollectorBlock;
import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.CollectorBlockEntity;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Lasers, phase two: lenses in a Lens Mount give the light leaving it an action (harvest, damage) and
 * modifiers (pickup), which add up along mounts in a row. The beam still carries power; its actions
 * spend some of it, and only while it's lit. A Collector up the beam catches what Pickup brings back.
 */
public final class LensTests {

    private static final int Y = 2;

    private LensTests() {}

    private static LaserEmitterBlockEntity emitter(GameTestHelper helper, BlockPos pos, Direction facing, boolean powered) {
        helper.setBlock(pos, ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, facing));
        if (powered) helper.setBlock(pos.relative(facing.getOpposite()), ModBlocks.CREATIVE_ENERGY_CELL.get());
        return helper.getBlockEntity(pos, LaserEmitterBlockEntity.class);
    }

    private static LensMountBlockEntity mount(GameTestHelper helper, BlockPos pos, Direction facing, Item... lenses) {
        helper.setBlock(pos, ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, facing));
        LensMountBlockEntity mount = helper.getBlockEntity(pos, LensMountBlockEntity.class);
        for (int i = 0; i < lenses.length; i++) mount.getLensSlots().setStackInSlot(i, new ItemStack(lenses[i]));
        return mount;
    }

    private static CollectorBlockEntity collector(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.COLLECTOR.get().defaultBlockState().setValue(CollectorBlock.FACING, facing));
        return helper.getBlockEntity(pos, CollectorBlockEntity.class);
    }

    private static int count(CollectorBlockEntity collector, Item item) {
        int total = 0;
        for (int slot = 0; slot < CollectorBlockEntity.SLOTS; slot++) {
            ItemStack stack = collector.getItems().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    // covers: laser.lens.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aHarvestLensBreaksWhatTheBeamEndsOn(GameTestHelper helper) {
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        BlockPos stone = new BlockPos(9, Y, 8);
        helper.setBlock(stone, Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, stone);
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(stone)).inflate(2),
                    item -> item.getItem().is(Items.COBBLESTONE)).isEmpty(), "it dropped cobblestone");
            TestSupport.clearField(helper);
        });
    }

    // covers: laser.lens.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void aHarvestLensNeedsALitBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, false);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        BlockPos stone = new BlockPos(9, Y, 8);
        helper.setBlock(stone, Blocks.STONE);
        helper.runAfterDelay(50, () -> {
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.succeed();
        });
    }

    // covers: laser.lens.mount
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aLensedBeamStillCarriesPower(GameTestHelper helper) {
        // A Harvest lens doesn't break a machine, and the beam charges it as before.
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.DAMAGE_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), ModBlocks.FOLD_CORE.get());
        FoldCoreBlockEntity core = helper.getBlockEntity(new BlockPos(9, Y, 8), FoldCoreBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.FOLD_CORE.get(), new BlockPos(9, Y, 8));
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "charged through the lenses");
            TestSupport.clearField(helper);
        });
    }

    // covers: laser.lens.pickup, laser.collector
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupBringsWhatItBreaksBackToACollector(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 1, "the cobblestone came back up the beam");
            TestSupport.clearField(helper);
        });
    }

    // covers: laser.lens.mount, laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void mountsInARowAddTheirLensesUp(GameTestHelper helper) {
        // Pickup in the first mount, Harvest in the second: the light leaving the second does both.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 1, "harvested and brought back");
            TestSupport.clearField(helper);
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupAloneGathersItemsLyingInTheBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        ItemEntity item = new ItemEntity(helper.getLevel(), helper.absoluteVec(new BlockPos(10, Y, 8).getCenter()).x,
                helper.absolutePos(new BlockPos(10, Y, 8)).getY() + 0.5, helper.absoluteVec(new BlockPos(10, Y, 8).getCenter()).z,
                new ItemStack(Items.DIAMOND, 3));
        item.setDeltaMovement(0, 0, 0);
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.DIAMOND), 3, "the diamonds came back up the beam");
            TestSupport.clearField(helper);
        });
    }

    // covers: laser.lens.damage
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aDamageLensHurtsWhatStandsInTheBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(9, Y, 8));
        float full = pig.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < full, "the pig was hurt");
            TestSupport.clearField(helper);
        });
    }
}
