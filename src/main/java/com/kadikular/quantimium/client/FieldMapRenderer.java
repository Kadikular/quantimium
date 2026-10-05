package com.kadikular.quantimium.client;

import com.kadikular.quantimium.network.FieldSurveyPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Draws a field survey as a map, chunk by chunk, north up: flux in azure, anomaly as a violet wash
 * over it, and chunks under containment marked in the corner, pale when held and amber when
 * overloaded. For the Mirror Lens's field map and the Field Monitor's screen.
 */
public final class FieldMapRenderer {

    public static final int CELL = 8;
    public static final int GAP = 1;

    private static final int WELL = 0xFF05060D;
    private static final int FLUX_DEEP = 0x1B4FB8;
    private static final int FLUX = 0x3485FF;
    private static final int FLUX_WHITE = 0xD6E6FF;
    private static final int VIOLET = 0x8A60F0;
    private static final int MEMBRANE = 0xFF9FD0FF;
    private static final int OVERLOAD = 0xFFF0B429;
    private static final int MARK = 0xFFFFFFFF;

    private FieldMapRenderer() {}

    /** Width and height of the map for {@code survey}, in pixels. */
    public static int size(FieldSurveyPayload survey) {
        return (2 * survey.radius() + 1) * (CELL + GAP) - GAP;
    }

    /** Draws the map at ({@code x}, {@code y}), outlining the chunk ({@code markX}, {@code markZ}) from its centre. */
    public static void draw(GuiGraphicsExtractor graphics, FieldSurveyPayload survey, int x, int y, int markX, int markZ) {
        int radius = survey.radius();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int i = survey.index(dx, dz);
                int cx = x + (dx + radius) * (CELL + GAP);
                int cy = y + (dz + radius) * (CELL + GAP);
                graphics.fill(cx, cy, cx + CELL, cy + CELL, WELL);
                float flux = MirrorAtmosphereClient.intensityOf(survey.flux()[i]);
                float anomaly = MirrorAtmosphereClient.intensityOf(survey.anomaly()[i]);
                if (flux > 0.0f) {
                    int colour = flux < 0.5f ? blend(FLUX_DEEP, FLUX, flux * 2.0f) : blend(FLUX, FLUX_WHITE, (flux - 0.5f) * 2.0f);
                    int alpha = Math.round(Mth.lerp(Math.min(1.0f, flux * 4.0f), 60, 255));
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, (alpha << 24) | colour);
                }
                if (anomaly > 0.0f) {
                    int alpha = Math.round(Mth.clamp(anomaly, 0.0f, 1.0f) * 200.0f);
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, (alpha << 24) | VIOLET);
                }
                if (survey.load()[i] >= 0.0f) graphics.fill(cx, cy, cx + 2, cy + 2, survey.contained()[i] ? MEMBRANE : OVERLOAD);
                if (dx == markX && dz == markZ) graphics.outline(cx - 1, cy - 1, CELL + 2, CELL + 2, MARK);
            }
        }
    }

    /**
     * The colour a world map lays over a chunk with this field, as ARGB: flux in azure, stronger as it
     * rises, with anomaly washed violet over it. 0 for no field.
     */
    public static int mapColour(float flux, float anomaly) {
        float f = MirrorAtmosphereClient.intensityOf(flux);
        float a = MirrorAtmosphereClient.intensityOf(anomaly);
        if (f <= 0.0f && a <= 0.0f) return 0;
        int colour = f < 0.5f ? blend(FLUX_DEEP, FLUX, f * 2.0f) : blend(FLUX, FLUX_WHITE, (f - 0.5f) * 2.0f);
        float fluxAlpha = f <= 0.0f ? 0.0f : Mth.lerp(Math.min(1.0f, f * 2.0f), 0.2f, 0.55f);
        float wash = Mth.clamp(a, 0.0f, 1.0f) * 0.6f;
        float alpha = wash + fluxAlpha * (1.0f - wash);
        if (alpha <= 0.0f) return 0;
        colour = fluxAlpha <= 0.0f ? VIOLET : blend(colour, VIOLET, wash / alpha);
        return (Math.round(alpha * 255.0f) << 24) | colour;
    }

    /** The colour a world map lays over a chunk under containment at {@code load}, as ARGB: 0 when uncovered. */
    public static int loadColour(float load, boolean contained) {
        if (load < 0.0f) return 0;
        if (!contained) return 0xB0000000 | (OVERLOAD & 0xFFFFFF);
        int alpha = Math.round(Mth.lerp(Mth.clamp(load, 0.0f, 1.0f), 50, 150));
        return (alpha << 24) | (MEMBRANE & 0xFFFFFF);
    }

    private static int blend(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }
}
