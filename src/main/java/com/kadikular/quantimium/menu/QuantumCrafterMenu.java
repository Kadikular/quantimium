package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.kadikular.quantimium.util.SlotItemHandler;

public class QuantumCrafterMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 19;
    private static final int MENU_CATALYST_SLOT = 9;
    private static final int MACHINE_MENU_SLOTS = 10;
    /** Where the player's inventory starts on the panel (tools/gui_panels.py quantum_crafter). */
    public static final int PLAYER_Y = 152;

    private final QuantumCrafterBlockEntity blockEntity;
    private final ContainerData data;

    public QuantumCrafterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (QuantumCrafterBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public QuantumCrafterMenu(int containerId, Inventory playerInv, QuantumCrafterBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.QUANTUM_CRAFTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);

        // 3x3 Inputs (0-8)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(blockEntity.getInventory(), col + row * 3, 12 + col * 18, 18 + row * 18));
            }
        }

        // The outputs aren't slots: the screen shows them all in a grid, and takes from them by button.
        // Catalyst (menu slot 9; backing slot follows all 54 outputs)
        this.addSlot(new SlotItemHandler(blockEntity.getInventory(), QuantumCrafterBlockEntity.CATALYST_SLOT, 80, 36));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, PLAYER_Y + 58));
        }

        addDataSlots(data);
    }

    public QuantumCrafterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isBasic() {
        return blockEntity.isBasic();
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

    public boolean isRecipeLocked() {
        return data.get(5) != 0;
    }

    public int getGhostCount() {
        return data.get(6);
    }

    public int getCurrentPowerUse() {
        return (data.get(8) << 16) | (data.get(7) & 0xFFFF);
    }

    public int getAveragePowerUse() {
        return (data.get(10) << 16) | (data.get(9) & 0xFFFF);
    }

    public int getAnomalySurchargePercent() {
        return data.get(11);
    }

    public FluxBand getAnomalyBand() {
        int ordinal = data.get(12);
        FluxBand[] bands = FluxBand.values();
        if (ordinal < 0 || ordinal >= bands.length) return FluxBand.LOW;
        return bands[ordinal];
    }

    public int getPassiveDrainFePerTick() {
        return data.get(13);
    }

    public int getCoherenceReserve() {
        return data.get(14);
    }

    public int getCoherenceCapacity() {
        return data.get(15);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stackInSlot = slot.getItem();
        itemstack = stackInSlot.copy();

        if (index < MACHINE_MENU_SLOTS) {
            if (!this.moveItemStackTo(stackInSlot, MACHINE_MENU_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Prefer catalyst if empty, else inputs.
            ItemStack catalyst = blockEntity.getInventory().getStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT);
            if (catalyst.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlot, MENU_CATALYST_SLOT,
                        MENU_CATALYST_SLOT + 1, false)) {
                    if (!this.moveItemStackTo(stackInSlot, 0, QuantumCrafterBlockEntity.INPUT_SLOTS, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(stackInSlot, 0, QuantumCrafterBlockEntity.INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (stackInSlot.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stackInSlot);
        return itemstack;
    }

    /** Moves {@code stack} into the player's inventory; what didn't fit. */
    private ItemStack toInventory(ItemStack stack) {
        ItemStack moving = stack.copy();
        moveItemStackTo(moving, MACHINE_MENU_SLOTS, slots.size(), true);
        return moving;
    }

    /**
     * Menu buttons taking what output {@code slot} (0 to 53) shows: {@code mode * TAKE + slot}. As in a logistics
     * terminal, what's in its outputs comes first, from every output holding it, and only with none there is more
     * made. A batch is a stack of what's there, or a preview's batch; half of that; one; or the batch into the
     * inventory.
     */
    public static final int TAKE = 100, BATCH = 1, HALF = 2, ONE = 3, TO_INVENTORY = 4;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int mode = id / TAKE;
        int local = id % TAKE;
        if (mode < BATCH || mode > TO_INVENTORY || local >= QuantumCrafterBlockEntity.OUTPUT_SLOTS) return false;
        int absolute = QuantumCrafterBlockEntity.OUTPUT_START + local;
        ItemStack real = blockEntity.getInventory().getStackInSlot(absolute);
        ItemStack shown = real.isEmpty() ? blockEntity.getGhost(absolute) : real;
        if (shown.isEmpty()) return false;
        // What of it is really there, in every output.
        int there = 0;
        for (int slot = QuantumCrafterBlockEntity.OUTPUT_START; slot <= QuantumCrafterBlockEntity.OUTPUT_END; slot++) {
            ItemStack held = blockEntity.getInventory().getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(held, shown)) there += held.getCount();
        }
        int batch = there > 0 ? Math.min(there, shown.getMaxStackSize()) : shown.getCount();
        ItemStack carried = getCarried();
        boolean toInventory = mode == TO_INVENTORY;
        if (!toInventory && !carried.isEmpty() && !ItemStack.isSameItemSameComponents(carried, shown)) return false;
        int room = toInventory ? batch : shown.getMaxStackSize() - carried.getCount();
        int wanted = Math.min(room, switch (mode) {
            case HALF -> Math.max(1, (batch + 1) / 2);
            case ONE -> 1;
            default -> batch;
        });
        if (wanted <= 0) return false;
        ItemStack got;
        if (there > 0) {
            got = ItemStack.EMPTY;
            for (int slot = QuantumCrafterBlockEntity.OUTPUT_START; slot <= QuantumCrafterBlockEntity.OUTPUT_END && got.getCount() < wanted; slot++) {
                if (!ItemStack.isSameItemSameComponents(blockEntity.getInventory().getStackInSlot(slot), shown)) continue;
                ItemStack more = blockEntity.getInventory().extractItem(slot, wanted - got.getCount(), false);
                got = got.isEmpty() ? more : got.copyWithCount(got.getCount() + more.getCount());
            }
        } else {
            got = toInventory ? blockEntity.takeGhost(absolute) : blockEntity.takeGhost(absolute, wanted);
        }
        if (got.isEmpty()) return false;
        if (toInventory) {
            ItemStack left = toInventory(got);
            if (!left.isEmpty()) blockEntity.storeOutput(left);
        } else {
            // What a run made past what was asked for (eight more nuggets) stays in its outputs.
            int onto = Math.min(wanted, got.getCount());
            setCarried(carried.isEmpty() ? got.copyWithCount(onto) : carried.copyWithCount(carried.getCount() + onto));
            if (got.getCount() > onto) blockEntity.storeOutput(got.copyWithCount(got.getCount() - onto));
        }
        blockEntity.rebuildPreview();
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = this.blockEntity.getLevel() != null ? this.blockEntity.getLevel() : player.level();
        return level.getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && blockEntity.isUsableBy(player);
    }

    /** The flux band here, where it's heading, and the band the catalyst in the slot needs. */
    public FluxBand getBand() {
        return band(16);
    }

    public FluxBand getHeading() {
        return band(17);
    }

    public FluxBand getRequiredBand() {
        return band(18);
    }

    private FluxBand band(int index) {
        return FluxBand.values()[Math.clamp(data.get(index), 0, FluxBand.values().length - 1)];
    }
}
