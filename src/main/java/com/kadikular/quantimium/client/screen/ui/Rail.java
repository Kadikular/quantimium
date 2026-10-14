package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * How a rail tab is drawn. The rail is the same dark chrome on every panel tier, so the hand-drawn icons
 * read the same everywhere: a tab is open on its left, where it joins the panel.
 */
public final class Rail {

    public static final int FILL = 0xFF121424;
    public static final int EDGE = 0xFF262C48;
    public static final int LIT_FILL = 0xFF1C2340;
    public static final int LIT_EDGE = 0xFF3A4680;
    public static final int HOVER_EDGE = 0xFF6A7AC0;
    public static final int TEXT = 0xFFC8CDE0;
    public static final int TEXT_DIM = 0xFF7A82A6;
    public static final int BADGE = QuantumUiColours.ACCENT;
    public static final int BADGE_TEXT = 0xFF090A14;

    private Rail() {}

    public static void tab(GuiGraphicsExtractor graphics, int x, int y, int width, boolean lit, boolean hovered) {
        int size = 22;
        int edge = hovered ? HOVER_EDGE : lit ? LIT_EDGE : EDGE;
        graphics.fill(x, y, x + width, y + size, lit ? LIT_FILL : FILL);
        graphics.fill(x, y, x + width, y + 1, edge);
        graphics.fill(x, y + size - 1, x + width, y + size, edge);
        graphics.fill(x + width - 1, y, x + width, y + size, edge);
    }

    /** The corner tick on a tab that opens a page. */
    public static void opensTick(GuiGraphicsExtractor graphics, int x, int y, int width) {
        int right = x + width - 2;
        graphics.fill(right - 3, y + 2, right, y + 3, TEXT_DIM);
        graphics.fill(right - 1, y + 2, right, y + 5, TEXT_DIM);
    }

    /** A value in the tab's bottom-right corner, in the small font's place: tight, on an accent chip. */
    public static void badge(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width) {
        if (text.isEmpty()) return;
        int textWidth = font.width(text);
        int right = x + width - 1;
        int bottom = y + 22 - 1;
        graphics.fill(right - textWidth - 2, bottom - 9, right, bottom, BADGE);
        graphics.text(font, text, right - textWidth - 1, bottom - 8, BADGE_TEXT, false);
    }
}
