package com.kadikular.quantimium.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A ghost filter entry: an item, or (once shift-clicked) one of that item's tags. The tag rides on the
 * ghost stack itself, so it is saved and synced wherever the stack is, and the slot keeps showing the
 * item it was chosen from.
 */
public final class FilterEntry {

    private static final String TAG_KEY = "quantimium_filter_tag";

    private FilterEntry() {}

    /** The tag this entry stands for, if it has been switched to one. */
    public static Optional<TagKey<Item>> tagOf(ItemStack entry) {
        CustomData data = entry.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_KEY)) return Optional.empty();
        Identifier id = Identifier.tryParse(tag.getStringOr(TAG_KEY, ""));
        return id == null ? Optional.empty() : Optional.of(TagKey.create(Registries.ITEM, id));
    }

    /** Whether {@code item} is what this entry lists: the same item, or any item in its tag. */
    public static boolean matches(ItemStack entry, ItemStack item) {
        if (entry.isEmpty() || item.isEmpty()) return false;
        return tagOf(entry).map(item::is).orElseGet(() -> item.is(entry.getItem()));
    }

    /** A plain entry for {@code stack}: one of it, no tag. */
    public static ItemStack of(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(stack.getItem());
    }

    /**
     * The next step when an entry is shift-clicked: from the item to its first tag, through each tag in
     * turn, and back to the item. Convention ({@code c:}) tags come first, broadest first, so a first
     * click on iron ore gives {@code #c:ores}.
     */
    public static ItemStack cycle(ItemStack entry) {
        if (entry.isEmpty()) return entry;
        List<TagKey<Item>> tags = tags(entry);
        if (tags.isEmpty()) return of(entry);
        Optional<TagKey<Item>> current = tagOf(entry);
        int next = current.map(tags::indexOf).orElse(-1) + 1;
        if (next >= tags.size()) return of(entry);
        ItemStack out = of(entry);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KEY, tags.get(next).location().toString());
        out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return out;
    }

    /** The item's tags in cycling order. */
    public static List<TagKey<Item>> tags(ItemStack stack) {
        return stack.typeHolder().tags()
                .sorted(Comparator.<TagKey<Item>, Boolean>comparing(key -> !key.location().getNamespace().equals("c"))
                        .thenComparing(key -> key.location().getPath().length())
                        .thenComparing(key -> key.location().toString()))
                .toList();
    }
}
