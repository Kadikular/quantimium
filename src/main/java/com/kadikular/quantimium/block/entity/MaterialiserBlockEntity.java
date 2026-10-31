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
 * where it stands, and each Matter costs Anomalite Dust (see {@link Materialising}), flux from its own chunk,
 * and {@value #FE_PER_MATTER} FE. Anomalite Dust is paid in whole items; what a Matter didn't use is kept as
 * credit towards the next, so nothing is wasted.
 *
 * <p>With no output chosen it works <b>on demand</b>: every form it could make now (all the band can
 * choose) is offered to automation as a slot after its own, and a pipe, import bus or storage bus that
 * pulls one has it made there and then, paid for as usual. A pull for less than one Matter makes gets
 * what it asked for, and the rest goes to the output slots. With an output chosen, automation sees
 * only the real slots, so a hopper under it collects the results rather than making things.
 *
 * <p>A bound Tesseract in the Matter or Anomalite Dust slot reads the inventory it links to instead: the first
 * Matter there, and all of that history; all the Anomalite Dust.
 *
 * <p>Slots follow the side configuration's layout: Matter in 0, Anomalite Dust in 1, results in 9 to 11.
 */
public class MaterialiserBlockEntity extends BlockEntity
        implements MenuProvider, SideConfigurable, QuantumEnergyHost, FluxMeterReadout, com.kadikular.quantimium.laser.OfferMaker {

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
            if (slot == TRACE_SLOT) return Materialising.pays(stack) || EntangledLinks.isBound(stack);
            // The old output slots only hold what a world saved before the buffer; nothing goes in.
            return false;
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
    /** Asked with no face (viewers, other mods' screens): only what's really there, nothing made on a pull. */
    private final IItemHandler viewerHandler = new com.kadikular.quantimium.util.OnDemandSlots.RealOnly(unsidedHandler);
    private List<SideConfig> sideConfigs = defaultSideConfigs();
    private final BandGate gate = new BandGate();
    /** The form chosen for export (what pipes and hoppers see, and its faces push out); null for none. */
    @Nullable
    private Materialising.Target target;
    /**
     * What it made that wasn't taken: one Matter makes a whole roll's worth (three raw copper), and a take of one leaves
     * two here. Taken from first. Kept by item; no limit.
     */
    private final List<ItemStack> buffer = new java.util.ArrayList<>();
    /** Anomalite Dust paid for but not yet spent: a Matter rarely costs a whole one. */
    private double traceCredit;
    private int statusCode = STATUS_EMPTY;
    /** What it offers automation on demand: every form the band can choose for the Matter in it. */
    private List<Materialising.Option> catalog = List.of();
    private boolean catalogDirty = true;
    /**
     * The Matter it would work next, as many of that history as it can reach, and the Anomalite Dust it can reach:
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

    /** Every face takes Matter and Anomalite Dust in and hands results out; nothing moves by itself until told to. */
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
        // What pipes see is the export form: a new slot, or none.
        invalidateCapabilities();
    }

    // ---- the buffer ----

    /** How many like {@code like} wait in the buffer. */
    public int buffered(ItemStack like) {
        int count = 0;
        for (ItemStack stack : buffer) if (ItemStack.isSameItemSameComponents(stack, like)) count += stack.getCount();
        return count;
    }

    /** Takes up to {@code count} like {@code like} out of the buffer. */
    private ItemStack takeBuffered(ItemStack like, int count) {
        int got = 0;
        for (var it = buffer.iterator(); it.hasNext() && got < count; ) {
            ItemStack stack = it.next();
            if (!ItemStack.isSameItemSameComponents(stack, like)) continue;
            int take = Math.min(stack.getCount(), count - got);
            stack.shrink(take);
            got += take;
            if (stack.isEmpty()) it.remove();
        }
        if (got > 0) setChanged();
        return got == 0 ? ItemStack.EMPTY : like.copyWithCount(got);
    }

    /** Puts {@code stack} into the buffer. */
    public void addBuffered(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack there : buffer) {
            if (ItemStack.isSameItemSameComponents(there, stack)) {
                there.grow(stack.getCount());
                setChanged();
                return;
            }
        }
        buffer.add(stack.copy());
        setChanged();
    }

    /** Whether {@code stack} is something it makes, and so may go back into its buffer: a form of an ore it could choose. */
    public boolean takesBack(ItemStack stack) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) return false;
        for (Materialising.Option option : options(server)) if (ItemStack.isSameItemSameComponents(option.display(), stack)) return true;
        return buffered(stack) > 0;
    }

    /** The export form's option, if one is chosen and it may make it now. */
    @Nullable
    private Materialising.Option exportOption() {
        if (target == null) return null;
        for (Materialising.Option option : catalog()) if (target.is(option.ore(), option.display())) return option;
        return null;
    }

    /** How many of {@code option} it has or could make now: its buffer, then what its Matter, dust, FE and field pay for. */
    public long available(Materialising.Option option) {
        if (!(level instanceof ServerLevel server)) return 0;
        PackOres.Ore ore = PackOres.byTag(option.ore());
        long made = ore == null ? 0 : (long) affordable(server, ore, Integer.MAX_VALUE) * option.display().getCount();
        return buffered(option.display()) + made;
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
     * Anomalite Dust. At most once a tick, unless a slot changed or it just spent some; through a Tesseract, at most
     * once a second (a linked network is a whole network's worth of looking).
     */
    private void refreshSources() {
        if (level == null) return;
        long now = level.getGameTime();
        boolean throughLink = EntangledLinks.isLink(inventory.getStackInSlot(MATTER_SLOT)) || EntangledLinks.isLink(inventory.getStackInSlot(TRACE_SLOT));
        if (!sourcesDirty && (sourcesReadAt == now || throughLink && now - sourcesReadAt < 20)) return;
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
                    if (Materialising.pays(stack)) count += stack.getCount();
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

    /** Takes {@code count} Anomalite Dust, from its slot or through the Tesseract; all or nothing. */
    private boolean takeTrace(int count) {
        if (count <= 0) return true;
        if (traceSeen() < count) return false;
        ItemStack traceSlot = inventory.getStackInSlot(TRACE_SLOT);
        boolean done = EntangledLinks.isLink(traceSlot)
                ? takeLinked(traceSlot, stack -> Materialising.pays(stack), count)
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
        // Out of every transaction now: nothing left to give back.
        if (net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle() == net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.NONE) be.spentFlux.clear();
        if (now % AUTO_TRANSFER_INTERVAL == 0) be.runAutoTransfers(server);
        be.work(server);
        if (now % FLUX_INTERVAL == 0) {
            if (be.unemittedFe > 0) QuantumFlux.emitFromEnergy(server, pos, be.unemittedFe);
            be.unemittedFe = 0;
            be.averageFe = be.feThisSecond / FLUX_INTERVAL;
            be.feThisSecond = 0;
        }
    }

    /** What stops it, said on its screen: it makes only on demand, so this is whether the export form could be made. */
    private void work(ServerLevel level) {
        int status = readiness(level);
        statusCode = status;
        light(level, status == STATUS_WORKING || status == STATUS_ON_DEMAND && !matterSeen().isEmpty());
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
     * How many Matter it could make into {@code ore} now, as far as Matter, Anomalite Dust, FE and the chunk's
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
     * not at all, then the Anomalite Dust (whole items, the rest kept as credit), FE and the Matter itself.
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
        double fluxSpent = (double) Materialising.fluxCost(ore.rarity()) * runs;
        if (!QuantumFlux.tryConsumeChunkFlux(level, worldPosition, fluxSpent, 0.0)) {
            return false;
        }
        // Inside a transaction (a pull that may yet be aborted), remember it, to give back if it is.
        if (net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle() == net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.OPEN) {
            spentFlux.add(fluxSpent);
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

    /** The forms it may make now, offered on demand to a logistics network (pipes see only the export form). */
    private List<Materialising.Option> catalog() {
        if (!(level instanceof ServerLevel server)) return List.of();
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

    /** What automation sees in on-demand slot {@code index} of {@code offered}: the form, as many as it has or could make now. */
    private ItemStack view(List<Materialising.Option> offered, int index) {
        if (index < 0 || index >= offered.size() || !(level instanceof ServerLevel)) return ItemStack.EMPTY;
        Materialising.Option option = offered.get(index);
        long count = Math.min(option.display().getMaxStackSize(), available(option));
        return count <= 0 ? ItemStack.EMPTY : option.display().copyWithCount((int) count);
    }

    /**
     * Makes {@code amount} of {@code want} for a player at its screen, chosen output or not: as many Matter as it
     * takes, what's over going to the output slots. Nothing if the form isn't one it may make now or it can't pay.
     */
    public ItemStack makeFor(Materialising.Target want, int amount) {
        if (!(level instanceof ServerLevel server)) return ItemStack.EMPTY;
        refreshSources();
        for (Materialising.Option option : options(server)) {
            if (option.allowed() && want.is(option.ore(), option.display())) return make(option, amount, false);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Up to {@code amount} of {@code option}: from the buffer first, then as many Matter made into it as it takes to
     * cover the rest (at most {@value #MAX_RUNS_PER_PULL} at once), what a run makes over going into the buffer.
     */
    private ItemStack make(Materialising.Option option, int amount, boolean simulate) {
        if (amount <= 0 || !(level instanceof ServerLevel server)) return ItemStack.EMPTY;
        PackOres.Ore ore = PackOres.byTag(option.ore());
        if (ore == null) return ItemStack.EMPTY;
        int per = option.display().getCount();
        int had = Math.min(amount, buffered(option.display()));
        int runs = had >= amount ? 0 : affordable(server, ore, Math.min(MAX_RUNS_PER_PULL, Math.ceilDiv(amount - had, per)));
        int give = Math.min(amount, had + runs * per);
        if (give <= 0) return ItemStack.EMPTY;
        if (simulate) return option.display().copyWithCount(give);
        if (runs > 0 && !pay(server, ore, runs)) return ItemStack.EMPTY;
        ItemStack got = takeBuffered(option.display(), had);
        int over = had + runs * per - give;
        if (over > 0) addBuffered(option.display().copyWithCount(over));
        setChanged();
        return option.display().copyWithCount(give);
    }

    // ---- making for a logistics network ----

    /**
     * Behind a provider Receptacle: each form it may make now, as many as its buffer holds and its Matter, Anomalite
     * Dust, FE and the field pay for. It makes from its own slots (or what their Tesseracts read), never from the
     * network's stock.
     */
    @Override
    public List<com.kadikular.quantimium.laser.OfferMaker.Made> makes(com.kadikular.quantimium.laser.LogisticsController network,
                                                                       java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> stock,
                                                                       boolean fromCraftable) {
        List<com.kadikular.quantimium.laser.OfferMaker.Made> made = new java.util.ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> from = java.util.Set.of(ModItems.UNREALISED_MATTER.get(), ModItems.ANOMALITE_DUST.get());
        for (Materialising.Option option : catalog()) {
            long count = available(option);
            if (count > 0) made.add(new com.kadikular.quantimium.laser.OfferMaker.Made(
                    net.neoforged.neoforge.transfer.item.ItemResource.of(option.display()), count, from));
        }
        return made;
    }

    /** The slots past its own are what it makes on demand. */
    @Override
    public boolean makesOnDemand(int slot) {
        return slot >= TOTAL_SLOTS;
    }

    /** Nothing in its own slots is on offer as it is: its Matter and dust are what it makes from. */
    @Override
    public boolean stocks(int slot) {
        return false;
    }

    @Override
    public ItemStack peekOnDemand(int slot) {
        List<Materialising.Option> offered = catalog();
        int index = slot - TOTAL_SLOTS;
        return index < 0 || index >= offered.size() ? ItemStack.EMPTY : offered.get(index).display().copy();
    }

    /** What a provider Receptacle on {@code side} sees: every form it may make, not only the export form pipes see. */
    public IItemHandler networkHandler(Direction side) {
        return networkHandlers[side.get3DDataValue()];
    }

    private final IItemHandler[] networkHandlers = {new SidedHandler(Direction.DOWN, true), new SidedHandler(Direction.UP, true),
            new SidedHandler(Direction.NORTH, true), new SidedHandler(Direction.SOUTH, true), new SidedHandler(Direction.WEST, true),
            new SidedHandler(Direction.EAST, true)};

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

    /** Once a second, each face told to pulls Matter or Anomalite Dust in, or pushes a stack of results out. */
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

    /** Pushes a stack of the export form into {@code into}: from the buffer, then made, as much as it takes. */
    private void push(IItemHandler into, SideConfig config) {
        Materialising.Option option = exportOption();
        if (option == null) return;
        ItemStack trial = option.display().copyWithCount(option.display().getMaxStackSize());
        int fits = trial.getCount() - ItemHandlerHelper.insertItem(into, trial, true).getCount();
        if (fits <= 0) return;
        ItemStack made = make(option, fits, false);
        ItemStack left = ItemHandlerHelper.insertItem(into, made, false);
        addBuffered(left);
    }

    /** What a pipe or hopper on {@code side} sees: Matter and Anomalite Dust in, results out, where the face allows. */
    @Nullable
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return viewerHandler;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.itemMode() == SideMode.DISABLED ? null : sidedHandlers[side.get3DDataValue()];
    }

    /** Flux pulls in an open transaction spent, to give back if it aborts. */
    private final List<Double> spentFlux = new java.util.ArrayList<>();

    /** What a pull changes besides the inventory, for an aborted transaction to put back. */
    private record PullState(int energy, double traceCredit, long unemittedFe, int feThisSecond, int flux, List<ItemStack> buffer) {}

    private final class SidedHandler implements IItemHandler, OnDemandSlots {
        @Override
        public Object saveState() {
            return new PullState(energyStorage.getEnergyStored(), traceCredit, unemittedFe, feThisSecond, spentFlux.size(),
                    buffer.stream().map(ItemStack::copy).toList());
        }

        @Override
        public void restoreState(@Nullable Object state) {
            if (!(state instanceof PullState saved)) return;
            energyStorage.setEnergy(saved.energy());
            traceCredit = saved.traceCredit();
            unemittedFe = saved.unemittedFe();
            feThisSecond = saved.feThisSecond();
            buffer.clear();
            for (ItemStack stack : saved.buffer()) buffer.add(stack.copy());
            while (spentFlux.size() > saved.flux()) {
                double flux = spentFlux.removeLast();
                if (level instanceof ServerLevel server) QuantumFlux.refundChunkFlux(server, worldPosition, flux);
            }
            catalogDirty = true;
            setChanged();
        }

        @Override
        public void committed() {
            spentFlux.clear();
        }

        @Nullable
        private final Direction side;
        /** A logistics network's view: every form it may make. A pipe's: only the export form. */
        private final boolean network;

        private SidedHandler(@Nullable Direction side) {
            this(side, false);
        }

        private SidedHandler(@Nullable Direction side, boolean network) {
            this.side = side;
            this.network = network;
        }

        /** The forms this view offers on demand, in slot order. */
        private List<Materialising.Option> offered() {
            if (network) return catalog();
            Materialising.Option export = exportOption();
            return export == null ? List.of() : List.of(export);
        }

        private SideConfig config() {
            return side == null ? ALL_SLOTS : sideConfigs.get(side.get3DDataValue());
        }

        /** Its own slots, then on demand one slot per form it offers, if this face hands things out. */
        @Override
        public int getSlots() {
            return TOTAL_SLOTS + (config().itemMode().allowsOutput() ? offered().size() : 0);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot >= TOTAL_SLOTS) return config().itemMode().allowsOutput() ? view(offered(), slot - TOTAL_SLOTS) : ItemStack.EMPTY;
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
                List<Materialising.Option> offered = offered();
                int index = slot - TOTAL_SLOTS;
                return config().itemMode().allowsOutput() && index < offered.size() ? make(offered.get(index), amount, simulate) : ItemStack.EMPTY;
            }
            if (slot < OUTPUT_START || !config().allowsOutputSlot(slot, OUTPUT_START)) return ItemStack.EMPTY;
            return inventory.extractItem(slot, amount, simulate);
        }

        /** The catalogue's slots: pulling one makes Matter into that form, paying Matter, Anomalite Dust and FE. */
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
        MachineAccess.sidesChanged(this);
    }

    @Override
    public SideAutomationProfile sideAutomationProfile() {
        return SIDE_AUTOMATION_PROFILE;
    }

    @Override
    public boolean isUsableBy(Player player) {
        return MachineAccess.withinReach(this, player);
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
        ItemStack.CODEC.listOf().encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), List.copyOf(buffer))
                .result().ifPresent(list -> tag.put("Buffer", list));
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
        buffer.clear();
        if (tag.get("Buffer") != null) {
            ItemStack.CODEC.listOf().parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get("Buffer"))
                    .result().ifPresent(list -> list.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> buffer.add(stack.copy())));
        }
        // A world saved with output slots: what was in them waits in the buffer now.
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            ItemStack old = inventory.getStackInSlot(slot);
            if (old.isEmpty()) continue;
            addBuffered(old.copy());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
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
        for (ItemStack stack : buffer) {
            // Dropped a stack at a time.
            int left = stack.getCount();
            while (left > 0) {
                int n = Math.min(left, stack.getMaxStackSize());
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copyWithCount(n));
                left -= n;
            }
        }
    }
}
