package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Drawing primitives shared by the Quantimium screens. Everything here is plain fills and text: the
 * chrome stays resolution-independent and needs no texture, leaving PNG art for the container panels.
 */
public final class QuantumWidgets {

    public static final int TAB_WIDTH = 50;
    public static final int TAB_HEIGHT = 14;
    /** Mask cells are square and sized between a text line and an item slot. */
    public static final int MASK_CELL = 14;
    public static final int MASK_COLUMNS = 3;

    private QuantumWidgets() {}

    public static void backdrop(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), QuantumUiColours.BACKDROP);
    }

    public static void framedPanel(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, QuantumUiColours.BORDER);
        graphics.fill(left, top, left + width, top + height, QuantumUiColours.PANEL);
    }

    public static void title(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
        graphics.text(font, text, x, y, QuantumUiColours.ACCENT, false);
    }

    public static void tab(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, boolean active) {
        graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT,
                active ? QuantumUiColours.TAB_ACTIVE : QuantumUiColours.TAB_IDLE);
        graphics.text(font, label, x + 8, y + 3,
                active ? QuantumUiColours.TEXT : QuantumUiColours.TEXT_MUTED, false);
    }

    /** A labelled toggle. {@code onColour} carries the meaning, so callers pick in/out/neutral. */
    public static void chip(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
                            int width, int height, boolean active, int onColour) {
        graphics.fill(x, y, x + width, y + height, active ? onColour : QuantumUiColours.CHIP_OFF);
        if (active) outline(graphics, x, y, width, height, QuantumUiColours.SELECTION);
        int textX = x + Math.max(2, (width - font.width(label)) / 2);
        graphics.text(font, label, textX, y + (height - 8) / 2,
                active ? QuantumUiColours.TEXT : QuantumUiColours.TEXT_DIM, false);
    }

    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y, x + 1, y + height, colour);
        graphics.fill(x + width - 1, y, x + width, y + height, colour);
    }

    /**
     * The bits of a slot mask drawn in the shape of the grid they refer to, so bit 4 is visibly the
     * middle slot instead of a square labelled "4".
     */
    public static void maskGrid(GuiGraphicsExtractor graphics, int x, int y, int mask, int bits, int onColour) {
        for (int bit = 0; bit < bits; bit++) {
            int cellX = x + (bit % MASK_COLUMNS) * MASK_CELL;
            int cellY = y + (bit / MASK_COLUMNS) * MASK_CELL;
            boolean on = (mask & (1 << bit)) != 0;
            graphics.fill(cellX, cellY, cellX + MASK_CELL - 2, cellY + MASK_CELL - 2,
                    on ? onColour : QuantumUiColours.CHIP_OFF);
        }
    }

    /** Which mask bit the cursor is over, or -1. Mirrors the layout of {@link #maskGrid}. */
    public static int maskBitAt(int x, int y, int bits, double mouseX, double mouseY) {
        for (int bit = 0; bit < bits; bit++) {
            int cellX = x + (bit % MASK_COLUMNS) * MASK_CELL;
            int cellY = y + (bit / MASK_COLUMNS) * MASK_CELL;
            if (hit(mouseX, mouseY, cellX, cellY, MASK_CELL - 2, MASK_CELL - 2)) return bit;
        }
        return -1;
    }

    public static int maskRows(int bits) {
        return Math.max(1, (bits + MASK_COLUMNS - 1) / MASK_COLUMNS);
    }

    public static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
