package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.block.QuantumObservationChamberBlock;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.QuantumObservationChamberMenu;
import com.kadikular.quantimium.unrealised.MatterHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

/**
 * The Quantum Observation Chamber: the mid-tier chamber. It collapses Unrealised Matter on its own,
 * {@value #BATCH} every {@value #CYCLE_TICKS} ticks while it has power and room, paying
 * {@value #FE_PER_COLLAPSE} FE a collapse (plus the chunk's anomaly surcharge) and emitting flux for
 * it like any machine. Faces are configured like the crafter's and simulator's: which let Matter in,
 * which hand results out, and which push and pull by themselves.
 *
 * <p>Slots follow the side configuration's layout: Matter in 0 to 2, the collapse results in 9 to 16,
 * and Quantimium Trace in 17, the last output position, so a face can be set to hand out Trace alone.
 */
public class QuantumObservationChamberBlockEntity extends BlockEntity
        implements MenuProvider, SideConfigurable, QuantumEnergyHost, FluxMeterReadout {

    public static final int INPUT_SLOTS = 3;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_END = 16;
    public static final int TRACE_SLOT = 17;
    public static final int TOTAL_SLOTS = 18;

    public static final int CYCLE_TICKS = 10;
    public static final int BATCH = 4;
    public static final int FE_PER_COLLAPSE = 400;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_RECEIVE = 4_000;
    private static final int AUTO_TRANSFER_INTERVAL = 20;
    private static final int FLUX_INTERVAL = 20;

    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_OUTPUT_FULL = 2;
    public static final int STATUS_NO_POWER = 3;

    public static final int DATA_COUNT = 9;

    /** Every face carries items; nothing here holds fluids. */
    private static final SideAutomationProfile SIDE_AUTOMATION_PROFILE =
            new SideAutomationProfile(SideAutomationProfile.ALL_SIDES, 0);
    /** Every slot open, for the unsided automation view. */
    private static final SideConfig ALL_SLOTS = defaultSideConfigs().get(0);

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < INPUT_SLOTS) return com.kadikular.quantimium.unrealised.Matter.is(stack);
            if (slot == TRACE_SLOT) return stack.is(ModItems.QUANTIMIUM_TRACE.get());
            // Result slots take whatever a collapse makes; the menu and faces refuse insertion.
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
    private boolean voidExcessTrace = true;
    private int statusCode = STATUS_EMPTY;
    private int progress;
    private long unemittedFe;
    private int feThisSecond;
    private int averageFe;
    private int litTicks;

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
                case 6 -> voidExcessTrace ? 1 : 0;
                case 7 -> averageFe;
                case 8 -> level == null ? 0 : AnomalyEffects.surchargePercent(level, worldPosition);
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

    public QuantumObservationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_OBSERVATION_CHAMBER_BE.get(), pos, state);
        for (Direction side : Direction.values()) sidedHandlers[side.get3DDataValue()] = new SidedHandler(side);
    }

    /** Every face takes Matter in and hands results out; nothing moves by itself until a face is told to. */
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

    public boolean isVoidExcessTrace() {
        return voidExcessTrace;
    }

    public void toggleVoidExcessTrace() {
        voidExcessTrace = !voidExcessTrace;
        setChanged();
    }

    // ---- ticking ----

    public static void tick(Level level, BlockPos pos, BlockState state, QuantumObservationChamberBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
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
        if (be.litTicks > 0 && --be.litTicks == 0) be.light(server, pos, false);
    }

    /** A cycle's worth of progress, then as many collapses as power and room allow. */
    private void work(ServerLevel level) {
        if (matterCount() == 0) {
            statusCode = STATUS_EMPTY;
            progress = 0;
            return;
        }
        int cost = AnomalyEffects.scaleFe(FE_PER_COLLAPSE, level, worldPosition);
        if (energyStorage.getEnergyStored() < cost) {
            statusCode = STATUS_NO_POWER;
            return;
        }
        if (!room()) {
            statusCode = STATUS_OUTPUT_FULL;
            return;
        }
        statusCode = STATUS_WORKING;
        if (++progress < CYCLE_TICKS) return;
        progress = 0;
        int done = 0;
        while (done < BATCH && matterCount() > 0 && energyStorage.getEnergyStored() >= cost && room()) {
            ObservationChamberBlockEntity.Collapse collapse = ObservationChamberBlockEntity.rollCollapse(level,
                    worldPosition, MatterHistory.of(inventory.getStackInSlot(nextMatterSlot())));
            List<ItemStack> products = collapse.products();
            if (!fitsOutputs(products)) {
                statusCode = STATUS_OUTPUT_FULL;
                break;
            }
            for (ItemStack product : products) insertIntoOutputs(product, false);
            takeMatter();
            // A history that broke leaves a Trace, as the chance of one does; a full slot voids it.
            if (collapse.broke()) inventory.insertItem(TRACE_SLOT, new ItemStack(ModItems.QUANTIMIUM_TRACE.get()), false);
            energyStorage.consume(cost);
            unemittedFe += cost;
            feThisSecond += cost;
            if (level.getRandom().nextFloat() < ObservationChamberBlockEntity.TRACE_CHANCE) {
                // A full slot voids it; with voiding off, room() held the batch back before this.
                inventory.insertItem(TRACE_SLOT, new ItemStack(ModItems.QUANTIMIUM_TRACE.get()), false);
            }
            done++;
        }
        if (done > 0) {
            litTicks = CYCLE_TICKS + 2;
            light(level, worldPosition, true);
            setChanged();
        }
    }

    /** Somewhere for a result, and for its Trace unless excess Trace is voided. */
    private boolean room() {
        boolean resultRoom = false;
        for (int slot = OUTPUT_START; slot <= OUTPUT_END && !resultRoom; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            resultRoom = stack.isEmpty() || stack.getCount() < stack.getMaxStackSize();
        }
        return resultRoom && (voidExcessTrace || ObservationChamberBlockEntity.traceHasRoom(inventory, TRACE_SLOT));
    }

    private int matterCount() {
        int count = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) count += inventory.getStackInSlot(slot).getCount();
        return count;
    }

    private void takeMatter() {
        inventory.extractItem(nextMatterSlot(), 1, false);
    }

    /** The input slot the next Matter comes from: the first that has any. */
    private int nextMatterSlot() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) return slot;
        }
        return 0;
    }

    /** Whether all of {@code products} fit the result slots together. */
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

    private int insertIntoOutputs(ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = OUTPUT_START; slot <= OUTPUT_END && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, simulate);
        }
        return stack.getCount() - remaining.getCount();
    }

    private void light(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(QuantumObservationChamberBlock.LIT) && state.getValue(QuantumObservationChamberBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(QuantumObservationChamberBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // ---- automation ----

    /** Once a second, each face told to pulls a stack of Matter in or pushes a stack of results out. */
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
            if (offered.isEmpty() || !com.kadikular.quantimium.unrealised.Matter.is(offered)) continue;
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                if (!config.allowsInputSlot(slot)) continue;
                int accepted = offered.getCount() - inventory.insertItem(slot, offered, true).getCount();
                if (accepted <= 0) continue;
                inventory.insertItem(slot, from.extractItem(source, accepted, false), false);
                return;
            }
        }
    }

    private void push(IItemHandler into, SideConfig config) {
        for (int slot = OUTPUT_START; slot <= TRACE_SLOT; slot++) {
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

    /**
     * What a pipe or hopper on {@code side} sees: Matter in where the face allows, results out likewise.
     * With no side (a Quantum Simulator, a mod's own lookup) it is every face at once. That view still
     * refuses the result slots: they take anything from a collapse, and offering them would have the
     * simulator put Matter where the chamber never looks for it.
     */
    @Nullable
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return unsidedHandler;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.itemMode() == SideMode.DISABLED ? null : sidedHandlers[side.get3DDataValue()];
    }

    private final class SidedHandler implements IItemHandler {
        @Nullable
        private final Direction side;

        private SidedHandler(@Nullable Direction side) {
            this.side = side;
        }

        /** The face's settings, or every slot allowed for the unsided view. */
        private SideConfig config() {
            return side == null ? ALL_SLOTS : sideConfigs.get(side.get3DDataValue());
        }

        @Override
        public int getSlots() {
            return TOTAL_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= INPUT_SLOTS || !config().allowsInputSlot(slot)) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < OUTPUT_START || !config().allowsOutputSlot(slot)) return ItemStack.EMPTY;
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < INPUT_SLOTS && config().allowsInputSlot(slot) && inventory.isItemValid(slot, stack);
        }
    }

    // ---- side configuration ----

    @Override
    public List<SideConfig> getSideConfigs() {
        return List.copyOf(sideConfigs);
    }

    /** Read-only, for the wrench's port indicators. */
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
        return Component.translatable("block.quantimium.quantum_observation_chamber");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuantumObservationChamberMenu(containerId, playerInventory, this, data);
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
        tag.putBoolean("VoidExcessTrace", voidExcessTrace);
        tag.putInt("Progress", progress);
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
        voidExcessTrace = !tag.contains("VoidExcessTrace") || tag.getBooleanOr("VoidExcessTrace", false);
        progress = tag.getIntOr("Progress", 0);
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
