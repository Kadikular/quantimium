package com.kadikular.quantimium.flux;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.item.MirrorLensItem;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tells a player who can see the field what it looks like around them, once a second: anyone in the
 * mirror, where the field is laid bare, and anyone wearing a Mirror Lens, who sees a slice of it. The
 * client turns it into plumes, motes and threads (MirrorLensClient); nobody else is sent anything.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class FieldSurvey {

    /** Chunks out from the player's own: the visuals fade out over the last two. */
    public static final int RADIUS = 4;
    /** Feeders and drains this far away are drawn: further than the chunks, since they stand out more. */
    private static final double SOURCE_RANGE = RADIUS * 16.0 * 1.5;
    private static final int MAX_SOURCES = 48;
    private static final int INTERVAL = 20;

    /** Players sent a survey last time, so the one who stops seeing gets a clear. */
    private static final Set<UUID> SEEING = new HashSet<>();

    private FieldSurvey() {}

    /** Whether {@code player} can see the field: in the mirror, or through a lens. */
    public static boolean canSee(ServerPlayer player) {
        return MirrorPhase.isPhased(player) || MirrorLensItem.isWorn(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((player.level().getGameTime() + player.getId()) % INTERVAL != 0) return;
        if (canSee(player)) {
            SEEING.add(player.getUUID());
            PacketDistributor.sendToPlayer(player, survey(player));
        } else if (SEEING.remove(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, FieldSurveyPayload.clear());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SEEING.remove(event.getEntity().getUUID());
    }

    /** The field round {@code player} as it would be sent. */
    public static FieldSurveyPayload survey(ServerPlayer player) {
        return survey(player.level(), player.chunkPosition(), player.getBlockY(), player.blockPosition());
    }

    /**
     * The field in the {@link #RADIUS} chunks round {@code centre}, read at height {@code y}, with the
     * blocks feeding or draining it near {@code sourcesNear} (null for none).
     */
    public static FieldSurveyPayload survey(ServerLevel level, ChunkPos centre, int y, @Nullable BlockPos sourcesNear) {
        int side = 2 * RADIUS + 1;
        float[] flux = new float[side * side];
        float[] anomaly = new float[side * side];
        boolean[] contained = new boolean[side * side];
        float[] load = new float[side * side];
        float[] settling = new float[side * side];
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int i = (dz + RADIUS) * side + (dx + RADIUS);
                BlockPos middle = new ChunkPos(centre.x() + dx, centre.z() + dz).getMiddleBlockPosition(y);
                QuantumFlux.Neighbourhood field = QuantumFlux.chunk(level, middle);
                flux[i] = (float) field.flux();
                anomaly[i] = (float) field.anomaly();
                contained[i] = QuantumFlux.chunkContained(level, middle);
                load[i] = QuantumFlux.chunkShielded(level, middle) ? (float) QuantumFlux.chunkLoad(level, middle) : -1.0f;
                settling[i] = (float) QuantumFlux.chunkSettling(level, middle);
            }
        }
        return new FieldSurveyPayload(centre.x(), centre.z(), RADIUS, flux, anomaly, contained, load, settling,
                sourcesNear == null ? java.util.List.of() : FluxSources.near(level, sourcesNear, SOURCE_RANGE, MAX_SOURCES));
    }
}
