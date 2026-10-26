package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.CavityMirrorBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One mirror of an optical cavity. Light out of a Cavity Mirror's front that comes back to it holds there, as in a
 * laser's cavity: off a flat Laser Mirror straight back, or round any number of plain Laser Mirrors to one that sends it
 * back the way it came, or into a second Cavity Mirror's front. A splitter, a mirror switched off, or anything else in
 * the way, and there's no cavity. Unpaired, it
 * passes light on out of its front, towards where its partner would stand, as a Laser Mirror does. Light that reaches either mirror from outside charges it;
 * light leaves through its back, the output coupler, for blocks that ask for it. Each holds
 * {@link LaserTuning#CAVITY_FE_PER_BLOCK} for each block of gap, and loses a little every tick, as flux.
 * A mirror that loses its partner, or is broken, lets all its light go as flux.
 */
public class CavityMirrorBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    private long stored;
    /** Blocks of light held one way round, mirrors on the way included; 0 for no cavity. */
    private int gap;
    /** The way light runs round the cavity, from its front to where it turns back or its partner; null for none. */
    @Nullable
    private LaserBeam loop;
    /** Whether the partner is a Cavity Mirror too, so both hold the light; a Laser Mirror holds none. */
    private boolean partnerHolds;
    private double leaked;
    /**
     * What the light it holds does, besides carry power: whatever the light that filled it did since it
     * was last empty. It gives that light out, and pays for what it does. Null for plain light.
     */
    @Nullable
    private com.kadikular.quantimium.laser.Light held;
    /**
     * The faces what it holds leaves by, set as a Relay's are: by default its back, never its mirror. None, and the
     * cavity only stores. Null until first known: then its back.
     */
    @Nullable
    private java.util.EnumSet<Direction> outputFaces;
    private List<LaserBeam> shownBeams = List.of();
    /** On the client: how bright the light held looks, for the glow between the mirrors. */
    private int shownCharge;
    /** On the client: the look of the light held (see {@link com.kadikular.quantimium.laser.Light#packed}), for the glow's tint. */
    private int shownLenses;

    public CavityMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAVITY_MIRROR_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(CavityMirrorBlock.FACING);
    }

    public boolean paired() {
        return gap > 0;
    }

    public long stored() {
        return stored;
    }

    /** The face it stands on. */
    public Direction mount() {
        BlockState state = getBlockState();
        return state.hasProperty(CavityMirrorBlock.MOUNT) ? state.getValue(CavityMirrorBlock.MOUNT) : Direction.DOWN;
    }

    /** The faces what it holds leaves by: never its mirror, nor its base. */
    public Set<Direction> outputFaces() {
        if (outputFaces == null) outputFaces = java.util.EnumSet.of(CavityMirrorBlock.outputFor(facing(), mount()));
        outputFaces.remove(facing());
        outputFaces.remove(mount());
        return outputFaces;
    }

    /** A cavity holds straight light only. */
    @Override
    public boolean accepts(com.kadikular.quantimium.laser.Ray ray) {
        return ray.straight() && accepts(ray.face());
    }

    /** No light comes in by its base. */
    @Override
    public boolean accepts(Direction travel) {
        return travel.getOpposite() != mount();
    }

    /** Turns light out of {@code face} on or off; never its mirror. Whether it changed. */
    public boolean toggleOutput(Direction face) {
        if (face == facing() || face == mount()) return false;
        Set<Direction> faces = outputFaces();
        if (!faces.remove(face)) faces.add(face);
        outputsChanged();
        return true;
    }

    /** Its mirror turned, or its outputs changed: the block shows them, and the network looks again. */
    public void outputsChanged() {
        setChanged();
        showOutputs();
        sync();
        if (level != null) LaserNetworks.changed(level);
    }

    private void showOutputs() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(CavityMirrorBlock.OUTPUTS.get(Direction.NORTH))) return;
        BlockState shown = state;
        // One from before it stood on a base, its mirror facing down: it stands on the other side instead.
        if (shown.getValue(CavityMirrorBlock.MOUNT) == shown.getValue(CavityMirrorBlock.FACING)) {
            shown = shown.setValue(CavityMirrorBlock.MOUNT, shown.getValue(CavityMirrorBlock.FACING).getOpposite());
        }
        Set<Direction> faces = outputFaces();
        for (Direction face : Direction.values()) shown = shown.setValue(CavityMirrorBlock.OUTPUTS.get(face), faces.contains(face));
        if (shown != state) level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
    }

    public long capacity() {
        return gap * LaserTuning.CAVITY_FE_PER_BLOCK;
    }

    /**
     * Follows light out of its front, round plain Laser Mirrors, until it comes back: off a mirror that sends it back
     * the way it came (from there it retraces its way here), into a Cavity Mirror's front, or a Relay giving light back
     * along it. How many blocks of light that holds one way, mirrors on the way included, or 0 for no cavity; at most
     * {@link LaserTuning#CAVITY_MAX_GAP}, a loop of twice that.
     */
    private int findGap() {
        partnerHolds = false;
        loop = null;
        if (level == null) return 0;
        Direction facing = facing();
        com.kadikular.quantimium.laser.Ray way = com.kadikular.quantimium.laser.Ray.of(facing);
        BlockPos from = worldPosition;
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
            if (level.getBlockEntity(hit) instanceof CavityMirrorBlockEntity other) {
                if (!straightIn || other.facing() != way.face().getOpposite() || length < 1) return 0;
                partnerHolds = other != this;
                return closed(hit, firstLength, legs, length);
            }
            if (there.getBlock() instanceof com.kadikular.quantimium.block.LaserRelayBlock) {
                if (!straightIn || !there.getValue(com.kadikular.quantimium.block.LaserRelayBlock.OUTPUTS.get(way.face().getOpposite())) || length < 1) return 0;
                return closed(hit, firstLength, legs, length);
            }
            // Only plain Laser Mirrors, switched on: a splitter would let the light out.
            if (!(there.getBlock() instanceof com.kadikular.quantimium.block.LaserMirrorBlock mirror) || mirror.splits()
                    || there.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.POWERED)) return 0;
            com.kadikular.quantimium.laser.Ray turned = com.kadikular.quantimium.block.LaserMirrorBlock.plate(there).reflect(way.unit(), false);
            if (turned == null || turned == com.kadikular.quantimium.laser.Ray.of(there.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT))) return 0;
            // Straight back the way it came: it retraces its way here, and the cavity's closed.
            if (turned == way.opposite()) return length < 1 ? 0 : closed(hit, firstLength, legs, length);
            // The mirror's own block holds light too.
            length += 1;
            legs.add(new LaserBeam.Leg(hit, turned, 0));
            from = hit;
            way = turned;
        }
        return 0;
    }

    /** A cavity closed at {@code end}: remembers its way round, for the glow of the light it holds. */
    private int closed(BlockPos end, int firstLength, List<LaserBeam.Leg> legs, double length) {
        loop = new LaserBeam(worldPosition, facing(), null, firstLength, end, LaserBeam.End.BLOCKED, 0, true,
                com.kadikular.quantimium.laser.Lens.NONE, com.kadikular.quantimium.laser.Ray.of(facing()), false, List.copyOf(legs), 0);
        return Math.max(1, (int) Math.floor(length + 1.0e-6));
    }

    /** Where the light it holds turns back, or its partner: the cavity's far end. */
    @Nullable
    private BlockPos farEnd() {
        return loop != null ? loop.hit() : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CavityMirrorBlockEntity mirror) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % 20 == Math.floorMod(pos.hashCode(), 20)) mirror.checkPartner(server);
        mirror.share(server);
        if (mirror.stored > 0) {
            double leak = com.kadikular.quantimium.Config.cavityLeak(mirror.stored);
            mirror.leaked += leak;
            mirror.stored -= (long) Math.ceil(leak);
            if (mirror.stored < 0) mirror.stored = 0;
            mirror.setChanged();
        }
        mirror.forgetIfEmpty();
        if (level.getGameTime() % 20 == 0) {
            if (mirror.leaked >= 1) QuantumFlux.emitFromEnergy(server, pos, (long) mirror.leaked);
            mirror.leaked = 0;
            mirror.showCharge();
        }
    }

    /**
     * Light held in a cavity bounces between both mirrors: what either holds evens out between them, so
     * both glow and both can give it out. The lower of the two does it, once a tick.
     */
    private void share(ServerLevel server) {
        if (!paired() || !partnerHolds) return;
        BlockPos other = farEnd();
        if (other == null || other.compareTo(worldPosition) < 0 || !(server.getBlockEntity(other) instanceof CavityMirrorBlockEntity partner)) return;
        long total = stored + partner.stored;
        if (Math.abs(stored - partner.stored) <= 1) return;
        stored = Math.min(capacity(), total / 2);
        partner.stored = Math.min(partner.capacity(), total - stored);
        // The light mixes as it bounces: both hold what either did.
        if (!sameLight(held, partner.held)) {
            com.kadikular.quantimium.laser.Light mixed = held == null ? partner.held
                    : partner.held == null ? held : com.kadikular.quantimium.laser.Light.join(held, partner.held);
            if (mixed != null) {
                held = mixed.reEmitted(worldPosition);
                partner.held = mixed.reEmitted(partner.worldPosition);
                LaserNetworks.changed(server);
            }
        }
        setChanged();
        partner.setChanged();
    }

    /** Whether two mirrors' light does the same, whoever pays for it. */
    private static boolean sameLight(@Nullable com.kadikular.quantimium.laser.Light a, @Nullable com.kadikular.quantimium.laser.Light b) {
        if (a == null || b == null) return a == b;
        return a.covers(b) && b.covers(a);
    }

    /** Empty, it forgets what its light did: the next light to fill it decides. */
    private void forgetIfEmpty() {
        if (stored > 0 || held == null) return;
        held = null;
        setChanged();
        if (level != null) LaserNetworks.changed(level);
    }

    @Override
    public com.kadikular.quantimium.laser.Light emits() {
        return held != null ? held.reEmitted(worldPosition) : com.kadikular.quantimium.laser.Light.from(worldPosition, range());
    }

    @Override
    public boolean absorb(com.kadikular.quantimium.laser.Light light) {
        if (held != null && held.covers(light)) return false;
        if (held == null && light.streams().isEmpty() && light.boosts() == 0 && light.range() == range()) return false;
        held = (held == null ? light : com.kadikular.quantimium.laser.Light.join(held, light)).reEmitted(worldPosition);
        setChanged();
        return true;
    }

    /** What its light does, for readouts; null for plain light. */
    @Nullable
    public com.kadikular.quantimium.laser.Light held() {
        return held;
    }

    private void checkPartner(ServerLevel server) {
        boolean held = partnerHolds;
        LaserBeam was = loop;
        int now = findGap();
        if (now == gap && held == partnerHolds && java.util.Objects.equals(was, loop)) return;
        gap = now;
        if (stored > capacity()) release(server, stored - capacity());
        setChanged();
        LaserNetworks.changed(server);
        showCharge();
        sync();
    }

    /** Lets {@code fe} of held light go, as flux where it stands. */
    private void release(ServerLevel server, long fe) {
        if (fe <= 0) return;
        stored -= fe;
        QuantumFlux.emitFromEnergy(server, worldPosition, fe);
    }

    /** Tells clients when the light held changes enough to see. */
    private void showCharge() {
        int charge = LaserNetworks.brightness(stored / 100);
        BlockState state = getBlockState();
        boolean lit = stored > 0 && paired();
        if (level != null && state.getValue(CavityMirrorBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(CavityMirrorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        int lenses = held == null ? com.kadikular.quantimium.laser.Lens.NONE : held.packed();
        if (charge != shownCharge || lenses != shownLenses) {
            shownCharge = charge;
            shownLenses = lenses;
            sync();
        }
    }

    // ---- the network ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    /**
     * Paired, what it holds leaves by its outputs. Unpaired, it's a mirror: light that reaches it leaves by the
     * front, towards where its partner would stand.
     */
    @Override
    public Set<Direction> outputs() {
        if (!paired()) return Set.of(facing());
        return Set.copyOf(outputFaces());
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    /** Unpaired, it's a mirror: light passes through it, out of its front. */
    @Override
    public boolean relays() {
        return !paired();
    }

    @Override
    public boolean buffers() {
        return paired();
    }

    @Override
    public long available() {
        return paired() && !outputFaces().isEmpty() ? Math.min(stored, LaserTuning.CAVITY_RATE) : 0;
    }

    @Override
    public void draw(long fe) {
        stored = Math.max(0, stored - fe);
        setChanged();
    }

    @Override
    public long room() {
        return paired() ? Math.max(0, Math.min(capacity() - stored, LaserTuning.CAVITY_RATE)) : 0;
    }

    @Override
    public void fill(long fe) {
        stored = Math.min(capacity(), stored + fe);
        setChanged();
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same) sync();
    }

    /** The glow of the light held is wider than a beam carrying as much: the only light out of its front while paired. */
    @Override
    public float widthScale(LaserBeam beam) {
        return gap > 0 && beam.face() == facing() ? 1.8f : 1.0f;
    }

    /** Its output, and the light held, drawn all the way round: by this one, or the lower of two Cavity Mirrors. */
    @Override
    public List<LaserBeam> syncedBeams() {
        if (gap <= 0 || loop == null || shownCharge <= 0) return shownBeams;
        BlockPos other = farEnd();
        if (partnerHolds && other != null && other.compareTo(worldPosition) < 0) return shownBeams;
        List<LaserBeam> all = new ArrayList<>(shownBeams);
        long flow = (long) Math.pow(10, shownCharge - 1);
        all.add(loop.withFlow(flow).withLenses(shownLenses));
        return all;
    }

    // ---- life ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            gap = findGap();
            showOutputs();
            LaserNetworks.add(level, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) LaserNetworks.remove(level, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) LaserNetworks.remove(level, this);
    }

    /** Broken, it lets all the light it held go, as flux. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) release(server, stored);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("Stored", stored);
        out.putInt("Gap", gap);
        out.putBoolean("PartnerHolds", partnerHolds);
        int mask = 0;
        for (Direction face : outputFaces()) mask |= 1 << face.get3DDataValue();
        out.putInt("Outputs", mask);
        if (held != null) out.store("Light", CompoundTag.CODEC, held.save());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        stored = in.getLongOr("Stored", 0);
        gap = in.getIntOr("Gap", 0);
        partnerHolds = in.getBooleanOr("PartnerHolds", true);
        // Before outputs were set face by face: its back, or (output off) none.
        int mask = in.getIntOr("Outputs", -1);
        if (mask >= 0) {
            outputFaces = java.util.EnumSet.noneOf(Direction.class);
            for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputFaces.add(face);
        } else if (!in.getBooleanOr("Output", true)) {
            outputFaces = java.util.EnumSet.noneOf(Direction.class);
        }
        held = in.read("Light", CompoundTag.CODEC).map(tag -> com.kadikular.quantimium.laser.Light.load(tag, worldPosition)).orElse(null);
        shownCharge = in.getIntOr("Charge", 0);
        shownLenses = in.getIntOr("Lenses", 0);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
        loop = in.read("Loop", CompoundTag.CODEC).map(tag -> LaserBeamView.load(worldPosition, tag)).filter(beams -> !beams.isEmpty())
                .map(List::getFirst).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        if (loop != null) tag.put("Loop", LaserBeamView.save(List.of(loop)));
        tag.putInt("Charge", shownCharge);
        tag.putInt("Lenses", shownLenses);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What it holds, or why it holds nothing. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        if (!paired()) return Component.translatable("message.quantimium.cavity.unpaired").withStyle(ChatFormatting.GRAY);
        MutableComponent line = Component.translatable("message.quantimium.cavity.holds", String.format(java.util.Locale.ROOT, "%,d", stored),
                String.format(java.util.Locale.ROOT, "%,d", capacity()), gap).withStyle(ChatFormatting.DARK_AQUA);
        return !outputFaces().isEmpty() ? line : line.append(Component.translatable("message.quantimium.cavity.no_output").withStyle(ChatFormatting.GRAY));
    }
}
