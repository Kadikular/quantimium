package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.RelayMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A laser block with emitters on its faces (a Laser Focus, a Laser Relay): each holds one lens and has its own way of
 * listening to redstone. What the emitter menu, lens settings and tip colours work with.
 *
 * <p>The emitters themselves are its {@link EmitterFaces}; what follows a change (it's saved, the laser network works
 * its light out again, clients redraw its tips) is here, the same for every such block.
 */
public interface EmitterHolder {

    /** Its emitters' lenses, channels and redstone. */
    EmitterFaces emitterFaces();

    /** Whether there's an emitter on {@code face}. */
    boolean hasEmitter(Direction face);

    /** The lens in the emitter on {@code face}: empty for none. */
    default ItemStack lens(Direction face) {
        return emitterFaces().lens(face);
    }

    default void setLens(Direction face, ItemStack stack) {
        emitterFaces().setLens(face, stack);
        setChanged();
        Level level = getLevel();
        if (level != null) {
            LaserNetworks.changed(level);
            // Clients redraw its tip in the lens's colour: told by the server, or at once where it was changed.
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), level.isClientSide() ? Block.UPDATE_IMMEDIATE : Block.UPDATE_CLIENTS);
        }
    }

    /** How its emitters listen to redstone, but for those set one by one. */
    default RelayMode mode() {
        return emitterFaces().mode();
    }

    /** Steps every emitter on to the next way of listening to redstone, together. */
    default void cycleMode() {
        emitterFaces().cycleMode();
        setChanged();
        Level level = getLevel();
        if (level != null) LaserNetworks.changed(level);
    }

    /** How the emitter on {@code face} listens to redstone. */
    default RelayMode modeOf(Direction face) {
        return emitterFaces().modeOf(face);
    }

    /** Steps the emitter on {@code face} on to its next way of listening to redstone. */
    default void cycleMode(Direction face) {
        emitterFaces().cycleMode(face);
        setChanged();
        Level level = getLevel();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Whether the emitter on {@code face} lets light out now. */
    default boolean on(Direction face) {
        return emitterFaces().on(face, getLevel());
    }

    /** The channel the lens on {@code face} works on ({@link com.kadikular.quantimium.laser.Channels}). */
    default int channelOf(Direction face) {
        return emitterFaces().channelOf(face);
    }

    /** Sets the channel the lens on {@code face} works on. */
    default void setChannel(Direction face, int channel) {
        emitterFaces().setChannel(face, channel);
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            LaserNetworks.changed(level);
        }
    }

    @Nullable
    Level getLevel();

    BlockPos getBlockPos();

    BlockState getBlockState();

    boolean isRemoved();

    void setChanged();
}
