package com.kadikular.quantimium.recipe.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative description of how to read a foreign {@link net.minecraft.world.item.crafting.Recipe}
 * as item inputs/outputs for the Quantum Crafter. Loaded from datapacks and
 * {@code config/quantimium/recipe_adapters/*.json}.
 *
 * <p>Outputs read from fields keep only what's guaranteed: a chance below one, or a percentage roll that can
 * miss, isn't counted ({@link GuaranteedOutputs}). Fluids are found by their fields' types, or read from
 * {@code fluid_input_fields} and {@code fluid_output_fields} where named. Gases and exotic energy units still need a
 * code family (see {@code compat.mi.MiMachineFamily}).
 *
 * @param catalysts      machines that run these recipe types, for machines whose names don't say so
 *                       (EnderIO's Alloy Smelter runs {@code enderio:alloy_smelting})
 * @param catalystAlso   other recipe types those machines also run, such as vanilla smelting
 */
public record RecipeAdapter(
        Identifier id,
        List<Identifier> recipeTypes,
        InputMode inputMode,
        String inputField,
        List<String> inputFields,
        OutputMode outputMode,
        String outputField,
        List<String> outputFields,
        boolean skipEmptyIngredients,
        List<TagKey<Item>> nonConsumedTags,
        List<Identifier> nonConsumedItems,
        EnergySpec energy,
        List<Identifier> catalysts,
        List<Identifier> catalystAlso,
        List<String> fluidInputFields,
        List<String> fluidOutputFields,
        java.util.Map<String, Identifier> fluidAmountFields
) {
    /**
     * The fluids a recipe of its types takes and gives: from the fields it names, or found by their types; and as many
     * mB of a fluid as each of {@code fluid_amount_fields} says (Energized Power's Stone Solidifier's water and lava).
     */
    public com.kadikular.quantimium.recipe.FluidParts.Parts fluids(Object recipe) {
        com.kadikular.quantimium.recipe.FluidParts.Parts parts = fluidInputFields.isEmpty() && fluidOutputFields.isEmpty()
                ? com.kadikular.quantimium.recipe.FluidParts.of(recipe)
                : com.kadikular.quantimium.recipe.FluidParts.of(recipe, fluidInputFields, fluidOutputFields);
        if (fluidAmountFields.isEmpty()) return parts;
        return com.kadikular.quantimium.recipe.FluidParts.withAmounts(recipe, parts, fluidAmountFields);
    }

    public enum InputMode {
        /** {@link net.minecraft.world.item.crafting.Recipe#getIngredients()}. */
        INGREDIENTS,
        /** Single {@link net.minecraft.world.item.crafting.Ingredient} field on the recipe class. */
        FIELD
    }

    public enum OutputMode {
        /** {@link net.minecraft.world.item.crafting.Recipe#getResultItem(net.minecraft.core.HolderLookup.Provider)}. */
        RESULT_ITEM,
        /** {@link net.minecraft.world.item.ItemStack} field on the recipe class. */
        FIELD
    }

    /** A flat fee, or a recipe field read and scaled (an EnderIO recipe's {@code energy}), the flat fee if it can't be read. */
    public record EnergySpec(int flat, String field, double scale) {
        public EnergySpec {
            flat = Math.max(0, flat);
        }

        public EnergySpec(int flat) {
            this(flat, null, 1.0);
        }
    }

    public static RecipeAdapter parse(Identifier id, JsonObject json) {
        List<Identifier> types = new ArrayList<>();
        JsonArray typeArray = GsonHelper.getAsJsonArray(json, "recipe_types");
        for (JsonElement element : typeArray) {
            types.add(Identifier.parse(GsonHelper.convertToString(element, "recipe_types")));
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException(id + " needs at least one recipe_types entry");
        }

        InputMode inputMode = InputMode.valueOf(
                GsonHelper.getAsString(json, "input_mode", "ingredients").toUpperCase());
        String inputField = GsonHelper.getAsString(json, "input_field", "ingredient");
        List<String> inputFields = new ArrayList<>();
        if (json.has("input_fields")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "input_fields")) {
                inputFields.add(GsonHelper.convertToString(element, "input_fields"));
            }
        }
        // Named as none (a recipe taking only fluids): none, not the default.
        if (inputFields.isEmpty() && !json.has("input_fields")) inputFields.add(inputField);
        OutputMode outputMode = OutputMode.valueOf(
                GsonHelper.getAsString(json, "output_mode", "result_item").toUpperCase());
        String outputField = GsonHelper.getAsString(json, "output_field", "result");
        List<String> outputFields = strings(json, "output_fields");
        if (outputFields.isEmpty() && !json.has("output_fields")) outputFields = List.of(outputField);
        boolean skipEmpty = GsonHelper.getAsBoolean(json, "skip_empty_ingredients", true);

        List<TagKey<Item>> tags = new ArrayList<>();
        if (json.has("non_consumed_tags")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "non_consumed_tags")) {
                tags.add(TagKey.create(Registries.ITEM,
                        Identifier.parse(GsonHelper.convertToString(element, "non_consumed_tags"))));
            }
        }

        List<Identifier> items = new ArrayList<>();
        if (json.has("non_consumed_items")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "non_consumed_items")) {
                items.add(Identifier.parse(
                        GsonHelper.convertToString(element, "non_consumed_items")));
            }
        }

        int flat = 500;
        String energyField = null;
        double scale = 1.0;
        if (json.has("energy") && json.get("energy").isJsonObject()) {
            JsonObject energy = json.getAsJsonObject("energy");
            flat = GsonHelper.getAsInt(energy, "flat", flat);
            energyField = energy.has("field") ? GsonHelper.getAsString(energy, "field") : null;
            scale = GsonHelper.getAsDouble(energy, "scale", 1.0);
        } else if (json.has("energy_flat")) {
            flat = GsonHelper.getAsInt(json, "energy_flat", flat);
        }

        List<Identifier> catalysts = strings(json, "catalysts").stream().map(Identifier::parse).toList();
        List<Identifier> also = strings(json, "catalyst_also").stream().map(Identifier::parse).toList();
        return new RecipeAdapter(id, List.copyOf(types), inputMode, inputField, List.copyOf(inputFields),
                outputMode, outputField, List.copyOf(outputFields), skipEmpty, List.copyOf(tags), List.copyOf(items),
                new EnergySpec(flat, energyField, scale), catalysts, also,
                List.copyOf(strings(json, "fluid_input_fields")), List.copyOf(strings(json, "fluid_output_fields")), amounts(json));
    }

    /** {@code fluid_amount_fields}: each field's name, and the fluid whose mB it holds. */
    private static java.util.Map<String, Identifier> amounts(JsonObject json) {
        java.util.Map<String, Identifier> amounts = new java.util.LinkedHashMap<>();
        if (!json.has("fluid_amount_fields")) return amounts;
        for (var entry : GsonHelper.getAsJsonObject(json, "fluid_amount_fields").entrySet()) {
            amounts.put(entry.getKey(), Identifier.parse(GsonHelper.convertToString(entry.getValue(), entry.getKey())));
        }
        return java.util.Map.copyOf(amounts);
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        if (!json.has(key)) return list;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) list.add(GsonHelper.convertToString(element, key));
        return list;
    }
}
