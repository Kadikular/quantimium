package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.EntangledMirrorBlockEntity;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An Entangled Mirror (see {@link EntangledMirrorBlockEntity}): placed facing you. Bind a Tesseract to another one
 * (sneak and use it on that one), then use it on this one to put it in: light into this one's face leaves by that
 * one's. Sneak with an empty hand to take the Tesseract back out.
 */
public class EntangledMirrorBlock extends BaseEntityBlock implements com.kadikular.quantimium.item.BlockTooltip {

    public static final MapCodec<EntangledMirrorBlock> CODEC = simpleCodec(EntangledMirrorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public EntangledMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Sneaking, a Tesseract binds to this mirror, as it would to any block.
        if (player.isSecondaryUseActive() || !EntangledMirrorBlockEntity.fits(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EntangledMirrorBlockEntity mirror) {
            var bound = stack.get(com.kadikular.quantimium.init.ModDataComponents.BOUND_POS.get());
            if (bound != null && bound.pos().equals(pos) && bound.dimension().equals(level.dimension())) {
                player.sendOverlayMessage(Component.translatable("message.quantimium.entangled_mirror.itself"));
                return InteractionResult.CONSUME;
            }
            ItemStack out = mirror.insert(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!out.isEmpty() && !player.addItem(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
            player.sendOverlayMessage(bound != null && bound.dimension().equals(level.dimension())
                    ? Component.translatable("message.quantimium.entangled_mirror.linked", bound.pos().getX(), bound.pos().getY(), bound.pos().getZ())
                    : Component.translatable("message.quantimium.entangled_mirror.elsewhere"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EntangledMirrorBlockEntity mirror && !mirror.tesseract().isEmpty()) {
            ItemStack out = mirror.remove();
            if (!player.addItem(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                   @Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            LaserNetworks.changed(level);
        }
    }

    @Override
    public void appendTooltip(java.util.function.Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("block.quantimium.entangled_mirror.tip", Math.round(com.kadikular.quantimium.laser.LaserTuning.JUMP_TAX * 100))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntangledMirrorBlockEntity(pos, state);
    }
}
