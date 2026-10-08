package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
    /** How far each node's light reaches: its own range, or for a node that doesn't refocus, what's left of the light reaching it. */
    private final Map<BlockPos, Integer> ranges = new HashMap<>();
    private List<Network> networks = List.of();
    private boolean dirty;
    /** What each relay has lost to the hop tax since the last settle, to put into the field. */
    private final Map<BlockPos, Long> lost = new HashMap<>();
    /** What each beam has carried since the last settle, keyed by its node and face. */
    private final Map<BlockPos, long[]> carried = new HashMap<>();

    private LaserNetworks(ServerLevel level) {
        this.level = level;
    }

    public static LaserNetworks of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, key -> new LaserNetworks(level));
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
            carried.remove(pos);
            dirty = true;
        }
    }

    /** The beams leaving {@code pos}, as last traced, with what they carried: for readouts. */
    public List<LaserBeam> beamsOf(BlockPos pos) {
        return beams.getOrDefault(pos, List.of());
    }

    /** What the node at {@code pos} sent, or passed on, a tick on average over the last second. */
    public long throughput(BlockPos pos) {
        long total = 0;
        for (LaserBeam beam : beamsOf(pos)) total += beam.flow();
        return total;
    }

    // ---- the network ----

    /** One connected set of nodes: its sources, and each block it charges with how to reach it. */
    private record Network(List<LaserNode> sources, List<Sink> sinks) {}

    /**
     * A block a beam charges: where, from which face, through which beams from a source (each as its
     * node and face index), and what fraction of what leaves the source arrives.
     */
    private record Sink(BlockPos pos, Direction face, List<Route> routes, @Nullable LaserNode buffer) {}

    /** One source's best way to a block: the beams from it, and what arrives of what leaves it. */
    private record Route(BlockPos source, List<Hop> path, double efficiency) {}

    /** Adds {@code source}'s route to the block at {@code pos}, if it's that source's best. */
    private static void route(Map<BlockPos, Sink> sinks, BlockPos pos, Direction face, @Nullable LaserNode buffer,
                              BlockPos source, List<Hop> path, double efficiency) {
        Sink sink = sinks.get(pos);
        List<Route> routes = sink == null ? new ArrayList<>() : new ArrayList<>(sink.routes());
        routes.removeIf(route -> route.source().equals(source) && route.efficiency() < efficiency);
        if (routes.stream().noneMatch(route -> route.source().equals(source))) routes.add(new Route(source, List.copyOf(path), efficiency));
        sinks.put(pos, new Sink(pos, face, routes, buffer));
    }

    /** A beam on a sink's path: its node, which of the node's beams, and the relay it leaves from (null at the source). */
    private record Hop(BlockPos node, int beam, boolean relay) {}

    private void rebuild() {
        dirty = false;
        Map<BlockPos, List<LaserBeam>> before = new HashMap<>(beams);
        beams.clear();
        carried.clear();
        ranges.clear();
        // Nodes that refocus first, at their own range; then those that don't (a photodetector), at what's
        // left of the light reaching them, round by round so a line of them carries the remainder on.
        List<LaserNode> waiting = new ArrayList<>();
        for (LaserNode node : nodes.values()) {
            if (node.refocuses()) traceAt(node, node.range(), before);
            else waiting.add(node);
        }
        for (int round = 0; round <= waiting.size() && !waiting.isEmpty(); round++) {
            List<LaserNode> still = new ArrayList<>();
            for (LaserNode node : waiting) {
                int reach = remainingAt(node.nodePos());
                if (reach < 0 && round < waiting.size()) still.add(node);
                else traceAt(node, Math.max(0, reach), before);
            }
            if (still.size() == waiting.size()) {
                for (LaserNode node : still) traceAt(node, Math.max(0, remainingAt(node.nodePos())), before);
                break;
            }
            waiting = still;
        }
        // Every node reached from each source, nearest first: each hop costs the tax, so fewest hops is best.
        List<Network> built = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        for (LaserNode start : nodes.values()) {
            if (start.relays() || claimed.contains(start.nodePos())) continue;
            List<LaserNode> sources = new ArrayList<>();
            Map<BlockPos, Sink> sinks = new LinkedHashMap<>();
            // The sources joined to this one through shared relays share the network.
            for (LaserNode source : component(start)) {
                if (source.relays()) continue;
                claimed.add(source.nodePos());
                sources.add(source);
                reach(source, sinks);
            }
            built.add(new Network(sources, List.copyOf(sinks.values())));
        }
        networks = built;
    }

    /** Traces {@code node}'s beams at {@code range}; a beam that runs as it did keeps its brightness, so a change elsewhere doesn't make it flicker. */
    private void traceAt(LaserNode node, int range, Map<BlockPos, List<LaserBeam>> before) {
        ranges.put(node.nodePos(), range);
        List<LaserBeam> traced = trace(node, range);
        List<LaserBeam> was = before.getOrDefault(node.nodePos(), List.of());
        for (int i = 0; i < traced.size(); i++) {
            LaserBeam beam = traced.get(i);
            for (LaserBeam old : was) {
                if (old.sameRun(beam)) traced.set(i, beam.withFlow(old.flow()).withLive(old.live()));
            }
        }
        beams.put(node.nodePos(), traced);
        node.showBeams(traced);
    }

    /** The most range left in any light reaching {@code pos} from a node already traced; -1 if none has yet. */
    private int remainingAt(BlockPos pos) {
        int best = -1;
        for (Map.Entry<BlockPos, List<LaserBeam>> entry : beams.entrySet()) {
            int range = ranges.getOrDefault(entry.getKey(), 0);
            for (LaserBeam beam : entry.getValue()) {
                if (beam.end() == LaserBeam.End.NODE && pos.equals(beam.hit())) best = Math.max(best, range - beam.length() - 1);
            }
        }
        return best;
    }

    private int rangeOf(LaserNode node) {
        return ranges.getOrDefault(node.nodePos(), node.range());
    }

    private List<LaserBeam> trace(LaserNode node, int range) {
        List<LaserBeam> traced = new ArrayList<>();
        for (Direction face : node.outputs()) traced.add(LaserBeam.trace(level, node.nodePos(), face, range));
        for (BlockPos target : List.copyOf(node.targets())) {
            // A link to a block that's gone is let go.
            if (level.isLoaded(target) && level.getBlockState(target).isAir()) {
                node.forget(target);
                continue;
            }
            traced.add(LaserBeam.traceTo(level, node.nodePos(), target, range));
        }
        return traced;
    }

    /** Whether one of the node's links is to a block that's gone: light passes through both, so its beam alone doesn't show it. */
    private boolean linkGone(LaserNode node) {
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
    private void reach(LaserNode source, Map<BlockPos, Sink> sinks) {
        record Visit(BlockPos node, List<Hop> path, int relays, double efficiency) {}
        ArrayDeque<Visit> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(new Visit(source.nodePos(), List.of(), 0, 1.0));
        seen.add(source.nodePos());
        while (!queue.isEmpty()) {
            Visit visit = queue.poll();
            boolean relay = visit.relays() > 0;
            List<LaserBeam> out = beams.getOrDefault(visit.node(), List.of());
            for (int i = 0; i < out.size(); i++) {
                LaserBeam beam = out.get(i);
                List<Hop> path = new ArrayList<>(visit.path());
                path.add(new Hop(visit.node(), i, relay));
                if (beam.end() == LaserBeam.End.SINK) {
                    route(sinks, beam.hit(), beam.face().getOpposite(), null, source.nodePos(), path, visit.efficiency());
                } else if (beam.end() == LaserBeam.End.NODE) {
                    LaserNode next = nodes.get(beam.hit());
                    // A cavity takes light from emitters, never from another cavity.
                    if (next != null && next.buffers() && !source.buffers()) {
                        route(sinks, beam.hit(), beam.face().getOpposite(), next, source.nodePos(), path, visit.efficiency());
                    }
                    if (next != null && next.relays() && seen.add(beam.hit())) {
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
        if (networks != null) networks.tick();
    }

    private void tick() {
        if (nodes.isEmpty()) return;
        long time = level.getGameTime();
        // A slice of the nodes each tick: has anything been built across, or taken out of, their beams?
        if (!dirty) {
            for (LaserNode node : nodes.values()) {
                if (Math.floorMod(node.nodePos().hashCode() + time, RECHECK_TICKS) != 0) continue;
                List<LaserBeam> was = beams.get(node.nodePos());
                if (was == null || was.size() != node.outputs().size() + node.targets().size()
                        || (node.refocuses() && rangeOf(node) != node.range()) || linkGone(node)) {
                    dirty = true;
                    break;
                }
                for (LaserBeam beam : was) {
                    if (!beam.stillHolds(level, rangeOf(node))) {
                        dirty = true;
                        break;
                    }
                }
                if (dirty) break;
            }
        }
        if (dirty) rebuild();
        for (Network network : networks) move(network);
        if (time % SETTLE_TICKS == 0) settle();
    }

    /**
     * Moves this tick's power. Blocks are charged first from emitters, then from cavities for what the
     * emitters couldn't give; what emitters have left over then fills cavities. Each block draws only on
     * the sources whose light reaches it.
     */
    private void move(Network network) {
        if (network.sinks().isEmpty()) return;
        Map<BlockPos, Long> left = new HashMap<>();
        long supply = 0;
        for (LaserNode source : network.sources()) {
            long has = source.enabled() ? Math.max(0, source.available()) : 0;
            left.put(source.nodePos(), has);
            supply += has;
        }
        if (supply <= 0) return;
        Map<BlockPos, Long> had = new HashMap<>(left);
        charge(network, left, false, false);
        charge(network, left, false, true);
        charge(network, left, true, false);
        for (LaserNode source : network.sources()) {
            long taken = had.get(source.nodePos()) - left.get(source.nodePos());
            if (taken > 0) source.draw(taken);
        }
    }

    /**
     * Charges the network's blocks (or with {@code cavities}, its cavities) from its emitters (or with
     * {@code fromCavities}, its cavities). Each block asks the sources reaching it in proportion to what
     * each has left; a source asked for more than it has gives each a fair part. What it costs comes off
     * {@code left}: nothing is sent that isn't paid for, rounding included.
     */
    private void charge(Network network, Map<BlockPos, Long> left, boolean cavities, boolean fromCavities) {
        List<Sink> sinks = network.sinks();
        List<List<Route>> routes = new ArrayList<>(sinks.size());
        List<double[]> asks = new ArrayList<>(sinks.size());
        Map<BlockPos, Double> asked = new HashMap<>();
        for (Sink sink : sinks) {
            List<Route> from = List.of();
            double[] ask = new double[0];
            if ((sink.buffer() != null) == cavities) {
                from = sink.routes().stream().filter(route -> {
                    LaserNode source = nodes.get(route.source());
                    return source != null && source.buffers() == fromCavities && left.getOrDefault(route.source(), 0L) > 0 && open(route);
                }).toList();
                long has = 0;
                double reachable = 0;
                for (Route route : from) {
                    has += left.get(route.source());
                    reachable += left.get(route.source()) * route.efficiency();
                }
                long demand = from.isEmpty() ? 0 : demand(sink, (int) Math.min(Integer.MAX_VALUE, (long) reachable));
                ask = new double[from.size()];
                for (int r = 0; r < from.size() && demand > 0; r++) {
                    Route route = from.get(r);
                    // Its part of what the block wants, from what leaves this source to give it.
                    ask[r] = demand * ((double) left.get(route.source()) / has) / route.efficiency();
                    asked.merge(route.source(), ask[r], Double::sum);
                }
            }
            routes.add(from);
            asks.add(ask);
        }
        for (int i = 0; i < sinks.size(); i++) {
            List<Route> from = routes.get(i);
            if (from.isEmpty()) continue;
            double[] ask = asks.get(i);
            long[] give = new long[from.size()];
            long deliver = 0;
            for (int r = 0; r < from.size(); r++) {
                BlockPos source = from.get(r).source();
                double scale = Math.min(1.0, left.get(source) / asked.getOrDefault(source, 1.0));
                give[r] = Math.min(left.get(source), (long) Math.floor(ask[r] * scale));
                deliver += (long) Math.floor(give[r] * from.get(r).efficiency());
            }
            if (deliver <= 0) continue;
            long got = put(sinks.get(i), (int) Math.min(Integer.MAX_VALUE, deliver));
            if (got <= 0) continue;
            double part = (double) got / deliver;
            for (int r = 0; r < from.size(); r++) {
                long paid = Math.min(give[r], (long) Math.ceil(give[r] * part));
                if (paid <= 0) continue;
                left.merge(from.get(r).source(), -paid, Long::sum);
                account(from.get(r), paid);
            }
        }
    }

    /** What the block would take, up to {@code cap}. */
    private long demand(Sink sink, int cap) {
        if (cap <= 0) return 0;
        if (sink.buffer() != null) return Math.min(cap, sink.buffer().room());
        EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, sink.pos(), sink.face());
        if (handler == null) return 0;
        try (Transaction simulation = Transaction.openRoot()) {
            return handler.insert(cap, simulation);
        }
    }

    /** Gives the block up to {@code amount}; what it took. */
    private long put(Sink sink, int amount) {
        if (sink.buffer() != null) {
            long got = Math.min(amount, sink.buffer().room());
            sink.buffer().fill(got);
            return got;
        }
        EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, sink.pos(), sink.face());
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
            if (!hop.relay()) continue;
            LaserNode relay = nodes.get(hop.node());
            if (relay == null || !relay.enabled()) return false;
        }
        return true;
    }

    /** {@code sent} left the route's source for its block: credits what each beam on the way carried, and what each relay lost. */
    private void account(Route route, long sent) {
        double power = sent;
        for (Hop hop : route.path()) {
            if (hop.relay()) {
                LaserNode relay = nodes.get(hop.node());
                double tax = power * (relay == null ? LaserTuning.HOP_TAX : relay.tax());
                lost.merge(hop.node(), Math.round(tax), Long::sum);
                power -= tax;
            }
            long[] perBeam = carried.computeIfAbsent(hop.node(), key -> new long[Math.max(1, beamsOf(key).size())]);
            if (hop.beam() < perBeam.length) perBeam[hop.beam()] += Math.round(power);
        }
    }

    /** Once a second: the relays' losses into the field, and each beam's brightness to its node. */
    private void settle() {
        lost.forEach((pos, fe) -> QuantumFlux.emitFromEnergy(level, pos, fe));
        lost.clear();
        Set<BlockPos> live = live();
        for (LaserNode node : nodes.values()) {
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
        carried.clear();
    }

    /**
     * The nodes light runs from: every switched-on source with power to send, and every switched-on relay
     * its light reaches. Their beams glow dimly even when nothing at their ends needs power.
     */
    private Set<BlockPos> live() {
        Set<BlockPos> live = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (LaserNode node : nodes.values()) {
            if (!node.relays() && node.enabled() && node.available() > 0 && live.add(node.nodePos())) queue.add(node.nodePos());
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
