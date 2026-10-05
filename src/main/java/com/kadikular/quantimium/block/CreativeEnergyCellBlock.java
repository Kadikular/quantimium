package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.CreativeEnergyCellBlockEntity;
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
 * Creative testing only: endless FE, pushed into every neighbour each tick and there for any cable to
 * pull. Stands in for a partner mod's creative battery, so machines can be run in a pack without one.
 * No recipe.
 */
public class CreativeEnergyCellBlock extends BaseEntityBlock implements BlockTooltip {

    public static final MapCodec<CreativeEnergyCellBlock> CODEC = simpleCodec(CreativeEnergyCellBlock::new);

    public CreativeEnergyCellBlock(Properties properties) {
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
        tooltip.accept(Component.translatable("block.quantimium.creative_energy_cell.tip").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeEnergyCellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.CREATIVE_ENERGY_CELL_BE.get(), CreativeEnergyCellBlockEntity::tick);
    }
}
