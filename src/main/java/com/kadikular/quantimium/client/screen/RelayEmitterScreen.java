package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.LaserRelayBlockEntity;
import com.kadikular.quantimium.laser.RelayMode;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.menu.RelayEmitterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** One of a Laser Relay's emitters: its lens slot, and how it listens to redstone on a tab. */
public class RelayEmitterScreen extends QuantumMachineScreen<RelayEmitterMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/relay_emitter.png");

    public RelayEmitterScreen(RelayEmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private RelayMode mode() {
        LaserRelayBlockEntity relay = menu.getBlockEntity();
        return relay == null ? RelayMode.IGNORED : relay.modeOf(menu.face());
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(Tab.of(Component::empty, () -> press(RelayEmitterMenu.BUTTON_MODE),
                        () -> List.of(mode().label(), hint("gui.quantimium.tab.cycle")))
                .texture(() -> Identifier.withDefaultNamespace("textures/" + switch (mode()) {
                    case IGNORED -> "item/gunpowder";
                    case WITH_SIGNAL -> "block/redstone_torch";
                    case WITHOUT_SIGNAL -> "block/redstone_torch_off";
                    case PULSE -> "item/redstone";
                } + ".png"))
                // Four settings: three steps on is one back.
                .onRightClick(() -> {
                    for (int i = 0; i < 3; i++) press(RelayEmitterMenu.BUTTON_MODE);
                })
                .slot(RailSlot.REDSTONE));
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
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
        // Its redstone setting is on its tab.
        return Component.empty();
    }

    /** Whether the lens in it has settings: a filter lens, an Interact lens. */
    private boolean hasSettings() {
        LaserRelayBlockEntity relay = menu.getBlockEntity();
        var item = relay == null ? null : relay.lens(menu.face()).getItem();
        return item instanceof com.kadikular.quantimium.item.FilterLensItem || item instanceof com.kadikular.quantimium.item.InteractLensItem;
    }

    private boolean overSettings(double mouseX, double mouseY) {
        return com.kadikular.quantimium.client.screen.ui.QuantumWidgets.hit(mouseX, mouseY, leftPos + RelayEmitterMenu.LENS_X - 1,
                topPos + RelayEmitterMenu.BUTTON_Y, RelayEmitterMenu.BUTTON_W, RelayEmitterMenu.BUTTON_H);
    }

    /** The lens's settings button under its slot, as on a Lens Mount: three dots, lit when there are settings. */
    @Override
    protected void renderMachineBg(net.minecraft.client.gui.GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos + RelayEmitterMenu.LENS_X - 1;
        int y = topPos + RelayEmitterMenu.BUTTON_Y;
        boolean has = hasSettings();
        boolean over = has && overSettings(mouseX, mouseY);
        if (over) graphics.fill(x, y, x + RelayEmitterMenu.BUTTON_W - 1, y + RelayEmitterMenu.BUTTON_H - 1, 0x30FFFFFF);
        int dot = !has ? 0xFF2A2E4A : over ? 0xFFE4DCFF : 0xFF9A7BFF;
        for (int i = 0; i < 3; i++) graphics.fill(x + 5 + i * 3, y + 4, x + 7 + i * 3, y + 6, dot);
    }

    @Override
    protected void renderMachineTooltips(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!hasSettings() || !overSettings(mouseX, mouseY)) return;
        var lens = menu.getBlockEntity().lens(menu.face());
        Component tip = lens.getItem() instanceof com.kadikular.quantimium.item.InteractLensItem
                ? Component.translatable(com.kadikular.quantimium.item.InteractLensItem.hits(lens)
                        ? "gui.quantimium.lens_mount.interact.hits" : "gui.quantimium.lens_mount.interact.uses")
                : Component.translatable("gui.quantimium.lens_mount.settings");
        graphics.setComponentTooltipForNextFrame(font, List.of(tip), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (activeOverlay() == null && event.button() == 0 && hasSettings() && overSettings(event.x(), event.y())) {
            if (minecraft != null && !(menu.getBlockEntity().lens(menu.face()).getItem() instanceof com.kadikular.quantimium.item.InteractLensItem)) {
                FilterLensScreen.keepCursor(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
            }
            if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            press(RelayEmitterMenu.BUTTON_LENS);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.relay_emitter.lens.tip"));
    }
}
