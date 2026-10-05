package com.kadikular.quantimium.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/** The receive/extract contract over an {@link EnergyHandler}, for code that moves energy in simple amounts. */
@SuppressWarnings("deprecation")
public final class LegacyEnergy {
    private LegacyEnergy() {}

    @Nullable
    public static IEnergyStorage legacy(@Nullable EnergyHandler handler) {
        return handler == null ? null : IEnergyStorage.of(handler);
    }

    /** The energy an item holds, or null if it holds none. */
    @Nullable
    public static IEnergyStorage item(ItemStack stack) {
        return legacy(stack.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(stack)));
    }
}
