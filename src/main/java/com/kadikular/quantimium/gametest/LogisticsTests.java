package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Logistics: a controller's light joins receptacles into one network, and once a second its active providers push
 * and its active requesters pull, by filter and priority. Each test lays the light east along y = 2, receptacles on
 * chests just below it.
 */
public final class LogisticsTests {

    private static final int Y = 2;

    private LogisticsTests() {}

    private static LogisticsControllerBlockEntity controller(GameTestHelper helper, BlockPos pos, Direction facing, boolean powered) {
        helper.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState().setValue(LogisticsControllerBlock.FACING, facing)
                .setValue(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false).setValue(LogisticsControllerBlock.OUTPUTS.get(facing), true));
        LogisticsControllerBlockEntity controller = helper.getBlockEntity(pos, LogisticsControllerBlockEntity.class);
        if (powered) controller.getEnergyStorage().setEnergy(LaserTuning.LOGISTICS_CAPACITY);
        return controller;
    }

    /** A chest at {@code x} under the light, holding {@code stock}, and a receptacle on it set to {@code role}. */
    private static LogisticsReceptacleBlockEntity onChest(GameTestHelper helper, int x, LogisticsMember.Role role, boolean active, ItemStack... stock) {
        BlockPos chest = new BlockPos(x, Y - 1, 8);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity box = helper.getBlockEntity(chest, ChestBlockEntity.class);
        for (int i = 0; i < stock.length; i++) box.setItem(i, stock[i]);
        BlockPos pos = new BlockPos(x, Y, 8);
        helper.setBlock(pos, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        LogisticsReceptacleBlockEntity receptacle = helper.getBlockEntity(pos, LogisticsReceptacleBlockEntity.class);
        while (receptacle.role() != role) receptacle.cycleRole();
        if (active) receptacle.toggleActive();
        return receptacle;
    }

    /** An Emitter I at {@code x} shining east, a creative energy cell behind it. */
    private static void emitter(GameTestHelper helper, int x) {
        helper.setBlock(new BlockPos(x, Y, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(x - 1, Y, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
    }

    /** A Lens Mount at {@code x} shining east, holding {@code lenses}. */
    private static void mount(GameTestHelper helper, int x, Item... lenses) {
        BlockPos pos = new BlockPos(x, Y, 8);
        helper.setBlock(pos, ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LensMountBlock.FACING, Direction.EAST));
        var mount = helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.LensMountBlockEntity.class);
        for (int i = 0; i < lenses.length; i++) mount.getLensSlots().setStackInSlot(i, new ItemStack(lenses[i]));
    }

    /** A chest in the beam's path at {@code x}, on the light's line, holding {@code stock}. */
    private static ChestBlockEntity target(GameTestHelper helper, int x, ItemStack... stock) {
        BlockPos pos = new BlockPos(x, Y, 8);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity box = helper.getBlockEntity(pos, ChestBlockEntity.class);
        for (int i = 0; i < stock.length; i++) box.setItem(i, stock[i]);
        return box;
    }

    private static int count(ChestBlockEntity box, Item item) {
        int total = 0;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            if (box.getItem(slot).is(item)) total += box.getItem(slot).getCount();
        }
        return total;
    }

    private static int count(GameTestHelper helper, int x, Item item) {
        ChestBlockEntity box = helper.getBlockEntity(new BlockPos(x, Y - 1, 8), ChestBlockEntity.class);
        int total = 0;
        for (int slot = 0; slot < box.getContainerSize(); slot++) {
            if (box.getItem(slot).is(item)) total += box.getItem(slot).getCount();
        }
        return total;
    }

    // covers: laser.logistics.network, laser.logistics.provider
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void anActiveProviderPushesIntoStorage(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 32));
        LogisticsReceptacleBlockEntity storage = onChest(helper, 7, LogisticsMember.Role.STORAGE, false);
        helper.succeedWhen(() -> {
            helper.assertTrue(storage.isConnected(), "the storage is on the network");
            helper.assertValueEqual(count(helper, 7, Items.COBBLESTONE), 32, "cobblestone moved into storage");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 0, "and out of the provider");
        });
    }

    // covers: laser.logistics.provider
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aSpeedLensQuickensTheReceptaclesPastIt(GameTestHelper helper) {
        // Unquickened, a provider moves 16 a pass; past one Speed lens, 64: by the time two passes could have run, more
        // than two unquickened passes' worth has moved.
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, 3, com.kadikular.quantimium.init.ModItems.SPEED_LENS.get());
        onChest(helper, 5, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));
        onChest(helper, 8, LogisticsMember.Role.STORAGE, false);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(count(helper, 8, Items.COBBLESTONE) >= 64, "a quickened pass moved 64, not 16");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.requester
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 120)
    public static void aRequesterKeepingCountsTakesOnlyWhatItsShortOf(GameTestHelper helper) {
        // Keeping 10 cobblestone with 3 already there: it pulls 7 of storage's 64 and stops.
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 64));
        LogisticsReceptacleBlockEntity requester = onChest(helper, 7, LogisticsMember.Role.REQUESTER, true, new ItemStack(Items.COBBLESTONE, 3));
        requester.setFilterSlot(0, new ItemStack(Items.COBBLESTONE, 10));
        requester.toggleKeep();
        helper.runAfterDelay(90, () -> {
            helper.assertValueEqual(count(helper, 7, Items.COBBLESTONE), 10, "the requester holds just 10");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 57, "storage gave 7");
            helper.succeed();
        });
    }

    // covers: laser.logistics.priority
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void storageFillsHighestPriorityFirst(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.DIRT, 16));
        onChest(helper, 6, LogisticsMember.Role.STORAGE, false);
        onChest(helper, 8, LogisticsMember.Role.STORAGE, false).addPriority(5);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(helper, 8, Items.DIRT), 16, "all of it in the priority 5 storage");
            helper.assertValueEqual(count(helper, 6, Items.DIRT), 0, "none in the priority 0 one");
        });
    }

    // covers: laser.logistics.requester, laser.logistics.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void anActiveRequesterPullsWhatItLists(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.DIRT, 10));
        LogisticsReceptacleBlockEntity requester = onChest(helper, 7, LogisticsMember.Role.REQUESTER, true);
        requester.setFilterSlot(0, new ItemStack(Items.DIAMOND));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(helper, 7, Items.DIAMOND), 5, "the diamonds came");
            helper.assertValueEqual(count(helper, 7, Items.DIRT), 0, "the dirt didn't");
        });
    }

    // covers: laser.logistics.provider
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aRequesterTakesFromAPassiveProvider(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, false, new ItemStack(Items.IRON_INGOT, 12));
        onChest(helper, 7, LogisticsMember.Role.REQUESTER, true).setFilterSlot(0, new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> helper.assertValueEqual(count(helper, 7, Items.IRON_INGOT), 12, "the ingots came from the provider"));
    }

    // covers: laser.logistics.network
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void anIsolatorCutsTheNetwork(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 8));
        helper.setBlock(new BlockPos(6, Y, 8), ModBlocks.ISOLATOR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        LogisticsReceptacleBlockEntity storage = onChest(helper, 8, LogisticsMember.Role.STORAGE, false);
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(storage.isConnected(), "past the isolator, not on the network");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 8, "nothing moved");
            helper.succeed();
        });
    }

    // covers: laser.logistics.network
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void twoControllersOnOneNetworkStopBoth(GameTestHelper helper) {
        LogisticsControllerBlockEntity west = controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        controller(helper, new BlockPos(10, Y, 8), Direction.WEST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 8));
        onChest(helper, 7, LogisticsMember.Role.STORAGE, false);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 8, "nothing moved");
            helper.assertTrue(west.status().getString().equals(net.minecraft.network.chat.Component.translatable("message.quantimium.logistics_controller.clash").getString()),
                    "the controller says why");
            helper.assertBlockProperty(new BlockPos(2, Y, 8), com.kadikular.quantimium.block.LogisticsControllerBlock.LIT, false);
            helper.succeed();
        });
    }

    // covers: laser.logistics.controller
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void anUnpoweredControllerMovesNothing(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, false);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 8));
        onChest(helper, 7, LogisticsMember.Role.STORAGE, false);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 8, "nothing moved");
            helper.succeed();
        });
    }

    // covers: laser.logistics.port
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aLaserPortOffersWhatItCaught(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        BlockPos portAt = new BlockPos(4, Y, 8);
        helper.setBlock(portAt, ModBlocks.LASER_PORT.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserPortBlock.FACING, Direction.EAST));
        helper.getBlockEntity(portAt, com.kadikular.quantimium.block.entity.LaserPortBlockEntity.class).getItems()
                .setStackInSlot(0, new ItemStack(Items.WHEAT, 20));
        onChest(helper, 7, LogisticsMember.Role.REQUESTER, true).setFilterSlot(0, new ItemStack(Items.WHEAT));
        helper.succeedWhen(() -> helper.assertValueEqual(count(helper, 7, Items.WHEAT), 20, "the wheat came out of the port"));
    }

    // covers: laser.logistics.controller
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aControllerChargesFromLightAndPassesItOn(GameTestHelper helper) {
        emitter(helper, 1);
        LogisticsControllerBlockEntity controller = controller(helper, new BlockPos(3, Y, 8), Direction.EAST, false);
        helper.setBlock(new BlockPos(8, Y, 8), ModBlocks.FOLD_CORE.get());
        var sink = helper.getBlockEntity(new BlockPos(8, Y, 8), com.kadikular.quantimium.block.entity.FoldCoreBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(controller.getEnergyStorage().getEnergyStored() > 0, "the controller charged from the beam");
            helper.assertTrue(sink.getEnergyStorage().getEnergyStored() > 0, "and the light went on through it to the machine");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.logistics.controller
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void lightInByAControllersSideOnlyChargesIt(GameTestHelper helper) {
        // Facing east, its back to the west: an emitter shining into its north side charges it, and its light stops there.
        LogisticsControllerBlockEntity controller = controller(helper, new BlockPos(8, Y, 8), Direction.EAST, false);
        helper.setBlock(new BlockPos(8, Y, 4), ModBlocks.LASER_EMITTER_1.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserEmitterBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(8, Y, 3), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(12, Y, 8), ModBlocks.FOLD_CORE.get());
        var sink = helper.getBlockEntity(new BlockPos(12, Y, 8), com.kadikular.quantimium.block.entity.FoldCoreBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(controller.getEnergyStorage().getEnergyStored() > 0, "charged by light on its side");
            helper.assertValueEqual(sink.getEnergyStorage().getEnergyStored(), 0, "none of that light went on through");
            helper.assertFalse(controller.toggleOutput(Direction.WEST), "its back is never an output");
            helper.succeed();
        });
    }

    // covers: laser.logistics.controller
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aControllersOwnStoreChargesNoMachines(GameTestHelper helper) {
        LogisticsControllerBlockEntity controller = controller(helper, new BlockPos(3, Y, 8), Direction.EAST, true);
        helper.setBlock(new BlockPos(8, Y, 8), ModBlocks.FOLD_CORE.get());
        var sink = helper.getBlockEntity(new BlockPos(8, Y, 8), com.kadikular.quantimium.block.entity.FoldCoreBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(sink.getEnergyStorage().getEnergyStored(), 0, "the machine got nothing from the controller's store");
            helper.assertValueEqual(controller.getEnergyStorage().getEnergyStored(), LaserTuning.LOGISTICS_CAPACITY, "and it kept it");
            helper.succeed();
        });
    }

    // covers: laser.logistics.lenses
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 160)
    public static void anExtractLensPastTheControllerFillsTheNetwork(GameTestHelper helper) {
        emitter(helper, 1);
        controller(helper, new BlockPos(3, Y, 8), Direction.EAST, false);
        onChest(helper, 5, LogisticsMember.Role.STORAGE, false);
        mount(helper, 7, com.kadikular.quantimium.init.ModItems.EXTRACT_LENS.get());
        ChestBlockEntity from = target(helper, 9, new ItemStack(Items.DIRT, 10));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(from, Items.DIRT), 0, "taken from the chest the beam ends on");
            helper.assertValueEqual(count(helper, 5, Items.DIRT), 10, "into the network's storage");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.logistics.lenses
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 160)
    public static void aFilteredInsertLensPastTheControllerTakesFromTheNetwork(GameTestHelper helper) {
        emitter(helper, 1);
        controller(helper, new BlockPos(3, Y, 8), Direction.EAST, false);
        onChest(helper, 5, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.DIRT, 6));
        mount(helper, 7, com.kadikular.quantimium.init.ModItems.INSERT_LENS.get());
        filterSlot(helper, 7, 1, Items.DIAMOND);
        ChestBlockEntity into = target(helper, 9);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(into, Items.DIAMOND), 6, "the diamonds went into the chest the beam ends on");
            helper.assertValueEqual(count(helper, 5, Items.DIAMOND), 0, "out of the network's storage");
            helper.assertValueEqual(count(into, Items.DIRT), 0, "not the dirt");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.logistics.lenses
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void anUnfilteredInsertLensTakesNothingFromTheNetwork(GameTestHelper helper) {
        emitter(helper, 1);
        controller(helper, new BlockPos(3, Y, 8), Direction.EAST, false);
        onChest(helper, 5, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.DIAMOND, 6));
        mount(helper, 7, com.kadikular.quantimium.init.ModItems.INSERT_LENS.get());
        ChestBlockEntity into = target(helper, 9);
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(count(into, Items.DIAMOND), 0, "nothing inserted");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    /** Puts a Filter lens listing {@code item} in slot {@code slot} of the mount at {@code x}. */
    private static void filterSlot(GameTestHelper helper, int x, int slot, Item item) {
        ItemStack lens = new ItemStack(com.kadikular.quantimium.init.ModItems.FILTER_LENS.get());
        lens.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), com.kadikular.quantimium.laser.LensFilter.items(
                java.util.List.of(new ItemStack(item)), true));
        helper.getBlockEntity(new BlockPos(x, Y, 8), com.kadikular.quantimium.block.entity.LensMountBlockEntity.class).getLensSlots().setStackInSlot(slot, lens);
    }

    // covers: laser.logistics.controller
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aControllersStorePaysForNoLensWork(GameTestHelper helper) {
        // Cables only, no emitter: its receptacles could move, but nothing pays for the Insert lens.
        LogisticsControllerBlockEntity controller = controller(helper, new BlockPos(3, Y, 8), Direction.EAST, true);
        onChest(helper, 5, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.DIAMOND, 6));
        mount(helper, 7, com.kadikular.quantimium.init.ModItems.INSERT_LENS.get());
        ChestBlockEntity into = target(helper, 9);
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(count(into, Items.DIAMOND), 0, "nothing inserted");
            helper.assertValueEqual(controller.getEnergyStorage().getEnergyStored(), LaserTuning.LOGISTICS_CAPACITY, "the store untouched");
            helper.succeed();
        });
    }

    /** A Laser Port at {@code x} under the light, collecting, holding {@code stock}, and a receptacle on it set to {@code role}. */
    private static com.kadikular.quantimium.block.entity.LaserPortBlockEntity onPort(GameTestHelper helper, int x, LogisticsMember.Role role, boolean active, ItemStack... stock) {
        BlockPos portAt = new BlockPos(x, Y - 1, 8);
        helper.setBlock(portAt, ModBlocks.LASER_PORT.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserPortBlock.FACING, Direction.NORTH));
        var port = helper.getBlockEntity(portAt, com.kadikular.quantimium.block.entity.LaserPortBlockEntity.class);
        for (int i = 0; i < stock.length; i++) port.getItems().setStackInSlot(i, stock[i]);
        BlockPos pos = new BlockPos(x, Y, 8);
        helper.setBlock(pos, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        LogisticsReceptacleBlockEntity receptacle = helper.getBlockEntity(pos, LogisticsReceptacleBlockEntity.class);
        while (receptacle.role() != role) receptacle.cycleRole();
        if (active) receptacle.toggleActive();
        return port;
    }

    private static int count(com.kadikular.quantimium.block.entity.LaserPortBlockEntity port, Item item) {
        int total = 0;
        for (int slot = 0; slot < com.kadikular.quantimium.block.entity.LaserPortBlockEntity.SLOTS; slot++) {
            if (port.getItems().getStackInSlot(slot).is(item)) total += port.getItems().getStackInSlot(slot).getCount();
        }
        return total;
    }

    // covers: laser.logistics.port
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aReceptacleOnALaserPortProvidesFromIt(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        var port = onPort(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 16));
        onChest(helper, 7, LogisticsMember.Role.STORAGE, false);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(port, Items.COBBLESTONE), 0, "out of the port");
            helper.assertValueEqual(count(helper, 7, Items.COBBLESTONE), 16, "into storage");
        });
    }

    // covers: laser.logistics.port
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aReceptacleOnALaserPortStoresInIt(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, true, new ItemStack(Items.COBBLESTONE, 16));
        var port = onPort(helper, 7, LogisticsMember.Role.STORAGE, false);
        helper.succeedWhen(() -> helper.assertValueEqual(count(port, Items.COBBLESTONE), 16, "into the port"));
    }

    // covers: laser.logistics.terminal
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aTerminalShowsTakesAndStores(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 20));
        onChest(helper, 6, LogisticsMember.Role.PROVIDER, false, new ItemStack(Items.IRON_INGOT, 5));
        onChest(helper, 8, LogisticsMember.Role.REQUESTER, false, new ItemStack(Items.GOLD_INGOT, 7));
        BlockPos at = new BlockPos(10, Y, 8);
        helper.setBlock(at, ModBlocks.LOGISTICS_TERMINAL.get());
        var terminal = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            var contents = terminal.contents();
            helper.assertValueEqual(contents.get(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.COBBLESTONE)))[0], 20L, "storage's cobblestone shows");
            helper.assertValueEqual(contents.get(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.IRON_INGOT)))[0], 5L, "the provider's iron shows");
            helper.assertFalse(contents.containsKey(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.GOLD_INGOT))), "a requester's gold doesn't");
            ItemStack got = terminal.take(net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.COBBLESTONE)), 16);
            helper.assertValueEqual(got.getCount(), 16, "took 16 cobblestone");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 4, "out of storage");
            ItemStack left = terminal.insert(new ItemStack(Items.DIAMOND, 3));
            helper.assertTrue(left.isEmpty(), "the diamonds went in");
            helper.assertValueEqual(count(helper, 4, Items.DIAMOND), 3, "into storage");
            helper.succeed();
        });
    }

    // covers: laser.logistics.terminal
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aLecternIsATerminal(GameTestHelper helper) {
        // On a stand, the light coming in to its focus: it shows and takes as the block does.
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 20));
        BlockPos at = new BlockPos(7, Y, 8);
        helper.setBlock(at, ModBlocks.LOGISTICS_LECTERN.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LogisticsTerminalBlock.FACING, Direction.WEST));
        var lectern = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            var cobblestone = net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(lectern.contents().containsKey(cobblestone), "storage's cobblestone shows");
            helper.assertValueEqual(lectern.take(cobblestone, 5).getCount(), 5, "and can be taken");
            helper.succeed();
        });
    }

    // covers: laser.logistics.reactor
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_reactor", timeoutTicks = 120)
    public static void theReactorLogisticsPortIsStorage(GameTestHelper helper) {
        var core = ReactorTests.buildReactor(helper, 1);
        BlockPos portAt = ReactorTests.CORE.below().east(5);
        helper.setBlock(portAt, ModBlocks.REACTOR_LOGISTICS_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        // An active provider on a chest above the light, between the controller and the port.
        BlockPos chest = portAt.east(2).above();
        helper.setBlock(chest, Blocks.CHEST);
        helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 4));
        helper.setBlock(portAt.east(2), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.UP));
        LogisticsReceptacleBlockEntity provider = helper.getBlockEntity(portAt.east(2), LogisticsReceptacleBlockEntity.class);
        provider.cycleRole();
        provider.toggleActive();
        controller(helper, portAt.east(3), Direction.WEST, true);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(core.getLedger().count(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIAMOND)), 4L,
                    "the diamonds went into the horizon");
            TestSupport.clearField(helper);
        });
    }

    // covers: laser.logistics.reactor
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_reactor", timeoutTicks = 120)
    public static void lightEndingOnTheReactorLogisticsPortPowersTheReactor(GameTestHelper helper) {
        var core = ReactorTests.buildReactor(helper, 1);
        BlockPos portAt = ReactorTests.CORE.below().east(5);
        helper.setBlock(portAt, ModBlocks.REACTOR_LOGISTICS_PORT.get());
        core.revalidate(helper.getLevel());
        BlockPos emitterAt = portAt.east(3);
        helper.setBlock(emitterAt, ModBlocks.LASER_EMITTER_1.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserEmitterBlock.FACING, Direction.WEST));
        helper.setBlock(emitterAt.above(), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "the Reactor charged from the beam");
            TestSupport.finishLasers(helper);
            TestSupport.clearField(helper);
        });
    }

    /** A Reactor with a crafting table bay and a Reactor Logistics Port, a controller's light ending on it through a storage receptacle on a chest of {@code stock}. */
    private static com.kadikular.quantimium.block.entity.ReactorLogisticsPortBlockEntity reactorOnNetwork(GameTestHelper helper, ItemStack... stock) {
        var core = ReactorTests.buildReactor(helper, 1);
        ReactorTests.bay(helper, ReactorTests.CORE.below().north(2), Items.CRAFTING_TABLE);
        BlockPos portAt = ReactorTests.CORE.below().east(5);
        helper.setBlock(portAt, ModBlocks.REACTOR_LOGISTICS_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        BlockPos chest = portAt.east(2).above();
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity box = helper.getBlockEntity(chest, ChestBlockEntity.class);
        for (int i = 0; i < stock.length; i++) box.setItem(i, stock[i]);
        helper.setBlock(portAt.east(2), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.UP));
        controller(helper, portAt.east(3), Direction.WEST, true);
        return helper.getBlockEntity(portAt, com.kadikular.quantimium.block.entity.ReactorLogisticsPortBlockEntity.class);
    }

    private static net.neoforged.neoforge.transfer.item.ItemResource resource(Item item) {
        return net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(item));
    }

    // covers: laser.logistics.reactor
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_reactor", timeoutTicks = 100)
    public static void theReactorMakesWithWhatTheNetworkShares(GameTestHelper helper) {
        var port = reactorOnNetwork(helper, new ItemStack(Items.OAK_LOG, 2));
        var core = helper.getBlockEntity(ReactorTests.CORE, com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.class);
        helper.runAfterDelay(30, () -> {
            var made = core.request(resource(Items.OAK_PLANKS), 8);
            helper.assertTrue(made.planned(), "planks from the network's logs: " + made.problem());
            helper.assertValueEqual(core.getLedger().count(resource(Items.OAK_PLANKS)), 8L, "made and kept");
            helper.assertValueEqual(count(helper.getBlockEntity(ReactorTests.CORE.below().east(7).above(), ChestBlockEntity.class), Items.OAK_LOG), 0,
                    "the logs came out of the network's chest");
            port.toggleSharing();
            helper.assertTrue(port.network() == null, "not shared once set not to");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.reactor
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_reactor", timeoutTicks = 100)
    public static void theReactorOffersWhatItCanMakeAndMakesItWhenTaken(GameTestHelper helper) {
        var port = reactorOnNetwork(helper);
        var core = helper.getBlockEntity(ReactorTests.CORE, com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.class);
        core.getLedger().add(resource(Items.OAK_LOG), 1);
        core.getLedger().add(resource(Items.DIAMOND), 2);
        helper.runAfterDelay(30, () -> {
            core.recountNow();
            var listing = port.listing();
            helper.assertValueEqual(listing.get(resource(Items.DIAMOND))[0], 2L, "the diamonds held");
            helper.assertTrue(listing.containsKey(resource(Items.OAK_PLANKS)) && listing.get(resource(Items.OAK_PLANKS))[1] >= 4,
                    "planks can be made from the log");
            var inventory = port.inventory();
            ItemStack got = ItemStack.EMPTY;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (inventory.getStackInSlot(slot).is(Items.OAK_PLANKS)) got = inventory.extractItem(slot, 4, false);
            }
            helper.assertValueEqual(got.getCount(), 4, "four planks, made when taken");
            helper.assertValueEqual(core.getLedger().count(resource(Items.OAK_LOG)), 0L, "the log used");
            // Filtered to diamonds, it offers nothing else.
            port.setFilterSlot(0, new ItemStack(Items.DIAMOND));
            core.getLedger().add(resource(Items.STICK), 3);
            helper.assertFalse(port.listing().containsKey(resource(Items.STICK)), "the filter hides the sticks");
            helper.assertTrue(port.listing().containsKey(resource(Items.DIAMOND)), "and offers the diamonds");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.reactor
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_reactor", timeoutTicks = 100)
    public static void aReactorLogisticsPortSetNotToStoreTakesNothingIn(GameTestHelper helper) {
        var port = reactorOnNetwork(helper);
        port.toggleStoring();
        var core = helper.getBlockEntity(ReactorTests.CORE, com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.class);
        helper.runAfterDelay(30, () -> {
            ItemStack left = port.inventory().insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
            helper.assertValueEqual(left.getCount(), 3, "turned away");
            helper.assertValueEqual(core.getLedger().count(resource(Items.DIAMOND)), 0L, "none in the horizon");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aLogisticsMaterialiserOffersTheNetworkToPipes(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.DIAMOND, 5));
        BlockPos at = new BlockPos(8, Y, 8);
        helper.setBlock(at, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(30, () -> {
            var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, helper.absolutePos(at), null);
            helper.assertTrue(handler != null, "pipes see it");
            var diamond = resource(Items.DIAMOND);
            int slot = -1;
            for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).equals(diamond)) slot = i;
            helper.assertTrue(slot >= 0 && handler.getAmountAsLong(slot) == 5, "it offers the network's diamonds");
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                helper.assertValueEqual(handler.extract(slot, diamond, 3, transaction), 3, "promised");
            }
            helper.assertValueEqual(count(helper, 4, Items.DIAMOND), 5, "an aborted take moves nothing");
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                handler.extract(slot, diamond, 3, transaction);
                transaction.commit();
            }
            helper.assertValueEqual(count(helper, 4, Items.DIAMOND), 2, "a committed take moves them");
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                helper.assertValueEqual(handler.insert(resource(Items.DIRT), 7, transaction), 7, "room for dirt");
                transaction.commit();
            }
            helper.assertValueEqual(count(helper, 4, Items.DIRT), 7, "put away in storage");
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_crafter_loop", timeoutTicks = 200)
    public static void aCrafterFedByItsOwnNetworkMakesNothingFromNothing(GameTestHelper helper) {
        // A Crafter takes its ingredients from the network (a Tesseract bound to a Logistics Materialiser) and
        // offers what it can make back to it (a passive provider on it). Smooth stone taken out of the network
        // costs a cobblestone: cobblestone to stone to smooth stone, both crafts paid for.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 23));
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(6, Y - 1, 8), Items.FURNACE);
        TestSupport.fill(crafter.getEnergyStorage());
        helper.setBlock(new BlockPos(6, Y, 8), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        var provider = helper.getBlockEntity(new BlockPos(6, Y, 8), LogisticsReceptacleBlockEntity.class);
        while (provider.role() != LogisticsMember.Role.PROVIDER) provider.cycleRole();
        BlockPos at = new BlockPos(9, Y, 8);
        helper.setBlock(at, ModBlocks.LOGISTICS_MATERIALISER.get());
        ItemStack tesseract = new ItemStack(ModItems.TESSERACT.get());
        BlockPos bound = helper.absolutePos(at);
        tesseract.set(com.kadikular.quantimium.init.ModDataComponents.BOUND_POS.get(), net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), bound));
        tesseract.set(com.kadikular.quantimium.init.ModDataComponents.BOUND_SIDE.get(), Direction.UP);
        tesseract.set(com.kadikular.quantimium.init.ModDataComponents.BOUND_BLOCK.get(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ModBlocks.LOGISTICS_MATERIALISER.get()));
        crafter.getInventory().setStackInSlot(0, tesseract);
        helper.runAfterDelay(80, () -> {
            var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, bound, null);
            // Stone it can make from the network's cobblestone; smooth stone only if it can really make that too.
            for (Item item : java.util.List.of(Items.SMOOTH_STONE, Items.STONE)) {
                var wanted = resource(item);
                int slot = -1;
                for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).equals(wanted)) slot = i;
                if (slot < 0) {
                    helper.assertTrue(item != Items.STONE, "the network offers stone the Crafter can make");
                    continue;
                }
                int before = count(helper, 4, Items.COBBLESTONE);
                int took;
                try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    took = handler.extract(slot, wanted, 1, transaction);
                    transaction.commit();
                }
                int kept = 0;
                for (int i = 0; i < crafter.getInventory().getSlots(); i++) {
                    ItemStack stack = crafter.getInventory().getStackInSlot(i);
                    if (stack.is(Items.STONE) || stack.is(Items.SMOOTH_STONE)) kept += stack.getCount();
                }
                if (took > 0) helper.assertTrue(count(helper, 4, Items.COBBLESTONE) < before, item + " cost a cobblestone (" + kept + " kept in the Crafter)");
            }
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.receptacle
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aReceptacleRightBesideARelayJoinsTheNetwork(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        LogisticsReceptacleBlockEntity storage = onChest(helper, 6, LogisticsMember.Role.STORAGE, false);
        // And one shone on from above, onto its outer face: it joins, and the light stops there.
        helper.setBlock(new BlockPos(12, Y - 1, 12), Blocks.CHEST);
        helper.setBlock(new BlockPos(12, Y, 12), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        LogisticsReceptacleBlockEntity under = helper.getBlockEntity(new BlockPos(12, Y, 12), LogisticsReceptacleBlockEntity.class);
        controller(helper, new BlockPos(12, Y + 1, 12), Direction.DOWN, true);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(storage.isConnected(), "the relay's light reaches it");
            helper.assertTrue(under.isConnected(), "light onto its outer face joins it");
            helper.succeed();
        });
    }

    /**
     * A Crafter at {@code x} under the controller's light, making with {@code catalyst}: drawing on the network (a
     * Tesseract bound to the Logistics Materialiser at {@link #MATERIALISER}) if {@code draws}, offering what it can
     * make to the network (a passive provider on top) if {@code provides}.
     */
    private static QuantumCrafterBlockEntity crafterOn(GameTestHelper helper, int x, Item catalyst, boolean draws, boolean provides) {
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(x, Y - 1, 8), catalyst);
        TestSupport.fill(crafter.getEnergyStorage());
        if (draws) crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get()));
        if (provides) {
            helper.setBlock(new BlockPos(x, Y, 8), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
            var provider = helper.getBlockEntity(new BlockPos(x, Y, 8), LogisticsReceptacleBlockEntity.class);
            while (provider.role() != LogisticsMember.Role.PROVIDER) provider.cycleRole();
        }
        return crafter;
    }

    private static final BlockPos MATERIALISER = new BlockPos(12, Y, 8);

    /** The network through the Materialiser, as pipes and buses see it. */
    private static net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> network(GameTestHelper helper) {
        return helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, helper.absolutePos(MATERIALISER), Direction.UP);
    }

    /** Takes {@code amount} of {@code item} from the network, committing or not; how many came. */
    private static int take(GameTestHelper helper, Item item, int amount, boolean commit) {
        var handler = network(helper);
        var wanted = resource(item);
        for (int i = 0; i < handler.size(); i++) {
            if (!handler.getResource(i).equals(wanted)) continue;
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int got = handler.extract(i, wanted, amount, transaction);
                if (commit) transaction.commit();
                return got;
            }
        }
        return 0;
    }

    private static int countIn(QuantumCrafterBlockEntity crafter, Item item) {
        int total = 0;
        for (int i = 0; i < crafter.getInventory().getSlots(); i++) {
            ItemStack stack = crafter.getInventory().getStackInSlot(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_chain", timeoutTicks = 200)
    public static void aChainThroughTheNetworkPaysEachStep(GameTestHelper helper) {
        // Network -> Crafter A (a furnace, drawing on the network) -> Crafter B (a stonecutter, linked to A) -> back
        // to the network. Stone bricks taken from the network cost one cobblestone; a take called off costs nothing.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 23));
        QuantumCrafterBlockEntity furnace = crafterOn(helper, 6, Items.FURNACE, true, false);
        QuantumCrafterBlockEntity cutter = crafterOn(helper, 9, Items.STONECUTTER, false, true);
        cutter.getInventory().setStackInSlot(0, CrafterTests.link(helper, new BlockPos(6, Y - 1, 8), ModBlocks.QUANTUM_CRAFTER.get()));
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            int energy = furnace.getEnergyStorage().getEnergyStored();
            helper.assertValueEqual(take(helper, Items.STONE_BRICKS, 1, false), 1, "the network offers stone bricks, two steps away");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 23, "a take called off moves nothing");
            helper.assertValueEqual(furnace.getEnergyStorage().getEnergyStored(), energy, "and costs no power");
            helper.assertValueEqual(countIn(furnace, Items.STONE) + countIn(cutter, Items.STONE), 0, "and leaves nothing made");
            helper.assertValueEqual(take(helper, Items.STONE_BRICKS, 1, true), 1, "a stone brick taken");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 22, "for one cobblestone");
            helper.assertTrue(furnace.getEnergyStorage().getEnergyStored() < energy, "the furnace step paid");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_loop", timeoutTicks = 200)
    public static void twoCraftersFeedingEachOtherConserveEverything(GameTestHelper helper) {
        // Both draw on the network and offer to it: one way nine ingots make a block, the other a block makes nine
        // ingots. What's offered holds steady, and whatever's taken, the iron adds up.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.IRON_INGOT, 9));
        QuantumCrafterBlockEntity first = crafterOn(helper, 6, Items.CRAFTING_TABLE, true, true);
        QuantumCrafterBlockEntity second = crafterOn(helper, 9, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        java.util.List<String> seen = new java.util.ArrayList<>();
        java.util.function.Supplier<String> offered = () -> {
            var handler = network(helper);
            java.util.List<String> list = new java.util.ArrayList<>();
            for (int i = 0; i < handler.size(); i++) if (!handler.getResource(i).isEmpty()) list.add(handler.getResource(i) + " x" + handler.getAmountAsLong(i));
            return list.toString();
        };
        java.util.function.IntSupplier iron = () -> count(helper, 4, Items.IRON_INGOT) + 9 * count(helper, 4, Items.IRON_BLOCK)
                + countIn(first, Items.IRON_INGOT) + countIn(second, Items.IRON_INGOT)
                + 9 * (countIn(first, Items.IRON_BLOCK) + countIn(second, Items.IRON_BLOCK));
        helper.runAfterDelay(78, () -> seen.add(offered.get()));
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(offered.get(), seen.getFirst(), "what's offered holds steady");
            int blocks = take(helper, Items.IRON_BLOCK, 1, true);
            int ingots = take(helper, Items.IRON_INGOT, 9, true);
            helper.assertValueEqual(iron.getAsInt() + 9 * blocks + ingots, 9, "the iron adds up: " + blocks + " block, " + ingots + " ingots taken");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: pod.logistics
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 100)
    public static void aPodLogisticsModuleIsATerminal(GameTestHelper helper) {
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.EMERALD, 9));
        BlockPos at = new BlockPos(8, Y, 8);
        helper.setBlock(at, ModBlocks.POD_LOGISTICS_MODULE.get());
        var module = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.PodLogisticsModuleBlockEntity.class);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(module.controller() != null, "the controller's light reaches it");
            helper.assertValueEqual(module.contents().get(resource(Items.EMERALD))[0], 9L, "it lists the network's emeralds");
            helper.succeed();
        });
    }

    // covers: laser.logistics.controller
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 200)
    public static void blockingAndClearingAControllersOutputLeavesTheRestLit(GameTestHelper helper) {
        // An emitter into a Relay: east to a controller and on to a void, south straight to another void.
        emitter(helper, 1);
        BlockPos relayAt = new BlockPos(3, Y, 8);
        helper.setBlock(relayAt, ModBlocks.LASER_RELAY.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        helper.getBlockEntity(relayAt, com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class).addEmitter(Direction.SOUTH);
        controller(helper, new BlockPos(5, Y, 8), Direction.EAST, false);
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.CREATIVE_ENERGY_VOID.get());
        helper.setBlock(new BlockPos(3, Y, 12), ModBlocks.CREATIVE_ENERGY_VOID.get());
        var past = helper.getBlockEntity(new BlockPos(10, Y, 8), com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity.class);
        var beside = helper.getBlockEntity(new BlockPos(3, Y, 12), com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity.class);
        helper.runAfterDelay(40, () -> helper.setBlock(new BlockPos(7, Y, 8), Blocks.STONE));
        helper.runAfterDelay(80, () -> helper.setBlock(new BlockPos(7, Y, 8), Blocks.AIR));
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(past.taking() > 0, "light past the controller charges again once it's clear");
            helper.assertTrue(beside.taking() > 0, "and the Relay's other output still charges");
            var emitterBeams = helper.getBlockEntity(new BlockPos(1, Y, 8), com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity.class).syncedBeams();
            var relayBeams = helper.getBlockEntity(relayAt, com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class).syncedBeams();
            var controllerBeams = helper.getBlockEntity(new BlockPos(5, Y, 8), LogisticsControllerBlockEntity.class).syncedBeams();
            helper.assertTrue(emitterBeams.stream().allMatch(com.kadikular.quantimium.laser.LaserBeam::live), "the emitter's beam shows lit: " + emitterBeams);
            helper.assertTrue(relayBeams.stream().allMatch(com.kadikular.quantimium.laser.LaserBeam::live), "both of the Relay's show lit: " + relayBeams);
            helper.assertTrue(controllerBeams.stream().allMatch(com.kadikular.quantimium.laser.LaserBeam::live), "the controller's shows lit: " + controllerBeams);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.network.incremental
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics", timeoutTicks = 200)
    public static void aRelayPlacedInABeamAndBrokenAgainKeepsItRunning(GameTestHelper helper) {
        emitter(helper, 1);
        helper.setBlock(new BlockPos(12, Y, 8), ModBlocks.CREATIVE_ENERGY_VOID.get());
        var end = helper.getBlockEntity(new BlockPos(12, Y, 8), com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity.class);
        BlockPos relayAt = new BlockPos(6, Y, 8);
        helper.runAfterDelay(30, () -> helper.setBlock(relayAt, ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST)));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(end.taking() > 0, "light runs on through the Relay placed in it");
            helper.setBlock(relayAt, Blocks.AIR);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(end.taking() > 0, "and straight on again once it's broken");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }
}
