package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Harvest Laser: fires through a Rift Lens at an Anomalite crystal up to six blocks out, the ring
 * anywhere between. Everyone sees a beam go into a tear; the mirror sees the tear pour it onto the
 * crystal, which shatters for its shards.
 */
public class HarvestLaserBlock extends BaseEntityBlock {

    public static final MapCodec<HarvestLaserBlock> CODEC = simpleCodec(HarvestLaserBlock::new);
    /** Firing. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** The way the muzzle points. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public HarvestLaserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.NORTH));
    }

    /** Placed facing the player: stand where the crystal is to be, and it points at you. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    /** A ring already on its line is turned to take the beam. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || state.is(oldState.getBlock())) return;
        Direction facing = state.getValue(FACING);
        for (int i = 1; i <= HarvestLaserBlockEntity.REACH; i++) {
            BlockPos at = pos.relative(facing, i);
            BlockState ring = level.getBlockState(at);
            if (ring.is(ModBlocks.RIFT_LENS.get())) {
                if (ring.getValue(RiftLensBlock.AXIS) != facing.getAxis()) {
                    level.setBlock(at, RiftLensBlock.turnedTo(ring, facing.getAxis(), level, at), Block.UPDATE_ALL);
                }
                return;
            }
            if (!ring.isAir()) return;
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Any wrench turns it to the next of its six directions. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModTags.TOOLS_WRENCH)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) turn(level, pos, state);
        return InteractionResult.SUCCESS;
    }

    /** To the next of its six directions: down, up, north, south, west, east, and round. */
    public static void turn(Level level, BlockPos pos, BlockState state) {
        Direction next = Direction.from3DDataValue(state.getValue(FACING).get3DDataValue() + 1);
        level.setBlock(pos, state.setValue(FACING, next), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    /** Sneaking with an empty hand takes the shards and thread out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof HarvestLaserBlockEntity laser)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            for (int slot = 0; slot < laser.getOutput().getSlots(); slot++) {
                ItemStack taken = laser.getOutput().extractItem(slot, 64, false);
                if (!taken.isEmpty() && !player.addItem(taken)) player.drop(taken, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    /** The model pointing up: base plate, four corner posts round the window, top plate, muzzle. */
    private static final double[][] HOUSING = {{1, 0, 1, 15, 3, 15}, {1, 3, 1, 4, 11, 4}, {12, 3, 1, 15, 11, 4},
            {1, 3, 12, 4, 11, 15}, {12, 3, 12, 15, 11, 15}, {1, 11, 1, 15, 14, 15}, {4, 14, 4, 12, 16, 12}};
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : HOUSING) shape = Shapes.or(shape, turned(box, facing));
            SHAPES.put(facing, shape);
        }
    }

    /** A box of the upright housing, turned so its muzzle points {@code facing}. */
    private static VoxelShape turned(double[] box, Direction facing) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        return switch (facing) {
            case UP -> Block.box(x0, y0, z0, x1, y1, z1);
            case DOWN -> Block.box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case NORTH -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case SOUTH -> Block.box(x0, z0, y0, x1, z1, y1);
            case WEST -> Block.box(16 - y1, x0, z0, 16 - y0, x1, z1);
            case EAST -> Block.box(y0, x0, z0, y1, x1, z1);
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarvestLaserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntities.HARVEST_LASER_BE.get(), HarvestLaserBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.HARVEST_LASER_BE.get(), HarvestLaserBlockEntity::serverTick);
    }
}
