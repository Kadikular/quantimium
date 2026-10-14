package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.menu.LogisticsReceptacleMenu;
import com.kadikular.quantimium.menu.PriorityButton;
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
 * A Logistics Receptacle's screen: its filter, nine ghost entries (click one with an item, drag one in from JEI,
 * shift-click for its tags), and on the rail allow/deny, priority, its role, and active or passive.
 */
public class LogisticsReceptacleScreen extends QuantumMachineScreen<LogisticsReceptacleMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/logistics_receptacle.png");

    public LogisticsReceptacleScreen(LogisticsReceptacleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private LogisticsReceptacleBlockEntity receptacle() {
        return menu.getBlockEntity();
    }

    private String role() {
        return receptacle().role().getSerializedName();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                Tab.glyph(() -> receptacle().allows() ? GuiIcon.ALLOW : GuiIcon.DENY, () -> press(LogisticsReceptacleMenu.BUTTON_ALLOW),
                                () -> List.of(Component.translatable(receptacle().allows() ? "gui.quantimium.filter.allow" : "gui.quantimium.filter.deny"),
                                        hint("gui.quantimium.tab.switch")))
                        .slot(RailSlot.FILTER),
                priorityTab(() -> receptacle().priority(), value -> press(PriorityButton.encode(value)), PriorityButton.MAX),
                Tab.glyph(() -> switch (receptacle().role()) {
                                    case STORAGE -> GuiIcon.STORAGE;
                                    case PROVIDER -> GuiIcon.PROVIDER;
                                    case REQUESTER -> GuiIcon.REQUESTER;
                                }, () -> press(LogisticsReceptacleMenu.BUTTON_ROLE),
                                () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.role." + role()),
                                        hint("gui.quantimium.tab.cycle")))
                        // Three roles: two steps forward is one back.
                        .onRightClick(() -> {
                            press(LogisticsReceptacleMenu.BUTTON_ROLE);
                            press(LogisticsReceptacleMenu.BUTTON_ROLE);
                        }),
                Tab.glyph(() -> !receptacle().active() ? GuiIcon.PASSIVE
                                        : receptacle().role() == LogisticsMember.Role.PROVIDER ? GuiIcon.ACTIVE_EXPORT : GuiIcon.ACTIVE_IMPORT,
                                () -> press(LogisticsReceptacleMenu.BUTTON_ACTIVE),
                                () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle." + (receptacle().active() ? "active" : "passive") + "." + role()),
                                        hint("gui.quantimium.tab.switch")))
                        .visible(() -> receptacle().role() != LogisticsMember.Role.STORAGE));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        for (int i = 0; i < LogisticsReceptacleBlockEntity.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + LogisticsReceptacleMenu.FILTER_X + i * 18, topPos + LogisticsReceptacleMenu.FILTER_Y, 16, 16));
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
        return receptacle().describe();
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.logistics_receptacle.status.tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return receptacle().isConnected() ? PanelTheme.Tone.OK : PanelTheme.Tone.WARN;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {

    }

    /** A small # on entries standing for a tag rather than an item. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        for (int i = 0; i < LogisticsReceptacleBlockEntity.FILTER_SLOTS; i++) {
            ItemStack entry = receptacle().getFilter().getItem(i);
            if (entry.isEmpty() || FilterEntry.tagOf(entry).isEmpty()) continue;
            graphics.text(font, "#", leftPos + LogisticsReceptacleMenu.FILTER_X + i * 18 + 11, topPos + LogisticsReceptacleMenu.FILTER_Y - 1, QuantumUiColours.ACCENT, true);
        }
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < LogisticsReceptacleMenu.FILTER_START || index >= LogisticsReceptacleMenu.FILTER_END) return lines;
        lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag", "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item").withStyle(ChatFormatting.AQUA)));
        lines.add(Component.translatable("gui.quantimium.me_superposition_crafter.entry.hint").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
