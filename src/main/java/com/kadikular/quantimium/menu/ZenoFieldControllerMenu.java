package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
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

/** No machine slots: the controller's settings are its tabs, sent as menu buttons. */
public class ZenoFieldControllerMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 16;

    private final ZenoFieldControllerBlockEntity blockEntity;
    private final ContainerData data;

    public ZenoFieldControllerMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (ZenoFieldControllerBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public ZenoFieldControllerMenu(int containerId, Inventory playerInv, ZenoFieldControllerBlockEntity blockEntity,
                                   ContainerData data) {
        super(ModMenuTypes.ZENO_FIELD_CONTROLLER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
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

    public ZenoFieldControllerBlockEntity getBlockEntity() {
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

    public int getMode() {
        return data.get(5);
    }

    public int getRadius() {
        return data.get(6);
    }

    public int getFactor() {
        return data.get(7);
    }

    public int getCurrentPowerUse() {
        return (data.get(9) << 16) | (data.get(8) & 0xFFFF);
    }

    public int getAveragePowerUse() {
        return (data.get(11) << 16) | (data.get(10) & 0xFFFF);
    }

    public int getPassiveDrainFePerTick() {
        return data.get(12);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        blockEntity.pressButton(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.ZENO_FIELD_CONTROLLER.get());
    }

    /** The rate it runs at: the one chosen, capped by the flux band here. */
    public int getRunningFactor() {
        return data.get(13);
    }

    public FluxBand getBand() {
        return FluxBand.values()[Math.clamp(data.get(14), 0, FluxBand.values().length - 1)];
    }

    public FluxBand getHeading() {
        return FluxBand.values()[Math.clamp(data.get(15), 0, FluxBand.values().length - 1)];
    }
}
