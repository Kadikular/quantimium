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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * An Area Prism: light that reaches it spreads its actions over an area, set with the Laser Linker in
 * area mode: Harvest cuts each block there in turn, Damage hurts and Pickup gathers what's in it,
 * Placement fills each free space resting on something. The light goes no further, and work in an
 * area costs {@link LaserTuning#AREA_COST} times as much. A Filter lens says what counts.
 */
public class AreaPrismBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    public enum Placed { SET, TOO_BIG, TOO_FAR }

    @Nullable
    private BoundingBox area;
    /** Beams to what its actions are working on, as the network last showed them. */
    private List<LaserBeam> shownBeams = List.of();

    public AreaPrismBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AREA_PRISM_BE.get(), pos, state);
    }

    /** Gives it the area between two corners, if it's small enough and near enough. */
    public Placed setArea(BlockPos a, BlockPos b) {
        BoundingBox box = BoundingBox.fromCorners(a, b);
        if (box.getXSpan() > LaserTuning.AREA_MAX || box.getYSpan() > LaserTuning.AREA_MAX || box.getZSpan() > LaserTuning.AREA_MAX) {
            return Placed.TOO_BIG;
        }
        if (box.getCenter().getCenter().distanceTo(worldPosition.getCenter()) > LaserTuning.AREA_REACH) return Placed.TOO_FAR;
        area = box;
        changed();
        return Placed.SET;
    }

    public void clearArea() {
        area = null;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    @Nullable
    public BoundingBox area() {
        return area;
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
        if (!same && level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
        if (area != null) out.store("Area", BoundingBox.CODEC, area);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        area = in.read("Area", BoundingBox.CODEC).orElse(null);
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

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        if (area == null) return Component.translatable("message.quantimium.area_prism.none").withStyle(ChatFormatting.GRAY);
        return Component.translatable("message.quantimium.area_prism.area", area.getXSpan(), area.getYSpan(), area.getZSpan())
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}
