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
 * Use it in the air to switch to area mode: pick two corners, then use it on an Area Prism.
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
        com.kadikular.quantimium.laser.LinkerArea area = stack.getOrDefault(ModDataComponents.LINKER_AREA.get(), com.kadikular.quantimium.laser.LinkerArea.OFF);
        if (area.on()) return useArea(level, player, stack, pos, area);
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
        LaserPrismBlockEntity.Link link = prism.toggleLink(pos, context.getClickedFace());
        say(player, Component.translatable("message.quantimium.laser_linker." + link.name().toLowerCase(Locale.ROOT),
                pos.getX(), pos.getY(), pos.getZ()));
        if (link == LaserPrismBlockEntity.Link.ADDED || link == LaserPrismBlockEntity.Link.REMOVED) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f,
                    link == LaserPrismBlockEntity.Link.ADDED ? 1.4f : 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    /** In area mode: pick corners, then use it on an area prism to give it that area. */
    private static InteractionResult useArea(Level level, Player player, ItemStack stack, BlockPos pos, com.kadikular.quantimium.laser.LinkerArea area) {
        if (level.getBlockEntity(pos) instanceof com.kadikular.quantimium.block.entity.AreaPrismBlockEntity prism
                && area.first().isPresent() && area.second().isPresent()) {
            var placed = prism.setArea(area.first().get(), area.second().get());
            say(player, Component.translatable("message.quantimium.laser_linker.area." + placed.name().toLowerCase(Locale.ROOT),
                    com.kadikular.quantimium.laser.LaserTuning.areaMax()));
            if (placed == com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.Placed.SET) {
                stack.set(ModDataComponents.LINKER_AREA.get(), new com.kadikular.quantimium.laser.LinkerArea(true, java.util.Optional.empty(), java.util.Optional.empty()));
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.6f);
            }
            return InteractionResult.SUCCESS;
        }
        com.kadikular.quantimium.laser.LinkerArea next = area.pick(pos);
        stack.set(ModDataComponents.LINKER_AREA.get(), next);
        if (next.second().isPresent()) {
            var box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(next.first().get(), next.second().get());
            say(player, Component.translatable("message.quantimium.laser_linker.area.second", box.getXSpan(), box.getYSpan(), box.getZSpan()));
        } else {
            say(player, Component.translatable("message.quantimium.laser_linker.area.first", pos.getX(), pos.getY(), pos.getZ()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // In the air, without sneaking: switch between linking prisms and picking an area.
        if (!player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                var area = stack.getOrDefault(ModDataComponents.LINKER_AREA.get(), com.kadikular.quantimium.laser.LinkerArea.OFF);
                stack.set(ModDataComponents.LINKER_AREA.get(), area.on() ? com.kadikular.quantimium.laser.LinkerArea.OFF
                        : new com.kadikular.quantimium.laser.LinkerArea(true, java.util.Optional.empty(), java.util.Optional.empty()));
                say(player, Component.translatable(area.on() ? "message.quantimium.laser_linker.mode.link" : "message.quantimium.laser_linker.mode.area"));
            }
            return InteractionResult.SUCCESS;
        }
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
        if (stack.getOrDefault(ModDataComponents.LINKER_AREA.get(), com.kadikular.quantimium.laser.LinkerArea.OFF).on()) {
            tip.accept(Component.translatable("item.quantimium.laser_linker.area_mode").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
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
