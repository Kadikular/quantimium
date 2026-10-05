package com.kadikular.quantimium.compat.ae2.client;

import com.kadikular.quantimium.client.screen.EnergyBarTooltip;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.QuantumMachineScreen;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.compat.ae2.SuperpositionCrafterBlockEntity;
import com.kadikular.quantimium.compat.ae2.SuperpositionCrafterMenu;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.recipe.FilterEntry;
import com.kadikular.quantimium.flux.BandGate;
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
 * Two views on one panel: the catalyst and what it is offering the network, or the filter. The
 * filter's slots are ghosts: click one with an item, or drag an item onto it from JEI.
 */
public class SuperpositionCrafterScreen extends QuantumMachineScreen<SuperpositionCrafterMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/me_superposition_crafter.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/me_superposition_crafter_filter.png");

    private static final int INFO_X = 58;
    private static final int INFO_Y = 22;
    /** Five short lines fit the band between the title rule and the inventory rule. */
    private static final int LINE_HEIGHT = 10;

    public SuperpositionCrafterScreen(SuperpositionCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private boolean allow() {
        return menu.getFilterMode() == SuperpositionCrafterBlockEntity.MODE_ALLOW;
    }

    private boolean inputsAllow() {
        return menu.getInputMode() == SuperpositionCrafterBlockEntity.MODE_ALLOW;
    }

    /** Whitelist or blacklist, for whichever list the filter view is showing. */
    private boolean shownListAllows() {
        return menu.isInputList() ? inputsAllow() : allow();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of("F", () -> menu.setFilterView(!menu.isFilterView()),
                                List.of(Component.translatable("gui.quantimium.me_superposition_crafter.filter"),
                                        Component.translatable("gui.quantimium.me_superposition_crafter.filter.tip")))
                        .icon(() -> new ItemStack(menu.isFilterView() ? Items.CRAFTING_TABLE : Items.ITEM_FRAME)),
                Tab.of(() -> Component.literal(menu.getBatch() + "x"), () -> press(SuperpositionCrafterMenu.BUTTON_BATCH),
                                () -> List.of(Component.translatable("gui.quantimium.me_superposition_crafter.batch", menu.getBatch()),
                                        Component.translatable("gui.quantimium.me_superposition_crafter.batch.tip")))
                        .width(28)
                        .visible(() -> !menu.isFilterView()),
                // Which list the filter view is editing: outputs offered, or inputs never consumed.
                Tab.of(() -> Component.literal(menu.isInputList() ? "I" : "O"),
                                () -> menu.setInputList(!menu.isInputList()),
                                () -> List.of(Component.translatable(menu.isInputList()
                                                ? "gui.quantimium.me_superposition_crafter.list.inputs"
                                                : "gui.quantimium.me_superposition_crafter.list.outputs"),
                                        Component.translatable("gui.quantimium.me_superposition_crafter.list.tip")))
                        .icon(() -> new ItemStack(menu.isInputList() ? Items.HOPPER : Items.DROPPER))
                        .visible(menu::isFilterView),
                // Whitelist or blacklist, for the list on show; each list has its own.
                Tab.of(() -> Component.literal(shownListAllows() ? "W" : "B"),
                                () -> press(menu.isInputList() ? SuperpositionCrafterMenu.BUTTON_INPUT_MODE
                                        : SuperpositionCrafterMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable(shownListAllows()
                                                ? "gui.quantimium.me_superposition_crafter.mode.allow"
                                                : "gui.quantimium.me_superposition_crafter.mode.deny"),
                                        Component.translatable(menu.isInputList()
                                                ? "gui.quantimium.me_superposition_crafter.mode.inputs.tip"
                                                : "gui.quantimium.me_superposition_crafter.mode.tip")))
                        .icon(() -> new ItemStack(shownListAllows() ? Items.WHITE_WOOL : Items.BLACK_WOOL))
                        .visible(menu::isFilterView));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    /** Where the filter's ghost slots are on screen, for JEI to drop onto; empty while hidden. */
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        if (!menu.isFilterView()) return targets;
        for (int i = 0; i < SuperpositionCrafterBlockEntity.FILTER_SLOTS; i++) {
            // Index i is the i-th spot; ghostOffset() says which list those spots belong to now.
            targets.add(new Rect2i(leftPos + SuperpositionCrafterMenu.FILTER_X + (i % 9) * 18,
                    topPos + SuperpositionCrafterMenu.FILTER_Y + (i / 9) * 18, 16, 16));
        }
        return targets;
    }

    /** Filter index of the first spot in the list on show: outputs start at 0, inputs after them. */
    public int ghostOffset() {
        return menu.isInputList() ? SuperpositionCrafterBlockEntity.INPUT_FILTER_START : 0;
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
        return menu.getEnergyStored();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.getMaxEnergyStored();
    }

    @Override
    protected boolean hasEnergy() {
        return !menu.isFilterView();
    }

    /** In the filter view the strip names the list being edited, which is what the eye goes to. */
    @Override
    protected List<Component> energyTooltip() {
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(),
                menu.getAverageFe(), menu.getAverageFe(), false,
                menu.getSurchargePercent(), menu.getAnomalyBand(), menu.getPassiveDrain());
    }

    @Override
    protected Component statusLabel() {
        if (menu.isFilterView()) {
            if (!menu.isInputList()) return Component.translatable("gui.quantimium.me_superposition_crafter.list.outputs.short");
            return Component.translatable(inputsAllow()
                    ? "gui.quantimium.me_superposition_crafter.list.inputs.allow.short"
                    : "gui.quantimium.me_superposition_crafter.list.inputs.short");
        }
        return Component.translatable("gui.quantimium.me_superposition_crafter.status." + statusKey());
    }

    @Override
    protected List<Component> statusTooltip() {
        if (menu.getStatusCode() == SuperpositionCrafterBlockEntity.STATUS_NEEDS_FLUX) {
            return List.of(BandGate.refusal(menu.getRequiredBand(), menu.getBand(), menu.getHeading()),
                    Component.translatable("gui.quantimium.me_superposition_crafter.status.flux.tip").withStyle(ChatFormatting.GRAY));
        }
        return List.of(Component.translatable("gui.quantimium.me_superposition_crafter.status." + statusKey() + ".tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case SuperpositionCrafterBlockEntity.STATUS_WORKING, SuperpositionCrafterBlockEntity.STATUS_READY -> PanelTheme.Tone.OK;
            case SuperpositionCrafterBlockEntity.STATUS_NO_CATALYST -> PanelTheme.Tone.IDLE;
            case SuperpositionCrafterBlockEntity.STATUS_BLOCKED -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }

    private String statusKey() {
        return switch (menu.getStatusCode()) {
            case SuperpositionCrafterBlockEntity.STATUS_OFFLINE -> "offline";
            case SuperpositionCrafterBlockEntity.STATUS_READY -> "ready";
            case SuperpositionCrafterBlockEntity.STATUS_WORKING -> "working";
            case SuperpositionCrafterBlockEntity.STATUS_NO_POWER -> "power";
            case SuperpositionCrafterBlockEntity.STATUS_BLOCKED -> "blocked";
            case SuperpositionCrafterBlockEntity.STATUS_NEEDS_FLUX -> "flux";
            default -> "no_catalyst";
        };
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.isFilterView()) return;
        List<Component> lines = new ArrayList<>(List.of(
                Component.translatable("gui.quantimium.me_superposition_crafter.recipes", menu.getRecipeCount()),
                Component.translatable("gui.quantimium.me_superposition_crafter.runs", menu.getRunsPerSecond(), menu.getBatch()),
                Component.translatable("gui.quantimium.me_superposition_crafter.draw", String.format("%,d", menu.getAverageFe())),
                outputFilterSummary()));
        int listed = blockedInputs();
        if (listed > 0) {
            lines.add(Component.translatable(inputsAllow()
                    ? "gui.quantimium.me_superposition_crafter.filter.inputs.allow"
                    : "gui.quantimium.me_superposition_crafter.filter.inputs", listed));
        }
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE_HEIGHT,
                    i == 0 ? textColour() : dimColour(), false);
        }
    }

    private Component outputFilterSummary() {
        var filter = menu.getBlockEntity().getFilter();
        int outputs = 0;
        for (int i = 0; i < SuperpositionCrafterBlockEntity.FILTER_SLOTS; i++) {
            if (!filter.getItem(i).isEmpty()) outputs++;
        }
        if (outputs == 0) return Component.translatable("gui.quantimium.me_superposition_crafter.filter.none");
        return Component.translatable(allow() ? "gui.quantimium.me_superposition_crafter.filter.allow"
                : "gui.quantimium.me_superposition_crafter.filter.deny", outputs);
    }

    private int blockedInputs() {
        var filter = menu.getBlockEntity().getFilter();
        int inputs = 0;
        for (int i = 0; i < SuperpositionCrafterBlockEntity.FILTER_SLOTS; i++) {
            if (!filter.getItem(SuperpositionCrafterBlockEntity.INPUT_FILTER_START + i).isEmpty()) inputs++;
        }
        return inputs;
    }

    /** A small # on entries standing for a tag rather than an item. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.isFilterView()) return;
        var filter = menu.getBlockEntity().getFilter();
        graphics.nextStratum();
        for (int i = 0; i < SuperpositionCrafterBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(ghostOffset() + i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            int x = leftPos + SuperpositionCrafterMenu.FILTER_X + (i % 9) * 18;
            int y = topPos + SuperpositionCrafterMenu.FILTER_Y + (i / 9) * 18;
            graphics.text(font, "#", x + 11, y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < SuperpositionCrafterMenu.FILTER_START || index >= SuperpositionCrafterMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag",
                        "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item")
                        .withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
