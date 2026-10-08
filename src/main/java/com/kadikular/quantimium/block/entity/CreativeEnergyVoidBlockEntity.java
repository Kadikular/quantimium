package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/** Takes all it's given and keeps none; counts it, so the Flux Meter can say how much a second it took. */
public class CreativeEnergyVoidBlockEntity extends BlockEntity implements FluxMeterReadout {

    /** Taken this second so far, and in the last whole second. */
    private long taking;
    private long tookLastSecond;

    /** Rolls the count back with a transfer that's only simulated, or cancelled. */
    private final SnapshotJournal<Long> journal = new SnapshotJournal<>() {
        @Override
        protected Long createSnapshot() {
            return taking;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            taking = snapshot;
        }
    };

    private final EnergyHandler handler = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return Long.MAX_VALUE;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            journal.updateSnapshots(transaction);
            taking += amount;
            return amount;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    };

    public CreativeEnergyVoidBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_ENERGY_VOID_BE.get(), pos, state);
    }

    public EnergyHandler energy() {
        return handler;
    }

    /** FE a tick it took, on average over the last second. */
    public long taking() {
        return tookLastSecond / 20;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CreativeEnergyVoidBlockEntity voidBlock) {
        if (level.getGameTime() % 20 == 0) {
            voidBlock.tookLastSecond = voidBlock.taking;
            voidBlock.taking = 0;
        }
    }

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        return Component.translatable("message.quantimium.creative_energy_void.taking",
                String.format(java.util.Locale.ROOT, "%,d", taking())).withStyle(ChatFormatting.DARK_AQUA);
    }
}
