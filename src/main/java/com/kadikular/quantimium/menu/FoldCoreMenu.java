package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Fold Core: the Tesseract's socket on the left, what the chamber makes of it in the middle, the
 * Fold button under that and the seal's progress along the bottom. Layout: tools/gui_panels.py fold_core.
 */
public class FoldCoreMenu extends AbstractContainerMenu {

    public static final int SOCKET_X = 26;
    public static final int SOCKET_Y = 35;
    private static final int MACHINE_SLOTS = 1;

    private final FoldCoreBlockEntity blockEntity;
    private final ContainerData data;

    public FoldCoreMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (FoldCoreBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(FoldCoreBlockEntity.DATA_COUNT));
    }

    public FoldCoreMenu(int containerId, Inventory playerInv, FoldCoreBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.FOLD_CORE_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, FoldCoreBlockEntity.DATA_COUNT);
        addSlot(new SlotItemHandler(blockEntity.getSocket(), 0, SOCKET_X, SOCKET_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    public FoldCoreBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public FoldCoreBlockEntity.Status getStatus() {
        FoldCoreBlockEntity.Status[] all = FoldCoreBlockEntity.Status.values();
        int ordinal = data.get(4);
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FoldCoreBlockEntity.Status.EMPTY;
    }

    public int getSealed() {
        return data.get(5);
    }

    /** FE the fold in the socket will take, once it can go. */
    public int getCost() {
        return (data.get(7) << 16) | (data.get(6) & 0xFFFF);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == FoldCoreBlockEntity.BUTTON_FOLD && player instanceof ServerPlayer server) {
            blockEntity.start(server);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, blockEntity.getBlockState().getBlock());
    }
}
