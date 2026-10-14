package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Text at the size of an item count in a storage terminal: the font scaled down, for corners and badges. */
public final class SmallText {

    public static final float SCALE = 0.5f;

    private SmallText() {}

    /**
     * Draws {@code text} with its bottom-right corner at ({@code right}, {@code bottom}), on a chip of
     * {@code background} one pixel wider all round (none when it's 0).
     */
    public static void drawRight(GuiGraphicsExtractor graphics, Font font, String text, int right, int bottom, int colour, int background) {
        float width = font.width(text) * SCALE;
        float height = (font.lineHeight - 1) * SCALE;
        if (background != 0) {
            graphics.fill((int) Math.floor(right - width) - 1, (int) Math.floor(bottom - height) - 1, right + 1, bottom + 1, background);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(right - width, bottom - height);
        graphics.pose().scale(SCALE, SCALE);
        graphics.text(font, text, 0, 0, colour, false);
        graphics.pose().popMatrix();
    }
}
