package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.KeyEvent;
import com.kadikular.quantimium.client.screen.ui.ItemSearch;
import com.kadikular.quantimium.network.MaterialiserPutPayload;
import com.kadikular.quantimium.network.MaterialiserTakePayload;
import net.minecraft.client.input.MouseButtonEvent;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.MaterialiserBlockEntity;
import com.kadikular.quantimium.client.screen.ui.PanelTheme;
import com.kadikular.quantimium.client.screen.ui.QuantumUiColours;
import com.kadikular.quantimium.client.screen.ui.QuantumWidgets;
import com.kadikular.quantimium.flux.BandGate;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.menu.MaterialiserMenu;
import com.kadikular.quantimium.network.MaterialiserOptionsPayload;
import com.kadikular.quantimium.network.SetMaterialiserTargetPayload;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.kadikular.quantimium.unrealised.Materialising;
import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.unrealised.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The Materialiser: Matter and Anomalite Dust on the left; a grid of every form of every ore the Matter could become,
 * searched as JEI searches, taken from as at a Logistics Terminal. Each form shows what waits in its buffer (top) and
 * how many more it could make now (bottom, purple). Click one for a stack, right-click for half, shift-right-click for
 * one, shift-click for a stack into the inventory; shift and the wheel take one (down) or put one back (up); click the
 * grid holding something it makes to put it back. Middle-click a form to export it: the one pipes and hoppers see, and
 * its pushing faces push out (gold). Those the band can't make yet are dimmed. Layout: tools/gui_panels.py materialiser.
 */
public class MaterialiserScreen extends QuantumMachineScreen<MaterialiserMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/materialiser.png");

    private static final int SEARCH_X = 48;
    private static final int SEARCH_Y = 18;
    private static final int SEARCH_WIDTH = 108;
    private static final int GRID_X = 48;
    private static final int GRID_Y = 32;
    private static final int COLUMNS = 6;
    private static final int ROWS = 4;
    private static final int CELL = 18;
    private static final int EXPORT = 0xFFE8B030;
    private final com.kadikular.quantimium.client.screen.ui.ScrollBar bar =
            new com.kadikular.quantimium.client.screen.ui.ScrollBar(GRID_X + COLUMNS * CELL + 1, GRID_Y, 3, ROWS * CELL - 2);

    /** The last options the server sent, for whichever Materialiser is open, with each one's buffer and what it could make. */
    private static List<Materialising.Option> options = List.of();
    private static java.util.Map<Materialising.Option, long[]> counts = java.util.Map.of();
    @Nullable
    private static Materialising.Target target;
    /** The Matter it's reading: what a Tesseract in its Matter slot links to. */
    private static ItemStack reading = ItemStack.EMPTY;

    private EditBox search;
    private int scrollRow;

    public MaterialiserScreen(MaterialiserMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 192);
        options = List.of();
        counts = java.util.Map.of();
        target = null;
    }

    /** From the server: what the open Materialiser could make, how many, and what it exports. */
    public static void setOptions(MaterialiserOptionsPayload payload) {
        options = payload.options();
        java.util.Map<Materialising.Option, long[]> byOption = new java.util.IdentityHashMap<>();
        for (int i = 0; i < options.size(); i++) {
            long held = i < payload.buffered().size() ? payload.buffered().get(i) : 0;
            long more = i < payload.makeable().size() ? payload.makeable().get(i) : 0;
            byOption.put(options.get(i), new long[] {held, more});
        }
        counts = byOption;
        target = payload.target().orElse(null);
        reading = payload.reading();
    }

    private static long buffered(Materialising.Option option) {
        long[] both = counts.get(option);
        return both == null ? 0 : both[0];
    }

    private static long makeable(Materialising.Option option) {
        long[] both = counts.get(option);
        return both == null ? 0 : both[1];
    }

    @Override
    protected void init() {
        super.init();
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_WIDTH, 9,
                Component.translatable("gui.quantimium.materialiser.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(textColour());
        search.setHint(Component.translatable("gui.quantimium.materialiser.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(value -> scrollRow = 0);
        addRenderableWidget(search);
    }

    /** The options the search leaves (as JEI reads a search, or the ore's name), those it can give first. */
    private List<Materialising.Option> shown() {
        String query = search == null ? "" : search.getValue().trim();
        java.util.function.Predicate<ItemStack> matches = ItemSearch.compile(query);
        List<Materialising.Option> matching = new ArrayList<>();
        for (Materialising.Option option : options) {
            if (query.isEmpty() || matches.test(option.display()) || option.ore().getPath().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                matching.add(option);
            }
        }
        matching.sort(java.util.Comparator.comparing((Materialising.Option option) -> !option.allowed())
                .thenComparing(option -> buffered(option) + makeable(option) <= 0));
        return matching;
    }

    private static boolean exported(Materialising.Option option) {
        return target != null && target.is(option.ore(), option.display());
    }

    private static Materialising.Target formOf(Materialising.Option option) {
        return new Materialising.Target(option.ore(), BuiltInRegistries.ITEM.getKey(option.display().getItem()));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Typing in the search box shouldn't close the screen on the inventory key.
        if (activeOverlay() == null && search != null && search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(event) || search.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(sidesTab(menu.getBlockEntity().getBlockPos()));
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
    protected int energyStored() {
        return menu.getEnergyStored();
    }

    @Override
    protected int maxEnergyStored() {
        return menu.getMaxEnergyStored();
    }

    @Override
    protected List<Component> energyTooltip() {
        return EnergyBarTooltip.lines(menu.getEnergyStored(), menu.getMaxEnergyStored(), -1,
                menu.getAverageFePerTick(), false, menu.getSurchargePercent(), null);
    }

    private int maxScroll(List<Materialising.Option> shown) {
        return Math.max(0, Mth.positiveCeilDiv(shown.size(), COLUMNS) - ROWS);
    }

    private boolean onGrid(double mouseX, double mouseY) {
        return QuantumWidgets.hit(mouseX, mouseY, leftPos + GRID_X, topPos + GRID_Y, COLUMNS * CELL, ROWS * CELL);
    }

    /** The shown option under ({@code mouseX}, {@code mouseY}), or null. */
    @Nullable
    private Materialising.Option optionAt(List<Materialising.Option> shown, double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) return null;
        int index = (row + scrollRow) * COLUMNS + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    @Nullable
    public Shown shownAt(double mouseX, double mouseY) {
        if (activeOverlay() != null) return null;
        Materialising.Option option = optionAt(shown(), mouseX, mouseY);
        if (option == null) return null;
        int col = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
        return new Shown(option.display().copyWithCount(1),
                new net.minecraft.client.renderer.Rect2i(leftPos + GRID_X + col * CELL, topPos + GRID_Y + row * CELL, 16, 16));
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        List<Materialising.Option> shown = shown();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(shown));
        bar.render(graphics, leftPos, topPos, scrollRow, ROWS, Mth.positiveCeilDiv(shown.size(), COLUMNS), scrollColour());
        Materialising.Option hovered = optionAt(shown, mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= shown.size()) return;
                Materialising.Option option = shown.get(index);
                int x = leftPos + GRID_X + col * CELL;
                int y = topPos + GRID_Y + row * CELL;
                if (option == hovered) graphics.fill(x, y, x + CELL - 2, y + CELL - 2, QuantumUiColours.ROW_HOVER);
                graphics.item(option.display().copyWithCount(1), x, y);
                if (exported(option)) graphics.outline(x - 1, y - 1, CELL, CELL, EXPORT);
                if (!option.allowed()) {
                    graphics.fill(x, y, x + 16, y + 16, 0xA0101018);
                    continue;
                }
                long held = buffered(option);
                long more = makeable(option);
                graphics.nextStratum();
                graphics.pose().pushMatrix();
                graphics.pose().scale(0.5f, 0.5f);
                // What waits in its buffer at the top; what it could make more, purple, at the bottom.
                if (held > 0) {
                    String count = HorizonCoreScreen.slotCount(held);
                    graphics.text(font, count, 2 * (x + 16) - font.width(count), 2 * y, 0xFFFFFFFF, true);
                }
                if (more > 0) {
                    String count = HorizonCoreScreen.slotCount(more);
                    graphics.text(font, count, 2 * (x + 16) - font.width(count), 2 * (y + 11), 0xFFB49CFF, true);
                }
                graphics.pose().popMatrix();
            }
        }
    }

    @Override
    protected void renderMachineTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // A Tesseract in the Matter slot: what it's reading through it.
        if (EntangledLinks.isLink(menu.getSlot(0).getItem()) && QuantumWidgets.hit(mouseX, mouseY,
                leftPos + MaterialiserMenu.MATTER_X - 1, topPos + MaterialiserMenu.MATTER_Y - 1, 18, 18)) {
            List<Component> lines = new ArrayList<>();
            if (reading.isEmpty()) {
                lines.add(Component.translatable("gui.quantimium.materialiser.reading.none").withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("gui.quantimium.materialiser.reading", reading.getCount(), reading.getHoverName()));
                MatterHistory history = MatterHistory.of(reading);
                if (!history.isEmpty()) lines.add(history.describe().withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (!menu.getCarried().isEmpty()) return;
        Materialising.Option option = optionAt(shown(), mouseX, mouseY);
        if (option == null) return;
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, option.display().copyWithCount(1)));
        lines.add(Component.translatable("gui.quantimium.materialiser.option.form", option.display().getCount(),
                option.display().getHoverName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.quantimium.materialiser.option.rarity", rarityName(option.rarity()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (option.allowed()) {
            if (buffered(option) > 0) {
                lines.add(Component.translatable("gui.quantimium.materialiser.option.buffered", String.format(Locale.ROOT, "%,d", buffered(option)))
                        .withStyle(ChatFormatting.WHITE));
            }
            lines.add(Component.translatable("gui.quantimium.materialiser.option.makeable", String.format(Locale.ROOT, "%,d", makeable(option)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable("gui.quantimium.materialiser.option.cost",
                    String.format("%.2f", option.trace()), option.flux()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(BandGate.refusal(FluxBand.values()[option.neededBand()], menu.getBand(), menu.getHeading())
                    .withStyle(ChatFormatting.GOLD));
        }
        if (exported(option)) lines.add(Component.translatable("gui.quantimium.materialiser.option.exported").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.quantimium.materialiser.option.choose").withStyle(ChatFormatting.DARK_GRAY));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private static Component rarityName(int rarity) {
        return Component.translatable("unrealised.quantimium.rarity."
                + Rarity.values()[Mth.clamp(rarity, 0, Rarity.values().length - 1)].getSerializedName());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (activeOverlay() == null && button == 0 && bar.over(mouseX, mouseY, leftPos, topPos)) {
            scrollRow = bar.press(mouseY, topPos, maxScroll(shown()));
            return true;
        }
        if (activeOverlay() == null && onGrid(mouseX, mouseY) && button <= 2) {
            var pos = menu.getBlockEntity().getBlockPos();
            Materialising.Option picked = optionAt(shown(), mouseX, mouseY);
            ItemStack carried = menu.getCarried();
            if (button == 2) {
                // Middle-click: export this form (what pipes see and its faces push), or stop.
                if (picked != null) {
                    Optional<Materialising.Target> next = exported(picked) ? Optional.empty() : Optional.of(formOf(picked));
                    target = next.orElse(null);
                    ClientPacketDistributor.sendToServer(new SetMaterialiserTargetPayload(pos, next));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                }
                return true;
            }
            // Right-click on what's already in hand: that much more of it.
            if (button == 1 && picked != null && picked.allowed() && !carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, picked.display())) {
                ClientPacketDistributor.sendToServer(new MaterialiserTakePayload(pos, formOf(picked), rightClickCount(picked, event.hasShiftDown()), false));
                return true;
            }
            if (!carried.isEmpty()) {
                ClientPacketDistributor.sendToServer(new MaterialiserPutPayload(pos, button == 1));
                return true;
            }
            if (picked != null && picked.allowed()) {
                int count = button == 1 ? rightClickCount(picked, event.hasShiftDown()) : picked.display().getMaxStackSize();
                ClientPacketDistributor.sendToServer(new MaterialiserTakePayload(pos, formOf(picked), count, button == 0 && event.hasShiftDown()));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Half a stack, or one with shift. */
    private static int rightClickCount(Materialising.Option option, boolean shift) {
        return shift ? 1 : Math.max(1, option.display().getMaxStackSize() / 2);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (bar.dragging()) {
            scrollRow = bar.scrollAt(event.y(), topPos, maxScroll(shown()));
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
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Shift and the wheel over the grid: down, one more onto the cursor; up, one from the cursor back.
        if (activeOverlay() == null && onGrid(mouseX, mouseY) && minecraft != null && minecraft.hasShiftDown() && scrollY != 0) {
            var pos = menu.getBlockEntity().getBlockPos();
            if (scrollY > 0) {
                if (!menu.getCarried().isEmpty()) ClientPacketDistributor.sendToServer(new MaterialiserPutPayload(pos, true));
            } else {
                Materialising.Option option = optionAt(shown(), mouseX, mouseY);
                if (option != null && option.allowed()) ClientPacketDistributor.sendToServer(new MaterialiserTakePayload(pos, formOf(option), 1, false));
            }
            return true;
        }
        if (activeOverlay() == null && onGrid(mouseX, mouseY)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll(shown()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String statusKey() {
        return "gui.quantimium.materialiser.status." + switch (menu.getStatusCode()) {
            case MaterialiserBlockEntity.STATUS_WORKING -> "working";
            case MaterialiserBlockEntity.STATUS_NO_TARGET -> "no_target";
            case MaterialiserBlockEntity.STATUS_NEEDS_FLUX -> "flux";
            case MaterialiserBlockEntity.STATUS_CANT_BECOME -> "cant_become";
            case MaterialiserBlockEntity.STATUS_NO_TRACE -> "trace";
            case MaterialiserBlockEntity.STATUS_NO_POWER -> "power";
            case MaterialiserBlockEntity.STATUS_THIN_FIELD -> "thin_field";
            case MaterialiserBlockEntity.STATUS_OUTPUT_FULL -> "full";
            case MaterialiserBlockEntity.STATUS_ON_DEMAND -> "on_demand";
            default -> "empty";
        };
    }

    @Override
    protected Component statusLabel() {
        return Component.translatable(statusKey());
    }

    @Override
    protected List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        if (menu.getStatusCode() == MaterialiserBlockEntity.STATUS_NEEDS_FLUX && menu.getTargetRarity() >= 0) {
            FluxBand needed = Materialising.bandFor(Rarity.values()[menu.getTargetRarity()]);
            if (needed != null) lines.add(BandGate.refusal(needed, menu.getBand(), menu.getHeading()));
        }
        lines.add(Component.translatable(statusKey() + ".tip"));
        if (menu.getTraceCredit() > 0.0) {
            lines.add(Component.translatable("gui.quantimium.materialiser.credit",
                    String.format(Locale.ROOT, "%.2f", menu.getTraceCredit())).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    @Override
    protected PanelTheme.Tone statusTone() {
        return switch (menu.getStatusCode()) {
            case MaterialiserBlockEntity.STATUS_WORKING, MaterialiserBlockEntity.STATUS_ON_DEMAND -> PanelTheme.Tone.OK;
            case MaterialiserBlockEntity.STATUS_EMPTY -> PanelTheme.Tone.IDLE;
            case MaterialiserBlockEntity.STATUS_NO_TARGET, MaterialiserBlockEntity.STATUS_OUTPUT_FULL,
                 MaterialiserBlockEntity.STATUS_NO_TRACE -> PanelTheme.Tone.WARN;
            default -> PanelTheme.Tone.BAD;
        };
    }
}
