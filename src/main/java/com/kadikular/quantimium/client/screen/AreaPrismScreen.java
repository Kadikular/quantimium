package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.AreaPrismBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.menu.AreaPrismMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
                Tab.of(() -> Component.literal(switch (prism().shape()) { case PICKED -> "P"; case CUBE -> "C"; case SPHERE -> "S"; }),
                                () -> press(AreaPrismMenu.BUTTON_SHAPE),
                                () -> List.of(Component.translatable("gui.quantimium.area_prism.shape." + prism().shape().name().toLowerCase(java.util.Locale.ROOT)),
                                        Component.translatable("gui.quantimium.area_prism.shape.tip")))
                        .icon(() -> new ItemStack(switch (prism().shape()) {
                            case PICKED -> com.kadikular.quantimium.init.ModItems.LASER_LINKER.get();
                            case CUBE -> Items.STONE;
                            case SPHERE -> Items.SNOWBALL;
                        })),
                Tab.of(() -> Component.literal("+"), () -> press(minecraft != null && minecraft.hasShiftDown() ? AreaPrismMenu.BUTTON_MUCH_WIDER : AreaPrismMenu.BUTTON_WIDER),
                                () -> List.of(Component.translatable("gui.quantimium.area_prism.radius", prism().radius()),
                                        Component.translatable("gui.quantimium.area_prism.wider")))
                        .icon(() -> new ItemStack(Items.GLOWSTONE_DUST))
                        .visible(() -> prism().shape() != AreaPrismBlockEntity.Shape.PICKED),
                Tab.of(() -> Component.literal("-"), () -> press(minecraft != null && minecraft.hasShiftDown() ? AreaPrismMenu.BUTTON_MUCH_NARROWER : AreaPrismMenu.BUTTON_NARROWER),
                                () -> List.of(Component.translatable("gui.quantimium.area_prism.radius", prism().radius()),
                                        Component.translatable("gui.quantimium.area_prism.narrower")))
                        .icon(() -> new ItemStack(Items.REDSTONE))
                        .visible(() -> prism().shape() != AreaPrismBlockEntity.Shape.PICKED));
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
        return Component.translatable("gui.quantimium.area_prism.shape." + prism().shape().name().toLowerCase(java.util.Locale.ROOT));
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
        graphics.text(font, area, leftPos + 8, topPos + 24, textColour(), false);
        graphics.text(font, Component.translatable("gui.quantimium.area_prism.reach"), leftPos + 8, topPos + 40, dimColour(), false);
    }
}
