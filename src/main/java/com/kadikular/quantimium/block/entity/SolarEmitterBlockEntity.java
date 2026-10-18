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
 * at noon, less at dawn, dusk and in rain, none at night or under a roof), and holds none of it. Light that
 * reaches it, from anything (another Solar Emitter, a Laser Emitter, by mirrors or not), joins its own and
 * leaves by its emitter; but no more than {@link LaserTuning#SOLAR_CHAIN_MAX} FE/t leaves it in all, what it
 * makes included. It looks at the sky once a second.
 */
public class SolarEmitterBlockEntity extends BlockEntity implements LaserNode, LaserBeamView, FluxMeterReadout {

    /** The sun counts as full this many times over: up to about 48 degrees either side of noon. */
    static final float SUN_FULL = 1.5f;

    /** How bright the sky is to it, 0 to 15, as last looked: what it makes, and how its focus glows. */
    private int sun;
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
        if (was != solar.sun) level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /**
     * How bright the sky is above it, 0 to 15, the way a daylight detector reads it; nothing under a roof,
     * however high (glass lets the sky through).
     */
    private static int skyLight(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, above) < 15) return 0;
        int target = level.getEffectiveSkyBrightness(above);
        if (target > 0) {
            float sunAngle = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, above)
                    * (float) (Math.PI / 180.0);
            float offset = sunAngle < (float) Math.PI ? 0.0f : (float) (Math.PI * 2);
            sunAngle += (offset - sunAngle) * 0.2f;
            // Full through the middle of the day, as panels are: it only dims in the morning and evening.
            target = Math.round(target * Math.min(1.0f, SUN_FULL * Mth.cos(sunAngle)));
        }
        return Mth.clamp(target, 0, 15);
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

    /** It passes on light that reaches it, as a relay, and gives its own too. */
    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public boolean generates() {
        return true;
    }

    /** What it makes this tick, for the network to draw. */
    @Override
    public long available() {
        return making();
    }

    /** What it lets through from others: the rest of its limit, after its own. */
    @Override
    public long passLimit() {
        return Math.max(0, LaserTuning.SOLAR_CHAIN_MAX - making());
    }

    /** It's a source, not a mirror: what passes isn't taxed. */
    @Override
    public double tax() {
        return 0;
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
        long passing = level instanceof net.minecraft.server.level.ServerLevel server ? LaserNetworks.of(server).throughput(worldPosition) : 0;
        return Component.translatable("message.quantimium.solar_emitter.giving", making(), passing, LaserTuning.SOLAR_CHAIN_MAX)
                .withStyle(making() > 0 || passing > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }
}
