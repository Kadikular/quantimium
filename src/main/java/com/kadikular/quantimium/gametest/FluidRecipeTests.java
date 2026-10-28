package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.recipe.FluidParts;
import com.kadikular.quantimium.recipe.RecipeShape;
import com.kadikular.quantimium.recipe.RecipeShapes;
import com.mojang.datafixers.util.Either;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Recipes with fluids: what they take and give is read off their fields by type, as other mods' machines keep them
 * (stand-ins here, shaped as Ender IO's vat, Energized Power's transposer and MI's mixer keep theirs); and buckets fill
 * and empty as recipes.
 */
public final class FluidRecipeTests {

    private FluidRecipeTests() {}

    /** As Ender IO's vat keeps it: a sized ingredient in, a stack template out. */
    record Vat(SizedFluidIngredient input, FluidStackTemplate output, int energy) {}

    /** A mod's own fluid ingredient, as Energized Power's: a {@code test(FluidStack)} and an amount. */
    public static final class OwnIngredient {
        private final int amount;

        OwnIngredient(int amount) {
            this.amount = amount;
        }

        public boolean test(FluidStack fluid) {
            return fluid.getFluid().isSame(Fluids.LAVA);
        }

        public int amount() {
            return amount;
        }
    }

    /** As Energized Power's transposer: one field that's a stack (given) or its own ingredient (taken). */
    record Transposer(Either<FluidStackTemplate, OwnIngredient> fluid) {}

    /** Lists, arrays and optionals, named by side. */
    static final class Mixer {
        final List<SizedFluidIngredient> fluidInputs = List.of(SizedFluidIngredient.of(Fluids.WATER, 100), SizedFluidIngredient.of(Fluids.LAVA, 50));
        final FluidStack[] fluidOutputs = {new FluidStack(Fluids.WATER, 10)};
        final Optional<FluidStack> byproductOutput = Optional.empty();
        final int duration = 200;
    }

    private static FluidParts.Input only(GameTestHelper helper, FluidParts.Parts parts) {
        helper.assertValueEqual(parts.inputs().size(), 1, "one fluid taken");
        return parts.inputs().getFirst();
    }

    // covers: recipe.fluids
    @com.kadikular.quantimium.gametest.GameTest(template = TestSupport.FLOOR_17, batch = "fluid_recipes", timeoutTicks = 20)
    public static void aRecipesFluidsAreReadOffItsFieldsByType(GameTestHelper helper) {
        FluidParts.Parts vat = FluidParts.of(new Vat(SizedFluidIngredient.of(Fluids.WATER, 250), new FluidStackTemplate(Fluids.LAVA, 100), 5));
        FluidParts.Input water = only(helper, vat);
        helper.assertValueEqual(water.amount(), 250, "250 mB taken");
        helper.assertTrue(water.matches(new FluidStack(Fluids.WATER, 1)) && !water.matches(new FluidStack(Fluids.LAVA, 1)), "water, not lava");
        helper.assertValueEqual(vat.outputs().size(), 1, "one fluid given");
        helper.assertTrue(vat.outputs().getFirst().getFluid().isSame(Fluids.LAVA) && vat.outputs().getFirst().getAmount() == 100, "100 mB of lava given");

        FluidParts.Parts taking = FluidParts.of(new Transposer(Either.right(new OwnIngredient(500))));
        FluidParts.Input lava = only(helper, taking);
        helper.assertValueEqual(lava.amount(), 500, "its own amount");
        helper.assertTrue(lava.fluids().contains(Fluids.LAVA) && !lava.fluids().contains(Fluids.WATER), "the fluids it accepts, found by asking it");
        FluidParts.Parts giving = FluidParts.of(new Transposer(Either.left(new FluidStackTemplate(Fluids.WATER, 1000))));
        helper.assertTrue(giving.inputs().isEmpty() && giving.outputs().size() == 1, "a stack in that field is given");

        FluidParts.Parts mixer = FluidParts.of(new Mixer());
        helper.assertValueEqual(mixer.inputs().size(), 2, "both inputs from the list");
        helper.assertValueEqual(mixer.outputs().size(), 1, "the output from the array; the empty optional, nothing");

        helper.assertTrue(FluidParts.of(new ItemStack(Items.STONE)).isEmpty(), "nothing read from what holds no fluid");
        helper.succeed();
    }

    // covers: recipe.fluids
    @com.kadikular.quantimium.gametest.GameTest(template = TestSupport.FLOOR_17, batch = "fluid_recipes", timeoutTicks = 20)
    public static void bucketsFillAndEmptyAsRecipes(GameTestHelper helper) {
        List<RecipeShape> shapes = RecipeShapes.containers();
        RecipeShape fill = shapes.stream().filter(shape -> shape.id().getPath().equals("fill/minecraft/water")).findFirst().orElseThrow();
        helper.assertTrue(fill.primaryOutput().is(Items.WATER_BUCKET), "fills a water bucket");
        helper.assertTrue(fill.inputs().getFirst().ingredient().test(new ItemStack(Items.BUCKET)), "from an empty bucket");
        helper.assertValueEqual(fill.fluids().inputs().getFirst().amount(), 1000, "and a bucket's worth of water");
        RecipeShape empty = shapes.stream().filter(shape -> shape.id().getPath().equals("empty/minecraft/lava")).findFirst().orElseThrow();
        helper.assertTrue(empty.primaryOutput().is(Items.BUCKET), "emptying gives the bucket back");
        helper.assertValueEqual(empty.fluids().outputs().getFirst().getAmount(), 1000, "and the lava");
        helper.assertTrue(shapes.stream().noneMatch(shape -> shape.id().getPath().contains("flowing")), "flowing fluids have no buckets of their own");
        helper.succeed();
    }
}
