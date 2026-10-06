package com.kadikular.quantimium.reactor;

import com.kadikular.quantimium.block.ReactorPortBlock;
import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Quantimium Reactor's footprint: a disc of Reactor Plinth 11 blocks across under the Horizon
 * Core, which sits on its centre. On the plinth, around the core, stand up to three facing pairs of
 * Ring Emitters (each whole pair drives one ring). Up to {@link #MAX_BAYS} Catalyst Bays take the place
 * of plinth anywhere but under the core; ports take its place on the rim.
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
                } else if (state.is(ModBlocks.CATALYST_BAY.get()) && (dx != 0 || dz != 0)) {
                    if (bays.size() == MAX_BAYS) return refuse("message.quantimium.reactor.too_many_bays");
                    bays.add(pos.immutable());
                    parts.add(pos.immutable());
                } else if (state.getBlock() instanceof ReactorPortBlock && onRim(dx, dz)) {
                    ports.add(pos.immutable());
                    parts.add(pos.immutable());
                } else {
                    String key = state.getBlock() instanceof ReactorPortBlock ? "message.quantimium.reactor.port_off_rim"
                            : state.is(ModBlocks.CATALYST_BAY.get()) ? "message.quantimium.reactor.bay_misplaced"
                            : "message.quantimium.reactor.plinth_gap";
                    return refuse(Component.translatable(key, pos.getX(), pos.getY(), pos.getZ()));
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
        return new Layout(rings, ports, bays, parts, null, emitters);
    }

    /**
     * Sinks each Catalyst Bay still standing on the plinth, as bays did before they became part of the
     * floor, into the plinth block under it, catalyst and all. The plinth block it replaces is used up.
     */
    public static void sinkRaisedBays(Level level, BlockPos core) {
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if (!inDisc(dx, dz) || (dx == 0 && dz == 0)) continue;
                BlockPos raised = core.offset(dx, 0, dz);
                BlockPos floor = raised.below();
                if (!level.isLoaded(raised) || !level.getBlockState(raised).is(ModBlocks.CATALYST_BAY.get())
                        || !level.getBlockState(floor).is(ModBlocks.REACTOR_PLINTH.get())
                        || !(level.getBlockEntity(raised) instanceof CatalystBayBlockEntity bay)) {
                    continue;
                }
                List<ItemStack> catalysts = bay.getCatalysts().stream().map(ItemStack::copy).toList();
                for (int slot = 0; slot < CatalystBayBlockEntity.SLOTS; slot++) bay.setCatalyst(slot, ItemStack.EMPTY);
                level.removeBlock(raised, false);
                level.setBlock(floor, ReactorTraces.at(ModBlocks.CATALYST_BAY.get().defaultBlockState(), floor), Block.UPDATE_ALL);
                if (level.getBlockEntity(floor) instanceof CatalystBayBlockEntity sunk) {
                    for (int slot = 0; slot < catalysts.size(); slot++) sunk.setCatalyst(slot, catalysts.get(slot));
                }
            }
        }
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
