package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.menu.QuantumFoundryMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Console panel for the foundry: four attunement sockets in a diamond around the well, the field
 * gauge beside the energy bar, and the cycle running out to the product socket.
 */
public class QuantumFoundryScreen extends QuantumMachineScreen<QuantumFoundryMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID,
                    "textures/gui/container/quantum_foundry.png");

    private static final int FLUX_X = 14;
    private static final int FLUX_Y = 18;
    private static final int FLUX_WIDTH = 4;
    private static final int FLUX_HEIGHT = 52;

    private static final int PROGRESS_X = 120;
    private static final int PROGRESS_Y = 44;
    private static final int PROGRESS_WIDTH = 16;
    private static final int PROGRESS_HEIGHT = 4;

    private static final int WELL_X = 88;
    private static final int WELL_Y = 46;

    public QuantumFoundryScreen(QuantumFoundryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
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
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(),
                menu.getCurrentPowerUse(), menu.getAveragePowerUse(), false,
                0, anomalyBand(), menu.getPassiveDrain());
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(switch (menu.getStatusCode()) {
            case QuantumFoundryBlockEntity.STATUS_IDLE -> "gui.quantimium.quantum_foundry.status.idle";
            case QuantumFoundryBlockEntity.STATUS_NO_RECIPE -> "gui.quantimium.quantum_foundry.status.recipe";
            case QuantumFoundryBlockEntity.STATUS_NO_POWER -> "gui.quantimium.quantum_foundry.status.power";
            case QuantumFoundryBlockEntity.STATUS_LOW_FLUX -> "gui.quantimium.quantum_foundry.status.flux";
            case QuantumFoundryBlockEntity.STATUS_OUTPUT_FULL -> "gui.quantimium.quantum_foundry.status.output";
            case QuantumFoundryBlockEntity.STATUS_WORKING -> "gui.quantimium.quantum_foundry.status.working";
            default -> "gui.quantimium.quantum_foundry.status.unformed";
        });
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case QuantumFoundryBlockEntity.STATUS_WORKING -> PanelTheme.Tone.OK;
            case QuantumFoundryBlockEntity.STATUS_IDLE -> PanelTheme.Tone.IDLE;
            case QuantumFoundryBlockEntity.STATUS_LOW_FLUX, QuantumFoundryBlockEntity.STATUS_OUTPUT_FULL,
                 QuantumFoundryBlockEntity.STATUS_NO_RECIPE -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(switch (menu.getStatusCode()) {
            case QuantumFoundryBlockEntity.STATUS_IDLE -> "gui.quantimium.quantum_foundry.status.idle.tip";
            case QuantumFoundryBlockEntity.STATUS_NO_RECIPE -> "gui.quantimium.quantum_foundry.status.recipe.tip";
            case QuantumFoundryBlockEntity.STATUS_NO_POWER -> "gui.quantimium.quantum_foundry.status.power.tip";
            case QuantumFoundryBlockEntity.STATUS_LOW_FLUX -> "gui.quantimium.quantum_foundry.status.flux.tip";
            case QuantumFoundryBlockEntity.STATUS_OUTPUT_FULL -> "gui.quantimium.quantum_foundry.status.output.tip";
            case QuantumFoundryBlockEntity.STATUS_WORKING -> "gui.quantimium.quantum_foundry.status.working.tip";
            default -> "gui.quantimium.quantum_foundry.status.unformed.tip";
        }));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int duration = Math.max(1, menu.getDuration());
        float fraction = Mth.clamp(menu.getProgress() / (float) duration, 0.0f, 1.0f);

        int filled = (int) (PROGRESS_WIDTH * fraction);
        if (filled > 0) {
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                    leftPos + PROGRESS_X + filled, topPos + PROGRESS_Y + PROGRESS_HEIGHT,
                    QuantumUiColours.ACCENT);
            graphics.fill(leftPos + PROGRESS_X + filled - 1, topPos + PROGRESS_Y,
                    leftPos + PROGRESS_X + filled, topPos + PROGRESS_Y + PROGRESS_HEIGHT,
                    0xFFDFFBFF);
        }

        int band = menu.getFluxBandOrdinal();
        int fluxFill = Math.max(2, (band + 1) * FLUX_HEIGHT / FluxBand.values().length);
        graphics.fill(leftPos + FLUX_X, topPos + FLUX_Y + FLUX_HEIGHT - fluxFill,
                leftPos + FLUX_X + FLUX_WIDTH, topPos + FLUX_Y + FLUX_HEIGHT,
                fluxColour());
        int required = menu.getRequiredBandOrdinal();
        if (required > 0) {
            int mark = FLUX_HEIGHT - (required + 1) * FLUX_HEIGHT / FluxBand.values().length;
            graphics.fill(leftPos + FLUX_X - 1, topPos + FLUX_Y + mark,
                    leftPos + FLUX_X + FLUX_WIDTH + 1, topPos + FLUX_Y + mark + 1, 0xFFFFE070);
        }

        // The well answers the cycle, matching the field over the real plinth.
        if (menu.getStatusCode() == QuantumFoundryBlockEntity.STATUS_WORKING) {
            int glow = (int) (0x40 + 0x8F * (0.5f + 0.5f * Mth.sin(fraction * 12.0f))) << 24;
            graphics.fill(leftPos + WELL_X - 3, topPos + WELL_Y - 3,
                    leftPos + WELL_X + 3, topPos + WELL_Y + 3, glow | 0x00E5FF);
        }

        for (int slot = 0; slot < 4; slot++) {
            if ((menu.getPillarMask() & 1 << slot) != 0) continue;
            int x = leftPos + QuantumFoundryMenu.INPUT_POSITIONS[slot][0] - 1;
            int y = topPos + QuantumFoundryMenu.INPUT_POSITIONS[slot][1] - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xC00A0B16);
        }
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!QuantumWidgets.hit(mouseX, mouseY, leftPos + FLUX_X - 1, topPos + FLUX_Y,
                FLUX_WIDTH + 2, FLUX_HEIGHT + 1)) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.quantimium.quantum_foundry.flux.current",
                bandName(fluxBand())).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.quantimium.quantum_foundry.flux.required",
                bandName(requiredBand())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.quantimium.quantum_foundry.flux.cost",
                menu.getFluxCost()).withStyle(ChatFormatting.DARK_AQUA));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** Same ramp the block entity renderer uses, so panel and world agree on the band. */
    private int fluxColour() {
        if (anomalyBand().ordinal() > fluxBand().ordinal()) return 0xFFFF4738;
        return switch (fluxBand()) {
            case LOW -> 0xFF618CBF;
            case MEDIUM -> 0xFF3399FF;
            case HIGH -> 0xFF47D1FF;
            case CRITICAL -> 0xFFA86BFF;
            case SINGULARITY -> 0xFFFF4DEB;
        };
    }

    private FluxBand fluxBand() {
        return band(menu.getFluxBandOrdinal());
    }

    private FluxBand anomalyBand() {
        return band(menu.getAnomalyBandOrdinal());
    }

    private FluxBand requiredBand() {
        return band(menu.getRequiredBandOrdinal());
    }

    private static FluxBand band(int ordinal) {
        return FluxBand.values()[Math.clamp(ordinal, 0, FluxBand.values().length - 1)];
    }

    private static Component bandName(FluxBand band) {
        return Component.translatable("flux.quantimium.band." + band.getSerializedName());
    }
}
