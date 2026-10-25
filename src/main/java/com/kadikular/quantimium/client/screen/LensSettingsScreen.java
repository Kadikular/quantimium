package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.item.InteractLensItem;
import com.kadikular.quantimium.menu.LensSettingsMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * An Interact lens's few switches: whether it uses or hits, and what it works on; the switches on the
 * left rail, what they're set to written on the panel. Layout: tools/gui_panels.py lens_settings.
 */
public class LensSettingsScreen extends QuantumMachineScreen<LensSettingsMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/lens_settings.png");
    private static final int TEXT_X = 10;
    private static final int TEXT_Y = 22;

    public LensSettingsScreen(LensSettingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 64);
    }

    @Override
    protected void init() {
        super.init();
        FilterLensScreen.restoreCursor(minecraft);
    }

    @Override
    protected List<Tab> createTabs() {
        ItemStack lens = menu.lens();
        List<Tab> tabs = new ArrayList<>();
        if (lens.getItem() instanceof InteractLensItem) {
            tabs.add(Tab.glyph(() -> InteractLensItem.hits(menu.lens()) ? GuiIcon.HOSTILE : GuiIcon.ACTIONS, () -> press(LensSettingsMenu.BUTTON_HITS),
                    () -> List.of(Component.translatable(InteractLensItem.hits(menu.lens()) ? "item.quantimium.interact_lens.hits" : "item.quantimium.interact_lens.uses"),
                            hint("gui.quantimium.tab.switch"))));
            tabs.add(Tab.glyph(() -> switch (InteractLensItem.targets(menu.lens())) {
                        case com.kadikular.quantimium.laser.LensUse.BLOCKS -> GuiIcon.BLOCK;
                        case com.kadikular.quantimium.laser.LensUse.ENTITIES -> GuiIcon.ANIMAL;
                        default -> GuiIcon.BOTH;
                    }, () -> press(LensSettingsMenu.BUTTON_TARGETS),
                    () -> List.of(InteractLensItem.describe(menu.lens()).get(1), hint("gui.quantimium.tab.cycle"))));
        }
        return tabs;
    }

    private void press(int button) {
        if (minecraft == null || minecraft.gameMode == null || minecraft.player == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        menu.clickMenuButton(minecraft.player, button);
    }

    /** What it's set to, a line each, with what that means. */
    private List<Component> lines() {
        ItemStack lens = menu.lens();
        List<Component> lines = new ArrayList<>();
        if (lens.getItem() instanceof InteractLensItem) {
            lines.addAll(InteractLensItem.describe(lens));
        }
        return lines;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int y = topPos + TEXT_Y;
        for (Component line : lines()) {
            for (var wrapped : font.split(line, 154)) {
                graphics.text(font, wrapped, leftPos + TEXT_X, y, textColour(), false);
                y += 10;
            }
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
        return Component.empty();
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of();
    }
}
