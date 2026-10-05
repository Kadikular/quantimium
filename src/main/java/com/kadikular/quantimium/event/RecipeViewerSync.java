package com.kadikular.quantimium.event;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModRecipeTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends the Foundry's recipes to clients on join and after {@code /reload}. Clients hold no recipes
 * of their own since 1.21.2; without this a recipe viewer on a server has nothing to show.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class RecipeViewerSync {

    private RecipeViewerSync() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipeTypes.FOUNDRY_TYPE.get());
    }
}
