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

    public static void tick(Level level, BlockPos pos, BlockState state, CreativeEnergyCellBlockEntity cell) {
        for (Direction side : Direction.values()) {
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(side), side.getOpposite());
            if (target == null) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                target.insert(Integer.MAX_VALUE, transaction);
                transaction.commit();
            }
        }
    }
}
