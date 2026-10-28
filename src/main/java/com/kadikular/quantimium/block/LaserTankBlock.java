package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A Laser Tank: the Laser Port for fluids. Light passes through it and leaves by its face; it catches fluid an
 * Extract lens brings back up the light, sends what it holds down it, or both. Placed, it faces you; a wrench, or a
 * sneaking empty hand, on any face points it there; a bucket fills or empties it; an empty hand opens it.
 */
public class LaserTankBlock extends BaseEntityBlock {
    public static final MapCodec<LaserTankBlock> CODEC = simpleCodec(LaserTankBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** What it does with fluids, for the model: its grilles light in the mode's colour. */
    public static final EnumProperty<LaserPortBlockEntity.Mode> MODE = EnumProperty.create("mode", LaserPortBlockEntity.Mode.class);

    public LaserTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, LaserPortBlockEntity.Mode.COLLECT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // The laser tools (the Linker, the Spectroscope) do their own thing; anything else opens it.
        if (!stack.is(ModTags.TOOLS_WRENCH)) {
            if (LaserTools.isOne(stack)) return InteractionResult.PASS;
            // A bucket, or anything else holding fluid, fills or empties it, whatever its mode.
            if (level.getBlockEntity(pos) instanceof LaserTankBlockEntity
                    && net.neoforged.neoforge.transfer.fluid.FluidUtil.interactWithFluidHandler(player, hand, level, pos, null)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return point(state, level, pos, hit.getDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return point(state, level, pos, hit.getDirection());
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof com.kadikular.quantimium.block.entity.LaserTankBlockEntity port) player.openMenu(port, pos);
        return InteractionResult.SUCCESS;
    }

    /** Points the mirror out of the face clicked. */
    private static InteractionResult point(BlockState state, Level level, BlockPos pos, Direction face) {
        if (!level.isClientSide() && state.getValue(FACING) != face) {
            level.setBlock(pos, state.setValue(FACING, face), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            LaserNetworks.changed(level);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserTankBlockEntity(pos, state);
    }

    /** A Redstone Lens's light, ending on the block right beside it, gives that block its signal from here. */
    @Override
    protected int getSignal(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
                            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return com.kadikular.quantimium.laser.LaserNetworks.heldSignal(level, pos, direction, false);
    }

    @Override
    protected int getDirectSignal(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
                                  net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        return com.kadikular.quantimium.laser.LaserNetworks.heldSignal(level, pos, direction, true);
    }
}
