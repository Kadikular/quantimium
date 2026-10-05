package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.menu.ZenoFieldControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Mode, size and rate on the left; on the right the field drawn to scale, a square that grows with
 * the radius, filled with frost while holding and with ticking pips while accelerating.
 */
public class ZenoFieldControllerScreen extends QuantumMachineScreen<ZenoFieldControllerMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/zeno_field_controller.png");

    private static final int INFO_X = 18;
    private static final int INFO_Y = 22;
    private static final int LINE_HEIGHT = 12;
    /** The diagram well (tools/gui_panels.py: "field"). */
    private static final int FIELD_X = 128;
    private static final int FIELD_Y = 20;
    private static final int FIELD_SIZE = 44;
    private static final int FROST = 0x6090D8FF;

    public ZenoFieldControllerScreen(ZenoFieldControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private boolean accelerating() {
        return menu.getMode() == ZenoFieldControllerBlockEntity.MODE_ACCELERATE;
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of(() -> Component.literal("M"), () -> press(ZenoFieldControllerBlockEntity.BUTTON_MODE),
                                () -> List.of(Component.translatable(accelerating()
                                                ? "gui.quantimium.zeno_field_controller.mode.accelerate"
                                                : "gui.quantimium.zeno_field_controller.mode.pause"),
                                        Component.translatable("gui.quantimium.zeno_field_controller.mode.tip")))
                        .icon(() -> new ItemStack(accelerating() ? Items.CLOCK : Items.PACKED_ICE)),
                Tab.of(() -> Component.literal("r" + menu.getRadius()), () -> press(ZenoFieldControllerBlockEntity.BUTTON_RADIUS),
                                () -> List.of(Component.translatable("gui.quantimium.zeno_field_controller.radius", menu.getRadius()),
                                        Component.translatable("gui.quantimium.zeno_field_controller.radius.tip")))
                        .width(24),
                Tab.of(() -> Component.literal(menu.getFactor() + "x"), () -> press(ZenoFieldControllerBlockEntity.BUTTON_FACTOR),
                                this::factorTooltip)
                        .width(28)
                        .enabled(this::accelerating));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
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
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(),
                menu.getCurrentPowerUse(), menu.getAveragePowerUse(), false, 0, null,
                menu.getPassiveDrainFePerTick());
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(switch (menu.getStatusCode()) {
            case ZenoFieldControllerBlockEntity.STATUS_ACCELERATING -> "gui.quantimium.zeno_field_controller.status.accelerating";
            case ZenoFieldControllerBlockEntity.STATUS_NO_POWER -> "gui.quantimium.zeno_field_controller.status.power";
            default -> "gui.quantimium.zeno_field_controller.status.holding";
        });
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return menu.getStatusCode() == ZenoFieldControllerBlockEntity.STATUS_NO_POWER
                ? PanelTheme.Tone.BAD : PanelTheme.Tone.OK;
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(switch (menu.getStatusCode()) {
            case ZenoFieldControllerBlockEntity.STATUS_ACCELERATING -> "gui.quantimium.zeno_field_controller.status.accelerating.tip";
            case ZenoFieldControllerBlockEntity.STATUS_NO_POWER -> "gui.quantimium.zeno_field_controller.status.power.tip";
            default -> "gui.quantimium.zeno_field_controller.status.holding.tip";
        }));
    }

    /** The rate chosen, and when the band here holds it back, what it runs at and what it needs. */
    private List<Component> factorTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.quantimium.zeno_field_controller.factor", menu.getFactor()));
        if (menu.getRunningFactor() < menu.getFactor()) {
            FluxBand needed = FluxBand.SINGULARITY;
            for (FluxBand band : FluxBand.values()) {
                if (Config.zenoMaxFactor(band) >= menu.getFactor()) {
                    needed = band;
                    break;
                }
            }
            lines.add(Component.translatable("gui.quantimium.zeno_field_controller.factor.running", menu.getRunningFactor()));
            lines.add(BandGate.refusal(needed, menu.getBand(), menu.getHeading()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.quantimium.zeno_field_controller.factor.tip"));
        return lines;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int radius = menu.getRadius();
        int side = 2 * radius + 1;
        int draw = accelerating()
                ? ZenoFieldControllerBlockEntity.accelerateCost(radius, menu.getRunningFactor(), menu.getBand())
                : ZenoFieldControllerBlockEntity.pauseCost(radius);
        List<Component> lines = List.of(
                Component.translatable(accelerating()
                        ? "gui.quantimium.zeno_field_controller.info.accelerate"
                        : "gui.quantimium.zeno_field_controller.info.pause", menu.getRunningFactor()),
                Component.translatable("gui.quantimium.zeno_field_controller.info.area", side, side, side),
                Component.translatable("gui.quantimium.zeno_field_controller.info.draw", String.format("%,d", draw)));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE_HEIGHT,
                    i == 0 ? textColour() : dimColour(), false);
        }
        renderField(graphics, radius);
    }

    /** The field to scale: radius 8 fills the well, radius 1 is a small square in its middle. */
    private void renderField(GuiGraphicsExtractor graphics, int radius) {
        int size = Math.max(6, FIELD_SIZE * (2 * radius + 1) / (2 * ZenoFieldControllerBlockEntity.MAX_RADIUS + 1));
        int x = leftPos + FIELD_X + (FIELD_SIZE - size) / 2;
        int y = topPos + FIELD_Y + (FIELD_SIZE - size) / 2;
        boolean powered = menu.getStatusCode() != ZenoFieldControllerBlockEntity.STATUS_NO_POWER;
        int edge = powered ? QuantumUiColours.ACCENT : 0xFF555566;
        if (powered && !accelerating()) graphics.fill(x, y, x + size, y + size, FROST);
        if (powered && accelerating() && minecraft != null && minecraft.level != null) {
            // Pips that jump about: the extra random ticks, more of them at a higher factor.
            long time = minecraft.level.getGameTime();
            int pips = Math.min(12, menu.getRunningFactor());
            for (int i = 0; i < pips; i++) {
                long seed = (time / 4) * 31 + i * 17L;
                int px = x + 1 + (int) Math.floorMod(seed * 7919, Math.max(1, size - 3));
                int py = y + 1 + (int) Math.floorMod(seed * 104729, Math.max(1, size - 3));
                graphics.fill(px, py, px + 2, py + 2, QuantumUiColours.ACCENT);
            }
        }
        graphics.fill(x, y, x + size, y + 1, edge);
        graphics.fill(x, y + size - 1, x + size, y + size, edge);
        graphics.fill(x, y, x + 1, y + size, edge);
        graphics.fill(x + size - 1, y, x + size, y + size, edge);
        int centre = leftPos + FIELD_X + FIELD_SIZE / 2;
        int middle = topPos + FIELD_Y + FIELD_SIZE / 2;
        graphics.fill(centre - 1, middle - 1, centre + 1, middle + 1, 0xFFDCDCE4);
    }
}
