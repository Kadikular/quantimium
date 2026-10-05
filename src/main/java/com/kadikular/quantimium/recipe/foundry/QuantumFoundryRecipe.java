package com.kadikular.quantimium.recipe.foundry;

import net.minecraft.world.item.ItemStackTemplate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Timed, field-gated recipe executed by the Quantum Foundry. */
public record QuantumFoundryRecipe(
        List<FoundryIngredient> ingredients,
        int minimumPillars,
        FluxBand minimumFluxBand,
        int duration,
        int fePerTick,
        double fluxCost,
        double anomaly,
        ItemStackTemplate template
) implements Recipe<FoundryRecipeInput> {
    public QuantumFoundryRecipe {
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty() || ingredients.size() > 4) {
            throw new IllegalArgumentException("Foundry recipes require 1-4 ingredients");
        }
        minimumPillars = Math.clamp(minimumPillars, ingredients.size(), 4);
        duration = Math.max(1, duration);
        fePerTick = Math.max(0, fePerTick);
        fluxCost = Math.max(0.0, fluxCost);
        anomaly = Math.max(0.0, anomaly);
    }

    @Override
    public boolean matches(FoundryRecipeInput input, Level level) {
        if (input.pillarCount() < minimumPillars
                || FluxBand.of(input.flux()).ordinal() < minimumFluxBand.ordinal()) {
            return false;
        }
        boolean[] used = new boolean[input.size()];
        return matchIngredient(input, 0, used);
    }

    private boolean matchIngredient(FoundryRecipeInput input, int ingredientIndex, boolean[] used) {
        if (ingredientIndex >= ingredients.size()) return true;
        FoundryIngredient wanted = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < input.size(); slot++) {
            if (used[slot] || !wanted.test(input.getItem(slot))) continue;
            used[slot] = true;
            if (matchIngredient(input, ingredientIndex + 1, used)) return true;
            used[slot] = false;
        }
        return false;
    }

    /** Returns the slot used by each ingredient, in recipe order, or an empty list. */
    public List<Integer> matchedSlots(FoundryRecipeInput input) {
        List<Integer> slots = new ArrayList<>();
        boolean[] used = new boolean[input.size()];
        return collectSlots(input, 0, used, slots) ? List.copyOf(slots) : List.of();
    }

    private boolean collectSlots(FoundryRecipeInput input, int ingredientIndex,
                                 boolean[] used, List<Integer> slots) {
        if (ingredientIndex >= ingredients.size()) return true;
        FoundryIngredient wanted = ingredients.get(ingredientIndex);
        for (int slot = 0; slot < input.size(); slot++) {
            if (used[slot] || !wanted.test(input.getItem(slot))) continue;
            used[slot] = true;
            slots.add(slot);
            if (collectSlots(input, ingredientIndex + 1, used, slots)) return true;
            slots.remove(slots.size() - 1);
            used[slot] = false;
        }
        return false;
    }

    @Override
    public ItemStack assemble(FoundryRecipeInput input) {
        return result();
    }

    /** The finished stack, for the machine and the recipe viewers. */
    public ItemStack getResultItem() {
        return result();
    }

    /** The finished stack: built on demand, since a recipe is read before item components exist. */
    public ItemStack result() {
        return template.create();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    /** Never in the recipe book: the Foundry has no grid, and recipe viewers show it in their own category. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<QuantumFoundryRecipe> getSerializer() {
        return ModRecipeTypes.FOUNDRY_SERIALIZER.get();
    }

    @Override
    public RecipeType<QuantumFoundryRecipe> getType() {
        return ModRecipeTypes.FOUNDRY_TYPE.get();
    }

    public static final class Serializer {
        private static final Codec<FluxBand> BAND_CODEC = Codec.STRING.xmap(
                value -> FluxBand.valueOf(value.toUpperCase(Locale.ROOT)),
                band -> band.name().toLowerCase(Locale.ROOT));

        private static final MapCodec<QuantumFoundryRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        FoundryIngredient.CODEC.listOf().fieldOf("ingredients")
                                .forGetter(QuantumFoundryRecipe::ingredients),
                        Codec.INT.optionalFieldOf("minimum_pillars", 1)
                                .forGetter(QuantumFoundryRecipe::minimumPillars),
                        BAND_CODEC.optionalFieldOf("minimum_flux_band", FluxBand.MEDIUM)
                                .forGetter(QuantumFoundryRecipe::minimumFluxBand),
                        Codec.INT.fieldOf("duration").forGetter(QuantumFoundryRecipe::duration),
                        Codec.INT.fieldOf("fe_per_tick").forGetter(QuantumFoundryRecipe::fePerTick),
                        Codec.DOUBLE.optionalFieldOf("flux_cost", 0.0)
                                .forGetter(QuantumFoundryRecipe::fluxCost),
                        Codec.DOUBLE.optionalFieldOf("anomaly", 0.0)
                                .forGetter(QuantumFoundryRecipe::anomaly),
                        ItemStackTemplate.CODEC.fieldOf("result").forGetter(QuantumFoundryRecipe::template)
                ).apply(instance, QuantumFoundryRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, QuantumFoundryRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public QuantumFoundryRecipe decode(RegistryFriendlyByteBuf buffer) {
                        int size = buffer.readVarInt();
                        List<FoundryIngredient> ingredients = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                            ingredients.add(new FoundryIngredient(ingredient, buffer.readVarInt()));
                        }
                        int pillars = buffer.readVarInt();
                        FluxBand band = buffer.readEnum(FluxBand.class);
                        int duration = buffer.readVarInt();
                        int fePerTick = buffer.readVarInt();
                        double flux = buffer.readDouble();
                        double anomaly = buffer.readDouble();
                        ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buffer);
                        return new QuantumFoundryRecipe(ingredients, pillars, band, duration,
                                fePerTick, flux, anomaly, result);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, QuantumFoundryRecipe recipe) {
                        buffer.writeVarInt(recipe.ingredients.size());
                        for (FoundryIngredient ingredient : recipe.ingredients) {
                            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient.ingredient());
                            buffer.writeVarInt(ingredient.count());
                        }
                        buffer.writeVarInt(recipe.minimumPillars);
                        buffer.writeEnum(recipe.minimumFluxBand);
                        buffer.writeVarInt(recipe.duration);
                        buffer.writeVarInt(recipe.fePerTick);
                        buffer.writeDouble(recipe.fluxCost);
                        buffer.writeDouble(recipe.anomaly);
                        ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.template);
                    }
                };

        public static RecipeSerializer<QuantumFoundryRecipe> create() {
            return new RecipeSerializer<>(CODEC, STREAM_CODEC);
        }
    }
}
