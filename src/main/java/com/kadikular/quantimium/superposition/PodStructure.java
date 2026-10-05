package com.kadikular.quantimium.superposition;

import com.kadikular.quantimium.block.PodCradleBlock;
import com.kadikular.quantimium.block.PodModuleBlock;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * The Superposition Pod's cradle: a 3×3 floor one block below the capsule. Pod Cradle at the four
 * corners and under the capsule, and on each of the four edges either Pod Plating or a module.
 *
 * <pre>
 *   C M C
 *   M C M     C: Pod Cradle    M: plating or a module
 *   C M C     the capsule stands on the middle C
 * </pre>
 *
 * <p>The edges are where the player steps up into the capsule, so they are full blocks you can stand
 * on. Nothing here has a block entity but the capsule, which checks the cradle and lights it.
 */
public final class PodStructure {

    /** What a cradle adds up to: whether it is whole, and which modules it holds. */
    public record Scan(boolean formed, int modules) {
        public static final Scan BROKEN = new Scan(false, 0);
    }

    private PodStructure() {}

    public static Scan scan(Level level, BlockPos pod) {
        if (!level.getBlockState(pod.above()).is(level.getBlockState(pod).getBlock())) return Scan.BROKEN;
        int modules = 0;
        BlockPos floor = pod.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = floor.offset(dx, 0, dz);
                if (!level.isLoaded(at)) return Scan.BROKEN;
                BlockState state = level.getBlockState(at);
                boolean edge = (dx == 0) != (dz == 0);
                if (edge) {
                    if (!(state.getBlock() instanceof PodModuleBlock module)) return Scan.BROKEN;
                    modules |= module.module().bit();
                } else if (!(state.getBlock() instanceof PodCradleBlock)) {
                    return Scan.BROKEN;
                }
            }
        }
        return new Scan(true, modules);
    }

    /** Lights the cradle round {@code pod}, or puts it out, and turns its edges to face the capsule. */
    public static void light(Level level, BlockPos pod, boolean formed) {
        BlockPos floor = pod.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = floor.offset(dx, 0, dz);
                if (!level.isLoaded(at)) continue;
                BlockState state = level.getBlockState(at);
                BlockState lit = state;
                if (lit.hasProperty(PodCradleBlock.FORMED)) lit = lit.setValue(PodCradleBlock.FORMED, formed);
                if (formed && lit.hasProperty(PodModuleBlock.AXIS)) {
                    lit = lit.setValue(PodModuleBlock.AXIS, dx != 0 ? Direction.Axis.Z : Direction.Axis.X);
                }
                if (lit != state) level.setBlock(at, lit, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The pod whose cradle includes {@code cradle}, if any. */
    @Nullable
    public static SuperpositionPodBlockEntity podOver(Level level, BlockPos cradle) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pod = cradle.offset(dx, 1, dz);
                if (!level.isLoaded(pod)) continue;
                BlockState state = level.getBlockState(pod);
                if (state.getBlock() instanceof SuperpositionPodBlock
                        && state.getValue(SuperpositionPodBlock.HALF) == DoubleBlockHalf.LOWER
                        && level.getBlockEntity(pod) instanceof SuperpositionPodBlockEntity entity) {
                    return entity;
                }
            }
        }
        return null;
    }

    /** A cradle block changed: tell the pods it might belong to. */
    public static void changed(Level level, BlockPos cradle) {
        if (level.isClientSide()) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pod = cradle.offset(dx, 1, dz);
                if (level.isLoaded(pod) && level.getBlockEntity(pod) instanceof SuperpositionPodBlockEntity entity) {
                    entity.revalidate();
                }
            }
        }
    }
}
