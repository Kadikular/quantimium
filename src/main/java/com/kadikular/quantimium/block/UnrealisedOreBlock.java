package com.kadikular.quantimium.block;

import net.minecraft.util.ARGB;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Always Unrealised Ore to the world (WAILA, veinminer, silk touch). The client paints a vanilla ore
 * facade over it at bake time, so caves look like ordinary ore until you look twice.
 *
 * <p>Deliberately not a block entity: worldgen writes into a proto-chunk, which never instantiates
 * block entities, so an ore placed by a feature would have had nothing to render it.
 */
public class UnrealisedOreBlock extends Block {

    public static final MapCodec<UnrealisedOreBlock> CODEC = simpleCodec(UnrealisedOreBlock::new);

    private static final DustParticleOptions QUANTUM_DUST =
            new DustParticleOptions(ARGB.colorFromFloat(1.0f, 0.42f, 0.86f, 1.0f), 0.8f);

    public UnrealisedOreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).isSolidRender()) continue;
            if (random.nextInt(3) != 0) continue;

            double x = pos.getX() + faceOffset(direction, Direction.Axis.X, random);
            double y = pos.getY() + faceOffset(direction, Direction.Axis.Y, random);
            double z = pos.getZ() + faceOffset(direction, Direction.Axis.Z, random);
            level.addParticle(QUANTUM_DUST, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** Pins the particle just off the exposed face and scatters it across the other two axes. */
    private static double faceOffset(Direction face, Direction.Axis axis, RandomSource random) {
        if (face.getAxis() != axis) return 0.0625 + random.nextDouble() * 0.875;
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.05 : -0.05;
    }
}
