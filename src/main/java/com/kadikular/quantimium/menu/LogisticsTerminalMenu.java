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

    /** The terminal, on the server; on the client only its place, which may be far away (opened through a pod). */
    @Nullable
    private final LogisticsTerminalBlockEntity terminal;
    private final net.minecraft.core.BlockPos pos;
    /** Opened from afar, through a pod's Logistics module: no need to stand by it. */
    private final boolean remote;
    @Nullable
    private final Player player;
    private int sinceView;
    @Nullable
    private Map<ItemResource, List<Long>> sent;
    private boolean sentConnected;

    public LogisticsTerminalMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, null, extraData.readBlockPos(), true);
    }

    public LogisticsTerminalMenu(int containerId, Inventory playerInv, LogisticsTerminalBlockEntity terminal, boolean remote) {
        this(containerId, playerInv, terminal, terminal.getBlockPos(), remote);
    }

    private LogisticsTerminalMenu(int containerId, Inventory playerInv, @Nullable LogisticsTerminalBlockEntity terminal, net.minecraft.core.BlockPos pos,
                                  boolean remote) {
        super(ModMenuTypes.LOGISTICS_TERMINAL_MENU.get(), containerId);
        this.terminal = terminal;
        this.pos = pos;
        this.remote = remote;
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

    /** Where the terminal is: what the screen's clicks name. */
    public net.minecraft.core.BlockPos pos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server) || terminal == null || sinceView-- > 0) return;
        sinceView = VIEW_TICKS;
        boolean connected = terminal.controller() != null;
        Map<ItemResource, long[]> contents = terminal.contents();
        Map<ItemResource, List<Long>> comparable = new java.util.HashMap<>();
        contents.forEach((item, both) -> comparable.put(item, List.of(both[0], both[1])));
        if (comparable.equals(sent) && connected == sentConnected) return;
        sent = comparable;
        sentConnected = connected;
        List<LogisticsTerminalViewPayload.Entry> entries = new ArrayList<>(contents.size());
        contents.forEach((item, both) -> entries.add(new LogisticsTerminalViewPayload.Entry(item, both[0], both[1])));
        PacketDistributor.sendToPlayer(server, new LogisticsTerminalViewPayload(terminal.getBlockPos(), connected, entries));
    }

    /** Looks again at once, after the player moved something. */
    private void refresh() {
        sinceView = 0;
    }

    /** Takes {@code count} of {@code item}: onto the cursor (as much as it holds with what's there), or into the inventory. */
    public void take(Player player, ItemResource item, int count, boolean toInventory) {
        if (terminal == null) return;
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
        if (terminal == null) return;
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
        if (!slot.hasItem() || player.level().isClientSide() || terminal == null) return ItemStack.EMPTY;
        ItemStack left = terminal.insert(slot.getItem().copy());
        slot.set(left);
        refresh();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (terminal == null) return true;
        if (remote) return !terminal.isRemoved();
        return stillValid(ContainerLevelAccess.create(terminal.getLevel(), terminal.getBlockPos()), player, terminal.getBlockState().getBlock());
    }
}
