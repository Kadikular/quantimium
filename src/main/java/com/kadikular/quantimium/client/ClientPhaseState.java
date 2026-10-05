package com.kadikular.quantimium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe phase flags read by chunk-building and render threads. */
public final class ClientPhaseState {

    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static final Set<UUID> REMOTE_PHASED = ConcurrentHashMap.newKeySet();

    private ClientPhaseState() {}

    public static boolean isActive() {
        return ACTIVE.get();
    }

    /** Local viewer, or another player whose overlay flag has been synced to this client. */
    public static boolean isPhased(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player) return isActive();
        return entity instanceof Player && REMOTE_PHASED.contains(entity.getUUID());
    }

    public static void setRemote(UUID player, boolean active) {
        if (active) REMOTE_PHASED.add(player);
        else REMOTE_PHASED.remove(player);
    }

    public static void clearRemotes() {
        REMOTE_PHASED.clear();
    }

    public static void set(boolean active) {
        boolean changed = ACTIVE.getAndSet(active) != active;
        Minecraft minecraft = Minecraft.getInstance();
        if (changed && minecraft.level != null && minecraft.player != null) {
            rebuildPhaseSensitiveSections(minecraft);
        }
    }

    /**
     * Biome tints are baked into chunk meshes, so every visible section must rebuild on entry/exit.
     * Phase-only blocks share the same trigger.
     */
    private static void rebuildPhaseSensitiveSections(Minecraft minecraft) {
        ChunkPos center = minecraft.player.chunkPosition();
        int radius = minecraft.options.getEffectiveRenderDistance();
        for (int chunkX = center.x() - radius; chunkX <= center.x() + radius; chunkX++) {
            for (int chunkZ = center.z() - radius; chunkZ <= center.z() + radius; chunkZ++) {
                LevelChunk chunk = minecraft.level.getChunkSource()
                        .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    if (!sections[index].hasOnlyAir()) {
                        minecraft.levelRenderer.setSectionDirty(
                                chunkX, chunk.getSectionYFromSectionIndex(index), chunkZ);
                    }
                }
            }
        }
    }
}
