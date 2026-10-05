package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.util.SlotItemHandler;

public class TesseractStabilizerMenu extends AbstractContainerMenu {

    private final TesseractStabilizerBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public TesseractStabilizerMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (TesseractStabilizerBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(1));
    }

    public TesseractStabilizerMenu(int containerId, Inventory playerInv,
                                   TesseractStabilizerBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.TESSERACT_STABILIZER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        checkContainerDataCount(data, 1);

        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), TesseractStabilizerBlockEntity.LINK_SLOT, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EntangledLinks.fitsStabilizer(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public TesseractStabilizerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getStatusCode() {
        return data.get(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!EntangledLinks.fitsStabilizer(stack) || !moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.TESSERACT_STABILIZER.get());
    }
}
