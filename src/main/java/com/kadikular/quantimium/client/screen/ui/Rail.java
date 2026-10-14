package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * How rail tabs, and the pages they open, are drawn on a panel of each tier: the panel's own stone, so a tab
 * reads as part of it. A tab is open on its left, where it joins the panel.
 */
public record Rail(int fill, int edge, int litFill, int litEdge, int hoverEdge, int text, int textDim, int well, int title) {

    /** Obsidian, as the laser and logistics panels. */
    public static final Rail HIGH = new Rail(0xFF121424, 0xFF262C48, 0xFF1C2340, 0xFF3A4680, 0xFF6A7AC0, 0xFFC8CDE0, 0xFF7A82A6, 0xFF090A14, QuantumUiColours.ACCENT);
    /** Deepslate. */
    public static final Rail MID = new Rail(0xFF2E2E36, 0xFF484850, 0xFF3C3C48, 0xFF6E6E80, 0xFF9C9CB0, 0xFFDCDCE4, 0xFF9C9CA8, 0xFF18181E, 0xFF78B4FF);
    /** Light stone: its icons are lit by a darker ground than the panel's, and its text is dark. */
    public static final Rail LOW = new Rail(0xFF706E6A, 0xFF4F4D4B, 0xFF5A5856, 0xFF2896BE, 0xFF2896BE, 0xFF1E1C1A, 0xFF3C3A37, 0xFF42403E, 0xFF14606A);

    public static final int SIZE = 22;
    public static final int BADGE = QuantumUiColours.ACCENT;
    public static final int BADGE_TEXT = 0xFF090A14;

    public static Rail of(PanelTheme theme) {
        return switch (theme) {
            case HIGH -> HIGH;
            case MID -> MID;
            case LOW -> LOW;
        };
    }

    public void tab(GuiGraphicsExtractor graphics, int x, int y, int width, boolean lit, boolean hovered) {
        int edgeColour = hovered ? hoverEdge : lit ? litEdge : edge;
        graphics.fill(x, y, x + width, y + SIZE, lit ? litFill : fill);
        graphics.fill(x, y, x + width, y + 1, edgeColour);
        graphics.fill(x, y + SIZE - 1, x + width, y + SIZE, edgeColour);
        graphics.fill(x + width - 1, y, x + width, y + SIZE, edgeColour);
    }

    /** A framed button: the rail's colours, lit or not. */
    public void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean lit, boolean hovered) {
        graphics.fill(x, y, x + width, y + height, lit ? litFill : fill);
        QuantumWidgets.outline(graphics, x, y, width, height, hovered ? hoverEdge : lit ? litEdge : edge);
    }

    /** The corner tick on a tab that opens a page. */
    public void opensTick(GuiGraphicsExtractor graphics, int x, int y, int width) {
        int right = x + width - 2;
        graphics.fill(right - 3, y + 2, right, y + 3, textDim);
        graphics.fill(right - 1, y + 2, right, y + 5, textDim);
    }

    /** A value in the tab's bottom-right corner, at the small size of an item count. */
    public static void badge(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width) {
        if (text.isEmpty()) return;
        SmallText.drawRight(graphics, font, text, x + width - 2, y + SIZE - 2, BADGE_TEXT, BADGE);
    }
}
