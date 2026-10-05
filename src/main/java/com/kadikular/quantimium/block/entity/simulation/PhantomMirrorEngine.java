// Path: src/main/java/com/kadikular/quantimium/block/entity/simulation/PhantomMirrorEngine.java
package com.kadikular.quantimium.block.entity.simulation;

import com.kadikular.quantimium.util.NbtCompat;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The phantom mirror: runs a machine in RAM and multiplies everything it does by the batch size as it
 * happens.
 *
 * <h3>Why phantoms</h3>
 * Real items never leave the simulator's 3x3 grid. Copies ("phantoms") are projected into the RAM
 * machine's input slots so it has something to work with, and are wiped before the machine is ever
 * serialized. Feeding the machine real items is what used to strand them inside machine NBT where the
 * player could neither see nor recover them.
 *
 * <h3>Keeping stored-up work honest</h3>
 * Every tick the machine is diffed and the difference applied straight away, multiplied by N. That is
 * exact for anything the machine pays for as it goes, but not for work it buys in advance: a furnace
 * eats one coal and then smelts eight items on it. Multiplying those eight crafts by N while charging
 * for a single coal is what used to hand out free ingots.
 *
 * <p>So a purchase and the work it funds are always multiplied by the same number. When the machine
 * takes ingredients, that draw is charged immediately at the largest multiple the grid can afford, and
 * that multiple becomes the ceiling for everything the machine does until it buys again. One coal at a
 * batch of 8 therefore buys eight smelts at 1x; eight coal buys the same eight smelts at 8x. Either
 * way the fuel-to-output ratio matches the real machine, and output keeps arriving craft by craft
 * instead of in one lump at the end of the burn.
 */
public final class PhantomMirrorEngine {

    public static final int STATUS_WORKING = 1;
    public static final int STATUS_IDLE = 2;
    public static final int STATUS_NO_RECIPE = 3;
    public static final int STATUS_NO_POWER = 5;
    public static final int STATUS_OUTPUT_FULL = 6;
    public static final int STATUS_UNSUPPORTED = 7;
    public static final int STATUS_NO_SLOTS = 8;
    public static final int STATUS_LOW_INPUT = 9;
    public static final int STATUS_TARGET_UNPOWERED = 10;
    public static final int STATUS_INITIALIZING = 11;
    public static final int STATUS_NO_TICKER = 12;
    public static final int STATUS_STALLED = 13;

    private static final int BASELINE_FE_PER_TICK = 20;

    /** No multiplier has been pinned for a grid slot yet. */
    private static final int NO_DRAW = -1;
    /** How long to keep a machine running for a job it took ingredients for but never finished. */
    private static final int ABANDON_TICKS = 6000;
    /** Longest a machine keeps showing "working" while its internals settle after the last craft. */
    private static final int MAX_SETTLE_TICKS = 200;
    /**
     * How long an empty machine keeps being ticked while it is neither drawing power nor changing
     * anything. Generous enough for a machine to wind its own state down the way it would in the world
     * (Modern Industrialization bleeds off its overclock one tick at a time), then it is left alone so
     * an idle simulator is not ticking a machine that has nothing left to do for ever.
     */
    private static final int WIND_DOWN_TICKS = 600;
    /** Copies to stock an input with before the machine has shown how many it takes per craft. */
    private static final int FIRST_PROBE_UNITS = 1;
    private static final int MAX_PROBE_UNITS = 64;
    /** How long to wait for a machine to take the copies offered before offering it more. */
    private static final int PROBE_PATIENCE = 20;
    /**
     * How long a machine may sit on the copies it has been given, doing nothing whatsoever, before the
     * simulator stops waiting on it. Long enough for the offer to have been widened the whole way first,
     * so a machine that simply needed more of an ingredient has had its chance.
     */
    private static final int STALL_TICKS = 200;

    private record Harvest(int machineSlot, ItemStack perCraft) {}

    private final QuantumSimulatorBlockEntity be;
    private final int gridInputs;

    private final int[] phantomTarget;
    private final ItemStack[] phantomSource;
    /** Multiple each grid slot was last charged at, which caps work funded by that purchase. */
    private final int[] drawMultiplier;
    /** Same cap for input tanks, so a fluid-only recipe cannot pay for one batch and collect another. */
    private final int[] fluidDrawMultiplier;

    private int ticksSinceCraft;
    /** Length of the last completed cycle, used for the progress bar. Zero until one is measured. */
    private int craftInterval;
    /** Set once ingredients have been charged for and cleared when the machine delivers. */
    private boolean awaitingWork;
    /**
     * The machine is chewing on something the grid could not pay for. Nothing it starts while this is
     * set counts as a job in flight, or a machine sipping fuel it was never charged for would look
     * busy for ever and never reach the point where its leftovers are cleared out.
     */
    private boolean strandedWork;
    /** Ticks since the grid last had anything to fund, which is what bounds the wind-down window. */
    private int windDownTicks;
    /** Whether the grid could start a new job this tick, which is what licenses buying one. */
    private boolean fundableThisTick;
    private boolean heldPhantoms;
    /** True once this machine has completed at least one craft, so we stop calling it "initialising". */
    private boolean everCrafted;
    /** Last ingredient identity per simulator slot, excluding mutable components such as model XP. */
    private final ItemStack[] recipeInputs;
    /**
     * Ticks left in the grace period after the last real work. A machine keeps reporting "working"
     * while this counts down so the brief input/output churn as it finishes an internal pass does not
     * flicker between statuses before it truly goes idle.
     */
    private int settleTicks;
    private long energyDebt;
    /** Work the machine finished that the output grid had no room for; retried before ticking again. */
    @Nullable
    private TickDelta stalledDelta;
    private boolean sawItems;
    private boolean sawFluids;
    private boolean routedAnything;
    private boolean routedFluids;
    /** Everything charged during the current game tick, reported as one figure. */
    private long chargedThisTick;
    /** The machine does an effect rather than crafting; see {@link #present}. */
    private boolean effectMachine;
    /** Charged since flux was last emitted; emitted every {@link #FLUX_EMIT_INTERVAL} ticks. */
    private long unemittedFe;
    private static final int FLUX_EMIT_INTERVAL = 20;
    /** Largest draw seen from each grid slot in one craft, which is how many copies it needs stocked. */
    private final int[] craftDraw;
    /** Largest mB draw seen from each input tank in one craft. */
    private final int[] fluidCraftDraw;
    /** How many copies to offer a slot whose per-craft draw has not been seen yet. */
    private int probeUnits = FIRST_PROBE_UNITS;
    /** Consecutive ticks the machine has drawn nothing and changed nothing. */
    private int quietTicks;
    /**
     * As {@link #quietTicks}, but not forgiven each time the offer is widened, so it measures how long
     * the machine has been inert overall rather than how long it has ignored the current offer.
     */
    private int inertTicks;
    /** Fingerprint of the grid's inputs, to notice the player changing what is on offer. */
    private long offeredInputs;
    /** Machine tank currently holding phantom fluid for each simulator input tank. */
    private final int[] phantomFluidTarget;
    private final FluidStack[] phantomFluidSource;

    public PhantomMirrorEngine(QuantumSimulatorBlockEntity be) {
        this.be = be;
        this.gridInputs = QuantumSimulatorBlockEntity.INPUT_SLOTS;
        this.phantomTarget = new int[gridInputs];
        this.phantomSource = new ItemStack[gridInputs];
        this.drawMultiplier = new int[gridInputs];
        this.fluidDrawMultiplier = new int[SimulatorFluidTanks.INPUT_TANKS];
        this.craftDraw = new int[gridInputs];
        this.fluidCraftDraw = new int[SimulatorFluidTanks.INPUT_TANKS];
        this.phantomFluidTarget = new int[SimulatorFluidTanks.INPUT_TANKS];
        this.phantomFluidSource = new FluidStack[SimulatorFluidTanks.INPUT_TANKS];
        this.recipeInputs = new ItemStack[gridInputs];
        Arrays.fill(this.recipeInputs, ItemStack.EMPTY);
        Arrays.fill(this.phantomFluidSource, FluidStack.EMPTY);
        for (int slot = 0; slot < gridInputs; slot++) {
            phantomTarget[slot] = -1;
            drawMultiplier[slot] = NO_DRAW;
        }
        Arrays.fill(phantomFluidTarget, -1);
        Arrays.fill(fluidDrawMultiplier, NO_DRAW);
    }

    /**
     * Everything needed to pick this simulation back up after a reload: which machine slots and tanks
     * hold copies of which grid stacks, what those copies were charged at, and what the machine has
     * been seen to draw per craft.
     *
     * <p>Saved alongside the machine's live state rather than a phantom-free one. Machines that refuse
     * to give an input slot back — Modern Industrialization gates extraction on its pipe settings, so
     * every one of its machines does — cannot be serialized without their copies, and the only other
     * option is to rewind them to the moment containment began. That is what made a reloaded boiler
     * start over on fresh fuel. Persisting the bookkeeping keeps the copies accounted for as copies, so
     * the machine can be saved exactly as it stands.
     */
    public CompoundTag saveState(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("PhantomTarget", phantomTarget.clone());
        tag.putIntArray("DrawMultiplier", drawMultiplier.clone());
        tag.putIntArray("CraftDraw", craftDraw.clone());
        tag.putIntArray("FluidTarget", phantomFluidTarget.clone());
        tag.putIntArray("FluidDrawMultiplier", fluidDrawMultiplier.clone());
        tag.putIntArray("FluidCraftDraw", fluidCraftDraw.clone());
        tag.put("PhantomSource", saveStacks(phantomSource, registries));
        tag.put("RecipeInputs", saveStacks(recipeInputs, registries));
        tag.put("FluidSource", saveFluids(phantomFluidSource, registries));
        tag.putBoolean("AwaitingWork", awaitingWork);
        tag.putBoolean("EverCrafted", everCrafted);
        tag.putInt("CraftInterval", craftInterval);
        tag.putInt("TicksSinceCraft", ticksSinceCraft);
        tag.putInt("ProbeUnits", probeUnits);
        tag.putInt("SettleTicks", settleTicks);
        return tag;
    }

    /**
     * Restores saved bookkeeping against a freshly rebuilt machine. A binding is only kept where the
     * machine really is still holding the copy it describes: an unbacked binding would either strand a
     * grid slot behind a phantom that is not there, or bank the machine's own stock as free output.
     */
    public void loadState(CompoundTag tag, HolderLookup.Provider registries, VirtualMachineMirror mirror) {
        readInts(tag, "PhantomTarget", phantomTarget, -1);
        readInts(tag, "DrawMultiplier", drawMultiplier, NO_DRAW);
        readInts(tag, "CraftDraw", craftDraw, 0);
        readInts(tag, "FluidTarget", phantomFluidTarget, -1);
        readInts(tag, "FluidDrawMultiplier", fluidDrawMultiplier, NO_DRAW);
        readInts(tag, "FluidCraftDraw", fluidCraftDraw, 0);
        loadStacks(tag.getListOrEmpty("PhantomSource"), phantomSource, registries, null);
        loadStacks(tag.getListOrEmpty("RecipeInputs"), recipeInputs, registries, ItemStack.EMPTY);
        loadFluids(tag.getListOrEmpty("FluidSource"), phantomFluidSource, registries);
        awaitingWork = tag.getBooleanOr("AwaitingWork", false);
        everCrafted = tag.getBooleanOr("EverCrafted", false);
        craftInterval = tag.getIntOr("CraftInterval", 0);
        ticksSinceCraft = tag.getIntOr("TicksSinceCraft", 0);
        probeUnits = Math.max(FIRST_PROBE_UNITS, Math.min(MAX_PROBE_UNITS, tag.getIntOr("ProbeUnits", 0)));
        settleTicks = tag.getIntOr("SettleTicks", 0);

        IItemHandler handler = mirror.handler();
        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            int target = phantomTarget[gridSlot];
            if (target < 0) continue;
            ItemStack source = phantomSource[gridSlot];
            if (target >= handler.getSlots() || source == null || source.isEmpty()
                    || !ItemStack.isSameItem(handler.getStackInSlot(target), source)) {
                phantomTarget[gridSlot] = -1;
                phantomSource[gridSlot] = null;
            }
        }

        IFluidHandler fluids = mirror.fluidHandler();
        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            int target = phantomFluidTarget[simTank];
            if (target < 0) continue;
            FluidStack source = phantomFluidSource[simTank];
            if (fluids == null || target >= fluids.getTanks() || source.isEmpty()
                    || !FluidStack.isSameFluidSameComponents(fluids.getFluidInTank(target), source)) {
                phantomFluidTarget[simTank] = -1;
                phantomFluidSource[simTank] = FluidStack.EMPTY;
            }
        }

        heldPhantoms = holdsPhantoms();
    }

    private static ListTag saveStacks(ItemStack[] stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            CompoundTag entry = new CompoundTag();
            if (stack != null && !stack.isEmpty()) {
                entry = NbtCompat.saveStack(registries, stack);
            }
            list.add(entry);
        }
        return list;
    }

    private static void loadStacks(ListTag list, ItemStack[] into, HolderLookup.Provider registries,
                                   @Nullable ItemStack absent) {
        for (int index = 0; index < into.length; index++) {
            CompoundTag entry = index < list.size() ? list.getCompoundOrEmpty(index) : new CompoundTag();
            ItemStack stack = entry.isEmpty() ? ItemStack.EMPTY : NbtCompat.parseStack(registries, entry);
            into[index] = stack.isEmpty() ? absent : stack;
        }
    }

    private static ListTag saveFluids(FluidStack[] fluids, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (FluidStack fluid : fluids) {
            CompoundTag entry = new CompoundTag();
            if (fluid != null && !fluid.isEmpty()) {
                entry = NbtCompat.saveWith(FluidStack.CODEC, registries, fluid);
            }
            list.add(entry);
        }
        return list;
    }

    private static void loadFluids(ListTag list, FluidStack[] into, HolderLookup.Provider registries) {
        for (int index = 0; index < into.length; index++) {
            CompoundTag entry = index < list.size() ? list.getCompoundOrEmpty(index) : new CompoundTag();
            into[index] = entry.isEmpty() ? FluidStack.EMPTY : NbtCompat.parseWith(FluidStack.CODEC, registries, entry).orElse(FluidStack.EMPTY);
        }
    }

    private static void readInts(CompoundTag tag, String key, int[] into, int absent) {
        int[] saved = tag.getIntArray(key).orElse(new int[0]);
        for (int index = 0; index < into.length; index++) {
            into[index] = index < saved.length ? saved[index] : absent;
        }
    }

    /** Machine slots currently holding phantom copies, so they can be stripped before saving. */
    public int[] phantomSlots() {
        int count = 0;
        for (int target : phantomTarget) {
            if (target >= 0) count++;
        }
        int[] slots = new int[count];
        int index = 0;
        for (int target : phantomTarget) {
            if (target >= 0) slots[index++] = target;
        }
        return slots;
    }

    /** Machine tanks currently holding phantom fluid, the fluid counterpart of {@link #phantomSlots()}. */
    public int[] phantomTanks() {
        int count = 0;
        for (int target : phantomFluidTarget) {
            if (target >= 0) count++;
        }
        int[] tanks = new int[count];
        int index = 0;
        for (int target : phantomFluidTarget) {
            if (target >= 0) tanks[index++] = target;
        }
        return tanks;
    }

    private boolean holdsPhantoms() {
        for (int target : phantomTarget) {
            if (target >= 0) return true;
        }
        for (int target : phantomFluidTarget) {
            if (target >= 0) return true;
        }
        return false;
    }

    public int progress() {
        // Before the first cycle has been timed there is nothing to measure against, so show empty
        // rather than a bar that sits full for the length of the machine's first job.
        return craftInterval < 1 ? 0 : Math.min(ticksSinceCraft, craftInterval);
    }

    public int maxProgress() {
        return Math.max(1, craftInterval);
    }

    /** Advances the simulation by one game tick and returns the status code to display. */
    public int tick(VirtualMachineMirror mirror) {
        // Reported once per game tick rather than per machine tick: an overclocked simulator runs the
        // machine several times a tick, and reporting each of those separately both hid all but the
        // last and made the rolling average span a fraction of the twenty ticks it claims. Recording
        // unconditionally also means the readout falls back to zero when the machine stops, instead of
        // freezing on whatever it was drawing when it went quiet.
        chargedThisTick = 0;
        effectMachine = mirror.runsEffect();
        int status = present(advance(mirror));
        be.recordPowerUse(chargedThisTick);
        // Flux goes out once a second, as the other machines do it: writing the 5x5 chunks round the
        // simulator every tick was most of a busy simulator's own cost, for the same total. A simulator
        // running inside another emits nothing itself; the outer one emits for everything it charges,
        // this one's draw included.
        if (!SimulationContext.active()) unemittedFe = saturatingAdd(unemittedFe, chargedThisTick);
        if (unemittedFe > 0 && be.getLevel() instanceof ServerLevel serverLevel
                && serverLevel.getGameTime() % FLUX_EMIT_INTERVAL == 0) {
            QuantumFlux.emitFromEnergy(serverLevel, be.getBlockPos(), unemittedFe);
            unemittedFe = 0;
        }
        return status;
    }

    /** Does the actual work of a tick and returns the raw, un-smoothed status. */
    private int advance(VirtualMachineMirror mirror) {
        // No block entity ticker means the block is driven by something other than the world's tick,
        // typically a mod's own network. Nothing this engine does can make such a machine run.
        if (!mirror.canTick()) return STATUS_NO_TICKER;
        if (!payEnergyDebt()) return STATUS_NO_POWER;

        // Finished work is still sitting in the machine waiting for grid space; clear it first.
        if (stalledDelta != null) {
            int status = applyDelta(mirror, stalledDelta);
            if (status != STATUS_WORKING) return status;
        }

        // A machine that swallowed its ingredients but never delivered is given up on eventually, so a
        // job it silently abandoned cannot keep the simulation running for good.
        if (awaitingWork && ticksSinceCraft > ABANDON_TICKS) awaitingWork = false;

        boolean projectedItems = projectPhantoms(mirror);
        boolean projectedFluids = projectFluids(mirror);
        // Fuel keeps flowing long after the ingredient it was for has run out, so a steam macerator
        // with an empty grid still looks like it is being fed. A machine that has been seen to eat
        // items per craft cannot start one on fluid alone, and counting it as fed anyway is what let
        // one grind its way through a slot of leftover copies on the player's steam.
        boolean projected = projectedItems || (projectedFluids && !eatsGridItems());
        fundableThisTick = projected;
        refreshCleanSnapshot(mirror);
        // Fuel the machine cannot start a craft with is not an offer it is refusing, so it reads as
        // idle rather than as a machine with nowhere to put what it has been given.
        int idleStatus = (sawItems || (sawFluids && !eatsGridItems())) ? STATUS_NO_SLOTS : STATUS_IDLE;

        // Whatever the machine was making of its last offer, it is a different question once the grid
        // changes under it. This is what lets a stalled machine be tried again: add the fuel it was
        // missing, or swap the ingredient for one it knows, and it starts being ticked from scratch.
        long offer = offerFingerprint();
        if (offer != offeredInputs) {
            // A different ingredient is a different job: what the last one had to be stocked at says
            // nothing about this one, so the offer starts small again rather than dumping a stack into
            // a machine that may never hand it back.
            if ((offer >>> 32) != (offeredInputs >>> 32)) probeUnits = FIRST_PROBE_UNITS;
            offeredInputs = offer;
            quietTicks = 0;
            inertTicks = 0;
            strandedWork = false;
        }

        // A machine with nowhere to put ingredients never needs feeding: a cobblestone generator, a farm,
        // anything that makes something out of nothing. There is no purchase to fund and so nothing to
        // cap, which means every lane is live for as long as it is contained, and it is run and billed as
        // working rather than as a machine winding down. Left alone entirely once it has gone silent for
        // long enough to be sure it is not simply slow, so a block we have misread cannot draw power for
        // ever with nothing to show for it.
        // An effect machine (or a simulator running one) is fed the same way: it has nothing to buy. It is
        // never given up on, since drawing nothing can be its job: a Flux Maintainer holding its floor
        // spends nothing until the field sags, and a machine left unticked would never see it sag.
        boolean selfFeeding = mirror.runsEffect() || (!mirror.hasInputSlots() && quietTicks < ABANDON_TICKS);

        // A machine part way through a job has already swallowed its ingredients and the player has
        // already been charged for them, so it keeps being ticked even with nothing left to feed it, as
        // does one still holding goods that have not been banked. Freezing it in either state is what
        // made a macerator hand its last dust to the world on disengage.
        boolean nothingToFund = !projected && !awaitingWork && !selfFeeding && !hasUnharvestedGoods(mirror);
        windDownTicks = nothingToFund ? windDownTicks + 1 : 0;

        if (nothingToFund) {
            if (phantomSlots().length > 0) {
                // Copies the machine would not hand back are still in its input slots, so it cannot be
                // ticked without it grinding away at work nobody paid for, and they block whatever the
                // player wants to put there next. Nothing out here can shift them either: a machine only
                // lets go of an input slot by consuming it. Rewinding to its last copy-free state is
                // exactly what breaking containment and putting it back does, which is what this dead
                // end used to need by hand. Nothing is at stake in the rewind: copies cost nothing, and
                // there is provably no funded work in flight and nothing unbanked at this point.
                be.requestMachineRewind();
                return idleStatus;
            }
            // The machine is genuinely empty, so anything it does from here is its own business: bleeding
            // off an overclock, clearing a recipe, running down a lock. It is left ticking so that plays
            // out as it would in the world, and billed for exactly what it draws while it does. The
            // window is counted from when the grid ran dry rather than from the machine's last flicker
            // of activity: a steam machine sipping its tank is activity, and letting that restart the
            // count is how one kept itself alive on the player's fuel indefinitely.
            if (windDownTicks >= WIND_DOWN_TICKS) return idleStatus;
        }

        // The machine is holding everything the grid can offer it and has done absolutely nothing with
        // it: no power drawn, no item moved, and the offer already widened as far as it goes. That is
        // not a machine still working out its timing, it is one with no use for what it was given —
        // dust in a macerator, ore in an unlit furnace. Left as "calibrating" it would sit there lit up
        // as though it were busy, so it is parked here until the grid changes.
        if (projected && !awaitingWork && !selfFeeding && inertTicks >= STALL_TICKS) {
            return STATUS_STALLED;
        }

        int speed = Math.max(1, be.getSpeedMultiplier());

        int status = projected || awaitingWork || selfFeeding ? STATUS_WORKING : idleStatus;
        for (int i = 0; i < speed; i++) {
            // Overclocked simulators run several machine ticks per game tick, so the machine is
            // re-stocked between them instead of starving until the next game tick.
            if (i > 0) {
                projectPhantoms(mirror);
                projectFluids(mirror);
            }

            ItemStack[] before = snapshot(mirror);
            FluidStack[] fluidsBefore = mirror.snapshotFluids();
            mirror.beginEnergySample();
            SimulationContext.run(be.getBatchSize(), () -> be.runGuardedVirtualTick(mirror));
            long measuredFe = mirror.finishEnergySampleFe();
            ticksSinceCraft++;

            TickDelta delta = measure(mirror, before, fluidsBefore);
            boolean busy = awaitingWork || selfFeeding || !delta.isEmpty();
            // Machines with no energy interface of their own are charged a flat rate, but only for ticks
            // they are actually working: a flat rate for standing still would bill an idle simulator.
            delta.energyPerCopy = mirror.usesPower() ? measuredFe : (busy ? BASELINE_FE_PER_TICK : 0);
            if (delta.isEmpty()) {
                // A tick spent grinding away at a job the grid has already been charged for is paid for
                // by every lane, because every lane gets the output. A machine with no job in flight is
                // running its own internals, so that is one machine's upkeep no matter how wide the
                // batch: billing it per lane is what made a finished 8x batch look like it was still
                // drawing full power long after the last craft.
                int lanes = awaitingWork || selfFeeding ? activeCopies() : 1;
                if (!chargeEnergy(costWithOverhead(delta.energyPerCopy, lanes))) return STATUS_NO_POWER;
                boolean stirring = delta.energyPerCopy > 0;
                quietTicks = stirring ? 0 : quietTicks + 1;
                inertTicks = stirring ? 0 : inertTicks + 1;
                // Offered copies the machine has not touched in a while: it may need more of them in one
                // slot than a single craft's worth to recognise its recipe at all.
                if (projected && !awaitingWork && quietTicks >= PROBE_PATIENCE) widenProbe();
                continue;
            }
            quietTicks = 0;
            inertTicks = 0;

            // Ingredients drawn from the grid this tick are charged at the same multiple the work is,
            // and a job already funded is owed to every lane. A machine that needs no ingredients has
            // nothing to fund in the first place. Anything else came out of the machine's own pocket.
            delta.paidFor = delta.tookIngredients() || awaitingWork || selfFeeding;

            int applied = applyDelta(mirror, delta);
            if (applied != STATUS_WORKING) return applied;
            status = STATUS_WORKING;
        }
        return mirror.tickFailed() ? STATUS_NO_RECIPE : status;
    }

    /**
     * Smooths the raw status so the display does not flicker.
     *
     * <p>A machine finishing an internal pass churns between working and "nothing to do" for a moment,
     * which read as a stutter of IDLE/LOW INPUT. So after any real work the machine is granted a grace
     * period during which those quiet statuses are still shown as working, letting it settle into a
     * single steady state. Until its very first craft lands, that working state reads as
     * {@code INITIALIZING} to signal it is still calibrating the cycle length.
     */
    private int present(int raw) {
        // A machine whose work is an effect (a Zeno Field Controller) never crafts, so it has no cycle
        // to calibrate: it would read "Calibrating" for as long as it ran.
        int working = everCrafted || effectMachine ? STATUS_WORKING : STATUS_INITIALIZING;
        if (raw == STATUS_WORKING) {
            settleTicks = settleWindow();
            return working;
        }
        if (raw == STATUS_IDLE) {
            // Nothing left in the grid and nothing in flight, so the run really is over. Spending the
            // grace period here is what kept a finished batch reading as online for a whole cycle's
            // worth of ticks after the last craft landed.
            settleTicks = 0;
            return raw;
        }
        if (isQuiet(raw) && settleTicks > 0) {
            settleTicks--;
            return working;
        }
        return raw;
    }

    /**
     * Statuses that mean "nothing routable this instant" while there is still work on the table, so
     * they are safe to hold through the grace period rather than shown as a stutter.
     */
    private static boolean isQuiet(int status) {
        return status == STATUS_NO_SLOTS || status == STATUS_LOW_INPUT;
    }

    /** Grace period sized to comfortably span one internal machine pass, with sane bounds. */
    private int settleWindow() {
        return Math.min(MAX_SETTLE_TICKS, Math.max(20, craftInterval + 4));
    }

    /**
     * Whether a craft has ever been seen to draw items out of the grid. Such a machine is only worth
     * feeding while the grid holds items, however much fuel is still on offer.
     */
    private boolean eatsGridItems() {
        for (int draw : craftDraw) {
            if (draw > 0) return true;
        }
        return false;
    }

    /**
     * Whether the machine is still holding anything of the player's, i.e. anything that is not a
     * phantom copy. Only that is worth running the machine on with an empty grid.
     *
     * <p>Phantom copies are deliberately excluded, because they routinely outlast the items that paid
     * for them. One draw of a single item costs the grid a whole batch, so a slot holding sixty-four
     * copies is emptied by the grid long before the machine has chewed through them, and machines that
     * refuse extraction from their input slots (Modern Industrialization among them) will not hand the
     * surplus back. Treating those leftovers as a reason to keep running is what left a finished batch
     * grinding away at copies nobody paid for, billing real power for a machine climbing its own
     * overclock curve with the grid sitting empty. They are simply left where they are: they cost
     * nothing, and the moment the grid can pay again they are the stock it pays for.
     */
    private boolean hasUnharvestedGoods(VirtualMachineMirror mirror) {
        IItemHandler handler = mirror.handler();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) continue;
            if (gridSlotFeeding(slot) >= 0) continue;
            return true;
        }
        return false;
    }

    // --- Phantom projection -------------------------------------------------------------------

    /**
     * Mirrors the grid's input stacks into the machine, topping up phantoms that are already resident
     * rather than replacing them. Replacing a stack resets machine progress, and some machines hold on
     * to the exact stack they were given, so the copies are left alone for as long as possible.
     */
    private boolean projectPhantoms(VirtualMachineMirror mirror) {
        sawItems = false;
        routedAnything = false;

        IItemHandler handler = mirror.handler();
        ItemStackHandler grid = be.getInventory();
        List<SlotMapping> mappings = be.getSlotMappings();
        boolean[] used = new boolean[mirror.slotCount()];

        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            int target = phantomTarget[gridSlot];
            if (target >= 0 && target < used.length) used[target] = true;
        }

        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            SlotMapping mapping = mappings.get(gridSlot);
            ItemStack real = grid.getStackInSlot(gridSlot);

            if (mapping.locked() || real.isEmpty()) {
                releasePhantom(mirror, gridSlot, used);
                continue;
            }
            sawItems = true;

            int target = phantomTarget[gridSlot];
            if (target >= 0) {
                ItemStack resident = handler.getStackInSlot(target);
                if (resident.isEmpty() || !ItemStack.isSameItem(resident, real)) {
                    releasePhantom(mirror, gridSlot, used);
                    if (phantomTarget[gridSlot] >= 0) {
                        // The machine would not give the old copy back, so this grid slot stays tied
                        // to it. Re-binding now would leave that leftover unattributed, and whatever
                        // the machine turns it into would be banked as if it came from nowhere.
                        continue;
                    }
                    target = -1;
                }
            }
            if (target < 0) {
                target = chooseTarget(mirror, mapping, real, used);
                if (target < 0) continue;
            }

            ItemStack resident = handler.getStackInSlot(target);
            int limit = Math.min(safeSlotLimit(handler, target), real.getMaxStackSize());
            int wanted = Math.min(fundableCopies(gridSlot, real.getCount()), limit);
            int missing = wanted - resident.getCount();
            try {
                if (missing > 0) {
                    // Keep the resident copy's own components: the machine owns them while it works.
                    handler.insertItem(target, (resident.isEmpty() ? real : resident).copyWithCount(missing), false);
                } else if (missing < 0) {
                    // The machine is holding more copies than the grid can still pay for, because a
                    // draw of one item costs the grid several. The surplus is taken back so it can
                    // never start a job that would have to be thrown away unpaid.
                    handler.extractItem(target, -missing, false);
                }
            } catch (Throwable ignored) {
                // Refusals are handled by the emptiness check below.
            }

            if (handler.getStackInSlot(target).isEmpty()) continue;

            phantomTarget[gridSlot] = target;
            phantomSource[gridSlot] = real.copyWithCount(1);
            used[target] = true;
            routedAnything = true;
        }

        return routedAnything;
    }

    /**
     * Whenever the machine is provably phantom-free, record it as the state to fall back on if a later
     * withdrawal fails. Without this the fallback stays pinned to the moment of containment and
     * rewinding to it hands back items the player has already been given.
     */
    private void refreshCleanSnapshot(VirtualMachineMirror mirror) {
        boolean holds = holdsPhantoms();
        if (!holds && heldPhantoms) mirror.captureClean();
        heldPhantoms = holds;
    }

    /**
     * Mirrors simulator input tanks into the machine's fluid handler. Fill routing is left to the
     * machine (MI-style unique-fluid tanks); whichever tank grew is recorded as the phantom target.
     */
    private boolean projectFluids(VirtualMachineMirror mirror) {
        sawFluids = false;
        routedFluids = false;
        IFluidHandler handler = mirror.fluidHandler();
        if (handler == null) return false;

        SimulatorFluidTanks tanks = be.getFluidTanks();
        List<FluidMapping> mappings = be.getFluidMappings();
        boolean[] used = new boolean[Math.max(1, mirror.tankCount())];
        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            int target = phantomFluidTarget[simTank];
            if (target >= 0 && target < used.length) used[target] = true;
        }

        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            FluidMapping mapping = mappings.get(simTank);
            FluidStack real = tanks.getFluid(simTank);
            if (mapping.locked() || real.isEmpty()) {
                releasePhantomFluid(mirror, simTank);
                continue;
            }
            sawFluids = true;

            int perCraft = fluidCraftDraw[simTank] > 0 ? fluidCraftDraw[simTank] : Math.min(1000, probeUnits * 100);
            int batch = Math.max(1, be.getBatchSize());
            int wanted = Math.min(real.getAmount(), Math.max(perCraft, real.getAmount() / batch));
            if (wanted <= 0) continue;

            int target = phantomFluidTarget[simTank];
            if (mapping.isBound()) {
                target = mapping.targetTankIndex();
                if (target < 0 || target >= handler.getTanks() || used[target] && phantomFluidTarget[simTank] != target) {
                    continue;
                }
            } else if (target < 0 || !FluidStack.isSameFluidSameComponents(handler.getFluidInTank(target), real)
                    && !handler.getFluidInTank(target).isEmpty()) {
                target = -1;
            }

            int currently = 0;
            if (target >= 0 && target < handler.getTanks()) {
                FluidStack resident = handler.getFluidInTank(target);
                if (!resident.isEmpty() && FluidStack.isSameFluidSameComponents(resident, real)) {
                    currently = resident.getAmount();
                } else if (!resident.isEmpty()) {
                    target = -1;
                }
            }
            int missing = wanted - currently;
            if (missing < 0) {
                mirror.drainFluid(real.copyWithAmount(-missing), IFluidHandler.FluidAction.EXECUTE);
                phantomFluidTarget[simTank] = target;
                phantomFluidSource[simTank] = real.copyWithAmount(1);
                routedFluids = true;
                continue;
            }
            if (missing <= 0) {
                phantomFluidTarget[simTank] = target;
                phantomFluidSource[simTank] = real.copyWithAmount(1);
                if (target >= 0 && target < used.length) used[target] = true;
                routedFluids = true;
                continue;
            }

            FluidStack[] before = mirror.snapshotFluids();
            int filled = mirror.fillFluid(real.copyWithAmount(missing), IFluidHandler.FluidAction.EXECUTE);
            if (filled <= 0 && currently <= 0) continue;

            FluidStack[] after = mirror.snapshotFluids();
            if (target < 0) {
                for (int tank = 0; tank < after.length; tank++) {
                    int grew = after[tank].getAmount() - (tank < before.length ? before[tank].getAmount() : 0);
                    if (grew > 0 && FluidStack.isSameFluidSameComponents(after[tank], real)) {
                        target = tank;
                        break;
                    }
                }
            }
            if (target < 0 && currently > 0) continue;
            if (target < 0) target = 0;
            phantomFluidTarget[simTank] = target;
            phantomFluidSource[simTank] = real.copyWithAmount(1);
            if (target < used.length) used[target] = true;
            routedFluids = true;
        }
        return routedFluids;
    }

    private void releasePhantomFluid(VirtualMachineMirror mirror, int simTank) {
        int target = phantomFluidTarget[simTank];
        if (target < 0) {
            phantomFluidSource[simTank] = FluidStack.EMPTY;
            return;
        }
        FluidStack source = phantomFluidSource[simTank];
        if (!source.isEmpty() && mirror.fluidHandler() != null) {
            FluidStack held = mirror.fluidHandler().getFluidInTank(target);
            if (!held.isEmpty() && FluidStack.isSameFluidSameComponents(held, source)) {
                // Phantoms are unpaid copies; discard them rather than returning them to the tank.
                mirror.drainFluid(held.copy(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        phantomFluidTarget[simTank] = -1;
        phantomFluidSource[simTank] = FluidStack.EMPTY;
    }

    /**
     * How many copies of a grid slot's stack the machine is allowed to hold.
     *
     * <p>Handing it the whole stack is what stranded copies in it. One item drawn costs the grid a whole
     * batch, so a slot stocked with sixty-four copies is emptied by the grid after eight draws at 8x,
     * with fifty-six copies left over that machines refusing extraction from their input slots will never
     * give back. Stocking only what the grid can pay for means the two run out together.
     *
     * <p>A slot still needs enough in it for the machine to recognise its recipe, which can be several
     * items per craft. Until that draw has been seen the offer starts small and {@link #widenProbe()}
     * grows it if the machine does not bite.
     */
    private int fundableCopies(int gridSlot, int available) {
        int batch = Math.max(1, be.getBatchSize());
        int perCraft = craftDraw[gridSlot] > 0 ? craftDraw[gridSlot] : probeUnits;
        return Math.min(available, Math.max(perCraft, available / batch));
    }

    /**
     * A cheap fingerprint of the grid: which ingredients are on offer, in the high half, and how full
     * every slot is, inputs and outputs alike, in the low half. Any difference between two ticks means
     * the grid has been touched, which is the one thing that can make a machine that has been ignoring
     * it worth ticking again: a missing ingredient added, or room made for what it wants to produce.
     * The halves are kept apart because a different ingredient additionally invalidates everything
     * learned about how many copies the last one needed.
     */
    private long offerFingerprint() {
        ItemStackHandler grid = be.getInventory();
        SimulatorFluidTanks tanks = be.getFluidTanks();
        int items = 1;
        int counts = 1;
        for (int slot = 0; slot < grid.getSlots(); slot++) {
            ItemStack stack = grid.getStackInSlot(slot);
            counts = counts * 31 + stack.getCount();
            if (slot < gridInputs) items = items * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode());
        }
        for (int tank = 0; tank < SimulatorFluidTanks.TANK_COUNT; tank++) {
            FluidStack fluid = tanks.getFluid(tank);
            counts = counts * 31 + fluid.getAmount();
            if (tank < SimulatorFluidTanks.INPUT_TANKS) {
                items = items * 31 + (fluid.isEmpty() ? 0 : System.identityHashCode(fluid.getFluid()));
            }
        }
        return ((long) items << 32) | (counts & 0xFFFFFFFFL);
    }

    /** Doubles how many copies an unproven slot is stocked with, up to a full stack. */
    private void widenProbe() {
        if (probeUnits >= MAX_PROBE_UNITS) return;
        probeUnits = Math.min(MAX_PROBE_UNITS, probeUnits * 2);
        quietTicks = 0;
    }

    private int chooseTarget(VirtualMachineMirror mirror, SlotMapping mapping, ItemStack real, boolean[] used) {
        // A pinned slot is honoured strictly. When it is busy or will not take the item this grid slot
        // simply waits its turn: the whole point of pinning is knowing where an ingredient ends up, and
        // quietly rerouting to some other input is how items meant for one ingredient landed in the slot
        // for another. Several grid slots may share one machine slot, and they queue up behind it.
        if (mapping.isBound()) {
            return isViable(mirror, mapping.targetSlotIndex(), real, used) ? mapping.targetSlotIndex() : -1;
        }

        int occupied = -1;
        for (int slot = 0; slot < mirror.slotCount(); slot++) {
            if (!mirror.acceptsInput(slot) || !isViable(mirror, slot, real, used)) continue;
            if (mirror.handler().getStackInSlot(slot).isEmpty()) return slot;
            if (occupied < 0) occupied = slot;
        }
        if (occupied >= 0) return occupied;

        // No known input takes it. The slots were sorted with a handful of vanilla probe items, which
        // a machine that only takes its own items refuses everywhere; so ask with the real item, and
        // learn the first slot that takes it. The lowest slot wins, as inputs usually come first.
        for (int slot = 0; slot < mirror.slotCount(); slot++) {
            if (!mirror.canLearnInput(slot) || !isViable(mirror, slot, real, used)) continue;
            mirror.learnInput(slot);
            return slot;
        }
        return -1;
    }

    private boolean isViable(VirtualMachineMirror mirror, int slot, ItemStack real, boolean[] used) {
        if (slot < 0 || slot >= used.length || used[slot]) return false;
        ItemStack held = mirror.handler().getStackInSlot(slot);
        if (!held.isEmpty() && !ItemStack.isSameItem(held, real)) return false;
        return mirror.accepts(slot, real);
    }

    /**
     * Drops the phantom for a grid slot. Phantom copies are simply deleted; anything the machine turned
     * them into is banked into the output grid so it cannot vanish.
     *
     * <p>If the machine will not give the slot back, the binding is kept rather than abandoned. An
     * abandoned phantom would look like the machine's own stock, and anything it was turned into would
     * be banked as free output. Holding on to it means the cost is still charged to this grid slot.
     */
    private void releasePhantom(VirtualMachineMirror mirror, int gridSlot, boolean[] used) {
        int target = phantomTarget[gridSlot];
        if (target < 0) return;

        IItemHandler handler = mirror.handler();
        ItemStack resident = handler.getStackInSlot(target);
        ItemStack source = phantomSource[gridSlot];

        if (!resident.isEmpty()) {
            if (source != null && ItemStack.isSameItem(resident, source)) {
                VirtualMachineMirror.clearSlot(handler, target);
            } else {
                ItemStack recovered = resident.copy();
                if (insertIntoOutputGrid(recovered, true) == recovered.getCount()) {
                    VirtualMachineMirror.clearSlot(handler, target);
                    // Banked only once the machine has actually let go. Banking first would hand the
                    // player a copy of goods a handler that refuses to be emptied is still holding.
                    if (handler.getStackInSlot(target).isEmpty()) insertIntoOutputGrid(recovered, false);
                }
            }
            if (!handler.getStackInSlot(target).isEmpty()) return;
        }

        phantomTarget[gridSlot] = -1;
        if (target < used.length) used[target] = false;
        phantomSource[gridSlot] = null;
        // The pinned multiple deliberately survives: the machine may still be running on work this
        // slot paid for, and forgetting what it paid would let that work be multiplied for free.
    }

    /**
     * Wipes every phantom out of the machine and reports whether it worked. Machines that refuse
     * extraction from their input slots keep theirs, which the caller has to account for before the
     * machine is serialized or handed back to the world.
     */
    public boolean withdrawPhantoms(VirtualMachineMirror mirror) {
        int[] targets = phantomSlots();
        boolean[] used = new boolean[Math.max(1, mirror.slotCount())];
        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            releasePhantom(mirror, gridSlot, used);
        }
        boolean itemsClean = mirror.areSlotsEmpty(targets);
        return withdrawPhantomFluids(mirror) && itemsClean;
    }

    /**
     * Deletes the phantom fluid copies, then banks whatever the machine is still holding into the
     * simulator tanks. Without this a machine handed back to the world keeps a tank of unpaid copies
     * and whatever it made from them.
     */
    private boolean withdrawPhantomFluids(VirtualMachineMirror mirror) {
        if (mirror.fluidHandler() == null) return true;
        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            releasePhantomFluid(mirror, simTank);
        }
        return be.drainMachineFluids(mirror);
    }

    // --- Measurement --------------------------------------------------------------------------

    /** What a single tick of the machine changed. */
    private final class TickDelta {
        private final int[] consumed = new int[gridInputs];
        private final int[] damage = new int[gridInputs];
        private final ItemStack[] stateBefore = new ItemStack[gridInputs];
        private final ItemStack[] stateAfter = new ItemStack[gridInputs];
        private final Map<Integer, ItemStack> outputs = new LinkedHashMap<>();
        private final int[] fluidConsumed = new int[SimulatorFluidTanks.INPUT_TANKS];
        private final Map<Integer, FluidStack> fluidOutputs = new LinkedHashMap<>();
        /** The machine worked on a leftover copy that no longer matches what the grid slot holds. */
        private boolean strayIngredient;
        private long energyPerCopy;
        /** Whether the grid paid for this tick's work, which is what licenses multiplying it. */
        private boolean paidFor;

        boolean tookIngredients() {
            return tookGridItems() || tookGridFluids();
        }

        /** Items drawn out of the grid, as opposed to fuel or coolant alone. */
        boolean tookGridItems() {
            for (int amount : consumed) {
                if (amount > 0) return true;
            }
            return false;
        }

        private boolean tookGridFluids() {
            for (int amount : fluidConsumed) {
                if (amount > 0) return true;
            }
            return false;
        }

        boolean damagedTool() {
            for (int amount : damage) {
                if (amount != 0) return true;
            }
            return false;
        }

        boolean changedItemState() {
            for (ItemStack stack : stateAfter) {
                if (stack != null) return true;
            }
            return false;
        }

        boolean isEmpty() {
            return outputs.isEmpty() && fluidOutputs.isEmpty()
                    && !tookIngredients() && !damagedTool() && !changedItemState();
        }

        /**
         * Whether this tick finished something. A stack the machine merely stamped progress onto while
         * taking ingredients does not count: machines routinely note down an iteration on the way in,
         * and treating that as finished work would let the next round be charged for twice.
         */
        boolean didWork() {
            return !outputs.isEmpty() || !fluidOutputs.isEmpty()
                    || damagedTool() || (changedItemState() && !tookIngredients());
        }

        /** Ingredients taken with nothing to show for it: the machine is buying its next round. */
        boolean startsNewWork() {
            return tookIngredients() && outputs.isEmpty() && fluidOutputs.isEmpty() && !damagedTool();
        }
    }

    private ItemStack[] snapshot(VirtualMachineMirror mirror) {
        IItemHandler handler = mirror.handler();
        ItemStack[] snapshot = new ItemStack[handler.getSlots()];
        for (int slot = 0; slot < snapshot.length; slot++) {
            snapshot[slot] = handler.getStackInSlot(slot).copy();
        }
        return snapshot;
    }

    private TickDelta measure(VirtualMachineMirror mirror, ItemStack[] before, FluidStack[] fluidsBefore) {
        TickDelta delta = new TickDelta();
        IItemHandler handler = mirror.handler();
        int slots = Math.min(before.length, handler.getSlots());

        for (int slot = 0; slot < slots; slot++) {
            ItemStack was = before[slot];
            ItemStack now = handler.getStackInSlot(slot);
            int gridSlot = gridSlotFeeding(slot);

            if (gridSlot >= 0) {
                // A copy the machine would not hand back keeps its binding, so it can outlive the item
                // the player had in that slot. Anything done with it must not be billed to whatever is
                // sitting there now, or swapping iron for gold pays for iron dust with gold.
                ItemStack owner = be.getInventory().getStackInSlot(gridSlot);
                if (!owner.isEmpty() && !ItemStack.isSameItem(owner, was)) {
                    delta.strayIngredient = true;
                }

                if (now.isEmpty()) {
                    delta.consumed[gridSlot] += was.getCount();
                    phantomTarget[gridSlot] = -1;
                } else if (ItemStack.isSameItem(now, was)) {
                    int eaten = was.getCount() - now.getCount();
                    if (eaten > 0) {
                        delta.consumed[gridSlot] += eaten;
                    } else if (eaten < 0) {
                        recordProduced(delta.outputs, slot, now.copyWithCount(-eaten));
                    }
                    if (now.isDamageableItem()) {
                        delta.damage[gridSlot] += now.getDamageValue() - was.getDamageValue();
                    }
                    if (!Objects.equals(was.getComponentsPatch(), now.getComponentsPatch())) {
                        delta.stateBefore[gridSlot] = was.copyWithCount(1);
                        delta.stateAfter[gridSlot] = now.copyWithCount(1);
                    }
                } else {
                    // The machine swapped the phantom for something else (a bucket, a husk, a mould).
                    delta.consumed[gridSlot] += was.getCount();
                    recordProduced(delta.outputs, slot, now.copy());
                    phantomTarget[gridSlot] = -1;
                }
                continue;
            }

            if (now.isEmpty()) continue;
            int gained = ItemStack.isSameItemSameComponents(now, was) ? now.getCount() - was.getCount() : now.getCount();
            if (gained > 0) {
                recordProduced(delta.outputs, slot, now.copyWithCount(gained));
                mirror.markAsOutput(slot);
            }
        }

        FluidStack[] fluidsNow = mirror.snapshotFluids();
        int tanks = Math.max(fluidsBefore.length, fluidsNow.length);
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack was = tank < fluidsBefore.length ? fluidsBefore[tank] : FluidStack.EMPTY;
            FluidStack now = tank < fluidsNow.length ? fluidsNow[tank] : FluidStack.EMPTY;
            int simTank = fluidTankFeeding(tank);

            if (simTank >= 0) {
                if (now.isEmpty()) {
                    delta.fluidConsumed[simTank] += was.getAmount();
                    phantomFluidTarget[simTank] = -1;
                } else if (FluidStack.isSameFluidSameComponents(now, was)) {
                    int eaten = was.getAmount() - now.getAmount();
                    if (eaten > 0) delta.fluidConsumed[simTank] += eaten;
                    else if (eaten < 0) {
                        recordFluidProduced(delta.fluidOutputs, tank, now.copyWithAmount(-eaten));
                    }
                } else {
                    delta.fluidConsumed[simTank] += was.getAmount();
                    if (!now.isEmpty()) recordFluidProduced(delta.fluidOutputs, tank, now.copy());
                    phantomFluidTarget[simTank] = -1;
                }
                continue;
            }

            if (now.isEmpty()) continue;
            int gained = FluidStack.isSameFluidSameComponents(now, was)
                    ? now.getAmount() - was.getAmount()
                    : now.getAmount();
            if (gained > 0) {
                recordFluidProduced(delta.fluidOutputs, tank, now.copyWithAmount(gained));
                mirror.markFluidAsOutput(tank);
            }
        }
        return delta;
    }

    private static void recordFluidProduced(Map<Integer, FluidStack> into, int machineTank, FluidStack stack) {
        FluidStack existing = into.get(machineTank);
        if (existing != null && FluidStack.isSameFluidSameComponents(existing, stack)) {
            existing.grow(stack.getAmount());
        } else {
            into.put(machineTank, stack);
        }
    }

    private int fluidTankFeeding(int machineTank) {
        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            if (phantomFluidTarget[simTank] == machineTank) return simTank;
        }
        return -1;
    }

    private static void recordProduced(Map<Integer, ItemStack> into, int machineSlot, ItemStack stack) {
        ItemStack existing = into.get(machineSlot);
        if (existing != null && ItemStack.isSameItemSameComponents(existing, stack)) {
            existing.setCount(existing.getCount() + stack.getCount());
        } else {
            into.put(machineSlot, stack);
        }
    }

    private int gridSlotFeeding(int machineSlot) {
        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            if (phantomTarget[gridSlot] == machineSlot) return gridSlot;
        }
        return -1;
    }

    // --- Batch scaling and application --------------------------------------------------------

    /**
     * Multiplies one tick of machine behaviour by the batch size and commits it to the grid. Returns
     * {@link #STATUS_WORKING} on success, otherwise the reason it could not be applied.
     */
    private int applyDelta(VirtualMachineMirror mirror, TickDelta delta) {
        ItemStackHandler grid = be.getInventory();
        boolean buying = delta.startsNewWork();
        if (buying) observeRecipe(delta);

        int copies = Math.max(1, be.getBatchSize());
        // Work funded by an earlier purchase may not outgrow what that purchase paid for.
        if (!buying) copies = Math.min(copies, fundedMultiple());
        // Stock the machine was holding before containment is the machine's own, not the grid's, and
        // nothing was deducted for it. Multiplying what it turns into would be handing out eight ingots
        // for one ore, so unpaid work is banked one for one however wide the batch is set.
        if (!delta.paidFor) copies = Math.min(copies, 1);

        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            // What one craft draws from this slot is how much has to be kept stocked in the machine.
            craftDraw[gridSlot] = Math.max(craftDraw[gridSlot], delta.consumed[gridSlot]);
            if (delta.consumed[gridSlot] > 0) {
                copies = Math.min(copies, grid.getStackInSlot(gridSlot).getCount() / delta.consumed[gridSlot]);
            }
            if (delta.damage[gridSlot] > 0) {
                ItemStack real = grid.getStackInSlot(gridSlot);
                if (real.isDamageableItem()) {
                    int remaining = real.getMaxDamage() - real.getDamageValue();
                    copies = Math.min(copies, remaining / delta.damage[gridSlot]);
                }
            }
        }

        SimulatorFluidTanks tanks = be.getFluidTanks();
        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            fluidCraftDraw[simTank] = Math.max(fluidCraftDraw[simTank], delta.fluidConsumed[simTank]);
            if (delta.fluidConsumed[simTank] > 0) {
                copies = Math.min(copies, tanks.getFluid(simTank).getAmount() / delta.fluidConsumed[simTank]);
            }
        }

        long energyPerCopy = costWithOverhead(delta.energyPerCopy, 1);
        boolean shortOfPower = false;
        if (energyPerCopy > 0) {
            int affordable = (int) Math.min(Integer.MAX_VALUE,
                    be.getEnergyStorage().getEnergyStored() / energyPerCopy);
            shortOfPower = affordable < 1;
            copies = Math.min(copies, affordable);
        }

        // Work done on a leftover the grid can no longer account for is never charged and never paid
        // out; the machine simply grinds it away.
        if (delta.strayIngredient) copies = 0;
        // Fuel alone never buys a job. With nothing in flight and nothing the grid can start, a machine
        // drawing fuel is running on work nobody paid for — the steam a macerator burns chewing through
        // copies whose ingredients are gone — and booking that as a purchase is what left one claiming
        // a job in flight that did not exist, billing every lane for minutes.
        //
        // Two draws deliberately escape this. One that takes items from the grid is a real purchase,
        // capped by the grid's own stock a few lines above, and paying for it is how the machine gets
        // going again. One made while a funded job is in flight is that job's upkeep: refusing to pay
        // the steam a macerator needs to finish the batch it was paid for loses the player the batch.
        boolean unpaidUpkeep = !awaitingWork && (strandedWork || !fundableThisTick);
        if (buying && !delta.tookGridItems() && unpaidUpkeep) copies = 0;

        List<Harvest> plan = buildHarvestPlan(mirror, delta);
        int roomFor = largestBatchThatFits(plan, Math.max(copies, 0));
        if (roomFor < 1 && !plan.isEmpty() && copies >= 1) {
            // The goods exist but have nowhere to go. Leave them in the machine and retry.
            stalledDelta = delta;
            return STATUS_OUTPUT_FULL;
        }
        copies = Math.min(copies, roomFor);

        int fluidRoom = largestFluidBatchThatFits(delta, Math.max(copies, 0));
        if (fluidRoom < 1 && !delta.fluidOutputs.isEmpty() && copies >= 1) {
            stalledDelta = delta;
            return STATUS_OUTPUT_FULL;
        }
        copies = Math.min(copies, fluidRoom);

        if (copies < 1) {
            // Nothing here can be paid for, so the machine worked for nothing. Discarding is the only
            // honest option: none of it was ever deducted from the grid.
            discard(mirror, plan, delta);
            // Nothing was charged, so there is normally no job in flight to protect: claiming one would
            // keep the machine ticking, and billing, on ingredients the grid never paid for, and the
            // multiples are pinned at nothing so whatever it goes on to produce is not banked either.
            // The exception is a job paid for earlier that is only topping up fuel now. Cancelling that
            // mid-craft, or pinning its output away, is what made a macerator swallow its last eight
            // ore and deliver nothing.
            boolean upkeepOnAFundedJob = awaitingWork && !delta.tookGridItems();
            if (!upkeepOnAFundedJob) {
                pinMultiples(grid, delta, 0, buying);
                awaitingWork = false;
                // Short of power is a stall the player can fix by wiring the simulator up; anything
                // else here means the grid cannot pay at all and the machine is running on leftovers.
                strandedWork = !shortOfPower;
            }
            stalledDelta = null;
            return shortOfPower ? STATUS_NO_POWER : STATUS_LOW_INPUT;
        }

        pinMultiples(grid, delta, copies, buying);

        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            if (delta.consumed[gridSlot] > 0) {
                grid.extractItem(gridSlot, delta.consumed[gridSlot] * copies, false);
            }
            applyStateDelta(grid, delta, gridSlot, copies);
        }

        for (int simTank = 0; simTank < SimulatorFluidTanks.INPUT_TANKS; simTank++) {
            if (delta.fluidConsumed[simTank] > 0) {
                tanks.drainInternal(simTank, delta.fluidConsumed[simTank] * copies, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        for (Harvest harvest : plan) {
            int taken = drain(mirror.handler(), harvest.machineSlot(), harvest.perCraft().getCount());
            if (taken <= 0) continue;

            int wanted = taken * copies;
            int placed = insertIntoOutputGrid(harvest.perCraft().copyWithCount(wanted), false);
            if (placed < wanted) {
                // Should not happen after the capacity check, but never destroy items over it.
                ItemStack leftover = harvest.perCraft().copyWithCount(wanted - placed);
                mirror.handler().insertItem(harvest.machineSlot(), leftover, false);
            }
        }

        for (Map.Entry<Integer, FluidStack> entry : delta.fluidOutputs.entrySet()) {
            // One craft's worth is all the machine ever made; the batch is what the grid paid for, so
            // the single drain is multiplied on the way into the tanks exactly as item harvest is.
            // Draining once per copy instead is what pinned a parallel electrolyzer to a single lane.
            FluidStack drained = mirror.drainFluid(entry.getValue().copy(), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) continue;

            int wanted = drained.getAmount() * copies;
            int placed = tanks.insertIntoOutputs(drained.copyWithAmount(wanted), IFluidHandler.FluidAction.EXECUTE);
            if (placed < wanted) {
                // Should not happen after the capacity check, but never destroy fluid over it.
                mirror.fillFluid(drained.copyWithAmount(Math.min(drained.getAmount(), wanted - placed)),
                        IFluidHandler.FluidAction.EXECUTE);
            }
        }

        if (energyPerCopy > 0 && !chargeEnergy(saturatingMultiply(energyPerCopy, copies))) {
            return STATUS_NO_POWER;
        }
        strandedWork = false;
        if (buying) awaitingWork = true;

        if (delta.didWork()) {
            // A machine that takes its next round of ingredients in the same breath as handing over the
            // last one still has a job in flight, and the ticks it spends on that job have to keep being
            // charged to every lane that will collect its output. Only if that round was really bought,
            // though: a machine topping its fuel up as it finishes has taken on nothing new, and reading
            // that as a job in flight is what kept an empty macerator billing for minutes.
            awaitingWork = delta.tookIngredients() && (delta.tookGridItems() || fundableThisTick);
            everCrafted = true;
            craftInterval = Math.max(1, ticksSinceCraft);
            ticksSinceCraft = 0;
            be.onCycleApplied(copies, craftInterval);
        }
        stalledDelta = null;
        return STATUS_WORKING;
    }

    /** Recalibrates timing when the machine starts work with a different ingredient identity. */
    private void observeRecipe(TickDelta delta) {
        boolean hadRecipe = false;
        boolean changed = false;
        for (int slot = 0; slot < gridInputs; slot++) {
            if (!recipeInputs[slot].isEmpty()) hadRecipe = true;
            if (delta.consumed[slot] <= 0) continue;

            ItemStack source = phantomSource[slot];
            ItemStack previous = recipeInputs[slot];
            if (source != null && (previous.isEmpty() || !ItemStack.isSameItem(previous, source))) {
                changed = hadRecipe;
                recipeInputs[slot] = source.copyWithCount(1);
            }
        }
        if (changed) resetCalibration();
    }

    /**
     * Carries what the last engine measured over a rewind. The machine is a fresh copy, but the
     * observations are about the block, not about that copy: what a craft draws, how long one takes,
     * and whether the grid is currently unable to pay for one. Starting those over is what let a
     * rewound machine immediately re-arm itself on the fuel it still had, since an engine that has
     * never seen a craft cannot know that fuel alone will not feed one.
     */
    public void adoptCalibration(PhantomMirrorEngine previous) {
        System.arraycopy(previous.craftDraw, 0, craftDraw, 0, craftDraw.length);
        System.arraycopy(previous.fluidCraftDraw, 0, fluidCraftDraw, 0, fluidCraftDraw.length);
        System.arraycopy(previous.recipeInputs, 0, recipeInputs, 0, recipeInputs.length);
        craftInterval = previous.craftInterval;
        everCrafted = previous.everCrafted;
        probeUnits = previous.probeUnits;
        strandedWork = previous.strandedWork;
        // The grid has not been touched by the rewind, so the offer is the same one. Forgetting it
        // would read as the player having changed something and clear the stranded flag with it.
        offeredInputs = previous.offeredInputs;
    }

    public void resetCalibration() {
        everCrafted = false;
        craftInterval = 0;
        ticksSinceCraft = 0;
        settleTicks = 0;
        strandedWork = false;
        windDownTicks = 0;
        // A different recipe draws a different amount, so what the last one needed stocked is forgotten.
        Arrays.fill(craftDraw, 0);
        Arrays.fill(fluidCraftDraw, 0);
        probeUnits = FIRST_PROBE_UNITS;
        quietTicks = 0;
        inertTicks = 0;
    }

    /**
     * The largest multiple that everything the machine has already been paid for supports. Each grid
     * slot remembers the multiple its last withdrawal was charged at, and the smallest of those wins:
     * a furnace running on a single coal cannot smelt eight ores at a time no matter how much ore is in
     * the grid.
     */
    private int fundedMultiple() {
        int funded = Integer.MAX_VALUE;
        for (int multiple : drawMultiplier) {
            if (multiple != NO_DRAW) funded = Math.min(funded, multiple);
        }
        for (int multiple : fluidDrawMultiplier) {
            if (multiple != NO_DRAW) funded = Math.min(funded, multiple);
        }
        return funded;
    }

    private int activeCopies() {
        int funded = fundedMultiple();
        return funded == Integer.MAX_VALUE
                ? Math.max(1, be.getBatchSize())
                : Math.max(1, Math.min(be.getBatchSize(), funded));
    }

    /** Charges measured work plus the configured 20% entanglement overhead and anomaly surcharge. */
    private long costWithOverhead(long perCopy, int copies) {
        long base = saturatingMultiply(Math.max(0, perCopy), Math.max(1, copies));
        long withEntangle = base >= Long.MAX_VALUE / 6 ? Long.MAX_VALUE : (base * 6 + 4) / 5;
        // The simulator reads its chunk's surcharge once a second; reading it here was a chunk lookup per charge.
        return AnomalyEffects.scaleFe(withEntangle, 1.0 + be.getSurchargePercent() / 100.0);
    }

    private boolean chargeEnergy(long amount) {
        if (amount <= 0) return true;
        int paid = be.consumeEnergy(amount);
        chargedThisTick = saturatingAdd(chargedThisTick, paid);
        if (paid >= amount) return true;
        energyDebt = saturatingAdd(energyDebt, amount - paid);
        return false;
    }

    private boolean payEnergyDebt() {
        if (energyDebt <= 0) return true;
        int paid = be.consumeEnergy(energyDebt);
        chargedThisTick = saturatingAdd(chargedThisTick, paid);
        energyDebt -= paid;
        return energyDebt <= 0;
    }

    private static long saturatingMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    /**
     * Pins the multiple every slot drawn from this tick was charged at.
     *
     * <p>An advance withdrawal is pinned at exactly what was charged, because that is all the work it
     * bought. Ingredients taken as part of a finished craft are pinned at what the slot could have
     * afforded instead: pinning those at the charged amount would ratchet throughput down to whatever
     * the leanest craft managed and never let it climb back. Pinning them at all matters because a
     * machine can pay for a whole run of work in the same tick it finishes some, the way a furnace
     * swallows a lava bucket and hands back an empty one.
     */
    private void pinMultiples(ItemStackHandler grid, TickDelta delta, int copies, boolean buying) {
        int batch = Math.max(1, be.getBatchSize());
        for (int gridSlot = 0; gridSlot < gridInputs; gridSlot++) {
            int taken = delta.consumed[gridSlot];
            if (taken <= 0) continue;

            if (buying) {
                drawMultiplier[gridSlot] = copies;
            } else {
                // Counted before the deduction below, so it reflects the stock the machine drew from.
                drawMultiplier[gridSlot] = Math.min(batch, grid.getStackInSlot(gridSlot).getCount() / taken);
            }
        }

        SimulatorFluidTanks tanks = be.getFluidTanks();
        for (int tank = 0; tank < SimulatorFluidTanks.INPUT_TANKS; tank++) {
            int taken = delta.fluidConsumed[tank];
            if (taken <= 0) continue;

            if (buying) {
                fluidDrawMultiplier[tank] = copies;
            } else {
                int stored = tanks.getFluid(tank).getAmount();
                fluidDrawMultiplier[tank] = Math.min(batch, stored / taken);
            }
        }
    }

    private List<Harvest> buildHarvestPlan(VirtualMachineMirror mirror, TickDelta delta) {
        List<Harvest> plan = new ArrayList<>(delta.outputs.size());
        IItemHandler handler = mirror.handler();
        for (Map.Entry<Integer, ItemStack> entry : delta.outputs.entrySet()) {
            int machineSlot = entry.getKey();
            if (machineSlot >= handler.getSlots()) continue;
            ItemStack inSlot = handler.getStackInSlot(machineSlot);
            if (inSlot.isEmpty()) continue;
            int amount = Math.min(entry.getValue().getCount(), inSlot.getCount());
            if (amount > 0) {
                plan.add(new Harvest(machineSlot, inSlot.copyWithCount(amount)));
            }
        }
        return plan;
    }

    /** Largest number of copies up to {@code max} whose whole output fits in the 3x3 output grid. */
    private int largestBatchThatFits(List<Harvest> plan, int max) {
        if (plan.isEmpty()) return max;
        for (int copies = max; copies >= 1; copies--) {
            if (fitsInOutputGrid(plan, copies)) return copies;
        }
        return 0;
    }

    /**
     * Largest batch whose fluid output the tanks can take, under the same one-fluid-one-tank rule the
     * tanks themselves enforce. Modelling a fluid as spillable across several tanks would green-light a
     * batch the harvest could then only half deliver.
     */
    private int largestFluidBatchThatFits(TickDelta delta, int max) {
        if (delta.fluidOutputs.isEmpty()) return max;
        SimulatorFluidTanks tanks = be.getFluidTanks();

        // Worked out in one pass rather than by trying each batch size in turn: a boiler produces
        // every single tick, so this runs as often as the machine does.
        FluidStack[] resident = new FluidStack[SimulatorFluidTanks.OUTPUT_TANKS];
        int[] perCraftDemand = new int[resident.length];
        for (int i = 0; i < resident.length; i++) {
            resident[i] = tanks.getFluid(SimulatorFluidTanks.outputIndex(i));
        }

        for (FluidStack perCraft : delta.fluidOutputs.values()) {
            int match = -1;
            int empty = -1;
            for (int i = 0; i < resident.length; i++) {
                if (resident[i].isEmpty()) {
                    if (empty < 0) empty = i;
                } else if (FluidStack.isSameFluidSameComponents(resident[i], perCraft)) {
                    match = i;
                    break;
                }
            }
            int target = match >= 0 ? match : empty;
            if (target < 0) return 0;
            // Claimed for this fluid so a second output of the same kind lands in the same tank and
            // a different one does not try to share it.
            if (resident[target].isEmpty()) resident[target] = perCraft;
            perCraftDemand[target] += perCraft.getAmount();
        }

        int copies = max;
        for (int i = 0; i < resident.length; i++) {
            if (perCraftDemand[i] <= 0) continue;
            int space = SimulatorFluidTanks.CAPACITY - tanks.getFluid(SimulatorFluidTanks.outputIndex(i)).getAmount();
            copies = Math.min(copies, space / perCraftDemand[i]);
        }
        return Math.max(0, copies);
    }

    private boolean fitsInOutputGrid(List<Harvest> plan, int copies) {
        ItemStackHandler grid = be.getInventory();
        int outputStart = QuantumSimulatorBlockEntity.OUTPUT_START;
        int outputEnd = QuantumSimulatorBlockEntity.OUTPUT_END;

        ItemStack[] scratch = new ItemStack[outputEnd - outputStart + 1];
        for (int i = 0; i < scratch.length; i++) {
            scratch[i] = grid.getStackInSlot(outputStart + i).copy();
        }

        for (Harvest harvest : plan) {
            int remaining = harvest.perCraft().getCount() * copies;
            ItemStack unit = harvest.perCraft();
            for (int i = 0; i < scratch.length && remaining > 0; i++) {
                int limit = Math.min(grid.getSlotLimit(outputStart + i), unit.getMaxStackSize());
                if (scratch[i].isEmpty()) {
                    int placed = Math.min(remaining, limit);
                    scratch[i] = unit.copyWithCount(placed);
                    remaining -= placed;
                } else if (ItemStack.isSameItemSameComponents(scratch[i], unit)) {
                    int space = limit - scratch[i].getCount();
                    if (space > 0) {
                        int placed = Math.min(remaining, space);
                        scratch[i].grow(placed);
                        remaining -= placed;
                    }
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void applyStateDelta(ItemStackHandler grid, TickDelta delta, int gridSlot, int copies) {
        ItemStack baseline = delta.stateBefore[gridSlot];
        ItemStack phantom = delta.stateAfter[gridSlot];
        int damage = delta.damage[gridSlot];
        if (damage == 0 && phantom == null) return;

        ItemStack real = grid.getStackInSlot(gridSlot);
        if (real.isEmpty()) return;

        if (damage > 0 && real.isDamageableItem()) {
            int worn = real.getDamageValue() + damage * copies;
            if (worn >= real.getMaxDamage()) {
                grid.setStackInSlot(gridSlot, ItemStack.EMPTY);
                return;
            }
            real.setDamageValue(worn);
        }
        if (phantom != null) {
            ItemStateDelta.apply(real, baseline, phantom, copies,
                    be.getLevel() == null ? null : be.getLevel().registryAccess());
        }
        grid.setStackInSlot(gridSlot, real);
    }

    /** Throws away work the grid cannot pay for, so the machine does not jam on it. */
    private void discard(VirtualMachineMirror mirror, List<Harvest> plan, TickDelta delta) {
        for (Harvest harvest : plan) {
            drain(mirror.handler(), harvest.machineSlot(), harvest.perCraft().getCount());
        }
        for (FluidStack fluid : delta.fluidOutputs.values()) {
            mirror.drainFluid(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // --- Item plumbing ------------------------------------------------------------------------

    private static int safeSlotLimit(IItemHandler handler, int slot) {
        try {
            int limit = handler.getSlotLimit(slot);
            return limit > 0 ? limit : 64;
        } catch (Throwable t) {
            return 64;
        }
    }

    private static int drain(IItemHandler handler, int slot, int amount) {
        int taken = 0;
        int guard = 0;
        while (taken < amount && guard++ < 64) {
            ItemStack pulled;
            try {
                pulled = handler.extractItem(slot, amount - taken, false);
            } catch (Throwable t) {
                break;
            }
            if (pulled.isEmpty()) break;
            taken += pulled.getCount();
        }
        return taken;
    }

    /** Inserts across the whole output grid, splitting into stacks. Returns how much was placed. */
    private int insertIntoOutputGrid(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return 0;
        ItemStackHandler grid = be.getInventory();
        int remaining = stack.getCount();
        int placed = 0;

        for (int slot = QuantumSimulatorBlockEntity.OUTPUT_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END && remaining > 0; slot++) {
            ItemStack chunk = stack.copyWithCount(Math.min(remaining, stack.getMaxStackSize()));
            ItemStack leftover = grid.insertItem(slot, chunk, simulate);
            int moved = chunk.getCount() - leftover.getCount();
            placed += moved;
            remaining -= moved;
        }
        return placed;
    }
}
