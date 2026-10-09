package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A laser mirror: every beam that reaches it goes on out of its face, refocused, so its range starts
 * again. It takes {@link LaserTuning#HOP_TAX} of what passes, as flux.
 */
public class LaserMirrorBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    private List<LaserBeam> shownBeams = List.of();
    private com.kadikular.quantimium.laser.RelayMode mode = com.kadikular.quantimium.laser.RelayMode.IGNORED;
    /** Whether a redstone signal reaches it, as last told. */
    private boolean powered;
    /** In pulse mode: whether it's letting light through, and since when. */
    private boolean pulseOpen;
    private long pulseAt;

    public LaserMirrorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.LASER_MIRROR_BE.get(), pos, state);
    }

    /** For blocks built on the mirror: the Splitter, the Isolator. */
    protected LaserMirrorBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public com.kadikular.quantimium.laser.RelayMode mode() {
        return mode;
    }

    /** Steps to the next way of listening to redstone. */
    public void cycleMode() {
        mode = mode.next();
        pulseOpen = false;
        setChanged();
        if (level != null) LaserNetworks.changed(level);
    }

    /** The block next to it changed: a signal arriving opens a pulse. */
    public void onSignal(boolean signal) {
        if (signal && !powered && mode == com.kadikular.quantimium.laser.RelayMode.PULSE && level != null) {
            pulseOpen = true;
            pulseAt = level.getGameTime();
        }
        powered = signal;
    }

    @Override
    public boolean enabled() {
        return switch (mode) {
            case IGNORED -> true;
            case WITH_SIGNAL -> powered;
            case WITHOUT_SIGNAL -> !powered;
            case PULSE -> pulseOpen && level != null && level.getGameTime() - pulseAt <= com.kadikular.quantimium.laser.RelayMode.PULSE_TICKS;
        };
    }

    @Override
    public boolean switches() {
        return mode != com.kadikular.quantimium.laser.RelayMode.IGNORED;
    }

    @Override
    public void pulseDone() {
        pulseOpen = false;
    }

    public Direction facing() {
        return getBlockState().getValue(LaserMirrorBlock.FACING);
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of(facing());
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
            LaserNetworks.add(level, this);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Mode", mode.ordinal());
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
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        mode = com.kadikular.quantimium.laser.RelayMode.byOrdinal(in.getIntOr("Mode", 0));
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
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
        if (mode != com.kadikular.quantimium.laser.RelayMode.IGNORED) line.append(Component.literal("; ").append(mode.label()).withStyle(ChatFormatting.GRAY));
        return line;
    }
}
