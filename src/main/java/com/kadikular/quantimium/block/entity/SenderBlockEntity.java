package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A Sender: light passes through it, out of its face, as through a mirror, and what's in its slots goes
 * down the beam: to a Placement lens further along, to place, or to a Collector down the beam. Its faces
 * take items in from pipes and hoppers, or pull them from what's beside them. A bound Tesseract in a slot
 * sends from the inventory it links to instead.
 */
public class SenderBlockEntity extends ItemRelayBlockEntity {

    public SenderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SENDER_BE.get(), pos, state);
    }

    @Override
    protected SideMode defaultMode() {
        return SideMode.INPUT;
    }

    @Override
    protected boolean takesFromOutside() {
        return true;
    }

    @Override
    public boolean sends() {
        return true;
    }

    /** Up to {@code max} of the first thing {@code wants} accepts: from its slots, or through a bound Tesseract in one. */
    @Override
    public ItemStack send(Predicate<ItemStack> wants, int max, boolean simulate) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (EntangledLinks.isLink(stack)) {
                IItemHandler linked = EntangledLinks.resolve(level, worldPosition, stack);
                if (linked == null) continue;
                for (int i = 0; i < linked.getSlots(); i++) {
                    ItemStack there = linked.getStackInSlot(i);
                    if (!there.isEmpty() && wants.test(there)) return linked.extractItem(i, max, simulate);
                }
                continue;
            }
            if (wants.test(stack)) return items.extractItem(slot, max, simulate);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack takeBack(ItemStack stack) {
        return insert(stack, false);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.sender");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.SenderMenu(containerId, inventory, this);
    }

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        int stacks = 0;
        for (int slot = 0; slot < SLOTS; slot++) if (!items.getStackInSlot(slot).isEmpty()) stacks++;
        return Component.translatable("message.quantimium.sender.holds", stacks, SLOTS).withStyle(ChatFormatting.DARK_AQUA);
    }
}
