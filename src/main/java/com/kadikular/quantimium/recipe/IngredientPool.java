package com.kadikular.quantimium.recipe;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flat view of crafter inputs plus remote inventories behind Entangled Links. Each offer carries
 * provenance so a craft can withdraw from the right place without treating the link item as an
 * ingredient.
 */
public final class IngredientPool {

    public sealed interface Provenance {
        record Local(int crafterSlot) implements Provenance {}

        record Remote(int linkSlot, int remoteSlot) implements Provenance {}
    }

    public record Offer(ItemStack stack, Provenance provenance) {
        public Offer {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }

    /**
     * A pull to make when the craft commits. {@code expected} is the item the match was resolved
     * against: provenance names a slot, and a linked chest or crafter reshuffles its slots as it
     * works, so the count alone would let a cached craft spend whatever moved in afterwards. Null
     * only on the client's synced copy, which never withdraws anything. {@code components}, when set,
     * must match too: Unrealised Matter with one history is not Matter with another.
     */
    public record Withdrawal(Provenance provenance, int count, @Nullable Item expected,
                             @Nullable DataComponentPatch components) {
        public Withdrawal {
            count = Math.max(0, count);
        }

        public Withdrawal(Provenance provenance, int count, @Nullable Item expected) {
            this(provenance, count, expected, null);
        }

        public Withdrawal(Provenance provenance, int count) {
            this(provenance, count, null, null);
        }

        public boolean matches(ItemStack stack) {
            if (expected != null && !stack.is(expected)) return false;
            return components == null || components.equals(stack.getComponentsPatch());
        }

        /** The same pull, {@code count} of it. */
        public Withdrawal withCount(int count) {
            return new Withdrawal(provenance, count, expected, components);
        }
    }

    private final List<Offer> offers;

    private IngredientPool(List<Offer> offers) {
        this.offers = List.copyOf(offers);
    }

    public static IngredientPool build(@Nullable Level level, BlockPos source,
                                       ItemStackHandler grid, int inputSlots) {
        List<Offer> offers = new ArrayList<>();
        // What's in its own grid comes first, whatever slot a Tesseract is in: a craft takes from there before
        // reaching through a link for the same thing.
        List<Offer> linked = new ArrayList<>();
        for (int slot = 0; slot < inputSlots; slot++) {
            ItemStack stack = grid.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (EntangledLinks.isLink(stack)) {
                IItemHandler remote = EntangledLinks.resolve(level, source, stack);
                if (remote == null) continue;
                int remoteSlots = remote.getSlots();
                // Linked Quantum Crafters only ever yield from their output catalog; probing the
                // input half just burns canCommit calls for stacks we cannot extract.
                int start = remoteSlots == QuantumCrafterBlockEntity.CATALYST_SLOT
                        ? QuantumCrafterBlockEntity.INPUT_SLOTS : 0;
                for (int r = start; r < remoteSlots; r++) {
                    ItemStack remoteStack = remote.getStackInSlot(r);
                    if (remoteStack.isEmpty() || EntangledLinks.isLink(remoteStack)) continue;
                    // A slot we can see is not a slot we may empty: machine input slots routinely
                    // refuse extraction. Offer only what an actual pull would hand over, or the
                    // craft would consume nothing and print items for free.
                    ItemStack takeable = remote.extractItem(r, remoteStack.getCount(), true);
                    if (takeable.isEmpty()) continue;
                    // One pull hands over a stack at most: a slot holding more (a network, a drawer) that lets a
                    // whole stack go lets the rest go too, a stack at a time.
                    if (takeable.getCount() >= takeable.getMaxStackSize() && remoteStack.getCount() > takeable.getCount()) {
                        takeable = takeable.copyWithCount(remoteStack.getCount());
                    }
                    linked.add(new Offer(takeable, new Provenance.Remote(slot, r)));
                }
                continue;
            }
            offers.add(new Offer(stack, new Provenance.Local(slot)));
        }
        offers.addAll(linked);
        return new IngredientPool(offers);
    }

    public List<Offer> offers() {
        return offers;
    }

    public boolean isEmpty() {
        return offers.isEmpty();
    }

    /** Distinct items on hand, used to narrow which recipes are worth testing. */
    public Set<Item> items() {
        Set<Item> items = new HashSet<>();
        for (Offer offer : offers) {
            if (!offer.stack().isEmpty()) items.add(offer.stack().getItem());
        }
        return items;
    }

    public int available(Provenance provenance) {
        for (Offer offer : offers) {
            if (offer.provenance().equals(provenance)) return offer.stack().getCount();
        }
        return 0;
    }

    /** As {@link #available(Provenance)}, but zero unless the slot still holds the resolved item. */
    public int available(Withdrawal withdrawal) {
        for (Offer offer : offers) {
            if (!offer.provenance().equals(withdrawal.provenance())) continue;
            return withdrawal.matches(offer.stack()) ? offer.stack().getCount() : 0;
        }
        return 0;
    }

    /**
     * Identifies where a withdrawal may draw from. Every non-link crafter input is one source; each
     * link is its own. A withdrawal names a slot as a hint, but the whole source is fair game, since
     * the same item can sit in several slots (one raw iron in the first, a stack in the next).
     */
    public static int sourceKey(Provenance provenance) {
        return switch (provenance) {
            case Provenance.Local ignored -> -1;
            case Provenance.Remote(int linkSlot, int ignored) -> linkSlot;
        };
    }

    /** Total of {@code item} reachable from one source, summed across all of its slots. */
    public int totalAvailable(int sourceKey, Item item) {
        return totalAvailable(sourceKey, item, null);
    }

    /** As {@link #totalAvailable(int, Item)}, counting only stacks with exactly {@code components} when set. */
    public int totalAvailable(int sourceKey, Item item, @Nullable DataComponentPatch components) {
        int sum = 0;
        // Only the offers holding that item: a network's pool is hundreds, asked once for each ingredient of each craft.
        int[] holding = byItem().get(item);
        if (holding == null) return 0;
        for (int index : holding) {
            Offer offer = offers.get(index);
            if (sourceKey(offer.provenance()) != sourceKey) continue;
            if (!offer.stack().is(item)) continue;
            if (components != null && !components.equals(offer.stack().getComponentsPatch())) continue;
            sum += offer.stack().getCount();
        }
        return sum;
    }

    /** Mutable allocator used while matching a single recipe run. */
    /** Which offers hold each item, worked out once: a recipe's ingredient looks only at those. */
    @Nullable
    private Map<Item, int[]> byItem;
    /** For each ingredient asked about, the offers it could take, in order (by identity: recipes share theirs). */
    private final Map<net.minecraft.world.item.crafting.Ingredient, int[]> candidates = new java.util.IdentityHashMap<>();

    /** Which offers hold each item, in order. */
    private Map<Item, int[]> byItem() {
        if (byItem == null) {
            Map<Item, List<Integer>> found = new HashMap<>();
            for (int i = 0; i < offers.size(); i++) {
                ItemStack stack = offers.get(i).stack();
                if (!stack.isEmpty()) found.computeIfAbsent(stack.getItem(), k -> new ArrayList<>(1)).add(i);
            }
            Map<Item, int[]> index = new HashMap<>();
            found.forEach((item, list) -> index.put(item, list.stream().mapToInt(Integer::intValue).toArray()));
            byItem = index;
        }
        return byItem;
    }

    /**
     * The offers, in order, that {@code ingredient} could take: from the items it names, without testing every offer;
     * null for an ingredient that can't say which items it takes (a custom one), which is tested against them all.
     */
    @Nullable
    public int[] candidates(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient.isCustom()) return null;
        int[] known = candidates.get(ingredient);
        if (known != null) return known;
        Map<Item, int[]> index = byItem();
        java.util.TreeSet<Integer> picked = new java.util.TreeSet<>();
        ingredient.items().forEach(holder -> {
            int[] at = index.get(holder.value());
            if (at != null) for (int i : at) picked.add(i);
        });
        int[] result = picked.stream().mapToInt(Integer::intValue).toArray();
        candidates.put(ingredient, result);
        return result;
    }

    public final class Allocator {
        /** What's taken from each offer touched, by its place: a match touches a few of a pool's hundreds. */
        private final Map<Integer, Integer> taken = new java.util.TreeMap<>();

        private Allocator() {}

        public int remaining(int index) {
            return offers.get(index).stack().getCount() - taken.getOrDefault(index, 0);
        }

        public ItemStack stack(int index) {
            return offers.get(index).stack();
        }

        public int size() {
            return offers.size();
        }

        @Nullable
        public Withdrawal take(int index, int amount) {
            if (index < 0 || index >= offers.size() || amount <= 0) return null;
            if (remaining(index) < amount) return null;
            taken.merge(index, amount, Integer::sum);
            Offer offer = offers.get(index);
            return new Withdrawal(offer.provenance(), amount, offer.stack().getItem());
        }

        /**
         * Pull {@code amount} items matching {@code test} across offers. Returns null if the pool
         * cannot satisfy the request.
         */
        @Nullable
        public List<Withdrawal> takeMatching(java.util.function.Predicate<ItemStack> test, int amount) {
            List<Withdrawal> out = new ArrayList<>();
            int remaining = amount;
            for (int i = 0; i < offers.size() && remaining > 0; i++) {
                ItemStack stack = stack(i);
                int avail = remaining(i);
                if (avail <= 0 || !test.test(stack)) continue;
                int take = Math.min(avail, remaining);
                Withdrawal w = take(i, take);
                if (w == null) return null;
                out.add(w);
                remaining -= take;
            }
            return remaining == 0 ? out : null;
        }

        public List<Withdrawal> snapshot() {
            List<Withdrawal> out = new ArrayList<>();
            taken.forEach((i, count) -> {
                if (count <= 0) return;
                Offer offer = offers.get(i);
                out.add(new Withdrawal(offer.provenance(), count, offer.stack().getItem()));
            });
            return out;
        }
    }

    public Allocator allocator() {
        return new Allocator();
    }

    /** Merge duplicate provenances (e.g. after batching). */
    public static List<Withdrawal> merge(List<Withdrawal> withdrawals) {
        List<Withdrawal> merged = new ArrayList<>();
        for (Withdrawal w : withdrawals) {
            if (w.count() <= 0) continue;
            boolean found = false;
            for (int i = 0; i < merged.size(); i++) {
                Withdrawal existing = merged.get(i);
                if (existing.provenance().equals(w.provenance())) {
                    merged.set(i, new Withdrawal(existing.provenance(), existing.count() + w.count(),
                            existing.expected() == null ? w.expected() : existing.expected(),
                            existing.components() == null ? w.components() : existing.components()));
                    found = true;
                    break;
                }
            }
            if (!found) merged.add(w);
        }
        return List.copyOf(merged);
    }
}
