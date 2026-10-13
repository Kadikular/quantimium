package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Logistics Materialiser: where a controller's light ends, it offers the whole logistics network to any mod's pipes
 * and storage buses, on every face.
 */
public class LogisticsMaterialiserBlock extends BaseEntityBlock implements com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<LogisticsMaterialiserBlock> CODEC = simpleCodec(LogisticsMaterialiserBlock::new);

    public LogisticsMaterialiserBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.logistics_materialiser.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsMaterialiserBlockEntity(pos, state);
    }
}
