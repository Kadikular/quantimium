package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.menu.LogisticsControllerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/** A Logistics Controller: what's on its network, its power, and its channel on a tab. Layout: tools/gui_panels.py logistics_controller. */
public class LogisticsControllerScreen extends QuantumMachineScreen<LogisticsControllerMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/logistics_controller.png");
    /** Clear of the power bar on the left. */
    private static final int TEXT_X = 16;
    private static final int TEXT_Y = 22;
    private static final int LINE = 11;

    public LogisticsControllerScreen(LogisticsControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(channelTab(menu));
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
        return menu.energy();
    }

    @Override
    protected int maxEnergyStored() {
        return LaserTuning.LOGISTICS_CAPACITY;
    }

    private boolean clash() {
        return menu.get(LogisticsControllerMenu.CLASH) != 0;
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(clash() ? "gui.quantimium.logistics_controller.status.clash"
                : menu.energy() < LaserTuning.LOGISTICS_FE_PER_MOVE ? "gui.quantimium.logistics_controller.status.unpowered"
                : "gui.quantimium.logistics_controller.status.ready");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(clash() ? "gui.quantimium.logistics_controller.status.clash.tip"
                : "gui.quantimium.logistics_controller.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return clash() ? PanelTheme.Tone.WARN : menu.energy() < LaserTuning.LOGISTICS_FE_PER_MOVE ? PanelTheme.Tone.IDLE : PanelTheme.Tone.OK;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos + TEXT_X;
        int y = topPos + TEXT_Y;
        line(graphics, Component.translatable("gui.quantimium.logistics_controller.storage", menu.get(LogisticsControllerMenu.STORAGE)), x, y);
        line(graphics, Component.translatable("gui.quantimium.logistics_controller.providers", menu.get(LogisticsControllerMenu.PROVIDERS)), x, y + LINE);
        line(graphics, Component.translatable("gui.quantimium.logistics_controller.requesters", menu.get(LogisticsControllerMenu.REQUESTERS)), x, y + 2 * LINE);
        line(graphics, Component.translatable("gui.quantimium.channel", com.kadikular.quantimium.laser.Channels.label(menu.channel())), x, y + 3 * LINE);
        line(graphics, Component.translatable("gui.quantimium.logistics_controller.energy",
                String.format(Locale.ROOT, "%,d", menu.energy()), String.format(Locale.ROOT, "%,d", LaserTuning.LOGISTICS_CAPACITY)), x, y + 4 * LINE);
    }

    private void line(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(font, text, x, y, textColour(), false);
    }
}
