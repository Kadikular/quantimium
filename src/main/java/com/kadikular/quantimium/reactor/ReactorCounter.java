package com.kadikular.quantimium.reactor;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many of everything a Reactor could make from what it holds: the counts its screen and its
 * Materialiser Port show. Each is "if you took only this", an estimate; taking something always runs
 * the exact {@link ReactorPlanner}.
 *
 * <p>Four passes, each linear in the size of the graph:
 * <ol>
 *   <li><b>Reachable.</b> From what's held, mark every ingredient something matches and every recipe
 *   whose ingredients are all marked, and what those make, until nothing new appears. Only items
 *   reached this way can be made at all.</li>
 *   <li><b>Edges.</b> Each usable recipe links the items that feed it to the items it makes.</li>
 *   <li><b>Loops.</b> Items that can turn into each other (ingot ⇄ dust ⇄ nugget) form a group, found
 *   with Tarjan's algorithm. Between groups the graph only flows one way.</li>
 *   <li><b>Counts,</b> group by group in that one-way order. A lone item counts what it holds plus the
 *   most its best recipe can make from what's before it. In a group, each member counts its own real
 *   amount (held, plus what flowed in from before the group) plus what the <em>other</em> members' real
 *   amounts convert into, by the best path inside the group. So 100 ingots and 25 dust count as 125
 *   of each, never as 150: matter is never counted back into a form it came from. A group whose
 *   conversions multiply to more than they started with is a duplication loop in the pack; its
 *   members count only their real amounts, and it is reported.</li>
 * </ol>
 *
 * <p>Counted that way alone, a pickaxe made of planks and sticks would count the logs twice, once for
 * each. So each made item also carries a {@link Bill}: how much of each real source (a held item, or
 * a whole loop group as one pool) one of it uses. Its count is then the tighter of the two estimates,
 * and recipes that share a source share it. Something both held and made is a blend of the two, so
 * charged quartz held and charged from the plain quartz held still shares that plain quartz.
 */
public final class ReactorCounter {

    /**
     * Where recounts run: one background thread of their own, so they never wait behind chunk
     * generation in the shared pool, nor run more than one at a time however many Reactors there are.
     */
    public static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "Quantimium Reactor counts");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY + 1);
                return thread;
            });

    /** Groups bigger than this skip conversions: counted by their real amounts only, never over. */
    public static final int MAX_GROUP = 48;
    private static final double GAIN = 1.0 + 1.0E-9;

    /** The counts, and what it took to work them out. */
    public record Counts(Map<ItemResource, Long> counts, int reachable, int groups, List<ItemResource> gaining,
                         long nanos) {
        public static final Counts EMPTY = new Counts(Map.of(), 0, 0, List.of(), 0);

        public long count(ItemResource item) {
            return counts.getOrDefault(item, 0L);
        }
    }

    private final ReactorGraph graph;
    private final Object2IntOpenHashMap<ItemResource> ids = new Object2IntOpenHashMap<>();
    private final List<ItemResource> nodes = new ArrayList<>();
    private final it.unimi.dsi.fastutil.doubles.DoubleArrayList real = new it.unimi.dsi.fastutil.doubles.DoubleArrayList();
    private final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
    /** For each ingredient, the reachable items it matched. */
    private final IntArrayList[] matches;
    private final IntArrayList ready = new IntArrayList();
    /** Each item's bill: per real source, how much one of it uses. Null where it can't be said simply. */
    private Bill[] bills = new Bill[0];

    private ReactorCounter(ReactorGraph graph) {
        this.graph = graph;
        this.matches = new IntArrayList[graph.ingredients.size()];
        ids.defaultReturnValue(-1);
    }

    public static Counts count(ReactorGraph graph, Map<ItemResource, Long> stock) {
        long start = System.nanoTime();
        ReactorCounter counter = new ReactorCounter(graph);
        Counts counts = counter.run(stock);
        return new Counts(counts.counts(), counts.reachable(), counts.groups(), counts.gaining(), System.nanoTime() - start);
    }

    private int node(ItemResource item) {
        int id = ids.getInt(item);
        if (id >= 0) return id;
        id = nodes.size();
        ids.put(item, id);
        nodes.add(item);
        real.add(0.0);
        queue.enqueue(id);
        return id;
    }

    private Counts run(Map<ItemResource, Long> stock) {
        stock.forEach((item, amount) -> {
            if (amount > 0 && !item.isEmpty()) real.set(node(item), (double) amount);
        });
        reach();
        int n = nodes.size();

        // Edges and producers among what's reachable.
        IntArrayList[] edges = new IntArrayList[n];
        List<List<int[]>> producers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) producers.add(null);
        for (int r : ready) {
            ReactorGraph.Recipe recipe = graph.recipes.get(r);
            for (int o = 0; o < recipe.outputs().length; o++) {
                int out = ids.getInt(recipe.outputs()[o]);
                if (producers.get(out) == null) producers.set(out, new ArrayList<>(2));
                producers.get(out).add(new int[] {r, o});
                for (int in = 0; in < recipe.ingredients().length; in++) {
                    // Tools link too: nothing is made of them, but they must be counted first.
                    IntArrayList feeding = matches[recipe.ingredients()[in]];
                    for (int f = 0; f < feeding.size(); f++) {
                        int from = feeding.getInt(f);
                        if (edges[from] == null) edges[from] = new IntArrayList(4);
                        edges[from].add(out);
                    }
                }
            }
        }

        int[] comp = new int[n];
        List<int[]> comps = tarjan(n, edges, comp);
        double[] avail = new double[n];
        List<ItemResource> gaining = new ArrayList<>();
        bills = new Bill[n];
        double[] pools = new double[n + comps.size()];
        int groups = 0;
        // Tarjan gives groups sinks first; count from the sources.
        for (int c = comps.size() - 1; c >= 0; c--) {
            int[] members = comps.get(c);
            boolean loop = members.length > 1 || selfLoop(members[0], edges);
            if (!loop) {
                int x = members[0];
                double made = bestMade(producers.get(x), comp, -1, avail);
                avail[x] = real.getDouble(x) + made;
                if (real.getDouble(x) > 0 && made > 0) {
                    // Held and made: a blend, its held share a source of its own and the rest what
                    // making it costs. Charged quartz held and charged from plain quartz is partly
                    // that plain quartz, which anything else using it shares.
                    Bill bill = billOf(producers.get(x), avail, bills);
                    Bill blend = Bill.single(x, real.getDouble(x) / avail[x]);
                    if (bill != null && blend.add(bill, made / avail[x])) {
                        bills[x] = blend;
                        pools[x] = real.getDouble(x);
                        avail[x] = Math.min(avail[x], blend.most(pools));
                    } else {
                        bills[x] = Bill.single(x, 1.0);
                        pools[x] = avail[x];
                    }
                } else if (made <= 0) {
                    // Held only, or not made: a source of its own.
                    bills[x] = Bill.single(x, 1.0);
                    pools[x] = avail[x];
                } else {
                    Bill bill = billOf(producers.get(x), avail, bills);
                    if (bill != null) {
                        bills[x] = bill;
                        avail[x] = Math.min(avail[x], bill.most(pools));
                    }
                }
                continue;
            }
            groups++;
            double[] base = countGroup(members, comp, producers, avail, gaining);
            // What went into the group, from outside it: each member's held amount, a source of its
            // own, and what was made of things before it. Any one member can be all of that at once,
            // so its bill is the whole of it over how many of it there could be. Silicon smelted from
            // quartz and pressed into blocks and back still shares the quartz.
            Bill whole = base == null ? null : inflow(members, base, comp, producers, avail, pools);
            if (whole != null) {
                for (int m : members) {
                    if (avail[m] <= 0) continue;
                    Bill bill = new Bill();
                    if (!bill.add(whole, 1.0 / avail[m])) {
                        whole = null;
                        break;
                    }
                    bills[m] = bill;
                }
            }
            if (whole != null) {
                for (int m : members) {
                    if (avail[m] > 0) avail[m] = Math.min(avail[m], bills[m].most(pools));
                }
                continue;
            }
            // Otherwise the group is one pool, sized by its best-stocked member; a member is a share of it.
            int key = n + c;
            double pool = 0.0;
            for (int m : members) pool = Math.max(pool, avail[m]);
            pools[key] = pool;
            for (int m : members) {
                if (avail[m] > 0) bills[m] = Bill.single(key, pool / avail[m]);
            }
        }

        Map<ItemResource, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            double value = Math.floor(avail[i] + 1.0E-9);
            if (value >= 1.0) counts.put(nodes.get(i), value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) value);
        }
        return new Counts(counts, n, groups, gaining, 0);
    }

    /** Pass 1: everything reachable from what's held, and the recipes that become usable. */
    private void reach() {
        int[] missing = new int[graph.recipes.size()];
        for (int r = 0; r < missing.length; r++) missing[r] = graph.recipes.get(r).ingredients().length;
        boolean[] satisfied = new boolean[graph.ingredients.size()];
        while (!queue.isEmpty()) {
            int id = queue.dequeueInt();
            ItemResource item = nodes.get(id);
            int[] candidates = graph.ingredientsByItem.get(item.getItem());
            if (candidates == null) continue;
            ItemStack stack = item.toStack(1);
            for (int ingredient : candidates) {
                Ingredient test = graph.ingredients.get(ingredient);
                if (!test.test(stack)) continue;
                if (matches[ingredient] == null) matches[ingredient] = new IntArrayList(2);
                matches[ingredient].add(id);
                if (satisfied[ingredient]) continue;
                satisfied[ingredient] = true;
                for (int r : graph.users[ingredient]) {
                    if (--missing[r] != 0) continue;
                    ready.add(r);
                    for (ItemResource output : graph.recipes.get(r).outputs()) node(output);
                }
            }
        }
    }

    /**
     * The most {@code producers} can make, each limited by its scarcest ingredient. Items in group
     * {@code exclude} don't count as inputs: a group's own members feed it through conversions instead.
     */
    private double bestMade(List<int[]> producers, int[] comp, int exclude, double[] avail) {
        if (producers == null) return 0.0;
        double best = 0.0;
        for (int[] producer : producers) {
            ReactorGraph.Recipe recipe = graph.recipes.get(producer[0]);
            double runs = Double.MAX_VALUE;
            for (int i = 0; i < recipe.ingredients().length && runs > 0; i++) {
                double have = have(recipe.ingredients()[i], comp, exclude, avail);
                // A tool only has to be there; it doesn't limit the runs.
                if (recipe.counts()[i] == 0) {
                    if (have < 1.0) runs = 0;
                    continue;
                }
                runs = Math.min(runs, Math.floor(have / recipe.counts()[i]));
            }
            if (runs > 0 && runs < Double.MAX_VALUE) best = Math.max(best, runs * recipe.yields()[producer[1]]);
        }
        return best;
    }

    /**
     * How many of an ingredient there could be, across every item it accepts. Items made from the same
     * source can't both be had: charged certus quartz made from the plain quartz on hand is the same
     * quartz, so items whose bills share a source count as the most of them, not their sum.
     */
    private double have(int ingredient, int[] comp, int exclude, double[] avail) {
        IntArrayList feeding = matches[ingredient];
        int count = 0;
        int[] options = new int[feeding.size()];
        for (int f = 0; f < feeding.size(); f++) {
            int from = feeding.getInt(f);
            if (avail[from] > 0 && (comp == null || comp[from] != exclude)) options[count++] = from;
        }
        if (count == 1) return avail[options[0]];
        double have = 0.0;
        boolean[] done = new boolean[count];
        for (int a = 0; a < count; a++) {
            if (done[a]) continue;
            // This option's cluster: every option sharing a source with it, directly or through another.
            double most = avail[options[a]];
            done[a] = true;
            IntArrayList cluster = new IntArrayList();
            cluster.add(a);
            for (int c = 0; c < cluster.size(); c++) {
                Bill bill = bills[options[cluster.getInt(c)]];
                for (int b = 0; b < count; b++) {
                    if (done[b] || !Bill.overlap(bill, bills[options[b]])) continue;
                    done[b] = true;
                    cluster.add(b);
                    most = Math.max(most, avail[options[b]]);
                }
            }
            have += most;
        }
        return have;
    }

    /**
     * What one {@code x} costs in real sources, by the recipe that makes the most of it: each
     * ingredient's bill, scaled by how many it takes per item made. Null when that can't be said
     * simply: an ingredient that several held items could fill, or a bill with too many sources.
     */
    /**
     * Everything a loop group's {@code base} amounts came from, as one bill: a held amount is a source
     * of its own, an amount made from outside the group is what making it cost. Null if any of it
     * can't be said simply.
     */
    private Bill inflow(int[] members, double[] base, int[] comp, List<List<int[]>> producers, double[] avail,
                        double[] pools) {
        int group = comp[members[0]];
        Bill whole = new Bill();
        for (int a = 0; a < members.length; a++) {
            int x = members[a];
            double held = real.getDouble(x);
            double made = base[a] - held;
            if (held > 0) {
                pools[x] = held;
                if (!whole.add(Bill.single(x, 1.0), held)) return null;
            }
            if (made > 1.0E-9) {
                Bill bill = billOf(producers.get(x), avail, bills, comp, group);
                if (bill == null || !whole.add(bill, made)) return null;
            }
        }
        return whole;
    }

    private Bill billOf(List<int[]> producers, double[] avail, Bill[] bills) {
        return billOf(producers, avail, bills, null, -1);
    }

    /** As {@link #billOf(List, double[], Bill[])}, not counting items of group {@code exclude} as inputs. */
    private Bill billOf(List<int[]> producers, double[] avail, Bill[] bills, int[] comp, int exclude) {
        if (producers == null) return null;
        int[] best = null;
        double most = 0.0;
        for (int[] producer : producers) {
            double made = bestMade(List.<int[]>of(producer), comp, exclude, avail);
            if (made > most) {
                most = made;
                best = producer;
            }
        }
        if (best == null) return null;
        ReactorGraph.Recipe recipe = graph.recipes.get(best[0]);
        double yield = recipe.yields()[best[1]];
        Bill bill = new Bill();
        for (int i = 0; i < recipe.ingredients().length; i++) {
            if (recipe.counts()[i] == 0) continue;
            IntArrayList feeding = matches[recipe.ingredients()[i]];
            // Several items could fill it: fine if they all come from the same sources, then the
            // best-stocked one stands for them all. From separate sources it can't be said simply.
            int source = -1;
            for (int f = 0; f < feeding.size(); f++) {
                int from = feeding.getInt(f);
                if (avail[from] <= 0 || (comp != null && comp[from] == exclude)) continue;
                if (source >= 0 && !Bill.overlap(bills[source], bills[from])) return null;
                if (source < 0 || avail[from] > avail[source]) source = from;
            }
            if (source < 0 || bills[source] == null) return null;
            if (!bill.add(bills[source], recipe.counts()[i] / yield)) return null;
        }
        return bill;
    }

    /**
     * A bill of materials: for each real source (a held item, or a loop group as one pool), how much
     * of it one item uses. Kept short; past {@link #MAX_SOURCES} sources it gives up.
     */
    static final class Bill {
        static final int MAX_SOURCES = 16;
        private final int[] keys = new int[MAX_SOURCES];
        private final double[] amounts = new double[MAX_SOURCES];
        private int size;

        static Bill single(int key, double amount) {
            Bill bill = new Bill();
            bill.keys[0] = key;
            bill.amounts[0] = amount;
            bill.size = 1;
            return bill;
        }

        /** Whether two bills draw on a source in common. Unknown bills share nothing that can be seen. */
        static boolean overlap(Bill a, Bill b) {
            if (a == null || b == null) return false;
            for (int i = 0; i < a.size; i++) {
                for (int j = 0; j < b.size; j++) {
                    if (a.keys[i] == b.keys[j] && a.amounts[i] > 0 && b.amounts[j] > 0) return true;
                }
            }
            return false;
        }

        /** Adds {@code scale} of {@code other}; false when the bill would grow too long. */
        boolean add(Bill other, double scale) {
            for (int o = 0; o < other.size; o++) {
                int at = -1;
                for (int i = 0; i < size; i++) {
                    if (keys[i] == other.keys[o]) {
                        at = i;
                        break;
                    }
                }
                if (at < 0) {
                    if (size == MAX_SOURCES) return false;
                    at = size++;
                    keys[at] = other.keys[o];
                }
                amounts[at] += other.amounts[o] * scale;
            }
            return true;
        }

        /** How many it allows: the tightest of each source's pool over what one uses of it. */
        double most(double[] pools) {
            double most = Double.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                if (amounts[i] > 0) most = Math.min(most, pools[keys[i]] / amounts[i]);
            }
            return most;
        }
    }

    /**
     * Pass 4 for a loop group: real amounts, then what the other members convert into. Returns each
     * member's own amount, or null when the group counts by real amounts only (too big, or gaining).
     */
    private double[] countGroup(int[] members, int[] comp, List<List<int[]>> producers, double[] avail,
                            List<ItemResource> gaining) {
        int k = members.length;
        int group = comp[members[0]];
        double[] base = new double[k];
        for (int a = 0; a < k; a++) {
            int x = members[a];
            base[a] = real.getDouble(x) + bestMade(producers.get(x), comp, group, avail);
        }
        if (k > MAX_GROUP) {
            for (int a = 0; a < k; a++) avail[members[a]] = base[a];
            return null;
        }
        java.util.HashMap<Integer, Integer> local = new java.util.HashMap<>(k * 2);
        for (int a = 0; a < k; a++) local.put(members[a], a);

        // best[a][b]: how many of b one of a becomes, by the best path inside the group.
        double[][] best = new double[k][k];
        for (int b = 0; b < k; b++) {
            List<int[]> made = producers.get(members[b]);
            if (made == null) continue;
            for (int[] producer : made) {
                ReactorGraph.Recipe recipe = graph.recipes.get(producer[0]);
                double yield = recipe.yields()[producer[1]];
                for (int i = 0; i < recipe.ingredients().length; i++) {
                    if (recipe.counts()[i] == 0) continue;
                    IntArrayList feeding = matches[recipe.ingredients()[i]];
                    for (int f = 0; f < feeding.size(); f++) {
                        Integer a = local.get(feeding.getInt(f));
                        if (a == null || a == b) continue;
                        best[a][b] = Math.max(best[a][b], yield / recipe.counts()[i]);
                    }
                }
            }
        }
        for (int via = 0; via < k; via++) {
            for (int a = 0; a < k; a++) {
                if (best[a][via] == 0.0) continue;
                for (int b = 0; b < k; b++) {
                    double through = best[a][via] * best[via][b];
                    if (through > best[a][b]) best[a][b] = through;
                }
            }
        }
        for (int a = 0; a < k; a++) {
            if (best[a][a] > GAIN) {
                gaining.add(nodes.get(members[a]));
                for (int m = 0; m < k; m++) avail[members[m]] = base[m];
                return null;
            }
        }
        for (int b = 0; b < k; b++) {
            double total = base[b];
            for (int a = 0; a < k; a++) {
                if (a != b) total += best[a][b] * base[a];
            }
            avail[members[b]] = total;
        }
        return base;
    }

    private static boolean selfLoop(int x, IntArrayList[] edges) {
        return edges[x] != null && edges[x].contains(x);
    }

    /** Tarjan's strongly connected components, without recursion; groups come out sinks first. */
    private static List<int[]> tarjan(int n, IntArrayList[] edges, int[] comp) {
        int[] index = new int[n];
        int[] low = new int[n];
        boolean[] onStack = new boolean[n];
        java.util.Arrays.fill(index, -1);
        IntArrayList stack = new IntArrayList();
        int[] callNode = new int[n];
        int[] callEdge = new int[n];
        List<int[]> comps = new ArrayList<>();
        int counter = 0;
        for (int start = 0; start < n; start++) {
            if (index[start] >= 0) continue;
            int depth = 0;
            callNode[0] = start;
            callEdge[0] = 0;
            index[start] = low[start] = counter++;
            stack.add(start);
            onStack[start] = true;
            while (depth >= 0) {
                int v = callNode[depth];
                IntArrayList out = edges[v];
                if (out != null && callEdge[depth] < out.size()) {
                    int w = out.getInt(callEdge[depth]++);
                    if (index[w] < 0) {
                        index[w] = low[w] = counter++;
                        stack.add(w);
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
                    IntArrayList members = new IntArrayList();
                    int w;
                    do {
                        w = stack.removeInt(stack.size() - 1);
                        onStack[w] = false;
                        comp[w] = comps.size();
                        members.add(w);
                    } while (w != v);
                    comps.add(members.toIntArray());
                }
                depth--;
                if (depth >= 0) {
                    int parent = callNode[depth];
                    low[parent] = Math.min(low[parent], low[v]);
                }
            }
        }
        return comps;
    }
}
