package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
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
 * becomes one, paid by both. A modifier applies to the streams already there, so one before a join stays
 * on its own side, except Pickup, which stays on the light and gives every action added after it Pickup too.
 * Pickup is also a stream of its own, paid for like an action: it gathers items lying in the beam.
 *
 * @param range   the reach of a run of plain light from its source, before any Range lens
 * @param boosts  Range lenses met, up to {@link LaserTuning#RANGE_LENSES}
 * @param pickup  whether actions added from here on get Pickup
 * @param streams what the light does, one stream per action
 * @param feeders the sources whose light this is: emitters, or cavities giving out what they hold
 * @param left    for light through a photodetector, which doesn't refocus: blocks it has left; -1 otherwise
 * @param uv      whether the light can't be seen but with a wrench in hand; it stays on the light
 */
public record Light(int range, int boosts, boolean pickup, List<Stream> streams, Set<BlockPos> feeders, int left, boolean uv) {

    /**
     * One action the light carries, its modifiers (bits of {@link Lens}), and who pays for it; how many
     * Amplify lenses it's met, and what a Filter lens lets it act on (null for everything).
     */
    public record Stream(Lens action, int modifiers, Set<BlockPos> payers, int amps, @Nullable LensFilter filter) {
        public Stream(Lens action, int modifiers, Set<BlockPos> payers) {
            this(action, modifiers, payers, 0, null);
        }

        public boolean has(Lens modifier) {
            return (modifiers & 1 << modifier.ordinal()) != 0;
        }

        Stream paidBy(Set<BlockPos> payers) {
            return new Stream(action, modifiers, payers, amps, filter);
        }

        Stream with(int modifiers, int amps, @Nullable LensFilter filter) {
            return new Stream(action, modifiers, payers, amps, filter);
        }
    }

    /** Plain light from a source. */
    public static Light from(BlockPos source, int range) {
        return new Light(range, 0, false, List.of(), Set.of(source), -1, false);
    }

    /** How far a run of this light reaches. */
    public int reach() {
        if (left >= 0) return left;
        int base = streams.isEmpty() ? range : Math.min(range, LaserTuning.ACTION_REACH);
        return Math.min(LaserTuning.MAX_RANGE, base << boosts);
    }

    /** The light refocused, by a mirror: a fresh run at its own reach. */
    public Light refocused() {
        return left < 0 ? this : new Light(range, boosts, pickup, streams, feeders, -1, uv);
    }

    /** This light gone {@code length} blocks into a run and on through something that doesn't refocus it. */
    public Light passedOn(int length) {
        return new Light(range, boosts, pickup, streams, feeders, Math.max(0, reach() - length - 1), uv);
    }

    /** Light from two sides, joined: the longer reach, every stream, the same action paid for by both. */
    public static Light join(@Nullable Light a, Light b) {
        if (a == null) return b;
        Map<Lens, Stream> streams = new EnumMap<>(Lens.class);
        for (Light light : List.of(a, b)) {
            for (Stream stream : light.streams) {
                Stream had = streams.get(stream.action());
                if (had == null) {
                    streams.put(stream.action(), stream);
                } else {
                    Set<BlockPos> payers = new HashSet<>(had.payers());
                    payers.addAll(stream.payers());
                    // Joined, the same action does the most either side did; a filter from one side is kept.
                    streams.put(stream.action(), new Stream(stream.action(), had.modifiers() | stream.modifiers(), Set.copyOf(payers),
                            Math.max(had.amps(), stream.amps()), had.filter() != null ? had.filter() : stream.filter()));
                }
            }
        }
        Set<BlockPos> feeders = new HashSet<>(a.feeders);
        feeders.addAll(b.feeders);
        // Through a photodetector a side has only so far left: the joined light reaches as far as the farther side.
        int left = a.left < 0 && b.left < 0 ? -1 : Math.max(a.reach(), b.reach());
        return new Light(Math.max(a.range, b.range), Math.max(a.boosts, b.boosts), a.pickup || b.pickup,
                List.copyOf(streams.values()), Set.copyOf(feeders), left, a.uv || b.uv);
    }

    /**
     * The light after a mount's lenses, in order. An action lens gives the light that action, paid for by
     * every source feeding it here (a stream it had for that action is replaced, keeping its modifiers).
     * A modifier changes the streams already there: Filter sets what they act on, Amplify strengthens
     * them. Pickup goes on every stream there and every one added after, and gathers of its own; Range
     * doubles the reach; UV hides the light from here on.
     */
    public Light through(List<LensUse> lenses) {
        if (lenses.isEmpty()) return this;
        Map<Lens, Stream> streams = new EnumMap<>(Lens.class);
        for (Stream stream : this.streams) streams.put(stream.action(), stream);
        boolean pickup = this.pickup;
        boolean uv = this.uv;
        int boosts = this.boosts;
        for (LensUse use : lenses) {
            Lens lens = use.lens();
            if (lens.action) {
                Stream had = streams.get(lens);
                int modifiers = (had == null ? 0 : had.modifiers()) | (pickup ? 1 << Lens.PICKUP.ordinal() : 0);
                streams.put(lens, new Stream(lens, modifiers, feeders, had == null ? 0 : had.amps(), had == null ? null : had.filter()));
            } else switch (lens) {
                case PICKUP -> {
                    pickup = true;
                    streams.replaceAll((action, stream) -> stream.with(stream.modifiers() | 1 << Lens.PICKUP.ordinal(), stream.amps(), stream.filter()));
                    // Pickup is an action of its own too: gathering what lies in the beam.
                    Stream had = streams.get(Lens.PICKUP);
                    streams.put(Lens.PICKUP, new Stream(Lens.PICKUP, 1 << Lens.PICKUP.ordinal(), feeders, 0, had == null ? null : had.filter()));
                }
                case RANGE -> boosts = Math.min(LaserTuning.RANGE_LENSES, boosts + 1);
                case UV -> uv = true;
                case FILTER -> streams.replaceAll((action, stream) ->
                        stream.with(stream.modifiers() | 1 << Lens.FILTER.ordinal(), stream.amps(), use.filter() == null ? LensFilter.EMPTY : use.filter()));
                case AMPLIFY -> streams.replaceAll((action, stream) ->
                        stream.with(stream.modifiers() | 1 << Lens.AMPLIFY.ordinal(), Math.min(LaserTuning.AMPLIFY_LENSES, stream.amps() + 1), stream.filter()));
                default -> {}
            }
        }
        return new Light(range, boosts, pickup, List.copyOf(streams.values()), feeders, left, uv);
    }

    /** What an Isolator lets through: power, at the light's reach, and nothing it did. */
    public Light isolated() {
        return new Light(range, boosts, false, List.of(), feeders, left, false);
    }

    /** For the beam's look: a bit for each action it carries, and for each modifier any stream has. */
    public int packed() {
        int packed = 0;
        for (Stream stream : streams) packed |= 1 << stream.action().ordinal() | stream.modifiers();
        if (pickup) packed |= 1 << Lens.PICKUP.ordinal();
        if (boosts > 0) packed |= 1 << Lens.RANGE.ordinal();
        if (uv) packed |= 1 << Lens.UV.ordinal();
        return packed;
    }

    // ---- kept in a cavity ----

    /** What a cavity remembers of light it was filled with: everything but who paid, since it pays now. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Range", range);
        tag.putInt("Boosts", boosts);
        tag.putBoolean("Pickup", pickup);
        ListTag list = new ListTag();
        for (Stream stream : streams) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Action", stream.action().ordinal());
            entry.putInt("Modifiers", stream.modifiers());
            entry.putInt("Amps", stream.amps());
            if (stream.filter() != null) {
                LensFilter.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stream.filter()).result().ifPresent(filter -> entry.put("Filter", filter));
            }
            list.add(entry);
        }
        tag.put("Streams", list);
        tag.putBoolean("Uv", uv);
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
            streams.add(new Stream(Lens.values()[action], stream.getIntOr("Modifiers", 0), Set.of(source), stream.getIntOr("Amps", 0), filter));
        }
        return new Light(tag.getIntOr("Range", LaserTuning.RELAY_RANGE), tag.getIntOr("Boosts", 0), tag.getBooleanOr("Pickup", false),
                List.copyOf(streams), Set.of(source), -1, tag.getBooleanOr("Uv", false));
    }

    /** The same light, as a cavity at {@code source} gives it out: it pays for every stream now. */
    public Light reEmitted(BlockPos source) {
        List<Stream> mine = new ArrayList<>();
        for (Stream stream : streams) mine.add(stream.paidBy(Set.of(source)));
        return new Light(range, boosts, pickup, List.copyOf(mine), Set.of(source), -1, uv);
    }

    /** Whether this has everything {@code other} has, payers aside: what a cavity checks before it remembers more. */
    public boolean covers(Light other) {
        if (range < other.range || boosts < other.boosts || (other.pickup && !pickup) || (other.uv && !uv)) return false;
        for (Stream theirs : other.streams) {
            Stream mine = streams.stream().filter(stream -> stream.action() == theirs.action()).findFirst().orElse(null);
            if (mine == null || (mine.modifiers() & theirs.modifiers()) != theirs.modifiers() || mine.amps() < theirs.amps()) return false;
        }
        return true;
    }
}
