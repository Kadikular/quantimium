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

    /** On a laser block: the laser leaving it. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        return show(level, context.getPlayer(), context.getClickedPos(), -1) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** In the air: the laser it's aimed at, within reach. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        var eye = player.getEyePosition();
        var aim = LaserNetworks.of(server).aimedAt(eye, eye.add(player.getLookAngle().scale(REACH)));
        if (aim == null) return InteractionResult.PASS;
        return show(server, player, aim.pos(), aim.beam()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** How far off a laser in the air can be read. */
    private static final double REACH = 16.0;

    /** Reads the laser leaving {@code pos} (or its beam {@code beam}) and opens the reading on the player's screen. */
    private static boolean show(ServerLevel level, @org.jspecify.annotations.Nullable Player player, net.minecraft.core.BlockPos pos, int beam) {
        var reading = LaserNetworks.of(level).read(level, pos, beam);
        if (reading == null) return false;
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server, new com.kadikular.quantimium.network.SpectroscopePayload(reading));
        }
        level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.quantimium.spectroscope.tip").withStyle(ChatFormatting.GRAY));
    }
}
