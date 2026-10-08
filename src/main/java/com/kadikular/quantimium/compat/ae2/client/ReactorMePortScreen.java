package com.kadikular.quantimium.compat.ae2.client;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.GhostFilterScreen;
import com.kadikular.quantimium.client.screen.QuantumMachineScreen;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.compat.ae2.ReactorMePortBlockEntity;
import com.kadikular.quantimium.compat.ae2.ReactorMePortMenu;
import com.kadikular.quantimium.recipe.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
 * The ME Superposition Port, set up like a storage bus: its mode, its priority, whether the network may
 * store items in the Reactor, and a filter of two lists, one for which things it offers patterns for and
 * one for which held items the network sees.
 */
public class ReactorMePortScreen extends QuantumMachineScreen<ReactorMePortMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/reactor_me_port.png");
    private static final Identifier FILTER_TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/reactor_me_port_filter.png");

    private static final int INFO_X = 8;
    private static final int INFO_Y = 19;
    private static final int LINE_HEIGHT = 9;
    /** The priority row: its label, then six step buttons (tools/gui_panels.py reactor_me_port). */
    private static final int PRIORITY_Y = 62;
    private static final int STEP_X = 56;
    private static final int STEP_WIDTH = 19;

    private final List<Button> steps = new ArrayList<>();

    public ReactorMePortScreen(ReactorMePortMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        steps.clear();
        for (int i = 0; i < ReactorMePortMenu.PRIORITY_STEPS.length; i++) {
            int step = ReactorMePortMenu.PRIORITY_STEPS[i];
            int id = ReactorMePortMenu.BUTTON_PRIORITY + i;
            Button button = Button.builder(Component.literal(step > 0 ? "+" + step : String.valueOf(step)), b -> press(id))
                    .bounds(leftPos + STEP_X + i * (STEP_WIDTH + 1), topPos + PRIORITY_Y - 3, STEP_WIDTH, 14).build();
            steps.add(addRenderableWidget(button));
        }
    }

    private String modeKey() {
        return menu.mode().name().toLowerCase(Locale.ROOT);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.of("F", () -> menu.setFilterView(!menu.isFilterView()),
                                List.of(Component.translatable("gui.quantimium.reactor_me_port.filter"),
                                        Component.translatable("gui.quantimium.reactor_me_port.filter.tip")))
                        .icon(() -> new ItemStack(menu.isFilterView() ? Items.CRAFTING_TABLE : Items.ITEM_FRAME)),
                // How the Reactor's making is offered: both, whole trees or steps.
                Tab.of(() -> Component.literal("M"), () -> press(ReactorMePortMenu.BUTTON_MODE_NEXT),
                                () -> List.of(Component.translatable("message.quantimium.reactor_me_port.mode." + modeKey()),
                                        Component.translatable("gui.quantimium.reactor_me_port.mode.tip")))
                        .icon(() -> new ItemStack(switch (menu.mode()) {
                            case BOTH -> Items.ENDER_EYE;
                            case TREES -> Items.OAK_SAPLING;
                            case STEPS -> Items.CRAFTING_TABLE;
                        }))
                        .visible(() -> !menu.isFilterView()),
                // Whether the network may store items in the Reactor, as in a drive.
                Tab.of(() -> Component.literal("S"), () -> press(ReactorMePortMenu.BUTTON_ACCEPTS),
                                () -> List.of(Component.translatable(menu.acceptsItems()
                                                ? "gui.quantimium.reactor_me_port.accepts.on" : "gui.quantimium.reactor_me_port.accepts.off"),
                                        Component.translatable("gui.quantimium.reactor_me_port.accepts.tip")))
                        .icon(() -> new ItemStack(menu.acceptsItems() ? Items.CHEST : Items.BARRIER))
                        .visible(() -> !menu.isFilterView()),
                // Which list the filter view is editing: what it offers patterns for, or what the network sees.
                Tab.of(() -> Component.literal(menu.isStorageList() ? "S" : "P"),
                                () -> menu.setStorageList(!menu.isStorageList()),
                                () -> List.of(Component.translatable(menu.isStorageList()
                                                ? "gui.quantimium.reactor_me_port.list.storage"
                                                : "gui.quantimium.reactor_me_port.list.patterns"),
                                        Component.translatable("gui.quantimium.reactor_me_port.list.tip")))
                        .icon(() -> new ItemStack(menu.isStorageList() ? Items.CHEST : Items.CRAFTING_TABLE))
                        .visible(menu::isFilterView),
                // Whitelist or blacklist, for the list on show; each list has its own.
                Tab.of(() -> Component.literal(shownListAllows() ? "W" : "B"),
                                () -> press(menu.isStorageList() ? ReactorMePortMenu.BUTTON_STORAGE_ALLOW
                                        : ReactorMePortMenu.BUTTON_PATTERNS_ALLOW),
                                () -> List.of(Component.translatable(shownListAllows()
                                                ? "gui.quantimium.catalyst_bay.mode.allow" : "gui.quantimium.catalyst_bay.mode.deny"),
                                        Component.translatable(menu.isStorageList()
                                                ? "gui.quantimium.reactor_me_port.storage.tip"
                                                : "gui.quantimium.reactor_me_port.patterns.tip")))
                        .icon(() -> new ItemStack(shownListAllows() ? Items.WHITE_WOOL : Items.BLACK_WOOL))
                        .visible(menu::isFilterView));
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
        for (Button step : steps) step.visible = !menu.isFilterView();
        if (menu.isFilterView()) return;
        List<Component> lines = List.of(
                Component.translatable("gui.quantimium.reactor_me_port.mode",
                        Component.translatable("message.quantimium.reactor_me_port.mode." + modeKey() + ".short")),
                Component.translatable("gui.quantimium.reactor_me_port.shows", String.format(Locale.ROOT, "%,d", menu.shownKinds())),
                Component.translatable("gui.quantimium.reactor_me_port.patterns", String.format(Locale.ROOT, "%,d", menu.patternCount())),
                Component.translatable(menu.acceptsItems() ? "gui.quantimium.reactor_me_port.accepts.on"
                        : "gui.quantimium.reactor_me_port.accepts.off"));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), leftPos + INFO_X, topPos + INFO_Y + i * LINE_HEIGHT,
                    i == 0 ? textColour() : dimColour(), false);
        }
        graphics.text(font, Component.translatable("gui.quantimium.reactor_me_port.priority", menu.priority()),
                leftPos + INFO_X, topPos + PRIORITY_Y, textColour(), false);
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
