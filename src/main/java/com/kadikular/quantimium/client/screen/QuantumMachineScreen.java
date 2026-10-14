package com.kadikular.quantimium.client.screen;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import com.kadikular.quantimium.client.screen.ui.QuantumOverlay;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PriorityPage;
import com.kadikular.quantimium.client.screen.ui.Rail;
import com.kadikular.quantimium.client.screen.ui.SidesPage;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import com.kadikular.quantimium.util.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Common shell for Quantimium machine screens: the panel, the energy bar, the title strip with the
 * status, the rail of tabs attached to the right edge, and hosting for a modal overlay or a page.
 *
 * <p>Overlay input is routed in one place on purpose. Each screen used to forward its own subset of
 * events, which is how the crafter ended up closing its whole GUI when Escape was pressed inside a
 * config popup while the simulator handled it correctly.
 */
public abstract class QuantumMachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected static final int ENERGY_X = 6;
    protected static final int ENERGY_Y = 18;
    protected static final int ENERGY_WIDTH = 4;
    protected static final int ENERGY_HEIGHT = 52;
    /** Title and status share the strip above the panel's first rule (tools/gui_panels.py). */
    protected static final int TITLE_Y = 4;
    private static final int TITLE_X = 6;
    private static final int STATUS_RIGHT_MARGIN = 6;
    private static final int TITLE_STATUS_GAP = 8;

    /** The rail: tabs 22 square, joined to the panel's right edge, in a fixed order of {@link RailSlot}s. */
    public static final int TAB_TOP = 4;
    public static final int TAB_SIZE = 22;
    public static final int TAB_SPACING = 24;

    private final List<Tab> tabs = new ArrayList<>();

    @Nullable
    private QuantumOverlay overlay;

    protected QuantumMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    protected QuantumMachineScreen(T menu, Inventory playerInventory, Component title, int imageWidth, int imageHeight) {
        super(menu, playerInventory, title, imageWidth, imageHeight);
    }

    /**
     * Where a tab sits on the rail. Settings every block shares come first and always in this order, so
     * the filter and the priority are in the same place on every screen that has them; the block's own
     * modes follow, then tabs that belong to one view only (a filter view's list switch), last so that showing
     * them moves nothing. A page's Back tab takes the top of the rail.
     */
    public enum RailSlot { FILTER, PRIORITY, REDSTONE, SIDES, MODE, VIEW }

    /** A tab on the rail. It shows an icon (or, until a screen is redrawn, a short label or an item). */
    protected static final class Tab {
        private final Supplier<Component> label;
        private final Runnable onClick;
        private final Supplier<List<Component>> tooltip;
        private BooleanSupplier enabled = () -> true;
        private BooleanSupplier visible = () -> true;
        private int width = TAB_SIZE;
        private Supplier<ItemStack> icon = () -> ItemStack.EMPTY;
        @Nullable
        private Supplier<GuiIcon> glyph;
        @Nullable
        private BooleanSupplier on;
        private boolean opens;
        @Nullable
        private Supplier<String> badge;
        @Nullable
        private Runnable onRightClick;
        @Nullable
        private IntConsumer onScroll;
        private RailSlot slot = RailSlot.MODE;
        private int x;
        private int y;
        private boolean shown;

        private Tab(Supplier<Component> label, Runnable onClick, Supplier<List<Component>> tooltip) {
            this.label = label;
            this.onClick = onClick;
            this.tooltip = tooltip;
        }

        public static Tab of(Supplier<Component> label, Runnable onClick, Supplier<List<Component>> tooltip) {
            return new Tab(label, onClick, tooltip);
        }

        public static Tab of(String label, Runnable onClick, List<Component> tooltip) {
            return new Tab(() -> Component.literal(label), onClick, () -> tooltip);
        }

        /** A tab drawn with one of the shared icons; the tooltip names it. */
        public static Tab glyph(Supplier<GuiIcon> glyph, Runnable onClick, Supplier<List<Component>> tooltip) {
            Tab tab = new Tab(Component::empty, onClick, tooltip);
            tab.glyph = glyph;
            return tab;
        }

        public Tab width(int width) {
            this.width = width;
            return this;
        }

        /** Draws an item on the tab instead of its label; the tooltip still names it. */
        public Tab icon(ItemStack icon) {
            this.icon = () -> icon;
            return this;
        }

        /** An icon that follows the machine's state, such as a toggle. */
        public Tab icon(Supplier<ItemStack> icon) {
            this.icon = icon;
            return this;
        }

        /** A toggle: lit while on, its icon dimmed while off. */
        public Tab on(BooleanSupplier on) {
            this.on = on;
            return this;
        }

        /** It opens a page rather than changing a setting: a tick in its corner says so. */
        public Tab opens() {
            this.opens = true;
            return this;
        }

        /** A short value in its corner, such as the priority, so it reads without opening. */
        public Tab badge(Supplier<String> badge) {
            this.badge = badge;
            return this;
        }

        /** Right-click: a choice steps back. */
        public Tab onRightClick(Runnable onRightClick) {
            this.onRightClick = onRightClick;
            return this;
        }

        /** Scrolling over it: +1 up, -1 down. */
        public Tab onScroll(IntConsumer onScroll) {
            this.onScroll = onScroll;
            return this;
        }

        public Tab slot(RailSlot slot) {
            this.slot = slot;
            return this;
        }

        /** A tab that only belongs to one view of the screen, hidden (not just greyed) otherwise. */
        public Tab visible(BooleanSupplier visible) {
            this.visible = visible;
            return this;
        }

        public Tab enabled(BooleanSupplier enabled) {
            this.enabled = enabled;
            return this;
        }

        private boolean hit(double mouseX, double mouseY) {
            return shown && QuantumWidgets.hit(mouseX, mouseY, x, y, width, TAB_SIZE);
        }
    }

    protected abstract Identifier panelTexture();

    protected abstract int energyStored();

    protected abstract int maxEnergyStored();

    protected List<Component> energyTooltip() {
        return EnergyBarTooltip.lines(energyStored(), maxEnergyStored(), -1, -1, false);
    }

    /** Short text drawn on the panel itself; formatting codes are honoured. */
    protected abstract Component statusLabel();

    protected abstract List<Component> statusTooltip();

    /** How the status reads: its colour comes from the panel's theme. */
    protected PanelTheme.Tone statusTone() {
        return PanelTheme.Tone.OK;
    }

    /** The palette the panel texture is painted in; it decides every text colour on it. */
    protected abstract PanelTheme theme();

    /** Machines with no buffer (the Observation Chamber, the Tesseract Stabiliser) have no bar. */
    protected boolean hasEnergy() {
        return true;
    }

    /** Colour for plain text drawn on the panel. */
    protected final int textColour() {
        return theme().text;
    }

    protected final int dimColour() {
        return theme().dim;
    }

    protected List<Tab> createTabs() {
        return List.of();
    }

    /** Extra panel art: progress arrows, tanks, anything drawn under the item layer. */
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
    }

    /** Drawn after the item layer, for overlays that must sit on top of stacks. */
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    /** Hover text for machine-specific regions. Only called when no overlay is open. */
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    protected final void openOverlay(QuantumOverlay overlay) {
        this.overlay = overlay;
        overlay.layout(width, height);
    }

    /** The panel's frame on screen, where pages are drawn. */
    public final Rect2i panelArea() {
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    /**
     * The shared Priority tab: the value in its corner, scroll over it to nudge (shift for tens), click for the
     * page. {@code set} sends the new value; it arrives already clamped to {@code max}.
     */
    protected final Tab priorityTab(IntSupplier value, IntConsumer set, int max) {
        IntConsumer clamped = to -> {
            int next = Math.clamp(to, -max, max);
            if (next != value.getAsInt()) set.accept(next);
        };
        return Tab.glyph(() -> GuiIcon.PRIORITY, () -> openOverlay(new PriorityPage(this::panelArea, value, clamped, max)),
                        () -> List.of(Component.translatable("gui.quantimium.priority.value", value.getAsInt()),
                                hint("gui.quantimium.priority.tab.hint")))
                .slot(RailSlot.PRIORITY).opens()
                .badge(() -> priorityBadge(value.getAsInt()))
                .onScroll(step -> clamped.accept(value.getAsInt() + step * (Minecraft.getInstance().hasShiftDown() ? 10 : 1)));
    }

    /** The shared redstone tab: the item shows the setting; click steps on, right-click back. */
    protected final Tab redstoneTab(Supplier<RedstoneMode> mode, Runnable next) {
        return Tab.of(Component::empty, next, () -> List.of(mode.get().label(), hint("gui.quantimium.tab.cycle")))
                .icon(() -> new ItemStack(mode.get().icon))
                .slot(RailSlot.REDSTONE)
                // Three settings: two steps on is one back.
                .onRightClick(() -> {
                    next.run();
                    next.run();
                });
    }

    /** The grey second line of a tab's tooltip: how to use it. */
    protected static Component hint(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    /** The shared Sides tab: asks the server for the faces, which answers with {@link #openSideConfig}. */
    protected final Tab sidesTab(BlockPos pos) {
        return Tab.glyph(() -> GuiIcon.SIDES, () -> ClientPacketDistributor.sendToServer(new RequestSideConfigPayload(pos)),
                        () -> List.of(Component.translatable("gui.quantimium.sides")))
                .slot(RailSlot.SIDES).opens();
    }

    public final void openSideConfig(OpenSideConfigPayload payload) {
        openOverlay(new SidesPage(this::panelArea, payload.pos(), payload.configs(), payload.profile(), sideSlotMasks()));
    }

    /** Whether each face also picks which of the machine's input slots it may fill or empty. */
    protected boolean sideSlotMasks() {
        return false;
    }

    private static String priorityBadge(int value) {
        if (value == 0) return "";
        if (value > 99) return "99+";
        if (value < -99) return "-99";
        return Integer.toString(value);
    }

    /** How far the tab column reaches past the panel's right edge; for tools that capture the screen. */
    public final int tabColumnWidth() {
        int widest = 0;
        for (Tab tab : tabs) widest = Math.max(widest, tab.width - 1);
        return widest;
    }

    /**
     * Where the visible tabs are, on screen. The column hangs off the panel's right edge, where recipe
     * viewers draw their item lists, so they are told to keep clear of it.
     */
    public final List<Rect2i> tabAreas() {
        List<Rect2i> areas = new ArrayList<>();
        for (Tab tab : tabs) {
            if (tab.shown) areas.add(new Rect2i(tab.x, tab.y, tab.width, TAB_SIZE));
        }
        return areas;
    }

    /** The open overlay, if any; for tools that capture the screen. */
    @Nullable
    public final QuantumOverlay currentOverlay() {
        return activeOverlay();
    }

    @Nullable
    protected final QuantumOverlay activeOverlay() {
        if (overlay != null && overlay.isClosed()) overlay = null;
        return overlay;
    }

    @Override
    protected void init() {
        super.init();
        tabs.clear();
        tabs.addAll(createTabs());
        tabs.sort(Comparator.comparing(tab -> tab.slot));
        layoutTabs();

        if (overlay != null) overlay.layout(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        layoutTabs();
        QuantumOverlay open = activeOverlay();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (Tab tab : tabs) {
            ItemStack icon = !tab.shown || tab.glyph != null ? ItemStack.EMPTY : tab.icon.get();
            if (!icon.isEmpty()) graphics.item(icon, tab.x + (tab.width - 16) / 2, tab.y + 3);
        }
        renderMachineForeground(graphics, mouseX, mouseY);
        if (open != null) {
            open.render(graphics, font, mouseX, mouseY, partialTick);
            return;
        }

        renderChromeTooltips(graphics, mouseX, mouseY);
        renderMachineTooltips(graphics, mouseX, mouseY);
    }

    private void renderChromeTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hasEnergy() && QuantumWidgets.hit(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y,
                ENERGY_WIDTH + 1, ENERGY_HEIGHT + 1)) {
            graphics.setComponentTooltipForNextFrame(font, energyTooltip(), mouseX, mouseY);
            return;
        }
        int statusWidth = font.width(statusLabel());
        if (QuantumWidgets.hit(mouseX, mouseY, leftPos + statusX(statusWidth) - 2, topPos + TITLE_Y - 3,
                statusWidth + 4, font.lineHeight + 4)) {
            graphics.setComponentTooltipForNextFrame(font, statusTooltip(), mouseX, mouseY);
            return;
        }
        for (Tab tab : tabs) {
            if (!tab.hit(mouseX, mouseY)) continue;
            List<Component> lines = tab.tooltip.get();
            if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    private int statusX(int statusWidth) {
        return imageWidth - STATUS_RIGHT_MARGIN - statusWidth;
    }

    /**
     * The title strip: the machine's name on the left, its status on the right, in the theme's
     * colours. The status always fits; the name gives way to it, shortened with an ellipsis. There
     * is no "Inventory" label: the rule above the player's slots says the same thing.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component status = statusLabel();
        int statusWidth = font.width(status);
        int statusX = statusX(statusWidth);
        graphics.text(font, status, statusX, TITLE_Y, theme().tone(statusTone()), false);

        int room = statusX - TITLE_STATUS_GAP - TITLE_X;
        FormattedText name = title;
        if (font.width(name) > room) {
            name = FormattedText.composite(font.substrByWidth(title, room - font.width("…")),
                    FormattedText.of("…"));
        }
        graphics.text(font, Language.getInstance().getVisualOrder(name), TITLE_X, TITLE_Y,
                theme().title, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, panelTexture(), leftPos, topPos, 0.0f, 0.0f,
                imageWidth, imageHeight, 256, 256);

        int max = Math.max(1, maxEnergyStored());
        int filled = (int) ((long) ENERGY_HEIGHT * Math.max(0, energyStored()) / max);
        if (hasEnergy() && filled > 0) {
            graphics.fill(leftPos + ENERGY_X, topPos + ENERGY_Y + (ENERGY_HEIGHT - filled),
                    leftPos + ENERGY_X + ENERGY_WIDTH, topPos + ENERGY_Y + ENERGY_HEIGHT,
                    QuantumUiColours.ENERGY_FILL);
        }

        renderMachineBg(graphics, partialTick, mouseX, mouseY);
        boolean paged = activeOverlay() != null;
        for (Tab tab : tabs) {
            if (tab.shown) drawTab(graphics, tab, !paged && tab.hit(mouseX, mouseY));
        }
    }

    /** Hidden tabs close up, so the rest keep their order from the top. */
    private void layoutTabs() {
        int tabY = topPos + TAB_TOP;
        for (Tab tab : tabs) {
            tab.shown = tab.visible.getAsBoolean();
            if (!tab.shown) continue;
            tab.x = leftPos + imageWidth - 1;
            tab.y = tabY;
            tabY += TAB_SPACING;
        }
    }

    private void drawTab(GuiGraphicsExtractor graphics, Tab tab, boolean hovered) {
        boolean lit = tab.on != null && tab.on.getAsBoolean();
        boolean usable = tab.enabled.getAsBoolean();
        Rail.tab(graphics, tab.x, tab.y, tab.width, lit, hovered && usable);
        if (tab.glyph != null) {
            boolean dim = !usable || (tab.on != null && !lit);
            tab.glyph.get().draw(graphics, tab.x + (tab.width - GuiIcon.SIZE) / 2, tab.y + 3, dim ? GuiIcon.DIMMED : 0xFFFFFFFF);
        } else if (tab.icon.get().isEmpty()) {
            Component text = tab.label.get();
            graphics.text(font, text, tab.x + (tab.width - font.width(text)) / 2, tab.y + 7,
                    usable ? Rail.TEXT : Rail.TEXT_DIM, false);
        }
        if (tab.opens) Rail.opensTick(graphics, tab.x, tab.y, tab.width);
        if (tab.badge != null) Rail.badge(graphics, font, tab.badge.get(), tab.x, tab.y, tab.width);
    }

    /** A tab under the cursor, if the rail is in use. */
    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        if (activeOverlay() != null) return null;
        for (Tab tab : tabs) {
            if (tab.hit(mouseX, mouseY) && tab.enabled.getAsBoolean()) return tab;
        }
        return null;
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        QuantumOverlay open = activeOverlay();
        if (open != null) return open.mouseClicked(mouseX, mouseY, button);
        Tab tab = tabAt(mouseX, mouseY);
        if (tab != null) {
            Runnable action = button == 0 ? tab.onClick : button == 1 ? tab.onRightClick : null;
            if (action != null) {
                clickSound();
                action.run();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        QuantumOverlay open = activeOverlay();
        return open != null
                ? open.mouseReleased(mouseX, mouseY, button)
                : super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        QuantumOverlay open = activeOverlay();
        return open != null
                ? open.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                : super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        QuantumOverlay open = activeOverlay();
        if (open != null) return open.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        Tab tab = tabAt(mouseX, mouseY);
        if (tab != null && tab.onScroll != null && scrollY != 0) {
            tab.onScroll.accept(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        QuantumOverlay open = activeOverlay();
        return open != null ? open.charTyped(event.codepoint()) : super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        QuantumOverlay open = activeOverlay();
        return open != null
                ? open.keyPressed(keyCode)
                : super.keyPressed(event);
    }
}
