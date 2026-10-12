package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserRelayBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModDataComponents;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Laser Relay: every beam that reaches its focus, from any side but its base, goes on out of each of its
 * emitters, refocused, so its range starts again. It takes {@link LaserTuning#HOP_TAX} of what passes, as flux.
 * Each emitter can hold one lens, which only the light leaving by it goes through, and has its own way of
 * listening to the redstone signal reaching the block: always on, on with it, on without it, or a pulse.
 *
 * <p>The Isolator, built on it, has no base: its faces are set as the Relay's were, all with one redstone way.
 */
public class LaserRelayBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    /** The faces light leaves by: where its emitters are. */
    private final Set<Direction> outputs = EnumSet.noneOf(Direction.class);
    /** The lens in each emitter that has one. */
    private final Map<Direction, ItemStack> lenses = new EnumMap<>(Direction.class);
    /** How each emitter that's been set listens to redstone; the rest follow {@link #mode}. */
    private final Map<Direction, RelayMode> modes = new EnumMap<>(Direction.class);
    /** For emitters on a pulse: since when each is letting light through. */
    private final Map<Direction, Long> pulses = new EnumMap<>(Direction.class);

    private List<LaserBeam> shownBeams = List.of();
    /** How its emitters listen to redstone, but for those set one by one. */
    private RelayMode mode = RelayMode.IGNORED;
    /** Whether a redstone signal reaches it, as last told. */
    private boolean powered;

    public LaserRelayBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LASER_RELAY_BE.get(), pos, state);
    }

    /** For blocks built on the relay: the Isolator. */
    protected LaserRelayBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (state.hasProperty(LaserRelayBlock.FACING)) outputs.add(state.getValue(LaserRelayBlock.FACING));
    }

    // ---- its base and emitters ----

    /** Whether it stands on a base (a Relay), so light can't come in or go out that side. */
    private boolean mounted() {
        return getBlockState().getBlock() instanceof LaserRelayBlock relay && relay.mounted() && getBlockState().hasProperty(LaserRelayBlock.MOUNT);
    }

    @Nullable
    private Direction mount() {
        return mounted() ? getBlockState().getValue(LaserRelayBlock.MOUNT) : null;
    }

    public Direction facing() {
        return getBlockState().getValue(LaserRelayBlock.FACING);
    }

    /** Puts an emitter on {@code face}, if there isn't one and it isn't the base. Whether it did. */
    public boolean addEmitter(Direction face) {
        if (face == mount() || !outputs.add(face)) return false;
        outputsChanged();
        return true;
    }

    /** Takes the emitter off {@code face}, dropping its lens. Whether there was one. */
    public boolean removeEmitter(Direction face) {
        if (!outputs.remove(face)) return false;
        ItemStack lens = lenses.remove(face);
        modes.remove(face);
        pulses.remove(face);
        if (lens != null && !lens.isEmpty() && level != null) {
            BlockPos at = worldPosition.relative(face);
            Containers.dropItemStack(level, at.getX(), at.getY(), at.getZ(), lens);
        }
        outputsChanged();
        return true;
    }

    /** Turns light out of {@code face} on or off; one output always stays (the Isolator's way). Whether it changed. */
    public boolean toggleOutput(Direction face) {
        if (outputs.contains(face)) {
            if (outputs.size() == 1) return false;
            return removeEmitter(face);
        }
        return addEmitter(face);
    }

    private void outputsChanged() {
        setChanged();
        if (level == null) return;
        showOutputs();
        LaserNetworks.changed(level);
        if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The block shows which faces light leaves by. */
    private void showOutputs() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(LaserRelayBlock.OUTPUTS.get(Direction.NORTH))) return;
        BlockState shown = state;
        for (Direction face : Direction.values()) shown = shown.setValue(LaserRelayBlock.OUTPUTS.get(face), outputs.contains(face));
        if (shown != state) level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
    }

    /** The lens in the emitter on {@code face}: empty for none. */
    public ItemStack lens(Direction face) {
        return lenses.getOrDefault(face, ItemStack.EMPTY);
    }

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
        return List.of(new LensUse(lens.lens(), stack.get(ModDataComponents.LENS_FILTER.get()),
                Boolean.TRUE.equals(stack.get(ModDataComponents.INTERACT_HITS.get()))));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (ItemStack lens : lenses.values()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), lens);
        }
        lenses.clear();
    }

    // ---- redstone ----

    /** How its emitters listen to redstone, but for those set one by one. */
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

    /** How the emitter on {@code face} listens to redstone. */
    public RelayMode modeOf(Direction face) {
        return modes.getOrDefault(face, mode);
    }

    /** Steps the emitter on {@code face} on to its next way of listening to redstone. */
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
            for (Direction face : outputs) if (modeOf(face) == RelayMode.PULSE) pulses.put(face, level.getGameTime());
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

    /** On while any of its emitters is. */
    @Override
    public boolean enabled() {
        for (Direction face : outputs) if (on(face)) return true;
        return false;
    }

    @Override
    public boolean switches() {
        if (mode != RelayMode.IGNORED) return true;
        for (Direction face : outputs) if (modeOf(face) != RelayMode.IGNORED) return true;
        return false;
    }

    /** Which of its emitters are on: a change in any is a change in its light. */
    @Override
    public int switchState() {
        int on = 0;
        for (Direction face : outputs) if (on(face)) on |= 1 << face.get3DDataValue();
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

    @Override
    public Set<Direction> outputs() {
        return outputs;
    }

    /** Out of each emitter that's on; with no light coming, all of them: where light would go. */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        if (arriving.isEmpty()) return outputs;
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction face : outputs) if (on(face)) out.add(face);
        return out;
    }

    /** Light gets in by any side but its base. */
    @Override
    public boolean accepts(Direction travel) {
        return travel.getOpposite() != mount();
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            powered = level.hasNeighborSignal(worldPosition);
            // A face that's become its base (a Relay from before it had one) loses its emitter.
            if (mount() != null) outputs.remove(mount());
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

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Mode", mode.ordinal());
        int mask = 0;
        for (Direction face : outputs) mask |= 1 << face.get3DDataValue();
        out.putInt("Outputs", mask);
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
        mode = RelayMode.byOrdinal(in.getIntOr("Mode", 0));
        int mask = in.getIntOr("Outputs", -1);
        if (mask >= 0) {
            outputs.clear();
            for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputs.add(face);
        }
        lenses.clear();
        modes.clear();
        for (Direction face : Direction.values()) {
            in.read("Lens_" + face.getSerializedName(), ItemStack.CODEC).ifPresent(stack -> lenses.put(face, stack));
            int own = in.getIntOr("Mode_" + face.getSerializedName(), -1);
            if (own >= 0) modes.put(face, RelayMode.byOrdinal(own));
        }
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
    }

    /** What lenses it holds: its model's colours come from them. */
    private java.util.List<ItemStack> lensSignature() {
        java.util.List<ItemStack> sig = new java.util.ArrayList<>();
        for (Direction face : Direction.values()) sig.add(lens(face).copy());
        return sig;
    }

    /** New lenses on the client: its model is baked with their colours, so its chunk is built again. */
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) {
        java.util.List<ItemStack> before = lensSignature();
        super.onDataPacket(connection, input);
        java.util.List<ItemStack> after = lensSignature();
        boolean same = before.size() == after.size();
        for (int i = 0; same && i < before.size(); i++) same = ItemStack.matches(before.get(i), after.get(i));
        if (!same && level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What's passing through it. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        long passing = 0;
        for (LaserBeam beam : shownBeams) passing += beam.flow();
        MutableComponent line = passing <= 0 ? Component.translatable("message.quantimium.laser.mirror.dark").withStyle(ChatFormatting.GRAY)
                : Component.translatable("message.quantimium.laser.mirror.passing", String.format(java.util.Locale.ROOT, "%,d", passing))
                        .withStyle(ChatFormatting.DARK_AQUA);
        if (mode != RelayMode.IGNORED) line.append(Component.literal("; ").append(mode.label()).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
