package com.kadikular.quantimium.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * A recipe described in the abstract: what it takes, what it always gives, and what it costs to run
 * once. The Crafter works from what is in its grid ({@link ResolvedCraft}); an ME network plans before
 * anything is in hand, so the ME Superposition Crafter offers recipes as shapes.
 *
 * @param inputs  consumed ingredients, each with how many of it one run takes
 * @param outputs guaranteed outputs only; chance outputs are left out, as the Crafter leaves them out
 * @param baseFe  FE for one run at the instant-craft multiplier, before any anomaly surcharge
 * @param tools   ingredients that must be there but aren't used up, such as an Inscriber's press. Only
 *                the Reactor can keep one; the ME Superposition Crafter leaves such recipes out
 * @param wears   those of the tools that lose one durability each run, such as AE2's cutting knives
 */
public record RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe,
                          List<Ingredient> tools, List<Ingredient> wears) {

    public record Input(Ingredient ingredient, int count) {}

    public RecipeShape {
        inputs = List.copyOf(inputs);
        outputs = outputs.stream().map(ItemStack::copy).toList();
        tools = List.copyOf(tools);
        wears = List.copyOf(wears);
    }

    /** A recipe with tools that last forever. */
    public RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe, List<Ingredient> tools) {
        this(id, inputs, outputs, baseFe, tools, List.of());
    }

    /** A recipe that uses up everything it takes. */
    public RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe) {
        this(id, inputs, outputs, baseFe, List.of(), List.of());
    }

    public ItemStack primaryOutput() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().copy();
    }
}
