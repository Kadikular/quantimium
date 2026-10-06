package com.kadikular.quantimium.client.screen;

import net.minecraft.client.renderer.Rect2i;

import java.util.List;

/** A screen with ghost filter slots a recipe viewer can drop items onto, one list showing at a time. */
public interface GhostFilterScreen {

    /** Where the ghost slots of the list on show are on screen; empty while the filter is hidden. */
    List<Rect2i> ghostTargets();

    /** Filter index of the first spot in the list on show. */
    int ghostOffset();

    /** The open menu's id, which the drop is sent with. */
    int ghostContainerId();
}
