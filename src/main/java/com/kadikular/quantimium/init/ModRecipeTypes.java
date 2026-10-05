package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Quantimium.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Quantimium.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<QuantumFoundryRecipe>> FOUNDRY_TYPE =
            TYPES.register("quantum_foundry", () -> new RecipeType<QuantumFoundryRecipe>() {
                @Override
                public String toString() {
                    return Quantimium.MODID + ":quantum_foundry";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<QuantumFoundryRecipe>>
            FOUNDRY_SERIALIZER = SERIALIZERS.register("quantum_foundry",
            QuantumFoundryRecipe.Serializer::create);

    private ModRecipeTypes() {}

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
        SERIALIZERS.register(eventBus);
    }
}
