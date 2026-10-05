package com.kadikular.quantimium.client;

import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.network.MirrorFloraPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-only nearby snapshot; replaced atomically by each owner-only server payload. */
public final class MirrorFloraClientCache {

    private static volatile List<MirrorFloraData.Entry> entries = List.of();
    private static volatile Set<Long> positions = Set.of();

    private MirrorFloraClientCache() {}

    public static List<MirrorFloraData.Entry> entries() {
        return entries;
    }

    public static void set(MirrorFloraPayload payload) {
        Map<Long, MirrorFloraData.Entry> unique = new LinkedHashMap<>();
        for (MirrorFloraPayload.ChunkSnapshot chunk : payload.chunks()) {
            for (MirrorFloraData.Entry entry : chunk.entries()) {
                unique.put(entry.packedPos(), entry);
            }
        }
        entries = List.copyOf(new ArrayList<>(unique.values()));
        positions = Set.copyOf(unique.keySet());
    }

    /** Whether the phased snapshot already has a plant here, so a rift's bleed does not draw a second. */
    public static boolean contains(long packedPos) {
        return positions.contains(packedPos);
    }

    public static void clear() {
        entries = List.of();
        positions = Set.of();
    }
}
