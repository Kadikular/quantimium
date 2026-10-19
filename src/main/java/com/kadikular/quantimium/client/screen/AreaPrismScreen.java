package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.AreaPrismBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.menu.AreaPrismMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * An Area Prism: what its area is (the one picked with the Linker, or a cube or sphere round it) and the
 * radius, on tabs; what that comes to, written out.
 */
public class AreaPrismScreen extends QuantumMachineScreen<AreaPrismMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/area_prism.png");

    public AreaPrismScreen(AreaPrismMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private AreaPrismBlockEntity prism() {
        return menu.getBlockEntity();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.glyph(() -> switch (prism().shape()) {
                                    case PICKED -> GuiIcon.AREA_PICKED;
                                    case CUBE -> GuiIcon.AREA_CUBE;
                                    case SPHERE -> GuiIcon.AREA_SPHERE;
                                }, () -> press(AreaPrismMenu.BUTTON_SHAPE),
                                () -> List.of(Component.translatable("gui.quantimium.area_prism.shape." + prism().shape().name().toLowerCase(java.util.Locale.ROOT)),
                                        hint("gui.quantimium.tab.cycle")))
                        // Three shapes: two steps on is one back.
                        .onRightClick(() -> {
                            press(AreaPrismMenu.BUTTON_SHAPE);
                            press(AreaPrismMenu.BUTTON_SHAPE);
                        }),
                // Its radius on the tab: click or scroll up to widen, right-click or scroll down to narrow; shift for eight.
                Tab.of(() -> Component.literal(Integer.toString(prism().radius())), () -> widen(true),
                                () -> List.of(Component.translatable("gui.quantimium.area_prism.radius", prism().radius()),
                                        hint("gui.quantimium.tab.number")))
                        .onRightClick(() -> widen(false))
                        .onScroll(step -> widen(step > 0))
                        .visible(() -> prism().shape() != AreaPrismBlockEntity.Shape.PICKED),
                // The order its actions work the area in; right-click steps back.
                Tab.glyph(() -> switch (prism().scanOrder()) {
                                    case NEAREST -> GuiIcon.NEAREST;
                                    case TOP_DOWN -> GuiIcon.CEILING;
                                    case BOTTOM_UP -> GuiIcon.FLOOR;
                                }, () -> press(AreaPrismMenu.BUTTON_ORDER),
                                () -> List.of(Component.translatable("gui.quantimium.area_prism.order." + prism().scanOrder().name().toLowerCase(java.util.Locale.ROOT)),
                                        hint("gui.quantimium.tab.cycle")))
                        .onRightClick(() -> {
                            press(AreaPrismMenu.BUTTON_ORDER);
                            press(AreaPrismMenu.BUTTON_ORDER);
                        }));
    }

    private void widen(boolean wider) {
        boolean much = minecraft != null && minecraft.hasShiftDown();
        press(wider ? (much ? AreaPrismMenu.BUTTON_MUCH_WIDER : AreaPrismMenu.BUTTON_WIDER)
                : (much ? AreaPrismMenu.BUTTON_MUCH_NARROWER : AreaPrismMenu.BUTTON_NARROWER));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
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
        // The shape is on its tab; the panel says what it covers.
        return Component.empty();
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.area_prism.status.tip"));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        var box = prism().area();
        Component area = switch (prism().shape()) {
            case PICKED -> box == null ? Component.translatable("gui.quantimium.area_prism.none")
                    : Component.translatable("gui.quantimium.area_prism.picked", box.getXSpan(), box.getYSpan(), box.getZSpan());
            case CUBE -> Component.translatable("gui.quantimium.area_prism.cube", prism().radius(), 2 * prism().radius() + 1);
            case SPHERE -> Component.translatable("gui.quantimium.area_prism.sphere", prism().radius());
        };
        graphics.textWithWordWrap(font, area, leftPos + 8, topPos + 22, imageWidth - 16, textColour());
        graphics.textWithWordWrap(font, Component.translatable("gui.quantimium.area_prism.reach"), leftPos + 8, topPos + 48,
                imageWidth - 16, dimColour());
    }
}
