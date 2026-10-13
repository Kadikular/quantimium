package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ReactorLogisticsPortBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.menu.ReactorLogisticsPortMenu;
import com.kadikular.quantimium.recipe.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * A Reactor Logistics Port's screen: its filter on what it offers the network, nine ghost entries (click one with an
 * item, drag one in from JEI, shift-click for its tags), and tabs for storing, sharing, priority (shift for tens) and
 * allow/deny.
 */
public class ReactorLogisticsPortScreen extends QuantumMachineScreen<ReactorLogisticsPortMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/logistics_receptacle.png");

    public ReactorLogisticsPortScreen(ReactorLogisticsPortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private ReactorLogisticsPortBlockEntity port() {
        return menu.getBlockEntity();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of(() -> Component.literal(port().storing() ? "S" : "-"), () -> press(ReactorLogisticsPortMenu.BUTTON_STORING),
                                () -> List.of(Component.translatable("gui.quantimium.reactor_logistics_port.storing." + port().storing()),
                                        Component.translatable("gui.quantimium.reactor_logistics_port.storing.tip")))
                        .icon(() -> new ItemStack(port().storing() ? Items.CHEST : Items.BARRIER)),
                Tab.of(() -> Component.literal(port().sharing() ? "C" : "-"), () -> press(ReactorLogisticsPortMenu.BUTTON_SHARING),
                                () -> List.of(Component.translatable("gui.quantimium.reactor_logistics_port.sharing." + port().sharing()),
                                        Component.translatable("gui.quantimium.reactor_logistics_port.sharing.tip")))
                        .icon(() -> new ItemStack(port().sharing() ? Items.CRAFTING_TABLE : Items.BARRIER)),
                Tab.of(() -> Component.literal("+"), () -> press(shift() ? ReactorLogisticsPortMenu.BUTTON_PRIORITY_UP_TEN : ReactorLogisticsPortMenu.BUTTON_PRIORITY_UP),
                        () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.priority", port().priorityValue()),
                                Component.translatable("gui.quantimium.logistics_receptacle.priority.tip"))),
                Tab.of(() -> Component.literal("-"), () -> press(shift() ? ReactorLogisticsPortMenu.BUTTON_PRIORITY_DOWN_TEN : ReactorLogisticsPortMenu.BUTTON_PRIORITY_DOWN),
                        () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.priority", port().priorityValue()),
                                Component.translatable("gui.quantimium.logistics_receptacle.priority.tip"))),
                Tab.of(() -> Component.literal(port().allows() ? "W" : "B"), () -> press(ReactorLogisticsPortMenu.BUTTON_ALLOW),
                                () -> List.of(Component.translatable(port().allows() ? "gui.quantimium.reactor_logistics_port.allow" : "gui.quantimium.reactor_logistics_port.deny"),
                                        Component.translatable("gui.quantimium.reactor_logistics_port.allow.tip")))
                        .icon(() -> new ItemStack(port().allows() ? Items.WHITE_WOOL : Items.BLACK_WOOL)));
    }

    private boolean shift() {
        return minecraft != null && minecraft.hasShiftDown();
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        for (int i = 0; i < ReactorLogisticsPortBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + ReactorLogisticsPortMenu.FILTER_X + i * 18, topPos + ReactorLogisticsPortMenu.FILTER_Y, 16, 16));
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
        return Component.translatable("gui.quantimium.logistics_receptacle.priority", port().priorityValue());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.reactor_logistics_port.status.tip"));
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
        graphics.nextStratum();
        for (int i = 0; i < ReactorLogisticsPortBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = port().getFilter().getItem(i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            graphics.text(font, "#", leftPos + ReactorLogisticsPortMenu.FILTER_X + i * 18 + 11, topPos + ReactorLogisticsPortMenu.FILTER_Y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < ReactorLogisticsPortMenu.FILTER_START || index >= ReactorLogisticsPortMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag", "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item").withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
