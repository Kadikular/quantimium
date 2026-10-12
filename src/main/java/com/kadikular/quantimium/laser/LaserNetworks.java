package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.debug.TickProfiler;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Every laser network in a level: the nodes, the beams between them, and the power they move.
 *
 * <p>Fast by construction. A beam is traced when its node arrives, when the network changes, and
 * again every {@link #RECHECK_TICKS} ticks to see if something has been built across it, a node a
 * tick at a time; it's never traced every tick. The graph is rebuilt only when a beam has changed.
 * Each tick then just asks what the blocks at the beams' ends would take and moves that much.
 *
 * <p>Power goes where it's asked for. Every source sends only what the blocks its beams reach can take,
 * shared between the network's sources in proportion to what each has. Each relay a beam passes on
 * the way takes {@link LaserTuning#HOP_TAX} of what passes, and that becomes flux where the relay stands.
 *
 * <p>What light carries besides power ({@link Light}: its reach, and the actions its lenses gave it) is
 * worked out as it's traced, from the sources out; each action ({@link LensAction}) is a sink of its own,
 * paid for by the sources behind its lens.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class LaserNetworks {

    /** How often each node's beams are traced again, spread so a few are checked each tick. */
    public static final int RECHECK_TICKS = 10;
    /** How often what the relays lost is put into the field, and beams' brightness is sent to clients. */
    private static final int SETTLE_TICKS = 20;

    private static final Map<Level, LaserNetworks> LEVELS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, LaserNode> nodes = new LinkedHashMap<>();
    private final Map<BlockPos, List<LaserBeam>> beams = new HashMap<>();
    /** For each node, the nodes with a beam ending on it: kept as beams are traced, so nothing scans every beam to find what reaches a node. */
    private final Map<BlockPos, List<BlockPos>> into = new HashMap<>();
    /** How far each node's light reaches: its own range, or for a node that doesn't refocus, what's left of the light reaching it. */
    private final Map<BlockPos, Integer> ranges = new HashMap<>();
    private List<Network> networks = List.of();
    private boolean dirty;
    /** What each relay has lost to the hop tax since the last settle, to put into the field: one cell each, held by the hops through it. */
    private final Map<BlockPos, long[]> lost = new HashMap<>();
    /** What each of a node's beams has carried since the last settle: one array a node, held by the hops along it. */
    private final Map<BlockPos, long[]> carried = new HashMap<>();
    /** The nodes to recheck on each tick of {@link #RECHECK_TICKS}, so a tick looks only at its own. */
    private List<List<LaserNode>> rechecks = List.of();
    /** Each action a beam carries, by its node, beam and action: what it does, and what it has done towards it. */
    private final Map<LensAction.Key, LensAction> actions = new LinkedHashMap<>();
    /** Where each action's power comes from: its network and the routes from the sources paying for it. */
    private final Map<LensAction.Key, ActionSink> actionSinks = new HashMap<>();
    private record ActionSink(Network network, Sink sink) {}
    /** The light at each node's core, as last worked out: what leaves it, but for lenses on one face only. */
    private final Map<BlockPos, Light> lights = new HashMap<>();
    /** The light leaving each node by each face with lenses of its own, worked out as asked and kept till the next rebuild. */
    private final Map<BlockPos, Map<Direction, Light>> faceLights = new HashMap<>();
    /** The light reaching each cavity, which it remembers when it's filled. */
    private final Map<BlockPos, Light> cavityIn = new HashMap<>();
    /** What redstone can switch, and whether each was on when the network was last built. */
    private Map<LaserNode, Integer> switched = Map.of();
    /** The nodes light ran through at the last settle. */
    private Set<BlockPos> litNow = Set.of();

    private LaserNetworks(ServerLevel level) {
        this.level = level;
    }

    public static LaserNetworks of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, key -> new LaserNetworks(level));
    }

    /** A level going away takes its networks with it: each holds its level, so the weak map alone wouldn't let go. */
    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) LEVELS.remove(level);
    }

    // ---- nodes come and go ----

    public static void add(Level level, LaserNode node) {
        if (level instanceof ServerLevel server) of(server).put(node);
    }

    public static void remove(Level level, LaserNode node) {
        if (level instanceof ServerLevel server) of(server).drop(node);
    }

    /** A node's outputs or switch changed: trace again. */
    public static void changed(Level level) {
        if (level instanceof ServerLevel server) of(server).dirty = true;
    }

    private void put(LaserNode node) {
        nodes.put(node.nodePos().immutable(), node);
        dirty = true;
    }

    private void drop(LaserNode node) {
        BlockPos pos = node.nodePos();
        if (nodes.get(pos) == node) {
            nodes.remove(pos);
            beams.remove(pos);
            into.values().forEach(list -> list.remove(pos));
            carried.remove(pos);
            dirty = true;
        }
    }

    /** For tests: the action {@code action} at {@code node} (beam -1 for an area): its whole cut's cost, and what it asks this tick. */
    public long[] costs(BlockPos node, int beam, Lens action) {
        LensAction found = actions.get(new LensAction.Key(node, beam, action));
        return found == null ? new long[] {-1, -1} : new long[] {found.cutCost(), found.demand()};
    }

    /** For tests: every Veil Thread being drawn by mirrored Pickup is {@code ticksLeft} ticks from done. */
    public void nearlyDrawn(int ticksLeft) {
        for (LensAction action : actions.values()) action.nearlyDrawn(ticksLeft);
    }

    /** Puts what the relays have lost to the hop tax into the field now, rather than at the next settle: for tests, before they clear it. */
    public void flushLosses() {
        lost.forEach((pos, fe) -> {
            if (fe[0] > 0 && nodes.containsKey(pos)) QuantumFlux.emitFromEnergy(level, pos, fe[0]);
            fe[0] = 0;
        });
    }

    /** The beams leaving {@code pos}, as last traced, with what they carried: for readouts. */
    public List<LaserBeam> beamsOf(BlockPos pos) {
        return beams.getOrDefault(pos, List.of());
    }

    /**
     * For the Spectroscope: what the light leaving {@code pos} carries, line by line. Empty if nothing
     * there is a laser block.
     */
    public List<net.minecraft.network.chat.Component> describe(BlockPos pos) {
        LaserNode node = nodes.get(pos);
        if (node == null) return List.of();
        List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
        net.minecraft.ChatFormatting head = net.minecraft.ChatFormatting.AQUA;
        net.minecraft.ChatFormatting body = net.minecraft.ChatFormatting.GRAY;
        Light light = lights.get(pos);
        if (light == null) {
            lines.add(net.minecraft.network.chat.Component.translatable("message.quantimium.spectroscope.dark").withStyle(body));
            return lines;
        }
        lines.add(net.minecraft.network.chat.Component.translatable("message.quantimium.spectroscope.reach", light.reach(),
                light.feeders().size()).withStyle(head));
        if (light.streams().isEmpty()) {
            lines.add(net.minecraft.network.chat.Component.translatable("message.quantimium.spectroscope.plain").withStyle(body));
        }
        for (Light.Stream stream : light.acting()) {
            net.minecraft.network.chat.MutableComponent modifiers = net.minecraft.network.chat.Component.empty();
            for (Lens lens : Lens.values()) {
                if (lens.action || lens == stream.action() || !stream.has(lens)) continue;
                modifiers.append(" + ").append(net.minecraft.network.chat.Component.translatable(
                        lens.itemKey()));
            }
            int collectors = 0;
            for (LensAction action : actions.values()) {
                if (action.key().node().equals(pos) && action.key().action() == stream.action()) collectors = action.collectors().size();
            }
            lines.add(net.minecraft.network.chat.Component.translatable("message.quantimium.spectroscope.stream",
                    net.minecraft.network.chat.Component.translatable(stream.action().itemKey()),
                    modifiers, stream.payers().size(), collectors).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
        for (LaserBeam beam : beamsOf(pos)) {
            lines.add(net.minecraft.network.chat.Component.translatable("message.quantimium.spectroscope.beam",
                    beam.face().getSerializedName(), beam.length(), beam.end().name().toLowerCase(java.util.Locale.ROOT),
                    String.format(java.util.Locale.ROOT, "%,d", beam.flow())).withStyle(body));
        }
        return lines;
    }

    /** What the node at {@code pos} sent, or passed on, a tick on average over the last second. */
    public long throughput(BlockPos pos) {
        long total = 0;
        for (LaserBeam beam : beamsOf(pos)) total += beam.flow();
        return total;
    }

    // ---- the network ----

    /** One connected set of nodes: its sources, and each block it charges with how to reach it; whether any of it is a cavity. */
    private record Network(List<LaserNode> sources, List<Sink> sinks, boolean cavities, long[] left) {}

    /**
     * A block a beam charges: where, from which face, through which beams from a source (each as its
     * node and face index), and what fraction of what leaves the source arrives.
     */
    private record Sink(@Nullable BlockPos pos, Direction face, List<Route> routes, @Nullable LaserNode buffer,
                        @Nullable BlockCapabilityCache<EnergyHandler, @Nullable Direction> energy, @Nullable LensAction action) {}

    /** One source's best way to a block: the source (and its place in the network's list), the beams from it, and what arrives of what leaves it. */
    private record Route(BlockPos source, int index, List<Hop> path, double efficiency) {}

    /** Adds {@code source}'s route to the block at {@code pos}, if it's that source's best. */
    private void route(Map<Object, Sink> sinks, BlockPos pos, Direction face, @Nullable LaserNode buffer,
                       BlockPos source, int index, List<Hop> path, double efficiency) {
        route(sinks, pos, pos, face, buffer, null, source, index, path, efficiency);
    }

    /** Adds {@code source}'s route to a sink (a block, or a beam's lens action, by {@code key}), if it's that source's best. */
    private void route(Map<Object, Sink> sinks, Object key, @Nullable BlockPos pos, Direction face, @Nullable LaserNode buffer,
                       @Nullable LensAction action, BlockPos source, int index, List<Hop> path, double efficiency) {
        Sink sink = sinks.get(key);
        List<Route> routes = sink == null ? new ArrayList<>() : new ArrayList<>(sink.routes());
        routes.removeIf(route -> route.source().equals(source) && route.efficiency() < efficiency);
        if (routes.stream().noneMatch(route -> route.source().equals(source))) routes.add(new Route(source, index, List.copyOf(path), efficiency));
        // The block's energy, looked up once and kept: the cache follows the block if it changes.
        BlockCapabilityCache<EnergyHandler, @Nullable Direction> energy = sink != null ? sink.energy()
                : buffer != null || action != null || pos == null ? null : BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos, face);
        sinks.put(key, new Sink(pos, face, routes, buffer, energy, action));
    }

    /**
     * A beam on a sink's path: its node, which of the node's beams, the relay it leaves from (null at the
     * source), and where what it carries and what the relay loses are counted.
     */
    private record Hop(BlockPos node, int beam, @Nullable LaserNode relay, long[] carried, long @Nullable [] lost) {}

    /** For tests: how many times it's rebuilt, so a test can see it settle. */
    private long rebuilds;

    public long rebuilds() {
        return rebuilds;
    }

    private void rebuild() {
        dirty = false;
        rebuilds++;
        Map<BlockPos, List<LaserBeam>> before = new HashMap<>(beams);
        // What each beam has carried since the last settle is kept, so a rebuild (a block broken, say)
        // doesn't make the second's average look like nothing passed.
        Map<BlockPos, long[]> carriedBefore = new HashMap<>(carried);
        beams.clear();
        into.clear();
        carried.clear();
        // What the relays still standing lost so far goes into the field.
        lost.forEach((pos, fe) -> {
            if (fe[0] > 0 && nodes.containsKey(pos)) QuantumFlux.emitFromEnergy(level, pos, fe[0]);
        });
        lost.clear();
        ranges.clear();
        lights.clear();
        faceLights.clear();
        cavityIn.clear();
        actionSinks.clear();
        // Light from each source, then on through each relay it reaches, at the reach of the light that
        // gets there; again wherever more light arrives. Light only adds up, so this settles.
        ArrayDeque<BlockPos> work = new ArrayDeque<>();
        // What redstone switches, and how each stood now: watched each tick, rebuilt when one changes.
        Map<LaserNode, Integer> watching = new LinkedHashMap<>();
        for (LaserNode node : nodes.values()) {
            if (node.switches()) watching.put(node, node.switchState());
        }
        switched = watching;
        for (LaserNode node : nodes.values()) {
            if (!node.generates()) continue;
            // A switched-off source gives out no light: none of its reach, nor what it does.
            if (!node.enabled()) {
                traceAt(node, node.range(), before);
                continue;
            }
            Light light = node.emits();
            setLight(node.nodePos(), light);
            traceAt(node, light.reach(), before);
            hits(node.nodePos(), work);
        }
        int steps = 0;
        int cap = Math.max(64, nodes.size() * 16);
        while (!work.isEmpty() && steps++ < cap) {
            BlockPos at = work.poll();
            LaserNode node = nodes.get(at);
            // A switched-off relay passes nothing on: the light beyond it is only what reaches it some other way.
            if (node == null || !node.relays() || !node.enabled()) continue;
            Light in = incoming(node);
            if (in == null) continue;
            Light out = node.isolates() ? in.refocused().isolated() : node.refocuses() ? in.refocused().through(node.lenses()) : in;
            if (node.rifts()) out = out.throughRift();
            // One that gives out light of its own too (a Solar Emitter): its own joins what it passes on.
            if (node.generates()) out = Light.join(out, node.emits());
            if (out.equals(lights.get(at))) continue;
            setLight(at, out);
            traceAt(node, out.reach(), before);
            hits(at, work);
        }
        // Relays no light reaches still show where light would go, at their own range.
        for (LaserNode node : nodes.values()) {
            if (node.relays() && !lights.containsKey(node.nodePos())) traceAt(node, node.range(), before);
        }
        for (LaserNode node : nodes.values()) {
            if (!node.buffers()) continue;
            Light in = incoming(node);
            if (in != null) cavityIn.put(node.nodePos(), in.refocused());
        }
        actionsUp();
        for (Map.Entry<BlockPos, List<LaserBeam>> entry : beams.entrySet()) {
            long[] sums = new long[entry.getValue().size()];
            long[] had = carriedBefore.get(entry.getKey());
            if (had != null) System.arraycopy(had, 0, sums, 0, Math.min(had.length, sums.length));
            carried.put(entry.getKey(), sums);
        }
        List<List<LaserNode>> buckets = new ArrayList<>();
        for (int i = 0; i < RECHECK_TICKS; i++) buckets.add(new ArrayList<>());
        for (LaserNode node : nodes.values()) buckets.get(Math.floorMod(node.nodePos().hashCode(), RECHECK_TICKS)).add(node);
        rechecks = buckets;
        // Every node reached from each source, nearest first: each hop costs the tax, so fewest hops is best.
        List<Network> built = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        for (LaserNode start : nodes.values()) {
            if (!start.generates() || claimed.contains(start.nodePos())) continue;
            List<LaserNode> sources = new ArrayList<>();
            Map<Object, Sink> sinks = new LinkedHashMap<>();
            // The sources joined to this one through shared relays share the network.
            for (LaserNode source : component(start)) {
                if (!source.generates()) continue;
                claimed.add(source.nodePos());
                sources.add(source);
                reach(source, sources.size() - 1, sinks);
            }
            boolean cavities = sources.stream().anyMatch(LaserNode::buffers) || sinks.values().stream().anyMatch(sink -> sink.buffer() != null);
            Network network = new Network(sources, List.copyOf(sinks.values()), cavities, new long[sources.size()]);
            built.add(network);
            for (Sink sink : network.sinks()) {
                if (sink.action() != null) actionSinks.put(sink.action().key(), new ActionSink(network, sink));
            }
        }
        networks = built;
        limited = nodes.values().stream().anyMatch(node -> node.relays() && node.passLimit() != Long.MAX_VALUE);
    }

    /** Traces {@code node}'s beams at {@code range}; a beam that runs as it did keeps its brightness, so a change elsewhere doesn't make it flicker. */
    private void traceAt(LaserNode node, int range, Map<BlockPos, List<LaserBeam>> before) {
        ranges.put(node.nodePos(), range);
        Light light = lights.get(node.nodePos());
        List<LaserBeam> traced = trace(node, range, light == null ? 0 : light.marks());
        // What this node's beams end on, now: off the index for the old beams, on for the new.
        for (LaserBeam old : beams.getOrDefault(node.nodePos(), List.of())) {
            if (old.end() == LaserBeam.End.NODE) {
                List<BlockPos> list = into.get(old.hit());
                if (list != null) list.remove(node.nodePos());
            }
        }
        for (LaserBeam beam : traced) {
            if (beam.end() == LaserBeam.End.NODE) into.computeIfAbsent(beam.hit(), key -> new ArrayList<>()).add(node.nodePos());
        }
        List<LaserBeam> was = before.getOrDefault(node.nodePos(), List.of());
        for (int i = 0; i < traced.size(); i++) {
            LaserBeam beam = traced.get(i);
            LaserBeam same = null;
            for (LaserBeam old : was) {
                if (old.sameRun(beam)) same = old;
            }
            // A beam that now runs differently (a block broken at its end) keeps how bright it was until
            // the next settle says otherwise, so it doesn't blink out meanwhile.
            if (same == null && i < was.size() && was.get(i).face() == beam.face()) same = was.get(i);
            if (same != null) traced.set(i, beam.withFlow(same.flow()).withLive(same.live()));
        }
        beams.put(node.nodePos(), traced);
    }

    /**
     * Each beam takes the look of the light it carries, and gets an action for each stream (kept from
     * before, if it was there), with the collecting Laser Ports up that stream's light; then every node shows its beams.
     */
    private void actionsUp() {
        Map<LensAction.Key, LensAction> was = new HashMap<>(actions);
        actions.clear();
        for (LaserNode node : nodes.values()) {
            BlockPos pos = node.nodePos();
            Light core = lights.get(pos);
            List<LaserBeam> list = beams.getOrDefault(pos, List.of());
            if (core != null) {
                // The Laser Ports up each stream's light: the same for every beam it's on.
                Map<Set<BlockPos>, List<BlockPos>> collectors = new HashMap<>();
                Map<Set<BlockPos>, List<BlockPos>> senders = new HashMap<>();
                List<LaserBeam> lensed = new ArrayList<>(list.size());
                // Each beam takes the look, and the actions, of its own light: a face with lenses of its own differs.
                for (int i = 0; i < list.size(); i++) {
                    LaserBeam beam = list.get(i);
                    Light light = lightOf(pos, beam);
                    lensed.add(light == null || light.packed() == Lens.NONE ? beam : beam.withLenses(light.packed()));
                    if (light == null || light.packed() == Lens.NONE) continue;
                    for (Light.Stream stream : streamsOf(light)) {
                        LensAction.Key key = new LensAction.Key(pos, i, stream.action());
                        LensAction action = was.getOrDefault(key, new LensAction(key));
                        int solids = stream.action() == Lens.PHANTOM ? beam.solidsThrough(level) : 0;
                        action.update(beam, stream, collectors.computeIfAbsent(stream.payers(), payers -> upFor(pos, payers, LaserNode::collects)),
                                senders.computeIfAbsent(stream.payers(), payers -> upFor(pos, payers, LaserNode::sends)),
                                null, light.reach(), light.marks(), solids);
                        action.worldSteps(worldSteps(light));
                        actions.put(key, action);
                    }
                }
                list = lensed;
                beams.put(pos, list);
                // An area prism's light works on its area: one action each, as beam -1.
                if (node.area() != null && core.packed() != Lens.NONE) {
                    for (Light.Stream stream : core.acting()) {
                        LensAction.Key key = new LensAction.Key(pos, -1, stream.action());
                        LensAction action = was.getOrDefault(key, new LensAction(key));
                        action.update(null, stream, collectors.computeIfAbsent(stream.payers(), payers -> upFor(pos, payers, LaserNode::collects)),
                                senders.computeIfAbsent(stream.payers(), payers -> upFor(pos, payers, LaserNode::sends)),
                                node.area(), core.reach(), core.marks(), 0);
                        action.within(node::inArea);
                        actions.put(key, action);
                    }
                }
            }
            node.showBeams(list);
        }
    }

    /** What acts on {@code light}'s beam: its streams, and Phantom light's upkeep, paid by everyone whose light it is. */
    private static List<Light.Stream> streamsOf(Light light) {
        List<Light.Stream> streams = new ArrayList<>(light.acting());
        if (light.phantom()) streams.add(new Light.Stream(Lens.PHANTOM, 0, light.feeders()));
        return streams;
    }

    /** What Smelting and Crushing do to the world on {@code light}'s beam first: a Harvest waits for them. */
    private static List<Lens> worldSteps(Light light) {
        return light.acting().stream().filter(Light.Stream::inWorld).findFirst().map(Light.Stream::steps).orElse(List.of());
    }

    /**
     * Every node {@code wanted} picks (a collecting Laser Port, a sending Laser Port) up the light reaching {@code pos}, through
     * relays and cavities, nearest first, whose light comes from one of {@code payers}: where Pickup sends
     * what it brings back, or where Placement takes from, each standing in for the ones before. A stream
     * from one side of a join reaches back up its own side, and the shared part; past a cavity, up the
     * light that filled it.
     */
    private List<BlockPos> upFor(BlockPos pos, Set<BlockPos> payers, java.util.function.Predicate<LaserNode> wanted) {
        List<BlockPos> found = new ArrayList<>();
        // A cavity's light came from the sources that filled it: up past it, their light counts as the stream's own.
        Set<BlockPos> ours = new HashSet<>(payers);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            LaserNode node = nodes.get(at);
            if (node == null) continue;
            // A Laser Port counts if its own light comes from one of the stream's sources: on the stream's own side of a join.
            Light light = lights.get(at);
            if (wanted.test(node) && light != null && !java.util.Collections.disjoint(light.feeders(), ours)) found.add(at);
            if (node.buffers()) {
                Light filled = cavityIn.get(at);
                if (filled != null) ours.addAll(filled.feeders());
            }
            // Nothing passes back up through an Isolator, or past a source; a cavity, it does.
            if (((!node.relays() && !node.buffers()) || node.isolates()) && !at.equals(pos)) continue;
            for (BlockPos up : into.getOrDefault(at, List.of())) {
                if (letsIn(up, node) && seen.add(up)) queue.add(up);
            }
        }
        return found;
    }

    /** Whether light from {@code from} gets into {@code node}: one of its beams ends there, travelling a way it accepts. */
    private boolean letsIn(BlockPos from, LaserNode node) {
        for (LaserBeam beam : beams.getOrDefault(from, List.of())) {
            if (beam.end() == LaserBeam.End.NODE && node.nodePos().equals(beam.hit()) && node.accepts(beam.face())) return true;
        }
        return false;
    }

    /** Gives {@code stack} to the collecting Laser Ports at {@code positions}, in order; what none could take. */
    ItemStack collect(List<BlockPos> positions, ItemStack stack) {
        ItemStack left = stack;
        for (BlockPos pos : positions) {
            if (left.isEmpty()) break;
            LaserNode node = nodes.get(pos);
            if (node != null && node.collects()) left = node.collect(left, false);
        }
        return left;
    }

    /** An action finished one piece of work: every relay on the way that let light through for one pulse closes. */
    void workDone(LensAction action) {
        ActionSink at = actionSinks.get(action.key());
        if (at == null) return;
        for (Route route : at.sink().routes()) {
            for (Hop hop : route.path()) {
                if (hop.relay() == null) continue;
                // The pulse closes on the face its beam left by.
                List<LaserBeam> out = beams.getOrDefault(hop.node(), List.of());
                if (hop.beam() < out.size()) hop.relay().pulseDone(out.get(hop.beam()).face());
                else hop.relay().pulseDone();
            }
        }
    }

    /**
     * Once a second, each Laser Port with light reaching it sends a stack down the beam to the nearest
     * Laser Port further along with room for it, through relays but not past an Isolator. The light
     * reaching the sending Laser Port pays to carry it.
     */
    private void ship() {
        for (LaserNode node : nodes.values()) {
            if (!node.sends()) continue;
            Light light = lights.get(node.nodePos());
            // Only while light runs to it: a switched-off mirror before it stops it too.
            if (light == null || !litNow.contains(node.nodePos())) continue;
            List<BlockPos> down = downFor(node.nodePos());
            if (down.isEmpty()) continue;
            ItemStack offered = node.send(stack -> roomFor(down, stack), 64, true);
            if (offered.isEmpty() || !payFrom(light.feeders(), LaserTuning.PICKUP_FE_PER_STACK)) continue;
            int room = offered.getCount() - simulateCollect(down, offered).getCount();
            ItemStack sent = node.send(stack -> ItemStack.isSameItemSameComponents(stack, offered), room, false);
            ItemStack left = collect(down, sent);
            if (!left.isEmpty()) node.takeBack(left);
        }
    }

    /** What the collecting Laser Ports at {@code positions} wouldn't take of {@code stack}, between them. */
    ItemStack simulateCollect(List<BlockPos> positions, ItemStack stack) {
        ItemStack left = stack;
        for (BlockPos pos : positions) {
            LaserNode node = nodes.get(pos);
            if (node != null && node.collects() && !left.isEmpty()) left = node.collect(left, true);
        }
        return left;
    }

    /** Every Laser Port down the light leaving {@code pos}, through switched-on relays, nearest first; not past an Isolator. */
    private List<BlockPos> downFor(BlockPos pos) {
        List<BlockPos> found = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (LaserBeam beam : beams.getOrDefault(at, List.of())) {
                if (beam.end() != LaserBeam.End.NODE || !seen.add(beam.hit())) continue;
                LaserNode next = nodes.get(beam.hit());
                if (next == null || !next.relays() || next.isolates() || !next.enabled()) continue;
                if (next.collects()) found.add(beam.hit());
                queue.add(beam.hit());
            }
        }
        return found;
    }

    /** Pays {@code fe} from what the sources at {@code feeders} have left this tick, by what each has; nothing if they haven't enough. */
    private boolean payFrom(Set<BlockPos> feeders, long fe) {
        long can = 0;
        for (Network network : networks) {
            for (int i = 0; i < network.sources().size(); i++) {
                if (feeders.contains(network.sources().get(i).nodePos())) can += network.left()[i];
            }
        }
        if (can < fe) return false;
        long owed = fe;
        for (Network network : networks) {
            for (int i = 0; i < network.sources().size() && owed > 0; i++) {
                if (!feeders.contains(network.sources().get(i).nodePos())) continue;
                long part = Math.min(owed, Math.min(network.left()[i], (long) Math.ceil((double) fe * network.left()[i] / can)));
                network.left()[i] -= part;
                network.sources().get(i).draw(part);
                owed -= part;
            }
        }
        return true;
    }

    /** Each area prism shows a beam to whatever its actions are working on. */
    private void showAreaWork() {
        for (LaserNode node : nodes.values()) {
            if (node.area() == null) continue;
            Light light = lights.get(node.nodePos());
            List<LaserBeam> shown = new ArrayList<>();
            List<net.minecraft.world.phys.Vec3> points = new ArrayList<>();
            // Only while light is reaching it at all.
            if (light != null && litNow.contains(node.nodePos())) {
                Set<BlockPos> seen = new HashSet<>();
                for (LensAction action : actions.values()) {
                    if (!action.key().node().equals(node.nodePos()) || action.key().beam() != -1) continue;
                    BlockPos target = action.working(level);
                    net.minecraft.world.phys.Vec3 point = action.workingPoint(level);
                    if (target == null || point == null || !seen.add(target)) continue;
                    points.add(point);
                    // Into the middle of the block it's working on.
                    net.minecraft.world.phys.Vec3 to = target.getCenter().subtract(node.nodePos().getCenter());
                    Direction face = Direction.getApproximateNearest(to.x, to.y, to.z);
                    shown.add(new LaserBeam(node.nodePos(), face, target, (int) Math.ceil(to.length()), null, LaserBeam.End.OPEN,
                            1_000, true, light.packed()));
                }
            }
            node.showWork(shown, points);
        }
    }

    /** Up to {@code max} of the first thing {@code wants} accepts, from the sending Laser Ports at {@code positions} in order. */
    ItemStack take(List<BlockPos> positions, java.util.function.Predicate<ItemStack> wants, int max, boolean simulate) {
        for (BlockPos pos : positions) {
            LaserNode node = nodes.get(pos);
            if (node == null || !node.sends()) continue;
            ItemStack got = node.send(wants, max, simulate);
            if (!got.isEmpty()) return got;
        }
        return ItemStack.EMPTY;
    }

    /** Puts back into the sending Laser Ports at {@code positions} what one sent but couldn't be used; what none took. */
    ItemStack returnTo(List<BlockPos> positions, ItemStack stack) {
        ItemStack left = stack;
        for (BlockPos pos : positions) {
            LaserNode node = nodes.get(pos);
            if (node != null && node.sends() && !left.isEmpty()) left = node.takeBack(left);
        }
        return left;
    }

    /** Whether the sources paying for {@code action} have {@code fe} left between them this tick. */
    boolean canPay(LensAction action, long fe) {
        ActionSink at = actionSinks.get(action.key());
        if (at == null) return false;
        long[] left = at.network().left();
        double can = 0;
        for (Route route : at.sink().routes()) {
            if (open(route) && left[route.index()] > 0) can += left[route.index()] * route.efficiency();
        }
        return can >= fe;
    }

    /** Whether the collecting Laser Ports at {@code positions} would take all of {@code stack} between them. */
    boolean roomForAll(List<BlockPos> positions, ItemStack stack) {
        return simulateCollect(positions, stack).isEmpty();
    }

    /** Whether any of the collecting Laser Ports at {@code positions} would take some of {@code stack}. */
    boolean roomFor(List<BlockPos> positions, ItemStack stack) {
        for (BlockPos pos : positions) {
            LaserNode node = nodes.get(pos);
            if (node != null && node.collects() && node.collect(stack, true).getCount() < stack.getCount()) return true;
        }
        return false;
    }

    /**
     * Pays {@code fe} for work an action is doing now, from what the sources paying for it have left this
     * tick, each by what it has; nothing if they haven't enough between them. Whether it was paid.
     */
    boolean pay(LensAction action, long fe) {
        ActionSink at = actionSinks.get(action.key());
        if (at == null || fe <= 0) return fe <= 0;
        long[] left = at.network().left();
        List<LaserNode> sources = at.network().sources();
        double can = 0;
        for (Route route : at.sink().routes()) {
            if (open(route) && left[route.index()] > 0) can += left[route.index()] * route.efficiency();
        }
        if (can < fe) return false;
        long owed = fe;
        for (Route route : at.sink().routes()) {
            int source = route.index();
            if (owed <= 0 || !open(route) || left[source] <= 0) continue;
            long deliver = Math.min(owed, (long) Math.ceil(fe * (left[source] * route.efficiency() / can)));
            long cost = Math.min(left[source], (long) Math.ceil(deliver / route.efficiency()));
            left[source] -= cost;
            sources.get(source).draw(cost);
            account(route, cost);
            owed -= deliver;
        }
        return true;
    }

    /** Queues every node the beams leaving {@code pos} end on. */
    private void hits(BlockPos pos, ArrayDeque<BlockPos> work) {
        for (LaserBeam beam : beams.getOrDefault(pos, List.of())) {
            if (beam.end() == LaserBeam.End.NODE) work.add(beam.hit());
        }
    }

    /**
     * All the light reaching {@code node}, joined: from each node with light whose beam ends on it. For a
     * node that doesn't refocus, each as far as it had left once it got there.
     */
    @Nullable
    private Light incoming(LaserNode node) {
        BlockPos pos = node.nodePos();
        Light joined = null;
        for (BlockPos from : new HashSet<>(into.getOrDefault(pos, List.of()))) {
            for (LaserBeam beam : beams.getOrDefault(from, List.of())) {
                if (beam.end() != LaserBeam.End.NODE || !pos.equals(beam.hit()) || !node.accepts(beam.face())) continue;
                Light light = lightOf(from, beam);
                if (light == null) continue;
                joined = Light.join(joined, node.refocuses() ? light : light.passedOn(beam.length()));
            }
        }
        return joined;
    }

    private int rangeOf(LaserNode node) {
        return ranges.getOrDefault(node.nodePos(), node.range());
    }

    /** A node's core light is now {@code light}: what its faces' own lenses made of the old one is forgotten. */
    private void setLight(BlockPos pos, Light light) {
        lights.put(pos, light);
        faceLights.remove(pos);
    }

    /** The light leaving {@code node} by {@code face}: its core light, through that face's own lenses if it has any. */
    @Nullable
    private Light faceLight(LaserNode node, Direction face) {
        Light core = lights.get(node.nodePos());
        if (core == null) return null;
        List<LensUse> own = node.lensesFor(face);
        if (own.isEmpty()) return core;
        return faceLights.computeIfAbsent(node.nodePos(), key -> new java.util.EnumMap<>(Direction.class)).computeIfAbsent(face, key -> core.through(own));
    }

    /** The light {@code beam}, leaving the node at {@code pos}, carries: a linked beam, the node's core light. */
    @Nullable
    private Light lightOf(BlockPos pos, LaserBeam beam) {
        LaserNode node = nodes.get(pos);
        if (node == null || beam.aim() != null) return lights.get(pos);
        return faceLight(node, beam.face());
    }

    /** How far {@code beam} of {@code node} reaches: its own light's reach, or the node's range with none. */
    private int rangeOf(LaserNode node, LaserBeam beam) {
        Light light = lightOf(node.nodePos(), beam);
        return light == null ? rangeOf(node) : light.reach();
    }

    private List<LaserBeam> trace(LaserNode node, int range, int marks) {
        List<LaserBeam> traced = new ArrayList<>();
        // The ways light is travelling as it arrives, for a node that passes it straight on.
        Set<Direction> arriving = java.util.EnumSet.noneOf(Direction.class);
        for (BlockPos from : into.getOrDefault(node.nodePos(), List.of())) {
            for (LaserBeam beam : beams.getOrDefault(from, List.of())) {
                if (beam.end() == LaserBeam.End.NODE && node.nodePos().equals(beam.hit())) arriving.add(beam.face());
            }
        }
        for (Direction face : node.outputsFor(arriving)) {
            // A face with lenses of its own reaches, and passes, as its own light does.
            Light own = node.lensesFor(face).isEmpty() ? null : faceLight(node, face);
            traced.add(LaserBeam.trace(level, node.nodePos(), face, own == null ? range : own.reach(), own == null ? marks : own.marks()));
        }
        for (BlockPos target : List.copyOf(node.targets())) {
            // A link to a block that's gone is let go.
            if (!node.keepsLinks() && level.isLoaded(target) && level.getBlockState(target).isAir()) {
                node.forget(target);
                continue;
            }
            traced.add(LaserBeam.traceTo(level, node.nodePos(), target, range, marks));
        }
        return traced;
    }

    /** Whether one of the node's links is to a block that's gone: light passes through both, so its beam alone doesn't show it. */
    private boolean linkGone(LaserNode node) {
        if (node.keepsLinks()) return false;
        for (BlockPos target : node.targets()) {
            if (level.isLoaded(target) && level.getBlockState(target).isAir()) return true;
        }
        return false;
    }

    /** Every node joined to {@code start} by a beam either way. */
    private List<LaserNode> component(LaserNode start) {
        Map<BlockPos, List<BlockPos>> links = new HashMap<>();
        // Nodes whose light charges the same block share a network too, so that block draws on all of them.
        Map<BlockPos, List<BlockPos>> chargers = new HashMap<>();
        beams.forEach((from, list) -> {
            for (LaserBeam beam : list) {
                if (beam.end() == LaserBeam.End.SINK) chargers.computeIfAbsent(beam.hit(), key -> new ArrayList<>()).add(from);
                if (beam.end() != LaserBeam.End.NODE || !nodes.containsKey(beam.hit())) continue;
                links.computeIfAbsent(from, key -> new ArrayList<>()).add(beam.hit());
                links.computeIfAbsent(beam.hit(), key -> new ArrayList<>()).add(from);
            }
        });
        chargers.values().forEach(sharing -> {
            for (BlockPos a : sharing) {
                for (BlockPos b : sharing) if (!a.equals(b)) links.computeIfAbsent(a, key -> new ArrayList<>()).add(b);
            }
        });
        List<LaserNode> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.nodePos());
        seen.add(start.nodePos());
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            found.add(nodes.get(at));
            for (BlockPos next : links.getOrDefault(at, List.of())) {
                if (seen.add(next)) queue.add(next);
            }
        }
        return found;
    }

    /** The blocks {@code source}'s light reaches, by the fewest relays; adds those it reaches best. */
    private void reach(LaserNode source, int index, Map<Object, Sink> sinks) {
        record Visit(BlockPos node, List<Hop> path, int relays, double efficiency) {}
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(new Visit(source.nodePos(), List.of(), 0, 1.0));
        seen.add(source.nodePos());
        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            boolean relay = visit.relays() > 0;
            LaserNode at = nodes.get(visit.node());
            Light here = lights.get(visit.node());
            if (at != null && at.area() != null && here != null && !visit.path().isEmpty()) {
                for (Light.Stream stream : here.acting()) {
                    if (!stream.payers().contains(source.nodePos())) continue;
                    LensAction action = actions.get(new LensAction.Key(visit.node(), -1, stream.action()));
                    if (action != null) route(sinks, action.key(), null, Direction.UP, null, action, source.nodePos(), index, visit.path(), visit.efficiency());
                }
            }
            List<LaserBeam> out = beams.getOrDefault(visit.node(), List.of());
            for (int i = 0; i < out.size(); i++) {
                LaserBeam beam = out.get(i);
                List<Hop> path = new ArrayList<>(visit.path());
                LaserNode from = relay ? nodes.get(visit.node()) : null;
                path.add(new Hop(visit.node(), i, from, carried.get(visit.node()),
                        from == null ? null : lost.computeIfAbsent(visit.node(), key -> new long[1])));
                // Each action this beam carries is paid for by its stream's sources, as a block's power is.
                Light light = lightOf(visit.node(), beam);
                if (light != null) {
                    List<Light.Stream> streams = new ArrayList<>(light.acting());
                    if (light.phantom()) streams.add(new Light.Stream(Lens.PHANTOM, 0, light.feeders()));
                    for (Light.Stream stream : streams) {
                        if (!stream.payers().contains(source.nodePos())) continue;
                        LensAction action = actions.get(new LensAction.Key(visit.node(), i, stream.action()));
                        if (action != null) {
                            route(sinks, action.key(), beam.hit(), beam.face(), null, action, source.nodePos(), index, path, visit.efficiency());
                        }
                    }
                }
                if (beam.end() == LaserBeam.End.SINK) {
                    route(sinks, beam.hit(), beam.face().getOpposite(), null, source.nodePos(), index, path, visit.efficiency());
                } else if (beam.end() == LaserBeam.End.NODE) {
                    LaserNode next = nodes.get(beam.hit());
                    // A cavity takes light from emitters, never from another cavity.
                    if (next != null && next.buffers() && !source.buffers()) {
                        route(sinks, beam.hit(), beam.face().getOpposite(), next, source.nodePos(), index, path, visit.efficiency());
                    }
                    if (next != null && next.relays() && next.accepts(beam.face()) && seen.add(beam.hit())) {
                        queue.add(new Visit(beam.hit(), List.copyOf(path), visit.relays() + 1, visit.efficiency() * (1.0 - next.tax())));
                    }
                }
            }
        }
    }

    // ---- each tick ----

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        LaserNetworks networks = LEVELS.get(server);
        if (networks == null) return;
        if (!TickProfiler.active()) {
            networks.tick();
            return;
        }
        long start = System.nanoTime();
        networks.tick();
        TickProfiler.record(networks, System.nanoTime() - start);
    }

    private void tick() {
        if (nodes.isEmpty()) return;
        long time = level.getGameTime();
        // A slice of the nodes each tick: has anything been built across, or taken out of, their beams?
        // Something redstone switches has been switched: what light carries changes.
        if (!dirty) {
            for (Map.Entry<LaserNode, Integer> entry : switched.entrySet()) {
                if (entry.getKey().switchState() != entry.getValue()) {
                    dirty = true;
                    break;
                }
            }
        }
        if (!dirty) {
            for (LaserNode node : rechecks.isEmpty() ? List.<LaserNode>of() : rechecks.get(Math.floorMod(-time, RECHECK_TICKS))) {
                List<LaserBeam> was = beams.get(node.nodePos());
                if (was == null || was.size() != node.outputs().size() + node.targets().size() || linkGone(node)) {
                    dirty = true;
                    break;
                }
                for (LaserBeam beam : was) {
                    if (!beam.stillHolds(level, rangeOf(node, beam))) {
                        dirty = true;
                        break;
                    }
                }
                if (dirty) break;
            }
        }
        if (dirty) rebuild();
        // What each source has this tick. Work done at once (a hit, an item carried, a block placed) pays
        // first, so machines taking everything can't starve it; blocks are charged from what's left.
        for (Network network : networks) refill(network);
        for (LensAction action : actions.values()) action.act(level, this);
        if (time % SETTLE_TICKS == 10) ship();
        if (time % 2 == 0) showAreaWork();
        passing.clear();
        for (Network network : networks) move(network);
        if (time % SETTLE_TICKS == 0) settle();
    }

    /** What each of the network's sources has to give this tick. */
    private static void refill(Network network) {
        List<LaserNode> sources = network.sources();
        long[] left = network.left();
        for (int i = 0; i < left.length; i++) {
            LaserNode source = sources.get(i);
            left[i] = source.enabled() ? Math.max(0, source.available()) : 0;
        }
    }

    /**
     * Moves this tick's power, from what the sources have left once work done at once has paid. Blocks are
     * charged first from emitters, then from cavities for what the emitters couldn't give; what emitters
     * have left over then fills cavities. Each block draws only on the sources whose light reaches it.
     */
    private void move(Network network) {
        List<LaserNode> sources = network.sources();
        long[] left = network.left();
        long supply = 0;
        for (long has : left) supply += has;
        if (supply <= 0 || network.sinks().isEmpty()) return;
        long[] had = left.clone();
        charge(network, left, false, false);
        if (network.cavities()) {
            charge(network, left, false, true);
            charge(network, left, true, false);
        }
        for (int i = 0; i < left.length; i++) {
            if (had[i] > left[i]) sources.get(i).draw(had[i] - left[i]);
        }
    }

    /**
     * Charges the network's blocks (or with {@code cavities}, its cavities) from its emitters (or with
     * {@code fromCavities}, its cavities). Each block asks the sources reaching it in proportion to what
     * each has left; a source asked for more than it has gives each a fair part. What it costs comes off
     * {@code left}: nothing is sent that isn't paid for, rounding included.
     */
    private void charge(Network network, long[] left, boolean cavities, boolean fromCavities) {
        List<Sink> sinks = network.sinks();
        List<LaserNode> sources = network.sources();
        double[][] asks = new double[sinks.size()][];
        double[] asked = new double[left.length];
        boolean any = false;
        for (int i = 0; i < sinks.size(); i++) {
            Sink sink = sinks.get(i);
            if ((sink.buffer() != null) != cavities) continue;
            List<Route> routes = sink.routes();
            long has = 0;
            double reachable = 0;
            for (Route route : routes) {
                if (!usable(route, sources, left, fromCavities)) continue;
                has += left[route.index()];
                reachable += left[route.index()] * route.efficiency();
            }
            if (has <= 0) continue;
            long demand = demand(sink, (int) Math.min(Integer.MAX_VALUE, (long) reachable));
            if (demand <= 0) continue;
            double[] ask = new double[routes.size()];
            for (int r = 0; r < routes.size(); r++) {
                Route route = routes.get(r);
                if (!usable(route, sources, left, fromCavities)) continue;
                // Its part of what the block wants, from what leaves this source to give it.
                ask[r] = demand * ((double) left[route.index()] / has) / route.efficiency();
                asked[route.index()] += ask[r];
            }
            asks[i] = ask;
            any = true;
        }
        if (!any) return;
        if (limited) limitPassing(sinks, asks, asked);
        double[] scale = new double[left.length];
        for (int i = 0; i < left.length; i++) scale[i] = asked[i] > 0 ? Math.min(1.0, left[i] / asked[i]) : 0;
        for (int i = 0; i < sinks.size(); i++) {
            double[] ask = asks[i];
            if (ask == null) continue;
            List<Route> routes = sinks.get(i).routes();
            long[] give = new long[routes.size()];
            long deliver = 0;
            for (int r = 0; r < routes.size(); r++) {
                if (ask[r] <= 0) continue;
                int source = routes.get(r).index();
                // Up, so the last FE of a small ask still arrives after the tax; never more than the source has left.
                give[r] = Math.min(left[source], (long) Math.ceil(ask[r] * scale[source]));
                deliver += (long) Math.floor(give[r] * routes.get(r).efficiency());
            }
            if (deliver <= 0) continue;
            long got = put(sinks.get(i), (int) Math.min(Integer.MAX_VALUE, deliver));
            if (got <= 0) continue;
            double part = (double) got / deliver;
            for (int r = 0; r < routes.size(); r++) {
                long paid = Math.min(give[r], (long) Math.ceil(give[r] * part));
                if (paid <= 0) continue;
                left[routes.get(r).index()] -= paid;
                account(routes.get(r), paid);
                if (limited) {
                    for (Hop hop : routes.get(r).path()) {
                        if (hop.relay() != null && hop.relay().passLimit() != Long.MAX_VALUE) passingLeft(hop.relay())[0] -= paid;
                    }
                }
            }
        }
    }

    /** What each relay with a limit (a Solar Emitter) still lets through this tick. */
    private final Map<LaserNode, long[]> passing = new HashMap<>();
    /** Whether any relay has a limit at all: if none, power is handed out without looking for them. */
    private boolean limited;

    private long[] passingLeft(LaserNode relay) {
        return passing.computeIfAbsent(relay, node -> new long[] {node.passLimit()});
    }

    /**
     * Routes through a relay with a limit share what it still lets through: where more is asked through it
     * than that, each of them is asked for less, in proportion. {@code asked} is worked out again to match.
     */
    private void limitPassing(List<Sink> sinks, double[][] asks, double[] asked) {
        Map<LaserNode, Double> through = new HashMap<>();
        for (int i = 0; i < sinks.size(); i++) {
            if (asks[i] == null) continue;
            List<Route> routes = sinks.get(i).routes();
            for (int r = 0; r < routes.size(); r++) {
                if (asks[i][r] <= 0) continue;
                for (Hop hop : routes.get(r).path()) {
                    if (hop.relay() != null && hop.relay().passLimit() != Long.MAX_VALUE) through.merge(hop.relay(), asks[i][r], Double::sum);
                }
            }
        }
        if (through.isEmpty()) return;
        java.util.Arrays.fill(asked, 0);
        for (int i = 0; i < sinks.size(); i++) {
            if (asks[i] == null) continue;
            List<Route> routes = sinks.get(i).routes();
            for (int r = 0; r < routes.size(); r++) {
                if (asks[i][r] <= 0) continue;
                for (Hop hop : routes.get(r).path()) {
                    Double total = hop.relay() == null ? null : through.get(hop.relay());
                    if (total != null && total > 0) asks[i][r] *= Math.min(1.0, Math.max(0, passingLeft(hop.relay())[0]) / total);
                }
                asked[routes.get(r).index()] += asks[i][r];
            }
        }
    }

    /** Whether this route can carry power this pass: its source is the kind asked for, has some left, and every relay on the way is on. */
    private boolean usable(Route route, List<LaserNode> sources, long[] left, boolean fromCavities) {
        return sources.get(route.index()).buffers() == fromCavities && left[route.index()] > 0 && open(route);
    }

    /** What the block would take, up to {@code cap}. */
    private long demand(Sink sink, int cap) {
        if (cap <= 0) return 0;
        if (sink.action() != null) return Math.min(cap, sink.action().demand());
        if (sink.buffer() != null) return Math.min(cap, sink.buffer().room());
        EnergyHandler handler = sink.energy() == null ? null : sink.energy().getCapability();
        if (handler == null) return 0;
        try (Transaction simulation = Transaction.openRoot()) {
            return handler.insert(cap, simulation);
        }
    }

    /** Gives the block up to {@code amount}; what it took. */
    private long put(Sink sink, int amount) {
        if (sink.action() != null) return sink.action().fill(amount);
        if (sink.buffer() != null) {
            long got = Math.min(amount, sink.buffer().room());
            sink.buffer().fill(got);
            // What the light did, the cavity now does too; it gives that out, and pays for it.
            Light in = sink.pos() == null ? null : cavityIn.get(sink.pos());
            if (got > 0 && in != null && sink.buffer().absorb(in)) dirty = true;
            return got;
        }
        EnergyHandler handler = sink.energy() == null ? null : sink.energy().getCapability();
        if (handler == null) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            int got = handler.insert(amount, transaction);
            transaction.commit();
            return got;
        }
    }

    /** Whether every relay on the route is switched on. */
    private boolean open(Route route) {
        for (Hop hop : route.path()) {
            if (hop.relay() != null && !hop.relay().enabled()) return false;
        }
        return true;
    }

    /** {@code sent} left the route's source for its block: credits what each beam on the way carried, and what each relay lost. */
    private void account(Route route, long sent) {
        double power = sent;
        for (Hop hop : route.path()) {
            if (hop.relay() != null && hop.lost() != null) {
                double tax = power * hop.relay().tax();
                hop.lost()[0] += Math.round(tax);
                power -= tax;
            }
            if (hop.beam() < hop.carried().length) hop.carried()[hop.beam()] += Math.round(power);
        }
    }

    /** Once a second: the relays' losses into the field, and each beam's brightness to its node. */
    private void settle() {
        lost.forEach((pos, fe) -> {
            if (fe[0] > 0 && nodes.containsKey(pos)) QuantumFlux.emitFromEnergy(level, pos, fe[0]);
            fe[0] = 0;
        });
        Set<BlockPos> live = live();
        litNow = live;
        blind(live);
        for (LaserNode node : nodes.values()) {
            if (node.area() != null) continue;
            BlockPos pos = node.nodePos();
            List<LaserBeam> list = beams.getOrDefault(pos, List.of());
            long[] sum = carried.getOrDefault(pos, new long[0]);
            List<LaserBeam> flowing = new ArrayList<>(list.size());
            boolean on = live.contains(pos);
            for (int i = 0; i < list.size(); i++) {
                flowing.add(list.get(i).withFlow(i < sum.length ? sum[i] / SETTLE_TICKS : 0).withLive(on));
            }
            beams.put(pos, flowing);
            // The node tells its clients only if the beams look different: shape, liveness, or a step in brightness.
            node.showBeams(flowing);
        }
        carried.values().forEach(sum -> java.util.Arrays.fill(sum, 0));
    }

    /**
     * Light carrying power at eye height blinds: anything living whose eyes are in a lit beam can't see for
     * {@link LaserTuning#BLIND_TICKS}, unless it's looking through a Mirror Lens. Checked as the beams settle.
     */
    private void blind(Set<BlockPos> live) {
        for (Map.Entry<BlockPos, List<LaserBeam>> entry : beams.entrySet()) {
            if (!live.contains(entry.getKey())) continue;
            long[] sums = carried.getOrDefault(entry.getKey(), new long[0]);
            List<LaserBeam> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (i >= sums.length || sums[i] <= 0) continue;
                net.minecraft.world.phys.Vec3[] ends = list.get(i).ends();
                net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(ends[0], ends[1]).inflate(0.5);
                for (net.minecraft.world.entity.LivingEntity entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
                    if (com.kadikular.quantimium.item.MirrorLensItem.isWorn(entity)) continue;
                    if (distanceToSegment(entity.getEyePosition(), ends[0], ends[1]) > LaserTuning.BLIND_REACH) continue;
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, LaserTuning.BLIND_TICKS));
                }
            }
        }
    }

    private static double distanceToSegment(net.minecraft.world.phys.Vec3 point, net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b) {
        net.minecraft.world.phys.Vec3 ab = b.subtract(a);
        double length = ab.lengthSqr();
        double t = length == 0 ? 0 : Math.clamp(point.subtract(a).dot(ab) / length, 0, 1);
        return point.distanceTo(a.add(ab.scale(t)));
    }

    /**
     * The nodes light runs from: every switched-on source with power to send, and every switched-on relay
     * its light reaches. Their beams glow dimly even when nothing at their ends needs power.
     */
    private Set<BlockPos> live() {
        Set<BlockPos> live = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (LaserNode node : nodes.values()) {
            if (node.generates() && node.enabled() && node.available() > 0 && live.add(node.nodePos())) queue.add(node.nodePos());
        }
        while (!queue.isEmpty()) {
            for (LaserBeam beam : beams.getOrDefault(queue.poll(), List.of())) {
                if (beam.end() != LaserBeam.End.NODE) continue;
                LaserNode next = nodes.get(beam.hit());
                if (next != null && next.relays() && next.enabled() && live.add(beam.hit())) queue.add(beam.hit());
            }
        }
        return live;
    }

    /** A beam's brightness, in steps that change only when its flow changes by a lot: 0 for dark. */
    public static int brightness(long flow) {
        if (flow <= 0) return 0;
        return 1 + (int) Math.min(6, Math.floor(Math.log10(flow)));
    }
}
