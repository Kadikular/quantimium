package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.SideConfigWrench;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class QuantumCrafterBlock extends HorizontalMachineBlock {

    public static final MapCodec<QuantumCrafterBlock> CODEC = simpleCodec(QuantumCrafterBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /**
     * Which face the side configuration screen calls the front. The chassis looks the same all round,
     * so the renderer marks this face rather than the model being turned.
     */

    public QuantumCrafterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof QuantumCrafterBlockEntity crafter)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // A bucket, or anything else that holds fluid, fills its input tanks, or empty, fills from its output tanks.
        if (net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerInteraction(player, hand)
                .getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM) != null) {
            if (!level.isClientSide()) net.neoforged.neoforge.transfer.fluid.FluidUtil.interactWithFluidHandler(player, hand, pos, crafter.playerFluids(), null);
            return InteractionResult.SUCCESS;
        }
        return SideConfigWrench.handleUseItemOn(crafter, stack, level, player, hitResult);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumCrafterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, ModBlockEntities.QUANTUM_CRAFTER_BE.get(), QuantumCrafterBlockEntity::tick);
    }
}
