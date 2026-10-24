package com.kadikular.quantimium.laser;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /quantimium lasers profile}: for a few seconds, how long a level's lasers take each tick, by
 * what they were doing and by each lens action, so a slow base can find its costly lens. Off, it costs
 * one check a tick.
 */
public final class LaserProfile {

    /** What the lasers spend a tick on, apart from the lens actions. */
    public enum Phase { BEAMS, LENSES, LOGISTICS, POWER, SETTLE }

    private static final Map<ServerLevel, LaserProfile> RUNNING = new HashMap<>();

    private final CommandSourceStack source;
    private final long until;
    private int ticks;
    private final EnumMap<Phase, Long> phases = new EnumMap<>(Phase.class);
    private final Map<LensAction.Key, Long> actions = new HashMap<>();

    private LaserProfile(CommandSourceStack source, long until) {
        this.source = source;
        this.until = until;
    }

    public static void start(ServerLevel level, CommandSourceStack source, int seconds) {
        RUNNING.put(level, new LaserProfile(source, level.getGameTime() + seconds * 20L));
    }

    /** The profile running for {@code level}, or null: the usual answer. */
    @org.jetbrains.annotations.Nullable
    static LaserProfile of(ServerLevel level) {
        return RUNNING.isEmpty() ? null : RUNNING.get(level);
    }

    /** Counts the time since {@code since} to {@code phase}; returns now, the start of what comes next. */
    long phase(Phase phase, long since) {
        long now = System.nanoTime();
        phases.merge(phase, now - since, Long::sum);
        return now;
    }

    void action(LensAction.Key key, long nanos) {
        actions.merge(key, nanos, Long::sum);
    }

    /** At the end of each of the level's laser ticks: reports and stops once its time is up. */
    void tickDone(ServerLevel level) {
        ticks++;
        if (level.getGameTime() < until) return;
        RUNNING.remove(level);
        report();
    }

    private double perTick(long nanos) {
        return nanos / 1000.0 / Math.max(1, ticks);
    }

    private void report() {
        long total = phases.values().stream().mapToLong(Long::longValue).sum();
        source.sendSuccess(() -> Component.translatable("message.quantimium.lasers.profile.total",
                format(perTick(total)), ticks), false);
        for (Phase phase : Phase.values()) {
            long nanos = phases.getOrDefault(phase, 0L);
            source.sendSuccess(() -> Component.translatable("message.quantimium.lasers.profile.phase",
                    Component.translatable("message.quantimium.lasers.profile.phase." + phase.name().toLowerCase(Locale.ROOT)),
                    format(perTick(nanos))), false);
        }
        List<Map.Entry<LensAction.Key, Long>> slowest = new ArrayList<>(actions.entrySet());
        slowest.sort(Map.Entry.<LensAction.Key, Long>comparingByValue().reversed());
        for (Map.Entry<LensAction.Key, Long> entry : slowest.subList(0, Math.min(10, slowest.size()))) {
            LensAction.Key key = entry.getKey();
            source.sendSuccess(() -> Component.translatable("message.quantimium.lasers.profile.action",
                    Component.translatable("item.quantimium." + key.action().itemKey() + "_lens"),
                    key.node().getX(), key.node().getY(), key.node().getZ(), format(perTick(entry.getValue()))), false);
        }
    }

    private static String format(double micros) {
        return String.format(Locale.ROOT, "%.1f", micros);
    }

    /** A level going away takes its profile with it. */
    public static void forget(ServerLevel level) {
        RUNNING.remove(level);
    }
}
