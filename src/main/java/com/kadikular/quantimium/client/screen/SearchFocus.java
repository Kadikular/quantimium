package com.kadikular.quantimium.client.screen;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * A click anywhere but in one of our screens' search boxes lets go of it: typing after that is the game's again, so
 * a recipe viewer's keys (R, U) work over the grid. Done before the screen sees the click, as a screen that handles
 * a click on its grid itself would otherwise never move the focus.
 */
@EventBusSubscriber(modid = Quantimium.MODID, value = Dist.CLIENT)
public final class SearchFocus {

    private SearchFocus() {}

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!screen.getClass().getName().startsWith("com.kadikular.quantimium.")) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused() && !box.isMouseOver(event.getMouseX(), event.getMouseY())) {
                box.setFocused(false);
                if (screen.getFocused() == box) screen.setFocused(null);
            }
        }
    }
}
