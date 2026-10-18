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
    /** Low machines, the lasers' first blocks among them: made before any anomaly is siphoned or grown. */
    private static final List<String> LOW = List.of("observation_chamber", "basic_quantum_crafter", "flux_meter", "flux_detector",
            "quantum_exciter", "basic_anomaly_siphon", "flux_suppressor", "flash_emitter", "solar_emitter", "laser_relay",
            "laser_mirror", "laser_splitter", "laser_port", "photodetector", "logistics_controller", "logistics_receptacle",
            "logistics_terminal", "laser_linker");
    /** Laser blocks that need Anomalite: Mid, so made without the High tier. */
    private static final List<String> MID_LASERS = List.of("laser_emitter_1", "lens_mount", "laser_prism", "area_prism",
            "harvest_lens", "pickup_lens", "filter_lens", "speed_lens", "budding_anomalite");
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
        for (String machine : MID) reachable(helper, machine, HIGH_MATERIALS, "the High tier");
        for (String machine : MID_LASERS) reachable(helper, machine, HIGH_MATERIALS, "the High tier");
        helper.succeed();
    }

    // covers: tiers.materials
    @GameTest(template = TestSupport.FLOOR_9)
    public static void lowTierNeedsNoAnomaly(GameTestHelper helper) {
        // The first machines and lasers come before any anomaly: no fragments, no Anomalite, no Foundry.
        Set<String> later = new java.util.HashSet<>(MID_MATERIALS);
        later.addAll(HIGH_MATERIALS);
        for (String machine : LOW) reachable(helper, machine, later, "anomaly");
        helper.succeed();
    }

    private static void reachable(GameTestHelper helper, String machine, Set<String> later, String what) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Quantimium.MODID, machine));
        helper.assertTrue(makeableWithout(helper, item, later, new java.util.HashSet<>()), machine + " needs " + what + " somewhere in its parts");
    }

    /**
     * Whether {@code item} can be had without any of {@code later}: not ours, found rather than made (no recipe), or
     * made by some recipe outside the Foundry whose parts all can. Parts already being looked at count as reachable.
     */
    private static boolean makeableWithout(GameTestHelper helper, Item item, Set<String> later, Set<Item> seen) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals(Quantimium.MODID)) return true;
        if (later.contains(id.getPath())) return false;
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
                for (ItemStack stack : RecipeCompat.stacks(ingredient)) option |= makeableWithout(helper, stack.getItem(), later, seen);
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
