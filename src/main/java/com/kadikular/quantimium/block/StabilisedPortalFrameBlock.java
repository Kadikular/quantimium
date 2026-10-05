package com.kadikular.quantimium.block;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.LevelReader;
import com.kadikular.quantimium.init.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Ancient-city-styled frame. Light the 2×3 interior with a Rift Seed.
 *
 * <p>Connects to every neighbouring frame block, like chisel's connected textures: each face picks
 * its art from which of its four in-plane neighbours are frame, so a frame reads as one polished
 * deepslate piece with a vein running round it, turning at the corners. The six flags are pure
 * appearance; the portal itself only checks the block.
 */
public final class StabilisedPortalFrameBlock extends Block {

    public static final MapCodec<StabilisedPortalFrameBlock> CODEC = simpleCodec(StabilisedPortalFrameBlock::new);

    public StabilisedPortalFrameBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighbour.is(this));
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                    level.getBlockState(pos.relative(direction)).is(this));
        }
        return state;
    }

    /** Only a Rift Seed lights it: a wound opened on purpose, held in the frame (a Mid-tier build). */
    public static boolean isLighter(ItemStack stack) {
        return stack.is(ModItems.RIFT_SEED.get());
    }

    public static SoundEvent lightSound(ItemStack stack) {
        return SoundEvents.END_PORTAL_FRAME_FILL;
    }

    public static void consumeLighter(ItemStack stack, Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild) return;
        if (stack.is(Items.FLINT_AND_STEEL)) {
            stack.hurtAndBreak(1, player, (hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND));
            return;
        }
        stack.shrink(1);
    }
}
