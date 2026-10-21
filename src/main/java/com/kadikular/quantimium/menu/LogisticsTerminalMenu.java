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
 * network. Under the item grid, shown by a tab, a crafting grid that the network refills after each craft. Layout:
 * tools/gui_panels.py logistics_terminal and logistics_terminal_crafting.
 */
public class LogisticsTerminalMenu extends AbstractContainerMenu {

    public static final int PLAYER_Y = 158;
    /** The crafting grid and its result, under the item grid when shown (tools/gui_panels.py logistics_terminal_crafting). */
    private static final int CRAFT_GRID_X = 44;
    private static final int CRAFT_GRID_Y = 96;
    private static final int CRAFT_RESULT_X = 126;
    private static final int CRAFT_RESULT_Y = 114;
    /** Menu slots: the inventory, then the grid, then the result. */
    private static final int INVENTORY_SLOTS = 36;
    public static final int GRID_FIRST = INVENTORY_SLOTS;
    public static final int RESULT_SLOT = GRID_FIRST + 9;
    /** {@link #clickMenuButton} ids. */
    public static final int BUTTON_CLEAR = 0;
    /** Most kinds one ingredient slot may be filled from: a tag's worth, as a recipe viewer lists them. */
    public static final int MAX_OPTIONS = 64;

    /** Whether the crafting panel is shown (on the client; the server keeps the slots either way). */
    private static boolean craftingShown;

    public static boolean craftingShown() {
        return craftingShown;
    }

    public static void showCrafting(boolean shown) {
        craftingShown = shown;
    }

    private final net.minecraft.world.inventory.TransientCraftingContainer craftGrid = new net.minecraft.world.inventory.TransientCraftingContainer(this, 3, 3);
    private final net.minecraft.world.inventory.ResultContainer craftResult = new net.minecraft.world.inventory.ResultContainer();
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
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftGrid, col + row * 3, CRAFT_GRID_X + col * 18, CRAFT_GRID_Y + row * 18) {
                    @Override
                    public boolean isActive() {
                        return craftingShown || !player.level().isClientSide();
                    }
                });
            }
        }
        addSlot(new net.minecraft.world.inventory.ResultSlot(playerInv.player, craftGrid, craftResult, 0, CRAFT_RESULT_X, CRAFT_RESULT_Y) {
            @Override
            public boolean isActive() {
                return craftingShown || !player.level().isClientSide();
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                ItemStack[] before = new ItemStack[9];
                for (int i = 0; i < 9; i++) before[i] = craftGrid.getItem(i).copy();
                super.onTake(taker, stack);
                refill(before);
            }
        });
    }

    /** The crafting grid's slots, in order (for recipe viewers). */
    public List<Slot> gridSlots() {
        return slots.subList(GRID_FIRST, GRID_FIRST + 9);
    }

    /**
     * After a craft: each grid slot that had something used up is topped up to what it held, from what's stored on
     * the network (one craft's worth of the same thing, as the grid had it); what's left in it is kept.
     */
    private void refill(ItemStack[] before) {
        if (terminal == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack had = before[i];
            if (had.isEmpty()) continue;
            ItemStack now = craftGrid.getItem(i);
            if (!now.isEmpty() && !ItemStack.isSameItemSameComponents(now, had)) continue;
            int want = had.getCount() - now.getCount();
            if (want <= 0) continue;
            ItemStack got = terminal.takeStored(ItemResource.of(had), want);
            if (got.isEmpty()) continue;
            if (now.isEmpty()) craftGrid.setItem(i, got);
            else {
                now.grow(got.getCount());
                craftGrid.setItem(i, now);
            }
        }
        slotsChanged(craftGrid);
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        if (container != craftGrid || player == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var input = craftGrid.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        var recipe = level.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level,
                (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) null);
        if (recipe.isPresent() && (!(player instanceof ServerPlayer server) || craftResult.setRecipeUsed(server, recipe.get()))) {
            ItemStack made = recipe.get().value().assemble(input);
            if (made.isItemEnabled(level.enabledFeatures())) result = made;
        }
        craftResult.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        if (player instanceof ServerPlayer server && server.connection != null) {
            server.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_SLOT, result));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CLEAR) {
            clearGrid(player);
            return true;
        }
        return false;
    }

    /** Puts what's in the crafting grid back into the network; what it won't take goes to the player. */
    private void clearGrid(Player player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftGrid.getItem(i);
            if (stack.isEmpty()) continue;
            ItemStack left = terminal == null ? stack : terminal.insert(stack);
            if (!left.isEmpty() && !player.getInventory().add(left)) player.drop(left, false);
            craftGrid.setItem(i, ItemStack.EMPTY);
        }
        slotsChanged(craftGrid);
        refresh();
    }

    /**
     * Fills the crafting grid for a recipe a recipe viewer chose: {@code options} per grid slot (none for an empty
     * one), the first of each that can be had. What's in the grid goes back first. Each slot is filled from what's
     * stored on the network, then the player's inventory, then made by the network's machines; with {@code max},
     * as many crafts' worth as every slot can have, up to a stack.
     */
    public void fill(Player player, List<List<ItemStack>> options, boolean max) {
        if (terminal == null) return;
        clearGrid(player);
        ItemStack[] chosen = new ItemStack[9];
        for (int i = 0; i < Math.min(9, options.size()); i++) chosen[i] = pick(player, options.get(i));
        int target = 1;
        if (max) {
            // As many crafts as the scarcest item allows, shared among the slots it fills, up to a stack.
            target = 64;
            var controller = terminal.controller();
            var offers = controller == null ? null : controller.offers();
            java.util.Map<ItemResource, Integer> slotsFor = new java.util.HashMap<>();
            for (ItemStack pick : chosen) if (pick != null) slotsFor.merge(ItemResource.of(pick), 1, Integer::sum);
            for (ItemStack pick : chosen) {
                if (pick == null) continue;
                ItemResource item = ItemResource.of(pick);
                long have = (offers == null ? 0 : offers.real(item) + offers.craftable(item)) + inventoryCount(player, pick);
                target = (int) Math.min(target, Math.min(pick.getMaxStackSize(), have / slotsFor.get(item)));
            }
            target = Math.max(1, target);
        }
        int crafts = target;
        for (int i = 0; i < 9; i++) {
            if (chosen[i] == null) continue;
            ItemStack got = obtain(player, chosen[i], target);
            craftGrid.setItem(i, got);
            crafts = Math.min(crafts, got.getCount());
        }
        // As many crafts as every slot could have: the rest goes back.
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftGrid.getItem(i);
            if (stack.getCount() <= crafts || crafts <= 0) continue;
            ItemStack extra = stack.split(stack.getCount() - crafts);
            ItemStack left = terminal.insert(extra);
            if (!left.isEmpty() && !player.getInventory().add(left)) player.drop(left, false);
            craftGrid.setItem(i, stack);
        }
        slotsChanged(craftGrid);
        refresh();
    }

    /** Which of {@code options} to fill a slot with: one stored on the network, else in the inventory, else one the network can make. */
    @Nullable
    private ItemStack pick(Player player, List<ItemStack> options) {
        if (options.isEmpty() || terminal == null) return null;
        var controller = terminal.controller();
        var offers = controller == null ? null : controller.offers();
        for (ItemStack option : options) {
            if (!option.isEmpty() && offers != null && offers.real(ItemResource.of(option)) > 0) return option;
        }
        for (ItemStack option : options) {
            if (!option.isEmpty() && inventoryCount(player, option) > 0) return option;
        }
        for (ItemStack option : options) {
            if (!option.isEmpty() && offers != null && offers.craftable(ItemResource.of(option)) > 0) return option;
        }
        return options.getFirst().isEmpty() ? null : options.getFirst();
    }

    private static int inventoryCount(Player player, ItemStack like) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, like)) count += stack.getCount();
        }
        return count;
    }

    /** Up to {@code count} of {@code like}: stored on the network, then from the inventory, then made. */
    private ItemStack obtain(Player player, ItemStack like, int count) {
        ItemResource resource = ItemResource.of(like);
        ItemStack got = terminal.takeStored(resource, count);
        if (got.isEmpty()) got = ItemStack.EMPTY;
        var inventory = player.getInventory();
        for (int i = 0; i < INVENTORY_SLOTS && got.getCount() < count; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, like)) continue;
            ItemStack part = stack.split(Math.min(stack.getCount(), count - got.getCount()));
            if (got.isEmpty()) got = part;
            else got.grow(part.getCount());
            inventory.setChanged();
        }
        if (got.getCount() < count) {
            ItemStack made = terminal.take(resource, count - got.getCount());
            if (got.isEmpty()) got = made;
            else got.grow(made.getCount());
        }
        return got;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearGrid(player);
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

    /**
     * Shift-clicking a stack in the inventory or the crafting grid puts it into the network; shift-clicking the
     * result crafts into the inventory, again and again while the network refills the grid and there's room.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (index == RESULT_SLOT) {
            ItemStack made = slot.getItem();
            ItemStack copy = made.copy();
            if (!moveItemStackTo(made, 0, INVENTORY_SLOTS, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(made, copy);
            if (made.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (made.getCount() == copy.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, copy);
            if (!made.isEmpty()) player.drop(made, false);
            return copy;
        }
        if (player.level().isClientSide() || terminal == null) return ItemStack.EMPTY;
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
