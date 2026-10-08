package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.PhotodetectorBlock;
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
 * A photodetector: light passes through it, out of its face, untaxed, and it reads what passes. It
 * gives a redstone signal, and a comparator reading, from 1 for a trickle to 15 for a flood (one step
 * each time the flow grows tenfold), and lights up while light passes.
 */
public class PhotodetectorBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    private List<LaserBeam> shownBeams = List.of();
    private int signal;

    public PhotodetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHOTODETECTOR_BE.get(), pos, state);
    }

    public int signal() {
        return signal;
    }

    public long passing() {
        long total = 0;
        for (LaserBeam beam : shownBeams) total += beam.flow();
        return total;
    }

    /** 0 for no light; else 1 to 15, two steps each tenfold, so 1 FE/t reads 1 and a million reads 13. */
    public static int signalFor(long flow) {
        if (flow <= 0) return 0;
        return Math.min(15, 1 + 2 * (int) Math.floor(Math.log10(flow)));
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of(getBlockState().getValue(PhotodetectorBlock.FACING));
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    public boolean relays() {
        return true;
    }

    /** It only watches: it takes nothing of what passes. */
    @Override
    public double tax() {
        return 0;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        int now = signalFor(passing());
        if (now != signal && level != null && !level.isClientSide()) {
            signal = now;
            setChanged();
            BlockState state = getBlockState();
            if (state.getValue(PhotodetectorBlock.LIT) != (now > 0)) {
                level.setBlock(worldPosition, state.setValue(PhotodetectorBlock.LIT, now > 0), Block.UPDATE_ALL);
            }
            level.updateNeighborsAt(worldPosition, state.getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
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
        if (level != null && !level.isClientSide()) LaserNetworks.add(level, this);
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
        signal = in.getIntOr("Signal", 0);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Signal", signal);
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

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        long passing = passing();
        if (passing <= 0) return Component.translatable("message.quantimium.photodetector.dark").withStyle(ChatFormatting.GRAY);
        return Component.translatable("message.quantimium.photodetector.reading", String.format(java.util.Locale.ROOT, "%,d", passing), signal)
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}
