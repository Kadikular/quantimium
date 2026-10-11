package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.AreaPrismBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** An Area Prism: its shape (the area picked, or a cube or sphere round it) and radius, on tabs. Layout: tools/gui_panels.py area_prism. */
public class AreaPrismMenu extends AbstractContainerMenu {

    public static final int BUTTON_SHAPE = 0;
    public static final int BUTTON_WIDER = 1;
    public static final int BUTTON_NARROWER = 2;
    /** Shift on the radius buttons: eight at a time. */
    public static final int BUTTON_MUCH_WIDER = 3;
    public static final int BUTTON_MUCH_NARROWER = 4;

    private final AreaPrismBlockEntity prism;

    public AreaPrismMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (AreaPrismBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public AreaPrismMenu(int containerId, Inventory playerInv, AreaPrismBlockEntity prism) {
        super(ModMenuTypes.AREA_PRISM_MENU.get(), containerId);
        this.prism = prism;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
    }

    public AreaPrismBlockEntity getBlockEntity() {
        return prism;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_SHAPE -> prism.cycleShape();
            case BUTTON_WIDER -> prism.setRadius(prism.radius() + 1);
            case BUTTON_NARROWER -> prism.setRadius(prism.radius() - 1);
            case BUTTON_MUCH_WIDER -> prism.setRadius(prism.radius() + 8);
            case BUTTON_MUCH_NARROWER -> prism.setRadius(prism.radius() - 8);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(prism.getLevel(), prism.getBlockPos()), player, prism.getBlockState().getBlock());
    }
}
