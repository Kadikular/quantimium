package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.laser.LogisticsMember;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A Logistics Controller: its channel, its power, and what's on its network. Layout: tools/gui_panels.py logistics_controller. */
public class LogisticsControllerMenu extends AbstractContainerMenu implements ChannelMenu {

    /** What it shows: its receptacles by role, whether another controller's light joins its own, and its power. */
    public static final int STORAGE = 0, PROVIDERS = 1, REQUESTERS = 2, CLASH = 3, ENERGY_LOW = 4, ENERGY_HIGH = 5, DATA = 6;

    private final LogisticsControllerBlockEntity controller;
    private final ContainerData data;
    private final ChannelMenu.ChannelSlot channelSlot;

    public LogisticsControllerMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LogisticsControllerBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LogisticsControllerMenu(int containerId, Inventory playerInv, LogisticsControllerBlockEntity controller) {
        super(ModMenuTypes.LOGISTICS_CONTROLLER_MENU.get(), containerId);
        this.controller = controller;
        boolean server = controller != null && !playerInv.player.level().isClientSide();
        this.data = server ? new ContainerData() {
            @Override
            public int get(int index) {
                int energy = controller.getEnergyStorage().getEnergyStored();
                return switch (index) {
                    case STORAGE -> count(LogisticsMember.Role.STORAGE);
                    case PROVIDERS -> count(LogisticsMember.Role.PROVIDER);
                    case REQUESTERS -> count(LogisticsMember.Role.REQUESTER);
                    case CLASH -> controller.clashes() ? 1 : 0;
                    case ENERGY_LOW -> energy & 0xFFFF;
                    case ENERGY_HIGH -> energy >>> 16 & 0xFFFF;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA;
            }
        } : new SimpleContainerData(DATA);
        addDataSlots(data);
        addDataSlot(channelSlot = new ChannelMenu.ChannelSlot(server ? controller::channel : null));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
    }

    private int count(LogisticsMember.Role role) {
        int count = 0;
        for (LogisticsMember member : controller.allMembers()) if (member.role() == role) count++;
        return count;
    }

    public LogisticsControllerBlockEntity getBlockEntity() {
        return controller;
    }

    public int get(int index) {
        return data.get(index);
    }

    public int energy() {
        return data.get(ENERGY_LOW) | data.get(ENERGY_HIGH) << 16;
    }

    @Override
    public int channel() {
        return channelSlot.shown();
    }

    @Override
    public void setChannel(int channel) {
        if (controller != null) controller.setChannel(channel);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (controller == null) return false;
        Level level = controller.getLevel();
        return level != null && level.getBlockEntity(controller.getBlockPos()) == controller
                && player.distanceToSqr(controller.getBlockPos().getCenter()) <= 64.0;
    }
}
