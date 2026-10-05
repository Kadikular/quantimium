package com.kadikular.quantimium.compat.jei;

import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModRecipeTypes;
import com.kadikular.quantimium.recipe.foundry.FoundryIngredient;
import com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shows the Foundry's timed recipes, which are otherwise undiscoverable: nothing in-game tells a
 * player a recipe's flux band or how many attunement arms it needs until the structure refuses to
 * run it.
 */
public class FoundryRecipeCategory implements IRecipeCategory<RecipeHolder<QuantumFoundryRecipe>> {
    public static final RecipeType<RecipeHolder<QuantumFoundryRecipe>> TYPE =
            RecipeType.createFromVanilla(ModRecipeTypes.FOUNDRY_TYPE.get());

    private static final int WIDTH = 150;
    private static final int HEIGHT = 62;
    private static final int SLOT_PITCH = 18;
    private static final int INPUT_X = 1;
    private static final int SLOT_Y = 1;
    private static final int ARROW_X = 78;
    private static final int OUTPUT_X = 105;
    private static final int TEXT_X = 1;
    private static final int TEXT_Y = 24;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOUR = 0xFF6E6E6E;

    private final IDrawable icon;
    private final IDrawable arrow;

    public FoundryRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get());
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<QuantumFoundryRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.quantimium.quantum_foundry_controller");
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
    public Identifier getRegistryName(RecipeHolder<QuantumFoundryRecipe> holder) {
        return holder.id().identifier();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<QuantumFoundryRecipe> holder,
                          IFocusGroup focuses) {
        QuantumFoundryRecipe recipe = holder.value();
        List<FoundryIngredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            FoundryIngredient ingredient = ingredients.get(i);
            builder.addInputSlot(INPUT_X + i * SLOT_PITCH, SLOT_Y)
                    .setStandardSlotBackground()
                    .addItemStacks(stacksWithCount(ingredient));
        }
        builder.addOutputSlot(OUTPUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
        // The Foundry assigns ingredients to arms by backtracking, so pillar order is not part of
        // the recipe. Marking it shapeless stops players reading the row as a required order.
        if (ingredients.size() > 1) {
            builder.setShapeless();
        }
    }

    private static List<ItemStack> stacksWithCount(FoundryIngredient ingredient) {
        return RecipeCompat.stacks(ingredient.ingredient()).stream()
                .map(stack -> stack.copyWithCount(ingredient.count()))
                .toList();
    }

    @Override
    public void draw(RecipeHolder<QuantumFoundryRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, ARROW_X, SLOT_Y + 4);
        Font font = Minecraft.getInstance().font;
        List<Component> lines = details(holder.value());
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), TEXT_X, TEXT_Y + i * LINE_HEIGHT,
                    TEXT_COLOUR, false);
        }
    }

    private static List<Component> details(QuantumFoundryRecipe recipe) {
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable("jei.quantimium.foundry.timing",
                formatSeconds(recipe.duration()), String.format("%,d", recipe.fePerTick())));
        lines.add(Component.translatable("jei.quantimium.foundry.field",
                Component.translatable("flux.quantimium.band."
                        + recipe.minimumFluxBand().getSerializedName()),
                formatFlux(recipe.fluxCost())));
        int arms = recipe.minimumPillars();
        Component armLine = Component.translatable(
                arms == 1 ? "jei.quantimium.foundry.arms.one" : "jei.quantimium.foundry.arms", arms);
        lines.add(recipe.anomaly() > 0.0
                ? Component.translatable("jei.quantimium.foundry.arms_anomaly",
                        armLine, formatFlux(recipe.anomaly()))
                : armLine);
        return lines;
    }

    private static String formatSeconds(int ticks) {
        double seconds = ticks / 20.0;
        return seconds == Math.floor(seconds)
                ? String.valueOf((int) seconds)
                : String.format("%.1f", seconds);
    }

    private static String formatFlux(double flux) {
        return flux == Math.floor(flux) ? String.valueOf((long) flux) : String.format("%.1f", flux);
    }
}
