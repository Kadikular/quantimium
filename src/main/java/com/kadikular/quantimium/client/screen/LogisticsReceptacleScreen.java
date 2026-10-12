package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.menu.LogisticsReceptacleMenu;
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
 * A Logistics Receptacle's screen: its filter, nine ghost entries (click one with an item, drag one in from JEI,
 * shift-click for its tags), and tabs for its role, active or passive, priority (shift for tens) and allow/deny.
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
                Tab.of(() -> Component.literal(role().substring(0, 1).toUpperCase(java.util.Locale.ROOT)), () -> press(LogisticsReceptacleMenu.BUTTON_ROLE),
                                () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.role." + role()),
                                        Component.translatable("gui.quantimium.logistics_receptacle.role.tip")))
                        .icon(() -> new ItemStack(switch (receptacle().role()) {
                            case STORAGE -> Items.CHEST;
                            case PROVIDER -> Items.DROPPER;
                            case REQUESTER -> Items.HOPPER;
                        })),
                Tab.of(() -> Component.literal(receptacle().active() ? "A" : "P"), () -> press(LogisticsReceptacleMenu.BUTTON_ACTIVE),
                                () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle." + (receptacle().active() ? "active" : "passive") + "." + role()),
                                        Component.translatable("gui.quantimium.logistics_receptacle.active.tip")))
                        .icon(() -> new ItemStack(receptacle().active() ? Items.REDSTONE_TORCH : Items.LEVER))
                        .visible(() -> receptacle().role() != LogisticsMember.Role.STORAGE),
                Tab.of(() -> Component.literal("+"), () -> press(shift() ? LogisticsReceptacleMenu.BUTTON_PRIORITY_UP_TEN : LogisticsReceptacleMenu.BUTTON_PRIORITY_UP),
                        () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.priority", receptacle().priority()),
                                Component.translatable("gui.quantimium.logistics_receptacle.priority.tip"))),
                Tab.of(() -> Component.literal("-"), () -> press(shift() ? LogisticsReceptacleMenu.BUTTON_PRIORITY_DOWN_TEN : LogisticsReceptacleMenu.BUTTON_PRIORITY_DOWN),
                        () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.priority", receptacle().priority()),
                                Component.translatable("gui.quantimium.logistics_receptacle.priority.tip"))),
                Tab.of(() -> Component.literal(receptacle().allows() ? "W" : "B"), () -> press(LogisticsReceptacleMenu.BUTTON_ALLOW),
                                () -> List.of(Component.translatable(receptacle().allows() ? "gui.quantimium.logistics_receptacle.allow" : "gui.quantimium.logistics_receptacle.deny"),
                                        Component.translatable("gui.quantimium.logistics_receptacle.allow.tip")))
                        .icon(() -> new ItemStack(receptacle().allows() ? Items.WHITE_WOOL : Items.BLACK_WOOL)));
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
