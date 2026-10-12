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
 * A Laser Splitter's half-silvered plate splits light from its input side, on and turned, and joins light
 * from behind its turn side to what it turns.
 * Light from anywhere else stops on it. Off while a redstone signal reaches it.
 */
public class LaserMirrorBlockEntity extends BlockEntity implements LaserNode, LaserBeamView, com.kadikular.quantimium.flux.FluxMeterReadout {

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
     * A Mirror turns light between its two sides. A Laser Splitter splits light in by its input side, on and out
     * of its turn side; light from behind its turn side passes straight through and out of it; both at once leave
     * by the turn side only, joined. Any other light stops here. With none, where light would go.
     */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        Direction mount = getBlockState().getValue(LaserMirrorBlock.MOUNT);
        Set<Direction> out = java.util.EnumSet.noneOf(Direction.class);
        if (!splits()) {
            if (arriving.isEmpty() || arriving.contains(input().getOpposite())) out.add(output());
            if (arriving.isEmpty() || arriving.contains(output().getOpposite())) out.add(input());
        } else {
            boolean in = arriving.contains(input().getOpposite()) && input() != mount;
            boolean behind = arriving.contains(output()) && output().getOpposite() != mount;
            if (in || behind || arriving.isEmpty()) out.add(output());
            // Split on only while nothing joins it from behind: joined light leaves by the turn side alone.
            if ((in && !behind) || arriving.isEmpty()) out.add(input().getOpposite());
            out.remove(mount);
        }
        passing = out;
        return out;
    }

    /** Whether its plate is half-silvered: light goes straight through as well as turning. */
    private boolean splits() {
        return getBlockState().getBlock() instanceof LaserMirrorBlock mirror && mirror.splits();
    }

    /**
     * The faces light gets in by. A Mirror: its two sides. A Laser Splitter: its input side, and the face behind
     * its turn side; not one facing its mount.
     */
    private Set<Direction> entries() {
        if (!splits()) return java.util.EnumSet.of(input(), output());
        Set<Direction> entries = java.util.EnumSet.of(input(), output().getOpposite());
        entries.remove(getBlockState().getValue(LaserMirrorBlock.MOUNT));
        return entries;
    }

    /** Only light coming in by one of its entries; the rest hits the back or edge of the plate. */
    @Override
    public boolean accepts(Direction travel) {
        return entries().contains(travel.getOpposite());
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

    /** Which sides are which: a Mirror's two, a Splitter's input and turn sides; what's passing. */
    @Override
    public net.minecraft.network.chat.MutableComponent fluxMeterLine() {
        net.minecraft.network.chat.Component in = net.minecraft.network.chat.Component.translatable("direction.quantimium." + input().getSerializedName());
        net.minecraft.network.chat.Component out = net.minecraft.network.chat.Component.translatable("direction.quantimium." + output().getSerializedName());
        String key = splits() ? "message.quantimium.laser_splitter.sides" : input() == output() ? "message.quantimium.laser_mirror.flat_sides"
                : "message.quantimium.laser_mirror.sides";
        long passing = level instanceof net.minecraft.server.level.ServerLevel server ? LaserNetworks.of(server).throughput(worldPosition) : 0;
        Object[] values = input() == output() && !splits() ? new Object[] {in, passing} : new Object[] {in, out, passing};
        return net.minecraft.network.chat.Component.translatable(key, values)
                .withStyle(enabled() ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY);
    }
}
