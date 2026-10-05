package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
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
import com.kadikular.quantimium.util.SlotItemHandler;

/**
 * The Unfolding Array: three reagent slots down the left, the Sophon out on the right. Layout:
 * tools/gui_panels.py unfolding_array.
 */
public class UnfoldingArrayMenu extends AbstractContainerMenu {

    public static final int REAGENT_X = 26;
    public static final int REAGENT_Y = 17;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 35;
    private static final int MACHINE_SLOTS = 4;

    private final UnfoldingArrayBlockEntity blockEntity;
    private final ContainerData data;

    public UnfoldingArrayMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (UnfoldingArrayBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(UnfoldingArrayBlockEntity.DATA_COUNT));
    }

    public UnfoldingArrayMenu(int containerId, Inventory playerInv, UnfoldingArrayBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.UNFOLDING_ARRAY_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, UnfoldingArrayBlockEntity.DATA_COUNT);
        for (int slot = 0; slot < 3; slot++) {
            addSlot(new SlotItemHandler(blockEntity.getInventory(), slot, REAGENT_X, REAGENT_Y + slot * 18));
        }
        addSlot(new SlotItemHandler(blockEntity.getInventory(), UnfoldingArrayBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
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

    public UnfoldingArrayBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getStatus() {
        return data.get(4);
    }

    public int getProgress() {
        return data.get(5);
    }

    public int getSophons() {
        return data.get(6);
    }

    public int getMaxSophons() {
        return data.get(7);
    }

    public boolean isUnfolding() {
        return data.get(8) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == UnfoldingArrayBlockEntity.BUTTON_UNFOLD && player instanceof ServerPlayer server) {
            blockEntity.pressButton(server);
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
        } else if (!moveItemStackTo(stack, 0, 3, false)) {
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
