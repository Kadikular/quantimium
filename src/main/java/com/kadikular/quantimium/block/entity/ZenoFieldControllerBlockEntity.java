package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.ZenoFieldControllerBlock;
import com.kadikular.quantimium.block.entity.simulation.SimulatedEffect;
import com.kadikular.quantimium.block.entity.simulation.SimulationContext;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.menu.ZenoFieldControllerMenu;
import com.kadikular.quantimium.zeno.ZenoFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

/**
 * The Zeno Field Controller: a watched pot never boils. In a cube around it, random ticks are either
 * held still (crops, leaves, ice, copper all stop changing) or run faster, both for FE.
 *
 * <p>Pausing is a field the random-tick hook asks about ({@link ZenoFields}). Accelerating is done
 * here: the controller picks extra random positions in its cube at the rate vanilla would pick them,
 * times its factor less one, and ticks them itself. A paused block is never accelerated.
 *
 * <p>Taken into a Quantum Simulator, an accelerating controller does the extra ticks of every copy
 * the simulator runs, drawing one copy's FE; the simulator bills that draw for every copy, as it does
 * any machine with no inputs, so the cost scales with the batch.
 */
public class ZenoFieldControllerBlockEntity extends BlockEntity implements MenuProvider, QuantumEnergyHost, SimulatedEffect, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 500_000;
    public static final int MAX_RECEIVE = 10_000;

    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 8;
    public static final int DEFAULT_RADIUS = 4;
    public static final int[] FACTORS = {2, 4, 8, 16};

    /** Holding a field still costs this much per block of radius: 40 FE/t at radius 4. */
    public static final int HOLD_FE_PER_RADIUS = 10;
    /**
     * Accelerating costs this much for every step of factor above 1, per half block of radius, so the
     * largest field at 16x (radius 8) is 45 × 15 × 4 = 2,700 FE/t. The cost follows the radius, not the
     * volume: a field eight times as wide does not cost five hundred times as much.
     */
    public static final int ACCELERATE_FE = 45;
    /** Blocks per chunk section, over which vanilla spreads its random ticks. */
    private static final double SECTION_VOLUME = 4096.0;

    public static final int MODE_PAUSE = 0;
    public static final int MODE_ACCELERATE = 1;

    public static final int STATUS_HOLDING = 0;
    public static final int STATUS_ACCELERATING = 1;
    public static final int STATUS_NO_POWER = 2;

    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_RADIUS = 1;
    public static final int BUTTON_FACTOR = 2;

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    private int mode = MODE_PAUSE;
    private int radius = DEFAULT_RADIUS;
    private int factorIndex = 1;
    private int statusCode = STATUS_HOLDING;
    /** The flux band here, which caps how fast the field accelerates (running hot pays). */
    private final BandGate gate = new BandGate();
    /** Fractional extra random ticks owed, carried from tick to tick. */
    private double pendingTicks;
    /** FE spent since flux was last emitted. */
    private long unemittedFe;
    /**
     * For the extra random ticks. The level's own source is safe across threads and pays for it on
     * every call; this is only ever used on the server thread, where the ticks run.
     */
    private final RandomSource tickRandom = RandomSource.createThreadLocalInstance();
    /** Extra random ticks performed since placed; read by tests. */
    private long extraTicksDone;
    private int currentPowerUse;
    private int averagePowerUse;
    private int powerSampleIndex;
    private final int[] powerSamples = new int[20];
    private int syncedPassiveDrain;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> (energyStorage.getEnergyStored() >> 16) & 0xFFFF;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energyStorage.getMaxEnergyStored() >> 16) & 0xFFFF;
                case 4 -> statusCode;
                case 5 -> mode;
                case 6 -> radius;
                case 7 -> factor();
                case 8 -> currentPowerUse & 0xFFFF;
                case 9 -> (currentPowerUse >>> 16) & 0xFFFF;
                case 10 -> averagePowerUse & 0xFFFF;
                case 11 -> (averagePowerUse >>> 16) & 0xFFFF;
                case 12 -> syncedPassiveDrain;
                case 13 -> runningFactor();
                case 14 -> gate.band().ordinal();
                case 15 -> gate.heading().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ZenoFieldControllerMenu.DATA_COUNT;
        }
    };

    public ZenoFieldControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ZENO_FIELD_CONTROLLER_BE.get(), pos, state);
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ContainerData getData() {
        return data;
    }

    public int getMode() {
        return mode;
    }

    public int getRadius() {
        return radius;
    }

    public int factor() {
        return FACTORS[Mth.clamp(factorIndex, 0, FACTORS.length - 1)];
    }

    /** The rate it runs at: the one chosen, capped by the band here. */
    public int runningFactor() {
        return Math.min(factor(), Config.zenoMaxFactor(gate.band()));
    }

    public BandGate bandGate() {
        return gate;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long extraTicksDone() {
        return extraTicksDone;
    }

    public void setMode(int mode) {
        this.mode = mode == MODE_ACCELERATE ? MODE_ACCELERATE : MODE_PAUSE;
        pendingTicks = 0.0;
        setChanged();
    }

    public void setRadius(int radius) {
        this.radius = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        setChanged();
    }

    public void setFactor(int factor) {
        for (int i = 0; i < FACTORS.length; i++) {
            if (FACTORS[i] == factor) factorIndex = i;
        }
        setChanged();
    }

    /** The menu buttons: mode toggles, radius and factor cycle upwards and wrap. */
    public void pressButton(int id) {
        switch (id) {
            case BUTTON_MODE -> setMode(mode == MODE_PAUSE ? MODE_ACCELERATE : MODE_PAUSE);
            case BUTTON_RADIUS -> setRadius(radius >= MAX_RADIUS ? MIN_RADIUS : radius + 1);
            case BUTTON_FACTOR -> {
                factorIndex = (factorIndex + 1) % FACTORS.length;
                setChanged();
            }
            default -> {}
        }
    }

    public static int volume(int radius) {
        int side = 2 * radius + 1;
        return side * side * side;
    }

    /** FE/t to hold a field of this radius still. */
    public static int pauseCost(int radius) {
        return HOLD_FE_PER_RADIUS * radius;
    }

    /** FE/t to accelerate a field of this radius at this factor. */
    public static int accelerateCost(int radius, int factor) {
        return Mth.ceil(ACCELERATE_FE * (factor - 1) * radius / 2.0);
    }

    /** What accelerating costs in {@code band}: less at Singularity (running hot pays). */
    public static int accelerateCost(int radius, int factor, FluxBand band) {
        int cost = accelerateCost(radius, factor);
        return band == FluxBand.SINGULARITY ? Mth.ceil(cost * Config.zenoSingularityCostPercent() / 100.0) : cost;
    }

    /** Extra random ticks per game tick an accelerating field adds, before any simulator's copies. */
    public static double extraTicksPerTick(int radius, int factor, int randomTickSpeed) {
        return randomTickSpeed * (factor - 1) * volume(radius) / SECTION_VOLUME;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ZenoFieldControllerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (server.getGameTime() % 20L == 0L) be.gate.update(server, pos);
        int spent = be.mode == MODE_PAUSE ? be.hold(server, pos) : be.accelerate(server, pos);

        int leak = AnomalyEffects.passiveDrainFePerTick(server, pos);
        if (leak > 0) leak = be.energyStorage.consume(leak);
        be.syncedPassiveDrain = leak;
        be.recordPowerUse(spent);

        // Inside a simulator the simulator emits flux for everything it is charged.
        if (!SimulationContext.active()) {
            be.unemittedFe += spent;
            if (server.getGameTime() % 20L == 0L && be.unemittedFe > 0) {
                QuantumFlux.emitFromEnergy(server, pos, be.unemittedFe);
                be.unemittedFe = 0;
            }
        }
        if (spent > 0) be.setChanged();

        // A simulated controller stands where the simulator's field block is; that block is not ours.
        if (SimulationContext.active()) return;
        boolean active = be.statusCode != STATUS_NO_POWER;
        if (state.hasProperty(ZenoFieldControllerBlock.ACTIVE) && state.getValue(ZenoFieldControllerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ZenoFieldControllerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private int hold(ServerLevel level, BlockPos pos) {
        int cost = pauseCost(radius);
        if (energyStorage.getEnergyStored() < cost) {
            statusCode = STATUS_NO_POWER;
            return 0;
        }
        ZenoFields.renew(level, pos, radius);
        statusCode = STATUS_HOLDING;
        return energyStorage.consume(cost);
    }

    private int accelerate(ServerLevel level, BlockPos pos) {
        int speed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        // With random ticks switched off there is nothing to multiply, and nothing to pay for.
        if (speed <= 0) {
            statusCode = STATUS_ACCELERATING;
            return 0;
        }
        int cost = accelerateCost(radius, runningFactor(), gate.band());
        if (energyStorage.getEnergyStored() < cost) {
            statusCode = STATUS_NO_POWER;
            return 0;
        }
        statusCode = STATUS_ACCELERATING;
        pendingTicks += extraTicksPerTick(radius, runningFactor(), speed);
        int ticks = (int) pendingTicks;
        pendingTicks -= ticks;
        // In a simulator every copy does its own ticks, but the draw is one copy's: the simulator bills
        // a machine with no inputs for every copy, as it would a generator making something from nothing.
        int copies = SimulationContext.copies();
        extraTicksDone += randomTicks(level, pos, ticks * copies);
        return energyStorage.consume(cost);
    }

    /**
     * {@code total} random ticks at random spots in the cube, as vanilla would give them, never on the
     * controller itself or inside a paused field; at most the configured cap. Returns how many it
     * dealt out.
     *
     * <p>Dealt out section by section, as vanilla deals its own: each 16³ section the cube overlaps
     * gets its share by volume (rounded up or down at random, so the average is exact), and a section
     * with nothing in it that random-ticks is skipped whole, since no tick there could do anything.
     * The chunk is looked up once per section rather than twice per tick: the lookups were most of
     * the cost, and a simulator running the controller multiplies the ticks by its copies.
     */
    private int randomTicks(ServerLevel level, BlockPos centre, int total) {
        int cap = Config.zenoMaxExtraTicks();
        if (cap > 0) total = Math.min(total, cap);
        if (total <= 0) return 0;
        double slowedChance = Config.zenoSlowedChance();
        int minX = centre.getX() - radius, maxX = centre.getX() + radius;
        int minY = Math.max(level.getMinY(), centre.getY() - radius);
        int maxY = Math.min(level.getMaxY() - 1, centre.getY() + radius);
        int minZ = centre.getZ() - radius, maxZ = centre.getZ() + radius;
        double volume = (double) volume(radius);
        boolean anyPaused = ZenoFields.any(level);
        RandomSource random = tickRandom;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int sx = minX >> 4; sx <= maxX >> 4; sx++) {
            for (int sz = minZ >> 4; sz <= maxZ >> 4; sz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
                if (chunk == null) continue;
                int x0 = Math.max(minX, sx << 4), x1 = Math.min(maxX, (sx << 4) + 15);
                int z0 = Math.max(minZ, sz << 4), z1 = Math.min(maxZ, (sz << 4) + 15);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
                    double share = total * ((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) / volume);
                    int count = (int) share;
                    if (random.nextDouble() < share - count) count++;
                    if (count == 0) continue;
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
                    if (!section.isRandomlyTicking()) continue;
                    boolean fluids = section.isRandomlyTickingFluids();
                    int width = x1 - x0 + 1, height = y1 - y0 + 1, depth = z1 - z0 + 1;
                    int cells = width * height * depth;
                    for (int i = 0; i < count; i++) {
                        // One draw for the spot, split into its three coordinates.
                        int cell = random.nextInt(cells);
                        cursor.set(x0 + cell % width, y0 + cell / width % height, z0 + cell / (width * height));
                        if (cursor.equals(centre) || anyPaused && ZenoFields.isPaused(level, cursor)) continue;
                        BlockState state = section.getBlockState(cursor.getX() & 15, cursor.getY() & 15, cursor.getZ() & 15);
                        if (state.isRandomlyTicking() && accepts(state, random, slowedChance)) {
                            state.randomTick(level, cursor.immutable(), random);
                        }
                        if (fluids) {
                            FluidState fluid = state.getFluidState();
                            if (fluid.isRandomlyTicking()) fluid.randomTick(level, cursor.immutable(), random);
                        }
                    }
                }
            }
        }
        return total;
    }

    /**
     * Upkeep ticks (farmland's moisture, grass spreading) are skipped or thinned out: they cost the
     * most and grow nothing. The blocks still get their normal ticks.
     */
    private static boolean accepts(BlockState state, RandomSource random, double slowedChance) {
        return switch (ZenoFields.tickRule(state)) {
            case NORMAL -> true;
            case IGNORED -> false;
            case SLOWED -> random.nextDouble() < slowedChance;
        };
    }

    private void recordPowerUse(int amount) {
        currentPowerUse = Math.max(0, amount);
        powerSamples[powerSampleIndex++ % powerSamples.length] = currentPowerUse;
        long total = 0;
        for (int sample : powerSamples) total += sample;
        averagePowerUse = (int) (total / powerSamples.length);
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
        tag.putInt("Mode", mode);
        tag.putInt("Band", gate.band().ordinal());
        tag.putInt("Radius", radius);
        tag.putInt("Factor", factor());
        tag.putDouble("Pending", pendingTicks);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        mode = tag.getIntOr("Mode", 0) == MODE_ACCELERATE ? MODE_ACCELERATE : MODE_PAUSE;
        FluxBand band = FluxBand.values()[Math.clamp(tag.getIntOr("Band", 0), 0, FluxBand.values().length - 1)];
        gate.set(band, band);
        radius = tag.contains("Radius") ? Mth.clamp(tag.getIntOr("Radius", 0), MIN_RADIUS, MAX_RADIUS) : DEFAULT_RADIUS;
        if (tag.contains("Factor")) setFactor(tag.getIntOr("Factor", 0));
        pendingTicks = tag.getDoubleOr("Pending", 0.0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.zeno_field_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ZenoFieldControllerMenu(containerId, inventory, this, data);
    }

    /** Its field (mode, size, factor), then its draw and the flux that puts into the chunk. */
    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent field = mode == MODE_PAUSE
                ? Component.translatable("item.quantimium.flux_meter.zeno.hold", radius)
                : Component.translatable("item.quantimium.flux_meter.zeno.accelerate", factor(), radius);
        return field.withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("  ·  "))
                .append(FluxMeterReadout.emitter(averagePowerUse));
    }
}
