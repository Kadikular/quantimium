package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Machines an Acceleration Lens speeds up: extra ticks of their own ticker, each tick, within two time budgets. Each
 * machine's extra ticks may take at most {@link Config#accelerationBlockBudgetNanos()} a tick, judged by how long its
 * ticks have taken lately; all of them together at most {@link Config#accelerationTotalBudgetNanos()}. One that would
 * go over is given fewer, and says why. Server thread only.
 */
public final class Acceleration {
    private Acceleration() {}

    /** How much faster a machine was last made to go, and how much it was asked: for what it shows. */
    public record Shown(int multiplier, int asked, boolean budgeted) {}

    private static final class State {
        /** How long one of its ticks has taken lately, in nanoseconds: 0 till first measured. */
        double nanosPerTick;
        long lastTick;
        Shown shown = new Shown(1, 1, false);
    }

    private static final Map<Level, Map<Long, State>> STATES = new java.util.WeakHashMap<>();
    /** The game tick the total is being spent for, and how much of it has been. */
    private static long spentTick = Long.MIN_VALUE;
    private static long spentNanos;

    /** Whether {@code be} can be sped up: it ticks, it isn't a Quantimium machine, and a pack hasn't ruled it out. */
    public static boolean accelerable(@Nullable BlockEntity be) {
        if (be == null || be.isRemoved() || !Config.acceleration()) return false;
        BlockState state = be.getBlockState();
        if (state.is(ModTags.NOT_ACCELERATED)) return false;
        // Its own machines (lasers, the field, the Reactor) aren't built to run faster than the world.
        if (Quantimium.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return false;
        return ticker(be) != null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> ticker(BlockEntity be) {
        if (be.getLevel() == null) return null;
        return be.getBlockState().getTicker(be.getLevel(), (BlockEntityType<T>) be.getType());
    }

    /**
     * Gives the machine at {@code pos} up to {@code wanted} extra ticks this tick, as far as the budgets let; how many
     * it gave. {@code multiplier} is what it was asked to go at, for what it shows.
     */
    @SuppressWarnings("unchecked")
    public static int tick(ServerLevel level, BlockPos pos, int wanted, int multiplier) {
        BlockEntity be = level.getBlockEntity(pos);
        if (wanted <= 0 || !accelerable(be) || Freezing.frozen(be)) return 0;
        long now = level.getGameTime();
        if (spentTick != now) {
            spentTick = now;
            spentNanos = 0;
        }
        Map<Long, State> states = STATES.computeIfAbsent(level, k -> new HashMap<>());
        // Now and then, forget machines no light has sped up for a while.
        if (now % 1200 == 0) states.values().removeIf(old -> now - old.lastTick > 200);
        State state = states.computeIfAbsent(pos.asLong(), k -> new State());
        boolean measured = state.nanosPerTick > 0;
        int allowed = wanted;
        // Lately each of its ticks took this long: as many as fit its own budget, and what's left of everyone's.
        if (state.nanosPerTick > 0) {
            allowed = (int) Math.min(allowed, Config.accelerationBlockBudgetNanos() / state.nanosPerTick);
            allowed = (int) Math.min(allowed, Math.max(0, Config.accelerationTotalBudgetNanos() - spentNanos) / state.nanosPerTick);
        } else if (spentNanos >= Config.accelerationTotalBudgetNanos()) {
            allowed = 0;
        } else {
            // Not measured yet: one, to see.
            allowed = Math.min(allowed, 1);
        }
        int done = 0;
        if (allowed > 0) {
            BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) ticker(be);
            long began = System.nanoTime();
            for (; done < allowed && ticker != null && !be.isRemoved(); done++) ticker.tick(level, pos, be.getBlockState(), be);
            long took = System.nanoTime() - began;
            spentNanos += took;
            double each = done > 0 ? (double) took / done : 0;
            state.nanosPerTick = state.nanosPerTick <= 0 ? each : state.nanosPerTick * 0.8 + each * 0.2;
        }
        state.lastTick = now;
        // Asked for more than it got, once measured (the first tick gives one, to see): the budget held it back.
        state.shown = new Shown(1 + done, multiplier, measured && done < wanted);
        return done;
    }

    /** What the machine at {@code pos} was last sped up to, if it was within the last second; null if not. */
    @Nullable
    public static Shown shown(Level level, BlockPos pos) {
        Map<Long, State> states = STATES.get(level);
        State state = states == null ? null : states.get(pos.asLong());
        if (state == null || level.getGameTime() - state.lastTick > 20) return null;
        return state.shown;
    }
}
