package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.ItemSearch;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.LogisticsTerminalMenu;
import com.kadikular.quantimium.network.LogisticsTerminalInsertPayload;
import com.kadikular.quantimium.network.LogisticsTerminalTakePayload;
import com.kadikular.quantimium.network.LogisticsTerminalViewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A Logistics Terminal: a grid, like an ME terminal's, of everything its network's storage holds and its providers
 * offer, sorted by count or name and filtered by the search box (read as JEI reads its own: {@link ItemSearch}).
 * Click an item for a stack on the cursor, right-click for one, shift-click for a stack into the inventory. Click the
 * grid holding something to put it away (right-click: one); shift-click in the inventory does too. A tab shows a
 * crafting grid under a shorter item grid, as an ME crafting terminal has. Layout: tools/gui_panels.py
 * logistics_terminal and logistics_terminal_crafting.
 */
public class LogisticsTerminalScreen extends QuantumMachineScreen<LogisticsTerminalMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/logistics_terminal.png");
    private static final Identifier CRAFTING_TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/logistics_terminal_crafting.png");
    /** The clear button, right of the crafting grid (tools/gui_panels.py logistics_terminal_crafting). */
    private static final int CLEAR_X = 100;
    private static final int CLEAR_Y = 96;
    private static final int CLEAR_SIZE = 9;
    private static final int SEARCH_X = 9;
    private static final int SEARCH_Y = 20;
    private static final int SEARCH_W = 158;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 34;
    private static final int COLUMNS = 9;
    /** Rows of items: fewer while the crafting grid is shown under them. */
    private static final int ROWS = 6;
    private static final int CRAFTING_ROWS = 3;
    private static final int CELL = 18;
    /** The scroll bar's track, in the gap right of the grid (tools/gui_panels.py logistics_terminal), for each height. */
    private final com.kadikular.quantimium.client.screen.ui.ScrollBar tallBar =
            new com.kadikular.quantimium.client.screen.ui.ScrollBar(GRID_X + COLUMNS * CELL + 1, GRID_Y, 3, ROWS * CELL - 2);
    private final com.kadikular.quantimium.client.screen.ui.ScrollBar shortBar =
            new com.kadikular.quantimium.client.screen.ui.ScrollBar(GRID_X + COLUMNS * CELL + 1, GRID_Y, 3, CRAFTING_ROWS * CELL - 2);

    private static int rows() {
        return LogisticsTerminalMenu.craftingShown() ? CRAFTING_ROWS : ROWS;
    }

    private com.kadikular.quantimium.client.screen.ui.ScrollBar bar() {
        return LogisticsTerminalMenu.craftingShown() ? shortBar : tallBar;
    }

    /** The last view the server sent. */
    private static List<LogisticsTerminalViewPayload.Entry> entries = List.of();
    private static boolean connected;
    /** Kept between games (config/quantimium-client.toml), with the view and whether the crafting grid is shown. */
    private static boolean byName = com.kadikular.quantimium.ClientConfig.get(com.kadikular.quantimium.ClientConfig.TERMINAL_BY_NAME, false);

    /** What the grid counts: everything as one number, there and craftable apart, only what's there, or only what can be made. */
    private enum View {
        ALL, SPLIT, REAL, CRAFT;

        View next() {
            return values()[(ordinal() + 1) % values().length];
        }

        View previous() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }

        GuiIcon icon() {
            return switch (this) {
                case ALL -> GuiIcon.VIEW_ALL;
                case SPLIT -> GuiIcon.VIEW_BOTH;
                case REAL -> GuiIcon.VIEW_REAL;
                case CRAFT -> GuiIcon.VIEW_CRAFT;
            };
        }

        String key() {
            return "gui.quantimium.logistics_terminal.view." + name().toLowerCase(Locale.ROOT);
        }

        boolean shows(LogisticsTerminalViewPayload.Entry entry) {
            return switch (this) {
                case ALL, SPLIT -> true;
                case REAL -> entry.count() > 0;
                case CRAFT -> entry.craftable() > 0;
            };
        }

        /** The number shown, and sorted on first: a fluid's in buckets, as a bucket is one item. */
        long count(LogisticsTerminalViewPayload.Entry entry) {
            long count = switch (this) {
                case ALL -> entry.count() + entry.craftable();
                case SPLIT, REAL -> entry.count();
                case CRAFT -> entry.craftable();
            };
            return entry.item().is(com.kadikular.quantimium.init.ModItems.FLUID_ENTRY.get()) ? count / net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME : count;
        }
    }

    private static View view = readView();

    private static View readView() {
        try {
            return View.valueOf(com.kadikular.quantimium.ClientConfig.get(com.kadikular.quantimium.ClientConfig.TERMINAL_VIEW, "SPLIT"));
        } catch (IllegalArgumentException unknown) {
            return View.SPLIT;
        }
    }

    /** Sort and view are kept between games. */
    private static void remember() {
        com.kadikular.quantimium.ClientConfig.set(com.kadikular.quantimium.ClientConfig.TERMINAL_BY_NAME, byName);
        com.kadikular.quantimium.ClientConfig.set(com.kadikular.quantimium.ClientConfig.TERMINAL_VIEW, view.name());
    }
    private EditBox search;
    private int scrollRow;

    public LogisticsTerminalScreen(LogisticsTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 240);
        entries = List.of();
        connected = false;
    }

    /** Whether the open terminal's network has {@code stack} stored (for recipe viewers). */
    public static boolean stored(ItemStack stack) {
        var item = net.neoforged.neoforge.transfer.item.ItemResource.of(stack);
        for (LogisticsTerminalViewPayload.Entry entry : entries) {
            if (entry.item().equals(item) && entry.count() > 0) return true;
        }
        return false;
    }

    /** Whether the open terminal's network lists {@code stack}, there or craftable (for recipe viewers' missing items). */
    public static boolean listed(ItemStack stack) {
        var item = net.neoforged.neoforge.transfer.item.ItemResource.of(stack);
        for (LogisticsTerminalViewPayload.Entry entry : entries) {
            if (entry.item().equals(item) && entry.count() + entry.craftable() > 0) return true;
        }
        return false;
    }

    public static void setView(LogisticsTerminalViewPayload payload) {
        entries = payload.entries();
        connected = payload.connected();
    }

    @Override
    protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, 9, Component.translatable("gui.quantimium.horizon_core.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(textColour());
        search.setHint(Component.translatable("gui.quantimium.horizon_core.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> scrollRow = 0);
        addRenderableWidget(search);
    }

    private List<LogisticsTerminalViewPayload.Entry> shownFor;
    private String shownQuery;
    private boolean shownByName;
    private View shownView;
    /** Whether the list was last kept in its order for shift: sorted again once shift is let go. */
    private boolean frozen;
    private List<LogisticsTerminalViewPayload.Entry> shown = List.of();

    private List<LogisticsTerminalViewPayload.Entry> shown() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        boolean shift = minecraft != null && minecraft.hasShiftDown();
        if (entries == shownFor && query.equals(shownQuery) && byName == shownByName && view == shownView && (shift || !frozen)) return shown;
        // While shift is held the order stands still, so taking one at a time doesn't move what's under the mouse;
        // the counts still change, and what's run out drops away.
        if (shift && shownFor != null && query.equals(shownQuery) && byName == shownByName && view == shownView) {
            java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, LogisticsTerminalViewPayload.Entry> latest = new java.util.HashMap<>();
            for (LogisticsTerminalViewPayload.Entry entry : entries) latest.put(entry.item(), entry);
            List<LogisticsTerminalViewPayload.Entry> kept = new ArrayList<>();
            for (LogisticsTerminalViewPayload.Entry was : shown) {
                LogisticsTerminalViewPayload.Entry now = latest.get(was.item());
                if (now != null && view.shows(now)) kept.add(now);
            }
            shownFor = entries;
            shown = kept;
            frozen = true;
            return kept;
        }
        List<LogisticsTerminalViewPayload.Entry> cells = new ArrayList<>();
        java.util.function.Predicate<ItemStack> matches = ItemSearch.compile(query);
        for (LogisticsTerminalViewPayload.Entry entry : entries) {
            if (!view.shows(entry)) continue;
            if (query.isEmpty() || matches.test(entry.item().toStack(1))) cells.add(entry);
        }
        Comparator<LogisticsTerminalViewPayload.Entry> name = Comparator.comparing(e -> e.item().toStack(1).getHoverName().getString());
        // By count: what the view counts first, then (there and craftable apart) what can be made.
        Comparator<LogisticsTerminalViewPayload.Entry> byCount = Comparator.comparingLong((LogisticsTerminalViewPayload.Entry e) -> view.count(e)).reversed()
                .thenComparing(Comparator.comparingLong(LogisticsTerminalViewPayload.Entry::craftable).reversed());
        cells.sort(byName ? name : byCount.thenComparing(name));
        shownFor = entries;
        shownQuery = query;
        shownByName = byName;
        shownView = view;
        frozen = false;
        shown = cells;
        return cells;
    }

    private int maxScroll(List<LogisticsTerminalViewPayload.Entry> shown) {
        return Math.max(0, Mth.positiveCeilDiv(shown.size(), COLUMNS) - rows());
    }

    private boolean onGrid(double mouseX, double mouseY) {
        return QuantumWidgets.hit(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, COLUMNS * CELL, rows() * CELL);
    }

    @Nullable
    private LogisticsTerminalViewPayload.Entry cellAt(List<LogisticsTerminalViewPayload.Entry> shown, double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= rows()) return null;
        int index = (row + scrollRow) * COLUMNS + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    @Nullable
    public Shown shownAt(double mouseX, double mouseY) {
        if (activeOverlay() != null) return null;
        LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
        if (cell == null) return null;
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        return new Shown(cell.item().toStack(1), new net.minecraft.client.renderer.Rect2i(leftPos + GRID_X + col * CELL, topPos + GRID_Y + row * CELL, 16, 16));
    }

    /** Sort and view on the left rail. */
    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.glyph(() -> byName ? GuiIcon.SORT_NAME : GuiIcon.SORT_COUNT, () -> {
                                    byName = !byName;
                                    remember();
                                    scrollRow = 0;
                                },
                                () -> List.of(Component.translatable(byName ? "gui.quantimium.logistics_terminal.sort.name" : "gui.quantimium.logistics_terminal.sort.count"),
                                        hint("gui.quantimium.tab.switch"))),
                Tab.glyph(() -> view.icon(), () -> {
                                    view = view.next();
                                    remember();
                                    scrollRow = 0;
                                },
                                () -> List.of(Component.translatable(view.key() + ".tip"), hint("gui.quantimium.tab.cycle")))
                        .onRightClick(() -> {
                            view = view.previous();
                            remember();
                            scrollRow = 0;
                        }),
                Tab.glyph(() -> GuiIcon.CRAFTING, () -> {
                                    // Hidden, the grid keeps what's in it (in the terminal) for when it's shown again.
                                    LogisticsTerminalMenu.showCrafting(!LogisticsTerminalMenu.craftingShown());
                                    scrollRow = 0;
                                },
                                () -> List.of(Component.translatable(LogisticsTerminalMenu.craftingShown()
                                                ? "gui.quantimium.logistics_terminal.crafting.on" : "gui.quantimium.logistics_terminal.crafting.off"),
                                        hint("gui.quantimium.tab.switch")))
                        .on(LogisticsTerminalMenu::craftingShown));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        List<LogisticsTerminalViewPayload.Entry> shown = shown();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(shown));
        bar().render(graphics, leftPos, topPos, scrollRow, rows(), Mth.positiveCeilDiv(shown.size(), COLUMNS), scrollColour());
        LogisticsTerminalViewPayload.Entry hovered = cellAt(shown, mouseX, mouseY);
        if (LogisticsTerminalMenu.craftingShown()) {
            int x = leftPos + CLEAR_X;
            int y = topPos + CLEAR_Y;
            boolean over = QuantumWidgets.hit(mouseX, mouseY, x, y, CLEAR_SIZE, CLEAR_SIZE);
            graphics.fill(x, y, x + CLEAR_SIZE, y + CLEAR_SIZE, over ? QuantumUiColours.ROW_HOVER : 0x00000000);
            graphics.text(font, "\u00d7", x + 2, y + 1, over ? 0xFFFFFFFF : dimColour(), false);
        }
        if (LogisticsTerminalMenu.craftingShown()) {
            // What a slot will have made for it when a craft is taken: the item, faded, marked "Craft".
            List<net.minecraft.world.inventory.Slot> grid = menu.gridSlots();
            for (int i = 0; i < grid.size(); i++) {
                ItemStack ghost = menu.ghost(i);
                var slot = grid.get(i);
                if (ghost.isEmpty() || slot.hasItem()) continue;
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                graphics.item(ghost, x, y);
                graphics.nextStratum();
                graphics.fill(x, y, x + 16, y + 16, 0x88101224);
                String craft = Component.translatable("gui.quantimium.logistics_terminal.craft").getString();
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + 16, y + 11);
                graphics.pose().scale(0.5f, 0.5f);
                graphics.text(font, craft, -font.width(craft), 0, 0xFFB49CFF, true);
                graphics.pose().popMatrix();
            }
        }
        for (int row = 0; row < rows(); row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= shown.size()) break;
                LogisticsTerminalViewPayload.Entry cell = shown.get(index);
                int x = leftPos + GRID_X + col * CELL;
                int y = topPos + GRID_Y + row * CELL;
                if (cell == hovered) graphics.fill(x, y, x + CELL - 2, y + CELL - 2, QuantumUiColours.ROW_HOVER);
                ItemStack shownStack = cell.item().toStack(1);
                net.neoforged.neoforge.fluids.FluidStack fluid = com.kadikular.quantimium.recipe.FluidEntry.fluidOf(shownStack);
                if (fluid.isEmpty()) graphics.item(shownStack, x, y);
                else com.kadikular.quantimium.client.screen.ui.FluidSprites.tile(graphics, fluid, x, y);
                if (!fluid.isEmpty()) {
                    // A fluid's amount, in buckets or mB, as the view counts it: what can be made too, in violet.
                    boolean makeOnly = view == View.SPLIT && cell.count() == 0 && cell.craftable() > 0;
                    long mb = switch (view) {
                        case ALL -> cell.count() + cell.craftable();
                        case SPLIT, REAL -> cell.count();
                        case CRAFT -> cell.craftable();
                    };
                    String amount = makeOnly ? Component.translatable("gui.quantimium.logistics_terminal.craft").getString()
                            : HorizonCoreScreen.fluidCount(mb);
                    boolean purple = makeOnly || view == View.CRAFT || view == View.ALL && cell.count() == 0;
                    graphics.nextStratum();
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 16, y + 11);
                    graphics.pose().scale(0.5f, 0.5f);
                    graphics.text(font, amount, -font.width(amount), 0, purple ? 0xFFB49CFF : 0xFFFFFFFF, true);
                    graphics.pose().popMatrix();
                    continue;
                }
                // How many the view counts; there and craftable apart, with none there, that it can be made, as an ME terminal says "Craft".
                boolean makeOnly = view == View.SPLIT && cell.count() == 0 && cell.craftable() > 0;
                long shownCount = view.count(cell);
                boolean purple = makeOnly || view == View.CRAFT;
                if (shownCount > 1 || makeOnly) {
                    String count = makeOnly ? Component.translatable("gui.quantimium.logistics_terminal.craft").getString()
                            : HorizonCoreScreen.slotCount(shownCount);
                    graphics.nextStratum();
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 16, y + 11);
                    graphics.pose().scale(0.5f, 0.5f);
                    graphics.text(font, count, -font.width(count), 0, purple ? 0xFFB49CFF : 0xFFFFFFFF, true);
                    graphics.pose().popMatrix();
                }
            }
        }
    }

    private boolean onClear(double mouseX, double mouseY) {
        return LogisticsTerminalMenu.craftingShown() && QuantumWidgets.hit(mouseX, mouseY, leftPos + CLEAR_X, topPos + CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE);
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) return;
        if (dragging != null) {
            graphics.nextStratum();
            graphics.item(dragging.item().toStack(1), mouseX - 8, mouseY - 8);
            return;
        }
        int ghostSlot = activeOverlay() == null ? gridSlotAt(mouseX, mouseY) : -1;
        if (ghostSlot >= 0 && !menu.ghost(ghostSlot).isEmpty() && !menu.gridSlots().get(ghostSlot).hasItem()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(menu.ghost(ghostSlot).getHoverName(),
                    Component.translatable("gui.quantimium.logistics_terminal.crafting.ghost").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("gui.quantimium.logistics_terminal.crafting.ghost.clear").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            return;
        }
        if (activeOverlay() == null && onClear(mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.quantimium.logistics_terminal.crafting.clear")), mouseX, mouseY);
            return;
        }
        LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
        if (cell == null) return;
        // The item's own tooltip, as in a chest (enchantments, a filter's settings, a mod's lines), then the counts.
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, cell.item().toStack(1)));
        if (com.kadikular.quantimium.recipe.FluidEntry.is(cell.item().toStack(1))) {
            lines.add(Component.translatable("gui.quantimium.logistics_terminal.fluid", String.format(Locale.ROOT, "%,d", cell.count())).withStyle(ChatFormatting.GRAY));
            if (cell.craftable() > 0) {
                lines.add(Component.translatable("gui.quantimium.logistics_terminal.craftable_fluid", String.format(Locale.ROOT, "%,d", cell.craftable()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            lines.add(Component.translatable("gui.quantimium.logistics_terminal.fluid.tip").withStyle(ChatFormatting.DARK_GRAY));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        lines.add(Component.translatable("gui.quantimium.logistics_terminal.count", String.format(Locale.ROOT, "%,d", cell.count())).withStyle(ChatFormatting.GRAY));
        if (cell.craftable() > 0) {
            lines.add(Component.translatable("gui.quantimium.logistics_terminal.craftable", String.format(Locale.ROOT, "%,d", cell.craftable()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.translatable("gui.quantimium.logistics_terminal.take.tip").withStyle(ChatFormatting.DARK_GRAY));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (activeOverlay() == null && event.button() == 0 && bar().over(mouseX, mouseY, leftPos, topPos)) {
            scrollRow = bar().press(mouseY, topPos, maxScroll(shown()));
            return true;
        }
        if (activeOverlay() == null && event.button() == 0 && onClear(mouseX, mouseY) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LogisticsTerminalMenu.BUTTON_CLEAR);
            return true;
        }
        if (activeOverlay() == null && onGrid(mouseX, mouseY) && event.button() == 2) {
            // Middle-click: make some and keep it in storage, at most what can be made.
            LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
            if (cell != null && cell.craftable() > 0) {
                boolean fluid = com.kadikular.quantimium.recipe.FluidEntry.is(cell.item().toStack(1));
                long most = fluid ? cell.craftable() / net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME : cell.craftable();
                if (most > 0) {
                    var pos = menu.pos();
                    var item = cell.item();
                    openOverlay(new com.kadikular.quantimium.client.screen.ui.MakePage(this::panelArea, rail(), item.toStack(1), most, fluid,
                            count -> ClientPacketDistributor.sendToServer(new com.kadikular.quantimium.network.LogisticsTerminalMakePayload(pos, item, count))));
                }
            }
            return true;
        }
        if (activeOverlay() == null && onGrid(mouseX, mouseY) && (event.button() == 0 || event.button() == 1)) {
            var pos = menu.pos();
            LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
            ItemStack carried = menu.getCarried();
            // A fluid: a click fills what's in hand, or with nothing in hand gives a bucket of it (shift: into the
            // inventory); a right-click holding a filled one pours it in (below).
            if (cell != null && (event.button() == 0 || carried.isEmpty()) && com.kadikular.quantimium.recipe.FluidEntry.is(cell.item().toStack(1))) {
                ClientPacketDistributor.sendToServer(new LogisticsTerminalTakePayload(pos, cell.item(), 1, carried.isEmpty() && event.hasShiftDown()));
                return true;
            }
            // Right-click: half a stack; with shift, one. On what's already in hand, that much more of it (made, if it must be).
            if (event.button() == 1 && cell != null && !carried.isEmpty()
                    && net.neoforged.neoforge.transfer.item.ItemResource.of(carried).equals(cell.item())) {
                ClientPacketDistributor.sendToServer(new LogisticsTerminalTakePayload(pos, cell.item(), rightClickCount(cell, event.hasShiftDown()), false));
                return true;
            }
            if (!carried.isEmpty()) {
                ClientPacketDistributor.sendToServer(new LogisticsTerminalInsertPayload(pos, event.button() == 1));
                return true;
            }
            // Something only the network can make, with the crafting grid shown: held till let go, so it can be
            // dragged onto the grid to be made there when a craft is taken; let go where it was, it's taken as ever.
            if (cell != null && event.button() == 0 && !event.hasShiftDown() && LogisticsTerminalMenu.craftingShown()
                    && cell.count() == 0 && cell.craftable() > 0) {
                dragging = cell;
                return true;
            }
            if (cell != null) {
                int stack = cell.item().toStack(1).getMaxStackSize();
                int count = event.button() == 1 ? rightClickCount(cell, event.hasShiftDown()) : stack;
                ClientPacketDistributor.sendToServer(new LogisticsTerminalTakePayload(pos, cell.item(), count, event.button() == 0 && event.hasShiftDown()));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Half a stack, or one with shift. */
    private static int rightClickCount(LogisticsTerminalViewPayload.Entry cell, boolean shift) {
        return shift ? 1 : Math.max(1, cell.item().toStack(1).getMaxStackSize() / 2);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (bar().dragging()) {
            scrollRow = bar().scrollAt(event.y(), topPos, maxScroll(shown()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /** Something only the network can make, held from the item grid to be let go on the crafting grid; null for none. */
    @Nullable
    private LogisticsTerminalViewPayload.Entry dragging;

    /** The crafting grid slot (0-8) under the mouse, while the grid is shown; -1 for none. */
    private int gridSlotAt(double mouseX, double mouseY) {
        if (!LogisticsTerminalMenu.craftingShown()) return -1;
        List<net.minecraft.world.inventory.Slot> grid = menu.gridSlots();
        for (int i = 0; i < grid.size(); i++) {
            var slot = grid.get(i);
            if (QuantumWidgets.hit(mouseX, mouseY, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (bar().dragging()) {
            bar().release();
            return true;
        }
        if (dragging != null) {
            LogisticsTerminalViewPayload.Entry held = dragging;
            dragging = null;
            int slot = gridSlotAt(event.x(), event.y());
            if (slot >= 0) {
                ClientPacketDistributor.sendToServer(new com.kadikular.quantimium.network.LogisticsTerminalGhostPayload(menu.pos(), slot, held.item().toStack(1)));
            } else if (cellAt(shown(), event.x(), event.y()) == held) {
                ClientPacketDistributor.sendToServer(new LogisticsTerminalTakePayload(menu.pos(), held.item(), held.item().toStack(1).getMaxStackSize(), false));
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Shift and the wheel over the grid: down, one more onto the cursor (as shift-right-click); up, one from the cursor back.
        if (activeOverlay() == null && onGrid(mouseX, mouseY) && minecraft != null && minecraft.hasShiftDown() && scrollY != 0) {
            ItemStack carried = menu.getCarried();
            if (scrollY > 0) {
                if (!carried.isEmpty()) ClientPacketDistributor.sendToServer(new LogisticsTerminalInsertPayload(menu.pos(), true));
            } else {
                LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
                if (cell != null) ClientPacketDistributor.sendToServer(new LogisticsTerminalTakePayload(menu.pos(), cell.item(), 1, false));
            }
            return true;
        }
        if (activeOverlay() == null && onGrid(mouseX, mouseY)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll(shown()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeOverlay() == null && search != null && search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(event) || search.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    protected Identifier panelTexture() {
        return LogisticsTerminalMenu.craftingShown() ? CRAFTING_TEXTURE : TEXTURE;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected boolean hasEnergy() {
        return false;
    }

    @Override
    protected int energyStored() {
        return 0;
    }

    @Override
    protected int maxEnergyStored() {
        return 0;
    }

    @Override
    protected Component statusLabel() {
        return connected ? Component.translatable("gui.quantimium.logistics_terminal.status", entries.size())
                : Component.translatable("gui.quantimium.logistics_terminal.status.unconnected");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.logistics_terminal.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return connected ? PanelTheme.Tone.OK : PanelTheme.Tone.WARN;
    }
}
