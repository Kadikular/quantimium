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
public final class ReactorLedger {

    /** One entry, for saving. */
    public record Entry(ItemResource item, long count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)
        ).apply(instance, Entry::new));
    }

    public static final Codec<List<Entry>> CODEC = Entry.CODEC.listOf();

    private final Map<ItemResource, Long> counts = new LinkedHashMap<>();
    private long mass;

    public long mass() {
        return mass;
    }

    public long count(ItemResource item) {
        return counts.getOrDefault(item, 0L);
    }

    public Map<ItemResource, Long> view() {
        return Collections.unmodifiableMap(counts);
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public void add(ItemResource item, long amount) {
        if (amount <= 0 || item.isEmpty()) return;
        counts.merge(item, amount, Long::sum);
        mass += amount;
    }

    /** Takes up to {@code amount}; returns how many it took. */
    public long remove(ItemResource item, long amount) {
        long have = count(item);
        long taken = Math.min(have, Math.max(0, amount));
        if (taken == 0) return 0;
        if (taken == have) counts.remove(item);
        else counts.put(item, have - taken);
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
        mass = 0;
        for (Entry entry : entries) add(entry.item(), entry.count());
    }

    /** A copy to plan against, so a plan that fails changes nothing. */
    public Map<ItemResource, Long> snapshot() {
        return new LinkedHashMap<>(counts);
    }
}
