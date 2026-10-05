package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A wound waiting for somewhere to open. On a Rift Anchor it opens a held rift (see RiftAnchorBlock);
 * on the ground, a wild stage 1 rift, but only where there's anomaly for it to feed on: an
 * uncontained chunk at Medium or more. Anywhere else it fizzles and is kept. Planted rifts count
 * towards the same limits as the ones tears leave. It also lights a Stabilised Portal.
 */
public final class RiftSeedItem extends Item {

    public RiftSeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        BlockPos anchor = context.getClickedPos().above();
        String refusal = refusal(level, anchor);
        if (refusal != null) {
            if (player != null) player.sendOverlayMessage(Component.translatable(refusal));
            level.playSound(null, anchor, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
            return InteractionResult.FAIL;
        }
        FluxRift rift = FluxRiftManager.spawn(level, anchor, 1);
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        Vec3 centre = rift.centre();
        level.playSound(null, centre.x, centre.y, centre.z, ModSounds.RIFT_COLLAPSE.get(), SoundSource.BLOCKS, 1.0f, 1.4f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 60, 0.3, 0.8, 0.3, 0.15);
        return InteractionResult.CONSUME;
    }

    /** Why a seed won't take at {@code anchor}, as a lang key, or null if it will. */
    public static String refusal(ServerLevel level, BlockPos anchor) {
        if (!Config.fluxRiftsEnabled()) return "item.quantimium.rift_seed.disabled";
        if (!level.getBlockState(anchor).isAir() || !level.getBlockState(anchor.above()).isAir()) {
            return "item.quantimium.rift_seed.no_room";
        }
        if (QuantumFlux.chunkAnomalyBand(level, anchor).ordinal() < FluxBand.MEDIUM.ordinal()
                || QuantumFlux.chunkContained(level, anchor)) {
            return "item.quantimium.rift_seed.fizzles";
        }
        return switch (FluxRiftManager.room(level, anchor)) {
            case OPEN -> null;
            case TOO_CLOSE -> "item.quantimium.rift_seed.too_close";
            case CROWDED -> "item.quantimium.rift_seed.crowded";
            case FULL -> "item.quantimium.rift_seed.full";
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        tip.accept(Component.translatable("item.quantimium.rift_seed.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
