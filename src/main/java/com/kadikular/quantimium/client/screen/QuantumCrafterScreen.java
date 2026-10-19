package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.client.screen.ui.GuiIcon;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.menu.QuantumCrafterMenu;
import com.kadikular.quantimium.network.SetCrafterLockPayload;
import com.kadikular.quantimium.network.SetCrafterPagePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class QuantumCrafterScreen extends QuantumMachineScreen<QuantumCrafterMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/quantum_crafter.png");
    private static final Identifier BASIC_TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/basic_quantum_crafter.png");

    /** Tint marking an output cell as a preview rather than a stack that is really there. */
    private static final int GHOST_TINT = 0x4020A0FF;
    /** The Basic crafter's coherence gauge, flat under the catalyst socket (tools/gui_panels.py). */
    private static final int COHERENCE_X = 72;
    private static final int COHERENCE_Y = 60;
    private static final int COHERENCE_WIDTH = 32;
    private static final int COHERENCE_HEIGHT = 4;

    private Button previousPageButton;
    private Button nextPageButton;

    public QuantumCrafterScreen(QuantumCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();

        this.previousPageButton = Button.builder(Component.literal("<"),
                        button -> setOutputPage(menu.getOutputPage() - 1))
                .bounds(leftPos + 115, topPos + 72, 14, 10).build();
        this.nextPageButton = Button.builder(Component.literal(">"),
                        button -> setOutputPage(menu.getOutputPage() + 1))
                .bounds(leftPos + 157, topPos + 72, 14, 10).build();

        addRenderableWidget(previousPageButton);
        addRenderableWidget(nextPageButton);
    }

    @Override
    protected List<Tab> createTabs() {
        if (menu.isBasic()) return List.of();
        return List.of(
                sidesTab(menu.getBlockEntity().getBlockPos()),
                Tab.glyph(() -> menu.isRecipeLocked() ? GuiIcon.LOCKED : GuiIcon.UNLOCKED,
                                () -> ClientPacketDistributor.sendToServer(new SetCrafterLockPayload(
                                        menu.getBlockEntity().getBlockPos(), !menu.isRecipeLocked())),
                                () -> List.of(Component.translatable(menu.isRecipeLocked()
                                                ? "gui.quantimium.quantum_crafter.lock.on" : "gui.quantimium.quantum_crafter.lock.off"),
                                        hint("gui.quantimium.tab.switch")))
                        .on(menu::isRecipeLocked));
    }


    @Override
    protected boolean sideSlotMasks() {
        return true;
    }

    @Override
    protected Identifier panelTexture() {
        return menu.isBasic() ? BASIC_TEXTURE : TEXTURE;
    }

    @Override
    protected PanelTheme theme() {
        return menu.isBasic() ? PanelTheme.LOW : PanelTheme.MID;
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case QuantumCrafterBlockEntity.STATUS_READY -> PanelTheme.Tone.OK;
            case QuantumCrafterBlockEntity.STATUS_IDLE -> PanelTheme.Tone.IDLE;
            case QuantumCrafterBlockEntity.STATUS_NO_RECIPE, QuantumCrafterBlockEntity.STATUS_OUTPUT_FULL,
                 QuantumCrafterBlockEntity.STATUS_REPLENISHING -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
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
                menu.getCurrentPowerUse(), menu.getAveragePowerUse(), false,
                menu.getAnomalySurchargePercent(), menu.getAnomalyBand(),
                menu.getPassiveDrainFePerTick());
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable("gui.quantimium.quantum_crafter.status." + statusKey(menu.getStatusCode()));
    }

    @Override
    protected List<Component> statusTooltip() {
        if (menu.getStatusCode() == QuantumCrafterBlockEntity.STATUS_NEEDS_FLUX) {
            return List.of(BandGate.refusal(menu.getRequiredBand(), menu.getBand(), menu.getHeading()),
                    Component.translatable("gui.quantimium.quantum_crafter.status.flux.tip").withStyle(ChatFormatting.GRAY));
        }
        return List.of(Component.translatable(
                "gui.quantimium.quantum_crafter.status." + statusKey(menu.getStatusCode()) + ".tip"));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        previousPageButton.active = menu.getOutputPage() > 0;
        nextPageButton.active = menu.getOutputPage() + 1 < QuantumCrafterMenu.PAGE_COUNT;

        String page = (menu.getOutputPage() + 1) + "/" + QuantumCrafterMenu.PAGE_COUNT;
        graphics.text(font, page, leftPos + 143 - font.width(page) / 2,
                topPos + 74, QuantumUiColours.TEXT, false);

        if (menu.isBasic()) {
            int capacity = Math.max(1, menu.getCoherenceCapacity());
            int filled = COHERENCE_WIDTH * Math.max(0, menu.getCoherenceReserve()) / capacity;
            if (filled > 0) {
                graphics.fill(leftPos + COHERENCE_X, topPos + COHERENCE_Y,
                        leftPos + COHERENCE_X + filled, topPos + COHERENCE_Y + COHERENCE_HEIGHT,
                        QuantumUiColours.ACCENT);
            }
        }

        // Mark the output cells that are showing a preview rather than a committed stack.
        QuantumCrafterBlockEntity be = menu.getBlockEntity();
        for (int menuSlot = QuantumCrafterBlockEntity.OUTPUT_START;
             menuSlot < QuantumCrafterBlockEntity.OUTPUT_START + QuantumCrafterMenu.OUTPUTS_PER_PAGE; menuSlot++) {
            int absolute = menu.absoluteOutputForMenuSlot(menuSlot);
            Slot slot = menu.slots.get(menuSlot);
            if (!be.getInventory().getStackInSlot(absolute).isEmpty()) continue;
            if (slot.getItem().isEmpty()) continue;
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            graphics.fill(sx, sy, sx + 16, sy + 16, GHOST_TINT);
        }
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!menu.isBasic()
                || !QuantumWidgets.hit(mouseX, mouseY, leftPos + COHERENCE_X,
                topPos + COHERENCE_Y, COHERENCE_WIDTH + 1, COHERENCE_HEIGHT + 1)) {
            return;
        }
        graphics.setComponentTooltipForNextFrame(font, List.of(
                Component.literal("Coherence: " + menu.getCoherenceReserve()
                        + " / " + menu.getCoherenceCapacity()),
                Component.literal("Regenerates steadily over the configured interval")
                        .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        Slot slot = hoveredSlot;
        int menuSlot = slot == null ? -1 : menu.slots.indexOf(slot);
        if (menuSlot < QuantumCrafterBlockEntity.OUTPUT_START
                || menuSlot >= QuantumCrafterBlockEntity.OUTPUT_START + QuantumCrafterMenu.OUTPUTS_PER_PAGE) {
            return lines;
        }

        QuantumCrafterBlockEntity be = menu.getBlockEntity();
        int absolute = menu.absoluteOutputForMenuSlot(menuSlot);
        if (!be.getInventory().getStackInSlot(absolute).isEmpty()) return lines;
        QuantumCrafterBlockEntity.GhostSlot ghost = be.ghostAt(absolute);
        if (ghost == null) return lines;

        lines.add(Component.translatable("gui.quantimium.quantum_crafter.preview").withStyle(ChatFormatting.AQUA));
        int batch = Math.max(1, ghost.batchSize());
        if (batch > 1) {
            lines.add(Component.translatable("gui.quantimium.quantum_crafter.cost.batch", String.format("%,d", ghost.feCost()), batch,
                    String.format("%,d", ghost.feCost() / batch)).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.quantimium.quantum_crafter.cost", String.format("%,d", ghost.feCost())).withStyle(ChatFormatting.GRAY));
        }
        if (menu.isBasic()) {
            lines.add(Component.translatable("gui.quantimium.quantum_crafter.coherence", batch, menu.getCoherenceReserve()).withStyle(ChatFormatting.GRAY));
        }
        if (menu.getAnomalySurchargePercent() > 0) {
            lines.add(Component.translatable("gui.quantimium.energy.anomaly_surcharge",
                            Component.translatable("flux.quantimium.band."
                                    + menu.getAnomalyBand().getSerializedName()),
                            menu.getAnomalySurchargePercent())
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("gui.quantimium.quantum_crafter.can_make", String.format("%,d", ghost.available())).withStyle(ChatFormatting.GRAY));
        if (ghost.feCost() > menu.getEnergyStored()) {
            lines.add(Component.translatable("gui.quantimium.quantum_crafter.no_energy").withStyle(ChatFormatting.RED));
        } else if (ghost.energyCapped()) {
            lines.add(Component.translatable("gui.quantimium.quantum_crafter.energy_capped").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    @Override
    protected void renderMachineForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // A preview's count: how many it can make from what it has, small, as a terminal shows what's craftable.
        QuantumCrafterBlockEntity be = menu.getBlockEntity();
        for (int menuSlot = QuantumCrafterBlockEntity.OUTPUT_START;
             menuSlot < QuantumCrafterBlockEntity.OUTPUT_START + QuantumCrafterMenu.OUTPUTS_PER_PAGE; menuSlot++) {
            int absolute = menu.absoluteOutputForMenuSlot(menuSlot);
            if (!be.getInventory().getStackInSlot(absolute).isEmpty()) continue;
            QuantumCrafterBlockEntity.GhostSlot ghost = be.ghostAt(absolute);
            Slot slot = menu.slots.get(menuSlot);
            if (ghost == null || slot.getItem().isEmpty() || ghost.available() <= 1) continue;
            String count = HorizonCoreScreen.slotCount(ghost.available());
            graphics.nextStratum();
            graphics.pose().pushMatrix();
            graphics.pose().translate(leftPos + slot.x + 16, topPos + slot.y + 11);
            graphics.pose().scale(0.5f, 0.5f);
            graphics.text(font, count, -font.width(count), 0, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }
    }

    /** The place on the page of the previewed output under the mouse, or -1. */
    private int ghostUnder(double mouseX, double mouseY) {
        if (activeOverlay() != null) return -1;
        QuantumCrafterBlockEntity be = menu.getBlockEntity();
        for (int local = 0; local < QuantumCrafterMenu.OUTPUTS_PER_PAGE; local++) {
            Slot slot = menu.slots.get(QuantumCrafterBlockEntity.OUTPUT_START + local);
            if (!isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) continue;
            int absolute = menu.absoluteOutputSlot(local);
            return be.getInventory().getStackInSlot(absolute).isEmpty() && be.ghostAt(absolute) != null ? local : -1;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // On a preview, as in a logistics terminal: click for the batch, right-click for half, shift-right-click for
        // one, onto what's in hand. Shift-click still sends the batch into the inventory.
        int local = ghostUnder(event.x(), event.y());
        if (local >= 0 && (event.button() == 1 || event.button() == 0 && !event.hasShiftDown())) {
            press((event.button() == 0 ? QuantumCrafterMenu.BUTTON_TAKE_BATCH
                    : event.hasShiftDown() ? QuantumCrafterMenu.BUTTON_TAKE_ONE : QuantumCrafterMenu.BUTTON_TAKE_HALF) + local);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Shift and the wheel down over a preview: one more onto the cursor.
        int local = ghostUnder(mouseX, mouseY);
        if (local >= 0 && minecraft != null && minecraft.hasShiftDown() && scrollY != 0) {
            if (scrollY < 0) press(QuantumCrafterMenu.BUTTON_TAKE_ONE + local);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void setOutputPage(int page) {
        int bounded = Math.max(0, Math.min(QuantumCrafterMenu.PAGE_COUNT - 1, page));
        if (bounded == menu.getOutputPage()) return;
        menu.setOutputPage(bounded);
        ClientPacketDistributor.sendToServer(new SetCrafterPagePayload(menu.containerId, bounded));
    }

    private static String statusKey(int code) {
        return switch (code) {
            case QuantumCrafterBlockEntity.STATUS_READY -> "ready";
            case QuantumCrafterBlockEntity.STATUS_NO_POWER -> "power";
            case QuantumCrafterBlockEntity.STATUS_NO_RECIPE -> "recipe";
            case QuantumCrafterBlockEntity.STATUS_DENIED -> "denied";
            case QuantumCrafterBlockEntity.STATUS_OUTPUT_FULL -> "output";
            case QuantumCrafterBlockEntity.STATUS_ENTANGLED_LOOP -> "loop";
            case QuantumCrafterBlockEntity.STATUS_REPLENISHING -> "replenishing";
            case QuantumCrafterBlockEntity.STATUS_NEEDS_FLUX -> "flux";
            default -> "idle";
        };
    }
}
