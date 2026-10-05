package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A prism of Anomalite shards sealed in a glass casing: it turns ordinary light into light that
 * reaches into the mirror, which is what a Decoherence Projector decoheres with, and cracks as it does.
 * Its charge is how much prism is left; it wears fastest on the Veiled, and is used up whole, so
 * there's nothing to collect afterwards. A new Cell is full; its charge is kept on the stack once used.
 */
public class AnomaliteCellItem extends Item {

    /** Charge in a full Cell. A Projector spends about this much pinning one Veiled. */
    public static final int CAPACITY = 1_000;
    private static final int BAR_COLOUR = 0x8A60F0;

    public AnomaliteCellItem(Properties properties) {
        super(properties);
    }

    public static int charge(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModDataComponents.CELL_CHARGE.get(), CAPACITY), 0, CAPACITY);
    }

    /** {@code stack} with {@code charge} left. */
    public static void setCharge(ItemStack stack, int charge) {
        stack.set(ModDataComponents.CELL_CHARGE.get(), Mth.clamp(charge, 0, CAPACITY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return charge(stack) < CAPACITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * charge(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        tip.accept(Component.translatable("item.quantimium.anomalite_cell.charge",
                Math.round(100.0f * charge(stack) / CAPACITY)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.accept(Component.translatable("item.quantimium.anomalite_cell.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
