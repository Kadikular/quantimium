package com.kadikular.quantimium.recipe.foundry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

public record FoundryRecipeInput(List<ItemStack> stacks, int pillarCount, double flux) implements RecipeInput {
    public FoundryRecipeInput {
        stacks = List.copyOf(stacks);
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return stacks.size();
    }
}
