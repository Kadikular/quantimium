package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.gametest.TickTiming.Scenario;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tick timings of the machines that do the most per tick, idle and working, alone and inside
 * simulators. Wiki: reference/tick-timing (generated from the report these write; see
 * {@link TickTiming}).
 */
public final class TickTimingTests {

    private static final BlockPos BASE = new BlockPos(8, 2, 8);

    private TickTimingTests() {}

    // ---- Quantum Simulator ----

    @GameTest(template = TestSupport.FLOOR_9, batch = "timing_simulator_idle", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void simulatorIdle(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = simulator(helper, new BlockPos(4, 2, 4));
        TickTiming.measure(helper, new Scenario("simulator.idle", "Quantum Simulator",
                        "A simulator with nothing engaged", 10),
                simulator, () -> status(simulator), () -> {});
    }

    @GameTest(template = TestSupport.FLOOR_9, batch = "timing_simulator_furnace", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void simulatorFurnaceAtEight(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        QuantumSimulatorBlockEntity simulator = simulator(helper, at);
        helper.setBlock(at.above(), Blocks.FURNACE);
        simulator.toggleEngage(null);
        simulator.setBatchSize(8);
        helper.onEachTick(() -> {
            simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 64));
            simulator.getInventory().setStackInSlot(1, new ItemStack(Items.COAL, 64));
            clearOutputs(simulator);
        });
        TickTiming.measure(helper, new Scenario("simulator.furnace_8x", "Quantum Simulator",
                        "A furnace at 8x smelting raw iron, fed and emptied every tick", 20),
                simulator, () -> status(simulator), () -> {});
    }

    // ---- Quantimium Reactor ----

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_reactor_idle", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void reactorIdle(GameTestHelper helper) {
        com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity core = fullReactor(helper);
        helper.onEachTick(() -> TestSupport.fill(core.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario("reactor.idle", "Quantimium Reactor",
                        "Three rings, five workstations and a base's worth of stock; nothing changing", 20),
                core, () -> reactorStatus(core), () -> {});
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_reactor_streaming", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void reactorStreaming(GameTestHelper helper) {
        com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity core = fullReactor(helper);
        // A stack of cobblestone every tick: the stock always changing, so it recounts every second.
        helper.onEachTick(() -> {
            TestSupport.fill(core.getEnergyStorage());
            core.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.COBBLESTONE), 64);
        });
        TickTiming.measure(helper, new Scenario("reactor.streaming", "Quantimium Reactor",
                        "As idle, with a stack of cobblestone arriving every tick and a recount every second", 40),
                core, () -> reactorStatus(core), () -> {});
    }

    /** A three-ring Reactor with every vanilla workstation and 256 of every log, ingot, gem, dust and stone. */
    private static com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity fullReactor(GameTestHelper helper) {
        com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity core = ReactorTests.buildReactor(helper, 2);
        BlockPos centre = ReactorTests.CORE;
        net.minecraft.world.item.Item[] stations = {Items.CRAFTING_TABLE, Items.FURNACE, Items.BLAST_FURNACE,
                Items.SMOKER, Items.STONECUTTER};
        BlockPos[] bays = {centre.offset(2, 0, 1), centre.offset(-2, 0, 1), centre.offset(2, 0, -1),
                centre.offset(-2, 0, -1), centre.offset(1, 0, 2)};
        for (int i = 0; i < stations.length; i++) ReactorTests.bay(helper, bays[i], stations[i]);
        core.revalidate(helper.getLevel());
        for (String tag : java.util.List.of("minecraft:logs", "c:ingots", "c:gems", "c:dusts", "c:cobblestones",
                "c:stones", "c:sands", "c:raw_materials", "minecraft:wool", "c:dyes")) {
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse(tag)))
                    .forEach(holder -> core.take(net.neoforged.neoforge.transfer.item.ItemResource.of(holder.value()), 256));
        }
        return core;
    }

    private static String reactorStatus(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity core) {
        return core.isActive() + " active, " + core.getCounts().counts().size() + " counted, mass " + core.getLedger().mass();
    }

    // ---- Zeno Field Controller ----

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_alone", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoAlone(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        grassFloor(helper);
        ZenoFieldControllerBlockEntity controller = zeno(helper, BASE, 1, 16);
        helper.onEachTick(() -> TestSupport.fill(controller.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario("zeno.alone_r1", "Zeno Field Controller",
                        "Accelerating 16x, radius 1", 10),
                controller, () -> status(controller), restore);
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_alone_r8", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoAloneWide(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        grassFloor(helper);
        ZenoFieldControllerBlockEntity controller = zeno(helper, BASE, 8, 16);
        helper.onEachTick(() -> TestSupport.fill(controller.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario("zeno.alone_r8", "Zeno Field Controller",
                        "Accelerating 16x, radius 8", 20),
                controller, () -> status(controller), restore);
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_simulated", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoSimulatedAtEight(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        grassFloor(helper);
        QuantumSimulatorBlockEntity simulator = simulator(helper, BASE);
        zeno(helper, BASE.above(), 1, 16);
        simulator.toggleEngage(null);
        simulator.setBatchSize(8);
        helper.onEachTick(() -> TestSupport.fill(simulator.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario("zeno.simulated_8x", "Zeno Field Controller",
                        "Accelerating 16x, radius 1, in a simulator at 8x", 30),
                simulator, () -> status(simulator), restore);
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_nested", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoNestedAtSixtyFour(GameTestHelper helper) {
        nestedZeno(helper, 1, "zeno.nested_64x_r1", "Accelerating 16x, radius 1, in a simulator at 8x in another at 8x", 30);
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_nested_r8", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoNestedAtSixtyFourWide(GameTestHelper helper) {
        // Budgeted just under the 210 µs it took before the section-by-section random ticks.
        nestedZeno(helper, 8, "zeno.nested_64x_r8", "Accelerating 16x, radius 8, in a simulator at 8x in another at 8x", 200);
    }

    /**
     * A 9x9 farm of fully grown wheat round a water source, with three sugar cane beside the water,
     * under a controller accelerating 16x at radius 4 in a simulator at 8x in another at 8x. Grown
     * wheat no longer random-ticks, but the wet farmland under it does, and each farmland tick looks
     * for water nine blocks across; the cane always ticks.
     */
    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_nested_farm", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoNestedOverAFarm(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        farm(helper);
        QuantumSimulatorBlockEntity outer = simulator(helper, BASE);
        QuantumSimulatorBlockEntity inner = simulator(helper, BASE.above());
        zeno(helper, BASE.above(2), 4, 16);
        inner.setBatchSize(8);
        inner.toggleEngage(null);
        outer.toggleEngage(null);
        outer.setBatchSize(8);
        helper.onEachTick(() -> TestSupport.fill(outer.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario("zeno.nested_64x_farm", "Zeno Field Controller",
                        "Accelerating 16x, radius 4, over a grown 9x9 wheat farm and three sugar cane, "
                                + "in a simulator at 8x in another at 8x", 60),
                outer, () -> status(outer), restore);
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_zeno_alone_farm", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void zenoAloneOverAFarm(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        farm(helper);
        ZenoFieldControllerBlockEntity controller = zeno(helper, BASE, 4, 16);
        helper.onEachTick(() -> TestSupport.fill(controller.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario("zeno.alone_farm", "Zeno Field Controller",
                        "Accelerating 16x, radius 4, over a grown 9x9 wheat farm and three sugar cane", 20),
                controller, () -> status(controller), restore);
    }

    private static void nestedZeno(GameTestHelper helper, int radius, String id, String description, double budget) {
        Runnable restore = randomTickSpeed(helper, 3);
        grassFloor(helper);
        QuantumSimulatorBlockEntity outer = simulator(helper, BASE);
        QuantumSimulatorBlockEntity inner = simulator(helper, BASE.above());
        zeno(helper, BASE.above(2), radius, 16);
        inner.setBatchSize(8);
        inner.toggleEngage(null);
        outer.toggleEngage(null);
        outer.setBatchSize(8);
        helper.onEachTick(() -> TestSupport.fill(outer.getEnergyStorage()));
        TickTiming.measure(helper, new Scenario(id, "Zeno Field Controller", description, budget),
                outer, () -> status(outer), restore);
    }

    // ---- helpers ----

    /** The status as the screen names it, for the report. */
    public static String status(QuantumSimulatorBlockEntity simulator) {
        return switch (simulator.getStatusCode()) {
            case QuantumSimulatorBlockEntity.STATUS_OFFLINE -> "Offline";
            case PhantomMirrorEngine.STATUS_WORKING -> "Working";
            case PhantomMirrorEngine.STATUS_IDLE -> "Idle";
            case PhantomMirrorEngine.STATUS_INITIALIZING -> "Calibrating";
            case PhantomMirrorEngine.STATUS_NO_POWER -> "No power";
            case PhantomMirrorEngine.STATUS_OUTPUT_FULL -> "Output full";
            case PhantomMirrorEngine.STATUS_LOW_INPUT -> "Low input";
            case PhantomMirrorEngine.STATUS_STALLED -> "Stalled";
            default -> "Status " + simulator.getStatusCode();
        };
    }

    private static String status(ZenoFieldControllerBlockEntity controller) {
        return switch (controller.getStatusCode()) {
            case ZenoFieldControllerBlockEntity.STATUS_ACCELERATING -> "Accelerating";
            case ZenoFieldControllerBlockEntity.STATUS_HOLDING -> "Holding";
            default -> "No power";
        };
    }

    public static QuantumSimulatorBlockEntity simulator(GameTestHelper helper, BlockPos pos) {
        TestSupport.track(helper);
        helper.setBlock(pos, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(pos, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        return simulator;
    }

    public static void clearOutputs(QuantumSimulatorBlockEntity simulator) {
        for (int slot = QuantumSimulatorBlockEntity.OUTPUT_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END; slot++) {
            simulator.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private static ZenoFieldControllerBlockEntity zeno(GameTestHelper helper, BlockPos pos, int radius, int factor) {
        TestSupport.track(helper);
        helper.setBlock(pos, ModBlocks.ZENO_FIELD_CONTROLLER.get());
        ZenoFieldControllerBlockEntity controller = helper.getBlockEntity(pos, ZenoFieldControllerBlockEntity.class);
        controller.setMode(ZenoFieldControllerBlockEntity.MODE_ACCELERATE);
        controller.setRadius(radius);
        controller.setFactor(factor);
        return controller;
    }

    /**
     * Grass over the whole floor, so a field has blocks that do something on a random tick (spread,
     * check their light) as a lawn or a farm would, rather than bare stone that skips them all.
     */
    private static void grassFloor(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) {
            for (int z = 0; z < 17; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
        }
    }

    /**
     * Farmland under grown wheat, 9x9 round {@link #BASE}, with the water source under the machine
     * standing at its centre and sugar cane on sand on three sides of it.
     */
    private static void farm(GameTestHelper helper) {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
        BlockState wheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                helper.setBlock(new BlockPos(BASE.getX() + dx, 1, BASE.getZ() + dz), farmland);
                helper.setBlock(new BlockPos(BASE.getX() + dx, 2, BASE.getZ() + dz), wheat);
            }
        }
        helper.setBlock(new BlockPos(BASE.getX(), 1, BASE.getZ()), Blocks.WATER);
        for (BlockPos cane : new BlockPos[] {BASE.west(), BASE.east(), BASE.north()}) {
            helper.setBlock(cane.below(), Blocks.SAND);
            helper.setBlock(cane, Blocks.SUGAR_CANE);
        }
    }

    /** The game-test server turns random ticks off; this sets the default rate and returns the undo. */
    private static Runnable randomTickSpeed(GameTestHelper helper, int speed) {
        GameRules rules = helper.getLevel().getGameRules();
        int previous = rules.get(GameRules.RANDOM_TICK_SPEED);
        rules.set(GameRules.RANDOM_TICK_SPEED, speed, helper.getLevel().getServer());
        return () -> rules.set(GameRules.RANDOM_TICK_SPEED, previous, helper.getLevel().getServer());
    }
}
