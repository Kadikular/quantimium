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
 * @param fluids  the fluids it takes and gives (see {@link FluidParts}); none for most
 */
public record RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe,
                          List<Ingredient> tools, List<Ingredient> wears, FluidParts.Parts fluids) {

    public record Input(Ingredient ingredient, int count) {}

    public RecipeShape {
        inputs = List.copyOf(inputs);
        outputs = outputs.stream().map(ItemStack::copy).toList();
        tools = List.copyOf(tools);
        wears = List.copyOf(wears);
    }

    /** A recipe without fluids. */
    public RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe,
                       List<Ingredient> tools, List<Ingredient> wears) {
        this(id, inputs, outputs, baseFe, tools, wears, FluidParts.Parts.NONE);
    }

    /** The same recipe, taking and giving {@code fluids} too. */
    public RecipeShape withFluids(FluidParts.Parts fluids) {
        return new RecipeShape(id, inputs, outputs, baseFe, tools, wears, fluids);
    }

    /** Whether it takes or gives any fluid. */
    public boolean hasFluids() {
        return !fluids.isEmpty();
    }

    /** A recipe with tools that last forever. */
    public RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe, List<Ingredient> tools) {
        this(id, inputs, outputs, baseFe, tools, List.of());
    }

    /** A recipe that uses up everything it takes. */
    public RecipeShape(Identifier id, List<Input> inputs, List<ItemStack> outputs, int baseFe) {
        this(id, inputs, outputs, baseFe, List.of(), List.of());
    }

    /** What it makes first: an item, else nothing (a recipe making only fluids). */
    public ItemStack primaryOutput() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().copy();
    }
}
