package com.kadikular.quantimium.block;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.ScheduledTickAccess;
import com.kadikular.quantimium.block.entity.StabilisedPortalBlockEntity;
import com.kadikular.quantimium.phase.MirrorPhase;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Interior of a built 2×3 mirror gate. Visible and usable in both realms. */
public final class StabilisedPortalBlock extends BaseEntityBlock {

    public static final MapCodec<StabilisedPortalBlock> CODEC = simpleCodec(StabilisedPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public StabilisedPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StabilisedPortalBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AABB : X_AABB;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos currentPos,
                                     Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) {
        Direction.Axis facingAxis = facing.getAxis();
        Direction.Axis portalAxis = state.getValue(AXIS);
        boolean sideways = portalAxis != facingAxis && facingAxis.isHorizontal();
        if (!sideways && !facingState.is(this) && !StabilisedPortalShape.isComplete(level, currentPos, portalAxis)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, currentPos, facing, facingPos, facingState, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
        if (!inCore(state, pos, player)) return;
        MirrorPhase.tryStabilisedGate(player);
    }

    private static boolean inCore(BlockState state, BlockPos pos, Entity entity) {
        boolean thinZ = state.getValue(AXIS) == Direction.Axis.X;
        double along = thinZ ? entity.getZ() : entity.getX();
        double mid = (thinZ ? pos.getZ() : pos.getX()) + 0.5;
        return Math.abs(along - mid) <= 2.0 / 16.0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35f, random.nextFloat() * 0.4f + 0.8f, false);
        }
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double dx = (random.nextDouble() - 0.5) * 0.5;
            double dy = (random.nextDouble() - 0.5) * 0.5;
            double dz = (random.nextDouble() - 0.5) * 0.5;
            int side = random.nextInt(2) * 2 - 1;
            if (state.getValue(AXIS) == Direction.Axis.X) {
                z = pos.getZ() + 0.5 + 0.25 * side;
                dz = random.nextFloat() * 2.0f * side;
            } else {
                x = pos.getX() + 0.5 + 0.25 * side;
                dx = random.nextFloat() * 2.0f * side;
            }
            level.addParticle(ParticleTypes.PORTAL, x, y, z, dx, dy, dz);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData,
                                       Player player) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> state.getValue(AXIS) == Direction.Axis.X
                    ? state.setValue(AXIS, Direction.Axis.Z)
                    : state.setValue(AXIS, Direction.Axis.X);
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
