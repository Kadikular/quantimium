package com.kadikular.quantimium.block;

import com.kadikular.quantimium.item.BlockTooltip;
import java.util.function.Consumer;
import com.kadikular.quantimium.block.entity.DebugFieldEmitterBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Creative testing only: flux as if a machine here were spending a fixed FE/t, with no power needed.
 * Use steps it up through {@link #FE_PER_TICK}, sneak-use steps it down; 0 is off. For watching the
 * field settle (Field Model 2.0) against a known source. No recipe.
 */
public class DebugFieldEmitterBlock extends BaseEntityBlock implements BlockTooltip {

    public static final MapCodec<DebugFieldEmitterBlock> CODEC = simpleCodec(DebugFieldEmitterBlock::new);
    /** FE/t worth of work at each setting; setting 0 is off. */
    public static final long[] FE_PER_TICK = {0L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 100_000_000L};
    public static final IntegerProperty SETTING = IntegerProperty.create("setting", 0, FE_PER_TICK.length - 1);

    public DebugFieldEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SETTING, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SETTING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int settings = FE_PER_TICK.length;
        int next = (state.getValue(SETTING) + (player.isSecondaryUseActive() ? settings - 1 : 1)) % settings;
        level.setBlock(pos, state.setValue(SETTING, next), Block.UPDATE_ALL);
        player.sendOverlayMessage(describe(next));
        return InteractionResult.CONSUME;
    }

    /** What setting {@code setting} does, for the action bar. */
    public static Component describe(int setting) {
        long fe = FE_PER_TICK[setting];
        if (fe == 0L) return Component.translatable("block.quantimium.debug_field_emitter.off");
        return Component.translatable("block.quantimium.debug_field_emitter.setting",
                String.format("%,d", fe), String.format("%,.0f", DebugFieldEmitterBlockEntity.fluxPerSecond(fe)));
    }

    @Override
    public void appendTooltip(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("block.quantimium.debug_field_emitter.tip").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DebugFieldEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.DEBUG_FIELD_EMITTER_BE.get(), DebugFieldEmitterBlockEntity::tick);
    }
}
