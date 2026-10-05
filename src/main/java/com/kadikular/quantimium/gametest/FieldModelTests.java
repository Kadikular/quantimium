package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.DebugFieldEmitterBlock;
import com.kadikular.quantimium.block.entity.DebugFieldEmitterBlockEntity;
import com.kadikular.quantimium.flux.ChunkFlux;
import com.kadikular.quantimium.flux.FieldModel;
import com.kadikular.quantimium.flux.FieldTrend;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Field Model 2.0 (game plan workstream A). Each test runs the field second by second itself, inside
 * one game tick so the server's own step never lands in between, and clears it after. A "second" here is one call of {@link FieldModel#step}.
 */
public final class FieldModelTests {

    private static final BlockPos SOURCE = new BlockPos(8, 2, 8);
    /** Chunks out from the source that each test clears and steps. */
    private static final int REACH = 8;

    private FieldModelTests() {}

    // covers: field2.pool
    @GameTest(template = TestSupport.FLOOR_17, batch = "field2_pool", timeoutTicks = 40)
    public static void aBurstEasesIntoTheField(GameTestHelper helper) {
        withField(helper, field -> {
            QuantumFlux.emit(field.level, field.source, 1000.0);
            ChunkFlux data = field.data(0, 0);
            double pooled = 1000.0 * FieldModel.EMISSION_GAIN * QuantumFlux.share(0, 0, 1);
            double ringPooled = 1000.0 * FieldModel.EMISSION_GAIN * QuantumFlux.share(1, 1, 1);
            helper.assertTrue(data.flux() == 0.0, "an emission goes into the pool first, not the field: " + data.flux());
            helper.assertTrue(Math.abs(data.pendingFlux() - pooled) < 1.0E-6, "the centre's pool should hold its share: " + data.pendingFlux());
            helper.assertTrue(Math.abs(field.data(1, 1).pendingFlux() - ringPooled) < 1.0E-6,
                    "each chunk round it should pool its share: " + field.data(1, 1).pendingFlux());
            helper.assertTrue(field.data(2, 0).pendingFlux() == 0.0, "nothing lands two chunks out");
            helper.assertTrue(data.pendingAnomaly() == 0.0, "a craft adds no anomaly of its own: flux drives it");
            field.step();
            helper.assertTrue(data.flux() > pooled * 0.05 && data.flux() < pooled * 0.15,
                    "about a tenth should be in after a second: " + data.flux());
            for (int i = 0; i < 60; i++) field.step();
            helper.assertTrue(data.pendingFlux() < 5.0, "the pool should be nearly empty after a minute: " + data.pendingFlux());
        });
    }

    // covers: field2.steady
    @GameTest(template = TestSupport.FLOOR_17, batch = "field2_steady", timeoutTicks = 40)
    public static void aSteadySourceSettlesWithoutFlickerOrOvershoot(GameTestHelper helper) {
        withField(helper, field -> {
            // 15 flux a second: in the classic model this pins a field to the Medium/High edge.
            double previous = 0.0;
            FluxBand settled = null;
            for (int second = 0; second < 900; second++) {
                QuantumFlux.emit(field.level, field.source, 15.0);
                field.step();
                double now = field.data(0, 0).flux();
                helper.assertTrue(now >= previous - 1.0E-9,
                        "a steady source should only ever raise its field (no overshoot): second " + second + ", " + previous + " → " + now);
                previous = now;
                if (second == 300) settled = FluxBand.of(now);
                if (second > 300) {
                    helper.assertTrue(FluxBand.of(now) == settled, "the band should not change once settled: second " + second);
                }
            }
        });
    }

    // covers: field2.fall
    @GameTest(template = TestSupport.FLOOR_17, batch = "field2_fall", timeoutTicks = 40)
    public static void aFieldFallsWithoutReboundWhenTheSourceStops(GameTestHelper helper) {
        withField(helper, field -> {
            for (int second = 0; second < 600; second++) {
                QuantumFlux.emit(field.level, field.source, 150.0);
                field.step();
            }
            double peak = field.data(0, 0).flux();
            double previous = peak;
            for (int second = 0; second < 600; second++) {
                field.step();
                double now = field.data(0, 0).flux();
                helper.assertTrue(now <= previous + 1.0E-9, "a field should only fall once its source stops: second " + second);
                previous = now;
            }
            helper.assertTrue(previous < peak * 0.1, "ten minutes should take it down by 90%: " + peak + " → " + previous);
        });
    }

    // covers: field2.shape
    @GameTest(template = TestSupport.FLOOR_17, batch = "field2_shape", timeoutTicks = 40)
    public static void theFieldFadesWithDistanceAndEnds(GameTestHelper helper) {
        withField(helper, field -> {
            for (int second = 0; second < 900; second++) {
                QuantumFlux.emit(field.level, field.source, 150.0);
                field.step();
            }
            double[] ring = new double[REACH + 1];
            for (int d = 0; d <= REACH; d++) ring[d] = field.flux(d, 0);
            for (int d = 1; d <= 4; d++) {
                helper.assertTrue(ring[d] > 0.0 && ring[d] < ring[d - 1],
                        "the field should fade outwards: " + java.util.Arrays.toString(ring));
            }
            // The same whichever way you walk.
            helper.assertTrue(Math.abs(field.flux(0, 2) - ring[2]) < 1.0E-6 && Math.abs(field.flux(-2, 0) - ring[2]) < 1.0E-6,
                    "the field should be the same in every direction");
            helper.assertTrue(ring[REACH] < FieldModel.SPREAD_FLOOR, "the field should end before " + REACH + " chunks: " + ring[REACH]);
            // What a second of the field costs per active chunk, for the tick-timing budget.
            int active = 0;
            for (ChunkPos pos : field.positions) if (field.flux(pos.x() - field.centre.x(), pos.z() - field.centre.z()) > 0.0) active++;
            long start = System.nanoTime();
            for (int i = 0; i < 100; i++) field.step();
            double perChunkMicros = (System.nanoTime() - start) / 1000.0 / 100 / Math.max(1, active);
            Quantimium.LOGGER.info("Field step: {} active chunks, {} µs each a second; 2,000 would be {} ms a second",
                    active, String.format("%.2f", perChunkMicros), String.format("%.2f", perChunkMicros * 2000 / 1000.0));
        });
    }

    // covers: field2.plateau
    @GameTest(template = TestSupport.FLOOR_17, batch = "field2_plateau", timeoutTicks = 40)
    public static void aSourceMakesAPlateauABandForEachTenfold(GameTestHelper helper) {
        withField(helper, field -> {
            // 10,000 FE/t of work (200 flux a second): the whole 3×3 round it should read High.
            for (int second = 0; second < 600; second++) {
                QuantumFlux.emitFromEnergy(field.level, field.source, 10_000L * 20L);
                field.step();
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.assertTrue(FluxBand.of(field.flux(dx, dz)) == FluxBand.HIGH,
                            "10k FE/t should hold the 3×3 at High: " + dx + "," + dz + " is " + field.flux(dx, dz));
                }
            }
            helper.assertTrue(FluxBand.of(field.flux(3, 0)).ordinal() < FluxBand.HIGH.ordinal(),
                    "three chunks out should be below High: " + field.flux(3, 0));
        });
    }

    // covers: field2.settling
    @GameTest(template = TestSupport.FLOOR_17, batch = "field2_settling", timeoutTicks = 40)
    public static void itKnowsWhereItIsHeadingBeforeItGetsThere(GameTestHelper helper) {
        withField(helper, field -> {
            // 10,000 FE/t of work from nothing: the field climbs to High over a minute or two.
            double early = 0.0;
            double earlyFlux = 0.0;
            for (int second = 0; second < 600; second++) {
                QuantumFlux.emitFromEnergy(field.level, field.source, 10_000L * 20L);
                field.step();
                if (second == 40) {
                    early = field.data(0, 0).settling();
                    earlyFlux = field.data(0, 0).flux();
                }
            }
            double settled = field.data(0, 0).flux();
            helper.assertTrue(earlyFlux < settled * 0.9, "forty seconds in it should still be climbing: " + earlyFlux + " of " + settled);
            helper.assertTrue(FluxBand.of(early) == FluxBand.of(settled),
                    "forty seconds in, it should already say which band it will settle in: " + early + " → " + settled);
            helper.assertTrue(Math.abs(field.data(0, 0).settling() - settled) < settled * 0.05,
                    "once settled, where it is heading is where it is: " + field.data(0, 0).settling() + " vs " + settled);
            helper.assertTrue(FieldTrend.of(settled, field.data(0, 0).settling()) == FieldTrend.STEADY, "and it reads as steady");
        });
    }

    // covers: field2.decay
    @GameTest(template = TestSupport.FLOOR_9, batch = "field2_decay", timeoutTicks = 20)
    public static void decayRisesSmoothlyWithTheField(GameTestHelper helper) {
        double previous = FieldModel.decayRate(0.0);
        for (double value = 1.0; value <= 200_000.0; value *= 1.05) {
            double rate = FieldModel.decayRate(value);
            helper.assertTrue(rate >= previous, "decay should never fall as the field rises: " + value);
            helper.assertTrue(rate - previous < 0.001, "decay should have no steps: jump of " + (rate - previous) + " at " + value);
            previous = rate;
        }
        helper.succeed();
    }

    // covers: field2.debug_emitter
    @GameTest(template = TestSupport.FLOOR_9, batch = "field2_emitter", timeoutTicks = 60)
    public static void theDebugEmitterEmitsItsSetting(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.DEBUG_FIELD_EMITTER.get().defaultBlockState().setValue(DebugFieldEmitterBlock.SETTING, 2));
        helper.assertValueEqual(DebugFieldEmitterBlockEntity.fluxPerSecond(DebugFieldEmitterBlock.FE_PER_TICK[2]), 20.0,
                "1,000 FE/t is 20 flux a second");
        // Each second's 20 flux, times the field's gain, goes into the pools of the 3×3: 12 to this chunk.
        helper.runAfterDelay(25, () -> {
            ChunkFlux data = helper.getLevel().getChunkAt(helper.absolutePos(at)).getData(ModAttachments.CHUNK_FLUX);
            double here = data.flux() + data.pendingFlux();
            helper.assertTrue(here > 10.0 && here < 26.0, "one or two seconds at 1,000 FE/t should bring 12-24 here: " + here);
            helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // ---- harness ----

    /** The chunks round the test's source, cleared before and after {@code body} runs. */
    private static final class Field {
        final ServerLevel level;
        final BlockPos source;
        final ChunkPos centre;
        final List<ChunkPos> positions = new ArrayList<>();

        Field(GameTestHelper helper) {
            level = helper.getLevel();
            source = helper.absolutePos(SOURCE);
            centre = ChunkPos.containing(source);
            // One chunk beyond the stepped area too, so the edge has loaded neighbours to spread into.
            for (int dx = -REACH - 1; dx <= REACH + 1; dx++) {
                for (int dz = -REACH - 1; dz <= REACH + 1; dz++) {
                    level.getChunk(centre.x() + dx, centre.z() + dz);
                    if (Math.abs(dx) <= REACH && Math.abs(dz) <= REACH) positions.add(new ChunkPos(centre.x() + dx, centre.z() + dz));
                }
            }
        }

        void clear() {
            for (ChunkPos pos : positions) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
                if (chunk == null) continue;
                chunk.getExistingData(ModAttachments.CHUNK_FLUX).ifPresent(data -> {
                    data.clear();
                    data.setLastDrainTick(level.getGameTime());
                });
            }
        }

        void step() {
            FieldModel.step(level, positions);
        }

        ChunkFlux data(int dx, int dz) {
            LevelChunk chunk = level.getChunk(centre.x() + dx, centre.z() + dz);
            return chunk.getData(ModAttachments.CHUNK_FLUX);
        }

        double flux(int dx, int dz) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
            if (chunk == null) return 0.0;
            return chunk.getExistingData(ModAttachments.CHUNK_FLUX).map(ChunkFlux::flux).orElse(0.0);
        }
    }

    private static void withField(GameTestHelper helper, Consumer<Field> body) {
        Field field = new Field(helper);
        try {
            field.clear();
            body.accept(field);
        } finally {
            field.clear();
        }
        helper.succeed();
    }
}
