package com.kadikular.quantimium.debug;


import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Times block entity ticks, as a profiler such as Spark or Observable would attribute them: the whole
 * of the level's call into the block entity's ticker, including everything a simulator does for its
 * machines inside it. Off unless something is watching: the tick-timing game tests turn it on for
 * the block entities they measure, so a normal game pays one field read per ticker.
 */
public final class TickProfiler {

    /** Read on every block entity tick; true only while at least one block entity is watched. */
    private static volatile boolean active;
    private static final Map<Object, Samples> WATCHED = Collections.synchronizedMap(new IdentityHashMap<>());

    private TickProfiler() {}

    public static boolean active() {
        return active;
    }

    /** Starts recording {@code blockEntity}'s ticks (or another ticking thing's, such as a level's laser networks), from its next one. */
    public static Samples watch(Object blockEntity, int capacity) {
        Samples samples = new Samples(capacity);
        WATCHED.put(blockEntity, samples);
        active = true;
        return samples;
    }

    public static void unwatch(Object blockEntity) {
        WATCHED.remove(blockEntity);
        active = !WATCHED.isEmpty();
    }

    /** Called round each ticker call while active. */
    public static void record(Object blockEntity, long nanos) {
        Samples samples = WATCHED.get(blockEntity);
        if (samples != null) samples.add(nanos);
    }

    /** One block entity's tick times, in nanoseconds, up to a fixed count. */
    public static final class Samples {
        private final long[] nanos;
        private int count;

        Samples(int capacity) {
            this.nanos = new long[capacity];
        }

        void add(long value) {
            if (count < nanos.length) nanos[count++] = value;
        }

        public int count() {
            return count;
        }

        public boolean full() {
            return count >= nanos.length;
        }

        /** The {@code fraction} quantile, in microseconds: 0.5 for the median. */
        public double quantileMicros(double fraction) {
            if (count == 0) return 0;
            long[] sorted = Arrays.copyOf(nanos, count);
            Arrays.sort(sorted);
            int index = (int) Math.min(count - 1, Math.max(0, Math.round(fraction * (count - 1))));
            return sorted[index] / 1000.0;
        }

        public double meanMicros() {
            if (count == 0) return 0;
            long total = 0;
            for (int i = 0; i < count; i++) total += nanos[i];
            return total / 1000.0 / count;
        }
    }
}
