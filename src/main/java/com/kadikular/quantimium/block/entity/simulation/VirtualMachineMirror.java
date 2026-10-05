// Path: src/main/java/com/kadikular/quantimium/block/entity/simulation/VirtualMachineMirror.java
package com.kadikular.quantimium.block.entity.simulation;

import com.kadikular.quantimium.util.LegacyFluids;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A copy of the contained machine living purely in RAM.
 *
 * <p>The mirror owns one item handler port and one fluid handler port. Vanilla and most modded
 * machines expose the same underlying storage through several faces, and those views alias each
 * other: writing through one shows up in the others. Tracking a single port per media keeps indices
 * stable and makes the delta measurement in {@link PhantomMirrorEngine} impossible to double count.
 */
public final class VirtualMachineMirror {

    /** Items used to work out which slots are inputs when the grid is still empty. */
    private static final List<ItemStack> CLASSIFY_PROBES = List.of(
            new ItemStack(Items.COAL),
            new ItemStack(Items.RAW_IRON),
            new ItemStack(Items.COBBLESTONE),
            new ItemStack(Items.REDSTONE));

    private static final List<FluidStack> FLUID_PROBES = List.of(
            new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
            new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000));

    private static final IItemHandler NO_ITEMS = new IItemHandler() {
        @Override public int getSlots() { return 0; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };

    private final Level level;
    private final BlockPos pos;
    private final BlockEntity blockEntity;
    private final IItemHandler handler;
    @Nullable
    private final Direction handlerFace;
    @Nullable
    private final IFluidHandler fluidHandler;
    @Nullable
    private final Direction fluidFace;
    private final MachinePowerPorts powerPorts;
    private final boolean[] acceptsInput;
    private final boolean[] producesOutput;
    /** Slots the machine has been seen producing into, as opposed to ones that only refused the probes. */
    private final boolean[] observedOutput;
    private final boolean[] acceptsFluidInput;
    private final boolean[] producesFluidOutput;

    private BlockState state;
    @Nullable
    private BlockEntityTicker<BlockEntity> ticker;
    private boolean tickFailed;
    /** Last serialization known to be free of phantom copies. See {@link #saveSanitized(int[])}. */
    private CompoundTag cleanNbt;

    private VirtualMachineMirror(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                 IItemHandler handler, @Nullable Direction handlerFace,
                                 @Nullable IFluidHandler fluidHandler, @Nullable Direction fluidFace,
                                 CompoundTag cleanNbt) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.blockEntity = blockEntity;
        this.handler = handler;
        this.handlerFace = handlerFace;
        this.fluidHandler = fluidHandler;
        this.fluidFace = fluidFace;
        this.powerPorts = MachinePowerPorts.discover(level, pos, state, blockEntity);
        this.acceptsInput = new boolean[handler.getSlots()];
        this.producesOutput = new boolean[handler.getSlots()];
        this.observedOutput = new boolean[handler.getSlots()];
        int tanks = fluidHandler == null ? 0 : fluidHandler.getTanks();
        this.acceptsFluidInput = new boolean[tanks];
        this.producesFluidOutput = new boolean[tanks];
        this.cleanNbt = cleanNbt;
        resolveTicker();
        classifySlots();
        classifyTanks();
    }

    /**
     * Rebuilds the machine from stored NBT. Returns {@code null} when the block cannot be simulated,
     * either because it has no block entity or because it exposes neither item slots nor fluid tanks.
     */
    @Nullable
    public static VirtualMachineMirror create(Level level, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        return create(level, pos, state, nbt, null);
    }

    /**
     * As {@link #create(Level, BlockPos, BlockState, CompoundTag)}, but with the phantom-free state
     * supplied separately. A simulation resumed after a reload comes back with copies still inside the
     * machine, so the state it was built from is not one it can ever be rewound to.
     */
    @Nullable
    public static VirtualMachineMirror create(Level level, BlockPos pos, BlockState state,
                                              @Nullable CompoundTag nbt, @Nullable CompoundTag cleanNbt) {
        if (nbt == null) return null;

        BlockEntity blockEntity;
        try {
            blockEntity = BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
        } catch (Throwable t) {
            return null;
        }
        if (blockEntity == null) return null;

        // Machines read recipes and world state off the level, so a null level means instant NPEs.
        blockEntity.setLevel(level);

        IItemHandler chosen = null;
        Direction chosenFace = null;
        IItemHandler internal = itemHandler(level, pos, state, blockEntity, null);
        if (internal != null && internal.getSlots() > 0) {
            chosen = internal;
        } else {
            for (Direction face : Direction.values()) {
                IItemHandler sided = itemHandler(level, pos, state, blockEntity, face);
                if (sided != null && (chosen == null || sided.getSlots() > chosen.getSlots())) {
                    chosen = sided;
                    chosenFace = face;
                }
            }
        }

        IFluidHandler chosenFluid = null;
        Direction chosenFluidFace = null;
        IFluidHandler internalFluid = fluidHandler(level, pos, state, blockEntity, null);
        if (internalFluid != null && internalFluid.getTanks() > 0) {
            chosenFluid = internalFluid;
        } else {
            for (Direction face : Direction.values()) {
                IFluidHandler sided = fluidHandler(level, pos, state, blockEntity, face);
                if (sided != null && (chosenFluid == null || sided.getTanks() > chosenFluid.getTanks())) {
                    chosenFluid = sided;
                    chosenFluidFace = face;
                }
            }
        }

        boolean hasItems = chosen != null && chosen.getSlots() > 0;
        boolean hasFluids = chosenFluid != null && chosenFluid.getTanks() > 0;
        if (!hasItems && !hasFluids && !(blockEntity instanceof SimulatedEffect effect && effect.isSimulatedEffect())) return null;

        return new VirtualMachineMirror(level, pos, state, blockEntity,
                hasItems ? chosen : NO_ITEMS, chosenFace,
                chosenFluid, chosenFluidFace, (cleanNbt == null ? nbt : cleanNbt).copy());
    }

    @Nullable
    private static IItemHandler itemHandler(Level level, BlockPos pos, BlockState state, BlockEntity be,
                                            @Nullable Direction face) {
        try {
            return LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, pos, state, be, face));
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static IFluidHandler fluidHandler(Level level, BlockPos pos, BlockState state, BlockEntity be,
                                              @Nullable Direction face) {
        try {
            return LegacyFluids.legacy(level.getCapability(Capabilities.Fluid.BLOCK, pos, state, be, face));
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void resolveTicker() {
        try {
            this.ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(level, blockEntity.getType());
        } catch (Throwable t) {
            this.ticker = null;
        }
    }

    /**
     * Splits the machine's slots into inputs and results. A slot that refuses every probe item is
     * treated as a result slot; {@link #markAsOutput(int)} corrects any remaining mistakes once the
     * machine is observed actually producing into a slot.
     *
     * <p>Worth re-running once the machine has been emptied out. A result slot that still holds the
     * last batch of output reads as willing to take items, because {@link #accepts} deliberately
     * treats an occupied slot as available, and that is how a macerator's output slot ends up
     * classified as an input.
     */
    public void classifySlots() {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack held = handler.getStackInSlot(slot);
            boolean insertable = false;

            if (looksLikeChargeSlot(slot)) {
                acceptsInput[slot] = false;
                producesOutput[slot] = false;
                continue;
            }

            for (ItemStack probe : CLASSIFY_PROBES) {
                if (accepts(slot, probe, true)) {
                    insertable = true;
                    break;
                }
            }
            if (!insertable && !held.isEmpty()) {
                insertable = accepts(slot, held, true);
            }

            acceptsInput[slot] = insertable;
            producesOutput[slot] = !insertable;
        }
    }

    /**
     * Mekanism (and similar) charge slots accept redstone / energy tablets via an energy-conversion
     * recipe. Those must not be treated as process inputs, or ore is stuffed into the battery slot.
     */
    private boolean looksLikeChargeSlot(int slot) {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        if (!accepts(slot, redstone, true)) return false;
        if (accepts(slot, new ItemStack(Items.RAW_IRON), true)) return false;
        if (accepts(slot, new ItemStack(Items.COBBLESTONE), true)) return false;
        return true;
    }

    /**
     * Whether the slot would take this item. Both checks matter: {@code isItemValid} catches machines
     * that filter by slot role, and the simulated insert catches handlers that always answer
     * {@code true} but reject the write.
     */
    public boolean accepts(int slot, ItemStack stack) {
        return accepts(slot, stack, false);
    }

    /**
     * @param strict when set, a slot busy with something else counts as a refusal. Routing a phantom
     *               wants the lenient answer, since a slot occupied now may free up. Deciding whether
     *               a slot is an input at all wants the strict one, or every slot holding output would
     *               look like an input.
     */
    private boolean accepts(int slot, ItemStack stack, boolean strict) {
        if (slot < 0 || slot >= handler.getSlots() || stack.isEmpty()) return false;
        try {
            if (!handler.isItemValid(slot, stack)) return false;
            ItemStack held = handler.getStackInSlot(slot);
            if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) {
                return !strict;
            }
            ItemStack single = stack.copyWithCount(1);
            return handler.insertItem(slot, single, true).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean acceptsInput(int slot) {
        return slot >= 0 && slot < acceptsInput.length && acceptsInput[slot];
    }

    public boolean producesOutput(int slot) {
        return slot >= 0 && slot < producesOutput.length && producesOutput[slot];
    }

    /** Whether the machine has anywhere to put ingredients, i.e. whether it needs feeding at all. */
    /** Whether the machine's work is an effect on the world (see {@link SimulatedEffect}). */
    public boolean runsEffect() {
        return blockEntity instanceof SimulatedEffect effect && effect.isSimulatedEffect();
    }

    public boolean hasInputSlots() {
        for (boolean input : acceptsInput) {
            if (input) return true;
        }
        for (boolean input : acceptsFluidInput) {
            if (input) return true;
        }
        return false;
    }

    /** Called when a slot is caught producing items, so it is never used as a phantom target again. */
    public void markAsOutput(int slot) {
        if (slot < 0 || slot >= producesOutput.length) return;
        producesOutput[slot] = true;
        acceptsInput[slot] = false;
        observedOutput[slot] = true;
    }

    /**
     * Whether a slot the probes took for a result slot could still be an input. The probes are a few
     * vanilla items, and machines that take only their own items (a Hostile Neural Networks chamber
     * wants data models and prediction matrices) refuse all of them; such a slot is an input the probes
     * could not see. A slot the machine has actually produced into is a result slot for certain.
     */
    public boolean canLearnInput(int slot) {
        return slot >= 0 && slot < acceptsInput.length && !acceptsInput[slot] && !observedOutput[slot];
    }

    /** The machine took a real grid item here, so it is an input after all. */
    public void learnInput(int slot) {
        if (!canLearnInput(slot)) return;
        acceptsInput[slot] = true;
        producesOutput[slot] = false;
    }

    public void markFluidAsOutput(int tank) {
        if (tank < 0 || tank >= producesFluidOutput.length) return;
        producesFluidOutput[tank] = true;
        acceptsFluidInput[tank] = false;
    }

    public void classifyTanks() {
        if (fluidHandler == null) return;
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            FluidStack held = fluidHandler.getFluidInTank(tank);
            boolean insertable = false;
            for (FluidStack probe : FLUID_PROBES) {
                if (acceptsFluid(tank, probe, true)) {
                    insertable = true;
                    break;
                }
            }
            if (!insertable && !held.isEmpty()) {
                insertable = acceptsFluid(tank, held, true);
            }
            // A tank that can drain something is also a producer even if it accepts fills.
            boolean drainable = !held.isEmpty() && canDrainTank(tank);
            acceptsFluidInput[tank] = insertable;
            producesFluidOutput[tank] = drainable || !insertable;
        }
    }

    public boolean acceptsFluid(int tank, FluidStack stack) {
        return acceptsFluid(tank, stack, false);
    }

    private boolean acceptsFluid(int tank, FluidStack stack, boolean strict) {
        if (fluidHandler == null || tank < 0 || tank >= fluidHandler.getTanks() || stack.isEmpty()) return false;
        try {
            if (!fluidHandler.isFluidValid(tank, stack)) return false;
            FluidStack held = fluidHandler.getFluidInTank(tank);
            if (!held.isEmpty() && !FluidStack.isSameFluidSameComponents(held, stack)) {
                return !strict;
            }
            // Probe by filling the whole handler; tank-specific fill is not part of IFluidHandler.
            int filled = fluidHandler.fill(stack.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE);
            return filled > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canDrainTank(int tank) {
        if (fluidHandler == null) return false;
        try {
            FluidStack held = fluidHandler.getFluidInTank(tank);
            if (held.isEmpty()) return false;
            FluidStack drained = fluidHandler.drain(held.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE);
            return !drained.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean acceptsFluidInput(int tank) {
        return tank >= 0 && tank < acceptsFluidInput.length && acceptsFluidInput[tank];
    }

    public boolean producesFluidOutput(int tank) {
        return tank >= 0 && tank < producesFluidOutput.length && producesFluidOutput[tank];
    }

    public List<MachineSlotInfo> describeSlots() {
        List<MachineSlotInfo> infos = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            infos.add(new MachineSlotInfo(slot, handlerFace, handler.getStackInSlot(slot).copy(),
                    acceptsInput[slot], producesOutput[slot], handler.getSlotLimit(slot)));
        }
        return infos;
    }

    public List<MachineTankInfo> describeFluidTanks() {
        List<MachineTankInfo> infos = new ArrayList<>();
        if (fluidHandler == null) return infos;
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            infos.add(new MachineTankInfo(tank, fluidFace, fluidHandler.getFluidInTank(tank).copy(),
                    acceptsFluidInput[tank], producesFluidOutput[tank], fluidHandler.getTankCapacity(tank)));
        }
        return infos;
    }

    public IItemHandler handler() {
        return handler;
    }

    @Nullable
    public IFluidHandler fluidHandler() {
        return fluidHandler;
    }

    @Nullable
    public Direction handlerFace() {
        return handlerFace;
    }

    @Nullable
    public Direction fluidFace() {
        return fluidFace;
    }

    public int slotCount() {
        return handler.getSlots();
    }

    public int tankCount() {
        return fluidHandler == null ? 0 : fluidHandler.getTanks();
    }

    public FluidStack[] snapshotFluids() {
        if (fluidHandler == null) return new FluidStack[0];
        FluidStack[] snap = new FluidStack[fluidHandler.getTanks()];
        for (int tank = 0; tank < snap.length; tank++) {
            snap[tank] = fluidHandler.getFluidInTank(tank).copy();
        }
        return snap;
    }

    /** Empties a tank by draining it completely, best-effort. */
    public static void clearTank(IFluidHandler handler, int tank) {
        if (handler == null || tank < 0 || tank >= handler.getTanks()) return;
        try {
            FluidStack held = handler.getFluidInTank(tank);
            if (held.isEmpty()) return;
            handler.drain(held.copy(), IFluidHandler.FluidAction.EXECUTE);
        } catch (Throwable ignored) {
        }
    }

    /** Fills a specific tank by draining others first is not supported; fill the handler as a whole. */
    public int fillFluid(FluidStack stack, IFluidHandler.FluidAction action) {
        if (fluidHandler == null || stack.isEmpty()) return 0;
        try {
            return fluidHandler.fill(stack, action);
        } catch (Throwable t) {
            return 0;
        }
    }

    public FluidStack drainFluid(FluidStack stack, IFluidHandler.FluidAction action) {
        if (fluidHandler == null || stack.isEmpty()) return FluidStack.EMPTY;
        try {
            return fluidHandler.drain(stack, action);
        } catch (Throwable t) {
            return FluidStack.EMPTY;
        }
    }

    public BlockState state() {
        return state;
    }

    /**
     * Keeps the mirror in step with a block state the machine changed on its own (a furnace flipping
     * {@code lit}, for instance). Without this the machine would rewrite the state every single tick.
     */
    public void updateState(BlockState newState) {
        if (newState.getBlock() != state.getBlock()) return;
        this.state = newState;
        resolveTicker();
    }

    public boolean canTick() {
        return ticker != null;
    }

    public boolean tickFailed() {
        return tickFailed;
    }

    /** Tops every energy buffer up so machines that need power are never the bottleneck. */
    public void chargeEnergyPorts() {
        powerPorts.charge();
    }

    public boolean usesPower() { return !powerPorts.isEmpty(); }
    public void beginEnergySample() { powerPorts.beginSample(); }
    public long finishEnergySampleFe() { return powerPorts.finishSampleFe(); }
    public void restoreOriginalEnergy() { powerPorts.restoreOriginalEnergy(); }

    /**
     * False when the machine publishes an energy interface that we cannot fill. Such a machine will
     * never start a recipe, so it is worth telling the player about instead of idling silently.
     */
    public boolean isPowered() {
        return powerPorts.isEmpty() || powerPorts.hasCharge();
    }

    /** Advances the RAM machine by one tick. Returns false if the machine threw. */
    public boolean tickOnce() {
        if (ticker == null) return false;
        try {
            ticker.tick(level, pos, state, blockEntity);
            return true;
        } catch (Throwable t) {
            tickFailed = true;
            return false;
        }
    }

    public CompoundTag save() {
        return blockEntity.saveWithFullMetadata(level.registryAccess());
    }

    /**
     * Remembers the machine's current state as phantom-free. Has to be called whenever the engine
     * knows no phantoms are resident, because this snapshot is what the machine is rewound to if it
     * ever refuses to give a phantom back. A stale snapshot rewinds the machine to contents it has
     * since handed to the player, which duplicates them.
     */
    public void captureClean() {
        powerPorts.restoreOriginalEnergy();
        this.cleanNbt = save();
    }

    /** The most recent phantom-free serialization, used when phantoms cannot be removed. */
    public CompoundTag cleanNbt() {
        return cleanNbt;
    }

    /**
     * Serializes the machine with the given slots emptied.
     *
     * <p>Phantom stacks are copies of items that never left the simulator grid, so persisting them
     * would duplicate those items the next time the world loads. The clearing happens on a throwaway
     * clone: emptying a slot on the live machine would reset its cooking progress.
     *
     * <p>Some machines refuse extraction from their own input slots, which is exactly where phantoms
     * sit. When a slot will not empty, the last known phantom-free state is written instead: losing a
     * few ticks of machine progress is preferable to handing the player a duplicated stack.
     */
    public CompoundTag saveSanitized(int[] phantomSlots, int[] phantomTanks) {
        CompoundTag live = save();
        if (phantomSlots.length == 0 && phantomTanks.length == 0) return live;

        try {
            BlockEntity clone = BlockEntity.loadStatic(pos, state, live, level.registryAccess());
            if (clone == null) return cleanNbt;
            clone.setLevel(level);

            if (phantomSlots.length > 0) {
                IItemHandler cloneHandler = itemHandler(level, pos, state, clone, handlerFace);
                if (cloneHandler == null) return cleanNbt;

                for (int slot : phantomSlots) {
                    clearSlot(cloneHandler, slot);
                    if (slot >= 0 && slot < cloneHandler.getSlots() && !cloneHandler.getStackInSlot(slot).isEmpty()) {
                        return cleanNbt;
                    }
                }
            }

            if (phantomTanks.length > 0) {
                IFluidHandler cloneFluids = fluidHandler(level, pos, state, clone, fluidFace);
                if (cloneFluids == null) return cleanNbt;

                for (int tank : phantomTanks) {
                    clearTank(cloneFluids, tank);
                    if (tank >= 0 && tank < cloneFluids.getTanks() && !cloneFluids.getFluidInTank(tank).isEmpty()) {
                        return cleanNbt;
                    }
                }
            }
            return clone.saveWithFullMetadata(level.registryAccess());
        } catch (Throwable t) {
            return cleanNbt;
        }
    }

    /** Whether the given slots are all empty, i.e. whether phantom withdrawal actually worked. */
    public boolean areSlotsEmpty(int[] slots) {
        for (int slot : slots) {
            if (slot < 0 || slot >= handler.getSlots()) continue;
            if (!handler.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    /** Empties a slot, preferring the direct write so stubborn handlers cannot refuse. */
    public static void clearSlot(IItemHandler handler, int slot) {
        if (slot < 0 || slot >= handler.getSlots()) return;
        try {
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
                return;
            }
            int guard = 0;
            while (!handler.getStackInSlot(slot).isEmpty() && guard++ < 64) {
                if (handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), false).isEmpty()) break;
            }
        } catch (Throwable ignored) {
            // Nothing else we can do; the stack stays where it is.
        }
    }
}
