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
        return mode().label();
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.relay_emitter.lens.tip"));
    }
}
