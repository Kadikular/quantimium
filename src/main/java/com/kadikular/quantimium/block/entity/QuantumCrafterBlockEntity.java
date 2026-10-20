package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.OnDemandSlots;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.QuantumCrafterBlock;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.menu.QuantumCrafterMenu;
import com.kadikular.quantimium.recipe.CatalystResolver;
import com.kadikular.quantimium.recipe.CrafterPreview;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.kadikular.quantimium.recipe.IngredientPool;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;

/**
 * Instant Quantum Crafter: catalyst in the centre selects recipe types; inputs stay until an output
 * is taken (or auto-pushed), then one craft is committed and billed.
 */
public class QuantumCrafterBlockEntity extends BlockEntity
        implements MenuProvider, SideConfigurable, QuantumEnergyHost, FluxMeterReadout, com.kadikular.quantimium.laser.OfferMaker {
    private static final SideAutomationProfile SIDE_AUTOMATION_PROFILE =
            new SideAutomationProfile(SideAutomationProfile.ALL_SIDES, 0);

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_START = 9;
    /** Its output slots, which hold what it has really made. */
    public static final int OUTPUT_SLOTS = 54;
    public static final int OUTPUT_END = OUTPUT_START + OUTPUT_SLOTS - 1;
    public static final int CATALYST_SLOT = OUTPUT_END + 1;
    /**
     * Where its previews are numbered from: each output of each craft it could make is a preview of its own, as many
     * as there are, apart from the 54 output slots, which hold only what's really been made. Pipes and networks see
     * them as slots after the outputs (see {@link SidedAutomationHandler}).
     */
    public static final int GHOST_BASE = 1000;
    public static final int TOTAL_SLOTS = CATALYST_SLOT + 1;
    private static final int LEGACY_OUTPUT_END = 17;
    private static final int LEGACY_CATALYST_SLOT = 18;
    private static final int LEGACY_TOTAL_SLOTS = 19;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_NO_POWER = 2;
    public static final int STATUS_NO_RECIPE = 3;
    public static final int STATUS_DENIED = 4;
    public static final int STATUS_OUTPUT_FULL = 5;
    public static final int STATUS_ENTANGLED_LOOP = 6;
    public static final int STATUS_REPLENISHING = 7;
    /** The catalyst's family needs a hotter field than this one (running hot pays). */
    public static final int STATUS_NEEDS_FLUX = 8;

    private static final int REMOTE_POLL_TICKS = 10;
    /**
     * A remote inventory that keeps growing (a hopper feeding a linked chest) could unlock a recipe
     * needing more of the same item, so a rescan is still owed — just not on every change.
     */
    private static final int DISCOVERY_TICKS = 20;
    private static final int MAX_CHAIN_DEPTH = 16;

    private final CrafterPreview previewEngine = new CrafterPreview();
    private List<ResolvedCraft> preview = List.of();
    private List<SideConfig> sideConfigs = SideConfig.defaults();

    @Nullable
    private Identifier preferredRecipeId;
    private boolean recipeLocked;

    private int statusCode = STATUS_IDLE;
    /** The flux band here: which catalyst families work, and how much the instant-craft tax is. */
    private final BandGate gate = new BandGate();
    /** Which items are reachable; a change here means new recipes may match. */
    private long structureSignature = Long.MIN_VALUE;
    /** How many of them; a change here only resizes the crafts we already know about. Local and
     * remote are tracked apart so remote churn cannot force a rescan on every tick. */
    private long localCountSignature = Long.MIN_VALUE;
    private long remoteCountSignature = Long.MIN_VALUE;
    private long contentRevision;
    private long nextRemotePollTick;
    private long nextDiscoveryTick;
    /** Buffer level the current batch sizes were priced against. */
    private int scaledAtEnergy = -1;
    /** Anomaly band baked into the current preview costs. */
    private FluxBand pricedAtAnomaly = FluxBand.LOW;
    private int pricedAtSurchargePercent;
    private int syncedAnomalyOrdinal;
    private int syncedSurchargePercent;
    private int syncedPassiveDrain;
    /** A remote source grew, so a rescan is owed once the discovery throttle allows one. */
    private boolean discoveryPending;
    private boolean localDirty = true;
    private boolean clientSyncDirty;
    private int currentPowerUse;
    private int averagePowerUse;
    private int powerSampleIndex;
    private final int[] powerSamples = new int[20];
    private long feSpentThisTick;
    private boolean linkLoopDetected;
    /** Suppress per-slot dirty fan-out while a craft mutates several inventory cells at once. */
    private boolean suppressInventoryNotify;
    /** Guards against two crafters linked to each other committing through one another forever. */
    private boolean automationBusy;
    /** The previewed recipes that chain its own crafts (worked out with them on offer), to be sized the same way. */
    private java.util.Set<Identifier> chained = java.util.Set.of();
    /**
     * The recipes it's making right now. While one runs it may run another, which the first needs made from what
     * it would draw on (a furnace smelting stone for bricks it will smelt again), but never one already running.
     */
    private final java.util.Set<Identifier> running = new java.util.HashSet<>();
    /** Recipe runs left before the early crafter must rebuild its coherence reserve. */
    private int coherenceReserve = 32;
    private int coherenceRefillProgress;

    @Nullable
    private GhostSlot[] ghostLayout;
    /** Per-tick cache of what automation already asked about, keyed by content revision. */
    private long automationViewRevision = Long.MIN_VALUE;
    /** The tick the automation view and commit checks were worked out in: they last that tick at most. */
    private long automationViewTick = Long.MIN_VALUE;
    private long commitCheckTick = Long.MIN_VALUE;
    private long automationViewEpoch = Long.MIN_VALUE;
    private long commitCheckEpoch = Long.MIN_VALUE;
    private final java.util.Map<Long, ItemStack[]> automationViewCache = new java.util.HashMap<>();
    /** Per-preview-entry commit answers, keyed by content revision. 0 unknown, 1 yes, 2 no. */
    private long commitCheckRevision = Long.MIN_VALUE;
    private final java.util.Map<Long, byte[]> commitCheckCache = new java.util.HashMap<>();

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level == null || level.isClientSide()) return;
            // Bumped even mid-craft: it keys the per-tick automation cache, which must never answer
            // for contents that have since moved.
            contentRevision++;
            automationViewRevision = Long.MIN_VALUE;
            if (suppressInventoryNotify) return;
            // Only the server derives a layout; dropping the client's copy here would throw away the
            // synced one every time the menu writes a slot. Client block updates are throttled via
            // clientSyncDirty — hopping every slot change was drowning continuous craft.
            ghostLayout = null;
            localDirty = true;
            clientSyncDirty = true;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= OUTPUT_START && slot <= OUTPUT_END) return false;
            if (slot == CATALYST_SLOT) return true;
            return slot >= 0 && slot < INPUT_SLOTS;
        }
    };

    private final IItemHandler[] sidedItemHandlers = new IItemHandler[6];
    private final IItemHandler unsidedItemHandler = new SidedAutomationHandler(null);
    /** Asked with no face (viewers, other mods' screens): only what's really there, no crafting on a pull. */
    private final IItemHandler viewerItemHandler = new com.kadikular.quantimium.util.OnDemandSlots.RealOnly(unsidedItemHandler);

    public static final int ENERGY_CAPACITY = 100_000_000;
    public static final int ENERGY_MAX_RECEIVE = 10_000_000;
    /** Early buffer: ~8 minutes to fill from a 100 FE/t generator. */
    public static final int BASIC_ENERGY_CAPACITY = 1_000_000;
    public static final int BASIC_ENERGY_MAX_RECEIVE = 1_000_000;

    private final QuantumEnergyStorage energyStorage;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> (energyStorage.getEnergyStored() >> 16) & 0xFFFF;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energyStorage.getMaxEnergyStored() >> 16) & 0xFFFF;
                case 4 -> statusCode;
                case 5 -> recipeLocked ? 1 : 0;
                case 6 -> preview.size();
                case 7 -> currentPowerUse & 0xFFFF;
                case 8 -> (currentPowerUse >>> 16) & 0xFFFF;
                case 9 -> averagePowerUse & 0xFFFF;
                case 10 -> (averagePowerUse >>> 16) & 0xFFFF;
                case 11 -> syncedSurchargePercent;
                case 12 -> syncedAnomalyOrdinal;
                case 13 -> syncedPassiveDrain;
                case 14 -> getCoherenceReserve();
                case 15 -> getCoherenceCapacity();
                case 16 -> gate.band().ordinal();
                case 17 -> gate.heading().ordinal();
                case 18 -> requiredBand().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 4) statusCode = value;
            if (index == 5) recipeLocked = value != 0;
        }

        @Override
        public int getCount() {
            return 19;
        }
    };

    public QuantumCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_CRAFTER_BE.get(), pos, state);
        boolean basic = state.is(ModBlocks.BASIC_QUANTUM_CRAFTER.get());
        int capacity = basic ? BASIC_ENERGY_CAPACITY : ENERGY_CAPACITY;
        int maxReceive = basic ? BASIC_ENERGY_MAX_RECEIVE : ENERGY_MAX_RECEIVE;
        this.energyStorage = new QuantumEnergyStorage(capacity, maxReceive, 0) {
            @Override
            protected void onReceived() {
                    setChanged();
                    automationViewRevision = Long.MIN_VALUE;
                
            }
        };
        if (basic) {
            coherenceReserve = Config.basicCrafterCapacity();
        }
        for (Direction side : Direction.values()) {
            sidedItemHandlers[side.get3DDataValue()] = new SidedAutomationHandler(side);
        }
    }

    public boolean isBasic() {
        return getBlockState().is(ModBlocks.BASIC_QUANTUM_CRAFTER.get());
    }

    public int getCoherenceReserve() {
        return Math.min(coherenceReserve, Config.basicCrafterCapacity());
    }

    public int getCoherenceCapacity() {
        return Config.basicCrafterCapacity();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getCurrentPowerUse() {
        return currentPowerUse;
    }

    public int getAveragePowerUse() {
        return averagePowerUse;
    }

    private void recordPowerUse(long amount) {
        currentPowerUse = (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
        powerSamples[powerSampleIndex++ % powerSamples.length] = currentPowerUse;
        long total = 0;
        for (int sample : powerSamples) total += sample;
        averagePowerUse = (int) Math.min(Integer.MAX_VALUE, total / powerSamples.length);
    }

    public ContainerData getData() {
        return data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<ResolvedCraft> getPreview() {
        return preview;
    }

    public long contentRevision() {
        return contentRevision;
    }

    /**
     * Which craft, and which of its outputs, a given output slot is previewing. The price rides along
     * so the client can label a ghost without having to reach back into its copy of the preview list.
     */
    public record GhostSlot(int craftIndex, int outputIndex, int feCost, int batchSize,
                            boolean energyCapped, long available) {}

    private static final GhostSlot[] NO_GHOSTS = new GhostSlot[0];

    /**
     * Every guaranteed output of every craft in the preview, in order, so a centrifuge preview shows both the
     * aluminium and the chromium rather than hiding the byproduct until it lands.
     */
    private GhostSlot[] ghostLayout() {
        GhostSlot[] cached = ghostLayout;
        if (cached != null) return cached;
        // A layout the client works out for itself can name a different craft than the server will
        // run, which prices the tooltip off a craft the click never touches. Wait for the sync.
        if (level != null && level.isClientSide()) return NO_GHOSTS;

        List<GhostSlot> layout = new ArrayList<>();
        for (int craftIndex = 0; craftIndex < preview.size(); craftIndex++) {
            ResolvedCraft craft = preview.get(craftIndex);
            List<ItemStack> outputs = craft.outputs();
            for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                if (outputs.get(outputIndex).isEmpty()) continue;
                layout.add(new GhostSlot(craftIndex, outputIndex, craft.feCost(),
                        craft.batchSize(), craft.energyCapped(), craft.availableOutputCount(outputIndex)));
            }
        }
        ghostLayout = layout.toArray(GhostSlot[]::new);
        return ghostLayout;
    }

    /** How many previews it has: they're numbered from {@link #GHOST_BASE}. */
    public int ghostCount() {
        return ghostLayout().length;
    }

    /** The preview numbered {@code ghostSlot} ({@link #GHOST_BASE} on), or null. Server-assigned, so both sides agree. */
    @Nullable
    public GhostSlot ghostAt(int ghostSlot) {
        int local = ghostSlot - GHOST_BASE;
        GhostSlot[] layout = ghostLayout();
        if (local < 0 || local >= layout.length) return null;
        GhostSlot ghost = layout[local];
        return ghost != null && ghost.craftIndex() < preview.size() ? ghost : null;
    }

    /** What the preview numbered {@code ghostSlot} would make. */
    public ItemStack getGhost(int ghostSlot) {
        GhostSlot ghost = ghostAt(ghostSlot);
        if (ghost == null) return ItemStack.EMPTY;
        List<ItemStack> outputs = preview.get(ghost.craftIndex()).outputs();
        return ghost.outputIndex() < outputs.size() ? outputs.get(ghost.outputIndex()).copy() : ItemStack.EMPTY;
    }

    @Nullable
    public ResolvedCraft getCraftForOutputSlot(int absoluteOutputSlot) {
        GhostSlot ghost = ghostAt(absoluteOutputSlot);
        return ghost == null ? null : preview.get(ghost.craftIndex());
    }

    public boolean isRecipeLocked() {
        return recipeLocked;
    }

    public void setRecipeLocked(boolean locked) {
        this.recipeLocked = locked;
        localDirty = true;
        setChanged();
    }

    /** Every slot as a pipe on any face sees it, outputs crafting on a pull: what our own automation (and tests) use. */
    public IItemHandler pullHandler() {
        return unsidedItemHandler;
    }

    @Nullable
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return viewerItemHandler;
        if (isBasic()) return unsidedItemHandler;
        if (!SIDE_AUTOMATION_PROFILE.supportsItems(side)) return null;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.itemMode() == SideMode.DISABLED ? null : sidedItemHandlers[side.get3DDataValue()];
    }

    @Override
    public List<SideConfig> getSideConfigs() {
        return List.copyOf(sideConfigs);
    }

    /** Read-only view for the renderer, which reads the modes every frame and must not allocate. */
    public List<SideConfig> sideConfigsView() {
        return sideConfigs;
    }

    @Override
    public SideAutomationProfile sideAutomationProfile() {
        return SIDE_AUTOMATION_PROFILE;
    }

    @Override
    public void applySideConfigs(List<SideConfig> configs) {
        this.sideConfigs = SIDE_AUTOMATION_PROFILE.sanitize(configs);
        localDirty = true;
        contentRevision++;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean isUsableBy(Player player) {
        if (level == null || player.level() != level) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, QuantumCrafterBlockEntity be) {
        if (level.isClientSide()) return;
        be.serverTick(level, state);
    }

    /** Crafts' flux (their FE; 0 for a craft with none) waiting on the transaction they were made in. */
    private final List<Long> pendingFlux = new ArrayList<>();

    private void emitCraftFlux(ServerLevel serverLevel, long fe) {
        if (fe > 0) QuantumFlux.emitFromEnergy(serverLevel, worldPosition, fe);
        else QuantumFlux.emitNonelectric(serverLevel, worldPosition);
    }

    /** The transaction committed: the crafts it made emit their flux now. */
    private void flushFlux() {
        if (pendingFlux.isEmpty() || !(level instanceof ServerLevel serverLevel)) return;
        for (long fe : pendingFlux) emitCraftFlux(serverLevel, fe);
        pendingFlux.clear();
    }

    /** What a pull's craft changes besides the inventory, for an aborted transaction to put back. */
    private record CraftState(int energy, int coherence, int refill, long feSpent, int flux, List<ResolvedCraft> preview,
                              int status, @Nullable Identifier preferred, long structure, long localCount, long remoteCount, int scaledAt) {}

    private void serverTick(Level level, BlockState state) {
        long gameTime = level.getGameTime();
        // Anything left from a transaction no journal saw commit: it's out of every transaction now.
        if (net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle() == net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.NONE) flushFlux();
        replenishBasicCrafter();
        if (refreshAnomalyReadout()) localDirty = true;
        if (gameTime % 20L == 0L && refreshBand(level)) localDirty = true;
        // Someone has its screen open: it looks at its links, and tells their screen, every tick.
        watchedNow = watchedByPlayer();
        // Its screen open, the networks it draws on are watched too: they keep what they offer up to date for it.
        if (watchedNow) {
            for (var network : linkedNetworks()) network.watched();
            com.kadikular.quantimium.work.WorkQueue.hurry(this, com.kadikular.quantimium.work.WorkQueue.URGENT);
        }
        if (localDirty || watchedNow || gameTime >= nextRemotePollTick) {
            nextRemotePollTick = gameTime + REMOTE_POLL_TICKS;
            refreshPreviewIfNeeded();
        } else {
            updateStatusFromPreview();
        }

        if (clientSyncDirty && (watchedNow || gameTime % REMOTE_POLL_TICKS == 0L)) {
            flushClientSync();
        }

        if (!isBasic() && gameTime % 20L == 0L) {
            runAutoTransfers(level);
        }

        int leak = AnomalyEffects.passiveDrainFePerTick(level, worldPosition);
        if (leak > 0) {
            leak = energyStorage.consume(leak);
            if (leak > 0) setChanged();
        }
        syncedPassiveDrain = leak;

        // Same 20-sample window as the simulator so continuous crafts show a real FE/t average.
        recordPowerUse(feSpentThisTick);
        feSpentThisTick = 0;

        boolean active = statusCode == STATUS_READY || statusCode == STATUS_NO_POWER;
        if (state.getValue(QuantumCrafterBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(QuantumCrafterBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private void replenishBasicCrafter() {
        if (!isBasic()) return;
        int capacity = Config.basicCrafterCapacity();
        if (coherenceReserve > capacity) coherenceReserve = capacity;
        if (coherenceReserve >= capacity) {
            coherenceRefillProgress = 0;
            return;
        }
        int interval = Config.basicCrafterRefillTicks();
        coherenceRefillProgress += capacity;
        int restored = coherenceRefillProgress / interval;
        if (restored <= 0) return;
        coherenceRefillProgress %= interval;
        coherenceReserve = Math.min(capacity, coherenceReserve + restored);
        if (coherenceReserve >= capacity) coherenceRefillProgress = 0;
        rescaleCachedPreview();
        clientSyncDirty = true;
        setChanged();
    }

    private void refreshPreviewIfNeeded() {
        if (level == null || level.isClientSide()) return;
        boolean loopChanged = updateLinkLoopState();

        EntangledLinks.RemoteSummary remote = remoteSummary();
        long structure = structureSignature(remote);
        long localCounts = localCount();
        long remoteCounts = remote.countTotal();
        long gameTime = level.getGameTime();
        localDirty = false;

        if (loopChanged || structure != structureSignature) {
            structureSignature = structure;
            localCountSignature = localCounts;
            remoteCountSignature = remoteCounts;
            discoveryPending = false;
            nextDiscoveryTick = gameTime + DISCOVERY_TICKS;
            queueRefresh(true);
            return;
        }

        boolean localGrew = localCounts > localCountSignature;
        boolean remoteGrew = remoteCounts > remoteCountSignature;
        boolean changed = localCounts != localCountSignature || remoteCounts != remoteCountSignature;
        localCountSignature = localCounts;
        remoteCountSignature = remoteCounts;
        if (!changed && !discoveryPending) {
            // Batches trimmed to fit the buffer have to grow back once it refills.
            if (energyWouldResizeBatches()) queueRefresh(false);
            else updateStatusFromPreview();
            return;
        }

        // Growth can unlock a recipe wanting more of an item we already had (nine ingots into a
        // block). Our own grid rescans at once so the GUI keeps up; a link into something busy like a
        // hopper would otherwise rescan every tick, so remote growth waits for the throttle.
        if (remoteGrew) discoveryPending = true;
        if (localGrew || (discoveryPending && (watchedNow || gameTime >= nextDiscoveryTick))) {
            discoveryPending = false;
            nextDiscoveryTick = gameTime + DISCOVERY_TICKS;
            queueRefresh(true);
            return;
        }

        if (preview.isEmpty()) {
            updateStatusFromPreview();
            return;
        }
        queueRefresh(false);
    }

    /** Whether a player had its screen open this tick. */
    private boolean watchedNow;

    /** Whether a full rebuild is waiting in the queue: it covers any resize asked for meanwhile. */
    private boolean rebuildQueued;

    /** Whether a resize is waiting, for after any rebuild under way. */
    private boolean rescaleQueued;
    /** The preview being worked out in the queue, between steps; null when none is. */
    @Nullable
    private PreviewBuild previewBuilding;

    /**
     * Works its preview out again (all of it, or the batches of what it already shows), in the background, a step at
     * a time (see {@link com.kadikular.quantimium.work.WorkQueue}): a crafting table on a big network previews a
     * thousand crafts, which done at once would stall the server. Sooner while someone has its screen open.
     */
    private void queueRefresh(boolean full) {
        if (full) rebuildQueued = true;
        else rescaleQueued = true;
        // What it could make has changed here: the network it makes things for works its offers out again too.
        if (full && feeder != null) feeder.makerChanged();
        int priority = watchedByPlayer() ? com.kadikular.quantimium.work.WorkQueue.URGENT : com.kadikular.quantimium.work.WorkQueue.NORMAL;
        if (com.kadikular.quantimium.work.WorkQueue.waiting(this)) com.kadikular.quantimium.work.WorkQueue.hurry(this, priority);
        else com.kadikular.quantimium.work.WorkQueue.submit(this, priority, this::refreshStep);
    }

    /** A step of what's queued: a rebuild under way, a new one (asked for again while one ran: its inputs changed), or a resize. */
    private boolean refreshStep() {
        if (isRemoved() || level == null) {
            previewBuilding = null;
            return true;
        }
        if (previewBuilding == null) {
            if (rebuildQueued) {
                rebuildQueued = false;
                rescaleQueued = false;
                previewBuilding = startPreviewBuild();
                return previewBuilding == null && !rebuildQueued && !rescaleQueued;
            }
            if (rescaleQueued) {
                rescaleQueued = false;
                rescaleCachedPreview();
            }
            return !rebuildQueued && !rescaleQueued;
        }
        if (!previewBuilding.step(PREVIEW_SLICE_NANOS)) return false;
        previewBuilding = null;
        return !rebuildQueued && !rescaleQueued;
    }

    /** Works its whole preview out again soon, first in the queue: a player is waiting on it (they took something, or asked for something it should make). */
    public void refreshSoon() {
        queueRefresh(true);
        com.kadikular.quantimium.work.WorkQueue.hurry(this, com.kadikular.quantimium.work.WorkQueue.URGENT);
    }

    /** How many players have its screen open, as its menus open and close. */
    private int viewers;

    /** A player opened its screen ({@code true}) or closed it. */
    public void viewed(boolean open) {
        viewers = Math.max(0, viewers + (open ? 1 : -1));
    }

    /** Whether a player has its screen open. */
    private boolean watchedByPlayer() {
        return viewers > 0;
    }

    private boolean updateLinkLoopState() {
        if (level == null || level.isClientSide()) return false;
        boolean detected = EntangledLinks.hasUnsafeCrafterCycle(level,
                GlobalPos.of(level.dimension(), worldPosition), MAX_CHAIN_DEPTH);
        boolean changed = detected != linkLoopDetected;
        linkLoopDetected = detected;
        return changed;
    }

    // ---- on a logistics network ----

    /**
     * What its links to Logistics Materialisers should see instead of the live network: each network's offers as last
     * worked out (its own left out), or for {@code network}, {@code stock}. Empty if it draws on no network.
     */
    private java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> networkViews(
            @Nullable com.kadikular.quantimium.laser.LogisticsController network,
            @Nullable java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> stock, boolean fromCraftable, boolean withOwn) {
        java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views = new java.util.HashMap<>();
        if (level == null) return views;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack link = inventory.getStackInSlot(slot);
            if (!EntangledLinks.isLink(link)) continue;
            GlobalPos bound = link.get(com.kadikular.quantimium.init.ModDataComponents.BOUND_POS.get());
            if (bound == null || !bound.dimension().equals(level.dimension()) || !level.isLoaded(bound.pos())) continue;
            if (level.getBlockEntity(bound.pos()) instanceof QuantumCrafterBlockEntity crafter && crafter != this) {
                // Another Crafter, linked straight to it: what that one's preview offers, as stock, not asked live.
                views.put(bound, new com.kadikular.quantimium.laser.StockHandler(fromCraftable ? crafter.previewOutputs() : crafter.realOutputs()));
                continue;
            }
            if (!(level.getBlockEntity(bound.pos()) instanceof LogisticsMaterialiserBlockEntity materialiser)) continue;
            com.kadikular.quantimium.laser.LogisticsController controller = materialiser.controller();
            if (controller == null) continue;
            views.put(bound, network != null && controller == network && stock != null
                    ? handlerFor(stock)
                    : controller.offers().view(withOwn ? null : worldPosition, materialiser.offersReal(), materialiser.offersCraftable() && fromCraftable));
        }
        return views;
    }

    /** {@code stock} as a handler: the same one while it's the same stock (a network shows every machine one, a tier). */
    private com.kadikular.quantimium.laser.StockHandler handlerFor(java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> stock) {
        // A stock every machine is shown alike keeps one handler for them all.
        if (stock instanceof com.kadikular.quantimium.laser.OfferTable.Shown shown) return shown.handler();
        if (stock != handledStock || stockHandler == null) {
            handledStock = stock;
            stockHandler = new com.kadikular.quantimium.laser.StockHandler(stock);
        }
        return stockHandler;
    }

    @Nullable
    private java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> handledStock;
    @Nullable
    private com.kadikular.quantimium.laser.StockHandler stockHandler;

    /** Runs {@code body} with its network links seeing the networks' offers rather than the live networks. */
    private <T> T onOffers(java.util.function.Supplier<T> body) {
        return onOffers(false, body);
    }

    /** As {@link #onOffers(java.util.function.Supplier)}; {@code withOwn}, what it could make itself is on offer too. */
    private <T> T onOffers(boolean withOwn, java.util.function.Supplier<T> body) {
        var views = networkViews(null, null, true, withOwn);
        return views.isEmpty() ? body.get() : EntangledLinks.withOverrides(views, body);
    }

    /** Whether a craft, by what it takes, would loop on one of the networks it draws on ({@code networks}). */
    private static boolean loopsOn(List<com.kadikular.quantimium.laser.OfferTable> networks, ResolvedCraft craft) {
        java.util.Set<net.minecraft.world.item.Item> inputs = new java.util.HashSet<>();
        for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) if (withdrawal.expected() != null) inputs.add(withdrawal.expected());
        net.minecraft.world.item.Item out = craft.primaryOutput().getItem();
        for (var offers : networks) if (offers.loops(out, inputs)) return true;
        return false;
    }

    /** The logistics networks it draws on, through Tesseracts bound to their Materialisers. */
    private List<com.kadikular.quantimium.laser.LogisticsController> linkedNetworks() {
        List<com.kadikular.quantimium.laser.LogisticsController> networks = new ArrayList<>();
        if (level == null) return networks;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack link = inventory.getStackInSlot(slot);
            if (!EntangledLinks.isLink(link)) continue;
            GlobalPos bound = link.get(com.kadikular.quantimium.init.ModDataComponents.BOUND_POS.get());
            if (bound == null || !bound.dimension().equals(level.dimension()) || !level.isLoaded(bound.pos())) continue;
            if (!(level.getBlockEntity(bound.pos()) instanceof LogisticsMaterialiserBlockEntity materialiser)) continue;
            var controller = materialiser.controller();
            if (controller != null && !networks.contains(controller)) networks.add(controller);
        }
        return networks;
    }

    /** What the networks it draws on say it could make, chaining its own crafts as well as other machines'. */
    private java.util.Set<net.neoforged.neoforge.transfer.item.ItemResource> madeOnItsNetworks() {
        java.util.Set<net.neoforged.neoforge.transfer.item.ItemResource> made = new java.util.HashSet<>();
        for (var controller : linkedNetworks()) made.addAll(controller.offers().madeBy(worldPosition));
        return made;
    }

    @Override
    public List<com.kadikular.quantimium.laser.OfferMaker.Made> makes(
            com.kadikular.quantimium.laser.LogisticsController network,
            java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> stock, boolean fromCraftable) {
        Making making = making(network, stock, fromCraftable);
        while (!making.step(Long.MAX_VALUE)) {
            // all at once
        }
        return making.result();
    }

    /**
     * What it could make if a network offered {@code stock}, a step at a time: asked the same again (the same stock
     * shown, nothing of its own changed), it answers as before at once; otherwise it looks through its recipes a
     * slice at a time.
     */
    @Override
    public Making making(com.kadikular.quantimium.laser.LogisticsController network,
                         java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> stock, boolean fromCraftable) {
        if (level == null || level.isClientSide() || linkLoopDetected) return new Making(List.of());
        ItemStack catalyst = inventory.getStackInSlot(CATALYST_SLOT);
        if (catalyst.isEmpty() || isCatalystDenied(catalyst) || needsHotterField(catalyst)) return new Making(List.of());
        double multiplier = AnomalyEffects.feMultiplier(level, worldPosition) * Config.crafterTaxFraction(gate.band());
        return ask(networkViews(network, stock, fromCraftable, true), fromCraftable, catalyst, energyStorage.getEnergyStored(), multiplier);
    }

    /**
     * What it could make with {@code views} in front of it, a step at a time: the one way it works that out, for a
     * network asking and for its own preview. Asked the same again, the answer it had; shown nothing new, the crafts
     * it knows, resized; something new, only the recipes naming it matched; and every recipe only at first.
     */
    private Making ask(java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views,
                       boolean fromCraftable, ItemStack catalyst, int fe, double multiplier) {
        // Its power only to a twentieth of what it holds: it charges and drains every tick, and what it could make
        // barely moves with it.
        int power = fe >= energyStorage.getMaxEnergyStored() ? 20 : fe * 20 / Math.max(1, energyStorage.getMaxEnergyStored());
        List<Object> asked = List.of(views, fromCraftable, contentRevision, power, Math.round(multiplier * 1000), catalyst.getItem(),
                recipeLocked, String.valueOf(preferredRecipeId), coherenceReserve);
        List<com.kadikular.quantimium.laser.OfferMaker.Made> known = makesAnswers.get(asked);
        if (known != null) return new Making(known);
        // Shown nothing it wasn't shown a little while ago, only in other numbers, or less of it: the crafts it found
        // then, their batches worked out again, rather than every recipe matched again (most of a busy network's
        // working out). Something new to it, or every so often, it matches them all again: more of something can make
        // a recipe possible that wasn't.
        List<Object> shape = shapeOf(views, fromCraftable, catalyst, multiplier);
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown = shownItems(views);
        Matched matched = matchedByShape.get(shape);
        boolean recent = matched != null && level.getGameTime() - matched.tick() < REMATCH_TICKS;
        if (recent && matched.covers(shown)) {
            if (whyMatched != null) whyMatched.accept("resized");
            return new Making(asked, views, matched.crafts(), fe);
        }
        if (whyMatched != null) {
            String why;
            if (matched == null) why = "full: first";
            else if (!recent) why = "full: a minute on";
            else why = "partial: " + matched.news(shown).size() + (matched.news(shown).size() == 1 ? " item" : " items");
            whyMatched.accept(why.startsWith("partial") ? "partial" : why);
        }
        CrafterPreview.Run run = new CrafterPreview.Run(level, worldPosition, catalyst, inventory, INPUT_SLOTS, fe, multiplier, Integer.MAX_VALUE);
        Making making = new Making(asked, views, run);
        making.shape = shape;
        making.shown = shown;
        if (recent) {
            // Only what could have become possible: the recipes naming what's new, or what there's more of now that
            // there was too little of. The rest it knows already, and resizes.
            run.involving(matched.news(shown));
            making.partial = true;
        }
        return making;
    }

    /** What it's shown, less how many: its grid's items and each view's items, and what it makes things with. */
    private List<Object> shapeOf(java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views,
                                 boolean fromCraftable, ItemStack catalyst, double multiplier) {
        List<Object> shape = new ArrayList<>();
        shape.add(fromCraftable);
        shape.add(catalyst.getItem());
        shape.add(Math.round(multiplier * 1000));
        shape.add(recipeLocked);
        shape.add(String.valueOf(preferredRecipeId));
        long grid = 1;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            grid = grid * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 31L + stack.getComponents().hashCode());
        }
        shape.add(grid);
        views.keySet().stream().sorted(java.util.Comparator.comparing(GlobalPos::toString)).forEach(shape::add);
        return shape;
    }

    /** Every item its views show, and how many. */
    private static java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shownItems(
            java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views) {
        // Mostly one view, a handler that keeps its counts by item (shown alike to every machine asked).
        if (views.size() == 1 && views.values().iterator().next() instanceof com.kadikular.quantimium.laser.StockHandler only) return only.amounts();
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown = new java.util.HashMap<>();
        for (var view : views.values()) {
            if (view instanceof com.kadikular.quantimium.laser.StockHandler stock) {
                stock.amounts().forEach((item, n) -> shown.merge(item, n, Long::sum));
                continue;
            }
            for (int i = 0; i < view.size(); i++) shown.merge(view.getResource(i), view.getAmountAsLong(i), Long::sum);
        }
        return shown;
    }

    /**
     * Keeps the crafts a full match found, with what it was shown. Within the same few seconds, added to what earlier
     * matches found: a kind gone and back (taken out, put back) is still known, its crafts still there to resize, so it
     * doesn't set off a full match each time. Every few seconds it starts afresh.
     */
    private void remember(List<Object> shape, List<ResolvedCraft> crafts, java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown) {
        long now = level.getGameTime();
        Matched was = matchedByShape.get(shape);
        long enough = enoughFor(level, inventory.getStackInSlot(CATALYST_SLOT));
        if (was == null || now - was.tick() >= REMATCH_TICKS) {
            matchedByShape.put(shape, new Matched(crafts, now, new java.util.HashSet<>(shown.keySet()), few(shown, enough), enough));
            return;
        }
        java.util.Map<Identifier, ResolvedCraft> all = new java.util.LinkedHashMap<>();
        for (ResolvedCraft craft : crafts) all.put(craft.recipeId(), craft);
        for (ResolvedCraft craft : was.crafts()) all.putIfAbsent(craft.recipeId(), craft);
        java.util.Set<net.neoforged.neoforge.transfer.item.ItemResource> seen = new java.util.HashSet<>(was.seen());
        seen.addAll(shown.keySet());
        // Short of something then, or now: the most it's been matched with.
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> few = new java.util.HashMap<>(was.few());
        shown.forEach((item, count) -> {
            if (count >= enough) few.remove(item);
            else few.merge(item, count, Math::max);
        });
        matchedByShape.put(shape, new Matched(List.copyOf(all.values()), was.tick(), seen, few, enough));
    }

    /** Told why each network's ask was answered as it was: the stress tests' counts. Null otherwise. */
    @Nullable
    public static java.util.function.Consumer<String> whyMatched;

    /** How long crafts found for a shape are only resized before every recipe is matched again, whatever's changed. */
    private static final int REMATCH_TICKS = 1200;

    /**
     * Below this many of something, more of it could make a recipe possible that wasn't; from it on, more makes nothing
     * new possible: the most any of the catalyst's recipes takes in a run, all its inputs together (nine for a
     * crafting table), worked out once a catalyst. More of something past it only makes more of what's already found,
     * downstream too: each step of a chain is shown what the step before could make, in numbers that cross it.
     */
    private static long enoughFor(Level level, ItemStack catalyst) {
        return ENOUGH_BY_CATALYST.computeIfAbsent(catalyst.getItem(), item -> {
            var shapes = com.kadikular.quantimium.recipe.RecipeShapes.forCatalyst(level, catalyst);
            // Recipes it can't describe (a family of its own): a stack, to be safe.
            if (shapes.isEmpty()) return 64L;
            long most = 1;
            for (com.kadikular.quantimium.recipe.RecipeShape shape : shapes) {
                long run = 0;
                for (com.kadikular.quantimium.recipe.RecipeShape.Input input : shape.inputs()) run += input.count();
                most = Math.max(most, run);
            }
            return most;
        });
    }

    private static final java.util.Map<net.minecraft.world.item.Item, Long> ENOUGH_BY_CATALYST = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Crafts found by matching every recipe, by the shape of what it was shown, and when: what it was shown, and of that
     * what there was too little of ({@link #ENOUGH}) for more to be no news.
     */
    private record Matched(List<ResolvedCraft> crafts, long tick, java.util.Set<net.neoforged.neoforge.transfer.item.ItemResource> seen,
                           java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> few, long enough) {
        /** The items in {@code shown} that could make something possible that wasn't: new, or more of what was short. */
        java.util.Set<net.minecraft.world.item.Item> news(java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown) {
            java.util.Set<net.minecraft.world.item.Item> news = new java.util.HashSet<>();
            for (var entry : shown.entrySet()) {
                Long had = few.get(entry.getKey());
                if (!seen.contains(entry.getKey()) || had != null && entry.getValue() > had) news.add(entry.getKey().getItem());
            }
            return news;
        }

        /** Whether what it's {@code shown} now could only make the crafts found, resized: nothing new, and no more of what was short. */
        boolean covers(java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown) {
            for (var entry : shown.entrySet()) {
                if (!seen.contains(entry.getKey())) return false;
                Long had = few.get(entry.getKey());
                if (had != null && entry.getValue() > had) return false;
            }
            return true;
        }
    }

    private static java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> few(
            java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown, long enough) {
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> few = new java.util.HashMap<>();
        shown.forEach((item, count) -> {
            if (count < enough) few.put(item, count);
        });
        return few;
    }

    private final java.util.Map<List<Object>, Matched> matchedByShape = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<List<Object>, Matched> eldest) {
            return size() > 16;
        }
    };

    /**
     * {@code crafts}, their batches worked out again from what it's shown now (under the views): those it can't make a
     * run of any more left out. A craft that comes out as it was is the same craft, so a Crafter shown it again
     * sees nothing's changed.
     */
    private List<ResolvedCraft> resized(List<ResolvedCraft> crafts, int fe) {
        IngredientPool pool = IngredientPool.build(level, worldPosition, inventory, INPUT_SLOTS);
        java.util.Map<Identifier, ResolvedCraft> was = new java.util.HashMap<>();
        for (ResolvedCraft craft : preview) was.putIfAbsent(craft.recipeId(), craft);
        List<ResolvedCraft> next = new ArrayList<>(crafts.size());
        for (ResolvedCraft craft : crafts) {
            if (!craft.runSatisfiedBy(pool)) continue;
            ResolvedCraft resized = craft.withRuns(1).largestBatch(pool, fe);
            ResolvedCraft same = was.get(resized.recipeId());
            next.add(resized.equals(same) ? same : resized);
        }
        return next;
    }

    /** Working out what it could make for a network: a preview run under the network's offers, a slice a step. */
    private final class Making implements com.kadikular.quantimium.laser.OfferMaker.Making {
        @Nullable
        private final List<Object> asked;
        @Nullable
        private final java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views;
        @Nullable
        private final CrafterPreview.Run run;
        /** Crafts found before, to resize rather than match every recipe again; null to match them all. */
        @Nullable
        private final List<ResolvedCraft> resize;
        private final int fe;
        /** What it was shown, less how many: the crafts a full match finds are kept under it, with what was shown. */
        @Nullable
        List<Object> shape;
        @Nullable
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> shown;
        /** Matching only the recipes involving what's new: what it finds is added to what was known, and all of it resized. */
        boolean partial;
        @Nullable
        private List<com.kadikular.quantimium.laser.OfferMaker.Made> result;
        /** The crafts behind {@link #result}, once worked out. */
        @Nullable
        private List<ResolvedCraft> resolved;

        /** The crafts it found, once done: for its own preview. */
        List<ResolvedCraft> crafts() {
            if (resolved != null) return resolved;
            // An answer it had kept: the crafts its offers came of, each once.
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<ResolvedCraft> crafts = new ArrayList<>();
            for (com.kadikular.quantimium.laser.OfferMaker.Made made : result()) {
                if (made.craft() instanceof ResolvedCraft craft && seen.add(craft)) crafts.add(craft);
            }
            return resolved = crafts;
        }

        /** Already known. */
        Making(List<com.kadikular.quantimium.laser.OfferMaker.Made> known) {
            this.asked = null;
            this.views = null;
            this.run = null;
            this.resize = null;
            this.fe = 0;
            this.result = known;
        }

        Making(List<Object> asked, java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views,
               CrafterPreview.Run run) {
            this.asked = asked;
            this.views = views;
            this.run = run;
            this.resize = null;
            this.fe = 0;
        }

        Making(List<Object> asked, java.util.Map<GlobalPos, net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> views,
               List<ResolvedCraft> resize, int fe) {
            this.asked = asked;
            this.views = views;
            this.run = null;
            this.resize = resize;
            this.fe = fe;
        }

        @Override
        public boolean step() {
            return step(PREVIEW_SLICE_NANOS);
        }

        boolean step(long nanos) {
            if (result != null) return true;
            List<ResolvedCraft> crafts;
            if (resize != null) {
                crafts = EntangledLinks.withOverrides(views, () -> resized(resize, fe));
            } else {
                if (!EntangledLinks.withOverrides(views, () -> run.step(nanos))) return false;
                crafts = run.result();
                if (shape != null && shown != null && level != null) remember(shape, crafts, shown);
                if (partial && shape != null && matchedByShape.get(shape) != null) {
                    List<ResolvedCraft> known = matchedByShape.get(shape).crafts();
                    int energy = energyStorage.getEnergyStored();
                    crafts = EntangledLinks.withOverrides(views, () -> resized(known, energy));
                }
            }
            if (isBasic()) crafts = limitToCoherence(crafts);
            if (recipeLocked && preferredRecipeId != null) crafts = crafts.stream().filter(c -> c.recipeId().equals(preferredRecipeId)).toList();
            resolved = crafts;
            List<com.kadikular.quantimium.laser.OfferMaker.Made> made = new ArrayList<>();
            for (ResolvedCraft craft : crafts) {
                java.util.Set<net.minecraft.world.item.Item> from = new java.util.HashSet<>();
                for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) if (withdrawal.expected() != null) from.add(withdrawal.expected());
                List<ItemStack> outputs = craft.outputs();
                for (int i = 0; i < outputs.size(); i++) {
                    ItemStack output = outputs.get(i);
                    if (output.isEmpty()) continue;
                    made.add(new com.kadikular.quantimium.laser.OfferMaker.Made(net.neoforged.neoforge.transfer.item.ItemResource.of(output),
                            canMake(craft, i), java.util.Set.copyOf(from), craft));
                }
            }
            result = List.copyOf(made);
            makesAnswers.put(asked, result);
            return true;
        }

        @Override
        public List<com.kadikular.quantimium.laser.OfferMaker.Made> result() {
            return result == null ? List.of() : result;
        }
    }

    /** The network that last handed it what it makes (see {@link #offered}), and the crafts it handed. */
    @Nullable
    private com.kadikular.quantimium.laser.LogisticsController feeder;
    /** The crafts last handed it and shown; null once anything else has been shown since. */
    @Nullable
    private List<Object> fed;

    /**
     * Its network has worked out what it offers, this Crafter's crafts among them: those are its preview, at once.
     * They were worked out the same way its own preview is (its grid, and every network it draws on with what's on
     * offer there, its own crafts too), so it doesn't work them out again; and what the network shows it makes is
     * exactly what a pull from it can make.
     */
    @Override
    public void offered(com.kadikular.quantimium.laser.LogisticsController network, List<Object> crafts) {
        if (level == null || level.isClientSide()) return;
        feeder = network;
        if (fed != null && sameCrafts(crafts, fed)) return;
        List<ResolvedCraft> shown = new ArrayList<>(crafts.size());
        java.util.Set<Identifier> chainedNow = new java.util.HashSet<>();
        for (Object craft : crafts) {
            if (craft instanceof ResolvedCraft resolved && chainedNow.add(resolved.recipeId())) shown.add(resolved);
        }
        // A rebuild of its own already queued (its grid changed since the network asked) still runs, and is newer.
        show(CrafterPreview.cap(shown, CrafterPreview.MAX_OUTPUTS), chainedNow);
        fed = crafts;
    }

    /** Whether two hand-outs are the same crafts, craft for craft: an answer the network had kept comes back as it was. */
    private static boolean sameCrafts(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** Whether a network it's a maker on keeps what it makes worked out: it needn't watch that network change itself. */
    private boolean fedByNetwork() {
        var network = feeder;
        if (network == null) return false;
        if (network instanceof BlockEntity entity && entity.isRemoved()) {
            feeder = null;
            return false;
        }
        // Drawing on another network as well, it watches its links: that one's changes don't reach this one's table.
        for (var linked : linkedNetworks()) if (linked != network) return false;
        for (var member : network.members()) if (member.maker() == this) return true;
        feeder = null;
        return false;
    }

    /** How many of output {@code index} a craft could make from what it has: "Can make", not one batch. */
    private static long canMake(ResolvedCraft craft, int index) {
        return Math.max(craft.outputs().get(index).getCount(), craft.availableOutputCount(index));
    }

    /** What's really in its outputs, by item. */
    public java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> realOutputs() {
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> outputs = new java.util.LinkedHashMap<>();
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            ItemStack real = inventory.getStackInSlot(slot);
            if (!real.isEmpty()) outputs.merge(net.neoforged.neoforge.transfer.item.ItemResource.of(real), (long) real.getCount(), Long::sum);
        }
        return outputs;
    }

    /** The last few things a network asked it could make, and the answers (see {@link #makes}): one a tier, and a few more. */
    private final java.util.Map<List<Object>, List<com.kadikular.quantimium.laser.OfferMaker.Made>> makesAnswers =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<List<Object>, List<com.kadikular.quantimium.laser.OfferMaker.Made>> eldest) {
                    return size() > 64;
                }
            };

    /** What its preview offers, by item: the most of each one craft would make, plus what's really in its outputs. */
    public java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> previewOutputs() {
        java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> outputs = new java.util.LinkedHashMap<>();
        for (ResolvedCraft craft : preview) {
            for (int i = 0; i < craft.outputs().size(); i++) {
                ItemStack output = craft.outputs().get(i);
                if (!output.isEmpty()) outputs.merge(net.neoforged.neoforge.transfer.item.ItemResource.of(output), canMake(craft, i), Long::max);
            }
        }
        realOutputs().forEach((item, count) -> outputs.merge(item, count, Long::sum));
        return outputs;
    }

    @Override
    public ItemStack peekOnDemand(int slot) {
        return slot > OUTPUT_END ? getGhost(GHOST_BASE + slot - OUTPUT_END - 1) : ItemStack.EMPTY;
    }

    /** Its outputs: its grid and links are what it makes things from. */
    @Override
    public boolean stocks(int slot) {
        return slot >= OUTPUT_START && slot <= OUTPUT_END;
    }

    @Override
    public boolean makesOnDemand(int slot) {
        return slot > OUTPUT_END;
    }

    /** Works its preview out again, all at once. */
    public void rebuildPreview() {
        PreviewBuild build = startPreviewBuild();
        if (build == null) return;
        while (!build.step(Long.MAX_VALUE)) {
            // all at once
        }
    }

    /** How long one step of working a preview out takes, at most: a crafting table's thousand recipes take several. */
    private static final long PREVIEW_SLICE_NANOS = 1_000_000;

    /**
     * Starts working its preview out again: what it can't make anything for (no catalyst, a denied one, too cold a
     * field, a loop of links) is shown at once, and null comes back; otherwise a build to step.
     */
    @Nullable
    private PreviewBuild startPreviewBuild() {
        if (level == null || level.isClientSide()) return null;

        updateLinkLoopState();
        ghostLayout = null;
        if (linkLoopDetected) {
            showNothing(STATUS_ENTANGLED_LOOP);
            return null;
        }
        ItemStack catalyst = inventory.getStackInSlot(CATALYST_SLOT);
        if (catalyst.isEmpty()) {
            showNothing(STATUS_IDLE);
            return null;
        }
        if (isCatalystDenied(catalyst)) {
            showNothing(STATUS_DENIED);
            return null;
        }
        if (needsHotterField(catalyst)) {
            showNothing(STATUS_NEEDS_FLUX);
            return null;
        }
        // The anomaly surcharge, times the instant-craft tax for the band here: a hot field crafts cheaper.
        double multiplier = AnomalyEffects.feMultiplier(level, worldPosition) * Config.crafterTaxFraction(gate.band());
        return new PreviewBuild(catalyst, energyStorage.getEnergyStored(), multiplier);
    }

    private void showNothing(int status) {
        preview = List.of();
        statusCode = status;
        previewChanged();
        sync();
    }

    /**
     * Its preview worked out a step at a time: what it makes from what it has, then (on a network that says it could
     * chain its own crafts) what it makes with its own crafts on offer too, then shown.
     */
    private final class PreviewBuild {
        private final ItemStack catalyst;
        private final int fe;
        private final double multiplier;
        @Nullable
        private Making first;
        @Nullable
        private Making more;
        private List<ResolvedCraft> crafts = List.of();
        private java.util.Set<net.neoforged.neoforge.transfer.item.ItemResource> chainable = java.util.Set.of();

        PreviewBuild(ItemStack catalyst, int fe, double multiplier) {
            this.catalyst = catalyst.copy();
            this.fe = fe;
            this.multiplier = multiplier;
        }

        /** Works for about {@code nanos}; whether it's done and shown. */
        boolean step(long nanos) {
            if (level == null) return true;
            boolean priorBusy = automationBusy;
            enterChain();
            try {
                // Worked out as a network's ask is: what it knows resized where it can, not every recipe matched again.
                if (first == null) first = ask(networkViews(null, null, true, false), true, catalyst, fe, multiplier);
                if (more == null) {
                    if (!first.step(nanos)) return false;
                    crafts = CrafterPreview.cap(first.crafts(), CrafterPreview.MAX_OUTPUTS);
                    // On a network that says it could chain its own crafts (leggings from ingots it would make from
                    // blocks), those too: worked out again with its own crafts on offer, for what the network says.
                    chainable = madeOnItsNetworks();
                    if (chainable.isEmpty()) {
                        show(crafts, java.util.Set.of());
                        return true;
                    }
                    more = ask(networkViews(null, null, true, true), true, catalyst, fe, multiplier);
                    if (nanos != Long.MAX_VALUE) return false;
                }
                if (!more.step(nanos)) return false;
                // A craft it already shows from what's there is shown as the chain would make it, if that's more.
                List<ResolvedCraft> joined = new ArrayList<>(crafts);
                java.util.Set<Identifier> chainedNow = new java.util.HashSet<>();
                java.util.Map<Identifier, Integer> at = new java.util.HashMap<>();
                List<com.kadikular.quantimium.laser.OfferTable> networks = new ArrayList<>();
                for (var controller : linkedNetworks()) networks.add(controller.offers());
                for (int i = 0; i < joined.size(); i++) at.putIfAbsent(joined.get(i).recipeId(), i);
                for (ResolvedCraft craft : more.crafts()) {
                    boolean wanted = false;
                    for (ItemStack output : craft.outputs()) {
                        if (!output.isEmpty() && chainable.contains(net.neoforged.neoforge.transfer.item.ItemResource.of(output))) wanted = true;
                    }
                    // Not a craft out of what it would make from this craft's own output (ingots from the nuggets it'd
                    // make from those ingots): the network leaves such loops out, and so does it.
                    if (!wanted || loopsOn(networks, craft)) continue;
                    Integer was = at.get(craft.recipeId());
                    if (was == null) {
                        at.put(craft.recipeId(), joined.size());
                        joined.add(craft);
                        chainedNow.add(craft.recipeId());
                    } else if (craft.availableOutputCount(0) > joined.get(was).availableOutputCount(0)) {
                        joined.set(was, craft);
                        chainedNow.add(craft.recipeId());
                    }
                }
                show(joined, chainedNow);
                return true;
            } finally {
                exitChain(priorBusy);
            }
        }
    }

    /** Shows {@code crafts} as its preview, those in {@code chainedNow} worked out with its own crafts on offer. */
    private void show(List<ResolvedCraft> crafts, java.util.Set<Identifier> chainedNow) {
        // Whatever the network hands it next is shown, even the same crafts as before this.
        fed = null;
        chained = chainedNow;
        if (isBasic()) crafts = limitToCoherence(crafts);
        if (recipeLocked && preferredRecipeId != null) {
            List<ResolvedCraft> locked = new ArrayList<>();
            for (ResolvedCraft craft : crafts) {
                if (craft.recipeId().equals(preferredRecipeId)) locked.add(craft);
            }
            if (!locked.isEmpty()) crafts = locked;
        } else if (preferredRecipeId != null) {
            // Prefer last-selected recipe first in the ghost list.
            crafts = new ArrayList<>(crafts);
            crafts.sort((a, b) -> {
                boolean ap = a.recipeId().equals(preferredRecipeId);
                boolean bp = b.recipeId().equals(preferredRecipeId);
                if (ap == bp) return 0;
                return ap ? -1 : 1;
            });
        }

        preview = List.copyOf(crafts);
        previewChanged();
        updateStatusFromPreview();
        sync();
    }

    /** Everything derived from the preview is stale: layout, automation view, and the signatures. */
    private void previewChanged() {
        ghostLayout = null;
        automationViewRevision = Long.MIN_VALUE;
        commitCheckRevision = Long.MIN_VALUE;
        captureSignatures();
    }

    /**
     * Cheap follow-up after a craft or energy change: rebuild batch sizes for crafts we already know
     * match, without scanning the entire recipe manager again.
     */
    private void rescaleCachedPreview() {
        if (level == null || level.isClientSide() || linkLoopDetected || preview.isEmpty()) return;

        IngredientPool own;
        IngredientPool withOwn = null;
        boolean priorBusy = automationBusy;
        enterChain();
        try {
            own = onOffers(() -> IngredientPool.build(level, worldPosition, inventory, INPUT_SLOTS));
            if (!chained.isEmpty()) withOwn = onOffers(true, () -> IngredientPool.build(level, worldPosition, inventory, INPUT_SLOTS));
        } finally {
            exitChain(priorBusy);
        }
        int availableFe = energyStorage.getEnergyStored();
        List<ResolvedCraft> next = new ArrayList<>(preview.size());
        boolean stale = false;
        for (ResolvedCraft craft : preview) {
            IngredientPool pool = withOwn != null && chained.contains(craft.recipeId()) ? withOwn : own;
            if (!craft.runSatisfiedBy(pool)) {
                stale = true;
                continue;
            }
            ResolvedCraft resized = craft.withRuns(1).largestBatch(pool, availableFe);
            next.add(isBasic() ? limitToCoherence(resized) : resized);
        }

        if (next.isEmpty()) {
            // Cached recipes are spent; discover whatever else the grid can still make.
            queueRefresh(true);
            return;
        }
        if (recipeLocked && preferredRecipeId != null) {
            boolean stillHasPreferred = false;
            for (ResolvedCraft craft : next) {
                if (craft.recipeId().equals(preferredRecipeId)) {
                    stillHasPreferred = true;
                    break;
                }
            }
            if (!stillHasPreferred) {
                queueRefresh(true);
                return;
            }
        }

        if (samePreviewBatches(preview, next)) {
            // Outputs may still have moved under a suppressed commit, so the layout is not reusable.
            previewChanged();
            if (stale) scheduleRediscovery();
            updateStatusFromPreview();
            return;
        }

        preview = List.copyOf(next);
        previewChanged();
        if (stale) scheduleRediscovery();
        updateStatusFromPreview();
        clientSyncDirty = true;
        setChanged();
    }

    /**
     * A cached craft lost track of where its ingredients live: withdrawals name exact slots, and both a
     * linked chest restacking and a linked crafter re-parking its ghosts after a craft invalidate that
     * without the recipe itself being gone. Ask for a rescan on the next poll, or the craft stays
     * missing (or under-batched) until something else happens to invalidate the cache. Chained crafters
     * hit this every commit, which is why they appeared to need the GUI reopened by hand.
     */
    private void scheduleRediscovery() {
        discoveryPending = true;
        localDirty = true;
    }

    private static boolean samePreviewBatches(List<ResolvedCraft> a, List<ResolvedCraft> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ResolvedCraft left = a.get(i);
            ResolvedCraft right = b.get(i);
            if (!left.recipeId().equals(right.recipeId())) return false;
            if (left.batchSize() != right.batchSize()) return false;
            if (left.feCost() != right.feCost()) return false;
            if (left.energyCapped() != right.energyCapped()) return false;
            if (left.outputs().size() != right.outputs().size()) return false;
            for (int o = 0; o < left.outputs().size(); o++) {
                ItemStack lo = left.outputs().get(o);
                ItemStack ro = right.outputs().get(o);
                if (!ItemStack.isSameItemSameComponents(lo, ro) || lo.getCount() != ro.getCount()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void updateStatusFromPreview() {
        if (linkLoopDetected) {
            statusCode = STATUS_ENTANGLED_LOOP;
            return;
        }
        if (preview.isEmpty()) {
            // A refused catalyst also leaves the preview empty; say why rather than "no recipe".
            ItemStack catalyst = inventory.getStackInSlot(CATALYST_SLOT);
            statusCode = catalyst.isEmpty() ? STATUS_IDLE
                    : isCatalystDenied(catalyst) ? STATUS_DENIED
                    : needsHotterField(catalyst) ? STATUS_NEEDS_FLUX : STATUS_NO_RECIPE;
            return;
        }
        if (isBasic() && coherenceReserve <= 0) {
            statusCode = STATUS_REPLENISHING;
            return;
        }
        int cheapest = Integer.MAX_VALUE;
        for (ResolvedCraft craft : preview) {
            cheapest = Math.min(cheapest, craft.feCost());
        }
        if (cheapest > energyStorage.getEnergyStored()) {
            statusCode = STATUS_NO_POWER;
        } else if (!hasGhost()) {
            // Results are backed up in every output slot, so nothing can be previewed or taken.
            statusCode = STATUS_OUTPUT_FULL;
        } else {
            statusCode = STATUS_READY;
        }
    }

    /** Whether the catalyst's family needs a hotter field than the one here. */
    private boolean needsHotterField(ItemStack catalyst) {
        return gate.band().ordinal() < CatalystResolver.requiredBand(catalyst).ordinal();
    }

    /** The band the catalyst in the slot needs; Low when there is none. */
    private FluxBand requiredBand() {
        ItemStack catalyst = inventory.getStackInSlot(CATALYST_SLOT);
        return catalyst.isEmpty() ? FluxBand.LOW : CatalystResolver.requiredBand(catalyst);
    }

    /** Reads the band here; true when it changed, since that changes what works and what it costs. */
    private boolean refreshBand(Level level) {
        FluxBand before = gate.band();
        gate.update(level, worldPosition);
        return gate.band() != before;
    }

    public BandGate bandGate() {
        return gate;
    }

    /** Off the whitelist, on the blacklist, or more than a Basic crafter can take. */
    private boolean isCatalystDenied(ItemStack catalyst) {
        return !CatalystResolver.isCatalystAllowed(catalyst)
                || isBasic() && !catalyst.is(ModTags.BASIC_CRAFTER_CATALYSTS);
    }

    private List<ResolvedCraft> limitToCoherence(List<ResolvedCraft> crafts) {
        List<ResolvedCraft> limited = new ArrayList<>(crafts.size());
        for (ResolvedCraft craft : crafts) limited.add(limitToCoherence(craft));
        return limited;
    }

    private ResolvedCraft limitToCoherence(ResolvedCraft craft) {
        int runs = Math.max(1, Math.min(craft.batchSize(), Math.max(1, coherenceReserve)));
        return craft.withRuns(runs);
    }

    private boolean hasGhost() {
        for (GhostSlot ghost : ghostLayout()) {
            if (ghost != null) return true;
        }
        return false;
    }

    private EntangledLinks.RemoteSummary remoteSummary() {
        // A detected cycle points back at us; summarising through it would have the disabled crafters
        // invalidating one another forever. Topology is re-checked every poll instead. A network that hands it
        // what it makes tells it when that changes: it doesn't watch its links itself.
        if (linkLoopDetected) return EntangledLinks.RemoteSummary.EMPTY;
        if (fedByNetwork()) {
            // Looked at, the network sees what's moved on it, and works its offers (and so this) out again.
            feeder.offers();
            return EntangledLinks.RemoteSummary.EMPTY;
        }
        return EntangledLinks.summarize(level, worldPosition, inventory, INPUT_SLOTS);
    }

    /**
     * Which recipes could possibly match: the items on hand rather than how many of them. Only a
     * change here justifies scanning the recipe manager again.
     */
    private long structureSignature(EntangledLinks.RemoteSummary remote) {
        long hash = 1;
        // Inputs + catalyst only. Empty output cells dominate TOTAL_SLOTS and change every craft
        // park/unpark; hashing them forced a full recipe rescan for nothing.
        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode());
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getComponents().hashCode());
        }
        ItemStack catalyst = inventory.getStackInSlot(CATALYST_SLOT);
        hash = hash * 31 + (catalyst.isEmpty() ? 0 : catalyst.getItem().hashCode());
        // Energy is checked by updateStatusFromPreview / rescale; including the exact FE value here
        // made every hopper craft invalidate the recipe cache.
        hash = hash * 31 + (recipeLocked ? 1 : 0);
        hash = hash * 31 + (preferredRecipeId == null ? 0 : preferredRecipeId.hashCode());
        hash = hash * 31 + (linkLoopDetected ? 0x51C1E : 0);
        hash = hash * 31 + remote.typeHash();
        return hash;
    }

    /** How much of it there is here. Movement only resizes batches we already resolved. */
    private long localCount() {
        long total = 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            total += inventory.getStackInSlot(i).getCount();
        }
        return total;
    }

    private void captureSignatures() {
        EntangledLinks.RemoteSummary remote = remoteSummary();
        structureSignature = structureSignature(remote);
        localCountSignature = localCount();
        remoteCountSignature = remote.countTotal();
        scaledAtEnergy = energyStorage.getEnergyStored();
        localDirty = false;
    }

    /**
     * Updates the GUI surcharge readout. Returns true when the anomaly band changed, so preview
     * costs need to be rebuilt.
     */
    private boolean refreshAnomalyReadout() {
        FluxBand band = QuantumFlux.chunkAnomalyBand(level, worldPosition);
        int percent = AnomalyEffects.surchargePercent(level, worldPosition);
        syncedAnomalyOrdinal = band.ordinal();
        syncedSurchargePercent = percent;
        if (band == pricedAtAnomaly && percent == pricedAtSurchargePercent) return false;
        pricedAtAnomaly = band;
        pricedAtSurchargePercent = percent;
        return true;
    }

    /** Whether the buffer has moved in a way that would change how many runs a batch can pay for. */
    private boolean energyWouldResizeBatches() {
        if (preview.isEmpty() || energyStorage.getEnergyStored() == scaledAtEnergy) return false;
        if (statusCode == STATUS_NO_POWER) return true;
        for (ResolvedCraft craft : preview) {
            if (craft.energyCapped()) return true;
        }
        return false;
    }

    /** Distinct items automation could take from here, real or previewed. Cheap by design: linked
     * crafters poll this instead of asking whether each of our previews can commit. */
    public long advertisedTypeHash() {
        Set<Integer> ids = new TreeSet<>();
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) ids.add(BuiltInRegistries.ITEM.getId(stack.getItem()));
        }
        for (ResolvedCraft craft : preview) {
            for (ItemStack output : craft.outputs()) {
                if (!output.isEmpty()) ids.add(BuiltInRegistries.ITEM.getId(output.getItem()));
            }
        }
        long hash = 1;
        for (int id : ids) hash = hash * 31 + id;
        return hash;
    }

    /** Companion count for {@link #advertisedTypeHash()}. */
    public long advertisedCount() {
        long total = 0;
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            total += inventory.getStackInSlot(slot).getCount();
        }
        for (ResolvedCraft craft : preview) {
            for (ItemStack output : craft.outputs()) total += output.getCount();
        }
        return total;
    }

    /**
     * Whether the player (or menu) may take from this output slot — either real items or a commitable ghost.
     */
    public boolean mayTakeOutput(int absoluteOutputSlot) {
        if (absoluteOutputSlot >= OUTPUT_START && absoluteOutputSlot <= OUTPUT_END) return !inventory.getStackInSlot(absoluteOutputSlot).isEmpty();
        ResolvedCraft craft = getCraftForOutputSlot(absoluteOutputSlot);
        return craft != null && canCommit(craft);
    }

    public boolean canCommit(ResolvedCraft craft) {
        if (craft == null || level == null || linkLoopDetected) return false;
        if (energyStorage.getEnergyStored() < craft.feCost()) return false;
        if (isBasic() && coherenceReserve < craft.batchSize()) return false;
        return withdrawalsAvailable(craft);
    }

    /**
     * {@link #canCommit} for a preview entry, remembered until the contents move. A hopper asks about
     * all 54 output cells per transfer attempt, and each answer can walk a linked inventory.
     */
    private boolean canCommitCached(int craftIndex) {
        if (craftIndex < 0 || craftIndex >= preview.size()) return false;
        if (!com.kadikular.quantimium.recipe.CraftChain.cacheable()) return canCommit(preview.get(craftIndex));
        // Kept while its own contents stand, a tick at most, and not past a settled transaction; and per chain, since
        // what it can draw on depends on which machines are busy.
        long now = level == null ? 0 : level.getGameTime();
        long epoch = com.kadikular.quantimium.recipe.CraftChain.epoch();
        if (commitCheckRevision != contentRevision || commitCheckTick != now || commitCheckEpoch != epoch) {
            commitCheckCache.clear();
            commitCheckRevision = contentRevision;
            commitCheckTick = now;
            commitCheckEpoch = epoch;
        }
        byte[] cache = commitCheckCache.computeIfAbsent(com.kadikular.quantimium.recipe.CraftChain.context(), key -> new byte[preview.size()]);
        if (cache.length != preview.size()) return canCommit(preview.get(craftIndex));
        byte cached = cache[craftIndex];
        if (cached != 0) return cached == 1;
        boolean allowed = canCommit(preview.get(craftIndex));
        cache[craftIndex] = (byte) (allowed ? 1 : 2);
        return allowed;
    }

    private boolean withdrawalsAvailable(ResolvedCraft craft) {
        boolean priorBusy = automationBusy;
        enterChain();
        try {
            // What it would draw from a network is judged by the network's offers (its own crafts too: it may make
            // another recipe on the way), not by asking it live.
            return onOffers(true, () -> {
                for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
                    if (withdrawal.count() <= 0) continue;
                    if (!canWithdraw(withdrawal)) return false;
                }
                return true;
            });
        } finally {
            exitChain(priorBusy);
        }
    }

    /**
     * Puts this Crafter on the chain: from here until {@link #exitChain}, it offers nothing it would make. The
     * chain's length counts machines, so only joining it (not a second step of the same work) lengthens it.
     */
    private void enterChain() {
        if (!automationBusy) com.kadikular.quantimium.recipe.CraftChain.enter(worldPosition.asLong());
        automationBusy = true;
    }

    private void exitChain(boolean priorBusy) {
        if (!priorBusy) com.kadikular.quantimium.recipe.CraftChain.exit(worldPosition.asLong());
        automationBusy = priorBusy;
    }

    /** Simulated pull, so a source that only lets us look never counts as a source. */
    private boolean canWithdraw(IngredientPool.Withdrawal withdrawal) {
        int got = gatherWithdrawal(withdrawal, true).getCount();
        return got >= withdrawal.count();
    }

    @Nullable
    private IItemHandler remoteFor(int linkSlot) {
        if (linkSlot < 0 || linkSlot >= INPUT_SLOTS) return null;
        return EntangledLinks.resolve(level, worldPosition, inventory.getStackInSlot(linkSlot));
    }

    /**
     * The real pull, spanning every slot of the source. A withdrawal names one slot as a hint, but the
     * item it wants can be spread across several — one raw iron in the first chest slot, a stack in the
     * next — so it is gathered by identity until the count is met. Each source is checked against the
     * item the craft was resolved from: a linked inventory can restack, and taking whatever now sits in
     * the hinted slot would spend the wrong items while still minting the advertised output.
     */
    private ItemStack executeWithdrawal(IngredientPool.Withdrawal withdrawal) {
        return gatherWithdrawal(withdrawal, false);
    }

    /**
     * Collects up to {@code withdrawal.count()} of the resolved item from the source. {@code simulate}
     * leaves the source untouched; a real gather that comes up short is unwound by the caller's
     * all-or-nothing refund. Slots are combined only when their stacks share components, so a mixed
     * source never fuses two variants into one bogus stack.
     */
    private ItemStack gatherWithdrawal(IngredientPool.Withdrawal withdrawal, boolean simulate) {
        int need = withdrawal.count();
        if (need <= 0) return ItemStack.EMPTY;
        return switch (withdrawal.provenance()) {
            case IngredientPool.Provenance.Local(int hintSlot) ->
                    gather(withdrawal, need, simulate, hintSlot, INPUT_SLOTS, this::localSlot,
                            (slot, amount) -> inventory.extractItem(slot, amount, simulate));
            case IngredientPool.Provenance.Remote(int linkSlot, int hintSlot) -> {
                IItemHandler remote = remoteFor(linkSlot);
                if (remote == null) yield ItemStack.EMPTY;
                yield gather(withdrawal, need, simulate, hintSlot, remote.getSlots(),
                        remote::getStackInSlot, (slot, amount) -> remote.extractItem(slot, amount, simulate));
            }
        };
    }

    private ItemStack localSlot(int slot) {
        return slot >= 0 && slot < INPUT_SLOTS ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** Shared slot-walker for both sources: hint slot first, then the rest, gathering by identity. */
    private ItemStack gather(IngredientPool.Withdrawal withdrawal, int need, boolean simulate, int hintSlot,
                             int slotCount, java.util.function.IntFunction<ItemStack> peek,
                             java.util.function.BiFunction<Integer, Integer, ItemStack> extract) {
        if (hintSlot < 0 || hintSlot >= slotCount) hintSlot = 0;
        ItemStack gathered = ItemStack.EMPTY;
        for (int step = 0; step < slotCount && gathered.getCount() < need; step++) {
            // Visit the hint first, then every other slot in order, each exactly once.
            int slot = step == 0 ? hintSlot : (step <= hintSlot ? step - 1 : step);
            if (slot < 0 || slot >= slotCount) continue;
            if (step != 0 && slot == hintSlot) continue;
            ItemStack present = peek.apply(slot);
            if (present.isEmpty() || EntangledLinks.isLink(present) || !withdrawal.matches(present)) continue;
            if (!gathered.isEmpty() && !ItemStack.isSameItemSameComponents(gathered, present)) continue;
            ItemStack taken = extract.apply(slot, need - gathered.getCount());
            if (taken.isEmpty()) continue;
            if (!withdrawal.matches(taken) || (!gathered.isEmpty()
                    && !ItemStack.isSameItemSameComponents(gathered, taken))) {
                // A linked crafter crafts on demand and can hand back something else; give it back.
                if (!simulate) refundWithdrawal(withdrawal, taken);
                continue;
            }
            gathered = gathered.isEmpty() ? taken : gathered.copyWithCount(gathered.getCount() + taken.getCount());
            // One pull hands over a stack at most: a slot holding more (a network, a drawer) is pulled again.
            int fromSlot = taken.getCount();
            while (gathered.getCount() < need && taken.getCount() >= taken.getMaxStackSize() && present.getCount() > fromSlot) {
                if (simulate) {
                    // Asking again would see the same stack: count what's left in the slot instead.
                    gathered = gathered.copyWithCount(gathered.getCount() + Math.min(present.getCount() - fromSlot, need - gathered.getCount()));
                    break;
                }
                taken = extract.apply(slot, need - gathered.getCount());
                if (taken.isEmpty()) break;
                if (!ItemStack.isSameItemSameComponents(gathered, taken)) {
                    refundWithdrawal(withdrawal, taken);
                    break;
                }
                fromSlot += taken.getCount();
                gathered = gathered.copyWithCount(gathered.getCount() + taken.getCount());
            }
        }
        return gathered;
    }

    /**
     * Returns ingredients to where they came from when a later withdrawal falls short. The slot they
     * came out of can refuse them, since a linked crafter's output catalog is extract-only, so our own
     * input grid takes them next: it is where an ingredient belongs, and it keeps a part we made
     * upstream from being pushed back into that machine's inputs. The output grid is the last resort,
     * because anything parked there reads as a finished product and gets advertised as one.
     */
    private void refundWithdrawal(IngredientPool.Withdrawal withdrawal, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack leftover = stack;
        if (withdrawal.provenance() instanceof IngredientPool.Provenance.Local(int crafterSlot)) {
            leftover = inventory.insertItem(crafterSlot, leftover, false);
        } else if (withdrawal.provenance() instanceof IngredientPool.Provenance.Remote(int linkSlot, int remoteSlot)) {
            IItemHandler remote = remoteFor(linkSlot);
            if (remote != null && remoteSlot >= 0 && remoteSlot < remote.getSlots()) {
                leftover = remote.insertItem(remoteSlot, leftover, false);
            }
        }
        leftover = insertIntoInputs(leftover);
        if (!leftover.isEmpty()) insertIntoOutputs(leftover, false);
    }

    /** Puts a refunded ingredient back among the inputs, where an ingredient belongs. */
    private ItemStack insertIntoInputs(ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < INPUT_SLOTS && !remaining.isEmpty(); slot++) {
            if (EntangledLinks.isLink(inventory.getStackInSlot(slot))) continue;
            remaining = inventory.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    /**
     * Commits a craft into real output slots. The output at {@code handedOutIndex} is returned for the
     * caller to give to the player or automation; every other output stays in the machine. Pass -1 to
     * keep all of them.
     */
    public ItemStack commitCraft(ResolvedCraft craft, int handedOutIndex) {
        if (!canCommit(craft) || level == null || level.isClientSide()) return ItemStack.EMPTY;
        return commitCraftTrusted(craft, handedOutIndex);
    }

    /** Caller already proved the craft is payable; used by automation after advertising the ghost. */
    private ItemStack commitCraftTrusted(ResolvedCraft craft, int handedOutIndex) {
        if (craft == null || level == null || level.isClientSide() || linkLoopDetected) return ItemStack.EMPTY;
        if (energyStorage.getEnergyStored() < craft.feCost()) return ItemStack.EMPTY;
        if (isBasic() && coherenceReserve < craft.batchSize()) return ItemStack.EMPTY;

        // A link can lead back here: a craft that ran again halfway through itself would be working from inputs it
        // had already spent, so while it runs, that recipe isn't offered. Another may be, made for it.
        if (loopsUpChain(craft)) return ItemStack.EMPTY;
        if (!running.add(craft.recipeId())) return ItemStack.EMPTY;
        boolean priorBusy = automationBusy;
        boolean priorSuppress = suppressInventoryNotify;
        enterChain();
        suppressInventoryNotify = true;
        java.util.List<net.minecraft.world.item.Item> makes = outputItems(craft);
        com.kadikular.quantimium.recipe.CraftChain.startMaking(makes);
        try {
            return commitWithdrawnCraft(craft, handedOutIndex);
        } finally {
            com.kadikular.quantimium.recipe.CraftChain.stopMaking(makes);
            running.remove(craft.recipeId());
            suppressInventoryNotify = priorSuppress;
            exitChain(priorBusy);
            // Slot writes during the craft skipped their own notification, so ask for the sync here.
            clientSyncDirty = true;
            // Energy and remote sources moved even when none of our own slots did.
            automationViewRevision = Long.MIN_VALUE;
            commitCheckRevision = Long.MIN_VALUE;
        }
    }

    private ItemStack commitWithdrawnCraft(ResolvedCraft craft, int handedOutIndex) {
        // Ingredients first, and all or nothing: a remote source can refuse mid-craft (another
        // machine emptied it, or its own craft failed), and a partial pull would mint items.
        List<IngredientPool.Withdrawal> taken = new ArrayList<>();
        List<ItemStack> takenStacks = new ArrayList<>();
        for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
            if (withdrawal.count() <= 0) continue;
            ItemStack pulled = executeWithdrawal(withdrawal);
            if (pulled.getCount() < withdrawal.count()) {
                if (!pulled.isEmpty()) refundWithdrawal(withdrawal, pulled);
                for (int i = 0; i < taken.size(); i++) refundWithdrawal(taken.get(i), takenStacks.get(i));
                failed(craft);
                return ItemStack.EMPTY;
            }
            taken.add(withdrawal);
            takenStacks.add(pulled);
        }
        // What it made for itself on the way spent power (and coherence) too: if too little's left, it all goes back.
        if (energyStorage.getEnergyStored() < craft.feCost() || isBasic() && coherenceReserve < craft.batchSize()) {
            for (int i = 0; i < taken.size(); i++) refundWithdrawal(taken.get(i), takenStacks.get(i));
            failed(craft);
            return ItemStack.EMPTY;
        }

        if (isBasic()) {
            coherenceReserve = Math.max(0, coherenceReserve - craft.batchSize());
        }
        energyStorage.consume(craft.feCost());
        if (craft.feCost() > 0) {
            feSpentThisTick = Math.min(Long.MAX_VALUE - feSpentThisTick, feSpentThisTick + craft.feCost());
        }
        // Inside a transaction (a pull that may yet be aborted), the flux waits for it to commit.
        if (net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle() == net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.OPEN) {
            pendingFlux.add((long) craft.feCost());
        } else if (level instanceof ServerLevel serverLevel) {
            emitCraftFlux(serverLevel, craft.feCost());
        }
        preferredRecipeId = craft.recipeId();
        List<ItemStack> outputs = craft.outputs();
        ItemStack handedOut = ItemStack.EMPTY;
        for (int i = 0; i < outputs.size(); i++) {
            if (i == handedOutIndex) {
                handedOut = outputs.get(i).copy();
                continue;
            }
            insertIntoOutputs(outputs.get(i).copy(), false);
        }

        // Continuous hopper craft must not rescan every MI recipe on each item. All of it from networks, nothing it
        // could make changed as it sees them (it sees their offers, worked out before): its batches are worked out
        // again in the background, not now, ahead of the next craft of a busy pull.
        if (allFromNetworks(craft)) queueRefresh(false);
        else rescaleCachedPreview();
        return handedOut;
    }

    /**
     * A craft its preview said it could make couldn't get what it needed after all (the network had moved on since its
     * offers were worked out). It isn't offered again this tick, or a busy pull would try it over and over, and its
     * batches are worked out again in the background.
     */
    private void failed(ResolvedCraft craft) {
        long now = level == null ? 0 : level.getGameTime();
        if (failedTick != now) {
            failedTick = now;
            failedNow.clear();
        }
        failedNow.add(craft.recipeId());
        queueRefresh(false);
    }

    /** Whether {@code craft} failed already this tick. */
    private boolean failedNow(ResolvedCraft craft) {
        return level != null && failedTick == level.getGameTime() && failedNow.contains(craft.recipeId());
    }

    private long failedTick = Long.MIN_VALUE;
    private final java.util.Set<Identifier> failedNow = new java.util.HashSet<>();

    /** Whether every ingredient of {@code craft} came through a link to a network's Materialiser (not its grid, not a chest). */
    private boolean allFromNetworks(ResolvedCraft craft) {
        if (level == null) return false;
        for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
            if (withdrawal.count() <= 0) continue;
            if (!(withdrawal.provenance() instanceof IngredientPool.Provenance.Remote remote)) return false;
            ItemStack link = inventory.getStackInSlot(remote.linkSlot());
            GlobalPos bound = EntangledLinks.isLink(link) ? link.get(com.kadikular.quantimium.init.ModDataComponents.BOUND_POS.get()) : null;
            if (bound == null || !bound.dimension().equals(level.dimension()) || !level.isLoaded(bound.pos())
                    || !(level.getBlockEntity(bound.pos()) instanceof LogisticsMaterialiserBlockEntity)) return false;
        }
        return true;
    }

    /** {@link #canCommitCached}, with what {@code craft} makes marked as being made: what it would draw on mustn't loop back to it. */
    private boolean canCommitMaking(ResolvedCraft craft, int craftIndex) {
        java.util.List<net.minecraft.world.item.Item> makes = outputItems(craft);
        com.kadikular.quantimium.recipe.CraftChain.startMaking(makes);
        try {
            return canCommitCached(craftIndex);
        } finally {
            com.kadikular.quantimium.recipe.CraftChain.stopMaking(makes);
        }
    }

    /** Whether {@code craft} uses something a craft up the chain is making now: it would loop back through that one. */
    private static boolean loopsUpChain(ResolvedCraft craft) {
        for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
            if (withdrawal.expected() != null && com.kadikular.quantimium.recipe.CraftChain.makingUpChain(withdrawal.expected())) return true;
        }
        return false;
    }

    private static java.util.List<net.minecraft.world.item.Item> outputItems(ResolvedCraft craft) {
        java.util.List<net.minecraft.world.item.Item> items = new ArrayList<>();
        for (ItemStack output : craft.outputs()) if (!output.isEmpty()) items.add(output.getItem());
        return items;
    }

    /** Take from a ghost output slot: commit the craft and return the output that slot was showing. */
    public ItemStack takeGhost(int absoluteOutputSlot) {
        GhostSlot ghost = ghostAt(absoluteOutputSlot);
        if (ghost == null) return ItemStack.EMPTY;
        return commitCraft(preview.get(ghost.craftIndex()), ghost.outputIndex());
    }

    /** The output slot previewing {@code item} that can make the most of it; -1 for none. */
    public int bestGhostFor(ItemStack item) {
        int best = -1;
        long most = 0;
        for (int slot = GHOST_BASE; slot < GHOST_BASE + ghostCount(); slot++) {
            GhostSlot ghost = ghostAt(slot);
            if (ghost == null || !ItemStack.isSameItemSameComponents(getGhost(slot), item)) continue;
            long can = Math.max(1, ghost.available());
            if (can > most) {
                most = can;
                best = slot;
            }
        }
        return best;
    }

    /**
     * Makes at least {@code wanted} of what an output slot previews, in as few runs as cover it (all a run makes
     * comes out, so a little more for a recipe that makes several), at most the previewed batch.
     */
    public ItemStack takeGhost(int absoluteOutputSlot, int wanted) {
        GhostSlot ghost = ghostAt(absoluteOutputSlot);
        if (ghost == null || wanted <= 0) return ItemStack.EMPTY;
        return commitCraft(runsFor(preview.get(ghost.craftIndex()), ghost.outputIndex(), wanted), ghost.outputIndex());
    }

    /**
     * What automation sees of a preview ({@link #GHOST_BASE} on): what a pull would commit. A hopper or pipe only
     * asks for what it can already see, so an unadvertised ghost never gets crafted.
     */
    private ItemStack automationView(int absoluteOutputSlot) {
        // Busy working out what it could make, it offers nothing; busy making something, it may make something else.
        if (level == null || level.isClientSide() || automationBusy && running.isEmpty() || com.kadikular.quantimium.recipe.CraftChain.full()
                || com.kadikular.quantimium.recipe.CraftChain.storedOnly()) return ItemStack.EMPTY;
        if (statusCode == STATUS_NO_POWER || statusCode == STATUS_ENTANGLED_LOOP
                || statusCode == STATUS_IDLE || statusCode == STATUS_NO_RECIPE
                || statusCode == STATUS_DENIED) {
            return ItemStack.EMPTY;
        }

        int local = absoluteOutputSlot - GHOST_BASE;
        int ghosts = ghostCount();
        if (local < 0 || local >= ghosts) return ItemStack.EMPTY;
        // Kept per chain (a machine on it offers nothing), while its contents stand, a tick at most, and not past a
        // settled transaction.
        long now = level.getGameTime();
        long epoch = com.kadikular.quantimium.recipe.CraftChain.epoch();
        long context = com.kadikular.quantimium.recipe.CraftChain.context();
        if (automationViewRevision != contentRevision || automationViewTick != now || automationViewEpoch != epoch) {
            automationViewCache.clear();
            automationViewRevision = contentRevision;
            automationViewTick = now;
            automationViewEpoch = epoch;
        }
        ItemStack[] views = automationViewCache.get(context);
        if (views != null && local < views.length && views[local] != null) return views[local];

        // Deciding whether a preview is committable can walk a link that leads back here; the guard
        // makes the second visit report nothing rather than recursing.
        boolean priorBusy = automationBusy;
        enterChain();
        ItemStack shown = ItemStack.EMPTY;
        try {
            GhostSlot ghost = ghostAt(absoluteOutputSlot);
            if (ghost != null) {
                ResolvedCraft craft = preview.get(ghost.craftIndex());
                if (!running.contains(craft.recipeId()) && !failedNow(craft) && !loopsUpChain(craft) && canCommitMaking(craft, ghost.craftIndex())) {
                    List<ItemStack> outputs = craft.outputs();
                    if (ghost.outputIndex() < outputs.size()) {
                        shown = outputs.get(ghost.outputIndex()).copy();
                    }
                }
            }
        } finally {
            exitChain(priorBusy);
        }
        if (!com.kadikular.quantimium.recipe.CraftChain.cacheable() || automationViewRevision != contentRevision || automationViewTick != now) return shown;
        ItemStack[] kept = automationViewCache.get(context);
        if (kept == null || kept.length != ghosts) automationViewCache.put(context, kept = new ItemStack[ghosts]);
        kept[local] = shown;
        return shown;
    }

    /**
     * Automation pull. Real items leave as usual; pulling a preview runs the recipe just enough times
     * to answer the request, so a hopper under the block keeps it working without emptying it.
     */
    private ItemStack extractForAutomation(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack shown = automationView(slot);
        if (shown.isEmpty()) return ItemStack.EMPTY;
        int handOver = Math.min(amount, shown.getCount());
        if (simulate) return shown.copyWithCount(handOver);

        GhostSlot ghost = ghostAt(slot);
        if (ghost == null) return ItemStack.EMPTY;
        // Run the recipe only as many times as this pull actually needs; a hopper taking one item
        // should not burn a stack of ingredients and the energy for it.
        ResolvedCraft craft = runsFor(preview.get(ghost.craftIndex()), ghost.outputIndex(), handOver);
        if (!hasRoomForRemainder(craft, ghost.outputIndex(), handOver)) return ItemStack.EMPTY;

        // automationView already verified the parent batch is committable; the trimmed craft only
        // spends less. Skip a second canCommit walk (especially costly across linked crafters).
        ItemStack produced = commitCraftTrusted(craft, ghost.outputIndex());
        if (produced.isEmpty()) return ItemStack.EMPTY;

        ItemStack handed = produced.copyWithCount(Math.min(handOver, produced.getCount()));
        ItemStack leftover = produced.copyWithCount(produced.getCount() - handed.getCount());
        if (!leftover.isEmpty()) insertIntoOutputs(leftover, false);
        return handed;
    }

    /** Trims a preview batch to the fewest whole runs that cover {@code wanted} of one output. */
    private static ResolvedCraft runsFor(ResolvedCraft batch, int outputIndex, int wanted) {
        int perRun = batch.perRunOutputCount(outputIndex);
        if (perRun <= 0) return batch;
        return batch.withRuns(Math.ceilDiv(wanted, perRun));
    }

    private boolean hasRoomForRemainder(ResolvedCraft craft, int handedOutIndex, int handOver) {
        List<ItemStack> outputs = craft.outputs();
        boolean anyRemainder = false;
        for (int i = 0; i < outputs.size(); i++) {
            ItemStack output = outputs.get(i);
            if (output.isEmpty()) continue;
            int store = i == handedOutIndex
                    ? Math.max(0, output.getCount() - handOver)
                    : output.getCount();
            if (store > 0) {
                anyRemainder = true;
                break;
            }
        }
        if (!anyRemainder) return true;

        ItemStack[] pretend = new ItemStack[OUTPUT_SLOTS];
        for (int local = 0; local < pretend.length; local++) {
            pretend[local] = inventory.getStackInSlot(OUTPUT_START + local).copy();
        }
        for (int i = 0; i < outputs.size(); i++) {
            ItemStack output = outputs.get(i);
            if (output.isEmpty()) continue;
            ItemStack toStore = i == handedOutIndex
                    ? output.copyWithCount(Math.max(0, output.getCount() - handOver))
                    : output;
            if (toStore.isEmpty()) continue;
            if (insertIntoOutputs(toStore, true, pretend) < toStore.getCount()) return false;
        }
        return true;
    }

    private int insertIntoOutputs(ItemStack stack, boolean simulate) {
        ItemStack[] pretend = null;
        if (simulate) {
            pretend = new ItemStack[OUTPUT_SLOTS];
            for (int local = 0; local < pretend.length; local++) {
                pretend[local] = inventory.getStackInSlot(OUTPUT_START + local).copy();
            }
        }
        return insertIntoOutputs(stack, simulate, pretend);
    }

    /** {@code pretend} accumulates simulated occupancy so several outputs can be tested together. */
    private int insertIntoOutputs(ItemStack stack, boolean simulate, @Nullable ItemStack[] pretend) {
        int remaining = stack.getCount();
        for (int slot = OUTPUT_START; slot <= OUTPUT_END && remaining > 0; slot++) {
            int local = slot - OUTPUT_START;
            ItemStack held = pretend == null ? inventory.getStackInSlot(slot) : pretend[local];
            if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) continue;
            int limit = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
            int used = held.isEmpty() ? 0 : held.getCount();
            int placed = Math.min(limit - used, remaining);
            if (placed <= 0) continue;
            if (simulate) {
                if (pretend != null) {
                    pretend[local] = held.isEmpty()
                            ? stack.copyWithCount(placed)
                            : held.copyWithCount(held.getCount() + placed);
                }
            } else if (held.isEmpty()) {
                inventory.setStackInSlot(slot, stack.copyWithCount(placed));
            } else {
                ItemStack grown = held.copy();
                grown.grow(placed);
                inventory.setStackInSlot(slot, grown);
            }
            remaining -= placed;
        }
        return stack.getCount() - remaining;
    }

    /** Parks a result that could not be handed directly to a player or automation target. */
    public void storeOutput(ItemStack stack) {
        if (!stack.isEmpty()) insertIntoOutputs(stack, false);
    }

    private void runAutoTransfers(Level level) {
        for (Direction side : Direction.values()) {
            if (!SIDE_AUTOMATION_PROFILE.supportsItems(side)) continue;
            SideConfig config = sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemInput() && !config.autoItemOutput()) continue;

            BlockPos neighborPos = worldPosition.relative(side);
            IItemHandler neighbor = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, neighborPos, side.getOpposite()));
            if (neighbor == null) continue;

            if (config.autoItemInput() && config.itemMode().allowsInput()) {
                pullOneItem(neighbor, config);
            }
            if (config.autoItemOutput() && config.itemMode().allowsOutput()) {
                // Prefer committing a ghost craft then pushing; otherwise push real outputs.
                if (!tryAutoCommitAndPush(neighbor, config)) {
                    pushOneItem(neighbor, config);
                }
            }
        }
    }

    private boolean tryAutoCommitAndPush(IItemHandler neighbor, SideConfig config) {
        if (preview.isEmpty()) return false;

        ResolvedCraft craft = null;
        int selectedIndex = -1;
        if (preferredRecipeId != null) {
            for (int i = 0; i < preview.size(); i++) {
                ResolvedCraft candidate = preview.get(i);
                if (candidate.recipeId().equals(preferredRecipeId) && primaryAllowedOn(i, config)) {
                    craft = candidate;
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (craft == null) {
            for (int i = 0; i < preview.size(); i++) {
                if (!primaryAllowedOn(i, config)) continue;
                craft = preview.get(i);
                selectedIndex = i;
                break;
            }
        }
        if (craft == null || selectedIndex < 0) return false;
        if (!canCommitCached(selectedIndex)) {
            if (energyStorage.getEnergyStored() < craft.feCost()) statusCode = STATUS_NO_POWER;
            return false;
        }

        // Craft for the room the neighbor actually has rather than insisting the whole batch fits.
        ItemStack primary = craft.primaryOutput();
        ItemStack leftover = ItemHandlerHelper.insertItem(neighbor, primary.copy(), true);
        int accepted = primary.getCount() - leftover.getCount();
        if (accepted <= 0) {
            statusCode = STATUS_OUTPUT_FULL;
            return false;
        }
        craft = runsFor(craft, 0, accepted);
        if (!hasRoomForRemainder(craft, 0, accepted)) {
            statusCode = STATUS_OUTPUT_FULL;
            return false;
        }

        ItemStack produced = commitCraft(craft, 0);
        if (produced.isEmpty()) return false;
        ItemStack remaining = ItemHandlerHelper.insertItem(neighbor, produced, false);
        if (!remaining.isEmpty()) {
            // Neighbor lied or raced us — park leftovers in our outputs.
            insertIntoOutputs(remaining, false);
        }
        // Also push any byproducts that landed in outputs.
        pushOneItem(neighbor, config);
        return true;
    }

    private boolean primaryAllowedOn(int craftIndex, SideConfig config) {
        return config.allowsOutputs();
    }

    private void pullOneItem(IItemHandler neighbor, SideConfig config) {
        for (int from = 0; from < neighbor.getSlots(); from++) {
            ItemStack extracted = neighbor.extractItem(from, 64, true);
            if (extracted.isEmpty()) continue;
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                if (!config.allowsInputSlot(slot)) continue;
                ItemStack leftover = inventory.insertItem(slot, extracted, true);
                int accepted = extracted.getCount() - leftover.getCount();
                if (accepted <= 0) continue;
                ItemStack taken = neighbor.extractItem(from, accepted, false);
                inventory.insertItem(slot, taken, false);
                return;
            }
        }
    }

    private void pushOneItem(IItemHandler neighbor, SideConfig config) {
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            if (!config.allowsOutputSlot(slot)) continue;
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack leftover = ItemHandlerHelper.insertItem(neighbor, stack.copy(), false);
            int moved = stack.getCount() - leftover.getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
                return;
            }
        }
    }

    private void sync() {
        setChanged();
        clientSyncDirty = true;
        flushClientSync();
    }

    private void flushClientSync() {
        if (!clientSyncDirty || level == null || level.isClientSide()) return;
        clientSyncDirty = false;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private final class SidedAutomationHandler implements IItemHandler, OnDemandSlots {
        @Nullable
        private final Direction side;

        private SidedAutomationHandler(@Nullable Direction side) {
            this.side = side;
        }

        private SideConfig config() {
            return side == null
                    ? new SideConfig(Direction.UP, SideMode.BOTH, SideConfig.ALL_ITEM_INPUTS,
                    SideConfig.ALL_ITEM_OUTPUTS, false, false, SideMode.DISABLED,
                    SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false)
                    : sideConfigs.get(side.get3DDataValue());
        }

        /** Its grid, its outputs, then one slot for each preview. */
        @Override
        public int getSlots() {
            return OUTPUT_END + 1 + ghostCount();
        }

        /** The preview a slot past the outputs shows ({@link #GHOST_BASE} on), or -1 for one that isn't. */
        private int ghostOf(int slot) {
            return slot > OUTPUT_END ? GHOST_BASE + slot - OUTPUT_END - 1 : -1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int ghost = ghostOf(slot);
            if (ghost >= 0) return config().allowsOutputs() ? automationView(ghost) : ItemStack.EMPTY;
            if (slot >= OUTPUT_START && slot <= OUTPUT_END) {
                return config().allowsOutputSlot(slot) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
            }
            ItemStack stack = inventory.getStackInSlot(slot);
            // A Tesseract in the grid is a link, not an ingredient or stock: networks and pipes don't list it.
            return slot < INPUT_SLOTS && com.kadikular.quantimium.recipe.EntangledLinks.isLink(stack) ? ItemStack.EMPTY : stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= INPUT_SLOTS) return stack;
            if (!config().allowsInputSlot(slot)) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int ghost = ghostOf(slot);
            if (ghost >= 0) return config().allowsOutputs() ? extractForAutomation(ghost, amount, simulate) : ItemStack.EMPTY;
            if (!config().allowsOutputSlot(slot)) return ItemStack.EMPTY;
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public Object saveState() {
            return new CraftState(energyStorage.getEnergyStored(), coherenceReserve, coherenceRefillProgress, feSpentThisTick, pendingFlux.size(),
                    preview, statusCode, preferredRecipeId, structureSignature, localCountSignature, remoteCountSignature, scaledAtEnergy);
        }

        @Override
        public void restoreState(@Nullable Object state) {
            if (!(state instanceof CraftState saved)) return;
            energyStorage.setEnergy(saved.energy());
            coherenceReserve = saved.coherence();
            coherenceRefillProgress = saved.refill();
            feSpentThisTick = saved.feSpent();
            while (pendingFlux.size() > saved.flux()) pendingFlux.removeLast();
            // What it could make, as it was: a craft undone leaves the preview it had before.
            preview = saved.preview();
            statusCode = saved.status();
            preferredRecipeId = saved.preferred();
            // Put back as they were, not read again: this runs while the transaction is closing, when nothing
            // may be asked of another inventory.
            structureSignature = saved.structure();
            localCountSignature = saved.localCount();
            remoteCountSignature = saved.remoteCount();
            scaledAtEnergy = saved.scaledAt();
            ghostLayout = null;
            contentRevision++;
            automationViewRevision = Long.MIN_VALUE;
            commitCheckRevision = Long.MIN_VALUE;
            setChanged();
        }

        @Override
        public void restoring(boolean restoring) {
            suppressInventoryNotify = restoring;
        }

        @Override
        public void committed() {
            flushFlux();
        }

        /** A slot past the outputs: pulling it crafts what its preview shows. */
        @Override
        public boolean producesOnDemand(int slot) {
            return slot > OUTPUT_END;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < INPUT_SLOTS && config().allowsInputSlot(slot);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(isBasic()
                ? "block.quantimium.basic_quantum_crafter"
                : "block.quantimium.quantum_crafter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (level != null && !level.isClientSide()) {
            // Worked out again in the background, first in the queue; meanwhile the screen shows what it had.
            queueRefresh(true);
            // Straight to the one opening it, ahead of the screen: sent with the level's other updates at the end of the
            // tick, the previews would arrive after the screen had opened empty.
            if (player instanceof net.minecraft.server.level.ServerPlayer server) server.connection.send(ClientboundBlockEntityDataPacket.create(this));
        }
        return new QuantumCrafterMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("StatusCode", statusCode);
        tag.putInt("Band", gate.band().ordinal());
        if (isBasic()) {
            tag.putInt("CoherenceReserve", coherenceReserve);
            tag.putInt("CoherenceRefillProgress", coherenceRefillProgress);
        }
        tag.putBoolean("RecipeLocked", recipeLocked);
        if (preferredRecipeId != null) {
            tag.putString("PreferredRecipe", preferredRecipeId.toString());
        }
        tag.put("SideConfigs", SideConfig.saveAll(sideConfigs));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Inventory")) {
            CompoundTag inventoryTag = tag.getCompoundOrEmpty("Inventory");
            if (inventoryTag.getIntOr("Size", 0) == LEGACY_TOTAL_SLOTS) {
                ItemStackHandler legacy = new ItemStackHandler(LEGACY_TOTAL_SLOTS);
                legacy.deserializeNBT(registries, inventoryTag);
                for (int slot = 0; slot <= LEGACY_OUTPUT_END; slot++) {
                    inventory.setStackInSlot(slot, legacy.getStackInSlot(slot).copy());
                }
                inventory.setStackInSlot(CATALYST_SLOT,
                        legacy.getStackInSlot(LEGACY_CATALYST_SLOT).copy());
            } else {
                inventory.deserializeNBT(registries, inventoryTag);
            }
        }
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        if (isBasic()) {
            coherenceReserve = tag.contains("CoherenceReserve")
                    ? Math.min(Config.basicCrafterCapacity(), tag.getIntOr("CoherenceReserve", 0))
                    : Config.basicCrafterCapacity();
            coherenceRefillProgress = tag.getIntOr("CoherenceRefillProgress", 0);
        }
        statusCode = tag.getIntOr("StatusCode", 0);
        FluxBand band = FluxBand.values()[Math.clamp(tag.getIntOr("Band", 0), 0, FluxBand.values().length - 1)];
        gate.set(band, band);
        recipeLocked = tag.getBooleanOr("RecipeLocked", false);
        preferredRecipeId = tag.contains("PreferredRecipe")
                ? Identifier.tryParse(tag.getStringOr("PreferredRecipe", ""))
                : null;
        sideConfigs = SIDE_AUTOMATION_PROFILE.sanitize(
                tag.contains("SideConfigs")
                        ? SideConfig.loadAll(tag.getListOrEmpty("SideConfigs"))
                        : SideConfig.defaults());
        structureSignature = Long.MIN_VALUE;
        localCountSignature = Long.MIN_VALUE;
        remoteCountSignature = Long.MIN_VALUE;
        localDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        // Ghost previews for the client GUI.
        ListTag ghosts = new ListTag();
        for (ResolvedCraft craft : preview) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", craft.recipeId().toString());
            entry.putInt("Fe", craft.feCost());
            entry.putInt("Batch", craft.batchSize());
            entry.putBoolean("Cap", craft.energyCapped());
            ListTag outputs = new ListTag();
            for (ItemStack output : craft.outputs()) {
                if (output.isEmpty()) continue;
                outputs.add(NbtCompat.saveStack(registries, output));
            }
            entry.put("Outs", outputs);
            ListTag withdrawals = new ListTag();
            for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
                CompoundTag w = new CompoundTag();
                switch (withdrawal.provenance()) {
                    case IngredientPool.Provenance.Local(int crafterSlot) -> {
                        w.putByte("T", (byte) 0);
                        w.putInt("S", crafterSlot);
                    }
                    case IngredientPool.Provenance.Remote(int linkSlot, int remoteSlot) -> {
                        w.putByte("T", (byte) 1);
                        w.putInt("S", linkSlot);
                        w.putInt("R", remoteSlot);
                    }
                }
                w.putInt("A", withdrawal.count());
                withdrawals.add(w);
            }
            entry.put("Withdrawals", withdrawals);
            ghosts.add(entry);
        }
        tag.put("Ghosts", ghosts);

        // The slot assignment travels with the previews. Letting the client derive its own from the
        // same inputs sounds equivalent, but any disagreement — a stack that will not round-trip, an
        // inventory update that lands a tick late — silently unlabels a ghost the player can see.
        ListTag slots = new ListTag();
        for (GhostSlot ghost : ghostLayout()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("C", ghost == null ? -1 : ghost.craftIndex());
            entry.putInt("O", ghost == null ? -1 : ghost.outputIndex());
            entry.putInt("Fe", ghost == null ? 0 : ghost.feCost());
            entry.putInt("Batch", ghost == null ? 1 : ghost.batchSize());
            entry.putBoolean("Cap", ghost != null && ghost.energyCapped());
            entry.putLong("Avail", ghost == null ? 0L : ghost.available());
            slots.add(entry);
        }
        tag.put("GhostSlots", slots);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        handleUpdateLegacy(NbtCompat.read(input), NbtCompat.lookup(input));
    }

    private void handleUpdateLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        loadLegacy(tag, registries);
        if (tag.contains("Ghosts")) {
            ListTag ghosts = tag.getListOrEmpty("Ghosts");
            List<ResolvedCraft> crafts = new ArrayList<>();
            for (int i = 0; i < ghosts.size(); i++) {
                CompoundTag entry = ghosts.getCompoundOrEmpty(i);
                Identifier id = Identifier.tryParse(entry.getStringOr("Id", ""));
                if (id == null) continue;
                List<ItemStack> outputs = new ArrayList<>();
                ListTag outputsTag = entry.getListOrEmpty("Outs");
                for (int o = 0; o < outputsTag.size(); o++) {
                    ItemStack out = NbtCompat.parseStack(registries, outputsTag.getCompoundOrEmpty(o));
                    if (!out.isEmpty()) outputs.add(out);
                }
                List<IngredientPool.Withdrawal> withdrawals = new ArrayList<>();
                if (entry.contains("Withdrawals")) {
                    ListTag withdrawalTag = entry.getListOrEmpty("Withdrawals");
                    for (int w = 0; w < withdrawalTag.size(); w++) {
                        CompoundTag wt = withdrawalTag.getCompoundOrEmpty(w);
                        int amount = wt.getIntOr("A", 0);
                        if (amount <= 0) continue;
                        if (wt.getByteOr("T", (byte) 0) == 1) {
                            withdrawals.add(new IngredientPool.Withdrawal(
                                    new IngredientPool.Provenance.Remote(wt.getIntOr("S", 0), wt.getIntOr("R", 0)),
                                    amount));
                        } else {
                            withdrawals.add(new IngredientPool.Withdrawal(
                                    new IngredientPool.Provenance.Local(wt.getIntOr("S", 0)), amount));
                        }
                    }
                } else if (entry.contains("Consumed")) {
                    // Legacy client sync from before withdrawals — local-only counts.
                    ListTag consumedTag = entry.getListOrEmpty("Consumed");
                    for (int s = 0; s < INPUT_SLOTS && s < consumedTag.size(); s++) {
                        int amount = consumedTag.getCompoundOrEmpty(s).getIntOr("A", 0);
                        if (amount > 0) {
                            withdrawals.add(new IngredientPool.Withdrawal(
                                    new IngredientPool.Provenance.Local(s), amount));
                        }
                    }
                }
                crafts.add(new ResolvedCraft(id, withdrawals, outputs, entry.getIntOr("Fe", 0),
                        entry.getIntOr("Batch", 0), entry.getBooleanOr("Cap", false)));
            }
            preview = Collections.unmodifiableList(crafts);
            ghostLayout = null;
        }
        if (tag.contains("GhostSlots")) {
            ListTag slots = tag.getListOrEmpty("GhostSlots");
            GhostSlot[] layout = new GhostSlot[slots.size()];
            for (int local = 0; local < layout.length; local++) {
                CompoundTag entry = slots.getCompoundOrEmpty(local);
                int craftIndex = entry.getIntOr("C", 0);
                int outputIndex = entry.getIntOr("O", 0);
                if (craftIndex < 0 || outputIndex < 0) continue;
                layout[local] = new GhostSlot(craftIndex, outputIndex,
                        entry.getIntOr("Fe", 0), entry.getIntOr("Batch", 0), entry.getBooleanOr("Cap", false),
                        entry.getLongOr("Avail", 0L));
            }
            ghostLayout = layout;
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * NeoForge routes live block entity packets through {@code loadWithComponents}, which only reads
     * what {@link #loadAdditional} understands — so previews would reach the client on chunk load and
     * never again, leaving ghosts the GUI could draw but not price.
     */
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CompoundTag tag = NbtCompat.read(input);
        if (!tag.isEmpty()) handleUpdateLegacy(tag, NbtCompat.lookup(input));
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return FluxMeterReadout.emitter(getAveragePowerUse());
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        for (int slot = 0; slot < TOTAL_SLOTS; slot++) {
            ItemStack stack = getInventory().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }
}
