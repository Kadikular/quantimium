// Path: src/main/java/com/kadikular/quantimium/block/entity/QuantumSimulatorBlockEntity.java
package com.kadikular.quantimium.block.entity;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import com.kadikular.quantimium.util.LegacyFluids;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.QuantumSimulatorBlock;
import com.kadikular.quantimium.block.entity.simulation.FluidMapping;
import com.kadikular.quantimium.block.entity.simulation.MachineSlotInfo;
import com.kadikular.quantimium.block.entity.simulation.MachineTankInfo;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.block.entity.simulation.SimulatedEffect;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.block.entity.simulation.SlotMapping;
import com.kadikular.quantimium.block.entity.simulation.VirtualMachineMirror;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.menu.QuantumSimulatorMenu;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class QuantumSimulatorBlockEntity extends BlockEntity
        implements MenuProvider, SideConfigurable, QuantumEnergyHost, SimulatedEffect, FluxMeterReadout {
    private static final SideAutomationProfile SIDE_AUTOMATION_PROFILE = new SideAutomationProfile(
            SideAutomationProfile.sidesExcept(Direction.UP),
            SideAutomationProfile.sidesExcept(Direction.UP));

    private static final Identifier NESTED_SIMULATION_ADVANCEMENT =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "simulation_within_simulation");
    private static final int VISUALIZATION_DEPTH = 2;

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_END = 17;
    public static final int TARGET_DISPLAY_SLOT = 18;
    public static final int TOTAL_SLOTS = 19;

    public static final int MODE_PARALLEL = 1;
    /** @deprecated Recipe mode moved to Quantum Crafter; kept for legacy NBT only. */
    @Deprecated
    public static final int MODE_RECIPE = 0;

    // Status Codes:
    // 0 = OFFLINE, 1 = WORKING, 2 = IDLE / NO INPUTS, 3 = NO RECIPE, 4 = RECIPE_FAILED (MI custom class),
    // 5 = NO POWER, 6 = OUTPUT FULL, 7 = UNSUPPORTED TARGET, 8 = NO INPUT SLOTS, 9 = INPUTS TOO LOW,
    // 10 = TARGET UNPOWERED, 11 = INITIALISING, 12 = NOT DRIVEN BY BLOCK TICKS
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_IDLE = 2;
    public static final int STATUS_NO_RECIPE = 3;
    public static final int STATUS_RECIPE_FAILED = 4;
    public static final int STATUS_OUTPUT_FULL = 6;
    public static final int STATUS_UNSUPPORTED = 7;

    private boolean isEngaged = false;
    private boolean isVirtualTicking = false;
    private int progress = 0;
    private int maxProgress = 100;
    private int compostLevel = 0;

    private int speedMultiplier = 1;
    /** The batch chosen in the GUI; it runs at most as large as the band allows ({@link #getBatchSize}). */
    private int batchSize = 2;
    /** The flux band here, which sets the largest batch that runs (running hot pays). */
    private final BandGate gate = new BandGate();
    private int runningBatch = 2;
    private int currentPowerUse;
    private int averagePowerUse;
    private int syncedAnomalyOrdinal;
    private int syncedSurchargePercent;
    private int syncedPassiveDrain;
    private final int[] powerSamples = new int[20];
    private int powerSampleIndex;

    // Simulation Settings: 0 = RECIPE MODE, 1 = PARALLEL MODE
    private int simulationMode = MODE_PARALLEL;
    private int statusCode = STATUS_OFFLINE;

    /** Ticks the panels stay lit after the last piece of work, to ride out gaps between crafts. */
    private static final int ACTIVE_HOLD_TICKS = 20;

    /**
     * How often the client is told about tank movement. A boiler moves fluid every single tick, and
     * every block update re-serializes the whole inventory, so sending one per tick cost more than
     * running the simulation did. Four a second is well past what the bars need to look continuous.
     */
    private static final int CLIENT_SYNC_TICKS = 5;
    private int activeHoldTicks;
    /** Something the client draws has moved, and a block update is owed once the throttle allows one. */
    private boolean clientSyncDirty;
    /**
     * Whether this is the simulator the player placed, rather than a copy of one ticking inside
     * another simulator's containment field. A copy has no client to talk to and no business
     * reaching into the world around the field, so both are skipped for it.
     */
    private boolean worldResident = true;

    private BlockState containedBlockState = null;
    private CompoundTag containedBlockNBT = null;
    @Nullable
    private ContainedVisualization containedVisualization = null;
    private List<SlotMapping> slotMappings = SlotMapping.defaults(INPUT_SLOTS);
    private List<FluidMapping> fluidMappings = FluidMapping.defaults(SimulatorFluidTanks.INPUT_TANKS);
    private List<SideConfig> sideConfigs = SideConfig.defaults();
    /** Block the current mappings were made for, so they survive the field being toggled off and on. */
    @Nullable
    private String slotMappingTarget = null;
    /** Mappings were detected while the machine still held its own items, so slot roles may be wrong. */
    private boolean mappingsFromDirtyProbe = false;

    /** The contained machine as it exists in RAM. Rebuilt from NBT on demand, never saved directly. */
    @Nullable
    private VirtualMachineMirror mirror;
    @Nullable
    private PhantomMirrorEngine engine;
    /** The engine a rewind retired, kept only long enough to hand its measurements to its successor. */
    @Nullable
    private PhantomMirrorEngine retiredEngine;
    private boolean mirrorUnsupported = false;
    /** The machine as it stood mid-simulation, copies and all, waiting to be picked back up. */
    @Nullable
    private CompoundTag pendingLiveMachineNbt;
    /** The bookkeeping that says which of the copies in {@link #pendingLiveMachineNbt} belong to whom. */
    @Nullable
    private CompoundTag pendingEngineState;
    private boolean needsEviction = true;
    /** The engine has dead-ended on copies the machine will not release and wants a clean rebuild. */
    private boolean machineNeedsRewind = false;
    private boolean containmentLost = false;

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == TARGET_DISPLAY_SLOT) return false;
            return !isInputSlotLocked(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final SimulatorFluidTanks fluidTanks = new SimulatorFluidTanks(this::onFluidChanged);
    private final IFluidHandler[] sidedFluidHandlers = new IFluidHandler[6];
    private final IFluidHandler unsidedFluidHandler = fluidTanks.unsidedView();

    private final IItemHandler[] sidedItemHandlers = new IItemHandler[6];
    private final IItemHandler unsidedItemHandler = new SidedAutomationHandler(null);

    private void onFluidChanged() {
        setChanged();
        clientSyncDirty = true;
    }

    /**
     * What the unsided (capability-internal) view allows. Held as a constant because the mirror of a
     * contained simulator reads its slots through this view several dozen times a tick, and building a
     * fresh config for each of those reads showed up as pure allocation churn.
     */
    private static final SideConfig UNSIDED_ITEM_ACCESS = new SideConfig(
            Direction.UP, SideMode.BOTH, SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, false, false,
            SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false);

    private final class SidedAutomationHandler implements IItemHandler {
        @Nullable
        private final Direction side;

        private SidedAutomationHandler(@Nullable Direction side) {
            this.side = side;
        }

        private SideConfig config() {
            return side == null ? UNSIDED_ITEM_ACCESS : sideConfigs.get(side.get3DDataValue());
        }

        @Override
        public int getSlots() { return OUTPUT_END + 1; }

        @Override
        public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (isItemValid(slot, stack)) return inventory.insertItem(slot, stack, simulate);
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (config().allowsOutputSlot(slot)) {
                return inventory.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return config().allowsInputSlot(slot) && !isInputSlotLocked(slot);
        }
    }

    public static final int ENERGY_CAPACITY = 10_000_000;
    public static final int ENERGY_MAX_RECEIVE = 1_000_000;

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return progress;
                case 1: return maxProgress;
                case 2: return energyStorage.getEnergyStored() & 0xFFFF;
                case 3: return (energyStorage.getEnergyStored() >> 16) & 0xFFFF;
                case 4: return energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 5: return (energyStorage.getMaxEnergyStored() >> 16) & 0xFFFF;
                case 6: return isEngaged ? 1 : 0;
                case 7: return speedMultiplier;
                case 8: return simulationMode;
                case 9: return statusCode;
                case 10: return isEngaged ? SlotMapping.lockedMask(slotMappings) : 0;
                case 11: return batchSize;
                case 12: return currentPowerUse & 0xFFFF;
                case 13: return (currentPowerUse >>> 16) & 0xFFFF;
                case 14: return averagePowerUse & 0xFFFF;
                case 15: return (averagePowerUse >>> 16) & 0xFFFF;
                case 16: return syncedSurchargePercent;
                case 17: return syncedAnomalyOrdinal;
                case 18: return syncedPassiveDrain;
                case 19: return getBatchSize();
                case 20: return gate.band().ordinal();
                case 21: return gate.heading().ordinal();
                default: return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: progress = value; break;
                case 1: maxProgress = value; break;
                case 6: isEngaged = (value == 1); break;
                case 7: speedMultiplier = value; break;
                case 8: simulationMode = value; break;
                case 9: statusCode = value; break;
                case 11: batchSize = value; break;
            }
        }

        @Override
        public int getCount() { return 22; }
    };

    public QuantumSimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_SIMULATOR_BE.get(), pos, state);
        for (Direction side : Direction.values()) {
            int id = side.get3DDataValue();
            sidedItemHandlers[id] = new SidedAutomationHandler(side);
            sidedFluidHandlers[id] = fluidTanks.sidedView(() -> sideConfigs.get(id));
        }
    }

    public boolean isEngaged() { return isEngaged; }
    public boolean isVirtualTicking() { return isVirtualTicking; }
    public int getSimulationMode() { return simulationMode; }
    public BlockState getContainedBlockState() { return containedBlockState; }

    /** Upgrades that keep a Productive Bees hive's bees inside: its Simulator Upgrade, or Productivity III or IV. */
    private static final java.util.List<String> BEE_SIMULATION_UPGRADES = java.util.List.of(
            "productivelib:upgrade_simulator", "productivelib:upgrade_productivity_3", "productivelib:upgrade_productivity_4");

    /** Whether {@code state} is a Productive Bees hive whose bees would fly out of the field. */
    static boolean beesWouldWander(BlockState state, CompoundTag nbt) {
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("productivebees") || !id.getPath().contains("hive")) return false;
        String saved = nbt.toString();
        return BEE_SIMULATION_UPGRADES.stream().noneMatch(saved::contains);
    }
    @Nullable
    public ContainedVisualization getContainedVisualization() { return containedVisualization; }
    /** The batch that runs: the one chosen, capped by the band here. */
    public int getBatchSize() { return Math.min(batchSize, Config.simulatorMaxBatch(gate.band())); }
    /** The batch chosen in the GUI, which may be larger than the band here lets run. */
    public int getChosenBatchSize() { return batchSize; }
    public BandGate bandGate() { return gate; }
    /** A {@link PhantomMirrorEngine} status, or {@link #STATUS_OFFLINE} with the field down. */
    public int getStatusCode() { return statusCode; }
    public int getSpeedMultiplier() { return speedMultiplier; }
    public List<SlotMapping> getSlotMappings() { return slotMappings; }
    public List<FluidMapping> getFluidMappings() { return fluidMappings; }

    public void setBatchSize(int requested) {
        int validated = validBatchSize(requested);
        if (batchSize == validated) return;
        batchSize = validated;
        if (engine != null) engine.resetCalibration();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static int validBatchSize(int value) {
        return value == 1 || value == 2 || value == 4 || value == 8 || value == 16 || value == 32 ? value : 2;
    }

    public void setSimulationMode(int mode) {
        // Recipe mode has moved to the Quantum Crafter; the simulator is parallel-only.
        if (this.simulationMode == MODE_PARALLEL) return;
        this.simulationMode = MODE_PARALLEL;
        releaseVirtualMachine();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Locked grid slots only reject items while parallel mode owns the grid layout. */
    public boolean isInputSlotLocked(int slot) {
        if (!isEngaged) return false;
        if (slot < 0 || slot >= INPUT_SLOTS || slot >= slotMappings.size()) return false;
        return slotMappings.get(slot).locked();
    }

    public void toggleEngage() {
        toggleEngage(null);
    }

    public void toggleEngage(@Nullable ServerPlayer player) {
        if (level == null) return;
        BlockPos abovePos = worldPosition.above();

        if (!isEngaged) {
            BlockState aboveState = level.getBlockState(abovePos);

            if (aboveState.getDestroySpeed(level, abovePos) < 0) return;

            if (!aboveState.isAir() && !aboveState.is(ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get())) {
                // Nesting multiplies the batches; a pack can cap how deep a chain goes.
                BlockEntity candidate = level.getBlockEntity(abovePos);
                // A Productive Bees hive sends its bees out to a world it isn't in, where they get lost:
                // it needs an upgrade that keeps them home.
                if (candidate != null && beesWouldWander(aboveState, candidate.saveWithFullMetadata(level.registryAccess()))) {
                    if (player != null) player.sendOverlayMessage(Component.translatable(
                            "message.quantimium.simulator.bees_need_upgrade"));
                    return;
                }
                if (candidate != null && 1 + chainLength(candidate.saveWithFullMetadata(level.registryAccess()))
                        > Config.simulatorMaxNesting()) {
                    if (player != null) player.sendOverlayMessage(Component.translatable(
                            "message.quantimium.simulator.too_deep", Config.simulatorMaxNesting()));
                    return;
                }
                releaseVirtualMachine();
                this.containedBlockState = aboveState;
                inventory.setStackInSlot(TARGET_DISPLAY_SLOT, new ItemStack(aboveState.getBlock()));

                BlockEntity topBE = level.getBlockEntity(abovePos);
                if (topBE != null) {
                    this.containedBlockNBT = topBE.saveWithFullMetadata(level.registryAccess());
                    level.removeBlockEntity(abovePos);
                } else {
                    this.containedBlockNBT = null;
                }
                this.containedVisualization = buildVisualization(
                        aboveState, containedBlockNBT, level.registryAccess(), VISUALIZATION_DEPTH);

                // Mappings made for this same machine earlier are kept, so toggling the field does not
                // throw away a hand-made layout.
                if (!keepsExistingMappings(aboveState)) {
                    autoDetectSlotMappings();
                }

                this.isEngaged = true;
                this.statusCode = STATUS_IDLE;
                level.setBlock(abovePos, ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get().defaultBlockState(), 3);
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                if (player != null
                        && aboveState.is(ModBlocks.QUANTUM_SIMULATOR.get())
                        && containedBlockNBT != null
                        && containedBlockNBT.getBooleanOr("IsEngaged", false)) {
                    awardNestedSimulation(player);
                }
            }
        } else {
            disengageField();
        }
        setChanged();
    }

    /** How many engaged Simulators deep {@code nbt} is: 0 for anything but an engaged Simulator. */
    public static int chainLength(@Nullable CompoundTag nbt) {
        if (nbt == null || !nbt.getBooleanOr("IsEngaged", false) || !nbt.contains("ContainedState")) return 0;
        return 1 + chainLength(nbt.contains("ContainedNBT") ? nbt.getCompoundOrEmpty("ContainedNBT") : null);
    }

    public void disengageField() {
        if (level == null) return;
        releaseVirtualMachine();
        this.isEngaged = false;
        this.statusCode = STATUS_OFFLINE;
        BlockPos abovePos = worldPosition.above();

        boolean restored = false;
        if (this.containedBlockState != null) {
            level.setBlock(abovePos, this.containedBlockState, 3);
            restored = level.getBlockState(abovePos).is(this.containedBlockState.getBlock());

            if (restored && this.containedBlockNBT != null) {
                BlockEntity restoredBE = level.getBlockEntity(abovePos);
                if (restoredBE != null) {
                    restoredBE.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), this.containedBlockNBT));
                    restoredBE.setChanged();
                } else {
                    restored = false;
                }
            }
        }
        // The machine could not be put back, so whatever it was holding for the player would go down
        // with the NBT it was saved in. Most of the time that is a finished batch the output grid had
        // no room for, which the machine was deliberately sitting on until space appeared.
        if (!restored) spillContainedMachine(level);

        this.containedBlockState = null;
        this.containedBlockNBT = null;
        this.containedVisualization = null;
        inventory.setStackInSlot(TARGET_DISPLAY_SLOT, ItemStack.EMPTY);
        progress = 0;
        compostLevel = 0;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Drops the contained machine's items at the simulator's feet. Only for the case where the machine
     * itself cannot be restored: the throwaway copy built here is read once and then discarded along
     * with the NBT it came from, so nothing can be handed out twice.
     */
    private void spillContainedMachine(Level level) {
        if (containedBlockState == null || containedBlockNBT == null) return;

        VirtualMachineMirror salvage = VirtualMachineMirror.create(
                level, worldPosition.above(), containedBlockState, containedBlockNBT);
        if (salvage == null) return;

        IItemHandler handler = salvage.handler();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack held = handler.getStackInSlot(slot);
            if (held.isEmpty()) continue;

            // Read straight out of the slot when the handler refuses extraction, since a machine that
            // will not hand its own contents back would otherwise take them with it.
            ItemStack pulled = handler.extractItem(slot, held.getCount(), false);
            ItemStack dropped = pulled.isEmpty() ? held.copy() : pulled;
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), dropped);
        }
    }

    /** Whether the mappings on file were made for this block and can be reused as-is. */
    private boolean keepsExistingMappings(BlockState target) {
        return slotMappingTarget != null && slotMappingTarget.equals(blockKey(target));
    }

    private static String blockKey(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * The containment field stopped existing without the simulator asking (broken in creative, replaced
     * by a command, blown up). The machine is put back on the next tick rather than here, because the
     * field block is still in the middle of being removed.
     */
    public void onContainmentLost() {
        this.containmentLost = true;
        setChanged();
    }

    public ItemStackHandler getInventory() { return inventory; }
    public SimulatorFluidTanks getFluidTanks() { return fluidTanks; }

    /** Empties every tank. Broken simulators void their fluids rather than handing back buckets. */
    public void voidFluids() {
        for (int tank = 0; tank < SimulatorFluidTanks.TANK_COUNT; tank++) {
            fluidTanks.setFluid(tank, FluidStack.EMPTY);
        }
    }

    @Nullable
    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return unsidedItemHandler;
        if (!SIDE_AUTOMATION_PROFILE.supportsItems(side)) return null;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.itemMode() == SideMode.DISABLED ? null : sidedItemHandlers[side.get3DDataValue()];
    }
    @Nullable
    public IFluidHandler getAutomationFluidHandler(@Nullable Direction side) {
        if (side == null) return unsidedFluidHandler;
        if (!SIDE_AUTOMATION_PROFILE.supportsFluids(side)) return null;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        return config.fluidMode() == SideMode.DISABLED ? null : sidedFluidHandlers[side.get3DDataValue()];
    }
    public List<SideConfig> getSideConfigs() { return List.copyOf(sideConfigs); }

    /** Read-only view for the renderer, which reads the modes every frame and must not allocate. */
    public List<SideConfig> sideConfigsView() {
        return sideConfigs;
    }

    @Override
    public SideAutomationProfile sideAutomationProfile() { return SIDE_AUTOMATION_PROFILE; }
    public void applySideConfigs(List<SideConfig> configs) {
        this.sideConfigs = SIDE_AUTOMATION_PROFILE.sanitize(configs);
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
    public QuantumEnergyStorage getEnergyStorage() { return energyStorage; }

    /** The chunk's anomaly surcharge on energy, as of the last once-a-second read. */
    public int getSurchargePercent() { return syncedSurchargePercent; }

    private static final int ANOMALY_READ_INTERVAL = 20;
    /** When the anomaly readout and drain rate were last read; see {@link #refreshAnomalyReadout()}. */
    private long anomalyReadTick = Long.MIN_VALUE;
    private int drainRate;

    /** The game tick this last marked itself changed; see {@link #setChanged()}. */
    private long lastMarkedTick = Long.MIN_VALUE;

    /**
     * Marks the chunk for saving, once a tick. A busy simulator changes several times a tick (energy
     * in, a cycle applied, fluids moved, the drain), and saves happen between ticks, so the first call
     * in a tick covers the rest. Unlike the default it does not wake neighbouring comparators: the
     * simulator gives them no signal, and that neighbour update was most of what marking cost.
     */
    @Override
    public void setChanged() {
        if (level == null) return;
        long now = level.getGameTime();
        if (now == lastMarkedTick) return;
        lastMarkedTick = now;
        level.blockEntityChanged(worldPosition);
    }

    // --- Core Ticking Loop ---
    /**
     * Reads the band here once a second. When it changes the batch that runs, the engine recalibrates,
     * as it does when the player picks another batch.
     */
    private void refreshBand(Level level, BlockPos pos) {
        if (level.getGameTime() % 20L != 0L && runningBatch == getBatchSize()) return;
        gate.update(level, pos);
        int running = getBatchSize();
        if (running == runningBatch) return;
        runningBatch = running;
        if (engine != null) engine.resetCalibration();
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, QuantumSimulatorBlockEntity be) {
        if (level.isClientSide()) return;

        // A contained simulator is ticked through its own block ticker just like a placed one, so this
        // is the only place the two can be told apart.
        be.worldResident = level.getBlockEntity(pos) == be;

        be.refreshAnomalyReadout();
        be.refreshBand(level, pos);
        be.applyPassiveDrain();
        be.runSimulation(level);
        be.refreshActiveState(level, pos, state);
        be.flushClientSync(level);
    }

    private void runSimulation(Level level) {
        if (worldResident && level.getGameTime() % 20L == 0L) runAutoTransfers(level);

        if (isEngaged && hasLostContainment(level)) {
            // Put the machine back and tell the client, otherwise its ghost keeps hanging in the air.
            disengageField();
            return;
        }

        if (isEngaged && containedBlockState != null) {
            Block targetBlock = containedBlockState.getBlock();

            if (targetBlock == Blocks.COMPOSTER) {
                processComposterSimulation();
                return;
            }

            tickParallelMode(level);
            return;
        }

        if (!isEngaged) {
            statusCode = STATUS_OFFLINE;
            recordPowerUse(0);
            if (progress > 0) {
                progress = 0;
                setChanged();
            }
        }
    }

    /**
     * Lights the block's panels while the simulator is doing work. Machines routinely pause for a tick
     * between crafts, so the flag is held briefly after the last one: every flip re-meshes the chunk,
     * and panels that strobe once a second would read as a fault rather than as progress.
     */
    private void refreshActiveState(Level level, BlockPos pos, BlockState state) {
        if (!worldResident) return;
        if (statusCode == PhantomMirrorEngine.STATUS_WORKING || statusCode == PhantomMirrorEngine.STATUS_INITIALIZING) {
            activeHoldTicks = ACTIVE_HOLD_TICKS;
        } else if (statusCode == STATUS_OFFLINE) {
            activeHoldTicks = 0;
        } else if (activeHoldTicks > 0) {
            activeHoldTicks--;
        }

        // Read the block back rather than trusting the state the ticker started with: the simulation
        // that just ran is allowed to place blocks, and writing a stale state would undo that.
        BlockState current = level.getBlockState(pos);
        if (!current.hasProperty(QuantumSimulatorBlock.ACTIVE)) return;

        boolean lit = activeHoldTicks > 0;
        if (current.getValue(QuantumSimulatorBlock.ACTIVE) != lit) {
            level.setBlock(pos, current.setValue(QuantumSimulatorBlock.ACTIVE, lit), Block.UPDATE_CLIENTS);
        }
    }

    /** Sends the client the tank and inventory state it draws from, at most once every few ticks. */
    private void flushClientSync(Level level) {
        if (!clientSyncDirty) return;
        if (!worldResident) {
            // Nothing renders a contained machine directly: the outer simulator carries everything the
            // client is shown about it.
            clientSyncDirty = false;
            return;
        }
        if (level.getGameTime() % CLIENT_SYNC_TICKS != 0L) return;
        clientSyncDirty = false;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Bounded neighbor automation: at most one pulled and one pushed stack per configured face.
     *
     * <p>Pulling waits for the containment field. A simulator with nothing contained has no use for
     * ingredients, and hoovering them up anyway leaves them sitting in a grid the player has to empty
     * by hand before the block can be moved. Pushing is always allowed, so finished goods can still be
     * cleared out of a simulator that has been switched off.
     */
    private void runAutoTransfers(Level level) {
        for (Direction side : Direction.values()) {
            if (!SIDE_AUTOMATION_PROFILE.supportsItems(side)
                    && !SIDE_AUTOMATION_PROFILE.supportsFluids(side)) continue;
            SideConfig config = sideConfigs.get(side.get3DDataValue());

            if ((config.autoItemInput() || config.autoItemOutput())
                    && SIDE_AUTOMATION_PROFILE.supportsItems(side)
                    && config.itemMode() != SideMode.DISABLED) {
                IItemHandler neighbor = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK,
                        worldPosition.relative(side), side.getOpposite()));
                if (neighbor != null) {
                    if (config.autoItemInput() && isEngaged && config.itemMode().allowsInput()) {
                        pullOneStack(neighbor, config);
                    }
                    if (config.autoItemOutput() && config.itemMode().allowsOutput()) {
                        pushOneStack(neighbor, config);
                    }
                }
            }

            if ((config.autoFluidInput() || config.autoFluidOutput())
                    && SIDE_AUTOMATION_PROFILE.supportsFluids(side)
                    && config.fluidMode() != SideMode.DISABLED) {
                IFluidHandler neighbor = LegacyFluids.legacy(level.getCapability(Capabilities.Fluid.BLOCK,
                        worldPosition.relative(side), side.getOpposite()));
                if (neighbor != null) {
                    if (config.autoFluidInput() && isEngaged && config.fluidMode().allowsInput()) {
                        pullOneFluid(neighbor, config);
                    }
                    if (config.autoFluidOutput() && config.fluidMode().allowsOutput()) {
                        pushOneFluid(neighbor, config);
                    }
                }
            }
        }
    }

    private void pullOneStack(IItemHandler neighbor, SideConfig config) {
        for (int source = 0; source < neighbor.getSlots(); source++) {
            ItemStack offered = neighbor.extractItem(source, 64, true);
            if (offered.isEmpty()) continue;

            int accepted = acceptedByInputs(offered, config);
            if (accepted <= 0) continue;
            ItemStack extracted = neighbor.extractItem(source, accepted, false);
            if (!extracted.isEmpty()) insertIntoInputs(extracted, config, false);
            return;
        }
    }

    private int acceptedByInputs(ItemStack stack, SideConfig config) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < INPUT_SLOTS && remaining > 0; slot++) {
            if (!config.allowsInputSlot(slot) || isInputSlotLocked(slot)) continue;
            ItemStack chunk = stack.copyWithCount(remaining);
            remaining = inventory.insertItem(slot, chunk, true).getCount();
        }
        return stack.getCount() - remaining;
    }

    private ItemStack insertIntoInputs(ItemStack stack, SideConfig config, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < INPUT_SLOTS && !remaining.isEmpty(); slot++) {
            if (!config.allowsInputSlot(slot) || isInputSlotLocked(slot)) continue;
            remaining = inventory.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private void pushOneStack(IItemHandler neighbor, SideConfig config) {
        for (int slot = OUTPUT_START; slot <= OUTPUT_END; slot++) {
            if (!config.allowsOutputSlot(slot)) continue;
            ItemStack available = inventory.extractItem(slot, 64, true);
            if (available.isEmpty()) continue;

            ItemStack remaining = available.copy();
            for (int target = 0; target < neighbor.getSlots() && !remaining.isEmpty(); target++) {
                remaining = neighbor.insertItem(target, remaining, true);
            }
            int accepted = available.getCount() - remaining.getCount();
            if (accepted <= 0) continue;

            ItemStack extracted = inventory.extractItem(slot, accepted, false);
            for (int target = 0; target < neighbor.getSlots() && !extracted.isEmpty(); target++) {
                extracted = neighbor.insertItem(target, extracted, false);
            }
            if (!extracted.isEmpty()) inventory.insertItem(slot, extracted, false);
            return;
        }
    }

    private void pullOneFluid(IFluidHandler neighbor, SideConfig config) {
        FluidStack offered = neighbor.drain(SimulatorFluidTanks.CAPACITY, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return;
        int accepted = fluidTanks.fillUnique(0, SimulatorFluidTanks.INPUT_TANKS, config.fluidInputMask(),
                offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack extracted = neighbor.drain(offered.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (!extracted.isEmpty()) {
            fluidTanks.fillUnique(0, SimulatorFluidTanks.INPUT_TANKS, config.fluidInputMask(),
                    extracted, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private void pushOneFluid(IFluidHandler neighbor, SideConfig config) {
        for (int local = 0; local < SimulatorFluidTanks.OUTPUT_TANKS; local++) {
            if ((config.fluidOutputMask() & (1 << local)) == 0) continue;
            int tank = SimulatorFluidTanks.outputIndex(local);
            FluidStack available = fluidTanks.getFluid(tank);
            if (available.isEmpty()) continue;
            int accepted = neighbor.fill(available, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) continue;
            FluidStack drained = fluidTanks.drainInternal(tank, accepted, IFluidHandler.FluidAction.EXECUTE);
            int filled = neighbor.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (filled < drained.getAmount()) {
                fluidTanks.fillInternal(tank, drained.copyWithAmount(drained.getAmount() - filled),
                        IFluidHandler.FluidAction.EXECUTE);
            }
            return;
        }
    }

    // MODE 0: RECIPE MODE removed — instant crafts live on the Quantum Crafter.
    // MODE 1: PHANTOM MIRROR / O(1) DELTA SCALING
    private void tickParallelMode(Level level) {
        if (machineNeedsRewind) rewindMachineToCleanState();

        VirtualMachineMirror mirror = getOrCreateMirror(level);
        if (mirror == null || engine == null) {
            setStatus(STATUS_UNSUPPORTED);
            recordPowerUse(0);
            return;
        }

        // Items stranded inside machine NBT by an earlier build (or loaded in before containment)
        // belong to the player, so they are pushed back out before any simulation happens.
        if (needsEviction && !evictMachineContents(mirror)) {
            setStatus(STATUS_OUTPUT_FULL);
            recordPowerUse(0);
            return;
        }

        mirror.chargeEnergyPorts();

        int status = engine.tick(mirror);
        boolean active = status == PhantomMirrorEngine.STATUS_WORKING
                || status == PhantomMirrorEngine.STATUS_INITIALIZING;
        if (active && !mirror.isPowered()) {
            // The machine publishes an energy interface that refuses our charge, so it will never
            // start a recipe. Say so rather than sitting on "WORKING" forever.
            status = PhantomMirrorEngine.STATUS_TARGET_UNPOWERED;
            active = false;
        }
        setStatus(status);

        if (active) {
            this.progress = engine.progress();
            this.maxProgress = engine.maxProgress();
            // A running machine changes inside the mirror, where nothing else marks the chunk for
            // saving. Without this a simulation can be left out of a save and reload as it was ticks
            // or minutes ago.
            setChanged();
        } else {
            this.progress = 0;
            this.maxProgress = 1;
        }
    }

    /**
     * Whether the field above has gone missing. The state is checked as well as the flag so that
     * anything bypassing block removal callbacks is still caught.
     */
    private boolean hasLostContainment(Level level) {
        if (containmentLost) {
            containmentLost = false;
            return true;
        }
        return !isVirtualTicking && !level.getBlockState(worldPosition.above()).is(ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get());
    }

    private void setStatus(int status) {
        if (this.statusCode != status) {
            this.statusCode = status;
            setChanged();
        }
    }

    /** Runs one virtual tick and undoes any block state the machine tried to write into the world. */
    /** A simulator is an effect machine exactly when what it holds is one. */
    @Override
    public boolean isSimulatedEffect() {
        return mirror != null && mirror.runsEffect();
    }

    public void runGuardedVirtualTick(VirtualMachineMirror mirror) {
        if (level == null) return;
        this.isVirtualTicking = true;
        try {
            mirror.tickOnce();

            BlockPos abovePos = worldPosition.above();
            BlockState current = level.getBlockState(abovePos);
            if (!current.is(ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get())) {
                // A vanilla furnace flips its own `lit` property, which would delete the containment
                // field. Keep the new property values, then put the field back.
                if (containedBlockState != null && current.getBlock() == containedBlockState.getBlock()) {
                    this.containedBlockState = current;
                    mirror.updateState(current);
                }
                level.removeBlockEntity(abovePos);
                level.setBlock(abovePos, ModBlocks.QUANTUM_CONTAINMENT_BLOCK.get().defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            }
        } finally {
            this.isVirtualTicking = false;
        }
    }

    public boolean consumeEnergy(int amount) {
        if (amount <= 0) return true;
        if (energyStorage.getEnergyStored() < amount) return false;
        energyStorage.consume(amount);
        return true;
    }

    public int consumeEnergy(long amount) {
        if (amount <= 0) return 0;
        return energyStorage.consume(amount);
    }

    public void recordPowerUse(long amount) {
        currentPowerUse = (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
        powerSamples[powerSampleIndex++ % powerSamples.length] = currentPowerUse;
        long total = 0;
        for (int sample : powerSamples) total += sample;
        averagePowerUse = (int) Math.min(Integer.MAX_VALUE, total / powerSamples.length);
    }

    public int getCurrentPowerUse() {
        return currentPowerUse;
    }

    public int getAveragePowerUse() {
        return averagePowerUse;
    }

    /**
     * The chunk's anomaly, for the readout and the drain, read once a second: anomaly moves slowly,
     * and reading it was three chunk lookups every tick.
     */
    private void refreshAnomalyReadout() {
        if (anomalyReadTick != Long.MIN_VALUE && level.getGameTime() - anomalyReadTick < ANOMALY_READ_INTERVAL) return;
        anomalyReadTick = level.getGameTime();
        FluxBand band = QuantumFlux.chunkAnomalyBand(level, worldPosition);
        syncedAnomalyOrdinal = band.ordinal();
        syncedSurchargePercent = AnomalyEffects.surchargePercent(level, worldPosition);
        drainRate = AnomalyEffects.passiveDrainFePerTick(level, worldPosition);
    }

    private void applyPassiveDrain() {
        int leak = drainRate;
        if (leak > 0) {
            leak = energyStorage.consume(leak);
            if (leak > 0) setChanged();
        }
        syncedPassiveDrain = leak;
    }

    public void onCycleApplied(int crafts, int cycleTicks) {
        setChanged();
    }

    @Nullable
    private VirtualMachineMirror getOrCreateMirror(Level level) {
        if (mirror != null) return mirror;
        if (mirrorUnsupported || containedBlockState == null || containedBlockNBT == null) return null;

        CompoundTag live = pendingLiveMachineNbt;
        CompoundTag engineState = pendingEngineState;
        pendingLiveMachineNbt = null;
        pendingEngineState = null;

        BlockPos abovePos = worldPosition.above();
        if (live != null && engineState != null) {
            // Resumed mid-simulation: the machine comes back exactly as it was, with the phantom-free
            // copy kept aside as the state it can be rewound to.
            mirror = VirtualMachineMirror.create(level, abovePos, containedBlockState, live, containedBlockNBT);
        }
        if (mirror == null) {
            engineState = null;
            mirror = VirtualMachineMirror.create(level, abovePos, containedBlockState, containedBlockNBT);
        }
        if (mirror == null) {
            mirrorUnsupported = true;
            return null;
        }

        engine = new PhantomMirrorEngine(this);
        PhantomMirrorEngine retired = retiredEngine;
        retiredEngine = null;
        if (retired != null) engine.adoptCalibration(retired);
        if (engineState != null) {
            engine.loadState(engineState, level.registryAccess(), mirror);
            // Everything in there is either the machine's own or a copy the engine has just laid claim
            // to again. Evicting would hand the player a second helping of the copies.
            needsEviction = false;
        } else {
            needsEviction = true;
        }
        return mirror;
    }

    /**
     * Asked for by the engine when phantom copies the machine refuses to release have blocked the slots
     * the player is trying to feed. Acted on at the start of the next tick rather than here, because the
     * machine is mid-tick and pulling it out from under itself would be a fine way to crash it.
     */
    public void requestMachineRewind() {
        this.machineNeedsRewind = true;
    }

    /**
     * Rebuilds the RAM machine from its last copy-free state, which is the same rewind that happens when
     * containment is broken and re-established. Everything lost with it is either a phantom copy, which
     * costs nothing, or machine progress, which is worth less than a simulator the player has to
     * re-contain by hand to change recipe. The engine only asks for this once it has nothing in flight
     * and nothing unbanked, so there is no paid work to lose.
     */
    private void rewindMachineToCleanState() {
        this.machineNeedsRewind = false;
        if (mirror == null) return;

        this.containedBlockNBT = mirror.cleanNbt();
        this.mirror = null;
        // Handed to the engine built for the rebuilt machine: only the machine is being started over,
        // not what we have learned about how it behaves.
        this.retiredEngine = engine;
        this.engine = null;
        setChanged();
    }

    /**
     * Drops the RAM machine, first taking phantoms back out so the saved NBT stays honest. A machine
     * that refuses to give a phantom slot back is rewound to its last phantom-free state instead:
     * otherwise handing it to the world would duplicate whatever is still in the simulator grid.
     */
    private void releaseVirtualMachine() {
        if (mirror != null) {
            mirror.restoreOriginalEnergy();
            boolean clean = engine == null || engine.withdrawPhantoms(mirror);
            this.containedBlockNBT = clean ? mirror.save() : mirror.cleanNbt();
        }
        // The machine is going back to the world, so there is no simulation left to resume.
        this.pendingLiveMachineNbt = null;
        this.pendingEngineState = null;
        this.mirror = null;
        this.engine = null;
        // The next machine may not be this one, so nothing measured about this one carries over.
        this.retiredEngine = null;
        this.mirrorUnsupported = false;
        this.needsEviction = true;
    }

    /**
     * Empties the machine's tanks into the simulator's, machine inputs to input tanks and machine
     * outputs to output tanks. Reports whether the machine ended up dry: a handler that refuses to be
     * drained, or tanks with no room left, keeps its fluid.
     */
    public boolean drainMachineFluids(VirtualMachineMirror mirror) {
        IFluidHandler handler = mirror.fluidHandler();
        if (handler == null) return true;

        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack held = handler.getFluidInTank(tank).copy();
            if (held.isEmpty()) continue;

            boolean toOutputs = mirror.producesFluidOutput(tank);
            int room = toOutputs
                    ? fluidTanks.insertIntoOutputs(held, IFluidHandler.FluidAction.SIMULATE)
                    : fluidTanks.insertIntoInputs(held, IFluidHandler.FluidAction.SIMULATE);
            if (room <= 0) continue;

            // Only ever banked once the machine has actually let go, so a handler that reports a drain
            // it does not honour cannot leave the same fluid in both places.
            FluidStack pulled = mirror.drainFluid(held.copyWithAmount(room), IFluidHandler.FluidAction.EXECUTE);
            if (pulled.isEmpty()) continue;

            int stored = toOutputs
                    ? fluidTanks.insertIntoOutputs(pulled, IFluidHandler.FluidAction.EXECUTE)
                    : fluidTanks.insertIntoInputs(pulled, IFluidHandler.FluidAction.EXECUTE);
            if (stored < pulled.getAmount()) {
                mirror.fillFluid(pulled.copyWithAmount(pulled.getAmount() - stored), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        // Checked in a second pass: a handler-wide drain can empty any tank holding that fluid, not
        // just the one being read, so per-tank results mid-loop say nothing about the whole machine.
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) return false;
        }
        return true;
    }

    /** Moves everything sitting in the machine out to the grid. Returns true once the machine is clear. */
    private boolean evictMachineContents(VirtualMachineMirror mirror) {
        IItemHandler handler = mirror.handler();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            // Ingredients go back to the input half, finished goods to the output half.
            boolean toInputs = mirror.acceptsInput(slot);

            ItemStack pulled = handler.extractItem(slot, stack.getCount(), true);
            if (pulled.isEmpty()) {
                // The capability refuses extraction, which is how Modern Industrialization guards its
                // input slots, so the direct write is the only way these items are ever coming out.
                // Room is checked first and they are only handed over once the machine has let go, so a
                // machine that refuses both cannot leave them in two places. If it refuses both they are
                // left where they are rather than blocking containment for good.
                ItemStack recovered = stack.copy();
                if (pushToGrid(recovered, toInputs, true) == recovered.getCount()) {
                    VirtualMachineMirror.clearSlot(handler, slot);
                    if (handler.getStackInSlot(slot).isEmpty()) pushToGrid(recovered, toInputs, false);
                }
                continue;
            }

            int moved = pushToGrid(pulled.copy(), toInputs, true);
            if (moved <= 0) return false;

            ItemStack taken = handler.extractItem(slot, moved, false);
            if (!taken.isEmpty()) pushToGrid(taken, toInputs, false);
            if (!handler.getStackInSlot(slot).isEmpty()) return false;
        }
        // Best effort, unlike items: fluid can only leave through the handler, so a machine that will
        // not be drained would otherwise hold containment open forever. What stays behind is the
        // machine's own and is captured as part of its clean state below.
        drainMachineFluids(mirror);
        needsEviction = false;
        // Now that the slots are empty they can be told apart properly: a result slot still holding
        // the machine's last output looks exactly like a slot willing to accept ingredients.
        mirror.classifySlots();
        mirror.captureClean();
        if (mappingsFromDirtyProbe) {
            mappingsFromDirtyProbe = false;
            autoDetectSlotMappings();
        }
        return true;
    }

    /** Pushes a stack into one half of the grid, spilling into the other half. Returns amount placed. */
    private int pushToGrid(ItemStack stack, boolean preferInputs, boolean simulate) {
        int firstStart = preferInputs ? 0 : OUTPUT_START;
        int firstEnd = preferInputs ? INPUT_SLOTS - 1 : OUTPUT_END;
        int spillStart = preferInputs ? OUTPUT_START : 0;
        int spillEnd = preferInputs ? OUTPUT_END : INPUT_SLOTS - 1;

        int placed = fillRange(stack, 0, firstStart, firstEnd, simulate);
        placed += fillRange(stack, placed, spillStart, spillEnd, simulate);
        return placed;
    }

    private int fillRange(ItemStack stack, int alreadyPlaced, int firstSlot, int lastSlot, boolean simulate) {
        int remaining = stack.getCount() - alreadyPlaced;
        int placed = 0;
        for (int slot = firstSlot; slot <= lastSlot && remaining > 0; slot++) {
            ItemStack chunk = stack.copyWithCount(Math.min(remaining, stack.getMaxStackSize()));
            ItemStack leftover = inventory.insertItem(slot, chunk, simulate);
            int moved = chunk.getCount() - leftover.getCount();
            placed += moved;
            remaining -= moved;
        }
        return placed;
    }

    // --- Slot mapping ---

    /**
     * Binds the grid's input slots to the machine's input slots one for one: grid slot 0 to the
     * machine's first input, grid slot 1 to its second, and so on.
     *
     * <p>The grid always has nine slots and machines rarely have nine inputs, so once every machine
     * input has been given a grid slot the assignment starts over. Grid slots sharing a machine input
     * queue up behind it and feed it in turn, which turns the leftovers into a buffer: a single-input
     * macerator gets nine slots' worth of ore to chew through rather than one. Leaving them out
     * entirely is what made eight of the nine slots dead space.
     */
    public void autoDetectSlotMappings() {
        if (level == null) return;

        VirtualMachineMirror probe = mirror;
        if (probe == null && containedBlockState != null && containedBlockNBT != null) {
            probe = VirtualMachineMirror.create(level, worldPosition.above(), containedBlockState, containedBlockNBT);
        }
        if (probe == null) {
            this.slotMappings = SlotMapping.defaults(INPUT_SLOTS);
            this.fluidMappings = FluidMapping.defaults(SimulatorFluidTanks.INPUT_TANKS);
            this.slotMappingTarget = null;
            return;
        }
        // A machine still holding its own items reads a full result slot as somewhere ingredients can
        // go, so what comes out of this pass is provisional until the machine has been emptied.
        this.mappingsFromDirtyProbe = probe != mirror || needsEviction;

        List<Integer> machineInputs = new ArrayList<>();
        for (int machineSlot = 0; machineSlot < probe.slotCount(); machineSlot++) {
            if (probe.acceptsInput(machineSlot)) machineInputs.add(machineSlot);
        }

        if (machineInputs.isEmpty()) {
            // Nothing could be identified as an input; leave the grid fully open rather than
            // locking the player out of a machine we simply failed to read.
            this.slotMappings = SlotMapping.defaults(INPUT_SLOTS);
        } else {
            List<SlotMapping> detected = new ArrayList<>(INPUT_SLOTS);
            for (int gridSlot = 0; gridSlot < INPUT_SLOTS; gridSlot++) {
                int machineSlot = machineInputs.get(gridSlot % machineInputs.size());
                detected.add(SlotMapping.bound(gridSlot, machineSlot, probe.handlerFace()));
            }
            this.slotMappings = detected;
        }

        List<Integer> machineFluidInputs = new ArrayList<>();
        for (int tank = 0; tank < probe.tankCount(); tank++) {
            if (probe.acceptsFluidInput(tank)) machineFluidInputs.add(tank);
        }
        if (machineFluidInputs.isEmpty()) {
            this.fluidMappings = FluidMapping.defaults(SimulatorFluidTanks.INPUT_TANKS);
        } else {
            List<FluidMapping> detected = new ArrayList<>(SimulatorFluidTanks.INPUT_TANKS);
            for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
                int machineTank = machineFluidInputs.get(simTank % machineFluidInputs.size());
                detected.add(FluidMapping.bound(simTank, machineTank, probe.fluidFace()));
            }
            this.fluidMappings = detected;
        }

        this.slotMappingTarget = containedBlockState == null ? null : blockKey(containedBlockState);
        setChanged();
    }

    public void applySlotMappings(List<SlotMapping> mappings) {
        this.slotMappings = SlotMapping.normalize(mappings, INPUT_SLOTS);
        this.slotMappingTarget = containedBlockState == null ? null : blockKey(containedBlockState);
        // Hand-made mappings are never second-guessed, however the machine reads.
        this.mappingsFromDirtyProbe = false;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void applyFluidMappings(List<FluidMapping> mappings) {
        this.fluidMappings = FluidMapping.normalize(mappings, SimulatorFluidTanks.INPUT_TANKS);
        this.slotMappingTarget = containedBlockState == null ? null : blockKey(containedBlockState);
        this.mappingsFromDirtyProbe = false;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void applyAllMappings(List<SlotMapping> itemMappings, List<FluidMapping> fluidMaps) {
        this.slotMappings = SlotMapping.normalize(itemMappings, INPUT_SLOTS);
        this.fluidMappings = FluidMapping.normalize(fluidMaps, SimulatorFluidTanks.INPUT_TANKS);
        this.slotMappingTarget = containedBlockState == null ? null : blockKey(containedBlockState);
        this.mappingsFromDirtyProbe = false;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Guards the configuration packets so a player cannot reconfigure a simulator across the map. */
    public boolean isUsableBy(Player player) {
        if (level == null || player.level() != level) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    /** Slot list for the configuration pop-up. Empty when the target cannot be simulated. */
    public List<MachineSlotInfo> describeMachineSlots() {
        if (level == null || containedBlockState == null || containedBlockNBT == null) return List.of();
        VirtualMachineMirror probe = mirror != null
                ? mirror
                : VirtualMachineMirror.create(level, worldPosition.above(), containedBlockState, containedBlockNBT);
        return probe == null ? List.of() : probe.describeSlots();
    }

    public List<MachineTankInfo> describeMachineTanks() {
        if (level == null || containedBlockState == null || containedBlockNBT == null) return List.of();
        VirtualMachineMirror probe = mirror != null
                ? mirror
                : VirtualMachineMirror.create(level, worldPosition.above(), containedBlockState, containedBlockNBT);
        return probe == null ? List.of() : probe.describeFluidTanks();
    }

    private void processComposterSimulation() {
        this.maxProgress = 20;

        for (int i = 0; i < INPUT_SLOTS; i++) {
            ItemStack inputStack = inventory.getStackInSlot(i);
            if (!inputStack.isEmpty() && ComposterBlock.COMPOSTABLES.containsKey(inputStack.getItem())) {
                int targetOutputSlot = findOutputSlotFor(new ItemStack(Items.BONE_MEAL));

                if (targetOutputSlot != -1 && energyStorage.getEnergyStored() >= 10) {
                    this.statusCode = PhantomMirrorEngine.STATUS_WORKING;
                    energyStorage.consume(10);
                    if (level instanceof ServerLevel serverLevel) {
                        QuantumFlux.emitFromEnergy(serverLevel, worldPosition, 10);
                    }
                    progress += speedMultiplier;

                    if (progress >= maxProgress) {
                        progress = 0;
                        inventory.extractItem(i, 1, false);
                        compostLevel++;

                        if (compostLevel >= 7) {
                            compostLevel = 0;
                            inventory.insertItem(targetOutputSlot, new ItemStack(Items.BONE_MEAL), false);
                        }
                    }
                    setChanged();
                    return;
                }
            }
        }
        this.statusCode = STATUS_IDLE;
    }

    private int findOutputSlotFor(ItemStack result) {
        for (int i = OUTPUT_START; i <= OUTPUT_END; i++) {
            ItemStack current = inventory.getStackInSlot(i);
            if (current.isEmpty()) return i;
            if (ItemStack.isSameItemSameComponents(current, result) && current.getCount() + result.getCount() <= current.getMaxStackSize()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.quantum_simulator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuantumSimulatorMenu(containerId, playerInventory, this, this.data);
    }

    @Override
    public void setRemoved() {
        releaseVirtualMachine();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        stashVirtualMachine();
        super.onChunkUnloaded();
    }

    /**
     * Puts the RAM machine away for a later reload instead of handing it back to the world. Releasing
     * it here would rewind every machine that will not surrender its copies, which is how a boiler came
     * back from a reload with its fire out and bought a fresh eight blocks of coal.
     */
    private void stashVirtualMachine() {
        if (mirror != null && engine != null && level != null) {
            mirror.restoreOriginalEnergy();
            this.pendingLiveMachineNbt = mirror.save();
            this.pendingEngineState = engine.saveState(level.registryAccess());
            // Anything that hands the machine straight back to the world reads this instead, so it is
            // kept to the copy-free state even while the live one waits to be resumed.
            this.containedBlockNBT = mirror.cleanNbt();
        }
        this.mirror = null;
        this.engine = null;
        this.mirrorUnsupported = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // The machine's own NBT is server-side bookkeeping; the client only needs it to draw the GUI.
        writeSharedState(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        writeSharedState(tag, registries);

        CompoundTag machineNbt = currentMachineNbt();
        if (machineNbt != null) {
            tag.put("ContainedNBT", machineNbt);
        }

        // Written alongside the copy-free state, not instead of it: this is the simulation as it stands,
        // which is the only way a machine that will not give its copies back keeps its progress across a
        // reload. On the way back in it is only ever used together with the bookkeeping that explains it.
        CompoundTag live = mirror != null ? mirror.save() : pendingLiveMachineNbt;
        CompoundTag engineState = mirror != null && engine != null
                ? engine.saveState(registries)
                : pendingEngineState;
        if (live != null && engineState != null) {
            tag.put("LiveMachineNBT", live);
            tag.put("EngineState", engineState);
        }
    }

    private void writeSharedState(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("FluidTanks", fluidTanks.save(registries));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("SimulationMode", simulationMode);
        tag.putInt("StatusCode", statusCode);
        tag.putBoolean("IsEngaged", isEngaged);
        tag.put("SlotMappings", SlotMapping.saveAll(slotMappings));
        tag.put("FluidMappings", FluidMapping.saveAll(fluidMappings));
        tag.put("SideConfigs", SideConfig.saveAll(sideConfigs));
        tag.putInt("BatchSize", batchSize);
        tag.putInt("Band", gate.band().ordinal());
        if (slotMappingTarget != null) {
            tag.putString("SlotMappingTarget", slotMappingTarget);
        }
        if (containedBlockState != null) {
            tag.put("ContainedState", NbtUtils.writeBlockState(containedBlockState));
        }
        if (containedVisualization != null) {
            tag.put("ContainedVisualization", containedVisualization.save());
        }
    }

    @Nullable
    private static ContainedVisualization buildVisualization(
            @Nullable BlockState state,
            @Nullable CompoundTag machineNbt,
            HolderLookup.Provider registries,
            int remainingDepth) {
        if (state == null || state.isAir() || remainingDepth <= 0) return null;

        ContainedVisualization nested = null;
        if (remainingDepth > 1
                && state.is(ModBlocks.QUANTUM_SIMULATOR.get())
                && machineNbt != null
                && machineNbt.getBooleanOr("IsEngaged", false)
                && machineNbt.contains("ContainedState")) {
            BlockState nestedState = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK), machineNbt.getCompoundOrEmpty("ContainedState"));
            CompoundTag nestedNbt = machineNbt.contains("ContainedNBT")
                    ? machineNbt.getCompoundOrEmpty("ContainedNBT")
                    : null;
            nested = buildVisualization(nestedState, nestedNbt, registries, remainingDepth - 1);
        }
        return new ContainedVisualization(state, nested);
    }

    private static void awardNestedSimulation(ServerPlayer player) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(NESTED_SIMULATION_ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "nested_simulation");
        }
    }

    public record ContainedVisualization(BlockState state, @Nullable ContainedVisualization nested) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("State", NbtUtils.writeBlockState(state));
            if (nested != null) {
                tag.put("Nested", nested.save());
            }
            return tag;
        }

        @Nullable
        private static ContainedVisualization load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.contains("State")) return null;
            BlockState state = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK), tag.getCompoundOrEmpty("State"));
            ContainedVisualization nested = tag.contains("Nested")
                    ? load(tag.getCompoundOrEmpty("Nested"), registries)
                    : null;
            return new ContainedVisualization(state, nested);
        }
    }

    /**
     * The machine NBT to persist. While the simulation is live this comes from RAM with phantom copies
     * stripped out, so reloading the world can never turn a phantom into a real item.
     */
    @Nullable
    private CompoundTag currentMachineNbt() {
        if (mirror != null) {
            mirror.restoreOriginalEnergy();
            return mirror.saveSanitized(
                    engine != null ? engine.phantomSlots() : new int[0],
                    engine != null ? engine.phantomTanks() : new int[0]);
        }
        return containedBlockNBT;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        releaseVirtualMachine();

        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        if (tag.contains("FluidTanks")) {
            fluidTanks.load(registries, tag.getCompoundOrEmpty("FluidTanks"));
        }
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        this.progress = tag.getIntOr("Progress", 0);
        this.simulationMode = MODE_PARALLEL; // Legacy saves may still say recipe mode.
        this.statusCode = tag.getIntOr("StatusCode", 0);
        this.isEngaged = tag.getBooleanOr("IsEngaged", false);
        if (tag.contains("ContainedState")) {
            this.containedBlockState = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompoundOrEmpty("ContainedState"));
        }
        if (tag.contains("ContainedNBT")) {
            this.containedBlockNBT = tag.getCompoundOrEmpty("ContainedNBT");
        }
        if (tag.contains("LiveMachineNBT") && tag.contains("EngineState")) {
            this.pendingLiveMachineNbt = tag.getCompoundOrEmpty("LiveMachineNBT");
            this.pendingEngineState = tag.getCompoundOrEmpty("EngineState");
        }
        this.containedVisualization = tag.contains("ContainedVisualization")
                ? ContainedVisualization.load(tag.getCompoundOrEmpty("ContainedVisualization"), registries)
                : buildVisualization(containedBlockState, containedBlockNBT, registries, VISUALIZATION_DEPTH);
        if (tag.contains("SlotMappings")) {
            this.slotMappings = SlotMapping.loadAll(tag.getListOrEmpty("SlotMappings"), INPUT_SLOTS);
        } else {
            this.slotMappings = SlotMapping.defaults(INPUT_SLOTS);
        }
        this.fluidMappings = tag.contains("FluidMappings")
                ? FluidMapping.loadAll(tag.getListOrEmpty("FluidMappings"), SimulatorFluidTanks.INPUT_TANKS)
                : FluidMapping.defaults(SimulatorFluidTanks.INPUT_TANKS);
        this.sideConfigs = SIDE_AUTOMATION_PROFILE.sanitize(
                tag.contains("SideConfigs")
                        ? SideConfig.loadAll(tag.getListOrEmpty("SideConfigs"))
                        : SideConfig.defaults());
        this.batchSize = validBatchSize(tag.contains("BatchSize") ? tag.getIntOr("BatchSize", 0) : 2);
        FluxBand band = FluxBand.values()[Math.clamp(tag.getIntOr("Band", 0), 0, FluxBand.values().length - 1)];
        gate.set(band, band);
        runningBatch = getBatchSize();
        this.slotMappingTarget = tag.contains("SlotMappingTarget") ? tag.getStringOr("SlotMappingTarget", "") : null;
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
        // Revert top block back to its normal physical block state in the world!
        if (isEngaged()) {
            disengageField();
        }

        // Drop items from simulator inputs/outputs
        for (int i = 0; i < 18; i++) {
            ItemStack stack = getInventory().getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        voidFluids();
    }
}
