package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.laser.IsolatorSettings;
import com.kadikular.quantimium.menu.IsolatorLensMenu;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An Isolator Lens's settings: on the left whether a network, actions and modifiers pass it, and whether the lenses
 * listed are let through only or stopped; the list itself, nine lenses, on the panel.
 */
public class IsolatorLensScreen extends QuantumMachineScreen<IsolatorLensMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_port_filter.png");

    public IsolatorLensScreen(IsolatorLensMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        FilterLensScreen.restoreCursor(minecraft);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.glyph(() -> menu.settings().allow() ? GuiIcon.ALLOW : GuiIcon.DENY, () -> press(IsolatorLensMenu.BUTTON_ALLOW),
                        () -> List.of(Component.translatable(menu.settings().allow() ? "gui.quantimium.isolator_lens.allow" : "gui.quantimium.isolator_lens.deny"),
                                hint("gui.quantimium.tab.switch"))),
                passes("logistics", IsolatorLensMenu.BUTTON_LOGISTICS, new ItemStack(ModItems.LOGISTICS_CONTROLLER_ITEM.get()), IsolatorSettings::logistics),
                passes("actions", IsolatorLensMenu.BUTTON_ACTIONS, new ItemStack(ModItems.HARVEST_LENS.get()), IsolatorSettings::actions),
                passes("modifiers", IsolatorLensMenu.BUTTON_MODIFIERS, new ItemStack(ModItems.AMPLIFY_LENS.get()), IsolatorSettings::modifiers));
    }

    /** A switch for what passes it: lit while it does. */
    private Tab passes(String key, int button, ItemStack icon, Predicate<IsolatorSettings> on) {
        return Tab.of(Component::empty, () -> press(button),
                        () -> List.of(Component.translatable("gui.quantimium.isolator_lens." + key)
                                        .append(": ").append(Component.translatable(on.test(menu.settings()) ? "gui.quantimium.isolator_lens.passes" : "gui.quantimium.isolator_lens.stops")),
                                hint("gui.quantimium.tab.switch")))
                .icon(icon)
                .on(() -> on.test(menu.settings()));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        for (int i = 0; i < IsolatorSettings.SLOTS; i++) {
            targets.add(new Rect2i(leftPos + IsolatorLensMenu.FILTER_X + i * 18, topPos + IsolatorLensMenu.FILTER_Y, 16, 16));
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
