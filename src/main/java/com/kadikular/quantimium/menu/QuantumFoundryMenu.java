package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModMenuTypes;
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

public class QuantumFoundryMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 15;
    /** A diamond around the well, laid out the way the arms are: forward, right, back, left. */
    public static final int[][] INPUT_POSITIONS = {
            {80, 18}, {102, 38}, {80, 58}, {58, 38}
    };
    public static final int OUTPUT_X = 138;
    public static final int OUTPUT_Y = 38;

    private final QuantumFoundryBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public QuantumFoundryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                (QuantumFoundryBlockEntity) playerInventory.player.level()
                        .getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public QuantumFoundryMenu(int containerId, Inventory playerInventory,
                              QuantumFoundryBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.QUANTUM_FOUNDRY_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        checkContainerDataCount(data, DATA_COUNT);

        for (int slot = 0; slot < QuantumFoundryBlockEntity.INPUT_SLOTS; slot++) {
            int inputSlot = slot;
            addSlot(new SlotItemHandler(blockEntity.getInventory(), slot,
                    INPUT_POSITIONS[slot][0], INPUT_POSITIONS[slot][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return blockEntity.hasPillar(inputSlot);
                }
            });
        }
        addSlot(new SlotItemHandler(blockEntity.getInventory(), QuantumFoundryBlockEntity.OUTPUT_SLOT,
                OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    public QuantumFoundryBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return data.get(0) | data.get(1) << 16;
    }

    public int getMaxEnergyStored() {
        return data.get(2) | data.get(3) << 16;
    }

    public int getStatusCode() {
        return data.get(4);
    }

    public int getProgress() {
        return data.get(5);
    }

    public int getDuration() {
        return data.get(6);
    }

    public int getPillarMask() {
        return data.get(7);
    }

    public int getFluxBandOrdinal() {
        return data.get(8);
    }

    public int getAnomalyBandOrdinal() {
        return data.get(9);
    }

    public int getRequiredBandOrdinal() {
        return data.get(10);
    }

    public int getCurrentPowerUse() {
        return data.get(11);
    }

    public int getAveragePowerUse() {
        return data.get(12);
    }

    public int getFluxCost() {
        return data.get(13);
    }

    public int getPassiveDrain() {
        return data.get(14);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();
        if (index < 5) {
            if (!moveItemStackTo(stack, 5, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int input = 0; input < 4 && !moved; input++) {
                if ((getPillarMask() & 1 << input) != 0) {
                    moved = moveItemStackTo(stack, input, input + 1, false);
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get());
    }
}
