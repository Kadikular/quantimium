package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/** The Observation Chambers. Wiki: machines/quantum-observation-chamber. */
public final class ChamberTests {

    private static final BlockPos CHAMBER = new BlockPos(4, 2, 4);

    private ChamberTests() {}

    // covers: quantum_chamber.collapse
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_collapse", timeoutTicks = 80)
    public static void itCollapsesMatterOnItsOwnForFe(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumObservationChamberBlockEntity chamber = chamber(helper);
        chamber.getInventory().setStackInSlot(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 8));
        int before = chamber.getEnergyStorage().getEnergyStored();
        // Two cycles of four.
        helper.runAfterDelay(2 * QuantumObservationChamberBlockEntity.CYCLE_TICKS + 2, () -> {
            helper.assertTrue(chamber.getInventory().getStackInSlot(0).isEmpty(), "all eight collapsed");
            helper.assertTrue(results(chamber) > 0, "results in the output slots");
            helper.assertValueEqual(before - chamber.getEnergyStorage().getEnergyStored(),
                    8 * QuantumObservationChamberBlockEntity.FE_PER_COLLAPSE, "FE for eight collapses");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: unrealised.silk_touch
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_silk_ore", timeoutTicks = 80)
    public static void silkTouchedOreCollapsesLikeMatter(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumObservationChamberBlockEntity chamber = chamber(helper);
        ItemStack ore = new ItemStack(com.kadikular.quantimium.init.ModBlocks.UNREALISED_ORE.get(), 4);
        helper.assertTrue(chamber.getInventory().isItemValid(0, ore), "the chamber takes the ore block");
        chamber.getInventory().setStackInSlot(0, ore);
        helper.runAfterDelay(QuantumObservationChamberBlockEntity.CYCLE_TICKS + 2, () -> {
            helper.assertTrue(chamber.getInventory().getStackInSlot(0).isEmpty(), "all four blocks collapsed");
            helper.assertTrue(results(chamber) > 0, "results in the output slots");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: quantum_chamber.simulator
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_simulator", timeoutTicks = 400)
    public static void aSimulatedChamberCollapsesABatch(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.track(helper);
        helper.setBlock(CHAMBER, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(CHAMBER, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        helper.setBlock(CHAMBER.above(), ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get());
        simulator.toggleEngage(null);
        simulator.setBatchSize(4);
        simulator.getInventory().setStackInSlot(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 32));
        helper.succeedWhen(() -> {
            int made = 0;
            for (int slot = QuantumSimulatorBlockEntity.OUTPUT_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END; slot++) {
                ItemStack stack = simulator.getInventory().getStackInSlot(slot);
                if (!stack.isEmpty() && !stack.is(ModItems.UNREALISED_MATTER.get())) made += stack.getCount();
            }
            helper.assertTrue(made >= 8, "collapses should reach the grid's outputs: " + made + ", status "
                    + simulator.getStatusCode() + ", matter left " + simulator.getInventory().getStackInSlot(0).getCount());
            TestSupport.clearField(helper);
        });
    }

    // covers: quantum_chamber.power
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_power", timeoutTicks = 40)
    public static void withoutPowerItWaits(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(CHAMBER, ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get());
        QuantumObservationChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER, QuantumObservationChamberBlockEntity.class);
        chamber.getInventory().setStackInSlot(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(chamber.getInventory().getStackInSlot(0).getCount(), 4, "Matter left without power");
            helper.assertValueEqual(chamber.getStatusCode(), QuantumObservationChamberBlockEntity.STATUS_NO_POWER, "status");
            helper.succeed();
        });
    }

    // covers: quantum_chamber.sides
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_sides", timeoutTicks = 20)
    public static void facesTakeMatterAndHandOutResultsAsSet(GameTestHelper helper) {
        QuantumObservationChamberBlockEntity chamber = chamber(helper);
        chamber.applySideConfigs(configs(Direction.NORTH, SideMode.INPUT, false, false,
                Direction.SOUTH, SideMode.OUTPUT, false, false));
        chamber.getInventory().setStackInSlot(QuantumObservationChamberBlockEntity.OUTPUT_START, new ItemStack(net.minecraft.world.item.Items.COAL, 3));
        BlockPos at = helper.absolutePos(CHAMBER);
        IItemHandler north = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.NORTH));
        IItemHandler south = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.SOUTH));
        IItemHandler east = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.EAST));
        helper.assertTrue(north != null && south != null, "input and output faces should offer the chamber");
        helper.assertTrue(east == null, "a face set to off should offer nothing");
        ItemStack matter = new ItemStack(ModItems.UNREALISED_MATTER.get(), 5);
        helper.assertTrue(north.insertItem(0, matter, false).isEmpty(), "the input face should take Matter");
        helper.assertTrue(north.extractItem(QuantumObservationChamberBlockEntity.OUTPUT_START, 1, true).isEmpty(),
                "the input face should hand nothing out");
        helper.assertTrue(!south.insertItem(0, matter, true).isEmpty(), "the output face should refuse Matter");
        helper.assertValueEqual(south.extractItem(QuantumObservationChamberBlockEntity.OUTPUT_START, 3, false).getCount(), 3,
                "results taken from the output face");
        helper.succeed();
    }

    // covers: quantum_chamber.auto
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_auto", timeoutTicks = 120)
    public static void itPullsMatterAndPushesResultsByItself(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumObservationChamberBlockEntity chamber = chamber(helper);
        ChestBlockEntity source = CrafterTests.chest(helper, CHAMBER.east(), new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        ChestBlockEntity sink = CrafterTests.chest(helper, CHAMBER.west(), ItemStack.EMPTY);
        chamber.applySideConfigs(configs(Direction.EAST, SideMode.INPUT, true, false,
                Direction.WEST, SideMode.OUTPUT, false, true));
        helper.succeedWhen(() -> {
            helper.assertTrue(source.isEmpty(), "the Matter should be pulled out of the chest");
            int pushed = 0;
            for (int slot = 0; slot < sink.getContainerSize(); slot++) {
                ItemStack stack = sink.getItem(slot);
                if (!stack.is(ModItems.QUANTIMIUM_TRACE.get())) pushed += stack.getCount();
            }
            helper.assertTrue(pushed > 0, "results should be pushed into the other chest");
            TestSupport.clearField(helper);
        });
    }

    // covers: quantum_chamber.trace_hold, chamber.trace_hold
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_trace", timeoutTicks = 40)
    public static void keepingTraceWaitsForRoom(GameTestHelper helper) {
        QuantumObservationChamberBlockEntity chamber = chamber(helper);
        chamber.toggleVoidExcessTrace();
        chamber.getInventory().setStackInSlot(QuantumObservationChamberBlockEntity.TRACE_SLOT,
                new ItemStack(ModItems.QUANTIMIUM_TRACE.get(), 64));
        chamber.getInventory().setStackInSlot(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        // The low chamber keeps the same rule on a redstone pulse.
        BlockPos low = new BlockPos(1, 2, 1);
        helper.setBlock(low, ModBlocks.OBSERVATION_CHAMBER.get());
        ObservationChamberBlockEntity basic = helper.getBlockEntity(low, ObservationChamberBlockEntity.class);
        basic.setVoidExcessTrace(false);
        basic.getInventory().setStackInSlot(ObservationChamberBlockEntity.TRACE_SLOT, new ItemStack(ModItems.QUANTIMIUM_TRACE.get(), 64));
        basic.getInventory().setStackInSlot(ObservationChamberBlockEntity.INPUT_SLOT, new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        helper.setBlock(low.east(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(chamber.getInventory().getStackInSlot(0).getCount(), 4, "Matter kept while Trace has no room");
            helper.assertValueEqual(chamber.getStatusCode(), QuantumObservationChamberBlockEntity.STATUS_OUTPUT_FULL, "status");
            helper.assertValueEqual(basic.getInventory().getStackInSlot(ObservationChamberBlockEntity.INPUT_SLOT).getCount(), 4,
                    "the low chamber keeps its Matter too");
            helper.setBlock(low.east(), Blocks.AIR);
            helper.setBlock(low, Blocks.AIR);
            helper.succeed();
        });
    }

    // covers: quantum_chamber.flux
    @GameTest(template = TestSupport.FLOOR_9, batch = "chamber_flux", timeoutTicks = 80)
    public static void itsWorkEmitsFlux(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumObservationChamberBlockEntity chamber = chamber(helper);
        chamber.getInventory().setStackInSlot(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 64));
        helper.succeedWhen(() -> {
            helper.assertTrue(QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(CHAMBER)) > 0.0,
                    "a working chamber should raise its chunk's flux");
            TestSupport.clearField(helper);
        });
    }

    // ---- helpers ----

    private static QuantumObservationChamberBlockEntity chamber(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(CHAMBER, ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get());
        QuantumObservationChamberBlockEntity chamber = helper.getBlockEntity(CHAMBER, QuantumObservationChamberBlockEntity.class);
        TestSupport.fill(chamber.getEnergyStorage());
        return chamber;
    }

    private static int results(QuantumObservationChamberBlockEntity chamber) {
        int count = 0;
        for (int slot = QuantumObservationChamberBlockEntity.OUTPUT_START; slot <= QuantumObservationChamberBlockEntity.OUTPUT_END; slot++) {
            count += chamber.getInventory().getStackInSlot(slot).getCount();
        }
        return count;
    }

    /** Two faces set as given, every other face off. */
    private static List<SideConfig> configs(Direction a, SideMode aMode, boolean aIn, boolean aOut,
                                            Direction b, SideMode bMode, boolean bIn, boolean bOut) {
        List<SideConfig> configs = new ArrayList<>();
        for (Direction side : Direction.values()) {
            SideMode mode = side == a ? aMode : side == b ? bMode : SideMode.DISABLED;
            boolean in = side == a ? aIn : side == b && bIn;
            boolean out = side == a ? aOut : side == b && bOut;
            configs.add(new SideConfig(side, mode, SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, in, out,
                    SideMode.DISABLED, 0, 0, false, false));
        }
        return configs;
    }
}
