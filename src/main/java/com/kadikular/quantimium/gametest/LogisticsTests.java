package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
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
}
