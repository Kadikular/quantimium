package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A search box's query, read as JEI reads its own: words that must all match (in any order), {@code |} between
 * words any of which may, {@code -} before a word that mustn't. A word matches the item's name, or with a prefix:
 * {@code @} its mod (id or name), {@code #} its tooltip, {@code $} its tags. So {@code @mekanism ingot} is
 * Mekanism's ingots, {@code -@minecraft} everything not vanilla, {@code $c:ingots|$c:gems} ingots and gems.
 */
public final class ItemSearch {
    private ItemSearch() {}

    /** Mod names by id: looked up once. */
    private static final Map<String, String> MOD_NAMES = new HashMap<>();

    /** What {@code query} matches; everything if it's blank. */
    public static Predicate<ItemStack> compile(String query) {
        List<Predicate<ItemStack>> all = new ArrayList<>();
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.isEmpty()) continue;
            List<Predicate<ItemStack>> any = new ArrayList<>();
            for (String option : word.split("\\|")) {
                if (!option.isEmpty()) any.add(term(option));
            }
            if (any.isEmpty()) continue;
            all.add(any.size() == 1 ? any.getFirst() : stack -> {
                for (Predicate<ItemStack> one : any) if (one.test(stack)) return true;
                return false;
            });
        }
        if (all.isEmpty()) return stack -> true;
        return stack -> {
            for (Predicate<ItemStack> one : all) if (!one.test(stack)) return false;
            return true;
        };
    }

    private static Predicate<ItemStack> term(String option) {
        if (option.startsWith("-") && option.length() > 1) return term(option.substring(1)).negate();
        char prefix = option.charAt(0);
        String text = option.substring(1);
        if (text.isEmpty() && (prefix == '@' || prefix == '#' || prefix == '$')) return stack -> true;
        return switch (prefix) {
            case '@' -> stack -> {
                String mod = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
                return mod.contains(text) || modName(mod).contains(text);
            };
            case '#' -> stack -> tooltip(stack).contains(text);
            case '$' -> stack -> stack.typeHolder().tags().anyMatch(tag -> tag.location().toString().contains(text));
            default -> stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(option);
        };
    }

    private static String modName(String id) {
        return MOD_NAMES.computeIfAbsent(id, key -> ModList.get().getModContainerById(key)
                .map(container -> container.getModInfo().getDisplayName().toLowerCase(Locale.ROOT)).orElse(key));
    }

    /** Tooltips by item: worked out once a search needs one, as JEI does, and kept while the game runs. */
    private static final Map<Item, String> TOOLTIPS = new HashMap<>();

    private static String tooltip(ItemStack stack) {
        // Items with components (enchanted books, potions) read differently each; the rest once per item.
        if (!stack.getComponentsPatch().isEmpty()) return tooltipOf(stack);
        return TOOLTIPS.computeIfAbsent(stack.getItem(), item -> tooltipOf(stack));
    }

    private static String tooltipOf(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return "";
        StringBuilder text = new StringBuilder();
        List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.Default.NORMAL);
        for (int i = 1; i < lines.size(); i++) text.append(lines.get(i).getString()).append('\n');
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
