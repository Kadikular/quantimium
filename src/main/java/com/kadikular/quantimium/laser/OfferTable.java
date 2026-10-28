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
 * ({@link OfferMaker}) is shown the real items, and what it could make from them is its craftable offer. Machines
 * making the same thing from the network's stored items count once (the most of them), as those items are only made
 * into it once; a Reactor or another network, making from its own stock, counts beside them.
 *
 * <p>Each craftable remembers every item it's made from, all the way down. A machine isn't shown what was made out of
 * what it makes: a pulveriser crushing ingots doesn't let the furnace beside it count those ingots again. Where it
 * can't tell which part came from where, it counts less rather than more.
 *
 * <p>Taking is never decided here: a pull goes to the machines in a transaction and gets what they really give.
 */
public final class OfferTable {
    public static final OfferTable EMPTY = new OfferTable(Map.of(), Map.of(), Map.of(), Set.of(), false);
    /** A network's offers before they've first been worked out: nothing yet, and another network reading it should look again soon. */
    public static final OfferTable PENDING = new OfferTable(Map.of(), Map.of(), Map.of(), Set.of(), true);

    /** One item on offer: how many are there, how many could be made, and the shortest chain that makes it. */
    public record Entry(ItemResource item, long real, long craftable, int tier) {}

    /**
     * Some of an item one machine could make, and the items its craft takes (none known: nothing). {@code pooled}: made
     * from what the network stores, which every machine here draws on alike; otherwise from a stock of its own (a
     * Reactor's, another network's).
     */
    private record Source(long count, Set<Item> from, boolean pooled) {}

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
        return craftableClear(item, maker, tainted(avoid));
    }

    /** As {@link #craftableExcept(ItemResource, BlockPos)}, leaving out the ways that take anything in {@code tainted}. */
    private long craftableClear(ItemResource item, BlockPos maker, Set<Item> tainted) {
        Map<BlockPos, List<Source>> makers = craftable.get(item);
        if (makers == null) return 0;
        long total = 0, pooled = 0;
        for (Map.Entry<BlockPos, List<Source>> entry : makers.entrySet()) {
            if (entry.getKey().equals(maker)) continue;
            // One machine's crafts of the same thing are alternatives, not more: the most of them. So are machines
            // making it from the same stored items (a row of crafting tables): those items are only made into it once.
            long most = 0, mostPooled = 0;
            for (Source source : entry.getValue()) {
                if (!clean(source, tainted)) continue;
                if (source.pooled()) mostPooled = Math.max(mostPooled, source.count());
                else most = Math.max(most, source.count());
            }
            total += most;
            pooled = Math.max(pooled, mostPooled);
        }
        return total + pooled;
    }

    /** Every source of {@code item} (any machine, any components): gathered once for this table. */
    private List<Source> sourcesOf(Item item) {
        if (sourcesByItem == null) {
            sourcesByItem = new HashMap<>();
            craftable.forEach((resource, makers) -> makers.values()
                    .forEach(list -> sourcesByItem.computeIfAbsent(resource.getItem(), k -> new ArrayList<>()).addAll(list)));
        }
        return sourcesByItem.getOrDefault(item, List.of());
    }

    /**
     * Whether {@code input}, as it could be made here (by the ways not made of anything in {@code avoid}), is made of
     * {@code out}, all the way down: a craft of {@code out} from it would loop.
     */
    private boolean madeOf(Item input, Item out, Set<Item> tainted) {
        Reach reach = reach();
        // Nothing left out: every way of making it counts, and what it's made of is what it reaches.
        if (tainted.isEmpty()) return reach.reaches(input, out);
        for (Source source : sourcesOf(input)) {
            if (source.count() <= 0 || !clean(source, tainted)) continue;
            for (Item from : source.from()) if (from == out || reach.reaches(from, out)) return true;
        }
        return false;
    }

    /** Whether {@code source} takes nothing in {@code tainted} (see {@link #tainted}). */
    private static boolean clean(Source source, Set<Item> tainted) {
        if (tainted.isEmpty() || source.from().isEmpty()) return true;
        for (Item from : source.from()) if (tainted.contains(from)) return false;
        return true;
    }

    /** {@code avoid} and everything made of any of it, all the way down; nothing for nothing: worked out once a caller. */
    private Set<Item> tainted(Set<Item> avoid) {
        return avoid.isEmpty() ? Set.of() : leadingTo(avoid);
    }

    /** {@code targets}, and every item that could be made of any of them, all the way down: worked out once each. */
    private Set<Item> leadingTo(Set<Item> targets) {
        return leading.computeIfAbsent(Set.copyOf(targets), key -> {
            Set<Item> all = new HashSet<>(key);
            all.addAll(reach().leadingTo(key));
            return all;
        });
    }

    private final Map<Set<Item>, Set<Item>> leading = new HashMap<>();

    /**
     * What each item could be made of, all the way down, through every way of making it here: worked out once for
     * the table, over the items and the ways each is made (a cycle of things made of each other taken as one).
     * Each craftable keeps only what its own craft takes; this answers the rest.
     */
    private Reach reach() {
        if (reach == null) reach = new Reach();
        return reach;
    }

    @org.jetbrains.annotations.Nullable
    private Reach reach;

    private final class Reach {
        private final Map<Item, Integer> ids = new HashMap<>();
        private final List<Item> items = new ArrayList<>();
        private final int[] group;
        /** By group: every item reachable in one step or more. */
        private final long[][] reaches;

        Reach() {
            List<List<Integer>> edges = new ArrayList<>();
            craftable.forEach((resource, makers) -> {
                int at = id(resource.getItem(), edges);
                for (List<Source> sources : makers.values()) {
                    for (Source source : sources) {
                        if (source.count() <= 0) continue;
                        for (Item from : source.from()) {
                            int to = id(from, edges);
                            edges.get(at).add(to);
                        }
                    }
                }
            });
            int n = items.size();
            group = new int[n];
            // Groups of items made of each other (Tarjan's, without recursion), found leaves first.
            int[] index = new int[n], low = new int[n];
            boolean[] onStack = new boolean[n];
            java.util.Arrays.fill(index, -1);
            int[] stack = new int[n];
            int top = 0, counter = 0, groups = 0;
            List<int[]> members = new ArrayList<>();
            int[] callNode = new int[n], callEdge = new int[n];
            for (int start = 0; start < n; start++) {
                if (index[start] != -1) continue;
                int depth = 0;
                callNode[0] = start;
                callEdge[0] = 0;
                index[start] = low[start] = counter++;
                stack[top++] = start;
                onStack[start] = true;
                while (depth >= 0) {
                    int v = callNode[depth];
                    List<Integer> out = edges.get(v);
                    if (callEdge[depth] < out.size()) {
                        int w = out.get(callEdge[depth]++);
                        if (index[w] == -1) {
                            index[w] = low[w] = counter++;
                            stack[top++] = w;
                            onStack[w] = true;
                            depth++;
                            callNode[depth] = w;
                            callEdge[depth] = 0;
                        } else if (onStack[w]) {
                            low[v] = Math.min(low[v], index[w]);
                        }
                        continue;
                    }
                    if (low[v] == index[v]) {
                        int size = 0;
                        int[] found = new int[top];
                        int w;
                        do {
                            w = stack[--top];
                            onStack[w] = false;
                            group[w] = groups;
                            found[size++] = w;
                        } while (w != v);
                        members.add(java.util.Arrays.copyOf(found, size));
                        groups++;
                    }
                    depth--;
                    if (depth >= 0) {
                        int parent = callNode[depth];
                        low[parent] = Math.min(low[parent], low[v]);
                    }
                }
            }
            // Groups come leaves first: each one's reach is its steps' groups and theirs.
            int words = (n + 63) >>> 6;
            reaches = new long[groups][];
            for (int g = 0; g < groups; g++) {
                long[] bits = new long[words];
                for (int v : members.get(g)) {
                    for (int w : edges.get(v)) {
                        int h = group[w];
                        if (h == g) {
                            for (int m : members.get(g)) bits[m >>> 6] |= 1L << m;
                        } else {
                            long[] theirs = reaches[h];
                            for (int i = 0; i < words; i++) bits[i] |= theirs[i];
                            for (int m : members.get(h)) bits[m >>> 6] |= 1L << m;
                        }
                    }
                }
                reaches[g] = bits;
            }
        }

        private int id(Item item, List<List<Integer>> edges) {
            Integer known = ids.get(item);
            if (known != null) return known;
            int id = items.size();
            ids.put(item, id);
            items.add(item);
            edges.add(new ArrayList<>(2));
            return id;
        }

        /** Whether {@code from} could be made of {@code to}, all the way down (a step or more). */
        boolean reaches(Item from, Item to) {
            Integer a = ids.get(from), b = ids.get(to);
            if (a == null || b == null) return false;
            return (reaches[group[a]][b >>> 6] & (1L << b)) != 0;
        }

        /** Every item that could be made of any of {@code targets}, all the way down. */
        Set<Item> leadingTo(Set<Item> targets) {
            List<Integer> wanted = new ArrayList<>();
            for (Item target : targets) {
                Integer id = ids.get(target);
                if (id != null) wanted.add(id);
            }
            Set<Item> out = new HashSet<>();
            if (wanted.isEmpty()) return out;
            for (int v = 0; v < items.size(); v++) {
                long[] bits = reaches[group[v]];
                for (int w : wanted) {
                    if ((bits[w >>> 6] & (1L << w)) != 0) {
                        out.add(items.get(v));
                        break;
                    }
                }
            }
            return out;
        }
    }

    /**
     * What a machine drawing on the network sees: the real items and, with {@code craftables}, what the other
     * machines could make, leaving out what the machine at {@code maker} would (null for none).
     */
    public Map<ItemResource, Long> stock(BlockPos maker, boolean reals, boolean craftables) {
        Map<ItemResource, Long> stock = new LinkedHashMap<>();
        if (reals) stock.putAll(real);
        if (craftables) {
            for (ItemResource item : craftable.keySet()) {
                long more = craftableExcept(item, maker);
                if (more > 0) stock.merge(item, more, Long::sum);
            }
        }
        return stock;
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

    /**
     * Every item something on offer here is made from, all the way down: what, if its count moves, could change what
     * could be made. Worked out once.
     */
    public Set<Item> inputs() {
        if (inputs == null) {
            Set<Item> all = new HashSet<>();
            for (Map<BlockPos, List<Source>> makers : craftable.values()) {
                for (List<Source> sources : makers.values()) for (Source source : sources) all.addAll(source.from());
            }
            inputs = all;
        }
        return inputs;
    }

    @org.jetbrains.annotations.Nullable
    private Set<Item> inputs;

    /** What every machine is shown on its first look, made or not: the same each time it's asked of this table. */
    private Map<ItemResource, Long> shown(boolean craftables) {
        Map<ItemResource, Long> kept = craftables ? shownWithMade : shownStored;
        if (kept == null) {
            kept = new Shown(stock(null, true, craftables));
            if (craftables) shownWithMade = kept;
            else shownStored = kept;
        }
        return kept;
    }

    @org.jetbrains.annotations.Nullable
    private Map<ItemResource, Long> shownWithMade, shownStored;

    /** A stock every machine is shown alike, which keeps one handler for all of them to read it through. */
    public static final class Shown extends java.util.AbstractMap<ItemResource, Long> {
        private final Map<ItemResource, Long> stock;
        @org.jetbrains.annotations.Nullable
        private StockHandler handler;

        Shown(Map<ItemResource, Long> stock) {
            this.stock = stock;
        }

        /** The one handler for it. */
        public StockHandler handler() {
            if (handler == null) handler = new StockHandler(stock);
            return handler;
        }

        @Override
        public Long get(Object key) {
            return stock.get(key);
        }

        /** Looked up, not looked through: a network's stock is thousands of kinds. */
        @Override
        public boolean containsKey(Object key) {
            return stock.containsKey(key);
        }

        @Override
        public int size() {
            return stock.size();
        }

        @Override
        public Set<Map.Entry<ItemResource, Long>> entrySet() {
            return java.util.Collections.unmodifiableMap(stock).entrySet();
        }
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
            ItemResource resource = ItemResource.of(input.getDefaultInstance());
            if (real.getOrDefault(resource, 0L) > 0) continue;
            if (!madeOf(input, out, Set.of())) continue;
            // Some of it made without going through it (ingots from blocks, as well as from the dust it would make of
            // ingots): that's what it would use, and no loop.
            if (craftableExcept(resource, null, Set.of(out)) > 0) continue;
            return true;
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
     * there (one step), then each machine's offers (a step each), made from what's really there: a chain of crafts is
     * a Reactor's to plan. A big network's offers fill in over a few ticks rather than at once.
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
        private int next;
        @org.jetbrains.annotations.Nullable
        private OfferTable sofar;
        private final Map<BlockPos, Map<ItemResource, List<Source>>> found = new LinkedHashMap<>();
        /** Each machine's crafts behind what it was found to make. */
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
            // Each machine shown what's real (and what other networks and Reactors offer).
            if (next == 0) sofar = new OfferTable(real, craftable, tiers, origins);
            // One machine's offers, a step at a time.
            Maker maker = makers.get(next);
            boolean gone = maker.maker() instanceof net.minecraft.world.level.block.entity.BlockEntity entity && entity.isRemoved();
            if (!gone) {
                if (asking == null) asking = sofar.new Asking(network, maker.maker(), maker.member());
                if (!asking.look()) return false;
                found.put(maker.pos(), asking.kept);
                foundCrafts.put(maker.pos(), List.copyOf(asking.crafts));
            }
            asking = null;
            if (++next < makers.size()) return false;
            for (Map.Entry<BlockPos, Map<ItemResource, List<Source>>> made : found.entrySet()) {
                for (Map.Entry<ItemResource, List<Source>> offer : made.getValue().entrySet()) {
                    craftable.computeIfAbsent(offer.getKey(), k -> new LinkedHashMap<>()).put(made.getKey(), offer.getValue());
                    tiers.merge(offer.getKey(), 1, Math::min);
                }
            }
            finish();
            return true;
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
                        // Fluids aren't carried between networks (yet).
                        if (item.is(com.kadikular.quantimium.init.ModItems.FLUID_ENTRY.get())) return;
                        if (member.role() != LogisticsMember.Role.PROVIDER || member.lets(item.toStack(1))) real.merge(item, amount, Long::sum);
                    });
                    if (member.offersCraftable()) {
                        for (Entry entry : imported.entries()) {
                            if (entry.craftable() <= 0) continue;
                            if (member.role() == LogisticsMember.Role.PROVIDER && !member.lets(entry.item().toStack(1))) continue;
                            offer(craftable, entry.item(), pos, new Source(entry.craftable(), Set.of(), false));
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
                            offer(craftable, item, pos, new Source(both[1], Set.of(), false));
                            tiers.merge(item, 1, Math::min);
                        }
                    });
                    continue;
                }
                // Fluids, as fluid entries counted in mB.
                net.neoforged.neoforge.fluids.capability.IFluidHandler tanks = member.fluids();
                if (tanks != null) {
                    for (int tank = 0; tank < tanks.getTanks(); tank++) {
                        net.neoforged.neoforge.fluids.FluidStack there = tanks.getFluidInTank(tank);
                        if (there.isEmpty() || member.role() == LogisticsMember.Role.PROVIDER && !member.lets(there)) continue;
                        real.merge(ItemResource.of(com.kadikular.quantimium.recipe.FluidEntry.of(there)), (long) there.getAmount(), Long::sum);
                    }
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

    /** Asking the machine at {@code pos} what it could make from what's really there, a step at a time. */
    private final class Asking {
        private final LogisticsController network;
        private final LogisticsMember member;
        private final OfferMaker maker;
        final Map<ItemResource, List<Source>> kept = new LinkedHashMap<>();
        /** The machine's crafts behind what it kept (and what its filter keeps off the network), in order. */
        final Set<Object> crafts = new java.util.LinkedHashSet<>();
        /** The machine working out what it could make, between steps. */
        @org.jetbrains.annotations.Nullable
        private OfferMaker.Making making;

        Asking(LogisticsController network, OfferMaker maker, LogisticsMember member) {
            this.network = network;
            this.maker = maker;
            this.member = member;
        }

        /** Asks a step's worth (a machine may take several to look through its recipes); whether it's done. */
        boolean look() {
            // Every machine is shown the same stock: worked out once for them all.
            if (making == null) making = maker.making(network, shown(false), false);
            if (!making.step()) return false;
            for (OfferMaker.Made one : making.result()) {
                if (one.count() <= 0) continue;
                // The machine makes it whatever its Receptacle lets out to the network: its own screen shows it.
                if (one.craft() != null) crafts.add(one.craft());
                if (!member.lets(one.item().toStack(1))) continue;
                Source source = new Source(one.count(), Set.copyOf(one.from()), true);
                List<Source> sources = kept.computeIfAbsent(one.item(), k -> new ArrayList<>());
                if (!sources.contains(source)) sources.add(source);
            }
            making = null;
            return true;
        }
    }

    private static void offer(Map<ItemResource, Map<BlockPos, List<Source>>> craftable, ItemResource item, BlockPos pos, Source source) {
        craftable.computeIfAbsent(item, k -> new LinkedHashMap<>()).computeIfAbsent(pos, k -> new ArrayList<>()).add(source);
    }
}
