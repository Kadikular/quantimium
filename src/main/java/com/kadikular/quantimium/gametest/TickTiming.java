package com.kadikular.quantimium.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.debug.TickProfiler;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Measures how long a block entity's ticks take, as a server profiler would see them, and writes the
 * results to a report the wiki turns into its tick-timing page.
 *
 * <p>Each measurement runs in its own batch, so no other test ticks alongside it. It lets the setup
 * settle for {@value #WARMUP_TICKS} ticks (the JIT warms up, machines reach their working state),
 * then records {@link #SAMPLE_TICKS} ticks and reports the median, which a stray GC pause does not
 * move. Each carries a budget with plenty of headroom: it fails only on a real regression, not on a
 * slower machine.
 *
 * <p>The report goes to the file named by the {@code quantimium.tickReport} system property (the
 * gameTestServer run sets it to {@code wiki/data/tick-timing.json}); without it, only the log.
 */
public final class TickTiming {

    public static final int WARMUP_TICKS = 2_000;
    /**
     * Ticks recorded. {@code -Dquantimium.tickTimingSamples=20000} runs each scenario long enough to
     * profile it (see the {@code -Pjfr} run in build.gradle).
     */
    public static final int SAMPLE_TICKS = Integer.getInteger("quantimium.tickTimingSamples", 1_000);
    /** Only reached if a scenario hangs; long enough for a profiling run's samples. */
    public static final int TIMEOUT_TICKS = 40_000;

    private static final Map<String, JsonObject> RESULTS = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TickTiming() {}

    /** One scenario to measure. {@code state} describes what the machine was doing, for the report. */
    public record Scenario(String id, String group, String description, double budgetMicros) {}

    /**
     * Waits out the warm-up, records {@code target}'s ticks, reports them, and succeeds if the median
     * is inside the budget. {@code cleanup} runs either way, before the verdict.
     */
    public static void measure(GameTestHelper helper, Scenario scenario, BlockEntity target,
                               Supplier<String> state, Runnable cleanup) {
        // Flux and anomaly left by earlier tests would add surcharges; what these leave would do the same
        // to the tests after them.
        TestSupport.clearField(helper);
        TickProfiler.Samples[] samples = new TickProfiler.Samples[1];
        helper.runAfterDelay(WARMUP_TICKS, () -> samples[0] = TickProfiler.watch(target, SAMPLE_TICKS));
        helper.succeedWhen(() -> {
            helper.assertTrue(samples[0] != null && samples[0].full(), "still measuring");
            TickProfiler.unwatch(target);
            double median = samples[0].quantileMicros(0.5);
            record(scenario, samples[0], state.get());
            cleanup.run();
            TestSupport.clearField(helper);
            helper.assertTrue(median <= scenario.budgetMicros(), String.format(
                    "%s: median tick %.1f µs over its %.0f µs budget", scenario.id(), median, scenario.budgetMicros()));
        });
    }

    private static synchronized void record(Scenario scenario, TickProfiler.Samples samples, String state) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", scenario.id());
        entry.addProperty("group", scenario.group());
        entry.addProperty("description", scenario.description());
        entry.addProperty("state", state);
        entry.addProperty("medianMicros", round(samples.quantileMicros(0.5)));
        entry.addProperty("p90Micros", round(samples.quantileMicros(0.9)));
        entry.addProperty("meanMicros", round(samples.meanMicros()));
        entry.addProperty("budgetMicros", scenario.budgetMicros());
        entry.addProperty("samples", samples.count());
        RESULTS.put(scenario.id(), entry);
        Quantimium.LOGGER.info("Tick timing {}: median {} µs, p90 {} µs ({})", scenario.id(),
                round(samples.quantileMicros(0.5)), round(samples.quantileMicros(0.9)), state);
        write();
    }

    private static void write() {
        String target = System.getProperty("quantimium.tickReport");
        if (target == null) return;
        JsonObject report = new JsonObject();
        report.addProperty("date", LocalDate.now().toString());
        report.addProperty("java", System.getProperty("java.version"));
        report.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.addProperty("cores", Runtime.getRuntime().availableProcessors());
        report.addProperty("warmupTicks", WARMUP_TICKS);
        report.addProperty("sampleTicks", SAMPLE_TICKS);
        JsonArray results = new JsonArray();
        RESULTS.values().forEach(results::add);
        report.add("results", results);
        try {
            Path path = Path.of(target);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Quantimium.LOGGER.warn("Could not write the tick-timing report to {}", target, e);
        }
    }

    private static double round(double micros) {
        return Math.round(micros * 10) / 10.0;
    }
}
