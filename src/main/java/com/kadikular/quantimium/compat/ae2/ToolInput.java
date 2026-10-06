package com.kadikular.quantimium.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.kadikular.quantimium.recipe.RecipeCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A tool a pattern keeps, such as a press or a cutting knife: one of any item the ingredient accepts,
 * handed back after the run, worn by {@code wear} if it wears (nothing back if that breaks it). AE2 waits
 * for it to come back and uses it again, so the provider must hand it back (the ME Superposition Port
 * does).
 */
record ToolInput(Ingredient ingredient, int wear, GenericStack[] possible) implements IPatternDetails.IInput {

    ToolInput(Ingredient ingredient, int wear) {
        this(ingredient, wear, possibleOf(ingredient));
    }

    private static GenericStack[] possibleOf(Ingredient ingredient) {
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
        return 1;
    }

    @Override
    public boolean isValid(AEKey input, Level level) {
        return input instanceof AEItemKey item && ingredient.test(item.toStack());
    }

    @Nullable
    @Override
    public AEKey getRemainingKey(AEKey template) {
        if (wear == 0 || !(template instanceof AEItemKey item)) return template;
        ItemStack worn = item.toStack();
        if (worn.getDamageValue() + wear >= worn.getMaxDamage()) return null;
        worn.setDamageValue(worn.getDamageValue() + wear);
        return AEItemKey.of(worn);
    }
}
