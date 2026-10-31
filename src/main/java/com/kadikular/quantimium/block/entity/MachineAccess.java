package com.kadikular.quantimium.block.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Who may change a machine from afar, and what a machine does once its sides change: one rule for every
 * machine that takes the wrench, the side configuration screen or a button packet.
 */
public final class MachineAccess {

    /** Eight blocks, squared, from the player to the machine's centre. */
    public static final double REACH_SQR = 64.0;

    private MachineAccess() {
    }

    /** The player is in the machine's level, the machine is still there, and it's within eight blocks. */
    public static boolean withinReach(BlockEntity machine, Player player) {
        Level level = machine.getLevel();
        return level != null && player.level() == level
                && level.getBlockEntity(machine.getBlockPos()) == machine
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= REACH_SQR;
    }

    /**
     * After a machine takes new side settings: it's saved, pipes holding on to what a face offered ask
     * again, and clients see the new ports.
     */
    public static void sidesChanged(BlockEntity machine) {
        machine.setChanged();
        Level level = machine.getLevel();
        if (level == null) return;
        level.invalidateCapabilities(machine.getBlockPos());
        if (!level.isClientSide()) {
            level.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
