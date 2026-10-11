package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.laser.Lens;
import com.kadikular.quantimium.menu.LensMountMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A Lens Mount: its four lenses spaced along a line of light, left to right, each with a button under
 * it to open its settings (lit when it has any), and what the light leaving it will do. No power bar: it
 * takes what its lenses spend from the beam. Layout: tools/gui_panels.py lens_mount.
 */
public class LensMountScreen extends QuantumMachineScreen<LensMountMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/lens_mount.png");
    private static final int TEXT_X = 8;
    private static final int TEXT_Y = 20;

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
        // What these lenses alone do to plain light: its actions, and what changes them.
        com.kadikular.quantimium.laser.Light light = com.kadikular.quantimium.laser.Light
                .from(menu.getBlockEntity().getBlockPos(), 32).through(menu.getBlockEntity().lenses());
        MutableComponent actions = Component.empty();
        for (com.kadikular.quantimium.laser.Light.Stream stream : light.acting()) {
            if (!actions.getSiblings().isEmpty()) actions.append(", ");
            actions.append(name(stream.action()));
        }
        MutableComponent does = light.streams().isEmpty() ? Component.translatable("gui.quantimium.lens_mount.no_action")
                : Component.translatable("gui.quantimium.lens_mount.action", actions);
        if (light.boosts() > 0) does.append(Component.translatable("gui.quantimium.lens_mount.range", 1 << light.boosts()));
        graphics.text(font, does, leftPos + TEXT_X, topPos + TEXT_Y, textColour(), false);
        // Each lens's settings button: three dots, lit when it has settings to open, and brighter under the pointer.
        for (int slot = 0; slot < LensMountMenu.SLOT_X.length; slot++) {
            int x = leftPos + LensMountMenu.SLOT_X[slot] - 1;
            int y = topPos + LensMountMenu.BUTTON_Y;
            boolean has = hasSettings(slot);
            boolean over = has && com.kadikular.quantimium.client.screen.ui.QuantumWidgets.hit(mouseX, mouseY, x, y,
                    LensMountMenu.BUTTON_W, LensMountMenu.BUTTON_H);
            if (over) graphics.fill(x, y, x + LensMountMenu.BUTTON_W - 1, y + LensMountMenu.BUTTON_H - 1, 0x30FFFFFF);
            int dot = !has ? 0xFF2A2E4A : over ? 0xFFE4DCFF : 0xFF9A7BFF;
            for (int i = 0; i < 3; i++) {
                int dx = x + 5 + i * 3;
                graphics.fill(dx, y + 4, dx + 2, y + 6, dot);
            }
            if (over) {
                ItemStack lens = menu.getBlockEntity().getLensSlots().getStackInSlot(slot);
                Component tip = lens.getItem() instanceof com.kadikular.quantimium.item.InteractLensItem
                        ? Component.translatable(com.kadikular.quantimium.item.InteractLensItem.hits(lens)
                                ? "gui.quantimium.lens_mount.interact.hits" : "gui.quantimium.lens_mount.interact.uses")
                        : Component.translatable("gui.quantimium.lens_mount.settings");
                graphics.setComponentTooltipForNextFrame(font, List.of(tip), mouseX, mouseY);
            }
        }
    }

    /** Whether the lens in {@code slot} has settings of its own: a Filter lens, an Interact lens. */
    private boolean hasSettings(int slot) {
        var item = menu.getBlockEntity().getLensSlots().getStackInSlot(slot).getItem();
        return item instanceof com.kadikular.quantimium.item.FilterLensItem || item instanceof com.kadikular.quantimium.item.InteractLensItem;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (activeOverlay() == null && event.button() == 0) {
            for (int slot = 0; slot < LensMountMenu.SLOT_X.length; slot++) {
                if (hasSettings(slot) && com.kadikular.quantimium.client.screen.ui.QuantumWidgets.hit(event.x(), event.y(),
                        leftPos + LensMountMenu.SLOT_X[slot] - 1, topPos + LensMountMenu.BUTTON_Y, LensMountMenu.BUTTON_W, LensMountMenu.BUTTON_H)) {
                    if (minecraft != null && minecraft.gameMode != null) {
                        // The settings screen opens where the pointer is, not at the middle of the window.
                        if (!(menu.getBlockEntity().getLensSlots().getStackInSlot(slot).getItem() instanceof com.kadikular.quantimium.item.InteractLensItem)) {
                            FilterLensScreen.keepCursor(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
                        }
                        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static Component name(Lens lens) {
        return Component.translatable(lens.itemKey());
    }
}
