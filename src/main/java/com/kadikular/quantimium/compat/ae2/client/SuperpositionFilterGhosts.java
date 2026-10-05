package com.kadikular.quantimium.compat.ae2.client;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.kadikular.quantimium.network.SetGhostFilterPayload;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Dragging an item from JEI onto the Superposition Crafter's filter, as onto a storage bus. */
public final class SuperpositionFilterGhosts implements IGhostIngredientHandler<SuperpositionCrafterScreen> {

    private SuperpositionFilterGhosts() {}

    public static void register(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(SuperpositionCrafterScreen.class, new SuperpositionFilterGhosts());
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(SuperpositionCrafterScreen screen, ITypedIngredient<I> ingredient,
                                               boolean doStart) {
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
                    ClientPacketDistributor.sendToServer(new SetGhostFilterPayload(screen.getMenu().containerId,
                            screen.ghostOffset() + index, stack));
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {}
}
