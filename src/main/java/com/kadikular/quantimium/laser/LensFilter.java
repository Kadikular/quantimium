package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.recipe.FilterEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Filter lens lets the actions it modifies act on: items and blocks by its entries (a whitelist
 * or a blacklist, with tags), and living things by kind. An empty item list lets every item and block
 * through. A block counts by what it drops, so "wheat" means ripe wheat, and a field is cut ripe.
 *
 * @param entries ghost entries, as a Collector's filter's
 * @param allow   whitelist (only these) or blacklist (all but these)
 * @param living  what living things it lets an action reach
 */
public record LensFilter(List<ItemStack> entries, boolean allow, Living living) {

    public static final int SLOTS = 9;
    public static final LensFilter EMPTY = new LensFilter(List.of(), true, Living.ANY);

    /** Which living things an action may reach. */
    public enum Living implements StringRepresentable {
        ANY, HOSTILE, PASSIVE, PLAYERS, NOT_PLAYERS;

        public static final Codec<Living> CODEC = StringRepresentable.fromEnum(Living::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Living next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean lets(Entity entity) {
            return switch (this) {
                case ANY -> true;
                case HOSTILE -> entity instanceof Enemy;
                case PASSIVE -> entity instanceof LivingEntity && !(entity instanceof Enemy) && !(entity instanceof Player);
                case PLAYERS -> entity instanceof Player;
                case NOT_PLAYERS -> !(entity instanceof Player);
            };
        }
    }

    public static final Codec<LensFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("entries").forGetter(LensFilter::entries),
            Codec.BOOL.fieldOf("allow").forGetter(LensFilter::allow),
            Living.CODEC.fieldOf("living").forGetter(LensFilter::living)
    ).apply(instance, LensFilter::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LensFilter> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), LensFilter::entries,
            ByteBufCodecs.BOOL, LensFilter::allow,
            ByteBufCodecs.idMapper(i -> Living.values()[Math.floorMod(i, Living.values().length)], Living::ordinal).cast(), LensFilter::living,
            LensFilter::new);

    public LensFilter {
        entries = List.copyOf(entries);
    }

    /** Whether any item entry is set. */
    public boolean hasEntries() {
        return entries.stream().anyMatch(entry -> !entry.isEmpty());
    }

    /** Whether it lets an action carry, place or reach this item. */
    public boolean lets(ItemStack stack) {
        if (!hasEntries()) return true;
        boolean listed = entries.stream().anyMatch(entry -> FilterEntry.matches(entry, stack));
        return listed == allow;
    }

    /**
     * Whether it lets a Harvest beam break this block, which would drop {@code drops}: listed if its
     * item or any drop is. So "wheat" lets only ripe wheat through: unripe, it drops only seeds.
     */
    public boolean lets(BlockState state, List<ItemStack> drops) {
        if (!hasEntries()) return true;
        boolean listed = listed(new ItemStack(state.getBlock().asItem()));
        for (ItemStack drop : drops) listed |= listed(drop);
        return listed == allow;
    }

    private boolean listed(ItemStack stack) {
        return !stack.isEmpty() && entries.stream().anyMatch(entry -> FilterEntry.matches(entry, stack));
    }

    public LensFilter withEntry(int slot, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(entries);
        while (list.size() < SLOTS) list.add(ItemStack.EMPTY);
        list.set(slot, stack.isEmpty() ? ItemStack.EMPTY : FilterEntry.of(stack));
        return new LensFilter(list, allow, living);
    }

    public LensFilter cycledTag(int slot) {
        if (slot >= entries.size() || entries.get(slot).isEmpty()) return this;
        List<ItemStack> list = new ArrayList<>(entries);
        list.set(slot, FilterEntry.cycle(list.get(slot)));
        return new LensFilter(list, allow, living);
    }

    public ItemStack entry(int slot) {
        return slot < entries.size() ? entries.get(slot) : ItemStack.EMPTY;
    }
}
