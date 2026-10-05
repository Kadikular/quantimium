package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.RelayModuleBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.menu.RelayModuleMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * The Relay module's hold: each Tesseract with a button saying what can be done with the pod it leads
 * to: Recall its double into the Sophon slot, or Send the Sophon there. Layout: tools/gui_panels.py
 * relay_module.
 */
public class RelayModuleScreen extends QuantumMachineScreen<RelayModuleMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/relay_module.png");
    private static final int BUTTON_HEIGHT = 12;

    public RelayModuleScreen(RelayModuleMenu menu, Inventory playerInventory, Component title) {
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

    private boolean sophonHeld() {
        return !menu.getBlockEntity().getInventory().getStackInSlot(RelayModuleBlockEntity.SOPHON_SLOT).isEmpty();
    }

    /** The button beside a slot: its label key and whether it can be pressed, or null for none. */
    private Component label(int slot) {
        return switch (menu.linkState(slot)) {
            case RelayModuleBlockEntity.LINK_DOUBLE -> Component.translatable("gui.quantimium.relay.recall");
            case RelayModuleBlockEntity.LINK_EMPTY_POD -> Component.translatable("gui.quantimium.relay.send");
            case RelayModuleBlockEntity.LINK_NOT_A_POD -> Component.translatable("gui.quantimium.relay.not_a_pod");
            default -> null;
        };
    }

    private boolean enabled(int slot) {
        return switch (menu.linkState(slot)) {
            case RelayModuleBlockEntity.LINK_DOUBLE -> !sophonHeld();
            case RelayModuleBlockEntity.LINK_EMPTY_POD -> sophonHeld();
            default -> false;
        };
    }

    private int buttonX(int slot) {
        return leftPos + RelayModuleMenu.LINK_X[slot / 3] + RelayModuleMenu.BUTTON_OFFSET;
    }

    private int buttonY(int slot) {
        return topPos + RelayModuleMenu.LINK_Y + (slot % 3) * 18 + 2;
    }

    private boolean over(int slot, double mouseX, double mouseY) {
        return mouseX >= buttonX(slot) && mouseX < buttonX(slot) + RelayModuleMenu.BUTTON_WIDTH
                && mouseY >= buttonY(slot) && mouseY < buttonY(slot) + BUTTON_HEIGHT;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        for (int slot = 0; slot < RelayModuleBlockEntity.LINKS; slot++) {
            Component label = label(slot);
            if (label == null) continue;
            boolean enabled = enabled(slot);
            int x = buttonX(slot);
            int y = buttonY(slot);
            int fill = !enabled ? 0x40202840 : over(slot, mouseX, mouseY) ? 0xFF3485FF : 0xAA1B4FB8;
            graphics.fill(x, y, x + RelayModuleMenu.BUTTON_WIDTH, y + BUTTON_HEIGHT, fill);
            graphics.centeredText(font, label, x + RelayModuleMenu.BUTTON_WIDTH / 2, y + 2,
                    enabled ? 0xFFFFFFFF : dimColour());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && activeOverlay() == null && minecraft != null && minecraft.gameMode != null) {
            for (int slot = 0; slot < RelayModuleBlockEntity.LINKS; slot++) {
                if (!over(slot, mouseX, mouseY) || !enabled(slot)) continue;
                boolean recall = menu.linkState(slot) == RelayModuleBlockEntity.LINK_DOUBLE;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot * 2 + (recall ? 0 : 1));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable("gui.quantimium.relay.status");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.relay.status.tip"));
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int slot = 0; slot < RelayModuleBlockEntity.LINKS; slot++) {
            if (label(slot) == null || !over(slot, mouseX, mouseY)) continue;
            String key = switch (menu.linkState(slot)) {
                case RelayModuleBlockEntity.LINK_DOUBLE -> "gui.quantimium.relay.recall.tip";
                case RelayModuleBlockEntity.LINK_EMPTY_POD -> "gui.quantimium.relay.send.tip";
                default -> "gui.quantimium.relay.not_a_pod.tip";
            };
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(key)), mouseX, mouseY);
        }
    }
}
