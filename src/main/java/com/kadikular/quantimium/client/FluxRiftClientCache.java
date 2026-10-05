package com.kadikular.quantimium.client;

import com.kadikular.quantimium.client.renderer.MirrorLightningRenderer;
import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.item.LanceTargeting;
import com.kadikular.quantimium.network.FluxRiftSyncPayload;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.phase.FluxRift;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client copies of flux rifts with their own grow / collapse easing. Every client holds every rift
 * in its dimension; which face of it is drawn (shadow or anchor) is decided at render time.
 */
public final class FluxRiftClientCache {

    /** Ticks for a rift to open from nothing or fold shut on collapse. */
    private static final float EASE_TICKS = 12.0f;
    /** Blocks per tick the flora bleed advances or recedes: a stage 4 patch takes about ten seconds. */
    private static final float BLEED_SPREAD = 0.05f;
    /** Ticks between rescans of a rift's surroundings, so bleed follows terrain the player changes. */
    private static final int BLEED_RESCAN = 200;

    public static final class Entry {
        private final UUID id;
        private final BlockPos anchor;
        private final int shapeSeed;
        private int stage;
        private float coherence;
        private float shownScale;
        private float previousScale;
        private float presence;
        private float previousPresence;
        private boolean closing;
        private boolean draining;
        /** Held by a Rift Anchor: drawn calmer. */
        private boolean stabilised;
        /** Eased 0–1 "being shot" level, so the shudder builds and settles rather than switching. */
        private float shot;
        private float previousShot;
        private List<FluxRiftBleed.Bloom> bleed = List.of();
        private int scannedStage = -1;
        private int rescanIn;
        /** Bleed spreads outwards from the wound rather than appearing all at once. */
        private float bleedRadius;

        private Entry(FluxRiftSyncPayload.Snapshot snapshot) {
            this.id = snapshot.id();
            this.anchor = snapshot.anchor();
            this.shapeSeed = snapshot.shapeSeed();
            this.stage = snapshot.stage();
            this.coherence = snapshot.coherence();
            this.draining = snapshot.draining();
            this.stabilised = snapshot.stabilised();
            this.shownScale = FluxRift.scale(stage);
            this.previousScale = shownScale;
        }

        public UUID id() {
            return id;
        }

        public int shapeSeed() {
            return shapeSeed;
        }

        public float coherence() {
            return coherence;
        }

        public boolean stabilised() {
            return stabilised;
        }

        public boolean closing() {
            return closing;
        }

        public int stage() {
            return stage;
        }

        /** Bled plants currently showing: out to the spread radius, withering with the rift as it closes. */
        public List<MirrorFloraData.Entry> bleed() {
            float radius = bleedRadius * presence;
            List<MirrorFloraData.Entry> shown = new ArrayList<>();
            for (FluxRiftBleed.Bloom bloom : bleed) {
                if (bloom.distance() > radius) break;
                shown.add(bloom.entry());
            }
            return shown;
        }

        private void rescanIfDue(ClientLevel level) {
            if (stage == scannedStage && --rescanIn > 0) return;
            bleed = FluxRiftBleed.scan(level, anchor, shapeSeed, stage);
            scannedStage = stage;
            // Staggered per rift so several do not all rescan on the same tick.
            rescanIn = BLEED_RESCAN + Math.floorMod(shapeSeed, 40);
        }

        public BlockPos anchor() {
            return anchor;
        }

        /** How far the bleed reaches right now, shrinking with the rift as it closes. */
        public float bleedRadius() {
            return bleedRadius * presence;
        }

        public float shot(float partialTick) {
            return Mth.lerp(partialTick, previousShot, shot);
        }

        public float scale(float partialTick) {
            return Mth.lerp(partialTick, previousScale, shownScale);
        }

        public float presence(float partialTick) {
            return Mth.lerp(partialTick, previousPresence, presence);
        }

        /** Tracks the eased scale, so the wound grows into its next stage instead of popping. */
        public Vec3 centre(float partialTick) {
            float scale = scale(partialTick);
            return new Vec3(anchor.getX() + 0.5, anchor.getY() + 0.1 + 1.075 * scale, anchor.getZ() + 0.5);
        }

        private void tick() {
            previousScale = shownScale;
            previousPresence = presence;
            previousShot = shot;
            shot = draining ? Math.min(1.0f, shot + 0.25f) : Math.max(0.0f, shot - 0.08f);
            shownScale += (FluxRift.scale(stage) - shownScale) * 0.05f;
            float targetBleed = FluxRiftBleed.radius(stage);
            bleedRadius = bleedRadius < targetBleed
                    ? Math.min(targetBleed, bleedRadius + BLEED_SPREAD)
                    : Math.max(targetBleed, bleedRadius - BLEED_SPREAD);
            float step = 1.0f / EASE_TICKS;
            presence = closing ? Math.max(0.0f, presence - step) : Math.min(1.0f, presence + step);
        }
    }

    private static final Map<UUID, Entry> RIFTS = new LinkedHashMap<>();
    /** Told whenever the rifts change, for maps that mark them. */
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private FluxRiftClientCache() {}

    public static List<Entry> visible() {
        return new ArrayList<>(RIFTS.values());
    }

    /** Targeting spheres for beams drawn on this client. Closing rifts no longer catch the beam. */
    public static List<LanceTargeting.RiftSphere> spheres() {
        List<LanceTargeting.RiftSphere> spheres = new ArrayList<>();
        for (Entry entry : RIFTS.values()) {
            if (entry.closing) continue;
            spheres.add(new LanceTargeting.RiftSphere(entry.id, FluxRift.centre(entry.anchor, entry.stage),
                    FluxRift.hitRadius(entry.stage)));
        }
        return spheres;
    }

    public static void apply(FluxRiftSyncPayload payload) {
        Map<UUID, FluxRiftSyncPayload.Snapshot> next = new LinkedHashMap<>();
        for (FluxRiftSyncPayload.Snapshot snapshot : payload.rifts()) next.put(snapshot.id(), snapshot);

        for (Entry entry : RIFTS.values()) {
            if (next.containsKey(entry.id) || entry.closing) continue;
            entry.closing = true;
            collapse(entry);
        }
        for (FluxRiftSyncPayload.Snapshot snapshot : next.values()) {
            Entry entry = RIFTS.get(snapshot.id());
            if (entry == null) {
                RIFTS.put(snapshot.id(), new Entry(snapshot));
                continue;
            }
            if (snapshot.stage() > entry.stage) crack(entry.centre(1.0f));
            entry.stage = snapshot.stage();
            entry.coherence = snapshot.coherence();
            entry.draining = snapshot.draining();
            entry.stabilised = snapshot.stabilised();
            entry.closing = false;
        }
        LISTENERS.forEach(Runnable::run);
    }

    public static void listen(Runnable listener) {
        LISTENERS.add(listener);
    }

    /**
     * Nothing here advances while the game is paused: the world is frozen, and anything spawned into
     * it meanwhile would all appear at once on unpause. Particles also wait for screens to close.
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.level == null) return;
        boolean effects = MirrorAtmosphereEffects.effectsAllowed(minecraft);
        Iterator<Entry> iterator = RIFTS.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            entry.tick();
            if (entry.closing && entry.presence <= 0.0f) {
                iterator.remove();
                continue;
            }
            if (entry.closing) continue;
            entry.rescanIfDue(minecraft.level);
            if (!effects) continue;
            ambient(minecraft, entry);
            if (entry.draining && ClientPhaseState.isActive()) sparks(minecraft, entry);
            hum(minecraft, entry);
        }
        FluxRiftCorruption.update(visible());
    }

    /** Sparks thrown off the anchor while a lance is on it, so the player can see it biting. */
    private static void sparks(Minecraft minecraft, Entry entry) {
        var random = minecraft.level.getRandom();
        Vec3 centre = entry.centre(1.0f);
        for (int i = 0; i < 3; i++) {
            minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z,
                    (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.6);
        }
        if (random.nextInt(3) == 0) {
            minecraft.level.addParticle(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z,
                    (random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25,
                    (random.nextDouble() - 0.5) * 0.25);
        }
    }

    private static void hum(Minecraft minecraft, Entry entry) {
        if (minecraft.level.getRandom().nextInt(80) != 0 || minecraft.player == null) return;
        Vec3 centre = entry.centre(1.0f);
        if (minecraft.player.distanceToSqr(centre) > 24.0 * 24.0) return;
        minecraft.level.playLocalSound(centre.x, centre.y, centre.z, ModSounds.RIFT_AMBIENT.get(),
                SoundSource.AMBIENT, 0.6f + 0.15f * entry.stage, 1.0f, false);
    }

    public static void clear() {
        RIFTS.clear();
        FluxRiftCorruption.clear();
        LISTENERS.forEach(Runnable::run);
    }

    /** Every bled plant showing around every rift. */
    public static List<MirrorFloraData.Entry> bleed() {
        List<MirrorFloraData.Entry> all = new ArrayList<>();
        for (Entry entry : RIFTS.values()) all.addAll(entry.bleed());
        return all;
    }

    /** Motes drawn in from all round. Bigger rifts pull more, and one being drained pulls harder. */
    private static void ambient(Minecraft minecraft, Entry entry) {
        var random = minecraft.level.getRandom();
        float rate = 0.25f + 0.15f * entry.stage + (entry.draining ? 0.5f : 0.0f);
        if (random.nextFloat() > rate) return;
        Vec3 centre = entry.centre(1.0f);
        float reach = 1.5f + entry.stage;
        double x = centre.x + (random.nextDouble() - 0.5) * 2.0 * reach;
        double y = centre.y + (random.nextDouble() - 0.5) * 2.0 * reach;
        double z = centre.z + (random.nextDouble() - 0.5) * 2.0 * reach;
        // Portal motes start at origin + offset + one block up and ease into the origin; take the
        // block back off the offset so they start where rolled and end in the middle of the wound.
        minecraft.level.addParticle(ParticleTypes.PORTAL, centre.x, centre.y, centre.z,
                x - centre.x, y - centre.y - 1.0, z - centre.z);
    }

    private static void collapse(Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Vec3 centre = entry.centre(1.0f);
        crack(centre);
        minecraft.level.playLocalSound(centre.x, centre.y, centre.z, ModSounds.RIFT_COLLAPSE.get(),
                SoundSource.AMBIENT, 2.0f, 1.0f, false);
        var random = minecraft.level.getRandom();
        for (int i = 0; i < 40 + 20 * entry.stage; i++) {
            minecraft.level.addParticle(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z,
                    (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.3);
        }
    }

    private static void crack(Vec3 centre) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        MirrorLightningRenderer.strikeAt(minecraft.level, centre, minecraft.level.getRandom());
        if (ClientPhaseState.isActive()) {
            MirrorAtmosphereClient.beginFlash(6 + minecraft.level.getRandom().nextInt(5));
        }
    }
}
