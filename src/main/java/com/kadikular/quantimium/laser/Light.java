package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What light leaving a node carries, besides power: how far a run of it reaches, and what it does.
 *
 * <p><b>Range</b> comes from where the light started: a mirror refocuses it to its source's range, not
 * its own, so a Flash Emitter's light reaches 8 blocks a run however many mirrors it passes. Light with
 * an action reaches {@link LaserTuning#ACTION_REACH} instead. Each Range lens doubles it, up to two.
 * Where light joins, the longer reach wins.
 *
 * <p>Each <b>stream</b> is one action, its modifiers, and the sources that pay for it: those whose light
 * reached the lens that gave it. Joined light keeps both sides' streams; the same action from two sides
 * becomes one, paid by both. A modifier changes the actions the light already carries when it meets it:
 * those in slots before it in its mount, and those from mounts before; never one added after it, so a
 * Filter must come after the Harvest it filters. Pickup and Void are the exceptions: each stays on the light, and
 * actions added after it get it too. A Pickup before a join stays on its own side; a Void carries on past it.
 * Pickup is also a stream of its own, paid for like an action: it gathers items lying in the beam. So is
 * Smelting, which acts only where the light carries nothing else (see {@link #acting()}).
 *
 * @param range   the reach of a run of plain light from its source, before any Range lens
 * @param boosts  Range lenses met, up to {@link LaserTuning#RANGE_LENSES}
 * @param streams what the light does, one stream per action
 * @param feeders the sources whose light this is: emitters, or cavities giving out what they hold
 * @param left    for light through a photodetector, which doesn't refocus: blocks it has left; -1 otherwise
 * @param marks   what stays on the light once it's met it ({@link #UV}, {@link #PHANTOM}, {@link #MIRRORED})
 * @param logistics the Logistics Controllers whose light this is, each with its channel: it joins what it reaches on
 *                  that channel to their networks. An Isolator lets power through but not this.
 * @param voiding  the Void lenses the light has met, by channel: like Pickup, each stays on the light, and actions added
 *                  after it on its channel get it too (its mode and list)
 * @param speeds   Speed lenses met, by channel, two bits each ({@link #speeds(int)}): they quicken a logistics beam's
 *                  receptacles on that channel as they do an action's
 */
public record Light(int range, int boosts, List<Stream> streams, Set<BlockPos> feeders, int left, int marks, Map<BlockPos, Integer> logistics,
                    Map<Integer, LensUse> voiding, long speeds) {

    public Light {
        logistics = Map.copyOf(logistics);
        voiding = Map.copyOf(voiding);
    }

    public Light(int range, int boosts, List<Stream> streams, Set<BlockPos> feeders, int left, int marks, Map<BlockPos, Integer> logistics) {
        this(range, boosts, streams, feeders, left, marks, logistics, Map.of(), 0L);
    }

    public Light(int range, int boosts, List<Stream> streams, Set<BlockPos> feeders, int left, int marks) {
        this(range, boosts, streams, feeders, left, marks, Map.of());
    }

    /** This light, from a Logistics Controller at {@code controller} on {@code channel}. */
    public Light controlledBy(BlockPos controller, int channel) {
        return new Light(range, boosts, streams, feeders, left, marks, Map.of(controller, Channels.of(channel)), voiding, speeds);
    }

    /** This light, from a Logistics Controller at {@code controller} on channel 0. */
    public Light controlledBy(BlockPos controller) {
        return controlledBy(controller, 0);
    }

    /**
     * A Redstone Lens's stream: its settings, and the block it's in ({@code from}: where a lens sending on its block's
     * signal reads it; null where that isn't known).
     */
    public record Signal(RedstoneSettings settings, @Nullable BlockPos from) {}

    /** Past an Isolator Lens that stops power: it powers nothing, though what it does is still paid for. */
    public static final int UNPOWERED = 16;

    public boolean unpowered() {
        return (marks & UNPOWERED) != 0;
    }

    /** Seen only with a wrench or the Linker in hand. */
    public static final int UV = 1;
    /** Passes through blocks: only laser blocks, and blocks that take power, stop it. */
    public static final int PHANTOM = 2;
    /** Through a rift, in the mirror: it acts only on what's in the mirror, and is seen only through a Mirror Lens. */
    public static final int MIRRORED = 4;
    /** Where Speed lenses met were kept before channels: two bits of the marks, read as channel 0's. */
    private static final int OLD_SPEEDS_SHIFT = 8;
    private static final int OLD_SPEEDS = 3 << OLD_SPEEDS_SHIFT;

    /** Speed lenses met on {@code channel}, up to {@link LaserTuning#SPEED_LENSES}: what a logistics receptacle on it is quickened by. */
    public int speeds(int channel) {
        return (int) (speeds >> 2 * Channels.of(channel) & 3);
    }

    /** {@code speeds} with {@code channel}'s set to {@code to} (every channel's, for All). */
    private static long withSpeed(long speeds, int channel, int to) {
        int capped = Math.clamp(to, 0, LaserTuning.SPEED_LENSES);
        for (int c = 0; c < Channels.COUNT; c++) {
            if (!Channels.changes(channel, c)) continue;
            speeds = speeds & ~(3L << 2 * c) | (long) capped << 2 * c;
        }
        return speeds;
    }

    /** One more Speed lens on {@code channel} (on every channel, for All). */
    private static long faster(long speeds, int channel) {
        for (int c = 0; c < Channels.COUNT; c++) {
            if (Channels.changes(channel, c)) speeds = withSpeed(speeds, c, (int) (speeds >> 2 * c & 3) + 1);
        }
        return speeds;
    }

    /** The most of each channel's speeds. */
    private static long mostSpeeds(long a, long b) {
        long most = 0;
        for (int c = 0; c < Channels.COUNT; c++) most |= Math.max(a >> 2 * c & 3, b >> 2 * c & 3) << 2 * c;
        return most;
    }

    public boolean uv() {
        return (marks & UV) != 0;
    }

    public boolean phantom() {
        return (marks & PHANTOM) != 0;
    }

    public boolean mirrored() {
        return (marks & MIRRORED) != 0;
    }

    /** The light through a rift: into the mirror, or back out of it. */
    public Light throughRift() {
        return new Light(range, boosts, streams, feeders, left, marks ^ MIRRORED, logistics, voiding, speeds);
    }

    /**
     * One action the light carries, its modifiers (bits of {@link Lens}), and who pays for it; how many
     * Amplify lenses it's met, and what a Filter lens lets it act on (null for everything). {@code steps}
     * are the Smelting and Crushing lenses it's met, in order, each one a step what it frees or moves goes
     * through: Crushing, Crushing, Smelting makes cobblestone gravel, then sand, then glass. For Smelting's
     * and Crushing's own streams, {@code inWorld}: it changed no action before it, so it works on the world
     * itself, on what the beam ends on.
     */
    /**
     * {@code interact}: an Interact lens's settings, as {@link LensUse#interact} packs them (whether it hits, and what
     * it works on).
     */
    public record Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed, int interact,
                         int fortune, List<Lens> steps, boolean inWorld, @Nullable LensFilter voids, @Nullable Signal signal, int channel) {
        public Stream {
            steps = List.copyOf(steps);
        }

        /** On channel 0. */
        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed, int interact,
                      int fortune, List<Lens> steps, boolean inWorld, @Nullable LensFilter voids, @Nullable Signal signal) {
            this(action, modifiers, payers, amps, filter, speed, interact, fortune, steps, inWorld, voids, signal, 0);
        }

        /** The same, on {@code channel}. */
        public Stream onChannel(int channel) {
            return new Stream(action, modifiers, payers, amps, filter, speed, interact, fortune, steps, inWorld, voids, signal, channel);
        }

        /** Where it's kept among a light's streams: its action, and its channel. */
        int key() {
            return Light.key(action, channel);
        }

        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed, int interact,
                      int fortune, List<Lens> steps, boolean inWorld, @Nullable LensFilter voids) {
            this(action, modifiers, payers, amps, filter, speed, interact, fortune, steps, inWorld, voids, null);
        }

        /** {@code voids}: a Void lens's list of what it destroys (only those, or all but those); null for everything. */
        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed, int interact,
                      int fortune, List<Lens> steps, boolean inWorld) {
            this(action, modifiers, payers, amps, filter, speed, interact, fortune, steps, inWorld, null);
        }

        /** Whether a Void lens on it destroys {@code stack}: it has one, and its list (if it has one) lets it. */
        public boolean voids(net.minecraft.world.item.ItemStack stack) {
            return has(Lens.VOID) && (voids == null || voids.lets(stack));
        }

        /** Whether a Void lens on it destroys {@code fluid}, as {@link #voids(net.minecraft.world.item.ItemStack)}. */
        public boolean voids(net.neoforged.neoforge.fluids.FluidStack fluid) {
            return has(Lens.VOID) && (voids == null || voids.lets(fluid));
        }

        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed, boolean hits, int fortune) {
            this(action, modifiers, payers, amps, filter, speed, hits ? 1 : 0, fortune, List.of(), false);
        }

        /** Changed by a Void lens set to void everything it frees, not only what nothing has room for. */
        public boolean voidsAll() {
            return has(Lens.VOID) && (interact & LensUse.VOID_ALL) != 0;
        }

        /** An Interact lens set to hit (a left click), not use. */
        public boolean hits() {
            return (interact & LensUse.HITS) != 0;
        }

        /** What an Interact lens works on: {@link LensUse#BOTH}, {@link LensUse#BLOCKS} or {@link LensUse#ENTITIES}. */
        public int targets() {
            return LensUse.targets(interact);
        }

        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed) {
            this(action, modifiers, payers, amps, filter, speed, false, 0);
        }

        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter, int speed, boolean hits) {
            this(action, modifiers, payers, amps, filter, speed, hits, 0);
        }

        public Stream(Lens action, int modifiers, Set<BlockPos> payers) {
            this(action, modifiers, payers, 0, null, 0);
        }

        public Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter) {
            this(action, modifiers, payers, amps, filter, 0);
        }

        /** How many times faster it works: 1, 2, 4 or 8. */
        public int pace() {
            return 1 << speed;
        }

        public boolean has(Lens modifier) {
            return (modifiers & 1 << modifier.ordinal()) != 0;
        }

        Stream paidBy(Set<BlockPos> payers) {
            return new Stream(action, modifiers, payers, amps, filter, speed, interact, fortune, steps, inWorld, voids, signal, channel);
        }

        Stream with(int modifiers, int amps, @Nullable LensFilter filter) {
            return new Stream(action, modifiers, payers, amps, filter, speed, interact, fortune, steps, inWorld, voids, signal, channel);
        }

        Stream faster() {
            return new Stream(action, modifiers | 1 << Lens.SPEED.ordinal(), payers, amps, filter, Math.min(LaserTuning.SPEED_LENSES, speed + 1), interact, fortune,
                    steps, inWorld, voids, signal, channel);
        }

        Stream luckier() {
            return new Stream(action, modifiers | 1 << Lens.HARMONIC.ordinal(), payers, amps, filter, speed, interact,
                    Math.min(LaserTuning.HARMONIC_LENSES, fortune + 1), steps, inWorld, voids, signal, channel);
        }

        /** Through an Isolator Lens: without the modifiers it stops. */
        Stream keeping(java.util.function.Predicate<Lens> passes) {
            int kept = 0;
            for (Lens lens : Lens.values()) {
                if (has(lens) && passes.test(lens)) kept |= 1 << lens.ordinal();
            }
            List<Lens> keptSteps = steps.stream().filter(passes).toList();
            return new Stream(action, kept, payers,
                    passes.test(Lens.AMPLIFY) ? amps : 0, passes.test(Lens.FILTER) ? filter : null, passes.test(Lens.SPEED) ? speed : 0, interact,
                    passes.test(Lens.HARMONIC) ? fortune : 0, keptSteps, inWorld, passes.test(Lens.VOID) ? voids : null, signal, channel);
        }

        /** Through a Smelting or Crushing lens: one more step, after those it had, up to {@link LaserTuning#PROCESS_STEPS}. */
        Stream processedBy(Lens lens) {
            List<Lens> more = new java.util.ArrayList<>(steps);
            if (more.size() < LaserTuning.PROCESS_STEPS) more.add(lens);
            return new Stream(action, modifiers | 1 << lens.ordinal(), payers, amps, filter, speed, interact, fortune, more, inWorld, voids, signal, channel);
        }
    }

    /** A Void lens's list, if it's been given one with anything on it: else it voids everything. */
    @Nullable
    private static LensFilter voidList(@Nullable LensFilter filter) {
        return filter == null || filter.entries(LensFilter.ITEMS).stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty) ? null : filter;
    }

    /**
     * Steps from two sides as one. Where one side's are the last of the other's (a stream added later in a
     * mount saw only the lenses after it), the longer. Otherwise nothing says which came first (a crushing
     * beam and a smelting beam meeting at a mirror): as many of each as either side had, crushing first.
     */
    static List<Lens> joinedSteps(List<Lens> a, List<Lens> b) {
        if (endsWith(a, b)) return a;
        if (endsWith(b, a)) return b;
        List<Lens> joined = new ArrayList<>();
        for (Lens lens : List.of(Lens.CRUSH, Lens.SMELT)) {
            long times = Math.max(a.stream().filter(lens::equals).count(), b.stream().filter(lens::equals).count());
            for (int i = 0; i < times && joined.size() < LaserTuning.PROCESS_STEPS; i++) joined.add(lens);
        }
        return joined;
    }

    private static boolean endsWith(List<Lens> whole, List<Lens> end) {
        return end.size() <= whole.size() && whole.subList(whole.size() - end.size(), whole.size()).equals(end);
    }

    /** Plain light from a source. */
    public static Light from(BlockPos source, int range) {
        return new Light(range, 0, List.of(), Set.of(source), -1, 0);
    }

    /** Where a stream of {@code action} on {@code channel} is kept: by action first, so streams keep the order lenses have. */
    static int key(Lens action, int channel) {
        return action.ordinal() * Channels.COUNT + channel;
    }

    /** How far a run of this light reaches. */
    public int reach() {
        if (left >= 0) return left;
        int base = streams.isEmpty() ? range : Math.min(range, LaserTuning.ACTION_REACH);
        return Math.min(com.kadikular.quantimium.Config.maxLaserRange(), base << boosts);
    }

    /** The light refocused, by a mirror: a fresh run at its own reach. */
    public Light refocused() {
        return left < 0 ? this : new Light(range, boosts, streams, feeders, -1, marks, logistics, voiding, speeds);
    }

    /** This light gone {@code length} blocks into a run and on through something that doesn't refocus it. */
    public Light passedOn(int length) {
        return new Light(range, boosts, streams, feeders, Math.max(0, reach() - length - 1), marks, logistics, voiding, speeds);
    }

    /** Light from two sides, joined: the longer reach, every stream, the same action on the same channel paid for by both. */
    public static Light join(@Nullable Light a, Light b) {
        if (a == null) return b;
        Map<Integer, Stream> streams = new java.util.TreeMap<>();
        for (Light light : List.of(a, b)) {
            for (Stream stream : light.streams) {
                Stream had = streams.get(stream.key());
                if (had == null) {
                    streams.put(stream.key(), stream);
                } else {
                    Set<BlockPos> payers = new HashSet<>(had.payers());
                    payers.addAll(stream.payers());
                    // Joined, the same action does the most either side did; a filter from one side is kept.
                    streams.put(stream.key(), new Stream(stream.action(), had.modifiers() | stream.modifiers(), Set.copyOf(payers),
                            Math.max(had.amps(), stream.amps()), had.filter() != null ? had.filter() : stream.filter(),
                            Math.max(had.speed(), stream.speed()), had.interact() | stream.interact(), Math.max(had.fortune(), stream.fortune()),
                            joinedSteps(had.steps(), stream.steps()), had.inWorld() || stream.inWorld(), had.voids() != null ? had.voids() : stream.voids(),
                            had.signal() != null ? had.signal() : stream.signal(), stream.channel()));
                }
            }
        }
        Set<BlockPos> feeders = new HashSet<>(a.feeders);
        feeders.addAll(b.feeders);
        Map<BlockPos, Integer> logistics = new java.util.HashMap<>(a.logistics);
        logistics.putAll(b.logistics);
        Map<Integer, LensUse> voiding = new java.util.HashMap<>(b.voiding);
        voiding.putAll(a.voiding);
        // Through a photodetector a side has only so far left: the joined light reaches as far as the farther side.
        int left = a.left < 0 && b.left < 0 ? -1 : Math.max(a.reach(), b.reach());
        return new Light(Math.max(a.range, b.range), Math.max(a.boosts, b.boosts),
                List.copyOf(streams.values()), Set.copyOf(feeders), left,
                // Unpowered only if both sides are: power from either side reaches what it ends on.
                (a.marks | b.marks) & ~UNPOWERED | a.marks & b.marks & UNPOWERED, logistics, voiding, mostSpeeds(a.speeds, b.speeds));
    }

    /**
     * The light after a mount's lenses. An action lens gives the light that action on its slot's channel, paid for by
     * every source feeding it here (a stream it had for that action on that channel is replaced, keeping its
     * modifiers). A modifier changes every stream on its channel there (every stream, on All), the mount's own actions
     * included: Filter sets what they act on, Amplify strengthens them. Pickup after an action picks up what it frees;
     * before any, it gathers what lies in the beam. Range, UV and Phantom change the light itself, whatever their
     * channel: Range doubles the reach, UV hides the light from here on.
     */
    public Light through(List<LensUse> lenses) {
        return through(lenses, null);
    }

    /** As {@link #through(List)}, at {@code at}: where a Redstone Lens set to send on its block's signal reads it. */
    public Light through(List<LensUse> lenses, @Nullable BlockPos at) {
        if (lenses.isEmpty()) return this;
        Map<Integer, Stream> streams = new java.util.TreeMap<>();
        for (Stream stream : this.streams) streams.put(stream.key(), stream);
        int marks = this.marks;
        int boosts = this.boosts;
        long speeds = this.speeds;
        Map<BlockPos, Integer> logistics = new java.util.HashMap<>(this.logistics);
        Map<Integer, LensUse> voiding = new java.util.HashMap<>(this.voiding);
        for (LensUse use : lenses) {
            Lens lens = use.lens();
            int channel = Channels.of(use.channel());
            // What a modifier here changes: the streams on its channel.
            java.util.function.Predicate<Stream> mine = stream -> Channels.changes(channel, stream.channel());
            if (lens.action) {
                Stream had = streams.get(key(lens, channel));
                Stream made = new Stream(lens, had == null ? 0 : had.modifiers(), feeders, had == null ? 0 : had.amps(), had == null ? null : had.filter(),
                        had == null ? 0 : had.speed(), use.interact(), had == null ? 0 : had.fortune(), had == null ? List.of() : had.steps(), false,
                        had == null ? null : had.voids(), null, channel);
                if (lens == Lens.REDSTONE) {
                    made = new Stream(made.action(), made.modifiers(), made.payers(), made.amps(), made.filter(), made.speed(), made.interact(),
                            made.fortune(), made.steps(), made.inWorld(), made.voids(),
                            new Signal(use.redstone() == null ? RedstoneSettings.DEFAULT : use.redstone(), at), channel);
                }
                // A Void lens met before on this channel (or on All) stays on the light: this action gets it too.
                LensUse voids = voidingFor(voiding, channel);
                streams.put(key(lens, channel), voids == null || made.has(Lens.VOID) ? made : voided(made, voids));
            } else switch (lens) {
                case PICKUP -> {
                    // After an action on its channel (Harvest, Damage...): it picks up what that action frees, and the
                    // action waits while there's nowhere to put it. Otherwise it's an action of its own: gathering whatever
                    // lies in the beam, beside any action that comes after (which doesn't get it).
                    if (streams.values().stream().anyMatch(stream -> stream.action() != Lens.PICKUP && mine.test(stream))) {
                        streams.replaceAll((key, stream) -> stream.action() == Lens.PICKUP || !mine.test(stream) ? stream
                                : stream.with(stream.modifiers() | 1 << Lens.PICKUP.ordinal(), stream.amps(), stream.filter()));
                    } else {
                        Stream had = streams.get(key(Lens.PICKUP, channel));
                        Stream gathering = new Stream(Lens.PICKUP, 1 << Lens.PICKUP.ordinal(), feeders, 0, had == null ? null : had.filter()).onChannel(channel);
                        LensUse voids = voidingFor(voiding, channel);
                        streams.put(key(Lens.PICKUP, channel), voids == null ? gathering : voided(gathering, voids));
                    }
                }
                case RANGE -> boosts = Math.min(LaserTuning.RANGE_LENSES, boosts + 1);
                case UV -> marks |= UV;
                case PHANTOM -> marks |= PHANTOM;
                // Each filter lens sets its own part (items, blocks, entities) over what an earlier one set.
                case FILTER -> streams.replaceAll((key, stream) -> !mine.test(stream) ? stream
                        : stream.with(stream.modifiers() | 1 << Lens.FILTER.ordinal(), stream.amps(),
                                (use.filter() == null ? LensFilter.EMPTY : use.filter()).over(stream.filter() == null ? LensFilter.EMPTY : stream.filter())));
                case ISOLATE -> {
                    // What came before it on its channel, but for what it passes; power and reach always do. The light
                    // itself (power, UV, Phantom) is stopped whatever its channel.
                    IsolatorSettings passing = use.isolator() == null ? IsolatorSettings.POWER_ONLY : use.isolator();
                    streams.values().removeIf(stream -> mine.test(stream) && !passing.passes(stream.action()));
                    streams.replaceAll((key, stream) -> mine.test(stream) ? stream.keeping(passing::passes) : stream);
                    if (!passing.passes(Lens.UV)) marks &= ~UV;
                    if (!passing.passes(Lens.PHANTOM)) marks &= ~PHANTOM;
                    if (!passing.passes(Lens.SPEED)) speeds = withSpeed(speeds, channel, 0);
                    if (!passing.logistics()) logistics.values().removeIf(controller -> Channels.changes(channel, controller));
                    if (!passing.power()) marks |= UNPOWERED;
                    if (!passing.passes(Lens.VOID)) voiding.keySet().removeIf(on -> Channels.changes(channel, on));
                }
                case SPEED -> {
                    streams.replaceAll((key, stream) -> mine.test(stream) ? stream.faster() : stream);
                    speeds = faster(speeds, channel);
                }
                case HARMONIC -> streams.replaceAll((key, stream) -> mine.test(stream) ? stream.luckier() : stream);
                // A Void lens set to void all marks the streams it changes so.
                case VOID -> {
                    streams.replaceAll((key, stream) -> mine.test(stream) ? voided(stream, use) : stream);
                    voiding.put(channel, use);
                }
                case PRECISION, UNREALISE -> streams.replaceAll((key, stream) -> !mine.test(stream) ? stream
                        : stream.with(stream.modifiers() | 1 << lens.ordinal(), stream.amps(), stream.filter()));
                case SMELT, CRUSH -> {
                    // Smelting and Crushing are actions of their own too: where they change no action before them, they
                    // work on the world, on what the beam ends on (Crushing, Smelting, Harvest: the sandstone becomes glass,
                    // then it's cut). One there already took this lens as a step too.
                    boolean changes = streams.values().stream().anyMatch(stream -> !processes(stream.action()) && mine.test(stream));
                    streams.replaceAll((key, stream) -> mine.test(stream) ? stream.processedBy(lens) : stream);
                    streams.putIfAbsent(key(lens, channel), new Stream(lens, 1 << lens.ordinal(), feeders, 0, null, 0, 0, 0, List.of(lens), !changes,
                            null, null, channel));
                }
                case AMPLIFY -> streams.replaceAll((key, stream) -> !mine.test(stream) ? stream
                        : stream.with(stream.modifiers() | 1 << Lens.AMPLIFY.ordinal(), Math.min(LaserTuning.AMPLIFY_LENSES, stream.amps() + 1), stream.filter()));
                default -> {}
            }
        }
        return new Light(range, boosts, List.copyOf(streams.values()), feeders, left, marks, logistics, voiding, speeds);
    }

    /** The Void lens an action added on {@code channel} gets: one met on its channel, else one on All. */
    @Nullable
    private static LensUse voidingFor(Map<Integer, LensUse> voiding, int channel) {
        LensUse own = voiding.get(channel);
        return own != null ? own : voiding.get(Channels.ALL);
    }

    /** {@code stream} changed by the Void lens {@code use}: its mode, and its list. */
    private static Stream voided(Stream stream, LensUse use) {
        return new Stream(stream.action(), stream.modifiers() | 1 << Lens.VOID.ordinal(), stream.payers(), stream.amps(), stream.filter(),
                stream.speed(), stream.interact() | use.interact() & LensUse.VOID_ALL, stream.fortune(), stream.steps(), stream.inWorld(),
                voidList(use.filter()), stream.signal(), stream.channel());
    }

    /**
     * The streams that act: all of them, but Smelting's and Crushing's own only where they work on the
     * world; and those as one per channel, with all their steps (see {@link #joinedSteps}): the one that came first took
     * the others' steps after its own, so it does them all, in order.
     */
    public List<Stream> acting() {
        if (streams.stream().noneMatch(stream -> processes(stream.action()))) return streams;
        List<Stream> acting = new ArrayList<>();
        Map<Integer, Stream> merged = new java.util.TreeMap<>();
        for (Stream stream : streams) {
            if (!processes(stream.action())) {
                acting.add(stream);
            } else if (stream.inWorld()) {
                merged.merge(stream.channel(), stream, (had, more) -> new Stream(had.action(), had.modifiers() | more.modifiers(), had.payers(), had.amps(),
                        had.filter(), had.speed(), had.interact(), had.fortune(), joinedSteps(had.steps(), more.steps()), true, had.voids(),
                        had.signal(), had.channel()));
            }
        }
        acting.addAll(merged.values());
        return acting;
    }

    private static boolean processes(Lens action) {
        return action == Lens.SMELT || action == Lens.CRUSH;
    }

    /** What an Isolator lets through: power, at the light's reach, and nothing it did; still in the mirror, if it was. */
    public Light isolated() {
        return new Light(range, boosts, List.of(), feeders, left, marks & (MIRRORED | UNPOWERED));
    }

    /** For the beam's look: a bit for each action it carries, and for each modifier any stream has. */
    public int packed() {
        int packed = 0;
        for (Stream stream : streams) packed |= 1 << stream.action().ordinal() | stream.modifiers();
        if (boosts > 0) packed |= 1 << Lens.RANGE.ordinal();
        if (uv()) packed |= 1 << Lens.UV.ordinal();
        if (phantom()) packed |= 1 << Lens.PHANTOM.ordinal();
        if (mirrored()) packed |= 1 << Lens.MIRRORED_MARK;
        if (!logistics.isEmpty()) packed |= 1 << Lens.LOGISTICS_MARK;
        return packed;
    }

    // ---- kept in a cavity ----

    /** What a cavity remembers of light it was filled with: everything but who paid, since it pays now. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Range", range);
        tag.putInt("Boosts", boosts);
        ListTag list = new ListTag();
        for (Stream stream : streams) {
            // A Redstone Lens's signal isn't kept: its settings are the lens's, not the light's.
            if (stream.signal() != null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Action", stream.action().ordinal());
            entry.putInt("Modifiers", stream.modifiers());
            entry.putInt("Amps", stream.amps());
            entry.putInt("Speed", stream.speed());
            entry.putInt("Interact", stream.interact());
            entry.putInt("Fortune", stream.fortune());
            entry.putIntArray("Steps", stream.steps().stream().mapToInt(Lens::ordinal).toArray());
            entry.putBoolean("InWorld", stream.inWorld());
            if (stream.channel() != 0) entry.putInt("Channel", stream.channel());
            if (stream.filter() != null) {
                LensFilter.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stream.filter()).result().ifPresent(filter -> entry.put("Filter", filter));
            }
            if (stream.voids() != null) {
                LensFilter.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stream.voids()).result().ifPresent(voids -> entry.put("Voids", voids));
            }
            list.add(entry);
        }
        tag.put("Streams", list);
        tag.putInt("Marks", marks);
        tag.putLong("Speeds", speeds);
        return tag;
    }

    /** The light a cavity at {@code source} gives out, from what it remembers. */
    public static Light load(CompoundTag tag, BlockPos source) {
        List<Stream> streams = new ArrayList<>();
        for (Tag entry : tag.getListOrEmpty("Streams")) {
            if (!(entry instanceof CompoundTag stream)) continue;
            int action = stream.getIntOr("Action", -1);
            if (action < 0 || action >= Lens.values().length) continue;
            LensFilter filter = stream.get("Filter") == null ? null
                    : LensFilter.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, stream.get("Filter")).result().orElse(null);
            streams.add(new Stream(Lens.values()[action], stream.getIntOr("Modifiers", 0), Set.of(source), stream.getIntOr("Amps", 0), filter,
                    stream.getIntOr("Speed", 0), stream.getIntOr("Interact", 0), stream.getIntOr("Fortune", 0),
                    java.util.Arrays.stream(stream.getIntArray("Steps").orElse(new int[0])).filter(i -> i >= 0 && i < Lens.values().length)
                            .mapToObj(i -> Lens.values()[i]).toList(), stream.getBooleanOr("InWorld", false),
                    stream.get("Voids") == null ? null : LensFilter.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, stream.get("Voids")).result().orElse(null),
                    null, Channels.of(stream.getIntOr("Channel", 0))));
        }
        int marks = tag.getIntOr("Marks", 0);
        // Kept before channels: the Speed lenses met were in the marks, and were channel 0's.
        long speeds = tag.contains("Speeds") ? tag.getLongOr("Speeds", 0L) : (marks & OLD_SPEEDS) >> OLD_SPEEDS_SHIFT;
        return new Light(tag.getIntOr("Range", LaserTuning.RELAY_RANGE), tag.getIntOr("Boosts", 0),
                List.copyOf(streams), Set.of(source), -1, marks & ~OLD_SPEEDS, Map.of(), Map.of(), speeds);
    }

    /** The same light, as a cavity at {@code source} gives it out: it pays for every stream now. */
    public Light reEmitted(BlockPos source) {
        List<Stream> mine = new ArrayList<>();
        for (Stream stream : streams) mine.add(stream.paidBy(Set.of(source)));
        return new Light(range, boosts, List.copyOf(mine), Set.of(source), -1, marks, Map.of(), voiding, speeds);
    }

    /** Whether this has everything {@code other} has, payers aside: what a cavity checks before it remembers more. */
    public boolean covers(Light other) {
        if (range < other.range || boosts < other.boosts || (other.marks & ~marks) != 0) return false;
        for (int c = 0; c < Channels.COUNT; c++) if (speeds(c) < other.speeds(c)) return false;
        for (Stream theirs : other.streams) {
            Stream mine = streams.stream().filter(stream -> stream.key() == theirs.key()).findFirst().orElse(null);
            if (mine == null || (mine.modifiers() & theirs.modifiers()) != theirs.modifiers() || mine.amps() < theirs.amps()
                    || mine.speed() < theirs.speed() || !mine.steps().equals(theirs.steps()) || (theirs.inWorld() && !mine.inWorld())) return false;
        }
        return true;
    }
}
