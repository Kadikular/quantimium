package com.kadikular.quantimium.compat.jei;

import com.kadikular.quantimium.client.screen.LogisticsTerminalScreen;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.menu.LogisticsTerminalMenu;
import com.kadikular.quantimium.network.LogisticsTerminalFillPayload;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI's + on a crafting recipe, with a Logistics Terminal open: the recipe's items are put into the terminal's
 * crafting grid, from what the network stores, the player's inventory, or what the network can make (the server
 * picks). Items none of those has are shown red, but the rest still go in; those only a machine can make (made when
 * the craft is taken) are shown blue.
 */
final class TerminalCraftingTransfer implements IRecipeTransferHandler<LogisticsTerminalMenu, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helper;

    TerminalCraftingTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends LogisticsTerminalMenu> getContainerClass() {
        return LogisticsTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<LogisticsTerminalMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.LOGISTICS_TERMINAL_MENU.get());
    }

    @Override
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(LogisticsTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        // JEI's crafting recipes lay out all nine slots of the grid, empty ones too.
        if (inputs.size() != 9) return helper.createInternalError();
        List<List<ItemStack>> options = new ArrayList<>(9);
        List<IRecipeSlotView> missing = new ArrayList<>();
        List<IRecipeSlotView> made = new ArrayList<>();
        for (IRecipeSlotView input : inputs) {
            List<ItemStack> stacks = input.getItemStacks().filter(stack -> !stack.isEmpty())
                    .limit(LogisticsTerminalMenu.MAX_OPTIONS).map(stack -> stack.copyWithCount(1)).toList();
            options.add(stacks);
            if (stacks.isEmpty() || stacks.stream().anyMatch(stack -> LogisticsTerminalScreen.stored(stack) || player.getInventory().contains(stack))) continue;
            if (stacks.stream().anyMatch(LogisticsTerminalScreen::listed)) made.add(input);
            else missing.add(input);
        }
        if (doTransfer) {
            if (!LogisticsTerminalMenu.craftingShown()) LogisticsTerminalMenu.showCrafting(true);
            ClientPacketDistributor.sendToServer(new LogisticsTerminalFillPayload(menu.pos(), options, maxTransfer));
        }
        if (missing.isEmpty() && made.isEmpty()) return null;
        return new Shown(missing, made);
    }

    /**
     * Some of the recipe's items can't be had (red: the rest can still be put in), or are made by the network when
     * the craft is taken (blue).
     */
    private record Shown(List<IRecipeSlotView> missing, List<IRecipeSlotView> made) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return missing.isEmpty() ? 0x804080FF : 0x80FFA500;
        }

        @Override
        public void showError(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, IRecipeSlotsView view, int recipeX, int recipeY) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(recipeX, recipeY);
            for (IRecipeSlotView slot : missing) slot.drawHighlight(graphics, 0x66FF0000);
            for (IRecipeSlotView slot : made) slot.drawHighlight(graphics, 0x664080FF);
            graphics.pose().popMatrix();
        }

        @Override
        public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip) {
            if (!missing.isEmpty()) tooltip.add(Component.translatable("gui.quantimium.logistics_terminal.crafting.missing").withStyle(net.minecraft.ChatFormatting.RED));
            if (!made.isEmpty()) tooltip.add(Component.translatable("gui.quantimium.logistics_terminal.crafting.made").withStyle(net.minecraft.ChatFormatting.BLUE));
        }

        @Override
        public int getMissingCountHint() {
            return missing.size();
        }
    }
}
