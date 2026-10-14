package com.kadikular.quantimium.compat.ae2.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.GhostFilterScreen;
import com.kadikular.quantimium.client.screen.QuantumMachineScreen;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.compat.ae2.ReactorMePortBlockEntity;
import com.kadikular.quantimium.compat.ae2.ReactorMePortMenu;
import com.kadikular.quantimium.menu.PriorityButton;
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
import java.util.Locale;

/**
 * The ME Superposition Port, set up like a storage bus: a readout, and on the rail its filter, priority, mode
 * and whether the network may store items in the Reactor. The filter has two lists, one for which things it
 * offers patterns for and one for which held items the network sees; its view adds their tabs at the bottom.
 */
public class ReactorMePortScreen extends QuantumMachineScreen<ReactorMePortMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/reactor_me_port.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/reactor_me_port_filter.png");

    /** The readout (tools/gui_panels.py reactor_me_port). */
    private static final int INFO_X = 8;
    private static final int INFO_Y = 20;
    private static final int LINE_HEIGHT = 11;

    public ReactorMePortScreen(ReactorMePortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private String modeKey() {
        return menu.mode().name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.glyph(() -> GuiIcon.FILTER, () -> menu.setFilterView(!menu.isFilterView()),
                                () -> List.of(Component.translatable("gui.quantimium.reactor_me_port.filter"),
                                        hint("gui.quantimium.reactor_me_port.filter.tip")))
                        .on(menu::isFilterView)
                        .slot(RailSlot.FILTER),
                priorityTab(menu::priority, value -> press(PriorityButton.encode(value, ReactorMePortBlockEntity.MAX_PRIORITY)),
                        ReactorMePortBlockEntity.MAX_PRIORITY),
                // How the Reactor's making is offered: both, whole trees or steps.
                Tab.of(Component::empty, () -> press(ReactorMePortMenu.BUTTON_MODE_NEXT),
                                () -> List.of(Component.translatable("message.quantimium.reactor_me_port.mode." + modeKey()),
                                        hint("gui.quantimium.tab.cycle")))
                        .icon(() -> new ItemStack(switch (menu.mode()) {
                            case BOTH -> Items.ENDER_EYE;
                            case TREES -> Items.OAK_SAPLING;
                            case STEPS -> Items.CRAFTING_TABLE;
                        }))
                        .onRightClick(() -> press(ReactorMePortMenu.BUTTON_MODE_BACK)),
                // Whether the network may store items in the Reactor, as in a drive.
                Tab.glyph(() -> GuiIcon.STORAGE, () -> press(ReactorMePortMenu.BUTTON_ACCEPTS),
                                () -> List.of(Component.translatable(menu.acceptsItems()
                                                ? "gui.quantimium.reactor_me_port.accepts.on" : "gui.quantimium.reactor_me_port.accepts.off"),
                                        hint("gui.quantimium.tab.switch")))
                        .on(menu::acceptsItems),
                // Which list the filter view is editing: what it offers patterns for, or what the network sees.
                Tab.glyph(() -> menu.isStorageList() ? GuiIcon.STORAGE : GuiIcon.VIEW_CRAFT,
                                () -> menu.setStorageList(!menu.isStorageList()),
                                () -> List.of(Component.translatable(menu.isStorageList()
                                                ? "gui.quantimium.reactor_me_port.list.storage"
                                                : "gui.quantimium.reactor_me_port.list.patterns"),
                                        hint("gui.quantimium.tab.switch")))
                        .visible(menu::isFilterView)
                        .slot(RailSlot.VIEW),
                // Allow or deny, for the list on show; each list has its own.
                Tab.glyph(() -> shownListAllows() ? GuiIcon.ALLOW : GuiIcon.DENY,
                                () -> press(menu.isStorageList() ? ReactorMePortMenu.BUTTON_STORAGE_ALLOW
                                        : ReactorMePortMenu.BUTTON_PATTERNS_ALLOW),
                                () -> List.of(Component.translatable(shownListAllows() ? "gui.quantimium.filter.allow" : "gui.quantimium.filter.deny"),
                                        hint("gui.quantimium.tab.switch")))
                        .visible(menu::isFilterView)
                        .slot(RailSlot.VIEW));
    }

    private boolean shownListAllows() {
        return menu.isStorageList() ? menu.storageAllow() : menu.patternsAllow();
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
        for (int i = 0; i < ReactorMePortBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + ReactorMePortMenu.FILTER_X + (i % 9) * 18,
                    topPos + ReactorMePortMenu.FILTER_Y + (i / 9) * 18, 16, 16));
        }
        return targets;
    }

    @Override
    public int ghostOffset() {
        return menu.isStorageList() ? ReactorMePortBlockEntity.STORAGE_FILTER_START : 0;
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

    @Override
    protected Component statusLabel() {
        if (menu.isFilterView()) {
            return Component.translatable(menu.isStorageList() ? "gui.quantimium.reactor_me_port.list.storage.short"
                    : "gui.quantimium.reactor_me_port.list.patterns.short");
        }
        return Component.translatable("gui.quantimium.reactor_me_port.status." + switch (menu.link()) {
            case 2 -> "online";
            case 1 -> "offline";
            default -> "unlinked";
        });
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.reactor_me_port.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.link()) {
            case 2 -> PanelTheme.Tone.OK;
            case 1 -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.isFilterView()) return;
        List<Component> lines = List.of(
                Component.translatable("message.quantimium.reactor_me_port.mode." + modeKey() + ".short"),
                Component.translatable("gui.quantimium.reactor_me_port.shows", String.format(Locale.ROOT, "%,d", menu.shownKinds())),
                Component.translatable("gui.quantimium.reactor_me_port.patterns", String.format(Locale.ROOT, "%,d", menu.patternCount())),
                Component.translatable(menu.acceptsItems() ? "gui.quantimium.reactor_me_port.stores.on"
                        : "gui.quantimium.reactor_me_port.stores.off"));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE_HEIGHT,
                    i == 0 ? textColour() : dimColour(), false);
        }
    }

    /** A small # on entries standing for a tag rather than an item. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.isFilterView()) return;
        graphics.nextStratum();
        for (int i = 0; i < ReactorMePortBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = menu.getSlot(ghostOffset() + i).getItem();
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            int x = leftPos + ReactorMePortMenu.FILTER_X + (i % 9) * 18;
            int y = topPos + ReactorMePortMenu.FILTER_Y + (i / 9) * 18;
            graphics.text(font, "#", x + 11, y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < ReactorMePortMenu.FILTER_START || index >= ReactorMePortMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag",
                        "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item")
                        .withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
