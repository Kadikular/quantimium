package com.kadikular.quantimium.item;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.laser.LensFilter;
import com.kadikular.quantimium.menu.FilterLensMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A Filter lens: use it in the air to set what the actions it changes may act on. */
public class FilterLensItem extends LensItem {

    public FilterLensItem(Properties properties) {
        super(properties, Lens.FILTER);
    }

    public static LensFilter filterOf(ItemStack stack) {
        LensFilter filter = stack.get(ModDataComponents.LENS_FILTER.get());
        return filter == null ? LensFilter.EMPTY : filter;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider((id, inventory, who) -> new FilterLensMenu(id, inventory, hand),
                    Component.translatable("item.quantimium.filter_lens")), buf -> buf.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        LensFilter filter = filterOf(stack);
        long entries = filter.entries().stream().filter(entry -> !entry.isEmpty()).count();
        if (entries > 0) {
            tooltip.accept(Component.translatable(filter.allow() ? "item.quantimium.filter_lens.allows" : "item.quantimium.filter_lens.denies", entries)
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.accept(Component.translatable("item.quantimium.filter_lens.living." + filter.living().getSerializedName()).withStyle(ChatFormatting.AQUA));
        if (filter.grown()) tooltip.accept(Component.translatable("gui.quantimium.filter_lens.grown.on").withStyle(ChatFormatting.AQUA));
    }
}
