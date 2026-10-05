package com.kadikular.quantimium.client;

import com.kadikular.quantimium.client.renderer.MirrorLightningRenderer;
import com.kadikular.quantimium.network.MirrorRiftSyncPayload;
import com.kadikular.quantimium.phase.MirrorRift;
import com.kadikular.quantimium.phase.MirrorRiftKind;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client copies of world tears, each with its own open/close animator. */
public final class MirrorRiftClientCache {

    private static final Map<UUID, MirrorRiftAnimator> RIFTS = new LinkedHashMap<>();
    private static final Map<UUID, MirrorRiftKind> KINDS = new LinkedHashMap<>();

    private MirrorRiftClientCache() {}

    public static List<MirrorRiftAnimator> visible() {
        return new ArrayList<>(RIFTS.values());
    }

    public static void apply(MirrorRiftSyncPayload payload) {
        Map<UUID, MirrorRiftSyncPayload.Snapshot> next = new LinkedHashMap<>();
        for (MirrorRiftSyncPayload.Snapshot snapshot : payload.rifts()) {
            next.put(snapshot.id(), snapshot);
        }
        Iterator<Map.Entry<UUID, MirrorRiftAnimator>> iterator = RIFTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MirrorRiftAnimator> entry = iterator.next();
            if (next.containsKey(entry.getKey())) continue;
            MirrorRift closing = entry.getValue().rift();
            if (entry.getValue().close()) crack(closing);
            if (!entry.getValue().visible()) {
                iterator.remove();
                KINDS.remove(entry.getKey());
            }
        }
        for (MirrorRiftSyncPayload.Snapshot snapshot : next.values()) {
            MirrorRiftAnimator animator = RIFTS.get(snapshot.id());
            MirrorRift geometry = new MirrorRift(snapshot.anchor(), snapshot.facing(), snapshot.shapeSeed());
            if (animator == null) {
                animator = new MirrorRiftAnimator();
                if (snapshot.opening()) {
                    if (animator.open(geometry)) crack(geometry);
                } else {
                    animator.appearFull(geometry);
                }
                RIFTS.put(snapshot.id(), animator);
                KINDS.put(snapshot.id(), snapshot.kind());
            } else if (animator.rift() == null || !geometry.equals(animator.rift())) {
                if (animator.open(geometry)) crack(geometry);
            }
        }
    }

    public static void hideKindInstant(MirrorRiftKind kind) {
        Iterator<Map.Entry<UUID, MirrorRiftKind>> iterator = KINDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MirrorRiftKind> entry = iterator.next();
            if (entry.getValue() != kind) continue;
            RIFTS.remove(entry.getKey());
            iterator.remove();
        }
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, MirrorRiftAnimator>> iterator = RIFTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MirrorRiftAnimator> entry = iterator.next();
            entry.getValue().tick();
            if (!entry.getValue().visible()) {
                iterator.remove();
                KINDS.remove(entry.getKey());
            }
        }
    }

    public static void clear() {
        RIFTS.clear();
        KINDS.clear();
    }

    private static void crack(MirrorRift rift) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || rift == null) return;
        MirrorLightningRenderer.strikeAt(minecraft.level, rift.centre(), minecraft.level.getRandom());
        if (ClientPhaseState.isActive()) {
            MirrorAtmosphereClient.beginFlash(6 + minecraft.level.getRandom().nextInt(5));
        }
    }
}
