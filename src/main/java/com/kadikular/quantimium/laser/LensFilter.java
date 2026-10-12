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
 * @param entries ghost entries, as a collecting Laser Port's filter's
 * @param allow   whitelist (only these) or blacklist (all but these)
 * @param living  what living things it lets an action reach
 * @param grown   only what's fully grown: a Harvest beam cuts only crops (and anything else that grows)
 *                that are ripe, and nothing that doesn't grow; a Damage beam spares the young
 * @param keep    how many of what a Damage beam would hit it spares: it hits only those beyond that many
 */
public record LensFilter(List<ItemStack> entries, boolean allow, Living living, boolean grown, int keep) {

    public static final int MAX_KEEP = 64;

    public LensFilter(List<ItemStack> entries, boolean allow, Living living) {
        this(entries, allow, living, false, 0);
    }

    public LensFilter(List<ItemStack> entries, boolean allow, Living living, boolean grown) {
        this(entries, allow, living, grown, 0);
    }

    public LensFilter withKeep(int keep) {
        return new LensFilter(entries, allow, living, grown, Math.clamp(keep, 0, MAX_KEEP));
    }

    public static final int SLOTS = 9;
    public static final LensFilter EMPTY = new LensFilter(List.of(), true, Living.ANY, false, 0);

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
            Living.CODEC.fieldOf("living").forGetter(LensFilter::living),
            Codec.BOOL.optionalFieldOf("grown", false).forGetter(LensFilter::grown),
            Codec.INT.optionalFieldOf("keep", 0).forGetter(LensFilter::keep)
    ).apply(instance, LensFilter::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LensFilter> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), LensFilter::entries,
            ByteBufCodecs.BOOL, LensFilter::allow,
            ByteBufCodecs.idMapper(i -> Living.values()[Math.floorMod(i, Living.values().length)], Living::ordinal).cast(), LensFilter::living,
            ByteBufCodecs.BOOL, LensFilter::grown,
            ByteBufCodecs.VAR_INT, LensFilter::keep,
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
    public boolean lets(BlockState state, java.util.function.Supplier<List<ItemStack>> drops) {
        // Cheap first: ripeness, then the block's own item; what it drops only if that's still undecided.
        if (grown && !ripe(state)) return false;
        if (!hasEntries()) return true;
        boolean listed = listed(new ItemStack(state.getBlock().asItem()));
        if (!listed) for (ItemStack drop : drops.get()) listed |= listed(drop);
        return listed == allow;
    }

    /** Whether the block grows and is fully grown: at the top of its age. */
    private static boolean ripe(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) return crop.isMaxAge(state);
        for (var property : state.getProperties()) {
            if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age && property.getName().equals("age")) {
                return state.getValue(age) >= age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            }
        }
        return false;
    }

    /** Whether it lets a hit land on {@code entity}: its kind, and with Grown, never the young. */
    public boolean letsHit(Entity entity) {
        if (grown && entity instanceof net.minecraft.world.entity.AgeableMob mob && mob.isBaby()) return false;
        return living.lets(entity);
    }

    private boolean listed(ItemStack stack) {
        return !stack.isEmpty() && entries.stream().anyMatch(entry -> FilterEntry.matches(entry, stack));
    }

    public LensFilter withEntry(int slot, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(entries);
        while (list.size() < SLOTS) list.add(ItemStack.EMPTY);
        list.set(slot, stack.isEmpty() ? ItemStack.EMPTY : FilterEntry.of(stack));
        return new LensFilter(list, allow, living, grown, keep);
    }

    public LensFilter cycledTag(int slot) {
        if (slot >= entries.size() || entries.get(slot).isEmpty()) return this;
        List<ItemStack> list = new ArrayList<>(entries);
        list.set(slot, FilterEntry.cycle(list.get(slot)));
        return new LensFilter(list, allow, living, grown, keep);
    }

    public ItemStack entry(int slot) {
        return slot < entries.size() ? entries.get(slot) : ItemStack.EMPTY;
    }
}
