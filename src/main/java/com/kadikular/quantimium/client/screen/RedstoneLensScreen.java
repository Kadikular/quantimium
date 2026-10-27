package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.laser.RedstoneSettings;
import com.kadikular.quantimium.menu.RedstoneLensMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * A Redstone Lens's settings: on the rail its mode (click to cycle), its signal's strength (click, right-click or
 * scroll) and whether it's strong or weak; on the panel the item it counts and how many, with -64, -1, +1 and +64
 * buttons beside the count (shift: ten times as many), dimmed unless it counts. Click the count to type one.
 */
public class RedstoneLensScreen extends QuantumMachineScreen<RedstoneLensMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/redstone_lens.png");
    private static final Identifier ALWAYS = Identifier.withDefaultNamespace("textures/block/redstone_torch.png");
    private static final Identifier FORWARD = Identifier.withDefaultNamespace("textures/item/repeater.png");
    private static final Identifier STRONG = Identifier.withDefaultNamespace("textures/block/redstone_torch.png");
    private static final Identifier WEAK = Identifier.withDefaultNamespace("textures/block/redstone_torch_off.png");
    /** The count's buttons: what each adds, and where (the count's well is between them, tools/gui_panels.py). */
    private static final int[] STEPS = {-64, -1, 1, 64};
    private static final int[] STEP_X = {41, 63, 121, 137};
    private static final int[] STEP_W = {20, 14, 14, 20};
    private static final int COUNT_X = 79;
    private static final int COUNT_W = 40;
    private static final int ROW_Y = 35;
    private static final int ROW_H = 16;

    /** The count being typed, sent as it changes; null while it isn't. */
    private String typed;

    public RedstoneLensScreen(RedstoneLensMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        FilterLensScreen.restoreCursor(minecraft);
    }

    private RedstoneSettings settings() {
        return menu.settings();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                // The mode: < and ≥ drawn from the shared icons, always on a lit torch, sending on a repeater.
                Tab.glyph(() -> switch (settings().mode()) {
                                    case BELOW -> GuiIcon.LESS;
                                    case AT_LEAST -> GuiIcon.AT_LEAST;
                                    default -> null;
                                }, () -> press(RedstoneLensMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable("gui.quantimium.redstone_lens.mode." + settings().mode().getSerializedName()),
                                        hint("gui.quantimium.tab.cycle")))
                        .texture(() -> settings().mode() == RedstoneSettings.Mode.FORWARD ? FORWARD : ALWAYS)
                        .onRightClick(() -> {
                            for (int i = 0; i < RedstoneSettings.Mode.values().length - 1; i++) press(RedstoneLensMenu.BUTTON_MODE);
                        }),
                // Its strength, on the tab: click or scroll up for more, right-click or scroll down for less.
                Tab.of(() -> Component.literal(Integer.toString(settings().level())), () -> press(RedstoneLensMenu.BUTTON_STRONGER),
                                () -> List.of(Component.translatable("gui.quantimium.redstone_lens.level", settings().level())))
                        .onRightClick(() -> press(RedstoneLensMenu.BUTTON_WEAKER))
                        .onScroll(step -> press(step > 0 ? RedstoneLensMenu.BUTTON_STRONGER : RedstoneLensMenu.BUTTON_WEAKER))
                        .visible(() -> settings().mode() != RedstoneSettings.Mode.FORWARD),
                Tab.of(Component::empty, () -> press(RedstoneLensMenu.BUTTON_STRONG),
                                () -> List.of(Component.translatable(settings().strong() ? "gui.quantimium.redstone_lens.strong" : "gui.quantimium.redstone_lens.weak")))
                        .texture(() -> settings().strong() ? STRONG : WEAK));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        boolean counts = settings().mode().counts();
        for (int i = 0; i < STEPS.length; i++) {
            int x = leftPos + STEP_X[i];
            int y = topPos + ROW_Y;
            boolean hovered = counts && QuantumWidgets.hit(mouseX, mouseY, x, y, STEP_W[i], ROW_H);
            rail().button(graphics, x, y, STEP_W[i], ROW_H, false, hovered);
            String label = (STEPS[i] > 0 ? "+" : "") + STEPS[i];
            graphics.text(font, label, x + (STEP_W[i] - font.width(label)) / 2, y + 4, counts ? rail().text() : rail().textDim(), false);
        }
        if (!counts) typed = null;
        String count = typed != null ? typed : Integer.toString(settings().count());
        int countX = leftPos + COUNT_X + (COUNT_W - font.width(count)) / 2;
        graphics.text(font, count, countX, topPos + ROW_Y + 4, counts ? rail().text() : rail().textDim(), false);
        if (typed != null) {
            QuantumWidgets.outline(graphics, leftPos + COUNT_X, topPos + ROW_Y, COUNT_W, ROW_H, rail().litEdge());
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int caret = countX + font.width(count) + 1;
                graphics.fill(caret, topPos + ROW_Y + 3, caret + 1, topPos + ROW_Y + 13, rail().text());
            }
        }
        // Not counting: the item's slot is dimmed with the buttons.
        if (!counts) {
            int x = leftPos + RedstoneLensMenu.ITEM_X;
            int y = topPos + RedstoneLensMenu.ITEM_Y;
            graphics.fill(x, y, x + 16, y + 16, 0x80000000);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (activeOverlay() == null && event.button() == 0 && settings().mode().counts()) {
            boolean onCount = QuantumWidgets.hit(event.x(), event.y(), leftPos + COUNT_X, topPos + ROW_Y, COUNT_W, ROW_H);
            typed = onCount ? "" : null;
            if (onCount) return true;
            for (int i = 0; i < STEPS.length; i++) {
                if (QuantumWidgets.hit(event.x(), event.y(), leftPos + STEP_X[i], topPos + ROW_Y, STEP_W[i], ROW_H)) {
                    int step = STEPS[i] * (event.hasShiftDown() ? 10 : 1);
                    press(RedstoneLensMenu.BUTTON_COUNT + (int) Math.clamp((long) settings().count() + step, 0, RedstoneSettings.MAX_COUNT));
                    if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (typed == null) return super.charTyped(event);
        int codepoint = event.codepoint();
        if (codepoint >= '0' && codepoint <= '9' && typed.length() < Integer.toString(RedstoneSettings.MAX_COUNT).length()) {
            typed = (typed.equals("0") ? "" : typed) + (char) codepoint;
            sendTyped();
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (typed == null) return super.keyPressed(event);
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            typed = typed.isEmpty() ? "" : typed.substring(0, typed.length() - 1);
            sendTyped();
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            typed = null;
        }
        // Typing, no other key does its usual thing (E doesn't close the screen).
        return true;
    }

    /** Sends what's typed; nothing typed is 0. */
    private void sendTyped() {
        long count = typed.isEmpty() ? 0 : Long.parseLong(typed);
        press(RedstoneLensMenu.BUTTON_COUNT + (int) Math.min(count, RedstoneSettings.MAX_COUNT));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        if (!settings().mode().counts()) return List.of();
        return List.of(new Rect2i(leftPos + RedstoneLensMenu.ITEM_X, topPos + RedstoneLensMenu.ITEM_Y, 16, 16));
    }

    @Override
    public int ghostOffset() {
        return 0;
    }

    @Override
    public int ghostContainerId() {
        return menu.containerId;
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected boolean hasEnergy() {
        return false;
    }

    @Override
    protected int energyStored() {
        return 0;
    }

    @Override
    protected int maxEnergyStored() {
        return 0;
    }

    @Override
    protected Component statusLabel() {
        return Component.empty();
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of();
    }
}
