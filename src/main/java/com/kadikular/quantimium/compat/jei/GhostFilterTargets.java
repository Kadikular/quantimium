package com.kadikular.quantimium.compat.jei;

import com.kadikular.quantimium.client.screen.GhostFilterScreen;
import com.kadikular.quantimium.network.SetGhostFilterPayload;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Dragging an item from JEI onto a ghost filter (the Superposition Crafter's, a Catalyst Bay's), as onto a storage bus. */
public final class GhostFilterTargets<S extends Screen & GhostFilterScreen> implements IGhostIngredientHandler<S> {

    private GhostFilterTargets() {}

    public static <S extends Screen & GhostFilterScreen> void register(
            IGuiHandlerRegistration registration, Class<S> screen) {
        registration.addGhostIngredientHandler(screen, new GhostFilterTargets<>());
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(S screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (ingredient.getItemStack().isEmpty()) return targets;
        List<Rect2i> areas = screen.ghostTargets();
        for (int i = 0; i < areas.size(); i++) {
            int index = i;
            Rect2i area = areas.get(i);
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I value) {
                    if (!(value instanceof ItemStack stack)) return;
                    ClientPacketDistributor.sendToServer(new SetGhostFilterPayload(screen.ghostContainerId(),
                            screen.ghostOffset() + index, stack));
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {}
}
