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
    private Map<LaserNode, Integer> switched = new LinkedHashMap<>();
    /** Nodes found switched this tick: their networks are worked out again in full. */
    private final Set<BlockPos> switchedAt = new HashSet<>();
    /** For each block a beam charges, the nodes whose beams charge it: they share a network. Kept as beams are traced. */
    private final Map<BlockPos, Set<BlockPos>> chargedBy = new HashMap<>();
    /** Nodes whose beams changed on their own (a block cut, placed, switched): only their networks are worked out again. */
    private final Set<BlockPos> dirtyAt = new HashSet<>();
    /** Each Entangled Mirror, by where it stands, and the node it draws light from. */
    private final Map<BlockPos, BlockPos> drawsOn = new HashMap<>();
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
        if (event.getLevel() instanceof ServerLevel server) LaserProfile.forget(server);
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

    /**
     * Something along {@code node}'s beams changed (a block it cut, placed or used): only the network it's part of is
     * worked out again, not every one in the level.
     */
    public static void changed(Level level, BlockPos node) {
        if (level instanceof ServerLevel server) of(server).dirtyAt.add(node.immutable());
    }

    /**
     * A node arrives (placed, or its chunk loaded): only it, and the nodes whose beams pass where it stands, are worked
     * out again; their networks join with whatever its light reaches.
     */
    private void put(LaserNode node) {
        BlockPos pos = node.nodePos().immutable();
        LaserNode was = nodes.put(pos, node);
        bucketsStale = true;
        if (node.entangledSource() != null) relink(node);
        // One block entity in place of another at the same spot: everything again, to be safe.
        if (was != null && was != node) {
            dirty = true;
            return;
        }
        dirtyAt.add(pos);
        dirtyAt.addAll(beamsCrossing(pos));
    }

    /**
     * A node goes (broken, or its chunk unloaded): what it held is let go, and only the nodes it joined (those whose
     * beams reached it, those its beams reached, and those charging a block with it) are worked out again.
     */
    private void drop(LaserNode node) {
        BlockPos pos = node.nodePos();
        if (nodes.get(pos) != node) return;
        Set<BlockPos> seeds = new HashSet<>(into.getOrDefault(pos, List.of()));
        List<LaserBeam> old = beams.remove(pos);
        if (old != null) {
            for (LaserBeam beam : old) {
                if (beam.end() == LaserBeam.End.NODE) seeds.add(beam.hit());
                else if (beam.end() == LaserBeam.End.SINK) seeds.addAll(chargedBy.getOrDefault(beam.hit(), Set.of()));
            }
            unindex(pos, old);
        }
        nodes.remove(pos);
        BlockPos source = drawsOn.remove(pos);
        if (source != null) seeds.add(source);
        into.remove(pos);
        carried.remove(pos);
        lost.remove(pos);
        ranges.remove(pos);
        lights.remove(pos);
        faceLights.remove(pos);
        cavityIn.remove(pos);
        actions.keySet().removeIf(key -> key.node().equals(pos));
        actionSinks.keySet().removeIf(key -> key.node().equals(pos));
        switched.remove(node);
        networks = networks.stream().filter(network -> !network.sources().contains(node)).toList();
        seeds.remove(pos);
        dirtyAt.addAll(seeds);
        bucketsStale = true;
    }

    /** The nodes whose beams pass through, or end at, {@code pos}: a node placed there now stands in their light. */
    private Set<BlockPos> beamsCrossing(BlockPos pos) {
        Set<BlockPos> crossing = new HashSet<>();
        beams.forEach((from, list) -> {
            for (LaserBeam beam : list) {
                if (pos.equals(beam.hit()) || beam.jumps() && pos.equals(beam.aim())) {
                    crossing.add(from);
                } else if (beam.aim() == null) {
                    // A straight beam: on its line, within its length.
                    int dx = pos.getX() - from.getX(), dy = pos.getY() - from.getY(), dz = pos.getZ() - from.getZ();
                    Direction face = beam.face();
                    int along = dx * face.getStepX() + dy * face.getStepY() + dz * face.getStepZ();
                    if (along >= 1 && along <= beam.length()
                            && dx == along * face.getStepX() && dy == along * face.getStepY() && dz == along * face.getStepZ()) {
                        crossing.add(from);
                    }
                } else if (beam.path().contains(pos)) {
                    crossing.add(from);
                }
            }
        });
        return crossing;
    }

    /** Whether the recheck slices need sorting again: nodes have come or gone. */
    private boolean bucketsStale;

    private void rebucket() {
        List<List<LaserNode>> buckets = new ArrayList<>();
        for (int i = 0; i < RECHECK_TICKS; i++) buckets.add(new ArrayList<>());
        for (LaserNode node : nodes.values()) buckets.get(Math.floorMod(node.nodePos().hashCode(), RECHECK_TICKS)).add(node);
        rechecks = buckets;
        bucketsStale = false;
    }

    /** For tests: the action {@code action} at {@code node} (beam -1 for an area): its whole cut's cost, and what it asks this tick. */
    public long[] costs(BlockPos node, int beam, Lens action) {
        LensAction found = actions.get(new LensAction.Key(node, beam, action));
        return found == null ? new long[] {-1, -1} : new long[] {found.cutCost(), found.demand()};
    }

    /** For tests: as a world loaded again, the level's networks worked out afresh from the same blocks, nothing kept. */
    public static void reloadForTests(ServerLevel level) {
        LaserNetworks old = LEVELS.remove(level);
        if (old == null) return;
        LaserNetworks fresh = of(level);
        for (LaserNode node : old.nodes.values()) fresh.put(node);
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
            List<net.minecraft.network.chat.Component> said = new ArrayList<>();
            for (LensAction action : actions.values()) {
                if (!action.key().node().equals(pos) || action.key().action() != stream.action()) continue;
                collectors = action.collectors().size();
                if (action.said() != null) said.add(action.said().copy().withStyle(body));
            }
            lines.add(net.minecraft.network.chat.Component.translatable("message.quantimium.spectroscope.stream",
                    net.minecraft.network.chat.Component.translatable(stream.action().itemKey()),
                    modifiers, stream.payers().size(), collectors).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            lines.addAll(said);
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
    /** @param rest ticks it waits before charging again, after a tick when nothing it reaches took any power */
    private record Network(List<LaserNode> sources, List<Sink> sinks, boolean cavities, long[] left, int[] rest, List<LensAction> actions) {}

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

    /** For tests: how many of those worked out only the networks round a change. */
    private long partialRebuilds;

    public long partialRebuilds() {
        return partialRebuilds;
    }

    private void rebuild() {
        dirty = false;
        dirtyAt.clear();
        rebuilds++;
        Map<BlockPos, List<LaserBeam>> before = new HashMap<>(beams);
        // What each beam has carried since the last settle is kept, so a rebuild (a block broken, say)
        // doesn't make the second's average look like nothing passed.
        Map<BlockPos, long[]> carriedBefore = new HashMap<>(carried);
        beams.clear();
        into.clear();
        chargedBy.clear();
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
        // What redstone switches, and how each stood now: watched each tick, rebuilt when one changes.
        switched.clear();
        for (LaserNode node : nodes.values()) {
            if (node.switches()) switched.put(node, node.switchState());
        }
        propagate(nodes.values(), before, null);
        Map<LensAction.Key, LensAction> was = new HashMap<>(actions);
        actions.clear();
        actionsUp(nodes.values(), was);
        keepCarried(beams.keySet(), carriedBefore);
        rebucket();
        networks = buildNetworks(nodes.values(), null);
        limited = nodes.values().stream().anyMatch(node -> node.relays() && node.passLimit() != Long.MAX_VALUE);
    }

    /**
     * Works out again only the networks the nodes at {@code seeds} are part of: every node joined to them by a beam
     * either way, or by charging the same block. Their beams, light, actions and networks are made anew; every other
     * network stays as it was. If light from them now reaches a node outside, two networks have joined: then
     * everything is worked out again.
     */
    private void rebuildAround(Set<BlockPos> seeds, int tries, Map<LensAction.Key, LensAction> carryActions) {
        List<BlockPos> present = seeds.stream().filter(nodes::containsKey).toList();
        dirtyAt.clear();
        // A node switched on or off traces the same beams as before (dark ones while off), but what they carry has
        // changed: never only its ends moved.
        boolean switchedHere = present.stream().anyMatch(switchedAt::contains);
        switchedAt.clear();
        if (present.isEmpty()) return;
        if (tries == 0 && !switchedHere && onlyEndsMoved(present)) return;
        Set<BlockPos> scope = group(present);
        // Nearly the whole level: as cheap to do it all.
        if (scope.size() * 10 > nodes.size() * 9) {
            rebuild();
            return;
        }
        rebuilds++;
        partialRebuilds++;
        Map<BlockPos, List<LaserBeam>> before = new HashMap<>();
        Map<BlockPos, long[]> carriedBefore = new HashMap<>();
        for (BlockPos pos : scope) {
            List<LaserBeam> old = beams.remove(pos);
            if (old != null) {
                before.put(pos, old);
                unindex(pos, old);
            }
            long[] had = carried.remove(pos);
            if (had != null) carriedBefore.put(pos, had);
            long[] lostHere = lost.remove(pos);
            if (lostHere != null && lostHere[0] > 0) QuantumFlux.emitFromEnergy(level, pos, lostHere[0]);
            ranges.remove(pos);
            lights.remove(pos);
            faceLights.remove(pos);
            cavityIn.remove(pos);
        }
        Map<LensAction.Key, LensAction> was = new HashMap<>(carryActions);
        actions.entrySet().removeIf(entry -> {
            if (!scope.contains(entry.getKey().node())) return false;
            was.put(entry.getKey(), entry.getValue());
            return true;
        });
        actionSinks.keySet().removeIf(key -> scope.contains(key.node()));
        List<Network> kept = new ArrayList<>();
        for (Network network : networks) {
            if (network.sources().stream().noneMatch(source -> scope.contains(source.nodePos()))) kept.add(network);
        }
        List<LaserNode> group = new ArrayList<>(scope.size());
        for (BlockPos pos : scope) group.add(nodes.get(pos));
        switched.keySet().removeIf(node -> scope.contains(node.nodePos()));
        for (LaserNode node : group) {
            if (node.switches()) switched.put(node, node.switchState());
        }
        // Its light now reaches another network: the two are one now. Again, with that one too (its beams already
        // reach it, so it's in the group next time); after a few joins in one go, everything.
        if (!propagate(group, before, scope)) {
            if (tries < 3) rebuildAround(new HashSet<>(present), tries + 1, was);
            else rebuild();
            return;
        }
        actionsUp(group, was);
        keepCarried(scope, carriedBefore);
        List<Network> built = buildNetworks(group, scope);
        if (built == null) {
            if (tries < 3) rebuildAround(new HashSet<>(present), tries + 1, was);
            else rebuild();
            return;
        }
        kept.addAll(built);
        networks = kept;
        limited = nodes.values().stream().anyMatch(node -> node.relays() && node.passLimit() != Long.MAX_VALUE);
    }

    /**
     * The common case of a change along a beam: a block cut, placed or used where it ended, so the beam now stops
     * somewhere else, but on the same kind of thing (open air or a block it can't charge), or on the same node or
     * charged block. Nothing it carries or pays for changes: its node's beams are traced again and its actions told,
     * and every network stays as it was. Whether that was all it took for every node at {@code positions}.
     */
    private boolean onlyEndsMoved(List<BlockPos> positions) {
        Map<BlockPos, List<LaserBeam>> fresh = new HashMap<>();
        for (BlockPos pos : positions) {
            LaserNode node = nodes.get(pos);
            List<LaserBeam> was = beams.get(pos);
            if (node == null || was == null) return false;
            Light light = lights.get(pos);
            List<LaserBeam> now = trace(node, rangeOf(node), light == null ? 0 : light.marks());
            if (now.size() != was.size()) return false;
            for (int i = 0; i < now.size(); i++) {
                if (!sameEnding(was.get(i), now.get(i))) return false;
            }
            fresh.put(pos, now);
        }
        rebuilds++;
        partialRebuilds++;
        Map<LensAction.Key, LensAction> was = new HashMap<>();
        List<LaserNode> moved = new ArrayList<>();
        for (BlockPos pos : positions) {
            LaserNode node = nodes.get(pos);
            Map<BlockPos, List<LaserBeam>> before = Map.of(pos, beams.get(pos));
            traceAt(node, rangeOf(node), before);
            moved.add(node);
        }
        actions.entrySet().removeIf(entry -> {
            if (!fresh.containsKey(entry.getKey().node())) return false;
            was.put(entry.getKey(), entry.getValue());
            return true;
        });
        actionsUp(moved, was);
        // An action gone, or a new one with no way to be paid for: everything again, next tick.
        for (LensAction.Key key : was.keySet()) {
            if (!actions.containsKey(key)) dirty = true;
        }
        for (LensAction.Key key : actions.keySet()) {
            if (fresh.containsKey(key.node()) && !was.containsKey(key)) dirty = true;
        }
        return true;
    }

    /** Whether two beams leave the same way and end on the same kind of thing: the same node or charged block, or neither. */
    private static boolean sameEnding(LaserBeam a, LaserBeam b) {
        if (a.face() != b.face() || !java.util.Objects.equals(a.aim(), b.aim()) || a.end() != b.end()) return false;
        if (a.end() == LaserBeam.End.NODE || a.end() == LaserBeam.End.SINK) return java.util.Objects.equals(a.hit(), b.hit());
        return true;
    }

    /** Takes {@code pos}'s old beams off the indexes of what beams reach. */
    private void unindex(BlockPos pos, List<LaserBeam> old) {
        for (LaserBeam beam : old) {
            if (beam.end() == LaserBeam.End.NODE) {
                List<BlockPos> list = into.get(beam.hit());
                if (list != null) list.remove(pos);
            } else if (beam.end() == LaserBeam.End.SINK) {
                Set<BlockPos> set = chargedBy.get(beam.hit());
                if (set != null) set.remove(pos);
            }
        }
    }

    /**
     * Light from each source among {@code which}, then on through each relay it reaches, at the reach of the light
     * that gets there; again wherever more light arrives. Light only adds up, so this settles. With a {@code scope},
     * whether the light stayed within it (false: it reached a node outside, and nothing here can be trusted).
     */
    private boolean propagate(java.util.Collection<LaserNode> which, Map<BlockPos, List<LaserBeam>> before, @Nullable Set<BlockPos> scope) {
        ArrayDeque<BlockPos> work = new ArrayDeque<>();
        for (LaserNode node : which) {
            if (!node.generates()) continue;
            // A switched-off source gives out no light: none of its reach, nor what it does.
            if (!node.enabled()) {
                traceAt(node, node.range(), before);
                if (escapes(node.nodePos(), scope)) return false;
                continue;
            }
            Light light = node.emits();
            setLight(node.nodePos(), light);
            traceAt(node, light.reach(), before);
            if (escapes(node.nodePos(), scope)) return false;
            hits(node.nodePos(), work);
        }
        int steps = 0;
        int cap = Math.max(64, which.size() * 16);
        while (!work.isEmpty() && steps++ < cap) {
            BlockPos at = work.poll();
            LaserNode node = nodes.get(at);
            // A switched-off relay passes nothing on: the light beyond it is only what reaches it some other way.
            if (node == null || !node.relays() || !passes(node)) continue;
            Light in = incoming(node);
            if (in == null) continue;
            Light out = node.isolates() ? in.refocused().isolated() : node.refocuses() ? in.refocused().through(node.lenses()) : in;
            if (node.rifts()) out = out.throughRift();
            // One that gives out light of its own too (a Solar Emitter): its own joins what it passes on.
            if (node.generates()) out = Light.join(out, node.emits());
            if (out.equals(lights.get(at))) continue;
            setLight(at, out);
            traceAt(node, out.reach(), before);
            if (escapes(at, scope)) return false;
            hits(at, work);
        }
        // Relays no light reaches still show where light would go, at their own range.
        for (LaserNode node : which) {
            if (node.relays() && !lights.containsKey(node.nodePos())) {
                traceAt(node, node.range(), before);
                if (escapes(node.nodePos(), scope)) return false;
            }
        }
        for (LaserNode node : which) {
            if (!node.buffers()) continue;
            Light in = incoming(node);
            if (in != null) cavityIn.put(node.nodePos(), in.refocused());
        }
        return true;
    }

    /** Whether {@code pos}'s beams now reach a node, or share a block with a node, outside {@code scope}. */
    private boolean escapes(BlockPos pos, @Nullable Set<BlockPos> scope) {
        if (scope == null) return false;
        for (LaserBeam beam : beams.getOrDefault(pos, List.of())) {
            if (beam.end() == LaserBeam.End.NODE && nodes.containsKey(beam.hit()) && !scope.contains(beam.hit())) return true;
            if (beam.end() == LaserBeam.End.SINK) {
                for (BlockPos other : chargedBy.getOrDefault(beam.hit(), Set.of())) if (!scope.contains(other)) return true;
            }
        }
        return false;
    }

    /** What each beam of the nodes at {@code which} had carried since the last settle, kept. */
    private void keepCarried(java.util.Collection<BlockPos> which, Map<BlockPos, long[]> carriedBefore) {
        for (BlockPos pos : which) {
            List<LaserBeam> list = beams.get(pos);
            if (list == null) continue;
            long[] sums = new long[list.size()];
            long[] had = carriedBefore.get(pos);
            if (had != null) System.arraycopy(had, 0, sums, 0, Math.min(had.length, sums.length));
            carried.put(pos, sums);
        }
    }

    /**
     * The networks of the sources among {@code which}: every node reached from each, nearest first (each hop costs
     * the tax, so fewest hops is best). With a {@code scope}, null if a network reaches outside it.
     */
    @Nullable
    private List<Network> buildNetworks(java.util.Collection<LaserNode> which, @Nullable Set<BlockPos> scope) {
        List<Network> built = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        for (LaserNode start : which) {
            if (!start.generates() || claimed.contains(start.nodePos())) continue;
            List<LaserNode> sources = new ArrayList<>();
            Map<Object, Sink> sinks = new LinkedHashMap<>();
            // The sources joined to this one through shared relays share the network.
            Set<BlockPos> joined = group(List.of(start.nodePos()));
            if (scope != null && !scope.containsAll(joined)) return null;
            for (BlockPos pos : joined) {
                LaserNode source = nodes.get(pos);
                if (source == null || !source.generates()) continue;
                claimed.add(pos);
                sources.add(source);
                reach(source, sources.size() - 1, sinks);
            }
            boolean cavities = sources.stream().anyMatch(LaserNode::buffers) || sinks.values().stream().anyMatch(sink -> sink.buffer() != null);
            List<Sink> all = List.copyOf(sinks.values());
            List<LensAction> acting = new ArrayList<>();
            for (Sink sink : all) if (sink.action() != null) acting.add(sink.action());
            Network network = new Network(sources, all, cavities, new long[sources.size()], new int[1], List.copyOf(acting));
            built.add(network);
            for (Sink sink : network.sinks()) {
                if (sink.action() != null) actionSinks.put(sink.action().key(), new ActionSink(network, sink));
            }
        }
        return built;
    }

    /**
     * Every node joined to those at {@code seeds}, nearest first: by a beam either way, or by charging the same block,
     * so that block draws on all of them.
     */
    private Set<BlockPos> group(java.util.Collection<BlockPos> seeds) {
        Set<BlockPos> found = new java.util.LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos seed : seeds) {
            if (nodes.containsKey(seed) && found.add(seed)) queue.add(seed);
        }
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (LaserBeam beam : beams.getOrDefault(at, List.of())) {
                if (beam.end() == LaserBeam.End.NODE) {
                    if (nodes.containsKey(beam.hit()) && found.add(beam.hit())) queue.add(beam.hit());
                } else if (beam.end() == LaserBeam.End.SINK) {
                    for (BlockPos other : chargedBy.getOrDefault(beam.hit(), Set.of())) {
                        if (nodes.containsKey(other) && found.add(other)) queue.add(other);
                    }
                }
            }
            for (BlockPos from : into.getOrDefault(at, List.of())) {
                if (nodes.containsKey(from) && found.add(from)) queue.add(from);
            }
        }
        return found;
    }

    /** Traces {@code node}'s beams at {@code range}; a beam that runs as it did keeps its brightness, so a change elsewhere doesn't make it flicker. */
    private void traceAt(LaserNode node, int range, Map<BlockPos, List<LaserBeam>> before) {
        ranges.put(node.nodePos(), range);
        Light light = lights.get(node.nodePos());
        List<LaserBeam> traced = trace(node, range, light == null ? 0 : light.marks());
        // What this node's beams end on, now: off the index for the old beams, on for the new.
        unindex(node.nodePos(), beams.getOrDefault(node.nodePos(), List.of()));
        for (LaserBeam beam : traced) {
            if (beam.end() == LaserBeam.End.NODE) into.computeIfAbsent(beam.hit(), key -> new ArrayList<>()).add(node.nodePos());
            else if (beam.end() == LaserBeam.End.SINK) chargedBy.computeIfAbsent(beam.hit(), key -> new HashSet<>()).add(node.nodePos());
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
    private void actionsUp(java.util.Collection<LaserNode> which, Map<LensAction.Key, LensAction> was) {
        for (LaserNode node : which) {
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
                                sendersFor(stream, senders.computeIfAbsent(stream.payers(), payers -> upFor(pos, payers, LaserNode::sends))),
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
                                sendersFor(stream, senders.computeIfAbsent(stream.payers(), payers -> upFor(pos, payers, LaserNode::sends))),
                                node.area(), core.reach(), core.marks(), 0);
                        action.within(node::inArea);
                        actions.put(key, action);
                    }
                }
            }
            node.showBeams(list);
        }
    }

    /**
     * The senders a stream's lens may take from: a logistics network (its controller) only through a Filter lens,
     * so an unfiltered Insert, Placement or Interact lens doesn't empty the network; Laser Ports always.
     */
    private List<BlockPos> sendersFor(Light.Stream stream, List<BlockPos> senders) {
        if (stream.filter() != null) return senders;
        return senders.stream().filter(pos -> !(nodes.get(pos) instanceof LogisticsController)).toList();
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
            if (((!node.relays() && !node.buffers()) || stopsItems(node)) && !at.equals(pos)) continue;
            for (BlockPos up : into.getOrDefault(at, List.of())) {
                if (letsIn(up, node) && seen.add(up)) queue.add(up);
            }
        }
        return found;
    }

    /** Whether light from {@code from} gets into {@code node}: one of its beams ends there, travelling a way it accepts. */
    private boolean letsIn(BlockPos from, LaserNode node) {
        for (LaserBeam beam : beams.getOrDefault(from, List.of())) {
            if (beam.end() == LaserBeam.End.NODE && node.nodePos().equals(beam.hit()) && admits(node, beam)) return true;
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
            // A Logistics Controller sends only to lenses that ask, never down the beam by itself.
            if (!node.sends() || node instanceof LogisticsController) continue;
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

    /**
     * Whether items can't pass {@code node} between Laser Ports: an Isolator, or an Isolator lens in it not set to
     * pass actions (what the ports carry the yield of).
     */
    private static boolean stopsItems(LaserNode node) {
        if (node.isolates()) return true;
        for (LensUse use : node.lenses()) {
            if (use.lens() == Lens.ISOLATE && (use.isolator() == null || !use.isolator().actions())) return true;
        }
        return false;
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
                if (next == null || !next.relays() || stopsItems(next) || !passes(next)) continue;
                if (next.collects()) found.add(beam.hit());
                queue.add(beam.hit());
            }
        }
        return found;
    }

    /**
     * The Logistics Controller whose light reaches the node at {@code pos}, or null for none, or for light from
     * two (which stops both).
     */
    @Nullable
    public static LogisticsController logisticsAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        LaserNetworks networks = LEVELS.get(server);
        if (networks == null) return null;
        Light light = networks.lights.get(pos);
        if (light == null || light.logistics().size() != 1) return null;
        return networks.nodes.get(light.logistics().iterator().next()) instanceof LogisticsController controller ? controller : null;
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

    /**
     * Whether the collecting Laser Ports at {@code positions} would take all of {@code stacks} together: what the
     * first fills, the second can't use (seeds and wheat both wanting the one empty slot). Tried for real in a
     * transaction that's never committed.
     */
    boolean roomForAllTogether(List<BlockPos> positions, List<ItemStack> stacks) {
        var outer = net.neoforged.neoforge.transfer.transaction.Transaction.getCurrentOpenedTransaction();
        try (var transaction = outer == null ? net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()
                : net.neoforged.neoforge.transfer.transaction.Transaction.open(outer)) {
            Map<Object, Object> scratch = new HashMap<>();
            for (ItemStack stack : stacks) {
                ItemStack left = stack;
                for (BlockPos pos : positions) {
                    LaserNode node = nodes.get(pos);
                    if (node != null && node.collects() && !left.isEmpty()) left = node.collectAlong(left, transaction, scratch);
                }
                if (!left.isEmpty()) return false;
            }
            return true;
        }
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
        fe *= action.costFactor();
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
                if (beam.end() != LaserBeam.End.NODE || !pos.equals(beam.hit()) || !admits(node, beam)) continue;
                Light light = lightOf(from, beam);
                if (light == null) continue;
                joined = Light.join(joined, node.refocuses() ? light : light.passedOn(beam.length()));
            }
        }
        return joined;
    }

    private int rangeOf(LaserNode node) {
        Integer range = ranges.get(node.nodePos());
        return range != null ? range : node.range();
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
                if (beam.end() == LaserBeam.End.NODE && node.nodePos().equals(beam.hit()) && admits(node, beam)) arriving.add(beam.face());
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
            // Straight to the middle of the face it was linked by, at whatever angle, and in by that face; to a laser block's
            // focus, its middle.
            Direction inBy = level.isLoaded(target) && level.getBlockEntity(target) instanceof LaserNode ? null : node.linkFace(target);
            traced.add(LaserBeam.traceTo(level, node.nodePos(), target, inBy, range, marks));
        }
        // And to each Entangled Mirror drawing on it, at once.
        for (BlockPos mirror : drawing(node.nodePos())) traced.add(LaserBeam.jump(level, node.nodePos(), mirror));
        return traced;
    }

    /**
     * Whether light along {@code beam} gets into {@code node}: entangled light into an Entangled Mirror only, and
     * light down a line as the node accepts it.
     */
    private static boolean admits(LaserNode node, LaserBeam beam) {
        if (beam.jumps()) return node.entangledFace() != null;
        return beam.ray() != null ? node.accepts(beam.ray()) : node.accepts(beam.face());
    }

    /**
     * Whether light gets through a relay: it's switched on, or it's a Relay with every emitter off and an Entangled
     * Mirror drawing on it, which gets the light all the same.
     */
    private boolean passes(LaserNode node) {
        return node.enabled() || node.passesEntangled() && !drawing(node.nodePos()).isEmpty();
    }

    /** The Entangled Mirrors drawing on the node at {@code source}, in a fixed order. */
    private List<BlockPos> drawing(BlockPos source) {
        List<BlockPos> mirrors = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockPos> entry : drawsOn.entrySet()) if (entry.getValue().equals(source)) mirrors.add(entry.getKey());
        if (mirrors.size() > 1) mirrors.sort(java.util.Comparator.naturalOrder());
        return mirrors;
    }

    /** An Entangled Mirror's Tesseract went in, came out or was changed: light from the old and new source is traced again. */
    public static void relinked(Level level, LaserNode mirror) {
        if (level instanceof ServerLevel server) of(server).relink(mirror);
    }

    private void relink(LaserNode mirror) {
        BlockPos pos = mirror.nodePos().immutable();
        BlockPos source = mirror.entangledSource();
        BlockPos old = source == null ? drawsOn.remove(pos) : drawsOn.put(pos, source.immutable());
        if (old != null) dirtyAt.add(old);
        if (source != null) dirtyAt.add(source.immutable());
        dirtyAt.add(pos);
    }

    /** Whether one of the node's links is to a block that's gone: light passes through both, so its beam alone doesn't show it. */
    private boolean linkGone(LaserNode node) {
        if (node.keepsLinks()) return false;
        for (BlockPos target : node.targets()) {
            if (level.isLoaded(target) && level.getBlockState(target).isAir()) return true;
        }
        return false;
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
                    // One that light charges (a Logistics Controller) is a block that takes power too, though never from itself.
                    if (next != null && next.chargedByLight() && next != source) {
                        route(sinks, beam.hit(), beam.face().getOpposite(), null, source.nodePos(), index, path, visit.efficiency());
                    }
                    // One that hands light on to a machine beside it (a Receptacle powering what it's on): that machine.
                    net.minecraft.core.Direction feeds = next == null || next == source ? null : next.feeds();
                    if (feeds != null) {
                        route(sinks, beam.hit().relative(feeds), feeds.getOpposite(), null, source.nodePos(), index, path, visit.efficiency());
                    }
                    if (next != null && next.relays() && admits(next, beam) && seen.add(beam.hit())) {
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
        LaserProfile profile = LaserProfile.of(level);
        if (nodes.isEmpty()) {
            if (profile != null) profile.tickDone(level);
            return;
        }
        long mark = profile == null ? 0 : System.nanoTime();
        long time = level.getGameTime();
        // A slice of the nodes each tick: has anything been built across, or taken out of, their beams?
        // Something redstone switches has been switched: what light carries changes.
        if (bucketsStale) rebucket();
        if (!dirty) {
            // Each switched one marks only its own network to be worked out again.
            for (Map.Entry<LaserNode, Integer> entry : switched.entrySet()) {
                if (entry.getKey().switchState() != entry.getValue()) {
                    dirtyAt.add(entry.getKey().nodePos());
                    switchedAt.add(entry.getKey().nodePos());
                }
            }
            for (LaserNode node : rechecks.isEmpty() ? List.<LaserNode>of() : rechecks.get(Math.floorMod(-time, RECHECK_TICKS))) {
                List<LaserBeam> was = beams.get(node.nodePos());
                if (was == null || was.size() != node.outputs().size() + node.targets().size() + drawing(node.nodePos()).size() || linkGone(node)) {
                    dirtyAt.add(node.nodePos());
                    continue;
                }
                for (LaserBeam beam : was) {
                    if (!beam.stillHolds(level, rangeOf(node, beam))) {
                        dirtyAt.add(node.nodePos());
                        break;
                    }
                }
            }
        }
        if (dirty) {
            rebuild();
            switchedAt.clear();
        } else if (!dirtyAt.isEmpty()) rebuildAround(new HashSet<>(dirtyAt), 0, new HashMap<>());
        if (profile != null) mark = profile.phase(LaserProfile.Phase.BEAMS, mark);
        // What each source has this tick. Work done at once (a hit, an item carried, a block placed) pays
        // first, so machines taking everything can't starve it; blocks are charged from what's left.
        for (Network network : networks) refill(network);
        if (profile == null) {
            for (LensAction action : actions.values()) action.act(level, this);
        } else {
            for (LensAction action : actions.values()) {
                action.act(level, this);
                long at = System.nanoTime();
                profile.action(action.key(), at - mark);
                mark = profile.phase(LaserProfile.Phase.LENSES, mark);
            }
        }
        if (time % SETTLE_TICKS == 10) ship();
        // Each logistics network moves once a second, in its own slice of it, not all in one tick.
        Logistics.run(level, nodes.values(), lights, (int) Math.floorMod(time, SETTLE_TICKS));
        if (time % 2 == 0) showAreaWork();
        if (profile != null) mark = profile.phase(LaserProfile.Phase.LOGISTICS, mark);
        passing.clear();
        for (Network network : networks) move(network);
        if (profile != null) mark = profile.phase(LaserProfile.Phase.POWER, mark);
        if (time % SETTLE_TICKS == 0) settle();
        if (profile != null) {
            profile.phase(LaserProfile.Phase.SETTLE, mark);
            profile.tickDone(level);
        }
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
        // Nothing it reaches took power last time: it asks again in a while, not every tick. An action that
        // wants power now (a Harvest beam on to its next block) wakes it, so a rest never sets a beam's pace.
        if (network.rest()[0] > 0 && !anyActionWants(network)) {
            network.rest()[0]--;
            return;
        }
        long[] had = left.clone();
        charge(network, left, false, false);
        if (network.cavities()) {
            charge(network, left, false, true);
            charge(network, left, true, false);
        }
        boolean gave = false;
        for (int i = 0; i < left.length; i++) {
            if (had[i] > left[i]) {
                sources.get(i).draw(had[i] - left[i]);
                gave = true;
            }
        }
        network.rest()[0] = gave ? 0 : LaserTuning.FULL_REST;
    }

    private static boolean anyActionWants(Network network) {
        for (LensAction action : network.actions()) if (action.demand() > 0) return true;
        return false;
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
        double[] cap = fairShares(asks, sinks, asked, left);
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
                give[r] = Math.min(left[source], (long) Math.ceil(Math.min(ask[r], cap[source])));
                // Rounding up never takes a route past what a relay with a limit still lets through.
                if (limited) {
                    for (Hop hop : routes.get(r).path()) {
                        if (hop.relay() != null && hop.relay().passLimit() != Long.MAX_VALUE) {
                            give[r] = Math.min(give[r], Math.max(0, passingLeft(hop.relay())[0]));
                        }
                    }
                }
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

    /**
     * The most each source gives any one block this pass. A source with enough for every ask has no cap. One
     * asked for more than it has shares it out fairly: each block an even share, a block asking for less taking
     * only what it asks, the rest split again among the others. So one big buffer filling up can't starve the
     * machines beside it.
     */
    private static double[] fairShares(double[][] asks, List<Sink> sinks, double[] asked, long[] left) {
        double[] cap = new double[left.length];
        java.util.Arrays.fill(cap, Double.POSITIVE_INFINITY);
        int[] count = new int[left.length];
        boolean short_ = false;
        for (int s = 0; s < left.length; s++) short_ |= asked[s] > left[s];
        if (!short_) return cap;
        for (int i = 0; i < sinks.size(); i++) {
            if (asks[i] == null) continue;
            List<Route> routes = sinks.get(i).routes();
            for (int r = 0; r < routes.size(); r++) if (asks[i][r] > 0 && asked[routes.get(r).index()] > left[routes.get(r).index()]) count[routes.get(r).index()]++;
        }
        double[][] bySource = new double[left.length][];
        for (int s = 0; s < left.length; s++) if (count[s] > 0) bySource[s] = new double[count[s]];
        int[] filled = new int[left.length];
        for (int i = 0; i < sinks.size(); i++) {
            if (asks[i] == null) continue;
            List<Route> routes = sinks.get(i).routes();
            for (int r = 0; r < routes.size(); r++) {
                int s = routes.get(r).index();
                if (asks[i][r] > 0 && bySource[s] != null) bySource[s][filled[s]++] = asks[i][r];
            }
        }
        for (int s = 0; s < left.length; s++) {
            double[] each = bySource[s];
            if (each == null) continue;
            java.util.Arrays.sort(each);
            double remaining = left[s];
            cap[s] = 0;
            for (int k = 0; k < each.length; k++) {
                double share = remaining / (each.length - k);
                if (each[k] > share) {
                    cap[s] = share;
                    break;
                }
                remaining -= each[k];
                cap[s] = each[k];
            }
        }
        return cap;
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
        // A few rounds: each relay over its limit scales down the routes through it, which can leave another
        // with room or still over; each round only ever lowers asks, by the tightest relay on the route.
        for (int round = 0; round < 8; round++) {
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
            Map<LaserNode, Double> factor = new HashMap<>();
            for (Map.Entry<LaserNode, Double> entry : through.entrySet()) {
                double room = Math.max(0, passingLeft(entry.getKey())[0]);
                if (entry.getValue() > room * 1.0001) factor.put(entry.getKey(), entry.getValue() > 0 ? room / entry.getValue() : 0.0);
            }
            if (factor.isEmpty()) break;
            for (int i = 0; i < sinks.size(); i++) {
                if (asks[i] == null) continue;
                List<Route> routes = sinks.get(i).routes();
                for (int r = 0; r < routes.size(); r++) {
                    if (asks[i][r] <= 0) continue;
                    double scale = 1.0;
                    for (Hop hop : routes.get(r).path()) {
                        Double f = hop.relay() == null ? null : factor.get(hop.relay());
                        if (f != null) scale = Math.min(scale, f);
                    }
                    asks[i][r] *= scale;
                }
            }
        }
        java.util.Arrays.fill(asked, 0);
        for (int i = 0; i < sinks.size(); i++) {
            if (asks[i] == null) continue;
            List<Route> routes = sinks.get(i).routes();
            for (int r = 0; r < routes.size(); r++) if (asks[i][r] > 0) asked[routes.get(r).index()] += asks[i][r];
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
            if (hop.relay() != null && !passes(hop.relay())) return false;
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
            // A Logistics Controller gives no power, but its light shows while its store can pay for moves.
            // One whose power all went out this tick (a cavity filling took the lot) shines too: its other beams
            // still glow dimly, rather than going dark beside the one carrying it all.
            boolean shines = node.available() > 0 || node instanceof LogisticsController controller && controller.powered()
                    || sending(node.nodePos());
            if (node.generates() && node.enabled() && shines && live.add(node.nodePos())) queue.add(node.nodePos());
        }
        while (!queue.isEmpty()) {
            for (LaserBeam beam : beams.getOrDefault(queue.poll(), List.of())) {
                if (beam.end() != LaserBeam.End.NODE) continue;
                LaserNode next = nodes.get(beam.hit());
                if (next != null && next.relays() && passes(next) && live.add(beam.hit())) queue.add(beam.hit());
            }
        }
        return live;
    }

    /** Whether any beam leaving {@code pos} carried power when last worked out. */
    private boolean sending(BlockPos pos) {
        for (LaserBeam beam : beams.getOrDefault(pos, List.of())) if (beam.flow() > 0) return true;
        return false;
    }

    /** A beam's brightness, in steps that change only when its flow changes by a lot: 0 for dark. */
    public static int brightness(long flow) {
        if (flow <= 0) return 0;
        return 1 + (int) Math.min(6, Math.floor(Math.log10(flow)));
    }
}
