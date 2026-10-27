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
        /** An emitter: light leaves by it. Light also gets in by it. */
        EMITTER,
        /** A plain cap: light neither gets in nor out by it. */
        COVER;

        /** Whether light gets in and out by a face with this on it. */
        public boolean open() {
            return this == NONE || this == EMITTER;
        }

        /** The item it is, or nothing. */
        public ItemStack item() {
            Item item = switch (this) {
                case NONE -> null;
                case STAND -> ModItems.FOCUS_STAND.get();
                case EMITTER -> ModItems.FOCUS_EMITTER.get();
                case COVER -> ModItems.FOCUS_COVER.get();
            };
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        static Part byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
        }
    }

    /** What its model draws: its focus and each face's part, packed as {@link #layout()} says. */
    public static final ModelProperty<Integer> LAYOUT = new ModelProperty<>();

    private Core core = Core.NONE;
    private final Map<Direction, Part> parts = new EnumMap<>(Direction.class);
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

    /** Its focus and its faces, packed: 2 bits for the focus, then 3 for each face, by its 3D data value. */
    public int layout() {
        int packed = core.ordinal();
        for (Direction face : Direction.values()) packed |= part(face).ordinal() << (2 + 3 * face.get3DDataValue());
        return packed;
    }

    public static Core coreOf(int layout) {
        return Core.byOrdinal(layout & 3);
    }

    public static Part partOf(int layout, Direction face) {
        return Part.byOrdinal(layout >> (2 + 3 * face.get3DDataValue()) & 7);
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

    /** Light gets in by any open face, with a focus to take it. */
    @Override
    public boolean accepts(Direction travel) {
        return core != Core.NONE && part(travel.getOpposite()).open();
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

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    /** Light leaves an Emitter at its tip; light passing straight through a face with none leaves from the focus. */
    @Override
    public net.minecraft.world.phys.Vec3[] endsOf(LaserBeam beam) {
        net.minecraft.world.phys.Vec3[] ends = beam.ends();
        if (beam.aim() == null && !beam.jumps() && !hasEmitter(beam.face())) ends[0] = worldPosition.getCenter();
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

    /** Broken, it drops everything it holds. */
    /** Forgets everything it holds, without dropping it: broken in creative. */
    public void clearContents() {
        core = Core.NONE;
        parts.clear();
        lenses.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) for (ItemStack stack : contents()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        core = Core.NONE;
        parts.clear();
        lenses.clear();
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
        long passing = 0;
        for (LaserBeam beam : shownBeams) passing += beam.flow();
        MutableComponent line = passing <= 0 ? Component.translatable("message.quantimium.laser.mirror.dark").withStyle(ChatFormatting.GRAY)
                : Component.translatable("message.quantimium.laser.mirror.passing", String.format(java.util.Locale.ROOT, "%,d", passing))
                        .withStyle(ChatFormatting.DARK_AQUA);
        if (mode != RelayMode.IGNORED) line.append(Component.literal("; ").append(mode.label()).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
