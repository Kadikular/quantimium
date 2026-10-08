package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.item.BlockTooltip;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Creative testing only: takes all the FE anything gives it, on any face, and keeps none. A load that
 * never fills, for seeing how much a cable or a laser carries. No recipe.
 */
public class CreativeEnergyVoidBlock extends BaseEntityBlock implements BlockTooltip {

    public static final MapCodec<CreativeEnergyVoidBlock> CODEC = simpleCodec(CreativeEnergyVoidBlock::new);

    public CreativeEnergyVoidBlock(Properties properties) {
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

    @Override
    public void appendTooltip(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("block.quantimium.creative_energy_void.tip").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeEnergyVoidBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CREATIVE_ENERGY_VOID_BE.get(), CreativeEnergyVoidBlockEntity::tick);
    }
}
