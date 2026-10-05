package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.entity.RiftAnchorBlockEntity;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rift Anchor. Plant a Rift Seed on it to open a rift, or slip it under a natural one; surround it
 * with Rift Stabilisers to hold the rift. Used without a seed it opens its readout: the rift's
 * stage, the stabilisers holding it, the rate and progress of residue, and the residue slot.
 */
public class RiftAnchorBlock extends BaseEntityBlock {

    public static final MapCodec<RiftAnchorBlock> CODEC = simpleCodec(RiftAnchorBlock::new);

    public RiftAnchorBlock(Properties properties) {
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModItems.RIFT_SEED.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        if (FluxRiftManager.riftAbove(serverLevel, pos) != null) {
            player.sendOverlayMessage(Component.translatable("block.quantimium.rift_anchor.occupied"));
            return InteractionResult.CONSUME;
        }
        // The wound needs room to stand in.
        if (!level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) {
            player.sendOverlayMessage(Component.translatable("block.quantimium.rift_anchor.blocked"));
            return InteractionResult.CONSUME;
        }
        FluxRift rift = FluxRiftManager.seed(serverLevel, pos);
        if (level.getBlockEntity(pos) instanceof RiftAnchorBlockEntity anchor) anchor.holdNow(serverLevel);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        Vec3 centre = rift.centre();
        serverLevel.playSound(null, centre.x, centre.y, centre.z, ModSounds.RIFT_COLLAPSE.get(), SoundSource.BLOCKS,
                1.0f, 1.4f);
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 60, 0.3, 0.8, 0.3, 0.15);
        return InteractionResult.CONSUME;
    }

    /** Opens the readout, with the residue slot. Seeding is {@link #useItemOn}, with a seed in hand. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof RiftAnchorBlockEntity anchor) {
            player.openMenu(anchor, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RiftAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.RIFT_ANCHOR_BE.get(), RiftAnchorBlockEntity::serverTick);
    }
}
