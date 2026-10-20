package com.kadikular.quantimium.gametest.stress;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.work.WorkQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The stress tests' tools: a seeded random for each test (printed, so a failure can be run again exactly), a count of
 * every item in a test's area (so a duplicate or a loss shows), and timings: of each operation a test does, of whole
 * server ticks, and of each step of Quantimium's background work. What a test found goes to a report
 * ({@code build/stress-report.txt}) rather than only into a pass or a fail.
 *
 * <p>Only run with {@code -Pstress}; see {@link com.kadikular.quantimium.gametest.GameTests}.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class Stress {
    private Stress() {}

    public static List<Class<?>> classes() {
        List<Class<?>> classes = new ArrayList<>(List.of(LogisticsStress.class, ReactorStress.class, ChaosStress.class));
        if (net.neoforged.fml.ModList.get().isLoaded("ae2")) classes.add(Ae2Stress.class);
        return classes;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("quantimium.stress");
    }

    /** How much bigger than the default everything is made. */
    public static double scale() {
        return Double.parseDouble(System.getProperty("quantimium.stressScale", "1"));
    }

    public static int scaled(int n) {
        return Math.max(1, (int) Math.round(n * scale()));
    }

    /** A test's random: from -PstressSeed if given, else new; reported either way. */
    public static Random random(String test) {
        String given = System.getProperty("quantimium.stressSeed");
        long seed = given != null ? Long.parseLong(given) : new Random().nextLong();
        report(test, "seed " + seed + " (replay: -PstressSeed=" + seed + " -PgtFilter=" + test.toLowerCase(java.util.Locale.ROOT) + ")");
        return new Random(seed);
    }

    // ---- what's where ----

    /** Every item in {@code box} (relative to the test): in containers, in Crafters (all their slots), on the ground, and held by {@code players}. */
    public static Map<Item, Long> census(GameTestHelper helper, BlockPos from, BlockPos to, Iterable<ServerPlayer> players, Map<Item, Long> pile) {
        Map<Item, Long> counts = new HashMap<>();
        BlockPos a = helper.absolutePos(from), b = helper.absolutePos(to);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity entity = helper.getLevel().getBlockEntity(pos);
            if (entity instanceof QuantumCrafterBlockEntity crafter) {
                for (int i = 0; i < crafter.getInventory().getSlots(); i++) add(counts, crafter.getInventory().getStackInSlot(i));
            } else if (entity instanceof com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity core) {
                // What the Reactor holds is only numbers in its ledger.
                for (var entry : core.getLedger().entries()) counts.merge(entry.item().getItem(), entry.count(), Long::sum);
            } else if (entity instanceof com.kadikular.quantimium.block.entity.ReactorPortBlockEntity port && port.getOutput() != null) {
                for (int i = 0; i < port.getOutput().getSlots(); i++) add(counts, port.getOutput().getStackInSlot(i));
            } else if (entity instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) add(counts, container.getItem(i));
            }
        }
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(min.getCenter(), max.getCenter()).inflate(2))) add(counts, drop.getItem());
        for (ServerPlayer player : players) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) add(counts, player.getInventory().getItem(i));
            add(counts, player.containerMenu.getCarried());
        }
        pile.forEach((item, n) -> counts.merge(item, n, Long::sum));
        counts.values().removeIf(n -> n == 0);
        return counts;
    }

    private static void add(Map<Item, Long> counts, ItemStack stack) {
        if (!stack.isEmpty()) counts.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
    }

    /**
     * What things are worth, in iron or gold nuggets, or half-sticks of oak: every crafting recipe among these makes as
     * much worth as it takes, so crafting moves worth about but never makes or loses any.
     */
    public static final Map<Item, Long> WORTH = Map.of(
            Items.IRON_NUGGET, 1L, Items.IRON_INGOT, 9L, Items.IRON_BLOCK, 81L,
            Items.GOLD_NUGGET, 1L, Items.GOLD_INGOT, 9L, Items.GOLD_BLOCK, 81L,
            Items.OAK_PLANKS, 2L, Items.STICK, 1L);

    /** Which family an item's worth is counted in: iron, gold, wood. */
    private static String family(Item item) {
        if (item == Items.IRON_NUGGET || item == Items.IRON_INGOT || item == Items.IRON_BLOCK) return "iron";
        if (item == Items.GOLD_NUGGET || item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK) return "gold";
        return "wood";
    }

    /**
     * What's wrong between two counts, or null: a family whose worth went up (made from nothing) or down (lost), or any
     * other item whose count changed at all (nothing here should make or use it).
     */
    public static String compare(Map<Item, Long> before, Map<Item, Long> after) {
        Map<String, Long> worth = new LinkedHashMap<>();
        List<String> wrong = new ArrayList<>();
        java.util.Set<Item> items = new java.util.HashSet<>(before.keySet());
        items.addAll(after.keySet());
        for (Item item : items) {
            long was = before.getOrDefault(item, 0L), now = after.getOrDefault(item, 0L);
            Long each = WORTH.get(item);
            if (each != null) worth.merge(family(item), (now - was) * each, Long::sum);
            else if (was != now) wrong.add(item + " " + was + " -> " + now);
        }
        worth.forEach((family, delta) -> {
            if (delta != 0) wrong.add(family + " worth " + (delta > 0 ? "+" : "") + delta + " nuggets/half-sticks"
                    + " (" + family + ": " + detail(before, after, family) + ")");
        });
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    private static String detail(Map<Item, Long> before, Map<Item, Long> after, String family) {
        StringBuilder out = new StringBuilder();
        for (Item item : WORTH.keySet()) {
            if (!family(item).equals(family)) continue;
            long was = before.getOrDefault(item, 0L), now = after.getOrDefault(item, 0L);
            if (was != now) out.append(item).append(' ').append(was).append("->").append(now).append(' ');
        }
        return out.toString().trim();
    }

    // ---- timings ----

    /** How long one kind of thing took: how many, in all, the longest, and what the longest was. */
    public static final class Timing {
        long count, total, max;
        String worst = "";

        void add(long nanos, String what) {
            count++;
            total += nanos;
            if (nanos > max) {
                max = nanos;
                worst = what;
            }
        }

        @Override
        public String toString() {
            return String.format("n=%d avg=%.3fms max=%.3fms%s", count, total / 1e6 / Math.max(1, count), max / 1e6,
                    worst.isEmpty() ? "" : " (" + worst + ")");
        }
    }

    private static final Map<String, Timing> TIMINGS = new LinkedHashMap<>();
    private static final Map<String, Long> COUNTS = new java.util.TreeMap<>();
    /** A slow tick: how long it took (less the harness's own counting), and what took the time. */
    private record SlowTick(long nanos, long tick, String breakdown) {}

    /** The slowest server ticks while a stress test ran, by their time less the harness's. */
    private static final List<SlowTick> SLOW_TICKS = new ArrayList<>();
    /** This tick so far: time by kind of thing, [nanos, how many]. */
    private static final Map<String, long[]> TICK = new LinkedHashMap<>();

    private static void spent(String kind, long nanos) {
        long[] had = TICK.computeIfAbsent(kind, k -> new long[2]);
        had[0] += nanos;
        had[1]++;
    }

    /** Runs the harness's own work (counting items): left out of a tick's time. */
    public static <T> T harness(Supplier<T> body) {
        long began = System.nanoTime();
        try {
            return body.get();
        } finally {
            spent("harness", System.nanoTime() - began);
        }
    }
    private static long tickStart;
    private static long ticks;
    private static boolean running;
    /** What the test was doing last, for a slow tick's report. */
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>();

    /** Times {@code body} as {@code kind} ({@code what}, said if it's the slowest). */
    public static <T> T time(String kind, String what, Supplier<T> body) {
        long began = System.nanoTime();
        try {
            return body.get();
        } finally {
            long took = System.nanoTime() - began;
            TIMINGS.computeIfAbsent(kind, k -> new Timing()).add(took, what);
            spent(kind, took);
            note(kind + " " + what + " " + String.format("%.2fms", took / 1e6));
        }
    }

    /** Remembers what was just done (the last few, for a failure's report). */
    public static void note(String what) {
        RECENT.addLast(what);
        while (RECENT.size() > 30) RECENT.removeFirst();
    }

    public static String recent() {
        try {
            return String.join("\n    ", new ArrayList<>(RECENT));
        } catch (RuntimeException busy) {
            // read from the watchdog while the server thread was adding to it
            return "(busy)";
        }
    }

    /** A stress test starts: timings from now on are its. */
    public static void begin() {
        TIMINGS.clear();
        SLOW_TICKS.clear();
        RECENT.clear();
        ticks = 0;
        running = true;
        WorkQueue.stepTimes = (key, nanos) -> {
            String kind = "queue " + (key instanceof BlockEntity entity ? entity.getClass().getSimpleName() : key.getClass().getSimpleName());
            TIMINGS.computeIfAbsent(kind, k -> new Timing()).add(nanos, key instanceof BlockEntity entity ? entity.getBlockPos().toShortString() : "");
            spent(kind, nanos);
        };
        COUNTS.clear();
        WorkQueue.finished = key -> COUNTS.merge("finished " + (key instanceof BlockEntity entity ? entity.getClass().getSimpleName() : key.getClass().getSimpleName()), 1L, Long::sum);
        QuantumCrafterBlockEntity.whyMatched = why -> COUNTS.merge("crafter answers " + why, 1L, Long::sum);
        // Every block entity's tick, and the laser networks', by kind: what "the rest of the tick" was.
        com.kadikular.quantimium.debug.TickProfiler.everything((ticking, nanos) -> spent("tick " + ticking.getClass().getSimpleName(), nanos));
    }

    /** A stress test is done: its timings and findings go to the report. */
    public static void end(String test, List<String> findings) {
        running = false;
        WorkQueue.stepTimes = null;
        WorkQueue.finished = null;
        QuantumCrafterBlockEntity.whyMatched = null;
        com.kadikular.quantimium.debug.TickProfiler.everything(null);
        List<String> lines = new ArrayList<>();
        lines.add("ticks " + ticks);
        SLOW_TICKS.sort(Comparator.comparingLong(SlowTick::nanos).reversed());
        lines.add("slowest ticks (less the harness's counting):");
        for (int i = 0; i < Math.min(6, SLOW_TICKS.size()); i++) {
            SlowTick slow = SLOW_TICKS.get(i);
            lines.add(String.format("  %.1fms at tick %d: %s", slow.nanos() / 1e6, slow.tick(), slow.breakdown()));
        }
        TIMINGS.entrySet().stream().sorted(Comparator.comparingLong((Map.Entry<String, Timing> e) -> e.getValue().max).reversed())
                .forEach(e -> lines.add(e.getKey() + ": " + e.getValue()));
        COUNTS.forEach((what, n) -> lines.add("count " + what + ": " + n));
        // Each finished rebuild of a network's offers, what it cost in all.
        Timing builds = TIMINGS.get("queue LogisticsControllerBlockEntity");
        Long finished = COUNTS.get("finished LogisticsControllerBlockEntity");
        if (builds != null && finished != null && finished > 0) {
            lines.add(String.format("network offers worked out %d times, %.2fms each", finished, builds.total / 1e6 / finished));
        }
        if (findings.isEmpty()) lines.add("findings: none");
        else for (String finding : findings) lines.add("FINDING " + finding);
        for (String line : lines) report(test, line);
    }

    @SubscribeEvent
    public static void tickStarts(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
        TICK.clear();
        serverThread = Thread.currentThread();
        tickCount++;
        if (enabled() && watchdog == null) {
            watchdog = new Thread(Stress::watch, "quantimium-stress-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    private static volatile Thread serverThread;
    private static volatile long tickCount;
    private static Thread watchdog;

    /**
     * Watches the server thread: a tick that runs past five seconds is a freeze, and where the server is stuck goes in
     * the report (again every half minute it stays stuck).
     */
    private static void watch() {
        long seenTick = -1, stuckSince = 0, lastDump = 0;
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            long tick = tickCount;
            long now = System.nanoTime();
            if (tick != seenTick) {
                seenTick = tick;
                stuckSince = now;
                lastDump = 0;
                continue;
            }
            long stuck = now - stuckSince;
            if (stuck > 5_000_000_000L && (lastDump == 0 || now - lastDump > 30_000_000_000L)) {
                lastDump = now;
                Thread thread = serverThread;
                if (thread == null) continue;
                StringBuilder trace = new StringBuilder(String.format("FREEZE: tick stuck %.0fs; doing:", stuck / 1e9));
                int shown = 0, depth = 0;
                for (StackTraceElement frame : thread.getStackTrace()) {
                    depth++;
                    if (!frame.getClassName().startsWith("com.kadikular") || shown >= 40) continue;
                    trace.append("\n      ").append(frame);
                    shown++;
                }
                trace.append("\n      (").append(depth).append(" frames)\n    recent:\n    ").append(recent());
                report("watchdog", trace.toString());
            }
        }
    }

    @SubscribeEvent
    public static void tickEnds(ServerTickEvent.Post event) {
        if (!running) return;
        ticks++;
        long took = System.nanoTime() - tickStart;
        long harness = TICK.containsKey("harness") ? TICK.get("harness")[0] : 0;
        long net = took - harness;
        if (SLOW_TICKS.size() >= 32 && net <= SLOW_TICKS.getLast().nanos()) return;
        // What took the time: each kind of thing, and the rest (block entities ticking, hoppers, the server itself).
        StringBuilder breakdown = new StringBuilder();
        long known = 0;
        List<Map.Entry<String, long[]>> kinds = new ArrayList<>(TICK.entrySet());
        kinds.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed());
        for (Map.Entry<String, long[]> kind : kinds) {
            if (kind.getKey().equals("harness")) continue;
            known += kind.getValue()[0];
            if (kind.getValue()[0] < 500_000) continue;
            breakdown.append(String.format("%s %.1fms x%d, ", kind.getKey(), kind.getValue()[0] / 1e6, kind.getValue()[1]));
        }
        breakdown.append(String.format("rest of the tick %.1fms", (net - known) / 1e6));
        SLOW_TICKS.add(new SlowTick(net, ticks, breakdown.toString()));
        SLOW_TICKS.sort(Comparator.comparingLong(SlowTick::nanos).reversed());
        if (SLOW_TICKS.size() > 32) SLOW_TICKS.subList(32, SLOW_TICKS.size()).clear();
    }

    // ---- the report ----

    public static synchronized void report(String test, String line) {
        Quantimium.LOGGER.info("[stress] {}: {}", test, line);
        String file = System.getProperty("quantimium.stressReport");
        if (file == null) return;
        try {
            Files.writeString(Path.of(file), test + ": " + line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // the log has it
        }
    }
}
