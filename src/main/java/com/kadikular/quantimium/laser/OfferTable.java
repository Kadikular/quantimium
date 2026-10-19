package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a logistics network offers: real items, and what its machines could make, worked out once from a snapshot
 * of the network rather than by machines asking each other when someone looks. So it reads the same whatever is
 * read first, and stays put while the stock does.
 *
 * <p>Tier 0 is what's really there. Then each machine that makes things to order behind a provider Receptacle
 * ({@link OfferMaker}) is shown the real items and what the other machines could make so far, never its own, and
 * what it could make from that is its craftable offer; that's done again, so a chain of machines is found, up to
 * {@link #TIERS} deep. Two machines offering the same thing both count, as two providers would.
 *
 * <p>Each craftable remembers every item it's made from, all the way down. A machine isn't shown what was made out of
 * what it makes: a pulveriser crushing ingots doesn't let the furnace beside it count those ingots again. Where it
 * can't tell which part came from where, it counts less rather than more.
 *
 * <p>Taking is never decided here: a pull goes to the machines in a transaction and gets what they really give.
 */
public final class OfferTable {
    /** How many machines a chain of crafts on the network reaches through. */
    public static final int TIERS = 4;

    /** How many times a machine is asked again, leaving out more each time, before what still loops is dropped. */
    private static final int LOOP_RETRIES = 3;

    public static final OfferTable EMPTY = new OfferTable(Map.of(), Map.of(), Map.of(), Set.of());

    /** One item on offer: how many are there, how many could be made, and the shortest chain that makes it. */
    public record Entry(ItemResource item, long real, long craftable, int tier) {}

    /** Some of an item one machine could make, and every item that goes into it (none known: nothing). */
    private record Source(long count, Set<Item> from) {}

    private final Map<ItemResource, Long> real;
    /** What could be made, by item, then by the place of the machine that would make it, craft by craft. */
    private final Map<ItemResource, Map<BlockPos, List<Source>>> craftable;
    private final Map<ItemResource, Integer> tiers;
    /** The networks (by controller) it drew on: its own, and any it imports through another's Materialiser. */
    private final Set<BlockPos> origins;

    private OfferTable(Map<ItemResource, Long> real, Map<ItemResource, Map<BlockPos, List<Source>>> craftable,
                       Map<ItemResource, Integer> tiers, Set<BlockPos> origins) {
        this.real = real;
        this.craftable = craftable;
        this.tiers = tiers;
        this.origins = origins;
    }

    public long real(ItemResource item) {
        return real.getOrDefault(item, 0L);
    }

    public long craftable(ItemResource item) {
        return craftableExcept(item, null);
    }

    /** What could be made of {@code item} by machines other than the one at {@code maker}. */
    public long craftableExcept(ItemResource item, BlockPos maker) {
        return craftableExcept(item, maker, Set.of());
    }

    /** As {@link #craftableExcept(ItemResource, BlockPos)}, leaving out what's made from any of {@code avoid}. */
    private long craftableExcept(ItemResource item, BlockPos maker, Set<Item> avoid) {
        Map<BlockPos, List<Source>> makers = craftable.get(item);
        if (makers == null) return 0;
        long total = 0;
        for (Map.Entry<BlockPos, List<Source>> entry : makers.entrySet()) {
            if (entry.getKey().equals(maker)) continue;
            // One machine's crafts of the same thing are alternatives, not more: the most of them.
            long most = 0;
            for (Source source : entry.getValue()) if (Collections.disjoint(source.from(), avoid)) most = Math.max(most, source.count());
            total += most;
        }
        return total;
    }

    /** Everything the craftable {@code item} that {@code maker} would be shown is made from, all the way down. */
    private Set<Item> madeFrom(Item item, BlockPos maker, Set<Item> avoid) {
        Set<Item> from = new HashSet<>();
        for (Map.Entry<ItemResource, Map<BlockPos, List<Source>>> entry : craftable.entrySet()) {
            if (entry.getKey().getItem() != item) continue;
            for (Map.Entry<BlockPos, List<Source>> by : entry.getValue().entrySet()) {
                if (by.getKey().equals(maker)) continue;
                for (Source source : by.getValue()) {
                    if (source.count() > 0 && Collections.disjoint(source.from(), avoid)) from.addAll(source.from());
                }
            }
        }
        return from;
    }

    /**
     * What a machine drawing on the network sees: the real items and, with {@code craftables}, what the other
     * machines could make, leaving out what the machine at {@code maker} would (null for none).
     */
    public Map<ItemResource, Long> stock(BlockPos maker, boolean reals, boolean craftables) {
        return stock(maker, reals, craftables, Set.of());
    }

    private Map<ItemResource, Long> stock(BlockPos maker, boolean reals, boolean craftables, Set<Item> avoid) {
        Map<ItemResource, Long> stock = new LinkedHashMap<>();
        if (reals) stock.putAll(real);
        if (craftables) {
            for (ItemResource item : craftable.keySet()) {
                long more = craftableExcept(item, maker, avoid);
                if (more > 0) stock.merge(item, more, Long::sum);
            }
        }
        return stock;
    }

    /** Every item on offer, real and craftable apart. */
    public List<Entry> entries() {
        Set<ItemResource> items = new java.util.LinkedHashSet<>(real.keySet());
        items.addAll(craftable.keySet());
        List<Entry> list = new ArrayList<>(items.size());
        for (ItemResource item : items) {
            long craft = craftable(item);
            long there = real(item);
            if (there > 0 || craft > 0) list.add(new Entry(item, there, craft, there > 0 ? 0 : tiers.getOrDefault(item, 1)));
        }
        return list;
    }

    /** The same offers, only the real ones, only the craftable, or both. */
    public OfferTable only(boolean reals, boolean craftables) {
        if (reals && craftables) return this;
        return new OfferTable(reals ? real : Map.of(), craftables ? craftable : Map.of(), tiers, origins);
    }

    public Set<BlockPos> origins() {
        return origins;
    }

    /** Works out the table for {@code network} (its controller at {@code at}), whose members are {@code members}. */
    public static OfferTable build(Level level, LogisticsController network, BlockPos at, List<LogisticsMember> members) {
        Map<ItemResource, Long> real = new LinkedHashMap<>();
        Map<ItemResource, Map<BlockPos, List<Source>>> craftable = new LinkedHashMap<>();
        Map<ItemResource, Integer> tiers = new HashMap<>();
        Set<BlockPos> origins = new HashSet<>();
        origins.add(at);
        record Maker(BlockPos pos, OfferMaker maker, LogisticsMember member) {}
        List<Maker> makers = new ArrayList<>();

        List<LogisticsMember> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(LogisticsMember::inventoryPos));
        for (LogisticsMember member : sorted) {
            if (member.role() == LogisticsMember.Role.REQUESTER) continue;
            BlockPos pos = member.inventoryPos();
            OfferTable imported = member.imports();
            if (imported != null) {
                // Another network, through its Logistics Materialiser: unless it draws on this one, what it has is
                // offered here too, real as real and craftable as craftable.
                if (imported.origins().contains(at)) continue;
                origins.addAll(imported.origins());
                imported.real.forEach((item, amount) -> {
                    if (member.role() != LogisticsMember.Role.PROVIDER || member.lets(item.toStack(1))) real.merge(item, amount, Long::sum);
                });
                if (member.offersCraftable()) {
                    for (Entry entry : imported.entries()) {
                        if (entry.craftable() <= 0) continue;
                        if (member.role() == LogisticsMember.Role.PROVIDER && !member.lets(entry.item().toStack(1))) continue;
                        offer(craftable, entry.item(), pos, new Source(entry.craftable(), Set.of()));
                        tiers.merge(entry.item(), entry.tier(), Math::min);
                    }
                }
                continue;
            }
            Map<ItemResource, long[]> listing = member.listing();
            if (listing != null) {
                // A Reactor's port: what it holds is real, what it could make is craftable, already worked out.
                listing.forEach((item, both) -> {
                    if (both[0] > 0) real.merge(item, both[0], Long::sum);
                    if (both[1] > 0 && member.offersCraftable()) {
                        offer(craftable, item, pos, new Source(both[1], Set.of()));
                        tiers.merge(item, 1, Math::min);
                    }
                });
                continue;
            }
            IItemHandler inventory = member.inventory();
            if (inventory == null) continue;
            OfferMaker maker = member.maker();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                // What a machine would make is worked out below, not read off its slots.
                if (maker != null && maker.makesOnDemand(slot)) continue;
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || member.role() == LogisticsMember.Role.PROVIDER && !member.lets(stack)) continue;
                real.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
            }
            if (maker != null && member.role() == LogisticsMember.Role.PROVIDER && member.offersCraftable()) makers.add(new Maker(pos, maker, member));
        }

        // Tier by tier: each machine shown what's real and what the others could make so far.
        for (int tier = 1; tier <= TIERS && !makers.isEmpty(); tier++) {
            OfferTable sofar = new OfferTable(real, craftable, tiers, origins);
            Map<BlockPos, Map<ItemResource, List<Source>>> found = new LinkedHashMap<>();
            for (Maker maker : makers) found.put(maker.pos(), sofar.offersOf(network, maker.pos(), maker.maker(), maker.member()));
            boolean more = false;
            for (Map.Entry<BlockPos, Map<ItemResource, List<Source>>> made : found.entrySet()) {
                BlockPos pos = made.getKey();
                Map<ItemResource, List<Source>> had = new LinkedHashMap<>();
                for (Map.Entry<ItemResource, Map<BlockPos, List<Source>>> entry : craftable.entrySet()) {
                    List<Source> sources = entry.getValue().remove(pos);
                    if (sources != null) had.put(entry.getKey(), sources);
                }
                craftable.values().removeIf(Map::isEmpty);
                if (!had.equals(made.getValue())) more = true;
                for (Map.Entry<ItemResource, List<Source>> offer : made.getValue().entrySet()) {
                    craftable.computeIfAbsent(offer.getKey(), k -> new LinkedHashMap<>()).put(pos, offer.getValue());
                    tiers.merge(offer.getKey(), tier, Math::min);
                }
            }
            if (!more) break;
        }
        return new OfferTable(Map.copyOf(real), craftable, tiers, Set.copyOf(origins));
    }

    /**
     * What the machine at {@code pos} could make, shown this table: a craft out of something that was made out of
     * what that craft makes is a loop, so the machine is asked again with such things left out, and what still loops
     * after that is dropped.
     */
    private Map<ItemResource, List<Source>> offersOf(LogisticsController network, BlockPos pos, OfferMaker maker, LogisticsMember member) {
        Set<Item> avoid = new HashSet<>();
        Map<ItemResource, List<Source>> kept = new LinkedHashMap<>();
        for (int attempt = 0; attempt <= LOOP_RETRIES; attempt++) {
            kept.clear();
            Map<Item, Set<Item>> below = new HashMap<>();
            Set<Item> looped = new HashSet<>();
            boolean chains = member.craftsFromCraftable();
            for (OfferMaker.Made made : maker.makes(network, stock(pos, true, chains, avoid), chains)) {
                if (made.count() <= 0 || !member.lets(made.item().toStack(1))) continue;
                Item out = made.item().getItem();
                Set<Item> from = new HashSet<>(made.from());
                boolean loops = false;
                for (Item input : made.from()) {
                    Set<Item> under = below.computeIfAbsent(input, i -> madeFrom(i, pos, avoid));
                    if (under.contains(out)) loops = true;
                    from.addAll(under);
                }
                if (loops) {
                    looped.add(out);
                    continue;
                }
                Source source = new Source(made.count(), Set.copyOf(from));
                List<Source> sources = kept.computeIfAbsent(made.item(), k -> new ArrayList<>());
                if (!sources.contains(source)) sources.add(source);
            }
            if (looped.isEmpty() || !avoid.addAll(looped)) break;
        }
        return kept;
    }

    private static void offer(Map<ItemResource, Map<BlockPos, List<Source>>> craftable, ItemResource item, BlockPos pos, Source source) {
        craftable.computeIfAbsent(item, k -> new LinkedHashMap<>()).computeIfAbsent(pos, k -> new ArrayList<>()).add(source);
    }
}
