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
    public static void aFilterLensListingAFluidMovesOnlyThatFluid(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.SEND,
                new FluidStack(Fluids.WATER, 4_000), new FluidStack(Fluids.LAVA, 4_000));
        ItemStack filter = new ItemStack(ModItems.FILTER_LENS.get());
        filter.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), com.kadikular.quantimium.laser.LensFilter.items(
                java.util.List.of(com.kadikular.quantimium.recipe.FluidEntry.of(new FluidStack(Fluids.LAVA, 1_000))), true));
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
                java.util.List.of(com.kadikular.quantimium.recipe.FluidEntry.of(new FluidStack(Fluids.WATER, 1_000))), true));
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

    // covers: laser.tank.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void aLaserTanksFilterSendsOtherFluidsOnUpTheLaser(GameTestHelper helper) {
        // The nearer tank catches only lava: the water goes on up to the one behind it.
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity far = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.COLLECT);
        LaserTankBlockEntity near = tank(helper, new BlockPos(5, Y, 8), LaserPortBlockEntity.Mode.COLLECT);
        near.setFilterSlot(0, new ItemStack(Items.LAVA_BUCKET));
        mount(helper, new BlockPos(7, Y, 8), lens(ModItems.EXTRACT_LENS.get()));
        BlockPos cauldron = new BlockPos(10, Y, 8);
        fullWaterCauldron(helper, cauldron);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(held(far, Fluids.WATER), 1_000, "water in the far tank");
            helper.assertValueEqual(held(near, Fluids.WATER), 0, "none in the lava tank");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pump
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anExtractLaserPumpsAWaterSourceOnItsWay(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.COLLECT);
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.EXTRACT_LENS.get()));
        // One source, walled in with glass the light passes, and stone beyond for it to end on.
        BlockPos water = new BlockPos(8, Y, 8);
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(water.relative(side), Blocks.GLASS);
        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(held(tank, Fluids.WATER), 1_000, "a bucket's worth");
            helper.assertBlockPresent(Blocks.AIR, water);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.tank.sides
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void aLaserTankFaceSetToPushFillsWhatsBesideIt(GameTestHelper helper) {
        LaserTankBlockEntity from = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.COLLECT, new FluidStack(Fluids.WATER, 6_000));
        LaserTankBlockEntity into = tank(helper, new BlockPos(3, Y, 7), LaserPortBlockEntity.Mode.SEND);
        java.util.List<com.kadikular.quantimium.block.entity.simulation.SideConfig> sides = new java.util.ArrayList<>(from.getSideConfigs());
        int north = Direction.NORTH.get3DDataValue();
        var config = sides.get(north);
        sides.set(north, new com.kadikular.quantimium.block.entity.simulation.SideConfig(config.side(), config.itemMode(), config.itemInputMask(),
                config.itemOutputMask(), false, false, config.fluidMode(), config.fluidInputMask(), config.fluidOutputMask(), false, true));
        from.applySideConfigs(sides);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(held(into, Fluids.WATER), 6_000, "pushed north");
            helper.assertValueEqual(held(from, Fluids.WATER), 0, "none left");
        });
    }

    // covers: laser.lens.interact_fluid
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anInteractLaserPoursABucketFromALaserTank(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        LaserTankBlockEntity tank = tank(helper, new BlockPos(3, Y, 8), LaserPortBlockEntity.Mode.SEND, new FluidStack(Fluids.LAVA, 2_500));
        mount(helper, new BlockPos(5, Y, 8), lens(ModItems.INTERACT_LENS.get()));
        BlockPos cauldron = new BlockPos(8, Y, 8);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.LAVA_CAULDRON, cauldron);
            helper.assertValueEqual(held(tank, Fluids.LAVA), 1_500, "a bucket's worth poured");
            TestSupport.finishLasers(helper);
        });
    }

    // ---- on a logistics network ----

    private static com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity controller(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LogisticsControllerBlock.FACING, Direction.EAST)
                .setValue(com.kadikular.quantimium.block.LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false)
                .setValue(com.kadikular.quantimium.block.LogisticsControllerBlock.OUTPUTS.get(Direction.EAST), true));
        var controller = helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity.class);
        controller.getEnergyStorage().setEnergy(com.kadikular.quantimium.laser.LaserTuning.LOGISTICS_CAPACITY);
        return controller;
    }

    /** A Laser Tank under the light at {@code x}, holding {@code fluids}, and a receptacle on it set to {@code role}. */
    private static LaserTankBlockEntity onTank(GameTestHelper helper, int x, com.kadikular.quantimium.laser.LogisticsMember.Role role, boolean active,
                                               FluidStack... fluids) {
        LaserTankBlockEntity tank = tank(helper, new BlockPos(x, Y - 1, 8), LaserPortBlockEntity.Mode.COLLECT, fluids);
        BlockPos pos = new BlockPos(x, Y, 8);
        TestSupport.receptacle(helper, pos, Direction.DOWN);
        var receptacle = TestSupport.receptacleAt(helper, pos);
        while (receptacle.role() != role) receptacle.cycleRole();
        if (active) receptacle.toggleActive();
        return tank;
    }

    // covers: laser.logistics.fluids
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anActiveProviderPushesFluidIntoStorage(GameTestHelper helper) {
        var controller = controller(helper, new BlockPos(2, Y, 8));
        LaserTankBlockEntity from = onTank(helper, 4, com.kadikular.quantimium.laser.LogisticsMember.Role.PROVIDER, true, new FluidStack(Fluids.WATER, 3_000));
        LaserTankBlockEntity into = onTank(helper, 7, com.kadikular.quantimium.laser.LogisticsMember.Role.STORAGE, false);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(held(into, Fluids.WATER), 3_000, "water moved into storage");
            helper.assertValueEqual(held(from, Fluids.WATER), 0, "and out of the provider");
            var listed = com.kadikular.quantimium.laser.OfferTable.storedNow(controller, controller.getBlockPos(), controller.members());
            helper.assertValueEqual(listed.get(net.neoforged.neoforge.transfer.item.ItemResource.of(
                    com.kadikular.quantimium.recipe.FluidEntry.of(new FluidStack(Fluids.WATER, 1)))), 3_000L, "the network lists it in mB");
        });
    }

    // covers: laser.logistics.fluids
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 140)
    public static void aRequesterKeepingCountsPullsBucketsOfAFluid(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8));
        LaserTankBlockEntity stored = onTank(helper, 4, com.kadikular.quantimium.laser.LogisticsMember.Role.STORAGE, false,
                new FluidStack(Fluids.LAVA, 5_000), new FluidStack(Fluids.WATER, 5_000));
        LaserTankBlockEntity wants = onTank(helper, 7, com.kadikular.quantimium.laser.LogisticsMember.Role.REQUESTER, true);
        var requester = TestSupport.receptacleAt(helper, new BlockPos(7, Y, 8));
        requester.toggleKeep();
        requester.setFilterSlot(0, com.kadikular.quantimium.recipe.FluidEntry.of(new FluidStack(Fluids.LAVA, 1)).copyWithCount(2));
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(held(wants, Fluids.LAVA), 2_000, "two buckets kept");
            helper.assertValueEqual(held(wants, Fluids.WATER), 0, "nothing it doesn't list");
            helper.assertValueEqual(held(stored, Fluids.LAVA), 3_000, "the rest stays stored");
            helper.succeed();
        });
    }

    // covers: laser.logistics.fluids
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 100)
    public static void anExtractLaserOnALogisticsBeamStoresACauldronsWater(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        controller(helper, new BlockPos(3, Y, 8));
        LaserTankBlockEntity into = onTank(helper, 5, com.kadikular.quantimium.laser.LogisticsMember.Role.STORAGE, false);
        mount(helper, new BlockPos(7, Y, 8), lens(ModItems.EXTRACT_LENS.get()));
        BlockPos cauldron = new BlockPos(9, Y, 8);
        fullWaterCauldron(helper, cauldron);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.CAULDRON, cauldron);
            helper.assertValueEqual(held(into, Fluids.WATER), 1_000, "the cauldron's water stored");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.logistics.fluids
    @GameTest(template = TestSupport.FLOOR_17, batch = "fluids", timeoutTicks = 60)
    public static void anInsertLaserFilteringItemsLeavesTheNetworksFluidAlone(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8));
        controller(helper, new BlockPos(3, Y, 8));
        LaserTankBlockEntity stored = onTank(helper, 5, com.kadikular.quantimium.laser.LogisticsMember.Role.STORAGE, false, new FluidStack(Fluids.WATER, 3_000));
        ItemStack filter = new ItemStack(ModItems.FILTER_LENS.get());
        filter.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), com.kadikular.quantimium.laser.LensFilter.items(
                java.util.List.of(new ItemStack(Items.COBBLESTONE)), true));
        mount(helper, new BlockPos(7, Y, 8), lens(ModItems.INSERT_LENS.get()), filter);
        BlockPos cauldron = new BlockPos(9, Y, 8);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.CAULDRON, cauldron);
            helper.assertValueEqual(held(stored, Fluids.WATER), 3_000, "still stored");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }
}
