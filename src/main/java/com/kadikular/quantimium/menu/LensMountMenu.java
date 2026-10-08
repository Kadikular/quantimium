package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Lens Mount: its four lenses in a row, in the order light meets them. Layout: tools/gui_panels.py lens_mount. */
public class LensMountMenu extends AbstractContainerMenu {

    public static final int LENS_X = 53;
    public static final int LENS_Y = 35;

    private final LensMountBlockEntity mount;

    public LensMountMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LensMountBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LensMountMenu(int containerId, Inventory playerInv, LensMountBlockEntity mount) {
        super(ModMenuTypes.LENS_MOUNT_MENU.get(), containerId);
        this.mount = mount;
        for (int slot = 0; slot < LensMountBlockEntity.SLOTS; slot++) {
            addSlot(new SlotItemHandler(mount.getLensSlots(), slot, LENS_X + slot * 18, LENS_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public LensMountBlockEntity getBlockEntity() {
        return mount;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        int lenses = LensMountBlockEntity.SLOTS;
        if (index < lenses) {
            if (!moveItemStackTo(stack, lenses, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, lenses, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(mount.getLevel(), mount.getBlockPos()), player, mount.getBlockState().getBlock());
    }
}
