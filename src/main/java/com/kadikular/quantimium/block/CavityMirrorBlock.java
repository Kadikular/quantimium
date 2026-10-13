package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A Cavity Mirror: a Relay with a flat Splitter mirror for its front. Two facing each other across a clear gap hold
 * light between them. Placed, its mirror faces you; a wrench on a face turns the mirror there. Its outputs are set
 * as a Relay's are: a sneaking empty hand on a face adds one there, or takes it away; never on the mirror.
 */
public class CavityMirrorBlock extends BaseEntityBlock implements com.kadikular.quantimium.item.BlockTooltip, com.kadikular.quantimium.laser.BeamAnchor {
    public static final MapCodec<CavityMirrorBlock> CODEC = simpleCodec(CavityMirrorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** The face it stands on, as a Relay does: never its mirror nor an output, and no light comes in by it. */
    public static final EnumProperty<Direction> MOUNT = LaserRelayBlock.MOUNT;
    /** Which faces light leaves by, for the model: each shows an emitter. */
    public static final java.util.Map<Direction, BooleanProperty> OUTPUTS = net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;
    private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = new java.util.EnumMap<>(Direction.class);

    static {
        // Drawn with its mirror north: the plate on that face, the rod back to the focus, and the focus.
        for (Direction face : Direction.values()) {
            SHAPES.put(face, net.minecraft.world.phys.shapes.Shapes.or(turned(face, 3, 3, 0, 13, 13, 1), turned(face, 7, 7, 1, 9, 9, 6),
                    turned(face, 6, 6, 6, 10, 10, 10)));
        }
    }

    /** A box drawn with the mirror north, turned so the mirror faces {@code face}. */
    private static net.minecraft.world.phys.shapes.VoxelShape turned(Direction face, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[] a = place(face, x0, y0, z0);
        double[] b = place(face, x1, y1, z1);
        return Block.box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    private static double[] place(Direction face, double x, double y, double z) {
        return switch (face) {
            case NORTH -> new double[] {x, y, z};
            case SOUTH -> new double[] {16 - x, y, 16 - z};
            case WEST -> new double[] {z, y, 16 - x};
            case EAST -> new double[] {16 - z, y, x};
            case UP -> new double[] {x, 16 - z, 16 - y};
            case DOWN -> new double[] {x, z, y};
        };
    }

    public CavityMirrorBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(MOUNT, Direction.DOWN);
        for (BooleanProperty output : OUTPUTS.values()) state = state.setValue(output, false);
        registerDefaultState(state.setValue(OUTPUTS.get(Direction.SOUTH), true));
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                  net.minecraft.world.phys.shapes.CollisionContext context) {
        Direction mount = state.getValue(MOUNT);
        return net.minecraft.world.phys.shapes.Shapes.or(SHAPES.get(state.getValue(FACING)), box(LaserRelayBlock.BASES.get(mount)), box(LaserRelayBlock.POSTS.get(mount)));
    }

    private static net.minecraft.world.phys.shapes.VoxelShape box(net.minecraft.world.phys.AABB sixteenths) {
        return Block.box(sixteenths.minX, sixteenths.minY, sixteenths.minZ, sixteenths.maxX, sixteenths.maxY, sixteenths.maxZ);
    }

    /** Light comes in to the focus in its middle. */
    @Override
    public net.minecraft.world.phys.Vec3 beamPoint(BlockState state, BlockPos pos) {
        return net.minecraft.world.phys.Vec3.atCenterOf(pos);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, MOUNT);
        OUTPUTS.values().forEach(builder::add);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction mount = context.getClickedFace().getOpposite();
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (facing == mount) facing = mount.getOpposite();
        Direction output = outputFor(facing, mount);
        return defaultBlockState().setValue(OUTPUTS.get(Direction.SOUTH), false).setValue(FACING, facing).setValue(MOUNT, mount).setValue(OUTPUTS.get(output), true);
    }

    /** Where its one output goes to begin with: behind its mirror, or if that's its base, the first side free of both. */
    public static Direction outputFor(Direction facing, Direction mount) {
        if (facing.getOpposite() != mount) return facing.getOpposite();
        for (Direction side : Direction.values()) {
            if (side != facing && side != mount && side.getAxis() != facing.getAxis()) return side;
        }
        return facing.getOpposite();
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
    public void appendTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip) {
        String rate = String.format(java.util.Locale.ROOT, "%,d", com.kadikular.quantimium.laser.LaserTuning.CAVITY_RATE);
        String held = String.format(java.util.Locale.ROOT, "%,d", com.kadikular.quantimium.laser.LaserTuning.CAVITY_FE_PER_BLOCK);
        tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.cavity_mirror.tip", rate, held)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // Anything else in hand (the Linker, the Spectroscope) does its own thing; an empty hand uses the block.
        if (!stack.is(ModTags.TOOLS_WRENCH)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return point(state, level, pos, hit.getDirection());
    }

    /** Sneaking: an output on the face clicked, on or off. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        Direction face = hit.getDirection();
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity mirror) {
            if (!mirror.toggleOutput(face)) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.cavity.not_on_mirror"));
            } else {
                boolean on = mirror.outputFaces().contains(face);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, on ? 1.3f : 0.8f);
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(on
                        ? "message.quantimium.cavity.output_on" : "message.quantimium.cavity.output_off"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Turns the mirror to the face clicked; an output there goes. */
    private static InteractionResult point(BlockState state, Level level, BlockPos pos, Direction face) {
        if (face == state.getValue(MOUNT)) return InteractionResult.SUCCESS;
        if (!level.isClientSide() && state.getValue(FACING) != face) {
            level.setBlock(pos, state.setValue(FACING, face), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            if (level.getBlockEntity(pos) instanceof com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity mirror) mirror.outputsChanged();
            LaserNetworks.changed(level);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CavityMirrorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CAVITY_MIRROR_BE.get(), CavityMirrorBlockEntity::serverTick);
    }
}
