package com.kadikular.quantimium.item;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.laser.IsolatorSettings;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.menu.IsolatorLensMenu;
import com.kadikular.quantimium.menu.LensHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** The Isolator Lens: lets power through, and what else it's set to pass. Use it in the air to set it. */
public class IsolatorLensItem extends LensItem implements LensWithSettings {

    public IsolatorLensItem(Properties properties) {
        super(properties, Lens.ISOLATE);
    }

    public static IsolatorSettings settingsOf(ItemStack stack) {
        IsolatorSettings settings = stack.get(ModDataComponents.ISOLATOR.get());
        return settings == null ? IsolatorSettings.POWER_ONLY : settings;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) LensHolder.openInHand(server, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public AbstractContainerMenu menu(int containerId, Inventory inventory, LensHolder holder) {
        return new IsolatorLensMenu(containerId, inventory, holder);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        IsolatorSettings settings = settingsOf(stack);
        if (!settings.power()) tooltip.accept(Component.translatable("item.quantimium.isolator_lens.no_power").withStyle(ChatFormatting.AQUA));
        if (settings.power() && !settings.logistics() && !settings.actions() && !settings.modifiers() && !settings.redstone()) {
            tooltip.accept(Component.translatable("item.quantimium.isolator_lens.power_only").withStyle(ChatFormatting.AQUA));
            return;
        }
        for (String part : new String[] {"logistics", "actions", "modifiers", "redstone"}) {
            boolean on = switch (part) {
                case "logistics" -> settings.logistics();
                case "actions" -> settings.actions();
                case "redstone" -> settings.redstone();
                default -> settings.modifiers();
            };
            if (on) tooltip.accept(Component.translatable("item.quantimium.isolator_lens.passes." + part).withStyle(ChatFormatting.AQUA));
        }
        long listed = settings.lenses().stream().filter(entry -> !entry.isEmpty()).count();
        if (listed > 0) {
            tooltip.accept(Component.translatable(settings.allow() ? "item.quantimium.filter_lens.allows" : "item.quantimium.filter_lens.denies", listed)
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
