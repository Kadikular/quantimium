package com.kadikular.quantimium.gametest.compat;

import com.kadikular.quantimium.util.LegacyFluids;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.gametest.TickTiming;
import com.kadikular.quantimium.gametest.TickTiming.Scenario;
import com.kadikular.quantimium.gametest.TickTimingTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.fluids.FluidStack;

import static com.kadikular.quantimium.gametest.compat.CompatGameTests.block;

/** Tick timings of Modern Industrialization machines in simulators. See {@link TickTiming}. */
public final class MiTickTimingTests {

    private static final String NS = Quantimium.MODID;
    private static final BlockPos AT = new BlockPos(4, 2, 4);
    /** How long the dry boiler burns coal before its fuel stops, inside the warm-up. */
    private static final int FIRED_TICKS = 1_000;

    private MiTickTimingTests() {}

    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "timing_mi_boiler",
            timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void boilerSimulatedAtEight(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = TickTimingTests.simulator(helper, AT);
        helper.setBlock(AT.above(), block("modern_industrialization:bronze_boiler"));
        simulator.toggleEngage(null);
        simulator.setBatchSize(8);
        helper.onEachTick(() -> feedBoiler(simulator));
        TickTiming.measure(helper, new Scenario("mi.boiler_8x", "Modern Industrialization",
                        "A bronze boiler at 8x, fed coal and water every tick", 50),
                simulator, () -> TickTimingTests.status(simulator), () -> {});
    }

    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "timing_mi_boiler_nested",
            timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void boilerNestedAtSixtyFour(GameTestHelper helper) {
        QuantumSimulatorBlockEntity outer = TickTimingTests.simulator(helper, AT);
        QuantumSimulatorBlockEntity inner = TickTimingTests.simulator(helper, AT.above());
        helper.setBlock(AT.above(2), block("modern_industrialization:bronze_boiler"));
        inner.setBatchSize(8);
        inner.toggleEngage(null);
        outer.toggleEngage(null);
        outer.setBatchSize(8);
        helper.onEachTick(() -> feedBoiler(outer));
        TickTiming.measure(helper, new Scenario("mi.boiler_nested_64x", "Modern Industrialization",
                        "A bronze boiler at 8x in a simulator at 8x, fed coal and water every tick", 60),
                outer, () -> TickTimingTests.status(outer), () -> {});
    }

    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "timing_mi_boiler_nested_dry",
            timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void boilerNestedOutOfFuel(GameTestHelper helper) {
        QuantumSimulatorBlockEntity outer = TickTimingTests.simulator(helper, AT);
        QuantumSimulatorBlockEntity inner = TickTimingTests.simulator(helper, AT.above());
        helper.setBlock(AT.above(2), block("modern_industrialization:bronze_boiler"));
        inner.setBatchSize(8);
        inner.toggleEngage(null);
        outer.toggleEngage(null);
        outer.setBatchSize(8);
        // Fired up first, then left with water only: measured while it cools, as one that ran out would.
        helper.onEachTick(() -> {
            if (helper.getTick() < FIRED_TICKS) {
                feedBoiler(outer);
            } else {
                TestSupport.fill(outer.getEnergyStorage());
                outer.getInventory().setStackInSlot(0, ItemStack.EMPTY);
                outer.getFluidTanks().setFluid(0, new FluidStack(Fluids.WATER, outer.getFluidTanks().getCapacity(0)));
            }
        });
        TickTiming.measure(helper, new Scenario("mi.boiler_nested_64x_no_fuel", "Modern Industrialization",
                        "A bronze boiler at 8x in a simulator at 8x, run on coal and then left with water only", 60),
                outer, () -> TickTimingTests.status(outer), () -> {});
    }

    /**
     * A real bronze boiler, not simulated, fed coal and water through its own capabilities and left
     * to fill its steam buffer: what Modern Industrialization's own tick costs, for comparison.
     */
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "timing_mi_boiler_alone",
            timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void boilerAlone(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(AT, block("modern_industrialization:bronze_boiler"));
        BlockEntity boiler = helper.getBlockEntity(AT, BlockEntity.class);
        BlockPos at = helper.absolutePos(AT);
        helper.onEachTick(() -> {
            IItemHandler items = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, null));
            if (items != null) ItemHandlerHelper.insertItem(items, new ItemStack(Items.COAL, 64), false);
            IFluidHandler fluids = LegacyFluids.legacy(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, at, null));
            if (fluids != null) fluids.fill(new FluidStack(Fluids.WATER, 16_000), IFluidHandler.FluidAction.EXECUTE);
        });
        TickTiming.measure(helper, new Scenario("mi.boiler_alone", "Modern Industrialization",
                        "A bronze boiler on its own (not simulated), fuelled, its steam left to fill up", 60),
                boiler, () -> "Native", () -> {});
    }

    /** Coal and water in, steam and anything else out, so it never stalls on either. */
    private static void feedBoiler(QuantumSimulatorBlockEntity simulator) {
        TestSupport.fill(simulator.getEnergyStorage());
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.COAL, 64));
        simulator.getFluidTanks().setFluid(0, new FluidStack(Fluids.WATER, simulator.getFluidTanks().getCapacity(0)));
        for (int tank = 0; tank < simulator.getFluidTanks().getTanks(); tank++) {
            if (simulator.getFluidTanks().isOutput(tank)) simulator.getFluidTanks().setFluid(tank, FluidStack.EMPTY);
        }
        TickTimingTests.clearOutputs(simulator);
    }
}
