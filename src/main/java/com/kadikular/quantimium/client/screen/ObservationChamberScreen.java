package com.kadikular.quantimium.client.screen;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.menu.ObservationChamberMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class ObservationChamberScreen extends QuantumMachineScreen<ObservationChamberMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/observation_chamber.png");

    public ObservationChamberScreen(ObservationChamberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        // A lava bucket while excess Trace is destroyed, a chest while it is kept.
        return List.of(Tab.of(
                () -> Component.literal(menu.isVoidExcessTrace() ? "§cT" : "§aT"),
                () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ObservationChamberMenu.BUTTON_VOID_TRACE),
                () -> List.of(
                        Component.translatable(menu.isVoidExcessTrace()
                                ? "gui.quantimium.observation_chamber.void_on"
                                : "gui.quantimium.observation_chamber.void_off"),
                        Component.translatable("gui.quantimium.observation_chamber.void.tip"),
                        Component.translatable("gui.quantimium.observation_chamber.trace.chance",
                                Math.round(ObservationChamberBlockEntity.TRACE_CHANCE * 100))))
                .icon(() -> new ItemStack(menu.isVoidExcessTrace() ? Items.LAVA_BUCKET : Items.CHEST)));
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
    }

    @Override
    protected int energyStored() {
        return 0;
    }

    @Override
    protected int maxEnergyStored() {
        return 1;
    }

    @Override
    protected List<Component> energyTooltip() {
        return List.of(Component.translatable("gui.quantimium.observation_chamber.no_energy"));
    }

    @Override
    protected Component statusLabel() {
        return switch (menu.getStatusCode()) {
            case ObservationChamberBlockEntity.STATUS_READY ->
                    Component.translatable("gui.quantimium.observation_chamber.status.ready");
            case ObservationChamberBlockEntity.STATUS_OUTPUT_FULL ->
                    Component.translatable("gui.quantimium.observation_chamber.status.full");
            case ObservationChamberBlockEntity.STATUS_IDLE ->
                    Component.translatable("gui.quantimium.observation_chamber.status.idle");
            default -> Component.translatable("gui.quantimium.observation_chamber.status.empty");
        };
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.LOW;
    }

    @Override
    protected boolean hasEnergy() {
        return false;
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case ObservationChamberBlockEntity.STATUS_READY -> PanelTheme.Tone.OK;
            case ObservationChamberBlockEntity.STATUS_OUTPUT_FULL -> PanelTheme.Tone.BAD;
            default -> PanelTheme.Tone.IDLE;
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(switch (menu.getStatusCode()) {
            case ObservationChamberBlockEntity.STATUS_READY ->
                    Component.translatable("gui.quantimium.observation_chamber.status.ready.tip");
            case ObservationChamberBlockEntity.STATUS_OUTPUT_FULL ->
                    Component.translatable("gui.quantimium.observation_chamber.status.full.tip");
            default -> Component.translatable("gui.quantimium.observation_chamber.status.empty.tip");
        });
    }

    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Centred over the Trace socket's frame (80, 35 in tools/gui_panels.py).
        Component trace = Component.translatable("gui.quantimium.observation_chamber.trace");
        graphics.text(font, trace, leftPos + 88 - font.width(trace) / 2, topPos + 20, dimColour(), false);
    }
}
