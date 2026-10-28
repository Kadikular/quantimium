package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One straight run of light: out of a node's face, or from a prism at a block it's linked to, along
 * any line. How far it got and what stopped it.
 *
 * @param face   the way it travels, to the nearest of the six: which face of what it ends on it reaches
 * @param aim    the block a prism aimed it at, or null for light leaving a face
 * @param length blocks crossed before what it ends on
 * @param hit    the block it ends on, or null if it faded in open air at its range
 * @param end    what that block is to the network
 * @param flow   FE a tick it carried when last worked out, for its brightness
 * @param live   whether light runs along it at all: a powered, switched-on source reaches it, even when
 *               nothing at its end needs power, so it glows dimly as it checks
 * @param lenses the lenses it's met, packed as {@link Lens} says: what it does besides carry power
 * @param ray    which of the 26 ways it travels, block centre to block centre ({@code length} counts its steps); null for
 *               a free line to {@code aim}, a prism's
 * @param toFace for a free line: it runs to the middle of {@code aim}'s face it goes in by ({@code face}'s opposite), a
 *               prism's link; else to {@code aim}'s middle (an Area Prism's sight lines)
 * @param legs   the runs after each Laser Mirror it bounces off, in order: the fields above are its first run, and
 *               {@code hit} and {@code end} what its last ends on
 * @param fork   which of the beams a Laser Splitter forks one into this is (0 for the first)
 */
public record LaserBeam(BlockPos from, Direction face, @Nullable BlockPos aim, int length, @Nullable BlockPos hit, End end,
                        long flow, boolean live, int lenses, @Nullable Ray ray, boolean toFace, List<Leg> legs, int fork) {

    /** One run after a bounce: from the mirror at {@code at}, {@code length} steps along {@code ray} before what it ends on. */
    public record Leg(BlockPos at, Ray ray, int length) {}

    /** The most mirrors one beam bounces off: light caught between mirrors fades after so many. */
    public static final int MAX_BOUNCES = 32;
    /** The most beams Laser Splitters fork one beam into: past that, light forked further fades. */
    public static final int MAX_FORKS = 64;

    public LaserBeam {
        legs = List.copyOf(legs);
    }

    public LaserBeam(BlockPos from, Direction face, @Nullable BlockPos aim, int length, @Nullable BlockPos hit, End end,
                     long flow, boolean live, int lenses, @Nullable Ray ray, boolean toFace) {
        this(from, face, aim, length, hit, end, flow, live, lenses, ray, toFace, List.of(), 0);
    }

    /** A beam out of a face (its straight way), or a free line to {@code aim}'s middle. */
    public LaserBeam(BlockPos from, Direction face, @Nullable BlockPos aim, int length, @Nullable BlockPos hit, End end,
                     long flow, boolean live, int lenses) {
        this(from, face, aim, length, hit, end, flow, live, lenses, aim == null ? Ray.of(face) : null, false);
    }

    public LaserBeam(BlockPos from, Direction face, @Nullable BlockPos aim, int length, @Nullable BlockPos hit, End end,
                     long flow, boolean live, int lenses, @Nullable Ray ray) {
        this(from, face, aim, length, hit, end, flow, live, lenses, ray, false);
    }

    /** Where a free line runs to: the middle of the face it goes in by, or of the block. */
    private static Vec3 point(BlockPos aim, @Nullable Direction inBy) {
        return inBy == null ? aim.getCenter() : aim.getCenter().add(Vec3.atLowerCornerOf(inBy.getUnitVec3i()).scale(0.499));
    }

    /** Where this free line runs to. */
    private Vec3 point() {
        return point(aim, toFace ? face.getOpposite() : null);
    }

    /** What a beam ends on. */
    public enum End {
        /** Another laser node: the beam joins them. */
        NODE,
        /** A block that takes power on that face: the beam charges it. */
        SINK,
        /** A block that stops light and takes nothing. */
        BLOCKED,
        /** Nothing within range: it faded out. */
        OPEN,
        /** A Laser Mirror: only while it's traced, then followed on through (see {@link #follow}). */
        MIRROR
    }

    /** The length an entangled beam is given: it crosses nothing, so it has no path and isn't drawn. */
    public static final int JUMP = -1;

    /** Whether it's entangled light: from an Entangled Mirror straight to its partner, crossing nothing between. */
    public boolean jumps() {
        return length == JUMP;
    }

    /**
     * Light from an Entangled Mirror at {@code from} to its partner at {@code target}: there at once, if the partner
     * is loaded and is one (it arrives as if from behind it, to leave by its face); otherwise it goes nowhere.
     */
    public static LaserBeam jump(Level level, BlockPos from, BlockPos target) {
        if (level.isLoaded(target) && level.getBlockEntity(target) instanceof LaserNode node && node.entangledFace() != null && !target.equals(from)) {
            return new LaserBeam(from, node.entangledFace(), target, JUMP, target, End.NODE, 0, false, Lens.NONE);
        }
        return new LaserBeam(from, Direction.UP, target, JUMP, null, End.OPEN, 0, false, Lens.NONE);
    }

    public LaserBeam withFlow(long flow) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses, ray, toFace, legs, fork);
    }

    public LaserBeam withLive(boolean live) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses, ray, toFace, legs, fork);
    }

    public LaserBeam withLenses(int lenses) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses, ray, toFace, legs, fork);
    }

    private LaserBeam withRay(Ray ray) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses, ray, toFace);
    }

    /** Whether it's the same run of light as {@code other}, whatever each carried. */
    public boolean sameRun(LaserBeam other) {
        return face == other.face && length == other.length && end == other.end && Objects.equals(aim, other.aim)
                && Objects.equals(hit, other.hit) && ray == other.ray && toFace == other.toFace && legs.equals(other.legs)
                && fork == other.fork;
    }

    /**
     * {@code first} (one run, as traced) followed on through every Laser Mirror it ends on: off the plate's mirrored side
     * it bounces, the way out the nearest of the 26 to the true reflection (light in at a prism's free angle is bent
     * only here), and its range starts again; on the back or edge, or a mirror switched off, it stops. A Laser Splitter
     * also lets it straight through, so one beam can become several: each is a whole beam from {@code first}'s start.
     */
    public static List<LaserBeam> follow(Level level, LaserBeam first, int range, int marks) {
        List<LaserBeam> out = new ArrayList<>();
        if (first.end != End.MIRROR) {
            out.add(first);
            return out;
        }
        bend(level, first, new ArrayList<>(), first.hit, first.travel(), range, marks, out);
        return out;
    }

    private static void bend(Level level, LaserBeam first, List<Leg> legs, BlockPos mirror, Vec3 travel, int range, int marks,
                             List<LaserBeam> out) {
        BlockState state = level.getBlockState(mirror);
        List<Ray> ways = new ArrayList<>(2);
        if (legs.size() < MAX_BOUNCES && state.getBlock() instanceof com.kadikular.quantimium.block.LaserMirrorBlock block
                && !state.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.POWERED)) {
            Ray turned = com.kadikular.quantimium.block.LaserMirrorBlock.plate(state).reflect(travel, block.splits());
            if (turned != null) ways.add(turned);
            // Half-silvered: straight on as well.
            if (block.splits()) ways.add(Ray.nearest(travel));
            // Straight into its stand, light stops there.
            Ray stand = Ray.of(state.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT));
            ways.removeIf(way -> way == stand);
        }
        if (ways.isEmpty()) {
            out.add(finish(first, legs, mirror, End.BLOCKED, out.size()));
            return;
        }
        for (Ray way : ways) {
            // Round and round between mirrors, leaving one the same way it was left before (or forked too many times): it
            // fades there.
            if (out.size() >= MAX_FORKS || legs.stream().anyMatch(leg -> leg.at.equals(mirror) && leg.ray == way)) {
                if (out.size() < MAX_FORKS) out.add(finish(first, legs, mirror, End.OPEN, out.size()));
                continue;
            }
            LaserBeam run = trace(level, mirror, way, range, marks, null);
            List<Leg> more = new ArrayList<>(legs);
            more.add(new Leg(mirror, way, run.length));
            if (run.end == End.MIRROR) bend(level, first, more, run.hit, way.unit(), range, marks, out);
            else out.add(finish(first, more, run.hit, run.end, out.size()));
        }
    }

    private static LaserBeam finish(LaserBeam first, List<Leg> legs, @Nullable BlockPos hit, End end, int fork) {
        return new LaserBeam(first.from, first.face, first.aim, first.length, hit, end, 0, false, Lens.NONE, first.ray, first.toFace,
                legs, fork);
    }

    /** Which way its first run travels: a unit vector. */
    private Vec3 travel() {
        if (ray != null) return ray.unit();
        return point().subtract(from.getCenter()).normalize();
    }

    /** Where its first run ends: at the first mirror it bounces off, or on what it ends on. */
    @Nullable
    public BlockPos firstEnd() {
        return legs.isEmpty() ? hit : legs.getFirst().at();
    }

    /** The way its last run travels; null for a free line that never bounced. */
    @Nullable
    public Ray lastRay() {
        return legs.isEmpty() ? ray : legs.getLast().ray();
    }

    /** The way, to the nearest of the six, it's travelling as it reaches what it ends on: that block's face it comes in by is this one's opposite. */
    public Direction arriving() {
        return legs.isEmpty() ? face : legs.getLast().ray().face();
    }

    /** How many steps its last run takes before what it ends on: its first run's, if it never bounced. */
    public int lastLength() {
        return legs.isEmpty() ? length : legs.getLast().length();
    }

    /** How many mirrors it bounced off: each takes a little of what it carries. */
    public int bounces() {
        return legs.size();
    }

    /**
     * Follows light from {@code from}'s {@code direction} face for up to {@code range} blocks. Light
     * crosses air, glass and anything with no collision (grass, torches, flowers); it ends on the first
     * other block, or on a laser node, or where a chunk isn't loaded.
     */
    public static LaserBeam trace(Level level, BlockPos from, Direction direction, int range) {
        return trace(level, from, direction, range, 0);
    }

    /** As {@link #trace(Level, BlockPos, Direction, int)}, for light with {@code marks} ({@link Light#PHANTOM}, {@link Light#MIRRORED}). */
    public static LaserBeam trace(Level level, BlockPos from, Direction direction, int range, int marks) {
        BlockPos.MutableBlockPos at = from.mutable();
        for (int step = 0; step < range; step++) {
            at.move(direction);
            LaserBeam ended = endsAt(level, from, direction, null, step, at.immutable(), marks);
            if (ended != null) return ended;
        }
        return new LaserBeam(from, direction, null, range, null, End.OPEN, 0, false, Lens.NONE);
    }

    /**
     * Follows light from {@code from} along {@code ray}, block centre to block centre, for up to {@code range} blocks
     * (a diagonal step counts its true length). It ends as a face's light does; and on {@code aim}, a prism's linked
     * block, if it passes through it, whatever's there. A diagonal slips between two blocks that meet at a corner across
     * its way unless {@link com.kadikular.quantimium.Config#diagonalsPassCorners()} is off.
     */
    public static LaserBeam trace(Level level, BlockPos from, Ray ray, int range, int marks, @Nullable BlockPos aim) {
        Direction face = ray.face();
        int steps = (int) Math.floor(range / ray.length + 1.0e-9);
        for (int step = 1; step <= steps; step++) {
            BlockPos at = ray.step(from, step);
            if (!ray.straight() && !com.kadikular.quantimium.Config.diagonalsPassCorners() && cornerShut(level, ray.step(from, step - 1), ray, marks)) {
                return new LaserBeam(from, face, aim, step - 1, null, End.OPEN, 0, false, Lens.NONE, ray);
            }
            LaserBeam ended = endsAt(level, from, face, aim, step - 1, at, marks);
            if (at.equals(aim)) {
                return (ended != null ? ended : new LaserBeam(from, face, aim, step - 1, at, End.BLOCKED, 0, false, Lens.NONE)).withRay(ray);
            }
            if (ended != null) return ended.withRay(ray);
        }
        return new LaserBeam(from, face, aim, steps, null, End.OPEN, 0, false, Lens.NONE, ray);
    }

    /** Whether every block a diagonal step from {@code at} squeezes past, one axis at a time, stops light: the corner's shut. */
    private static boolean cornerShut(Level level, BlockPos at, Ray ray, int marks) {
        for (Direction side : ray.faces()) {
            BlockPos beside = at.relative(side);
            if (!level.isLoaded(beside)) return false;
            BlockState state = level.getBlockState(beside);
            if (state.isAir() || passes(level, beside, state) || (marks & Light.PHANTOM) != 0) return false;
        }
        return true;
    }

    /**
     * Follows light from the middle of {@code from} towards the middle of {@code target}, through every
     * block the line crosses, for up to {@code range} blocks. Ends on the first block that stops light,
     * or on the target.
     */
    public static LaserBeam traceTo(Level level, BlockPos from, BlockPos target, int range) {
        return traceTo(level, from, target, range, 0);
    }

    /** As {@link #traceTo(Level, BlockPos, BlockPos, int)}, for light with {@code marks}. */
    public static LaserBeam traceTo(Level level, BlockPos from, BlockPos target, int range, int marks) {
        return traceTo(level, from, target, null, range, marks);
    }

    /**
     * A prism's light: as {@link #traceTo(Level, BlockPos, BlockPos, int, int)}, but to the middle of {@code target}'s
     * {@code inBy} face, and in by it (null: to its middle). The line is never bent onto one of the 26 ways.
     */
    public static LaserBeam traceTo(Level level, BlockPos from, BlockPos target, @Nullable Direction inBy, int range, int marks) {
        Vec3 start = from.getCenter();
        // A face reached only through the block (from level with it, or in front): the light meets the face it really
        // comes to first, and is that face's, as any light would be.
        if (inBy != null) inBy = entryFace(start, point(target, inBy), target);
        Vec3 delta = point(target, inBy).subtract(start);
        Direction face = inBy != null ? inBy.getOpposite() : Direction.getApproximateNearest(delta.x, delta.y, delta.z);
        boolean toFace = inBy != null;
        if (delta.length() > range) return new LaserBeam(from, face, target, range, null, End.OPEN, 0, false, Lens.NONE, null, toFace);
        // Every block the line passes through, in order (Amanatides and Woo's walk).
        int x = from.getX(), y = from.getY(), z = from.getZ();
        int stepX = (int) Math.signum(delta.x), stepY = (int) Math.signum(delta.y), stepZ = (int) Math.signum(delta.z);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.x);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.y);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.z);
        // From the middle of a block, the first boundary is half a block away along each axis.
        double tMaxX = tDeltaX * 0.5, tMaxY = tDeltaY * 0.5, tMaxZ = tDeltaZ * 0.5;
        int steps = 0;
        while (steps++ < range * 3 + 3) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            BlockPos pos = new BlockPos(x, y, z);
            int length = (int) Math.round(pos.getCenter().distanceTo(start)) - 1;
            if (pos.equals(target)) {
                LaserBeam ended = endsAt(level, from, face, target, length, pos, marks);
                return (ended != null ? ended : new LaserBeam(from, face, target, length, pos, End.BLOCKED, 0, false, Lens.NONE)).free(toFace);
            }
            LaserBeam ended = endsAt(level, from, face, target, length, pos, marks);
            if (ended != null) return ended.free(toFace);
        }
        return new LaserBeam(from, face, target, range, null, End.OPEN, 0, false, Lens.NONE, null, toFace);
    }

    /** The face of {@code box} a line from {@code start} to {@code end} goes in by first: the slab it's last to enter. */
    private static Direction entryFace(Vec3 start, Vec3 end, BlockPos box) {
        Vec3 d = end.subtract(start);
        double best = Double.NEGATIVE_INFINITY;
        Direction face = Direction.getApproximateNearest(-d.x, -d.y, -d.z);
        for (Direction.Axis axis : Direction.Axis.values()) {
            double step = d.get(axis);
            if (Math.abs(step) < 1e-9) continue;
            double near = step > 0 ? box.get(axis) : box.get(axis) + 1;
            double t = (near - start.get(axis)) / step;
            if (t > best) {
                best = t;
                face = Direction.fromAxisAndDirection(axis, step > 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
            }
        }
        return face;
    }

    /** This, as a free line (no ray), to the face or the middle of what it's aimed at. */
    private LaserBeam free(boolean toFace) {
        return new LaserBeam(from, face, aim, length, hit, end, flow, live, lenses, null, toFace);
    }

    /** What the light does at {@code pos}: ends there (and how), or null to go on through. */
    @Nullable
    private static LaserBeam endsAt(Level level, BlockPos from, Direction face, @Nullable BlockPos aim, int length, BlockPos pos, int marks) {
        if (!level.isLoaded(pos)) return new LaserBeam(from, face, aim, length, null, End.OPEN, 0, false, Lens.NONE);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        // A Laser Mirror: followed on through by follow().
        if (state.getBlock() instanceof com.kadikular.quantimium.block.LaserMirrorBlock) return new LaserBeam(from, face, aim, length, pos, End.MIRROR, 0, false, Lens.NONE);
        // Only a block with a block entity can be a laser block or take power: the rest are looked up no further.
        boolean entity = state.hasBlockEntity();
        // A Laser Focus block with no focus in it: light passes it by bare faces, in and out; an attachment on either stops it.
        if (entity && level.getBlockEntity(pos) instanceof com.kadikular.quantimium.block.entity.LaserFocusBlockEntity focus
                && focus.core() == com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Core.NONE) {
            var none = com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Part.NONE;
            if (focus.part(face) == none && focus.part(face.getOpposite()) == none) return null;
            return new LaserBeam(from, face, aim, length, pos, End.BLOCKED, 0, false, Lens.NONE);
        }
        if (entity && level.getBlockEntity(pos) instanceof LaserNode) return new LaserBeam(from, face, aim, length, pos, End.NODE, 0, false, Lens.NONE);
        if (passes(level, pos, state)) return null;
        // Anomalite is of the mirror: light in the real world goes through it as if it weren't there.
        if ((marks & Light.MIRRORED) == 0 && state.getBlock() instanceof com.kadikular.quantimium.block.AnomaliteCrystalBlock) return null;
        // In the mirror, light charges nothing of the real world: a block that takes power just stops it.
        boolean takes = entity && (marks & Light.MIRRORED) == 0 && level.getCapability(Capabilities.Energy.BLOCK, pos, face.getOpposite()) != null;
        // Phantom light goes through anything but a laser block, or a block it charges.
        if ((marks & Light.PHANTOM) != 0 && !takes) return null;
        return new LaserBeam(from, face, aim, length, pos, takes ? End.SINK : End.BLOCKED, 0, false, Lens.NONE);
    }

    /**
     * The block just before what it ends on, one step back along its way (a crop light passes through before the
     * farmland; where a block it places goes). Only for a beam that ends on something.
     */
    public BlockPos before() {
        Ray last = lastRay();
        if (last != null) return last.step(hit, -1);
        return hit.relative(face.getOpposite());
    }

    /** The marks this beam's light carries, from its look. */
    public int marks() {
        return (Lens.has(lenses, Lens.PHANTOM) ? Light.PHANTOM : 0) | ((lenses & 1 << Lens.MIRRORED_MARK) != 0 ? Light.MIRRORED : 0);
    }

    /** Every block this beam passes on its way, not counting what it ends on. */
    public java.util.List<BlockPos> path() {
        java.util.List<BlockPos> cells = firstPath();
        for (Leg leg : legs) for (int step = 1; step <= leg.length(); step++) cells.add(leg.ray().step(leg.at(), step));
        return cells;
    }

    /** The blocks its first run crosses. */
    private java.util.List<BlockPos> firstPath() {
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        if (ray != null && !jumps()) {
            for (int step = 1; step <= length; step++) cells.add(ray.step(from, step));
            return cells;
        }
        BlockPos firstEnd = firstEnd();
        Vec3 start = from.getCenter();
        Vec3 delta = point().subtract(start);
        int x = from.getX(), y = from.getY(), z = from.getZ();
        int stepX = (int) Math.signum(delta.x), stepY = (int) Math.signum(delta.y), stepZ = (int) Math.signum(delta.z);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.x);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.y);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / delta.z);
        double tMaxX = tDeltaX * 0.5, tMaxY = tDeltaY * 0.5, tMaxZ = tDeltaZ * 0.5;
        for (int steps = 0; steps < length * 3 + 3; steps++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (pos.equals(firstEnd) || pos.equals(aim) || pos.getCenter().distanceTo(start) > length + 1) break;
            cells.add(pos);
        }
        return cells;
    }

    /** How many blocks light wouldn't otherwise pass this beam goes through: what Phantom light pays for. */
    public int solidsThrough(Level level) {
        int solids = 0;
        for (BlockPos pos : path()) {
            if (level.isLoaded(pos) && !passes(level, pos, level.getBlockState(pos))) solids++;
        }
        return solids;
    }

    /** Whether light goes through this block. */
    public static boolean passes(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(com.kadikular.quantimium.init.ModTags.LASER_CLEAR)) return true;
        // Light goes through water, and a waterlogged block it would go through dry; any other fluid stops it.
        var fluid = state.getFluidState();
        if (!fluid.isEmpty() && !fluid.is(net.minecraft.tags.FluidTags.WATER)) return false;
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /** Traces this beam again the same way (light from a face, or aimed at a block), and on through its mirrors: each beam it makes. */
    public List<LaserBeam> retrace(Level level, int range) {
        if (jumps()) return List.of(jump(level, from, aim));
        LaserBeam first;
        if (aim == null) first = trace(level, from, face, range, marks());
        else first = ray != null ? trace(level, from, ray, range, marks(), aim) : traceTo(level, from, aim, toFace ? face.getOpposite() : null, range, marks());
        return follow(level, first, range, marks());
    }

    /** Whether this beam still runs as it did: every block it crosses still lets light through, and it ends on the same thing. */
    public boolean stillHolds(Level level, int range) {
        for (LaserBeam again : retrace(level, range)) if (sameRun(again)) return true;
        return false;
    }

    /** Where the light starts and stops, block-world coordinates: from the node's side to what it ends on. */
    public Vec3[] ends() {
        List<Vec3> points = points();
        return new Vec3[] {points.getFirst(), points.getLast()};
    }

    /**
     * The corners of its line, in order: where it starts, the middle of each mirror it bounces off, and where it ends.
     * Two points for a beam that never bounced.
     */
    public List<Vec3> points() {
        Vec3[] first = firstEnds();
        if (legs.isEmpty()) return List.of(first[0], first[1]);
        List<Vec3> points = new ArrayList<>(legs.size() + 2);
        points.add(first[0]);
        for (Leg leg : legs) points.add(leg.at().getCenter());
        Leg last = legs.getLast();
        points.add(last.at().getCenter().add(last.ray().vector().scale(last.length() + 0.5)));
        return points;
    }

    /** Its first run's two ends: to the middle of the first mirror it bounces off, if it does. */
    private Vec3[] firstEnds() {
        Vec3 centre = from.getCenter();
        if (jumps()) return new Vec3[] {centre, centre};
        if (!legs.isEmpty()) {
            Vec3 mirror = legs.getFirst().at().getCenter();
            return new Vec3[] {centre.add(mirror.subtract(centre).normalize().scale(0.5)), mirror};
        }
        if (ray != null) {
            // From the edge (or corner) it leaves by to the one it reaches what it ends on by: half a step each end.
            Vec3 step = ray.vector();
            return new Vec3[] {centre.add(step.scale(0.5)), centre.add(step.scale(length + 0.5))};
        }
        Vec3 to = point();
        Vec3 dir = to.subtract(centre).normalize();
        // Faded short of what it aimed at, it stops at its range; at what it aimed at, on the face it goes in by (or at
        // its middle); short of something in the way, at that.
        double reach = hit == null ? Math.min(length + 0.5, to.distanceTo(centre))
                : hit.equals(aim) ? to.distanceTo(centre) : Math.max(0.5, hit.getCenter().distanceTo(centre) - 0.5);
        return new Vec3[] {centre.add(dir.scale(0.5)), centre.add(dir.scale(reach))};
    }

    /** For the client: how it runs, what it ends on and how bright. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Dir", face.get3DDataValue());
        tag.putInt("Length", length);
        tag.putInt("End", end.ordinal());
        tag.putLong("Flow", flow);
        if (aim != null) tag.putLong("Aim", aim.asLong());
        if (hit != null) tag.putLong("Hit", hit.asLong());
        tag.putBoolean("Live", live);
        if (lenses != Lens.NONE) tag.putInt("Lenses", lenses);
        if (ray != null && aim != null) tag.putInt("Ray", ray.ordinal());
        if (toFace) tag.putBoolean("ToFace", true);
        if (!legs.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Leg leg : legs) {
                CompoundTag one = new CompoundTag();
                one.putLong("At", leg.at().asLong());
                one.putInt("Ray", leg.ray().ordinal());
                one.putInt("Length", leg.length());
                list.add(one);
            }
            tag.put("Legs", list);
        }
        if (fork != 0) tag.putInt("Fork", fork);
        return tag;
    }

    public static LaserBeam load(BlockPos from, CompoundTag tag) {
        Direction face = Direction.from3DDataValue(tag.getIntOr("Dir", 0));
        int length = tag.getIntOr("Length", 0);
        End end = End.values()[Math.clamp(tag.getIntOr("End", 0), 0, End.values().length - 1)];
        BlockPos aim = tag.getLong("Aim").map(BlockPos::of).orElse(null);
        BlockPos hit = tag.getLong("Hit").map(BlockPos::of).orElse(null);
        int ray = tag.getIntOr("Ray", -1);
        List<Leg> legs = new ArrayList<>();
        for (net.minecraft.nbt.Tag one : tag.getListOrEmpty("Legs")) {
            if (!(one instanceof CompoundTag leg)) continue;
            int way = leg.getIntOr("Ray", 0);
            legs.add(new Leg(BlockPos.of(leg.getLongOr("At", 0)), Ray.values()[Math.clamp(way, 0, Ray.values().length - 1)], leg.getIntOr("Length", 0)));
        }
        return new LaserBeam(from, face, aim, length, hit, end, tag.getLongOr("Flow", 0), tag.getBooleanOr("Live", false),
                tag.getIntOr("Lenses", Lens.NONE), aim == null ? Ray.of(face) : ray >= 0 && ray < Ray.values().length ? Ray.values()[ray] : null,
                tag.getBooleanOr("ToFace", false), legs, tag.getIntOr("Fork", 0));
    }
}
