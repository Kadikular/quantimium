package com.kadikular.quantimium.recipe.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kadikular.quantimium.Quantimium;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads recipe adapters from datapacks ({@code data/<ns>/recipe_adapters/*.json}) and from
 * {@code config/quantimium/recipe_adapters/*.json}. Config files override datapack entries that
 * declare the same adapter id (file name).
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class RecipeAdapterRegistry extends SimpleJsonResourceReloadListener<JsonElement> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "recipe_adapters";

    /** Written once into the config folder as a starting point for pack makers. */
    private static final String EXAMPLE_ADAPTER = """
            {
              "_comment": [
                "Rename this file (drop the leading underscore) or add sibling *.json files to teach",
                "the Quantum Crafter about another mod's item recipes without writing Java.",
                "Datapack adapters under data/<namespace>/recipe_adapters/ also work (/reload).",
                "",
                "input_mode: ingredients | field",
                "input_field / input_fields: recipe Java field(s) when input_mode is field",
                "output_mode: result_item | field",
                "output_field / output_fields: recipe field(s) when output_mode is field; only guaranteed outputs count",
                "catalysts: machines that run these recipe types; catalyst_also: other types they run",
                "non_consumed_tags / non_consumed_items: presses/tools that must be present but are not spent",
                "energy.flat: base FE before the crafter's instant-craft tax; energy.field (x energy.scale) reads the recipe's own"
              ],
              "recipe_types": ["examplemod:grinder"],
              "input_mode": "ingredients",
              "input_field": "ingredient",
              "output_mode": "result_item",
              "output_field": "result",
              "skip_empty_ingredients": true,
              "non_consumed_tags": [],
              "non_consumed_items": [],
              "energy": { "flat": 500 }
            }
            """;

    public static final RecipeAdapterRegistry INSTANCE = new RecipeAdapterRegistry();

    private volatile Map<Identifier, List<RecipeAdapter>> byType = Map.of();
    private volatile List<RecipeAdapter> all = List.of();

    private RecipeAdapterRegistry() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(DIRECTORY));
    }

    /**
     * Reads every adapter file, as the vanilla loader would, but defensively: a pack that answers with
     * files from outside {@code recipe_adapters/} (Potions Master's generated pack does) has them
     * skipped, and a duplicate is logged and skipped. Vanilla throws on a duplicate, which here stopped
     * the whole data load and left world creation hanging.
     */
    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        FileToIdConverter files = FileToIdConverter.json(DIRECTORY);
        Map<Identifier, JsonElement> read = new HashMap<>();
        for (Map.Entry<Identifier, net.minecraft.server.packs.resources.Resource> entry : files.listMatchingResources(manager).entrySet()) {
            Identifier file = entry.getKey();
            if (!file.getPath().startsWith(DIRECTORY + "/") || !file.getPath().endsWith(".json")) continue;
            Identifier id = files.fileToId(file);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                if (json == null) continue;
                if (read.putIfAbsent(id, json) != null) LOGGER.warn("Skipped a second recipe adapter {} from {}", id, file);
            } catch (Exception e) {
                LOGGER.error("Couldn't read recipe adapter {}", file, e);
            }
        }
        return read;
    }

    public List<RecipeAdapter> adaptersFor(RecipeType<?> type) {
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (id == null) return List.of();
        return byType.getOrDefault(id, List.of());
    }

    public List<RecipeAdapter> all() {
        return all;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<Identifier, RecipeAdapter> merged = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            try {
                if (!entry.getValue().isJsonObject()) continue;
                RecipeAdapter adapter = RecipeAdapter.parse(entry.getKey(), entry.getValue().getAsJsonObject());
                merged.put(entry.getKey(), adapter);
            } catch (Exception e) {
                LOGGER.error("Failed to parse recipe adapter {}", entry.getKey(), e);
            }
        }

        loadConfigOverrides(merged);
        publish(merged);
        com.kadikular.quantimium.recipe.CatalystResolver.clearCache();
        LOGGER.info("Loaded {} Quantum Crafter recipe adapter(s)", merged.size());
    }

    private static void loadConfigOverrides(Map<Identifier, RecipeAdapter> merged) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(Quantimium.MODID).resolve(DIRECTORY);
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("_example.json");
            if (!Files.exists(readme)) {
                Files.writeString(readme, EXAMPLE_ADAPTER);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not create {}", dir, e);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (fileName.startsWith("_")) continue;
                String baseName = fileName.substring(0, fileName.length() - ".json".length());
                Identifier id = Identifier.fromNamespaceAndPath(Quantimium.MODID, "config/" + baseName);
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;
                    RecipeAdapter adapter = RecipeAdapter.parse(id, json);
                    merged.put(id, adapter);
                    LOGGER.info("Loaded config recipe adapter {}", path.getFileName());
                } catch (Exception e) {
                    LOGGER.error("Failed to parse config recipe adapter {}", path, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {}", dir, e);
        }
    }

    private void publish(Map<Identifier, RecipeAdapter> merged) {
        Map<Identifier, List<RecipeAdapter>> index = new HashMap<>();
        List<RecipeAdapter> list = new ArrayList<>(merged.values());
        for (RecipeAdapter adapter : list) {
            for (Identifier type : adapter.recipeTypes()) {
                index.computeIfAbsent(type, key -> new ArrayList<>()).add(adapter);
            }
        }
        Map<Identifier, List<RecipeAdapter>> frozen = new HashMap<>();
        for (Map.Entry<Identifier, List<RecipeAdapter>> entry : index.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.byType = Collections.unmodifiableMap(frozen);
        this.all = List.copyOf(list);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Quantimium.MODID, "recipe_adapters"), INSTANCE);
    }
}
