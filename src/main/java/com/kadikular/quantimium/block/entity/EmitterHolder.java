package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.laser.RelayMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * A laser block with emitters on its faces (a Laser Focus, a Laser Relay): each holds one lens and has its own way of
 * listening to redstone. What the emitter menu, lens settings and tip colours work with.
 */
public interface EmitterHolder {

    /** Whether there's an emitter on {@code face}. */
    boolean hasEmitter(Direction face);

    /** The lens in the emitter on {@code face}: empty for none. */
    ItemStack lens(Direction face);

    void setLens(Direction face, ItemStack stack);

    /** How the emitter on {@code face} listens to redstone. */
    RelayMode modeOf(Direction face);

    /** Steps the emitter on {@code face} on to its next way of listening to redstone. */
    void cycleMode(Direction face);

    BlockPos getBlockPos();

    boolean isRemoved();

    void setChanged();
}
