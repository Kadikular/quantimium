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

    /** Each lens's slot, spaced along the line of light; its settings button under it. */
    public static final int[] SLOT_X = {32, 64, 96, 128};
    public static final int LENS_Y = 34;
    /** Each lens's channel, above it. */
    public static final int CHANNEL_Y = 21;
    /** Menu buttons cycling slot i's channel: this + 2i, or + 2i + 1 back. */
    public static final int BUTTON_CHANNEL = 100;
    public static final int BUTTON_Y = 55;
    public static final int BUTTON_W = 18;
    public static final int BUTTON_H = 10;

    private final LensMountBlockEntity mount;

    public LensMountMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LensMountBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LensMountMenu(int containerId, Inventory playerInv, LensMountBlockEntity mount) {
        super(ModMenuTypes.LENS_MOUNT_MENU.get(), containerId);
        this.mount = mount;
        for (int slot = 0; slot < LensMountBlockEntity.SLOTS; slot++) {
            addSlot(new SlotItemHandler(mount.getLensSlots(), slot, SLOT_X[slot], LENS_Y));
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

    /** Button {@code id} (0 to 3) opens the settings of the lens in that slot, if it has any. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_CHANNEL && id < BUTTON_CHANNEL + 2 * LensMountBlockEntity.SLOTS) {
            int slot = (id - BUTTON_CHANNEL) / 2;
            mount.setSlotChannel(slot, com.kadikular.quantimium.laser.Channels.cycle(mount.slotChannel(slot), (id - BUTTON_CHANNEL) % 2 == 1));
            return true;
        }
        if (id < 0 || id >= LensMountBlockEntity.SLOTS) return false;
        ItemStack lens = mount.getLensSlots().getStackInSlot(id);
        if (!(lens.getItem() instanceof com.kadikular.quantimium.item.LensWithSettings)) return false;
        if (player instanceof net.minecraft.server.level.ServerPlayer server) LensHolder.openInMount(server, mount, id);
        return true;
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
