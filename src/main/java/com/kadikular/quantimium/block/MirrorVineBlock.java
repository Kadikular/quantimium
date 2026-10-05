package com.kadikular.quantimium.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Model holder for render-only mirror vines. The world never places this block; older worlds
 * migrate leftover states into {@link com.kadikular.quantimium.flux.MirrorFloraData}.
 */
public final class MirrorVineBlock extends MultifaceBlock {

    public static final MapCodec<MirrorVineBlock> CODEC = simpleCodec(MirrorVineBlock::new);

    public MirrorVineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean isFaceSupported(Direction face) {
        return face.getAxis().isHorizontal();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return null;
    }
}
