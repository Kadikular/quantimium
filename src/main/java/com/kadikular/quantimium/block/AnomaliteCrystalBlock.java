package com.kadikular.quantimium.block;

import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.level.ScheduledTickAccess;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.entity.AnomaliteCrystalBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.phase.MirrorPhase;
import com.kadikular.quantimium.phase.OverlayBlockEdit;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import org.joml.Vector3f;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A four-stage anomalite parasite attached to one face of a powered block entity. */
public class AnomaliteCrystalBlock extends BaseEntityBlock {

    public static final MapCodec<AnomaliteCrystalBlock> CODEC = simpleCodec(AnomaliteCrystalBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);

    private static final int[] HEIGHT = {3, 5, 7, 10};
    private static final int[] RADIUS = {2, 3, 4, 5};
    private static final DustParticleOptions MOTE =
            new DustParticleOptions(ARGB.colorFromFloat(1.0f, 0.78f, 0.32f, 0.92f), 0.4f);

    public AnomaliteCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Creative-only placement avoids a survival player placing a block they cannot see. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isCreative()) return null;
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(AGE, 3);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos host = hostPos(pos, state.getValue(FACING));
        return !level.getBlockState(host).isAir() && level.getBlockEntity(host) != null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!Config.anomaliteEnabled()) return;
        if (isCultivated(level, pos, state.getValue(FACING))) return;

        if (QuantumFlux.chunkContained(level, pos)) {
            if (random.nextInt(100) < Config.anomaliteDecayChance()) recede(state, level, pos);
            return;
        }
        if (state.getValue(AGE) >= 3
                || random.nextInt(100) >= Config.anomaliteGrowthChance()
                || QuantumFlux.chunkAnomalyBand(level, pos).ordinal() < FluxBand.HIGH.ordinal()) return;
        if (!(level.getBlockEntity(pos) instanceof AnomaliteCrystalBlockEntity crystal)
                || !crystal.wasRecentlyFed(level.getGameTime())) return;
        level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_ALL);
    }

    /**
     * Rare magenta motes so an unphased factory still reads that a crystal is feeding on a host.
     * Bigger clusters leak a little more often.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (random.nextInt(18 - age * 3) != 0) return;
        Direction outward = state.getValue(FACING);
        double along = (HEIGHT[age] / 16.0) * (0.25 + random.nextDouble() * 0.7);
        double u = (random.nextDouble() - 0.5) * (RADIUS[age] / 8.0);
        double v = (random.nextDouble() - 0.5) * (RADIUS[age] / 8.0);
        double x = pos.getX() + 0.5 + outward.getStepX() * along;
        double y = pos.getY() + 0.5 + outward.getStepY() * along;
        double z = pos.getZ() + 0.5 + outward.getStepZ() * along;
        if (outward.getAxis() == Direction.Axis.Y) {
            x += u;
            z += v;
        } else if (outward.getAxis() == Direction.Axis.X) {
            y += u;
            z += v;
        } else {
            x += u;
            y += v;
        }
        level.addParticle(MOTE, x, y, z, 0.0, 0.0, 0.0);
    }

    /** Starved back down a stage; a receding bud leaves nothing behind to harvest. */
    private static void recede(BlockState state, ServerLevel level, BlockPos pos) {
        int age = state.getValue(AGE);
        if (age <= 0) {
            OverlayBlockEdit.allow(() -> level.destroyBlock(pos, false));
            return;
        }
        level.setBlock(pos, state.setValue(AGE, age - 1), Block.UPDATE_ALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        if (hiddenFrom(context)) return Shapes.empty();
        int age = state.getValue(AGE);
        return shape(state.getValue(FACING), HEIGHT[age], RADIUS[age]);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /**
     * A player in the backing world builds straight through crystals they cannot perceive; a phased
     * one, or a falling block, meets them as solid. The block is flagged replaceable so placers that
     * skip this and read only the flag (AE2's cable parts) behave like a player in the backing world.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getPlayer() != null && !MirrorPhase.isPhased(context.getPlayer());
    }

    /** The replaceable flag would otherwise let buckets and flowing fluids wash crystals away. */
    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    /**
     * Water, pistons, and neighbour updates invoke the loot table without a breaker. Only a real
     * player who is in the Mirror Phase may materialise a crystal the backing world cannot perceive.
     * Fake players (annihilation planes, digital miners) are the backing world.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemInstance tool = params.getOptionalParameter(LootContextParams.TOOL);
        return params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player player
                && MirrorPhase.canHarvestOverlay(player)
                && tool instanceof ItemStack toolStack
                && !toolStack.isEmpty()
                ? super.getDrops(state, params)
                : List.of();
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       ItemStack toolStack, boolean willHarvest, FluidState fluid) {
        if (!MirrorPhase.canHarvestOverlay(player)) return false;
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnomaliteCrystalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.ANOMALITE_CRYSTAL_BE.get(),
                AnomaliteCrystalBlockEntity::tick);
    }

    public static BlockPos hostPos(BlockPos crystalPos, Direction outward) {
        return crystalPos.relative(outward.getOpposite());
    }

    /** Farm crystals on Budding Anomalite ignore anomaly, flux, and containment recede. */
    public static boolean isCultivated(LevelReader level, BlockPos crystalPos, Direction outward) {
        return level.getBlockState(hostPos(crystalPos, outward)).is(ModBlocks.BUDDING_ANOMALITE.get());
    }

    /**
     * Only an actual unphased player loses the shape. {@code CollisionContext.empty()} is itself an
     * entity context holding no entity, and callers like the terrain particle engine take
     * {@code bounds()} of whatever comes back, so an entity-less query must stay physical.
     */
    private static boolean hiddenFrom(CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && !MirrorPhase.isPhased(player);
    }

    private static VoxelShape shape(Direction outward, int height, int radius) {
        double low = 8 - radius;
        double high = 8 + radius;
        return switch (outward) {
            case UP -> Block.box(low, 0, low, high, height, high);
            case DOWN -> Block.box(low, 16 - height, low, high, 16, high);
            case EAST -> Block.box(0, low, low, height, high, high);
            case WEST -> Block.box(16 - height, low, low, 16, high, high);
            case SOUTH -> Block.box(low, low, 0, high, high, height);
            case NORTH -> Block.box(low, low, 16 - height, high, high, 16);
        };
    }
}
