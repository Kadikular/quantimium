package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.BuddingAnomaliteBlock;
import com.kadikular.quantimium.flux.AnomaliteSpawner;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Late-tier farm host: spend FE to grow Anomalite on every face, ignoring flux, anomaly, and
 * containment. Crystals on this block do not parasitically drain it.
 */
public class BuddingAnomaliteBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    public static final int FE_PER_TICK = 40;
    public static final int ENERGY_CAPACITY = 50_000;
    public static final int MAX_RECEIVE = 400;
    /** One growth attempt every 5 seconds while powered and a face still has room. */
    public static final int GROW_INTERVAL = 100;

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    private int growProgress;
    private int currentPowerUse;
    private int averagePowerUse;
    private int powerSampleIndex;
    private final int[] powerSamples = new int[20];

    public BuddingAnomaliteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUDDING_ANOMALITE_BE.get(), pos, state);
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getAveragePowerUse() {
        return averagePowerUse;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BuddingAnomaliteBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        boolean canGrow = Config.anomaliteEnabled() && hasGrowableFace(serverLevel, pos);
        int spent = 0;
        if (canGrow && be.energyStorage.getEnergyStored() >= FE_PER_TICK) {
            spent = be.energyStorage.consume(FE_PER_TICK);
            if (spent > 0) {
                be.growProgress++;
                if (be.growProgress >= GROW_INTERVAL) {
                    be.growProgress = 0;
                    tryGrow(serverLevel, pos);
                }
                be.setChanged();
            }
        } else {
            be.growProgress = 0;
        }

        int leak = AnomalyEffects.passiveDrainFePerTick(serverLevel, pos);
        if (leak > 0) {
            leak = be.energyStorage.consume(leak);
            if (leak > 0) be.setChanged();
        }

        be.recordPowerUse(spent);

        boolean active = spent > 0;
        if (state.getValue(BuddingAnomaliteBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(BuddingAnomaliteBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean hasGrowableFace(ServerLevel level, BlockPos host) {
        for (Direction face : Direction.values()) {
            if (isGrowable(level, host, face)) return true;
        }
        return false;
    }

    private static void tryGrow(ServerLevel level, BlockPos host) {
        List<Direction> open = new ArrayList<>(6);
        for (Direction face : Direction.values()) {
            if (isGrowable(level, host, face)) open.add(face);
        }
        if (open.isEmpty()) return;

        Direction outward = open.get(level.getRandom().nextInt(open.size()));
        BlockPos crystalPos = host.relative(outward);
        BlockState existing = level.getBlockState(crystalPos);
        if (AnomaliteSpawner.canReplaceWithCrystal(level, crystalPos)) {
            level.setBlock(crystalPos, ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                    .setValue(AnomaliteCrystalBlock.FACING, outward)
                    .setValue(AnomaliteCrystalBlock.AGE, 0), Block.UPDATE_ALL);
        } else {
            level.setBlock(crystalPos, existing.setValue(AnomaliteCrystalBlock.AGE,
                    existing.getValue(AnomaliteCrystalBlock.AGE) + 1), Block.UPDATE_ALL);
        }
        // Only the mirror sees the crystal, so only the mirror hears it grow.
        com.kadikular.quantimium.phase.MirrorSounds.play(level, crystalPos, SoundEvents.AMETHYST_CLUSTER_PLACE,
                SoundSource.BLOCKS, 0.4f, 0.8f + level.getRandom().nextFloat() * 0.4f);
    }

    private static boolean isGrowable(ServerLevel level, BlockPos host, Direction outward) {
        BlockPos crystalPos = host.relative(outward);
        if (!level.isLoaded(crystalPos)) return false;
        if (AnomaliteSpawner.canReplaceWithCrystal(level, crystalPos)) return true;
        BlockState state = level.getBlockState(crystalPos);
        return state.is(ModBlocks.ANOMALITE_CRYSTAL.get())
                && state.getValue(AnomaliteCrystalBlock.FACING) == outward
                && state.getValue(AnomaliteCrystalBlock.AGE) < 3;
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
        tag.putInt("GrowProgress", growProgress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        growProgress = tag.getIntOr("GrowProgress", 0);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return Component.translatable("item.quantimium.flux_meter.budding_anomalite", FluxMeterReadout.fe(getAveragePowerUse()))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
