package com.kadikular.quantimium.block;

import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.LevelReader;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.superposition.Superposition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Superposition Pod's capsule: two blocks tall, open at the front so you can step in, standing on
 * the middle of its cradle ({@link com.kadikular.quantimium.superposition.PodStructure}). The glass,
 * its rings and the double inside are drawn by its renderer; the block itself is a base and a crown,
 * with thin walls you cannot see that stop you walking through the glass.
 *
 * <p>Use it with a Sophon to unfold your double into it; sneak-use it with an empty hand to fold your
 * double back up. Anything else opens its screen, which from inside is where you swap.
 */
public class SuperpositionPodBlock extends BaseEntityBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = PodCradleBlock.FORMED;

    private static final VoxelShape OUTLINE = Block.box(1, 0, 1, 15, 16, 15);
    private static final Map<Direction, VoxelShape> LOWER_WALLS = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> UPPER_WALLS = new EnumMap<>(Direction.class);

    static {
        // A player is 1.8 tall: a 1px floor and a 1px roof leave 1.875 to walk in upright.
        VoxelShape floor = Block.box(0, 0, 0, 16, 1, 16);
        VoxelShape roof = Block.box(0, 15, 0, 16, 16, 16);
        for (Direction front : Direction.Plane.HORIZONTAL) {
            VoxelShape walls = Shapes.empty();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (side != front) walls = Shapes.or(walls, wall(side));
            }
            LOWER_WALLS.put(front, Shapes.or(floor, walls));
            UPPER_WALLS.put(front, Shapes.or(roof, walls));
        }
    }

    private static VoxelShape wall(Direction side) {
        return switch (side) {
            case NORTH -> Block.box(0, 0, 0, 16, 16, 1);
            case SOUTH -> Block.box(0, 0, 15, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, 1, 16, 16);
            default -> Block.box(15, 0, 0, 16, 16, 16);
        };
    }

    public SuperpositionPodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
                .setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SuperpositionPodBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING, FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_WALLS : UPPER_WALLS).get(state.getValue(FACING));
    }

    // ---- placing and breaking, as a door does ----

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SuperpositionPodBlockEntity pod) {
            if (placer instanceof Player player) pod.claim(player);
            if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                pod.setCustomName(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
            }
            pod.revalidate();
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            if (!neighbour.is(this) || neighbour.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
            // The upper half follows the lower one, which is the half that knows whether the pod is whole.
            return half == DoubleBlockHalf.UPPER ? state.setValue(FORMED, neighbour.getValue(FORMED)) : state;
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // In creative, breaking the top must not leave the bottom to drop an item.
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---- use ----

    @Nullable
    private static SuperpositionPodBlockEntity pod(Level level, BlockPos pos, BlockState state) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return level.getBlockEntity(lower) instanceof SuperpositionPodBlockEntity pod ? pod : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.has(ModDataComponents.SOPHON.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        SuperpositionPodBlockEntity pod = pod(level, pos, state);
        if (pod == null) return InteractionResult.FAIL;
        if (!level.isClientSide() && player instanceof ServerPlayer server) pod.unfold(server, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        SuperpositionPodBlockEntity pod = pod(level, pos, state);
        if (pod == null) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            if (player.isSecondaryUseActive() && pod.hasDouble()) {
                pod.fold(server);
            } else {
                Superposition.openScreen(server, pod);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ---- block entity ----

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new SuperpositionPodBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return createTickerHelper(type, ModBlockEntities.SUPERPOSITION_POD_BE.get(),
                level.isClientSide() ? SuperpositionPodBlockEntity::clientTick : SuperpositionPodBlockEntity::serverTick);
    }

    /** Motes drift up through a pod holding a double, as the stasis field keeps it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || !state.getValue(FORMED)) return;
        if (!(level.getBlockEntity(pos) instanceof SuperpositionPodBlockEntity pod) || !pod.hasDouble()) return;
        if (random.nextInt(3) != 0) return;
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 0.25 + random.nextDouble() * 0.12;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(angle) * radius, pos.getY() + 0.2,
                pos.getZ() + 0.5 + Math.sin(angle) * radius, 0, 0.02 + random.nextDouble() * 0.02, 0);
    }
}
