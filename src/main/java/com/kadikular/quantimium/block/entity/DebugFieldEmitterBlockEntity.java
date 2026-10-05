package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.DebugFieldEmitterBlock;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emits once a second what a second of its FE/t would, the same way a Quantum Crafter's work emits
 * (1 flux per {@link QuantumFlux#FE_PER_FLUX} FE). No buffer and no power: its setting is its state.
 */
public class DebugFieldEmitterBlockEntity extends BlockEntity {

    public DebugFieldEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEBUG_FIELD_EMITTER_BE.get(), pos, state);
    }

    /** Flux a second that {@code fePerTick} of work makes. */
    public static double fluxPerSecond(long fePerTick) {
        return fePerTick * 20.0 / QuantumFlux.FE_PER_FLUX;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DebugFieldEmitterBlockEntity emitter) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20L != 0L) return;
        long fe = DebugFieldEmitterBlock.FE_PER_TICK[state.getValue(DebugFieldEmitterBlock.SETTING)];
        if (fe > 0L) QuantumFlux.emitFromEnergy(server, pos, fe * 20L);
    }
}
