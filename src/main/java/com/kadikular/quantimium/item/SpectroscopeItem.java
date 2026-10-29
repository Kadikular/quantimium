package com.kadikular.quantimium.item;

import com.kadikular.quantimium.laser.LaserNetworks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Spectroscope: splits the light leaving a laser block into its lines, as a real spectroscope's
 * prism splits light into its colours. Use it on an emitter, mirror, mount, prism or anything else
 * light passes through to read what its light carries: how far a run reaches, each action and its
 * modifiers, who pays, where Pickup sends things, and what each beam carries.
 */
public class SpectroscopeItem extends Item {

    public SpectroscopeItem(Properties properties) {
        super(properties);
    }

    /**
     * On a block, before the block's own use: one of ours, the laser leaving it; else a laser in front of it, if the look
     * meets one (the block's own use otherwise).
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // A laser block, or a laser it draws in front of the block: the reading, not the block's own use.
            if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof com.kadikular.quantimium.laser.LaserBeamView) return InteractionResult.SUCCESS;
            Player looker = context.getPlayer();
            return looker != null && com.kadikular.quantimium.client.ClientBeams.aimedAt(context.getLevel(), looker, REACH)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        // As the client decides: a block that draws lasers is read; any other, only a laser in front of it.
        if (level.getBlockEntity(context.getClickedPos()) instanceof com.kadikular.quantimium.laser.LaserBeamView
                && show(level, context.getPlayer(), context.getClickedPos(), -1)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return aim(level, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** In the air: the laser it's aimed at, within reach. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        return aim(server, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Reads the lasers the player's look meets first, if any; whether it did. */
    private static boolean aim(ServerLevel level, Player player) {
        var networks = LaserNetworks.of(level);
        var aims = networks.aimedAt(level, player, player.getEyePosition(), player.getLookAngle(), REACH);
        var reading = networks.read(level, aims);
        if (reading == null) return false;
        send(level, player, aims.getFirst().pos(), reading);
        return true;
    }

    /** How far off a laser in the air can be read. */
    private static final double REACH = 8.0;

    /** Reads the laser leaving {@code pos} (or its beam {@code beam}) and opens the reading on the player's screen. */
    private static boolean show(ServerLevel level, @org.jspecify.annotations.Nullable Player player, net.minecraft.core.BlockPos pos, int beam) {
        var reading = LaserNetworks.of(level).read(level, pos, beam);
        if (reading == null) return false;
        send(level, player, pos, reading);
        return true;
    }

    private static void send(ServerLevel level, @org.jspecify.annotations.Nullable Player player, net.minecraft.core.BlockPos pos,
                             com.kadikular.quantimium.laser.SpectroscopeReading reading) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server, new com.kadikular.quantimium.network.SpectroscopePayload(reading));
        }
        level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.quantimium.spectroscope.tip").withStyle(ChatFormatting.GRAY));
    }
}
