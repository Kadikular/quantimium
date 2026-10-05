package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.SideConfigWrench;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Open frame that docks an Entangled Link and proxies item/fluid I/O to the bound inventory.
 * The silhouette is a base, four mid-edge posts and face port plates, with no plate on the open face,
 * so the tesseract can sit in the void. It faces away from what it was placed against: on a floor its
 * open face is up, on a machine's side it points out from that side, so it can hang off any face.
 *
 * <p>Interaction ladder: dock a link into an empty slot, sneak with an empty hand to eject it,
 * wrench (or sneak+wrench) to cycle item/fluid mode on the hit face, otherwise open side config.
 */
public class TesseractStabilizerBlock extends BaseEntityBlock {

    public static final MapCodec<TesseractStabilizerBlock> CODEC = simpleCodec(TesseractStabilizerBlock::new);

    /** The open face: away from the block it was placed against. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /** Combined frame collision so the open centre stays walkable-looking but ports are solid; open face up. */
    private static final VoxelShape SHAPE_UP = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(6, 2, 0, 10, 14, 2),
            Block.box(6, 2, 14, 10, 14, 16),
            Block.box(0, 2, 6, 2, 14, 10),
            Block.box(14, 2, 6, 16, 14, 10),
            Block.box(5, 5, 0, 11, 11, 1),
            Block.box(5, 5, 15, 11, 11, 16),
            Block.box(0, 5, 5, 1, 11, 11),
            Block.box(15, 5, 5, 16, 11, 11)
    );

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) SHAPES.put(facing, rotate(SHAPE_UP, facing));
    }

    public TesseractStabilizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * The open-face-up shape turned to {@code facing}, the same turns the blockstate gives the model:
     * x 180 for down, x 90 for north, then y 90 / 180 / 270 for east, south and west.
     */
    private static VoxelShape rotate(VoxelShape shape, Direction facing) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double[] a = turn(x0, y0, z0, facing);
            double[] b = turn(x1, y1, z1, facing);
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])));
        });
        return out[0].optimize();
    }

    /** A point of the up-facing frame (in blocks) where it lands facing {@code facing}. */
    private static double[] turn(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case UP -> new double[] {x, y, z};
            case DOWN -> new double[] {x, 1 - y, 1 - z};
            case NORTH -> new double[] {x, z, 1 - y};
            case SOUTH -> new double[] {1 - x, z, y};
            case EAST -> new double[] {y, z, x};
            case WEST -> new double[] {1 - y, z, 1 - x};
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof TesseractStabilizerBlockEntity stabilizer)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (stabilizer.getLink().isEmpty()) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                ItemStack link = stabilizer.removeLink();
                if (!link.isEmpty() && !player.addItem(link)) {
                    Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), link);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            player.openMenu(stabilizer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof TesseractStabilizerBlockEntity stabilizer)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        // Empty hand falls through to useWithoutItem (eject on sneak, else open config).
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        InteractionResult wrench = SideConfigWrench.handleUseItemOn(
                stabilizer, stack, level, player, hitResult);
        if (wrench != InteractionResult.TRY_WITH_EMPTY_HAND) {
            return wrench;
        }

        if (EntangledLinks.fitsStabilizer(stack) && stabilizer.getLink().isEmpty()) {
            if (!level.isClientSide()) stabilizer.insertLink(stack, player);
            return InteractionResult.SUCCESS;
        }

        // Non-wrench items (and a link when the dock is already full) open the side config.
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TesseractStabilizerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.TESSERACT_STABILIZER_BE.get(),
                TesseractStabilizerBlockEntity::tick);
    }
}
