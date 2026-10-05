package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
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
 * Layout matches {@code tools/gui_panels.py} observation_chamber panel:
 * input (44, 35), Trace (80, 35), outputs 3×3 at (116, 18).
 */
public class ObservationChamberMenu extends AbstractContainerMenu {

    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 35;
    public static final int TRACE_X = 80;
    public static final int TRACE_Y = 35;
    public static final int OUTPUT_ORIGIN_X = 116;
    public static final int OUTPUT_ORIGIN_Y = 18;

    public static final int BUTTON_VOID_TRACE = 0;

    private final ObservationChamberBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public ObservationChamberMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (ObservationChamberBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    public ObservationChamberMenu(int containerId, Inventory playerInv,
                                  ObservationChamberBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.OBSERVATION_CHAMBER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        checkContainerDataCount(data, 2);

        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), ObservationChamberBlockEntity.INPUT_SLOT,
                INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.UNREALISED_MATTER.get());
            }
        });

        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), ObservationChamberBlockEntity.TRACE_SLOT,
                TRACE_X, TRACE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = ObservationChamberBlockEntity.OUTPUT_START + row * 3 + col;
                this.addSlot(new SlotItemHandler(blockEntity.getInventory(), slot,
                        OUTPUT_ORIGIN_X + col * 18, OUTPUT_ORIGIN_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

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

    public ObservationChamberBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getStatusCode() {
        return data.get(0);
    }

    public boolean isVoidExcessTrace() {
        return data.get(1) != 0;
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
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();
        int machineSlots = 11;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!stack.is(ModItems.UNREALISED_MATTER.get())
                    || !moveItemStackTo(stack, ObservationChamberBlockEntity.INPUT_SLOT,
                    ObservationChamberBlockEntity.INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.OBSERVATION_CHAMBER.get());
    }
}
