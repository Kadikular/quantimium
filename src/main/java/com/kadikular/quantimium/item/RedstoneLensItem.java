package com.kadikular.quantimium.item;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.laser.RedstoneSettings;
import com.kadikular.quantimium.menu.LensHolder;
import com.kadikular.quantimium.menu.RedstoneLensMenu;
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

/** The Redstone Lens: gives what the beam ends on a signal, as it's set. Use it in the air to set it. */
public class RedstoneLensItem extends LensItem implements LensWithSettings {

    public RedstoneLensItem(Properties properties) {
        super(properties, Lens.REDSTONE);
    }

    public static RedstoneSettings settingsOf(ItemStack stack) {
        RedstoneSettings settings = stack.get(ModDataComponents.REDSTONE.get());
        return settings == null ? RedstoneSettings.DEFAULT : settings;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) LensHolder.openInHand(server, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public AbstractContainerMenu menu(int containerId, Inventory inventory, LensHolder holder) {
        return new RedstoneLensMenu(containerId, inventory, holder);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(describe(settingsOf(stack)).withStyle(ChatFormatting.AQUA));
    }

    /** What it's set to, in a line: "Signal 15 while fewer than 64 Cobblestone". */
    public static net.minecraft.network.chat.MutableComponent describe(RedstoneSettings settings) {
        String key = "item.quantimium.redstone_lens.mode." + settings.mode().getSerializedName();
        if (settings.mode() == RedstoneSettings.Mode.FORWARD) return Component.translatable(key);
        if (!settings.mode().counts()) return Component.translatable(key, settings.level());
        Component item = settings.item().isEmpty() ? Component.translatable("item.quantimium.redstone_lens.nothing") : settings.item().getHoverName();
        return Component.translatable(key, settings.level(), settings.count(), item);
    }
}
