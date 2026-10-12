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
 * A Lens Mount: a holder on a base, its four lenses standing in a row. Light goes straight through it, in by
 * the side behind its output and out of its output, through its lenses in order. Its output is never along its
 * base's axis: on a floor, it shines sideways. Placed, it shines towards you; a wrench, or a sneaking empty
 * hand, on a side points it there; an empty hand opens it.
 */
public class LensMountBlock extends BaseEntityBlock {
    public static final MapCodec<LensMountBlock> CODEC = simpleCodec(LensMountBlock::new);
    /** The way light leaves it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** The side its base stands on: the block it was placed against. */
    public static final EnumProperty<Direction> MOUNT = EnumProperty.create("mount", Direction.class);

    /**
     * Where each lens stands, in sixteenths along the holder from where light leaves (0) to where it comes in
     * (16): the first lens nearest where light comes in.
     */
    public static final double[] SLOT_ALONG = {12.5, 9.5, 6.5, 3.5};

    /** The holder drawn standing on a floor, shining north: its base, and the space its lenses stand in. */
    private static final net.minecraft.world.phys.AABB[] HOLDER = {new net.minecraft.world.phys.AABB(3, 0, 0, 13, 2, 16),
            new net.minecraft.world.phys.AABB(4, 2, 1, 12, 12, 15)};
    private static final java.util.Map<BlockState, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public LensMountBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Direction.DOWN));
    }

    /**
     * A point of the holder as drawn standing on a floor shining north (in sixteenths: x across, y up from the
     * base, z from where light leaves), turned to stand on {@code mount} shining {@code facing}: in sixteenths of
     * the block. The model, its shape and where its lenses are drawn all turn this way.
     */
    public static net.minecraft.world.phys.Vec3 turn(double x, double y, double z, Direction mount, Direction facing) {
        net.minecraft.world.phys.Vec3 up = net.minecraft.world.phys.Vec3.atLowerCornerOf(mount.getOpposite().getUnitVec3i());
        net.minecraft.world.phys.Vec3 forward = net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getUnitVec3i());
        net.minecraft.world.phys.Vec3 across = forward.cross(up);
        return new net.minecraft.world.phys.Vec3(8, 8, 8).add(across.scale(x - 8)).add(up.scale(y - 8)).add(forward.scale(8 - z));
    }

    private static net.minecraft.world.phys.AABB turn(net.minecraft.world.phys.AABB box, Direction mount, Direction facing) {
        return new net.minecraft.world.phys.AABB(turn(box.minX, box.minY, box.minZ, mount, facing), turn(box.maxX, box.maxY, box.maxZ, mount, facing));
    }

    /** Whether it can shine {@code facing} standing on {@code mount}: never along its base's axis. */
    public static boolean shines(Direction mount, Direction facing) {
        return facing.getAxis() != mount.getAxis();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    /** On the block it was placed against, shining towards you, or as near that as its base lets it. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction mount = context.getClickedFace().getOpposite();
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (!shines(mount, facing)) facing = context.getHorizontalDirection().getOpposite();
        if (!shines(mount, facing)) facing = facing.getClockWise();
        return defaultBlockState().setValue(FACING, facing).setValue(MOUNT, mount);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                  net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPES.computeIfAbsent(state, LensMountBlock::shapeOf);
    }

    private static net.minecraft.world.phys.shapes.VoxelShape shapeOf(BlockState state) {
        net.minecraft.world.phys.shapes.VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.empty();
        for (net.minecraft.world.phys.AABB part : HOLDER) {
            net.minecraft.world.phys.AABB turned = turn(part, state.getValue(MOUNT), state.getValue(FACING));
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, Block.box(turned.minX, turned.minY, turned.minZ, turned.maxX, turned.maxY, turned.maxZ));
        }
        return shape;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING))).setValue(MOUNT, rotation.rotate(state.getValue(MOUNT)));
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

    /** Points it out of the side clicked, if its base lets it shine that way. */
    private static InteractionResult point(BlockState state, Level level, BlockPos pos, Direction face) {
        if (!level.isClientSide() && state.getValue(FACING) != face && shines(state.getValue(MOUNT), face)) {
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
