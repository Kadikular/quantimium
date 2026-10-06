package com.kadikular.quantimium.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Two lists of {@link FilterEntry ghost entries} that shape which of a catalyst's recipes are used,
 * each a whitelist or a blacklist: outputs (which recipes at all) and inputs (which items they may use
 * up). An empty list filters nothing. Shared by the ME Superposition Crafter and the Catalyst Bay.
 */
public record RecipeFilter(List<ItemStack> outputs, boolean outputsAllow, List<ItemStack> inputs, boolean inputsAllow) {

    public static final RecipeFilter NONE = new RecipeFilter(List.of(), true, List.of(), false);

    /**
     * The filter held in {@code container}: {@code perList} output entries, then {@code perList}
     * input entries.
     */
    public static RecipeFilter of(Container container, int perList, boolean outputsAllow, boolean inputsAllow) {
        List<ItemStack> outputs = new ArrayList<>();
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < Math.min(container.getContainerSize(), perList * 2); i++) {
            ItemStack entry = container.getItem(i);
            if (!entry.isEmpty()) (i < perList ? outputs : inputs).add(entry.copy());
        }
        return new RecipeFilter(List.copyOf(outputs), outputsAllow, List.copyOf(inputs), inputsAllow);
    }

    /** An empty output list offers everything; otherwise the output must (whitelist) or must not (blacklist) be listed. */
    public boolean offers(ItemStack output) {
        if (outputs.isEmpty()) return true;
        boolean listed = outputs.stream().anyMatch(entry -> FilterEntry.matches(entry, output));
        return outputsAllow == listed;
    }

    /** {@code shape} as this filter lets it run, or null if it may not run at all. */
    @Nullable
    public RecipeShape apply(RecipeShape shape) {
        return offers(shape.primaryOutput()) ? withInputs(shape) : null;
    }

    /**
     * The shape with its ingredients cut down by the input list: a blacklist takes the listed items
     * out, a whitelist keeps only them. Null if an ingredient is left with nothing it accepts. Only
     * items go, not whole recipes: a recipe taking any log still takes birch when oak is blacklisted.
     */
    @Nullable
    public RecipeShape withInputs(RecipeShape shape) {
        if (inputs.isEmpty()) return shape;
        List<RecipeShape.Input> kept = new ArrayList<>();
        boolean changed = false;
        for (RecipeShape.Input input : shape.inputs()) {
            List<ItemStack> options = RecipeCompat.stacks(input.ingredient());
            List<ItemStack> allowed = new ArrayList<>();
            for (ItemStack option : options) {
                boolean onList = inputs.stream().anyMatch(entry -> FilterEntry.matches(entry, option));
                if (onList == inputsAllow) allowed.add(option);
            }
            if (allowed.isEmpty()) return null;
            if (allowed.size() == options.size()) {
                kept.add(input);
            } else {
                kept.add(new RecipeShape.Input(Ingredient.of(allowed.stream().map(ItemStack::getItem)), input.count()));
                changed = true;
            }
        }
        return changed ? new RecipeShape(shape.id(), kept, shape.outputs(), shape.baseFe(), shape.tools()) : shape;
    }

    /** Whether {@code other} filters exactly as this does: same entries, tags and modes. */
    public boolean same(RecipeFilter other) {
        return outputsAllow == other.outputsAllow && inputsAllow == other.inputsAllow
                && sameEntries(outputs, other.outputs) && sameEntries(inputs, other.inputs);
    }

    private static boolean sameEntries(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) return false;
        }
        return true;
    }
}
