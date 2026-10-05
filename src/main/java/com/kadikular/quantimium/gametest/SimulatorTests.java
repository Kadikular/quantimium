package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyFluids;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.block.entity.simulation.SlotMapping;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;

/**
 * The Quantum Simulator with a vanilla furnace in its field: engaging and disengaging, slot mapping,
 * batches, what it charges, and its faces. See wiki: machines/quantum-simulator.
 *
 * <p>A furnace takes 200 ticks a smelt and the simulator calibrates before it scales up, so the
 * running tests wait on the machine's state rather than on fixed tick counts where they can.
 */
public final class SimulatorTests {

    private static final BlockPos SIMULATOR = new BlockPos(4, 2, 4);
    private static final int GRID_OUTPUTS_START = QuantumSimulatorBlockEntity.OUTPUT_START;

    private SimulatorTests() {}

    // covers: simulator.engage, simulator.disengage
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_engage", timeoutTicks = 40)
    public static void engagingTakesTheMachineAndGivesItBack(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = simulator(helper);
        helper.setBlock(SIMULATOR.above(), Blocks.FURNACE);
        FurnaceBlockEntity furnace = helper.getBlockEntity(SIMULATOR.above(), FurnaceBlockEntity.class);
        furnace.setItem(1, new ItemStack(Items.COAL, 5));

        simulator.toggleEngage(null);
        helper.assertTrue(simulator.isEngaged(), "the field should be up");
        helper.assertBlockPresent(ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get(), SIMULATOR.above());
        helper.assertTrue(simulator.getInventory().getStackInSlot(QuantumSimulatorBlockEntity.TARGET_DISPLAY_SLOT)
                .is(Items.FURNACE), "the display slot should show the furnace");

        simulator.toggleEngage(null);
        helper.assertTrue(!simulator.isEngaged(), "the field should be down");
        helper.assertBlockPresent(Blocks.FURNACE, SIMULATOR.above());
        FurnaceBlockEntity back = helper.getBlockEntity(SIMULATOR.above(), FurnaceBlockEntity.class);
        helper.assertTrue(back.getItem(1).is(Items.COAL) && back.getItem(1).getCount() == 5,
                "the furnace should come back with its coal");
        helper.succeed();
    }

    // covers: simulator.engage
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_bedrock", timeoutTicks = 40)
    public static void unbreakableBlocksCannotBeTaken(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = simulator(helper);
        helper.setBlock(SIMULATOR.above(), Blocks.BEDROCK);
        simulator.toggleEngage(null);
        helper.assertTrue(!simulator.isEngaged(), "bedrock should not be taken");
        helper.assertBlockPresent(Blocks.BEDROCK, SIMULATOR.above());
        helper.setBlock(SIMULATOR.above(), Blocks.AIR);
        helper.succeed();
    }

    // covers: simulator.mapping
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_mapping", timeoutTicks = 40)
    public static void theGridIsMappedAcrossTheMachinesInputs(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engagedFurnace(helper);
        List<SlotMapping> mappings = simulator.getSlotMappings();
        for (int grid = 0; grid < QuantumSimulatorBlockEntity.INPUT_SLOTS; grid++) {
            // A furnace takes an ingredient (slot 0) and fuel (slot 1); the grid cycles through them.
            helper.assertValueEqual(mappings.get(grid).targetSlotIndex(), grid % 2, "machine slot for grid slot " + grid);
        }
        helper.succeed();
    }

    // covers: simulator.batch
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_batch", timeoutTicks = 800)
    public static void eachCycleMakesABatch(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engagedFurnace(helper);
        simulator.setBatchSize(4);
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 16));
        simulator.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 8));
        // The first ingots should arrive as a whole batch at once, never one at a time.
        int[] first = {0};
        helper.onEachTick(() -> {
            int made = outputs(simulator);
            if (first[0] == 0 && made > 0) first[0] = made;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0, "no ingots yet");
            helper.assertValueEqual(first[0], 4, "ingots from the first cycle at 4x");
        });
    }

    // covers: simulator.batch
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_partial", timeoutTicks = 800)
    public static void aShortGridRunsFewerCopies(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engagedFurnace(helper);
        simulator.setBatchSize(8);
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 5));
        simulator.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 8));
        // The batch is a ceiling: five raw iron at 8x smelt five at a time.
        int[] first = {0};
        helper.onEachTick(() -> {
            if (first[0] == 0) first[0] = outputs(simulator);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first[0] > 0, "no ingots yet");
            helper.assertValueEqual(first[0], 5, "ingots from the first cycle with five raw iron at 8x");
        });
    }

    // covers: simulator.cost, simulator.baseline
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_cost", timeoutTicks = 800)
    public static void anUnpoweredMachineIsBilledABaselinePerCopy(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumSimulatorBlockEntity simulator = engagedFurnace(helper);
        simulator.setBatchSize(2);
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 32));
        simulator.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 16));
        // Once it is working, 20 ticks of a busy furnace at 2x: 20 FE/t × 2 copies × 1.2 = 48 FE/t.
        int[] energy = {-1, -1};
        long[] since = {0};
        helper.onEachTick(() -> {
            int stored = simulator.getEnergyStorage().getEnergyStored();
            if (energy[0] < 0 && simulator.getStatusCode() == PhantomMirrorEngine.STATUS_WORKING
                    && outputs(simulator) > 0) {
                energy[0] = stored;
                since[0] = helper.getTick();
            } else if (energy[0] >= 0 && energy[1] < 0 && helper.getTick() == since[0] + 20) {
                energy[1] = stored;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(energy[1] >= 0, "not measured yet");
            helper.assertValueEqual(energy[0] - energy[1], 20 * 48, "FE over 20 busy ticks at 2x");
        });
    }

    // covers: simulator.debt
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_debt", timeoutTicks = 400)
    public static void anEmptyBufferStopsTheWork(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engagedFurnace(helper);
        simulator.getEnergyStorage().setEnergy(30);
        simulator.setBatchSize(2);
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 8));
        simulator.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 4));
        helper.succeedWhen(() -> helper.assertValueEqual(simulator.getStatusCode(),
                PhantomMirrorEngine.STATUS_NO_POWER, "status with 30 FE"));
    }

    // covers: simulator.flux
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_flux", timeoutTicks = 800)
    public static void workEmitsFlux(GameTestHelper helper) {
        TestSupport.setField(helper, SIMULATOR, 0.0, 0.0);
        QuantumSimulatorBlockEntity simulator = engagedFurnace(helper);
        simulator.setBatchSize(8);
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 32));
        simulator.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 16));
        helper.succeedWhen(() -> {
            double flux = QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(SIMULATOR));
            helper.assertTrue(flux > 0.0, "a working simulator should raise its chunk's flux");
            TestSupport.setField(helper, SIMULATOR, 0.0, 0.0);
        });
    }

    // covers: simulator.automation
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_faces", timeoutTicks = 40)
    public static void everyFaceButTheTop(GameTestHelper helper) {
        simulator(helper);
        BlockPos at = helper.absolutePos(SIMULATOR);
        helper.assertTrue(LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.UP)) == null,
                "the top is the field, so it should offer nothing");
        for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN}) {
            helper.assertTrue(LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, side)) != null,
                    "the " + side + " face should offer items");
            helper.assertTrue(LegacyFluids.legacy(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, at, side)) == null,
                    "fluids start switched off on the " + side + " face");
        }
        helper.succeed();
    }

    // ---- helpers ----

    private static QuantumSimulatorBlockEntity simulator(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(SIMULATOR, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(SIMULATOR, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        return simulator;
    }

    private static QuantumSimulatorBlockEntity engagedFurnace(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = simulator(helper);
        helper.setBlock(SIMULATOR.above(), Blocks.FURNACE);
        simulator.toggleEngage(null);
        return simulator;
    }

    private static int outputs(QuantumSimulatorBlockEntity simulator) {
        int count = 0;
        for (int slot = GRID_OUTPUTS_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END; slot++) {
            ItemStack stack = simulator.getInventory().getStackInSlot(slot);
            if (stack.is(Items.IRON_INGOT)) count += stack.getCount();
        }
        return count;
    }
    // covers: simulator.nesting_cap
    @GameTest(template = TestSupport.FLOOR_9, batch = "simulator_nesting_cap", timeoutTicks = 40)
    public static void packsCanCapHowDeepSimulatorsNest(GameTestHelper helper) {
        TestSupport.track(helper);
        // Three simulators stacked, a furnace on top: two deep is allowed by default, three is not.
        BlockPos base = new BlockPos(4, 2, 4);
        helper.setBlock(base, ModBlocks.QUANTUM_SIMULATOR.get());
        helper.setBlock(base.above(), ModBlocks.QUANTUM_SIMULATOR.get());
        helper.setBlock(base.above(2), ModBlocks.QUANTUM_SIMULATOR.get());
        helper.setBlock(base.above(3), net.minecraft.world.level.block.Blocks.FURNACE);
        QuantumSimulatorBlockEntity outer = helper.getBlockEntity(base, QuantumSimulatorBlockEntity.class);
        QuantumSimulatorBlockEntity middle = helper.getBlockEntity(base.above(), QuantumSimulatorBlockEntity.class);
        QuantumSimulatorBlockEntity inner = helper.getBlockEntity(base.above(2), QuantumSimulatorBlockEntity.class);
        inner.toggleEngage(null);
        middle.toggleEngage(null);
        helper.assertTrue(middle.isEngaged(), "a simulator in a simulator is allowed");
        outer.toggleEngage(null);
        helper.assertTrue(!outer.isEngaged(), "a third level is past the default cap of 2");
        int before = Config.SIMULATOR_MAX_NESTING.get();
        Config.SIMULATOR_MAX_NESTING.set(3);
        outer.toggleEngage(null);
        Config.SIMULATOR_MAX_NESTING.set(before);
        helper.assertTrue(outer.isEngaged(), "with the cap raised to 3, it is allowed");
        helper.succeed();
    }
}
