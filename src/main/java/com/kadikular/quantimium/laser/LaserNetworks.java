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
    private record Sink(BlockPos pos, Direction face, List<Hop> path, double efficiency, @Nullable LaserNode buffer) {}

    /** A beam on a sink's path: its node, which of the node's beams, and the relay it leaves from (null at the source). */
    private record Hop(BlockPos node, int beam, boolean relay) {}

    private void rebuild() {
        dirty = false;
        Map<BlockPos, List<LaserBeam>> before = new HashMap<>(beams);
        beams.clear();
        carried.clear();
        for (LaserNode node : nodes.values()) {
            // A beam that runs as it did keeps its brightness, so a change elsewhere doesn't make it flicker.
            List<LaserBeam> traced = trace(node);
            List<LaserBeam> was = before.getOrDefault(node.nodePos(), List.of());
            for (int i = 0; i < traced.size(); i++) {
                LaserBeam beam = traced.get(i);
                for (LaserBeam old : was) {
                    if (old.sameRun(beam)) {
                        traced.set(i, beam.withFlow(old.flow()));
                    }
                }
            }
            beams.put(node.nodePos(), traced);
            node.showBeams(traced);
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

    private List<LaserBeam> trace(LaserNode node) {
        List<LaserBeam> traced = new ArrayList<>();
        for (Direction face : node.outputs()) traced.add(LaserBeam.trace(level, node.nodePos(), face, node.range()));
        for (BlockPos target : node.targets()) traced.add(LaserBeam.traceTo(level, node.nodePos(), target, node.range()));
        return traced;
    }

    /** Every node joined to {@code start} by a beam either way. */
    private List<LaserNode> component(LaserNode start) {
        Map<BlockPos, List<BlockPos>> links = new HashMap<>();
        beams.forEach((from, list) -> {
            for (LaserBeam beam : list) {
                if (beam.end() != LaserBeam.End.NODE || !nodes.containsKey(beam.hit())) continue;
                links.computeIfAbsent(from, key -> new ArrayList<>()).add(beam.hit());
                links.computeIfAbsent(beam.hit(), key -> new ArrayList<>()).add(from);
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
                    double efficiency = visit.efficiency();
                    Sink sink = sinks.get(beam.hit());
                    if (sink == null || sink.efficiency() < efficiency) {
                        sinks.put(beam.hit(), new Sink(beam.hit(), beam.face().getOpposite(), List.copyOf(path), efficiency, null));
                    }
                } else if (beam.end() == LaserBeam.End.NODE) {
                    LaserNode next = nodes.get(beam.hit());
                    // A cavity takes light from emitters, never from another cavity.
                    if (next != null && next.buffers() && !source.buffers()) {
                        double efficiency = visit.efficiency();
                        Sink sink = sinks.get(beam.hit());
                        if (sink == null || sink.efficiency() < efficiency) {
                            sinks.put(beam.hit(), new Sink(beam.hit(), beam.face().getOpposite(), List.copyOf(path), efficiency, next));
                        }
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
                if (was == null || was.size() != node.outputs().size() + node.targets().size()) {
                    dirty = true;
                    break;
                }
                for (LaserBeam beam : was) {
                    if (!beam.stillHolds(level, node.range())) {
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
     * Moves this tick's power. Blocks are charged first, from emitters and then cavities, scaled to what
     * the network has; what emitters have left over then fills its cavities.
     */
    private void move(Network network) {
        List<LaserNode> sources = network.sources();
        long[] available = new long[sources.size()];
        long fromEmitters = 0;
        long fromCavities = 0;
        for (int i = 0; i < available.length; i++) {
            LaserNode source = sources.get(i);
            available[i] = source.enabled() ? Math.max(0, source.available()) : 0;
            if (source.buffers()) fromCavities += available[i];
            else fromEmitters += available[i];
        }
        long supply = fromEmitters + fromCavities;
        if (supply <= 0 || network.sinks().isEmpty()) return;

        // Blocks first.
        long cost = charge(network, supply, false);
        long[] taken = new long[sources.size()];
        long emittersLeft = fromEmitters - take(sources, available, taken, cost, false);
        if (cost > fromEmitters) take(sources, available, taken, cost - fromEmitters, true);
        // Then cavities, with what the emitters have left.
        if (emittersLeft > 0) {
            long fill = charge(network, emittersLeft, true);
            take(sources, available, taken, fill, false);
        }
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] > 0) sources.get(i).draw(taken[i]);
        }
    }

    /**
     * Charges the network's blocks (or with {@code cavities}, its cavities) with up to {@code budget}
     * leaving the sources, shared by what each asks for. What it cost the sources.
     */
    private long charge(Network network, long budget, boolean cavities) {
        int cap = (int) Math.min(Integer.MAX_VALUE, budget);
        List<Sink> sinks = network.sinks();
        long[] demand = new long[sinks.size()];
        double required = 0;
        for (int i = 0; i < demand.length; i++) {
            Sink sink = sinks.get(i);
            if ((sink.buffer() != null) != cavities || !pathOpen(sink)) continue;
            if (sink.buffer() != null) {
                demand[i] = Math.min(cap, sink.buffer().room());
            } else {
                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, sink.pos(), sink.face());
                if (handler == null) continue;
                try (Transaction simulation = Transaction.openRoot()) {
                    demand[i] = handler.insert(cap, simulation);
                }
            }
            required += demand[i] / sink.efficiency();
        }
        if (required <= 0) return 0;
        double share = Math.min(1.0, budget / required);
        long left = budget;
        for (int i = 0; i < demand.length && left > 0; i++) {
            if (demand[i] <= 0) continue;
            Sink sink = sinks.get(i);
            // Never more than the budget has left, rounding included: nothing is sent that isn't paid for.
            int want = (int) Math.min(Math.floor(demand[i] * share), Math.floor(left * sink.efficiency()));
            if (want <= 0) continue;
            long got;
            if (sink.buffer() != null) {
                got = want;
                sink.buffer().fill(got);
            } else {
                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, sink.pos(), sink.face());
                if (handler == null) continue;
                try (Transaction transaction = Transaction.openRoot()) {
                    got = handler.insert(want, transaction);
                    transaction.commit();
                }
            }
            if (got <= 0) continue;
            long sent = Math.min(left, (long) Math.ceil(got / sink.efficiency()));
            left -= sent;
            account(sink, sent);
        }
        return budget - left;
    }

    /**
     * Takes {@code amount} from the emitters (or the cavities), each in proportion to what it has,
     * adding to {@code taken}. How much it found.
     */
    private static long take(List<LaserNode> sources, long[] available, long[] taken, long amount, boolean cavities) {
        long pool = 0;
        for (int i = 0; i < available.length; i++) {
            if (sources.get(i).buffers() == cavities) pool += available[i] - taken[i];
        }
        if (pool <= 0 || amount <= 0) return 0;
        long left = Math.min(amount, pool);
        long want = left;
        for (int i = 0; i < available.length && left > 0; i++) {
            if (sources.get(i).buffers() != cavities) continue;
            long has = available[i] - taken[i];
            long share = Math.min(has, Math.min(left, (long) Math.ceil((double) want * has / pool)));
            taken[i] += share;
            left -= share;
        }
        // Rounding leftovers from whoever still has some.
        for (int i = 0; i < available.length && left > 0; i++) {
            if (sources.get(i).buffers() != cavities) continue;
            long share = Math.min(left, available[i] - taken[i]);
            taken[i] += share;
            left -= share;
        }
        return want - left;
    }

    /** Whether every relay on the way to this sink is switched on. */
    private boolean pathOpen(Sink sink) {
        for (Hop hop : sink.path()) {
            if (!hop.relay()) continue;
            LaserNode relay = nodes.get(hop.node());
            if (relay == null || !relay.enabled()) return false;
        }
        return true;
    }

    /** {@code sent} left the source for this sink: what each beam on the way carried, and what each relay lost. */
    private void account(Sink sink, long sent) {
        double power = sent;
        for (Hop hop : sink.path()) {
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
        for (LaserNode node : nodes.values()) {
            BlockPos pos = node.nodePos();
            List<LaserBeam> list = beams.getOrDefault(pos, List.of());
            long[] sum = carried.getOrDefault(pos, new long[0]);
            List<LaserBeam> flowing = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) flowing.add(list.get(i).withFlow(i < sum.length ? sum[i] / SETTLE_TICKS : 0));
            beams.put(pos, flowing);
            // The node tells its clients only if the beams look different: shape, or a step in brightness.
            node.showBeams(flowing);
        }
        carried.clear();
    }

    /** A beam's brightness, in steps that change only when its flow changes by a lot: 0 for dark. */
    public static int brightness(long flow) {
        if (flow <= 0) return 0;
        return 1 + (int) Math.min(6, Math.floor(Math.log10(flow)));
    }
}
