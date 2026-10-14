package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.menu.LaserEmitterMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * A laser emitter: what it's sending and could send, how far, and its redstone setting on a tab. The
 * flash emitter adds its fuel slot and a flame for what's burning. Layout: tools/gui_panels.py laser_emitter.
 */
public class LaserEmitterScreen extends QuantumMachineScreen<LaserEmitterMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_emitter.png");
    private static final Identifier FLASH_PANEL = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/flash_emitter.png");
    private static final int TEXT_X = 52;
    private static final int TEXT_Y = 20;
    private static final int LINE_HEIGHT = 11;
    /** The burn gauge, over the fuel slot. */
    private static final int BURN_X = 26;
    private static final int BURN_Y = 36;
    private static final int BURN_W = 16;
    private static final int BURN_H = 3;
    private static final int FLAME = 0xFFF0B429;

    public LaserEmitterScreen(LaserEmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private boolean flash() {
        return menu.getBlockEntity().tier() == LaserTuning.Tier.FLASH;
    }

    @Override
    protected Identifier panelTexture() {
        return flash() ? FLASH_PANEL : PANEL;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected int energyStored() {
        return menu.energy();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.capacity();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(redstoneTab(menu::redstone, () -> press(LaserEmitterMenu.BUTTON_REDSTONE)));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(menu.sending() > 0 ? "gui.quantimium.laser_emitter.status.sending"
                : menu.energy() > 0 ? "gui.quantimium.laser_emitter.status.idle" : "gui.quantimium.laser_emitter.status.empty");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.laser_emitter.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return menu.sending() > 0 ? PanelTheme.Tone.OK : menu.energy() > 0 ? PanelTheme.Tone.IDLE : PanelTheme.Tone.WARN;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        LaserTuning.Tier tier = menu.getBlockEntity().tier();
        List<Component> lines = List.of(
                Component.translatable("gui.quantimium.laser_emitter.sending", String.format(Locale.ROOT, "%,d", menu.sending())),
                Component.translatable("gui.quantimium.laser_emitter.rate", String.format(Locale.ROOT, "%,d", tier.rate)),
                Component.translatable("gui.quantimium.laser_emitter.range", tier.range),
                Component.translatable("gui.quantimium.laser_emitter.outputs", menu.getBlockEntity().outputs().size()));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + TEXT_X, topPos + TEXT_Y + i * LINE_HEIGHT, i == 0 ? textColour() : dimColour(), false);
        }
        if (flash()) {
            int filled = Math.round(BURN_W * menu.burn());
            if (filled > 0) graphics.fill(leftPos + BURN_X, topPos + BURN_Y, leftPos + BURN_X + filled, topPos + BURN_Y + BURN_H, FLAME);
        }
    }
}
