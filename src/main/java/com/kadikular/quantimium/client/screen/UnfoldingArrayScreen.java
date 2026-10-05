package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.UnfoldingArrayMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The Unfolding Array: what it needs (ghosts in the empty reagent slots, with how many), how many
 * Sophons you have against the soul's limit, and the button that starts it. The progress runs along
 * the bottom. Layout: tools/gui_panels.py unfolding_array.
 */
public class UnfoldingArrayScreen extends QuantumMachineScreen<UnfoldingArrayMenu> {

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/unfolding_array.png");
    private static final int INFO_X = 50;
    private static final int INFO_Y = 19;
    private static final int BUTTON_X = 50;
    private static final int BUTTON_Y = 51;
    private static final int BUTTON_W = 68;
    private static final int BUTTON_H = 14;
    private static final int GAUGE_X = 48;
    private static final int GAUGE_Y = 70;
    private static final int GAUGE_W = 112;
    private static final int GAUGE_H = 4;
    private static final int AZURE = 0xFF3485FF;
    private static final int AZURE_CORE = 0xFF7FB2FF;
    private static final int VIOLET = 0xFFB196FF;

    public UnfoldingArrayScreen(UnfoldingArrayMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected Identifier panelTexture() {
        return PANEL;
    }

    @Override
    protected PanelTheme theme() {
        return PanelTheme.HIGH;
    }

    @Override
    protected int energyStored() {
        return menu.getEnergyStored();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.getMaxEnergyStored();
    }

    @Override
    protected List<Component> energyTooltip() {
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(),
                UnfoldingArrayBlockEntity.FE_PER_TICK, -1, false);
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        // Ghosts of what each reagent slot wants, with how many, while it is short.
        ghost(graphics, 0, new ItemStack(ModItems.SEMI_STABLE_TESSERACT.get()), UnfoldingArrayBlockEntity.TESSERACTS);
        ghost(graphics, 1, new ItemStack(ModItems.ANOMALY_FRAGMENT.get()), UnfoldingArrayBlockEntity.FRAGMENTS);
        ghost(graphics, 2, new ItemStack(ModItems.RIFT_RESIDUE.get()), UnfoldingArrayBlockEntity.RESIDUE);

        graphics.text(font, Component.translatable("gui.quantimium.unfolding_array.sophons"),
                leftPos + INFO_X, topPos + INFO_Y, textColour(), false);
        for (int i = 0; i < menu.getMaxSophons(); i++) {
            int x = leftPos + INFO_X + i * 8;
            int y = topPos + INFO_Y + 12;
            if (i < menu.getSophons()) {
                graphics.fill(x, y, x + 6, y + 6, AZURE_CORE);
            } else {
                graphics.fill(x, y, x + 6, y + 1, dimColour());
                graphics.fill(x, y + 5, x + 6, y + 6, dimColour());
                graphics.fill(x, y, x + 1, y + 6, dimColour());
                graphics.fill(x + 5, y, x + 6, y + 6, dimColour());
            }
        }

        boolean hovered = within(mouseX, mouseY);
        boolean unfolding = menu.isUnfolding();
        int fill = hovered ? AZURE : unfolding ? 0xAA5A2FA8 : 0xAA1B4FB8;
        graphics.fill(leftPos + BUTTON_X, topPos + BUTTON_Y, leftPos + BUTTON_X + BUTTON_W, topPos + BUTTON_Y + BUTTON_H, fill);
        graphics.centeredText(font, Component.translatable(unfolding ? "gui.quantimium.unfolding_array.stop"
                : "gui.quantimium.unfolding_array.unfold"), leftPos + BUTTON_X + BUTTON_W / 2, topPos + BUTTON_Y + 3, 0xFFFFFFFF);

        int filled = GAUGE_W * menu.getProgress() / UnfoldingArrayBlockEntity.UNFOLD_TICKS;
        if (filled > 0) {
            graphics.fill(leftPos + GAUGE_X, topPos + GAUGE_Y, leftPos + GAUGE_X + filled, topPos + GAUGE_Y + GAUGE_H,
                    menu.getStatus() == UnfoldingArrayBlockEntity.STATUS_STEP_ON ? VIOLET : AZURE_CORE);
        }
    }

    private void ghost(GuiGraphicsExtractor graphics, int slot, ItemStack stack, int count) {
        ItemStack held = menu.getBlockEntity().getInventory().getStackInSlot(slot);
        int x = leftPos + UnfoldingArrayMenu.REAGENT_X;
        int y = topPos + UnfoldingArrayMenu.REAGENT_Y + slot * 18;
        if (held.isEmpty()) {
            graphics.fakeItem(stack, x, y);
            // Items draw well in front of the panel, so the veil has to sit in front of them to dim them.
            graphics.nextStratum();
            graphics.fill(x, y, x + 16, y + 16, 0xA0121424);
        }
        if (held.getCount() < count) {
            String need = String.valueOf(count);
            graphics.nextStratum();
            graphics.text(font, need, x - 2 - font.width(need), y + 5, dimColour(), false);
        }
    }

    private boolean within(double mouseX, double mouseY) {
        return mouseX >= leftPos + BUTTON_X && mouseX < leftPos + BUTTON_X + BUTTON_W
                && mouseY >= topPos + BUTTON_Y && mouseY < topPos + BUTTON_Y + BUTTON_H;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && activeOverlay() == null && within(mouseX, mouseY) && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, UnfoldingArrayBlockEntity.BUTTON_UNFOLD);
            playClick();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void playClick() {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private String statusKey() {
        return switch (menu.getStatus()) {
            case UnfoldingArrayBlockEntity.STATUS_UNFOLDING -> "unfolding";
            case UnfoldingArrayBlockEntity.STATUS_STEP_ON -> "step_on";
            case UnfoldingArrayBlockEntity.STATUS_NO_PYLONS -> "pylons";
            case UnfoldingArrayBlockEntity.STATUS_MISSING -> "missing";
            case UnfoldingArrayBlockEntity.STATUS_NO_POWER -> "power";
            case UnfoldingArrayBlockEntity.STATUS_AT_CAP -> "cap";
            case UnfoldingArrayBlockEntity.STATUS_OUTPUT_FULL -> "output";
            default -> "idle";
        };
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable("gui.quantimium.unfolding_array.status." + statusKey());
    }

    @Override
    protected List<Component> statusTooltip() {
        return List.of(Component.translatable("gui.quantimium.unfolding_array.status." + statusKey() + ".tip"));
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatus()) {
            case UnfoldingArrayBlockEntity.STATUS_UNFOLDING -> PanelTheme.Tone.OK;
            case UnfoldingArrayBlockEntity.STATUS_IDLE, UnfoldingArrayBlockEntity.STATUS_STEP_ON -> PanelTheme.Tone.IDLE;
            default -> PanelTheme.Tone.BAD;
        };
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (within(mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(menu.isUnfolding()
                    ? "gui.quantimium.unfolding_array.stop.tip" : "gui.quantimium.unfolding_array.unfold.tip")), mouseX, mouseY);
        }
    }
}
