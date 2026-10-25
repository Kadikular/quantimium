package com.kadikular.quantimium.item;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.menu.LensHolder;
import com.kadikular.quantimium.menu.LensSettingsMenu;
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

/**
 * The Void Lens: destroys what the actions before it free. Set to void excess (the default), only what no Laser Port
 * or network up the beam has room for; set to void all, everything. Use it in the air to set it.
 */
public class VoidLensItem extends LensItem implements LensWithSettings {

    public VoidLensItem(Properties properties) {
        super(properties, Lens.VOID);
    }

    public static boolean voidsAll(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.VOID_ALL.get()));
    }

    /** Switches between void all and void excess; whether it now voids all. */
    public static boolean toggle(ItemStack stack) {
        boolean all = !voidsAll(stack);
        stack.set(ModDataComponents.VOID_ALL.get(), all);
        return all;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) LensHolder.openInHand(server, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public AbstractContainerMenu menu(int containerId, Inventory inventory, LensHolder holder) {
        return new com.kadikular.quantimium.menu.FilterLensMenu(containerId, inventory, holder);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(voidsAll(stack) ? "item.quantimium.void_lens.all" : "item.quantimium.void_lens.excess")
                .withStyle(ChatFormatting.AQUA));
        var filter = stack.get(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get());
        long listed = filter == null ? 0 : filter.entries(com.kadikular.quantimium.laser.LensFilter.ITEMS).stream().filter(entry -> !entry.isEmpty()).count();
        if (listed > 0) {
            tooltip.accept(Component.translatable(filter.allows(com.kadikular.quantimium.laser.LensFilter.ITEMS) ? "item.quantimium.filter_lens.allows"
                    : "item.quantimium.filter_lens.denies", listed).withStyle(ChatFormatting.AQUA));
        }
    }
}
