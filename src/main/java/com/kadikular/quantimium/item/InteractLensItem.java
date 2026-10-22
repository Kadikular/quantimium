package com.kadikular.quantimium.item;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.laser.Lens;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * An Interact lens: set to use (a right click) or to hit, and to work on blocks, living things or both. Use it in the
 * air (or its button in a mount) to switch use and hit; sneak and use it to switch what it works on.
 */
public class InteractLensItem extends LensItem {

    public InteractLensItem(Properties properties) {
        super(properties, Lens.INTERACT);
    }

    public static boolean hits(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.INTERACT_HITS.get()));
    }

    /** Switches between use and hit; whether it now hits. */
    public static boolean toggle(ItemStack stack) {
        boolean hits = !hits(stack);
        stack.set(ModDataComponents.INTERACT_HITS.get(), hits);
        return hits;
    }

    /** What it works on: {@link com.kadikular.quantimium.laser.LensUse#BOTH}, BLOCKS or ENTITIES. */
    public static int targets(ItemStack stack) {
        Integer targets = stack.get(ModDataComponents.INTERACT_TARGETS.get());
        return targets == null ? com.kadikular.quantimium.laser.LensUse.BOTH : targets;
    }

    /** Steps to what it works on next; what that is. */
    public static int cycleTargets(ItemStack stack) {
        int next = (targets(stack) + 1) % 3;
        stack.set(ModDataComponents.INTERACT_TARGETS.get(), next);
        return next;
    }

    private static Component targetsLine(ItemStack stack) {
        return Component.translatable("item.quantimium.interact_lens.targets." + switch (targets(stack)) {
            case com.kadikular.quantimium.laser.LensUse.BLOCKS -> "blocks";
            case com.kadikular.quantimium.laser.LensUse.ENTITIES -> "entities";
            default -> "both";
        }).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isShiftKeyDown()) {
                cycleTargets(stack);
                player.sendOverlayMessage(targetsLine(stack));
            } else {
                boolean hits = toggle(stack);
                player.sendOverlayMessage(Component.translatable(hits ? "item.quantimium.interact_lens.hits" : "item.quantimium.interact_lens.uses")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(hits(stack) ? "item.quantimium.interact_lens.hits" : "item.quantimium.interact_lens.uses")
                .withStyle(ChatFormatting.AQUA));
        tooltip.accept(targetsLine(stack));
    }
}
