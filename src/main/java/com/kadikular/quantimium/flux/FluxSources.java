package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Where the field is being fed and drained, recently: every block that put flux or anomaly into it
 * (machines, rifts, the Veiled feeding) and every one that pulled it out (suppressors, halls).
 * Kept only for the Mirror Lens to draw, so it holds a few seconds of memory and nothing is saved.
 *
 * <p>Each source's strength is what it moved lately, decaying with a time constant of
 * {@value #DECAY_TICKS} ticks: a machine emitting once a second holds a steady value, one that stops
 * fades out, and after {@value #FORGET_TICKS} ticks without news it is forgotten.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class FluxSources {

    private static final double DECAY_TICKS = 40.0;
    private static final long FORGET_TICKS = 80L;

    /** A block feeding or draining the field; positive amounts, with {@code sink} saying which way. */
    public record Source(BlockPos pos, float flux, float anomaly, boolean sink) {}

    private static final class Entry {
        double flux;
        double anomaly;
        long lastTick;
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> EMITTERS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> SINKS = new HashMap<>();

    private FluxSources() {}

    static void emitted(ServerLevel level, BlockPos pos, double flux, double anomaly) {
        record(EMITTERS, level, pos, flux, anomaly);
    }

    /**
     * A source for the eye only: something that should be seen bleeding into the field (a rift's tear,
     * a brooding crystal) without anything being added to it here.
     */
    public static void show(ServerLevel level, BlockPos pos, double flux, double anomaly) {
        record(EMITTERS, level, pos, flux, anomaly);
    }

    /** A drain for the eye only: something that holds the field back without taking it (containment). */
    public static void showSink(ServerLevel level, BlockPos pos, double flux, double anomaly) {
        record(SINKS, level, pos, flux, anomaly);
    }

    static void drew(ServerLevel level, BlockPos pos, double flux, double anomaly) {
        record(SINKS, level, pos, flux, anomaly);
    }

    private static void record(Map<ResourceKey<Level>, Map<BlockPos, Entry>> map, ServerLevel level, BlockPos pos,
                               double flux, double anomaly) {
        if (flux <= 0.0 && anomaly <= 0.0) return;
        long now = level.getGameTime();
        Entry entry = map.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .computeIfAbsent(pos.immutable(), key -> new Entry());
        double decay = Math.exp(-(now - entry.lastTick) / DECAY_TICKS);
        entry.flux = entry.flux * decay + flux;
        entry.anomaly = entry.anomaly * decay + anomaly;
        entry.lastTick = now;
    }

    /** Sources within {@code range} blocks of {@code centre}, nearest first, at most {@code limit}. */
    public static List<Source> near(ServerLevel level, BlockPos centre, double range, int limit) {
        List<Source> found = new ArrayList<>();
        long now = level.getGameTime();
        collect(EMITTERS.get(level.dimension()), centre, range, now, false, found);
        collect(SINKS.get(level.dimension()), centre, range, now, true, found);
        found.sort((a, b) -> Double.compare(a.pos().distSqr(centre), b.pos().distSqr(centre)));
        return found.size() > limit ? List.copyOf(found.subList(0, limit)) : found;
    }

    private static void collect(Map<BlockPos, Entry> entries, BlockPos centre, double range, long now, boolean sink,
                                List<Source> into) {
        if (entries == null) return;
        double rangeSq = range * range;
        Iterator<Map.Entry<BlockPos, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Entry> next = it.next();
            Entry entry = next.getValue();
            long age = now - entry.lastTick;
            if (age > FORGET_TICKS || age < 0) {
                it.remove();
                continue;
            }
            if (next.getKey().distSqr(centre) > rangeSq) continue;
            double decay = Math.exp(-age / DECAY_TICKS);
            into.add(new Source(next.getKey(), (float) (entry.flux * decay), (float) (entry.anomaly * decay), sink));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EMITTERS.clear();
        SINKS.clear();
    }
}
