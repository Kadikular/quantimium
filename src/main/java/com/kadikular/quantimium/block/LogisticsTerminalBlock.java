package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Logistics Terminal: light from a Logistics Controller ends on it, and it opens onto everything that network can
 * give. Placed, its screen faces you.
 */
public class LogisticsTerminalBlock extends HorizontalMachineBlock implements com.kadikular.quantimium.item.BlockTooltip {
    public static final MapCodec<LogisticsTerminalBlock> CODEC = simpleCodec(LogisticsTerminalBlock::new);

    public LogisticsTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("block.quantimium.logistics_terminal.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsTerminalBlockEntity(pos, state);
    }
}
