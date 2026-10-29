package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.Receptacle;
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

    private Receptacle receptacle() {
        return menu.getBlockEntity();
    }

    private String role() {
        return receptacle().role().getSerializedName();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                channelTab(menu),
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
                Tab.glyph(() -> receptacle().allows() ? GuiIcon.ALLOW : GuiIcon.DENY, () -> press(LogisticsReceptacleMenu.BUTTON_ALLOW),
                                () -> List.of(Component.translatable(receptacle().allows() ? "gui.quantimium.filter.allow" : "gui.quantimium.filter.deny"),
                                        hint("gui.quantimium.tab.switch"))),
                Tab.glyph(() -> !receptacle().active() ? GuiIcon.PASSIVE
                                        : receptacle().role() == LogisticsMember.Role.PROVIDER ? GuiIcon.ACTIVE_EXPORT : GuiIcon.ACTIVE_IMPORT,
                                () -> press(LogisticsReceptacleMenu.BUTTON_ACTIVE),
                                () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle." + (receptacle().active() ? "active" : "passive") + "." + role()),
                                        hint("gui.quantimium.tab.switch")))
                        .visible(() -> receptacle().role() != LogisticsMember.Role.STORAGE),
                // A provider on a machine that makes things to order: whether it offers its crafts, or only what's there.
                Tab.glyph(() -> switch (receptacle().crafts()) {
                                    case STORED -> GuiIcon.VIEW_CRAFT;
                                    case NOTHING -> GuiIcon.VIEW_REAL;
                                },
                                () -> press(LogisticsReceptacleMenu.BUTTON_MADE),
                                () -> List.of(Component.translatable("gui.quantimium.logistics_receptacle.crafts."
                                                + receptacle().crafts().name().toLowerCase(java.util.Locale.ROOT)),
                                        hint("gui.quantimium.tab.switch")))
                        .visible(() -> receptacle().role() == LogisticsMember.Role.PROVIDER && receptacle().maker() != null),
                // An active requester: whether machines may make what it asks for, or it takes only what's stored.
                Tab.glyph(() -> receptacle().requestsCraftable() ? GuiIcon.VIEW_BOTH : GuiIcon.VIEW_REAL,
                                () -> press(LogisticsReceptacleMenu.BUTTON_REQUEST_MADE),
                                () -> List.of(Component.translatable(receptacle().requestsCraftable()
                                                ? "gui.quantimium.logistics_receptacle.requests.made" : "gui.quantimium.logistics_receptacle.requests.stored"),
                                        hint("gui.quantimium.tab.switch")))
                        .visible(() -> receptacle().role() == LogisticsMember.Role.REQUESTER && receptacle().active()),
                // A requester keeping counts stocked: each entry's count is how many it keeps.
                Tab.glyph(() -> GuiIcon.KEEP, () -> press(LogisticsReceptacleMenu.BUTTON_KEEP),
                                () -> List.of(Component.translatable(receptacle().keeps() ? "gui.quantimium.logistics_receptacle.keep.on" : "gui.quantimium.logistics_receptacle.keep.off"),
                                        hint("gui.quantimium.tab.switch")))
                        .on(() -> receptacle().keeps())
                        .visible(() -> receptacle().role() == LogisticsMember.Role.REQUESTER),
                // On a machine that takes power: whether the light reaching it powers that machine too.
                Tab.glyph(() -> GuiIcon.ENERGY, () -> press(LogisticsReceptacleMenu.BUTTON_POWERS),
                                () -> List.of(Component.translatable(receptacle().powers() ? "gui.quantimium.logistics_receptacle.powers.on" : "gui.quantimium.logistics_receptacle.powers.off"),
                                        hint("gui.quantimium.tab.switch")))
                        .on(() -> receptacle().powers())
                        .visible(() -> receptacle().canPower() || receptacle().powers()));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        List<Rect2i> targets = new ArrayList<>();
        for (int i = 0; i < Receptacle.FILTER_SLOTS; i++) {
            targets.add(new Rect2i(leftPos + LogisticsReceptacleMenu.FILTER_X + i * 18, topPos + LogisticsReceptacleMenu.FILTER_Y, 16, 16));
        }
        return targets;
    }

    @Override
    public boolean ghostFluids() {
        return true;
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
        return Component.translatable(receptacle().isConnected() ? "gui.quantimium.logistics.status.on" : "gui.quantimium.logistics.status.off");
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

    private boolean keeping() {
        return receptacle().keeps() && receptacle().role() == LogisticsMember.Role.REQUESTER;
    }

    /** A small # on entries standing for a tag rather than an item; keeping counts, each entry's count. */
    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        for (int i = 0; i < Receptacle.FILTER_SLOTS; i++) {
            ItemStack entry = receptacle().getFilter().getItem(i);
            if (entry.isEmpty()) continue;
            int x = leftPos + LogisticsReceptacleMenu.FILTER_X + i * 18;
            int y = topPos + LogisticsReceptacleMenu.FILTER_Y;
            if (FilterEntry.tagOf(entry).isPresent()) graphics.text(font, "#", x + 11, y - 1, QuantumUiColours.ACCENT, true);
            if (keeping()) {
                String count = HorizonCoreScreen.slotCount(receptacle().keepCount(i)) + (com.kadikular.quantimium.recipe.FluidEntry.is(entry) ? "B" : "");
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + 16, y + 11);
                graphics.pose().scale(0.5f, 0.5f);
                graphics.text(font, count, -font.width(count), 0, 0xFF5BE38A, true);
                graphics.pose().popMatrix();
            }
        }
    }

    /** Keeping counts, the wheel over an entry changes its count: up for more, down for fewer; shift, sixteen. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeOverlay() == null && keeping() && scrollY != 0) {
            for (int i = 0; i < Receptacle.FILTER_SLOTS; i++) {
                int x = leftPos + LogisticsReceptacleMenu.FILTER_X + i * 18;
                int y = topPos + LogisticsReceptacleMenu.FILTER_Y;
                if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) continue;
                boolean shift = minecraft != null && minecraft.hasShiftDown();
                press(LogisticsReceptacleMenu.BUTTON_COUNT + i * 4 + (scrollY < 0 ? 1 : 0) + (shift ? 2 : 0));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Filter entries say what they match and how to change them. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int index = hoveredSlot == null ? -1 : menu.slots.indexOf(hoveredSlot);
        if (index < LogisticsReceptacleMenu.FILTER_START || index >= LogisticsReceptacleMenu.FILTER_END) return lines;
        boolean fluid = com.kadikular.quantimium.recipe.FluidEntry.is(stack);
        if (fluid) lines.add(Component.translatable("gui.quantimium.filter.entry.fluid").withStyle(ChatFormatting.AQUA));
        else lines.add(FilterEntry.tagOf(stack)
                .map(tag -> Component.translatable("gui.quantimium.me_superposition_crafter.entry.tag", "#" + tag.location()).withStyle(ChatFormatting.AQUA))
                .orElse(Component.translatable("gui.quantimium.me_superposition_crafter.entry.item").withStyle(ChatFormatting.AQUA)));
        if (keeping()) {
            lines.add(Component.translatable(fluid ? "gui.quantimium.logistics_receptacle.keep.count_fluid" : "gui.quantimium.logistics_receptacle.keep.count",
                            receptacle().keepCount(index - LogisticsReceptacleMenu.FILTER_START))
                    .withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("gui.quantimium.logistics_receptacle.keep.hint").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.quantimium.filter.entry.hint_fluid").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
