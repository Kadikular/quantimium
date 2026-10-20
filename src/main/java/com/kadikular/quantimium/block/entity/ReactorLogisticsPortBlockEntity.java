package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.Logistics;
import com.kadikular.quantimium.laser.LogisticsController;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.reactor.ReactorNetwork;
import com.kadikular.quantimium.recipe.FilterEntry;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Reactor Logistics Port: a Reactor port that joins the Reactor to the logistics network whose controller's light
 * ends on it, both ways, as the ME Superposition Port does for an ME network.
 *
 * <ul>
 *   <li>To the network it is storage, at its priority: it offers what the Reactor holds, what its other linked
 *       networks hold (an ME network's, passed through), and what could be made from all of it, made when taken; and,
 *       if set to store, takes things into the horizon. Its filter says what it offers.</li>
 *   <li>To the Reactor, if set to share, the network is a linked network: what it holds can be used in what the
 *       Reactor makes, and is shown on through an ME Superposition Port.</li>
 * </ul>
 * Light ending on it powers the Reactor.
 */
public class ReactorLogisticsPortBlockEntity extends ReactorPortBlockEntity implements LaserNode, LogisticsMember, ReactorNetwork, MenuProvider {

    public static final int FILTER_SLOTS = 9;
    public static final int MAX_PRIORITY = 999;

    private int priority;
    /** Whether the network may store things in the Reactor. */
    private boolean storing = true;
    /** Whether the Reactor may use what the network holds, and show it on to its ME network. */
    private boolean sharing = true;
    /** Ghost entries: what it offers the network, held or made. Empty, everything. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            ReactorLogisticsPortBlockEntity.this.setChanged();
        }
    };
    private boolean allow = true;

    /** One thing it offers: held in the horizon, held by another linked network, and how many more could be made. */
    private record Offer(ItemResource item, long held, long passed, long craftable) {
        long total() {
            return held + passed + craftable;
        }
    }

    /** What it offers, worked out once a tick so slot numbers hold still while something reads them. */
    private List<Offer> offers = List.of();
    private long offersTick = Long.MIN_VALUE;
    /** The Reactor's ledger and counts the offers were worked out from. */
    private int offersVersion;
    @Nullable
    private Object offersCounts;
    /** What the network holds, for the Reactor, once a tick. */
    private Map<ItemResource, Long> networkStock = Map.of();
    private long networkStockTick = Long.MIN_VALUE;

    public ReactorLogisticsPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_LOGISTICS_PORT_BE.get(), pos, state);
    }

    /** The Reactor's power, for light ending on it. */
    @Nullable
    public EnergyHandler beamEnergy() {
        HorizonCoreBlockEntity horizon = core();
        return horizon == null ? null : horizon.getEnergyStorage();
    }

    // ---- settings ----

    public int priorityValue() {
        return priority;
    }

    public boolean storing() {
        return storing;
    }

    public boolean sharing() {
        return sharing;
    }

    public boolean allows() {
        return allow;
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    public void addPriority(int by) {
        priority = Math.clamp((long) priority + by, -MAX_PRIORITY, MAX_PRIORITY);
        changed();
    }

    public void toggleStoring() {
        storing = !storing;
        changed();
    }

    public void toggleSharing() {
        sharing = !sharing;
        changed();
    }

    public void toggleAllow() {
        allow = !allow;
        changed();
    }

    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS) filter.setItem(slot, FilterEntry.of(stack));
        offersTick = Long.MIN_VALUE;
    }

    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, FilterEntry.cycle(entry));
        offersTick = Long.MIN_VALUE;
    }

    /** Whether its filter lets it offer {@code item}. */
    private boolean offers(ItemResource item) {
        boolean listed = false;
        boolean any = false;
        ItemStack stack = item.toStack(1);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (FilterEntry.matches(entry, stack)) listed = true;
        }
        return !any || listed == allow;
    }

    private void changed() {
        offersTick = Long.MIN_VALUE;
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- what it offers the network ----

    /** The core, unless it's reading its networks just now (which may be reading this very port). */
    @Nullable
    private HorizonCoreBlockEntity visibleCore() {
        HorizonCoreBlockEntity horizon = core();
        return horizon == null || !horizon.isFormed() || horizon.isDrawing() ? null : horizon;
    }

    /** The Reactor's other linked networks: an ME network, never this port's own. */
    private List<ReactorNetwork> otherNetworks(HorizonCoreBlockEntity horizon) {
        if (!(level instanceof ServerLevel server)) return List.of();
        Object mine = network();
        List<ReactorNetwork> others = new ArrayList<>();
        for (ReactorNetwork network : horizon.networks(server)) {
            if (network != this && (mine == null || network.network() != mine)) others.add(network);
        }
        return others;
    }

    /**
     * What it offers, worked out again only when the Reactor's holdings or counts change, or once a second for what
     * its other networks hold: every Insert lens and terminal on the network reads it.
     */
    private List<Offer> offers() {
        if (level == null) return List.of();
        long tick = level.getGameTime();
        HorizonCoreBlockEntity horizon = visibleCore();
        if (horizon == null) return List.of();
        if (offersTick != Long.MIN_VALUE && tick - offersTick < 20 && offersVersion == horizon.ledgerVersion()
                && offersCounts == horizon.getCounts()) {
            return offers;
        }
        offersTick = tick;
        offersVersion = horizon.ledgerVersion();
        offersCounts = horizon.getCounts();
        Map<ItemResource, Long> held = horizon.available();
        Map<ItemResource, Long> passed = new HashMap<>();
        for (ReactorNetwork network : otherNetworks(horizon)) {
            horizon.whileDrawing(network::stock).forEach((item, amount) -> passed.merge(item, amount, Long::sum));
        }
        Map<ItemResource, Long> totals = horizon.getCounts().counts();
        Map<ItemResource, Long> counted = horizon.countedNetworkStock();
        Map<ItemResource, Offer> found = new LinkedHashMap<>();
        held.forEach((item, amount) -> found.put(item, new Offer(item, amount, 0, 0)));
        passed.forEach((item, amount) -> found.merge(item, new Offer(item, 0, amount, 0), (a, b) -> new Offer(item, a.held(), b.passed(), 0)));
        totals.forEach((item, total) -> {
            long craftable = total - held.getOrDefault(item, 0L) - counted.getOrDefault(item, 0L);
            if (craftable <= 0) return;
            found.merge(item, new Offer(item, 0, 0, craftable), (a, b) -> new Offer(item, a.held(), a.passed(), b.craftable()));
        });
        List<Offer> list = new ArrayList<>();
        for (Offer offer : found.values()) {
            if (offer.total() > 0 && offers(offer.item())) list.add(offer);
        }
        return offers = list;
    }

    /** Takes up to {@code amount} of {@code item}: held first, then from the other networks, then made. */
    private ItemStack give(Offer offer, int amount, boolean simulate) {
        HorizonCoreBlockEntity horizon = visibleCore();
        if (horizon == null || amount <= 0) return ItemStack.EMPTY;
        ItemResource item = offer.item();
        long got = 0;
        long fromHeld = Math.min(amount, horizon.availableCount(item));
        got += simulate ? fromHeld : horizon.withdraw(item, fromHeld);
        for (ReactorNetwork network : otherNetworks(horizon)) {
            if (got >= amount) break;
            long want = amount - got;
            got += horizon.whileDrawing(() -> network.extract(item, want, simulate));
        }
        if (got < amount && offer.craftable() > 0) {
            long want = Math.min(amount - got, offer.craftable());
            if (simulate) {
                got += want;
            } else if (horizon.isActive() && horizon.make(item, want)) {
                got += horizon.withdraw(item, want);
            }
        }
        if (!simulate && got > 0) offersTick = Long.MIN_VALUE;
        return got <= 0 ? ItemStack.EMPTY : item.toStack((int) got);
    }

    /**
     * Moves inside a transaction, as the Reactor's Materialiser Port makes them: what's held is reserved, what's
     * made is planned from what it holds and its ingredients reserved, and on commit they're withdrawn and spent;
     * an abort releases them. Nothing is drawn from its other networks meanwhile: what it took couldn't be put back.
     */
    private final Promises promises = new Promises();

    /**
     * Where a transaction stood: how many promises were made, and the offers as worked out then, if they were still
     * good; an abort puts the Reactor back as it was, so they're good again.
     */
    private record PromisesSnapshot(int ops, List<Offer> offers, long tick, boolean fresh) {}

    private final class Promises extends SnapshotJournal<PromisesSnapshot> {
        private record Op(ItemResource item, long held, @Nullable com.kadikular.quantimium.reactor.ReactorPlanner.Plan plan, long fe, long in) {}

        private final List<Op> ops = new ArrayList<>();

        long pendingIn() {
            long total = 0;
            for (Op op : ops) total += op.in();
            return total;
        }

        void in(ItemResource item, int amount) {
            updateSnapshots(Transaction.getCurrentOpenedTransaction());
            ops.add(new Op(item, 0, null, 0, amount));
        }

        ItemStack out(Offer offer, int amount) {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (horizon == null || amount <= 0) return ItemStack.EMPTY;
            ItemResource item = offer.item();
            long held = Math.min(amount, horizon.availableCount(item));
            com.kadikular.quantimium.reactor.ReactorPlanner.Plan plan = null;
            long fe = 0;
            long want = amount - held;
            if (want > 0 && offer.craftable() > 0 && horizon.isActive()) {
                // With none of it held, what's held is the plan's stock as it stands, and a plan may be one worked out
                // before; with some held (to be handed over first), from what's left.
                Map<ItemResource, Long> stock = null;
                if (held > 0) {
                    // Changed over what's available, not in it: that may be the ledger itself.
                    stock = new com.kadikular.quantimium.reactor.StockOverlay(horizon.available());
                    stock.computeIfPresent(item, (k, have) -> have > held ? have - held : null);
                }
                long spare = horizon.getEnergyStorage().getEnergyStored();
                for (Op op : ops) spare -= op.fe();
                // The whole of the rest if it can be made and paid for, else a half, a quarter: a few plans at most.
                for (long tryAmount = Math.min(want, offer.craftable()); tryAmount > 0 && plan == null; tryAmount /= 2) {
                    var result = stock == null ? horizon.planFromHeld(item, tryAmount) : horizon.plan(stock, item, tryAmount);
                    if (result.planned() && HorizonCoreBlockEntity.feFor(result.plan()) <= spare) {
                        plan = result.plan();
                        fe = HorizonCoreBlockEntity.feFor(plan);
                        want = tryAmount;
                    }
                }
            }
            long made = plan == null ? 0 : want;
            if (held + made <= 0) return ItemStack.EMPTY;
            updateSnapshots(Transaction.getCurrentOpenedTransaction());
            ops.add(new Op(item, held, plan, fe, 0));
            if (held > 0) horizon.reserve(Map.of(item, held));
            if (plan != null) horizon.reserve(plan.consumed());
            offersTick = Long.MIN_VALUE;
            return item.toStack((int) (held + made));
        }

        @Override
        protected PromisesSnapshot createSnapshot() {
            HorizonCoreBlockEntity horizon = core();
            boolean fresh = horizon != null && offersTick != Long.MIN_VALUE && offersVersion == horizon.ledgerVersion()
                    && offersCounts == horizon.getCounts();
            return new PromisesSnapshot(ops.size(), offers, offersTick, fresh);
        }

        @Override
        protected void revertToSnapshot(PromisesSnapshot snapshot) {
            HorizonCoreBlockEntity horizon = core();
            while (ops.size() > snapshot.ops()) {
                Op op = ops.removeLast();
                if (horizon == null) continue;
                if (op.held() > 0) horizon.release(Map.of(op.item(), op.held()));
                if (op.plan() != null) horizon.release(op.plan().consumed());
            }
            // Back as it was: the offers worked out then stand (a storage bus asking what it could take, and
            // calling it off, doesn't make the Reactor plan everything again).
            if (snapshot.fresh() && horizon != null) {
                offers = snapshot.offers();
                offersTick = snapshot.tick();
                offersVersion = horizon.ledgerVersion();
                offersCounts = horizon.getCounts();
            } else {
                offersTick = Long.MIN_VALUE;
            }
        }

        @Override
        protected void onRootCommit(PromisesSnapshot originalState) {
            HorizonCoreBlockEntity horizon = core();
            if (horizon != null && level instanceof ServerLevel server) {
                for (Op op : ops) {
                    if (op.in() > 0) {
                        horizon.take(op.item(), (int) op.in());
                        continue;
                    }
                    if (op.held() > 0) {
                        horizon.release(Map.of(op.item(), op.held()));
                        horizon.withdraw(op.item(), op.held());
                    }
                    if (op.plan() != null) {
                        horizon.release(op.plan().consumed());
                        horizon.spend(server, op.plan(), op.fe());
                    }
                }
            }
            ops.clear();
            offersTick = Long.MIN_VALUE;
        }
    }

    private final IItemHandler offered = new IItemHandler() {
        @Override
        public int getSlots() {
            // One more, empty, so there's somewhere to put things in.
            return visibleCore() == null ? 0 : offers().size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<Offer> list = offers();
            if (slot < 0 || slot >= list.size()) return ItemStack.EMPTY;
            Offer offer = list.get(slot);
            return offer.item().toStack((int) Math.min(Integer.MAX_VALUE, offer.total()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            HorizonCoreBlockEntity horizon = visibleCore();
            if (!storing || stack.isEmpty() || horizon == null || !horizon.accepts()) return stack;
            int fits = (int) Math.min(stack.getCount(), horizon.room() - promises.pendingIn());
            if (fits <= 0) return stack;
            if (!simulate) {
                if (Transaction.getLifecycle() == Transaction.Lifecycle.OPEN) {
                    promises.in(ItemResource.of(stack), fits);
                } else {
                    horizon.take(ItemResource.of(stack), fits);
                    offersTick = Long.MIN_VALUE;
                }
            }
            return stack.copyWithCount(stack.getCount() - fits);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            List<Offer> list = offers();
            if (slot < 0 || slot >= list.size()) return ItemStack.EMPTY;
            // Inside a transaction (a Logistics Materialiser's move, which may yet be undone), only promised.
            if (!simulate && Transaction.getLifecycle() == Transaction.Lifecycle.OPEN) return promises.out(list.get(slot), amount);
            return give(list.get(slot), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return storing;
        }
    };

    // ---- logistics ----

    @Override
    public LogisticsMember logistics() {
        return this;
    }

    @Override
    public Role role() {
        return Role.STORAGE;
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public int priority() {
        return priority;
    }

    /** What may be stored in it. */
    @Override
    public boolean lets(ItemStack stack) {
        return storing;
    }

    @Override
    @Nullable
    public IItemHandler inventory() {
        return core() == null ? null : offered;
    }

    @Override
    public BlockPos inventoryPos() {
        return worldPosition;
    }

    /** For a terminal: what's there, and what more could be made. */
    @Override
    @Nullable
    public Map<ItemResource, long[]> listing() {
        List<Offer> now = offers();
        // Worked out once for each list of offers: a take asks for it every time.
        if (listingFor != now) {
            Map<ItemResource, long[]> listing = new LinkedHashMap<>();
            for (Offer offer : now) listing.put(offer.item(), new long[] {offer.held() + offer.passed(), offer.craftable()});
            listingCache = java.util.Collections.unmodifiableMap(listing);
            listingFor = now;
        }
        return listingCache;
    }

    /** By item, as a network takes from it: what a slot showing it would give. */
    @Override
    public ItemStack takeListed(ItemResource item, int max, boolean simulate) {
        List<Offer> now = offers();
        if (byItemFor != now) {
            byItem = new HashMap<>();
            for (Offer offer : now) byItem.put(offer.item(), offer);
            byItemFor = now;
        }
        Offer offer = byItem.get(item);
        if (offer == null || max <= 0) return ItemStack.EMPTY;
        // Inside a transaction (a Logistics Materialiser's move, which may yet be undone), only promised.
        if (!simulate && Transaction.getLifecycle() == Transaction.Lifecycle.OPEN) return promises.out(offer, max);
        return give(offer, max, simulate);
    }

    @Nullable
    private List<Offer> byItemFor;
    private Map<ItemResource, Offer> byItem = Map.of();

    @Nullable
    private List<Offer> listingFor;
    private Map<ItemResource, long[]> listingCache = Map.of();

    // ---- the network, to the Reactor ----

    @Nullable
    private LogisticsController controller() {
        return level == null ? null : LaserNetworks.logisticsAt(level, worldPosition);
    }

    /** The network's members, never a Reactor Logistics Port: the Reactor is never shown itself. */
    private List<LogisticsMember> networkMembers() {
        LogisticsController controller = controller();
        if (controller == null) return List.of();
        return controller.members().stream().filter(member -> !(member instanceof ReactorLogisticsPortBlockEntity)).toList();
    }

    @Override
    @Nullable
    public Object network() {
        return sharing && core() != null ? controller() : null;
    }

    @Override
    public Map<ItemResource, Long> stock() {
        if (level == null) return Map.of();
        long tick = level.getGameTime();
        if (networkStockTick == tick) return networkStock;
        networkStockTick = tick;
        Map<ItemResource, Long> stock = new HashMap<>();
        for (LogisticsMember member : networkMembers()) {
            if (member.role() == Role.REQUESTER) continue;
            IItemHandler inventory = member.inventory();
            if (inventory == null) continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty() || member.role() == Role.PROVIDER && !member.lets(stack)) continue;
                stock.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
            }
        }
        return networkStock = stock;
    }

    @Override
    public long extract(ItemResource item, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        if (simulate) return Math.min(amount, stock().getOrDefault(item, 0L));
        List<LogisticsMember> members = networkMembers();
        long got = 0;
        while (got < amount) {
            ItemStack more = Logistics.take(members, stack -> ItemResource.of(stack).equals(item), (int) Math.min(Integer.MAX_VALUE, amount - got), false);
            if (more.isEmpty()) break;
            got += more.getCount();
        }
        networkStockTick = Long.MIN_VALUE;
        return got;
    }

    /** Whether a receptacle on this network faces {@code side} at {@code pos}: a Materialiser Port it reads would show twice. */
    @Override
    public boolean readsFrom(Level level, BlockPos pos, Direction side) {
        for (LogisticsMember member : networkMembers()) {
            if (member instanceof LogisticsReceptacleBlockEntity receptacle && receptacle.getBlockPos().equals(pos) && receptacle.facing() == side) return true;
        }
        return false;
    }

    // ---- the laser network: light ends here ----

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
    public boolean chargedByLight() {
        return true;
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

    // ---- screen and saving ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.reactor_logistics_port");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.ReactorLogisticsPortMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Priority", priority);
        out.putBoolean("Storing", storing);
        out.putBoolean("Sharing", sharing);
        out.putBoolean("Allow", allow);
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        priority = in.getIntOr("Priority", 0);
        storing = in.getBooleanOr("Storing", true);
        sharing = in.getBooleanOr("Sharing", true);
        allow = in.getBooleanOr("Allow", true);
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
