package com.kadikular.quantimium.recipe;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.TesseractItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolve and fingerprint Entangled Links. Nested links inside a remote inventory are never followed.
 */
public final class EntangledLinks {
    public static final int SEMI_STABLE_RANGE = 16;

    private EntangledLinks() {}

    public static boolean isLink(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TesseractItem;
    }

    public static boolean isSemiStable(ItemStack stack) {
        return isLink(stack) && stack.getItem() instanceof TesseractItem link && link.isSemiStable();
    }

    public static boolean fitsStabilizer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.TESSERACT.get());
    }

    public static boolean isBound(ItemStack stack) {
        return isLink(stack) && stack.get(ModDataComponents.BOUND_POS.get()) != null;
    }

    @Nullable
    public static GlobalPos boundPos(ItemStack stack) {
        return isBound(stack) ? stack.get(ModDataComponents.BOUND_POS.get()) : null;
    }

    @Nullable
    public static Direction boundSide(ItemStack stack) {
        return isBound(stack) ? stack.get(ModDataComponents.BOUND_SIDE.get()) : null;
    }

    /** The block this link was bound to, for display, or null if it was never captured or is unknown. */
    @Nullable
    public static Block boundBlock(ItemStack stack) {
        if (!isBound(stack)) return null;
        Identifier id = stack.get(ModDataComponents.BOUND_BLOCK.get());
        if (id == null) return null;
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block == Blocks.AIR ? null : block;
    }

    @Nullable
    public static IItemHandler resolve(@Nullable Level level, ItemStack link) {
        return resolve(level, null, link);
    }

    @Nullable
    public static IItemHandler resolve(@Nullable Level level, @Nullable BlockPos source, ItemStack link) {
        return LegacyItems.legacy(resolveItems(level, source, link));
    }

    @Nullable
    public static ResourceHandler<ItemResource> resolveItems(@Nullable Level level, ItemStack link) {
        return resolveItems(level, null, link);
    }

    @Nullable
    public static ResourceHandler<ItemResource> resolveItems(@Nullable Level level, @Nullable BlockPos source,
                                                             ItemStack link) {
        if (level == null || level.isClientSide() || !isBound(link)) return null;
        GlobalPos bound = link.get(ModDataComponents.BOUND_POS.get());
        if (bound == null || !inRange(level, source, bound, link)) return null;

        ServerLevel target = resolveLevel(level, bound);
        if (target == null || !matchesBoundBlock(target, bound.pos(), link)) return null;

        Direction side = link.get(ModDataComponents.BOUND_SIDE.get());
        return target.getCapability(Capabilities.Item.BLOCK, bound.pos(), side);
    }

    @Nullable
    public static IFluidHandler resolveFluid(@Nullable Level level, ItemStack link) {
        ResourceHandler<FluidResource> fluids = resolveFluids(level, link);
        return fluids == null ? null : IFluidHandler.of(fluids);
    }

    @Nullable
    public static ResourceHandler<FluidResource> resolveFluids(@Nullable Level level, ItemStack link) {
        if (level == null || level.isClientSide() || !isBound(link) || isSemiStable(link)) return null;
        GlobalPos bound = link.get(ModDataComponents.BOUND_POS.get());
        if (bound == null) return null;

        ServerLevel target = resolveLevel(level, bound);
        if (target == null || !matchesBoundBlock(target, bound.pos(), link)) return null;

        Direction side = link.get(ModDataComponents.BOUND_SIDE.get());
        return target.getCapability(Capabilities.Fluid.BLOCK, bound.pos(), side);
    }

    /**
     * Client- and server-safe check that the bound chunk is loaded in this level's dimension.
     * Does not require a capability — used for status and the red shell.
     */
    public static boolean isBoundReachable(@Nullable Level level, ItemStack link) {
        return isBoundReachable(level, null, link);
    }

    public static boolean isBoundReachable(@Nullable Level level, @Nullable BlockPos source, ItemStack link) {
        GlobalPos bound = boundPos(link);
        if (bound == null || level == null) return false;
        if (!bound.dimension().equals(level.dimension())) return false;
        return inRange(level, source, bound, link) && matchesBoundBlock(level, bound.pos(), link);
    }

    private static boolean inRange(Level level, @Nullable BlockPos source, GlobalPos bound, ItemStack link) {
        if (!isSemiStable(link)) return true;
        if (source == null || !bound.dimension().equals(level.dimension())) return false;
        BlockPos target = bound.pos();
        return Math.abs(target.getX() - source.getX()) <= SEMI_STABLE_RANGE
                && Math.abs(target.getY() - source.getY()) <= SEMI_STABLE_RANGE
                && Math.abs(target.getZ() - source.getZ()) <= SEMI_STABLE_RANGE;
    }

    private static boolean matchesBoundBlock(Level level, net.minecraft.core.BlockPos pos, ItemStack link) {
        if (!level.isLoaded(pos)) return false;
        Block expected = boundBlock(link);
        return expected != null && level.getBlockState(pos).is(expected);
    }

    @Nullable
    private static ServerLevel resolveLevel(Level probe, GlobalPos bound) {
        if (probe instanceof ServerLevel server && server.dimension().equals(bound.dimension())) {
            return server;
        }
        MinecraftServer server = probe.getServer();
        return server == null ? null : server.getLevel(bound.dimension());
    }

    /**
     * Detect a crafter link graph that contains a cycle, or is too deep to evaluate safely. Only
     * crafter input links form graph edges; ordinary inventories and simulators terminate a branch.
     */
    public static boolean hasUnsafeCrafterCycle(Level level, GlobalPos origin, int maxDepth) {
        return walkCrafterGraph(level, origin, Math.max(1, maxDepth),
                new HashSet<>(), new HashSet<>(), 0);
    }

    private static boolean walkCrafterGraph(Level probe, GlobalPos node, int maxDepth,
                                            Set<GlobalPos> visiting, Set<GlobalPos> complete, int depth) {
        if (depth > maxDepth) return true;
        if (!visiting.add(node)) return true;
        if (complete.contains(node)) {
            visiting.remove(node);
            return false;
        }

        ServerLevel targetLevel = resolveLevel(probe, node);
        if (targetLevel == null || !targetLevel.isLoaded(node.pos())) {
            visiting.remove(node);
            complete.add(node);
            return false;
        }
        BlockEntity target = targetLevel.getBlockEntity(node.pos());
        if (target instanceof QuantumCrafterBlockEntity crafter) {
            ItemStackHandler grid = crafter.getInventory();
            for (int slot = 0; slot < QuantumCrafterBlockEntity.INPUT_SLOTS; slot++) {
                ItemStack link = grid.getStackInSlot(slot);
                GlobalPos next = boundPos(link);
                if (next == null) continue;
                if (!inRange(targetLevel, node.pos(), next, link)) continue;

                ServerLevel nextLevel = resolveLevel(targetLevel, next);
                if (nextLevel == null || !nextLevel.isLoaded(next.pos())) continue;
                BlockEntity nextEntity = nextLevel.getBlockEntity(next.pos());
                if (!(nextEntity instanceof QuantumCrafterBlockEntity nextCrafter)) continue;
                if (nextCrafter.getAutomationItemHandler(boundSide(link)) == null) continue;
                if (walkCrafterGraph(targetLevel, next, maxDepth, visiting, complete, depth + 1)) {
                    visiting.remove(node);
                    return true;
                }
            }
        }
        visiting.remove(node);
        complete.add(node);
        return false;
    }

    /**
     * What a crafter can currently reach through its links, split so the two kinds of change can be
     * treated differently: {@code typeHash} covers which items (and which targets) are reachable,
     * {@code countTotal} only how many. New item types can unlock recipes and need a full rescan;
     * shifting counts only rescale the crafts we already resolved.
     */
    public record RemoteSummary(long typeHash, long countTotal) {
        public static final RemoteSummary EMPTY = new RemoteSummary(0L, 0L);
    }

    public static RemoteSummary summarize(@Nullable Level level, BlockPos source,
                                          ItemStackHandler grid, int inputSlots) {
        long typeHash = 1;
        long countTotal = 0;
        for (int slot = 0; slot < inputSlots; slot++) {
            ItemStack stack = grid.getStackInSlot(slot);
            if (!isBound(stack)) continue;
            GlobalPos pos = stack.get(ModDataComponents.BOUND_POS.get());
            Direction side = stack.get(ModDataComponents.BOUND_SIDE.get());
            typeHash = typeHash * 31 + (pos == null ? 0 : pos.hashCode());
            typeHash = typeHash * 31 + (side == null ? -1 : side.ordinal());

            if (level == null || pos == null || !inRange(level, source, pos, stack)) {
                typeHash = typeHash * 31 - 7;
                continue;
            }
            ServerLevel targetLevel = level == null || pos == null ? null : resolveLevel(level, pos);
            BlockEntity target = targetLevel == null || !targetLevel.isLoaded(pos.pos())
                    ? null : targetLevel.getBlockEntity(pos.pos());
            if (target instanceof QuantumCrafterBlockEntity crafter) {
                // Enumerating virtual outputs recursively asks whether every downstream craft can
                // commit; the crafter summarises its own catalog instead.
                typeHash = typeHash * 31 + crafter.advertisedTypeHash();
                countTotal += crafter.advertisedCount();
                continue;
            }
            IItemHandler handler = resolve(level, source, stack);
            if (handler == null) {
                typeHash = typeHash * 31 - 7;
                continue;
            }
            // Items shuffling between slots (a busy hopper) must not read as a new item type.
            Set<Integer> ids = new TreeSet<>();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack remote = handler.getStackInSlot(i);
                if (remote.isEmpty() || isLink(remote)) continue;
                ids.add(BuiltInRegistries.ITEM.getId(remote.getItem()));
                countTotal += remote.getCount();
            }
            for (int id : ids) typeHash = typeHash * 31 + id;
        }
        return new RemoteSummary(typeHash, countTotal);
    }
}
