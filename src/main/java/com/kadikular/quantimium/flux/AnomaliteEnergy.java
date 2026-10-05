package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.block.entity.QuantumEnergyHost;
import com.kadikular.quantimium.block.entity.QuantumEnergyStorage;
import com.kadikular.quantimium.block.entity.simulation.MachinePowerPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Short-lived access to a prospective Anomalite Crystal host's cross-mod energy buffers. */
public final class AnomaliteEnergy {

    public record Access(BlockEntity blockEntity, @Nullable QuantumEnergyStorage internal,
                         MachinePowerPorts ports) {
        public long storedFe() {
            return internal != null ? internal.getEnergyStored() : ports.totalStoredFe();
        }

        public boolean canExtract() {
            return internal != null ? internal.getEnergyStored() > 0 : ports.canExtract();
        }

        public long extractFe(long maxFe) {
            if (maxFe <= 0) return 0;
            if (internal != null) {
                int removed = internal.consume(maxFe);
                if (removed > 0) blockEntity.setChanged();
                return removed;
            }
            long removed = ports.extractFe(maxFe);
            if (removed > 0) blockEntity.setChanged();
            return removed;
        }
    }

    private AnomaliteEnergy() {}

    public static @Nullable Access find(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity.isRemoved()) return null;

        // Quantimium capabilities are insertion-only, so bill against the internal storage instead.
        if (blockEntity instanceof QuantumEnergyHost host) {
            return new Access(blockEntity, host.getEnergyStorage(), MachinePowerPorts.none());
        }

        BlockState state = level.getBlockState(pos);
        MachinePowerPorts ports = MachinePowerPorts.discover(level, pos, state, blockEntity);
        return ports.isEmpty() ? null : new Access(blockEntity, null, ports);
    }
}
