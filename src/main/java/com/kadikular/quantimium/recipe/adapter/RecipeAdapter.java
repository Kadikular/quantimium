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
 * <p>This covers item-shaped machines. Outputs read from fields keep only what's guaranteed: a chance
 * below one, or a percentage roll that can miss, isn't counted ({@link GuaranteedOutputs}). Fluids,
 * gases and exotic energy units still need a code family (see {@code MiMachineFamily}).
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
        List<Identifier> catalystAlso
) {
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
        if (inputFields.isEmpty()) inputFields.add(inputField);
        OutputMode outputMode = OutputMode.valueOf(
                GsonHelper.getAsString(json, "output_mode", "result_item").toUpperCase());
        String outputField = GsonHelper.getAsString(json, "output_field", "result");
        List<String> outputFields = strings(json, "output_fields");
        if (outputFields.isEmpty()) outputFields = List.of(outputField);
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
                new EnergySpec(flat, energyField, scale), catalysts, also);
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        if (!json.has(key)) return list;
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key)) list.add(GsonHelper.convertToString(element, key));
        return list;
    }
}
