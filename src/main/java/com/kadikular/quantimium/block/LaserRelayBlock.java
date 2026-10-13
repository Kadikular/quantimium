package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserRelayBlockEntity;
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
 * A Laser Relay: a focus on a post, on a base on the block it was placed against, with emitters on any of its
 * other sides. Whatever light reaches its focus, from any side but its base, leaves by each emitter, refocused,
 * shared by what each beam's blocks ask for. Placed, its one emitter faces you. A sneaking click (by hand or with a
 * wrench) on a side of the focus puts an emitter there; on an emitter, takes it away, dropping its lens. A plain
 * click on an emitter opens it: its lens, and how it listens to redstone.
 *
 * <p>The Isolator, built on it, has no base: a wrench or a sneaking empty hand on a face adds or takes away an
 * output there (one always stays), and an empty hand steps through how it listens to redstone.
 */
public class LaserRelayBlock extends BaseEntityBlock implements com.kadikular.quantimium.laser.BeamAnchor {
    public static final MapCodec<LaserRelayBlock> CODEC = simpleCodec(LaserRelayBlock::new);
    /** The face it was placed facing: its first output. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** Whether light leaves by that face: what the block shows. */
    public static final java.util.Map<Direction, net.minecraft.world.level.block.state.properties.BooleanProperty> OUTPUTS =
            net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;
    /** The side its base stands on: the block it was placed against. */
    public static final EnumProperty<Direction> MOUNT = EnumProperty.create("mount", Direction.class);

    /** In sixteenths: its focus, an emitter on each side, its base (with its boss) on each side, and its post to it. */
    private static final net.minecraft.world.phys.AABB FOCUS = new net.minecraft.world.phys.AABB(6, 6, 6, 10, 10, 10);
    static final java.util.Map<Direction, net.minecraft.world.phys.AABB> EMITTERS = new java.util.EnumMap<>(java.util.Map.of(
            Direction.NORTH, new net.minecraft.world.phys.AABB(5, 5, 0, 11, 11, 6), Direction.SOUTH, new net.minecraft.world.phys.AABB(5, 5, 10, 11, 11, 16),
            Direction.WEST, new net.minecraft.world.phys.AABB(0, 5, 5, 6, 11, 11), Direction.EAST, new net.minecraft.world.phys.AABB(10, 5, 5, 16, 11, 11),
            Direction.DOWN, new net.minecraft.world.phys.AABB(5, 0, 5, 11, 6, 11), Direction.UP, new net.minecraft.world.phys.AABB(5, 10, 5, 11, 16, 11)));
    static final java.util.Map<Direction, net.minecraft.world.phys.AABB> BASES = new java.util.EnumMap<>(java.util.Map.of(
            Direction.DOWN, new net.minecraft.world.phys.AABB(2, 0, 2, 14, 2, 14), Direction.UP, new net.minecraft.world.phys.AABB(2, 14, 2, 14, 16, 14),
            Direction.NORTH, new net.minecraft.world.phys.AABB(2, 2, 0, 14, 14, 2), Direction.SOUTH, new net.minecraft.world.phys.AABB(2, 2, 14, 14, 14, 16),
            Direction.WEST, new net.minecraft.world.phys.AABB(0, 2, 2, 2, 14, 14), Direction.EAST, new net.minecraft.world.phys.AABB(14, 2, 2, 16, 14, 14)));
    static final java.util.Map<Direction, net.minecraft.world.phys.AABB> POSTS = new java.util.EnumMap<>(java.util.Map.of(
            Direction.DOWN, new net.minecraft.world.phys.AABB(7, 2, 7, 9, 6, 9), Direction.UP, new net.minecraft.world.phys.AABB(7, 10, 7, 9, 14, 9),
            Direction.NORTH, new net.minecraft.world.phys.AABB(7, 7, 2, 9, 9, 6), Direction.SOUTH, new net.minecraft.world.phys.AABB(7, 7, 10, 9, 9, 14),
            Direction.WEST, new net.minecraft.world.phys.AABB(2, 7, 7, 6, 9, 9), Direction.EAST, new net.minecraft.world.phys.AABB(10, 7, 7, 14, 9, 9)));
    private static final java.util.Map<BlockState, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public LaserRelayBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Direction.DOWN);
        for (var property : OUTPUTS.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
        OUTPUTS.values().forEach(builder::add);
    }

    /** Light into it goes to its focus, in its middle. */
    @Override
    public net.minecraft.world.phys.Vec3 beamPoint(BlockState state, BlockPos pos) {
        return pos.getCenter();
    }

    /** Whether it stands on a base: a Relay does; the Isolator, built on it, is a plain block. */
    public boolean mounted() {
        return true;
    }

    /** On the block it was placed against, its one emitter facing you (away from its base, if you face that). */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction mount = context.getClickedFace().getOpposite();
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (mounted() && facing == mount) facing = mount.getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(MOUNT, mount).setValue(OUTPUTS.get(facing), true);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                  net.minecraft.world.phys.shapes.CollisionContext context) {
        if (!mounted()) return net.minecraft.world.phys.shapes.Shapes.block();
        return SHAPES.computeIfAbsent(state, LaserRelayBlock::shapeOf);
    }

    private static net.minecraft.world.phys.shapes.VoxelShape shapeOf(BlockState state) {
        Direction mount = state.getValue(MOUNT);
        net.minecraft.world.phys.shapes.VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.or(box(FOCUS), box(BASES.get(mount)), box(POSTS.get(mount)));
        for (Direction face : Direction.values()) {
            if (face != mount && state.getValue(OUTPUTS.get(face))) shape = net.minecraft.world.phys.shapes.Shapes.or(shape, box(EMITTERS.get(face)));
        }
        return shape;
    }

    private static net.minecraft.world.phys.shapes.VoxelShape box(net.minecraft.world.phys.AABB sixteenths) {
        return Block.box(sixteenths.minX, sixteenths.minY, sixteenths.minZ, sixteenths.maxX, sixteenths.maxY, sixteenths.maxZ);
    }

    /** The emitter a click at {@code hit} landed on, if it did. */
    @Nullable
    private static Direction emitterAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        net.minecraft.world.phys.Vec3 at = hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)).scale(16);
        for (Direction face : Direction.values()) {
            if (face != state.getValue(MOUNT) && state.getValue(OUTPUTS.get(face)) && EMITTERS.get(face).inflate(0.1).contains(at)) return face;
        }
        return null;
    }

    /** Whether a click at {@code hit} landed on the focus. */
    private static boolean onFocus(BlockPos pos, BlockHitResult hit) {
        return FOCUS.inflate(0.1).contains(hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)).scale(16));
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
        // A lens used on an emitter goes into it, in place of any there.
        if (mounted() && !player.isSecondaryUseActive() && stack.getItem() instanceof com.kadikular.quantimium.item.LensItem) {
            Direction emitter = emitterAt(state, pos, hit);
            if (emitter == null) return InteractionResult.PASS;
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity relay) {
                ItemStack was = relay.lens(emitter);
                relay.setLens(emitter, stack.split(1));
                if (!was.isEmpty() && !player.getInventory().add(was)) player.drop(was, false);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        // Anything else in hand (the Linker, the Spectroscope) does its own thing; an empty hand uses the block.
        if (!stack.is(ModTags.TOOLS_WRENCH)) return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        if (!mounted()) return toggle(level, pos, hit.getDirection(), player);
        return addOrRemove(state, level, pos, player, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!mounted()) {
            if (player.isSecondaryUseActive()) return toggle(level, pos, hit.getDirection(), player);
            return cycleMode(level, pos, player);
        }
        if (player.isSecondaryUseActive()) return addOrRemove(state, level, pos, player, hit);
        Direction emitter = emitterAt(state, pos, hit);
        if (emitter == null) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer server
                && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity relay) {
            server.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, who) -> new com.kadikular.quantimium.menu.RelayEmitterMenu(id, inventory, relay, emitter),
                    net.minecraft.network.chat.Component.translatable("gui.quantimium.relay_emitter.title",
                            net.minecraft.network.chat.Component.translatable("direction.quantimium." + emitter.getSerializedName()))),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeEnum(emitter);
                    });
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * On an emitter: takes its lens out first, to the player; with none in it, takes the emitter away. On a side of
     * the focus: puts an emitter there, but not on its base.
     */
    private static InteractionResult addOrRemove(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof LaserRelayBlockEntity relay)) return InteractionResult.SUCCESS;
        Direction emitter = emitterAt(state, pos, hit);
        boolean changed;
        if (emitter != null && !relay.lens(emitter).isEmpty()) {
            ItemStack lens = relay.lens(emitter);
            relay.setLens(emitter, ItemStack.EMPTY);
            if (!player.getInventory().add(lens)) player.drop(lens, false);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        } else if (emitter != null) {
            changed = relay.removeEmitter(emitter);
        } else if (onFocus(pos, hit)) {
            changed = relay.addEmitter(hit.getDirection());
        } else {
            return InteractionResult.PASS;
        }
        if (changed) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, emitter == null ? 1.3f : 0.8f);
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.laser_relay.outputs",
                    String.join(", ", relay.outputs().stream().map(Direction::getSerializedName).toList()))
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    /** An empty hand steps through how it listens to redstone: ignored, with a signal, without one, pulse. */
    protected static InteractionResult cycleMode(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity mirror) {
            mirror.cycleMode();
            player.sendOverlayMessage(mirror.mode().label().copy().withStyle(net.minecraft.ChatFormatting.AQUA));
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6f, 1.0f + 0.1f * mirror.mode().ordinal());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity mirror) mirror.onSignal(level.hasNeighborSignal(pos));
    }

    /** Adds an output on the face clicked, or takes it away: one always stays. */
    private static InteractionResult toggle(Level level, BlockPos pos, Direction face, @Nullable Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserRelayBlockEntity relay && relay.toggleOutput(face)) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, relay.outputs().contains(face) ? 1.3f : 0.8f);
            if (player != null) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.laser_relay.outputs",
                        String.join(", ", relay.outputs().stream().map(Direction::getSerializedName).toList()))
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserRelayBlockEntity(pos, state);
    }
}
