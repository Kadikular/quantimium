package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
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
 * offer, sorted by count or name and filtered by the search box. Click an item for a stack on the cursor, right-click
 * for one, shift-click for a stack into the inventory. Click the grid holding something to put it away (right-click:
 * one); shift-click in the inventory does too. Layout: tools/gui_panels.py logistics_terminal.
 */
public class LogisticsTerminalScreen extends QuantumMachineScreen<LogisticsTerminalMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/logistics_terminal.png");
    private static final int SORT_X = 8;
    private static final int SORT_Y = 18;
    private static final int SORT_W = 72;
    private static final int SORT_H = 12;
    private static final int SEARCH_X = 95;
    private static final int SEARCH_Y = 20;
    private static final int SEARCH_W = 72;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 34;
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 18;

    /** The last view the server sent. */
    private static List<LogisticsTerminalViewPayload.Entry> entries = List.of();
    private static boolean connected;
    /** Remembered while the game runs, like a terminal's sort. */
    private static boolean byName;
    private EditBox search;
    private int scrollRow;

    public LogisticsTerminalScreen(LogisticsTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 222);
        entries = List.of();
        connected = false;
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
    private List<LogisticsTerminalViewPayload.Entry> shown = List.of();

    private List<LogisticsTerminalViewPayload.Entry> shown() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (entries == shownFor && query.equals(shownQuery) && byName == shownByName) return shown;
        List<LogisticsTerminalViewPayload.Entry> cells = new ArrayList<>();
        for (LogisticsTerminalViewPayload.Entry entry : entries) {
            if (query.isEmpty() || entry.item().toStack(1).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) cells.add(entry);
        }
        Comparator<LogisticsTerminalViewPayload.Entry> name = Comparator.comparing(e -> e.item().toStack(1).getHoverName().getString());
        cells.sort(byName ? name : Comparator.comparingLong(LogisticsTerminalViewPayload.Entry::count).reversed().thenComparing(name));
        shownFor = entries;
        shownQuery = query;
        shownByName = byName;
        shown = cells;
        return cells;
    }

    private int maxScroll(List<LogisticsTerminalViewPayload.Entry> shown) {
        return Math.max(0, Mth.positiveCeilDiv(shown.size(), COLUMNS) - ROWS);
    }

    private boolean onGrid(double mouseX, double mouseY) {
        return QuantumWidgets.hit(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, COLUMNS * CELL, ROWS * CELL);
    }

    @Nullable
    private LogisticsTerminalViewPayload.Entry cellAt(List<LogisticsTerminalViewPayload.Entry> shown, double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) return null;
        int index = (row + scrollRow) * COLUMNS + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos + SORT_X;
        int top = topPos + SORT_Y;
        boolean sortHovered = QuantumWidgets.hit(mouseX, mouseY, left, top, SORT_W, SORT_H);
        graphics.fill(left, top, left + SORT_W, top + SORT_H, sortHovered ? 0x66303650 : 0x33303650);
        graphics.centeredText(font, Component.translatable(byName ? "gui.quantimium.horizon_core.sort.name" : "gui.quantimium.horizon_core.sort.count"),
                left + SORT_W / 2, top + 2, textColour());

        List<LogisticsTerminalViewPayload.Entry> shown = shown();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(shown));
        LogisticsTerminalViewPayload.Entry hovered = cellAt(shown, mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= shown.size()) break;
                LogisticsTerminalViewPayload.Entry cell = shown.get(index);
                int x = leftPos + GRID_X + col * CELL;
                int y = topPos + GRID_Y + row * CELL;
                if (cell == hovered) graphics.fill(x, y, x + CELL - 2, y + CELL - 2, QuantumUiColours.ROW_HOVER);
                graphics.item(cell.item().toStack(1), x, y);
                if (cell.count() > 1) {
                    String count = HorizonCoreScreen.slotCount(cell.count());
                    graphics.nextStratum();
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 16, y + 11);
                    graphics.pose().scale(0.5f, 0.5f);
                    graphics.text(font, count, -font.width(count), 0, 0xFFFFFFFF, true);
                    graphics.pose().popMatrix();
                }
            }
        }
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) return;
        LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
        if (cell == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(cell.item().toStack(1).getHoverName());
        lines.add(Component.translatable("gui.quantimium.logistics_terminal.count", String.format(Locale.ROOT, "%,d", cell.count())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.quantimium.logistics_terminal.take.tip").withStyle(ChatFormatting.DARK_GRAY));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (activeOverlay() == null && event.button() == 0 && QuantumWidgets.hit(mouseX, mouseY, leftPos + SORT_X, topPos + SORT_Y, SORT_W, SORT_H)) {
            byName = !byName;
            scrollRow = 0;
            click();
            return true;
        }
        if (activeOverlay() == null && onGrid(mouseX, mouseY) && (event.button() == 0 || event.button() == 1)) {
            var pos = menu.getBlockEntity().getBlockPos();
            if (!menu.getCarried().isEmpty()) {
                ClientPacketDistributor.sendToServer(new LogisticsTerminalInsertPayload(pos, event.button() == 1));
                return true;
            }
            LogisticsTerminalViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
            if (cell != null) {
                int stack = cell.item().toStack(1).getMaxStackSize();
                int count = event.button() == 1 ? 1 : stack;
                ClientPacketDistributor.sendToServer(new LogisticsTerminalTakePayload(pos, cell.item(), count, event.button() == 0 && event.hasShiftDown()));
                click();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
        return TEXTURE;
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
