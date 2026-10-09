package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.laser.LensFilter;
import com.kadikular.quantimium.menu.FilterLensMenu;
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

/** A Filter lens's settings: nine ghost entries, whitelist or blacklist, and which living things it lets through. */
public class FilterLensScreen extends QuantumMachineScreen<FilterLensMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/collector_filter.png");

    public FilterLensScreen(FilterLensMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of(() -> Component.literal(menu.filter().allow() ? "W" : "B"), () -> press(FilterLensMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable(menu.filter().allow() ? "gui.quantimium.filter_lens.mode.allow"
                                                : "gui.quantimium.filter_lens.mode.deny"),
                                        Component.translatable("gui.quantimium.filter_lens.mode.tip")))
                        .icon(() -> new ItemStack(menu.filter().allow() ? Items.WHITE_WOOL : Items.BLACK_WOOL)),
                Tab.of(() -> Component.literal("L"), () -> press(FilterLensMenu.BUTTON_LIVING),
                                () -> List.of(Component.translatable("item.quantimium.filter_lens.living." + menu.filter().living().getSerializedName()),
                                        Component.translatable("gui.quantimium.filter_lens.living.tip")))
                        .icon(() -> new ItemStack(switch (menu.filter().living()) {
                            case ANY -> Items.ARMOR_STAND;
                            case HOSTILE -> Items.ZOMBIE_HEAD;
                            case PASSIVE -> Items.WHEAT;
                            case PLAYERS -> Items.PLAYER_HEAD;
                            case NOT_PLAYERS -> Items.SKELETON_SKULL;
                        })));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        for (int i = 0; i < LensFilter.SLOTS; i++) targets.add(new Rect2i(leftPos + FilterLensMenu.FILTER_X + i * 18, topPos + FilterLensMenu.FILTER_Y, 16, 16));
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
        return Component.translatable("item.quantimium.filter_lens.living." + menu.filter().living().getSerializedName());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("item.quantimium.filter_lens.tip"));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.quantimium.filter_lens.note"), leftPos + 8, topPos + 22, dimColour(), false);
    }

    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        for (int i = 0; i < LensFilter.SLOTS; i++) {
            ItemStack entry = menu.filter().entry(i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            graphics.text(font, "#", leftPos + FilterLensMenu.FILTER_X + i * 18 + 11, topPos + FilterLensMenu.FILTER_Y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < 0 || index >= FilterLensMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag", "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item").withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
