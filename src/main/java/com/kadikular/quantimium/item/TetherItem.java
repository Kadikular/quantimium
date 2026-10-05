package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.InteractionResult;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Tether: a pod's screen in your hand. Use it anywhere to swap into one of your doubles, paid for from
 * its own buffer rather than a pod's. What you leave behind is the catch: your body stands where you were,
 * a field double, with nothing round it.
 */
public class TetherItem extends Item {

    public static final int CAPACITY = 1_000_000;
    public static final int MAX_RECEIVE = 10_000;
    private static final int BAR_COLOUR = 0x3485FF;

    public TetherItem(Properties properties) {
        super(properties);
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ModDataComponents.ENERGY.get(), Mth.clamp(energy, 0, CAPACITY));
    }

    /** The Tether in {@code player}'s hands, main hand first, or an empty stack. */
    public static ItemStack held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof TetherItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) Superposition.openTether(server, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * energy(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        tip.accept(Component.translatable("item.quantimium.tether.energy",
                String.format("%,d", energy(stack)), String.format("%,d", CAPACITY)).withStyle(ChatFormatting.GRAY));
        tip.accept(Component.translatable("item.quantimium.tether.tip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
