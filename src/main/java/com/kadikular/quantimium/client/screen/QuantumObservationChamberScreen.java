package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumObservationChamberBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.menu.QuantumObservationChamberMenu;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/** The mid-tier chamber: Matter, a progress arrow, results and Trace, with the Trace and faces tabs. */
public class QuantumObservationChamberScreen extends QuantumMachineScreen<QuantumObservationChamberMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/quantum_observation_chamber.png");

    /** The arrow's well (tools/gui_panels.py: "progress"). */
    private static final int PROGRESS_X = 48;
    private static final int PROGRESS_Y = 41;
    private static final int PROGRESS_WIDTH = 20;
    private static final int PROGRESS_HEIGHT = 5;

    public QuantumObservationChamberScreen(QuantumObservationChamberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of("S", () -> ClientPacketDistributor.sendToServer(
                                new RequestSideConfigPayload(menu.getBlockEntity().getBlockPos())),
                        List.of(Component.translatable("gui.quantimium.quantum_observation_chamber.sides"),
                                Component.translatable("gui.quantimium.quantum_observation_chamber.sides.tip")))
                        .icon(new ItemStack(Items.HOPPER)),
                // A lava bucket while excess Trace is destroyed, a chest while it is kept.
                Tab.of(() -> Component.literal("T"),
                        () -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                                QuantumObservationChamberMenu.BUTTON_VOID_TRACE),
                        () -> List.of(
                                Component.translatable(menu.isVoidExcessTrace()
                                        ? "gui.quantimium.observation_chamber.void_on"
                                        : "gui.quantimium.observation_chamber.void_off"),
                                Component.translatable("gui.quantimium.observation_chamber.void.tip"),
                                Component.translatable("gui.quantimium.observation_chamber.trace.chance",
                                        Math.round(ObservationChamberBlockEntity.TRACE_CHANCE * 100))))
                        .icon(() -> new ItemStack(menu.isVoidExcessTrace() ? Items.LAVA_BUCKET : Items.CHEST)));
    }

    public void openSideConfig(OpenSideConfigPayload payload) {
        // Whole faces only: its three Matter slots and eight results would not fit the crafter's grid.
        openOverlay(new SideConfigScreen(payload.pos(), payload.configs(), payload.profile(), false));
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
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
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(), -1,
                menu.getAverageFePerTick(), false, menu.getSurchargePercent(), null);
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int filled = PROGRESS_WIDTH * menu.getProgress() / QuantumObservationChamberBlockEntity.CYCLE_TICKS;
        if (filled > 0 && menu.getStatusCode() == QuantumObservationChamberBlockEntity.STATUS_WORKING) {
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y, leftPos + PROGRESS_X + filled,
                    topPos + PROGRESS_Y + PROGRESS_HEIGHT, QuantumUiColours.ACCENT);
        }
    }

    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Centred over the Trace socket's frame.
        Component trace = Component.translatable("gui.quantimium.observation_chamber.trace");
        graphics.text(font, trace, leftPos + QuantumObservationChamberMenu.TRACE_X + 8 - font.width(trace) / 2,
                topPos + 20, dimColour(), false);
    }

    private String statusKey() {
        return switch (menu.getStatusCode()) {
            case QuantumObservationChamberBlockEntity.STATUS_WORKING -> "gui.quantimium.quantum_observation_chamber.status.working";
            case QuantumObservationChamberBlockEntity.STATUS_OUTPUT_FULL -> "gui.quantimium.quantum_observation_chamber.status.full";
            case QuantumObservationChamberBlockEntity.STATUS_NO_POWER -> "gui.quantimium.quantum_observation_chamber.status.power";
            default -> "gui.quantimium.quantum_observation_chamber.status.empty";
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
        return switch (menu.getStatusCode()) {
            case QuantumObservationChamberBlockEntity.STATUS_WORKING -> PanelTheme.Tone.OK;
            case QuantumObservationChamberBlockEntity.STATUS_OUTPUT_FULL,
                 QuantumObservationChamberBlockEntity.STATUS_NO_POWER -> PanelTheme.Tone.BAD;
            default -> PanelTheme.Tone.IDLE;
        };
    }
}
