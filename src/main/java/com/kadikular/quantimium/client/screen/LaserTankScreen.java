package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.LaserTankMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** A Laser Tank: its four tanks as gauges, filled from the bottom, and its mode. Layout: tools/gui_panels.py laser_tank. */
public class LaserTankScreen extends QuantumMachineScreen<LaserTankMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_tank.png");
    public static final int TANK_X = 50;
    public static final int TANK_Y = 20;
    public static final int TANK_W = 16;
    public static final int TANK_H = 56;
    public static final int TANK_STEP = 20;

    public LaserTankScreen(LaserTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private LaserTankBlockEntity tank() {
        return menu.getBlockEntity();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.glyph(() -> switch (tank().mode()) {
                                    case COLLECT -> GuiIcon.COLLECT;
                                    case SEND -> GuiIcon.SEND;
                                    case BOTH -> GuiIcon.BOTH;
                                }, () -> press(LaserTankMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable("gui.quantimium.laser_port.mode." + tank().mode().name().toLowerCase(java.util.Locale.ROOT)),
                                        hint("gui.quantimium.tab.cycle")))
                        // Three modes: two steps on is one back.
                        .onRightClick(() -> {
                            press(LaserTankMenu.BUTTON_MODE);
                            press(LaserTankMenu.BUTTON_MODE);
                        }));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        for (int i = 0; i < LaserTankBlockEntity.TANKS; i++) {
            drawTank(graphics, tank().tanks().get(i), leftPos + TANK_X + i * TANK_STEP, topPos + TANK_Y);
        }
    }

    /** The well is painted on the panel; this only fills it, from the bottom up. */
    private static void drawTank(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) return;
        int filled = Math.max(1, (int) ((long) TANK_H * fluid.getAmount() / LaserTankBlockEntity.CAPACITY));
        FluidTintSource tint = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(fluid.getFluid().defaultFluidState()).fluidTintSource();
        int color = tint == null ? 0xFFFFFFFF : tint.colorAsStack(fluid);
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;
        graphics.fill(x, y + TANK_H - filled, x + TANK_W, y + TANK_H, color);
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < LaserTankBlockEntity.TANKS; i++) {
            if (!QuantumWidgets.hit(mouseX, mouseY, leftPos + TANK_X + i * TANK_STEP - 1, topPos + TANK_Y - 1, TANK_W + 2, TANK_H + 2)) continue;
            FluidStack fluid = tank().tanks().get(i);
            List<Component> tip = new ArrayList<>(2);
            tip.add(Component.translatable("gui.quantimium.laser_tank.tank", i + 1));
            tip.add(fluid.isEmpty() ? Component.translatable("gui.quantimium.slot_config.empty").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("gui.quantimium.quantum_simulator.tank.holds", fluid.getHoverName(),
                            String.format("%,d", fluid.getAmount()), String.format("%,d", LaserTankBlockEntity.CAPACITY)).withStyle(ChatFormatting.GRAY));
            graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
            return;
        }
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
        return 0;
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable("gui.quantimium.laser_tank.status." + tank().mode().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of();
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return PanelTheme.Tone.OK;
    }
}
