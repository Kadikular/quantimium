package com.kadikular.quantimium.block.entity;

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
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A laser prism: whatever light reaches it, from any side and any number of beams, leaves along a
 * straight line to each block it's linked to, at any angle, sharing it by what each asks for. It joins
 * and splits, and refocuses as a mirror does, at the same tax. Linked with the Laser Linker.
 */
public class LaserPrismBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    private final List<BlockPos> targets = new ArrayList<>();
    private List<LaserBeam> shownBeams = List.of();

    public LaserPrismBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_PRISM_BE.get(), pos, state);
    }

    public enum Link { ADDED, REMOVED, FULL, TOO_FAR, SELF }

    /** Links {@code target}, or unlinks it if it was linked. */
    public Link toggleLink(BlockPos target) {
        if (target.equals(worldPosition)) return Link.SELF;
        if (targets.remove(target)) {
            changed();
            return Link.REMOVED;
        }
        if (targets.size() >= LaserTuning.PRISM_LINKS) return Link.FULL;
        if (target.getCenter().distanceTo(worldPosition.getCenter()) > LaserTuning.PRISM_RANGE) return Link.TOO_FAR;
        targets.add(target.immutable());
        changed();
        return Link.ADDED;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    public List<BlockPos> targets() {
        return targets;
    }

    @Override
    public int range() {
        return LaserTuning.PRISM_RANGE;
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
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Targets", BlockPos.CODEC.listOf(), List.copyOf(targets));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        targets.clear();
        in.read("Targets", BlockPos.CODEC.listOf()).ifPresent(targets::addAll);
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

    /** How many it's linked to, and what's passing through. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        long passing = 0;
        for (LaserBeam beam : shownBeams) passing += beam.flow();
        return Component.translatable("message.quantimium.laser.prism", targets.size(), LaserTuning.PRISM_LINKS,
                String.format(java.util.Locale.ROOT, "%,d", passing)).withStyle(passing > 0 ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY);
    }
}
