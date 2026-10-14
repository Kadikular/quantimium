package com.kadikular.quantimium.client.screen.ui;

import com.kadikular.quantimium.client.screen.QuantumMachineScreen;
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
 * A page a rail tab opens: drawn in the panel's own frame, over it, so the screen doesn't jump. Its Back tab
 * is always at the top of the rail; Escape goes back too, not out of the screen.
 */
public abstract class Page implements QuantumOverlay {

    private final Supplier<Rect2i> panel;
    private final Component title;
    private boolean closed;
    protected int left;
    protected int top;
    protected int width;
    protected int height;

    protected Page(Supplier<Rect2i> panel, Component title) {
        this.panel = panel;
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
        return new Rect2i(left, top, width + QuantumMachineScreen.TAB_SIZE, height);
    }

    private int backX() {
        return left + width - 1;
    }

    private int backY() {
        return top + QuantumMachineScreen.TAB_TOP;
    }

    private boolean overBack(double mouseX, double mouseY) {
        return QuantumWidgets.hit(mouseX, mouseY, backX(), backY(), QuantumMachineScreen.TAB_SIZE, QuantumMachineScreen.TAB_SIZE);
    }

    @Override
    public final void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float partialTick) {
        graphics.nextStratum();
        graphics.fill(left, top, left + width, top + height, Rail.FILL);
        QuantumWidgets.outline(graphics, left, top, width, height, Rail.EDGE);
        graphics.fill(left + 3, top + 15, left + width - 3, top + 16, Rail.EDGE);
        graphics.text(font, title, left + 6, top + 4, QuantumUiColours.ACCENT, false);

        boolean overBack = overBack(mouseX, mouseY);
        Rail.tab(graphics, backX(), backY(), QuantumMachineScreen.TAB_SIZE, false, overBack);
        GuiIcon.BACK.draw(graphics, backX() + 3, backY() + 3);

        renderPage(graphics, font, mouseX, mouseY);
        if (overBack) graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.quantimium.page.back")), mouseX, mouseY);
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
    protected static void button(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int w, int h, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, Rail.LIT_FILL);
        QuantumWidgets.outline(graphics, x, y, w, h, hovered ? Rail.HOVER_EDGE : Rail.LIT_EDGE);
        graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, Rail.TEXT, false);
    }
}
