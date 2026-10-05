package com.kadikular.quantimium.block.entity;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.block.FieldControlBlock;
import com.kadikular.quantimium.block.entity.simulation.SimulatedEffect;
import com.kadikular.quantimium.block.entity.simulation.SimulationContext;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.FieldControlMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Holds its chunk's field where it is set, for the {@link FieldControlBlock} it sits in.
 *
 * <p><b>Flux floor</b> (Maintainer, Regulator): raises flux towards {@value #RAISE_TARGET} times the floor
 * band's lower bound, spending up to {@value #RAISE_FE_PER_TICK} FE/t on pure flux at {@value #EFFICIENCY}×
 * the usual yield, as the Quantum Exciter does. It works in proportion to how far short the chunk is,
 * counting flux still easing in, so it settles inside the band rather than switching on and off round
 * an edge.
 *
 * <p><b>Anomaly ceiling</b> (Siphon, Regulator): holds anomaly in each chunk of the 3×3 round it just under
 * the top of its ceiling band ({@value #HOLD_UNDER} of it: 80 for Low), or at nothing for
 * {@link Ceiling#CLEAR}, pulling anomaly and only anomaly, up to {@value #PULL_PER_SECOND} a second. Each
 * pull says what to leave, and the field's own step takes exactly the excess, after the climb it
 * answers: each chunk lands on the mark and stays, with no swing past it. It draws
 * {@value #PULL_FE_PER_TICK} FE/t pulling flat out, less as it takes less. What it pulls it turns into
 * Anomaly Fragments, one per {@value #ANOMALY_PER_FRAGMENT}.
 *
 * <p>The Regulator does both, at {@code 0.8} of the FE. It reads the field every {@value #READ_TICKS}
 * ticks and emits its flux once a second.
 *
 * <p>In a Quantum Simulator it holds the simulator's chunk, and every copy raises and pulls: the flux
 * and the pull are multiplied here, one copy's FE is drawn for the simulator to bill per copy, and one
 * copy's share of the pull is banked towards fragments, which the simulator multiplies as it does any
 * machine's output.
 */
public class FieldControlBlockEntity extends BlockEntity
        implements MenuProvider, QuantumEnergyHost, FluxMeterReadout, SimulatedEffect {

    public static final int ENERGY_CAPACITY = 2_000_000;
    public static final int ENERGY_MAX_RECEIVE = 40_000;
    public static final int RAISE_FE_PER_TICK = 10_000;
    public static final double EFFICIENCY = QuantumExciterBlockEntity.EFFICIENCY;
    public static final int PULL_FE_PER_TICK = 160;
    /** Anomaly pulled a second from the 3×3 round it, flat out, spread by {@link QuantumFlux#share}. */
    public static final double PULL_PER_SECOND = 6000.0;
    /** Anomaly pulled per Anomaly Fragment. Pulling flat out, a Siphon makes about four a minute. */
    public static final double ANOMALY_PER_FRAGMENT = 24_000.0;
    /**
     * Raises towards this many times the floor band's lower bound, harder the further below it the
     * chunk is (counting flux still easing in), so it settles inside the band instead of switching on
     * and off round it.
     */
    public static final double RAISE_TARGET = 4.0;
    /** A ceiling holds anomaly at this share of the top of its band: just under it, clear of the edge. */
    public static final double HOLD_UNDER = 0.8;
    /** Pulling, it draws at least this share of its full FE, however little it takes. */
    private static final double PULL_IDLE_SHARE = 0.05;

    /** What a Siphon keeps anomaly under: just below the top of a band, or nothing at all. */
    public enum Ceiling {
        LOW(FluxBand.LOW),
        MEDIUM(FluxBand.MEDIUM),
        HIGH(FluxBand.HIGH),
        /** Holds anomaly at nothing, as containment would. */
        CLEAR(null);

        @Nullable
        private final FluxBand band;

        Ceiling(@Nullable FluxBand band) {
            this.band = band;
        }

        /** The anomaly it holds each chunk at. */
        public double target() {
            return band == null ? 0.0 : band.ceiling() * HOLD_UNDER;
        }

        public Component label() {
            return band == null ? Component.translatable("gui.quantimium.field_control.clear")
                    : Component.translatable("flux.quantimium.band." + band.getSerializedName());
        }
    }
    private static final int READ_TICKS = 10;
    private static final int EMIT_TICKS = 20;

    /** The floors a Maintainer can hold, and the ceilings a Siphon can keep under. */
    public static final FluxBand[] FLOORS = {FluxBand.MEDIUM, FluxBand.HIGH, FluxBand.CRITICAL};
    public static final Ceiling[] CEILINGS = Ceiling.values();

    public static final int STATUS_HOLDING = 0;
    public static final int STATUS_RAISING = 1;
    public static final int STATUS_PULLING = 2;
    public static final int STATUS_BOTH = 3;
    public static final int STATUS_NO_POWER = 4;

    public static final int BUTTON_FLOOR = 0;
    public static final int BUTTON_CEILING = 1;
    public static final int DATA_COUNT = 14;

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.ANOMALY_FRAGMENT.get());
        }
    };

    /** Take-only, for pipes and hoppers: the fragments come out, nothing goes in. */
    private final ResourceHandler<ItemResource> automation = RestrictedItems.takeOnly(inventory);

    private int floor = 0;
    private int ceiling = 0;
    private boolean raising;
    private boolean pulling;
    /** How hard it is raising and pulling, 0 to 1, from the last read. */
    private double raiseThrottle;
    private double pullThrottle;
    private int status = STATUS_HOLDING;
    private double flux;
    private double anomaly;
    private double pulled;
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
                case 4 -> status;
                case 5 -> floor;
                case 6 -> ceiling;
                case 7 -> (int) Math.min(Integer.MAX_VALUE, flux) & 0xFFFF;
                case 8 -> ((int) Math.min(Integer.MAX_VALUE, flux) >>> 16) & 0xFFFF;
                case 9 -> (int) Math.min(Integer.MAX_VALUE, anomaly) & 0xFFFF;
                case 10 -> ((int) Math.min(Integer.MAX_VALUE, anomaly) >>> 16) & 0xFFFF;
                case 11 -> averageFe & 0xFFFF;
                case 12 -> (averageFe >>> 16) & 0xFFFF;
                case 13 -> (int) Math.round(Math.min(1.0, pulled / ANOMALY_PER_FRAGMENT) * 1000);
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

    public FieldControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIELD_CONTROL_BE.get(), pos, state);
    }

    public FieldControlBlock.Kind kind() {
        return getBlockState().getBlock() instanceof FieldControlBlock block ? block.kind() : FieldControlBlock.Kind.MAINTAINER;
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Nullable
    public ResourceHandler<ItemResource> getAutomationItemHandler() {
        return kind().pullsAnomaly() ? automation : null;
    }

    public ContainerData getData() {
        return data;
    }

    public FluxBand floor() {
        return FLOORS[floor];
    }

    /** Its ceiling: for anomaly on a Siphon or Regulator, for flux on a Suppressor, fixed at Low on a Basic Siphon. */
    public Ceiling ceiling() {
        return kind().setsCeiling() ? CEILINGS[ceiling] : Ceiling.LOW;
    }

    public int status() {
        return status;
    }

    /** The next floor or ceiling, round again after the last. */
    public void pressButton(int id) {
        if (id == BUTTON_FLOOR && kind().raisesFlux()) floor = (floor + 1) % FLOORS.length;
        if (id == BUTTON_CEILING && kind().setsCeiling()) ceiling = (ceiling + 1) % CEILINGS.length;
        setChanged();
    }

    public void setFloor(FluxBand band) {
        for (int i = 0; i < FLOORS.length; i++) if (FLOORS[i] == band) floor = i;
        setChanged();
    }

    public void setCeiling(Ceiling setting) {
        ceiling = setting.ordinal();
        setChanged();
    }

    /** Flux it raises towards for {@code floor}; see {@link #RAISE_TARGET}. */
    public static double raiseTo(FluxBand floor) {
        return floor.minFlux() * RAISE_TARGET;
    }


    // ---- ticking ----

    public static void tick(Level level, BlockPos pos, BlockState state, FieldControlBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        FieldControlBlock.Kind kind = be.kind();
        if (now % READ_TICKS == 0) be.decide(server, kind);

        int leak = AnomalyEffects.passiveDrainFePerTick(server, pos);
        if (leak > 0) be.energyStorage.consume(leak);

        boolean powered = true;
        boolean working = false;
        if (be.raising) {
            int cost = (int) Math.ceil(RAISE_FE_PER_TICK * kind.feFactor() * be.raiseThrottle);
            int spent = be.energyStorage.consume(Math.min(cost, be.energyStorage.getEnergyStored()));
            if (spent > 0) {
                // Every copy in a simulator raises, on one copy's FE: the simulator bills the rest.
                be.unemittedFe += (long) spent * SimulationContext.copies();
                be.feThisSecond += spent;
                working = true;
            }
            if (spent < cost) powered = false;
        }
        if (kind.pulls()) {
            // Pulling costs in proportion to what the last second took; asking with nothing to take is free.
            int cost = be.pulling ? (int) Math.ceil(PULL_FE_PER_TICK * kind.feFactor() * Math.max(PULL_IDLE_SHARE, be.pullThrottle)) : 0;
            if (be.energyStorage.getEnergyStored() >= cost) {
                be.energyStorage.consume(cost);
                be.feThisSecond += cost;
                if (now % EMIT_TICKS == 0) {
                    // It asks every second, since a pull takes only the excess over its mark. Each copy
                    // pulls; the fragments are banked from one copy's share, since the simulator
                    // multiplies what comes out of the slot. What comes back is what the last second's
                    // pull took, which also sets how hard it is working.
                    int copies = SimulationContext.copies();
                    double full = PULL_PER_SECOND * kind.pullScale() * copies;
                    double took;
                    if (kind.pullsFlux()) {
                        // A Suppressor: flux only, and nothing to show for it.
                        took = QuantumFlux.holdFluxUnder(server, pos, full, QuantumFlux.SOURCE_RADIUS, be.ceiling().target());
                    } else {
                        took = QuantumFlux.holdAnomalyUnder(server, pos, full, QuantumFlux.SOURCE_RADIUS, be.ceiling().target());
                        be.scavenge(took / copies);
                    }
                    be.pullThrottle = Math.min(1.0, took / full);
                }
                if (be.pulling) working = true;
            } else {
                powered = false;
            }
        }
        // Inside its floor band it is holding, though it still tops the field up; below, it is raising.
        boolean climbing = be.raising && be.flux < be.floor().minFlux();
        be.status = !powered ? STATUS_NO_POWER
                : (climbing ? STATUS_RAISING : 0) | (be.pulling ? STATUS_PULLING : 0);
        if (working) be.setChanged();

        if (now % EMIT_TICKS == 0) {
            // Pure flux, at the Exciter's yield: the point is to raise flux, not anomaly. Emitted in a
            // simulator too, where the flux is the product; the simulator's own is on top, as its upkeep.
            if (be.unemittedFe > 0) QuantumFlux.emitFromEnergy(server, pos, be.unemittedFe, EFFICIENCY);
            be.unemittedFe = 0;
            be.averageFe = be.feThisSecond / EMIT_TICKS;
            be.feThisSecond = 0;
        }
        // A simulated one stands where the simulator's field block is; that block is not ours.
        if (!SimulationContext.active() && state.getValue(FieldControlBlock.ACTIVE) != working) {
            level.setBlock(pos, state.setValue(FieldControlBlock.ACTIVE, working), Block.UPDATE_CLIENTS);
        }
    }

    /** Reads the field and sets how hard to raise and to pull, each in proportion to how far off it is. */
    private void decide(ServerLevel level, FieldControlBlock.Kind kind) {
        QuantumFlux.Neighbourhood here = QuantumFlux.chunk(level, worldPosition);
        flux = here.flux();
        anomaly = here.anomaly();
        raiseThrottle = kind.raisesFlux()
                ? QuantumExciterBlockEntity.throttle(QuantumFlux.chunkCommittedFlux(level, worldPosition), raiseTo(floor()))
                : 0.0;
        raising = raiseThrottle > 0.0;
        // It asks every second, since a pull only takes the excess over its mark; skipping a second
        // would let anomaly climb past the mark and back. It counts as pulling while it takes anything,
        // or anything it covers is over the mark.
        double worst = kind.pullsFlux() ? QuantumFlux.peakFlux(level, worldPosition, QuantumFlux.SOURCE_RADIUS)
                : kind.pullsAnomaly() ? QuantumFlux.peakAnomaly(level, worldPosition, QuantumFlux.SOURCE_RADIUS) : 0.0;
        pulling = kind.pulls() && (pullThrottle > 0.0 || worst > ceiling().target() + 0.5);
    }

    /** What it pulls, banked towards fragments; a full slot voids the surplus, as a suppressor's does. */
    private void scavenge(double amount) {
        if (amount <= 0.0) return;
        pulled += amount;
        while (pulled >= ANOMALY_PER_FRAGMENT) {
            pulled -= ANOMALY_PER_FRAGMENT;
            inventory.insertItem(0, new ItemStack(ModItems.ANOMALY_FRAGMENT.get()), false);
        }
    }

    // ---- readouts, menu and saving ----

    @Override
    public MutableComponent fluxMeterLine() {
        FieldControlBlock.Kind kind = kind();
        MutableComponent line = Component.translatable("item.quantimium.flux_meter.field_control."
                + kind.name().toLowerCase(java.util.Locale.ROOT),
                band(floor()), ceiling().label(), FluxMeterReadout.fe(averageFe));
        return line.withStyle(ChatFormatting.DARK_AQUA);
    }

    private static Component band(FluxBand band) {
        return Component.translatable("flux.quantimium.band." + band.getSerializedName());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FieldControlMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Floor", floor);
        tag.putInt("Ceiling", ceiling);
        tag.putDouble("Pulled", pulled);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        floor = Math.floorMod(tag.getIntOr("Floor", 0), FLOORS.length);
        ceiling = Math.floorMod(tag.getIntOr("Ceiling", 0), CEILINGS.length);
        pulled = tag.getDoubleOr("Pulled", 0.0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getInventory().getStackInSlot(0));
    }
}
