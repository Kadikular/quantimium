package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.ChunkFlux;
import com.kadikular.quantimium.flux.FieldModel;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The field every chunk carries. Wiki: concepts/flux-and-anomaly.
 *
 * <p>Each test sets its own chunk's field and runs in its own batch, since neighbouring tests can share
 * a chunk.
 */
public final class FluxTests {

    private static final BlockPos MIDDLE = new BlockPos(8, 2, 8);

    private FluxTests() {}

    // covers: flux_meter.readouts
    @GameTest(template = TestSupport.FLOOR_9, batch = "flux_meter", timeoutTicks = 40)
    public static void theMeterReadsTheNewerMachines(GameTestHelper helper) {
        TestSupport.track(helper);
        BlockPos zenoPos = new BlockPos(2, 2, 2);
        BlockPos projectorPos = new BlockPos(6, 2, 2);
        BlockPos detectorPos = new BlockPos(2, 2, 6);
        helper.setBlock(zenoPos, ModBlocks.ZENO_FIELD_CONTROLLER.get());
        helper.setBlock(projectorPos, ModBlocks.DECOHERENCE_PROJECTOR.get());
        helper.setBlock(detectorPos, ModBlocks.FLUX_DETECTOR.get());
        ZenoFieldControllerBlockEntity zeno = helper.getBlockEntity(zenoPos, ZenoFieldControllerBlockEntity.class);
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(projectorPos, DecoherenceProjectorBlockEntity.class);
        zeno.setMode(ZenoFieldControllerBlockEntity.MODE_ACCELERATE);
        helper.assertValueEqual(meterKey(zeno), "item.quantimium.flux_meter.zeno.accelerate", "accelerating controller");
        zeno.setMode(ZenoFieldControllerBlockEntity.MODE_PAUSE);
        helper.assertValueEqual(meterKey(zeno), "item.quantimium.flux_meter.zeno.hold", "holding controller");
        helper.assertValueEqual(meterKey(projector), "item.quantimium.flux_meter.projector.unpowered", "empty projector");
        TestSupport.fill(projector.getEnergyStorage());
        helper.assertValueEqual(meterKey(projector), "item.quantimium.flux_meter.projector.watching", "powered projector");
        helper.assertValueEqual(meterKey(TestSupport.blockEntity(helper, detectorPos)), "item.quantimium.flux_meter.detector", "detector");
        helper.succeed();
    }

    /** The translation key of what the Flux Meter would say about {@code be}. */
    public static String meterKey(BlockEntity be) {
        if (!(be instanceof FluxMeterReadout readout)) return "none";
        MutableComponent line = readout.fluxMeterLine();
        return line != null && line.getContents() instanceof TranslatableContents contents ? contents.getKey() : "none";
    }

    // covers: flux.bands
    @GameTest(template = TestSupport.FLOOR_9, batch = "flux_bands")
    public static void bandsStartAtPowersOfTen(GameTestHelper helper) {
        double[] from = {0, 100, 1_000, 10_000, 100_000};
        FluxBand[] bands = FluxBand.values();
        for (int i = 0; i < bands.length; i++) {
            helper.assertValueEqual(FluxBand.of(from[i]), bands[i], "band at " + from[i]);
            if (i > 0) helper.assertValueEqual(FluxBand.of(from[i] - 0.01), bands[i - 1], "band just under " + from[i]);
        }
        // Read with hysteresis, a band is kept until the reading falls below 85% of its start.
        helper.assertValueEqual(FluxBand.of(900.0, FluxBand.HIGH), FluxBand.HIGH, "900 after High stays High");
        helper.assertValueEqual(FluxBand.of(840.0, FluxBand.HIGH), FluxBand.MEDIUM, "840 after High drops to Medium");
        helper.assertValueEqual(FluxBand.of(900.0, FluxBand.MEDIUM), FluxBand.MEDIUM, "900 after Medium is Medium");
        helper.succeed();
    }

    // covers: flux.drain
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_drain", timeoutTicks = 100)
    public static void loadedChunksDecay(GameTestHelper helper) {
        // Flux left nearby by earlier tests would flow in and spoil the count.
        TestSupport.clearField(helper);
        TestSupport.setField(helper, MIDDLE, 1_000.0, 0.0);
        // The field is stepped once a second; 70 ticks is three or four steps. Each loses about 2.1%
        // to decay at 1,000, and up to 4% to its four neighbours.
        helper.runAfterDelay(70, () -> {
            double flux = QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(MIDDLE));
            double most = FieldModel.decayed(1_000.0, 3);
            double least = 1_000.0 * Math.pow(1.0 - FieldModel.decayRate(1_000.0) - 4 * FieldModel.DIFFUSION, 4);
            helper.assertTrue(flux <= most && flux >= least,
                    "three or four seconds from 1,000 should leave between " + least + " and " + most + ", found " + flux);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: anomaly.coupling
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_coupling", timeoutTicks = 60)
    public static void anomalyChasesFluxWhenUncontained(GameTestHelper helper) {
        TestSupport.setField(helper, MIDDLE, 5_000.0, 0.0);
        helper.runAfterDelay(30, () -> {
            double anomaly = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(MIDDLE));
            // With nothing containing it, anomaly closes 4% of its gap to the flux each second: one or
            // two seconds in, 190 to 390.
            helper.assertTrue(anomaly > 150.0 && anomaly < 420.0,
                    "a second or two at 5,000 uncontained flux should raise anomaly to about 200-390, found " + anomaly);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: anomaly.surcharge, anomaly.leak
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_effects", timeoutTicks = 40)
    public static void anomalySurchargesAndLeaksByBand(GameTestHelper helper) {
        // setField leaves containment alone, and a neighbouring hall's can still be up: it zeroes the surcharge.
        TestSupport.clearField(helper);
        double[] anomaly = {0.0, 500.0, 5_000.0, 50_000.0, 500_000.0};
        int[] surcharge = {0, 25, 50, 100, 200};
        int[] leak = {0, 16, 40, 80, 160};
        BlockPos at = helper.absolutePos(MIDDLE);
        for (int i = 0; i < anomaly.length; i++) {
            TestSupport.setField(helper, MIDDLE, 0.0, anomaly[i]);
            helper.assertValueEqual(AnomalyEffects.surchargePercent(helper.getLevel(), at), surcharge[i],
                    "craft surcharge at " + anomaly[i] + " anomaly");
            helper.assertValueEqual(AnomalyEffects.passiveDrainFePerTick(helper.getLevel(), at), leak[i],
                    "buffer leak at " + anomaly[i] + " anomaly");
        }
        TestSupport.setField(helper, MIDDLE, 0.0, 0.0);
        helper.succeed();
    }

    // covers: flux.emit
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_emit", timeoutTicks = 40)
    public static void workEmitsFluxAcrossTheNeighbourhood(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = helper.absolutePos(MIDDLE);
        QuantumFlux.emitFromEnergy(helper.getLevel(), at, 1_000_000L);
        // 1,000 FE → 1 flux, times the field's gain: 3,000 flux into the pools of the 3×3, a fifth to
        // this chunk and a tenth to each other. No anomaly: flux drives that.
        LevelChunk chunk = helper.getLevel().getChunkAt(at);
        ChunkFlux data = chunk.getData(ModAttachments.CHUNK_FLUX);
        double total = 1_000.0 * FieldModel.EMISSION_GAIN;
        helper.assertTrue(Math.abs(data.pendingFlux() - total * 0.2) < 0.001, "this chunk's pool should hold a fifth, found " + data.pendingFlux());
        ChunkFlux next = helper.getLevel().getChunkAt(at.east(16)).getData(ModAttachments.CHUNK_FLUX);
        helper.assertTrue(Math.abs(next.pendingFlux() - total * 0.1) < 0.001, "a neighbour's pool should hold a tenth, found " + next.pendingFlux());
        helper.assertTrue(data.pendingAnomaly() == 0.0, "work adds no anomaly directly");
        TestSupport.clearField(helper);
        helper.succeed();
    }

    /** Containment's core rating in these tests: what basic containment used to hold. */
    private static final double CAPACITY = 2_000.0;

    /** Covers {@code MIDDLE}'s 3×3 with {@link #CAPACITY} each second, as a containment machine would. */
    private static void containEachSecond(GameTestHelper helper, double leak) {
        BlockPos at = helper.absolutePos(MIDDLE);
        helper.onEachTick(() -> {
            if (helper.getLevel().getGameTime() % 20L == 0L) QuantumFlux.contain(helper.getLevel(), at, CAPACITY, leak, 1);
        });
    }

    // covers: containment.shield
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_contain", timeoutTicks = 100)
    public static void containmentHoldsUpToItsCapacity(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.setField(helper, MIDDLE, 1_000.0, 0.0);
        containEachSecond(helper, FieldModel.LEAK);
        BlockPos at = helper.absolutePos(MIDDLE);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(Math.abs(QuantumFlux.chunkCapacity(helper.getLevel(), at) - CAPACITY) < 1.0,
                    "capacity should be 2,000, found " + QuantumFlux.chunkCapacity(helper.getLevel(), at));
            helper.assertTrue(QuantumFlux.chunkContained(helper.getLevel(), at), "1,000 flux should be contained");
            // Just under half: the field has had a second or two to decay and spread.
            double load = QuantumFlux.chunkLoad(helper.getLevel(), at);
            helper.assertTrue(load > 0.35 && load <= 0.5, "load should be just under 50%, found " + load);
            TestSupport.setField(helper, MIDDLE, 10_000.0, 0.0);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(QuantumFlux.chunkShielded(helper.getLevel(), at), "the chunk should still be covered at 10,000");
            helper.assertTrue(!QuantumFlux.chunkContained(helper.getLevel(), at), "but overloaded: 10,000 is past its capacity");
            helper.assertTrue(QuantumFlux.chunkOverflow(helper.getLevel(), at) > 5_000.0,
                    "the flux past capacity is its overflow, found " + QuantumFlux.chunkOverflow(helper.getLevel(), at));
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: containment.edge
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_contain_edge", timeoutTicks = 60)
    public static void containmentFadesSharplyPastItsCore(GameTestHelper helper) {
        TestSupport.clearField(helper);
        containEachSecond(helper, FieldModel.LEAK);
        BlockPos at = helper.absolutePos(MIDDLE);
        helper.runAfterDelay(25, () -> {
            double side = QuantumFlux.chunkCapacity(helper.getLevel(), at.east(16)) / CAPACITY;
            double corner = QuantumFlux.chunkCapacity(helper.getLevel(), at.east(16).south(16)) / CAPACITY;
            double two = QuantumFlux.chunkCapacity(helper.getLevel(), at.east(32)) / CAPACITY;
            double far = QuantumFlux.chunkCapacity(helper.getLevel(), at.east(32).south(32)) / CAPACITY;
            helper.assertTrue(side > 0.95 && corner > 0.85, "nearly full across its 3×3: " + side + ", " + corner);
            helper.assertTrue(Math.abs(two - 0.5) < 0.02, "half strength two chunks out: " + two);
            helper.assertTrue(far < 0.05, "next to nothing beyond: " + far);
            helper.assertTrue(QuantumFlux.chunkCapacity(helper.getLevel(), at.east(64)) == 0.0, "nothing four chunks out");
            helper.succeed();
        });
    }

    // covers: containment.leak
    @GameTest(template = TestSupport.FLOOR_17, batch = "flux_leak", timeoutTicks = 60)
    public static void containedAnomalyIsAtmosphereOnly(GameTestHelper helper) {
        // Medium anomaly in a contained chunk costs nothing: only overload is destructive.
        TestSupport.clearField(helper);
        TestSupport.setField(helper, MIDDLE, 1_000.0, 500.0);
        containEachSecond(helper, FieldModel.LEAK);
        BlockPos at = helper.absolutePos(MIDDLE);
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(AnomalyEffects.surchargePercent(helper.getLevel(), at), 0, "no surcharge while contained");
            helper.assertValueEqual(AnomalyEffects.passiveDrainFePerTick(helper.getLevel(), at), 0, "no buffer leak while contained");
            // Contained flux still couples a little: at a 3% leak, anomaly heads for 150 of 5,000.
            helper.assertTrue(Math.abs(FieldModel.anomalyTarget(5_000.0, 10_000.0, 0.03) - 150.0) < 1.0E-6, "the leak's target");
            helper.assertTrue(Math.abs(FieldModel.anomalyTarget(15_000.0, 10_000.0, 0.03) - 5_300.0) < 1.0E-6,
                    "over capacity, the overflow couples in full on top of the leak");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }
}
