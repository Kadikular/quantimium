package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * A Laser Mirror's light: it turns light between its two sides, both ways, as a mirror would: what comes
 * in by one leaves by the other, refocused, so its range starts again; flat, it goes back the way it came.
 * A Laser Splitter's half-silvered plate sends light both straight on and turned, by any of its four ports.
 * Light from anywhere else stops on it. Off while a redstone signal reaches it.
 */
public class LaserMirrorBlockEntity extends BlockEntity implements LaserNode, LaserBeamView {

    private List<LaserBeam> shownBeams = List.of();

    public LaserMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_MIRROR_BE.get(), pos, state);
    }

    private Direction input() {
        return getBlockState().getValue(LaserMirrorBlock.INPUT);
    }

    private Direction output() {
        return getBlockState().getValue(LaserMirrorBlock.OUTPUT);
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    /** The sides light leaves by, as the network last found it arriving: what its beams are, so they're checked against it. */
    private Set<Direction> passing = Set.of();

    @Override
    public Set<Direction> outputs() {
        return passing;
    }

    /**
     * Light coming in by a port turns out of its partner (a Mirror's two sides to each other); through a Beam
     * Splitter it also goes straight on. Any other light stops here. With none, every port: where light would go.
     */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        Set<Direction> ports = ports();
        if (arriving.isEmpty()) {
            passing = ports;
            return passing;
        }
        Set<Direction> out = java.util.EnumSet.noneOf(Direction.class);
        for (Direction travel : arriving) {
            Direction in = travel.getOpposite();
            if (!ports.contains(in)) continue;
            if (ports.contains(turned(in))) out.add(turned(in));
            if (splits() && ports.contains(travel)) out.add(travel);
        }
        passing = out;
        return out;
    }

    /** Whether its plate is half-silvered: light goes straight through as well as turning. */
    private boolean splits() {
        return getBlockState().getBlock() instanceof LaserMirrorBlock mirror && mirror.splits();
    }

    /**
     * The faces light gets in and out by: a Mirror's two sides; a Laser Splitter's four, its two sides and the
     * faces across from them, but for one facing its mount.
     */
    private Set<Direction> ports() {
        Set<Direction> ports = java.util.EnumSet.of(input(), output());
        if (splits()) {
            ports.add(input().getOpposite());
            ports.add(output().getOpposite());
            ports.remove(getBlockState().getValue(LaserMirrorBlock.MOUNT));
        }
        return ports;
    }

    /** The face light in by {@code face} turns out of, off the plate: one side to the other, and across the back likewise. */
    private Direction turned(Direction face) {
        if (face == input()) return output();
        if (face == output()) return input();
        if (face == input().getOpposite()) return output().getOpposite();
        return input().getOpposite();
    }

    /** Only light coming in by one of its ports; the rest hits the back or edge of the plate. */
    @Override
    public boolean accepts(Direction travel) {
        return ports().contains(travel.getOpposite());
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
    public boolean enabled() {
        return !getBlockState().getValue(LaserMirrorBlock.POWERED);
    }

    /** Its light leaves from the plate, not its face. */
    @Override
    public Vec3[] endsOf(LaserBeam beam) {
        Vec3[] ends = beam.ends();
        Vec3 from = getBlockState().getBlock() instanceof LaserMirrorBlock mirror ? mirror.beamPoint(getBlockState(), worldPosition) : ends[0];
        return new Vec3[] {from, ends[1]};
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
}
