package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.menu.LaserPortMenu;
import com.kadikular.quantimium.recipe.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Two views on one panel: what the Laser Port holds, in a dispenser's 3x3 grid, or the filter on what it
 * catches, nine ghost entries: click one with an item, or drag an item onto it from JEI; shift-click for its tags.
 */
public class LaserPortScreen extends QuantumMachineScreen<LaserPortMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_port.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_port_filter.png");

    public LaserPortScreen(LaserPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private LaserPortBlockEntity port() {
        return menu.getBlockEntity();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                filterTab(menu::isFilterView, menu::setFilterView, Component.translatable("gui.quantimium.laser_port.filter"),
                        "gui.quantimium.laser_port.filter.tip"),
                Tab.glyph(() -> port().allows() ? GuiIcon.ALLOW : GuiIcon.DENY, () -> press(LaserPortMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable(port().allows() ? "gui.quantimium.filter.allow" : "gui.quantimium.filter.deny"),
                                        hint("gui.quantimium.tab.switch")))
                        .during(menu::isFilterView),
                Tab.glyph(() -> switch (port().mode()) {
                                    case COLLECT -> GuiIcon.COLLECT;
                                    case SEND -> GuiIcon.SEND;
                                    case BOTH -> GuiIcon.BOTH;
                                }, () -> press(LaserPortMenu.BUTTON_RELAY),
                                () -> List.of(Component.translatable("gui.quantimium.laser_port.mode." + port().mode().name().toLowerCase(java.util.Locale.ROOT)),
                                        hint("gui.quantimium.tab.cycle")))
                        // Three modes: two steps on is one back.
                        .onRightClick(() -> {
                            press(LaserPortMenu.BUTTON_RELAY);
                            press(LaserPortMenu.BUTTON_RELAY);
                        }),
                // Under the mode it's set to.
                channelTab(menu),
                sidesTab(port().getBlockPos()));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        if (!menu.isFilterView()) return targets;
        for (int i = 0; i < LaserPortBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + LaserPortMenu.FILTER_X + i * 18, topPos + LaserPortMenu.FILTER_Y, 16, 16));
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

    private int listed() {
        int count = 0;
        for (int i = 0; i < LaserPortBlockEntity.FILTER_SLOTS; i++) if (!port().getFilter().getItem(i).isEmpty()) count++;
        return count;
    }

    @Override
    protected Component statusLabel() {
        if (port().mode() == LaserPortBlockEntity.Mode.SEND) return Component.translatable("gui.quantimium.laser_port.status.send");
        if (listed() == 0) return Component.translatable("gui.quantimium.laser_port.status.all");
        return Component.translatable(port().allows() ? "gui.quantimium.laser_port.status.allow" : "gui.quantimium.laser_port.status.deny", listed());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable(port().mode() == LaserPortBlockEntity.Mode.SEND ? "gui.quantimium.laser_port.status.send.tip"
                : "gui.quantimium.laser_port.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return PanelTheme.Tone.OK;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {

    }

    /** A small # on entries standing for a tag rather than an item. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.isFilterView()) return;
        graphics.nextStratum();
        for (int i = 0; i < LaserPortBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = port().getFilter().getItem(i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            graphics.text(font, "#", leftPos + LaserPortMenu.FILTER_X + i * 18 + 11, topPos + LaserPortMenu.FILTER_Y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < LaserPortMenu.FILTER_START || index >= LaserPortMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag", "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item").withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
