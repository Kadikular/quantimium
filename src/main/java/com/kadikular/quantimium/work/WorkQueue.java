package com.kadikular.quantimium.work;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quantimium's background work, done a little each tick within a time budget ({@link Config#workBudgetNanos()},
 * more for work a player is waiting on: {@link Config#urgentWorkBudgetNanos()}):
 * what a logistics network offers, what a Crafter could make. Each piece of work is a {@link Job} done in steps;
 * each tick the most urgent are stepped until the budget is spent, and the rest wait. Waiting work grows more
 * urgent, so nothing waits for ever; at least one step is taken every tick, so everything gets there.
 *
 * <p>Nothing a player or a pipe is waiting on goes through here: taking or crafting something works out what it
 * needs there and then. Only the server thread uses it.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class WorkQueue {

    /** How urgent a job is: a player looking at what it works out, or not. */
    public static final int URGENT = 100, NORMAL = 10;

    /** A piece of work done in steps. */
    @FunctionalInterface
    public interface Job {
        /** Does a step of it; whether it's done. */
        boolean step();
    }

    private static final class Entry {
        final Job job;
        int priority;
        long queuedTick;

        Entry(Job job, int priority, long queuedTick) {
            this.job = job;
            this.priority = priority;
            this.queuedTick = queuedTick;
        }
    }

    /** The work waiting, by what it's for (one job a thing: queued again, the newest job replaces it). */
    private static final Map<Object, Entry> WAITING = new LinkedHashMap<>();
    private static long tick;

    private WorkQueue() {}

    /**
     * Queues {@code job} for {@code key} (a block entity, say), replacing any job already waiting for it; at the more
     * urgent of the two priorities.
     */
    public static void submit(Object key, int priority, Job job) {
        Entry was = WAITING.get(key);
        Entry entry = new Entry(job, was == null ? priority : Math.max(priority, was.priority), was == null ? tick : was.queuedTick);
        WAITING.put(key, entry);
    }

    /** Makes the job waiting for {@code key}, if any, at least {@code priority} urgent. */
    public static void hurry(Object key, int priority) {
        Entry entry = WAITING.get(key);
        if (entry != null) entry.priority = Math.max(entry.priority, priority);
    }

    public static boolean waiting(Object key) {
        return WAITING.containsKey(key);
    }

    /** Lets go of the work for {@code key}: what it was for has gone. */
    public static void cancel(Object key) {
        WAITING.remove(key);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        if (WAITING.isEmpty()) return;
        long start = System.nanoTime();
        boolean first = true;
        while (!WAITING.isEmpty()) {
            Map.Entry<Object, Entry> next = mostUrgent();
            // Work a player is waiting on gets more of the tick than the rest.
            long budget = next.getValue().priority >= URGENT ? Math.max(Config.workBudgetNanos(), Config.urgentWorkBudgetNanos())
                    : Config.workBudgetNanos();
            if (!first && System.nanoTime() - start >= budget) break;
            first = false;
            boolean done;
            try {
                done = next.getValue().job.step();
            } catch (RuntimeException failed) {
                Quantimium.LOGGER.error("Background work for {} failed; dropped", next.getKey(), failed);
                done = true;
            }
            // Queued again while it ran (it found more to do): the new job stands.
            if (done && WAITING.get(next.getKey()) == next.getValue()) WAITING.remove(next.getKey());
        }
    }

    /** The job to step next: the most urgent, counting a tick's wait as one more; the oldest first among equals. */
    private static Map.Entry<Object, Entry> mostUrgent() {
        Map.Entry<Object, Entry> best = null;
        long bestScore = Long.MIN_VALUE;
        for (Map.Entry<Object, Entry> entry : WAITING.entrySet()) {
            long score = entry.getValue().priority + (tick - entry.getValue().queuedTick);
            if (score > bestScore) {
                best = entry;
                bestScore = score;
            }
        }
        return best;
    }

    /** Everything waiting, done now: for tests, and a world closing. */
    public static void drain() {
        int guard = 0;
        while (!WAITING.isEmpty() && guard++ < 1_000_000) {
            Map.Entry<Object, Entry> next = mostUrgent();
            boolean done;
            try {
                done = next.getValue().job.step();
            } catch (RuntimeException failed) {
                done = true;
            }
            if (done && WAITING.get(next.getKey()) == next.getValue()) WAITING.remove(next.getKey());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WAITING.clear();
    }
}
