package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.RiftStabiliserBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Rift Stabiliser: a powered pillar that a nearby Rift Anchor claims. Stands 2–5 blocks out from
 * the anchor in any direction, with a clear line to the rift. Three hold it; up to six make more.
 */
public class RiftStabiliserBlock extends BaseEntityBlock {

    public static final MapCodec<RiftStabiliserBlock> CODEC = simpleCodec(RiftStabiliserBlock::new);

    /** Beaming at a rift: its tesseract burns bright rather than murky. Set by the block entity. */
    public static final BooleanProperty BEAMING = BooleanProperty.create("beaming");

    public RiftStabiliserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BEAMING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BEAMING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** The model's steps: base slab, body, neck and cap. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(2, 3, 2, 14, 8, 14),
            Block.box(4, 8, 4, 12, 13, 12),
            Block.box(5, 13, 5, 11, 16, 11));

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftStabiliserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.RIFT_STABILISER_BE.get(), RiftStabiliserBlockEntity::serverTick);
    }
}
