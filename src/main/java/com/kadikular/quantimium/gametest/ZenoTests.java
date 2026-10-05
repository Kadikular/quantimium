package com.kadikular.quantimium.gametest;

import net.minecraft.network.chat.Component;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.zeno.ZenoFields;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Zeno Field Controller. Sugar cane is the witness: every random tick it gets adds one to its
 * age, and at 15 it grows a block taller, so whether it moved at all says whether it was ticked.
 * See wiki: machines/zeno-field-controller.
 */
public final class ZenoTests {

    private static final BlockPos CONTROLLER = new BlockPos(8, 2, 8);

    private ZenoTests() {}

    // covers: zeno.hold
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_hold", timeoutTicks = 120)
    public static void aHeldFieldGetsNoRandomTicks(GameTestHelper helper) {
        ZenoFieldControllerBlockEntity controller = controller(helper, ZenoFieldControllerBlockEntity.MODE_PAUSE, 2, 2);
        BlockPos inside = new BlockPos(9, 3, 9);
        BlockPos outside = new BlockPos(14, 3, 14);
        cane(helper, inside);
        cane(helper, outside);
        // Vanilla only random-ticks chunks near a player, and gives a block one every ~20 minutes on
        // average. This batch runs alone, so it turns the rate right up to make the difference plain.
        TestSupport.player(helper, new BlockPos(2, 2, 2));
        // Random ticks come before block entities in a server tick, so the field goes up first.
        Runnable[] restore = new Runnable[1];
        helper.runAfterDelay(2, () -> restore[0] = randomTickSpeed(helper, 4096));
        helper.runAfterDelay(42, () -> {
            restore[0].run();
            helper.assertTrue(ZenoFields.isPaused(helper.getLevel(), helper.absolutePos(inside)), "the cane should be in the field");
            helper.assertTrue(grew(helper, outside), "cane outside the field should have grown");
            helper.assertTrue(!grew(helper, inside), "cane inside a held field should not have been ticked");
            helper.assertValueEqual(controller.getStatusCode(), ZenoFieldControllerBlockEntity.STATUS_HOLDING, "status");
            helper.succeed();
        });
    }

    // covers: zeno.hold_cost
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_hold_cost", timeoutTicks = 60)
    public static void holdingCostsTenFePerBlockOfRadius(GameTestHelper helper) {
        TestSupport.clearField(helper);
        ZenoFieldControllerBlockEntity controller = controller(helper, ZenoFieldControllerBlockEntity.MODE_PAUSE, 4, 2);
        int[] energy = new int[1];
        helper.runAfterDelay(5, () -> energy[0] = controller.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(ZenoFieldControllerBlockEntity.pauseCost(4), 40, "FE/t at radius 4");
            helper.assertValueEqual(energy[0] - controller.getEnergyStorage().getEnergyStored(), 20 * 40, "FE over 20 ticks");
            helper.succeed();
        });
    }

    // covers: zeno.accelerate, zeno.accelerate_cost
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_accelerate", timeoutTicks = 260)
    public static void acceleratingAddsTicksAtTheFactorForAFlatCost(GameTestHelper helper) {
        TestSupport.clearField(helper);
        Runnable restore = randomTickSpeed(helper, 3);
        ZenoFieldControllerBlockEntity controller = controller(helper, ZenoFieldControllerBlockEntity.MODE_ACCELERATE, 2, 4);
        int[] energy = new int[1];
        long[] ticks = new long[1];
        helper.runAfterDelay(5, () -> {
            energy[0] = controller.getEnergyStorage().getEnergyStored();
            ticks[0] = controller.extraTicksDone();
        });
        helper.runAfterDelay(205, () -> {
            restore.run();
            // Radius 2 at 4x: vanilla's 3 random ticks per section per tick, 3 extra of them, over
            // 125 of the section's 4096 blocks.
            double expected = ZenoFieldControllerBlockEntity.extraTicksPerTick(2, 4, 3) * 200;
            long done = controller.extraTicksDone() - ticks[0];
            helper.assertTrue(Math.abs(done - expected) <= 1.5, "extra ticks in 200 ticks: " + done + ", expected about " + expected);
            // 45 FE × 3 steps × half of radius 2: 135 FE/t, however many ticks that bought.
            helper.assertValueEqual(ZenoFieldControllerBlockEntity.accelerateCost(2, 4), 135, "FE/t at radius 2, 4x");
            helper.assertValueEqual(ZenoFieldControllerBlockEntity.accelerateCost(8, 16), 2_700, "FE/t at radius 8, 16x");
            helper.assertValueEqual(energy[0] - controller.getEnergyStorage().getEnergyStored(), 200 * 135, "FE over 200 ticks");
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: zeno.cap
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_cap", timeoutTicks = 200)
    public static void theConfiguredCapLimitsExtraTicks(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        int previousCap = Config.ZENO_MAX_EXTRA_TICKS.get();
        Config.ZENO_MAX_EXTRA_TICKS.set(5);
        ZenoFieldControllerBlockEntity controller = controller(helper, ZenoFieldControllerBlockEntity.MODE_ACCELERATE, 8, 16);
        long[] ticks = new long[1];
        helper.runAfterDelay(5, () -> ticks[0] = controller.extraTicksDone());
        helper.runAfterDelay(105, () -> {
            Config.ZENO_MAX_EXTRA_TICKS.set(previousCap);
            restore.run();
            // Uncapped, radius 8 at 16x would add about 54 a tick.
            helper.assertValueEqual(controller.extraTicksDone() - ticks[0], 5L * 100, "extra ticks in 100 ticks, capped at 5");
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: zeno.ignored
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_ignored", timeoutTicks = 700)
    public static void farmlandGetsNoExtraTicks(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        // A wet floor of farmland with no water in reach: every tick it got would dry it out.
        BlockState wet = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
        List<BlockPos> floor = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            // Not under the controller: farmland under a solid block turns to dirt, ticks or no ticks.
            for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) floor.add(new BlockPos(8 + dx, 1, 8 + dz));
        }
        floor.forEach(pos -> helper.setBlock(pos, wet));
        ZenoFieldControllerBlockEntity controller = controller(helper, ZenoFieldControllerBlockEntity.MODE_ACCELERATE, 1, 16);
        helper.runAfterDelay(600, () -> {
            restore.run();
            // About 0.3 extra ticks a tick for 600 ticks, a third of them on the floor.
            helper.assertTrue(controller.extraTicksDone() >= 100, "the field should have ticked: " + controller.extraTicksDone());
            // The game-test chunks are block-ticked whether or not a player is near (they were not before 26.1),
            // so vanilla's own random ticks, about three or four over the whole floor, dry a little of it. The
            // extra ticks would dry all of it: fifty-odd of them land on it, and each takes a point of moisture.
            int lost = 0;
            for (BlockPos pos : floor) {
                BlockState state = helper.getBlockState(pos);
                lost += state.is(Blocks.FARMLAND) ? FarmlandBlock.MAX_MOISTURE - state.getValue(FarmlandBlock.MOISTURE)
                        : FarmlandBlock.MAX_MOISTURE + 1;
            }
            helper.assertTrue(lost <= 16, "farmland dried by " + lost + " points; only vanilla's own ticks should have reached it");
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: zeno.slowed
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_slowed", timeoutTicks = 700)
    public static void slowedBlocksGetTheirShare(GameTestHelper helper) {
        Runnable restore = randomTickSpeed(helper, 3);
        // With the share at nothing, grass in the field never gets to spread onto the dirt round it.
        Config.ZENO_SLOWED_CHANCE.set(0.0);
        // Vanilla random ticks reach these chunks too and spread a little grass of their own, so a single
        // spot can't be asserted. Zeno's ticks would be fifteen times vanilla's, so count the spread over
        // a patch instead: 8 seeds on 41 dirt, vanilla manages a couple, Zeno would manage most of them.
        List<BlockPos> dirt = new ArrayList<>();
        for (int x = 5; x <= 11; x++) {
            for (int z = 5; z <= 11; z++) {
                BlockPos pos = new BlockPos(x, 1, z);
                boolean seed = (x == 5 || x == 8 || x == 11) && (z == 5 || z == 8 || z == 11) && !(x == 8 && z == 8);
                helper.setBlock(pos, seed ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                if (!seed) dirt.add(pos);
            }
        }
        ZenoFieldControllerBlockEntity controller = controller(helper, ZenoFieldControllerBlockEntity.MODE_ACCELERATE, 3, 16);
        helper.runAfterDelay(600, () -> {
            Config.ZENO_SLOWED_CHANCE.set(0.125);
            restore.run();
            helper.assertTrue(controller.extraTicksDone() >= 100, "the field should have ticked: " + controller.extraTicksDone());
            int spread = 0;
            for (BlockPos pos : dirt) {
                if (helper.getBlockState(pos).is(Blocks.GRASS_BLOCK)) spread++;
            }
            helper.assertTrue(spread <= SLOWED_SPREAD_LIMIT, "grass spread onto " + spread + " of " + dirt.size()
                    + " dirt blocks; vanilla alone manages a few, Zeno ticks would manage most");
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: zeno.accelerate, zeno.hold_wins
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_both", timeoutTicks = 900)
    public static void acceleratedCaneGrowsButAHeldOneDoesNot(GameTestHelper helper) {
        TestSupport.clearField(helper);
        Runnable restore = randomTickSpeed(helper, 3);
        ZenoFieldControllerBlockEntity accel = controller(helper, ZenoFieldControllerBlockEntity.MODE_ACCELERATE, 1, 16);
        // A holding controller at the far corner covers the second cane only.
        controller(helper, new BlockPos(10, 2, 10), ZenoFieldControllerBlockEntity.MODE_PAUSE, 1, 2);
        BlockPos sped = new BlockPos(7, 3, 7);
        BlockPos held = new BlockPos(9, 3, 9);
        cane(helper, sped);
        cane(helper, held);
        helper.runAfterDelay(800, () -> {
            restore.run();
            helper.assertTrue(grew(helper, sped), "cane in the accelerated field should have been ticked; "
                    + helper.getBlockState(sped) + " below " + helper.getBlockState(sped.below()) + ", extra ticks " + accel.extraTicksDone());
            helper.assertTrue(!grew(helper, held), "cane also inside a held field should not have been");
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: zeno.simulator
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_simulator", timeoutTicks = 900, padding = 32)
    public static void aSimulatedControllerStillSpeedsUpItsField(GameTestHelper helper) {
        // Flux or anomaly left nearby adds a surcharge to the energy spent, which is what this measures.
        TestSupport.clearField(helper);
        Runnable restore = randomTickSpeed(helper, 3);
        TestSupport.track(helper);
        helper.setBlock(CONTROLLER, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(CONTROLLER, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        helper.setBlock(CONTROLLER.above(), ModBlocks.ZENO_FIELD_CONTROLLER.get());
        ZenoFieldControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER.above(), ZenoFieldControllerBlockEntity.class);
        controller.setMode(ZenoFieldControllerBlockEntity.MODE_ACCELERATE);
        controller.setRadius(1);
        controller.setFactor(16);
        simulator.toggleEngage(null);
        simulator.setBatchSize(8);
        // The field centres on the controller, now the simulator's field block, one up.
        BlockPos cane = new BlockPos(8, 3, 7);
        cane(helper, cane);
        int before = simulator.getEnergyStorage().getEnergyStored();
        helper.runAfterDelay(800, () -> {
            restore.run();
            helper.assertBlockPresent(ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get(), CONTROLLER.above());
            helper.assertTrue(grew(helper, cane), "cane beside a simulated controller should have been ticked, status "
                    + simulator.getStatusCode());
            // It crafts nothing, so it has no cycle to calibrate: it reads as working, not "Calibrating".
            helper.assertValueEqual(simulator.getStatusCode(), PhantomMirrorEngine.STATUS_WORKING, "simulator status");
            // Eight copies' extra ticks at 50 FE each, plus the simulator's 20% overhead, for ~800 ticks.
            double perTick = ZenoFieldControllerBlockEntity.accelerateCost(1, 16) * 8 * 1.2;
            double spent = before - simulator.getEnergyStorage().getEnergyStored();
            helper.assertTrue(spent > perTick * 800 * 0.8 && spent < perTick * 800 * 1.2,
                    "the simulator should pay for eight copies: spent " + (long) spent + ", expected about "
                            + (long) (perTick * 800));
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: zeno.simulator_nested
    @GameTest(template = TestSupport.FLOOR_17, batch = "zeno_nested", timeoutTicks = 1100, padding = 32)
    public static void aSimulatorInASimulatorMultipliesAndKeepsRunning(GameTestHelper helper) {
        TestSupport.clearField(helper);
        Runnable restore = randomTickSpeed(helper, 3);
        TestSupport.track(helper);
        // Inner simulator on the outer one, controller on top; the inner takes the controller, then
        // the outer takes the inner, field and all.
        BlockPos innerPos = CONTROLLER.above();
        BlockPos zenoPos = CONTROLLER.above(2);
        helper.setBlock(CONTROLLER, ModBlocks.QUANTUM_SIMULATOR.get());
        helper.setBlock(innerPos, ModBlocks.QUANTUM_SIMULATOR.get());
        helper.setBlock(zenoPos, ModBlocks.ZENO_FIELD_CONTROLLER.get());
        QuantumSimulatorBlockEntity outer = helper.getBlockEntity(CONTROLLER, QuantumSimulatorBlockEntity.class);
        QuantumSimulatorBlockEntity inner = helper.getBlockEntity(innerPos, QuantumSimulatorBlockEntity.class);
        ZenoFieldControllerBlockEntity controller = helper.getBlockEntity(zenoPos, ZenoFieldControllerBlockEntity.class);
        controller.setMode(ZenoFieldControllerBlockEntity.MODE_ACCELERATE);
        controller.setRadius(1);
        controller.setFactor(16);
        inner.setBatchSize(4);
        TestSupport.fill(inner.getEnergyStorage());
        inner.toggleEngage(null);
        TestSupport.fill(outer.getEnergyStorage());
        outer.toggleEngage(null);
        outer.setBatchSize(4);

        // 4x within 4x: sixteen copies, each billed with both simulators' 20% overhead.
        double perTick = ZenoFieldControllerBlockEntity.accelerateCost(1, 16) * 16 * 1.2 * 1.2;
        int[] energy = new int[2];
        helper.runAfterDelay(100, () -> energy[0] = outer.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(300, () -> {
            energy[1] = outer.getEnergyStorage().getEnergyStored();
            double spent = energy[0] - energy[1];
            helper.assertTrue(spent > perTick * 200 * 0.75 && spent < perTick * 200 * 1.25,
                    "the outer simulator should pay for sixteen copies: spent " + (long) spent
                            + " over 200 ticks, expected about " + (long) (perTick * 200));
        });
        // Past the 600-tick wind-down that used to park it, a newly planted cane still grows.
        BlockPos cane = new BlockPos(7, 4, 8);
        helper.runAfterDelay(700, () -> cane(helper, cane));
        helper.runAfterDelay(900, () -> {
            restore.run();
            helper.assertTrue(grew(helper, cane), "a cane planted after 700 ticks should still be sped up, status "
                    + outer.getStatusCode());
            TestSupport.setField(helper, CONTROLLER, 0.0, 0.0);
            helper.succeed();
        });
    }

    // ---- helpers ----

    /** Most of the slowed test's dirt grass may spread onto with the share at nothing: vanilla's ticks alone. */
    private static final int SLOWED_SPREAD_LIMIT = 12;

    /** The game-test server turns random ticks off; each test sets the rate it needs and puts it back. */
    private static Runnable randomTickSpeed(GameTestHelper helper, int speed) {
        GameRules rules = helper.getLevel().getGameRules();
        int previous = rules.get(GameRules.RANDOM_TICK_SPEED);
        rules.set(GameRules.RANDOM_TICK_SPEED, speed, helper.getLevel().getServer());
        return () -> rules.set(GameRules.RANDOM_TICK_SPEED, previous, helper.getLevel().getServer());
    }

    private static ZenoFieldControllerBlockEntity controller(GameTestHelper helper, int mode, int radius, int factor) {
        return controller(helper, CONTROLLER, mode, radius, factor);
    }

    private static ZenoFieldControllerBlockEntity controller(GameTestHelper helper, BlockPos pos, int mode, int radius, int factor) {
        TestSupport.track(helper);
        // Anomaly left by an earlier test on this spot adds a surcharge to every cost measured here.
        TestSupport.clearField(helper);
        helper.setBlock(pos, ModBlocks.ZENO_FIELD_CONTROLLER.get());
        ZenoFieldControllerBlockEntity controller = helper.getBlockEntity(pos, ZenoFieldControllerBlockEntity.class);
        controller.setMode(mode);
        controller.setRadius(radius);
        controller.setFactor(factor);
        TestSupport.fill(controller.getEnergyStorage());
        return controller;
    }

    /** Sugar cane at age 0 on sand, with water beside the sand so it can stand. */
    private static void cane(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.SAND);
        helper.setBlock(pos.below().north(), Blocks.WATER);
        helper.setBlock(pos, Blocks.SUGAR_CANE);
    }

    /** Aged or grew taller: it has had at least one random tick. */
    private static boolean grew(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        if (!state.is(Blocks.SUGAR_CANE)) return true;
        return state.getValue(SugarCaneBlock.AGE) > 0 || helper.getBlockState(pos.above()).is(Blocks.SUGAR_CANE);
    }
}
