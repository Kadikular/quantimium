package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The priority page every block with a priority opens from its rail: type the number, click a step, or
 * scroll over the page (shift for tens). Each change is sent as it's made.
 */
public final class PriorityPage extends Page {

    private static final int[] STEPS = {-100, -10, -1, 1, 10, 100};
    private static final int STEP_WIDTH = 24;
    private static final int STEP_HEIGHT = 16;
    private static final int STEP_GAP = 2;
    private static final int FIELD_WIDTH = 64;
    private static final int FIELD_HEIGHT = 16;

    private final IntSupplier value;
    private final IntConsumer set;
    private final int max;
    /** What's being typed, or null while it shows the value as it is. */
    private String typed;

    public PriorityPage(Supplier<Rect2i> panel, IntSupplier value, IntConsumer set, int max) {
        super(panel, Component.translatable("gui.quantimium.priority"));
        this.value = value;
        this.set = set;
        this.max = max;
    }

    private int fieldX() {
        return left + (width - FIELD_WIDTH) / 2;
    }

    private int fieldY() {
        return top + 30;
    }

    private int stepX(int index) {
        int row = STEPS.length * STEP_WIDTH + (STEPS.length - 1) * STEP_GAP + 6;
        int x = left + (width - row) / 2 + index * (STEP_WIDTH + STEP_GAP);
        return index >= STEPS.length / 2 ? x + 6 : x;
    }

    private int stepY() {
        return fieldY() + FIELD_HEIGHT + 10;
    }

    private void change(int to) {
        int clamped = Math.clamp(to, -max, max);
        if (clamped != value.getAsInt()) set.accept(clamped);
    }

    @Override
    protected void renderPage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int x = fieldX();
        int y = fieldY();
        graphics.fill(x, y, x + FIELD_WIDTH, y + FIELD_HEIGHT, 0xFF090A14);
        QuantumWidgets.outline(graphics, x, y, FIELD_WIDTH, FIELD_HEIGHT, 0xFF343A5E);
        String shown = typed != null ? typed : Integer.toString(value.getAsInt());
        int textX = x + (FIELD_WIDTH - font.width(shown)) / 2;
        graphics.text(font, shown, textX, y + 4, Rail.TEXT, false);
        if (typed != null && (System.currentTimeMillis() / 500) % 2 == 0) {
            graphics.fill(textX + font.width(shown) + 1, y + 3, textX + font.width(shown) + 2, y + 13, QuantumUiColours.ACCENT);
        }

        for (int i = 0; i < STEPS.length; i++) {
            String label = (STEPS[i] > 0 ? "+" : "") + STEPS[i];
            boolean hovered = QuantumWidgets.hit(mouseX, mouseY, stepX(i), stepY(), STEP_WIDTH, STEP_HEIGHT);
            button(graphics, font, label, stepX(i), stepY(), STEP_WIDTH, STEP_HEIGHT, hovered);
        }

        Component hint = Component.translatable("gui.quantimium.priority.hint");
        graphics.text(font, hint, left + (width - font.width(hint)) / 2, stepY() + STEP_HEIGHT + 10, Rail.TEXT_DIM, false);
    }

    @Override
    protected void pageClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        for (int i = 0; i < STEPS.length; i++) {
            if (QuantumWidgets.hit(mouseX, mouseY, stepX(i), stepY(), STEP_WIDTH, STEP_HEIGHT)) {
                click();
                typed = null;
                change(value.getAsInt() + STEPS[i]);
                return;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return true;
        typed = null;
        change(value.getAsInt() + (scrollY > 0 ? 1 : -1) * (shift() ? 10 : 1));
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        String now = typed == null ? "" : typed;
        if (codepoint >= '0' && codepoint <= '9' && now.replace("-", "").length() < Integer.toString(max).length()) {
            typed = now + (char) codepoint;
        } else if (codepoint == '-') {
            typed = now.startsWith("-") ? now.substring(1) : "-" + now;
        } else {
            return true;
        }
        apply();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String now = typed == null ? Integer.toString(value.getAsInt()) : typed;
            typed = now.isEmpty() ? "" : now.substring(0, now.length() - 1);
            apply();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            typed = null;
            return true;
        }
        return super.keyPressed(keyCode);
    }

    /** Sends what's typed once it reads as a number; "" and "-" count as 0. */
    private void apply() {
        String digits = typed.replace("-", "");
        int parsed = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        change(typed.startsWith("-") ? -parsed : parsed);
    }
}
