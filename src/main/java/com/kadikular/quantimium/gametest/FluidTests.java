package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LaserTankBlock;
import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluids along the light: a Laser Tank sends what it holds down the light, to an Insert lens or another tank, and
 * catches what an Extract lens sends back up it. A Filter Lens lists fluids as filled containers; a Void lens destroys
 * them.
 */
public final class FluidTests {

    private static final int Y = 2;

    private FluidTests() {}

    private static void emitter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(pos.west(), ModBlocks.CREATIVE_ENERGY_CELL.get());
    }

    private static LaserTankBlockEntity tank(GameTestHelper helper, BlockPos pos, LaserPortBlockEntity.Mode mode, FluidStack... fluids) {
        helper.setBlock(pos, ModBlocks.LASER_TANK.get().defaultBlockState().setValue(LaserTankBlock.FACING, Direction.EAST));
        LaserTankBlockEntity tank = helper.getBlockEntity(pos, LaserTankBlockEntity.class);
        while (tank.mode() != mode) tank.cycleMode();
        for (int i = 0; i < fluids.length; i++) tank.tanks().set(i, fluids[i]);
        return tank;
    }

    private static LensMountBlockEntity mount(GameTestHelper helper, BlockPos pos, ItemStack... lenses) {
        helper.setBlock(pos, ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
        LensMountBlockEntity mount = helper.getBlockEntity(pos, LensMountBlockEntity.class);
        for (int i = 0; i < lenses.length; i++) mount.getLensSlots().setStackInSlot(i, lenses[i]);
        return mount;
    }

    private static ItemStack lens(Item item) {
        return new ItemStack(item);
    }

    private static int held(LaserTankBlockEntity tank, net.minecraft.world.level.material.Fluid fluid) {
        int total = 0;
        for (int i = 0; i < LaserTankBlockEntity.TANKS; i++) {
            if (tank.tanks().get(i).getFluid().isSame(fluid)) total += tank.tanks().get(i).getAmount();
        }
        return total;
    }

    private static void fullWaterCauldron(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    }

    // covers: laser.tank.send, laser.lens.insert_fluid
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anInsertLaserFillsACauldronFromALaserTank(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.SEND, new FluidStack(Fluids.WATER, 4_000));
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.INSERT_LENS.get()));
        BlockPos cauldron = new BlockPos(8, Y, 8);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER_CAULDRON, cauldron);
            helper.assertValueEqual(held(tank, Fluids.WATER), 3_000, "a bucket's worth left the tank");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.tank.collect, laser.lens.extract_fluid
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anExtractLaserEmptiesACauldronIntoALaserTank(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.COLLECT);
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.EXTRACT_LENS.get()));
        BlockPos cauldron = new BlockPos(8, Y, 8);
        fullWaterCauldron(helper, cauldron);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.CAULDRON, cauldron);
            helper.assertValueEqual(held(tank, Fluids.WATER), 1_000, "the cauldron's water in the tank");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.tank.send
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void aLaserTankSendsFluidToTheNextTankDownTheLaser(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity sender = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.SEND, new FluidStack(Fluids.LAVA, 6_000));
        LaserTankBlockEntity catcher = tank(helper, new BlockPos(6, Y, 8), LaserPortBlockEntity.Mode.COLLECT);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(held(catcher, Fluids.LAVA), 6_000, "all of it down the light");
            helper.assertValueEqual(held(sender, Fluids.LAVA), 0, "none left");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.filter_fluid
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void aFilterLensListingABucketMovesOnlyThatFluid(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.SEND,
                new FluidStack(Fluids.WATER, 4_000), new FluidStack(Fluids.LAVA, 4_000));
        ItemStack filter = new ItemStack(ModItems.FILTER_LENS.get());
        filter.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), com.kadikular.quantimium.laser.LensFilter.items(
                java.util.List.of(new ItemStack(Items.LAVA_BUCKET)), true));
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.INSERT_LENS.get()), filter);
        BlockPos cauldron = new BlockPos(8, Y, 8);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.LAVA_CAULDRON, cauldron);
            helper.assertValueEqual(held(tank, Fluids.WATER), 4_000, "the water stayed");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.filter_fluid
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void aFilterListingOnlyItemsLetsEveryFluidThrough(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.SEND, new FluidStack(Fluids.WATER, 2_000));
        ItemStack filter = new ItemStack(ModItems.FILTER_LENS.get());
        filter.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), com.kadikular.quantimium.laser.LensFilter.items(
                java.util.List.of(new ItemStack(Items.COBBLESTONE)), true));
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.INSERT_LENS.get()), filter);
        BlockPos cauldron = new BlockPos(8, Y, 8);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER_CAULDRON, cauldron);
            helper.assertValueEqual(held(tank, Fluids.WATER), 1_000, "a bucket went");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.void_fluid
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anExtractLaserWithVoidAllEmptiesACauldronWithNoTank(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        ItemStack voids = new ItemStack(ModItems.VOID_LENS.get());
        com.kadikular.quantimium.item.VoidLensItem.toggle(voids);
        voids.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), com.kadikular.quantimium.laser.LensFilter.items(
                java.util.List.of(new ItemStack(Items.WATER_BUCKET)), true));
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.EXTRACT_LENS.get()), voids);
        BlockPos cauldron = new BlockPos(8, Y, 8);
        fullWaterCauldron(helper, cauldron);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.CAULDRON, cauldron);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.tank.pipes
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 20)
    public static void pipesFillALaserTankOnlyWhileItSends(GameTestHelper helper) {
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.COLLECT, new FluidStack(Fluids.WATER, 1_000));
        var pipe = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                helper.absolutePos(new BlockPos(3, Y, 8)), Direction.UP);
        helper.assertTrue(pipe != null, "a fluid handler on its faces");
        var water = net.neoforged.neoforge.transfer.fluid.FluidResource.of(Fluids.WATER);
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            helper.assertValueEqual(pipe.insert(water, 500, transaction), 0, "collecting: pipes can't fill it");
            helper.assertValueEqual(pipe.extract(water, 500, transaction), 500, "but can drain it");
        }
        tank.cycleMode();
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            helper.assertValueEqual(pipe.insert(water, 500, transaction), 500, "sending: pipes fill it");
            transaction.commit();
        }
        helper.assertValueEqual(held(tank, Fluids.WATER), 1_500, "filled");
        helper.succeed();
    }
}
