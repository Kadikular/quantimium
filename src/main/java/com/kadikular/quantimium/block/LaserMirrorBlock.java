package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.BeamAnchor;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A Laser Mirror: a silver plate on a steel stand that turns light through a right angle, both ways. It
 * stands on the block it was placed against (its mount), for good. Its plate faces half way between two
 * sides at right angles, neither its mount: one set with a sneaking click, by hand or wrench, the other
 * with a plain click. Light in by either leaves by the other; light from anywhere else hits the back or
 * edge of the plate and stops. A redstone signal turns it off. (INPUT and OUTPUT name the two sides.)
 */
public class LaserMirrorBlock extends BaseEntityBlock implements BeamAnchor, com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<LaserMirrorBlock> CODEC = simpleCodec(LaserMirrorBlock::new);
    public static final EnumProperty<Direction> MOUNT = EnumProperty.create("mount", Direction.class);
    public static final EnumProperty<Direction> INPUT = EnumProperty.create("input", Direction.class);
    public static final EnumProperty<Direction> OUTPUT = EnumProperty.create("output", Direction.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** How far in front of the hub the plate's face is, in pixels: where light meets it. */
    private static final double PLATE_FACE = 2.5;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        VoxelShape middle = Block.box(3, 3, 3, 13, 13, 13);
        SHAPES.put(Direction.DOWN, Shapes.or(Block.box(2, 0, 2, 14, 2, 14), middle));
        SHAPES.put(Direction.UP, Shapes.or(Block.box(2, 14, 2, 14, 16, 14), middle));
        SHAPES.put(Direction.NORTH, Shapes.or(Block.box(2, 2, 0, 14, 14, 2), middle));
        SHAPES.put(Direction.SOUTH, Shapes.or(Block.box(2, 2, 14, 14, 14, 16), middle));
        SHAPES.put(Direction.WEST, Shapes.or(Block.box(0, 2, 2, 2, 14, 14), middle));
        SHAPES.put(Direction.EAST, Shapes.or(Block.box(14, 2, 2, 16, 14, 14), middle));
    }

    public LaserMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Direction.DOWN).setValue(INPUT, Direction.NORTH)
                .setValue(OUTPUT, Direction.UP).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, INPUT, OUTPUT, POWERED);
    }

    /** Whether light coming in by {@code input} may leave by {@code output} on a mirror standing on {@code mount}. */
    public static boolean turns(Direction mount, Direction input, Direction output) {
        return input != mount && output != mount && output.getAxis() != input.getAxis();
    }

    /** An output for light in by {@code input}: away from the mount if that's at right angles, else the first that is. */
    private static Direction outputFor(Direction mount, Direction input) {
        if (turns(mount, input, mount.getOpposite())) return mount.getOpposite();
        for (Direction output : Direction.values()) if (turns(mount, input, output)) return output;
        return mount.getOpposite();
    }

    /** Stands on what it was placed against, takes light from the side facing you, and sends it away from its mount where it can. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction mount = context.getClickedFace().getOpposite();
        // On a floor or ceiling, from the side facing you; on a wall, from the way you look at it.
        Direction input = mount.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection().getOpposite()
                : context.getNearestLookingDirection().getOpposite();
        if (input == mount) input = mount.getOpposite();
        return defaultBlockState().setValue(MOUNT, mount).setValue(INPUT, input).setValue(OUTPUT, outputFor(mount, input))
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(MOUNT));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(MOUNT, rotation.rotate(state.getValue(MOUNT))).setValue(INPUT, rotation.rotate(state.getValue(INPUT)))
                .setValue(OUTPUT, rotation.rotate(state.getValue(OUTPUT)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(MOUNT, mirror.mirror(state.getValue(MOUNT))).setValue(INPUT, mirror.mirror(state.getValue(INPUT)))
                .setValue(OUTPUT, mirror.mirror(state.getValue(OUTPUT)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // Anything else in hand (the Linker, the Spectroscope) does its own thing; an empty hand uses the block.
        if (!stack.is(ModTags.TOOLS_WRENCH)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return set(state, level, pos, player, hit.getDirection(), player.isSecondaryUseActive());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return set(state, level, pos, player, hit.getDirection(), player.isSecondaryUseActive());
    }

    /** Sneaking: light comes in by the face clicked. Otherwise: it leaves by it. */
    private static InteractionResult set(BlockState state, Level level, BlockPos pos, Player player, Direction face, boolean input) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Direction mount = state.getValue(MOUNT);
        BlockState next;
        if (input) {
            if (face == mount) return refuse(player, "message.quantimium.laser_mirror.mount");
            Direction output = state.getValue(OUTPUT);
            next = state.setValue(INPUT, face).setValue(OUTPUT, turns(mount, face, output) ? output : outputFor(mount, face));
        } else {
            if (face == mount) return refuse(player, "message.quantimium.laser_mirror.mount");
            if (face.getAxis() == state.getValue(INPUT).getAxis()) return refuse(player, "message.quantimium.laser_mirror.straight");
            next = state.setValue(OUTPUT, face);
        }
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, input ? 1.0f : 1.3f);
            LaserNetworks.changed(level);
        }
        player.sendOverlayMessage(Component.translatable("message.quantimium.laser_mirror.set",
                direction(next.getValue(INPUT)), direction(next.getValue(OUTPUT))).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(Player player, String key) {
        player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private static Component direction(Direction direction) {
        return Component.translatable("direction.quantimium." + direction.getSerializedName());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            LaserNetworks.changed(level);
        }
    }

    /** The middle of the plate's face: half way between where light comes in and where it goes out. */
    @Override
    public Vec3 beamPoint(BlockState state, BlockPos pos) {
        Vec3 normal = Vec3.atLowerCornerOf(state.getValue(INPUT).getUnitVec3i()).add(Vec3.atLowerCornerOf(state.getValue(OUTPUT).getUnitVec3i())).normalize();
        return pos.getCenter().add(normal.scale(PLATE_FACE / 16.0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserMirrorBlockEntity(pos, state);
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("block.quantimium.laser_mirror.tip").withStyle(ChatFormatting.GRAY));
    }
}
