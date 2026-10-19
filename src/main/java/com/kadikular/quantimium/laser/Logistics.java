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
 * Each active member moves as an Insert or Extract lens does: {@link #budget} items a second, four times as many for
 * each Speed lens its light met, and idle it looks less often, waiting twice as long each time up to
 * {@link com.kadikular.quantimium.Config#laserIdleTicks()}, then half as long again after each move. Every transfer costs the
 * controller {@link LaserTuning#LOGISTICS_FE_PER_MOVE} FE; without it, the network waits.
 */
public final class Logistics {

    /** A transfer the controller couldn't pay for: the network stops for this second. */
    private static final int UNPAID = -1;

    private Logistics() {}

    /** Each tick, the networks whose controllers fall in this {@code slice} of the second (0 to 19) do their moves. */
    static void run(Level level, Collection<LaserNode> nodes, Map<BlockPos, Light> lights, int slice) {
        Map<BlockPos, LogisticsController> controllers = new HashMap<>();
        for (LaserNode node : nodes) {
            if (node instanceof LogisticsController controller) controllers.put(node.nodePos(), controller);
        }
        // None due this tick: nothing to look at. Who's on which network is worked out again when one is.
        boolean due = false;
        for (BlockPos controller : controllers.keySet()) due |= Math.floorMod(controller.hashCode(), 20) == slice;
        // With none at all, receptacles are told they're on no network once a second.
        if (controllers.isEmpty() ? slice != 0 : !due) return;
        // Whose light reaches each member; light from two controllers on one member stops both.
        Map<LogisticsMember, BlockPos> owners = new HashMap<>();
        Map<LogisticsMember, Integer> speeds = new HashMap<>();
        Set<BlockPos> clashing = new HashSet<>();
        List<LogisticsMember> all = new ArrayList<>();
        for (LaserNode node : nodes) {
            LogisticsMember member = node.logistics();
            if (member == null) continue;
            all.add(member);
            Light light = lights.get(node.nodePos());
            if (light == null) continue;
            speeds.put(member, light.speeds());
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
            if (Math.floorMod(entry.getKey().hashCode(), 20) != slice) continue;
            List<LogisticsMember> members = networks.getOrDefault(entry.getKey(), List.of());
            boolean clash = clashing.contains(entry.getKey());
            entry.getValue().report(members, clash);
            if (!clash && !members.isEmpty()) runNetwork(level, entry.getValue(), members, speeds);
        }
    }

    /** Items an active member may move in a pass: an Insert lens's second's worth, quickened by the Speed lenses it met. */
    static int budget(int speeds) {
        return LaserTuning.MOVE_ITEMS * (20 / LaserTuning.PLACE_COOLDOWN) << 2 * speeds;
    }

    private static void runNetwork(Level level, LogisticsController controller, List<LogisticsMember> members, Map<LogisticsMember, Integer> speeds) {
        List<LogisticsMember> highestFirst = new ArrayList<>(members);
        highestFirst.sort(Comparator.comparingInt(LogisticsMember::priority).reversed()
                .thenComparing(LogisticsMember::inventoryPos));
        List<LogisticsMember> lowestFirst = new ArrayList<>(highestFirst);
        java.util.Collections.reverse(lowestFirst);
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.REQUESTER}) {
            for (LogisticsMember member : highestFirst) {
                if (member.role() != role || !member.active()) continue;
                int[] pace = member.pace();
                if (pace != null && --pace[0] > 0) continue;
                int budget = budget(speeds.getOrDefault(member, 0));
                int moved = role == LogisticsMember.Role.PROVIDER ? push(level, controller, member, highestFirst, budget)
                        : pull(level, controller, member, highestFirst, lowestFirst, budget);
                if (moved > 0) controller.stockMoved();
                if (moved == UNPAID) return;
                if (pace != null) {
                    // Busy, it looks again next second; idle, it waits twice as long as last time, up to the most.
                    int most = Math.max(1, com.kadikular.quantimium.Config.laserIdleTicks() / 20);
                    pace[1] = moved > 0 ? Math.max(1, pace[1] / 2) : Math.min(most, Math.max(1, pace[1] * 2));
                    pace[0] = pace[1];
                }
            }
        }
    }

    /** An active provider pushes what it lets go: to requesters listing it, then storage. How many it moved, or {@link #UNPAID}. */
    private static int push(Level level, LogisticsController controller, LogisticsMember provider, List<LogisticsMember> highestFirst, int most) {
        IItemHandler from = provider.inventory();
        if (from == null) return 0;
        int budget = most;
        for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
            ItemStack there = from.getStackInSlot(slot);
            if (there.isEmpty() || !provider.lets(there)) continue;
            for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.REQUESTER, LogisticsMember.Role.STORAGE}) {
                for (LogisticsMember to : highestFirst) {
                    if (budget <= 0 || from.getStackInSlot(slot).isEmpty()) break;
                    if (to.role() != role || to.inventoryPos().equals(provider.inventoryPos()) || !to.lets(there)) continue;
                    int room = Math.min(budget, to.wants(there));
                    if (room <= 0) continue;
                    int moved = move(level, controller, provider, from, slot, to.inventory(), room);
                    if (moved == UNPAID) return UNPAID;
                    budget -= moved;
                }
            }
        }
        return most - budget;
    }

    /** An active requester pulls what it lists: from providers, then storage, lowest priority first. How many it moved, or {@link #UNPAID}. */
    private static int pull(Level level, LogisticsController controller, LogisticsMember requester, List<LogisticsMember> highestFirst,
                            List<LogisticsMember> lowestFirst, int most) {
        IItemHandler into = requester.inventory();
        if (into == null) return 0;
        int budget = most;
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
            for (LogisticsMember source : role == LogisticsMember.Role.PROVIDER ? highestFirst : lowestFirst) {
                if (source.role() != role || source.inventoryPos().equals(requester.inventoryPos())) continue;
                IItemHandler from = source.inventory();
                if (from == null) continue;
                for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
                    ItemStack there = from.getStackInSlot(slot);
                    // Storage gives up anything it holds; a provider only what its filter lets go.
                    if (there.isEmpty() || !requester.lets(there) || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                    int room = Math.min(budget, requester.wants(there));
                    if (room <= 0) continue;
                    int moved = move(level, controller, source, from, slot, into, room);
                    if (moved == UNPAID) return UNPAID;
                    budget -= moved;
                }
                if (budget <= 0) return most;
            }
        }
        return most - budget;
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
                if (into == null) continue;
                // A requester keeping counts takes only what it's short of.
                int room = Math.min(left.getCount(), to.wants(left));
                if (room <= 0) continue;
                ItemStack rest = ItemHandlerHelper.insertItem(into, left.copyWithCount(room), simulate);
                left = left.copyWithCount(left.getCount() - room + rest.getCount());
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
        return take(members, wants, max, simulate, true, true);
    }

    /**
     * As {@link #take}, from what's really there ({@code reals}), from what machines would make ({@code made}), or
     * both. A provider whose mode is "only what's there" never makes anything for it.
     */
    public static ItemStack take(List<LogisticsMember> members, java.util.function.Predicate<ItemStack> wants, int max, boolean simulate,
                                 boolean reals, boolean made) {
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
            for (LogisticsMember source : byPriority(members, role == LogisticsMember.Role.PROVIDER)) {
                if (source.role() != role) continue;
                IItemHandler from = source.inventory();
                if (from == null) continue;
                for (int slot = 0; slot < from.getSlots(); slot++) {
                    ItemStack there = from.getStackInSlot(slot);
                    if (there.isEmpty() || !wants.test(there) || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                    boolean onDemand = source.onDemand(slot);
                    if (onDemand ? !made || !source.offersCraftable() : !reals) continue;
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
