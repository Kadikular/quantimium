package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.FoldedTesseractItem;
import com.kadikular.quantimium.menu.FoldCoreMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * The Fold Core: the socket, the chamber it reads, what it makes of what's in the socket (why not, or
 * what a fold will take), and the button that starts it. The seal's progress runs along the bottom.
 * Layout: tools/gui_panels.py fold_core.
 */
public class FoldCoreScreen extends QuantumMachineScreen<FoldCoreMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/fold_core.png");
    private static final int TEXT_X = 48;
    private static final int TEXT_W = 120;
    private static final int CHAMBER_Y = 17;
    private static final int STATUS_Y = 28;
    private static final int LINE_HEIGHT = 9;
    private static final int STATUS_LINES = 3;
    private static final int BUTTON_X = 48;
    private static final int BUTTON_Y = 56;
    private static final int BUTTON_W = 56;
    private static final int BUTTON_H = 13;
    private static final int COST_X = 108;
    private static final int COST_Y = 59;
    private static final int GAUGE_X = 48;
    private static final int GAUGE_Y = 72;
    private static final int GAUGE_W = 120;
    private static final int GAUGE_H = 3;
    private static final int AZURE = 0xFF3485FF;
    private static final int VIOLET = 0xFFB196FF;
    private static final int READY_FILL = 0xAA1B4FB8;
    private static final int IDLE_FILL = 0x66303650;

    public FoldCoreScreen(FoldCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected Identifier panelTexture() {
        return PANEL;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected int energyStored() {
        return menu.getEnergyStored();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.getMaxEnergyStored();
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        FoldCoreBlockEntity core = menu.getBlockEntity();
        ghost(graphics);

        String chamber = font.plainSubstrByWidth(core.getChamberLine().getString(), TEXT_W);
        graphics.text(font, chamber, leftPos + TEXT_X, topPos + CHAMBER_Y, dimColour(), false);
        // Short enough to read at a glance; past three lines it trails off, and the status's tooltip
        // has the whole of it.
        List<FormattedText> lines = font.getSplitter().splitLines(core.getMessage().getString(), TEXT_W, Style.EMPTY);
        for (int i = 0; i < Math.min(STATUS_LINES, lines.size()); i++) {
            String line = lines.get(i).getString();
            if (i == STATUS_LINES - 1 && lines.size() > STATUS_LINES) {
                line = font.plainSubstrByWidth(line, TEXT_W - font.width("…")).stripTrailing() + "…";
            }
            graphics.text(font, line, leftPos + TEXT_X, topPos + STATUS_Y + i * LINE_HEIGHT, textColour(), false);
        }

        boolean ready = menu.getStatus() == FoldCoreBlockEntity.Status.READY;
        boolean hovered = ready && within(mouseX, mouseY);
        int fill = hovered ? AZURE : ready ? READY_FILL : IDLE_FILL;
        graphics.fill(leftPos + BUTTON_X, topPos + BUTTON_Y, leftPos + BUTTON_X + BUTTON_W,
                topPos + BUTTON_Y + BUTTON_H, fill);
        graphics.centeredText(font, buttonLabel(), leftPos + BUTTON_X + BUTTON_W / 2, topPos + BUTTON_Y + 3,
                ready ? 0xFFFFFFFF : dimColour());

        if (menu.getCost() > 0) {
            graphics.text(font, Component.translatable("gui.quantimium.fold_core.cost",
                            String.format(Locale.ROOT, "%,d", menu.getCost())),
                    leftPos + COST_X, topPos + COST_Y, dimColour(), false);
        }

        int filled = GAUGE_W * Math.min(menu.getSealed(), FoldCoreBlockEntity.SEAL_TICKS) / FoldCoreBlockEntity.SEAL_TICKS;
        if (filled > 0) {
            graphics.fill(leftPos + GAUGE_X, topPos + GAUGE_Y, leftPos + GAUGE_X + filled, topPos + GAUGE_Y + GAUGE_H,
                    VIOLET);
        }
    }

    /** A dimmed Tesseract in the empty socket, so it's clear what goes there. */
    private void ghost(GuiGraphicsExtractor graphics) {
        if (!menu.getSlot(0).getItem().isEmpty()) return;
        int x = leftPos + FoldCoreMenu.SOCKET_X;
        int y = topPos + FoldCoreMenu.SOCKET_Y;
        graphics.fakeItem(new ItemStack(ModItems.TESSERACT.get()), x, y);
        graphics.nextStratum();
        graphics.fill(x, y, x + 16, y + 16, 0xA0121424);
    }

    private Component buttonLabel() {
        boolean unfold = menu.getSlot(0).getItem().getItem() instanceof FoldedTesseractItem;
        if (menu.getStatus() == FoldCoreBlockEntity.Status.SEALING) {
            return Component.translatable(unfold ? "gui.quantimium.fold_core.unfolding" : "gui.quantimium.fold_core.folding");
        }
        return Component.translatable(unfold ? "gui.quantimium.fold_core.unfold" : "gui.quantimium.fold_core.fold");
    }

    private boolean within(double mouseX, double mouseY) {
        return mouseX >= leftPos + BUTTON_X && mouseX < leftPos + BUTTON_X + BUTTON_W
                && mouseY >= topPos + BUTTON_Y && mouseY < topPos + BUTTON_Y + BUTTON_H;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && activeOverlay() == null && within(event.x(), event.y())
                && menu.getStatus() == FoldCoreBlockEntity.Status.READY
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FoldCoreBlockEntity.BUTTON_FOLD);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private String statusKey() {
        return menu.getStatus().name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable("gui.quantimium.fold_core.status." + statusKey());
    }

    /** The whole message, for when it runs past the lines the panel has room for. */
    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.fold_core.status." + statusKey()),
                menu.getBlockEntity().getMessage());
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatus()) {
            case READY, SEALING -> PanelTheme.Tone.OK;
            case EMPTY -> PanelTheme.Tone.IDLE;
            case NO_POWER -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (within(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, font.split(menu.getStatus() == FoldCoreBlockEntity.Status.READY
                    ? Component.translatable("gui.quantimium.fold_core.fold.tip")
                    : menu.getBlockEntity().getMessage(), 200), mouseX, mouseY);
            return;
        }
        int x = leftPos + TEXT_X;
        int y = topPos + STATUS_Y;
        if (mouseX >= x && mouseX < x + TEXT_W && mouseY >= y && mouseY < y + STATUS_LINES * LINE_HEIGHT) {
            graphics.setTooltipForNextFrame(font, font.split(menu.getBlockEntity().getMessage(), 200), mouseX, mouseY);
        }
    }
}
