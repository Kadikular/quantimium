package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.util.SlotItemHandler;

public class TesseractStabilizerMenu extends QuantumMenu {

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

        addPlayerInventory(playerInv, PLAYER_INVENTORY_Y);

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
        return shiftClick(player, index, stack -> index == 0
                ? moveItemStackTo(stack, 1, slots.size(), true)
                : EntangledLinks.fitsStabilizer(stack) && moveItemStackTo(stack, 0, 1, false));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.TESSERACT_STABILIZER.get());
    }
}
