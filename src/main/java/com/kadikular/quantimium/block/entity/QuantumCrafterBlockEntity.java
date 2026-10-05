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
        implements MenuProvider, SideConfigurable, QuantumEnergyHost, FluxMeterReadout {
    private static final SideAutomationProfile SIDE_AUTOMATION_PROFILE =
            new SideAutomationProfile(SideAutomationProfile.ALL_SIDES, 0);

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_SLOTS = CrafterPreview.MAX_OUTPUTS;
    public static final int OUTPUT_END = OUTPUT_START + OUTPUT_SLOTS - 1;
    public static final int CATALYST_SLOT = OUTPUT_END + 1;
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
    /** Recipe runs left before the early crafter must rebuild its coherence reserve. */
    private int coherenceReserve = 32;
    private int coherenceRefillProgress;

    @Nullable
    private GhostSlot[] ghostLayout;
    /** Per-tick cache of what automation already asked about, keyed by content revision. */
    private long automationViewRevision = Long.MIN_VALUE;
    @Nullable
    private ItemStack[] automationViewCache;
    /** Per-preview-entry commit answers, keyed by content revision. 0 unknown, 1 yes, 2 no. */
    private long commitCheckRevision = Long.MIN_VALUE;
    @Nullable
    private byte[] commitCheckCache;

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

    private static final GhostSlot[] NO_GHOSTS = new GhostSlot[OUTPUT_END - OUTPUT_START + 1];

    /**
     * Assigns every guaranteed output of a craft its own slot, so a centrifuge preview shows both the
     * aluminium and the chromium rather than hiding the byproduct until it lands. Crafts are laid out
     * in preview order over the slots not already holding real items; a craft whose outputs no longer
     * fit is skipped in favour of the next one that does.
     */
    private GhostSlot[] ghostLayout() {
        GhostSlot[] cached = ghostLayout;
        if (cached != null) return cached;
        // A layout the client works out for itself can name a different craft than the server will
        // run, which prices the tooltip off a craft the click never touches. Wait for the sync.
        if (level != null && level.isClientSide()) return NO_GHOSTS;

        GhostSlot[] layout = new GhostSlot[OUTPUT_END - OUTPUT_START + 1];
        List<Integer> free = new ArrayList<>(layout.length);
        for (int local = 0; local < layout.length; local++) {
            if (inventory.getStackInSlot(OUTPUT_START + local).isEmpty()) free.add(local);
        }

        int cursor = 0;
        for (int craftIndex = 0; craftIndex < preview.size() && cursor < free.size(); craftIndex++) {
            ResolvedCraft craft = preview.get(craftIndex);
            List<ItemStack> outputs = craft.outputs();
            int needed = 0;
            for (ItemStack output : outputs) {
                if (!output.isEmpty()) needed++;
            }
            if (needed == 0 || cursor + needed > free.size()) continue;
            for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
                if (outputs.get(outputIndex).isEmpty()) continue;
                layout[free.get(cursor++)] = new GhostSlot(craftIndex, outputIndex, craft.feCost(),
                        craft.batchSize(), craft.energyCapped(), craft.availableOutputCount(outputIndex));
            }
        }

        ghostLayout = layout;
        return layout;
    }

    /** The preview shown by an output slot, or null. Server-assigned, so both sides agree. */
    @Nullable
    public GhostSlot ghostAt(int absoluteOutputSlot) {
        int local = absoluteOutputSlot - OUTPUT_START;
        GhostSlot[] layout = ghostLayout();
        if (local < 0 || local >= layout.length) return null;
        GhostSlot ghost = layout[local];
        return ghost != null && ghost.craftIndex() < preview.size() ? ghost : null;
    }

    public ItemStack getGhost(int absoluteOutputSlot) {
        // Only show a ghost when the slot has no real items waiting.
        if (!inventory.getStackInSlot(absoluteOutputSlot).isEmpty()) return ItemStack.EMPTY;
        GhostSlot ghost = ghostAt(absoluteOutputSlot);
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

    @Nullable
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (isBasic()) return unsidedItemHandler;
        if (side == null) return unsidedItemHandler;
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

    private void serverTick(Level level, BlockState state) {
        long gameTime = level.getGameTime();
        replenishBasicCrafter();
        if (refreshAnomalyReadout()) localDirty = true;
        if (gameTime % 20L == 0L && refreshBand(level)) localDirty = true;
        if (localDirty || gameTime >= nextRemotePollTick) {
            nextRemotePollTick = gameTime + REMOTE_POLL_TICKS;
            refreshPreviewIfNeeded();
        } else {
            updateStatusFromPreview();
        }

        if (clientSyncDirty && gameTime % REMOTE_POLL_TICKS == 0L) {
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
            rebuildPreview();
            return;
        }

        boolean localGrew = localCounts > localCountSignature;
        boolean remoteGrew = remoteCounts > remoteCountSignature;
        boolean changed = localCounts != localCountSignature || remoteCounts != remoteCountSignature;
        localCountSignature = localCounts;
        remoteCountSignature = remoteCounts;
        if (!changed && !discoveryPending) {
            // Batches trimmed to fit the buffer have to grow back once it refills.
            if (energyWouldResizeBatches()) rescaleCachedPreview();
            else updateStatusFromPreview();
            return;
        }

        // Growth can unlock a recipe wanting more of an item we already had (nine ingots into a
        // block). Our own grid rescans at once so the GUI keeps up; a link into something busy like a
        // hopper would otherwise rescan every tick, so remote growth waits for the throttle.
        if (remoteGrew) discoveryPending = true;
        if (localGrew || (discoveryPending && gameTime >= nextDiscoveryTick)) {
            discoveryPending = false;
            nextDiscoveryTick = gameTime + DISCOVERY_TICKS;
            rebuildPreview();
            return;
        }

        if (preview.isEmpty()) {
            updateStatusFromPreview();
            return;
        }
        rescaleCachedPreview();
    }

    private boolean updateLinkLoopState() {
        if (level == null || level.isClientSide()) return false;
        boolean detected = EntangledLinks.hasUnsafeCrafterCycle(level,
                GlobalPos.of(level.dimension(), worldPosition), MAX_CHAIN_DEPTH);
        boolean changed = detected != linkLoopDetected;
        linkLoopDetected = detected;
        return changed;
    }

    public void rebuildPreview() {
        if (level == null || level.isClientSide()) return;

        updateLinkLoopState();
        ghostLayout = null;
        if (linkLoopDetected) {
            preview = List.of();
            statusCode = STATUS_ENTANGLED_LOOP;
            previewChanged();
            sync();
            return;
        }
        ItemStack catalyst = inventory.getStackInSlot(CATALYST_SLOT);
        if (catalyst.isEmpty()) {
            preview = List.of();
            statusCode = STATUS_IDLE;
            previewChanged();
            sync();
            return;
        }
        if (isCatalystDenied(catalyst)) {
            preview = List.of();
            statusCode = STATUS_DENIED;
            previewChanged();
            sync();
            return;
        }
        if (needsHotterField(catalyst)) {
            preview = List.of();
            statusCode = STATUS_NEEDS_FLUX;
            previewChanged();
            sync();
            return;
        }

        int availableFe = energyStorage.getEnergyStored();
        // The anomaly surcharge, times the instant-craft tax for the band here: a hot field crafts cheaper.
        double multiplier = AnomalyEffects.feMultiplier(level, worldPosition) * Config.crafterTaxFraction(gate.band());
        List<ResolvedCraft> crafts = previewEngine.preview(
                level, worldPosition, catalyst, inventory, INPUT_SLOTS, availableFe, multiplier);
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

        IngredientPool pool = IngredientPool.build(level, worldPosition, inventory, INPUT_SLOTS);
        int availableFe = energyStorage.getEnergyStored();
        List<ResolvedCraft> next = new ArrayList<>(preview.size());
        boolean stale = false;
        for (ResolvedCraft craft : preview) {
            if (!craft.runSatisfiedBy(pool)) {
                stale = true;
                continue;
            }
            ResolvedCraft resized = craft.withRuns(1).largestBatch(pool, availableFe);
            next.add(isBasic() ? limitToCoherence(resized) : resized);
        }

        if (next.isEmpty()) {
            // Cached recipes are spent; discover whatever else the grid can still make.
            rebuildPreview();
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
                rebuildPreview();
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
        // invalidating one another forever. Topology is re-checked every poll instead.
        return linkLoopDetected
                ? EntangledLinks.RemoteSummary.EMPTY
                : EntangledLinks.summarize(level, worldPosition, inventory, INPUT_SLOTS);
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
        if (!inventory.getStackInSlot(absoluteOutputSlot).isEmpty()) return true;
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
        if (commitCheckCache == null || commitCheckCache.length != preview.size()
                || commitCheckRevision != contentRevision) {
            commitCheckCache = new byte[preview.size()];
            commitCheckRevision = contentRevision;
        }
        byte cached = commitCheckCache[craftIndex];
        if (cached != 0) return cached == 1;
        boolean allowed = canCommit(preview.get(craftIndex));
        commitCheckCache[craftIndex] = (byte) (allowed ? 1 : 2);
        return allowed;
    }

    private boolean withdrawalsAvailable(ResolvedCraft craft) {
        for (IngredientPool.Withdrawal withdrawal : craft.withdrawals()) {
            if (withdrawal.count() <= 0) continue;
            if (!canWithdraw(withdrawal)) return false;
        }
        return true;
    }

    /** Simulated pull, so a source that only lets us look never counts as a source. */
    private boolean canWithdraw(IngredientPool.Withdrawal withdrawal) {
        return gatherWithdrawal(withdrawal, true).getCount() >= withdrawal.count();
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

        // A link can lead back here, so hide our own previews for the duration: a craft that ran
        // again halfway through itself would be working from inputs it had already spent.
        boolean priorBusy = automationBusy;
        automationBusy = true;
        suppressInventoryNotify = true;
        try {
            return commitWithdrawnCraft(craft, handedOutIndex);
        } finally {
            suppressInventoryNotify = false;
            automationBusy = priorBusy;
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
                rescaleCachedPreview();
                return ItemStack.EMPTY;
            }
            taken.add(withdrawal);
            takenStacks.add(pulled);
        }

        if (isBasic()) {
            coherenceReserve = Math.max(0, coherenceReserve - craft.batchSize());
        }
        energyStorage.consume(craft.feCost());
        if (craft.feCost() > 0) {
            feSpentThisTick = Math.min(Long.MAX_VALUE - feSpentThisTick, feSpentThisTick + craft.feCost());
        }
        if (level instanceof ServerLevel serverLevel) {
            if (craft.feCost() > 0) {
                QuantumFlux.emitFromEnergy(serverLevel, worldPosition, craft.feCost());
            } else {
                QuantumFlux.emitNonelectric(serverLevel, worldPosition);
            }
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

        // Continuous hopper craft must not rescan every MI recipe on each item.
        rescaleCachedPreview();
        return handedOut;
    }

    /** Take from a ghost output slot: commit the craft and return the output that slot was showing. */
    public ItemStack takeGhost(int absoluteOutputSlot) {
        GhostSlot ghost = ghostAt(absoluteOutputSlot);
        if (ghost == null) return ItemStack.EMPTY;
        return commitCraft(preview.get(ghost.craftIndex()), ghost.outputIndex());
    }

    /**
     * What automation sees in an output slot: real items, or the preview a pull would commit. A hopper
     * or pipe only asks for what it can already see, so an unadvertised ghost never gets crafted.
     */
    private ItemStack automationView(int absoluteOutputSlot) {
        ItemStack real = inventory.getStackInSlot(absoluteOutputSlot);
        if (!real.isEmpty()) return real;
        if (level == null || level.isClientSide() || automationBusy) return ItemStack.EMPTY;
        if (statusCode == STATUS_NO_POWER || statusCode == STATUS_ENTANGLED_LOOP
                || statusCode == STATUS_IDLE || statusCode == STATUS_NO_RECIPE
                || statusCode == STATUS_DENIED) {
            return ItemStack.EMPTY;
        }

        int local = absoluteOutputSlot - OUTPUT_START;
        if (local < 0 || local >= OUTPUT_SLOTS) return ItemStack.EMPTY;
        if (automationViewCache != null && automationViewRevision == contentRevision) {
            ItemStack cached = automationViewCache[local];
            if (cached != null) return cached;
        }

        // Deciding whether a preview is committable can walk a link that leads back here; the guard
        // makes the second visit report nothing rather than recursing.
        automationBusy = true;
        ItemStack shown = ItemStack.EMPTY;
        try {
            GhostSlot ghost = ghostAt(absoluteOutputSlot);
            if (ghost != null) {
                ResolvedCraft craft = preview.get(ghost.craftIndex());
                if (canCommitCached(ghost.craftIndex())) {
                    List<ItemStack> outputs = craft.outputs();
                    if (ghost.outputIndex() < outputs.size()) {
                        shown = outputs.get(ghost.outputIndex()).copy();
                    }
                }
            }
        } finally {
            automationBusy = false;
        }

        if (automationViewCache == null || automationViewRevision != contentRevision) {
            automationViewCache = new ItemStack[OUTPUT_SLOTS];
            automationViewRevision = contentRevision;
        }
        automationViewCache[local] = shown;
        return shown;
    }

    /**
     * Automation pull. Real items leave as usual; pulling a preview runs the recipe just enough times
     * to answer the request, so a hopper under the block keeps it working without emptying it.
     */
    private ItemStack extractForAutomation(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack real = inventory.getStackInSlot(slot);
        if (!real.isEmpty()) return inventory.extractItem(slot, amount, simulate);

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
        GhostSlot[] layout = ghostLayout();
        for (int local = 0; local < layout.length; local++) {
            GhostSlot ghost = layout[local];
            if (ghost != null && ghost.craftIndex() == craftIndex && ghost.outputIndex() == 0) {
                return config.allowsOutputSlot(OUTPUT_START + local);
            }
        }
        return false;
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

        @Override
        public int getSlots() {
            return OUTPUT_END + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot >= OUTPUT_START && slot <= OUTPUT_END && config().allowsOutputSlot(slot)) {
                return automationView(slot);
            }
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= INPUT_SLOTS) return stack;
            if (!config().allowsInputSlot(slot)) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!config().allowsOutputSlot(slot)) return ItemStack.EMPTY;
            return extractForAutomation(slot, amount, simulate);
        }

        /** An output slot with nothing real in it: pulling it crafts what its preview shows. */
        @Override
        public boolean producesOnDemand(int slot) {
            return slot >= OUTPUT_START && slot <= OUTPUT_END && inventory.getStackInSlot(slot).isEmpty();
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
        if (level != null && !level.isClientSide()) rebuildPreview();
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
            GhostSlot[] layout = new GhostSlot[OUTPUT_END - OUTPUT_START + 1];
            for (int local = 0; local < layout.length && local < slots.size(); local++) {
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
