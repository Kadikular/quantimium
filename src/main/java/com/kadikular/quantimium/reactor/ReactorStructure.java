package com.kadikular.quantimium.reactor;

import com.kadikular.quantimium.block.ReactorPortBlock;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Quantimium Reactor's footprint: a disc of Reactor Plinth 11 blocks across under the Horizon
 * Core, which sits on its centre. On the plinth, around the core, stand up to three facing pairs of
 * Ring Emitters (each whole pair drives one ring) and up to {@link #MAX_BAYS} Catalyst Bays. Ports
 * take the place of plinth on the disc's rim.
 *
 * <p>Fixed size: more rings come from more emitters, not a bigger floor.
 */
public final class ReactorStructure {

    /** The disc: every column within this distance of the core's. 11 across. */
    public static final double RADIUS = 5.5;
    public static final int REACH = 5;
    public static final int MAX_BAYS = 8;
    public static final int MAX_RINGS = 3;

    /** Each pair of emitter spots, as (dx, dz) offsets from the core: one ring each when both are built. */
    private static final int[][] EMITTER_PAIRS = {{4, 0, -4, 0}, {0, 4, 0, -4}, {3, 3, -3, -3}};

    private ReactorStructure() {}

    public static boolean inDisc(int dx, int dz) {
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** On the disc's outer edge, where ports go. */
    public static boolean onRim(int dx, int dz) {
        return inDisc(dx, dz) && (!inDisc(dx + 1, dz) || !inDisc(dx - 1, dz) || !inDisc(dx, dz + 1) || !inDisc(dx, dz - 1));
    }

    public static boolean isEmitterSpot(int dx, int dz) {
        for (int[] pair : EMITTER_PAIRS) {
            if ((pair[0] == dx && pair[1] == dz) || (pair[2] == dx && pair[3] == dz)) return true;
        }
        return false;
    }

    /** Every plinth-layer cell, under the core's layer. */
    public static void forEachPlinthCell(BlockPos core, Consumer<BlockPos> action) {
        BlockPos floor = core.below();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if (inDisc(dx, dz)) action.accept(floor.offset(dx, 0, dz));
            }
        }
    }

    /**
     * What stands around a core: how many rings its emitters drive, its ports and bays, every block
     * that belongs to it, or why it isn't a reactor.
     */
    public record Layout(int rings, List<BlockPos> ports, List<BlockPos> bays, List<BlockPos> parts,
                         Component problem, List<BlockPos> emitters) {
        /** A layout without emitters: none found, or a refusal. */
        public Layout(int rings, List<BlockPos> ports, List<BlockPos> bays, List<BlockPos> parts, Component problem) {
            this(rings, ports, bays, parts, problem, List.of());
        }

        public boolean formed() {
            return problem == null;
        }
    }

    public static Layout read(Level level, BlockPos core) {
        List<BlockPos> ports = new ArrayList<>();
        List<BlockPos> bays = new ArrayList<>();
        List<BlockPos> parts = new ArrayList<>();
        BlockPos floor = core.below();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if (!inDisc(dx, dz)) continue;
                BlockPos pos = floor.offset(dx, 0, dz);
                if (!level.isLoaded(pos)) return refuse("message.quantimium.reactor.unloaded");
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.REACTOR_PLINTH.get())) {
                    parts.add(pos.immutable());
                } else if (state.getBlock() instanceof ReactorPortBlock && onRim(dx, dz)) {
                    ports.add(pos.immutable());
                    parts.add(pos.immutable());
                } else {
                    return refuse(Component.translatable(state.getBlock() instanceof ReactorPortBlock
                                    ? "message.quantimium.reactor.port_off_rim" : "message.quantimium.reactor.plinth_gap",
                            pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        }
        int rings = 0;
        List<BlockPos> emitters = new ArrayList<>();
        for (int[] pair : EMITTER_PAIRS) {
            BlockPos a = core.offset(pair[0], 0, pair[1]);
            BlockPos b = core.offset(pair[2], 0, pair[3]);
            if (isEmitter(level, a) && isEmitter(level, b)) {
                rings++;
                parts.add(a.immutable());
                parts.add(b.immutable());
                emitters.add(a.immutable());
                emitters.add(b.immutable());
            }
        }
        if (rings == 0) return refuse("message.quantimium.reactor.no_rings");
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if (!inDisc(dx, dz) || (dx == 0 && dz == 0) || isEmitterSpot(dx, dz)) continue;
                BlockPos pos = core.offset(dx, 0, dz);
                if (level.getBlockState(pos).is(ModBlocks.CATALYST_BAY.get()) && bays.size() < MAX_BAYS) {
                    bays.add(pos.immutable());
                }
            }
        }
        return new Layout(rings, ports, bays, parts, null, emitters);
    }

    private static boolean isEmitter(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.RING_EMITTER.get());
    }

    private static Layout refuse(String key) {
        return refuse(Component.translatable(key));
    }

    private static Layout refuse(Component problem) {
        return new Layout(0, List.of(), List.of(), List.of(), problem);
    }
}
