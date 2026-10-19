package com.kadikular.quantimium.laser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
                    if (role == LogisticsMember.Role.REQUESTER && !to.requestsCraftable() && split(provider, provider.listing(), slot, there)[0] <= 0) continue;
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

    /**
     * An active requester pulls what it lists: from providers, then storage, lowest priority first; what's stored
     * first, and only then, if it may, what machines would make. How many it moved, or {@link #UNPAID}.
     */
    private static int pull(Level level, LogisticsController controller, LogisticsMember requester, List<LogisticsMember> highestFirst,
                            List<LogisticsMember> lowestFirst, int most) {
        IItemHandler into = requester.inventory();
        if (into == null) return 0;
        int budget = most;
        for (boolean made : requester.requestsCraftable() ? new boolean[] {false, true} : new boolean[] {false}) {
            for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
                for (LogisticsMember source : role == LogisticsMember.Role.PROVIDER ? highestFirst : lowestFirst) {
                    if (source.role() != role || source.inventoryPos().equals(requester.inventoryPos())) continue;
                    IItemHandler from = source.inventory();
                    if (from == null) continue;
                    Map<ItemResource, long[]> listing = source.listing();
                    for (int slot = 0; slot < from.getSlots() && budget > 0; slot++) {
                        ItemStack there = from.getStackInSlot(slot);
                        // Storage gives up anything it holds; a provider only what its filter lets go.
                        if (there.isEmpty() || !requester.lets(there) || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                        int room = Math.min(Math.min(budget, requester.wants(there)), split(source, listing, slot, there)[made ? 1 : 0]);
                        if (room <= 0) continue;
                        int at = slot;
                        int moved = pulling(source, slot, () -> move(level, controller, source, from, at, into, room));
                        if (moved == UNPAID) return UNPAID;
                        budget -= moved;
                    }
                    if (budget <= 0) return most;
                }
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
     * both, what's there first: nothing is made while it's stored somewhere. A provider whose mode is "only what's
     * there" never makes anything for it.
     */
    public static ItemStack take(List<LogisticsMember> members, java.util.function.Predicate<ItemStack> wants, int max, boolean simulate,
                                 boolean reals, boolean made) {
        if (reals) {
            ItemStack stored = takeFrom(members, wants, max, simulate, true, false);
            if (!stored.isEmpty() || !made) return stored;
        }
        return made ? takeFrom(members, wants, max, simulate, false, true) : ItemStack.EMPTY;
    }

    private static ItemStack takeFrom(List<LogisticsMember> members, java.util.function.Predicate<ItemStack> wants, int max, boolean simulate,
                                      boolean reals, boolean made) {
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
            for (LogisticsMember source : byPriority(members, role == LogisticsMember.Role.PROVIDER)) {
                if (source.role() != role) continue;
                IItemHandler from = source.inventory();
                if (from == null) continue;
                Map<ItemResource, long[]> listing = source.listing();
                for (int slot = 0; slot < from.getSlots(); slot++) {
                    ItemStack there = from.getStackInSlot(slot);
                    if (there.isEmpty() || !wants.test(there) || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                    int[] parts = split(source, listing, slot, there);
                    int most = Math.min(max, (reals ? parts[0] : 0) + (made ? parts[1] : 0));
                    if (most <= 0) continue;
                    int at = slot;
                    ItemStack taken = pulling(source, slot, () -> from.extractItem(at, most, simulate));
                    if (!taken.isEmpty()) return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** One slot of a member's inventory where something was seen. */
    public record Spot(LogisticsMember member, int slot) {}

    /**
     * Where each thing on a network is, in the order a take looks: providers (as their filters let go), highest
     * priority first, then storage, lowest first. One look over everything, so a take after it looks at the few slots
     * holding what it wants rather than at every slot again.
     */
    public static Map<ItemResource, List<Spot>> locate(List<LogisticsMember> members) {
        Map<ItemResource, List<Spot>> spots = new HashMap<>();
        for (LogisticsMember.Role role : new LogisticsMember.Role[] {LogisticsMember.Role.PROVIDER, LogisticsMember.Role.STORAGE}) {
            for (LogisticsMember source : byPriority(members, role == LogisticsMember.Role.PROVIDER)) {
                if (source.role() != role) continue;
                IItemHandler from = source.inventory();
                if (from == null) continue;
                for (int slot = 0; slot < from.getSlots(); slot++) {
                    ItemStack there = from.getStackInSlot(slot);
                    if (there.isEmpty() || role == LogisticsMember.Role.PROVIDER && !source.lets(there)) continue;
                    spots.computeIfAbsent(ItemResource.of(there), k -> new ArrayList<>(2)).add(new Spot(source, slot));
                }
            }
        }
        return spots;
    }

    /**
     * As {@link #take(List, java.util.function.Predicate, int, boolean, boolean, boolean)} for one thing, looking only
     * where {@code spots} last saw it ({@link LogisticsController#spots()}); each slot is checked to hold it still.
     */
    public static ItemStack take(Map<ItemResource, List<Spot>> spots, ItemResource item, int max, boolean simulate, boolean reals, boolean made) {
        return take(spots, null, item, max, simulate, reals, made);
    }

    /** As {@link #take(Map, ItemResource, int, boolean, boolean, boolean)}, from {@code only} those members, if set. */
    public static ItemStack take(Map<ItemResource, List<Spot>> spots, @org.jetbrains.annotations.Nullable Set<LogisticsMember> only,
                                 ItemResource item, int max, boolean simulate, boolean reals, boolean made) {
        List<Spot> where = spots.get(item);
        if (where == null) return ItemStack.EMPTY;
        if (only != null) where = where.stream().filter(spot -> only.contains(spot.member())).toList();
        if (reals) {
            ItemStack stored = takeAt(where, item, max, simulate, true, false);
            if (!stored.isEmpty() || !made) return stored;
        }
        return made ? takeAt(where, item, max, simulate, false, true) : ItemStack.EMPTY;
    }

    private static ItemStack takeAt(List<Spot> where, ItemResource item, int max, boolean simulate, boolean reals, boolean made) {
        Map<LogisticsMember, Map<ItemResource, long[]>> listings = new HashMap<>();
        for (Spot spot : where) {
            LogisticsMember source = spot.member();
            IItemHandler from = source.inventory();
            if (from == null || spot.slot() >= from.getSlots()) continue;
            ItemStack there = from.getStackInSlot(spot.slot());
            if (there.isEmpty() || !item.matches(there)) continue;
            Map<ItemResource, long[]> listing = listings.computeIfAbsent(source, LogisticsMember::listing);
            int[] parts = split(source, listing, spot.slot(), there);
            int most = Math.min(max, (reals ? parts[0] : 0) + (made ? parts[1] : 0));
            if (most <= 0) continue;
            int at = spot.slot();
            ItemStack taken = pulling(source, at, () -> from.extractItem(at, most, simulate));
            if (!taken.isEmpty()) return taken;
        }
        return ItemStack.EMPTY;
    }

    /**
     * How much of what a member shows in {@code slot} is really there, and how much more a pull would make: a
     * Crafter's preview is all made, a Reactor port's slot is what it holds and then what it could make.
     */
    private static int[] split(LogisticsMember source, @org.jetbrains.annotations.Nullable Map<ItemResource, long[]> listing, int slot, ItemStack there) {
        if (source.onDemand(slot)) return new int[] {0, source.offersCraftable() ? there.getCount() : 0};
        long[] both = listing == null ? null : listing.get(ItemResource.of(there));
        if (both == null) return new int[] {there.getCount(), 0};
        int held = (int) Math.min(there.getCount(), both[0]);
        return new int[] {held, source.offersCraftable() ? there.getCount() - held : 0};
    }

    /** Runs {@code pull} from {@code slot}: from a machine set to craft from stored items only, what it draws on makes nothing for it. */
    private static <T> T pulling(LogisticsMember source, int slot, java.util.function.Supplier<T> pull) {
        return source.onDemand(slot) && !source.craftsFromCraftable() ? com.kadikular.quantimium.recipe.CraftChain.storedOnly(pull) : pull.get();
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
