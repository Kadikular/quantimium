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
 * ({@link OfferMaker}) is shown the real items and what the machines (itself too) could make so far, and what it
 * could make from that is its craftable offer; that's done again, so a chain of machines is found, up to
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
    public static final int TIERS = 8;

    /** How many times a machine is asked again about its looping crafts, leaving out more each time, before they're dropped. */
    private static final int LOOP_RETRIES = 2;

    public static final OfferTable EMPTY = new OfferTable(Map.of(), Map.of(), Map.of(), Set.of(), false);
    /** A network's offers before they've first been worked out: nothing yet, and another network reading it should look again soon. */
    public static final OfferTable PENDING = new OfferTable(Map.of(), Map.of(), Map.of(), Set.of(), true);

    /** One item on offer: how many are there, how many could be made, and the shortest chain that makes it. */
    public record Entry(ItemResource item, long real, long craftable, int tier) {}

    /** Some of an item one machine could make, and every item that goes into it (none known: nothing). */
    private record Source(long count, Set<Item> from) {}

    private final Map<ItemResource, Long> real;
    /** What could be made, by item, then by the place of the machine that would make it, craft by craft. */
    private final Map<ItemResource, Map<BlockPos, List<Source>>> craftable;
    private final Map<ItemResource, Integer> tiers;
    /** Every source of what could be made, by item, gathered when first needed: a table is asked this often while built. */
    @org.jetbrains.annotations.Nullable
    private Map<Item, List<Source>> sourcesByItem;
    /** The networks (by controller) it drew on: its own, and any it imports through another's Materialiser. */
    private final Set<BlockPos> origins;

    /** Whether what could be made hasn't been worked out yet (what's stored may have been looked at since). */
    private final boolean pending;

    private OfferTable(Map<ItemResource, Long> real, Map<ItemResource, Map<BlockPos, List<Source>>> craftable,
                       Map<ItemResource, Integer> tiers, Set<BlockPos> origins) {
        this(real, craftable, tiers, origins, false);
    }

    private OfferTable(Map<ItemResource, Long> real, Map<ItemResource, Map<BlockPos, List<Source>>> craftable,
                       Map<ItemResource, Integer> tiers, Set<BlockPos> origins, boolean pending) {
        this.pending = pending;
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
        if (maker == null) {
            // Every machine's: from the sources of that item alone, gathered once for this table.
            if (sourcesByItem == null) {
                sourcesByItem = new HashMap<>();
                craftable.forEach((resource, makers) -> makers.values()
                        .forEach(list -> sourcesByItem.computeIfAbsent(resource.getItem(), k -> new ArrayList<>()).addAll(list)));
            }
            Set<Item> from = new HashSet<>();
            for (Source source : sourcesByItem.getOrDefault(item, List.of())) {
                if (source.count() > 0 && Collections.disjoint(source.from(), avoid)) from.addAll(source.from());
            }
            return from;
        }
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

    /**
     * {@link #stock(BlockPos, boolean, boolean)} as a handler, the same one each time it's asked for the same view: a
     * Crafter checks what it could draw on many times a tick, and a table doesn't change once it's in use.
     */
    public StockHandler view(@org.jetbrains.annotations.Nullable BlockPos maker, boolean reals, boolean craftables) {
        List<Object> key = java.util.Arrays.asList(maker, reals, craftables);
        StockHandler handler = views.get(key);
        if (handler == null) views.put(key, handler = new StockHandler(stock(maker, reals, craftables)));
        return handler;
    }

    /** The views handed out, by who for and what they show. */
    private final Map<List<Object>, StockHandler> views = new HashMap<>();

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

    /** What the machine at {@code maker} could make, by the table. */
    /** Where the machines are that could make some of {@code item}. */
    public Set<BlockPos> makers(ItemResource item) {
        Map<BlockPos, List<Source>> makers = craftable.get(item);
        return makers == null ? Set.of() : Collections.unmodifiableSet(makers.keySet());
    }

    public Set<ItemResource> madeBy(BlockPos maker) {
        Set<ItemResource> made = new java.util.LinkedHashSet<>();
        for (Map.Entry<ItemResource, Map<BlockPos, List<Source>>> entry : craftable.entrySet()) {
            List<Source> sources = entry.getValue().get(maker);
            if (sources == null) continue;
            for (Source source : sources) {
                if (source.count() > 0) {
                    made.add(entry.getKey());
                    break;
                }
            }
        }
        return made;
    }

    /** Every item on offer, real and craftable apart. */
    public List<Entry> entries() {
        // A table is read every tick by whatever's looking (a terminal, a Materialiser's pipes): worked out once.
        if (entries == null) entries = List.copyOf(listEntries());
        return entries;
    }

    /** {@link #entries()}, as worked out when first asked. */
    @org.jetbrains.annotations.Nullable
    private List<Entry> entries;

    private List<Entry> listEntries() {
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
        return new OfferTable(reals ? real : Map.of(), craftables ? craftable : Map.of(), tiers, origins, pending);
    }

    public Set<BlockPos> origins() {
        return origins;
    }

    /** What's really there, as these offers were worked out from. */
    public Map<ItemResource, Long> stored() {
        return real;
    }

    /**
     * Whether a craft making {@code out} from {@code inputs} would loop: one of its inputs is only on offer as made
     * from {@code out} (nuggets made from the ingots it would make).
     */
    public boolean loops(Item out, Set<Item> inputs) {
        for (Item input : inputs) {
            if (real.getOrDefault(ItemResource.of(input.getDefaultInstance()), 0L) > 0) continue;
            if (madeFrom(input, null, Set.of()).contains(out)) return true;
        }
        return false;
    }

    /** These offers, with what's really there as {@code stored} instead (looked at now, the rest worked out before). */
    public OfferTable withStored(Map<ItemResource, Long> stored) {
        return new OfferTable(stored, craftable, tiers, origins, pending);
    }

    /**
     * What's really on {@code network} (its controller at {@code at}) now: the first step of working its offers out,
     * alone. A look over its members' slots, without asking any machine what it could make.
     */
    public static Map<ItemResource, Long> storedNow(LogisticsController network, BlockPos at, List<LogisticsMember> members) {
        Builder builder = new Builder(network, at, members);
        builder.scan();
        return Map.copyOf(builder.real);
    }

    /** Works out the table for {@code network} (its controller at {@code at}), whose members are {@code members}, all at once. */
    public static OfferTable build(Level level, LogisticsController network, BlockPos at, List<LogisticsMember> members) {
        Builder builder = new Builder(network, at, members);
        while (!builder.step()) {
            // on to the next machine
        }
        return builder.result();
    }

    /**
     * Works the table out a step at a time, for {@link com.kadikular.quantimium.work.WorkQueue}: first what's really
     * there (one step), then each machine's offers at each tier (a step each). A big network's offers fill in over
     * a few ticks rather than at once.
     */
    public static final class Builder {
        private record Maker(BlockPos pos, OfferMaker maker, LogisticsMember member) {}

        private final LogisticsController network;
        private final BlockPos at;
        private final List<LogisticsMember> sorted;
        private final Map<ItemResource, Long> real = new LinkedHashMap<>();
        private final Map<ItemResource, Map<BlockPos, List<Source>>> craftable = new LinkedHashMap<>();
        private final Map<ItemResource, Integer> tiers = new HashMap<>();
        private final Set<BlockPos> origins = new HashSet<>();
        private final List<Maker> makers = new ArrayList<>();
        private boolean scanned;
        private int tier = 1;
        private int next;
        @org.jetbrains.annotations.Nullable
        private OfferTable sofar;
        private final Map<BlockPos, Map<ItemResource, List<Source>>> found = new LinkedHashMap<>();
        /** Each machine's crafts behind what it was found to make, this tier. */
        private final Map<BlockPos, List<Object>> foundCrafts = new LinkedHashMap<>();
        /** The machine being asked, between looks. */
        @org.jetbrains.annotations.Nullable
        private Asking asking;
        @org.jetbrains.annotations.Nullable
        private OfferTable result;
        /** Whether it read another network's offers before they were first worked out: it should be worked out again soon. */
        private boolean early;

        /** Whether it read another network's offers before they were first worked out. */
        public boolean early() {
            return early;
        }

        public Builder(LogisticsController network, BlockPos at, List<LogisticsMember> members) {
            this.network = network;
            this.at = at;
            this.sorted = new ArrayList<>(members);
            this.sorted.sort(Comparator.comparing(LogisticsMember::inventoryPos));
            origins.add(at);
        }

        /** The table, once worked out; null till then. */
        @org.jetbrains.annotations.Nullable
        public OfferTable result() {
            return result;
        }

        /** Does a step; whether the table is worked out. */
        public boolean step() {
            if (result != null) return true;
            if (!scanned) {
                scan();
                scanned = true;
                if (makers.isEmpty()) finish();
                return result != null;
            }
            // Each machine shown what's real and what the machines could make so far, a tier at a time.
            if (next == 0) {
                sofar = new OfferTable(real, craftable, tiers, origins);
                found.clear();
                foundCrafts.clear();
            }
            // One machine's offers, a look at a time: its first look, then (if some of its crafts loop) a look again.
            Maker maker = makers.get(next);
            boolean gone = maker.maker() instanceof net.minecraft.world.level.block.entity.BlockEntity entity && entity.isRemoved();
            if (!gone) {
                if (asking == null) {
                    asking = sofar.new Asking(network, maker.pos(), maker.maker(), maker.member());
                    if (!asking.look()) return false;
                } else if (!asking.look()) {
                    return false;
                }
                found.put(maker.pos(), asking.kept);
                foundCrafts.put(maker.pos(), List.copyOf(asking.crafts));
            }
            asking = null;
            if (++next < makers.size()) return false;
            next = 0;
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
            if (!more || ++tier > TIERS) finish();
            return result != null;
        }

        private void finish() {
            result = new OfferTable(Map.copyOf(real), craftable, tiers, Set.copyOf(origins));
        }

        /**
         * Hands each machine the crafts the finished table counts it for (as last asked, with everything the network
         * offers in front of it), so what it shows it makes is what the table says. For the network's controller to
         * call once the table's in use.
         */
        public void handOut() {
            if (result == null) return;
            for (Maker maker : makers) {
                List<Object> crafts = foundCrafts.get(maker.pos());
                if (crafts == null) continue;
                if (maker.maker() instanceof net.minecraft.world.level.block.entity.BlockEntity entity && entity.isRemoved()) continue;
                maker.maker().offered(network, crafts);
            }
        }

        /** What's really there, and which machines make things to order. */
        private void scan() {
            for (LogisticsMember member : sorted) {
                if (member.role() == LogisticsMember.Role.REQUESTER) continue;
                BlockPos pos = member.inventoryPos();
                OfferTable imported = member.imports();
                if (imported != null) {
                    // Another network, through its Logistics Materialiser: unless it draws on this one, what it has is
                    // offered here too, real as real and craftable as craftable.
                    if (imported.pending) early = true;
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
                    if (maker != null && (maker.makesOnDemand(slot) || !maker.stocks(slot))) continue;
                    ItemStack stack = inventory.getStackInSlot(slot);
                    if (stack.isEmpty() || member.role() == LogisticsMember.Role.PROVIDER && !member.lets(stack)) continue;
                    real.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
                }
                if (maker != null && member.role() == LogisticsMember.Role.PROVIDER && member.offersCraftable()) makers.add(new Maker(pos, maker, member));
            }
        }
    }

    /**
     * Asking the machine at {@code pos} what it could make, shown this table, a look at a time. A craft out of
     * something that was made out of what that craft makes is a loop: the machine is asked again about those things,
     * with what was made from any of them left out, and what still loops after that is dropped. Its other crafts
     * keep everything they were shown.
     */
    private final class Asking {
        private final LogisticsController network;
        private final BlockPos pos;
        private final OfferMaker maker;
        private final LogisticsMember member;
        private final boolean chains;
        final Map<ItemResource, List<Source>> kept = new LinkedHashMap<>();
        /** The machine's crafts behind what it kept (and what its filter keeps off the network), in order. */
        final Set<Object> crafts = new java.util.LinkedHashSet<>();
        @org.jetbrains.annotations.Nullable
        private Set<Item> looped;
        private final Set<Item> avoid = new HashSet<>();
        private int attempts;

        Asking(LogisticsController network, BlockPos pos, OfferMaker maker, LogisticsMember member) {
            this.network = network;
            this.pos = pos;
            this.maker = maker;
            this.member = member;
            this.chains = member.craftsFromCraftable();
        }

        /** The machine working out what it could make, between steps. */
        @org.jetbrains.annotations.Nullable
        private OfferMaker.Making making;

        /** Asks a step's worth (a machine may take several to look through its recipes); whether it's done. */
        boolean look() {
            if (making == null) {
                // Shown its own crafts too, so one machine can chain its own steps (ingots from blocks, then leggings);
                // what would loop back through itself is caught as any loop is. Looping crafts are asked about again
                // together, with what was made from any of them left out: one more look whatever their number.
                making = maker.making(network, stock(null, true, chains, looped == null ? Set.of() : avoid), chains);
            }
            if (!making.step()) return false;
            List<OfferMaker.Made> made = making.result();
            making = null;
            if (looped == null) {
                looped = collect(made, pos, member, Set.of(), null, kept, crafts);
                avoid.addAll(looped);
                return looped.isEmpty();
            }
            Set<Item> still = collect(made, pos, member, avoid, looped, kept, crafts);
            return still.isEmpty() || !avoid.addAll(still) || ++attempts >= LOOP_RETRIES;
        }
    }

    /**
     * Keeps in {@code kept} the crafts in {@code made} (only those making {@code only}, if set) that don't loop, shown
     * the table less what's made from {@code avoid}; what the ones that loop make.
     */
    private Set<Item> collect(List<OfferMaker.Made> made, BlockPos pos, LogisticsMember member, Set<Item> avoid, @org.jetbrains.annotations.Nullable Set<Item> only,
                              Map<ItemResource, List<Source>> kept, Set<Object> crafts) {
        Map<Item, Set<Item>> below = new HashMap<>();
        Set<Item> looped = new java.util.LinkedHashSet<>();
        for (OfferMaker.Made one : made) {
            Item out = one.item().getItem();
            if (only != null && !only.contains(out)) continue;
            if (one.count() <= 0) continue;
            Set<Item> from = new HashSet<>(one.from());
            boolean loops = false;
            for (Item input : one.from()) {
                Set<Item> under = below.computeIfAbsent(input, i -> madeFrom(i, null, avoid));
                if (under.contains(out)) loops = true;
                from.addAll(under);
            }
            if (loops) {
                looped.add(out);
                continue;
            }
            // The machine makes it whatever its Receptacle lets out to the network: its own screen shows it.
            if (one.craft() != null) crafts.add(one.craft());
            if (!member.lets(one.item().toStack(1))) continue;
            Source source = new Source(one.count(), Set.copyOf(from));
            List<Source> sources = kept.computeIfAbsent(one.item(), k -> new ArrayList<>());
            if (!sources.contains(source)) sources.add(source);
        }
        return looped;
    }

    private static void offer(Map<ItemResource, Map<BlockPos, List<Source>>> craftable, ItemResource item, BlockPos pos, Source source) {
        craftable.computeIfAbsent(item, k -> new LinkedHashMap<>()).computeIfAbsent(pos, k -> new ArrayList<>()).add(source);
    }
}
