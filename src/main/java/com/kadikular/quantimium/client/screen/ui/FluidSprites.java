package com.kadikular.quantimium.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fluids drawn in screens with their own still texture and tint, as in the world: tank gauges and fluid filter entries. */
public final class FluidSprites {
    private FluidSprites() {}

    /** Fills {@code w} x {@code h} at ({@code x}, {@code y}) with {@code fluid}, its texture tiled from the top left. */
    public static void fill(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y, int w, int h) {
        if (fluid.isEmpty() || w <= 0 || h <= 0) return;
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        FluidTintSource tint = model.fluidTintSource();
        int color = tint == null ? 0xFFFFFFFF : tint.colorAsStack(fluid);
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        graphics.enableScissor(x, y, x + w, y + h);
        for (int tx = x; tx < x + w; tx += 16) {
            for (int ty = y; ty < y + h; ty += 16) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tx, ty, 16, 16, color);
        }
        graphics.disableScissor();
    }

    /** A gauge filled from the bottom by {@code fluid}, {@code amount} of {@code capacity}. */
    public static void gauge(GuiGraphicsExtractor graphics, FluidStack fluid, int capacity, int x, int y, int w, int h) {
        if (fluid.isEmpty() || capacity <= 0) return;
        int filled = Math.max(1, (int) Math.min(h, (long) h * fluid.getAmount() / capacity));
        fill(graphics, fluid, x, y + h - filled, w, filled);
    }
}
