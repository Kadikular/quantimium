package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.LensItem;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LensUse;
import com.kadikular.quantimium.laser.RelayMode;
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
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a Laser Focus block holds: a focus in its middle, or none, and on each of its six faces an attachment, or
 * nothing. Like a cable bus: attachments can be put on a block with no focus, and do nothing until one is added; the
 * focus can be taken out and leave them. The block goes when it holds nothing.
 *
 * <p>With a {@link Core#LASER Laser Focus}, light that reaches it from any open face stops there and joins, and leaves by
 * each of its Emitters, refocused, shared by what each beam's blocks ask for. A {@link Core#CLEAR Clear Laser Focus}
 * does the same, and light also passes straight through it and on out of the opposite face, unless something there
 * stops it. The focus itself loses nothing; each Emitter takes {@link Config#relayLoss()} of what leaves by it. Each
 * Emitter holds one lens, which only the light leaving by it goes through, and has its own way of listening to
 * redstone.
 */
public class LaserFocusBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, EmitterHolder {

    /** What's in its middle. */
    public enum Core {
        NONE, LASER, CLEAR;

        /** The item it is, or nothing. */
        public ItemStack item() {
            return switch (this) {
                case NONE -> ItemStack.EMPTY;
                case LASER -> new ItemStack(ModItems.LASER_FOCUS.get());
                case CLEAR -> new ItemStack(ModItems.CLEAR_LASER_FOCUS.get());
            };
        }

        static Core byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
        }
    }

    /** What's on a face. */
    public enum Part {
        /** Nothing: light gets in and out freely. */
        NONE,
        /** A stand on the block that face is against. Light neither gets in nor out by it. */
        STAND,
        /** An emitter: light leaves by it; only diagonal light gets in by it. */
        EMITTER,
        /** A plain cap: light neither gets in nor out by it. */
        COVER,
        /** A Logistics Receptacle: the inventory that face is against is on the network (see {@link Receptacle}). */
        RECEPTACLE,
        /** A Cavity Mirror plate, facing out: it holds light that comes back to it (see {@link CavityPlate}). */
        CAVITY;

        /** Whether diagonal light gets in by a face with this on it (straight light only by a bare face). */
        public boolean open() {
            return this == NONE || this == EMITTER || this == CAVITY;
        }

        /** The item it is, or nothing. */
        public ItemStack item() {
            Item item = switch (this) {
                case NONE -> null;
                case STAND -> ModItems.FOCUS_STAND.get();
                case EMITTER -> ModItems.FOCUS_EMITTER.get();
                case COVER -> ModItems.FOCUS_COVER.get();
                case RECEPTACLE -> ModItems.LOGISTICS_RECEPTACLE_ITEM.get();
                case CAVITY -> ModItems.CAVITY_MIRROR_ITEM.get();
            };
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        static Part byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
        }
    }

    /** What its model draws: its focus and each face's part, packed as {@link #layout()} says. */
    public static final ModelProperty<Long> LAYOUT = new ModelProperty<>();

    private Core core = Core.NONE;
    private final Map<Direction, Part> parts = new EnumMap<>(Direction.class);
    /** The Logistics Receptacle on each face that has one. */
    private final Map<Direction, Receptacle> receptacles = new EnumMap<>(Direction.class);
    /** The Cavity Mirror plate on each face that has one. */
    private final Map<Direction, CavityPlate> cavities = new EnumMap<>(Direction.class);
    /** The lens in each emitter that has one. */
    private final Map<Direction, ItemStack> lenses = new EnumMap<>(Direction.class);
    /** How each emitter that's been set listens to redstone; the rest follow {@link #mode}. */
    private final Map<Direction, RelayMode> modes = new EnumMap<>(Direction.class);
    /** For emitters on a pulse: since when each is letting light through. */
    private final Map<Direction, Long> pulses = new EnumMap<>(Direction.class);
    /** The faces light passed straight through and out of, last worked out (a Clear Laser Focus). */
    private Set<Direction> passing = EnumSet.noneOf(Direction.class);

    private List<LaserBeam> shownBeams = List.of();
    /** How its emitters listen to redstone, but for those set one by one. */
    private RelayMode mode = RelayMode.IGNORED;
    /** Whether a redstone signal reaches it, as last told. */
    private boolean powered;
    /** Whether it's joined to the laser network: only with a focus in it. */
    private boolean joined;

    public LaserFocusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_FOCUS_BE.get(), pos, state);
    }

    // ---- what it holds ----

    public Core core() {
        return core;
    }

    public Part part(Direction face) {
        return parts.getOrDefault(face, Part.NONE);
    }

    /** Whether it holds nothing at all: then the block goes. */
    public boolean empty() {
        return core == Core.NONE && parts.isEmpty();
    }

    /** Puts {@code core} in its middle, if there's none. Whether it did. */
    public boolean setCore(Core core) {
        if (this.core != Core.NONE || core == Core.NONE) return false;
        this.core = core;
        changed();
        return true;
    }

    /** Takes its focus out: what it was, or nothing. */
    public ItemStack takeCore() {
        if (core == Core.NONE) return ItemStack.EMPTY;
        ItemStack was = core.item();
        core = Core.NONE;
        changed();
        return was;
    }

    /** Puts {@code part} on {@code face}, if there's nothing there. Whether it did. */
    public boolean setPart(Direction face, Part part) {
        if (part == Part.NONE || part(face) != Part.NONE) return false;
        parts.put(face, part);
        if (part == Part.RECEPTACLE) receptacles.put(face, new Receptacle(this, face));
        if (part == Part.CAVITY) {
            CavityPlate cavity = new CavityPlate(this, face);
            cavities.put(face, cavity);
            if (level instanceof net.minecraft.server.level.ServerLevel server) cavity.checkPartner(server);
        }
        changed();
        return true;
    }

    /** Puts an Emitter on {@code face}, if there's nothing there. Whether it did. */
    public boolean addEmitter(Direction face) {
        return setPart(face, Part.EMITTER);
    }

    /** Takes what's on {@code face} off: it, and an emitter's lens, as items. */
    public List<ItemStack> takePart(Direction face) {
        Part was = parts.remove(face);
        if (was == null) return List.of();
        List<ItemStack> out = new ArrayList<>(2);
        out.add(was.item());
        ItemStack lens = lenses.remove(face);
        if (lens != null && !lens.isEmpty()) out.add(lens);
        modes.remove(face);
        pulses.remove(face);
        receptacles.remove(face);
        // A cavity taken off lets all it held go, as flux.
        CavityPlate cavity = cavities.remove(face);
        if (cavity != null && level instanceof net.minecraft.server.level.ServerLevel server) cavity.releaseAll(server);
        changed();
        return out;
    }

    /** Everything it holds, as items: what breaking it drops. */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        if (core != Core.NONE) out.add(core.item());
        for (Part part : parts.values()) out.add(part.item());
        for (ItemStack lens : lenses.values()) if (!lens.isEmpty()) out.add(lens.copy());
        return out;
    }

    /**
     * Its focus and its faces, packed: 2 bits for the focus, then 6 for each face, by its 3D data value: 3 for what's
     * there and 3 for how it looks (a receptacle's role and whether it's lit).
     */
    public long layout() {
        long packed = core.ordinal();
        for (Direction face : Direction.values()) {
            Receptacle receptacle = receptacles.get(face);
            long look = receptacle != null ? receptacle.look() : 0;
            packed |= (part(face).ordinal() | look << 3) << (2 + 6 * face.get3DDataValue());
        }
        return packed;
    }

    public static Core coreOf(long layout) {
        return Core.byOrdinal((int) (layout & 3));
    }

    public static Part partOf(long layout, Direction face) {
        return Part.byOrdinal((int) (layout >> (2 + 6 * face.get3DDataValue()) & 7));
    }

    /** How what's on {@code face} looks: a receptacle's role and whether it's lit, as {@link Receptacle#look}. */
    public static int lookOf(long layout, Direction face) {
        return (int) (layout >> (5 + 6 * face.get3DDataValue()) & 7);
    }

    /** The Logistics Receptacle on {@code face}, or null. */
    @Nullable
    public Receptacle receptacle(Direction face) {
        return receptacles.get(face);
    }

    /** A receptacle's settings or state changed: saved, shown, and its look sent to clients. */
    void receptacleChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Its receptacles, on a logistics network while there's a focus in it. */
    @Override
    public List<com.kadikular.quantimium.laser.LogisticsMember> logisticsMembers() {
        return core == Core.NONE ? List.of() : List.copyOf(receptacles.values());
    }

    /** The faces whose receptacle powers the machine it's on, with a focus in it. */
    @Override
    public Set<Direction> feedsAll() {
        if (core == Core.NONE) return Set.of();
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        receptacles.forEach((face, receptacle) -> {
            if (receptacle.powers()) out.add(face);
        });
        return out;
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(LAYOUT, layout());
    }

    /** What it holds changed: its shape and look, and the light through it. */
    private void changed() {
        setChanged();
        if (level == null) return;
        if (!level.isClientSide()) {
            join();
            LaserNetworks.changed(level);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            // Its shape changed: a lever on a Stand that's gone falls off.
            getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** Joins the laser network with a focus in it; leaves it without. */
    private void join() {
        if (level == null || level.isClientSide()) return;
        boolean should = core != Core.NONE && !isRemoved();
        if (should == joined) return;
        joined = should;
        if (should) {
            LaserNetworks.add(level, this);
        } else {
            LaserNetworks.remove(level, this);
            // Out of the network, it shows no light leaving it.
            showBeams(List.of());
        }
    }

    // ---- emitters ----

    @Override
    public boolean hasEmitter(Direction face) {
        return part(face) == Part.EMITTER;
    }

    @Override
    public ItemStack lens(Direction face) {
        return lenses.getOrDefault(face, ItemStack.EMPTY);
    }

    @Override
    public void setLens(Direction face, ItemStack stack) {
        if (stack.isEmpty()) lenses.remove(face);
        else lenses.put(face, stack);
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            // Clients redraw its tip in the lens's colour: told by the server, or at once where it was changed.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), level.isClientSide() ? Block.UPDATE_IMMEDIATE : Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public List<LensUse> lensesFor(Direction face) {
        ItemStack stack = lens(face);
        if (!(stack.getItem() instanceof LensItem lens)) return List.of();
        return List.of(LensUse.of(stack, lens.lens()));
    }

    // ---- redstone ----

    public RelayMode mode() {
        return mode;
    }

    /** Steps every emitter on to the next way of listening to redstone, together. */
    public void cycleMode() {
        mode = mode.next();
        modes.clear();
        pulses.clear();
        setChanged();
        if (level != null) LaserNetworks.changed(level);
    }

    @Override
    public RelayMode modeOf(Direction face) {
        return modes.getOrDefault(face, mode);
    }

    @Override
    public void cycleMode(Direction face) {
        modes.put(face, modeOf(face).next());
        pulses.remove(face);
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The block next to it changed: a signal arriving opens a pulse on each emitter waiting for one. */
    public void onSignal(boolean signal) {
        if (signal && !powered && level != null) {
            for (Direction face : emitters()) if (modeOf(face) == RelayMode.PULSE) pulses.put(face, level.getGameTime());
        }
        powered = signal;
    }

    /** Whether the emitter on {@code face} lets light out now. */
    public boolean on(Direction face) {
        return switch (modeOf(face)) {
            case IGNORED -> true;
            case WITH_SIGNAL -> powered;
            case WITHOUT_SIGNAL -> !powered;
            case PULSE -> {
                Long at = pulses.get(face);
                yield at != null && level != null && level.getGameTime() - at <= RelayMode.PULSE_TICKS;
            }
        };
    }

    /** The faces with an emitter on. */
    private Set<Direction> emitters() {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Map.Entry<Direction, Part> entry : parts.entrySet()) if (entry.getValue() == Part.EMITTER) out.add(entry.getKey());
        return out;
    }

    // ---- cavities ----

    /** The Cavity Mirror plate on {@code face}, or null. */
    @Nullable
    public CavityPlate cavity(Direction face) {
        return cavities.get(face);
    }

    /** With a focus in it and a cavity of one of its plates closed, it stores light. */
    @Override
    public boolean buffers() {
        if (core == Core.NONE) return false;
        for (CavityPlate cavity : cavities.values()) if (cavity.paired()) return true;
        return false;
    }

    /** Storing light, it gives it out too, as well as passing on what reaches it. */
    @Override
    public boolean generates() {
        return buffers();
    }

    /** What its cavities hold leaves by its Emitters: with none, it only stores. */
    @Override
    public long available() {
        if (core == Core.NONE || emitters().isEmpty()) return 0;
        long total = 0;
        for (CavityPlate cavity : cavities.values()) total += cavity.available();
        return total;
    }

    @Override
    public void draw(long fe) {
        for (CavityPlate cavity : cavities.values()) {
            if (fe <= 0) break;
            long part = Math.min(fe, cavity.available());
            cavity.draw(part);
            fe -= part;
        }
    }

    @Override
    public long room() {
        if (core == Core.NONE) return 0;
        long total = 0;
        for (CavityPlate cavity : cavities.values()) total += cavity.room();
        return total;
    }

    @Override
    public void fill(long fe) {
        for (CavityPlate cavity : cavities.values()) {
            if (fe <= 0) break;
            long part = Math.min(fe, cavity.room());
            cavity.fill(part);
            fe -= part;
        }
    }

    /** What its cavities' light does: whatever filled them. */
    @Override
    public com.kadikular.quantimium.laser.Light emits() {
        com.kadikular.quantimium.laser.Light out = null;
        for (CavityPlate cavity : cavities.values()) {
            if (cavity.held() == null) continue;
            out = out == null ? cavity.held() : com.kadikular.quantimium.laser.Light.join(out, cavity.held());
        }
        return out != null ? out.reEmitted(worldPosition) : com.kadikular.quantimium.laser.Light.from(worldPosition, range());
    }

    @Override
    public boolean absorb(com.kadikular.quantimium.laser.Light light) {
        boolean more = false;
        for (CavityPlate cavity : cavities.values()) more |= cavity.absorb(light);
        return more;
    }

    /** On the server, each tick: its cavities. */
    public void serverTick() {
        if (cavities.isEmpty() || !(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        boolean show = false;
        boolean before = buffers();
        for (CavityPlate cavity : cavities.values()) show |= cavity.tick(server);
        if (before != buffers()) LaserNetworks.changed(server);
        if (show) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** On while any of its emitters is; a Clear Laser Focus always lets light through. */
    @Override
    public boolean enabled() {
        if (core == Core.CLEAR) return true;
        for (Direction face : emitters()) if (on(face)) return true;
        return false;
    }

    /** Its switch is only its emitters': an Entangled Mirror drawing on it still gets its light. */
    @Override
    public boolean passesEntangled() {
        return true;
    }

    @Override
    public boolean switches() {
        if (mode != RelayMode.IGNORED) return true;
        for (Direction face : emitters()) if (modeOf(face) != RelayMode.IGNORED) return true;
        return false;
    }

    /** Which of its emitters are on: a change in any is a change in its light. */
    @Override
    public int switchState() {
        int on = 0;
        for (Direction face : emitters()) if (on(face)) on |= 1 << face.get3DDataValue();
        return on;
    }

    @Override
    public void pulseDone() {
        pulses.clear();
    }

    @Override
    public void pulseDone(Direction face) {
        pulses.remove(face);
    }

    // ---- light ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    /** Its emitters, and (a Clear Laser Focus) the faces light last passed straight out of. */
    @Override
    public Set<Direction> outputs() {
        if (core == Core.NONE) return Set.of();
        Set<Direction> out = emitters();
        out.addAll(passing);
        return out;
    }

    /**
     * Out of each emitter that's on (with no light coming, all of them: where light would go); a Clear Laser Focus also
     * lets light straight on out of the opposite face, if that's open.
     */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        if (core == Core.NONE) return Set.of();
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction face : emitters()) if (arriving.isEmpty() || on(face)) out.add(face);
        Set<Direction> through = EnumSet.noneOf(Direction.class);
        if (core == Core.CLEAR) for (Direction travel : arriving) if (part(travel).open()) through.add(travel);
        passing = through;
        out.addAll(through);
        return out;
    }

    /** Straight light gets in by a bare face, with a focus to take it: not by a Stand, Cover or Emitter. */
    @Override
    public boolean accepts(Direction travel) {
        return core != Core.NONE && part(travel.getOpposite()) == Part.NONE;
    }

    /** Diagonal light gets in past an Emitter as well: by any face it crosses that has no Stand or Cover. */
    @Override
    public boolean accepts(com.kadikular.quantimium.laser.Ray ray) {
        if (ray.straight()) return accepts(ray.face());
        if (core == Core.NONE) return false;
        for (Direction travel : ray.faces()) if (part(travel.getOpposite()).open()) return true;
        return false;
    }

    /** The focus loses nothing: its emitters do. */
    @Override
    public double tax() {
        return 0;
    }

    /** Each emitter takes the relays' loss of what leaves by it; light passing straight through loses nothing. */
    @Override
    public double outTax(Direction face) {
        return hasEmitter(face) ? Config.relayLoss() : 0;
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same && level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Its beams, and the light its cavities hold, drawn all the way round. */
    @Override
    public List<LaserBeam> syncedBeams() {
        if (cavities.isEmpty()) return shownBeams;
        List<LaserBeam> all = new ArrayList<>(shownBeams);
        for (CavityPlate cavity : cavities.values()) {
            LaserBeam glow = cavity.glow();
            if (glow != null) all.add(glow);
        }
        return all;
    }

    /** The glow of the light a cavity holds is wider than a beam carrying as much. */
    @Override
    public float widthScale(LaserBeam beam) {
        CavityPlate cavity = beam.aim() == null ? cavities.get(beam.face()) : null;
        return cavity != null && cavity.paired() ? 1.8f : 1.0f;
    }

    /** Light leaves an Emitter at its tip; light passing straight through a face with none leaves from the focus. */
    @Override
    public net.minecraft.world.phys.Vec3[] endsOf(LaserBeam beam) {
        net.minecraft.world.phys.Vec3[] ends = beam.ends();
        if (beam.aim() == null && !beam.jumps() && !hasEmitter(beam.face()) && part(beam.face()) != Part.CAVITY) ends[0] = worldPosition.getCenter();
        return ends;
    }

    // ---- life ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            powered = level.hasNeighborSignal(worldPosition);
            joined = false;
            join();
        }
    }

    @Override
    public void setRemoved() {
        // Its model data goes with it: one put down here next starts afresh.
        requestModelDataUpdate();
        super.setRemoved();
        if (level != null) LaserNetworks.remove(level, this);
        joined = false;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) LaserNetworks.remove(level, this);
        joined = false;
    }

    /** Forgets everything it holds, without dropping it: broken in creative. */
    public void clearContents() {
        core = Core.NONE;
        parts.clear();
        lenses.clear();
        receptacles.clear();
        cavities.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) for (ItemStack stack : contents()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        // Broken, its cavities let all they held go, as flux.
        if (level instanceof net.minecraft.server.level.ServerLevel server) for (CavityPlate cavity : cavities.values()) cavity.releaseAll(server);
        clearContents();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Core", core.ordinal());
        for (Map.Entry<Direction, Part> entry : parts.entrySet()) out.putInt("Part_" + entry.getKey().getSerializedName(), entry.getValue().ordinal());
        out.putInt("Mode", mode.ordinal());
        for (Direction face : Direction.values()) {
            ItemStack lens = lens(face);
            if (!lens.isEmpty()) out.store("Lens_" + face.getSerializedName(), ItemStack.CODEC, lens);
            RelayMode own = modes.get(face);
            if (own != null) out.putInt("Mode_" + face.getSerializedName(), own.ordinal());
            Receptacle receptacle = receptacles.get(face);
            if (receptacle != null) receptacle.save(out.child("Receptacle_" + face.getSerializedName()));
            CavityPlate cavity = cavities.get(face);
            if (cavity != null) {
                ValueOutput child = out.child("Cavity_" + face.getSerializedName());
                cavity.save(child);
                cavity.saveLook(child);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        core = Core.byOrdinal(in.getIntOr("Core", 0));
        parts.clear();
        lenses.clear();
        modes.clear();
        mode = RelayMode.byOrdinal(in.getIntOr("Mode", 0));
        for (Direction face : Direction.values()) {
            Part part = Part.byOrdinal(in.getIntOr("Part_" + face.getSerializedName(), 0));
            if (part != Part.NONE) parts.put(face, part);
            in.read("Lens_" + face.getSerializedName(), ItemStack.CODEC).ifPresent(stack -> lenses.put(face, stack));
            int own = in.getIntOr("Mode_" + face.getSerializedName(), -1);
            if (own >= 0) modes.put(face, RelayMode.byOrdinal(own));
            // Kept, not made again, so an open screen goes on showing the one there.
            if (part == Part.RECEPTACLE) {
                Receptacle receptacle = receptacles.computeIfAbsent(face, side -> new Receptacle(this, side));
                receptacle.load(in.childOrEmpty("Receptacle_" + face.getSerializedName()));
            } else {
                receptacles.remove(face);
            }
            if (part == Part.CAVITY) {
                cavities.computeIfAbsent(face, side -> new CavityPlate(this, side)).load(in.childOrEmpty("Cavity_" + face.getSerializedName()));
            } else {
                cavities.remove(face);
            }
        }
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    /** Set up as a Laser Relay was: a Laser Focus, a stand on {@code mount}, emitters on {@code outputs}, its lenses and redstone. */
    public void becomeRelay(Direction mount, Set<Direction> outputs, Map<Direction, ItemStack> lenses, RelayMode mode, Map<Direction, RelayMode> modes) {
        core = Core.LASER;
        parts.clear();
        parts.put(mount, Part.STAND);
        for (Direction face : outputs) if (face != mount) parts.put(face, Part.EMITTER);
        this.lenses.clear();
        lenses.forEach((face, lens) -> {
            if (parts.get(face) == Part.EMITTER && !lens.isEmpty()) this.lenses.put(face, lens.copy());
        });
        this.mode = mode;
        this.modes.clear();
        modes.forEach((face, own) -> {
            if (parts.get(face) == Part.EMITTER) this.modes.put(face, own);
        });
        changed();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
    }

    /** On the client: the look its model was last asked to draw (see {@link #look}), or none yet. */
    @Nullable
    private List<Object> drawn;

    /** What it holds, and its lenses: its model is drawn from them, so its chunk is built again when they change. */
    private List<Object> look() {
        List<Object> look = new ArrayList<>();
        look.add(layout());
        for (Direction face : Direction.values()) look.add(lens(face).getItem());
        return look;
    }

    /**
     * On the client, every tick: if what it holds isn't what its model was last asked to draw (new data arrived, or it
     * was put down or broken and placed again), its model data is refreshed and its chunk built again. Checked each tick
     * rather than only when data arrives, so no timing between the block, its data and its chunk can leave it unseen.
     */
    public void clientTick() {
        if (level == null || !level.isClientSide()) return;
        List<Object> look = look();
        if (look.equals(drawn)) return;
        drawn = look;
        requestModelDataUpdate();
        // Built again off the main thread: a chunk full of them loading mustn't stall a frame.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) {
        super.onDataPacket(connection, input);
        clientTick();
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        requestModelDataUpdate();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What's passing through it, or that it holds no focus. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        if (core == Core.NONE) return Component.translatable("message.quantimium.laser_focus.empty").withStyle(ChatFormatting.GRAY);
        // A cavity: what it holds.
        for (CavityPlate cavity : cavities.values()) {
            if (!cavity.paired()) continue;
            return Component.translatable("message.quantimium.cavity.holds", String.format(java.util.Locale.ROOT, "%,d", cavity.stored()),
                    String.format(java.util.Locale.ROOT, "%,d", cavity.capacity()), cavity.gap()).withStyle(ChatFormatting.DARK_AQUA);
        }
        long passing = 0;
        for (LaserBeam beam : shownBeams) passing += beam.flow();
        MutableComponent line = passing <= 0 ? Component.translatable("message.quantimium.laser.mirror.dark").withStyle(ChatFormatting.GRAY)
                : Component.translatable("message.quantimium.laser.mirror.passing", String.format(java.util.Locale.ROOT, "%,d", passing))
                        .withStyle(ChatFormatting.DARK_AQUA);
        if (mode != RelayMode.IGNORED) line.append(Component.literal("; ").append(mode.label()).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
