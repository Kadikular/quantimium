package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.EntangledDockBlockEntity;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Entangled Dock. Wiki: blocks/entangled_dock. */
public final class DockTests {

    private static final BlockPos DOCK = new BlockPos(4, 2, 4);

    private DockTests() {}

    // covers: dock.bind
    @GameTest(template = TestSupport.FLOOR_9, batch = "dock_bind", timeoutTicks = 20)
    public static void anFeItemIsEntangledAndStaysInHand(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, new BlockPos(4, 2, 1));
        EntangledDockBlockEntity dock = dock(helper);
        ItemStack lance = lanceIn(player);
        helper.assertTrue(!EntangledDockBlockEntity.canBind(new ItemStack(Items.STICK)), "only items that hold FE bind");
        dock.bind(player, lance);
        helper.assertTrue(dock.isBound(), "the dock should be bound");
        helper.assertTrue(lance.has(ModDataComponents.DOCK_BINDING.get()), "the item carries the binding");
        helper.assertTrue(player.getInventory().getItem(0) == lance, "the item stays in the player's inventory");
        helper.assertTrue(dock.shown().is(ModItems.DECOHERENCE_LANCE.get()), "the dock shows a copy of it");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: dock.charge
    @GameTest(template = TestSupport.FLOOR_9, batch = "dock_charge", timeoutTicks = 60)
    public static void theDockChargesItsItemWhereverItIs(GameTestHelper helper) {
        TestSupport.clearField(helper);
        ServerPlayer player = TestSupport.player(helper, new BlockPos(1, 2, 1));
        EntangledDockBlockEntity dock = dock(helper);
        ItemStack lance = lanceIn(player);
        dock.bind(player, lance);
        int dockBefore = dock.getEnergyStorage().getEnergyStored();
        helper.runAfterDelay(2 * EntangledDockBlockEntity.INTERVAL + 1, () -> {
            int charged = DecoherenceLanceItem.energy(player.getInventory().getItem(0));
            helper.assertTrue(charged > 0, "the lance in the player's inventory should have been charged");
            helper.assertValueEqual(dockBefore - dock.getEnergyStorage().getEnergyStored(), charged, "FE taken from the dock");
            helper.assertValueEqual(dock.status(), EntangledDockBlockEntity.STATUS_CHARGING, "status");
            helper.assertTrue(QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(DOCK)) > 0.0, "charging emits flux");
            TestSupport.clearField(helper);
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // covers: dock.release
    @GameTest(template = TestSupport.FLOOR_9, batch = "dock_release", timeoutTicks = 60)
    public static void aReleasedOrReboundItemIsNoLongerCharged(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, new BlockPos(1, 2, 1));
        EntangledDockBlockEntity dock = dock(helper);
        ItemStack first = lanceIn(player);
        dock.bind(player, first);
        // Binding a second item leaves the first behind; releasing leaves both.
        ItemStack second = new ItemStack(ModItems.DECOHERENCE_LANCE.get());
        player.getInventory().setItem(1, second);
        dock.bind(player, second);
        helper.runAfterDelay(EntangledDockBlockEntity.INTERVAL + 1, () -> {
            helper.assertValueEqual(DecoherenceLanceItem.energy(player.getInventory().getItem(0)), 0, "the first lance, left behind");
            helper.assertTrue(DecoherenceLanceItem.energy(player.getInventory().getItem(1)) > 0, "the second lance, now bound");
            dock.release();
            int held = DecoherenceLanceItem.energy(player.getInventory().getItem(1));
            helper.runAfterDelay(EntangledDockBlockEntity.INTERVAL + 1, () -> {
                helper.assertValueEqual(DecoherenceLanceItem.energy(player.getInventory().getItem(1)), held, "after release");
                TestSupport.removePlayer(helper, player);
                helper.succeed();
            });
        });
    }

    // covers: dock.decohere
    @GameTest(template = TestSupport.FLOOR_9, batch = "dock_decohere", timeoutTicks = 60)
    public static void atCriticalAnomalyTheEntanglementDecoheres(GameTestHelper helper) {
        TestSupport.clearField(helper);
        ServerPlayer player = TestSupport.player(helper, new BlockPos(1, 2, 1));
        EntangledDockBlockEntity dock = dock(helper);
        dock.bind(player, lanceIn(player));
        TestSupport.setField(helper, DOCK, 0.0, 100_000.0);
        helper.runAfterDelay(EntangledDockBlockEntity.INTERVAL + 1, () -> {
            helper.assertTrue(!dock.isBound(), "a Critical field should break the entanglement");
            TestSupport.setField(helper, DOCK, 0.0, 0.0);
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    private static EntangledDockBlockEntity dock(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(DOCK, ModBlocks.ENTANGLED_DOCK.get());
        EntangledDockBlockEntity dock = helper.getBlockEntity(DOCK, EntangledDockBlockEntity.class);
        TestSupport.fill(dock.getEnergyStorage());
        return dock;
    }

    /** An empty Lance in the player's first slot. */
    private static ItemStack lanceIn(ServerPlayer player) {
        ItemStack lance = new ItemStack(ModItems.DECOHERENCE_LANCE.get());
        player.getInventory().setItem(0, lance);
        return lance;
    }
}
