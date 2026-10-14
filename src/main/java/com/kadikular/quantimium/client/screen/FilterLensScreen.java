package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
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

import java.util.ArrayList;
import java.util.List;

/** A filter lens's settings: an Item or Block lens's entries and allow or deny, or an Entity lens's kinds and spares. */
public class FilterLensScreen extends QuantumMachineScreen<FilterLensMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/laser_port_filter.png");

    /** Where the pointer was when a Lens Mount's button opened this, to put it back: or NaN. */
    private static double keptX = Double.NaN;
    private static double keptY = Double.NaN;

    public FilterLensScreen(FilterLensMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** Opening from another screen recentres the pointer; this one puts it back where it was. */
    public static void keepCursor(double x, double y) {
        keptX = x;
        keptY = y;
    }

    @Override
    protected void init() {
        super.init();
        restoreCursor(minecraft);
    }

    /** Puts the pointer back where {@link #keepCursor} kept it, if it did: for any lens's settings screen. */
    static void restoreCursor(@org.jetbrains.annotations.Nullable net.minecraft.client.Minecraft minecraft) {
        if (!Double.isNaN(keptX) && minecraft != null) {
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), keptX, keptY);
            keptX = Double.NaN;
            keptY = Double.NaN;
        }
    }

    private boolean entities() {
        return menu.part() == LensFilter.ENTITIES;
    }

    @Override
    protected List<Tab> createTabs() {
        Tab allow = Tab.glyph(() -> menu.filter().allows(menu.part()) ? GuiIcon.ALLOW : GuiIcon.DENY, () -> press(FilterLensMenu.BUTTON_ALLOW),
                () -> List.of(Component.translatable(menu.filter().allows(menu.part()) ? "gui.quantimium.filter.allow" : "gui.quantimium.filter.deny"),
                        hint("gui.quantimium.tab.switch")));
        if (entities()) {
            return List.of(
                    allow,
                    kind(LensFilter.HOSTILE, GuiIcon.HOSTILE, "hostile"),
                    kind(LensFilter.ANIMALS, GuiIcon.ANIMAL, "animals"),
                    kind(LensFilter.PLAYERS, GuiIcon.PLAYER, "players"),
                    Tab.glyph(() -> GuiIcon.BABY, () -> press(FilterLensMenu.BUTTON_YOUNG),
                                    () -> List.of(Component.translatable(menu.filter().spareYoung() ? "gui.quantimium.filter_lens.young.on" : "gui.quantimium.filter_lens.young.off"),
                                            hint("gui.quantimium.tab.switch")))
                            .on(() -> menu.filter().spareYoung()),
                    // How many it spares, on the tab: click or scroll up for more, right-click or scroll down for fewer; shift, ten.
                    Tab.of(() -> Component.literal(Integer.toString(menu.filter().keep())), () -> keep(true),
                                    () -> List.of(Component.translatable("gui.quantimium.filter_lens.keep", menu.filter().keep()),
                                            hint("gui.quantimium.filter_lens.keep.hint")))
                            .onRightClick(() -> keep(false))
                            .onScroll(step -> keep(step > 0)));
        }
        return List.of(
                allow,
                Tab.glyph(() -> GuiIcon.CROP, () -> press(FilterLensMenu.BUTTON_RIPE),
                                () -> List.of(Component.translatable(menu.filter().ripe() ? "gui.quantimium.filter_lens.ripe.on" : "gui.quantimium.filter_lens.ripe.off"),
                                        hint("gui.quantimium.tab.switch")))
                        .on(() -> menu.filter().ripe())
                        .visible(() -> menu.part() == LensFilter.BLOCKS));
    }

    private void keep(boolean more) {
        boolean ten = minecraft != null && minecraft.hasShiftDown();
        press(more ? (ten ? FilterLensMenu.BUTTON_KEEP_MORE_TEN : FilterLensMenu.BUTTON_KEEP_MORE)
                : (ten ? FilterLensMenu.BUTTON_KEEP_FEWER_TEN : FilterLensMenu.BUTTON_KEEP_FEWER));
    }

    /** A kind of living thing it may hit: lit while it's among them. None lit, any. */
    private Tab kind(int kind, GuiIcon icon, String key) {
        return Tab.glyph(() -> icon, () -> press(FilterLensMenu.BUTTON_KIND + kind),
                        () -> List.of(Component.translatable("gui.quantimium.filter_lens.kind." + key)
                                        .append(": ").append(Component.translatable((menu.filter().kinds() & kind) != 0 ? "gui.quantimium.on" : "gui.quantimium.off")),
                                hint(menu.filter().kinds() == 0 ? "gui.quantimium.filter_lens.kinds.none" : "gui.quantimium.tab.switch")))
                .on(() -> (menu.filter().kinds() & kind) != 0);
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
        // Its tabs say what it's set to.
        return Component.empty();
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("item.quantimium.filter_lens.tip"));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {

    }

    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        for (int i = 0; i < LensFilter.SLOTS; i++) {
            ItemStack entry = menu.filter().entry(menu.part(), i);
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
