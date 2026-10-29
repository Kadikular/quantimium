package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.EmitterHolder;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.item.LensItem;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One of a Laser Relay's emitters: the lens in it (only the light leaving by it goes through it), and how it
 * listens to the redstone reaching the block. Layout: tools/gui_panels.py relay_emitter.
 */
public class RelayEmitterMenu extends AbstractContainerMenu implements ChannelMenu {

    public static final int BUTTON_MODE = 0;
    /** The settings of the lens in it: an Interact lens's switches in place, a filter lens's open. */
    public static final int BUTTON_LENS = 1;
    public static final int BUTTON_Y = 55;
    public static final int BUTTON_W = 18;
    public static final int BUTTON_H = 10;
    public static final int LENS_X = 80;
    public static final int LENS_Y = 35;

    private final EmitterHolder relay;
    private final Direction face;

    public RelayEmitterMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (EmitterHolder) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                extraData.readEnum(Direction.class));
    }

    public RelayEmitterMenu(int containerId, Inventory playerInv, EmitterHolder relay, Direction face) {
        super(ModMenuTypes.RELAY_EMITTER_MENU.get(), containerId);
        this.relay = relay;
        this.face = face;
        addDataSlot(channelSlot = new ChannelMenu.ChannelSlot(relay == null || playerInv.player.level().isClientSide() ? null : () -> relay.channelOf(face)));
        addSlot(new Slot(new LensHolder(relay, face), 0, LENS_X, LENS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof LensItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
    }

    public EmitterHolder getBlockEntity() {
        return relay;
    }

    public Direction face() {
        return face;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            relay.cycleMode(face);
            return true;
        }
        if (id != BUTTON_LENS) return false;
        ItemStack lens = relay.lens(face);
        if (lens.getItem() instanceof com.kadikular.quantimium.item.InteractLensItem) {
            com.kadikular.quantimium.item.InteractLensItem.toggle(lens);
            relay.setLens(face, lens);
            return true;
        }
        if (!(lens.getItem() instanceof com.kadikular.quantimium.item.LensWithSettings)) return false;
        if (player instanceof net.minecraft.server.level.ServerPlayer server) com.kadikular.quantimium.menu.LensHolder.openInRelay(server, relay, face);
        return true;
    }

    /** Shift-click: a lens into the emitter if it's empty, or the emitter's lens out to the inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!(stack.getItem() instanceof LensItem) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return relay != null && !relay.isRemoved() && relay.hasEmitter(face)
                && player.distanceToSqr(relay.getBlockPos().getCenter()) <= 64.0;
    }

    /** The emitter's lens, as a one-slot container. */
    private record LensHolder(EmitterHolder relay, Direction face) implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return relay.lens(face).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return relay.lens(face);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack lens = relay.lens(face);
            if (lens.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            relay.setLens(face, ItemStack.EMPTY);
            return lens;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            relay.setLens(face, stack.copyWithCount(Math.min(1, stack.getCount())));
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            relay.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !relay.isRemoved();
        }

        @Override
        public void clearContent() {
            relay.setLens(face, ItemStack.EMPTY);
        }
    }

    private ChannelMenu.ChannelSlot channelSlot;

    @Override
    public int channel() {
        return channelSlot.shown();
    }

    @Override
    public void setChannel(int channel) {
        if (relay != null) relay.setChannel(face, channel);
    }
}
