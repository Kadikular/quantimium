package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.menu.TesseractStabilizerMenu;
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The docked Tesseract in its socket, the link's state in the title strip, and where the link
 * points written underneath. Which faces offer the linked inventory is the side configuration,
 * behind the tab like on every other machine.
 */
public class TesseractStabilizerScreen extends QuantumMachineScreen<TesseractStabilizerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/container/tesseract_stabilizer.png");

    /** Two lines centred under the socket (tools/gui_panels.py: "link text"). */
    private static final int LINK_TEXT_Y = 58;
    private static final int LINE_HEIGHT = 10;
    private static final int LINK_TEXT_WIDTH = 152;

    public TesseractStabilizerScreen(TesseractStabilizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected Identifier panelTexture() {
        return TEXTURE;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.MID;
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
        return 1;
    }

    @Override
    protected Component statusLabel() {
        return switch (menu.getStatusCode()) {
            case TesseractStabilizerBlockEntity.STATUS_LINKED ->
                    Component.translatable("gui.quantimium.tesseract_stabilizer.status.linked");
            case TesseractStabilizerBlockEntity.STATUS_UNLOADED ->
                    Component.translatable("gui.quantimium.tesseract_stabilizer.status.unloaded");
            default -> Component.translatable("gui.quantimium.tesseract_stabilizer.status.empty");
        };
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case TesseractStabilizerBlockEntity.STATUS_LINKED -> PanelTheme.Tone.OK;
            case TesseractStabilizerBlockEntity.STATUS_UNLOADED -> PanelTheme.Tone.BAD;
            default -> PanelTheme.Tone.IDLE;
        };
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(switch (menu.getStatusCode()) {
            case TesseractStabilizerBlockEntity.STATUS_LINKED ->
                    Component.translatable("gui.quantimium.tesseract_stabilizer.status.linked.tip");
            case TesseractStabilizerBlockEntity.STATUS_UNLOADED ->
                    Component.translatable("gui.quantimium.tesseract_stabilizer.status.unloaded.tip");
            default -> Component.translatable("gui.quantimium.tesseract_stabilizer.status.empty.tip");
        });
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(Tab.of("S", this::requestSideConfig,
                        List.of(Component.literal("§6Configure Sides"),
                                Component.literal("§7Set which faces offer the linked inventory.")))
                .icon(new ItemStack(Items.HOPPER)));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        List<Component> lines = linkLines(menu.getSlot(0).getItem());
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            String text = font.plainSubstrByWidth(line.getString(), LINK_TEXT_WIDTH);
            graphics.text(font, text, leftPos + imageWidth / 2 - font.width(text) / 2,
                    topPos + LINK_TEXT_Y + i * LINE_HEIGHT, i == 0 ? textColour() : dimColour(), false);
        }
    }

    /** What the docked Tesseract reaches: the block, then where it is and from which face. */
    private List<Component> linkLines(ItemStack link) {
        List<Component> lines = new ArrayList<>(2);
        if (!EntangledLinks.isBound(link)) {
            lines.add(Component.translatable("gui.quantimium.tesseract_stabilizer.hint"));
            return lines;
        }
        Identifier blockId = link.get(ModDataComponents.BOUND_BLOCK.get());
        lines.add(blockId == null
                ? Component.translatable("gui.quantimium.tesseract_stabilizer.unknown_block")
                : BuiltInRegistries.BLOCK.getValue(blockId).getName());
        GlobalPos bound = link.get(ModDataComponents.BOUND_POS.get());
        Direction side = link.get(ModDataComponents.BOUND_SIDE.get());
        if (bound != null) {
            BlockPos pos = bound.pos();
            boolean elsewhere = minecraft != null && minecraft.level != null
                    && !bound.dimension().equals(minecraft.level.dimension());
            lines.add(Component.translatable(elsewhere
                            ? "gui.quantimium.tesseract_stabilizer.where.dimension"
                            : "gui.quantimium.tesseract_stabilizer.where",
                    pos.getX(), pos.getY(), pos.getZ(),
                    faceName(side), bound.dimension().identifier().getPath()));
        }
        return lines;
    }

    /** Top and bottom rather than up and down, as the face would be described standing at the block. */
    private static String faceName(Direction side) {
        if (side == null) return "?";
        return switch (side) {
            case UP -> "top";
            case DOWN -> "bottom";
            default -> side.getName();
        };
    }

    private void requestSideConfig() {
        ClientPacketDistributor.sendToServer(new RequestSideConfigPayload(menu.getBlockEntity().getBlockPos()));
    }

    public void openSideConfig(OpenSideConfigPayload payload) {
        // The linked inventory's slots are not ours to mask, so the editor shows modes only.
        openOverlay(new SideConfigScreen(payload.pos(), payload.configs(), payload.profile(), false));
    }
}
