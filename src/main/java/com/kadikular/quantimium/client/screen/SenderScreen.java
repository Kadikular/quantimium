package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.menu.SenderMenu;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/** A Sender: what it sends down the beam, in a 3x3 grid, and its faces on a tab. */
public class SenderScreen extends QuantumMachineScreen<SenderMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/collector.png");

    public SenderScreen(SenderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(Tab.of("S", () -> ClientPacketDistributor.sendToServer(new RequestSideConfigPayload(menu.getBlockEntity().getBlockPos())),
                        List.of(Component.translatable("gui.quantimium.item_relay.sides"), Component.translatable("gui.quantimium.sender.sides.tip")))
                .icon(new ItemStack(Items.HOPPER)));
    }

    public void openSideConfig(OpenSideConfigPayload payload) {
        openOverlay(new SideConfigScreen(payload.pos(), payload.configs(), payload.profile(), false));
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
        return Component.translatable("gui.quantimium.sender.status");
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.sender.status.tip"));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {}
}
