package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Logistics Receptacle block from before it became an attachment of a Laser Focus: it holds what it was set to only
 * until it becomes a Clear Laser Focus with a Receptacle on the same face, the tick after it loads (see
 * {@link #becomeFocus}).
 */
public class LogisticsReceptacleBlockEntity extends BlockEntity {

    /** What it was set to, read as it was saved. */
    private final Receptacle settings;

    public LogisticsReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_RECEPTACLE_BE.get(), pos, state);
        settings = new Receptacle(null, facing());
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(LogisticsReceptacleBlock.FACING) ? state.getValue(LogisticsReceptacleBlock.FACING) : Direction.DOWN;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    /** Becomes a Clear Laser Focus, light passing straight through as it did, with a Receptacle set as this one was. */
    public void becomeFocus() {
        Level world = level;
        if (world == null || world.isClientSide() || isRemoved()) return;
        BlockPos at = worldPosition;
        Direction face = facing();
        world.setBlock(at, ModBlocks.LASER_FOCUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(world.getBlockEntity(at) instanceof LaserFocusBlockEntity focus)) return;
        focus.setCore(LaserFocusBlockEntity.Core.CLEAR);
        focus.setPart(face, LaserFocusBlockEntity.Part.RECEPTACLE);
        Receptacle receptacle = focus.receptacle(face);
        if (receptacle != null) receptacle.copyFrom(settings);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        settings.save(out);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        settings.load(in);
    }
}
