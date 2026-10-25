package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.laser.BeamAnchor;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.MirrorPlate;
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
 * A Laser Mirror: a silver plate on a steel stand. Its plate faces one of the six sides, or is turned from one towards
 * another by 22.5° or 45° (see {@link MirrorPlate}): a click on a face sets it, by hand or wrench, the middle square
 * flat, the rings round it turned towards the side clicked. A sneaking click moves the stand. Light off its silvered
 * side bounces as off a real mirror, as many beams as reach it, each its own; on its back or edge light stops. Flat,
 * facing a Cavity Mirror, it closes the cavity. A redstone signal turns it off, and then it stops light.
 */
public class LaserMirrorBlock extends BaseEntityBlock implements BeamAnchor, com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<LaserMirrorBlock> CODEC = simpleCodec(LaserMirrorBlock::new);
    public static final EnumProperty<Direction> MOUNT = EnumProperty.create("mount", Direction.class);
    public static final EnumProperty<Direction> INPUT = EnumProperty.create("input", Direction.class);
    public static final EnumProperty<Direction> OUTPUT = EnumProperty.create("output", Direction.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

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

    /** Whether its plate is half-silvered (a Laser Splitter): both sides reflect, and light goes straight through as well. */
    public boolean splits() {
        return false;
    }

    /** An output for light in by {@code input}, as mirrors were placed before plates turned freely. */
    private static Direction outputFor(Direction mount, Direction input) {
        if (input != mount.getOpposite()) return mount.getOpposite();
        for (Direction output : Direction.values()) if (output.getAxis() != input.getAxis() && output != mount) return output;
        return input;
    }

    /**
     * Stands on what it was placed against; its plate turns light coming from the side facing you away from its mount
     * where it can (half way between the two). INPUT and OUTPUT only say how a new mirror's plate starts, and how one
     * placed before plates turned freely is read (see {@link LaserMirrorBlockEntity}).
     */
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

    /**
     * Where on a face of the mirror a click lands: in the middle square ({@code ring} 0), the ring round it (1) or the
     * outer ring (2), and for a ring, which side of the face it's towards (split by the diagonals).
     */
    public record Zone(Direction face, @Nullable Direction toward, int ring) {}

    /** How far from the face's middle the middle square and the inner ring reach, in blocks. */
    private static final double SQUARE = 0.11, RING = 0.22;

    /** The zone {@code hit} (on the mirror at {@code pos}) lands in. */
    public static Zone zone(BlockPos pos, BlockHitResult hit) {
        Direction face = hit.getDirection();
        net.minecraft.world.phys.Vec3 local = hit.getLocation().subtract(pos.getCenter());
        Direction best = null;
        double far = 0;
        for (Direction side : Direction.values()) {
            if (side.getAxis() == face.getAxis()) continue;
            double along = local.x * side.getStepX() + local.y * side.getStepY() + local.z * side.getStepZ();
            if (along > far) {
                far = along;
                best = side;
            }
        }
        int ring = far < SQUARE ? 0 : far < RING ? 1 : 2;
        return new Zone(face, ring == 0 ? null : best, ring);
    }

    /**
     * The plate a click on {@code zone} sets: facing that face, turned towards the side clicked by 22.5° (the inner
     * ring) or 45° (the outer). Null on the mount's face, or for a plate facing into the mount.
     */
    @Nullable
    public static MirrorPlate plateFor(Zone zone, Direction mount) {
        if (zone.face() == mount) return null;
        MirrorPlate plate = zone.toward() == null ? MirrorPlate.flat(zone.face()) : MirrorPlate.of(zone.face(), zone.toward(), zone.ring());
        return plate.facesInto(mount) ? null : plate;
    }

    /** The mount a sneaking click on {@code zone} moves the stand to: the face clicked from its middle, else the side clicked towards. */
    public static Direction mountFor(Zone zone) {
        return zone.toward() == null ? zone.face() : zone.toward();
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
        return set(state, level, pos, player, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return set(state, level, pos, player, hit);
    }

    /**
     * A click on a face sets the plate (see {@link #plateFor}); a sneaking click moves the stand (see {@link #mountFor}),
     * turning the plate away from it if it would face into it.
     */
    private static InteractionResult set(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LaserMirrorBlockEntity mirror)) return InteractionResult.PASS;
        Zone zone = zone(pos, hit);
        Direction mount = state.getValue(MOUNT);
        if (player.isSecondaryUseActive()) {
            Direction next = mountFor(zone);
            if (next != mount) {
                level.setBlock(pos, state.setValue(MOUNT, next), Block.UPDATE_ALL);
                if (mirror.plate().facesInto(next)) mirror.setPlate(MirrorPlate.flat(next.getOpposite()));
                else LaserNetworks.changed(level);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 0.8f);
            }
            player.sendOverlayMessage(Component.translatable("message.quantimium.laser_mirror.stands",
                    Component.translatable("direction.quantimium." + next.getSerializedName())).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
        MirrorPlate plate = plateFor(zone, mount);
        if (plate == null) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.laser_mirror.mount").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (!plate.equals(mirror.plate())) {
            mirror.setPlate(plate);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f + 0.15f * plate.steps());
        }
        player.sendOverlayMessage(LaserMirrorBlockEntity.describe(plate).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
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

    /** The middle of the plate's face, which is the block's middle: beams run straight to it and away. */
    @Override
    public Vec3 beamPoint(BlockState state, BlockPos pos) {
        return pos.getCenter();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserMirrorBlockEntity(pos, state);
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
    }
}
