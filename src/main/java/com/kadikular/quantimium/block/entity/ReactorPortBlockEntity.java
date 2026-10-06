package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.ReactorPortBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Reactor port. It knows its Horizon Core once the core has found it, and forwards to it:
 *
 * <ul>
 *   <li><b>Input</b> takes items for the horizon. What a pipe pushes is only added to the ledger
 *   when its transaction commits, so a simulated insert costs nothing.</li>
 *   <li><b>Output</b> is a buffer of {@link #OUTPUT_SLOTS} slots the Reactor fills and pipes empty.</li>
 *   <li><b>Energy</b> hands power straight to the core.</li>
 *   <li><b>Materialiser</b> offers everything the Reactor holds or could make, one slot each, with
 *   its counted amount; see {@link MaterialiserHandler}.</li>
 * </ul>
 */
public class ReactorPortBlockEntity extends BlockEntity implements com.kadikular.quantimium.flux.FluxMeterReadout {

    public static final int OUTPUT_SLOTS = 9;

    @Nullable
    private BlockPos core;

    /** Only the Reactor fills the output buffer; pipes can only take from it. */
    private boolean delivering;

    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delivering;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final InputHandler input = new InputHandler();
    private final MaterialiserHandler materialiser = new MaterialiserHandler();

    public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.REACTOR_PORT_BE.get(), pos, state);
    }

    /** For a port of another block entity type: the ME Superposition Port. */
    protected ReactorPortBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                     BlockState state) {
        super(type, pos, state);
    }

    public ReactorPortBlock.Kind kind() {
        return getBlockState().getBlock() instanceof ReactorPortBlock port ? port.kind() : ReactorPortBlock.Kind.INPUT;
    }

    /** Set by the core when it finds this port, cleared when it loses it. */
    public void link(@Nullable BlockPos core) {
        if (java.util.Objects.equals(this.core, core)) return;
        this.core = core == null ? null : core.immutable();
        setChanged();
        invalidateCapabilities();
    }

    @Nullable
    public HorizonCoreBlockEntity core() {
        if (core == null || level == null || !level.isLoaded(core)) return null;
        return level.getBlockEntity(core) instanceof HorizonCoreBlockEntity horizon ? horizon : null;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    /** What the item capability offers, by kind. */
    @Nullable
    public ResourceHandler<ItemResource> itemHandler() {
        return switch (kind()) {
            case INPUT -> input;
            case OUTPUT -> output;
            case ENERGY, ME -> null;
            case MATERIALISER -> materialiser;
        };
    }

    @Nullable
    public EnergyHandler energyHandler() {
        if (kind() != ReactorPortBlock.Kind.ENERGY) return null;
        HorizonCoreBlockEntity horizon = core();
        return horizon == null ? null : horizon.getEnergyStorage();
    }

    /**
     * What the Flux Meter and Jade say of a Materialiser Port: what it shows, or why it shows nothing.
     * Server side.
     */
    @Override
    @Nullable
    public net.minecraft.network.chat.MutableComponent fluxMeterLine() {
        if (kind() != ReactorPortBlock.Kind.MATERIALISER) return null;
        HorizonCoreBlockEntity horizon = core();
        if (horizon == null) {
            return net.minecraft.network.chat.Component.translatable("message.quantimium.reactor_port.unlinked")
                    .withStyle(net.minecraft.ChatFormatting.GRAY);
        }
        if (horizon.isDarkPort(worldPosition)) {
            return net.minecraft.network.chat.Component.translatable("message.quantimium.reactor_port.dark")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW);
        }
        return net.minecraft.network.chat.Component.translatable("message.quantimium.reactor_port.showing",
                        String.format(java.util.Locale.ROOT, "%,d", horizon.getOwnCounts().counts().size()))
                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA);
    }

    /** Room for {@code stack} in the output buffer, as a count. */
    public int roomFor(ItemStack stack) {
        int room = 0;
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            ItemStack there = output.getStackInSlot(slot);
            if (there.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(there, stack)) room += there.getMaxStackSize() - there.getCount();
        }
        return room;
    }

    /** Puts {@code stack} in the output buffer; returns what didn't fit. */
    public ItemStack deliver(ItemStack stack) {
        ItemStack left = stack;
        delivering = true;
        try {
            for (int slot = 0; slot < OUTPUT_SLOTS && !left.isEmpty(); slot++) {
                left = output.insertItem(slot, left, false);
            }
        } finally {
            delivering = false;
        }
        return left;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output.getStackInSlot(slot));
            output.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (core != null) out.store("Core", BlockPos.CODEC, core);
        output.serialize(out.child("Output"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        core = in.read("Core", BlockPos.CODEC).orElse(null);
        in.child("Output").ifPresent(output::deserialize);
    }

    /**
     * Input to the horizon: one slot that always looks empty and takes anything the core has room for.
     * Inserts wait in a journal until the outermost transaction commits.
     */
    private final class InputHandler extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
        private record Pending(ItemResource item, int amount) {}

        private final List<Pending> pending = new ArrayList<>();

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            HorizonCoreBlockEntity horizon = core();
            return horizon == null ? 0 : Math.max(0, horizon.room() - pendingTotal());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            HorizonCoreBlockEntity horizon = core();
            return horizon != null && horizon.accepts();
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) return 0;
            HorizonCoreBlockEntity horizon = core();
            if (horizon == null || !horizon.accepts()) return 0;
            int taken = (int) Math.min(amount, Math.max(0, horizon.room() - pendingTotal()));
            if (taken <= 0) return 0;
            updateSnapshots(transaction);
            pending.add(new Pending(resource, taken));
            return taken;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        private long pendingTotal() {
            long total = 0;
            for (Pending entry : pending) total += entry.amount();
            return total;
        }

        @Override
        protected Integer createSnapshot() {
            return pending.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            while (pending.size() > snapshot) pending.removeLast();
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            HorizonCoreBlockEntity horizon = core();
            for (Pending entry : pending) {
                if (horizon != null) horizon.take(entry.item(), entry.amount());
            }
            pending.clear();
        }
    }

    /**
     * The Materialiser Port's view of the horizon: one slot for everything held or makeable, holding
     * its counted amount, and one empty slot at the end to put things in.
     *
     * <p>Looking is free: the slots are the core's last count. Taking is exact: the planner runs for the
     * amount asked against the ledger as this transaction has left it, trims to what really can be made
     * and paid for, and reserves it. Nothing is used until the outermost transaction commits, so a
     * simulated extraction, which pipes and storage buses make constantly, costs nothing but the plan.
     * Asked twice in a tick for the same thing with nothing pending, it answers from the last plan.
     */
    private final class MaterialiserHandler extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
        /** At most this many plans to find how much of an ask can really be made. */
        private static final int MAX_PLANS = 7;

        /** Something this transaction took (a plan) or put in (no plan). */
        private record Op(ItemResource item, int amount, @Nullable com.kadikular.quantimium.reactor.ReactorPlanner.Plan plan,
                          long fe) {}

        private final List<Op> ops = new ArrayList<>();
        private com.kadikular.quantimium.reactor.ReactorCounter.Counts slotsFrom;
        private List<ItemResource> slots = List.of();
        private List<Long> amounts = List.of();
        /** The last ask answered with nothing pending: when, against which ledger, and the answer. */
        private long cachedTick = Long.MIN_VALUE;
        private int cachedVersion;
        private ItemResource cachedItem;
        private int cachedAmount;
        private Op cachedAnswer;

        /**
         * The core, unless this port is to show nothing: while the core is reading or taking from a
         * network (which might be reading this very port), or while a network it's linked to reads
         * this port through a storage bus and would see it twice.
         */
        @Nullable
        private HorizonCoreBlockEntity visibleCore() {
            HorizonCoreBlockEntity horizon = core();
            if (horizon == null || horizon.isDrawing() || horizon.isDarkPort(worldPosition)) return null;
            return horizon;
        }

        private void refreshSlots(HorizonCoreBlockEntity horizon) {
            com.kadikular.quantimium.reactor.ReactorCounter.Counts counts = horizon.getOwnCounts();
            if (counts == slotsFrom) return;
            slotsFrom = counts;
            List<ItemResource> items = new ArrayList<>(counts.counts().size());
            List<Long> values = new ArrayList<>(counts.counts().size());
            counts.counts().forEach((item, amount) -> {
                items.add(item);
                values.add(amount);
            });
            slots = items;
            amounts = values;
        }

        @Override
        public int size() {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null) return 0;
            refreshSlots(horizon);
            return slots.size() + 1;
        }

        @Override
        public ItemResource getResource(int index) {
            if (visibleCore() == null) return ItemResource.EMPTY;
            return index >= 0 && index < slots.size() ? slots.get(index) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            if (visibleCore() == null) return 0;
            return index >= 0 && index < amounts.size() ? amounts.get(index) : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            HorizonCoreBlockEntity horizon = visibleCore();
            return horizon != null && horizon.accepts() && com.kadikular.quantimium.Config.materialiserPortAcceptsItems();
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return insert(resource, amount, transaction);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !com.kadikular.quantimium.Config.materialiserPortAcceptsItems()) return 0;
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null || !horizon.accepts()) return 0;
            long pending = 0;
            for (Op op : ops) {
                if (op.plan() == null) pending += op.amount();
            }
            int taken = (int) Math.min(amount, Math.max(0, horizon.room() - pending));
            if (taken <= 0) return 0;
            updateSnapshots(transaction);
            ops.add(new Op(resource, taken, null, 0));
            return taken;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (index < 0 || index >= slots.size() || !slots.get(index).equals(resource)) return 0;
            return extract(resource, amount, transaction);
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) return 0;
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null || !horizon.isActive() || level == null) return 0;
            Op answer = answer(horizon, resource, amount);
            if (answer == null) return 0;
            updateSnapshots(transaction);
            ops.add(answer);
            return answer.amount();
        }

        /** As much of {@code amount} as can really be made and paid for now, planned; null for none. */
        @Nullable
        private Op answer(HorizonCoreBlockEntity horizon, ItemResource item, int amount) {
            long tick = level.getGameTime();
            boolean clean = ops.isEmpty();
            if (clean && cachedTick == tick && cachedVersion == horizon.ledgerVersion() && item.equals(cachedItem)
                    && cachedAmount == amount) {
                return cachedAnswer;
            }
            java.util.Map<ItemResource, Long> stock = horizon.getLedger().snapshot();
            long fe = horizon.getEnergyStorage().getEnergyStored();
            for (Op op : ops) {
                if (op.plan() == null) {
                    stock.merge(op.item(), (long) op.amount(), Long::sum);
                    continue;
                }
                op.plan().consumed().forEach((taken, count) -> stock.computeIfPresent(taken, (k, have) -> have > count ? have - count : null));
                op.plan().leftovers().forEach((left, count) -> stock.merge(left, count, Long::sum));
                fe -= op.fe();
            }
            Op best = null;
            int low = 1;
            int high = amount;
            int plans = 0;
            // The full ask first; if that can't be done, halve towards what can.
            while (low <= high && plans < MAX_PLANS) {
                int tryAmount = plans == 0 ? high : (low + high) / 2;
                plans++;
                com.kadikular.quantimium.reactor.ReactorPlanner.Result result = horizon.plan(stock, item, tryAmount);
                long cost = result.planned() ? HorizonCoreBlockEntity.feFor(result.plan()) : Long.MAX_VALUE;
                if (result.planned() && cost <= fe) {
                    best = new Op(item, tryAmount, result.plan(), cost);
                    low = tryAmount + 1;
                } else {
                    high = tryAmount - 1;
                }
            }
            if (clean) {
                cachedTick = tick;
                cachedVersion = horizon.ledgerVersion();
                cachedItem = item;
                cachedAmount = amount;
                cachedAnswer = best;
            }
            return best;
        }

        @Override
        protected Integer createSnapshot() {
            return ops.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            while (ops.size() > snapshot) ops.removeLast();
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            HorizonCoreBlockEntity horizon = core();
            if (horizon != null && level instanceof net.minecraft.server.level.ServerLevel server) {
                for (Op op : ops) {
                    if (op.plan() == null) horizon.take(op.item(), op.amount());
                    else horizon.spend(server, op.plan(), op.fe());
                }
            }
            ops.clear();
        }
    }
}
