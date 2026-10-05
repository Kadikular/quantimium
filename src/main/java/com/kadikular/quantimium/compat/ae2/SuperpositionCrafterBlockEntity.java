package com.kadikular.quantimium.compat.ae2;

import com.kadikular.quantimium.recipe.RecipeCompat;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;
import net.minecraft.world.item.crafting.Ingredient;
import com.kadikular.quantimium.recipe.FilterEntry;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import com.kadikular.quantimium.block.entity.QuantumEnergyHost;
import com.kadikular.quantimium.block.entity.QuantumEnergyStorage;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.recipe.CatalystResolver;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.recipe.RecipeShape;
import com.kadikular.quantimium.recipe.RecipeShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;

/**
 * The ME Superposition Crafter: every recipe its catalyst can run, offered to the ME network as
 * patterns nobody had to encode. When the network asks for a run, the ingredients it hands over are
 * used up and the result goes straight back into the network, which passes it to the crafting job
 * waiting on it. It has no grid and no preview; off a network it does nothing.
 *
 * <p>A run costs what the Quantum Crafter would charge for it, anomaly surcharge included, from the
 * crafter's own FE, and emits flux like any craft. Two filters shape what is offered, each of
 * {@value #FILTER_SLOTS} entries and each a whitelist or a blacklist: outputs (which recipes are
 * offered) and inputs (which items may be used up). An empty list filters nothing. An entry is an
 * item or one of its tags.
 */
public class SuperpositionCrafterBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost, ICraftingProvider, IActionHost, MenuProvider, QuantumEnergyHost,
                   FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 100_000_000;
    public static final int MAX_RECEIVE = 10_000_000;
    /** Recipe runs it takes in one tick; past this the network is told it is busy. FE is the real limit. */
    public static final int MAX_RUNS_PER_TICK = 1024;
    /** Recipe runs per pattern run: AE2 hands over one pattern run at a time, so this sets the pace. */
    public static final int[] BATCHES = {1, 2, 4, 8, 16, 32, 64};
    /** Entries in each of the two lists; the filter container holds outputs first, then inputs. */
    public static final int FILTER_SLOTS = 27;
    public static final int INPUT_FILTER_START = FILTER_SLOTS;

    public static final int MODE_ALLOW = 0;
    public static final int MODE_DENY = 1;

    public static final int STATUS_NO_CATALYST = 0;
    public static final int STATUS_OFFLINE = 1;
    public static final int STATUS_READY = 2;
    public static final int STATUS_WORKING = 3;
    public static final int STATUS_NO_POWER = 4;
    public static final int STATUS_BLOCKED = 5;
    /** The catalyst's tier needs a hotter field here, so it offers nothing (running hot pays). */
    public static final int STATUS_NEEDS_FLUX = 6;

    private static final IGridNodeListener<SuperpositionCrafterBlockEntity> LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(SuperpositionCrafterBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(SuperpositionCrafterBlockEntity owner, IGridNode node, State state) {
            owner.patternsDirty = true;
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, LISTENER)
            .setVisualRepresentation(Ae2Content.SUPERPOSITION_CRAFTER_ITEM.get())
            .setInWorldNode(true)
            .setTagName("node")
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setIdlePowerUsage(1.0)
            .addService(ICraftingProvider.class, this);

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    private final ItemStackHandler catalyst = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            patternsDirty = true;
            setChanged();
            // The catalyst is drawn inside the block, so clients need to see it change.
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Ae2Content.CATALYSTS);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    /** Ghost stacks, never real items: outputs to allow or deny, then inputs never to consume. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS * 2) {
        @Override
        public void setChanged() {
            super.setChanged();
            patternsDirty = true;
            SuperpositionCrafterBlockEntity.this.setChanged();
        }
    };

    private int filterMode = MODE_ALLOW;
    /** Input list starts as a blacklist: "never use these" is what it is usually for. */
    private int inputMode = MODE_DENY;
    private int syncedSurchargePercent;
    private int syncedAnomalyOrdinal;
    private int syncedPassiveDrain;
    private int batchIndex = 0;
    private List<SuperpositionPattern> patterns = List.of();
    private boolean patternsDirty = true;
    /** Results the network had no room for; while any wait here, the crafter is busy. */
    private final List<GenericStack> pending = new ArrayList<>();
    private int runsThisTick;
    private long feThisTick;
    private long unemittedFe;
    private int statusCode = STATUS_NO_CATALYST;
    /** The flux band here: which catalyst tiers work, and the instant-craft tax, as for the Quantum Crafter. */
    private final BandGate gate = new BandGate();
    /** The last run the network asked for could not be paid for; cleared by the next that can. */
    private boolean starved;
    private final int[] runSamples = new int[20];
    private final long[] feSamples = new long[20];
    private int sampleIndex;
    private int syncedRunsPerSecond;
    private int syncedAverageFe;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> (energyStorage.getEnergyStored() >> 16) & 0xFFFF;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energyStorage.getMaxEnergyStored() >> 16) & 0xFFFF;
                case 4 -> statusCode;
                case 5 -> filterMode;
                case 6 -> patterns.size();
                case 7 -> syncedRunsPerSecond;
                case 8 -> syncedAverageFe & 0xFFFF;
                case 9 -> (syncedAverageFe >>> 16) & 0xFFFF;
                case 10 -> batch();
                case 11 -> inputMode;
                case 12 -> syncedSurchargePercent;
                case 13 -> syncedAnomalyOrdinal;
                case 14 -> syncedPassiveDrain;
                case 15 -> gate.band().ordinal();
                case 16 -> gate.heading().ordinal();
                case 17 -> requiredBand().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return SuperpositionCrafterMenu.DATA_COUNT;
        }
    };

    public SuperpositionCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(Ae2Content.SUPERPOSITION_CRAFTER_BE.get(), pos, state);
    }

    // ---- accessors ----

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ItemStackHandler getCatalyst() {
        return catalyst;
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    public ContainerData getData() {
        return data;
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getFilterMode() {
        return filterMode;
    }

    public int batch() {
        return BATCHES[Math.clamp(batchIndex, 0, BATCHES.length - 1)];
    }

    public void setBatch(int batch) {
        for (int i = 0; i < BATCHES.length; i++) {
            if (BATCHES[i] == batch) batchIndex = i;
        }
        patternsDirty = true;
        setChanged();
    }

    public void cycleBatch() {
        batchIndex = (batchIndex + 1) % BATCHES.length;
        patternsDirty = true;
        setChanged();
    }

    public int getInputMode() {
        return inputMode;
    }

    public void setInputMode(int mode) {
        inputMode = mode == MODE_ALLOW ? MODE_ALLOW : MODE_DENY;
        patternsDirty = true;
        setChanged();
    }

    public void setFilterMode(int mode) {
        filterMode = mode == MODE_DENY ? MODE_DENY : MODE_ALLOW;
        patternsDirty = true;
        setChanged();
    }

    /** Sets entry {@code slot} (outputs 0-26, inputs 27-53) to plain {@code stack}, or clears it. */
    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= filter.getContainerSize()) return;
        filter.setItem(slot, FilterEntry.of(stack));
    }

    /** Shift-click: steps the entry through its item's tags and back. */
    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= filter.getContainerSize()) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, FilterEntry.cycle(entry));
    }

    /** The patterns on offer right now, rebuilt first if anything they depend on changed. */
    public List<SuperpositionPattern> patterns() {
        rebuildIfDirty();
        return patterns;
    }

    // ---- ME node ----

    @Override
    public void onLoad() {
        super.onLoad();
        GridHelper.onFirstTick(this, be -> be.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    // ---- crafting provider ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.copyOf(patterns());
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        if (!(details instanceof SuperpositionPattern pattern) || !mainNode.isActive()) return false;
        if (isBusy() || !patterns().contains(pattern) || !(level instanceof ServerLevel server)) return false;
        long cost = (long) costOf(pattern.shape(), server) * pattern.batch();
        if (runsThisTick + pattern.batch() > MAX_RUNS_PER_TICK && runsThisTick > 0) return false;
        if (energyStorage.getEnergyStored() < cost) {
            starved = true;
            return false;
        }
        starved = false;
        // The network has already taken the ingredients out of storage for us; they are simply used up.
        energyStorage.consume(cost);
        feThisTick += cost;
        runsThisTick += pattern.batch();
        // The results go to the network on this crafter's next tick, never from inside this call: AE2
        // only starts waiting for a run's results after this returns, and anything that arrives
        // before then goes into plain storage, stranding the job that asked for it.
        pending.addAll(pattern.getOutputs());
        setChanged();
        return true;
    }

    @Override
    public boolean isBusy() {
        return !pending.isEmpty() || runsThisTick >= MAX_RUNS_PER_TICK;
    }

    /**
     * What one run costs here and now: the Crafter's price, with this chunk's anomaly surcharge and
     * the instant-craft tax for the band here.
     */
    public int costOf(RecipeShape shape, ServerLevel level) {
        double multiplier = AnomalyEffects.feMultiplier(level, worldPosition) * Config.crafterTaxFraction(gate.band());
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(shape.baseFe() * multiplier)));
    }

    /** Whether the catalyst's tier needs a hotter field than the one here. */
    private boolean needsHotterField() {
        return gate.band().ordinal() < requiredBand().ordinal();
    }

    /** The band the catalyst in the slot needs; Low when there is none. */
    private FluxBand requiredBand() {
        ItemStack stack = catalyst.getStackInSlot(0);
        return stack.isEmpty() ? FluxBand.LOW : CatalystResolver.requiredBand(stack);
    }

    public BandGate bandGate() {
        return gate;
    }

    /** Hands waiting results to the network; whatever it cannot take waits for the next tick. */
    private void flush() {
        if (pending.isEmpty()) return;
        var grid = mainNode.getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(this);
        for (int i = 0; i < pending.size(); i++) {
            GenericStack stack = pending.get(i);
            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (inserted >= stack.amount()) {
                pending.remove(i--);
            } else if (inserted > 0) {
                pending.set(i, new GenericStack(stack.what(), stack.amount() - inserted));
            }
        }
    }

    private void rebuildIfDirty() {
        if (!patternsDirty || level == null || level.isClientSide()) return;
        patternsDirty = false;
        List<SuperpositionPattern> built = new ArrayList<>();
        // Below the catalyst's band it offers nothing, so the crafts drop out of the network's terminals.
        if (needsHotterField()) {
            patterns = List.of();
            ICraftingProvider.requestUpdate(mainNode);
            return;
        }
        for (RecipeShape shape : RecipeShapes.forCatalyst(level, catalyst.getStackInSlot(0))) {
            // A pattern can't keep a press between crafts: recipes with tools are the Reactor's alone.
            if (!shape.tools().isEmpty()) continue;
            if (!offers(shape.primaryOutput())) continue;
            RecipeShape allowed = withInputFilter(shape);
            if (allowed != null) built.add(new SuperpositionPattern(allowed, batch()));
        }
        patterns = List.copyOf(built);
        ICraftingProvider.requestUpdate(mainNode);
    }

    /** An empty output list offers everything; otherwise the output must (whitelist) or must not (blacklist) be listed. */
    private boolean offers(ItemStack output) {
        boolean empty = true;
        boolean listed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            empty = false;
            if (FilterEntry.matches(entry, output)) listed = true;
        }
        if (empty) return true;
        return filterMode == MODE_ALLOW ? listed : !listed;
    }

    /**
     * The shape with its ingredients cut down by the input list: a blacklist takes the listed items
     * out, a whitelist keeps only them. Null if an ingredient is left with nothing it accepts. Only
     * items go, not whole recipes: a recipe taking any log still takes birch when oak is blacklisted.
     */
    @Nullable
    private RecipeShape withInputFilter(RecipeShape shape) {
        List<ItemStack> listed = new ArrayList<>();
        for (int i = INPUT_FILTER_START; i < filter.getContainerSize(); i++) {
            if (!filter.getItem(i).isEmpty()) listed.add(filter.getItem(i));
        }
        if (listed.isEmpty()) return shape;
        boolean whitelist = inputMode == MODE_ALLOW;
        List<RecipeShape.Input> inputs = new ArrayList<>();
        boolean changed = false;
        for (RecipeShape.Input input : shape.inputs()) {
            List<ItemStack> kept = new ArrayList<>();
            for (ItemStack option : RecipeCompat.stacks(input.ingredient())) {
                boolean onList = listed.stream().anyMatch(entry -> FilterEntry.matches(entry, option));
                if (onList == whitelist) kept.add(option);
            }
            if (kept.isEmpty()) return null;
            if (kept.size() == RecipeCompat.stacks(input.ingredient()).size()) {
                inputs.add(input);
            } else {
                inputs.add(new RecipeShape.Input(Ingredient.of(kept.stream().map(ItemStack::getItem)), input.count()));
                changed = true;
            }
        }
        return changed ? new RecipeShape(shape.id(), inputs, shape.outputs(), shape.baseFe(), shape.tools()) : shape;
    }

    // ---- tick ----

    /** Reads the band here; a change in it can change what it may offer, so the patterns are rebuilt. */
    private void refreshBand(ServerLevel level, BlockPos pos) {
        FluxBand before = gate.band();
        gate.update(level, pos);
        if (gate.band() != before) {
            patternsDirty = true;
            setChanged();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SuperpositionCrafterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (server.getGameTime() % 20L == 0L) be.refreshBand(server, pos);
        be.rebuildIfDirty();
        be.flush();

        int leak = AnomalyEffects.passiveDrainFePerTick(server, pos);
        if (leak > 0) leak = be.energyStorage.consume(leak);
        be.syncedPassiveDrain = leak;
        be.syncedSurchargePercent = AnomalyEffects.surchargePercent(server, pos);
        be.syncedAnomalyOrdinal = QuantumFlux.chunk(server, pos).anomalyBand().ordinal();

        be.unemittedFe += be.feThisTick;
        if (server.getGameTime() % 20L == 0L && be.unemittedFe > 0) {
            QuantumFlux.emitFromEnergy(server, pos, be.unemittedFe);
            be.unemittedFe = 0;
        }

        be.runSamples[be.sampleIndex] = be.runsThisTick;
        be.feSamples[be.sampleIndex] = be.feThisTick;
        be.sampleIndex = (be.sampleIndex + 1) % be.runSamples.length;
        int runs = 0;
        long fe = 0;
        for (int i = 0; i < be.runSamples.length; i++) {
            runs += be.runSamples[i];
            fe += be.feSamples[i];
        }
        be.syncedRunsPerSecond = runs;
        be.syncedAverageFe = (int) Math.min(Integer.MAX_VALUE, fe / be.feSamples.length);

        if (be.catalyst.getStackInSlot(0).isEmpty()) be.statusCode = STATUS_NO_CATALYST;
        else if (be.needsHotterField()) be.statusCode = STATUS_NEEDS_FLUX;
        else if (!be.mainNode.isActive()) be.statusCode = STATUS_OFFLINE;
        else if (!be.pending.isEmpty()) be.statusCode = STATUS_BLOCKED;
        else if (be.starved) be.statusCode = STATUS_NO_POWER;
        else be.statusCode = runs > 0 ? STATUS_WORKING : STATUS_READY;
        be.runsThisTick = 0;
        be.feThisTick = 0;

        SuperpositionCrafterBlock.Link link = be.statusCode == STATUS_WORKING ? SuperpositionCrafterBlock.Link.ACTIVE
                : be.mainNode.isActive() ? SuperpositionCrafterBlock.Link.ONLINE : SuperpositionCrafterBlock.Link.OFFLINE;
        if (state.getValue(SuperpositionCrafterBlock.LINK) != link) {
            level.setBlock(pos, state.setValue(SuperpositionCrafterBlock.LINK, link), Block.UPDATE_CLIENTS);
        }
    }

    // ---- save ----

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        TagValueOutput nodeOut = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        mainNode.serialize(nodeOut);
        tag.merge(nodeOut.buildResult());
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.put("Catalyst", catalyst.serializeNBT(registries));
        // Slot by slot: the container's own tag drops empty slots, which would slide entries between lists.
        ListTag entries = new ListTag();
        for (int i = 0; i < filter.getContainerSize(); i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            CompoundTag saved = new CompoundTag();
            saved.putInt("Slot", i);
            saved.put("Item", NbtCompat.saveStack(registries, entry));
            entries.add(saved);
        }
        tag.put("FilterEntries", entries);
        tag.putInt("FilterMode", filterMode);
        tag.putInt("InputMode", inputMode);
        tag.putInt("Batch", batch());
        tag.putInt("Band", gate.band().ordinal());
        ListTag waiting = new ListTag();
        for (GenericStack stack : pending) waiting.add(NbtCompat.saveWith(GenericStack.CODEC, registries, stack));
        tag.put("Pending", waiting);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        mainNode.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        if (tag.contains("Catalyst")) catalyst.deserializeNBT(registries, tag.getCompoundOrEmpty("Catalyst"));
        filter.clearContent();
        for (Tag raw : tag.getListOrEmpty("FilterEntries")) {
            CompoundTag saved = (CompoundTag) raw;
            int slot = saved.getIntOr("Slot", 0);
            if (slot >= 0 && slot < filter.getContainerSize()) {
                filter.setItem(slot, NbtCompat.parseStack(registries, saved.getCompoundOrEmpty("Item")));
            }
        }
        filterMode = tag.getIntOr("FilterMode", 0) == MODE_DENY ? MODE_DENY : MODE_ALLOW;
        inputMode = tag.contains("InputMode") && tag.getIntOr("InputMode", 0) == MODE_ALLOW ? MODE_ALLOW : MODE_DENY;
        if (tag.contains("Batch")) setBatch(tag.getIntOr("Batch", 0));
        FluxBand band = FluxBand.values()[Math.clamp(tag.getIntOr("Band", 0), 0, FluxBand.values().length - 1)];
        gate.set(band, band);
        pending.clear();
        for (Tag entry : tag.getListOrEmpty("Pending")) {
            if (entry instanceof CompoundTag entryTag) {
                NbtCompat.parseWith(GenericStack.CODEC, registries, entryTag).ifPresent(pending::add);
            }
        }
        patternsDirty = true;
    }

    /** Clients only need the catalyst, to draw it; the rest (the node, energy, filters) stays server-side. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Catalyst", catalyst.serializeNBT(registries));
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        handleUpdateLegacy(NbtCompat.read(input), NbtCompat.lookup(input));
    }

    private void handleUpdateLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Catalyst")) catalyst.deserializeNBT(registries, tag.getCompoundOrEmpty("Catalyst"));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CompoundTag tag = NbtCompat.read(input);
        if (!tag.isEmpty()) handleUpdateLegacy(tag, NbtCompat.lookup(input));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.me_superposition_crafter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SuperpositionCrafterMenu(containerId, inventory, this, data);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return FluxMeterReadout.emitter(syncedAverageFe);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getCatalyst().getStackInSlot(0));
        }
    }
}
