package com.kadikular.quantimium.item;

import com.kadikular.quantimium.block.entity.LaserPrismBlockEntity;
import com.kadikular.quantimium.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Links a laser prism to the blocks its light should reach. Use it on a prism to pick that prism,
 * then on each block: once to link it, again to unlink it. Sneak-use in the air to let the prism go.
 */
public class LaserLinkerItem extends Item {

    public LaserLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof LaserPrismBlockEntity && (player == null || !player.isSecondaryUseActive() || picked(stack, level) == null)) {
            stack.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(level.dimension(), pos.immutable()));
            say(player, Component.translatable("message.quantimium.laser_linker.picked", pos.getX(), pos.getY(), pos.getZ()));
            return InteractionResult.SUCCESS;
        }
        GlobalPos prismAt = picked(stack, level);
        if (prismAt == null || !(level.getBlockEntity(prismAt.pos()) instanceof LaserPrismBlockEntity prism)) {
            say(player, Component.translatable("message.quantimium.laser_linker.none"));
            return InteractionResult.FAIL;
        }
        LaserPrismBlockEntity.Link link = prism.toggleLink(pos);
        say(player, Component.translatable("message.quantimium.laser_linker." + link.name().toLowerCase(Locale.ROOT),
                pos.getX(), pos.getY(), pos.getZ()));
        if (link == LaserPrismBlockEntity.Link.ADDED || link == LaserPrismBlockEntity.Link.REMOVED) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f,
                    link == LaserPrismBlockEntity.Link.ADDED ? 1.4f : 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ModDataComponents.BOUND_POS.get())) {
            if (!level.isClientSide()) {
                stack.remove(ModDataComponents.BOUND_POS.get());
                say(player, Component.translatable("message.quantimium.laser_linker.cleared"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** The prism picked, if it's in this dimension. */
    private static GlobalPos picked(ItemStack stack, Level level) {
        GlobalPos at = stack.get(ModDataComponents.BOUND_POS.get());
        return at != null && at.dimension().equals(level.dimension()) ? at : null;
    }

    private static void say(Player player, Component message) {
        if (player != null) player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tip,
                                TooltipFlag flag) {
        GlobalPos at = stack.get(ModDataComponents.BOUND_POS.get());
        tip.accept(at == null ? Component.translatable("item.quantimium.laser_linker.empty").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.quantimium.laser_linker.picked", at.pos().getX(), at.pos().getY(), at.pos().getZ())
                        .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.BOUND_POS.get());
    }
}
