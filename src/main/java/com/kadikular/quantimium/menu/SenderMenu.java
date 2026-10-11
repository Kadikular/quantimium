package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.SenderBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Sender: what it sends, in a dispenser's 3x3 grid, and the player's inventory. Layout: tools/gui_panels.py collector. */
public class SenderMenu extends AbstractContainerMenu {

    private final SenderBlockEntity sender;

    public SenderMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (SenderBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public SenderMenu(int containerId, Inventory playerInv, SenderBlockEntity sender) {
        super(ModMenuTypes.SENDER_MENU.get(), containerId);
        this.sender = sender;
        for (int i = 0; i < SenderBlockEntity.SLOTS; i++) {
            addSlot(new SlotItemHandler(sender.getItems(), i, CollectorMenu.GRID_X + (i % 3) * 18, CollectorMenu.GRID_Y + (i / 3) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
    }

    public static final int BUTTON_RELAY = 0;

    public SenderBlockEntity getBlockEntity() {
        return sender;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_RELAY) return false;
        sender.cycleMode();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = SenderBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, own, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(sender.getLevel(), sender.getBlockPos()), player, sender.getBlockState().getBlock());
    }
}
