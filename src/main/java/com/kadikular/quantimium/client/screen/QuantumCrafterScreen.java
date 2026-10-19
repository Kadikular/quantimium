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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Quantum Crafter: its inputs and catalyst, and under them, as in a logistics terminal, a scrolling grid of what's
 * in its outputs and every craft it could make, searchable. Click an output for the batch on the cursor, right-click
 * for half, shift-right-click (or shift and the wheel down) for one, shift-click for the batch into the inventory;
 * while shift is held the grid holds still. Layout: tools/gui_panels.py crafter.
 */
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
    private static final int SEARCH_X = 9;
    private static final int SEARCH_Y = 74;
    private static final int SEARCH_W = 158;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 88;
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int CELL = 18;

    /** One output shown: which output slot (0 to 53), what's there or previewed, and whether it's a preview. */
    private record Cell(int slot, ItemStack stack, boolean preview, long count) {
        /** The same output across updates: the item, and whether it's there or previewed. */
        boolean same(Cell other) {
            return preview == other.preview && ItemStack.isSameItemSameComponents(stack, other.stack);
        }
    }

    private EditBox search;
    private int scrollRow;
    /** The cells as last shown: while shift is held they keep their order, so what's under the mouse stays put. */
    private List<Cell> shown = List.of();
    /** The mouse button whose release belongs to a take handled here, or -1. */
    private int swallowRelease = -1;

    public QuantumCrafterScreen(QuantumCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 232);
    }

    @Override
    protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, 9, Component.translatable("gui.quantimium.horizon_core.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(textColour());
        search.setHint(Component.translatable("gui.quantimium.horizon_core.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> scrollRow = 0);
        addRenderableWidget(search);
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

    // ---- the output grid ----

    /** Every output there or previewed, in slot order, filtered by the search; held in place while shift is down. */
    private List<Cell> cells() {
        QuantumCrafterBlockEntity be = menu.getBlockEntity();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Cell> latest = new ArrayList<>();
        for (int slot = 0; slot < QuantumCrafterBlockEntity.OUTPUT_SLOTS; slot++) {
            int absolute = QuantumCrafterBlockEntity.OUTPUT_START + slot;
            ItemStack real = be.getInventory().getStackInSlot(absolute);
            Cell cell;
            if (!real.isEmpty()) {
                cell = new Cell(slot, real, false, real.getCount());
            } else {
                QuantumCrafterBlockEntity.GhostSlot ghost = be.ghostAt(absolute);
                ItemStack preview = be.getGhost(absolute);
                if (ghost == null || preview.isEmpty()) continue;
                cell = new Cell(slot, preview, true, Math.max(preview.getCount(), ghost.available()));
            }
            if (query.isEmpty() || cell.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) latest.add(cell);
        }
        if (minecraft == null || !minecraft.hasShiftDown()) {
            shown = latest;
            return latest;
        }
        // Shift held: the order stands still; counts change, and what's run out drops away.
        List<Cell> kept = new ArrayList<>();
        List<Cell> left = new ArrayList<>(latest);
        for (Cell was : shown) {
            for (int i = 0; i < left.size(); i++) {
                if (!left.get(i).same(was)) continue;
                kept.add(left.remove(i));
                break;
            }
        }
        kept.addAll(left);
        shown = kept;
        return kept;
    }

    private int maxScroll(List<Cell> cells) {
        return Math.max(0, Mth.positiveCeilDiv(cells.size(), COLUMNS) - ROWS);
    }

    private boolean onGrid(double mouseX, double mouseY) {
        return activeOverlay() == null && QuantumWidgets.hit(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, COLUMNS * CELL, ROWS * CELL);
    }

    @Nullable
    private Cell cellAt(List<Cell> cells, double mouseX, double mouseY) {
        if (!onGrid(mouseX, mouseY)) return null;
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) return null;
        int index = (row + scrollRow) * COLUMNS + col;
        return index < cells.size() ? cells.get(index) : null;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.isBasic()) {
            int capacity = Math.max(1, menu.getCoherenceCapacity());
            int filled = COHERENCE_WIDTH * Math.max(0, menu.getCoherenceReserve()) / capacity;
            if (filled > 0) {
                graphics.fill(leftPos + COHERENCE_X, topPos + COHERENCE_Y,
                        leftPos + COHERENCE_X + filled, topPos + COHERENCE_Y + COHERENCE_HEIGHT,
                        QuantumUiColours.ACCENT);
            }
        }

        List<Cell> cells = cells();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(cells));
        Cell hovered = cellAt(cells, mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= cells.size()) break;
                Cell cell = cells.get(index);
                int x = leftPos + GRID_X + col * CELL;
                int y = topPos + GRID_Y + row * CELL;
                if (cell == hovered) graphics.fill(x, y, x + CELL - 2, y + CELL - 2, QuantumUiColours.ROW_HOVER);
                // A preview is marked apart from what's really there; its count is how many it can make in all.
                if (cell.preview()) graphics.fill(x, y, x + 16, y + 16, GHOST_TINT);
                graphics.item(cell.stack().copyWithCount(1), x, y);
                if (cell.count() > 1) {
                    String count = HorizonCoreScreen.slotCount(cell.count());
                    graphics.nextStratum();
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(x + 16, y + 11);
                    graphics.pose().scale(0.5f, 0.5f);
                    graphics.text(font, count, -font.width(count), 0, cell.preview() ? 0xFFB49CFF : 0xFFFFFFFF, true);
                    graphics.pose().popMatrix();
                }
            }
        }
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (menu.isBasic() && QuantumWidgets.hit(mouseX, mouseY, leftPos + COHERENCE_X,
                topPos + COHERENCE_Y, COHERENCE_WIDTH + 1, COHERENCE_HEIGHT + 1)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal("Coherence: " + menu.getCoherenceReserve()
                            + " / " + menu.getCoherenceCapacity()),
                    Component.literal("Regenerates steadily over the configured interval")
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (!menu.getCarried().isEmpty()) return;
        Cell cell = cellAt(shown, mouseX, mouseY);
        if (cell == null) return;
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(cell.stack()));
        if (cell.preview()) lines.addAll(previewLines(QuantumCrafterBlockEntity.OUTPUT_START + cell.slot()));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** What a preview costs and makes, under its name. */
    private List<Component> previewLines(int absolute) {
        List<Component> lines = new ArrayList<>();
        QuantumCrafterBlockEntity.GhostSlot ghost = menu.getBlockEntity().ghostAt(absolute);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (onGrid(event.x(), event.y()) && (event.button() == 0 || event.button() == 1)) {
            Cell cell = cellAt(shown, event.x(), event.y());
            if (cell != null) {
                int mode = event.button() == 0 ? (event.hasShiftDown() ? QuantumCrafterMenu.TO_INVENTORY : QuantumCrafterMenu.BATCH)
                        : event.hasShiftDown() ? QuantumCrafterMenu.ONE : QuantumCrafterMenu.HALF;
                press(mode * QuantumCrafterMenu.TAKE + cell.slot());
            }
            // The release that follows isn't a click on a slot under the grid's cells.
            swallowRelease = event.button();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == swallowRelease) {
            swallowRelease = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (onGrid(mouseX, mouseY) && scrollY != 0) {
            if (minecraft != null && minecraft.hasShiftDown()) {
                // Shift and the wheel down: one more onto the cursor.
                Cell cell = cellAt(shown, mouseX, mouseY);
                if (scrollY < 0 && cell != null) press(QuantumCrafterMenu.ONE * QuantumCrafterMenu.TAKE + cell.slot());
            } else {
                scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll(shown));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeOverlay() == null && search != null && search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(event) || search.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
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
