package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.DebugFieldEmitterBlock;
import com.kadikular.quantimium.block.FieldMonitorBlock;
import com.kadikular.quantimium.block.entity.FieldControlBlockEntity;
import com.kadikular.quantimium.block.entity.FieldMonitorBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.flux.FieldMapSync;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.network.FieldMapPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** The Flux Maintainer, Anomaly Siphon and Field Regulator. Wiki: machines/field-control. */
public final class FieldControlTests {

    private static final BlockPos AT = new BlockPos(8, 2, 8);

    private FieldControlTests() {}

    // covers: field.maintain
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_maintain", timeoutTicks = 400)
    public static void theMaintainerRaisesFluxToItsFloorThenHolds(GameTestHelper helper) {
        // The whole neighbourhood, not just this chunk: anomaly an earlier batch left next door would
        // drift in and read as the Maintainer's own.
        TestSupport.clearField(helper);
        FieldControlBlockEntity maintainer = place(helper, ModBlocks.FLUX_MAINTAINER.get());
        maintainer.setFloor(FluxBand.MEDIUM);
        // Pure flux: an ordinary machine's emission brings a quarter as much anomaly with it. Read at the
        // first emission, before uncontained flux has had time to couple into anomaly, as it always does.
        double[] first = {-1.0, 0.0};
        helper.onEachTick(() -> {
            double flux = QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(AT));
            if (first[0] < 0.0 && flux > 0.0) {
                first[0] = flux;
                first[1] = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(AT));
            }
        });
        helper.succeedWhen(() -> {
            double flux = QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(AT));
            helper.assertTrue(flux >= FluxBand.MEDIUM.minFlux() && flux < FluxBand.HIGH.minFlux(),
                    "flux should be raised into Medium, and no further, is " + flux);
            helper.assertValueEqual(maintainer.status(), FieldControlBlockEntity.STATUS_HOLDING, "status once there");
            helper.assertTrue(first[1] < first[0] * 0.05, "raising should bring no anomaly of its own: "
                    + first[1] + " with the first " + first[0] + " flux");
            TestSupport.setField(helper, AT, 0.0, 0.0);
        });
    }

    // covers: field.siphon
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_siphon", timeoutTicks = 400)
    public static void theSiphonPullsAnomalyUnderItsCeilingAndLeavesFlux(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.setField(helper, AT, 500.0, 400.0);
        FieldControlBlockEntity siphon = place(helper, ModBlocks.ANOMALY_SIPHON.get());
        siphon.setCeiling(FieldControlBlockEntity.Ceiling.LOW);
        helper.succeedWhen(() -> {
            double anomaly = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(AT));
            // A Low ceiling holds anomaly at 80: just under Medium, clear of the edge.
            helper.assertTrue(anomaly <= FieldControlBlockEntity.Ceiling.LOW.target() + 1.0,
                    "anomaly should be pulled down to 80 and held, is " + anomaly);
            helper.assertTrue(QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(AT)) > 400.0,
                    "flux is left alone (only its natural drain)");
            IItemHandler pipe = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(AT), Direction.UP));
            helper.assertTrue(pipe != null, "fragments can be piped out");
            TestSupport.setField(helper, AT, 0.0, 0.0);
        });
    }

    // covers: field.siphon_steady
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_siphon_steady", timeoutTicks = 520)
    public static void aSiphonHoldsAWorkshopInLowWithoutFlicker(GameTestHelper helper) {
        TestSupport.clearField(helper);
        // A 10,000 FE/t workshop beside it, which would otherwise take anomaly towards High.
        BlockPos workshop = AT.east(3);
        helper.setBlock(workshop, ModBlocks.DEBUG_FIELD_EMITTER.get().defaultBlockState().setValue(DebugFieldEmitterBlock.SETTING, 3));
        FieldControlBlockEntity siphon = place(helper, ModBlocks.ANOMALY_SIPHON.get());
        siphon.setCeiling(FieldControlBlockEntity.Ceiling.LOW);
        boolean[] offAgain = {false};
        boolean[] started = {false};
        helper.onEachTick(() -> {
            TestSupport.fill(siphon.getEnergyStorage());
            if (helper.getTick() < 100) return;
            double worst = QuantumFlux.peakAnomaly(helper.getLevel(), helper.absolutePos(AT), QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(worst <= FieldControlBlockEntity.Ceiling.LOW.target() + 1.0,
                    "every chunk round it should be held at 80 or under, just inside Low: " + worst);
            boolean pulling = (siphon.status() & FieldControlBlockEntity.STATUS_PULLING) != 0;
            if (pulling) started[0] = true;
            else if (started[0]) offAgain[0] = true;
        });
        helper.runAfterDelay(500, () -> {
            helper.assertTrue(started[0] && !offAgain[0], "it should pull steadily, not switch on and off");
            helper.setBlock(workshop, Blocks.AIR);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: field.siphon_mark
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_siphon_mark", timeoutTicks = 260)
    public static void aSiphonLandsOnItsMarkAndStays(GameTestHelper helper) {
        TestSupport.clearField(helper);
        // Every chunk round it at 3,000 anomaly (High), with 5,000 flux pushing it higher, and a Medium
        // ceiling: each should come down to 800 and sit there, never swinging past it.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) TestSupport.setField(helper, AT.offset(dx * 16, 0, dz * 16), 5_000.0, 3_000.0);
        }
        FieldControlBlockEntity siphon = place(helper, ModBlocks.ANOMALY_SIPHON.get());
        siphon.setCeiling(FieldControlBlockEntity.Ceiling.MEDIUM);
        double mark = FieldControlBlockEntity.Ceiling.MEDIUM.target();
        helper.onEachTick(() -> {
            TestSupport.fill(siphon.getEnergyStorage());
            if (helper.getTick() >= 160) return;
            double here = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(AT));
            helper.assertTrue(here >= mark * 0.95, "it should never pull past its mark: " + here);
        });
        helper.runAfterDelay(160, () -> {
            double worst = QuantumFlux.peakAnomaly(helper.getLevel(), helper.absolutePos(AT), QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(worst <= mark + 1.0, "every chunk should be down to 800: " + worst);
            // Clear holds it at nothing.
            siphon.setCeiling(FieldControlBlockEntity.Ceiling.CLEAR);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 165, "not yet");
            double worst = QuantumFlux.peakAnomaly(helper.getLevel(), helper.absolutePos(AT), QuantumFlux.SOURCE_RADIUS);
            // Nothing, bar a trace drifting in each second from the uncontained chunks just outside.
            helper.assertTrue(worst < 10.0, "Clear should take it to nearly nothing: " + worst);
            TestSupport.clearField(helper);
        });
    }

    // covers: field.basic_siphon
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_basic_siphon", timeoutTicks = 1720)
    public static void aBasicSiphonHoldsASmallWorkshopButNotABigOne(GameTestHelper helper) {
        TestSupport.clearField(helper);
        // 1,000 FE/t beside it, which would take anomaly to Medium: it holds every chunk round it at 80.
        BlockPos workshop = AT.east(3);
        helper.setBlock(workshop, ModBlocks.DEBUG_FIELD_EMITTER.get().defaultBlockState().setValue(DebugFieldEmitterBlock.SETTING, 2));
        FieldControlBlockEntity siphon = place(helper, ModBlocks.BASIC_ANOMALY_SIPHON.get());
        helper.onEachTick(() -> TestSupport.fill(siphon.getEnergyStorage()));
        double mark = FieldControlBlockEntity.Ceiling.LOW.target();
        helper.runAfterDelay(400, () -> {
            double worst = QuantumFlux.peakAnomaly(helper.getLevel(), helper.absolutePos(AT), QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(worst <= mark + 1.0, "a small workshop should be held at the top of Low: " + worst);
            helper.assertValueEqual(siphon.ceiling(), FieldControlBlockEntity.Ceiling.LOW, "its mark is fixed");
            siphon.pressButton(FieldControlBlockEntity.BUTTON_CEILING);
            helper.assertValueEqual(siphon.ceiling(), FieldControlBlockEntity.Ceiling.LOW, "and has no tab to change it");
            // Ten times the work is more than it can pull: that is a Siphon's job.
            helper.setBlock(workshop, ModBlocks.DEBUG_FIELD_EMITTER.get().defaultBlockState().setValue(DebugFieldEmitterBlock.SETTING, 3));
        });
        // A minute for the bigger field to build.
        helper.runAfterDelay(1700, () -> {
            double worst = QuantumFlux.peakAnomaly(helper.getLevel(), helper.absolutePos(AT), QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(worst > FluxBand.MEDIUM.minFlux(), "a 10k FE/t workshop should overwhelm it: " + worst);
            helper.setBlock(workshop, Blocks.AIR);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: field.suppressor
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_suppressor", timeoutTicks = 420)
    public static void aSuppressorHoldsFluxUnderItsCeiling(GameTestHelper helper) {
        TestSupport.clearField(helper);
        // 1,000 FE/t beside it would hold the 3×3 at Medium flux; a Low flux ceiling keeps it at 80.
        BlockPos workshop = AT.east(3);
        helper.setBlock(workshop, ModBlocks.DEBUG_FIELD_EMITTER.get().defaultBlockState().setValue(DebugFieldEmitterBlock.SETTING, 2));
        FieldControlBlockEntity suppressor = place(helper, ModBlocks.FLUX_SUPPRESSOR.get());
        helper.onEachTick(() -> TestSupport.fill(suppressor.getEnergyStorage()));
        helper.assertValueEqual(suppressor.ceiling(), FieldControlBlockEntity.Ceiling.LOW, "default flux ceiling");
        helper.runAfterDelay(400, () -> {
            double worst = QuantumFlux.peakFlux(helper.getLevel(), helper.absolutePos(AT), QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(worst <= FieldControlBlockEntity.Ceiling.LOW.target() + 1.0, "flux round it should be held at 80: " + worst);
            IItemHandler pipe = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(AT), Direction.UP));
            helper.assertTrue(pipe == null, "it makes no fragments, so it has nothing to pipe out");
            helper.setBlock(workshop, Blocks.AIR);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: field.map_overlay
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_map_overlay")
    public static void worldMapsAreSentEveryChunkHoldingAField(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.setField(helper, AT, 2_000.0, 500.0);
        ChunkPos here = ChunkPos.containing(helper.absolutePos(AT));
        FieldMapPayload map = FieldMapSync.snapshot(helper.getLevel(), here);
        int found = -1;
        for (int i = 0; i < map.chunks().length; i++) {
            ChunkPos pos = ChunkPos.unpack(map.chunks()[i]);
            helper.assertTrue(map.flux()[i] >= 1.0f || map.anomaly()[i] >= 1.0f, "only chunks holding a field are sent: " + pos);
            if (pos.equals(here)) found = i;
        }
        helper.assertTrue(found >= 0, "the charged chunk should be on the map");
        helper.assertTrue(Math.abs(map.flux()[found] - 2_000.0f) < 1.0f, "its flux: " + map.flux()[found]);
        helper.assertTrue(Math.abs(map.anomaly()[found] - 500.0f) < 1.0f, "its anomaly: " + map.anomaly()[found]);
        helper.assertTrue(map.load()[found] < 0.0f, "no containment covers it");
        TestSupport.clearField(helper);
        helper.succeed();
    }

    // covers: field.monitor
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_monitor", timeoutTicks = 60)
    public static void theMonitorRaisesTheAlarmForWhatItWatches(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.FIELD_MONITOR.get());
        FieldMonitorBlockEntity monitor = helper.getBlockEntity(at, FieldMonitorBlockEntity.class);
        monitor.setWatch(FieldMonitorBlockEntity.Watch.ANOMALY_MEDIUM);
        helper.assertValueEqual(monitor.alerting(), 0, "a quiet field raises nothing");
        helper.assertTrue(!helper.getBlockState(at).getValue(FieldMonitorBlock.ALERT), "no alarm");
        // Medium anomaly, uncontained, in its own chunk and the next one.
        TestSupport.setField(helper, at, 0.0, 500.0);
        TestSupport.setField(helper, at.east(16), 0.0, 500.0);
        monitor.read(helper.getLevel());
        helper.assertValueEqual(monitor.alerting(), 2, "two chunks of uncontained Medium anomaly");
        helper.assertTrue(helper.getBlockState(at).getValue(FieldMonitorBlock.ALERT), "the alarm is up");
        helper.assertValueEqual(helper.getBlockState(at).getSignal(helper.getLevel(), helper.absolutePos(at), Direction.WEST), 15,
                "it gives a full redstone signal");
        helper.assertValueEqual(monitor.comparatorSignal(), 2, "a comparator reads how many chunks");
        // Watching for High instead, Medium is no alarm.
        monitor.setWatch(FieldMonitorBlockEntity.Watch.ANOMALY_HIGH);
        helper.assertValueEqual(monitor.alerting(), 0, "Medium is under a High watch");
        TestSupport.clearField(helper);
        helper.succeed();
    }

    // covers: field.regulator
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_regulator", timeoutTicks = 60)
    public static void theRegulatorDoesBothForLess(GameTestHelper helper) {
        TestSupport.setField(helper, AT, 0.0, 0.0);
        FieldControlBlockEntity regulator = place(helper, ModBlocks.FIELD_REGULATOR.get());
        // A Critical floor keeps it raising flat out, or nearly: its own first flux, still easing in,
        // eases it off by a percent or two. A High ceiling keeps its pull out of the count.
        regulator.setFloor(FluxBand.CRITICAL);
        regulator.setCeiling(FieldControlBlockEntity.Ceiling.HIGH);
        int[] energy = new int[1];
        // The first read is on a tenth tick; measure ten ticks of raising after it.
        helper.runAfterDelay(12, () -> energy[0] = regulator.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(22, () -> {
            int spent = energy[0] - regulator.getEnergyStorage().getEnergyStored();
            int full = 10 * (int) Math.round(FieldControlBlockEntity.RAISE_FE_PER_TICK * 0.8);
            helper.assertTrue(spent <= full && spent >= full * 0.95,
                    "FE over ten ticks of raising, at 0.8 of the Maintainer's: " + spent + " of " + full);
            helper.assertValueEqual(regulator.status(), FieldControlBlockEntity.STATUS_RAISING, "status");
            TestSupport.setField(helper, AT, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: field.simulator
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_simulator", timeoutTicks = 300)
    public static void aSimulatedMaintainerRaisesForEveryCopy(GameTestHelper helper) {
        TestSupport.setField(helper, AT, 0.0, 0.0);
        QuantumSimulatorBlockEntity simulator = simulatorUnder(helper, ModBlocks.FLUX_MAINTAINER.get(), FluxBand.CRITICAL);
        // A Critical floor, so it is still raising flat out while the billing is measured: nearer a
        // Medium target it eases off within a second, since the simulator's own upkeep flux counts too.
        int[] energy = new int[1];
        // Each copy draws the Maintainer's 10,000 FE/t; the simulator bills all eight (and its overhead).
        helper.runAfterDelay(12, () -> energy[0] = simulator.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(22, () -> {
            int spent = energy[0] - simulator.getEnergyStorage().getEnergyStored();
            helper.assertTrue(spent >= 10 * 8 * FieldControlBlockEntity.RAISE_FE_PER_TICK,
                    "eight copies raising should be billed eight times over, spent " + spent);
        });
        helper.succeedWhen(() -> {
            double flux = QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(AT));
            helper.assertTrue(flux >= FluxBand.CRITICAL.minFlux() && flux < FluxBand.SINGULARITY.minFlux(),
                    "flux should be raised into Critical, and no further, is " + flux);
            helper.assertValueEqual(simulator.getStatusCode(), PhantomMirrorEngine.STATUS_WORKING, "simulator status");
            TestSupport.setField(helper, AT, 0.0, 0.0);
        });
    }

    // covers: field.simulator
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_simulator_siphon", timeoutTicks = 900)
    public static void aSimulatedSiphonPullsForEveryCopyAndIsPaidOnce(GameTestHelper helper) {
        // 30,000 in each of the 9 chunks it pulls from: 270,000 in all, about 34,000 for each of eight
        // copies. Some decays or spreads before it is pulled, but each copy still banks a fragment
        // (24,000) and not two. Multiplying the pull and the fragments both would make 64 of them.
        TestSupport.clearField(helper);
        neighbourhood(helper, 30_000.0);
        QuantumSimulatorBlockEntity simulator = simulatorUnder(helper, ModBlocks.ANOMALY_SIPHON.get());
        helper.onEachTick(() -> TestSupport.fill(simulator.getEnergyStorage()));
        helper.succeedWhen(() -> {
            double anomaly = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(AT));
            // Held under where the pull starts again. The simulator's own upkeep brings a little flux, which
            // couples back into anomaly, so it need not stay under where the pull stops.
            helper.assertTrue(anomaly < FluxBand.MEDIUM.minFlux(),
                    "anomaly should be held under the ceiling, is " + anomaly);
            int fragments = 0;
            for (int slot = QuantumSimulatorBlockEntity.OUTPUT_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END; slot++) {
                ItemStack stack = simulator.getInventory().getStackInSlot(slot);
                if (stack.is(ModItems.ANOMALY_FRAGMENT.get())) fragments += stack.getCount();
            }
            helper.assertValueEqual(fragments, 8, "fragments for 270,000 anomaly at 8x");
            helper.assertValueEqual(simulator.getStatusCode(), PhantomMirrorEngine.STATUS_WORKING, "simulator status");
            neighbourhood(helper, 0.0);
        });
    }

    // covers: field.power
    @GameTest(template = TestSupport.FLOOR_17, batch = "field_power", timeoutTicks = 40)
    public static void withoutPowerItSaysSo(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.track(helper);
        helper.setBlock(AT, ModBlocks.FLUX_MAINTAINER.get());
        FieldControlBlockEntity maintainer = helper.getBlockEntity(AT, FieldControlBlockEntity.class);
        helper.runAfterDelay(15, () -> {
            helper.assertValueEqual(maintainer.status(), FieldControlBlockEntity.STATUS_NO_POWER, "status");
            helper.assertTrue(QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(AT)) == 0.0, "nothing raised");
            helper.succeed();
        });
    }

    // covers: field.setpoints
    @GameTest(template = TestSupport.FLOOR_9, batch = "field_setpoints", timeoutTicks = 20)
    public static void theTabsStepThroughTheBands(GameTestHelper helper) {
        TestSupport.track(helper);
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.FIELD_REGULATOR.get());
        FieldControlBlockEntity regulator = helper.getBlockEntity(at, FieldControlBlockEntity.class);
        helper.assertValueEqual(regulator.floor(), FluxBand.MEDIUM, "default floor");
        helper.assertValueEqual(regulator.ceiling(), FieldControlBlockEntity.Ceiling.LOW, "default ceiling");
        regulator.pressButton(FieldControlBlockEntity.BUTTON_FLOOR);
        regulator.pressButton(FieldControlBlockEntity.BUTTON_FLOOR);
        helper.assertValueEqual(regulator.floor(), FluxBand.CRITICAL, "floor after two steps");
        regulator.pressButton(FieldControlBlockEntity.BUTTON_FLOOR);
        helper.assertValueEqual(regulator.floor(), FluxBand.MEDIUM, "floor wraps round");
        regulator.pressButton(FieldControlBlockEntity.BUTTON_CEILING);
        helper.assertValueEqual(regulator.ceiling(), FieldControlBlockEntity.Ceiling.MEDIUM, "ceiling after a step");
        // A Maintainer has no ceiling to step.
        helper.setBlock(at, ModBlocks.FLUX_MAINTAINER.get());
        FieldControlBlockEntity maintainer = helper.getBlockEntity(at, FieldControlBlockEntity.class);
        maintainer.pressButton(FieldControlBlockEntity.BUTTON_CEILING);
        helper.assertValueEqual(maintainer.ceiling(), FieldControlBlockEntity.Ceiling.LOW, "a Maintainer's ceiling does not move");
        helper.succeed();
    }

    /** Sets anomaly (and no flux) in each chunk of the 3x3 round {@code AT}. */
    private static void neighbourhood(GameTestHelper helper, double anomaly) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) TestSupport.setField(helper, AT.offset(dx * 16, 0, dz * 16), 0.0, anomaly);
        }
    }

    /** A simulator at {@code AT} running {@code block} (Medium floor, Low ceiling) at 8x, one up. */
    private static QuantumSimulatorBlockEntity simulatorUnder(GameTestHelper helper, Block block) {
        return simulatorUnder(helper, block, FluxBand.MEDIUM);
    }

    /** As {@link #simulatorUnder(GameTestHelper, Block)}, its floor set to {@code floor} before it is taken in. */
    private static QuantumSimulatorBlockEntity simulatorUnder(GameTestHelper helper, Block block, FluxBand floor) {
        TestSupport.track(helper);
        helper.setBlock(AT, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(AT, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        helper.setBlock(AT.above(), block);
        FieldControlBlockEntity control = helper.getBlockEntity(AT.above(), FieldControlBlockEntity.class);
        if (control.kind().raisesFlux()) control.setFloor(floor);
        simulator.toggleEngage(null);
        simulator.setBatchSize(8);
        return simulator;
    }

    private static FieldControlBlockEntity place(GameTestHelper helper, Block block) {
        TestSupport.track(helper);
        helper.setBlock(AT, block);
        FieldControlBlockEntity control = helper.getBlockEntity(AT, FieldControlBlockEntity.class);
        TestSupport.fill(control.getEnergyStorage());
        return control;
    }
}
