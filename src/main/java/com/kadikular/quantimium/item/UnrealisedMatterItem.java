package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.unrealised.MatterHistory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Unresolved cave loot. The display name glitches until the Observation Chamber collapses it into
 * a real ore (and maybe a Quantimium Trace).
 */
public class UnrealisedMatterItem extends Item {

    public UnrealisedMatterItem(Properties properties) {
        super(properties);
    }

    /**
     * Its real name, with letters scrambled on screen: the obfuscated style changes how a letter looks,
     * not what it is, so JEI and ME terminals still find "Unrealised Matter" by name. Which letters are
     * scrambled crawls with the wall clock, so it glitches in an inventory without a level tick.
     */
    @Override
    public Component getName(ItemStack stack) {
        // A dedicated server has no mod translations: there, a plain translatable name each client reads.
        if (!net.minecraft.locale.Language.getInstance().has(getDescriptionId())) {
            return Component.translatable(getDescriptionId()).withStyle(ChatFormatting.DARK_PURPLE);
        }
        String text = net.minecraft.locale.Language.getInstance().getOrDefault(getDescriptionId());
        long tick = System.currentTimeMillis() / 80L;
        MutableComponent name = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            boolean glitch = letter != ' ' && ((tick + i * 7L) % 5) < 2;
            MutableComponent part = Component.literal(String.valueOf(letter));
            name.append(glitch ? part.withStyle(ChatFormatting.OBFUSCATED) : part);
        }
        return name.withStyle(ChatFormatting.DARK_PURPLE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        MatterHistory history = MatterHistory.of(stack);
        if (!history.isEmpty()) {
            tooltip.accept(Component.translatable("item.quantimium.unrealised_matter.history", history.describe())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.accept(Component.translatable("item.quantimium.unrealised_matter.tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
