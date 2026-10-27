package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Core;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Part;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.item.LensItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Laser Focus block: a focus in its middle, or none, and an attachment on any of its faces (see
 * {@link LaserFocusBlockEntity}). It's never placed as itself: putting down a Laser Focus, or an attachment, makes one,
 * and more go onto it by clicking them on. A sneaking wrench takes off what it's pointed at, the focus or an attachment,
 * and leaves the rest; the block goes when it holds nothing. A click on an Emitter opens it: its lens, and how it
 * listens to redstone. A lens used on an Emitter goes into it.
 */
public class LaserFocusBlock extends BaseEntityBlock implements com.kadikular.quantimium.laser.BeamAnchor {
    public static final MapCodec<LaserFocusBlock> CODEC = simpleCodec(LaserFocusBlock::new);

    /** In sixteenths: the focus, and on each face, what's there. */
    public static final AABB FOCUS = new AABB(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, AABB> STANDS = new EnumMap<>(Direction.class);
    private static final Map<Direction, AABB> POSTS = new EnumMap<>(Direction.class);
    private static final Map<Direction, AABB> NOZZLES = new EnumMap<>(Direction.class);
    /** A Cavity Mirror plate: 10 across, a pixel thick, on the face. */
    private static final Map<Direction, AABB> PLATES = new EnumMap<>(Map.of(
            Direction.NORTH, new AABB(3, 3, 0, 13, 13, 1), Direction.SOUTH, new AABB(3, 3, 15, 13, 13, 16),
            Direction.WEST, new AABB(0, 3, 3, 1, 13, 13), Direction.EAST, new AABB(15, 3, 3, 16, 13, 13),
            Direction.DOWN, new AABB(3, 0, 3, 13, 1, 13), Direction.UP, new AABB(3, 15, 3, 13, 16, 13)));

    static {
        for (Direction face : Direction.values()) {
            STANDS.put(face, LaserRelayBlock.BASES.get(face));
            POSTS.put(face, LaserRelayBlock.POSTS.get(face));
            NOZZLES.put(face, LaserRelayBlock.EMITTERS.get(face));
        }
    }

    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public LaserFocusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Light into it goes to its focus, in its middle. */
    @Override
    public Vec3 beamPoint(BlockState state, BlockPos pos) {
        return pos.getCenter();
    }

    // ---- shape ----

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        long layout = level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus ? focus.layout() : Core.LASER.ordinal();
        return SHAPES.computeIfAbsent(layout, LaserFocusBlock::shapeOf);
    }

    private static VoxelShape shapeOf(long layout) {
        VoxelShape shape = Shapes.empty();
        if (LaserFocusBlockEntity.coreOf(layout) != Core.NONE) shape = box(FOCUS);
        for (Direction face : Direction.values()) {
            for (AABB part : boxes(LaserFocusBlockEntity.partOf(layout, face), face)) shape = Shapes.or(shape, box(part));
        }
        // Never nothing at all: something to click and break.
        return shape.isEmpty() ? box(FOCUS) : shape;
    }

    /**
     * What other blocks can rest on: its shape, and a Stand's face whole, so a lever or torch can go on a stand that
     * holds nothing up.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = getShape(state, level, pos, CollisionContext.empty());
        if (!(level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus)) return shape;
        for (Direction face : Direction.values()) {
            if (focus.part(face) != Part.STAND) continue;
            double lo = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15 : 0;
            shape = Shapes.or(shape, switch (face.getAxis()) {
                case X -> Block.box(lo, 0, 0, lo + 1, 16, 16);
                case Y -> Block.box(0, lo, 0, 16, lo + 1, 16);
                case Z -> Block.box(0, 0, lo, 16, 16, lo + 1);
            });
        }
        return shape;
    }

    /** The boxes {@code part} on {@code face} fills, in sixteenths. */
    public static List<AABB> boxes(Part part, Direction face) {
        return switch (part) {
            case NONE -> List.of();
            case STAND, RECEPTACLE -> List.of(STANDS.get(face), POSTS.get(face));
            case EMITTER, COVER -> List.of(NOZZLES.get(face));
            case CAVITY -> List.of(PLATES.get(face), NOZZLES.get(face));
        };
    }

    private static VoxelShape box(AABB sixteenths) {
        return Block.box(sixteenths.minX, sixteenths.minY, sixteenths.minZ, sixteenths.maxX, sixteenths.maxY, sixteenths.maxZ);
    }

    /** What a click at {@code hit} landed on: a face's attachment, or (null) the focus or nothing. */
    @Nullable
    public static Direction partAt(LaserFocusBlockEntity focus, BlockPos pos, BlockHitResult hit) {
        Vec3 at = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).scale(16);
        for (Direction face : Direction.values()) {
            for (AABB box : boxes(focus.part(face), face)) if (box.inflate(0.1).contains(at)) return face;
        }
        return null;
    }

    // ---- clicks ----

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus)) return InteractionResult.PASS;
        Direction face = partAt(focus, pos, hit);
        // A lens used on an emitter goes into it, in place of any there; sneaking, the emitter's lens comes out.
        if (stack.getItem() instanceof LensItem && face != null && focus.hasEmitter(face)) {
            if (!level.isClientSide()) {
                ItemStack was = focus.lens(face);
                if (player.isSecondaryUseActive()) {
                    focus.setLens(face, ItemStack.EMPTY);
                } else {
                    focus.setLens(face, stack.split(1));
                }
                if (!was.isEmpty() && !player.getInventory().add(was)) Block.popResource(level, pos, was);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModTags.TOOLS_WRENCH)) return useWithoutItem(state, level, pos, player, hit);
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        // An emitter or receptacle opens as a chest does, whatever's in hand (sneak to place against it); a focus going
        // into an empty block, and a laser tool, do their own thing.
        boolean menu = face != null && (focus.hasEmitter(face) || focus.receptacle(face) != null);
        boolean fills = stack.getItem() instanceof com.kadikular.quantimium.item.FocusPieceItem piece && piece.core() != null
                && focus.core() == LaserFocusBlockEntity.Core.NONE;
        return menu && !fills && !LaserTools.isOne(stack) ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    /** On an emitter: opens it. Sneaking, takes off what it's pointed at, as a sneaking wrench does. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus)) return InteractionResult.PASS;
        Direction face = partAt(focus, pos, hit);
        if (player.isSecondaryUseActive()) return takeOff(focus, level, pos, player, face);
        if (face != null && focus.receptacle(face) != null) return openReceptacle(focus, level, player, face);
        if (face == null || !focus.hasEmitter(face)) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider(
                    (id, inventory, who) -> new com.kadikular.quantimium.menu.RelayEmitterMenu(id, inventory, focus, face),
                    Component.translatable("gui.quantimium.relay_emitter.title", Component.translatable("direction.quantimium." + face.getSerializedName()))),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeEnum(face);
                    });
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens the receptacle on {@code face}: its role, priority, filter and the rest. */
    public static InteractionResult openReceptacle(LaserFocusBlockEntity focus, Level level, Player player, Direction face) {
        if (!level.isClientSide() && player instanceof ServerPlayer server) {
            var receptacle = focus.receptacle(face);
            // Whether what it's on takes power, for the screen to offer powering it.
            receptacle.checkPower();
            server.openMenu(new SimpleMenuProvider((id, inventory, who) -> new com.kadikular.quantimium.menu.LogisticsReceptacleMenu(id, inventory, receptacle),
                    Component.translatable("block.quantimium.logistics_receptacle")), buffer -> {
                buffer.writeBlockPos(focus.getBlockPos());
                buffer.writeEnum(face);
            });
        }
        return InteractionResult.SUCCESS;
    }

    /** Takes off what a sneaking wrench is pointed at, the focus or an attachment, to the player; the block goes with the last. */
    private static InteractionResult takeOff(LaserFocusBlockEntity focus, Level level, BlockPos pos, Player player, @Nullable Direction face) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        List<ItemStack> taken = face != null ? focus.takePart(face) : List.of(focus.takeCore());
        if (taken.isEmpty() || taken.getFirst().isEmpty()) return InteractionResult.PASS;
        // What won't fit drops where it came off, not from the player; in creative, nothing.
        if (!player.isCreative()) for (ItemStack stack : taken) if (!player.getInventory().add(stack)) Block.popResource(level, pos, stack);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, face == null ? 0.8f : 1.0f);
        if (focus.empty()) level.removeBlock(pos, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus) focus.onSignal(level.hasNeighborSignal(pos));
    }

    /** Picked: what the player's looking at, an attachment or the focus; else anything it holds. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        if (!(level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus)) return ItemStack.EMPTY;
        if (player.pick(player.blockInteractionRange(), 1.0f, false) instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)) {
            Direction face = partAt(focus, pos, hit);
            if (face != null) return focus.part(face).item();
            if (focus.core() != Core.NONE) return focus.core().item();
        }
        List<ItemStack> contents = focus.contents();
        return contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst();
    }

    /** Broken in creative, it drops nothing. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus) focus.clearContents();
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** On the client, each one checks its model shows what it holds (see {@link LaserFocusBlockEntity#clientTick}). */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                                         net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, com.kadikular.quantimium.init.ModBlockEntities.LASER_FOCUS_BE.get(),
                (world, at, blockState, focus) -> focus.clientTick())
                : createTickerHelper(type, com.kadikular.quantimium.init.ModBlockEntities.LASER_FOCUS_BE.get(), (world, at, blockState, focus) -> focus.serverTick());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserFocusBlockEntity(pos, state);
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
