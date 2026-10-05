package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.HorizonCoreMenu;
import com.kadikular.quantimium.network.HorizonRequestPayload;
import com.kadikular.quantimium.network.HorizonViewPayload;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Horizon Core: the horizon's load, then one grid, like an ME terminal, of everything it holds or
 * could make from what it holds, with how many of each could be had (an estimate: see
 * {@link com.kadikular.quantimium.reactor.ReactorCounter}). Sorted by count or by name, filtered by the
 * search box. Click an item for one, shift-click for a stack, sent to the Output ports: from what's
 * held if it's there, made through the catalysts if not. The last result shows along the bottom.
 * Layout: tools/gui_panels.py horizon_core.
 */
public class HorizonCoreScreen extends QuantumMachineScreen<HorizonCoreMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/horizon_core.png");
    private static final int LOAD_X = 16;
    private static final int LOAD_Y = 18;
    private static final int LOAD_W = 152;
    private static final int LOAD_H = 3;
    private static final int INFO_Y = 24;
    private static final int TAB_Y = 35;
    private static final int SORT_X = 16;
    private static final int SORT_W = 74;
    private static final int TAB_H = 12;
    private static final int SEARCH_X = 95;
    private static final int SEARCH_Y = 37;
    private static final int SEARCH_W = 72;
    private static final int GRID_X = 16;
    private static final int GRID_Y = 51;
    private static final int COLUMNS = 8;
    private static final int ROWS = 6;
    private static final int CELL = 18;
    private static final int MESSAGE_Y = 163;
    private static final int LOAD_FILL = 0xFFB196FF;

    /** The last view the server sent, for whichever core is open. */
    private static long mass;
    private static long capacity;
    private static int rings;
    private static boolean active;
    private static List<HorizonViewPayload.Entry> entries = List.of();
    private static Component message = Component.empty();
    /** Remembered while the game runs, like a terminal's sort. */
    private static boolean byName;
    private EditBox search;
    private int scrollRow;

    public HorizonCoreScreen(HorizonCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 176);
        entries = List.of();
        message = Component.empty();
    }

    /** From the server: the open core's load, and its lists when they changed. */
    public static void setView(HorizonViewPayload payload) {
        mass = payload.mass();
        capacity = payload.capacity();
        rings = payload.rings();
        active = payload.active();
        payload.entries().ifPresent(list -> entries = list);
        payload.message().ifPresent(text -> message = text);
    }

    @Override
    protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, 9,
                Component.translatable("gui.quantimium.horizon_core.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(textColour());
        search.setHint(Component.translatable("gui.quantimium.horizon_core.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> scrollRow = 0);
        addRenderableWidget(search);
    }

    /** The sorted, filtered list, cached until the entries, sort or search change. */
    private List<HorizonViewPayload.Entry> shownFor = null;
    private String shownQuery = null;
    private boolean shownByName;
    private List<HorizonViewPayload.Entry> shown = List.of();

    private List<HorizonViewPayload.Entry> shown() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (entries == shownFor && query.equals(shownQuery) && byName == shownByName) return shown;
        List<HorizonViewPayload.Entry> cells = new ArrayList<>();
        for (HorizonViewPayload.Entry entry : entries) {
            if (matches(entry.item(), query)) cells.add(entry);
        }
        Comparator<HorizonViewPayload.Entry> name = Comparator.comparing(e -> e.item().toStack(1).getHoverName().getString());
        cells.sort(byName ? name : Comparator.comparingLong(HorizonViewPayload.Entry::total).reversed().thenComparing(name));
        shownFor = entries;
        shownQuery = query;
        shownByName = byName;
        shown = cells;
        return cells;
    }

    private static boolean matches(ItemResource item, String query) {
        return query.isEmpty() || item.toStack(1).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private int maxScroll(List<HorizonViewPayload.Entry> shown) {
        return Math.max(0, Mth.positiveCeilDiv(shown.size(), COLUMNS) - ROWS);
    }

    @Nullable
    private HorizonViewPayload.Entry cellAt(List<HorizonViewPayload.Entry> shown, double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) return null;
        int index = (row + scrollRow) * COLUMNS + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int filled = capacity <= 0 ? 0 : (int) Math.min(LOAD_W, LOAD_W * mass / capacity);
        if (filled > 0) {
            graphics.fill(leftPos + LOAD_X, topPos + LOAD_Y, leftPos + LOAD_X + filled, topPos + LOAD_Y + LOAD_H, LOAD_FILL);
        }
        graphics.text(font, Component.translatable("gui.quantimium.horizon_core.info", rings,
                        compact(mass), compact(capacity)),
                leftPos + LOAD_X, topPos + INFO_Y, dimColour(), false);

        sortButton(graphics, mouseX, mouseY);

        List<HorizonViewPayload.Entry> shown = shown();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(shown));
        HorizonViewPayload.Entry hovered = cellAt(shown, mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= shown.size()) break;
                HorizonViewPayload.Entry cell = shown.get(index);
                int x = leftPos + GRID_X + col * CELL;
                int y = topPos + GRID_Y + row * CELL;
                if (cell == hovered) graphics.fill(x, y, x + CELL - 2, y + CELL - 2, QuantumUiColours.ROW_HOVER);
                ItemStack stack = cell.item().toStack(1);
                graphics.item(stack, x, y);
                if (cell.total() > 1) {
                    // Half size in the slot's corner, as an ME terminal draws its counts.
                    String count = slotCount(cell.total());
                    graphics.nextStratum();
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 16, y + 11);
                    graphics.pose().scale(0.5f, 0.5f);
                    graphics.text(font, count, -font.width(count), 0, 0xFFFFFFFF, true);
                    graphics.pose().popMatrix();
                }
            }
        }

        String line = font.plainSubstrByWidth(message.getString(), LOAD_W);
        graphics.text(font, line, leftPos + LOAD_X, topPos + MESSAGE_Y, dimColour(), false);
    }

    private void sortButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = leftPos + SORT_X;
        int top = topPos + TAB_Y;
        boolean hovered = QuantumWidgets.hit(mouseX, mouseY, left, top, SORT_W, TAB_H);
        graphics.fill(left, top, left + SORT_W, top + TAB_H, hovered ? 0x66303650 : 0x33303650);
        graphics.centeredText(font, Component.translatable(byName ? "gui.quantimium.horizon_core.sort.name"
                : "gui.quantimium.horizon_core.sort.count"), left + SORT_W / 2, top + 2, textColour());
    }

    /** At most four characters, as AE2 writes them: 999, 1.2k, 12k, 120k, 1.2M, 12M, 1.2G. */
    static String slotCount(long value) {
        if (value < 1000) return Long.toString(value);
        String[] units = {"k", "M", "G", "T", "P"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1000 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        // Rounded down, so a count never shows more than there is.
        String number = scaled < 10 ? String.format(Locale.ROOT, "%.1f", Math.floor(scaled * 10) / 10)
                : Long.toString((long) Math.floor(scaled));
        if (number.endsWith(".0")) number = number.substring(0, number.length() - 2);
        return number + units[unit];
    }

    /** 1,234 up to 99,999, then 120k, 3.4M. */
    private static String compact(long value) {
        if (value < 100_000) return String.format(Locale.ROOT, "%,d", value);
        if (value < 10_000_000) return (value / 1000) + "k";
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        HorizonViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
        if (cell != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(cell.item().toStack(1).getHoverName());
            if (cell.held() > 0) {
                lines.add(Component.translatable("gui.quantimium.horizon_core.held",
                        String.format(Locale.ROOT, "%,d", cell.held())).withStyle(ChatFormatting.GRAY));
            }
            if (cell.total() > cell.held()) {
                lines.add(Component.translatable("gui.quantimium.horizon_core.makeable",
                        String.format(Locale.ROOT, "%,d", cell.total() - cell.held())).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            lines.add(Component.translatable("gui.quantimium.horizon_core.take.tip").withStyle(ChatFormatting.DARK_GRAY));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (QuantumWidgets.hit(mouseX, mouseY, leftPos + LOAD_X, topPos + MESSAGE_Y - 1, LOAD_W, 10)
                && !message.getString().isEmpty()) {
            graphics.setTooltipForNextFrame(font, font.split(message, 200), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (activeOverlay() == null && event.button() == 0) {
            if (QuantumWidgets.hit(mouseX, mouseY, leftPos + SORT_X, topPos + TAB_Y, SORT_W, TAB_H)) {
                byName = !byName;
                scrollRow = 0;
                click();
                return true;
            }
            HorizonViewPayload.Entry cell = cellAt(shown(), mouseX, mouseY);
            if (cell != null) {
                int count = event.hasShiftDown() ? cell.item().toStack(1).getMaxStackSize() : 1;
                ClientPacketDistributor.sendToServer(new HorizonRequestPayload(menu.getBlockEntity().getBlockPos(),
                        cell.item(), count));
                click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeOverlay() == null && QuantumWidgets.hit(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y,
                COLUMNS * CELL, ROWS * CELL)) {
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
    protected int energyStored() {
        return menu.getEnergyStored();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.getMaxEnergyStored();
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(active ? "gui.quantimium.horizon_core.status.active"
                : rings > 0 ? "gui.quantimium.horizon_core.status.unpowered" : "gui.quantimium.horizon_core.status.unformed");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(active ? "gui.quantimium.horizon_core.status.active.tip"
                : rings > 0 ? "gui.quantimium.horizon_core.status.unpowered.tip"
                : "gui.quantimium.horizon_core.status.unformed.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return active ? PanelTheme.Tone.OK : rings > 0 ? PanelTheme.Tone.WARN : PanelTheme.Tone.BAD;
    }
}
