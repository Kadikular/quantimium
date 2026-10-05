package com.kadikular.quantimium.block;

import net.minecraft.core.Direction;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.multiblock.PartClaim;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Controller for the Anomaly Containment Hall, buried at the centre of its own plinth base. */
public class AnomalyContainmentHallBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<AnomalyContainmentHallBlock> CODEC =
            simpleCodec(AnomalyContainmentHallBlock::new);

    public AnomalyContainmentHallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(QuantumFoundryStructure.FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, QuantumFoundryStructure.FORMED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ContainmentHallBlockEntity hall) {
            player.openMenu(hall, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A held cell is never quite still: motes rise and drift inside the glass, and now and then one
     * is drawn back down into the middle. Client-side and cosmetic, seen from both realms.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ContainmentHallBlockEntity hall) || !hall.isOccupied()) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 1.1 + random.nextDouble() * 2.8;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0.0, 0.01 + random.nextDouble() * 0.02, 0.0);
        if (random.nextInt(3) == 0) {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 2.5;
            double cz = pos.getZ() + 0.5;
            // Portal motes ease from origin + offset into the origin: here, into the heart of the cell.
            level.addParticle(ParticleTypes.PORTAL, cx, cy, cz,
                    (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 2.0 - 1.0,
                    (random.nextDouble() - 0.5) * 0.8);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ContainmentHallBlockEntity hall) {
            hall.revalidateStructure();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof ContainmentHallBlockEntity hall) {
            return switch (hall.getStatusCode()) {
                case ContainmentHallBlockEntity.STATUS_CONTAINING -> 15;
                case ContainmentHallBlockEntity.STATUS_OVERWHELMED -> 7;
                default -> 0;
            };
        }
        return 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContainmentHallBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,
                ModBlockEntities.ANOMALY_CONTAINMENT_HALL_BE.get(), ContainmentHallBlockEntity::tick);
    }
}
