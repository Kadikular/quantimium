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

    /** A click steps what it offers: real and craftable, real only, craftable only. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                  net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LogisticsMaterialiserBlockEntity materialiser) {
            materialiser.cycleMode();
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.logistics_materialiser.mode."
                    + materialiser.mode().name().toLowerCase(java.util.Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsMaterialiserBlockEntity(pos, state);
    }
}
