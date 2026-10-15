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
import com.kadikular.quantimium.client.screen.ui.Page;
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
 * status, the two rails of tabs, and hosting for a modal overlay or a page. The right rail holds the pages
 * (filter, priority, sides): the open one is lit and clicking it again closes it. The left rail holds the
 * settings of what's on show, the block's own on its main view and a page's on that page.
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
    /** The size of the light a status becomes when the name leaves it no room. */
    private static final int STATUS_LIGHT = 6;

    /** The rails: tabs 22 square, joined to the panel's edges, in a fixed order of {@link RailSlot}s. */
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
     * Where a tab sits. Pages go on the right in this order, so the filter and the priority are in the same
     * place on every screen that has them; settings go on the left, redstone first, then the block's own.
     */
    public enum RailSlot {
        FILTER(true), PRIORITY(true), SIDES(true), SLOTS(true), REDSTONE(false), MODE(false);

        private final boolean page;

        RailSlot(boolean page) {
            this.page = page;
        }
    }

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
        private Supplier<Identifier> texture;
        @Nullable
        private BooleanSupplier on;
        /** A page tab: whether its page is open, and how to close it. */
        @Nullable
        private BooleanSupplier open;
        @Nullable
        private Runnable close;
        /** A setting that belongs to a page, shown only while it's open; null for the main view's. */
        @Nullable
        private BooleanSupplier during;
        private boolean awaits;
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

        /** A whole 16×16 texture as its icon, such as a vanilla item's. */
        public Tab texture(Supplier<Identifier> texture) {
            this.texture = texture;
            return this;
        }

        /** A toggle: lit while on, its icon dimmed while off. */
        public Tab on(BooleanSupplier on) {
            this.on = on;
            return this;
        }

        /** A page tab, for the right rail: lit while {@code open}; clicked again, it runs {@code close}. */
        public Tab page(RailSlot slot, BooleanSupplier open, Runnable close) {
            this.slot = slot;
            this.open = open;
            this.close = close;
            return this;
        }

        /**
         * A page the server opens, once asked: the page open now stays up until it does, so switching doesn't show
         * the machine for a moment between.
         */
        public Tab awaits() {
            this.awaits = true;
            return this;
        }

        /** A setting of a page: shown on the left only while that page is open. */
        public Tab during(BooleanSupplier page) {
            this.during = page;
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

    /** Whether the pages open now close when the server answers with the one asked for. */
    private boolean closingOnAnswer;

    protected final void openOverlay(QuantumOverlay overlay) {
        if (closingOnAnswer) {
            closingOnAnswer = false;
            closePages();
        }
        this.overlay = overlay;
        overlay.layout(width, height);
    }

    protected final void closeOverlay() {
        overlay = null;
    }

    /** Whether one of the right rail's pages is open. */
    private boolean pageOpen() {
        for (Tab tab : tabs) {
            if (tab.open != null && tab.open.getAsBoolean()) return true;
        }
        return false;
    }

    /** The rails stay in use under a page; a modal overlay takes all input. */
    private boolean railsActive() {
        QuantumOverlay open = activeOverlay();
        return open == null || open instanceof Page;
    }

    /** The rail's colours, from the panel's tier. */
    protected final Rail rail() {
        return Rail.of(theme());
    }

    /** The panel's frame on screen, where pages are drawn. */
    public final Rect2i panelArea() {
        return new Rect2i(leftPos, topPos, imageWidth, imageHeight);
    }

    /**
     * The shared Priority tab: its value on hover, scroll over it to nudge (shift for tens), click for the
     * page. {@code set} sends the new value; it arrives already clamped to {@code max}.
     */
    protected final Tab priorityTab(IntSupplier value, IntConsumer set, int max) {
        IntConsumer clamped = to -> {
            int next = Math.clamp(to, -max, max);
            if (next != value.getAsInt()) set.accept(next);
        };
        return Tab.glyph(() -> GuiIcon.PRIORITY, () -> openOverlay(new PriorityPage(this::panelArea, rail(), value, clamped, max)),
                        () -> List.of(Component.translatable("gui.quantimium.priority.value", value.getAsInt()),
                                hint("gui.quantimium.priority.tab.hint")))
                .page(RailSlot.PRIORITY, () -> activeOverlay() instanceof PriorityPage, this::closeOverlay)
                .onScroll(step -> clamped.accept(value.getAsInt() + step * (Minecraft.getInstance().hasShiftDown() ? 10 : 1)));
    }

    /** The shared redstone tab: the item shows the setting; click steps on, right-click back. */
    protected final Tab redstoneTab(Supplier<RedstoneMode> mode, Runnable next) {
        return Tab.of(Component::empty, next, () -> List.of(mode.get().label(), hint("gui.quantimium.tab.cycle")))
                .texture(() -> mode.get().texture)
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

    /**
     * A Filter tab for a screen whose filter is a view of its own menu: lit while it shows, and while it does,
     * the tabs marked {@link Tab#during} with {@code shown} are the settings on the left.
     */
    protected final Tab filterTab(BooleanSupplier shown, java.util.function.Consumer<Boolean> show, Component name, String hintKey) {
        return Tab.glyph(() -> GuiIcon.FILTER, () -> show.accept(true), () -> List.of(name, hint(hintKey)))
                .page(RailSlot.FILTER, shown, () -> show.accept(false));
    }

    /** The shared Sides tab: asks the server for the faces, which answers with {@link #openSideConfig}. */
    protected final Tab sidesTab(BlockPos pos) {
        return Tab.glyph(() -> GuiIcon.SIDES, () -> ClientPacketDistributor.sendToServer(new RequestSideConfigPayload(pos)),
                        () -> List.of(Component.translatable("gui.quantimium.sides")))
                .page(RailSlot.SIDES, () -> activeOverlay() instanceof SidesPage, this::closeOverlay)
                .awaits();
    }

    public final void openSideConfig(OpenSideConfigPayload payload) {
        openOverlay(new SidesPage(this::panelArea, rail(), payload.pos(), payload.configs(), payload.profile(), sideSlotMasks()));
    }

    /** Whether each face also picks which of the machine's input slots it may fill or empty. */
    protected boolean sideSlotMasks() {
        return false;
    }

    /** How far the rails reach past the panel's left and right edges; for tools that capture the screen. */
    public final int railWidth(boolean left) {
        int widest = 0;
        for (Tab tab : tabs) {
            if (tab.slot.page != left) widest = Math.max(widest, tab.width - 1);
        }
        return widest;
    }

    /**
     * Where the visible tabs are, on screen. The rails hang off the panel's edges, where recipe viewers draw
     * their item lists and bookmarks, so they are told to keep clear of them.
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
    /** Under a page, nothing of the machine or inventory is hovered: no item tooltips through it. */
    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        return activeOverlay() == null && super.isHovering(left, top, w, h, xm, ym);
    }

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
            ItemStack icon = !tab.shown || tab.glyph != null || tab.texture != null ? ItemStack.EMPTY : tab.icon.get();
            if (!icon.isEmpty()) graphics.item(icon, tab.x + (tab.width - 16) / 2, tab.y + 3);
        }
        renderMachineForeground(graphics, mouseX, mouseY);
        if (open != null) {
            open.render(graphics, font, mouseX, mouseY, partialTick);
            if (railsActive()) renderTabTooltip(graphics, mouseX, mouseY);
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
        if (statusWidth > 0 && QuantumWidgets.hit(mouseX, mouseY, leftPos + statusX - 2, topPos + TITLE_Y - 3,
                statusWidth + 4, font.lineHeight + 4)) {
            List<Component> lines = statusTooltip();
            // Shortened to fit, it reads in full here.
            if (statusShortened) {
                lines = new ArrayList<>(lines);
                lines.addFirst(statusLabel());
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        renderTabTooltip(graphics, mouseX, mouseY);
    }

    /** A tab's name and hint; an open page's tab says it closes it. */
    private void renderTabTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Tab tab : tabs) {
            if (!tab.hit(mouseX, mouseY)) continue;
            List<Component> lines = tab.tooltip.get();
            if (tab.open != null && tab.open.getAsBoolean() && !lines.isEmpty()) {
                lines = List.of(lines.getFirst(), hint("gui.quantimium.page.close"));
            }
            if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    /** Where the status was last drawn, panel-relative, and whether it had to be shortened. */
    private int statusX;
    private int statusWidth;
    private boolean statusShortened;

    /**
     * The title strip: the machine's name on the left, its status on the right, in the theme's colours. The
     * name always reads in full; a status with no room beside it becomes a light in its colour, read on hover,
     * so the two never overlap and neither is cut short. There is no "Inventory" label: the rule above the
     * player's slots says the same thing.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // A page that's a view of the menu itself (a filter) has the same back arrow as a covering page.
        int titleX = TITLE_X;
        if (viewPageOpen()) {
            Page.backArrow(graphics, rail(), 0, 0, overViewBack(mouseX, mouseY));
            titleX = Page.TITLE_AFTER_BACK;
        }
        int right = imageWidth - STATUS_RIGHT_MARGIN;
        FormattedText name = fit(title, right - titleX);
        graphics.text(font, Language.getInstance().getVisualOrder(name), titleX, TITLE_Y, theme().title, false);

        Component status = statusLabel();
        int room = right - (titleX + font.width(name) + TITLE_STATUS_GAP);
        int colour = theme().tone(statusTone());
        statusShortened = font.width(status) > room;
        if (font.width(status) == 0) {
            statusWidth = 0;
        } else if (statusShortened) {
            statusWidth = STATUS_LIGHT;
            statusX = right - STATUS_LIGHT;
            graphics.fill(statusX, TITLE_Y + 1, statusX + STATUS_LIGHT, TITLE_Y + 1 + STATUS_LIGHT, colour);
        } else {
            statusWidth = font.width(status);
            statusX = right - statusWidth;
            graphics.text(font, status, statusX, TITLE_Y, colour, false);
        }
    }

    /** {@code text} cut to {@code room} with an ellipsis, or whole if it fits. */
    private FormattedText fit(Component text, int room) {
        if (font.width(text) <= room) return text;
        return FormattedText.composite(font.substrByWidth(text, Math.max(0, room - font.width("…"))), FormattedText.of("…"));
    }

    /** A page open that isn't drawn over the panel: the panel itself is showing it. */
    private boolean viewPageOpen() {
        return activeOverlay() == null && pageOpen();
    }

    private boolean overViewBack(double mouseX, double mouseY) {
        return QuantumWidgets.hit(mouseX, mouseY, leftPos + Page.BACK_X, topPos + Page.BACK_Y, Page.BACK_SIZE, Page.BACK_SIZE);
    }

    private void closePages() {
        for (Tab each : tabs) {
            if (each.open != null && each.open.getAsBoolean()) each.close.run();
        }
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
        boolean active = railsActive();
        for (Tab tab : tabs) {
            if (tab.shown) drawTab(graphics, tab, active && tab.hit(mouseX, mouseY));
        }
    }

    /**
     * Pages on the right, settings on the left: the main view's while no page is open, a page's while it is.
     * Hidden tabs close up, so the rest keep their order from the top.
     */
    private void layoutTabs() {
        boolean paged = pageOpen();
        int rightY = topPos + TAB_TOP;
        int leftY = topPos + TAB_TOP;
        for (Tab tab : tabs) {
            tab.shown = tab.visible.getAsBoolean()
                    && (tab.slot.page || (tab.during == null ? !paged : tab.during.getAsBoolean()));
            if (!tab.shown) continue;
            if (tab.slot.page) {
                tab.x = leftPos + imageWidth - 1;
                tab.y = rightY;
                rightY += TAB_SPACING;
            } else {
                tab.x = leftPos - tab.width + 1;
                tab.y = leftY;
                leftY += TAB_SPACING;
            }
        }
    }

    private void drawTab(GuiGraphicsExtractor graphics, Tab tab, boolean hovered) {
        boolean lit = tab.open != null ? tab.open.getAsBoolean() : tab.on != null && tab.on.getAsBoolean();
        boolean usable = tab.enabled.getAsBoolean();
        rail().tab(graphics, tab.x, tab.y, tab.width, lit, hovered && usable, !tab.slot.page);
        if (tab.glyph != null) {
            boolean dim = !usable || (tab.on != null && !lit);
            tab.glyph.get().draw(graphics, tab.x + (tab.width - GuiIcon.SIZE) / 2, tab.y + 3, dim ? GuiIcon.DIMMED : 0xFFFFFFFF);
        } else if (tab.texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, tab.texture.get(), tab.x + (tab.width - 16) / 2, tab.y + 3, 0.0f, 0.0f,
                    16, 16, 16, 16, usable ? 0xFFFFFFFF : GuiIcon.DIMMED);
        } else if (tab.icon.get().isEmpty()) {
            Component text = tab.label.get();
            graphics.text(font, text, tab.x + (tab.width - font.width(text)) / 2, tab.y + 7,
                    usable ? rail().text() : rail().textDim(), false);
        }
    }

    /** A tab under the cursor, if the rails are in use. */
    @Nullable
    private Tab tabAt(double mouseX, double mouseY) {
        if (!railsActive()) return null;
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
        Tab tab = tabAt(mouseX, mouseY);
        if (tab != null) {
            if (tab.open != null && button == 0) {
                clickSound();
                switchPage(tab);
                return true;
            }
            Runnable action = button == 0 ? tab.onClick : button == 1 ? tab.onRightClick : null;
            if (action != null) {
                clickSound();
                action.run();
            }
            return true;
        }
        QuantumOverlay open = activeOverlay();
        if (open != null) return open.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && viewPageOpen() && overViewBack(mouseX, mouseY)) {
            clickSound();
            closePages();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Opens the page in {@code slot}, as clicking its tab would; for tools that capture the screen. */
    public final void openPage(RailSlot slot) {
        for (Tab tab : tabs) {
            if (tab.open != null && tab.slot == slot && !tab.open.getAsBoolean()) switchPage(tab);
        }
    }

    /** A page tab clicked: whatever page is open closes, and this one opens unless it was the one open. */
    private void switchPage(Tab tab) {
        boolean wasOpen = tab.open.getAsBoolean();
        if (!wasOpen && tab.awaits) {
            // What's open stays drawn until the server's answer replaces it, in the same frame.
            closingOnAnswer = true;
            tab.onClick.run();
            return;
        }
        closingOnAnswer = false;
        closePages();
        if (!wasOpen) tab.onClick.run();
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
        Tab tab = tabAt(mouseX, mouseY);
        if (tab != null && tab.onScroll != null && scrollY != 0) {
            tab.onScroll.accept(scrollY > 0 ? 1 : -1);
            return true;
        }
        QuantumOverlay open = activeOverlay();
        if (open != null) return open.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (open != null) return open.keyPressed(keyCode);
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && viewPageOpen()) {
            closePages();
            return true;
        }
        return super.keyPressed(event);
    }
}
