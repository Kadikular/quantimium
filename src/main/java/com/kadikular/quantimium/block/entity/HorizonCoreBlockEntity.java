package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.reactor.ReactorCounter;
import com.kadikular.quantimium.reactor.ReactorLedger;
import com.kadikular.quantimium.reactor.ReactorPlanner;
import com.kadikular.quantimium.reactor.ReactorGraph;
import com.kadikular.quantimium.reactor.ReactorNetwork;
import com.kadikular.quantimium.reactor.ReactorRecipes;
import com.kadikular.quantimium.recipe.RecipeFilter;
import com.kadikular.quantimium.reactor.ReactorStructure;
import com.kadikular.quantimium.reactor.ReactorTraces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import com.kadikular.quantimium.menu.HorizonCoreMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Quantimium Reactor's controller and its horizon: a {@link ReactorLedger} of everything put in,
 * and the rings that hold it.
 *
 * <p>Each Ring Emitter pair drives one ring, and each ring quadruples how much the horizon holds
 * ({@link #BASE_CAPACITY} for one). The emitters draw {@link #EMITTER_FE_PER_TICK} each from the
 * core's buffer, fed through Energy ports. Without that power the reactor goes quiet: it takes
 * nothing in and makes nothing, but keeps everything it holds. In this first version nothing it holds
 * is ever at risk; a full horizon simply refuses more.
 *
 * <p>It looks its reactor over every second: the plinth, the emitters, the ports (which it tells where
 * it is) and the Catalyst Bays.
 */
public class HorizonCoreBlockEntity extends BlockEntity implements MenuProvider {

    public static final long BASE_CAPACITY = 1_000_000L;
    public static final int EMITTER_FE_PER_TICK = 1_000;
    public static final int ENERGY_CAPACITY = 20_000_000;
    public static final int MAX_RECEIVE = 500_000;
    private static final int CHECK_TICKS = 20;

    private final ReactorLedger ledger = new ReactorLedger();
    private final QuantumEnergyStorage energy = new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    private ReactorStructure.Layout layout = new ReactorStructure.Layout(0, List.of(), List.of(), List.of(),
            Component.translatable("message.quantimium.reactor.unchecked"));
    /** Parts lit last time, so the ones that drop out can be darkened. */
    private final Set<BlockPos> lit = new HashSet<>();
    private boolean powered;
    /** Whether a Singularity is seated in the cage. The ledger is the Singularity's: it leaves with it. */
    private boolean seated;
    private ReactorRecipes recipes = ReactorRecipes.NONE;
    /** Bumped whenever the ledger changes, so an open screen knows to fetch the stock again. */
    private int ledgerVersion;

    /** At most one recount a second, and only when the stock or the catalysts have changed. */
    private static final int RECOUNT_TICKS = 20;
    private ReactorCounter.Counts counts = ReactorCounter.Counts.EMPTY;
    /** What it could make of what it holds alone, leaving out any network: what Materialiser Ports show. */
    private ReactorCounter.Counts ownCounts = ReactorCounter.Counts.EMPTY;
    @Nullable
    private java.util.concurrent.CompletableFuture<ReactorCounter.Counts[]> recount;
    private int countedVersion = -1;
    private java.util.Map<ItemResource, Long> countedNetwork = java.util.Map.of();
    /** What was held when the counts in hand were made, to tell what's changed since. */
    private java.util.Map<ItemResource, Long> countedLedger = java.util.Map.of();
    /** The network stock the counts in hand were made with, and the one the recount under way uses. */
    private java.util.Map<ItemResource, Long> countsNetwork = java.util.Map.of();
    private java.util.Map<ItemResource, Long> recountNetwork = java.util.Map.of();

    /** While above zero the core is reading or taking from a network: its own ports show nothing. */
    private int drawing;
    /** The network stock as last read, and when, so it's read at most once a tick. */
    private java.util.Map<ItemResource, Long> networkStock = java.util.Map.of();
    private long networkStockTick = Long.MIN_VALUE;
    /** Materialiser Ports a linked network reads through a storage bus: dark, so it never sees them twice. */
    private Set<BlockPos> darkPorts = Set.of();
    @Nullable
    private ReactorRecipes countedRecipes;
    /** Far enough back that the first recount can start at once, without overflowing the subtraction. */
    private long lastRecount = -RECOUNT_TICKS;

    /** Synced for the horizon's look: the client never sees the ledger itself. */
    private long syncedMass;
    private int syncedRings;
    private boolean syncedActive;
    private List<BlockPos> syncedBays = List.of();
    /** Each ring's emitter pair, in ring order: two positions a ring. */
    private List<BlockPos> syncedEmitters = List.of();
    /**
     * When the last craft ran, and which catalysts it used, in order, each as bay * SLOTS + slot: the
     * moons flare for it.
     */
    private long flashTime = Long.MIN_VALUE;
    private List<Integer> flashBays = List.of();

    public HorizonCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HORIZON_CORE_BE.get(), pos, state);
    }

    public ReactorLedger getLedger() {
        return ledger;
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ReactorStructure.Layout getLayout() {
        return layout;
    }

    public boolean isFormed() {
        return layout.formed();
    }

    /** Formed and its rings powered: taking in and making. */
    public boolean isActive() {
        return layout.formed() && powered;
    }

    public int rings() {
        return layout.formed() ? layout.rings() : 0;
    }

    /** How many items the horizon holds with its rings: ×4 a ring. */
    public long capacity() {
        int rings = rings();
        return rings == 0 ? 0 : BASE_CAPACITY << (2 * (rings - 1));
    }

    public long room() {
        return Math.max(0, capacity() - ledger.mass());
    }

    public boolean accepts() {
        return isActive() && room() > 0;
    }

    /** Adds what an Input port took. Called on the transaction's commit, so it always fits. */
    public void take(ItemResource item, int amount) {
        // All of it, even past the room left: two ports in one transaction each checked the room on their
        // own, and what they took has already left the pipe. A little over is better than items lost.
        if (amount <= 0) return;
        ledger.add(item, amount);
        ledgerVersion++;
        markBusy();
        setChanged();
    }

    /**
     * What open transactions have promised away (Materialiser Ports' extractions waiting to commit), so
     * no other port, or a network, takes it meanwhile.
     */
    private final java.util.Map<ItemResource, Long> reserved = new java.util.HashMap<>();

    public void reserve(java.util.Map<ItemResource, Long> items) {
        items.forEach((item, amount) -> reserved.merge(item, amount, Long::sum));
    }

    public void release(java.util.Map<ItemResource, Long> items) {
        items.forEach((item, amount) -> reserved.computeIfPresent(item, (k, have) -> have > amount ? have - amount : null));
    }

    public boolean hasReservations() {
        return !reserved.isEmpty();
    }

    /** What's held and not promised to an open transaction: what a plan may use. */
    public java.util.Map<ItemResource, Long> available() {
        java.util.Map<ItemResource, Long> stock = ledger.snapshot();
        reserved.forEach((item, amount) -> stock.computeIfPresent(item, (k, have) -> have > amount ? have - amount : null));
        return stock;
    }

    /** How many of {@code item} are held and not promised to an open transaction. */
    public long availableCount(ItemResource item) {
        return Math.max(0, ledger.count(item) - reserved.getOrDefault(item, 0L));
    }

    /** Takes up to {@code amount} of {@code item} out of the horizon as it is, for a network taking it. How many. */
    public long withdraw(ItemResource item, long amount) {
        long taken = ledger.remove(item, Math.min(amount, availableCount(item)));
        if (taken > 0) {
            ledgerVersion++;
            markBusy();
            setChanged();
        }
        return taken;
    }

    /** Takes {@code amount} of {@code item} into the horizon, as far as there's room. How many it took. */
    public long store(ItemResource item, long amount) {
        long fits = Math.min(amount, room());
        if (fits <= 0) return 0;
        ledger.add(item, fits);
        ledgerVersion++;
        markBusy();
        setChanged();
        return fits;
    }

    public int ledgerVersion() {
        return ledgerVersion;
    }

    public boolean isUsableBy(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.horizon_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new HorizonCoreMenu(containerId, playerInventory, this, data);
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> energy.getEnergyStored() >>> 16;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                case 3 -> energy.getMaxEnergyStored() >>> 16;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return HorizonCoreMenu.DATA_COUNT;
        }
    };

    /** Clients' view of the horizon. */
    public long syncedMass() {
        return syncedMass;
    }

    public int syncedRings() {
        return syncedRings;
    }

    public boolean syncedActive() {
        return syncedActive;
    }

    public List<BlockPos> syncedBays() {
        return syncedBays;
    }

    public List<BlockPos> syncedEmitters() {
        return syncedEmitters;
    }

    public long flashTime() {
        return flashTime;
    }

    public List<Integer> flashBays() {
        return flashBays;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HorizonCoreBlockEntity core) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % CHECK_TICKS == 0) core.revalidate(server);
        core.recount(server);
        core.payUpkeep();
        core.syncLook();
        core.showBusy(server);
    }

    /** How long the floor's signals keep running after the last thing the Reactor did. */
    private static final int BUSY_TICKS = 60;
    private long busyUntil = Long.MIN_VALUE;
    private boolean shownBusy;

    /** Something went in, came out or was made: the floor's signals run for a while. */
    private void markBusy() {
        if (level != null) busyUntil = level.getGameTime() + BUSY_TICKS;
    }

    /** Sets the floor running or still when that changes: a block update for each part, so only then. */
    private void showBusy(ServerLevel server) {
        boolean busy = isActive() && server.getGameTime() < busyUntil;
        if (busy == shownBusy) return;
        shownBusy = busy;
        for (BlockPos part : layout.formed() ? layout.parts() : List.<BlockPos>of()) {
            BlockState state = server.getBlockState(part);
            if (state.hasProperty(ReactorTraces.BUSY) && state.getValue(ReactorTraces.BUSY) != busy) {
                server.setBlock(part, state.setValue(ReactorTraces.BUSY, busy), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void payUpkeep() {
        int upkeep = rings() * 2 * EMITTER_FE_PER_TICK;
        boolean was = powered;
        powered = upkeep > 0 && energy.getEnergyStored() >= upkeep;
        if (powered) energy.consume(upkeep);
        if (was != powered) setChanged();
    }

    public boolean isSeated() {
        return seated;
    }

    /**
     * Seats {@code singularity} in the empty cage, and with it everything it holds. Takes one from the
     * stack; false when a Singularity is already seated.
     */
    public boolean seat(ItemStack singularity) {
        if (seated || !singularity.is(com.kadikular.quantimium.init.ModItems.SINGULARITY.get())) return false;
        List<ReactorLedger.Entry> held = singularity.get(com.kadikular.quantimium.init.ModDataComponents.HORIZON_LEDGER.get());
        ledger.load(held == null ? List.of() : held);
        ledgerVersion++;
        seated = true;
        singularity.shrink(1);
        afterSeating();
        return true;
    }

    /** Takes the Singularity out, everything the horizon held inside it; empty when none is seated. */
    public ItemStack unseat() {
        ItemStack singularity = release();
        if (!singularity.isEmpty()) afterSeating();
        return singularity;
    }

    /** The Singularity and everything in it, out of the cage, without touching the world. */
    private ItemStack release() {
        if (!seated) return ItemStack.EMPTY;
        ItemStack singularity = new ItemStack(com.kadikular.quantimium.init.ModItems.SINGULARITY.get());
        if (!ledger.isEmpty()) {
            singularity.set(com.kadikular.quantimium.init.ModDataComponents.HORIZON_LEDGER.get(), ledger.entries());
        }
        ledger.load(List.of());
        ledgerVersion++;
        seated = false;
        return singularity;
    }

    private void afterSeating() {
        setChanged();
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            if (state.hasProperty(com.kadikular.quantimium.block.HorizonCoreBlock.SEATED)
                    && state.getValue(com.kadikular.quantimium.block.HorizonCoreBlock.SEATED) != seated) {
                server.setBlock(worldPosition, state.setValue(com.kadikular.quantimium.block.HorizonCoreBlock.SEATED, seated),
                        Block.UPDATE_CLIENTS);
            }
            revalidate(server);
        }
    }

    /** Broken, the core lets its Singularity go, with everything it held. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && seated) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), release());
        }
        // The reactor goes dark and its ports forget it; the core's own block is already going.
        if (level instanceof ServerLevel server) {
            for (BlockPos part : lit) {
                if (!part.equals(pos)) setFormed(server, part, false);
            }
            for (BlockPos port : layout.ports()) {
                if (server.getBlockEntity(port) instanceof ReactorPortBlockEntity entity) entity.link(null);
            }
            lit.clear();
        }
    }

    /** Reads the structure, tells its ports where it is and lights its parts. */
    public void revalidate(ServerLevel server) {
        ReactorStructure.sinkRaisedBays(server, worldPosition);
        ReactorStructure.Layout next = seated ? ReactorStructure.read(server, worldPosition)
                : new ReactorStructure.Layout(0, List.of(), List.of(), List.of(),
                        Component.translatable("message.quantimium.reactor.no_singularity"));
        Set<BlockPos> nowLit = new HashSet<>(next.formed() ? next.parts() : List.of());
        for (BlockPos pos : lit) {
            if (!nowLit.contains(pos)) setFormed(server, pos, false);
        }
        for (BlockPos pos : nowLit) setFormed(server, pos, true);
        setFormed(server, worldPosition, next.formed());
        for (BlockPos pos : layout.ports()) {
            if (!next.ports().contains(pos) && server.getBlockEntity(pos) instanceof ReactorPortBlockEntity port) {
                port.link(null);
            }
        }
        for (BlockPos pos : next.ports()) {
            if (server.getBlockEntity(pos) instanceof ReactorPortBlockEntity port) port.link(worldPosition);
        }
        lit.clear();
        lit.addAll(nowLit);
        layout = next;
        Set<BlockPos> dark = findDarkPorts(server);
        for (BlockPos pos : next.ports()) setDark(server, pos, dark.contains(pos));
        for (BlockPos pos : darkPorts) {
            if (!dark.contains(pos)) setDark(server, pos, false);
        }
        darkPorts = dark;
        refreshRecipes(server);
    }

    /** Rebuilds what the catalysts can make, only when a catalyst has changed. */
    private void refreshRecipes(ServerLevel server) {
        List<ItemStack> catalysts = new ArrayList<>();
        List<RecipeFilter> filters = new ArrayList<>();
        for (BlockPos bay : layout.bays()) {
            // Every slot, empty or not, so catalyst i is always slot i % SLOTS of bay i / SLOTS.
            CatalystBayBlockEntity entity = server.getBlockEntity(bay) instanceof CatalystBayBlockEntity found ? found : null;
            RecipeFilter filter = entity == null ? RecipeFilter.NONE : entity.recipeFilter();
            for (int i = 0; i < CatalystBayBlockEntity.SLOTS; i++) {
                catalysts.add(entity == null ? ItemStack.EMPTY : entity.getCatalyst(i));
                filters.add(filter);
            }
        }
        if (!recipes.builtFrom(catalysts, filters)) recipes = ReactorRecipes.build(server, catalysts, filters);
        // Unrealised Matter held, or in a linked network, can be observed into what it could be, with no catalyst.
        java.util.Set<ItemResource> matter = new java.util.HashSet<>();
        for (ItemResource item : ledger.view().keySet()) {
            if (com.kadikular.quantimium.unrealised.Matter.is(item)) matter.add(item);
        }
        for (ItemResource item : networkStock(server).keySet()) {
            if (com.kadikular.quantimium.unrealised.Matter.is(item)) matter.add(item);
        }
        recipes = recipes.withMatter(server, worldPosition, matter);
    }

    public ReactorRecipes getRecipes() {
        return recipes;
    }

    /** What it could make of what it holds and its networks hold, as last counted: the screen's list. */
    public ReactorCounter.Counts getCounts() {
        return counts;
    }

    /**
     * What it could make of what it holds alone, as last counted: the Materialiser Ports' list. A
     * network's stock is never offered back out through them, so nothing a network sees of the Reactor
     * can feed the Reactor's own counts.
     */
    public ReactorCounter.Counts getOwnCounts() {
        return ownCounts;
    }

    /** Counts on the spot, on this thread: for tests, which can't wait on wall-clock time. */
    public ReactorCounter.Counts recountNow() {
        if (!isFormed()) {
            counts = ownCounts = ReactorCounter.Counts.EMPTY;
        } else {
            java.util.Map<ItemResource, Long> network = level instanceof ServerLevel server ? networkStock(server) : java.util.Map.of();
            ReactorCounter.Counts[] both = countBoth(recipes, ledger.snapshot(), network);
            counts = both[0];
            ownCounts = both[1];
            countedNetwork = network;
            countsNetwork = network;
            countedLedger = ledger.snapshot();
        }
        countedVersion = ledgerVersion;
        countedRecipes = recipes;
        return counts;
    }

    /** With the network and without; the same count twice over when there's no network. */
    private static ReactorCounter.Counts[] countBoth(ReactorRecipes recipes, java.util.Map<ItemResource, Long> held,
                                                     java.util.Map<ItemResource, Long> network) {
        ReactorCounter.Counts own = ReactorCounter.count(recipes.graph(), held);
        if (network.isEmpty()) return new ReactorCounter.Counts[] {own, own};
        return new ReactorCounter.Counts[] {ReactorCounter.count(recipes.graph(), merged(held, network)), own};
    }

    private static java.util.Map<ItemResource, Long> merged(java.util.Map<ItemResource, Long> held,
                                                           java.util.Map<ItemResource, Long> network) {
        java.util.Map<ItemResource, Long> all = new java.util.HashMap<>(held);
        network.forEach((item, amount) -> all.merge(item, amount, Long::sum));
        return all;
    }

    /**
     * Starts a recount off the server thread when the stock, a network's stock or the catalysts have
     * changed, at most once a second, and picks up a finished one. A result finished after the stock
     * moved again is still kept, being newer than the last; the next recount follows.
     */
    private void recount(ServerLevel server) {
        if (recount != null) {
            if (!recount.isDone()) return;
            try {
                ReactorCounter.Counts[] both = recount.join();
                counts = both[0];
                ownCounts = both[1];
                countsNetwork = recountNetwork;
            } catch (RuntimeException e) {
                com.kadikular.quantimium.Quantimium.LOGGER.warn("A Horizon Core at {} failed to count what it can make",
                        worldPosition, e);
            }
            recount = null;
        }
        if (server.getGameTime() - lastRecount < RECOUNT_TICKS) return;
        java.util.Map<ItemResource, Long> network = isFormed() ? networkStock(server) : java.util.Map.of();
        boolean stale = countedVersion != ledgerVersion || countedRecipes != recipes || !network.equals(countedNetwork);
        if (!stale) return;
        if (countedRecipes == recipes && network.equals(countedNetwork) && isFormed() && patchInert()) return;
        lastRecount = server.getGameTime();
        countedVersion = ledgerVersion;
        countedRecipes = recipes;
        countedNetwork = network;
        if (!isFormed()) {
            counts = ownCounts = ReactorCounter.Counts.EMPTY;
            countsNetwork = java.util.Map.of();
            return;
        }
        recountNetwork = network;
        ReactorRecipes graphOf = recipes;
        java.util.Map<ItemResource, Long> stock = ledger.snapshot();
        countedLedger = stock;
        recount = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> countBoth(graphOf, stock, network), ReactorCounter.EXECUTOR);
    }

    /**
     * The quick way: if everything held that has changed since the last count is inert (no recipe uses
     * or makes it), its count is simply what's held now, and nothing else moves. Whether it could.
     */
    private boolean patchInert() {
        ReactorGraph graph = recipes.graph();
        java.util.Map<ItemResource, Long> now = ledger.view();
        java.util.Set<ItemResource> changed = new java.util.HashSet<>();
        now.forEach((item, amount) -> {
            if (!amount.equals(countedLedger.get(item))) changed.add(item);
        });
        countedLedger.keySet().forEach(item -> {
            if (!now.containsKey(item)) changed.add(item);
        });
        for (ItemResource item : changed) {
            if (!graph.inert(item)) return false;
        }
        counts = patched(counts, changed, now);
        ownCounts = patched(ownCounts, changed, now);
        countedLedger = ledger.snapshot();
        countedVersion = ledgerVersion;
        return true;
    }

    private static ReactorCounter.Counts patched(ReactorCounter.Counts counts, java.util.Set<ItemResource> changed,
                                                 java.util.Map<ItemResource, Long> held) {
        java.util.Map<ItemResource, Long> map = new java.util.LinkedHashMap<>(counts.counts());
        for (ItemResource item : changed) {
            long amount = held.getOrDefault(item, 0L);
            if (amount > 0) map.put(item, amount);
            else map.remove(item);
        }
        return new ReactorCounter.Counts(map, counts.reachable(), counts.groups(), counts.gaining(), 0);
    }

    // ---- networks ----

    /** Whether the core is reading or taking from a network just now; its own ports show nothing meanwhile. */
    public boolean isDrawing() {
        return drawing > 0;
    }

    /** Whether a linked network reads this Materialiser Port through a storage bus, so it shows nothing. */
    public boolean isDarkPort(BlockPos port) {
        return darkPorts.contains(port);
    }

    /** The networks linked through this reactor's ports, each once. */
    public List<ReactorNetwork> networks(ServerLevel server) {
        List<ReactorNetwork> found = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        if (!layout.formed()) return found;
        for (BlockPos pos : layout.ports()) {
            if (server.getBlockEntity(pos) instanceof ReactorNetwork network && network.network() != null
                    && seen.add(network.network())) {
                found.add(network);
            }
        }
        return found;
    }

    /** Everything the linked networks hold, never counting what they see of this reactor; read once a tick. */
    public java.util.Map<ItemResource, Long> networkStock(ServerLevel server) {
        if (networkStockTick == server.getGameTime()) return networkStock;
        java.util.Map<ItemResource, Long> stock = new java.util.HashMap<>();
        drawing++;
        try {
            ReactorGraph graph = recipes.graph();
            for (ReactorNetwork network : networks(server)) {
                // Only what some recipe could use: the rest changes no count, and a busy network's churn
                // in it would only set off recounts.
                network.stock().forEach((item, amount) -> {
                    if (!graph.inert(item)) stock.merge(item, amount, Long::sum);
                });
            }
        } finally {
            drawing--;
        }
        networkStock = stock.isEmpty() ? java.util.Map.of() : stock;
        networkStockTick = server.getGameTime();
        return networkStock;
    }

    /** The linked networks' stock that the counts in hand were made with. */
    public java.util.Map<ItemResource, Long> countedNetworkStock() {
        return countsNetwork;
    }

    /** What the horizon holds and its networks hold, together: what a request may plan with. */
    public java.util.Map<ItemResource, Long> stockWithNetworks(ServerLevel server) {
        return merged(available(), networkStock(server));
    }

    /**
     * Pulls into the horizon whatever {@code plan} uses beyond what's held, from the linked networks.
     * Anything pulled stays held, so a pull that comes up short loses nothing. Whether all of it came.
     */
    private boolean pullFromNetworks(ServerLevel server, ReactorPlanner.Plan plan) {
        java.util.Map<ItemResource, Long> missing = new java.util.HashMap<>();
        plan.consumed().forEach((item, amount) -> {
            long short_ = amount - ledger.count(item);
            if (short_ > 0) missing.put(item, short_);
        });
        if (missing.isEmpty()) return true;
        List<ReactorNetwork> networks = networks(server);
        drawing++;
        try {
            // All of it, simulated, before any of it for real.
            for (var entry : missing.entrySet()) {
                long found = 0;
                for (ReactorNetwork network : networks) {
                    found += network.extract(entry.getKey(), entry.getValue() - found, true);
                    if (found >= entry.getValue()) break;
                }
                if (found < entry.getValue()) return false;
            }
            boolean all = true;
            for (var entry : missing.entrySet()) {
                long got = 0;
                for (ReactorNetwork network : networks) {
                    got += network.extract(entry.getKey(), entry.getValue() - got, false);
                    if (got >= entry.getValue()) break;
                }
                if (got > 0) ledger.add(entry.getKey(), got);
                if (got < entry.getValue()) all = false;
            }
            ledgerVersion++;
            networkStockTick = Long.MIN_VALUE;
            setChanged();
            return all;
        } finally {
            drawing--;
        }
    }

    /**
     * Pays for {@code runs} runs of {@code shape} that a network has handed the inputs for, at the
     * Reactor's price. Whether it could pay.
     */
    public boolean payForRuns(com.kadikular.quantimium.recipe.RecipeShape shape, long runs) {
        return pay((long) Math.ceil(shape.baseFe() * runs * Config.crafterTaxFraction(FluxBand.SINGULARITY)));
    }

    /** Pays {@code fe} for work a network asked of it, if it's running and has it. Whether it could. */
    public boolean pay(long fe) {
        if (!(level instanceof ServerLevel server) || !isActive() || energy.getEnergyStored() < fe) return false;
        energy.consume(fe);
        markBusy();
        if (fe > 0) QuantumFlux.emitFromEnergy(server, worldPosition, fe);
        setChanged();
        return true;
    }

    /** Materialiser Ports a linked network reads through something of its own beside them. */
    private Set<BlockPos> findDarkPorts(ServerLevel server) {
        Set<BlockPos> dark = new HashSet<>();
        List<ReactorNetwork> networks = networks(server);
        if (networks.isEmpty()) return dark;
        for (BlockPos pos : layout.ports()) {
            if (!(server.getBlockEntity(pos) instanceof ReactorPortBlockEntity port)
                    || port.kind() != com.kadikular.quantimium.block.ReactorPortBlock.Kind.MATERIALISER) {
                continue;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                BlockPos beside = pos.relative(side);
                if (!server.isLoaded(beside)) continue;
                for (ReactorNetwork network : networks) {
                    if (network.readsFrom(server, beside, side.getOpposite())) dark.add(pos);
                }
            }
        }
        return dark;
    }

    /**
     * Makes {@code count} of {@code target} from what the horizon holds, through its catalysts, and
     * sends it to the Output ports; whatever they have no room for stays in the horizon. All at once
     * or not at all: the whole tree is planned, the energy checked, and only then is anything used.
     */
    public ReactorPlanner.Result request(ItemResource target, long count) {
        if (!(level instanceof ServerLevel server)) return refused("message.quantimium.reactor.offline");
        if (!isActive()) return refused("message.quantimium.reactor.offline");
        refreshRecipes(server);
        ReactorPlanner.Result result = ReactorPlanner.plan(recipes, stockWithNetworks(server), target, count);
        if (!result.planned()) return result;
        ReactorPlanner.Plan plan = result.plan();

        long fe = feFor(plan);
        if (energy.getEnergyStored() < fe) {
            return new ReactorPlanner.Result(null, Component.translatable("message.quantimium.reactor.no_power",
                    String.format(Locale.ROOT, "%,d", fe)));
        }
        // Whatever the ports can't take goes back in; the horizon must have room for it.
        List<ReactorPortBlockEntity> outputs = outputPorts(server);
        long deliverable = 0;
        ItemStack sample = target.toStack(1);
        for (ReactorPortBlockEntity port : outputs) deliverable += port.roomFor(sample);
        long kept = Math.max(0, count - deliverable);
        long consumed = 0;
        long pulled = 0;
        for (var entry : plan.consumed().entrySet()) {
            consumed += entry.getValue();
            pulled += Math.max(0, entry.getValue() - ledger.count(entry.getKey()));
        }
        long left = 0;
        for (long amount : plan.leftovers().values()) left += amount;
        if (ledger.mass() + pulled - consumed + left + kept > capacity()) return refused("message.quantimium.reactor.full");
        // Whatever it uses from a network comes into the horizon first; if the network has changed since
        // the plan, what did come stays held and nothing is made.
        if (!pullFromNetworks(server, plan)) return refused("message.quantimium.reactor.network_moved");

        if (!spend(server, plan, fe)) return refused("message.quantimium.reactor.network_moved");
        long toSend = count;
        for (ReactorPortBlockEntity port : outputs) {
            while (toSend > 0) {
                int batch = (int) Math.min(toSend, sample.getMaxStackSize());
                ItemStack rest = port.deliver(target.toStack(batch));
                toSend -= batch - rest.getCount();
                if (!rest.isEmpty()) break;
            }
        }
        if (toSend > 0) ledger.add(target, toSend);
        return result;
    }

    /** What the Reactor charges for {@code plan}: its energy at the Singularity tax. */
    public static long feFor(ReactorPlanner.Plan plan) {
        return (long) Math.ceil(plan.energy() * Config.crafterTaxFraction(FluxBand.SINGULARITY));
    }

    /** Plans {@code count} of {@code item} from {@code stock}, which may differ from the ledger. Changes nothing. */
    public ReactorPlanner.Result plan(java.util.Map<ItemResource, Long> stock, ItemResource item, long count) {
        if (!isActive()) return refused("message.quantimium.reactor.offline");
        return ReactorPlanner.plan(recipes, stock, item, count);
    }

    /**
     * Carries out {@code plan} whose result has gone elsewhere: takes its inputs, keeps its leftovers,
     * pays {@code fe}, and lights the moons that worked. The caller has checked it still fits. Refuses,
     * changing nothing, if the horizon doesn't hold everything the plan uses.
     */
    public boolean spend(ServerLevel server, ReactorPlanner.Plan plan, long fe) {
        // Never more than is held: a plan that's gone stale would otherwise make things from nothing.
        for (var entry : plan.consumed().entrySet()) {
            if (ledger.count(entry.getKey()) < entry.getValue()) {
                com.kadikular.quantimium.Quantimium.LOGGER.error("A Horizon Core at {} was asked to use {} x{} but holds {}; "
                        + "nothing was made", worldPosition, entry.getKey(), entry.getValue(), ledger.count(entry.getKey()));
                return false;
            }
        }
        plan.consumed().forEach(ledger::remove);
        plan.leftovers().forEach(ledger::add);
        ledgerVersion++;
        markBusy();
        energy.consume(fe);
        if (fe > 0) QuantumFlux.emitFromEnergy(server, worldPosition, fe);
        if (!plan.steps().isEmpty()) {
            flashTime = server.getGameTime();
            List<Integer> used = new ArrayList<>();
            for (ReactorPlanner.Step step : plan.steps()) {
                if (step.bay() != ReactorRecipes.NO_BAY) used.add(step.bay());
            }
            flashBays = List.copyOf(used);
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        setChanged();
        return true;
    }

    private static ReactorPlanner.Result refused(String key) {
        return new ReactorPlanner.Result(null, Component.translatable(key));
    }

    private List<ReactorPortBlockEntity> outputPorts(ServerLevel server) {
        List<ReactorPortBlockEntity> ports = new ArrayList<>();
        for (BlockPos pos : layout.ports()) {
            if (server.getBlockEntity(pos) instanceof ReactorPortBlockEntity port
                    && port.kind() == com.kadikular.quantimium.block.ReactorPortBlock.Kind.OUTPUT) {
                ports.add(port);
            }
        }
        return ports;
    }

    private static void setDark(ServerLevel server, BlockPos pos, boolean dark) {
        BlockState state = server.getBlockState(pos);
        if (state.hasProperty(com.kadikular.quantimium.block.ReactorPortBlock.DARK)
                && state.getValue(com.kadikular.quantimium.block.ReactorPortBlock.DARK) != dark) {
            server.setBlock(pos, state.setValue(com.kadikular.quantimium.block.ReactorPortBlock.DARK, dark), Block.UPDATE_CLIENTS);
        }
    }

    private static void setFormed(ServerLevel server, BlockPos pos, boolean formed) {
        BlockState state = server.getBlockState(pos);
        if (state.hasProperty(QuantumFoundryStructure.FORMED) && state.getValue(QuantumFoundryStructure.FORMED) != formed) {
            server.setBlock(pos, state.setValue(QuantumFoundryStructure.FORMED, formed), Block.UPDATE_CLIENTS);
        }
    }

    /** Sends the look to clients when it has changed enough to see. */
    private void syncLook() {
        long mass = ledger.mass();
        boolean active = isActive();
        boolean massMoved = mass != syncedMass && (syncedMass == 0 || Math.abs(mass - syncedMass) * 100 > syncedMass
                || level.getGameTime() % 100 == 0);
        List<BlockPos> bays = layout.formed() ? layout.bays() : List.of();
        List<BlockPos> emitters = layout.formed() ? layout.emitters() : List.of();
        if (!massMoved && rings() == syncedRings && active == syncedActive && bays.equals(syncedBays)
                && emitters.equals(syncedEmitters)) {
            return;
        }
        syncedEmitters = List.copyOf(emitters);
        syncedMass = mass;
        syncedRings = rings();
        syncedActive = active;
        syncedBays = List.copyOf(bays);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Ledger", ReactorLedger.CODEC, ledger.entries());
        out.putInt("Energy", energy.getEnergyStored());
        out.putBoolean("Powered", powered);
        out.putBoolean("Seated", seated);
        out.store("Lit", BlockPos.CODEC.listOf(), List.copyOf(lit));
        saveLook(out);
    }

    private void saveLook(ValueOutput out) {
        out.putLong("Mass", syncedMass);
        out.putInt("Rings", syncedRings);
        out.putBoolean("Active", syncedActive);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.read("Ledger", ReactorLedger.CODEC).ifPresent(ledger::load);
        energy.setEnergy(in.getIntOr("Energy", 0));
        powered = in.getBooleanOr("Powered", false);
        // A core from before Singularities were seated held its ledger itself: count it as seated.
        seated = in.getBooleanOr("Seated", !ledger.isEmpty() || in.getBooleanOr("Powered", false));
        lit.clear();
        in.read("Lit", BlockPos.CODEC.listOf()).ifPresent(lit::addAll);
        syncedMass = in.getLongOr("Mass", 0L);
        syncedRings = in.getIntOr("Rings", 0);
        syncedActive = in.getBooleanOr("Active", false);
    }

    /** Only the look: the ledger stays on the server. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Mass", syncedMass);
        tag.putInt("Rings", syncedRings);
        tag.putBoolean("Active", syncedActive);
        tag.putLongArray("Bays", syncedBays.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Emitters", syncedEmitters.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLong("Flash", flashTime);
        tag.putIntArray("Used", flashBays.stream().mapToInt(Integer::intValue).toArray());
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        CompoundTag tag = com.kadikular.quantimium.util.NbtCompat.read(input);
        syncedMass = tag.getLongOr("Mass", 0L);
        syncedRings = tag.getIntOr("Rings", 0);
        syncedActive = tag.getBooleanOr("Active", false);
        syncedBays = tag.getLongArray("Bays").map(longs -> java.util.Arrays.stream(longs).mapToObj(BlockPos::of).toList())
                .orElse(List.of());
        syncedEmitters = tag.getLongArray("Emitters").map(longs -> java.util.Arrays.stream(longs).mapToObj(BlockPos::of).toList())
                .orElse(List.of());
        flashTime = tag.getLongOr("Flash", Long.MIN_VALUE);
        flashBays = tag.getIntArray("Used").map(ints -> java.util.Arrays.stream(ints).boxed().toList()).orElse(List.of());
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
