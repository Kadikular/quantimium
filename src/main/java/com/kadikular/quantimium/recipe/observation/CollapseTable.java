package com.kadikular.quantimium.recipe.observation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.unrealised.PackOres;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a Chamber can collapse Matter into, for a recipe viewer: the pack's ores with their chance in
 * each band (see PackOres), or, where the pack uses the hand-made loot table, its weighted entries.
 *
 * <p>Loot tables are server-only, so this runs on the server and the result is sent to clients.
 * There is no public accessor for a table's pools, so the live table is re-encoded through
 * {@link LootTable#DIRECT_CODEC} and the resulting JSON is walked. Going through the codec rather
 * than reading the shipped file keeps datapack overrides honest.
 */
public final class CollapseTable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CollapseTable() {}

    public static List<CollapseOutcome> read(MinecraftServer server) {
        if (PackOres.inUse()) return fromPackOres(server);
        return fromLootTable(server);
    }

    /** Each of the pack's ores, with its rarity and its chance in each band. */
    private static List<CollapseOutcome> fromPackOres(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<CollapseOutcome> outcomes = new ArrayList<>();
        for (PackOres.Ore ore : PackOres.ores()) {
            List<ItemStack> drops = PackOres.drops(level, ore, BlockPos.ZERO);
            if (drops.isEmpty()) continue;
            List<Float> chances = new ArrayList<>(FluxBand.values().length);
            for (FluxBand band : FluxBand.values()) chances.add((float) PackOres.chance(ore, band));
            outcomes.add(new CollapseOutcome(drops.getFirst(), ore.rarity().ordinal(), List.copyOf(chances)));
        }
        return List.copyOf(outcomes);
    }

    private static List<CollapseOutcome> fromLootTable(MinecraftServer server) {
        LootTable table = server.reloadableRegistries()
                .getLootTable(ObservationChamberBlockEntity.collapseLootKey());
        if (table == LootTable.EMPTY) return List.of();

        JsonElement json = LootTable.DIRECT_CODEC
                .encodeStart(JsonOps.INSTANCE, table)
                .resultOrPartial(error -> LOGGER.warn(
                        "Could not read the collapse loot table for recipe viewers: {}", error))
                .orElse(null);
        if (json == null || !json.isJsonObject()) return List.of();

        List<CollapseOutcome> outcomes = new ArrayList<>();
        JsonElement pools = json.getAsJsonObject().get("pools");
        if (pools == null || !pools.isJsonArray()) return List.of();
        for (JsonElement pool : pools.getAsJsonArray()) {
            if (!pool.isJsonObject()) continue;
            JsonElement entries = pool.getAsJsonObject().get("entries");
            if (entries == null || !entries.isJsonArray()) continue;
            collectEntries(entries.getAsJsonArray(), outcomes);
        }
        // Weights to chances, the same in every band: a hand-made table ignores the field.
        float total = 0.0f;
        for (CollapseOutcome outcome : outcomes) total += outcome.chances().getFirst();
        List<CollapseOutcome> shared = new ArrayList<>(outcomes.size());
        for (CollapseOutcome outcome : outcomes) {
            float chance = total <= 0.0f ? 0.0f : outcome.chances().getFirst() / total;
            shared.add(new CollapseOutcome(outcome.stack(), -1, Collections.nCopies(FluxBand.values().length, chance)));
        }
        return List.copyOf(shared);
    }

    /**
     * Only plain {@code minecraft:item} entries are listed. Nested groups, alternatives and tag
     * entries are skipped rather than guessed at: a wrong chance is worse than a missing row.
     */
    private static void collectEntries(JsonArray entries, List<CollapseOutcome> outcomes) {
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("type") || !entry.has("name")) continue;
            if (!"minecraft:item".equals(entry.get("type").getAsString())) continue;

            Identifier id = Identifier.tryParse(entry.get("name").getAsString());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.getValue(id);
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            if (weight <= 0) continue;
            outcomes.add(new CollapseOutcome(new ItemStack(item), -1, List.of((float) weight)));
        }
    }
}
