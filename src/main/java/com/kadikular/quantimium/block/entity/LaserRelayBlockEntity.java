package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserRelayBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Laser Relay: every beam that reaches its focus, from any side but its base, goes on out of each of its
 * emitters, refocused, so its range starts again. It takes {@link LaserTuning#HOP_TAX} of what passes, as flux.
 * Each emitter can hold one lens, which only the light leaving by it goes through, and has its own way of
 * listening to the redstone signal reaching the block: always on, on with it, on without it, or a pulse.
 *
 * <p>The Isolator, built on it, has no base: its faces are set as the Relay's were, all with one redstone way.
 */
public class LaserRelayBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, EmitterHolder {

    /** The faces light leaves by: where its emitters are. */
    private final Set<Direction> outputs = EnumSet.noneOf(Direction.class);
    /** Its emitters' lenses, channels and redstone. */
    private final EmitterFaces faces = new EmitterFaces();

    @Override
    public EmitterFaces emitterFaces() {
        return faces;
    }


    private List<LaserBeam> shownBeams = List.of();

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
        ItemStack lens = faces.remove(face);
        if (!lens.isEmpty() && level != null) {
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

    @Override
    public boolean hasEmitter(Direction face) {
        return outputs.contains(face);
    }

    @Override
    public List<LensUse> lensesFor(Direction face) {
        return faces.lensUses(face);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (ItemStack lens : faces.lenses()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), lens);
        }
        faces.clearLenses();
    }

    /**
     * Becomes a Laser Focus with a Stand on its base and an Emitter on each of its outputs, keeping its lenses and how
     * each listens to redstone: what a Laser Relay is now.
     */
    public void becomeFocus() {
        if (level == null || level.isClientSide() || isRemoved()) return;
        Direction mount = mount() != null ? mount() : Direction.DOWN;
        Set<Direction> outputFaces = EnumSet.copyOf(outputs.isEmpty() ? EnumSet.of(facing()) : outputs);
        EmitterFaces kept = new EmitterFaces();
        kept.copyFrom(faces, EnumSet.allOf(Direction.class));
        // Its lenses go with it, not on the floor.
        faces.clearLenses();
        BlockPos at = worldPosition;
        net.minecraft.world.level.Level world = level;
        world.setBlock(at, com.kadikular.quantimium.init.ModBlocks.LASER_FOCUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (world.getBlockEntity(at) instanceof LaserFocusBlockEntity focus) focus.becomeRelay(mount, outputFaces, kept);
    }

    // ---- redstone ----

    /** The block next to it changed: a signal arriving opens a pulse on each emitter waiting for one. */
    public void onSignal(boolean signal) {
        faces.signal(signal, level, outputs);
    }

    /** On while any of its emitters is. */
    @Override
    public boolean enabled() {
        for (Direction face : outputs) if (on(face)) return true;
        return false;
    }

    /** Its switch is only its emitters': an Entangled Mirror drawing on it still gets its light. */
    @Override
    public boolean passesEntangled() {
        return true;
    }

    @Override
    public boolean switches() {
        return faces.switches(outputs);
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
        faces.pulseDone();
    }

    @Override
    public void pulseDone(Direction face) {
        faces.pulseDone(face);
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
            faces.setPowered(level.hasNeighborSignal(worldPosition));
            // A face that's become its base (a Relay from before it had one) loses its emitter.
            if (mount() != null) outputs.remove(mount());
            // A Laser Relay is a Laser Focus now: it becomes one (the Isolator, built on it, stays).
            if (getBlockState().is(com.kadikular.quantimium.init.ModBlocks.LASER_RELAY.get())) {
                level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            }
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
        faces.save(out);
        int mask = 0;
        for (Direction face : outputs) mask |= 1 << face.get3DDataValue();
        out.putInt("Outputs", mask);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        faces.load(in);
        int mask = in.getIntOr("Outputs", -1);
        if (mask >= 0) {
            outputs.clear();
            for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputs.add(face);
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
        if (faces.mode() != RelayMode.IGNORED) line.append(Component.literal("; ").append(faces.mode().label()).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
