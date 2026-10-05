package com.kadikular.quantimium.gametest.compat;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Helpers for the tests against partner mods, which {@link com.kadikular.quantimium.gametest.GameTests} registers only when
 * the mod is loaded.
 *
 * <p>The tests reach partner blocks, items and fluids by id, as the mod itself does, so there is no
 * compile-time dependency on any of them. Each {@code @GameTest} names the template namespace itself,
 * which a holder would otherwise supply.
 */
public final class CompatGameTests {

    private CompatGameTests() {}

    static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.getValue(Identifier.parse(id));
    }
}
