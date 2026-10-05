package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.FieldMonitorBlockEntity;
import com.kadikular.quantimium.client.FieldMapRenderer;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.menu.FieldMonitorMenu;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Field Monitor: a map of the field round it (the monitor's own chunk outlined), what it watches
 * for, and how many chunks are alerting. The W tab steps what it watches for. Layout:
 * tools/gui_panels.py field_monitor.
 */
public class FieldMonitorScreen extends QuantumMachineScreen<FieldMonitorMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/field_monitor.png");
    private static final int MAP_X = 8;
    private static final int MAP_Y = 20;
    private static final int INFO_X = 96;
    private static final int INFO_Y = 21;
    private static final int LINE = 11;

    /** The latest map sent for the open screen. */
    @Nullable
    private static FieldSurveyPayload map;

    public FieldMonitorScreen(FieldMonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DEFAULT_IMAGE_WIDTH, 106);
        map = null;
    }

    /** From the server, once a second while the screen is open. */
    public static void setMap(FieldSurveyPayload survey) {
        map = survey;
    }

    @Override
    protected Identifier panelTexture() {
        return PANEL;
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
    protected PanelTheme theme() {
        return PanelTheme.MID;
    }

    @Override
    protected List<Tab> createTabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.of(() -> Component.literal("W"), this::press,
                        () -> List.of(Component.translatable("gui.quantimium.field_monitor.watch",
                                        Component.translatable("gui.quantimium.field_monitor.watch." + menu.getWatch().key())),
                                Component.translatable("gui.quantimium.field_monitor.watch.tip")))
                .icon(new ItemStack(Items.REDSTONE)));
        return tabs;
    }

    private void press() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FieldMonitorBlockEntity.BUTTON_WATCH);
        }
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        if (map != null) FieldMapRenderer.draw(graphics, map, leftPos + MAP_X, topPos + MAP_Y, 0, 0);
        List<Component> lines = List.of(
                Component.translatable("gui.quantimium.field_monitor.watching"),
                Component.translatable("gui.quantimium.field_monitor.watch." + menu.getWatch().key()),
                Component.translatable("gui.quantimium.field_monitor.alerting", menu.getAlerting(), menu.getWatched()),
                Component.translatable("gui.quantimium.field_monitor.peak_flux", FluxMeterReadout.flux(menu.getPeakFlux()),
                        band(menu.getPeakFlux())),
                Component.translatable("gui.quantimium.field_monitor.peak_anomaly", FluxMeterReadout.flux(menu.getPeakAnomaly()),
                        band(menu.getPeakAnomaly())));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE + (i >= 2 ? 6 : 0),
                    i == 1 || i == 2 ? textColour() : dimColour(), false);
        }
    }

    private static Component band(int value) {
        return Component.translatable("flux.quantimium.band." + FluxBand.of(value).getSerializedName());
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(menu.getAlerting() > 0 ? "gui.quantimium.field_monitor.status.alert"
                : "gui.quantimium.field_monitor.status.clear");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(menu.getAlerting() > 0 ? "gui.quantimium.field_monitor.status.alert.tip"
                : "gui.quantimium.field_monitor.status.clear.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return menu.getAlerting() > 0 ? PanelTheme.Tone.BAD : PanelTheme.Tone.OK;
    }
}
