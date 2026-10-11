package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.RiftLensBlock;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Rift Lens with laser light through it: a tear in the world. Light along its axis goes straight on
 * through, into the mirror, or out of it if it was already there. Mirrored light acts only on what's in
 * the mirror (Anomalite, the Veiled), charges nothing of the real world, and shows only through a Mirror
 * Lens. It doesn't refocus the light, takes nothing for it, and its tear opens while lit light passes.
 */
public class RiftLensBlockEntity extends BlockEntity implements LaserNode, LaserBeamView, FluxMeterReadout {

    /** The ways light goes on out, as the network last found it arriving. */
    private Set<Direction> passing = EnumSet.noneOf(Direction.class);
    private List<LaserBeam> shownBeams = List.of();
    /** Whether laser light opened its tear (and so may close it). */
    private boolean openedByLight;

    public RiftLensBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT_LENS_BE.get(), pos, state);
    }

    private Direction.Axis axis() {
        return getBlockState().getValue(RiftLensBlock.AXIS);
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return passing;
    }

    /** Light arriving along its axis goes straight on; from the side, it goes nowhere. */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction travel : arriving) if (travel.getAxis() == axis()) out.add(travel);
        passing = out;
        return out;
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
    public boolean refocuses() {
        return false;
    }

    @Override
    public double tax() {
        return 0;
    }

    @Override
    public boolean rifts() {
        return true;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (level == null || level.isClientSide()) return;
        // The tear opens while lit light passes through it; it closes only a tear it opened, not a Harvest Laser's.
        boolean lit = beams.stream().anyMatch(LaserBeam::live);
        BlockState state = getBlockState();
        if (lit && !state.getValue(RiftLensBlock.OPEN)) {
            openedByLight = true;
            level.setBlock(worldPosition, state.setValue(RiftLensBlock.OPEN, true), Block.UPDATE_ALL);
        } else if (!lit && openedByLight) {
            openedByLight = false;
            level.setBlock(worldPosition, state.setValue(RiftLensBlock.OPEN, false), Block.UPDATE_ALL);
        } else if (!same) {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
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
        return Component.translatable(passing.isEmpty() ? "message.quantimium.rift_lens.closed" : "message.quantimium.rift_lens.passing")
                .withStyle(passing.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE);
    }
}
