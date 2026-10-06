package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.util.OnDemandSlots;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.block.MaterialiserBlock;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.MaterialiserMenu;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.kadikular.quantimium.unrealised.Materialising;
import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.unrealised.MatterSteps;
import com.kadikular.quantimium.unrealised.PackOres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

import java.util.List;
import java.util.function.Predicate;

/**
 * The Materialiser (game plan E4): chooses what Unrealised Matter becomes. The player picks one of the
 * pack's ores in one of the forms the Matter's history could make of it (raw, dust, ingot, plate…);
 * each cycle one Matter becomes that, as many as it makes on average, rounded down. What it can choose is capped by the flux band
 * where it stands, and each Matter costs Trace (see {@link Materialising}), flux from its own chunk,
 * and {@value #FE_PER_MATTER} FE. Trace is paid in whole items; what a Matter didn't use is kept as
 * credit towards the next, so nothing is wasted.
 *
 * <p>With no output chosen it works <b>on demand</b>: every form it could make now (all the band can
 * choose) is offered to automation as a slot after its own, and a pipe, import bus or storage bus that
 * pulls one has it made there and then, paid for as usual. A pull for less than one Matter makes gets
 * what it asked for, and the rest goes to the output slots. With an output chosen, automation sees
 * only the real slots, so a hopper under it collects the results rather than making things.
 *
 * <p>A bound Tesseract in the Matter or Trace slot reads the inventory it links to instead: the first
 * Matter there, and all of that history; all the Trace.
 *
 * <p>Slots follow the side configuration's layout: Matter in 0, Trace in 1, results in 9 to 11.
 */
public class MaterialiserBlockEntity extends BlockEntity
        implements MenuProvider, SideConfigurable, QuantumEnergyHost, FluxMeterReadout {

    public static final int MATTER_SLOT = 0;
    public static final int TRACE_SLOT = 1;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_END = 11;
    public static final int TOTAL_SLOTS = 12;

    public static final int CYCLE_TICKS = 20;
    public static final int FE_PER_MATTER = 4_000;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_RECEIVE = 4_000;
    private static final int AUTO_TRANSFER_INTERVAL = 20;
    private static final int FLUX_INTERVAL = 20;

    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_NO_TARGET = 2;
    public static final int STATUS_NEEDS_FLUX = 3;
    public static final int STATUS_CANT_BECOME = 4;
    public static final int STATUS_NO_TRACE = 5;
    public static final int STATUS_NO_POWER = 6;
    public static final int STATUS_THIN_FIELD = 7;
    public static final int STATUS_OUTPUT_FULL = 8;
    /** No output chosen, with Matter in: automation picks what it makes. */
    public static final int STATUS_ON_DEMAND = 9;
    /** Most Matter one pull may make at once. */
    private static final int MAX_RUNS_PER_PULL = 64;

    public static final int DATA_COUNT = 12;

    private static final SideAutomationProfile SIDE_AUTOMATION_PROFILE =
            new SideAutomationProfile(SideAutomationProfile.ALL_SIDES, 0);
    private static final SideConfig ALL_SLOTS = defaultSideConfigs().get(0);

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            if (slot == MATTER_SLOT || slot == TRACE_SLOT) sourcesDirty = true;
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // A bound Tesseract reads its linked inventory instead (see refreshSources).
            if (slot == MATTER_SLOT) return com.kadikular.quantimium.unrealised.Matter.is(stack) || EntangledLinks.isBound(stack);
            if (slot == TRACE_SLOT) return stack.is(ModItems.QUANTIMIUM_TRACE.get()) || EntangledLinks.isBound(stack);
            return slot >= OUTPUT_START && slot <= OUTPUT_END;
        }
    };

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    private final IItemHandler[] sidedHandlers = new IItemHandler[6];
    private final IItemHandler unsidedHandler = new SidedHandler(null);
    private List<SideConfig> sideConfigs = defaultSideConfigs();
    private final BandGate gate = new BandGate();
    /** The ore and form chosen; null for none. */
    @Nullable
    private Materialising.Target target;
    /** Trace paid for but not yet spent: a Matter rarely costs a whole one. */
    private double traceCredit;
    private int statusCode = STATUS_EMPTY;
    /** What it offers automation on demand: every form the band can choose for the Matter in it. */
    private List<Materialising.Option> catalog = List.of();
    private boolean catalogDirty = true;
    /**
     * The Matter it would work next, as many of that history as it can reach, and the Trace it can reach:
     * in its slots, or in the inventories a bound Tesseract in them links to. Read once a tick at most.
     */
    private ItemStack matterSeen = ItemStack.EMPTY;
    private int traceSeen;
    private boolean sourcesDirty = true;
    private long sourcesReadAt = -1L;
    private FluxBand catalogBand = FluxBand.LOW;
    private int progress;
    private long unemittedFe;
    private int feThisSecond;
    private int averageFe;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> (energyStorage.getEnergyStored() >>> 16) & 0xFFFF;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energyStorage.getMaxEnergyStored() >>> 16) & 0xFFFF;
                case 4 -> statusCode;
                case 5 -> progress;
                case 6 -> averageFe;
                case 7 -> level == null ? 0 : AnomalyEffects.surchargePercent(level, worldPosition);
                case 8 -> gate.band().ordinal();
                case 9 -> gate.heading().ordinal();
                case 10 -> (int) Math.round(traceCredit * 1000.0);
                case 11 -> targetRarity();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MaterialiserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATERIALISER_BE.get(), pos, state);
        for (Direction side : Direction.values()) sidedHandlers[side.get3DDataValue()] = new SidedHandler(side);
    }

    /** Every face takes Matter and Trace in and hands results out; nothing moves by itself until told to. */
    private static List<SideConfig> defaultSideConfigs() {
        return SIDE_AUTOMATION_PROFILE.sanitize(SideConfig.defaults().stream()
                .map(config -> new SideConfig(config.side(), SideMode.BOTH, SideConfig.ALL_ITEM_INPUTS,
                        SideConfig.ALL_ITEM_OUTPUTS, false, false, SideMode.DISABLED, 0, 0, false, false))
                .toList());
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ContainerData getData() {
        return data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public BandGate bandGate() {
        return gate;
    }

    @Nullable
    public Materialising.Target getTarget() {
        return target;
    }

    public void setTarget(@Nullable Materialising.Target target) {
        this.target = target;
        progress = 0;
        setChanged();
        // Choosing an output hides the catalogue from automation, and clearing it shows it again.
        invalidateCapabilities();
    }

    public double traceCredit() {
        return traceCredit;
    }

    /** The chosen ore's rarity (an ordinal), or -1. */
    private int targetRarity() {
        PackOres.Ore ore = target == null ? null : PackOres.byTag(target.ore());
        return ore == null ? -1 : ore.rarity().ordinal();
    }

    /** The history of the Matter it would work next. */
    public MatterHistory history() {
        return MatterHistory.of(matterSeen());
    }

    /** The Matter it would work next, as many of that history as it can reach. */
    public ItemStack matterSeen() {
        refreshSources();
        return matterSeen;
    }

    private int traceSeen() {
        refreshSources();
        return traceSeen;
    }

    /**
     * Reads what its two input slots hold, or what the inventories a bound Tesseract in them links to
     * hold: the first Matter found, and every Matter of the same history counted with it; all the
     * Trace. At most once a tick, unless a slot changed or it just spent some.
     */
    private void refreshSources() {
        if (level == null) return;
        long now = level.getGameTime();
        if (!sourcesDirty && sourcesReadAt == now) return;
        sourcesDirty = false;
        sourcesReadAt = now;
        ItemStack before = matterSeen;
        ItemStack matterSlot = inventory.getStackInSlot(MATTER_SLOT);
        if (EntangledLinks.isLink(matterSlot)) {
            IItemHandler linked = EntangledLinks.resolve(level, worldPosition, matterSlot);
            ItemStack first = ItemStack.EMPTY;
            int count = 0;
            if (linked != null) {
                for (int slot = 0; slot < linked.getSlots(); slot++) {
                    ItemStack stack = linked.getStackInSlot(slot);
                    if (!com.kadikular.quantimium.unrealised.Matter.is(stack)) continue;
                    if (first.isEmpty()) first = stack;
                    if (ItemStack.isSameItemSameComponents(first, stack)) count += stack.getCount();
                }
            }
            matterSeen = first.isEmpty() ? ItemStack.EMPTY : first.copyWithCount(count);
        } else {
            matterSeen = matterSlot.copy();
        }
        ItemStack traceSlot = inventory.getStackInSlot(TRACE_SLOT);
        if (EntangledLinks.isLink(traceSlot)) {
            IItemHandler linked = EntangledLinks.resolve(level, worldPosition, traceSlot);
            int count = 0;
            if (linked != null) {
                for (int slot = 0; slot < linked.getSlots(); slot++) {
                    ItemStack stack = linked.getStackInSlot(slot);
                    if (stack.is(ModItems.QUANTIMIUM_TRACE.get())) count += stack.getCount();
                }
            }
            traceSeen = count;
        } else {
            traceSeen = traceSlot.getCount();
        }
        // Another history means other forms to offer.
        if (!ItemStack.isSameItemSameComponents(before, matterSeen)) catalogDirty = true;
    }

    /** Takes {@code count} of the Matter it's working, from its slot or through the Tesseract; all or nothing. */
    private boolean takeMatter(int count) {
        ItemStack template = matterSeen();
        if (count <= 0 || template.getCount() < count) return count <= 0;
        ItemStack matterSlot = inventory.getStackInSlot(MATTER_SLOT);
        boolean done = EntangledLinks.isLink(matterSlot)
                ? takeLinked(matterSlot, stack -> ItemStack.isSameItemSameComponents(stack, template), count)
                : inventory.extractItem(MATTER_SLOT, count, false).getCount() == count;
        sourcesDirty = true;
        return done;
    }

    /** Takes {@code count} Trace, from its slot or through the Tesseract; all or nothing. */
    private boolean takeTrace(int count) {
        if (count <= 0) return true;
        if (traceSeen() < count) return false;
        ItemStack traceSlot = inventory.getStackInSlot(TRACE_SLOT);
        boolean done = EntangledLinks.isLink(traceSlot)
                ? takeLinked(traceSlot, stack -> stack.is(ModItems.QUANTIMIUM_TRACE.get()), count)
                : inventory.extractItem(TRACE_SLOT, count, false).getCount() == count;
        sourcesDirty = true;
        return done;
    }

    /** Takes {@code count} matching items from the inventory {@code link} links to, checked first so it's all or nothing. */
    private boolean takeLinked(ItemStack link, Predicate<ItemStack> matches, int count) {
        IItemHandler linked = EntangledLinks.resolve(level, worldPosition, link);
        if (linked == null) return false;
        int available = 0;
        for (int slot = 0; slot < linked.getSlots() && available < count; slot++) {
            ItemStack stack = linked.getStackInSlot(slot);
            if (matches.test(stack)) available += linked.extractItem(slot, count - available, true).getCount();
        }
        if (available < count) return false;
        int left = count;
        for (int slot = 0; slot < linked.getSlots() && left > 0; slot++) {
            if (matches.test(linked.getStackInSlot(slot))) left -= linked.extractItem(slot, left, false).getCount();
        }
        return left <= 0;
    }

    // ---- ticking ----

    public static void tick(Level level, BlockPos pos, BlockState state, MaterialiserBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (now % 20L == 0L) {
            be.gate.update(server, pos);
            if (be.gate.band() != be.catalogBand) be.catalogDirty = true;
        }
        int leak = AnomalyEffects.passiveDrainFePerTick(server, pos);
        if (leak > 0) be.energyStorage.consume(leak);
        if (now % AUTO_TRANSFER_INTERVAL == 0) be.runAutoTransfers(server);
        be.work(server);
        if (now % FLUX_INTERVAL == 0) {
            if (be.unemittedFe > 0) QuantumFlux.emitFromEnergy(server, pos, be.unemittedFe);
            be.unemittedFe = 0;
            be.averageFe = be.feThisSecond / FLUX_INTERVAL;
            be.feThisSecond = 0;
        }
    }

    /** A cycle's progress, then one Matter made into the chosen ore, if everything it needs is there. */
    private void work(ServerLevel level) {
        int status = readiness(level);
        statusCode = status;
        if (status != STATUS_WORKING) {
            progress = 0;
            light(level, false);
            return;
        }
        light(level, true);
        if (++progress < CYCLE_TICKS) return;
        progress = 0;
        materialise(level);
    }

    /** What stops it working now, or {@link #STATUS_WORKING}. Checks everything but the roll itself. */
    private int readiness(ServerLevel level) {
        ItemStack matter = matterSeen();
        if (matter.isEmpty()) return STATUS_EMPTY;
        if (target == null) return STATUS_ON_DEMAND;
        PackOres.Ore ore = PackOres.byTag(target.ore());
        if (ore == null) return STATUS_NO_TARGET;
        if (!Materialising.choosable(ore.rarity(), gate.band())) return STATUS_NEEDS_FLUX;
        if (Materialising.make(level, worldPosition, MatterHistory.of(matter), target).isEmpty()) return STATUS_CANT_BECOME;
        if (traceCredit + traceSeen() + 1e-9 < traceCost(ore)) return STATUS_NO_TRACE;
        if (energyStorage.getEnergyStored() < energyCost(level)) return STATUS_NO_POWER;
        if (QuantumFlux.chunkFlux(level, worldPosition) < Materialising.fluxCost(ore.rarity())) return STATUS_THIN_FIELD;
        return STATUS_WORKING;
    }

    private double traceCost(PackOres.Ore ore) {
        return Materialising.traceCost(ore.rarity(), gate.band());
    }

    private int energyCost(ServerLevel level) {
        return AnomalyEffects.scaleFe(FE_PER_MATTER, level, worldPosition);
    }

    /**
     * One Matter becomes the chosen ore in the chosen form: the same count every time, its average
     * rounded down, which is what the screen shows.
     */
    private void materialise(ServerLevel level) {
        PackOres.Ore ore = target == null ? null : PackOres.byTag(target.ore());
        if (ore == null || matterSeen().isEmpty()) return;
        ItemStack form = Materialising.make(level, worldPosition, history(), target);
        if (form.isEmpty()) {
            statusCode = STATUS_CANT_BECOME;
            return;
        }
        if (!fitsOutputs(List.of(form))) {
            statusCode = STATUS_OUTPUT_FULL;
            return;
        }
        if (!pay(level, ore, 1)) {
            statusCode = STATUS_THIN_FIELD;
            return;
        }
        insertIntoOutputs(form);
        setChanged();
    }

    /**
     * How many Matter it could make into {@code ore} now, as far as Matter, Trace, FE and the chunk's
     * flux go; at most {@code limit}.
     */
    private int affordable(ServerLevel level, PackOres.Ore ore, int limit) {
        int runs = Math.min(limit, matterSeen().getCount());
        double trace = traceCost(ore);
        if (trace > 0.0) {
            runs = Math.min(runs, (int) Math.floor((traceCredit + traceSeen()) / trace + 1e-9));
        }
        int energy = energyCost(level);
        if (energy > 0) runs = Math.min(runs, energyStorage.getEnergyStored() / energy);
        int flux = Materialising.fluxCost(ore.rarity());
        if (flux > 0) runs = Math.min(runs, (int) Math.floor(QuantumFlux.chunkFlux(level, worldPosition) / flux));
        return Math.max(0, runs);
    }

    /**
     * Pays for {@code runs} Matter made into {@code ore}: the flux first, taken from the chunk at once or
     * not at all, then the Trace (whole items, the rest kept as credit), FE and the Matter itself.
     */
    private boolean pay(ServerLevel level, PackOres.Ore ore, int runs) {
        double cost = traceCost(ore) * runs;
        int wholeTrace = (int) Math.ceil(Math.max(0.0, cost - traceCredit) - 1e-9);
        int energy = energyCost(level) * runs;
        if (matterSeen().getCount() < runs
                || traceSeen() < wholeTrace
                || energyStorage.getEnergyStored() < energy) {
            return false;
        }
        if (!QuantumFlux.tryConsumeChunkFlux(level, worldPosition, (double) Materialising.fluxCost(ore.rarity()) * runs, 0.0)) {
            return false;
        }
        // The flux is spent; if a linked chest changed under it since the check, the rest is still taken
        // as far as it goes rather than handing out something unpaid.
        if (!takeMatter(runs)) return false;
        takeTrace(wholeTrace);
        traceCredit = Math.max(0.0, traceCredit + wholeTrace - cost);
        energyStorage.consume(energy);
        unemittedFe += energy;
        feThisSecond += energy;
        return true;
    }

    // ---- on demand ----

    /** The forms offered on demand: none with an output chosen, since it's making that. */
    private List<Materialising.Option> catalog() {
        if (target != null || !(level instanceof ServerLevel server)) return List.of();
        refreshSources();
        if (catalogDirty) {
            catalogDirty = false;
            catalogBand = gate.band();
            ItemStack matter = matterSeen();
            catalog = matter.isEmpty() ? List.of()
                    : Materialising.options(server, worldPosition, MatterHistory.of(matter), catalogBand).stream()
                            .filter(Materialising.Option::allowed).toList();
        }
        return catalog;
    }

    /** What automation sees in on-demand slot {@code index}: the form, as many as it could make now. */
    private ItemStack onDemandView(int index) {
        List<Materialising.Option> offered = catalog();
        if (index < 0 || index >= offered.size() || !(level instanceof ServerLevel server)) return ItemStack.EMPTY;
        Materialising.Option option = offered.get(index);
        PackOres.Ore ore = PackOres.byTag(option.ore());
        if (ore == null) return ItemStack.EMPTY;
        int per = option.display().getCount();
        int runs = affordable(server, ore, Math.max(1, option.display().getMaxStackSize() / per));
        return runs <= 0 ? ItemStack.EMPTY : option.display().copyWithCount(Math.min(option.display().getMaxStackSize(), runs * per));
    }

    /**
     * A pull from on-demand slot {@code index}: as many Matter made into that form as it takes to cover
     * {@code amount}, what's over going to the output slots. Nothing if it can't pay for one.
     */
    private ItemStack extractOnDemand(int index, int amount, boolean simulate) {
        List<Materialising.Option> offered = catalog();
        if (amount <= 0 || index < 0 || index >= offered.size() || !(level instanceof ServerLevel server)) return ItemStack.EMPTY;
        Materialising.Option option = offered.get(index);
        PackOres.Ore ore = PackOres.byTag(option.ore());
        if (ore == null) return ItemStack.EMPTY;
        int per = option.display().getCount();
        int runs = affordable(server, ore, Math.min(MAX_RUNS_PER_PULL, Math.ceilDiv(amount, per)));
        if (runs <= 0) return ItemStack.EMPTY;
        int give = Math.min(amount, runs * per);
        int over = runs * per - give;
        // What a pull doesn't take must fit the outputs, or it makes one run fewer and gives what that makes.
        if (over > 0 && !fitsOutputs(List.of(option.display().copyWithCount(over)))) {
            runs = give / per;
            if (runs <= 0) return ItemStack.EMPTY;
            give = runs * per;
            over = 0;
        }
        if (simulate) return option.display().copyWithCount(give);
        if (!pay(server, ore, runs)) return ItemStack.EMPTY;
        if (over > 0) insertIntoOutputs(option.display().copyWithCount(over));
        setChanged();
        return option.display().copyWithCount(give);
    }

    private boolean fitsOutputs(List<ItemStack> products) {
        ItemStackHandler trial = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) trial.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        for (ItemStack product : products) {
            ItemStack remaining = product.copy();
            for (int slot = OUTPUT_START; slot <= OUTPUT_END && !remaining.isEmpty(); slot++) {
                remaining = trial.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private void insertIntoOutputs(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = OUTPUT_START; slot <= OUTPUT_END && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
        }
    }

    private void light(ServerLevel level, boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(MaterialiserBlock.LIT) && state.getValue(MaterialiserBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(MaterialiserBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** What it could make of the Matter in it, for the screen. */
    public List<Materialising.Option> options(ServerLevel level) {
        ItemStack matter = matterSeen();
        MatterHistory history = matter.isEmpty() ? MatterHistory.NONE : MatterHistory.of(matter);
        return Materialising.options(level, worldPosition, history, gate.band());
    }

    // ---- automation ----

    /** Once a second, each face told to pulls Matter or Trace in, or pushes a stack of results out. */
    private void runAutoTransfers(ServerLevel level) {
        for (Direction side : Direction.values()) {
            SideConfig config = sideConfigs.get(side.get3DDataValue());
            if (!config.autoItemInput() && !config.autoItemOutput()) continue;
            IItemHandler neighbour = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(side),
                    side.getOpposite()));
            if (neighbour == null) continue;
            if (config.autoItemInput() && config.itemMode().allowsInput()) pull(neighbour, config);
            if (config.autoItemOutput() && config.itemMode().allowsOutput()) push(neighbour, config);
        }
    }

    private void pull(IItemHandler from, SideConfig config) {
        for (int source = 0; source < from.getSlots(); source++) {
            ItemStack offered = from.extractItem(source, 64, true);
            if (offered.isEmpty()) continue;
            for (int slot = MATTER_SLOT; slot <= TRACE_SLOT; slot++) {
                if (!config.allowsInputSlot(slot) || !inventory.isItemValid(slot, offered)) continue;
                int accepted = offered.getCount() - inventory.insertItem(slot, offered, true).getCount();
                if (accepted <= 0) continue;
                inventory.insertItem(slot, from.extractItem(source, accepted, false), false);
                return;
            }
        }
    }

    private void push(IItemHandler into, SideConfig config) {
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            if (!config.allowsOutputSlot(slot)) continue;
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            int moved = stack.getCount() - ItemHandlerHelper.insertItem(into, stack.copy(), false).getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
                return;
            }
        }
    }

    /** What a pipe or hopper on {@code side} sees: Matter and Trace in, results out, where the face allows. */
    @Nullable
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return unsidedHandler;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.itemMode() == SideMode.DISABLED ? null : sidedHandlers[side.get3DDataValue()];
    }

    private final class SidedHandler implements IItemHandler, OnDemandSlots {
        @Nullable
        private final Direction side;

        private SidedHandler(@Nullable Direction side) {
            this.side = side;
        }

        private SideConfig config() {
            return side == null ? ALL_SLOTS : sideConfigs.get(side.get3DDataValue());
        }

        /** Its own slots, then on demand one slot per form it offers, if this face hands things out. */
        @Override
        public int getSlots() {
            return TOTAL_SLOTS + (config().itemMode().allowsOutput() ? catalog().size() : 0);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot >= TOTAL_SLOTS) return config().itemMode().allowsOutput() ? onDemandView(slot - TOTAL_SLOTS) : ItemStack.EMPTY;
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot > TRACE_SLOT || !config().allowsInputSlot(slot)) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= TOTAL_SLOTS) {
                return config().itemMode().allowsOutput() ? extractOnDemand(slot - TOTAL_SLOTS, amount, simulate) : ItemStack.EMPTY;
            }
            if (slot < OUTPUT_START || !config().allowsOutputSlot(slot)) return ItemStack.EMPTY;
            return inventory.extractItem(slot, amount, simulate);
        }

        /** The catalogue's slots: pulling one makes Matter into that form, paying Matter, Trace and FE. */
        @Override
        public boolean producesOnDemand(int slot) {
            return slot >= TOTAL_SLOTS;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= TOTAL_SLOTS ? 64 : inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot <= TRACE_SLOT && config().allowsInputSlot(slot) && inventory.isItemValid(slot, stack);
        }
    }

    // ---- side configuration ----

    @Override
    public List<SideConfig> getSideConfigs() {
        return List.copyOf(sideConfigs);
    }

    public List<SideConfig> sideConfigsView() {
        return sideConfigs;
    }

    @Override
    public void applySideConfigs(List<SideConfig> configs) {
        sideConfigs = SIDE_AUTOMATION_PROFILE.sanitize(configs);
        setChanged();
        invalidateCapabilities();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public SideAutomationProfile sideAutomationProfile() {
        return SIDE_AUTOMATION_PROFILE;
    }

    @Override
    public boolean isUsableBy(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    // ---- readouts, menu and saving ----

    @Override
    public MutableComponent fluxMeterLine() {
        return FluxMeterReadout.emitter(averageFe);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.materialiser");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MaterialiserMenu(containerId, playerInventory, this, data);
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
        tag.putInt("Progress", progress);
        tag.putDouble("TraceCredit", traceCredit);
        if (target != null) {
            tag.putString("TargetOre", target.ore().toString());
            tag.putString("TargetForm", target.form().toString());
        }
        tag.putInt("Band", gate.band().ordinal());
        tag.put("SideConfigs", SideConfig.saveAll(sideConfigs));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        progress = tag.getIntOr("Progress", 0);
        traceCredit = tag.getDoubleOr("TraceCredit", 0.0);
        Identifier ore = Identifier.tryParse(tag.getStringOr("TargetOre", ""));
        Identifier form = Identifier.tryParse(tag.getStringOr("TargetForm", ""));
        target = tag.contains("TargetOre") && ore != null && form != null ? new Materialising.Target(ore, form) : null;
        FluxBand band = FluxBand.values()[Math.clamp(tag.getIntOr("Band", 0), 0, FluxBand.values().length - 1)];
        gate.set(band, band);
        if (tag.contains("SideConfigs")) {
            sideConfigs = SIDE_AUTOMATION_PROFILE.sanitize(SideConfig.loadAll(tag.getListOrEmpty("SideConfigs")));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("SideConfigs", SideConfig.saveAll(sideConfigs));
        return tag;
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        for (int slot = 0; slot < getInventory().getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getInventory().getStackInSlot(slot));
        }
    }
}
