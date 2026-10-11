package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.AreaPrismBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** An Area Prism. Give it an area with the Laser Linker in area mode; sneak-click it with an empty hand to clear it. */
public class AreaPrismBlock extends BaseEntityBlock {
    public static final MapCodec<AreaPrismBlock> CODEC = simpleCodec(AreaPrismBlock::new);

    public AreaPrismBlock(Properties properties) {
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AreaPrismBlockEntity prism) player.openMenu(prism, pos);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AreaPrismBlockEntity prism) {
            prism.clearArea();
            player.sendOverlayMessage(Component.translatable("message.quantimium.area_prism.cleared").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AreaPrismBlockEntity(pos, state);
    }
}
