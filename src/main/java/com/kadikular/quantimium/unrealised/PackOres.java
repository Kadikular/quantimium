package com.kadikular.quantimium.unrealised;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.flux.FluxBand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Unrealised Matter can collapse into (game plan E1): every ore in the pack's {@code #c:ores/*}
 * tags, so in a big pack Matter is "any ore in this pack" with no per-mod work. One Matter is one ore
 * block's worth: a collapse rolls the ore's own drops as if mined with a plain pickaxe, so raw iron,
 * four or five redstone, a diamond.
 *
 * <p>Which ore comes out goes by {@link Rarity} and the flux band where it's observed: a band gives
 * each rarity a share of the rolls ({@link Config#rarityShares}), split evenly among that rarity's
 * ores. So forty common ores in a pack don't bury its diamonds, and a hotter field turns up rarer ones.
 * Rebuilt whenever tags load.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class PackOres {

    /** One kind of ore: its tag, the block that stands for it, and its rarity. */
    public record Ore(TagKey<Block> tag, BlockState state, Rarity rarity) {}

    private static List<Ore> ores = List.of();
    /** Each ore's average drops, worked out once per reload. */
    private static final Map<Identifier, List<Form>> EXPECTED = new HashMap<>();
    private static Map<Rarity, List<Ore>> byRarity = Map.of();

    private PackOres() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) rebuild();
    }

    /** Reads every {@code #c:ores/*} tag: one ore per tag, stood for by its first block. */
    public static void rebuild() {
        List<Ore> found = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTags().forEach(named -> {
            TagKey<Block> tag = named.key();
            if (!tag.location().getNamespace().equals("c") || !tag.location().getPath().startsWith("ores/")) return;
            BlockState state = representative(named);
            if (state == null || state.is(Rarity.EXCLUDED)) return;
            found.add(new Ore(tag, state, Rarity.of(state)));
        });
        found.sort(Comparator.comparing(ore -> ore.tag().location().toString()));
        Map<Rarity, List<Ore>> grouped = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) grouped.put(rarity, new ArrayList<>());
        for (Ore ore : found) grouped.get(ore.rarity()).add(ore);
        ores = List.copyOf(found);
        byRarity = grouped;
        EXPECTED.clear();
        Quantimium.LOGGER.info("Unrealised Matter can collapse into {} ores", ores.size());
    }

    /** The tag's first block: vanilla stone ores are listed before their deepslate twins. */
    @Nullable
    private static BlockState representative(HolderSet.Named<Block> blocks) {
        for (Holder<Block> holder : blocks) {
            BlockState state = holder.value().defaultBlockState();
            if (!state.isAir()) return state;
        }
        return null;
    }

    public static List<Ore> ores() {
        return ores;
    }

    /** The chance that an observation in {@code band} lands on {@code ore}. */
    public static double chance(Ore ore, FluxBand band) {
        double[] shares = shares(band);
        List<Ore> same = byRarity.getOrDefault(ore.rarity(), List.of());
        return same.isEmpty() ? 0.0 : shares[ore.rarity().ordinal()] / same.size();
    }

    /**
     * Each rarity's share of the rolls in {@code band}, normalised over the rarities that have any
     * ores: a pack with no very rare ores gives their share to the rest, rather than to nothing.
     */
    private static double[] shares(FluxBand band) {
        List<? extends Integer> configured = Config.rarityShares(band);
        double[] shares = new double[Rarity.values().length];
        double total = 0.0;
        for (Rarity rarity : Rarity.values()) {
            if (byRarity.getOrDefault(rarity, List.of()).isEmpty()) continue;
            shares[rarity.ordinal()] = Math.max(0, configured.get(rarity.ordinal()));
            total += shares[rarity.ordinal()];
        }
        if (total <= 0.0) return shares;
        for (int i = 0; i < shares.length; i++) shares[i] /= total;
        return shares;
    }

    /** {@code rarity}'s share of the rolls in {@code band}, 0 to 1, over the rarities the pack has. */
    public static double share(Rarity rarity, FluxBand band) {
        return shares(band)[rarity.ordinal()];
    }

    /** The pack's ore with tag {@code id}, or null. */
    @Nullable
    public static Ore byTag(Identifier id) {
        for (Ore ore : ores) {
            if (ore.tag().location().equals(id)) return ore;
        }
        return null;
    }

    /** An ore picked for an observation in {@code band}; null if the pack has none it can give. */
    @Nullable
    public static Ore roll(RandomSource random, FluxBand band) {
        double[] shares = shares(band);
        double pick = random.nextDouble();
        for (Rarity rarity : Rarity.values()) {
            double share = shares[rarity.ordinal()];
            if (share <= 0.0) continue;
            if (pick < share) {
                List<Ore> same = byRarity.get(rarity);
                return same.get(random.nextInt(same.size()));
            }
            pick -= share;
        }
        return null;
    }

    /** What {@code ore} drops mined with a plain pickaxe at {@code pos}: its own loot, rolled afresh. */
    public static List<ItemStack> drops(ServerLevel level, Ore ore, BlockPos pos) {
        List<ItemStack> drops = Block.getDrops(ore.state(), level, pos, null, null, new ItemStack(Items.NETHERITE_PICKAXE));
        List<ItemStack> kept = new ArrayList<>(drops.size());
        for (ItemStack stack : drops) if (!stack.isEmpty()) kept.add(stack);
        return kept;
    }

    /** Rolls of an ore's loot averaged for its expected drops: enough that the average barely moves. */
    private static final int EXPECTED_ROLLS = 1024;

    /**
     * What {@code ore} drops on average, mined with a plain pickaxe: its loot rolled with fixed seeds, so
     * the same every time. Copper ore gives 3.5 raw copper, redstone ore 4.5 redstone.
     */
    public static List<Form> expectedDrops(ServerLevel level, Ore ore) {
        return EXPECTED.computeIfAbsent(ore.tag().location(), key -> {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(ore.state().getBlock().getLootTable().orElseThrow());
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.NETHERITE_PICKAXE))
                    .withParameter(LootContextParams.BLOCK_STATE, ore.state())
                    .create(LootContextParamSets.BLOCK);
            Map<Item, Double> totals = new LinkedHashMap<>();
            Map<Item, ItemStack> kinds = new LinkedHashMap<>();
            // Well-mixed seeds from one fixed generator: consecutive small seeds give correlated first
            // rolls (seeds 1 to 256 average copper at 4, not 3.5), and a seed of 0 means none at all.
            RandomSource seeds = RandomSource.create(0x5EED_0F_0DE5L);
            for (int roll = 0; roll < EXPECTED_ROLLS; roll++) {
                long seed = seeds.nextLong();
                for (ItemStack stack : table.getRandomItems(params, seed == 0L ? 1L : seed)) {
                    if (stack.isEmpty()) continue;
                    totals.merge(stack.getItem(), (double) stack.getCount(), Double::sum);
                    kinds.putIfAbsent(stack.getItem(), stack);
                }
            }
            List<Form> forms = new ArrayList<>();
            for (Map.Entry<Item, Double> entry : totals.entrySet()) {
                forms.add(new Form(kinds.get(entry.getKey()), entry.getValue() / EXPECTED_ROLLS));
            }
            return List.copyOf(forms);
        });
    }

    /** Whether collapses come from the pack's ores (the default) rather than the hand-made loot table. */
    public static boolean inUse() {
        return Config.collapseSource() == Config.CollapseSource.PACK_ORES && !ores.isEmpty();
    }
}
