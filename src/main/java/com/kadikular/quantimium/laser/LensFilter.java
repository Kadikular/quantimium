package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.recipe.FilterEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What filter lenses let the actions they modify act on. There are three lenses, one per part: items (what's
 * carried, placed, put in or taken out), blocks (what a Harvest beam cuts) and entities (what a beam hits). Each
 * lens sets only its own part, so an Item and a Block lens on one beam both apply; a later lens of the same kind
 * replaces the earlier one's part. A part that's not set lets everything through, as does an empty list.
 *
 * @param parts       which parts are set: {@link #ITEMS}, {@link #BLOCKS}, {@link #ENTITIES}
 * @param items       item ghost entries, with tags, as a Laser Port's filter's
 * @param itemsAllow  allow (only these) or deny (all but these)
 * @param blocks      block entries: a block counts by its own item or any it drops, so "wheat" means ripe wheat
 * @param blocksAllow allow or deny
 * @param ripe        only blocks that grow and are fully grown
 * @param mobs        spawn eggs: the kinds of mob listed
 * @param mobsAllow   allow (only those) or deny (all but those)
 * @param kinds       which living things: {@link #HOSTILE}, {@link #ANIMALS}, {@link #PLAYERS}; none set, any
 * @param spareYoung  never hit the young
 * @param keep        how many of what a Damage beam would hit it spares: it hits only those beyond that many
 */
public record LensFilter(int parts, List<ItemStack> items, boolean itemsAllow, List<ItemStack> blocks, boolean blocksAllow,
                         boolean ripe, List<ItemStack> mobs, boolean mobsAllow, int kinds, boolean spareYoung, int keep) {

    public static final int ITEMS = 1;
    public static final int BLOCKS = 2;
    public static final int ENTITIES = 4;

    public static final int HOSTILE = 1;
    public static final int ANIMALS = 2;
    public static final int PLAYERS = 4;

    public static final int SLOTS = 9;
    public static final int MAX_KEEP = 64;
    public static final LensFilter EMPTY = new LensFilter(0, List.of(), true, List.of(), true, false, List.of(), true, 0, false, 0);

    public LensFilter {
        items = List.copyOf(items);
        blocks = List.copyOf(blocks);
        mobs = List.copyOf(mobs);
    }

    /** An Item lens's filter. */
    public static LensFilter items(List<ItemStack> entries, boolean allow) {
        return new LensFilter(ITEMS, entries, allow, List.of(), true, false, List.of(), true, 0, false, 0);
    }

    /** A Block lens's filter. */
    public static LensFilter blocks(List<ItemStack> entries, boolean allow, boolean ripe) {
        return new LensFilter(BLOCKS, List.of(), true, entries, allow, ripe, List.of(), true, 0, false, 0);
    }

    /** An Entity lens's filter. */
    public static LensFilter entities(int kinds, boolean spareYoung, int keep) {
        return entities(List.of(), true, kinds, spareYoung, keep);
    }

    /** An Entity lens's filter, with spawn eggs listed. */
    public static LensFilter entities(List<ItemStack> eggs, boolean allow, int kinds, boolean spareYoung, int keep) {
        return new LensFilter(ENTITIES, List.of(), true, List.of(), true, false, eggs, allow, kinds, spareYoung, Math.clamp(keep, 0, MAX_KEEP));
    }

    /** This filter's parts laid over {@code under}'s: what a lens later on the beam does to one before it. */
    public LensFilter over(LensFilter under) {
        boolean i = (parts & ITEMS) != 0;
        boolean b = (parts & BLOCKS) != 0;
        boolean e = (parts & ENTITIES) != 0;
        return new LensFilter(parts | under.parts,
                i ? items : under.items, i ? itemsAllow : under.itemsAllow,
                b ? blocks : under.blocks, b ? blocksAllow : under.blocksAllow, b ? ripe : under.ripe,
                e ? mobs : under.mobs, e ? mobsAllow : under.mobsAllow,
                e ? kinds : under.kinds, e ? spareYoung : under.spareYoung, e ? keep : under.keep);
    }

    public static final Codec<LensFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("parts").forGetter(filter -> Optional.of(filter.parts)),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LensFilter::items),
            Codec.BOOL.optionalFieldOf("items_allow", true).forGetter(LensFilter::itemsAllow),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(LensFilter::blocks),
            Codec.BOOL.optionalFieldOf("blocks_allow", true).forGetter(LensFilter::blocksAllow),
            Codec.BOOL.optionalFieldOf("ripe", false).forGetter(LensFilter::ripe),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(LensFilter::mobs),
            Codec.BOOL.optionalFieldOf("mobs_allow", true).forGetter(LensFilter::mobsAllow),
            Codec.INT.optionalFieldOf("kinds", 0).forGetter(LensFilter::kinds),
            Codec.BOOL.optionalFieldOf("spare_young", false).forGetter(LensFilter::spareYoung),
            Codec.INT.optionalFieldOf("keep", 0).forGetter(LensFilter::keep),
            // The single Filter lens before the split: its one list served items and blocks alike.
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("entries").forGetter(filter -> Optional.empty()),
            Codec.BOOL.optionalFieldOf("allow").forGetter(filter -> Optional.empty()),
            Codec.STRING.optionalFieldOf("living").forGetter(filter -> Optional.empty()),
            Codec.BOOL.optionalFieldOf("grown").forGetter(filter -> Optional.empty())
    ).apply(instance, (parts, items, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, young, keep, entries, allow, living, grown) -> {
        if (parts.isPresent()) return new LensFilter(parts.get(), items, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, young, keep);
        List<ItemStack> list = entries.orElse(List.of());
        boolean allows = allow.orElse(true);
        boolean old = grown.orElse(false);
        return new LensFilter(ITEMS | BLOCKS | ENTITIES, list, allows, list, allows, old, List.of(), true, legacyKinds(living.orElse("any")), old, keep);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, LensFilter> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** The old five-way living switch, as kinds. */
    private static int legacyKinds(String living) {
        return switch (living) {
            case "hostile" -> HOSTILE;
            case "passive" -> ANIMALS;
            case "players" -> PLAYERS;
            case "not_players" -> HOSTILE | ANIMALS;
            default -> 0;
        };
    }

    private static boolean any(List<ItemStack> entries) {
        return entries.stream().anyMatch(entry -> !entry.isEmpty());
    }

    private static boolean listed(List<ItemStack> entries, ItemStack stack) {
        return !stack.isEmpty() && entries.stream().anyMatch(entry -> FilterEntry.matches(entry, stack));
    }

    /** Whether it lets an action carry, place, put in or take out this item. Entries holding a fluid are for fluids only. */
    public boolean lets(ItemStack stack) {
        if (items.stream().noneMatch(entry -> !entry.isEmpty() && fluidOf(entry).isEmpty())) return true;
        return items.stream().anyMatch(entry -> fluidOf(entry).isEmpty() && FilterEntry.matches(entry, stack)) == itemsAllow;
    }

    /** Whether it lets Insert or Extract move this fluid: listed as a filled container (a water bucket), or no fluid listed. */
    public boolean lets(net.neoforged.neoforge.fluids.FluidStack fluid) {
        boolean any = false;
        boolean listed = false;
        for (ItemStack entry : items) {
            net.neoforged.neoforge.fluids.FluidStack held = fluidOf(entry);
            if (held.isEmpty()) continue;
            any = true;
            if (held.getFluid().isSame(fluid.getFluid())) listed = true;
        }
        return !any || listed == itemsAllow;
    }

    /**
     * The fluid a filter entry stands for: what it holds, if it's a filled container. Entries are bare items (a water
     * bucket), so it's looked up once per item.
     */
    private static net.neoforged.neoforge.fluids.FluidStack fluidOf(ItemStack entry) {
        if (entry.isEmpty()) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        if (!entry.isComponentsPatchEmpty()) return net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(entry);
        return HELD.computeIfAbsent(entry.getItem(), item -> net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(new ItemStack(item)));
    }

    private static final java.util.Map<net.minecraft.world.item.Item, net.neoforged.neoforge.fluids.FluidStack> HELD =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether it lets a Placement beam place this item: the item, and as a block, the block it places. */
    public boolean letsPlace(ItemStack stack) {
        if (!lets(stack)) return false;
        return !any(blocks) || listed(blocks, stack) == blocksAllow;
    }

    /**
     * Whether it lets a Harvest beam break this block, which would drop {@code drops}: listed if its item or any
     * drop is. So "wheat" lets only ripe wheat through: unripe, it drops only seeds.
     */
    public boolean lets(BlockState state, Supplier<List<ItemStack>> drops) {
        // Cheap first: ripeness, then the block's own item; what it drops only if that's still undecided.
        if (ripe && !ripe(state)) return false;
        if (!any(blocks)) return true;
        boolean listed = listed(blocks, new ItemStack(state.getBlock().asItem()));
        if (!listed) for (ItemStack drop : drops.get()) listed |= listed(blocks, drop);
        return listed == blocksAllow;
    }

    /** Whether the block grows and is fully grown: at the top of its age. */
    private static boolean ripe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                return state.getValue(age) >= age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            }
        }
        return false;
    }

    /** Whether it lets a hit land on {@code entity}: its mob by the eggs listed, its kind, and never the young if it spares them. */
    public boolean letsHit(Entity entity) {
        if (spareYoung && entity instanceof AgeableMob mob && mob.isBaby()) return false;
        if (any(mobs)) {
            // A bound entry is that one entity; an egg, every one of its kind.
            boolean listed = mobs.stream().anyMatch(egg -> !egg.isEmpty() && BoundEntity.of(egg)
                    .map(id -> id.equals(entity.getUUID()))
                    .orElseGet(() -> net.minecraft.world.item.SpawnEggItem.spawnsEntity(egg, entity.getType())));
            if (listed != mobsAllow) return false;
        }
        if (kinds == 0) return true;
        boolean player = entity instanceof Player;
        boolean hostile = entity instanceof Enemy;
        boolean animal = entity instanceof LivingEntity && !hostile && !player;
        return player && (kinds & PLAYERS) != 0 || hostile && (kinds & HOSTILE) != 0 || animal && (kinds & ANIMALS) != 0;
    }

    /** The list a lens of {@code part} edits. */
    public List<ItemStack> entries(int part) {
        return part == BLOCKS ? blocks : part == ENTITIES ? mobs : items;
    }

    public boolean allows(int part) {
        return part == BLOCKS ? blocksAllow : part == ENTITIES ? mobsAllow : itemsAllow;
    }

    public ItemStack entry(int part, int slot) {
        List<ItemStack> list = entries(part);
        return slot < list.size() ? list.get(slot) : ItemStack.EMPTY;
    }

    public LensFilter withEntry(int part, int slot, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(entries(part));
        while (list.size() < SLOTS) list.add(ItemStack.EMPTY);
        list.set(slot, stack.isEmpty() ? ItemStack.EMPTY : FilterEntry.of(stack));
        return withList(part, list);
    }

    /**
     * With {@code entity} bound in the Entity list's first free slot; as it was if it's listed already or the list is
     * full (then null).
     */
    @org.jetbrains.annotations.Nullable
    public LensFilter withBound(Entity entity) {
        List<ItemStack> list = new ArrayList<>(mobs);
        while (list.size() < SLOTS) list.add(ItemStack.EMPTY);
        for (ItemStack entry : list) {
            if (BoundEntity.of(entry).filter(entity.getUUID()::equals).isPresent()) return this;
        }
        int free = -1;
        for (int i = 0; i < list.size() && free < 0; i++) if (list.get(i).isEmpty()) free = i;
        if (free < 0) return null;
        list.set(free, BoundEntity.entry(entity));
        return withList(ENTITIES, list);
    }

    public LensFilter cycledTag(int part, int slot) {
        List<ItemStack> list = entries(part);
        if (slot >= list.size() || list.get(slot).isEmpty() || BoundEntity.of(list.get(slot)).isPresent()) return this;
        List<ItemStack> changed = new ArrayList<>(list);
        changed.set(slot, FilterEntry.cycle(changed.get(slot)));
        return withList(part, changed);
    }

    private LensFilter withList(int part, List<ItemStack> list) {
        return switch (part) {
            case BLOCKS -> new LensFilter(parts | BLOCKS, items, itemsAllow, list, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
            case ENTITIES -> new LensFilter(parts | ENTITIES, items, itemsAllow, blocks, blocksAllow, ripe, list, mobsAllow, kinds, spareYoung, keep);
            default -> new LensFilter(parts | ITEMS, list, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
        };
    }

    public LensFilter withAllow(int part, boolean allow) {
        return switch (part) {
            case BLOCKS -> new LensFilter(parts | BLOCKS, items, itemsAllow, blocks, allow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
            case ENTITIES -> new LensFilter(parts | ENTITIES, items, itemsAllow, blocks, blocksAllow, ripe, mobs, allow, kinds, spareYoung, keep);
            default -> new LensFilter(parts | ITEMS, items, allow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
        };
    }

    public LensFilter withRipe(boolean ripe) {
        return new LensFilter(parts | BLOCKS, items, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
    }

    public LensFilter withKinds(int kinds) {
        return new LensFilter(parts | ENTITIES, items, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
    }

    public LensFilter withSpareYoung(boolean spareYoung) {
        return new LensFilter(parts | ENTITIES, items, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, keep);
    }

    public LensFilter withKeep(int keep) {
        return new LensFilter(parts | ENTITIES, items, itemsAllow, blocks, blocksAllow, ripe, mobs, mobsAllow, kinds, spareYoung, Math.clamp(keep, 0, MAX_KEEP));
    }

    /**
     * By value: an {@link ItemStack} only equals itself, so a record's own equality would make every copy of a
     * filter (sent to a client, taken through a network) a different item, never stacking or found again.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof LensFilter that && parts == that.parts && itemsAllow == that.itemsAllow
                && blocksAllow == that.blocksAllow && ripe == that.ripe && mobsAllow == that.mobsAllow && kinds == that.kinds
                && spareYoung == that.spareYoung && keep == that.keep && ItemStack.listMatches(items, that.items)
                && ItemStack.listMatches(blocks, that.blocks) && ItemStack.listMatches(mobs, that.mobs);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(parts, itemsAllow, blocksAllow, ripe, mobsAllow, kinds, spareYoung, keep,
                ItemStack.hashStackList(items), ItemStack.hashStackList(blocks), ItemStack.hashStackList(mobs));
    }
}
