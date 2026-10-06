package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.QuantumObservationChamberBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
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

/**
 * Layout matches {@code tools/gui_panels.py} quantum_observation_chamber: Matter in a column at
 * (26, 17), results 4×2 at (74, 26), Trace in its socket at (152, 35).
 */
public class QuantumObservationChamberMenu extends AbstractContainerMenu {

    public static final int INPUT_X = 26;
    public static final int INPUT_Y = 17;
    public static final int OUTPUT_X = 74;
    public static final int OUTPUT_Y = 26;
    public static final int TRACE_X = 152;
    public static final int TRACE_Y = 35;

    public static final int BUTTON_VOID_TRACE = 0;

    /** Machine slots in the menu: three inputs, eight results, Trace. */
    private static final int MACHINE_SLOTS = QuantumObservationChamberBlockEntity.INPUT_SLOTS + 8 + 1;

    private final QuantumObservationChamberBlockEntity blockEntity;
    private final ContainerData data;

    public QuantumObservationChamberMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (QuantumObservationChamberBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(QuantumObservationChamberBlockEntity.DATA_COUNT));
    }

    public QuantumObservationChamberMenu(int containerId, Inventory playerInv,
                                         QuantumObservationChamberBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.QUANTUM_OBSERVATION_CHAMBER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, QuantumObservationChamberBlockEntity.DATA_COUNT);

        for (int i = 0; i < QuantumObservationChamberBlockEntity.INPUT_SLOTS; i++) {
            addSlot(new SlotItemHandler(blockEntity.getInventory(), i, INPUT_X, INPUT_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return com.kadikular.quantimium.unrealised.Matter.is(stack);
                }
            });
        }
        for (int i = 0; i < 8; i++) {
            addSlot(takeOnly(QuantumObservationChamberBlockEntity.OUTPUT_START + i, OUTPUT_X + (i % 4) * 18,
                    OUTPUT_Y + (i / 4) * 18));
        }
        addSlot(takeOnly(QuantumObservationChamberBlockEntity.TRACE_SLOT, TRACE_X, TRACE_Y));

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

    private SlotItemHandler takeOnly(int slot, int x, int y) {
        return new SlotItemHandler(blockEntity.getInventory(), slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    public QuantumObservationChamberBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public int getStatusCode() {
        return data.get(4);
    }

    public int getProgress() {
        return data.get(5);
    }

    public boolean isVoidExcessTrace() {
        return data.get(6) != 0;
    }

    public int getAverageFePerTick() {
        return data.get(7);
    }

    public int getSurchargePercent() {
        return data.get(8);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_VOID_TRACE) {
            blockEntity.toggleVoidExcessTrace();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!com.kadikular.quantimium.unrealised.Matter.is(stack)
                || !moveItemStackTo(stack, 0, QuantumObservationChamberBlockEntity.INPUT_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get());
    }
}
