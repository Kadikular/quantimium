package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.PriorityPage;
import com.kadikular.quantimium.item.RedstoneLensItem;
import com.kadikular.quantimium.laser.RedstoneSettings;
import com.kadikular.quantimium.menu.RedstoneLensMenu;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * A Redstone Lens's settings: on the rail its mode (click to cycle), its signal's strength (click, right-click or
 * scroll) and for a count how many (its page, as a priority's); the item it counts in the panel's one slot.
 */
public class RedstoneLensScreen extends QuantumMachineScreen<RedstoneLensMenu> implements GhostFilterScreen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/redstone_lens.png");
    private static final Identifier ALWAYS = Identifier.withDefaultNamespace("textures/block/redstone_torch.png");
    private static final Identifier FORWARD = Identifier.withDefaultNamespace("textures/item/repeater.png");

    public RedstoneLensScreen(RedstoneLensMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        FilterLensScreen.restoreCursor(minecraft);
    }

    private RedstoneSettings settings() {
        return menu.settings();
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(
                // The mode: < and ≥ drawn from the shared icons, always on a lit torch, sending on a repeater.
                Tab.glyph(() -> switch (settings().mode()) {
                                    case BELOW -> GuiIcon.LESS;
                                    case AT_LEAST -> GuiIcon.AT_LEAST;
                                    default -> null;
                                }, () -> press(RedstoneLensMenu.BUTTON_MODE),
                                () -> List.of(Component.translatable("gui.quantimium.redstone_lens.mode." + settings().mode().getSerializedName()),
                                        hint("gui.quantimium.tab.cycle")))
                        .texture(() -> settings().mode() == RedstoneSettings.Mode.FORWARD ? FORWARD : ALWAYS)
                        .onRightClick(() -> {
                            for (int i = 0; i < RedstoneSettings.Mode.values().length - 1; i++) press(RedstoneLensMenu.BUTTON_MODE);
                        }),
                // Its strength, on the tab: click or scroll up for more, right-click or scroll down for less.
                Tab.of(() -> Component.literal(Integer.toString(settings().level())), () -> press(RedstoneLensMenu.BUTTON_STRONGER),
                                () -> List.of(Component.translatable("gui.quantimium.redstone_lens.level", settings().level()),
                                        hint("gui.quantimium.redstone_lens.level.hint")))
                        .onRightClick(() -> press(RedstoneLensMenu.BUTTON_WEAKER))
                        .onScroll(step -> press(step > 0 ? RedstoneLensMenu.BUTTON_STRONGER : RedstoneLensMenu.BUTTON_WEAKER))
                        .visible(() -> settings().mode() != RedstoneSettings.Mode.FORWARD),
                Tab.glyph(() -> GuiIcon.COUNT, () -> openOverlay(new PriorityPage(this::panelArea, rail(),
                                        Component.translatable("gui.quantimium.redstone_lens.count.title"),
                                        Component.translatable("gui.quantimium.redstone_lens.count.hint"),
                                        () -> settings().count(), this::setCount, 0, RedstoneSettings.MAX_COUNT)),
                                () -> List.of(Component.translatable("gui.quantimium.redstone_lens.count", settings().count())))
                        .page(RailSlot.PRIORITY, () -> activeOverlay() instanceof PriorityPage, this::closeOverlay)
                        .onScroll(step -> setCount(settings().count() + step * (minecraft != null && minecraft.hasShiftDown() ? 10 : 1)))
                        .visible(() -> settings().mode().counts()));
    }

    private void setCount(int count) {
        press(RedstoneLensMenu.BUTTON_COUNT + Math.clamp(count, 0, RedstoneSettings.MAX_COUNT));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public List<Rect2i> ghostTargets() {
        if (!settings().mode().counts()) return List.of();
        return List.of(new Rect2i(leftPos + RedstoneLensMenu.ITEM_X, topPos + RedstoneLensMenu.ITEM_Y, 16, 16));
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

    /** What it's set to, on the title strip. */
    @Override
    protected Component statusLabel() {
        return RedstoneLensItem.describe(settings());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(RedstoneLensItem.describe(settings()));
    }
}
