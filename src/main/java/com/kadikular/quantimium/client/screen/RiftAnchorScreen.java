package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.RiftAnchorBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.RiftAnchorMenu;
import com.kadikular.quantimium.phase.FluxRift;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The anchor's readout: the rift, the stabilisers holding it and what it is making, with the reason
 * the array is short if it is, and a bar to the next residue. No energy bar: the anchor draws none.
 */
public class RiftAnchorScreen extends QuantumMachineScreen<RiftAnchorMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/rift_anchor.png");

    private static final int INFO_X = 10;
    private static final int INFO_Y = 21;
    private static final int LINE_HEIGHT = 11;
    private static final int INFO_WIDTH = 132;
    /** The gauge well (tools/gui_panels.py: "progress"). */
    private static final int PROGRESS_X = 10;
    private static final int PROGRESS_Y = 68;
    private static final int PROGRESS_WIDTH = 132;
    private static final int PROGRESS_HEIGHT = 4;
    private static final int TIME_CENTRE = RiftAnchorMenu.RESIDUE_X + 8;
    private static final int TIME_Y = RiftAnchorMenu.RESIDUE_Y + 24;

    public RiftAnchorScreen(RiftAnchorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
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
        return 1;
    }

    /** Why stabilisers in reach are not helping, most pressing first. */
    private List<Component> problems() {
        List<Component> problems = new ArrayList<>(3);
        if (menu.getStage() == 0) return problems;
        if (menu.getUnpowered() > 0) {
            problems.add(Component.translatable("gui.quantimium.rift_anchor.unpowered", menu.getUnpowered()));
        }
        if (menu.getBlind() > 0) {
            problems.add(Component.translatable("gui.quantimium.rift_anchor.blind", menu.getBlind()));
        }
        if (menu.getSpare() > 0) {
            problems.add(Component.translatable("gui.quantimium.rift_anchor.spare", menu.getSpare(),
                    RiftAnchorBlockEntity.MAX_STABILISERS));
        }
        return problems;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int stage = menu.getStage();
        int x = leftPos + INFO_X;
        int y = topPos + INFO_Y;
        if (stage == 0) {
            graphics.text(font, Component.translatable("gui.quantimium.rift_anchor.no_rift"), x, y,
                    textColour(), false);
            graphics.textWithWordWrap(font, Component.translatable("gui.quantimium.rift_anchor.no_rift.hint"),
                    x, y + LINE_HEIGHT, INFO_WIDTH, dimColour());
        } else {
            graphics.text(font, Component.translatable("gui.quantimium.rift_anchor.stage", stage,
                    FluxRift.MAX_STAGE), x, y, textColour(), false);
            boolean held = menu.getHolding() >= RiftAnchorBlockEntity.MIN_STABILISERS;
            graphics.text(font, held
                            ? Component.translatable("gui.quantimium.rift_anchor.stabilisers", menu.getHolding(),
                                    RiftAnchorBlockEntity.MAX_STABILISERS)
                            : Component.translatable("gui.quantimium.rift_anchor.stabilisers.short", menu.getHolding(),
                                    RiftAnchorBlockEntity.MIN_STABILISERS),
                    x, y + LINE_HEIGHT, held ? dimColour() : theme().tone(PanelTheme.Tone.BAD), false);
            graphics.text(font, held
                            ? Component.translatable("gui.quantimium.rift_anchor.rate",
                                    String.format("%.1f", menu.getResiduePerHour()))
                            : Component.translatable("gui.quantimium.rift_anchor.rate.none"),
                    x, y + 2 * LINE_HEIGHT, dimColour(), false);
            List<Component> problems = problems();
            if (!problems.isEmpty()) {
                Component first = problems.size() > 1
                        ? Component.translatable("gui.quantimium.rift_anchor.more", problems.get(0), problems.size() - 1)
                        : problems.get(0);
                graphics.text(font, first, x, y + 3 * LINE_HEIGHT, theme().tone(PanelTheme.Tone.WARN), false);
            }
        }

        int filled = Math.round(PROGRESS_WIDTH * Math.min(1f, menu.getProgress()));
        if (filled > 0) {
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y, leftPos + PROGRESS_X + filled,
                    topPos + PROGRESS_Y + PROGRESS_HEIGHT, QuantumUiColours.ACCENT);
        }

        boolean full = menu.getStatusCode() == RiftAnchorBlockEntity.STATUS_FULL;
        String time = full ? Component.translatable("gui.quantimium.rift_anchor.full").getString()
                : clock(menu.getSecondsToNext());
        graphics.text(font, time, leftPos + TIME_CENTRE - font.width(time) / 2, topPos + TIME_Y,
                full ? theme().tone(PanelTheme.Tone.WARN) : dimColour(), false);
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean onProgress = QuantumWidgets.hit(mouseX, mouseY, leftPos + PROGRESS_X - 1, topPos + PROGRESS_Y - 1,
                PROGRESS_WIDTH + 2, PROGRESS_HEIGHT + 2);
        boolean onTime = QuantumWidgets.hit(mouseX, mouseY, leftPos + TIME_CENTRE - 20, topPos + TIME_Y - 2, 40, 12);
        if (onProgress || onTime) {
            List<Component> lines = new ArrayList<>(3);
            lines.add(Component.translatable("gui.quantimium.rift_anchor.progress",
                    Math.round(menu.getProgress() * 100)));
            if (menu.getSecondsToNext() >= 0) {
                lines.add(Component.translatable("gui.quantimium.rift_anchor.next", clock(menu.getSecondsToNext())));
            }
            if (menu.getWorkPerTick() > 0) {
                lines.add(Component.translatable("gui.quantimium.rift_anchor.rate.tip", menu.getStage(),
                        menu.getHolding()).withStyle(ChatFormatting.GRAY));
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        List<Component> problems = problems();
        boolean onProblems = problems.size() > 1 && QuantumWidgets.hit(mouseX, mouseY, leftPos + INFO_X,
                topPos + INFO_Y + 3 * LINE_HEIGHT - 1, INFO_WIDTH, LINE_HEIGHT);
        if (onProblems) graphics.setComponentTooltipForNextFrame(font, problems, mouseX, mouseY);
    }

    /** m:ss, or h:mm:ss past the hour; a dash while nothing is being made. */
    private static String clock(int seconds) {
        if (seconds < 0) return "-";
        if (seconds >= Short.MAX_VALUE) return "9h+";
        return seconds >= 3600
                ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(statusKey());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(statusKey() + ".tip"));
    }

    private String statusKey() {
        return switch (menu.getStatusCode()) {
            case RiftAnchorBlockEntity.STATUS_LOOSE -> "gui.quantimium.rift_anchor.status.loose";
            case RiftAnchorBlockEntity.STATUS_HELD -> "gui.quantimium.rift_anchor.status.held";
            case RiftAnchorBlockEntity.STATUS_FULL -> "gui.quantimium.rift_anchor.status.full";
            default -> "gui.quantimium.rift_anchor.status.empty";
        };
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case RiftAnchorBlockEntity.STATUS_HELD -> PanelTheme.Tone.OK;
            case RiftAnchorBlockEntity.STATUS_FULL -> PanelTheme.Tone.WARN;
            case RiftAnchorBlockEntity.STATUS_LOOSE -> PanelTheme.Tone.BAD;
            default -> PanelTheme.Tone.IDLE;
        };
    }
}
