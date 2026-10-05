package com.kadikular.quantimium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Real grass and leaves around a stage 2+ rift sicken towards a dusty violet — the real world
 * starting to take on the mirror's colour. Tint only, through {@link MirrorPhaseColours}: nothing
 * is replaced, and it recovers as the rift closes. Replacing blocks would have been permanent
 * damage to someone's base for what is meant to read as the veil thinning.
 *
 * <p>Tints are baked into chunk meshes, which are built on worker threads, so zones are published
 * as an immutable snapshot and re-meshing is requested only when a zone's radius crosses a whole
 * block — a growing patch re-meshes its few sections every so often, not every frame.
 */
public final class FluxRiftCorruption {

    /** Past the plants by this much, so the sickness leads the growth. */
    private static final float MARGIN = 2.0f;
    private static final int BELOW = 6;
    /** Tall enough to reach a tree canopy. */
    private static final int ABOVE = 14;
    private static final float STRENGTH = 0.8f;
    private static final int TARGET = 0x5E4A6E;

    private record Zone(int x, int y, int z, int radius) {}

    private static volatile List<Zone> zones = List.of();

    private FluxRiftCorruption() {}

    /** Called each client tick with every rift's current bleed radius. */
    static void update(List<FluxRiftClientCache.Entry> rifts) {
        List<Zone> next = new ArrayList<>();
        for (FluxRiftClientCache.Entry rift : rifts) {
            float bleed = rift.bleedRadius();
            if (bleed <= 0.01f) continue;
            BlockPos anchor = rift.anchor();
            next.add(new Zone(anchor.getX(), anchor.getY(), anchor.getZ(), Mth.floor(bleed + MARGIN)));
        }
        List<Zone> previous = zones;
        if (next.equals(previous)) return;
        zones = List.copyOf(next);
        for (Zone zone : previous) if (!next.contains(zone)) remesh(zone);
        for (Zone zone : next) if (!previous.contains(zone)) remesh(zone);
    }

    static void clear() {
        zones = List.of();
    }

    /** 0 untouched, up to {@link #STRENGTH} at the wound. Safe to call from mesh-building threads. */
    public static float strength(BlockPos pos) {
        List<Zone> current = zones;
        if (current.isEmpty()) return 0.0f;
        float best = 0.0f;
        for (Zone zone : current) {
            int dy = pos.getY() - zone.y;
            if (dy < -BELOW || dy > ABOVE) continue;
            double dx = pos.getX() + 0.5 - (zone.x + 0.5);
            double dz = pos.getZ() + 0.5 - (zone.z + 0.5);
            float closeness = 1.0f - (float) Math.sqrt(dx * dx + dz * dz) / zone.radius;
            if (closeness <= 0.0f) continue;
            best = Math.max(best, closeness * closeness * (3.0f - 2.0f * closeness));
        }
        return best * STRENGTH;
    }

    public static int corrupt(int rgb, BlockPos pos) {
        float amount = strength(pos);
        if (amount <= 0.0f) return rgb;
        int r = Mth.floor(Mth.lerp(amount, (rgb >> 16) & 0xFF, (TARGET >> 16) & 0xFF));
        int g = Mth.floor(Mth.lerp(amount, (rgb >> 8) & 0xFF, (TARGET >> 8) & 0xFF));
        int b = Mth.floor(Mth.lerp(amount, rgb & 0xFF, TARGET & 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    private static void remesh(Zone zone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        // One block wider than the zone: biome tints blend with neighbours at the edge.
        int reach = zone.radius + 1;
        minecraft.levelRenderer.setBlocksDirty(zone.x - reach, zone.y - BELOW, zone.z - reach,
                zone.x + reach, zone.y + ABOVE, zone.z + reach);
    }
}
