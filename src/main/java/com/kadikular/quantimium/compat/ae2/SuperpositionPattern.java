package com.kadikular.quantimium.compat.ae2;

import com.kadikular.quantimium.recipe.RecipeCompat;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.kadikular.quantimium.recipe.RecipeShape;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One recipe offered to an ME network, made in code rather than encoded on an item. Its definition,
 * which AE2 uses to tell patterns apart, is the offering block's own item (the ME Superposition
 * Crafter's or Port's) tagged with the recipe id and batch: two crafters offering the same recipe at
 * the same batch offer the same pattern, and AE2 may use either.
 */
public final class SuperpositionPattern implements IPatternDetails {

    private final RecipeShape shape;
    private final int batch;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    /** One run of the pattern is {@code batch} runs of the recipe: inputs, outputs and cost all scaled. */
    public SuperpositionPattern(RecipeShape shape, int batch) {
        this(shape, batch, Ae2Content.SUPERPOSITION_CRAFTER_ITEM.get());
    }

    /** As offered by the block whose item is {@code offeredBy}: the ME Superposition Port's are its own. */
    public SuperpositionPattern(RecipeShape shape, int batch, net.minecraft.world.item.Item offeredBy) {
        this(shape, batch, offeredBy, false);
    }

    /**
     * With {@code tools}, the recipe's tools are inputs too, each handed back after the run (worn by one,
     * if it wears): AE2 expects them back and uses them again. Only for a provider that hands them back,
     * the ME Superposition Port.
     */
    public SuperpositionPattern(RecipeShape shape, int batch, net.minecraft.world.item.Item offeredBy, boolean tools) {
        this.shape = shape;
        this.batch = Math.max(1, batch);
        ItemStack marker = new ItemStack(offeredBy);
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", shape.id().toString());
        tag.putInt("batch", this.batch);
        marker.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        this.definition = AEItemKey.of(marker);

        List<IInput> list = new ArrayList<>();
        for (RecipeShape.Input input : shape.inputs()) {
            list.add(new Input(input.ingredient(), input.count() * this.batch, Input.possibleOf(input.ingredient()), tools));
        }
        if (tools) {
            for (Ingredient tool : shape.tools()) {
                boolean wears = shape.wears().stream().anyMatch(worn -> worn == tool);
                list.add(new ToolInput(tool, wears ? this.batch : 0));
            }
        }
        this.inputs = list.toArray(IInput[]::new);

        List<GenericStack> out = new ArrayList<>();
        for (ItemStack stack : shape.outputs()) {
            GenericStack generic = GenericStack.fromItemStack(stack);
            if (generic != null) out.add(new GenericStack(generic.what(), generic.amount() * this.batch));
        }
        this.outputs = List.copyOf(out);
    }

    public RecipeShape shape() {
        return shape;
    }

    /** Recipe runs in one run of this pattern. */
    public int batch() {
        return batch;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SuperpositionPattern pattern && pattern.definition.equals(definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    /**
     * Any item the ingredient accepts, {@code count} of them per run. With {@code givesBack}, what a craft
     * leaves of it (an empty bucket for a filled one) comes back, for a provider that hands it back.
     */
    private record Input(Ingredient ingredient, long count, GenericStack[] possible, boolean givesBack) implements IInput {

        static GenericStack[] possibleOf(Ingredient ingredient) {
            List<GenericStack> stacks = new ArrayList<>();
            for (ItemStack stack : RecipeCompat.stacks(ingredient)) {
                if (stack.isEmpty()) continue;
                GenericStack one = GenericStack.fromItemStack(stack.copyWithCount(1));
                if (one != null) stacks.add(one);
            }
            return stacks.toArray(GenericStack[]::new);
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possible;
        }

        @Override
        public long getMultiplier() {
            return count;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input instanceof AEItemKey item && ingredient.test(item.toStack());
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            if (!givesBack || !(template instanceof AEItemKey item)) return null;
            ItemStack left = com.kadikular.quantimium.reactor.ReactorPlanner.remainder(item.toStack());
            return left.isEmpty() ? null : AEItemKey.of(left);
        }
    }
}
