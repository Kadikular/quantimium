package com.kadikular.quantimium.item;

import com.kadikular.quantimium.laser.LaserNetworks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        List<Component> lines = LaserNetworks.of(level).describe(context.getClickedPos());
        if (lines.isEmpty()) return InteractionResult.PASS;
        if (player != null) {
            for (Component line : lines) player.sendSystemMessage(line);
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.quantimium.spectroscope.tip").withStyle(ChatFormatting.GRAY));
    }
}
