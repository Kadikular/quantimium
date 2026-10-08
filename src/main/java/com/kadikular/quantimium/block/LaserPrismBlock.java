package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.LaserPrismBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A laser prism: links to blocks with the Laser Linker, and sends what light reaches it to each. A
 * sneaking empty hand lets go of every link.
 */
public class LaserPrismBlock extends BaseEntityBlock {
    public static final MapCodec<LaserPrismBlock> CODEC = simpleCodec(LaserPrismBlock::new);

    public LaserPrismBlock(Properties properties) {
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

    /** Sneaking with an empty hand lets go of every link. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserPrismBlockEntity prism) {
            int had = prism.clearLinks();
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.quantimium.laser_prism.cleared", had)
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            if (had > 0) level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserPrismBlockEntity(pos, state);
    }
}
