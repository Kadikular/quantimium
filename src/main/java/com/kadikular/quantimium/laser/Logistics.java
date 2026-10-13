package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logistics networks: everything a Logistics Controller's light reaches (through mirrors, splitters and relays,
 * joined with other light or not; never past an Isolator, which lets no item through) is one network. Light from
 * two controllers meeting stops both. Once a second each network moves items:
 *
 * <ul>
 *   <li>each active provider pushes what its filter lets go: to requesters that list it first, then into
 *       storage, highest priority first;</li>
 *   <li>each active requester pulls what its filter lists: from providers first, then from storage, lowest
 *       priority first.</li>
 * </ul>
 *
 * Each active member moves up to {@link LaserTuning#LOGISTICS_ITEMS} items a second, and every transfer costs the
 * controller {@link LaserTuning#LOGISTICS_FE_PER_MOVE} FE; without it, the network waits.
 */
public final class Logistics {

    /** A transfer the controller couldn't pay for: the network stops for this second. */
    private static final int UNPAID = -1;

    private Logistics() {}

    static void run(Level level, Collection<LaserNode> nodes, Map<BlockPos, Light> lights) {
        Map<BlockPos, LogisticsController> controllers = new HashMap<>();
        for (LaserNode node : nodes) {
            if (node instanceof LogisticsController controller) controllers.put(node.nodePos(), controller);
        }
        // Whose light reaches each member; light from two controllers on one member stops both.
        Map<LogisticsMember, BlockPos> owners = new HashMap<>();
        Set<BlockPos> clashing = new HashSet<>();
        List<LogisticsMember> all = new ArrayList<>();
        for (LaserNode node : nodes) {
            LogisticsMember member = node.logistics();
            if (member == null) continue;
            all.add(member);
            Light light = lights.get(node.nodePos());
            if (light == null) continue;
            BlockPos owner = null;
            for (BlockPos feeder : light.logistics()) {
                if (!controllers.containsKey(feeder)) continue;
                if (owner == null) {
                    owner = feeder;
                } else {
                    clashing.add(owner);
                    clashing.add(feeder);
                }
            }
            if (owner != null) owners.put(member, owner);
        }
        Map<BlockPos, List<LogisticsMember>> networks = new HashMap<>();
        for (LogisticsMember member : all) {
            BlockPos owner = owners.get(member);
            boolean joined = owner != null && !clashing.contains(owner);
            member.connected(joined);
            if (joined) networks.computeIfAbsent(owner, key -> new ArrayList<>()).add(member);
        }
        for (Map.Entry<BlockPos, LogisticsController> entry : controllers.entrySet()) {
            List<LogisticsMember> members = networks.getOrDefault(entry.getKey(), List.of());
            boolean clash = clashing.contains(entry.getKey());
            entry.getValue().report(members, clash);
            if (!clash && !members.isEmpty()) runNetwork(level, entry.getValue(), members);
        }
    }

    private static void runNetwork(Level level, LogisticsController controller, List<LogisticsMember> members) {
        List<LogisticsMember> highestFirst = new ArrayList<>(members);
        highestFirst.sort(Comparator.comparingInt(LogisticsMember::priority).reversed()
                .thenComparing(LogisticsMember::inventoryPos));
        List<LogisticsMember> lowestFirst = new ArrayList<>(highestFirst);
        java.util.Collections.reverse(lowestFirst);
        for (LogisticsMember member : highestFirst) {
            if (member.role() == LogisticsMember.Role.PROVIDER && member.active() && push(level, controller, member, highestFirst) == UNPAID) return;
        }
        for (LogisticsMember member : highestFirst) {
            if (member.role() == LogisticsMember.Role.REQUESTER && member.active() && pull(level, controller, member, highestFirst, lowestFirst) == UNPAID) return;
        }
    }

    /** An active provider pushes what it lets go: to requesters listing it, then storage. How many it moved, or {@link #UNPAID}. */
    private static int push(Level level, LogisticsController controller, LogisticsMember provider, List<LogisticsMember> highestFirst) {
        IItemHandler from = provider.inventory();
        if (from == null) return 0;
        int budget = LaserTuning.LOGISTICS_ITEMS;
        for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
            ItemStack there = from.getStackInSlot(slot);
            if (there.isEmpty() || !provider.lets(there)) continue;
            for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.REQUESTER, LogisticsMember.Role.STORAGE}) {
                for (LogisticsMember to : highestFirst) {
                    if (budget <= 0 || from.getStackInSlot(slot).isEmpty()) break;
                    if (to.role() != role || to.inventoryPos().equals(provider.inventoryPos()) || !to.lets(there)) continue;
                    int moved = move(level, controller, provider, from, slot, to.inventory(), budget);
                    if (moved == UNPAID) return UNPAID;
                    budget -= moved;
                }
            }
        }
        return LaserTuning.LOGISTICS_ITEMS - budget;
    }

    /** An active requester pulls what it lists: from providers, then storage, lowest priority first. How many it moved, or {@link #UNPAID}. */
    private static int pull(Level level, LogisticsController controller, LogisticsMember requester, List<LogisticsMember> highestFirst,
                            List<LogisticsMember> lowestFirst) {
        IItemHandler into = requester.inventory();
        if (into == null) return 0;
        int budget = LaserTuning.LOGISTICS_ITEMS;
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
            for (LogisticsMember source : role == LogisticsMember.Role.PROVIDER ? highestFirst : lowestFirst) {
                if (source.role() != role || source.inventoryPos().equals(requester.inventoryPos())) continue;
                IItemHandler from = source.inventory();
                if (from == null) continue;
                for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
                    ItemStack there = from.getStackInSlot(slot);
                    // Storage gives up anything it holds; a provider only what its filter lets go.
                    if (there.isEmpty() || !requester.lets(there) || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                    int moved = move(level, controller, source, from, slot, into, budget);
                    if (moved == UNPAID) return UNPAID;
                    budget -= moved;
                }
                if (budget <= 0) return LaserTuning.LOGISTICS_ITEMS;
            }
        }
        return LaserTuning.LOGISTICS_ITEMS - budget;
    }

    /**
     * Puts {@code stack} into a network (a Pickup or Extract lens's catch, through its controller): to requesters
     * listing it first, then storage, highest priority first. What's left.
     */
    public static ItemStack insert(List<LogisticsMember> members, ItemStack stack, boolean simulate) {
        return insert(members, stack, simulate, true);
    }

    /** As {@link #insert(List, ItemStack, boolean)}; without {@code requesters}, into storage only (what a player puts away). */
    public static ItemStack insert(List<LogisticsMember> members, ItemStack stack, boolean simulate, boolean requesters) {
        ItemStack left = stack.copy();
        LogisticsMember.Role[] roles = requesters ? new LogisticsMember.Role[] {LogisticsMember.Role.REQUESTER, LogisticsMember.Role.STORAGE}
                : new LogisticsMember.Role[] {LogisticsMember.Role.STORAGE};
        for (LogisticsMember.Role role : roles) {
            for (LogisticsMember to : byPriority(members, true)) {
                if (left.isEmpty()) return ItemStack.EMPTY;
                if (to.role() != role || !to.lets(left)) continue;
                IItemHandler into = to.inventory();
                if (into != null) left = ItemHandlerHelper.insertItem(into, left, simulate);
            }
        }
        return left;
    }

    /**
     * How much of {@code stack} the network's storage has room for, up to {@code max}, by reading its slots alone:
     * for a caller inside an open transaction, where a simulated insert would open another.
     */
    public static int roomFor(List<LogisticsMember> members, ItemStack stack, int max) {
        int room = 0;
        for (LogisticsMember member : members) {
            if (member.role() != LogisticsMember.Role.STORAGE || !member.lets(stack)) continue;
            IItemHandler into = member.inventory();
            if (into == null) continue;
            for (int slot = 0; slot < into.getSlots() && room < max; slot++) {
                if (!into.isItemValid(slot, stack)) continue;
                ItemStack there = into.getStackInSlot(slot);
                int limit = Math.min(into.getSlotLimit(slot), stack.getMaxStackSize());
                if (there.isEmpty()) room += limit;
                else if (ItemStack.isSameItemSameComponents(there, stack)) room += Math.max(0, limit - there.getCount());
            }
            if (room >= max) return max;
        }
        return Math.min(room, max);
    }

    /**
     * Takes up to {@code max} of the first thing {@code wants} accepts from a network (for an Insert, Placement or
     * Interact lens, through its controller): from providers first, as their filters let go, then storage, lowest
     * priority first.
     */
    public static ItemStack take(List<LogisticsMember> members, java.util.function.Predicate<ItemStack> wants, int max, boolean simulate) {
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
            for (LogisticsMember source : byPriority(members, role == LogisticsMember.Role.PROVIDER)) {
                if (source.role() != role) continue;
                IItemHandler from = source.inventory();
                if (from == null) continue;
                for (int slot = 0; slot < from.getSlots(); slot++) {
                    ItemStack there = from.getStackInSlot(slot);
                    if (there.isEmpty() || !wants.test(there) || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                    ItemStack taken = from.extractItem(slot, Math.min(max, there.getCount()), simulate);
                    if (!taken.isEmpty()) return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<LogisticsMember> byPriority(List<LogisticsMember> members, boolean highestFirst) {
        List<LogisticsMember> sorted = new ArrayList<>(members);
        Comparator<LogisticsMember> order = Comparator.comparingInt(LogisticsMember::priority).thenComparing(LogisticsMember::inventoryPos);
        sorted.sort(highestFirst ? order.reversed() : order);
        return sorted;
    }

    /** Moves up to {@code max} of what's in {@code slot} of {@code from} into {@code into}, as far as it fits, if the controller pays. */
    private static int move(Level level, LogisticsController controller, LogisticsMember source, IItemHandler from, int slot, IItemHandler into, int max) {
        if (into == null) return 0;
        ItemStack offered = from.extractItem(slot, max, true);
        if (offered.isEmpty()) return 0;
        int fits = offered.getCount() - ItemHandlerHelper.insertItem(into, offered.copy(), true).getCount();
        if (fits <= 0) return 0;
        if (!controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE)) return UNPAID;
        ItemStack taken = from.extractItem(slot, fits, false);
        ItemStack left = ItemHandlerHelper.insertItem(into, taken, false);
        int moved = taken.getCount() - left.getCount();
        if (!left.isEmpty()) {
            // It took less than it said it would: back where it came from, or failing that, out by it.
            left = ItemHandlerHelper.insertItem(from, left, false);
            BlockPos at = source.inventoryPos();
            if (!left.isEmpty()) Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, left);
        }
        return moved;
    }
}
