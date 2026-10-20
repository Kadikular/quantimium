package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A slim scroll bar beside a scrolling grid: its track is a well in the panel's art (tools/gui_panels.py), its thumb
 * drawn here, as long as the share of rows showing, where they are. Click or drag on it to scroll there.
 */
public final class ScrollBar {
    /** Where its track's inside is, on the panel. */
    private final int x, y, width, height;
    private boolean dragging;

    public ScrollBar(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Draws the thumb, for {@code shown} rows of {@code total} with the first showing at {@code scroll}; none if all show. */
    public void render(GuiGraphicsExtractor graphics, int left, int top, int scroll, int shown, int total, int colour) {
        if (total <= shown) return;
        int thumb = Math.max(6, height * shown / total);
        int travel = height - thumb;
        int maxScroll = total - shown;
        int at = maxScroll <= 0 ? 0 : travel * scroll / maxScroll;
        graphics.fill(left + x, top + y + at, left + x + width, top + y + at + thumb, colour);
    }

    /** Whether the mouse is on its track (with a pixel to spare either side, it being so thin). */
    public boolean over(double mouseX, double mouseY, int left, int top) {
        return mouseX >= left + x - 1 && mouseX < left + x + width + 1 && mouseY >= top + y && mouseY < top + y + height;
    }

    /** A click on the track: it starts dragging. The scroll it lands on, of {@code maxScroll}. */
    public int press(double mouseY, int top, int maxScroll) {
        dragging = true;
        return scrollAt(mouseY, top, maxScroll);
    }

    public boolean dragging() {
        return dragging;
    }

    public void release() {
        dragging = false;
    }

    /** The scroll a mouse at {@code mouseY} on the track means. */
    public int scrollAt(double mouseY, int top, int maxScroll) {
        if (maxScroll <= 0) return 0;
        double along = (mouseY - top - y) / Math.max(1.0, height - 1);
        return (int) Math.round(Math.clamp(along, 0.0, 1.0) * maxScroll);
    }
}
