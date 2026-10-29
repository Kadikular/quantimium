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
        int color = colour(model, fluid);
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        graphics.enableScissor(x, y, x + w, y + h);
        for (int tx = x; tx < x + w; tx += 16) {
            for (int ty = y; ty < y + h; ty += 16) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tx, ty, 16, 16, color);
        }
        graphics.disableScissor();
    }

    /** One 16x16 tile of {@code fluid} at ({@code x}, {@code y}): a slot's worth, as a fluid filter entry. */
    public static void tile(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) return;
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), x, y, 16, 16, colour(model, fluid));
    }

    private static int colour(FluidModel model, FluidStack fluid) {
        FluidTintSource tint = model.fluidTintSource();
        int color = tint == null ? 0xFFFFFFFF : tint.colorAsStack(fluid);
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    /** Graduation marks: a long one each quarter, a short one each eighth between. */
    private static final int MAJOR = 0x90FFFFFF;
    private static final int MINOR = 0x50FFFFFF;

    /** A gauge filled from the bottom by {@code fluid}, {@code amount} of {@code capacity}, marked as a measuring jug is. */
    public static void gauge(GuiGraphicsExtractor graphics, FluidStack fluid, int capacity, int x, int y, int w, int h) {
        if (!fluid.isEmpty() && capacity > 0) {
            int filled = Math.max(1, (int) Math.min(h, (long) h * fluid.getAmount() / capacity));
            fill(graphics, fluid, x, y + h - filled, w, filled);
        }
        // Down the right side, over the fluid; not at the very top or bottom.
        for (int eighth = 1; eighth < 8; eighth++) {
            int at = y + h - Math.round(h * eighth / 8f);
            boolean major = eighth % 2 == 0;
            int length = Math.max(2, major ? w / 2 : w / 4);
            graphics.fill(x + w - length, at, x + w, at + 1, major ? MAJOR : MINOR);
        }
    }
}
