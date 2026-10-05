package com.kadikular.quantimium.client.screen;

import net.minecraft.client.input.KeyEvent;
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
import com.kadikular.quantimium.network.OpenSideConfigPayload;
import com.kadikular.quantimium.network.RequestSideConfigPayload;
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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The Materialiser: Matter and Trace on the left; in the middle, every form of every ore the Matter
 * could become (as its history would make them), scrolled with the wheel and filtered by the search
 * box over them; results on the right. Click one to choose it, again to clear; those the band can't
 * choose yet are dimmed.
 */
public class MaterialiserScreen extends QuantumMachineScreen<MaterialiserMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Quantimium.MODID, "textures/gui/container/materialiser.png");

    /** The search box and the choices under it (tools/gui_panels.py: "search", "choices"). */
    private static final int SEARCH_X = 50;
    private static final int SEARCH_Y = 19;
    private static final int SEARCH_WIDTH = 86;
    private static final int CHOICES_X = 48;
    private static final int CHOICES_Y = 32;
    private static final int COLUMNS = 5;
    private static final int ROWS = 2;
    private static final int CELL = 18;

    /** The last options the server sent, for whichever Materialiser is open. */
    private static List<Materialising.Option> options = List.of();
    @Nullable
    private static Materialising.Target target;
    /** The Matter it's reading: what a Tesseract in its Matter slot links to. */
    private static ItemStack reading = ItemStack.EMPTY;

    private EditBox search;
    private int scrollRow;

    public MaterialiserScreen(MaterialiserMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        options = List.of();
        target = null;
    }

    /** From the server: what the open Materialiser could make, and what it's set to. */
    public static void setOptions(MaterialiserOptionsPayload payload) {
        options = payload.options();
        target = payload.target().orElse(null);
        reading = payload.reading();
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

    /** The options the search leaves: by item name or ore, ignoring case. */
    private List<Materialising.Option> shown() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return options;
        List<Materialising.Option> matching = new ArrayList<>();
        for (Materialising.Option option : options) {
            if (option.display().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    || option.ore().getPath().toLowerCase(Locale.ROOT).contains(query)) {
                matching.add(option);
            }
        }
        return matching;
    }

    private static boolean chosen(Materialising.Option option) {
        return target != null && target.is(option.ore(), option.display());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        // Typing in the search box shouldn't close the screen on the inventory key.
        if (activeOverlay() == null && search != null && search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(event) || search.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    protected List<Tab> createTabs() {
        return List.of(Tab.of("S", () -> ClientPacketDistributor.sendToServer(
                                new RequestSideConfigPayload(menu.getBlockEntity().getBlockPos())),
                        List.of(Component.translatable("gui.quantimium.materialiser.sides"),
                                Component.translatable("gui.quantimium.materialiser.sides.tip")))
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

    /** The shown option under ({@code mouseX}, {@code mouseY}), or null. */
    @Nullable
    private Materialising.Option optionAt(List<Materialising.Option> shown, double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - leftPos - CHOICES_X) / CELL);
        int row = (int) Math.floor((mouseY - topPos - CHOICES_Y) / CELL);
        if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) return null;
        int index = (row + scrollRow) * COLUMNS + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    protected void renderMachineBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        List<Materialising.Option> shown = shown();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll(shown));
        Materialising.Option hovered = optionAt(shown, mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = (row + scrollRow) * COLUMNS + col;
                if (index >= shown.size()) return;
                Materialising.Option option = shown.get(index);
                int x = leftPos + CHOICES_X + col * CELL;
                int y = topPos + CHOICES_Y + row * CELL;
                if (chosen(option)) {
                    graphics.fill(x, y, x + CELL, y + CELL, QuantumUiColours.ROW_HOVER);
                    graphics.outline(x, y, CELL, CELL, QuantumUiColours.ACCENT);
                } else if (option == hovered) {
                    graphics.fill(x, y, x + CELL, y + CELL, QuantumUiColours.ROW);
                }
                graphics.item(option.display(), x + 1, y + 1);
                graphics.itemDecorations(font, option.display(), x + 1, y + 1);
                if (!option.allowed()) graphics.fill(x, y, x + CELL, y + CELL, 0xA0101018);
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
        Materialising.Option option = optionAt(shown(), mouseX, mouseY);
        if (option == null) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.quantimium.materialiser.option.form", option.display().getCount(),
                option.display().getHoverName()));
        lines.add(Component.translatable("gui.quantimium.materialiser.option.rarity", rarityName(option.rarity()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (option.allowed()) {
            lines.add(Component.translatable("gui.quantimium.materialiser.option.cost",
                    String.format("%.2f", option.trace()), option.flux()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(BandGate.refusal(FluxBand.values()[option.neededBand()], menu.getBand(), menu.getHeading())
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable(chosen(option)
                ? "gui.quantimium.materialiser.option.chosen" : "gui.quantimium.materialiser.option.choose")
                .withStyle(ChatFormatting.DARK_GRAY));
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
        if (activeOverlay() == null && button == 0) {
            Materialising.Option picked = optionAt(shown(), mouseX, mouseY);
            if (picked != null) {
                Optional<Materialising.Target> next = chosen(picked) ? Optional.empty()
                        : Optional.of(new Materialising.Target(picked.ore(), BuiltInRegistries.ITEM.getKey(picked.display().getItem())));
                target = next.orElse(null);
                ClientPacketDistributor.sendToServer(new SetMaterialiserTargetPayload(menu.getBlockEntity().getBlockPos(), next));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeOverlay() == null && QuantumWidgets.hit(mouseX, mouseY, leftPos + CHOICES_X, topPos + CHOICES_Y,
                COLUMNS * CELL, ROWS * CELL)) {
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
