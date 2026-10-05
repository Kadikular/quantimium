package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.recipe.EntangledLinks;
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

public class ContainmentHallMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 20;
    /** Top right of the machine band, clear of the status plate and the readout lines. */
    public static final int RESIDUE_X = 152;
    public static final int RESIDUE_Y = 20;
    private static final int PLAYER_START = 1;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final ContainmentHallBlockEntity blockEntity;
    private final ContainerData data;

    public ContainmentHallMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (ContainmentHallBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public ContainmentHallMenu(int containerId, Inventory playerInv, ContainmentHallBlockEntity blockEntity,
                              ContainerData data) {
        super(ModMenuTypes.CONTAINMENT_HALL_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);

        this.addSlot(new SlotItemHandler(blockEntity.getResidue(), 0, RESIDUE_X, RESIDUE_Y));

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

    public ContainmentHallBlockEntity getBlockEntity() {
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

    public int getCurrentPowerUse() {
        return (data.get(6) << 16) | (data.get(5) & 0xFFFF);
    }

    public int getAveragePowerUse() {
        return (data.get(8) << 16) | (data.get(7) & 0xFFFF);
    }

    public int getAnomalySurchargePercent() {
        return data.get(9);
    }

    public FluxBand getAnomalyBand() {
        return band(data.get(10));
    }

    public int getPassiveDrainFePerTick() {
        return data.get(11);
    }

    public int getArmCount() {
        return Integer.bitCount(data.get(12));
    }

    public FluxBand getShieldCap() {
        return ContainmentHallBlockEntity.shieldCap(getArmCount());
    }

    /** Anomaly removed from the whole field in the last second. */
    public double getSuppressionPerSecond() {
        return data.get(13);
    }

    /** Load in the hall's chunk as a percentage; above 100 the overflow is becoming anomaly. */
    /** Its cell's state, for a Veiled. */
    public ContainmentHallBlockEntity.Cell getCell() {
        ContainmentHallBlockEntity.Cell[] cells = ContainmentHallBlockEntity.Cell.values();
        return cells[Math.clamp(data.get(19), 0, cells.length - 1)];
    }

    public int getLoadPercent() {
        return data.get(18);
    }

    /** Residue on hand: in the slot, or in the inventory a Tesseract there links to. */
    public int getResidueCount() {
        return data.get(16);
    }

    public boolean isLinked() {
        return data.get(17) != 0;
    }

    /** Holding time left on what is burnt and stored. */
    public int getResidueSecondsLeft() {
        return data.get(14);
    }

    /** Ticks the hall has held on with nothing to burn; zero while fed. */
    public int getStarvingTicks() {
        return data.get(15);
    }

    public int getRatedFePerTick() {
        return ContainmentHallBlockEntity.fePerTick(getArmCount());
    }

    private static FluxBand band(int ordinal) {
        FluxBand[] bands = FluxBand.values();
        return ordinal < 0 || ordinal >= bands.length ? FluxBand.LOW : bands[ordinal];
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (stack.is(ModItems.RIFT_RESIDUE.get()) || EntangledLinks.isBound(stack)) {
            if (!moveItemStackTo(stack, 0, PLAYER_START, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
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
                player, ModBlocks.ANOMALY_CONTAINMENT_HALL.get());
    }
}
