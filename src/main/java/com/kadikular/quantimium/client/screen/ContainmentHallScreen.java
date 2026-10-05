package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.ContainmentHallMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContainmentHallScreen extends QuantumMachineScreen<ContainmentHallMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/containment_hall.png");

    private static final int INFO_X = 18;
    private static final int INFO_Y = 22;
    private static final int LINE_HEIGHT = 10;
    /** The cell's line: always the last of the info, and hovered for the longer line. */
    private static final int CELL_LINE = 4;
    /** The time left is centred under the residue socket's frame. */
    private static final int RESIDUE_TEXT_CENTRE = ContainmentHallMenu.RESIDUE_X + 8;
    private static final int RESIDUE_TEXT_Y = ContainmentHallMenu.RESIDUE_Y + 24;
    private static final int RESIDUE_HOVER_LEFT = ContainmentHallMenu.RESIDUE_X - 12;
    private static final int RESIDUE_HOVER_WIDTH = 40;

    public ContainmentHallScreen(ContainmentHallMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of();
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
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int arms = menu.getArmCount();
        List<Component> lines = new ArrayList<>(5);
        lines.add(Component.translatable("gui.quantimium.containment_hall.arms",
                arms, ContainmentHallStructure.MAX_ARMS));
        if (arms > 0) {
            lines.add(Component.translatable("gui.quantimium.containment_hall.rated",
                    Component.translatable("flux.quantimium.band."
                            + menu.getShieldCap().getSerializedName())));
            lines.add(Component.translatable("gui.quantimium.containment.load", menu.getLoadPercent()));
            lines.add(Component.translatable("gui.quantimium.containment_hall.pull",
                    String.format("%,.0f", menu.getSuppressionPerSecond())));
        }
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE_HEIGHT,
                    i == 0 ? textColour() : dimColour(), false);
        }
        ContainmentHallBlockEntity.Cell cell = menu.getCell();
        graphics.text(font, cellLabel(cell), leftPos + INFO_X, topPos + INFO_Y + CELL_LINE * LINE_HEIGHT,
                cell == ContainmentHallBlockEntity.Cell.WAITING ? theme().tone(PanelTheme.Tone.OK)
                        : cell == ContainmentHallBlockEntity.Cell.TAKEN ? textColour() : theme().tone(PanelTheme.Tone.WARN), false);

        // Starved, the readout becomes the countdown to the occupant walking out.
        int starving = menu.getStarvingTicks();
        String time = starving > 0
                ? clock((ContainmentHallBlockEntity.STARVE_GRACE_TICKS - starving + 19) / 20)
                : clock(menu.getResidueSecondsLeft());
        boolean warn = starving > 0 || menu.getResidueSecondsLeft() == 0;
        // Centred under the socket, but never past the panel's edge: "1:00:00" is wider than the socket.
        int timeX = Math.min(RESIDUE_TEXT_CENTRE - font.width(time) / 2, imageWidth - 5 - font.width(time));
        graphics.text(font, time, leftPos + timeX,
                topPos + RESIDUE_TEXT_Y, warn ? theme().tone(PanelTheme.Tone.BAD) : textColour(), false);
    }

    private static Component cellLabel(ContainmentHallBlockEntity.Cell cell) {
        return Component.translatable("gui.quantimium.containment_hall.cell",
                Component.translatable("gui.quantimium.containment_hall.cell." + cell.name().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cellY = topPos + INFO_Y + CELL_LINE * LINE_HEIGHT;
        if (QuantumWidgets.hit(mouseX, mouseY, leftPos + INFO_X, cellY - 1, font.width(cellLabel(menu.getCell())), 10)) {
            graphics.setTooltipForNextFrame(font, font.split(Component.translatable("gui.quantimium.containment_hall.cell."
                    + menu.getCell().name().toLowerCase(Locale.ROOT) + ".tip"), 200), mouseX, mouseY);
            return;
        }
        // The slot's own item tooltip covers it once filled, so only claim it while empty.
        boolean slotEmpty = menu.getSlot(0).getItem().isEmpty();
        boolean onTime = QuantumWidgets.hit(mouseX, mouseY, leftPos + RESIDUE_HOVER_LEFT,
                topPos + RESIDUE_TEXT_Y - 2, RESIDUE_HOVER_WIDTH, 12);
        boolean onSlot = slotEmpty && QuantumWidgets.hit(mouseX, mouseY, leftPos + ContainmentHallMenu.RESIDUE_X - 1,
                topPos + ContainmentHallMenu.RESIDUE_Y - 1, 18, 18);
        if (!onTime && !onSlot) return;
        List<Component> lines = new ArrayList<>(4);
        lines.add(menu.isLinked()
                ? Component.translatable("gui.quantimium.containment_hall.residue.linked", menu.getResidueCount())
                : Component.translatable("gui.quantimium.containment_hall.residue",
                        menu.getResidueCount(), ContainmentHallBlockEntity.RESIDUE_CAPACITY));
        int starving = menu.getStarvingTicks();
        if (starving > 0) {
            lines.add(Component.translatable("gui.quantimium.containment_hall.residue.starving",
                    clock((ContainmentHallBlockEntity.STARVE_GRACE_TICKS - starving + 19) / 20))
                    .withStyle(ChatFormatting.RED));
        } else if (menu.getResidueSecondsLeft() == 0) {
            lines.add(Component.translatable("gui.quantimium.containment_hall.residue.empty")
                    .withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("gui.quantimium.containment_hall.residue.left",
                    clock(menu.getResidueSecondsLeft())));
        }
        lines.add(Component.translatable("gui.quantimium.containment_hall.residue.rate")
                .withStyle(ChatFormatting.GRAY));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** m:ss, or h:mm:ss once past the hour. The synced value caps at about nine hours. */
    private static String clock(int seconds) {
        if (seconds >= Short.MAX_VALUE) return "9h+";
        int s = Math.max(0, seconds);
        return s >= 3600
                ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    protected List<Component> energyTooltip() {
        List<Component> lines = new ArrayList<>(EnergyBarTooltip.lines(
                menu.getEnergyStored(), menu.getMaxEnergyStored(),
                menu.getCurrentPowerUse(), menu.getAveragePowerUse(), false,
                menu.getAnomalySurchargePercent(), menu.getAnomalyBand(),
                menu.getPassiveDrainFePerTick()));
        if (menu.getStatusCode() == ContainmentHallBlockEntity.STATUS_CONTAINING) {
            lines.add(Component.translatable("gui.quantimium.energy.contained")
                    .withStyle(ChatFormatting.AQUA));
        }
        return List.copyOf(lines);
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(switch (menu.getStatusCode()) {
            case ContainmentHallBlockEntity.STATUS_CONTAINING ->
                    "gui.quantimium.containment_hall.status.containing";
            case ContainmentHallBlockEntity.STATUS_OVERWHELMED ->
                    "gui.quantimium.containment_hall.status.overwhelmed";
            case ContainmentHallBlockEntity.STATUS_NO_POWER ->
                    "gui.quantimium.containment_hall.status.power";
            default -> "gui.quantimium.containment_hall.status.unformed";
        });
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case ContainmentHallBlockEntity.STATUS_CONTAINING -> PanelTheme.Tone.OK;
            case ContainmentHallBlockEntity.STATUS_OVERWHELMED -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(switch (menu.getStatusCode()) {
            case ContainmentHallBlockEntity.STATUS_CONTAINING ->
                    "gui.quantimium.containment_hall.status.containing.tip";
            case ContainmentHallBlockEntity.STATUS_OVERWHELMED ->
                    "gui.quantimium.containment_hall.status.overwhelmed.tip";
            case ContainmentHallBlockEntity.STATUS_NO_POWER ->
                    "gui.quantimium.containment_hall.status.power.tip";
            default -> "gui.quantimium.containment_hall.status.unformed.tip";
        }));
    }
}
