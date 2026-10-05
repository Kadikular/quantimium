package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.FieldControlBlock;
import com.kadikular.quantimium.block.entity.FieldControlBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.menu.FieldControlMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The field control blocks (Flux Maintainer, Anomaly Siphon and its basic form, Field Regulator, Flux
 * Suppressor): the field here against the setpoints, the draw, and (where it pulls anomaly) the
 * fragment slot with its progress. The F, A and C tabs step the flux floor, the anomaly ceiling and the
 * Suppressor's flux ceiling; each shows only on the blocks that have it.
 */
public class FieldControlScreen extends QuantumMachineScreen<FieldControlMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/field_control.png");
    private static final Identifier PANEL_FRAGMENTS = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/field_control_fragments.png");

    private static final int INFO_X = 18;
    private static final int INFO_Y = 21;
    private static final int LINE = 11;
    /** The fragment gauge (tools/gui_panels.py: "fragment progress"). */
    private static final int GAUGE_X = 132;
    private static final int GAUGE_Y = 46;
    private static final int GAUGE_WIDTH = 38;
    private static final int GAUGE_HEIGHT = 4;
    private static final int VIOLET = 0xFF8A60F0;

    public FieldControlScreen(FieldControlMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        List<Tab> tabs = new ArrayList<>();
        FieldControlBlock.Kind kind = menu.kind();
        if (kind.raisesFlux()) {
            tabs.add(Tab.of(() -> Component.literal("F"), () -> press(FieldControlBlockEntity.BUTTON_FLOOR),
                            () -> List.of(Component.translatable("gui.quantimium.field_control.floor", band(menu.getFloor())),
                                    Component.translatable("gui.quantimium.field_control.floor.tip")))
                    .icon(new ItemStack(Items.GLOWSTONE_DUST)));
        }
        if (kind.setsCeiling()) {
            // A Siphon's ceiling is for anomaly; a Suppressor's, for flux.
            String key = kind.pullsFlux() ? "gui.quantimium.field_control.flux_ceiling" : "gui.quantimium.field_control.ceiling";
            tabs.add(Tab.of(() -> Component.literal(kind.pullsFlux() ? "C" : "A"), () -> press(FieldControlBlockEntity.BUTTON_CEILING),
                            () -> List.of(Component.translatable(key, menu.getCeiling().label()),
                                    Component.translatable(key + ".tip")))
                    .icon(new ItemStack(kind.pullsFlux() ? Items.LAPIS_LAZULI : Items.AMETHYST_SHARD)));
        }
        return tabs;
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static Component band(FluxBand band) {
        return Component.translatable("flux.quantimium.band." + band.getSerializedName());
    }

    @Override
    protected Identifier panelTexture() {
        return menu.kind().pullsAnomaly() ? PANEL_FRAGMENTS : PANEL;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.MID;
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
    protected List<Component> energyTooltip() {
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(), -1, menu.getAverageFe(), false);
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        FieldControlBlock.Kind kind = menu.kind();
        List<Component> lines = new ArrayList<>(5);
        if (kind.raisesFlux() || kind.pullsFlux()) {
            lines.add(Component.translatable("gui.quantimium.field_control.flux", String.format("%,d", menu.getFlux()),
                    band(FluxBand.of(menu.getFlux()))));
            lines.add(kind.raisesFlux()
                    ? Component.translatable("gui.quantimium.field_control.floor", band(menu.getFloor()))
                    : Component.translatable("gui.quantimium.field_control.flux_ceiling", menu.getCeiling().label()));
        }
        if (kind.pullsAnomaly()) {
            lines.add(Component.translatable("gui.quantimium.field_control.anomaly", String.format("%,d", menu.getAnomaly()),
                    band(FluxBand.of(menu.getAnomaly()))));
            lines.add(kind.setsCeiling()
                    ? Component.translatable("gui.quantimium.field_control.ceiling", menu.getCeiling().label())
                    : Component.translatable("gui.quantimium.field_control.holds_at",
                            String.format("%,.0f", menu.getCeiling().target())));
        }
        lines.add(Component.translatable("gui.quantimium.field_control.draw", String.format("%,d", menu.getAverageFe())));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE,
                    i % 2 == 0 && i < lines.size() - 1 ? textColour() : dimColour(), false);
        }
        if (kind.pullsAnomaly()) {
            int filled = Math.round(GAUGE_WIDTH * menu.getFragmentProgress());
            if (filled > 0) {
                graphics.fill(leftPos + GAUGE_X, topPos + GAUGE_Y, leftPos + GAUGE_X + filled, topPos + GAUGE_Y + GAUGE_HEIGHT,
                        VIOLET);
            }
        }
    }

    private String statusKey() {
        return switch (menu.getStatus()) {
            case FieldControlBlockEntity.STATUS_RAISING -> "gui.quantimium.field_control.status.raising";
            case FieldControlBlockEntity.STATUS_PULLING -> "gui.quantimium.field_control.status.pulling";
            case FieldControlBlockEntity.STATUS_BOTH -> "gui.quantimium.field_control.status.both";
            case FieldControlBlockEntity.STATUS_NO_POWER -> "gui.quantimium.field_control.status.power";
            default -> "gui.quantimium.field_control.status.holding";
        };
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(statusKey());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(statusKey() + ".tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatus()) {
            case FieldControlBlockEntity.STATUS_NO_POWER -> PanelTheme.Tone.BAD;
            case FieldControlBlockEntity.STATUS_HOLDING -> PanelTheme.Tone.IDLE;
            default -> PanelTheme.Tone.OK;
        };
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.kind().pullsAnomaly()) return;
        if (mouseX >= leftPos + GAUGE_X - 1 && mouseX < leftPos + GAUGE_X + GAUGE_WIDTH + 1
                && mouseY >= topPos + GAUGE_Y - 1 && mouseY < topPos + GAUGE_Y + GAUGE_HEIGHT + 1) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.quantimium.field_control.fragment",
                    Math.round(menu.getFragmentProgress() * 100))), mouseX, mouseY);
        }
    }
}
