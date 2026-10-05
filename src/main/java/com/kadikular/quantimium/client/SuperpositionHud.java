package com.kadikular.quantimium.client;

import com.kadikular.quantimium.network.SuperpositionFlashPayload;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * What it looks like from inside to change bodies: the screen whites out to azure and clears from the
 * middle outward, with scanlines running through it, as the new body's eyes open. A rescue is slower
 * and colder, and starts from violet.
 */
public final class SuperpositionHud implements GuiLayer {

    private static final long SWAP_MILLIS = 1100;
    private static final long RESCUE_MILLIS = 2600;

    private static long started = Long.MIN_VALUE;
    private static int kind;

    public static void flash(int flashKind) {
        kind = flashKind;
        started = Util.getMillis();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (started == Long.MIN_VALUE) return;
        long length = kind == SuperpositionFlashPayload.RESCUE ? RESCUE_MILLIS : SWAP_MILLIS;
        float t = (Util.getMillis() - started) / (float) length;
        if (t >= 1.0f) {
            started = Long.MIN_VALUE;
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        boolean rescue = kind == SuperpositionFlashPayload.RESCUE;
        // The whole screen washes out, then clears from the centre as a widening slit: an eye opening.
        float wash = t < 0.15f ? t / 0.15f : 1.0f - (t - 0.15f) / 0.85f;
        wash = Mth.clamp(wash, 0.0f, 1.0f);
        int colour = rescue ? 0x8A60F0 : 0x3485FF;
        int base = ((int) (wash * 0.85f * 255) << 24) | colour;
        float open = Mth.clamp((t - 0.12f) / 0.6f, 0.0f, 1.0f);
        int slit = (int) (height * 0.5f * open * open);
        int mid = height / 2;
        graphics.fill(0, 0, width, Math.max(0, mid - slit), base);
        graphics.fill(0, Math.min(height, mid + slit), width, height, base);
        int inner = ((int) (wash * 0.35f * 255) << 24) | (rescue ? 0xD2BAFC : 0xD6E6FF);
        graphics.fill(0, mid - slit, width, mid + slit, inner);
        // Scanlines drift down through it.
        int lineAlpha = (int) (wash * 0.28f * 255);
        if (lineAlpha > 0) {
            int drift = (int) ((Util.getMillis() / 12) % 4);
            for (int y = drift; y < height; y += 4) graphics.fill(0, y, width, y + 1, (lineAlpha << 24) | 0x0B0D18);
        }
        // The seam of the opening eye, bright.
        if (open > 0.0f && open < 1.0f) {
            int seam = ((int) ((1.0f - open) * 255) << 24) | 0xFFFFFF;
            graphics.fill(0, mid - slit - 1, width, mid - slit, seam);
            graphics.fill(0, mid + slit, width, mid + slit + 1, seam);
        }
    }
}
