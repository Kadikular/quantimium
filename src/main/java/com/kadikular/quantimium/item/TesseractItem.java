package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Binds to a block inventory. In the Quantum Crafter, a bound link exposes that inventory as virtual
 * ingredients without leaving the input slot.
 */
public class TesseractItem extends Item {
    private final boolean semiStable;

    public TesseractItem(Properties properties, boolean semiStable) {
        super(properties);
        this.semiStable = semiStable;
    }

    public boolean isSemiStable() {
        return semiStable;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();

        if (!level.isClientSide()) {
            stack.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            stack.set(ModDataComponents.BOUND_SIDE.get(), side);
            // Remember what we bound to so the tesseract can render it without the target loaded.
            stack.set(ModDataComponents.BOUND_BLOCK.get(),
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
            player.sendOverlayMessage(Component.translatable(messageKey(stack, "bound"),
                    pos.getX(), pos.getY(), pos.getZ()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!EntangledLinks.isBound(stack)) {
            return InteractionResult.PASS;
        }
        // use() also runs after useOn PASS on a block — only clear when the click missed everything.
        if (player.pick(5.0, 0.0f, false).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.remove(ModDataComponents.BOUND_POS.get());
            stack.remove(ModDataComponents.BOUND_SIDE.get());
            stack.remove(ModDataComponents.BOUND_BLOCK.get());
            player.sendOverlayMessage(Component.translatable(messageKey(stack, "cleared")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        GlobalPos bound = stack.get(ModDataComponents.BOUND_POS.get());
        if (bound == null) {
            tip.accept(Component.translatable(messageKey(stack, "tooltip.unbound"))
                    .withStyle(ChatFormatting.GRAY));
            tip.accept(Component.translatable(messageKey(stack, "tooltip.bind_hint"))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tip.accept(Component.translatable(messageKey(stack, "tooltip.range"))
                    .withStyle(ChatFormatting.BLUE));
            return;
        }
        Direction side = stack.get(ModDataComponents.BOUND_SIDE.get());
        tip.accept(Component.translatable(messageKey(stack, "tooltip.bound"),
                        bound.pos().getX(), bound.pos().getY(), bound.pos().getZ(),
                        bound.dimension().identifier().toString())
                .withStyle(ChatFormatting.AQUA));
        if (side != null) {
            tip.accept(Component.translatable(messageKey(stack, "tooltip.side"), side.getSerializedName())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tip.accept(Component.translatable(messageKey(stack, "tooltip.range"))
                .withStyle(ChatFormatting.BLUE));
        tip.accept(Component.translatable(messageKey(stack, "tooltip.clear_hint"))
                .withStyle(ChatFormatting.DARK_GRAY));
        tip.accept(Component.translatable(messageKey(stack, "tooltip.crafter"))
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static String messageKey(ItemStack stack, String suffix) {
        return stack.getItem().getDescriptionId() + "." + suffix;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return EntangledLinks.isBound(stack) || super.isFoil(stack);
    }
}
