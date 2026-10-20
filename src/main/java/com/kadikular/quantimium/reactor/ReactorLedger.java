package com.kadikular.quantimium.reactor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Reactor holds: every item exactly as it went in, components and all, with a count. Nothing
 * merges by itself; block, ingot and nugget are three entries until a crafting table among the
 * catalysts turns one into another.
 *
 * <p>Counts are longs, since a horizon holds millions. {@link #mass} is the total, kept as it changes.
 */
public final class ReactorLedger implements StockIndex {

    /** One entry, for saving. */
    public record Entry(ItemResource item, long count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)
        ).apply(instance, Entry::new));
    }

    public static final Codec<List<Entry>> CODEC = Entry.CODEC.listOf();

    private final Map<ItemResource, Long> counts = new LinkedHashMap<>();
    private final Map<ItemResource, Long> readOnly = Collections.unmodifiableMap(counts);
    private long mass;
    /** Its kinds by item, and when each came in: for plans (see {@link StockIndex}). */
    private final Map<net.minecraft.world.item.Item, List<ItemResource>> byItem = new java.util.HashMap<>();
    private final Map<ItemResource, Long> since = new java.util.HashMap<>();
    private long arrivals;

    /** Changes so far, and the kinds the last of them were to (a few hundred): for what's shown of it to catch up by. */
    private long changes;
    private final java.util.ArrayDeque<ItemResource> recent = new java.util.ArrayDeque<>();
    private static final int RECENT = 512;

    private void changed(ItemResource item) {
        recent.addLast(item);
        changes++;
        if (recent.size() > RECENT) recent.removeFirst();
    }

    /** How many changes it's had: a mark to ask {@link #changedSince} from. */
    public long changes() {
        return changes;
    }

    /** The kinds changed since {@code mark}, or null if that's further back than it remembers. */
    @org.jetbrains.annotations.Nullable
    public java.util.Set<ItemResource> changedSince(long mark) {
        long first = changes - recent.size();
        if (mark < first || mark > changes) return null;
        java.util.Set<ItemResource> out = new java.util.LinkedHashSet<>();
        long at = first;
        for (ItemResource item : recent) {
            if (at++ >= mark) out.add(item);
        }
        return out;
    }

    /** Something about {@code item} changed besides its count (what's promised of it): noted with the rest. */
    public void touch(ItemResource item) {
        changed(item);
    }

    @Override
    public List<ItemResource> kindsOf(net.minecraft.world.item.Item item) {
        return byItem.getOrDefault(item, List.of());
    }

    @Override
    public long order(ItemResource kind) {
        return since.getOrDefault(kind, Long.MAX_VALUE);
    }

    public long mass() {
        return mass;
    }

    public long count(ItemResource item) {
        return counts.getOrDefault(item, 0L);
    }

    /** What it holds, read only and live: the same map each time. */
    public Map<ItemResource, Long> view() {
        return readOnly;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public void add(ItemResource item, long amount) {
        if (amount <= 0 || item.isEmpty()) return;
        changed(item);
        if (counts.merge(item, amount, Long::sum) == amount) {
            byItem.computeIfAbsent(item.getItem(), k -> new ArrayList<>(1)).add(item);
            since.put(item, arrivals++);
        }
        mass += amount;
    }

    /** Takes up to {@code amount}; returns how many it took. */
    public long remove(ItemResource item, long amount) {
        long have = count(item);
        long taken = Math.min(have, Math.max(0, amount));
        if (taken == 0) return 0;
        changed(item);
        if (taken == have) {
            counts.remove(item);
            List<ItemResource> kinds = byItem.get(item.getItem());
            if (kinds != null) {
                kinds.remove(item);
                if (kinds.isEmpty()) byItem.remove(item.getItem());
            }
            since.remove(item);
        } else {
            counts.put(item, have - taken);
        }
        mass -= taken;
        return taken;
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(counts.size());
        counts.forEach((item, count) -> out.add(new Entry(item, count)));
        return out;
    }

    public void load(List<Entry> entries) {
        counts.clear();
        recent.clear();
        changes += RECENT + 1;
        byItem.clear();
        since.clear();
        mass = 0;
        for (Entry entry : entries) add(entry.item(), entry.count());
    }

    /** A copy to plan against, so a plan that fails changes nothing. */
    public Map<ItemResource, Long> snapshot() {
        return new LinkedHashMap<>(counts);
    }
}
