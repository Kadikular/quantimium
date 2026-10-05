package com.kadikular.quantimium.compat.jei;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.recipe.observation.CollapseOutcome;
import com.kadikular.quantimium.unrealised.Rarity;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/**
 * One row per possible collapse result, with its rarity and its chance in the lowest band it comes
 * out in (every band in the tooltip). Not a recipe, but a player holding Unrealised Matter still wants
 * to know what it can turn into, and how hot a field it takes.
 */
public class ObservationChamberCategory implements IRecipeCategory<ObservationChamberCategory.Display> {

    /** One outcome, with its rarity and chance in each band. */
    public record Display(CollapseOutcome outcome) {}

    public static final RecipeType<Display> TYPE = RecipeType.create(
            Quantimium.MODID, "observation_chamber", Display.class);

    private static final int WIDTH = 132;
    private static final int HEIGHT = 40;
    private static final int INPUT_X = 1;
    private static final int SLOT_Y = 1;
    private static final int ARROW_X = 26;
    private static final int OUTPUT_X = 51;
    private static final int TEXT_X = 1;
    private static final int TEXT_Y = 23;
    private static final int TEXT_COLOUR = 0xFF6E6E6E;

    private final IDrawable icon;
    private final IDrawable arrow;

    public ObservationChamberCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.OBSERVATION_CHAMBER.get());
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<Display> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.quantimium.observation_chamber");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public Identifier getRegistryName(Display display) {
        Identifier output = BuiltInRegistries.ITEM.getKey(display.outcome().stack().getItem());
        return Identifier.fromNamespaceAndPath(Quantimium.MODID,
                "observation_chamber/" + output.getNamespace() + "/" + output.getPath());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Display display, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(ModItems.UNREALISED_MATTER.get()));
        builder.addOutputSlot(OUTPUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(display.outcome().stack());
    }

    @Override
    public void draw(Display display, IRecipeSlotsView slotsView, GuiGraphicsExtractor graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, ARROW_X, SLOT_Y + 4);
        Font font = Minecraft.getInstance().font;
        graphics.text(font, summary(display.outcome()), TEXT_X, TEXT_Y, TEXT_COLOUR, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Display display, IRecipeSlotsView slotsView,
                           double mouseX, double mouseY) {
        CollapseOutcome outcome = display.outcome();
        if (outcome.rarity() >= 0) {
            for (FluxBand band : FluxBand.values()) {
                float chance = outcome.chances().get(band.ordinal());
                tooltip.add(Component.translatable("jei.quantimium.observation.band_chance",
                        Component.translatable("flux.quantimium.band." + band.getSerializedName()),
                        chance > 0.0f ? formatChance(chance) + "%" : "–"));
            }
        }
        tooltip.add(Component.translatable("jei.quantimium.observation.batch",
                ObservationChamberBlockEntity.MEASUREMENT_BATCH));
        tooltip.add(Component.translatable("jei.quantimium.observation.trace",
                Math.round(ObservationChamberBlockEntity.TRACE_CHANCE * 100.0f)));
    }

    private static String formatChance(float chance) {
        float percent = chance * 100.0f;
        if (percent > 0.0f && percent < 0.1f) return "<0.1";
        return percent >= 10.0f
                ? String.format("%.0f", percent)
                : String.format("%.1f", percent);
    }

    /**
     * "Common · 12% at Low", "Rare · from High flux, 3%": its rarity and its chance in the lowest band
     * it comes out in. The tooltip has every band.
     */
    private static Component summary(CollapseOutcome outcome) {
        int first = outcome.firstBand();
        if (outcome.rarity() < 0 || first < 0) {
            return Component.translatable("jei.quantimium.observation.chance",
                    formatChance(outcome.chances().isEmpty() ? 0.0f : outcome.chances().getFirst()));
        }
        Component rarity = Component.translatable("unrealised.quantimium.rarity."
                + Rarity.values()[outcome.rarity()].getSerializedName());
        Component band = Component.translatable("flux.quantimium.band." + FluxBand.values()[first].getSerializedName());
        return Component.translatable(first == 0 ? "jei.quantimium.observation.rarity_low" : "jei.quantimium.observation.rarity_from",
                rarity, band, formatChance(outcome.chances().get(first)));
    }

    /** One display row per outcome, commonest first. */
    public static List<Display> displays(List<CollapseOutcome> outcomes) {
        return outcomes.stream()
                .sorted(Comparator.comparingInt(CollapseOutcome::rarity))
                .map(Display::new).toList();
    }
}
