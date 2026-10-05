package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Quantimium;
import java.util.List;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/**
 * The recipes the server sent this client (see {@code RecipeViewerSync}), for the recipe viewer.
 * Kept before recipe viewers read theirs, which they do from the same event.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class ClientRecipes {

    private static RecipeMap recipes = RecipeMap.EMPTY;

    private ClientRecipes() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
    }

    /** The server's recipes of {@code type}, as last sent; none before the first sync. */
    public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> byType(RecipeType<T> type) {
        return List.copyOf(recipes.byType(type));
    }
}
