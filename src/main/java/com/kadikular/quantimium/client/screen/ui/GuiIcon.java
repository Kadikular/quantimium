package com.kadikular.quantimium.client.screen.ui;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The shared 16×16 icons in {@code textures/gui/icons.png}, one per cell. The sheet is hand-drawn; a new
 * icon goes in a free cell and gets its column and row here (private/GUI_ICONS.md keeps the map).
 */
public enum GuiIcon {
    FILTER(0, 0), ALLOW(1, 0), DENY(2, 0), TAG(3, 0), COMPONENTS(4, 0), PRIORITY(5, 0), INFO(6, 0), BACK(7, 0),
    SIDES(8, 0), REDSTONE(9, 0), ENERGY(10, 0), ANOMALY(11, 0),
    STORAGE(0, 1), PROVIDER(1, 1), REQUESTER(2, 1), ACTIVE_EXPORT(3, 1), ACTIVE_IMPORT(4, 1), PASSIVE(5, 1),
    COLLECT(6, 1), SEND(7, 1), BOTH(8, 1), SHARE(9, 1),
    SORT_COUNT(0, 2), SORT_NAME(1, 2), VIEW_ALL(2, 2), VIEW_REAL(3, 2), VIEW_CRAFT(4, 2), VIEW_BOTH(5, 2),
    HOSTILE(0, 3), ANIMAL(1, 3), PLAYER(2, 3), GROWN(3, 3), BABY(4, 3), COUNT(5, 3), BLOCK(6, 3), CROP(7, 3),
    LOCKED(0, 4), UNLOCKED(1, 4), VOID(2, 4), SLOTS(3, 4), ACCELERATE(4, 4), HOLD(5, 4), AREA_PICKED(6, 4),
    AREA_CUBE(7, 4), AREA_SPHERE(8, 4), FLOOR(9, 4), CEILING(10, 4), WATCH(11, 4), TREES(12, 4), STEPS(13, 4),
    TREES_AND_STEPS(14, 4), AUTO(15, 4),
    LOGISTICS(0, 5), ACTIONS(1, 5), MODIFIERS(2, 5), KEEP(3, 5), NEAREST(4, 5);

    public static final Identifier SHEET = Identifier.fromNamespaceAndPath(Quantimium.MODID, "textures/gui/icons.png");
    public static final int SIZE = 16;
    /** Icons the player can't act on now are drawn at this tint: the same art, dimmed. */
    public static final int DIMMED = 0xFF6A7090;

    private final int u;
    private final int v;

    GuiIcon(int column, int row) {
        this.u = column * SIZE;
        this.v = row * SIZE;
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        draw(graphics, x, y, 0xFFFFFFFF);
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int tint) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, u, v, SIZE, SIZE, 256, 256, tint);
    }
}
