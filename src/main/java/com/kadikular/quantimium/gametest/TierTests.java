package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Set;

/**
 * The tier skeleton (game plan C): each tier's recipes need a material from the loop before it.
 * Checked on the recipes as loaded, so a datapack or a later recipe change that skips a tier fails
 * here. See wiki: concepts/tiers.
 */
public final class TierTests {

    private static final List<String> MID = List.of("quantum_crafter", "quantum_simulator", "tesseract_stabilizer",
            "flux_maintainer", "anomaly_siphon", "zeno_field_controller", "decoherence_lance", "decoherence_projector",
            "mirror_lens", "tether", "rift_seed", "materialiser", "rift_lens", "fold_pylon");
    private static final List<String> HIGH = List.of("quantum_foundry_controller", "anomaly_containment_hall",
            "field_regulator", "rift_anchor", "rift_stabiliser", "superposition_pod", "unfolding_array", "entangled_dock", "fold_core");
    private static final Set<String> MID_MATERIALS = Set.of("anomaly_fragment", "anomalite_shard", "anomalite_lattice", "anomalite_emitter");
    private static final Set<String> HIGH_MATERIALS = Set.of("rift_residue", "tesseract");

    private TierTests() {}

    // covers: tiers.materials
    @GameTest(template = TestSupport.FLOOR_9)
    public static void eachTierNeedsTheMaterialOfTheLoopBefore(GameTestHelper helper) {
        for (String machine : MID) needs(helper, machine, MID_MATERIALS);
        for (String machine : HIGH) needs(helper, machine, HIGH_MATERIALS);
        helper.succeed();
    }

    // covers: tiers.materials
    @GameTest(template = TestSupport.FLOOR_9)
    public static void midTierNeedsNothingFromTheHighTier(GameTestHelper helper) {
        // Each Mid machine, and each of our parts in it, can be made without Rift Residue, a Tesseract or the Foundry:
        // the Lance closes the rifts that pay the residue the Foundry is built with.
        for (String machine : MID) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Quantimium.MODID, machine));
            helper.assertTrue(makeableBelowHigh(helper, item, new java.util.HashSet<>()), machine + " needs the High tier somewhere in its parts");
        }
        helper.succeed();
    }

    /**
     * Whether {@code item} can be had without the High tier: not ours, found rather than made (no recipe), or made by
     * some recipe outside the Foundry whose parts all can. Parts already being looked at count as reachable.
     */
    private static boolean makeableBelowHigh(GameTestHelper helper, Item item, Set<Item> seen) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals(Quantimium.MODID)) return true;
        if (HIGH_MATERIALS.contains(id.getPath())) return false;
        if (!seen.add(item)) return true;
        boolean any = false;
        for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            // The Foundry's own recipes show no result to recipe viewers' displays: read it from the recipe.
            if (recipe instanceof com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe foundry) {
                any |= foundry.result().is(item);
                continue;
            }
            if (!RecipeCompat.result(recipe, helper.getLevel().registryAccess()).is(item)) continue;
            any = true;
            boolean all = true;
            for (Ingredient ingredient : RecipeCompat.ingredients(recipe)) {
                boolean option = false;
                for (ItemStack stack : RecipeCompat.stacks(ingredient)) option |= makeableBelowHigh(helper, stack.getItem(), seen);
                all &= option;
            }
            if (all) return true;
        }
        return !any;
    }

    /** The recipe making {@code machine} takes at least one of {@code materials}. */
    private static void needs(GameTestHelper helper, String machine, Set<String> materials) {
        Item result = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Quantimium.MODID, machine));
        boolean found = false;
        boolean made = false;
        for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!RecipeCompat.result(recipe, helper.getLevel().registryAccess()).is(result)) continue;
            made = true;
            for (Ingredient ingredient : RecipeCompat.ingredients(recipe)) {
                for (ItemStack option : RecipeCompat.stacks(ingredient)) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(option.getItem());
                    if (id.getNamespace().equals(Quantimium.MODID) && materials.contains(id.getPath())) found = true;
                }
            }
        }
        helper.assertTrue(made, machine + " should have a recipe");
        helper.assertTrue(found, machine + " should need one of " + materials);
    }
}
