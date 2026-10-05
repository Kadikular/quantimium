package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.flux.AnomaliteEnergy;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.kadikular.quantimium.flux.FluxSources;

/**
 * Smoothly drains the adjacent host; random block ticks handle growth and decay.
 *
 * <p>Feeding deliberately ignores containment and the anomaly band. A crystal is damage already
 * done: a hall shields the field, it does not unmake the parasite. Containment instead makes the
 * crystal recede a stage at a time until it is gone. Crystals on Budding Anomalite skip both the
 * drain and the recede — the host pays a flat FE/t to grow them.
 */
public class AnomaliteCrystalBlockEntity extends BlockEntity implements FluxMeterReadout {

    private long lastFedTick = Long.MIN_VALUE;
    private long lastFieldCheck = Long.MIN_VALUE;
    private FluxBand anomalyBand = FluxBand.LOW;
    private boolean contained;
    private boolean cultivated;
    private int lastDrainFe;
    private AnomaliteEnergy.Access host;

    public AnomaliteCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANOMALITE_CRYSTAL_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AnomaliteCrystalBlockEntity crystal) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) return;

        long now = serverLevel.getGameTime();
        Direction outward = state.getValue(AnomaliteCrystalBlock.FACING);
        boolean resurvey = crystal.lastFieldCheck == Long.MIN_VALUE || now - crystal.lastFieldCheck >= 20;
        if (resurvey) {
            crystal.anomalyBand = QuantumFlux.chunkAnomalyBand(serverLevel, pos);
            crystal.contained = QuantumFlux.chunkContained(serverLevel, pos);
            crystal.cultivated = AnomaliteCrystalBlock.isCultivated(serverLevel, pos, outward);
            crystal.lastFieldCheck = now;
            // Through a Mirror Lens a crystal broods in its own ink, more the bigger it is. Visual only.
            FluxSources.show(serverLevel, pos, 0.0, 0.6 * (state.getValue(AnomaliteCrystalBlock.AGE) + 1));
        }
        if (!Config.anomaliteEnabled() || crystal.cultivated) {
            crystal.host = null;
            crystal.lastDrainFe = 0;
            return;
        }

        AnomaliteEnergy.Access energy = crystal.host(serverLevel, pos, outward, resurvey);
        if (energy == null) {
            crystal.lastDrainFe = 0;
            return;
        }

        long requested = Config.anomaliteDrain(
                energy.storedFe(), state.getValue(AnomaliteCrystalBlock.AGE), crystal.anomalyBand);
        if (requested <= 0) {
            crystal.lastDrainFe = 0;
            return;
        }

        long removed = energy.extractFe(requested);
        crystal.lastDrainFe = (int) Math.min(Integer.MAX_VALUE, removed);
        if (removed > 0) crystal.lastFedTick = now;
    }

    /**
     * Discovering a host's energy interface means walking every cross-mod capability and reflective
     * port we know of, which is far too expensive to repeat each tick. The result is held until the
     * next field survey, or until the host block entity goes away underneath us.
     */
    private AnomaliteEnergy.Access host(ServerLevel level, BlockPos pos, Direction outward, boolean resurvey) {
        if (host != null && !resurvey && !host.blockEntity().isRemoved()) return host;
        host = AnomaliteEnergy.find(level, AnomaliteCrystalBlock.hostPos(pos, outward));
        return host;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        host = null;
    }

    public boolean wasRecentlyFed(long gameTime) {
        return lastFedTick != Long.MIN_VALUE && gameTime - lastFedTick <= 2;
    }

    public boolean isContained() {
        return contained;
    }

    public boolean isCultivated() {
        return cultivated;
    }

    public int getLastDrainFe() {
        return lastDrainFe;
    }

    @Override
    public MutableComponent fluxMeterLine() {
        int drain = getLastDrainFe();
        String status = isCultivated() ? "cultivated" : isContained() ? "receding" : drain > 0 ? "feeding" : "dormant";
        return Component.translatable("item.quantimium.flux_meter.anomalite_crystal",
                        getBlockState().getValue(AnomaliteCrystalBlock.AGE) + 1, FluxMeterReadout.fe(drain),
                        Component.translatable("item.quantimium.flux_meter.anomalite_crystal." + status))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
