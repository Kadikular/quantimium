package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * A Sophon: a proton unfolded around someone's pattern and folded back up with a body double inside.
 * Made at the Unfolding Array; put into a Superposition Pod, the double unfolds there.
 *
 * <p>It cannot burn, blow up or despawn: a Sophon counts towards its owner's cap wherever it is, so
 * the only ways to lose one are the void and a rescue, and both are noticed.
 */
public class SophonItem extends Item {

    public SophonItem(Properties properties) {
        super(properties);
    }

    /** A Sophon holding {@code id}'s double, for {@code owner}. */
    public static ItemStack of(UUID id, UUID owner, String ownerName) {
        ItemStack stack = new ItemStack(ModItems.SOPHON.get());
        stack.set(ModDataComponents.SOPHON.get(), new SophonBinding(id, owner, ownerName));
        return stack;
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        SophonBinding binding = stack.get(ModDataComponents.SOPHON.get());
        if (binding == null) {
            tooltip.accept(Component.translatable("item.quantimium.sophon.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("item.quantimium.sophon.of", binding.ownerName()).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("item.quantimium.sophon.tip").withStyle(ChatFormatting.GRAY));
    }
}
