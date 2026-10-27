package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.Light;
import com.kadikular.quantimium.laser.Ray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Cavity Mirror plate on a face of a Laser Focus block: a flat half-silvered mirror facing out. Light out of it that
 * comes back to it is held there, as in a real laser's cavity: off a flat Laser Mirror straight back, off a Laser Focus's
 * Emitter giving light back, into another Cavity Mirror plate facing back, or round any number of plain Laser Mirrors on
 * the way. Each plate on a focus finds its own cavity and holds its own light. What they hold leaves by the focus's
 * Emitters; light reaching the focus from outside charges them.
 */
public final class CavityPlate {

    @Nullable
    private final LaserFocusBlockEntity host;
    private final Direction face;

    private long stored;
    /** Blocks of light held one way round, mirrors on the way included; 0 for no cavity. */
    private int gap;
    /** The way light runs round the cavity, from the plate to where it turns back or its partner; null for none. */
    @Nullable
    private LaserBeam loop;
    /** Where the light it holds turns back, or its partner: the cavity's far end. Null for none. */
    @Nullable
    private BlockPos farEnd;
    /** Whether the partner is a Cavity Mirror plate too, so both hold the light; a Laser Mirror holds none. */
    private boolean partnerHolds;
    private double leaked;
    /** What the light it holds does, besides carry power: whatever filled it since it was last empty. Null for plain light. */
    @Nullable
    private Light held;
    /** On the client: how bright the light held looks. */
    private int shownCharge;
    /** On the client: the look of the light held, for the glow's tint. */
    private int shownLenses;

    CavityPlate(@Nullable LaserFocusBlockEntity host, Direction face) {
        this.host = host;
        this.face = face;
    }

    public Direction facing() {
        return face;
    }

    /** The Laser Focus block it's on. */
    @Nullable
    public LaserFocusBlockEntity host() {
        return host;
    }

    public boolean paired() {
        return gap > 0;
    }

    public long stored() {
        return stored;
    }

    public int gap() {
        return gap;
    }

    public long capacity() {
        return gap * LaserTuning.CAVITY_FE_PER_BLOCK;
    }

    @Nullable
    public Light held() {
        return held;
    }

    @Nullable
    private Level level() {
        return host == null ? null : host.getLevel();
    }

    private BlockPos pos() {
        return host == null ? BlockPos.ZERO : host.getBlockPos();
    }

    private void changed() {
        if (host != null) host.setChanged();
    }

    /** Takes on what {@code other} held: a Cavity Mirror block's, as it becomes one of these. */
    void copyFrom(CavityPlate other) {
        stored = other.stored;
        held = other.held == null ? null : other.held.reEmitted(pos());
        changed();
    }

    // ---- the cavity ----

    /**
     * Follows light out of the plate, round plain Laser Mirrors, until it comes back: off a mirror that sends it back the
     * way it came (from there it retraces its way here), into a Cavity Mirror plate facing back, or a Laser Focus's
     * Emitter giving light back along it. How many blocks of light that holds one way, mirrors on the way included, or
     * 0 for no cavity; at most {@link LaserTuning#CAVITY_MAX_GAP}, a loop of twice that.
     */
    private int findGap() {
        partnerHolds = false;
        loop = null;
        farEnd = null;
        Level level = level();
        if (level == null) return 0;
        Ray way = Ray.of(face);
        BlockPos from = pos();
        int firstLength = -1;
        List<LaserBeam.Leg> legs = new ArrayList<>();
        double length = 0;
        while (legs.size() <= LaserBeam.MAX_BOUNCES) {
            // One block further than it may hold, for what closes it.
            int reach = (int) Math.floor(LaserTuning.CAVITY_MAX_GAP - length + way.length);
            if (reach < 1) return 0;
            LaserBeam run = LaserBeam.trace(level, from, way, reach, 0, null);
            BlockPos hit = run.hit();
            if (hit == null || !level.isLoaded(hit)) return 0;
            length += run.length() * way.length;
            if (length > LaserTuning.CAVITY_MAX_GAP + 1.0e-6) return 0;
            if (firstLength < 0) firstLength = run.length();
            else legs.set(legs.size() - 1, new LaserBeam.Leg(from, way, run.length()));
            BlockState there = level.getBlockState(hit);
            boolean straightIn = way.straight();
            if (level.getBlockEntity(hit) instanceof LaserFocusBlockEntity other) {
                if (!straightIn || length < 1) return 0;
                Direction back = way.face().getOpposite();
                // Another cavity plate facing back: both hold the light.
                if (other.cavity(back) != null) {
                    partnerHolds = other != host;
                    return closed(hit, firstLength, legs, length, true);
                }
                // An Emitter of a focus giving light back along it.
                if (other.core() != LaserFocusBlockEntity.Core.NONE && other.hasEmitter(back)) return closed(hit, firstLength, legs, length, false);
                return 0;
            }
            if (there.getBlock() instanceof com.kadikular.quantimium.block.LaserRelayBlock) {
                if (!straightIn || !there.getValue(com.kadikular.quantimium.block.LaserRelayBlock.OUTPUTS.get(way.face().getOpposite())) || length < 1) return 0;
                return closed(hit, firstLength, legs, length, false);
            }
            // Only plain Laser Mirrors, switched on: a splitter would let the light out.
            if (!(there.getBlock() instanceof com.kadikular.quantimium.block.LaserMirrorBlock mirror) || mirror.splits()
                    || there.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.POWERED)) return 0;
            Ray turned = com.kadikular.quantimium.block.LaserMirrorBlock.plate(there).reflect(way.unit(), false);
            if (turned == null || turned == Ray.of(there.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT))) return 0;
            // Straight back the way it came: it retraces its way here, and the cavity's closed.
            if (turned == way.opposite()) return length < 1 ? 0 : closed(hit, firstLength, legs, length, false);
            // The mirror's own block holds light too.
            length += 1;
            legs.add(new LaserBeam.Leg(hit, turned, 0));
            from = hit;
            way = turned;
        }
        return 0;
    }

    /** A cavity closed at {@code end}: remembers its way round, for the glow of the light it holds. */
    private int closed(BlockPos end, int firstLength, List<LaserBeam.Leg> legs, double length, boolean onPlate) {
        farEnd = end;
        // Into a cavity plate, the glow ends on its face; into a mirror or emitter, on to its middle.
        loop = new LaserBeam(pos(), face, null, firstLength, onPlate ? null : end, onPlate ? LaserBeam.End.OPEN : LaserBeam.End.BLOCKED, 0, true,
                com.kadikular.quantimium.laser.Lens.NONE, Ray.of(face), false, List.copyOf(legs), 0);
        return Math.max(1, (int) Math.floor(length + 1.0e-6));
    }

    /** Looks for its cavity again: whether anything changed. */
    boolean checkPartner(ServerLevel server) {
        boolean held = partnerHolds;
        LaserBeam was = loop;
        BlockPos wasEnd = farEnd;
        int now = findGap();
        if (now == gap && held == partnerHolds && java.util.Objects.equals(was, loop) && java.util.Objects.equals(wasEnd, farEnd)) return false;
        gap = now;
        if (stored > capacity()) release(server, stored - capacity());
        changed();
        return true;
    }

    /** Each tick: shares with its partner, leaks, forgets what its light did once empty. Whether its look changed. */
    boolean tick(ServerLevel server) {
        boolean changed = false;
        if (server.getGameTime() % 20 == Math.floorMod(pos().hashCode() + face.ordinal(), 20)) changed |= checkPartner(server);
        share(server);
        if (stored > 0) {
            double leak = com.kadikular.quantimium.Config.cavityLeak(stored);
            leaked += leak;
            stored -= (long) Math.ceil(leak);
            if (stored < 0) stored = 0;
            changed();
        }
        if (stored <= 0 && held != null) {
            held = null;
            changed();
            LaserNetworks.changed(server);
        }
        if (server.getGameTime() % 20 == 0) {
            if (leaked >= 1) QuantumFlux.emitFromEnergy(server, pos(), (long) leaked);
            leaked = 0;
            changed |= showCharge();
        }
        return changed;
    }

    /**
     * Light held in a cavity bounces between both plates: what either holds evens out between them, so both glow and
     * both can give it out. The lower of the two does it, once a tick.
     */
    private void share(ServerLevel server) {
        if (!paired() || !partnerHolds || farEnd == null || farEnd.compareTo(pos()) < 0) return;
        if (!(server.getBlockEntity(farEnd) instanceof LaserFocusBlockEntity other)) return;
        CavityPlate partner = other.cavity(loop != null && loop.lastRay() != null ? loop.lastRay().face().getOpposite() : face.getOpposite());
        if (partner == null || partner == this) return;
        long total = stored + partner.stored;
        if (Math.abs(stored - partner.stored) <= 1) return;
        stored = Math.min(capacity(), total / 2);
        partner.stored = Math.min(partner.capacity(), total - stored);
        // The light mixes as it bounces: both hold what either did.
        if (!sameLight(held, partner.held)) {
            Light mixed = held == null ? partner.held : partner.held == null ? held : Light.join(held, partner.held);
            if (mixed != null) {
                held = mixed.reEmitted(pos());
                partner.held = mixed.reEmitted(partner.pos());
                LaserNetworks.changed(server);
            }
        }
        changed();
        partner.changed();
    }

    private static boolean sameLight(@Nullable Light a, @Nullable Light b) {
        if (a == null || b == null) return a == b;
        return a.covers(b) && b.covers(a);
    }

    /** Lets {@code fe} of held light go, as flux where it stands. */
    void release(ServerLevel server, long fe) {
        if (fe <= 0) return;
        stored -= fe;
        QuantumFlux.emitFromEnergy(server, pos(), fe);
    }

    /** All it holds goes, as flux: taken off, or its block broken. */
    void releaseAll(ServerLevel server) {
        release(server, stored);
    }

    /** Whether how bright it looks changed enough to show. */
    private boolean showCharge() {
        // By how full it is: from a glimmer to full brightness at 80% full.
        int charge = stored <= 0 || capacity() <= 0 ? 0 : 1 + (int) Math.min(6, Math.floor(stored / (capacity() * 0.8) * 6));
        int lenses = held == null ? com.kadikular.quantimium.laser.Lens.NONE : held.packed();
        if (charge == shownCharge && lenses == shownLenses) return false;
        shownCharge = charge;
        shownLenses = lenses;
        return true;
    }

    // ---- storing ----

    /** Takes in what light it can of {@code light}'s doing: whether it now remembers more. */
    boolean absorb(Light light) {
        if (!paired()) return false;
        if (held != null && held.covers(light)) return false;
        if (held == null && light.streams().isEmpty() && light.boosts() == 0) return false;
        held = (held == null ? light : Light.join(held, light)).reEmitted(pos());
        changed();
        return true;
    }

    long available() {
        return paired() ? Math.min(stored, LaserTuning.CAVITY_RATE) : 0;
    }

    void draw(long fe) {
        stored = Math.max(0, stored - fe);
        changed();
    }

    long room() {
        return paired() ? Math.max(0, Math.min(capacity() - stored, LaserTuning.CAVITY_RATE)) : 0;
    }

    public void fill(long fe) {
        stored = Math.min(capacity(), stored + fe);
        changed();
    }

    // ---- showing ----

    /** The light held, drawn all the way round: by this plate, or the lower of two. Null when there's none to show. */
    @Nullable
    LaserBeam glow() {
        if (gap <= 0 || loop == null || shownCharge <= 0) return null;
        if (partnerHolds && farEnd != null && farEnd.compareTo(pos()) < 0) return null;
        long flow = (long) Math.pow(10, shownCharge - 1);
        return loop.withFlow(flow).withLenses(shownLenses);
    }

    // ---- saving ----

    void save(ValueOutput out) {
        out.putLong("Stored", stored);
        out.putInt("Gap", gap);
        out.putBoolean("PartnerHolds", partnerHolds);
        if (held != null) out.store("Light", CompoundTag.CODEC, held.save());
    }

    /** For clients: how it shows, besides what's saved. */
    void saveLook(ValueOutput out) {
        out.putInt("Charge", shownCharge);
        out.putInt("Lenses", shownLenses);
        if (loop != null) out.store("Loop", CompoundTag.CODEC, LaserBeamView.save(List.of(loop)));
        if (farEnd != null) out.putLong("FarEnd", farEnd.asLong());
    }

    /** Read as saved here, or as a Cavity Mirror block saved it before it became one of these. */
    void load(ValueInput in) {
        stored = in.getLongOr("Stored", 0);
        gap = in.getIntOr("Gap", 0);
        partnerHolds = in.getBooleanOr("PartnerHolds", true);
        held = in.read("Light", CompoundTag.CODEC).map(tag -> Light.load(tag, pos())).orElse(null);
        shownCharge = in.getIntOr("Charge", 0);
        shownLenses = in.getIntOr("Lenses", 0);
        loop = in.read("Loop", CompoundTag.CODEC).map(tag -> LaserBeamView.load(pos(), tag)).filter(beams -> !beams.isEmpty())
                .map(List::getFirst).orElse(null);
        farEnd = in.getLong("FarEnd").map(BlockPos::of).orElse(null);
    }
}
