package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.LaserTankMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** A Laser Tank: its four tanks as gauges, filled from the bottom, and its mode. Layout: tools/gui_panels.py laser_tank. */
public class LaserTankScreen extends QuantumMachineScreen<LaserTankMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_tank.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_port_filter.png");
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
                filterTab(menu::isFilterView, menu::setFilterView, Component.translatable("gui.quantimium.laser_port.filter"),
                        "gui.quantimium.laser_tank.filter.tip"),
                Tab.glyph(() -> tank().allows() ? GuiIcon.ALLOW : GuiIcon.DENY, () -> press(LaserTankMenu.BUTTON_ALLOW),
                                () -> List.of(Component.translatable(tank().allows() ? "gui.quantimium.filter.allow" : "gui.quantimium.filter.deny"),
                                        hint("gui.quantimium.tab.switch")))
                        .during(menu::isFilterView),
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
                        }),
                sidesTab(tank().getBlockPos()));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** A bucket on the cursor clicked on a tank fills or empties it. */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int tank = menu.isFilterView() || menu.getCarried().isEmpty() ? -1 : tankAt(event.x(), event.y());
        if (tank >= 0) {
            press(LaserTankMenu.BUTTON_TANK + tank);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int tankAt(double mouseX, double mouseY) {
        for (int i = 0; i < LaserTankBlockEntity.TANKS; i++) {
            if (QuantumWidgets.hit((int) mouseX, (int) mouseY, leftPos + TANK_X + i * TANK_STEP - 1, topPos + TANK_Y - 1, TANK_W + 2, TANK_H + 2)) return i;
        }
        return -1;
    }

    @Override
    public List<net.minecraft.client.renderer.Rect2i> ghostTargets() {
        List<net.minecraft.client.renderer.Rect2i> targets = new ArrayList<>();
        if (!menu.isFilterView()) return targets;
        for (int i = 0; i < LaserTankBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new net.minecraft.client.renderer.Rect2i(leftPos + LaserTankMenu.FILTER_X + i * 18, topPos + LaserTankMenu.FILTER_Y, 16, 16));
        }
        return targets;
    }

    @Override
    public int ghostOffset() {
        return 0;
    }

    @Override
    public int ghostContainerId() {
        return menu.containerId;
    }

    @Override
    public boolean ghostFluids() {
        return true;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.isFilterView()) return;
        for (int i = 0; i < LaserTankBlockEntity.TANKS; i++) {
            drawTank(graphics, tank().tanks().get(i), leftPos + TANK_X + i * TANK_STEP, topPos + TANK_Y);
        }
    }

    /** The well is painted on the panel; this only fills it, from the bottom up. */
    private static void drawTank(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y) {
        com.kadikular.quantimium.client.screen.ui.FluidSprites.gauge(graphics, fluid, LaserTankBlockEntity.CAPACITY, x, y, TANK_W, TANK_H);
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.isFilterView()) {
            // An empty entry says how to set it.
            if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty() && menu.slots.indexOf(hoveredSlot) < LaserTankMenu.FILTER_END) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.quantimium.laser_tank.filter.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
            return;
        }
        for (int i = 0; i < LaserTankBlockEntity.TANKS; i++) {
            if (!QuantumWidgets.hit(mouseX, mouseY, leftPos + TANK_X + i * TANK_STEP - 1, topPos + TANK_Y - 1, TANK_W + 2, TANK_H + 2)) continue;
            FluidStack fluid = tank().tanks().get(i);
            List<Component> tip = new ArrayList<>(2);
            tip.add(Component.translatable("gui.quantimium.laser_tank.tank", i + 1));
            tip.add(Component.translatable("gui.quantimium.laser_tank.tank.hint").withStyle(ChatFormatting.DARK_GRAY));
            tip.add(fluid.isEmpty() ? Component.translatable("gui.quantimium.slot_config.empty").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("gui.quantimium.quantum_simulator.tank.holds", fluid.getHoverName(),
                            String.format("%,d", fluid.getAmount()), String.format("%,d", LaserTankBlockEntity.CAPACITY)).withStyle(ChatFormatting.GRAY));
            graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
            return;
        }
    }

    @Override
    protected Identifier panelTexture() {
        return menu.isFilterView() ? FILTER_TEXTURE : TEXTURE;
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
        if (menu.isFilterView()) {
            int listed = 0;
            for (int i = 0; i < LaserTankBlockEntity.FILTER_SLOTS; i++) if (!tank().getFilter().getItem(i).isEmpty()) listed++;
            if (listed == 0) return Component.translatable("gui.quantimium.laser_port.status.all");
            return Component.translatable(tank().allows() ? "gui.quantimium.laser_port.status.allow" : "gui.quantimium.laser_port.status.deny", listed);
        }
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
