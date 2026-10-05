package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.FluxDetectorBlock;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/** Samples this chunk once a second and pushes redstone when the selected band reading changes. */
public class FluxDetectorBlockEntity extends BlockEntity implements FluxMeterReadout {

    private int signalStrength;
    /** The band last read, for hysteresis. Not saved: it settles again within a read. */
    private FluxBand lastBand;

    public FluxDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUX_DETECTOR_BE.get(), pos, state);
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluxDetectorBlockEntity detector) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 20L != 0L) return;
        detector.refresh(serverLevel, pos, state);
    }

    public void refresh(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        // The field at the detector, with hysteresis so a reading hovering at an edge switches once.
        QuantumFlux.Neighbourhood field = QuantumFlux.sample(level, pos);
        double value = switch (state.getValue(FluxDetectorBlock.MODE)) {
            case FLUX -> field.flux();
            case ANOMALY -> field.anomaly();
            case BOTH -> Math.max(field.flux(), field.anomaly());
        };
        FluxBand band = FluxBand.of(value, lastBand);
        lastBand = band;
        int next = band.detectorSignal();
        boolean powered = next > 0;
        if (next == signalStrength && state.getValue(FluxDetectorBlock.POWERED) == powered) return;

        signalStrength = next;
        BlockState updated = state.setValue(FluxDetectorBlock.POWERED, powered);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
        level.updateNeighborsAt(pos, updated.getBlock());
        for (Direction face : Direction.values()) {
            level.updateNeighborsAt(pos.relative(face), updated.getBlock());
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Signal", signalStrength);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        signalStrength = tag.getIntOr("Signal", 0);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return Component.translatable("item.quantimium.flux_meter.detector",
                        Component.translatable("item.quantimium.flux_meter.detector."
                                + getBlockState().getValue(FluxDetectorBlock.MODE).getSerializedName()),
                        signalStrength)
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}
