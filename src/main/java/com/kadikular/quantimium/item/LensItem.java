package com.kadikular.quantimium.item;

import com.kadikular.quantimium.laser.Lens;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Consumer;

/** A lens, for a Lens Mount: an action or a modifier for the beams leaving it. */
public class LensItem extends Item {

    private final Lens lens;

    public LensItem(Properties properties, Lens lens) {
        super(properties);
        this.lens = lens;
    }

    public Lens lens() {
        return lens;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(lens.action ? "item.quantimium.lens.action" : "item.quantimium.lens.modifier")
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable(BuiltInRegistries.ITEM.getKey(stack.getItem()).toLanguageKey("item") + ".tip")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Sneaking with a lens on a Relay or Laser Focus, the block decides: it takes the lens out, rather than the lens being used. */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos,
                                      net.minecraft.world.entity.player.Player player) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof com.kadikular.quantimium.block.LaserRelayBlock || block instanceof com.kadikular.quantimium.block.LaserFocusBlock;
    }
}
