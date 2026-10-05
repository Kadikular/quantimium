package com.kadikular.quantimium.block.multiblock;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A controller that plinth blocks stand in for: they forward capabilities and the GUI to it.
 *
 * <p>Both multiblocks bury their controller inside a plinth ring, so without this the only way to
 * pipe power in would be from below.
 */
public interface MultiblockHost extends MultiblockController, MenuProvider {

    boolean isFormed();

    /** Energy face exposed through the plinth, or null when the structure is incomplete. */
    @Nullable
    EnergyHandler energyPort();

    /** Item face exposed through the plinth. Null for machines without an inventory. */
    @Nullable
    default ResourceHandler<ItemResource> itemPort() {
        return null;
    }
}
