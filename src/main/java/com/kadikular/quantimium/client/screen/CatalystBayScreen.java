package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.menu.CatalystBayMenu;
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
 * Two views on one panel: the bay's four catalysts and what its filter does, or the filter itself. The
 * filter's slots are ghosts: click one with an item, or drag an item onto it from JEI.
 */
public class CatalystBayScreen extends QuantumMachineScreen<CatalystBayMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/catalyst_bay.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/catalyst_bay_filter.png");

    private static final int INFO_X = 76;
    private static final int INFO_Y = 24;
    private static final int LINE_HEIGHT = 10;

    public CatalystBayScreen(CatalystBayMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private CatalystBayBlockEntity bay() {
        return menu.getBlockEntity();
    }

    /** Whitelist or blacklist, for whichever list the filter view is showing. */
    private boolean shownListAllows() {
        return menu.isInputList() ? bay().inputsAllow() : bay().outputsAllow();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of("F", () -> menu.setFilterView(!menu.isFilterView()),
                                List.of(Component.translatable("gui.quantimium.catalyst_bay.filter"),
                                        Component.translatable("gui.quantimium.catalyst_bay.filter.tip")))
                        .icon(() -> new ItemStack(menu.isFilterView() ? Items.CRAFTING_TABLE : Items.ITEM_FRAME)),
                // Which list the filter view is editing: outputs made, or inputs used up.
                Tab.of(() -> Component.literal(menu.isInputList() ? "I" : "O"),
                                () -> menu.setInputList(!menu.isInputList()),
                                () -> List.of(Component.translatable(menu.isInputList()
                                                ? "gui.quantimium.catalyst_bay.list.inputs"
                                                : "gui.quantimium.catalyst_bay.list.outputs"),
                                        Component.translatable("gui.quantimium.catalyst_bay.list.tip")))
                        .icon(() -> new ItemStack(menu.isInputList() ? Items.HOPPER : Items.DROPPER))
                        .visible(menu::isFilterView),
                // Whitelist or blacklist, for the list on show; each list has its own.
                Tab.of(() -> Component.literal(shownListAllows() ? "W" : "B"),
                                () -> press(menu.isInputList() ? CatalystBayMenu.BUTTON_INPUT_MODE
                                        : CatalystBayMenu.BUTTON_OUTPUT_MODE),
                                () -> List.of(Component.translatable(shownListAllows()
                                                ? "gui.quantimium.catalyst_bay.mode.allow"
                                                : "gui.quantimium.catalyst_bay.mode.deny"),
                                        Component.translatable(menu.isInputList()
                                                ? "gui.quantimium.catalyst_bay.mode.inputs.tip"
                                                : "gui.quantimium.catalyst_bay.mode.outputs.tip")))
                        .icon(() -> new ItemStack(shownListAllows() ? Items.WHITE_WOOL : Items.BLACK_WOOL))
                        .visible(menu::isFilterView));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        if (!menu.isFilterView()) return targets;
        for (int i = 0; i < CatalystBayBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + CatalystBayMenu.FILTER_X + (i % 9) * 18,
                    topPos + CatalystBayMenu.FILTER_Y + (i / 9) * 18, 16, 16));
        }
        return targets;
    }

    @Override
    public int ghostOffset() {
        return menu.isInputList() ? CatalystBayBlockEntity.INPUT_FILTER_START : 0;
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
    protected int energyStored() {
        return 0;
    }

    @Override
    protected int maxEnergyStored() {
        return 0;
    }

    @Override
    protected boolean hasEnergy() {
        return false;
    }

    private int catalysts() {
        int count = 0;
        for (int i = 0; i < CatalystBayBlockEntity.SLOTS; i++) if (!bay().getCatalyst(i).isEmpty()) count++;
        return count;
    }

    @Override
    protected Component statusLabel() {
        if (menu.isFilterView()) {
            return Component.translatable(menu.isInputList()
                    ? (bay().inputsAllow() ? "gui.quantimium.catalyst_bay.list.inputs.allow.short"
                            : "gui.quantimium.catalyst_bay.list.inputs.short")
                    : "gui.quantimium.catalyst_bay.list.outputs.short");
        }
        return Component.translatable("gui.quantimium.catalyst_bay.status", catalysts(), CatalystBayBlockEntity.SLOTS);
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.catalyst_bay.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return catalysts() > 0 ? PanelTheme.Tone.OK : PanelTheme.Tone.IDLE;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.isFilterView()) return;
        int outputs = listed(0);
        int inputs = listed(CatalystBayBlockEntity.INPUT_FILTER_START);
        List<Component> lines = new ArrayList<>();
        lines.add(outputs == 0 ? Component.translatable("gui.quantimium.catalyst_bay.filter.none")
                : Component.translatable(bay().outputsAllow() ? "gui.quantimium.catalyst_bay.filter.allow"
                        : "gui.quantimium.catalyst_bay.filter.deny", outputs));
        if (inputs > 0) {
            lines.add(Component.translatable(bay().inputsAllow() ? "gui.quantimium.catalyst_bay.filter.inputs.allow"
                    : "gui.quantimium.catalyst_bay.filter.inputs", inputs));
        }
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE_HEIGHT,
                    i == 0 ? textColour() : dimColour(), false);
        }
    }

    private int listed(int start) {
        int count = 0;
        for (int i = 0; i < CatalystBayBlockEntity.FILTER_SLOTS; i++) {
            if (!bay().getFilter().getItem(start + i).isEmpty()) count++;
        }
        return count;
    }

    /** A small # on entries standing for a tag rather than an item. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.isFilterView()) return;
        graphics.nextStratum();
        for (int i = 0; i < CatalystBayBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = bay().getFilter().getItem(ghostOffset() + i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            int x = leftPos + CatalystBayMenu.FILTER_X + (i % 9) * 18;
            int y = topPos + CatalystBayMenu.FILTER_Y + (i / 9) * 18;
            graphics.text(font, "#", x + 11, y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < CatalystBayMenu.FILTER_START || index >= CatalystBayMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag",
                        "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item")
                        .withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Override
    public int ghostContainerId() {
        return menu.containerId;
    }
}
