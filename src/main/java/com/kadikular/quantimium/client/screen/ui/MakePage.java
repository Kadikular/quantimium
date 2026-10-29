package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * A terminal's craft page: how many of something to make and keep in storage, at most what can be made. Type the number,
 * click a step, or scroll (shift for tens); Craft or Enter sends it. A fluid is counted in buckets, typed to the mB (1.25).
 */
public final class MakePage extends Page {

    private static final int[] STEPS = {-64, -10, -1, 1, 10, 64};
    private static final int STEP_WIDTH = 24;
    private static final int STEP_HEIGHT = 16;
    private static final int STEP_GAP = 2;
    private static final int FIELD_WIDTH = 64;
    private static final int FIELD_HEIGHT = 16;
    private static final int MAKE_WIDTH = 50;

    private final ItemStack shown;
    private final long most;
    private final boolean fluid;
    private final LongConsumer make;
    private static final int BUCKET = net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME;
    /** In items, or mB for a fluid. */
    private long amount;
    /** What's being typed, or null while it shows the amount. */
    private String typed;

    /** {@code most} in items, or for a fluid in mB; {@code make} is given the same. */
    public MakePage(Supplier<Rect2i> panel, Rail rail, ItemStack shown, long most, boolean fluid, LongConsumer make) {
        super(panel, rail, Component.translatable("gui.quantimium.make"));
        this.shown = shown;
        this.most = Math.max(1, most);
        this.amount = fluid ? Math.min(this.most, BUCKET) : 1;
        this.fluid = fluid;
        this.make = make;
    }

    private int fieldX() {
        return left + (width - FIELD_WIDTH) / 2;
    }

    private int fieldY() {
        return top + 38;
    }

    private int stepX(int index) {
        int row = STEPS.length * STEP_WIDTH + (STEPS.length - 1) * STEP_GAP + 6;
        int x = left + (width - row) / 2 + index * (STEP_WIDTH + STEP_GAP);
        return index >= STEPS.length / 2 ? x + 6 : x;
    }

    private int stepY() {
        return fieldY() + FIELD_HEIGHT + 8;
    }

    private int makeX() {
        return left + (width - MAKE_WIDTH * 2 - 4) / 2;
    }

    private int makeY() {
        return stepY() + STEP_HEIGHT + 8;
    }

    private void set(long to) {
        amount = Math.clamp(to, 1, most);
    }

    /** A step's worth: an item, or a bucket. */
    private long unit() {
        return fluid ? BUCKET : 1;
    }

    /** How an amount reads: a count, or buckets to the mB. */
    private String written(long value) {
        if (!fluid) return String.format(Locale.ROOT, "%,d", value);
        String buckets = new java.math.BigDecimal(value).movePointLeft(3).stripTrailingZeros().toPlainString();
        return buckets + " B";
    }

    private void send() {
        click();
        make.accept(amount);
        close();
    }

    /** What's typed, read: items, or buckets to the mB. */
    private void readTyped() {
        if (typed.isEmpty() || typed.equals(".")) return;
        set(fluid ? Math.round(Double.parseDouble(typed) * BUCKET) : Long.parseLong(typed));
    }

    @Override
    protected void renderPage(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        // What, and how many can be made.
        Component name = shown.getHoverName();
        int nameX = left + (width - font.width(name) - 20) / 2;
        var tile = com.kadikular.quantimium.recipe.FluidEntry.fluidOf(shown);
        if (fluid && !tile.isEmpty()) FluidSprites.tile(graphics, tile.copyWithAmount(1_000), nameX, top + 16);
        else graphics.item(shown.copyWithCount(1), nameX, top + 16);
        graphics.text(font, name, nameX + 20, top + 20, rail.text(), false);

        int x = fieldX();
        int y = fieldY();
        graphics.fill(x, y, x + FIELD_WIDTH, y + FIELD_HEIGHT, rail.well());
        QuantumWidgets.outline(graphics, x, y, FIELD_WIDTH, FIELD_HEIGHT, rail.litEdge());
        String unit = fluid && typed != null ? " B" : "";
        String value = typed != null ? typed : written(amount);
        int textX = x + (FIELD_WIDTH - font.width(value + unit)) / 2;
        graphics.text(font, value + unit, textX, y + 4, rail.text(), false);
        if (typed != null && (System.currentTimeMillis() / 500) % 2 == 0) {
            graphics.fill(textX + font.width(value) + 1, y + 3, textX + font.width(value) + 2, y + 13, QuantumUiColours.ACCENT);
        }

        for (int i = 0; i < STEPS.length; i++) {
            String label = (STEPS[i] > 0 ? "+" : "") + STEPS[i];
            button(graphics, font, label, stepX(i), stepY(), STEP_WIDTH, STEP_HEIGHT,
                    QuantumWidgets.hit(mouseX, mouseY, stepX(i), stepY(), STEP_WIDTH, STEP_HEIGHT));
        }
        button(graphics, font, Component.translatable("gui.quantimium.make.max").getString(), makeX(), makeY(), MAKE_WIDTH, STEP_HEIGHT,
                QuantumWidgets.hit(mouseX, mouseY, makeX(), makeY(), MAKE_WIDTH, STEP_HEIGHT));
        button(graphics, font, Component.translatable("gui.quantimium.make.go").getString(), makeX() + MAKE_WIDTH + 4, makeY(), MAKE_WIDTH, STEP_HEIGHT,
                QuantumWidgets.hit(mouseX, mouseY, makeX() + MAKE_WIDTH + 4, makeY(), MAKE_WIDTH, STEP_HEIGHT));

        Component hint = Component.translatable("gui.quantimium.make.most", written(most));
        graphics.text(font, hint, left + (width - font.width(hint)) / 2, makeY() + STEP_HEIGHT + 8, rail.textDim(), false);
    }

    @Override
    protected void pageClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        for (int i = 0; i < STEPS.length; i++) {
            if (QuantumWidgets.hit(mouseX, mouseY, stepX(i), stepY(), STEP_WIDTH, STEP_HEIGHT)) {
                click();
                typed = null;
                set(amount + STEPS[i] * unit());
                return;
            }
        }
        if (QuantumWidgets.hit(mouseX, mouseY, makeX(), makeY(), MAKE_WIDTH, STEP_HEIGHT)) {
            click();
            typed = null;
            set(most);
            return;
        }
        if (QuantumWidgets.hit(mouseX, mouseY, makeX() + MAKE_WIDTH + 4, makeY(), MAKE_WIDTH, STEP_HEIGHT)) send();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return true;
        typed = null;
        set(amount + (scrollY > 0 ? 1 : -1) * (shift() ? 10 : 1) * unit());
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        String now = typed == null ? "" : typed;
        boolean point = fluid && codepoint == '.' && !now.contains(".");
        // A fluid to the mB: three places at most.
        boolean room = !now.contains(".") || now.length() - now.indexOf('.') <= 3;
        if ((codepoint >= '0' && codepoint <= '9' && room || point) && now.length() < 12) {
            typed = now + (char) codepoint;
            readTyped();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String now = typed == null ? (fluid ? written(amount).replace(" B", "") : Long.toString(amount)) : typed;
            typed = now.isEmpty() ? "" : now.substring(0, now.length() - 1);
            readTyped();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(keyCode);
    }
}
