package com.kadikular.quantimium.item;

import com.kadikular.quantimium.QuantimiumAdvancements;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.reactor.ReactorLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A Tesseract collapsed in an empty Fold Chamber, with nothing inside to hold the volume open.
 * Seated in a Horizon Core it becomes a Reactor's horizon, and everything the Reactor holds is held
 * in it: taken out, or with the core broken, it carries all of that along.
 *
 * <p>For now it is only a black pearl that should not exist, and is perfectly safe.
 */
public class SingularityItem extends Item {

    public SingularityItem(Properties properties) {
        super(properties);
    }

    /** What it holds: empty for a new one. */
    public static List<ReactorLedger.Entry> held(ItemStack stack) {
        List<ReactorLedger.Entry> held = stack.get(ModDataComponents.HORIZON_LEDGER.get());
        return held == null ? List.of() : held;
    }

    public static long mass(ItemStack stack) {
        long mass = 0;
        for (ReactorLedger.Entry entry : held(stack)) mass += entry.count();
        return mass;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        tip.accept(Component.translatable("item.quantimium.singularity.tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        List<ReactorLedger.Entry> held = held(stack);
        if (!held.isEmpty()) {
            tip.accept(Component.translatable("item.quantimium.singularity.holds",
                    String.format(Locale.ROOT, "%,d", mass(stack)), held.size()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tip.accept(Component.translatable("item.quantimium.singularity.seat").withStyle(ChatFormatting.DARK_PURPLE));
    }

    /** Carrying one that holds something earns "Should you be holding that?". */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (owner instanceof ServerPlayer player && level.getGameTime() % 20 == 0 && !held(stack).isEmpty()) {
            QuantimiumAdvancements.award(player, "should_you_be_holding_that", "held_a_horizon");
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
