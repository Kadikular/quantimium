package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.SolarEmitterBlock;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Solar Emitter's light. It makes what the sky gives it, as a daylight detector reads the sky (full sun
 * at noon, less at dawn, dusk and in rain, none at night or under a roof), and holds none of it. Where its
 * light shines into another Solar Emitter, it gives nothing to the network itself: its light joins that
 * one's, and the last of a chain gives the whole chain's, up to {@link LaserTuning#SOLAR_CHAIN_MAX} FE/t.
 * It looks at the sky, and at what feeds it, once a second.
 */
public class SolarEmitterBlockEntity extends BlockEntity implements LaserNode, LaserBeamView, FluxMeterReadout {

    /** How bright the sky is to it, 0 to 15, as last looked: what it makes, and how its focus glows. */
    private int sun;
    /** Whether its light shines into another Solar Emitter, which gives it on. */
    private boolean feeds;
    /** What it gives the network a tick: its own, and its chain's, for the last of a chain; nothing for one that feeds another. */
    private long giving;
    private List<LaserBeam> shownBeams = List.of();

    public SolarEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_EMITTER_BE.get(), pos, state);
    }

    private Direction output() {
        return getBlockState().getValue(SolarEmitterBlock.OUTPUT);
    }

    /** How bright the sky is to it, 0 to 15. */
    public int sun() {
        return sun;
    }

    /** What it makes itself a tick, in this light. */
    long making() {
        return (long) LaserTuning.SOLAR_FE * sun / 15;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarEmitterBlockEntity solar) {
        if (level.getGameTime() % 20 != Math.floorMod(pos.hashCode(), 20)) return;
        int was = solar.sun;
        solar.sun = skyLight(level, pos);
        solar.feeds = solar.into(level) != null;
        solar.giving = solar.feeds ? 0 : Math.min(LaserTuning.SOLAR_CHAIN_MAX, solar.chain(level, new HashSet<>()));
        boolean day = solar.sun > 0;
        if (state.getValue(SolarEmitterBlock.DAY) != day) level.setBlock(pos, state.setValue(SolarEmitterBlock.DAY, day), Block.UPDATE_CLIENTS);
        else if (was != solar.sun) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** How bright the sky is above it, 0 to 15, the way a daylight detector reads it. */
    private static int skyLight(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        int target = level.getEffectiveSkyBrightness(above);
        if (target > 0) {
            float sunAngle = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, above)
                    * (float) (Math.PI / 180.0);
            float offset = sunAngle < (float) Math.PI ? 0.0f : (float) (Math.PI * 2);
            sunAngle += (offset - sunAngle) * 0.2f;
            target = Math.round(target * Mth.cos(sunAngle));
        }
        return Mth.clamp(target, 0, 15);
    }

    /** The Solar Emitter its light shines straight into, within its reach, if it does. */
    @Nullable
    private SolarEmitterBlockEntity into(Level level) {
        BlockPos.MutableBlockPos at = worldPosition.mutable();
        for (int step = 1; step <= LaserTuning.SOLAR_RANGE; step++) {
            at.move(output());
            if (!level.isLoaded(at)) return null;
            if (level.getBlockEntity(at) instanceof SolarEmitterBlockEntity other) return other.output() == output().getOpposite() ? null : other;
            BlockState there = level.getBlockState(at);
            if (!LaserBeam.passes(level, at, there)) return null;
        }
        return null;
    }

    /** What it and every Solar Emitter shining into it, and into those, make a tick. */
    private long chain(Level level, Set<BlockPos> seen) {
        if (!seen.add(worldPosition)) return 0;
        long total = making();
        for (Direction from : Direction.values()) {
            if (from == output() || from == Direction.UP) continue;
            BlockPos.MutableBlockPos at = worldPosition.mutable();
            for (int step = 1; step <= LaserTuning.SOLAR_RANGE; step++) {
                at.move(from);
                if (!level.isLoaded(at)) break;
                if (level.getBlockEntity(at) instanceof SolarEmitterBlockEntity other) {
                    if (other.output() == from.getOpposite()) total += other.chain(level, seen);
                    break;
                }
                if (!LaserBeam.passes(level, at, level.getBlockState(at))) break;
            }
        }
        return total;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of(output());
    }

    @Override
    public int range() {
        return LaserTuning.SOLAR_RANGE;
    }

    @Override
    public boolean relays() {
        return false;
    }

    @Override
    public long available() {
        return giving;
    }

    /** Its beam as it looks: one feeding another Solar Emitter carries what it makes, though the network draws nothing through it. */
    @Override
    public void showBeams(List<LaserBeam> beams) {
        if (feeds && making() > 0) beams = beams.stream().map(beam -> beam.withFlow(making()).withLive(true)).toList();
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
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        sun = in.getIntOr("Sun", 0);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putInt("Sun", sun);
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
        if (feeds) return Component.translatable("message.quantimium.solar_emitter.feeds", making()).withStyle(ChatFormatting.AQUA);
        return Component.translatable("message.quantimium.solar_emitter.giving", giving, making()).withStyle(making() > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }
}
