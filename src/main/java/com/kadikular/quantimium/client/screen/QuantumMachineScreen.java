package com.kadikular.quantimium.client.screen;

import net.minecraft.client.renderer.RenderPipelines;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Common shell for Quantimium machine screens: the panel, the energy bar, the title strip with the
 * status, the right-edge tab column, and hosting for a modal overlay.
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

    private static final int TAB_TOP = 10;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_SPACING = 22;

    private final List<Tab> tabs = new ArrayList<>();

    @Nullable
    private QuantumOverlay overlay;

    protected QuantumMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    protected QuantumMachineScreen(T menu, Inventory playerInventory, Component title, int imageWidth, int imageHeight) {
        super(menu, playerInventory, title, imageWidth, imageHeight);
    }

    /** A button in the column down the right edge of the panel. */
    protected static final class Tab {
        private final Supplier<Component> label;
        private final Runnable onClick;
        private final Supplier<List<Component>> tooltip;
        private BooleanSupplier enabled = () -> true;
        private BooleanSupplier visible = () -> true;
        private int width = 20;
        private Supplier<ItemStack> icon = () -> ItemStack.EMPTY;
        @Nullable
        private Button button;

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

        /** A tab that only belongs to one view of the screen, hidden (not just greyed) otherwise. */
        public Tab visible(BooleanSupplier visible) {
            this.visible = visible;
            return this;
        }

        public Tab enabled(BooleanSupplier enabled) {
            this.enabled = enabled;
            return this;
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

    /** How far the tab column reaches past the panel's right edge; for tools that capture the screen. */
    public final int tabColumnWidth() {
        int widest = 0;
        for (Tab tab : tabs) widest = Math.max(widest, tab.width);
        return widest;
    }

    /**
     * Where the visible tabs are, on screen. The column hangs off the panel's right edge, where recipe
     * viewers draw their item lists, so they are told to keep clear of it.
     */
    public final List<Rect2i> tabAreas() {
        List<Rect2i> areas = new ArrayList<>();
        for (Tab tab : tabs) {
            if (tab.button != null && tab.button.visible) {
                areas.add(new Rect2i(tab.button.getX(), tab.button.getY(), tab.button.getWidth(), tab.button.getHeight()));
            }
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

        int tabX = leftPos + imageWidth;
        int tabY = topPos + TAB_TOP;
        for (Tab tab : tabs) {
            Button button = Button.builder(tab.label.get(), b -> tab.onClick.run())
                    .bounds(tabX, tabY, tab.width, TAB_HEIGHT).build();
            tab.button = button;
            addRenderableWidget(button);
            tabY += TAB_SPACING;
        }

        if (overlay != null) overlay.layout(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Hidden tabs close up, so a view's tabs sit together at the top of the column.
        int tabY = topPos + TAB_TOP;
        for (Tab tab : tabs) {
            if (tab.button == null) continue;
            tab.button.setMessage(tab.icon.get().isEmpty() ? tab.label.get() : Component.empty());
            tab.button.active = tab.enabled.getAsBoolean();
            tab.button.visible = tab.visible.getAsBoolean();
            if (!tab.button.visible) continue;
            tab.button.setY(tabY);
            tabY += TAB_SPACING;
        }

        QuantumOverlay open = activeOverlay();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (Tab tab : tabs) {
            ItemStack icon = tab.button == null || !tab.button.visible ? ItemStack.EMPTY : tab.icon.get();
            if (icon.isEmpty()) continue;
            graphics.item(icon, tab.button.getX() + (tab.width - 16) / 2, tab.button.getY() + 2);
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
            if (tab.button == null || !tab.button.visible || !tab.button.isHovered()) continue;
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
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        QuantumOverlay open = activeOverlay();
        return open != null
                ? open.mouseClicked(mouseX, mouseY, button)
                : super.mouseClicked(event, doubleClick);
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
        return open != null
                ? open.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                : super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
