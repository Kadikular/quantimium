package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
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
 * A Lens Mount: a frame cube holding four lenses in a row, its front edges marking the way it shines. Light goes
 * straight through it, in by the side behind its front and out of its front, through its lenses in order. Placed,
 * it shines towards you; a wrench, or a sneaking empty hand, on any side points it there; an empty hand opens it.
 */
public class LensMountBlock extends BaseEntityBlock implements com.kadikular.quantimium.laser.BeamAnchor {
    public static final MapCodec<LensMountBlock> CODEC = simpleCodec(LensMountBlock::new);
    /** The way light leaves it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * Where each lens stands, in sixteenths along the light from the front, where it leaves (0), to the back,
     * where it comes in (16): the first lens nearest the back.
     */
    public static final double[] SLOT_ALONG = {12.5, 9.5, 6.5, 3.5};

    public LensMountBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Where the lens in {@code slot} stands, in sixteenths of the block, for one shining {@code facing}. */
    public static net.minecraft.world.phys.Vec3 slotAt(int slot, Direction facing) {
        return new net.minecraft.world.phys.Vec3(8, 8, 8).add(net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(8 - SLOT_ALONG[slot]));
    }

    /** Light runs through its middle, past its lenses. */
    @Override
    public net.minecraft.world.phys.Vec3 beamPoint(BlockState state, BlockPos pos) {
        return pos.getCenter();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Shining towards you. */
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
        if (!stack.is(ModTags.TOOLS_WRENCH)) return LaserTools.isOne(stack) ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
        return point(state, level, pos, hit.getDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return point(state, level, pos, hit.getDirection());
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LensMountBlockEntity mount) player.openMenu(mount, pos);
        return InteractionResult.SUCCESS;
    }

    /** Points it out of the side clicked. */
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
        return new LensMountBlockEntity(pos, state);
    }
}
