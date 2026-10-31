package com.kadikular.quantimium.compat.productivebees;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * What the Quantum Simulator needs to know of Productive Bees: a hive's bees fly out to work, and out of a
 * simulator's field they'd be lost, unless an upgrade keeps them in. Read by id and saved data alone, so
 * Productive Bees needn't be on the classpath.
 */
public final class ProductiveBees {

    /** Upgrades that keep a hive's bees inside: its Simulator Upgrade, or Productivity III or IV. */
    private static final List<String> SIMULATION_UPGRADES = List.of(
            "productivelib:upgrade_simulator", "productivelib:upgrade_productivity_3", "productivelib:upgrade_productivity_4");

    private ProductiveBees() {}

    /** Whether {@code state} is a Productive Bees hive whose bees would fly out of the field. */
    public static boolean beesWouldWander(BlockState state, CompoundTag nbt) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("productivebees") || !id.getPath().contains("hive")) return false;
        String saved = nbt.toString();
        return SIMULATION_UPGRADES.stream().noneMatch(saved::contains);
    }
}
