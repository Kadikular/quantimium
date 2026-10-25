package com.kadikular.quantimium.item;

import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.laser.LensFilter;
import com.kadikular.quantimium.menu.FilterLensMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A filter lens: an Item, Block or Entity Filter Lens, each setting its own part of what the actions it changes may
 * act on ({@link LensFilter}). Use it in the air to set it.
 */
public class FilterLensItem extends LensItem implements LensWithSettings {

    /** {@link LensFilter#ITEMS}, {@link LensFilter#BLOCKS} or {@link LensFilter#ENTITIES}. */
    private final int part;

    public FilterLensItem(Properties properties, int part) {
        super(properties, Lens.FILTER);
        this.part = part;
    }

    public int part() {
        return part;
    }

    public static LensFilter filterOf(ItemStack stack) {
        LensFilter filter = stack.get(ModDataComponents.LENS_FILTER.get());
        return filter == null ? LensFilter.EMPTY : filter;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // An Entity lens, sneaking: bound to yourself.
        if (part == LensFilter.ENTITIES && player.isShiftKeyDown()) {
            if (!level.isClientSide()) bind(player.getItemInHand(hand), player, player);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) com.kadikular.quantimium.menu.LensHolder.openInHand(server, hand);
        return InteractionResult.SUCCESS;
    }

    /** An Entity lens, sneaking, used on something living: bound to that one. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, net.minecraft.world.entity.LivingEntity target, InteractionHand hand) {
        if (part != LensFilter.ENTITIES || !player.isShiftKeyDown()) return InteractionResult.PASS;
        // The stack handed in may be a copy (creative): the one in hand is the lens.
        if (!player.level().isClientSide()) bind(player.getItemInHand(hand), player, target);
        return InteractionResult.SUCCESS;
    }

    /** Adds {@code entity} to the lens's list, and says so. */
    private static void bind(ItemStack lens, Player player, net.minecraft.world.entity.Entity entity) {
        LensFilter filter = filterOf(lens);
        LensFilter bound = filter.withBound(entity);
        if (bound == null) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.filter_lens.bound.full"));
            return;
        }
        if (bound == filter) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.filter_lens.bound.already", entity.getName()));
            return;
        }
        lens.set(ModDataComponents.LENS_FILTER.get(), bound);
        player.sendOverlayMessage(Component.translatable("message.quantimium.filter_lens.bound", entity.getName()));
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu menu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                   com.kadikular.quantimium.menu.LensHolder holder) {
        return new FilterLensMenu(containerId, inventory, holder);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        LensFilter filter = filterOf(stack);
        long listed = filter.entries(part).stream().filter(entry -> !entry.isEmpty()).count();
        if (listed > 0) {
            tooltip.accept(Component.translatable(filter.allows(part) ? "item.quantimium.filter_lens.allows" : "item.quantimium.filter_lens.denies", listed)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (part == LensFilter.ENTITIES) {
            tooltip.accept(kinds(filter.kinds()).withStyle(ChatFormatting.AQUA));
            if (filter.spareYoung()) tooltip.accept(Component.translatable("gui.quantimium.filter_lens.young.on").withStyle(ChatFormatting.AQUA));
            if (filter.keep() > 0) tooltip.accept(Component.translatable("gui.quantimium.filter_lens.keep", filter.keep()).withStyle(ChatFormatting.AQUA));
            return;
        }
        if (part == LensFilter.BLOCKS && filter.ripe()) tooltip.accept(Component.translatable("gui.quantimium.filter_lens.ripe.on").withStyle(ChatFormatting.AQUA));
    }

    /** "Any living thing", or the kinds it lets through: "Hostile, players". */
    public static net.minecraft.network.chat.MutableComponent kinds(int kinds) {
        if (kinds == 0) return Component.translatable("gui.quantimium.filter_lens.kinds.any");
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        String[] keys = {"hostile", "animals", "players"};
        for (int i = 0; i < keys.length; i++) {
            if ((kinds & 1 << i) == 0) continue;
            if (!line.getSiblings().isEmpty()) line.append(", ");
            line.append(Component.translatable("gui.quantimium.filter_lens.kind." + keys[i]));
        }
        return line;
    }
}
