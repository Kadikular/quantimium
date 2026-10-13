package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Fills each neighbour that takes FE, every tick. No buffer: it has as much as anything asks for. */
public class CreativeEnergyCellBlockEntity extends BlockEntity {

    public CreativeEnergyCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_ENERGY_CELL_BE.get(), pos, state);
    }

    /** Each neighbour's energy, looked up once and kept: the cache follows the block if it changes. */
    @org.jetbrains.annotations.Nullable
    private net.neoforged.neoforge.capabilities.BlockCapabilityCache<EnergyHandler, @org.jetbrains.annotations.Nullable Direction>[] neighbours;

    @SuppressWarnings("unchecked")
    public static void tick(Level level, BlockPos pos, BlockState state, CreativeEnergyCellBlockEntity cell) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (cell.neighbours == null) {
            cell.neighbours = new net.neoforged.neoforge.capabilities.BlockCapabilityCache[6];
            for (Direction side : Direction.values()) {
                cell.neighbours[side.get3DDataValue()] = net.neoforged.neoforge.capabilities.BlockCapabilityCache.create(
                        Capabilities.Energy.BLOCK, server, pos.relative(side), side.getOpposite());
            }
        }
        for (var cache : cell.neighbours) {
            EnergyHandler target = cache.getCapability();
            // Asked first, so a neighbour that's full costs no transaction.
            if (target == null || target.getAmountAsLong() >= target.getCapacityAsLong()) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                target.insert(Integer.MAX_VALUE, transaction);
                transaction.commit();
            }
        }
    }
}
