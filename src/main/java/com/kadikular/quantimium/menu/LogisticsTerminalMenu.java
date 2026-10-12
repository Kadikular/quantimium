package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.LogisticsTerminalBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.network.LogisticsTerminalViewPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Logistics Terminal: the player's inventory below a grid of what the network can give, sent by
 * {@link LogisticsTerminalViewPayload} when it changes. Shift-clicking a stack in the inventory puts it into the
 * network. Layout: tools/gui_panels.py logistics_terminal.
 */
public class LogisticsTerminalMenu extends AbstractContainerMenu {

    public static final int PLAYER_Y = 140;
    /** How often it looks again at what the network holds. */
    private static final int VIEW_TICKS = 10;

    private final LogisticsTerminalBlockEntity terminal;
    @Nullable
    private final Player player;
    private int sinceView;
    @Nullable
    private Map<ItemResource, Long> sent;
    private boolean sentConnected;

    public LogisticsTerminalMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (LogisticsTerminalBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public LogisticsTerminalMenu(int containerId, Inventory playerInv, LogisticsTerminalBlockEntity terminal) {
        super(ModMenuTypes.LOGISTICS_TERMINAL_MENU.get(), containerId);
        this.terminal = terminal;
        this.player = playerInv.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, PLAYER_Y + 58));
        }
    }

    public LogisticsTerminalBlockEntity getBlockEntity() {
        return terminal;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server) || sinceView-- > 0) return;
        sinceView = VIEW_TICKS;
        boolean connected = terminal.controller() != null;
        Map<ItemResource, Long> contents = terminal.contents();
        if (contents.equals(sent) && connected == sentConnected) return;
        sent = contents;
        sentConnected = connected;
        List<LogisticsTerminalViewPayload.Entry> entries = new ArrayList<>(contents.size());
        contents.forEach((item, count) -> entries.add(new LogisticsTerminalViewPayload.Entry(item, count)));
        PacketDistributor.sendToPlayer(server, new LogisticsTerminalViewPayload(terminal.getBlockPos(), connected, entries));
    }

    /** Looks again at once, after the player moved something. */
    private void refresh() {
        sinceView = 0;
    }

    /** Takes {@code count} of {@code item}: onto the cursor (as much as it holds with what's there), or into the inventory. */
    public void take(Player player, ItemResource item, int count, boolean toInventory) {
        int room = count;
        ItemStack carried = getCarried();
        if (!toInventory) {
            if (!carried.isEmpty() && !ItemResource.of(carried).equals(item)) return;
            room = Math.min(count, item.toStack(1).getMaxStackSize() - carried.getCount());
        }
        if (room <= 0) return;
        ItemStack got = terminal.take(item, room);
        if (got.isEmpty()) return;
        if (toInventory) {
            player.getInventory().add(got);
            if (!got.isEmpty()) {
                ItemStack back = terminal.insert(got);
                if (!back.isEmpty()) player.drop(back, false);
            }
        } else if (carried.isEmpty()) {
            setCarried(got);
        } else {
            carried.grow(got.getCount());
            setCarried(carried);
        }
        refresh();
    }

    /** Puts what's on the cursor (or one of it) into the network. */
    public void insertCarried(boolean one) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return;
        if (one) {
            ItemStack left = terminal.insert(carried.copyWithCount(1));
            if (left.isEmpty()) carried.shrink(1);
            setCarried(carried);
        } else {
            setCarried(terminal.insert(carried));
        }
        refresh();
    }

    /** Shift-clicking a stack in the inventory puts it into the network. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || player.level().isClientSide()) return ItemStack.EMPTY;
        ItemStack left = terminal.insert(slot.getItem().copy());
        slot.set(left);
        refresh();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(terminal.getLevel(), terminal.getBlockPos()), player, terminal.getBlockState().getBlock());
    }
}
