package com.kadikular.quantimium.recipe.foundry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record FoundryIngredient(Ingredient ingredient, int count) {
    public static final Codec<FoundryIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(FoundryIngredient::ingredient),
            Codec.INT.optionalFieldOf("count", 1).forGetter(FoundryIngredient::count)
    ).apply(instance, FoundryIngredient::new));

    public FoundryIngredient {
        count = Math.max(1, count);
    }

    public boolean test(ItemStack stack) {
        return ingredient.test(stack) && stack.getCount() >= count;
    }
}
