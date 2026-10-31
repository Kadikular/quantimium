package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.util.RedstoneMode;
import com.kadikular.quantimium.util.SlotItemHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A laser emitter: its power, what it's sending, its redstone setting, and for the flash emitter, its
 * fuel. Layout: tools/gui_panels.py laser_emitter.
 */
public class LaserEmitterMenu extends QuantumMenu {

    public static final int BUTTON_REDSTONE = 0;
    public static final int FUEL_X = 26;
    public static final int FUEL_Y = 44;

    private final LaserEmitterBlockEntity emitter;
    private final ContainerData data;
    private final int machineSlots;

    public LaserEmitterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (LaserEmitterBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(LaserEmitterBlockEntity.DATA_COUNT));
    }

    public LaserEmitterMenu(int containerId, Inventory playerInv, LaserEmitterBlockEntity emitter) {
        this(containerId, playerInv, emitter, emitter.getData());
    }

    private LaserEmitterMenu(int containerId, Inventory playerInv, LaserEmitterBlockEntity emitter, ContainerData data) {
        super(ModMenuTypes.LASER_EMITTER_MENU.get(), containerId);
        this.emitter = emitter;
        this.data = data;
        checkContainerDataCount(data, LaserEmitterBlockEntity.DATA_COUNT);
        boolean flash = emitter.tier() == LaserTuning.Tier.FLASH;
        if (flash) addSlot(new SlotItemHandler(emitter.getFuel(), 0, FUEL_X, FUEL_Y));
        machineSlots = flash ? 1 : 0;
        addPlayerInventory(playerInv, PLAYER_INVENTORY_Y);
        addDataSlots(data);
    }

    public LaserEmitterBlockEntity getBlockEntity() {
        return emitter;
    }

    public int energy() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int capacity() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    public RedstoneMode redstone() {
        return RedstoneMode.byOrdinal(data.get(4));
    }

    /** How much of the fuel burning now is left, 0 to 1. */
    public float burn() {
        int total = data.get(6);
        return total <= 0 ? 0 : Math.clamp(data.get(5) / (float) total, 0, 1);
    }

    public int sending() {
        return (data.get(8) << 16) | (data.get(7) & 0xFFFF);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            emitter.cycleRedstone();
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
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (machineSlots == 0 || !moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(emitter.getLevel(), emitter.getBlockPos()),
                player, emitter.getBlockState().getBlock());
    }
}
