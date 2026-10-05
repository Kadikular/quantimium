package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A modal drawn on top of a machine screen. Not a real {@code Screen}: the host keeps the container
 * behind it alive and forwards input, so every method returns whether the overlay consumed the event
 * (they generally do — an open modal should swallow clicks rather than let them reach the slots).
 */
public interface QuantumOverlay {

    void layout(int screenWidth, int screenHeight);

    boolean isClosed();

    void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partialTick);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean keyPressed(int keyCode);

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    /** Where the overlay is drawn, in GUI pixels, once laid out; used to crop wiki screenshots. */
    @Nullable
    default Rect2i bounds() {
        return null;
    }
}
