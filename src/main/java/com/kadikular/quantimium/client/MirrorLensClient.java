package com.kadikular.quantimium.client;

import com.kadikular.quantimium.item.MirrorLensItem;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * What this client knows of the field, from the server's once-a-second {@link FieldSurveyPayload}: in full for
 * a player in the mirror, and a toned-down slice of it through a Mirror Lens in the real world. The HUD, the
 * map and the field particles all read it from here.
 */
public final class MirrorLensClient {

    private static final float LENS_GHOST = 0.35f;

    @Nullable
    private static FieldSurveyPayload survey;

    private MirrorLensClient() {}

    public static void set(FieldSurveyPayload payload) {
        survey = payload.isClear() ? null : payload;
    }

    public static void clear() {
        survey = null;
        MirrorLensParticles.clear();
    }

    /** The last survey of the field round the player, or null if they cannot see it. */
    @Nullable
    public static FieldSurveyPayload survey() {
        return survey;
    }

    /** {@code load} is how full the containment over it is, -1 with none. */
    public record Reading(float flux, float anomaly, boolean contained, float load, float settling) {

        public boolean covered() {
            return load >= 0.0f;
        }
    }

    /** What the last survey said about the chunk the player is in now, or null if it did not cover it. */
    @Nullable
    public static Reading here(LocalPlayer player) {
        FieldSurveyPayload field = survey;
        if (field == null) return null;
        int dx = player.chunkPosition().x() - field.centreX();
        int dz = player.chunkPosition().z() - field.centreZ();
        if (Math.abs(dx) > field.radius() || Math.abs(dz) > field.radius()) return null;
        int i = field.index(dx, dz);
        return new Reading(field.flux()[i], field.anomaly()[i], field.contained()[i], field.load()[i], field.settling()[i]);
    }

    /** Whether this client can see the field right now. */
    public static boolean canSee() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (ClientPhaseState.isActive() || MirrorLensItem.isWorn(player));
    }

    /**
     * How solid a mirror creature's true body draws: whole in the mirror, faint through a Lens.
     * Only asked about bodies this client shows at all (see MirrorWispRenderer).
     */
    public static float ghostOpacity() {
        return ClientPhaseState.isActive() ? 1.0f : LENS_GHOST;
    }

    /** Whether a lens wearer in the real world sees the Veiled's true body. */
    public static boolean seesTrueBodies() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !ClientPhaseState.isActive() && MirrorLensItem.isWorn(player);
    }

    /** Called every client tick. */
    public static void tick(Minecraft minecraft) {
        MirrorLensParticles.tick(minecraft);
    }
}
