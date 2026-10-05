package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.RiftAnchorBlockEntity;
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

/** One slot, the residue the rift sheds, which can be taken but not put back; the rest is readout. */
public class RiftAnchorMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 9;
    /** Top right, as on the Containment Hall, with the time to the next one under it. */
    public static final int RESIDUE_X = 152;
    public static final int RESIDUE_Y = 20;
    private static final int PLAYER_START = 1;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final RiftAnchorBlockEntity blockEntity;
    private final ContainerData data;

    public RiftAnchorMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (RiftAnchorBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public RiftAnchorMenu(int containerId, Inventory playerInv, RiftAnchorBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.RIFT_ANCHOR_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);

        addSlot(new SlotItemHandler(blockEntity.getOutput(), 0, RESIDUE_X, RESIDUE_Y) {
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

    public RiftAnchorBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getStatusCode() {
        return data.get(0);
    }

    /** The rift's stage, 0 with no rift. */
    public int getStage() {
        return data.get(1);
    }

    /** Stabilisers paying for the rift this tick. */
    public int getHolding() {
        return data.get(2);
    }

    public int getClaimed() {
        return data.get(3);
    }

    public int getSighted() {
        return data.get(4);
    }

    public int getBlind() {
        return data.get(5);
    }

    public int getSpare() {
        return data.get(6);
    }

    /** Claimed, able to see the rift, but not paying: out of power. Only counted once enough can see it. */
    public int getUnpowered() {
        return getSighted() >= RiftAnchorBlockEntity.MIN_STABILISERS ? Math.max(0, getClaimed() - getHolding()) : 0;
    }

    /** Progress to the next residue, 0 to 1. */
    public float getProgress() {
        return data.get(7) / 1000.0f;
    }

    /** -1 while not producing. */
    public int getSecondsToNext() {
        return data.get(8);
    }

    public int getWorkPerTick() {
        return getHolding() >= RiftAnchorBlockEntity.MIN_STABILISERS ? getStage() * getHolding() : 0;
    }

    public double getResiduePerHour() {
        return RiftAnchorBlockEntity.residuePerHour(getWorkPerTick());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= PLAYER_START) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.RIFT_ANCHOR.get());
    }
}
