package com.kadikular.quantimium.recipe;

import com.kadikular.quantimium.recipe.adapter.ConfiguredRecipeFamily;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every recipe a catalyst can run, as {@link RecipeShape}s: the Crafter's preview, turned round. The
 * preview asks "what can these ingredients make?"; this asks "what could this machine ever make?",
 * which is what an ME network needs to plan with.
 *
 * <p>The same families answer, in the same order, so a recipe is described the way the Crafter would
 * price and run it. Recipes a family cannot put in fixed terms (tools that are not used up, chance inputs) are
 * left out, and those with fluids unless asked for.
 */
public final class RecipeShapes {

    /** Base FE for a crafting-table craft, as {@link CraftingFamily} prices it. */
    private static final int CRAFTING_BASE_FE = 100;
    /** Base FE for filling or emptying a bucket. */
    private static final int CONTAINER_FE = 10;

    private RecipeShapes() {}

    /** As {@link #forCatalyst(Level, ItemStack, boolean)}, leaving out recipes with fluids: what has none to give yet. */
    public static List<RecipeShape> forCatalyst(Level level, ItemStack catalyst) {
        return forCatalyst(level, catalyst, false);
    }

    /** Every recipe {@code catalyst} runs; those taking or giving fluids only with {@code fluids}. */
    public static List<RecipeShape> forCatalyst(Level level, ItemStack catalyst, boolean fluids) {
        List<RecipeShape> shapes = new ArrayList<>();
        if (level == null || catalyst.isEmpty() || !CatalystResolver.isCatalystAllowed(catalyst)) return shapes;
        RecipeIndex index = RecipeIndex.shared();
        index.bind(level);
        HolderLookup.Provider registries = level.registryAccess();
        boolean crafting = CatalystResolver.isCraftingCatalyst(catalyst);
        int foundryArms = FoundryFamily.arms(catalyst);
        for (RecipeType<?> type : CatalystResolver.resolve(catalyst)) {
            for (RecipeHolder<?> holder : index.recipesOf(type)) {
                if (FoundryFamily.supports(holder.value())) {
                    FoundryFamily.describe(holder, foundryArms).ifPresent(shapes::add);
                    continue;
                }
                describe(holder, registries, crafting).ifPresent(shapes::add);
            }
        }
        if (!fluids) shapes.removeIf(RecipeShape::hasFluids);
        return shapes;
    }

    /**
     * Filling and emptying buckets, as recipes: an empty bucket and a bucket's worth of a fluid make its bucket, and
     * the other way round. So a recipe wanting a water bucket can be met from stored water, and a recipe making lava
     * can fill buckets. Every fluid with a bucket of its own.
     */
    public static List<RecipeShape> containers() {
        List<RecipeShape> shapes = new ArrayList<>();
        int bucket = net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME;
        for (net.minecraft.world.level.material.Fluid fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
            net.minecraft.world.item.Item filled = fluid.getBucket();
            if (filled == net.minecraft.world.item.Items.AIR || filled == net.minecraft.world.item.Items.BUCKET) continue;
            net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
            String path = id.getNamespace() + "/" + id.getPath();
            FluidParts.Input fluidIn = new FluidParts.Input(List.of(fluid), bucket, each -> each.getFluid().isSame(fluid));
            shapes.add(new RecipeShape(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.kadikular.quantimium.Quantimium.MODID, "fill/" + path),
                    List.of(new RecipeShape.Input(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.BUCKET), 1)),
                    List.of(new ItemStack(filled)), CONTAINER_FE, List.of(), List.of(), new FluidParts.Parts(List.of(fluidIn), List.of())));
            shapes.add(new RecipeShape(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.kadikular.quantimium.Quantimium.MODID, "empty/" + path),
                    List.of(new RecipeShape.Input(net.minecraft.world.item.crafting.Ingredient.of(filled), 1)),
                    List.of(new ItemStack(net.minecraft.world.item.Items.BUCKET)), CONTAINER_FE, List.of(), List.of(),
                    new FluidParts.Parts(List.of(), List.of(new net.neoforged.neoforge.fluids.FluidStack(fluid, bucket)))));
        }
        return shapes;
    }

    private static Optional<RecipeShape> describe(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                                  boolean crafting) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof CraftingRecipe) {
            if (!crafting || recipe.isSpecial()) return Optional.empty();
            return CraftingFamily.describe(holder, registries, CraftEnergy.flat(CRAFTING_BASE_FE));
        }
        if (VanillaSingleInputFamily.supports(recipe)) return VanillaSingleInputFamily.describe(holder, registries);
        if (MiMachineFamily.supports(recipe)) return MiMachineFamily.describe(holder);
        Optional<RecipeShape> configured = ConfiguredRecipeFamily.describe(registries, holder);
        if (configured.isPresent()) return configured;
        return GenericItemFamily.describe(holder, registries);
    }
}
