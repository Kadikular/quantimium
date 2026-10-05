package com.kadikular.quantimium.compat.jei;

import com.kadikular.quantimium.client.ClientRecipes;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.client.renderer.Rect2i;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import com.kadikular.quantimium.client.screen.QuantumMachineScreen;
import com.kadikular.quantimium.compat.ae2.client.SuperpositionFilterGhosts;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.neoforged.fml.ModList;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.client.ClientCollapseOutcomes;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModRecipeTypes;
import com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Optional JEI integration. Only loaded by JEI itself, so nothing in common code may reference
 * this package or the mod would fail to load without JEI installed.
 */
@JeiPlugin
public class QuantimiumJeiPlugin implements IModPlugin {
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new FoundryRecipeCategory(guiHelper),
                new ObservationChamberCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The server sends them on join; a client holds no recipes of its own (see RecipeViewerSync).
        List<RecipeHolder<QuantumFoundryRecipe>> foundry = ClientRecipes.byType(ModRecipeTypes.FOUNDRY_TYPE.get());
        registration.addRecipes(FoundryRecipeCategory.TYPE, foundry);
        registration.addRecipes(ObservationChamberCategory.TYPE,
                ObservationChamberCategory.displays(ClientCollapseOutcomes.get()));

        // Folding has no recipe to look up, so its blocks explain themselves.
        Component[] chamber = {
                Component.translatable("jei.quantimium.fold_chamber.info.frame"),
                Component.translatable("jei.quantimium.fold_chamber.info.fold"),
                Component.translatable("jei.quantimium.fold_chamber.info.rules")};
        registration.addItemStackInfo(List.of(new ItemStack(ModBlocks.FOLD_CORE.get()),
                new ItemStack(ModBlocks.FOLD_PYLON.get()), new ItemStack(ModBlocks.FOLD_RAIL.get())), chamber);
        registration.addIngredientInfo(ModItems.FOLDED_TESSERACT.get(),
                Component.translatable("jei.quantimium.folded_tesseract.info.catalyst"),
                Component.translatable("jei.quantimium.folded_tesseract.info.unfold"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The controller is what a player looks up; the plinth is the block they are most likely
        // holding while wondering what the thing makes.
        registration.addCraftingStation(FoundryRecipeCategory.TYPE,
                ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get(), ModBlocks.QUANTUM_FOUNDRY_PLINTH.get());
        // A folded Foundry makes the same recipes in a Quantum Crafter, those its arms allow.
        registration.addCraftingStation(FoundryRecipeCategory.TYPE, ModItems.FOLDED_TESSERACT.get());
        registration.addCraftingStation(ObservationChamberCategory.TYPE,
                ModBlocks.OBSERVATION_CHAMBER.get(), ModBlocks.QUANTUM_OBSERVATION_CHAMBER.get());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Every machine screen's tab column hangs off the panel's right edge; keep the item list off it.
        registration.addGenericGuiContainerHandler(QuantumMachineScreen.class, new IGuiContainerHandler<QuantumMachineScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(QuantumMachineScreen<?> screen) {
                return screen.tabAreas();
            }
        });
        // The Superposition Crafter only exists with AE2; its handler is only touched then.
        if (ModList.get().isLoaded("ae2")) SuperpositionFilterGhosts.register(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // The collapse table sync and JEI's own startup race each other. If the data arrives after
        // this plugin loaded, feed it in rather than leaving the category empty until the next join.
        ClientCollapseOutcomes.listen(outcomes -> runtime.getRecipeManager().addRecipes(
                ObservationChamberCategory.TYPE, ObservationChamberCategory.displays(outcomes)));
    }
}
