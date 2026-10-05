package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.FieldTrend;
import com.kadikular.quantimium.flux.FluxBand;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Mirror Lens's readout of the chunk the player stands in: a bar each for flux (azure) and
 * anomaly (violet), filled on the same band scale the field is drawn on, ticked at the band
 * boundaries and named at the end, with a tag when a containment field holds the chunk. Shown while
 * the field can be seen (a lens worn, or in the mirror); out of the way of F1 and F3.
 *
 * <p>The bars ease towards each new reading rather than jumping, and pulse from Critical.
 */
public final class FieldHud implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int PAD = 4;
    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 5;
    private static final int ROW = 11;

    private static final int PANEL = 0xB00B0D18;
    private static final int EDGE = 0xFF262A4A;
    private static final int WELL = 0xFF05060D;
    private static final int TICK = 0xFF262A4A;
    private static final int TEXT = 0xFFDFE3F4;
    private static final int FLUX_DEEP = 0x1B4FB8;
    private static final int FLUX = 0x3485FF;
    private static final int FLUX_WHITE = 0xD6E6FF;
    private static final int INK = 0x3D1A5C;
    private static final int VIOLET = 0x8A60F0;
    private static final int CRYSTAL = 0xC9B4FF;
    private static final int MEMBRANE = 0xFF9FD0FF;
    private static final int OVERLOAD = 0xFFF0B429;

    /** The bars as drawn, easing towards the reading. */
    private float shownFlux;
    private float shownAnomaly;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) return;
        if (!Config.fieldHud() || !MirrorLensClient.canSee()) return;
        MirrorLensClient.Reading reading = MirrorLensClient.here(player);
        if (reading == null) return;

        float flux = MirrorAtmosphereClient.intensityOf(reading.flux());
        float anomaly = MirrorAtmosphereClient.intensityOf(reading.anomaly());
        float ease = Mth.clamp(delta.getRealtimeDeltaTicks() * 0.15f, 0.0f, 1.0f);
        shownFlux = Mth.lerp(ease, shownFlux, flux);
        shownAnomaly = Mth.lerp(ease, shownAnomaly, anomaly);

        Font font = minecraft.font;
        Component fluxLabel = Component.translatable("gui.quantimium.field_hud.flux");
        Component anomalyLabel = Component.translatable("gui.quantimium.field_hud.anomaly");
        // Where the flux is heading, so a slow field still answers at once: "Medium · heading for High".
        Component fluxBand = FieldTrend.describe(reading.flux(), reading.settling());
        Component anomalyBand = band(reading.anomaly());
        int labelWidth = Math.max(font.width(fluxLabel), font.width(anomalyLabel));
        int bandWidth = Math.max(font.width(fluxBand), font.width(anomalyBand));
        int width = PAD + labelWidth + PAD + BAR_WIDTH + PAD + bandWidth + PAD;
        int height = PAD + ROW * 2 + (reading.covered() ? ROW : 0) + PAD - 2;

        Config.HudCorner corner = Config.fieldHudCorner();
        boolean right = corner == Config.HudCorner.TOP_RIGHT || corner == Config.HudCorner.BOTTOM_RIGHT;
        boolean bottom = corner == Config.HudCorner.BOTTOM_LEFT || corner == Config.HudCorner.BOTTOM_RIGHT;
        int x = right ? graphics.guiWidth() - MARGIN - width : MARGIN;
        // At the bottom, clear of the hotbar and the bars above it.
        int y = bottom ? graphics.guiHeight() - MARGIN - height - 40 : MARGIN;

        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, EDGE);
        float time = (player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.15f;
        int barX = x + PAD + labelWidth + PAD;
        row(graphics, font, fluxLabel, fluxBand, x + PAD, barX, y + PAD, shownFlux, flux, FLUX_DEEP, FLUX, FLUX_WHITE, time);
        row(graphics, font, anomalyLabel, anomalyBand, x + PAD, barX, y + PAD + ROW, shownAnomaly, anomaly, INK, VIOLET,
                CRYSTAL, time + 1.3f);
        if (reading.covered()) {
            // How full the containment is; past 100% the overflow is feeding anomaly.
            int percent = Math.round(reading.load() * 100.0f);
            boolean held = reading.contained();
            graphics.text(font, Component.translatable(held ? "gui.quantimium.field_hud.contained" : "gui.quantimium.field_hud.overloaded",
                    percent), barX, y + PAD + ROW * 2, held ? MEMBRANE : OVERLOAD, false);
        }
    }

    private static Component band(float value) {
        return Component.translatable("flux.quantimium.band." + FluxBand.of(value).getSerializedName());
    }

    /** A label, a bar filled to {@code fill} with a three-stop gradient along it, and the band's name. */
    private static void row(GuiGraphicsExtractor graphics, Font font, Component label, Component band, int labelX, int barX, int y,
                            float fill, float level, int from, int middle, int to, float time) {
        graphics.text(font, label, labelX, y, TEXT, false);
        int barY = y + 2;
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, WELL);
        int filled = Mth.clamp(Math.round(fill * BAR_WIDTH), 0, BAR_WIDTH);
        // Critical and above pulse, so a dangerous chunk catches the eye without being read.
        float pulse = level >= 0.75f ? 0.75f + 0.25f * Mth.sin(time) : 1.0f;
        for (int i = 0; i < filled; i++) {
            float t = (float) i / (BAR_WIDTH - 1);
            int colour = t < 0.5f ? blend(from, middle, t * 2.0f) : blend(middle, to, (t - 0.5f) * 2.0f);
            graphics.fill(barX + i, barY, barX + i + 1, barY + BAR_HEIGHT, dim(colour, pulse));
        }
        for (int boundary = 1; boundary < 4; boundary++) {
            int tick = barX + BAR_WIDTH * boundary / 4;
            graphics.fill(tick, barY, tick + 1, barY + BAR_HEIGHT, TICK);
        }
        graphics.text(font, band, barX + BAR_WIDTH + PAD, y, 0xFF000000 | blend(middle, to, 0.5f), false);
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    private static int dim(int rgb, float by) {
        int r = Math.round(((rgb >> 16) & 0xFF) * by);
        int g = Math.round(((rgb >> 8) & 0xFF) * by);
        int b = Math.round((rgb & 0xFF) * by);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
