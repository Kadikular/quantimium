package com.kadikular.quantimium.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Map;

/**
 * A storage network a Reactor is linked to through a port of its own (the ME Superposition Port), kept
 * free of any one mod's classes so the core can use it whether that mod is installed or not.
 *
 * <p>The core only ever calls these while it has its own ports turned away
 * ({@link com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity#isDrawing()}), so whatever the
 * network sees of the Reactor itself, through this port or a storage bus on one of its Materialiser
 * Ports, is never counted or taken back from it.
 */
public interface ReactorNetwork {

    /** Identifies the network, so two ports on one network are only counted once. Null while offline. */
    Object network();

    /** Everything the network holds, as it stands. */
    Map<ItemResource, Long> stock();

    /** Takes up to {@code amount} of {@code item} from the network; a simulation takes nothing. */
    long extract(ItemResource item, long amount, boolean simulate);

    /**
     * Whether a storage bus, or anything else on this network that reads inventories, sits at
     * {@code pos} facing {@code side}: one of the Reactor's own Materialiser Ports would be shown to
     * this network twice.
     */
    boolean readsFrom(Level level, BlockPos pos, Direction side);
}
