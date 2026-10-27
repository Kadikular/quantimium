package com.kadikular.quantimium.block;

import com.kadikular.quantimium.laser.LaserNetworks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Where a Redstone Lens's beam ends on a block: the air in front of it, giving that block (and only it) a strong
 * signal, as a lever on it would. Unseen and air to everything else. It goes when no Redstone Lens's light holds it
 * (checked twice a second, so one left by an unloaded or broken beam clears itself).
 */
public class LaserSignalBlock extends Block {

    public static final MapCodec<LaserSignalBlock> CODEC = simpleCodec(LaserSignalBlock::new);
    /** Towards the block it powers. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int CHECK_TICKS = 10;

    public LaserSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 15));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING).getOpposite() ? state.getValue(POWER) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        if (level.isClientSide()) return;
        if (!old.is(this)) level.scheduleTick(pos, this, CHECK_TICKS);
        updateNeighbours(state, level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        updateNeighbours(state, level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (LaserNetworks.holdsSignal(level, pos)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        } else {
            level.removeBlock(pos, false);
        }
    }

    /** It and the block it powers: their neighbours see the signal change. */
    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction front = state.getValue(FACING);
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, front, front.getAxis().isHorizontal() ? Direction.UP : front);
        level.updateNeighborsAt(pos, this, orientation);
        level.updateNeighborsAt(pos.relative(front), this, orientation);
    }
}
