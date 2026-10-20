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
import java.util.List;
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
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_bus_probe", timeoutTicks = 100)
    public static void askingForEveryItemAndCallingItOffStaysQuick(GameTestHelper helper) {
        // What an AE2 storage bus does every few ticks: for each item the network offers, take one in a transaction
        // and call it off. With a Reactor on the network holding and able to make things, that stays quick.
        ItemStack[] chest = new ItemStack[27];
        Item[] kinds = {Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.COBBLESTONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE,
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.COAL, Items.FLINT, Items.CLAY_BALL, Items.BRICK,
                Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.STRING, Items.BONE, Items.FEATHER, Items.LEATHER, Items.PAPER,
                Items.SUGAR, Items.WHEAT, Items.EGG};
        for (int i = 0; i < 27; i++) chest[i] = new ItemStack(kinds[i], 16);
        reactorOnNetwork(helper, chest);
        var core = helper.getBlockEntity(ReactorTests.CORE, com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.class);
        for (Item item : List.of(Items.OAK_LOG, Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
                Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE, Items.STICK)) {
            core.getLedger().add(resource(item), 64);
        }
        var controller = helper.getBlockEntity(ReactorTests.CORE.below().east(8), LogisticsControllerBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            core.recountNow();
            List<LogisticsMember> members = controller.members();
            var entries = controller.offers().entries();
            helper.assertTrue(entries.size() > 40, "plenty on offer: " + entries.size());
            long start = System.nanoTime();
            int asked = 0;
            for (int round = 0; round < 2; round++) {
                for (var entry : entries) {
                    try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                        com.kadikular.quantimium.laser.Logistics.take(controller.spots(), entry.item(), 1, false, true, true);
                        asked++;
                    }
                }
            }
            double millis = (System.nanoTime() - start) / 1e6;
            com.kadikular.quantimium.Quantimium.LOGGER.info("Asking about every item: {} asks in {} ms", asked, millis);
            double realMs = 0, craftMs = 0; int reals = 0, crafts = 0;
            for (var entry : entries) {
                long t0 = System.nanoTime();
                try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    com.kadikular.quantimium.laser.Logistics.take(controller.spots(), entry.item(), 1, false, true, true);
                }
                double t = (System.nanoTime() - t0) / 1e6;
                if (entry.real() > 0) { realMs += t; reals++; } else { craftMs += t; crafts++; }
            }
            // Once the Reactor has planned each thing (the first round), asking again is quick.
            helper.assertTrue(realMs + craftMs < 10, "a round of asking took " + (realMs + craftMs) + " ms: "
                    + reals + " held in " + realMs + " ms, " + crafts + " to be made in " + craftMs + " ms");
            TestSupport.clearField(helper);
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
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_three", timeoutTicks = 200)
    public static void threeCraftersOnOneNetworkStayQuick(GameTestHelper helper) {
        // Two furnace Crafters and a crafting-table one, all drawing on the network and offering to it, 64 cobblestone
        // in storage: it settles, a tick of reading the network stays quick, and what's taken adds up.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 64));
        QuantumCrafterBlockEntity first = crafterOn(helper, 6, Items.FURNACE, true, true);
        QuantumCrafterBlockEntity second = crafterOn(helper, 8, Items.FURNACE, true, true);
        QuantumCrafterBlockEntity third = crafterOn(helper, 10, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(100, () -> {
            long start = System.nanoTime();
            var handler = network(helper);
            int kinds = 0;
            for (int i = 0; i < handler.size(); i++) if (!handler.getResource(i).isEmpty()) kinds++;
            long millis = (System.nanoTime() - start) / 1_000_000;
            helper.assertTrue(millis < 50, "reading the network took " + millis + " ms");
            helper.assertTrue(kinds > 1, "it offers more than the cobblestone");
            int took = take(helper, Items.SMOOTH_STONE, 4, true);
            int stone = countIn(first, Items.STONE) + countIn(second, Items.STONE) + countIn(third, Items.STONE)
                    + countIn(first, Items.SMOOTH_STONE) + countIn(second, Items.SMOOTH_STONE) + countIn(third, Items.SMOOTH_STONE);
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE) + stone + took, 64, "every cobblestone accounted for");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_steady", timeoutTicks = 200)
    public static void whatANetworkOffersHoldsSteady(GameTestHelper helper) {
        // Two furnace Crafters and a crafting-table one, all drawing on the network and offering to it: once settled,
        // the network offers the same every tick, whatever else reads it, and it offers stone and smooth stone.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 64));
        QuantumCrafterBlockEntity first = crafterOn(helper, 6, Items.FURNACE, true, true);
        crafterOn(helper, 8, Items.FURNACE, true, true);
        crafterOn(helper, 10, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        int[] t = {0};
        helper.onEachTick(() -> {
            if (++t[0] < 60) return;
            // Something else reading first, in a different order each tick: a crafter's screen, then the network.
            if (t[0] % 2 == 0) first.rebuildPreview();
            var handler = network(helper);
            java.util.List<String> list = new java.util.ArrayList<>();
            for (int i = 0; i < handler.size(); i++) if (!handler.getResource(i).isEmpty()) list.add(handler.getResource(i).getItem() + "x" + handler.getAmountAsLong(i));
            seen.add(list.toString());
        });
        helper.runAfterDelay(100, () -> {
            helper.assertValueEqual(seen.size(), 1, "one answer every tick: " + seen);
            String offered = seen.iterator().next();
            helper.assertTrue(offered.contains("minecraft:stonex") && offered.contains("minecraft:smooth_stonex"), "stone and smooth stone: " + offered);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_import", timeoutTicks = 200)
    public static void aNetworkReadsAnotherThroughItsMaterialiser(GameTestHelper helper) {
        // Network A: cobblestone, a furnace crafter offering to it, its Materialiser. Network B, a row above: a provider
        // on A's Materialiser, and B's own Materialiser. B offers A's cobblestone as real, its stone as craftable.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 23));
        crafterOn(helper, 6, Items.FURNACE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        controller(helper, new BlockPos(2, Y + 1, 8), Direction.EAST, true);
        helper.setBlock(MATERIALISER.above(), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        var importer = helper.getBlockEntity(MATERIALISER.above(), LogisticsReceptacleBlockEntity.class);
        while (importer.role() != LogisticsMember.Role.PROVIDER) importer.cycleRole();
        BlockPos theirs = new BlockPos(14, Y + 1, 8);
        helper.setBlock(theirs, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(100, () -> {
            var b = helper.getBlockEntity(new BlockPos(2, Y + 1, 8), com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity.class).offers();
            var cobble = resource(Items.COBBLESTONE);
            var stone = resource(Items.STONE);
            helper.assertValueEqual(b.real(cobble), 23L, "A's cobblestone, real: " + b.entries());
            helper.assertTrue(b.craftable(stone) > 0, "A's stone, craftable: " + b.entries());
            var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, helper.absolutePos(theirs), Direction.UP);
            int got = 0;
            for (int i = 0; i < handler.size(); i++) {
                if (!handler.getResource(i).equals(stone)) continue;
                try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    got = handler.extract(i, stone, 1, transaction);
                    transaction.commit();
                }
            }
            helper.assertValueEqual(got, 1, "stone through B");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 22, "made from A's cobblestone");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_self_import", timeoutTicks = 200)
    public static void aMaterialiserReadByItsOwnNetworkCountsOnce(GameTestHelper helper) {
        // A provider of the network sits on the network's own Materialiser: nothing loops or doubles. And its modes.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 23));
        crafterOn(helper, 6, Items.FURNACE, true, true);
        // The light up a level at a relay, and along over the Materialiser, through a provider sitting on it.
        BlockPos split = new BlockPos(10, Y, 8);
        helper.setBlock(split, ModBlocks.LASER_RELAY.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        helper.getBlockEntity(split, com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class).addEmitter(Direction.UP);
        helper.setBlock(split.above(), ModBlocks.LASER_RELAY.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST)
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.MOUNT, Direction.NORTH));
        helper.setBlock(MATERIALISER.above(), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        var onItself = helper.getBlockEntity(MATERIALISER.above(), LogisticsReceptacleBlockEntity.class);
        while (onItself.role() != LogisticsMember.Role.PROVIDER) onItself.cycleRole();
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        var materialiser = helper.getBlockEntity(MATERIALISER, com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity.class);
        helper.runAfterDelay(100, () -> {
            var offers = helper.getBlockEntity(new BlockPos(2, Y, 8), com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity.class).offers();
            helper.assertTrue(onItself.isConnected(), "the provider on the Materialiser is on the network");
            helper.assertValueEqual(offers.real(resource(Items.COBBLESTONE)), 23L, "the cobblestone once: " + offers.entries());
            helper.assertTrue(offers.craftable(resource(Items.STONE)) > 0, "and stone craftable");
            materialiser.cycleMode();
            helper.assertTrue(listed(helper, Items.COBBLESTONE) && !listed(helper, Items.STONE), "real only: no stone");
            materialiser.cycleMode();
            helper.assertTrue(!listed(helper, Items.COBBLESTONE) && listed(helper, Items.STONE), "craftable only: no cobblestone");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    private static boolean listed(GameTestHelper helper, Item item) {
        var handler = network(helper);
        for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).equals(resource(item))) return true;
        return false;
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_basic_pair", timeoutTicks = 200)
    public static void twoBasicCraftersOnOneNetworkSettle(GameTestHelper helper) {
        // A chest of cobblestone as a provider; two Basic Crafters with furnaces, both drawing on the network, the
        // first also offering to it. They settle without breaking, and smooth stone taken costs a cobblestone.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.PROVIDER, false, new ItemStack(Items.COBBLESTONE, 16));
        QuantumCrafterBlockEntity first = CrafterTests.basic(helper, new BlockPos(6, Y - 1, 8), Items.FURNACE);
        QuantumCrafterBlockEntity second = CrafterTests.basic(helper, new BlockPos(9, Y - 1, 8), Items.FURNACE);
        for (QuantumCrafterBlockEntity crafter : List.of(first, second)) {
            TestSupport.fill(crafter.getEnergyStorage());
            crafter.getInventory().setStackInSlot(0, CrafterTests.link(helper, MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get()));
        }
        helper.setBlock(new BlockPos(6, Y, 8), ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState().setValue(LogisticsReceptacleBlock.FACING, Direction.DOWN));
        var provider = helper.getBlockEntity(new BlockPos(6, Y, 8), LogisticsReceptacleBlockEntity.class);
        while (provider.role() != LogisticsMember.Role.PROVIDER) provider.cycleRole();
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(CrafterTests.craftOf(second, Items.SMOOTH_STONE) != null, "the second makes smooth stone from the first's stone");
            int slot = CrafterTests.ghostSlot(second, Items.SMOOTH_STONE);
            ItemStack taken = second.pullHandler().extractItem(slot, 1, false);
            helper.assertValueEqual(taken.getCount(), 1, "a smooth stone");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE) + countIn(first, Items.STONE) + countIn(second, Items.STONE)
                    + countIn(second, Items.SMOOTH_STONE), 15, "for one cobblestone");
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

    private static Item ae2(String name) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("ae2", name));
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_ae2_chain", timeoutTicks = 200)
    public static void aSixStepChainIsListedAndMade(GameTestHelper helper) {
        // AE2 storage components from raw stuff: an Inscriber Crafter prints circuits and presses processors, a
        // crafting-table Crafter makes 1k, 4k, 16k and 64k components from them: six steps, all listed. Three 1k
        // components stored besides: the table shows the 4k components the chain could make, not just the one
        // those three make. A 64k component taken makes the whole chain.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false,
                new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.REDSTONE, 64),
                new ItemStack(ae2("certus_quartz_crystal"), 64), new ItemStack(ae2("certus_quartz_crystal"), 64),
                new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(ae2("silicon"), 64), new ItemStack(ae2("silicon"), 64),
                new ItemStack(ae2("quartz_glass"), 64), new ItemStack(Items.GLOWSTONE_DUST, 64), new ItemStack(ae2("cell_component_1k"), 3));
        QuantumCrafterBlockEntity inscriber = crafterOn(helper, 6, ae2("inscriber"), true, true);
        inscriber.getInventory().setStackInSlot(1, new ItemStack(ae2("logic_processor_press")));
        inscriber.getInventory().setStackInSlot(2, new ItemStack(ae2("calculation_processor_press")));
        inscriber.getInventory().setStackInSlot(3, new ItemStack(ae2("silicon_press")));
        QuantumCrafterBlockEntity table = crafterOn(helper, 9, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(amount(helper, ae2("cell_component_16k")) > 0, "16k components listed");
            helper.assertTrue(amount(helper, ae2("cell_component_64k")) > 0, "64k components listed");
            table.rebuildPreview();
            var four = CrafterTests.craftOf(table, ae2("cell_component_4k"));
            helper.assertTrue(four != null && four.availableOutputCount(0) > 1, "the table shows what the chain makes: "
                    + (four == null ? "none" : four.availableOutputCount(0)));
            helper.assertValueEqual(take(helper, ae2("cell_component_64k"), 1, true), 1, "a 64k component taken");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_many", timeoutTicks = 200)
    public static void aNetworkListsEverythingACrafterCouldMake(GameTestHelper helper) {
        // Eight kinds of planks and sticks stored, a crafting-table Crafter on the network: more than its screen's 54
        // outputs could be made, and the network lists them all.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.BIRCH_PLANKS, 64),
                new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.JUNGLE_PLANKS, 64), new ItemStack(Items.ACACIA_PLANKS, 64),
                new ItemStack(Items.DARK_OAK_PLANKS, 64), new ItemStack(Items.MANGROVE_PLANKS, 64), new ItemStack(Items.CHERRY_PLANKS, 64),
                new ItemStack(Items.STICK, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 64));
        crafterOn(helper, 6, Items.CRAFTING_TABLE, true, true);
        LogisticsControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(2, Y, 8), LogisticsControllerBlockEntity.class);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            int made = controller.offers().madeBy(helper.absolutePos(new BlockPos(6, Y - 1, 8))).size();
            helper.assertTrue(made > 54, "the network lists all it could make: " + made);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_own_chain", timeoutTicks = 200)
    public static void aLoneCrafterChainsItsOwnCrafts(GameTestHelper helper) {
        // One crafting-table Crafter on the network, 35 iron blocks and 4 ingots stored: it offers leggings (ingots it
        // would make from blocks first), shows them itself, and one taken costs seven ingots' worth of iron. No more
        // blocks are offered than are stored.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.IRON_BLOCK, 35), new ItemStack(Items.IRON_INGOT, 4));
        QuantumCrafterBlockEntity crafter = crafterOn(helper, 6, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(amount(helper, Items.IRON_LEGGINGS) > 0, "leggings offered");
            helper.assertValueEqual(amount(helper, Items.IRON_BLOCK), 35L, "only the stored blocks");
            crafter.rebuildPreview();
            helper.assertTrue(CrafterTests.craftOf(crafter, Items.IRON_LEGGINGS) != null, "the Crafter shows leggings too");
            helper.assertValueEqual(take(helper, Items.IRON_LEGGINGS, 1, true), 1, "a pair taken");
            int iron = 9 * (count(helper, 4, Items.IRON_BLOCK) + countIn(crafter, Items.IRON_BLOCK))
                    + count(helper, 4, Items.IRON_INGOT) + countIn(crafter, Items.IRON_INGOT);
            helper.assertValueEqual(iron, 35 * 9 + 4 - 7, "for seven ingots' worth");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_twice", timeoutTicks = 200)
    public static void aCrafterMakesTwoStepsOfOneChain(GameTestHelper helper) {
        // Cobblestone stored; a furnace Crafter and a crafting-table one, both drawing on the network and offering to
        // it. Cracked stone bricks need the furnace twice: stone for the bricks, then the bricks cracked. One taken
        // costs four cobblestone; what's left over stays as stone bricks.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 16));
        QuantumCrafterBlockEntity furnace = crafterOn(helper, 6, Items.FURNACE, true, true);
        QuantumCrafterBlockEntity table = crafterOn(helper, 9, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(listed(helper, Items.CRACKED_STONE_BRICKS), "cracked stone bricks are offered");
            helper.assertValueEqual(take(helper, Items.CRACKED_STONE_BRICKS, 1, true), 1, "one taken");
            int bricks = countIn(furnace, Items.STONE_BRICKS) + countIn(table, Items.STONE_BRICKS) + count(helper, 4, Items.STONE_BRICKS);
            int stone = countIn(furnace, Items.STONE) + countIn(table, Items.STONE);
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE), 12, "for four cobblestone");
            helper.assertValueEqual(bricks + stone, 3, "the rest kept: " + bricks + " bricks, " + stone + " stone");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_loop_one_craft", timeoutTicks = 200)
    public static void aLoopingCraftLeavesTheOthersBe(GameTestHelper helper) {
        // 35 iron blocks and 4 ingots stored, two crafting-table Crafters on the network. Blocks made from ingots
        // made from blocks loop, so no more blocks are offered; but leggings and nuggets from ingots made from blocks
        // are.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.IRON_BLOCK, 35), new ItemStack(Items.IRON_INGOT, 4));
        crafterOn(helper, 6, Items.CRAFTING_TABLE, true, true);
        crafterOn(helper, 9, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(amount(helper, Items.IRON_BLOCK), 35L, "only the stored blocks");
            helper.assertTrue(amount(helper, Items.IRON_LEGGINGS) >= 45, "leggings from ingots made from blocks: " + amount(helper, Items.IRON_LEGGINGS));
            helper.assertTrue(amount(helper, Items.IRON_NUGGET) > 36, "nuggets past the four stored ingots: " + amount(helper, Items.IRON_NUGGET));
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_whole_stock", timeoutTicks = 200)
    public static void aCrafterOnTheNetworkCountsEveryStack(GameTestHelper helper) {
        // Two stacks of iron in storage, a crafting-table Crafter drawing on the network and offering to it: it can
        // make fourteen blocks, not one stack's worth, and ten taken cost ninety ingots.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.IRON_INGOT, 64));
        QuantumCrafterBlockEntity crafter = crafterOn(helper, 6, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            var craft = CrafterTests.craftOf(crafter, Items.IRON_BLOCK);
            helper.assertTrue(craft != null, "it previews iron blocks");
            helper.assertValueEqual(craft.availableOutputCount(0), 14L, "it can make fourteen");
            helper.assertValueEqual(amount(helper, Items.IRON_BLOCK), 14L, "the network offers fourteen");
            helper.assertValueEqual(take(helper, Items.IRON_BLOCK, 10, true), 10, "ten taken");
            helper.assertValueEqual(count(helper, 4, Items.IRON_INGOT) + countIn(crafter, Items.IRON_INGOT), 128 - 90, "for ninety ingots");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.requester, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_request_stored", timeoutTicks = 300)
    public static void aRequesterForStoredItemsHasNothingMade(GameTestHelper helper) {
        // An active requester for stone, and a furnace Crafter offering stone it can make from its cobblestone. Set to
        // request stored items only, it gets none and nothing is made; let it have things made, and stone comes.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        QuantumCrafterBlockEntity furnace = crafterOn(helper, 4, Items.FURNACE, false, true);
        furnace.getInventory().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 16));
        LogisticsReceptacleBlockEntity requester = onChest(helper, 8, LogisticsMember.Role.REQUESTER, true);
        requester.setFilterSlot(0, new ItemStack(Items.STONE));
        requester.toggleRequestsMade();
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(count(helper, 8, Items.STONE), 0, "no stone made for it");
            helper.assertValueEqual(countIn(furnace, Items.COBBLESTONE), 16, "no cobblestone spent");
            requester.toggleRequestsMade();
            helper.succeedWhen(() -> helper.assertTrue(count(helper, 8, Items.STONE) > 0, "allowed, stone is made for it"));
        });
    }

    // covers: laser.logistics.terminal, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_stored_first", timeoutTicks = 200)
    public static void aTerminalHandsOverWhatsStoredBeforeMakingMore(GameTestHelper helper) {
        // One furnace stored, and a Crafter offering to the network that could make four from its 32 cobblestone. The
        // terminal lists four craftable, not one craft's worth; a stack asked for brings the one stored furnace, and
        // only asked again does the Crafter make some.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.FURNACE, 1));
        QuantumCrafterBlockEntity crafter = crafterOn(helper, 6, Items.CRAFTING_TABLE, false, true);
        crafter.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 32));
        BlockPos at = new BlockPos(10, Y, 8);
        helper.setBlock(at, ModBlocks.LOGISTICS_TERMINAL.get());
        var terminal = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity.class);
        var furnace = resource(Items.FURNACE);
        helper.runAfterDelay(80, () -> {
            long[] listed = terminal.contents().get(furnace);
            helper.assertTrue(listed != null, "furnaces are listed");
            helper.assertValueEqual(listed[0], 1L, "one stored");
            helper.assertValueEqual(listed[1], 4L, "four can be made");
            helper.assertValueEqual(terminal.take(furnace, 64).getCount(), 1, "the stored one, and no more");
            helper.assertValueEqual(countIn(crafter, Items.COBBLESTONE), 32, "nothing made for it");
            helper.assertTrue(terminal.take(furnace, 64).getCount() > 0, "asked again, some are made");
            helper.assertTrue(countIn(crafter, Items.COBBLESTONE) < 32, "out of the Crafter's cobblestone");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: laser.logistics.materialiser, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_loop_count", timeoutTicks = 200)
    public static void ingotsMadeBackFromTheirOwnBlocksArentCounted(GameTestHelper helper) {
        // Two Crafters drawing on the network and offering to it, each able to make a block from nine ingots and nine
        // ingots from a block. With nine ingots stored, the network offers those nine: none more made from blocks
        // that would be made from them.
        TestSupport.clearField(helper);
        controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.IRON_INGOT, 9));
        crafterOn(helper, 6, Items.CRAFTING_TABLE, true, true);
        crafterOn(helper, 9, Items.CRAFTING_TABLE, true, true);
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(amount(helper, Items.IRON_INGOT), 9L, "the nine stored ingots only");
            helper.assertTrue(amount(helper, Items.IRON_BLOCK) > 0, "blocks can still be made");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    private static long amount(GameTestHelper helper, Item item) {
        var handler = network(helper);
        for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).equals(resource(item))) return handler.getAmountAsLong(i);
        return 0;
    }

    // covers: laser.logistics.receptacle, crafter.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "logistics_stored_only", timeoutTicks = 200)
    public static void aCrafterMakingFromStoredItemsOnlyMakesNoChain(GameTestHelper helper) {
        // Two furnace Crafters drawing on the network and offering to it, their providers set to craft from stored
        // items only: stone is offered, smooth stone (stone the other would have to make first) isn't, and can't be
        // taken.
        TestSupport.clearField(helper);
        LogisticsControllerBlockEntity controller = controller(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        onChest(helper, 4, LogisticsMember.Role.STORAGE, false, new ItemStack(Items.COBBLESTONE, 16));
        QuantumCrafterBlockEntity first = crafterOn(helper, 6, Items.FURNACE, true, true);
        QuantumCrafterBlockEntity second = crafterOn(helper, 9, Items.FURNACE, true, true);
        for (int x : new int[] {6, 9}) {
            var provider = helper.getBlockEntity(new BlockPos(x, Y, 8), LogisticsReceptacleBlockEntity.class);
            while (provider.crafts() != LogisticsReceptacleBlockEntity.Crafts.STORED) provider.cycleCrafts();
        }
        helper.setBlock(MATERIALISER, ModBlocks.LOGISTICS_MATERIALISER.get());
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(listed(helper, Items.STONE), "stone, from the stored cobblestone");
            helper.assertFalse(listed(helper, Items.SMOOTH_STONE), "no smooth stone: that would take a chain");
            ItemStack smooth = com.kadikular.quantimium.laser.Logistics.take(controller.members(), stack -> stack.is(Items.SMOOTH_STONE), 1, false);
            helper.assertTrue(smooth.isEmpty(), "and none can be taken");
            helper.assertValueEqual(count(helper, 4, Items.COBBLESTONE) + countIn(first, Items.STONE) + countIn(second, Items.STONE), 16,
                    "no cobblestone spent trying");
            helper.assertValueEqual(take(helper, Items.STONE, 1, true), 1, "stone is made");
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
