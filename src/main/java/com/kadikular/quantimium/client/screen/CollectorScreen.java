package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.CollectorBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.menu.CollectorMenu;
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
 * Two views on one panel: what the Collector has caught, in a dispenser's 3x3 grid, or its filter, nine
 * ghost entries: click one with an item, or drag an item onto it from JEI; shift-click for its tags.
 */
public class CollectorScreen extends QuantumMachineScreen<CollectorMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/collector.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/collector_filter.png");

    public CollectorScreen(CollectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private CollectorBlockEntity collector() {
        return menu.getBlockEntity();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of("F", () -> menu.setFilterView(!menu.isFilterView()),
                                List.of(Component.translatable("gui.quantimium.collector.filter"),
                                        Component.translatable("gui.quantimium.collector.filter.tip")))
                        .icon(() -> new ItemStack(menu.isFilterView() ? Items.HOPPER : Items.ITEM_FRAME)),
                Tab.of(() -> Component.literal(collector().allows() ? "W" : "B"), () -> press(CollectorMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable(collector().allows() ? "gui.quantimium.collector.mode.allow"
                                                : "gui.quantimium.collector.mode.deny"),
                                        Component.translatable("gui.quantimium.collector.mode.tip")))
                        .icon(() -> new ItemStack(collector().allows() ? Items.WHITE_WOOL : Items.BLACK_WOOL))
                        .visible(menu::isFilterView),
                Tab.of(() -> Component.literal(switch (collector().mode()) { case COLLECT -> "C"; case SEND -> "S"; case BOTH -> "B"; }),
                                () -> press(CollectorMenu.BUTTON_RELAY),
                                () -> List.of(Component.translatable("gui.quantimium.item_relay.mode." + collector().mode().name().toLowerCase(java.util.Locale.ROOT)),
                                        Component.translatable("gui.quantimium.item_relay.mode.tip")))
                        .icon(() -> new ItemStack(switch (collector().mode()) { case COLLECT -> Items.HOPPER; case SEND -> Items.DISPENSER; case BOTH -> Items.DROPPER; })),
                Tab.of("S", () -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                                        new com.kadikular.quantimium.network.RequestSideConfigPayload(collector().getBlockPos())),
                                List.of(Component.translatable("gui.quantimium.item_relay.sides"), Component.translatable("gui.quantimium.collector.sides.tip")))
                        .icon(new ItemStack(Items.HOPPER)));
    }

    public void openSideConfig(com.kadikular.quantimium.network.OpenSideConfigPayload payload) {
        openOverlay(new SideConfigScreen(payload.pos(), payload.configs(), payload.profile(), false));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        if (!menu.isFilterView()) return targets;
        for (int i = 0; i < CollectorBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + CollectorMenu.FILTER_X + i * 18, topPos + CollectorMenu.FILTER_Y, 16, 16));
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
        for (int i = 0; i < CollectorBlockEntity.FILTER_SLOTS; i++) if (!collector().getFilter().getItem(i).isEmpty()) count++;
        return count;
    }

    @Override
    protected Component statusLabel() {
        if (listed() == 0) return Component.translatable("gui.quantimium.collector.status.all");
        return Component.translatable(collector().allows() ? "gui.quantimium.collector.status.allow" : "gui.quantimium.collector.status.deny", listed());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.collector.status.tip"));
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
        for (int i = 0; i < CollectorBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = collector().getFilter().getItem(i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            graphics.text(font, "#", leftPos + CollectorMenu.FILTER_X + i * 18 + 11, topPos + CollectorMenu.FILTER_Y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < CollectorMenu.FILTER_START || index >= CollectorMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag", "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item").withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
