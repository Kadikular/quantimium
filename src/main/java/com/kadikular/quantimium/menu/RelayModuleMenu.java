package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.RelayModuleBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.superposition.Relay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * The Relay module's hold: six Tesseracts in two columns of three, each with its button (Recall or Send),
 * and the Sophon slot on the right. Layout: tools/gui_panels.py relay_module.
 */
public class RelayModuleMenu extends AbstractContainerMenu {

    public static final int[] LINK_X = {26, 84};
    public static final int LINK_Y = 17;
    public static final int BUTTON_OFFSET = 18;
    public static final int BUTTON_WIDTH = 36;
    public static final int SOPHON_X = 152;
    public static final int SOPHON_Y = 35;
    private static final int MACHINE_SLOTS = RelayModuleBlockEntity.LINKS + 1;

    private final RelayModuleBlockEntity blockEntity;
    private final ContainerData data;
    private final Player player;

    public RelayModuleMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (RelayModuleBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(RelayModuleBlockEntity.DATA_COUNT));
    }

    public RelayModuleMenu(int containerId, Inventory playerInv, RelayModuleBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.RELAY_MODULE_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.player = playerInv.player;
        checkContainerDataCount(data, RelayModuleBlockEntity.DATA_COUNT);
        for (int slot = 0; slot < RelayModuleBlockEntity.LINKS; slot++) {
            addSlot(new SlotItemHandler(blockEntity.getInventory(), slot, LINK_X[slot / 3], LINK_Y + (slot % 3) * 18));
        }
        addSlot(new SlotItemHandler(blockEntity.getInventory(), RelayModuleBlockEntity.SOPHON_SLOT, SOPHON_X, SOPHON_Y));
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

    public RelayModuleBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** What Tesseract slot {@code slot} leads to: one of {@link RelayModuleBlockEntity}'s {@code LINK_} values. */
    public int linkState(int slot) {
        return data.get(slot);
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer server) blockEntity.refresh((ServerLevel) server.level(), server.getUUID());
        super.broadcastChanges();
    }

    /** Button {@code 2 × slot} recalls from that slot's pod; {@code 2 × slot + 1} sends to it. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server)) return true;
        int slot = id / 2;
        if (slot < 0 || slot >= RelayModuleBlockEntity.LINKS) return false;
        if (id % 2 == 0) {
            Relay.recall(server, blockEntity, slot);
        } else {
            Relay.send(server, blockEntity, slot);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, blockEntity.getBlockState().getBlock());
    }
}
