package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Core;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Part;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.item.LensItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
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
    private static final AABB FOCUS = new AABB(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, AABB> STANDS = new EnumMap<>(Direction.class);
    private static final Map<Direction, AABB> POSTS = new EnumMap<>(Direction.class);
    private static final Map<Direction, AABB> NOZZLES = new EnumMap<>(Direction.class);

    static {
        for (Direction face : Direction.values()) {
            STANDS.put(face, LaserRelayBlock.BASES.get(face));
            POSTS.put(face, LaserRelayBlock.POSTS.get(face));
            NOZZLES.put(face, LaserRelayBlock.EMITTERS.get(face));
        }
    }

    private static final Map<Integer, VoxelShape> SHAPES = new ConcurrentHashMap<>();

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
        int layout = level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus ? focus.layout() : Core.LASER.ordinal();
        return SHAPES.computeIfAbsent(layout, LaserFocusBlock::shapeOf);
    }

    private static VoxelShape shapeOf(int layout) {
        VoxelShape shape = Shapes.empty();
        if (LaserFocusBlockEntity.coreOf(layout) != Core.NONE) shape = box(FOCUS);
        for (Direction face : Direction.values()) {
            for (AABB part : boxes(LaserFocusBlockEntity.partOf(layout, face), face)) shape = Shapes.or(shape, box(part));
        }
        // Never nothing at all: something to click and break.
        return shape.isEmpty() ? box(FOCUS) : shape;
    }

    /** The boxes {@code part} on {@code face} fills, in sixteenths. */
    private static List<AABB> boxes(Part part, Direction face) {
        return switch (part) {
            case NONE -> List.of();
            case STAND -> List.of(STANDS.get(face), POSTS.get(face));
            case EMITTER, COVER -> List.of(NOZZLES.get(face));
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
                if (!was.isEmpty() && !player.getInventory().add(was)) player.drop(was, false);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModTags.TOOLS_WRENCH)) {
            if (player.isSecondaryUseActive()) return takeOff(focus, level, pos, player, face);
            return useWithoutItem(state, level, pos, player, hit);
        }
        // Focuses and attachments go on by their items; anything else in hand does its own thing.
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    /** On an emitter: opens it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus)) return InteractionResult.PASS;
        Direction face = partAt(focus, pos, hit);
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

    /** Takes off what a sneaking wrench is pointed at, the focus or an attachment, to the player; the block goes with the last. */
    private static InteractionResult takeOff(LaserFocusBlockEntity focus, Level level, BlockPos pos, Player player, @Nullable Direction face) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        List<ItemStack> taken = face != null ? focus.takePart(face) : List.of(focus.takeCore());
        if (taken.isEmpty() || taken.getFirst().isEmpty()) return InteractionResult.PASS;
        for (ItemStack stack : taken) if (!player.getInventory().add(stack)) player.drop(stack, false);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, face == null ? 0.8f : 1.0f);
        if (focus.empty()) level.removeBlock(pos, false);
        else if (face == null) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.laser_focus.taken").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus) focus.onSignal(level.hasNeighborSignal(pos));
    }

    /** Picked: its focus, or else what's on it. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        if (!(level.getBlockEntity(pos) instanceof LaserFocusBlockEntity focus)) return ItemStack.EMPTY;
        List<ItemStack> contents = focus.contents();
        return contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserFocusBlockEntity(pos, state);
    }
}
