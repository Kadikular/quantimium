package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

/**
 * A page a rail tab opens: drawn in the panel's own frame, over it, so the screen doesn't jump. A back arrow
 * sits before its title; its tab, clicked again, or Escape also go back, not out of the screen.
 */
public abstract class Page implements QuantumOverlay {

    private final Supplier<Rect2i> panel;
    private final Component title;
    protected final Rail rail;
    private boolean closed;
    protected int left;
    protected int top;
    protected int width;
    protected int height;

    protected Page(Supplier<Rect2i> panel, Rail rail, Component title) {
        this.panel = panel;
        this.rail = rail;
        this.title = title;
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
        Rect2i area = panel.get();
        left = area.getX();
        top = area.getY();
        width = area.getWidth();
        height = area.getHeight();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public void close() {
        closed = true;
    }

    @Override
    public Rect2i bounds() {
        return new Rect2i(left, top, width, height);
    }

    /** The back arrow before the title: the shared icon at half size, in a box the height of the title row. */
    public static final int BACK_X = 3;
    public static final int BACK_Y = 2;
    public static final int BACK_SIZE = 11;
    /** Where the title starts after it. */
    public static final int TITLE_AFTER_BACK = BACK_X + BACK_SIZE + 3;

    private boolean overBack(double mouseX, double mouseY) {
        return QuantumWidgets.hit(mouseX, mouseY, left + BACK_X, top + BACK_Y, BACK_SIZE, BACK_SIZE);
    }

    @Override
    public final void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partialTick) {
        graphics.nextStratum();
        graphics.fill(left, top, left + width, top + height, rail.fill());
        QuantumWidgets.outline(graphics, left, top, width, height, rail.edge());
        graphics.fill(left + 3, top + 15, left + width - 3, top + 16, rail.edge());
        backArrow(graphics, rail, left, top, overBack(mouseX, mouseY));
        graphics.text(font, title, left + TITLE_AFTER_BACK, top + 4, rail.title(), false);

        renderPage(graphics, font, mouseX, mouseY);
        if (overBack(mouseX, mouseY)) graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.quantimium.page.back")), mouseX, mouseY);
    }

    /** The back arrow for a panel whose top-left corner is at ({@code left}, {@code top}). */
    public static void backArrow(GuiGraphicsExtractor graphics, Rail rail, int left, int top, boolean hovered) {
        if (hovered) rail.button(graphics, left + BACK_X, top + BACK_Y, BACK_SIZE, BACK_SIZE, true, true);
        graphics.pose().pushMatrix();
        graphics.pose().translate(left + BACK_X + 1.5f, top + BACK_Y + 1.5f);
        graphics.pose().scale(0.5f, 0.5f);
        GuiIcon.BACK.draw(graphics, 0, 0, hovered ? 0xFFFFFFFF : rail.text());
        graphics.pose().popMatrix();
    }

    protected abstract void renderPage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY);

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBack(mouseX, mouseY)) {
            click();
            close();
            return true;
        }
        pageClicked(mouseX, mouseY, button);
        return true;
    }

    protected void pageClicked(double mouseX, double mouseY, int button) {
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) close();
        return true;
    }

    protected static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    protected static boolean shift() {
        return Minecraft.getInstance().hasShiftDown();
    }

    /** A plain button on a page: the rail's colours, its label centred. */
    protected void button(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int w, int h, boolean hovered) {
        rail.button(graphics, x, y, w, h, true, hovered);
        graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, rail.text(), false);
    }
}
