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

    private final TerminalCraftGrid craftGrid;
    private final net.minecraft.world.inventory.ResultContainer craftResult = new net.minecraft.world.inventory.ResultContainer();
    /** On the client: what the server last said each grid slot will have made for it (the server's are the terminal's). */
    private final ItemStack[] clientGhosts = new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    /** Grid slots holding what was made for them just now, for a craft: not something a player put there. */
    private final boolean[] madeFor = new boolean[9];
    @Nullable
    private List<ItemStack> sentGhosts;
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
        this.craftGrid = new TerminalCraftGrid(terminal == null ? net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY) : terminal.craftItems(),
                this::gridChanged);
        if (terminal != null) terminal.watch(this, true);
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

            /** On the server, what's to be made for the craft is made first: if it can't be, the craft isn't given. */
            @Override
            public boolean mayPickup(Player taker) {
                return (taker.level().isClientSide() || makeGhosts()) && super.mayPickup(taker);
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                ItemStack[] before = new ItemStack[9];
                for (int i = 0; i < 9; i++) before[i] = craftGrid.getItem(i).copy();
                super.onTake(taker, stack);
                refill(before);
            }
        });
        if (terminal != null) recompute();
    }

    /** The crafting grid's slots, in order (for recipe viewers). */
    public List<Slot> gridSlots() {
        return slots.subList(GRID_FIRST, GRID_FIRST + 9);
    }

    /** What grid slot {@code i} will have made for it when a craft is taken (EMPTY: nothing). */
    public ItemStack ghost(int i) {
        return terminal != null ? terminal.craftGhosts()[i] : clientGhosts[i];
    }

    private void setGhost(int i, ItemStack ghost) {
        if (terminal != null) terminal.craftGhosts()[i] = ghost;
        else clientGhosts[i] = ghost;
    }

    /** What the server says the grid's slots will have made for them. Client side. */
    public void setGhosts(List<ItemStack> ghosts) {
        for (int i = 0; i < 9; i++) clientGhosts[i] = i < ghosts.size() ? ghosts.get(i) : ItemStack.EMPTY;
    }

    private void gridChanged() {
        if (terminal == null) return;
        terminal.craftGridChanged(this);
        recompute();
    }

    /** Another player at the same terminal changed its grid: the result is worked out again here too. */
    public void gridChangedElsewhere() {
        recompute();
    }

    /**
     * Makes what the grid's slots are to have made for them and are empty of (one each), stored first: whether
     * all were. Those that can't be had any more are let go, and the result worked out again.
     */
    private boolean makeGhosts() {
        if (terminal == null) return true;
        boolean all = true;
        for (int i = 0; i < 9; i++) {
            ItemStack ghost = ghost(i);
            if (ghost.isEmpty() || !craftGrid.getItem(i).isEmpty()) continue;
            ItemStack got = terminal.take(ItemResource.of(ghost), 1);
            if (got.isEmpty()) {
                all = false;
                continue;
            }
            madeFor[i] = true;
            craftGrid.setItem(i, got);
        }
        if (!all) {
            checkGhosts();
            recompute();
        }
        return all;
    }

    /** Whether the network can make {@code item}. */
    private boolean craftable(ItemStack item) {
        var controller = terminal == null ? null : terminal.controller();
        return controller != null && controller.offers().craftable(ItemResource.of(item)) > 0;
    }

    /** Lets go of what the grid's empty slots were to have made for them that the network neither has nor can make. */
    private void checkGhosts() {
        if (terminal == null) return;
        var controller = terminal.controller();
        var offers = controller == null ? null : controller.offers();
        boolean changed = false;
        for (int i = 0; i < 9; i++) {
            ItemStack ghost = ghost(i);
            if (ghost.isEmpty() || !craftGrid.getItem(i).isEmpty()) continue;
            ItemResource item = ItemResource.of(ghost);
            if (offers != null && offers.real(item) + offers.craftable(item) > 0) continue;
            setGhost(i, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) gridChanged();
    }

    /**
     * After a craft: each grid slot that had something used up is topped up to what it held, from what's stored on
     * the network (one craft's worth of the same thing, as the grid had it); what's left in it is kept. A slot that
     * has something made for it each craft is left empty again, for the next; one whose item has run out has it made
     * for it from now on, if the network can.
     */
    private void refill(ItemStack[] before) {
        if (terminal == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack had = before[i];
            ItemStack now = craftGrid.getItem(i);
            if (now.isEmpty()) madeFor[i] = false;
            if (had.isEmpty() || !ghost(i).isEmpty()) continue;
            if (!now.isEmpty() && !ItemStack.isSameItemSameComponents(now, had)) continue;
            int want = had.getCount() - now.getCount();
            if (want <= 0) continue;
            ItemStack got = terminal.takeStored(ItemResource.of(had), want);
            if (got.isEmpty()) {
                if (now.isEmpty() && craftable(had)) setGhost(i, had.copyWithCount(1));
                continue;
            }
            if (now.isEmpty()) craftGrid.setItem(i, got);
            else {
                now.grow(got.getCount());
                craftGrid.setItem(i, now);
            }
        }
        recompute();
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        if (container == craftGrid) recompute();
    }

    /** What the grid makes, as if what its empty slots are to have made for them were there. Server side. */
    private void recompute() {
        if (player == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ItemStack real = craftGrid.getItem(i);
            items.add(real.isEmpty() && !ghost(i).isEmpty() ? ghost(i).copyWithCount(1) : real);
        }
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
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
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player clicker) {
        int i = slotId - GRID_FIRST;
        // A click with nothing in hand on an empty slot that's to have something made for it lets that go.
        if (i >= 0 && i < 9 && input == net.minecraft.world.inventory.ContainerInput.PICKUP && getCarried().isEmpty()
                && craftGrid.getItem(i).isEmpty() && !ghost(i).isEmpty()) {
            setGhost(i, ItemStack.EMPTY);
            gridChanged();
            return;
        }
        super.clicked(slotId, button, input, clicker);
        // Something a player put in a slot takes the place of what was to be made for it.
        boolean changed = false;
        for (int g = 0; g < 9; g++) {
            if (craftGrid.getItem(g).isEmpty()) madeFor[g] = false;
            else if (!madeFor[g] && !ghost(g).isEmpty()) {
                setGhost(g, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) gridChanged();
    }

    /** A double-click gathering a stack doesn't take from the result or the grid. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != craftResult && slot.container != craftGrid && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_CLEAR) {
            clearGrid(player);
            return true;
        }
        return false;
    }

    /** Puts what's in the crafting grid back into the network (what it won't take goes to the player), and lets go of what was to be made. */
    private void clearGrid(Player player) {
        for (int i = 0; i < 9; i++) {
            setGhost(i, ItemStack.EMPTY);
            madeFor[i] = false;
            ItemStack stack = craftGrid.getItem(i);
            if (stack.isEmpty()) continue;
            ItemStack left = terminal == null ? stack : terminal.insert(stack);
            if (!left.isEmpty() && !player.getInventory().add(left)) player.drop(left, false);
            craftGrid.setItem(i, ItemStack.EMPTY);
        }
        gridChanged();
        refresh();
    }

    /**
     * Sets grid slot {@code i}, if it's empty, to have {@code item} made for it when a craft is taken: what a player
     * dragged there from the item grid. Only what the network has or can make.
     */
    public void placeGhost(int i, ItemStack item) {
        if (terminal == null || i < 0 || i >= 9 || item.isEmpty() || !craftGrid.getItem(i).isEmpty()) return;
        var controller = terminal.controller();
        ItemResource resource = ItemResource.of(item);
        if (controller == null || controller.offers().real(resource) + controller.offers().craftable(resource) <= 0) return;
        setGhost(i, item.copyWithCount(1));
        gridChanged();
    }

    /**
     * Fills the crafting grid for a recipe a recipe viewer chose: {@code options} per grid slot (none for an empty
     * one), the first of each that can be had. What's in the grid goes back first. A slot is filled from what's
     * stored on the network, then the player's inventory; what's only to be made is made when the craft is taken
     * (shown as "craft"). One craft's worth, max or not, as an ME terminal fills it: the grid is refilled from the
     * network after each craft anyway.
     */
    public void fill(Player player, List<List<ItemStack>> options, boolean max) {
        if (terminal == null) return;
        clearGrid(player);
        var controller = terminal.controller();
        var offers = controller == null ? null : controller.offers();
        ItemStack[] chosen = new ItemStack[9];
        boolean[] made = new boolean[9];
        for (int i = 0; i < Math.min(9, options.size()); i++) {
            chosen[i] = pick(player, options.get(i), true);
            if (chosen[i] == null) {
                chosen[i] = pick(player, options.get(i), false);
                made[i] = chosen[i] != null;
            }
        }
        int target = 1;
        int crafts = target;
        for (int i = 0; i < 9; i++) {
            if (chosen[i] == null) continue;
            if (made[i]) {
                setGhost(i, chosen[i].copyWithCount(1));
                continue;
            }
            ItemStack got = obtain(player, chosen[i], target);
            if (got.isEmpty()) {
                // Gone since it was looked at: made when the craft is taken instead, if it can be.
                setGhost(i, chosen[i].copyWithCount(1));
                continue;
            }
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
        checkGhosts();
        gridChanged();
        refresh();
    }

    /**
     * Which of {@code options} to fill a slot with: with {@code had}, one stored on the network, else in the
     * inventory; without, one the network can make. Null for none.
     */
    @Nullable
    private ItemStack pick(Player player, List<ItemStack> options, boolean had) {
        if (options.isEmpty() || terminal == null) return null;
        var controller = terminal.controller();
        var offers = controller == null ? null : controller.offers();
        if (had) {
            for (ItemStack option : options) {
                if (!option.isEmpty() && offers != null && offers.real(ItemResource.of(option)) > 0) return option;
            }
            for (ItemStack option : options) {
                if (!option.isEmpty() && inventoryCount(player, option) > 0) return option;
            }
            return null;
        }
        for (ItemStack option : options) {
            if (!option.isEmpty() && offers != null && offers.craftable(ItemResource.of(option)) > 0) return option;
        }
        return null;
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

    /** Up to {@code count} of {@code like}: stored on the network, then from the inventory. Nothing is made. */
    private ItemStack obtain(Player player, ItemStack like, int count) {
        ItemStack got = terminal.takeStored(ItemResource.of(like), count);
        var inventory = player.getInventory();
        for (int i = 0; i < INVENTORY_SLOTS && got.getCount() < count; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!ItemStack.isSameItemSameComponents(stack, like)) continue;
            ItemStack part = stack.split(Math.min(stack.getCount(), count - got.getCount()));
            if (got.isEmpty()) got = part;
            else got.grow(part.getCount());
            inventory.setChanged();
        }
        return got;
    }

    /** The grid stays in the terminal for next time. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (terminal != null) terminal.watch(this, false);
    }

    /** Where the terminal is: what the screen's clicks name. */
    public net.minecraft.core.BlockPos pos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server) || terminal == null) return;
        List<ItemStack> ghosts = List.of(terminal.craftGhosts());
        if (sentGhosts == null || !ItemStack.listMatches(ghosts, sentGhosts)) {
            sentGhosts = ghosts.stream().map(ItemStack::copy).toList();
            PacketDistributor.sendToPlayer(server, new com.kadikular.quantimium.network.LogisticsTerminalGhostsPayload(containerId, sentGhosts));
        }
        if (sinceView-- > 0) return;
        sinceView = VIEW_TICKS;
        checkGhosts();
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
            // A shift-click crafts again and again without asking the slot each time: what's to be made, made here.
            if (!player.level().isClientSide() && !makeGhosts()) return ItemStack.EMPTY;
            if (!slot.hasItem()) return ItemStack.EMPTY;
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
