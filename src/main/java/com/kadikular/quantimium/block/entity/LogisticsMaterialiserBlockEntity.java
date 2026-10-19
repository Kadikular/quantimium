package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.Logistics;
import com.kadikular.quantimium.laser.LogisticsController;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Logistics Materialiser: where a controller's light ends, it offers the whole logistics network to any mod's
 * pipes and storage buses, as the Reactor's Materialiser Port offers the Reactor. One slot for each thing the network's
 * storage holds or its providers offer, with how many; and one empty slot, where what's put in goes into storage.
 * Each transfer costs the controller a move. Light ends at it.
 */
public class LogisticsMaterialiserBlockEntity extends BlockEntity implements LaserNode {

    /** One thing on offer, and how many. */
    private record Entry(ItemResource item, long amount) {}

    private long entriesTick = Long.MIN_VALUE;
    private long entriesEpoch = Long.MIN_VALUE;
    private final Map<Long, List<Entry>> entriesByContext = new java.util.HashMap<>();

    private final NetworkHandler handler = new NetworkHandler();

    public LogisticsMaterialiserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_MATERIALISER_BE.get(), pos, state);
    }

    /** What pipes and storage buses see on any face. */
    public ResourceHandler<ItemResource> itemHandler() {
        return handler;
    }

    /**
     * Asked with no face (other mods' screens, inventory viewers): the same, a stack at most to a slot, since
     * such screens turn each slot into an item stack, and one of thousands can't be sent to a client.
     */
    public ResourceHandler<ItemResource> viewerHandler() {
        return viewer;
    }

    private final ResourceHandler<ItemResource> viewer = new ResourceHandler<>() {
        @Override
        public int size() {
            return handler.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return handler.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            ItemResource item = handler.getResource(index);
            return item.isEmpty() ? 0 : Math.min(handler.getAmountAsLong(index), item.toStack(1).getMaxStackSize());
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return resource.isEmpty() ? 64 : resource.toStack(1).getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return handler.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return handler.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return handler.extract(index, resource, (int) Math.min(amount, getAmountAsLong(index)), transaction);
        }
    };

    @Nullable
    private LogisticsController controller() {
        return level == null ? null : LaserNetworks.logisticsAt(level, worldPosition);
    }

    /** The network's members, never a receptacle on a Logistics Materialiser: that one would read the network into itself. */
    private List<LogisticsMember> members() {
        LogisticsController controller = controller();
        if (controller == null || level == null) return List.of();
        return controller.members().stream()
                .filter(member -> !(level.getBlockEntity(member.inventoryPos()) instanceof LogisticsMaterialiserBlockEntity))
                .toList();
    }

    private List<Entry> entries() {
        if (level == null) return List.of();
        long tick = level.getGameTime();
        // A tick at most, and not past a transaction settling: a Crafter on the network may just have been put back.
        long epoch = com.kadikular.quantimium.recipe.CraftChain.epoch();
        // Reading the network can ask a Crafter what it can make, which can read this list again: a list is kept
        // only once built, per chain (a machine on it offers nothing), for a tick and within a settled transaction,
        // and never one built while a transaction closes.
        long context = com.kadikular.quantimium.recipe.CraftChain.context();
        if (entriesTick != tick || entriesEpoch != epoch) {
            entriesByContext.clear();
            entriesTick = tick;
            entriesEpoch = epoch;
        }
        List<Entry> known = entriesByContext.get(context);
        if (known != null) return known;
        boolean keep = com.kadikular.quantimium.recipe.CraftChain.cacheable();
        Map<ItemResource, Long> counts = new LinkedHashMap<>();
        for (LogisticsMember member : members()) {
            if (member.role() == LogisticsMember.Role.REQUESTER) continue;
            IItemHandler inventory = member.inventory();
            if (inventory == null) continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || member.role() == LogisticsMember.Role.PROVIDER && !member.lets(stack)) continue;
                counts.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
            }
        }
        List<Entry> list = new ArrayList<>(counts.size());
        counts.forEach((item, amount) -> list.add(new Entry(item, amount)));
        if (keep && entriesTick == tick && entriesEpoch == epoch) entriesByContext.put(context, list);
        return list;
    }

    /**
     * The network as a handler. A move happens at once, inside the caller's transaction: the members' inventories
     * are reached through {@link com.kadikular.quantimium.util.LegacyItems#legacy}, so they join it, and an abort
     * puts everything back. What it says it moved, it moved. The controller is paid a move each when the
     * outermost transaction commits.
     */
    private final class NetworkHandler extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
        /** Moves made in open transactions, paid for when the outermost commits. */
        private int moves;

        @Override
        public int size() {
            return controller() == null ? 0 : entries().size() + 1;
        }

        @Override
        public ItemResource getResource(int index) {
            List<Entry> list = entries();
            return index >= 0 && index < list.size() ? list.get(index).item() : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            List<Entry> list = entries();
            return index >= 0 && index < list.size() ? list.get(index).amount() : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return controller() != null;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            LogisticsController controller = controller();
            if (resource.isEmpty() || amount <= 0 || controller == null || !controller.powered()) return 0;
            ItemStack left = Logistics.insert(members(), resource.toStack(amount), false, false);
            int put = amount - left.getCount();
            if (put > 0) moved(transaction);
            return put;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            LogisticsController controller = controller();
            if (resource.isEmpty() || amount <= 0 || controller == null || !controller.powered()) return 0;
            int got = 0;
            List<LogisticsMember> members = members();
            while (got < amount) {
                ItemStack more = Logistics.take(members, stack -> ItemResource.of(stack).equals(resource), amount - got, false);
                if (more.isEmpty()) break;
                got += more.getCount();
            }
            if (got > 0) moved(transaction);
            return got;
        }

        private void moved(TransactionContext transaction) {
            updateSnapshots(transaction);
            moves++;
            entriesTick = Long.MIN_VALUE;
        }

        @Override
        protected Integer createSnapshot() {
            return moves;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            moves = snapshot;
            entriesTick = Long.MIN_VALUE;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            LogisticsController controller = controller();
            if (controller != null) for (int i = 0; i < moves; i++) controller.pay(LaserTuning.LOGISTICS_FE_PER_MOVE);
            moves = 0;
            entriesTick = Long.MIN_VALUE;
        }
    }

    // ---- the network: light ends here ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    /** It passes nothing on; a relay only so the light reaching it is known. */
    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public double tax() {
        return 0;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) LaserNetworks.add(level, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) LaserNetworks.remove(level, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) LaserNetworks.remove(level, this);
    }
}
