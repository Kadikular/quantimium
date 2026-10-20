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
 * in its outputs and every craft it could make, searchable. Click an output for a stack on the cursor (what's there
 * first, the rest made), right-click for half, shift-right-click (or shift and the wheel down) for one, shift-click
 * for a stack into the inventory;
 * while shift is held the grid holds still. Layout: tools/gui_panels.py crafter.
 */
public class QuantumCrafterScreen extends QuantumMachineScreen<QuantumCrafterMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/quantum_crafter.png");
    private static final Identifier BASIC_TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/basic_quantum_crafter.png");

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
    /** The scroll bar's track, in the gap right of the grid (tools/gui_panels.py crafter). */
    private final com.kadikular.quantimium.client.screen.ui.ScrollBar bar =
            new com.kadikular.quantimium.client.screen.ui.ScrollBar(GRID_X + COLUMNS * CELL + 1, GRID_Y, 3, ROWS * CELL - 2);

    /**
     * One item shown: where it's shown (an output slot, 0 to 53, or a preview, from GHOST_BASE), how many are in its
     * outputs, how many more it can make, and the preview that can make the most of it (-1 for none).
     */
    private record Cell(int slot, ItemStack stack, long stored, long craftable, int previewSlot) {
        long count() {
            return stored + craftable;
        }

        /** The same item across updates. */
        boolean same(Cell other) {
            return ItemStack.isSameItemSameComponents(stack, other.stack);
        }
    }

    /** Sorted by name, or by count (how many are there, or can be made); remembered while the game runs. */
    private static boolean byName;
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
        // Sort, as a logistics terminal's: by count or by name.
        Tab sort = Tab.glyph(() -> byName ? GuiIcon.SORT_NAME : GuiIcon.SORT_COUNT, () -> {
                            byName = !byName;
                            scrollRow = 0;
                        },
                        () -> List.of(Component.translatable(byName ? "gui.quantimium.logistics_terminal.sort.name" : "gui.quantimium.logistics_terminal.sort.count"),
                                hint("gui.quantimium.tab.switch")));
        if (menu.isBasic()) return List.of(sort);
        return List.of(
                sort,
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

    /** Every output there or previewed, sorted and filtered by the search; held in place while shift is down. */
    private List<Cell> cells() {
        QuantumCrafterBlockEntity be = menu.getBlockEntity();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        // As a terminal does: what's there and what can be made of one item in one cell. What's there is in its
        // output slots; what it can make, in its previews, as many as there are.
        List<Cell> latest = new ArrayList<>();
        int previews = be.ghostCount();
        for (int i = 0; i < QuantumCrafterBlockEntity.OUTPUT_SLOTS + previews; i++) {
            boolean real = i < QuantumCrafterBlockEntity.OUTPUT_SLOTS;
            int slot = real ? i : QuantumCrafterBlockEntity.GHOST_BASE + i - QuantumCrafterBlockEntity.OUTPUT_SLOTS;
            ItemStack stack;
            long stored = 0, craftable = 0;
            int previewSlot = -1;
            if (real) {
                stack = be.getInventory().getStackInSlot(QuantumCrafterBlockEntity.OUTPUT_START + slot);
                if (stack.isEmpty()) continue;
                stored = stack.getCount();
            } else {
                QuantumCrafterBlockEntity.GhostSlot ghost = be.ghostAt(slot);
                stack = be.getGhost(slot);
                if (ghost == null || stack.isEmpty()) continue;
                craftable = Math.max(stack.getCount(), ghost.available());
                previewSlot = slot;
            }
            int at = -1;
            for (int j = 0; j < latest.size(); j++) if (ItemStack.isSameItemSameComponents(latest.get(j).stack(), stack)) at = j;
            if (at < 0) {
                if (query.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    latest.add(new Cell(slot, stack, stored, craftable, previewSlot));
                }
                continue;
            }
            // Several previews of one item (two recipes for it): the one that can make the most speaks for it.
            Cell was = latest.get(at);
            boolean better = craftable > was.craftable();
            latest.set(at, new Cell(was.slot(), was.stack(), was.stored() + stored, Math.max(was.craftable(), craftable),
                    better ? previewSlot : was.previewSlot()));
        }
        java.util.Comparator<Cell> name = java.util.Comparator.comparing(c -> c.stack().getHoverName().getString());
        latest.sort(byName ? name : java.util.Comparator.comparingLong(Cell::count).reversed().thenComparing(name));
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
    @Nullable
    public Shown shownAt(double mouseX, double mouseY) {
        Cell cell = cellAt(shown, mouseX, mouseY);
        if (cell == null) return null;
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        return new Shown(cell.stack().copyWithCount(1), new net.minecraft.client.renderer.Rect2i(leftPos + GRID_X + col * CELL, topPos + GRID_Y + row * CELL, 16, 16));
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
        bar.render(graphics, leftPos, topPos, scrollRow, ROWS, Mth.positiveCeilDiv(cells.size(), COLUMNS), scrollColour());
        Cell hovered = cellAt(cells, mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= cells.size()) break;
                Cell cell = cells.get(index);
                int x = leftPos + GRID_X + col * CELL;
                int y = topPos + GRID_Y + row * CELL;
                if (cell == hovered) graphics.fill(x, y, x + CELL - 2, y + CELL - 2, QuantumUiColours.ROW_HOVER);
                graphics.item(cell.stack().copyWithCount(1), x, y);
                // What's in its outputs at the top right; under it, in violet, that and what it can make besides.
                boolean makes = cell.craftable() > 0;
                if (makes && cell.stored() > 0) smallCount(graphics, HorizonCoreScreen.slotCount(cell.stored()), x, y, 0, 0xFFFFFFFF);
                if (makes || cell.stored() > 1) {
                    smallCount(graphics, HorizonCoreScreen.slotCount(cell.count()), x, y, 11, makes ? 0xFFB49CFF : 0xFFFFFFFF);
                }
            }
        }
    }

    /** A small number against a cell's right edge, {@code dy} down from its top. */
    private void smallCount(GuiGraphicsExtractor graphics, String count, int x, int y, int dy, int colour) {
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 16, y + dy);
        graphics.pose().scale(0.5f, 0.5f);
        graphics.text(font, count, -font.width(count), 0, colour, true);
        graphics.pose().popMatrix();
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
        if (cell.stored() > 0) {
            lines.add(Component.translatable("gui.quantimium.quantum_crafter.stored", String.format(Locale.ROOT, "%,d", cell.stored()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (cell.previewSlot() >= 0) lines.addAll(previewLines(cell.previewSlot()));
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
        if (activeOverlay() == null && event.button() == 0 && bar.over(event.x(), event.y(), leftPos, topPos)) {
            scrollRow = bar.press(event.y(), topPos, maxScroll(shown));
            return true;
        }
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (bar.dragging()) {
            scrollRow = bar.scrollAt(event.y(), topPos, maxScroll(shown));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (bar.dragging()) {
            bar.release();
            return true;
        }
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
