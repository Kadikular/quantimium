package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.menu.LensMountMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * A Lens Mount: its four lenses in a row, in the order light meets them, and what the light leaving
 * it will do. No power bar: it takes what its lenses spend from the beam. Layout: tools/gui_panels.py lens_mount.
 */
public class LensMountScreen extends QuantumMachineScreen<LensMountMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/lens_mount.png");
    private static final int TEXT_X = 8;
    private static final int TEXT_Y = 20;
    private static final int ORDER_Y = 56;

    public LensMountScreen(LensMountMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected Identifier panelTexture() {
        return PANEL;
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
        return Component.translatable(menu.getBlockEntity().lenses().isEmpty() ? "gui.quantimium.lens_mount.status.empty"
                : "gui.quantimium.lens_mount.status.ready");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.lens_mount.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return menu.getBlockEntity().lenses().isEmpty() ? PanelTheme.Tone.IDLE : PanelTheme.Tone.OK;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int packed = Lens.after(Lens.NONE, menu.getBlockEntity().lenses());
        Lens action = Lens.action(packed);
        Component does = action == null ? Component.translatable("gui.quantimium.lens_mount.no_action")
                : Component.translatable("gui.quantimium.lens_mount.action",
                        Component.translatable("item.quantimium." + action.name().toLowerCase(java.util.Locale.ROOT) + "_lens"));
        graphics.text(font, does, leftPos + TEXT_X, topPos + TEXT_Y, textColour(), false);
        graphics.text(font, Component.translatable("gui.quantimium.lens_mount.order"), leftPos + TEXT_X, topPos + ORDER_Y, dimColour(), false);
    }
}
