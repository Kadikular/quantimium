package com.kadikular.quantimium.client;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.client.particle.FieldParticle;
import com.kadikular.quantimium.client.particle.FieldParticle.Look;
import com.kadikular.quantimium.client.particle.FieldParticle.Motion;
import com.kadikular.quantimium.flux.FluxSources;
import com.kadikular.quantimium.item.MirrorLensItem;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The field made visible, from the server's once-a-second {@link FieldSurveyPayload}: in full for a
 * player in the mirror, and a toned-down slice of it through a Mirror Lens in the real world.
 *
 * <p>Flux is weather. The air thickens with a low azure haze and slow veils above it, and now and then
 * a plume erupts: the ground seam glows for a moment, then a column rises, a few blocks at Medium and
 * some twenty at Singularity, its head blooming and drifting off. From High, sparks wink on the floors.
 * Under a containment field they rise three blocks and flatten against it, which shows the field.
 *
 * <p>Anomaly is ink: dark violet smoke that seeps up and sinks and pools, with bright specks snapping
 * about inside it, cracks from High and small vortices from Critical. Machines exhale what they emit,
 * more the more they emit, trailing violet in proportion to the anomaly in it; rifts and crystals pour
 * ink; suppressors, halls and containment draw it in along spirals.
 *
 * <p>Everything fades with distance, from full two chunks out to nothing at about four and a half,
 * within a per-tick budget that follows the particle setting and the config's density.
 */
// The survey state and readings they draw from live in MirrorLensClient.
public final class MirrorLensParticles {

    /** Full strength inside this, horizontally; nothing past {@link #FADE_END}. */
    private static final double FADE_START = 32.0;
    private static final double FADE_END = 72.0;
    /** Feeders and drains (halls, rifts, machines) stand out further than the air round you. */
    private static final double SOURCE_FADE_START = 48.0;
    private static final double SOURCE_FADE_END = 108.0;
    /** Anomaly-only sources this small are crystals: they brood, so they show half as often as a rift. */
    private static final double CRYSTAL_SIZED = 5.0;
    /** A Lens shows a toned-down slice of the field; the mirror shows it all, and more of it. */
    private static final float LENS_STRENGTH = 0.45f;
    private static final float MIRROR_DENSITY = 1.8f;
    private static final int BUDGET = 110;

    private static final int FLUX_DEEP = 0x1B4FB8;
    private static final int FLUX = 0x3485FF;
    private static final int FLUX_CORE = 0x7FB2FF;
    private static final int FLUX_WHITE = 0xD6E6FF;
    private static final int MEMBRANE = 0x9FD0FF;
    private static final int VIOLET = 0x8A60F0;
    private static final int CRYSTAL = 0xB196FF;
    private static final int INK = 0x2A1244;
    private static final int INK_DEEP = 0x3D1A5C;
    private static final int CRACK = 0xE8DCFF;

    private static final List<Plume> PLUMES = new ArrayList<>();
    private static int spawnedThisTick;

    /** A plume in progress: a glowing seam first, then an eruption fed from its base for a while. */
    private static final class Plume {
        final double x, y, z;
        final float intensity;
        final double height;
        final double ceiling;
        final int seam;
        final int length;
        final float angle;
        int age;

        Plume(double x, double y, double z, float intensity, double height, double ceiling, int length, float angle) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.intensity = intensity;
            this.height = height;
            this.ceiling = ceiling;
            this.seam = 8;
            this.length = length;
            this.angle = angle;
        }
    }

    private MirrorLensParticles() {}

    public static void clear() {
        PLUMES.clear();
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        FieldSurveyPayload field = MirrorLensClient.survey();
        if (level == null || player == null || field == null || !MirrorLensClient.canSee()) {
            PLUMES.clear();
            return;
        }
        if (!MirrorAtmosphereEffects.effectsAllowed(minecraft)) return;
        boolean mirror = ClientPhaseState.isActive();
        View view = new View(mirror ? 1.0f : LENS_STRENGTH, mirror ? MIRROR_DENSITY : 1.0f);
        spawnedThisTick = 0;
        int budget = budget(minecraft);
        RandomSource random = level.getRandom();

        tickPlumes(level, random, view, budget);
        for (int dz = -field.radius(); dz <= field.radius(); dz++) {
            for (int dx = -field.radius(); dx <= field.radius(); dx++) {
                int chunkX = field.centreX() + dx;
                int chunkZ = field.centreZ() + dz;
                double weight = fade(player, chunkX * 16 + 8, chunkZ * 16 + 8);
                if (weight <= 0.0) continue;
                int i = field.index(dx, dz);
                chunkEvents(level, player, random, view, chunkX, chunkZ, weight,
                        MirrorAtmosphereClient.intensityOf(field.flux()[i]),
                        MirrorAtmosphereClient.intensityOf(field.anomaly()[i]), field.contained()[i]);
            }
        }
        air(level, player, random, view, field, budget);
        sparkles(level, player, random, view, field, budget);
        for (FluxSources.Source source : field.sources()) {
            double weight = fade(player, source.pos().getX() + 0.5, source.pos().getZ() + 0.5,
                    SOURCE_FADE_START, SOURCE_FADE_END);
            if (weight > 0.0) source(level, random, view, source, weight, budget);
        }
    }

    /** How strongly this client sees the field: its alpha scale, and how much more of it the mirror shows. */
    private record View(float alpha, float density) {}

    // ---- plumes: rare events ----

    /**
     * Plumes a second per chunk at full weight: none at Low, and at about 25 chunks in view, one every
     * ten seconds or so across them at Medium, up to about one a second at Singularity.
     */
    private static double plumesPerSecond(float flux) {
        if (flux < 0.2f) return 0.0;
        return curve(flux, 0.004, 0.009, 0.019, 0.04);
    }

    /** Blocks tall: three at Medium, seven at High, fourteen at Critical, twenty-four at Singularity. */
    private static double plumeHeight(float flux) {
        return curve(flux, 3.0, 7.0, 14.0, 24.0);
    }

    /** A value at Medium, High, Critical and Singularity (0.25, 0.5, 0.75 and 1), eased between. */
    private static double curve(float band, double medium, double high, double critical, double singularity) {
        if (band < 0.25f) return medium * Math.max(0.0f, band) / 0.25;
        if (band < 0.5f) return Mth.lerp((band - 0.25) / 0.25, medium, high);
        if (band < 0.75f) return Mth.lerp((band - 0.5) / 0.25, high, critical);
        return Mth.lerp(Math.min(1.0, (band - 0.75) / 0.25), critical, singularity);
    }

    private static void chunkEvents(ClientLevel level, LocalPlayer player, RandomSource random, View view, int chunkX,
                                    int chunkZ, double weight, float flux, float anomaly, boolean contained) {
        if (random.nextDouble() < plumesPerSecond(flux) * weight * view.density() / 20.0 && PLUMES.size() < 16) {
            BlockPos ground = ground(level, player, chunkX * 16 + random.nextInt(16), chunkZ * 16 + random.nextInt(16));
            if (ground != null) {
                double height = plumeHeight(flux) * (0.85 + random.nextDouble() * 0.3);
                // Held under a containment field: it gets three blocks up and meets it.
                double ceiling = contained ? ground.getY() + 3.0 : Double.MAX_VALUE;
                PLUMES.add(new Plume(ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, flux, height, ceiling,
                        14 + (int) (flux * 26.0f), random.nextFloat() * Mth.TWO_PI));
            }
        }
        double cracks = anomaly >= 0.5f ? (anomaly - 0.45) * 0.06 : 0.0;
        if (random.nextDouble() < cracks * weight * view.density()) {
            BlockPos ground = ground(level, player, chunkX * 16 + random.nextInt(16), chunkZ * 16 + random.nextInt(16));
            if (ground != null) crack(level, random, view, Vec3.atBottomCenterOf(ground).add(0, 1.0 + random.nextDouble() * 2.0, 0));
        }
        double vortices = anomaly >= 0.75f ? (anomaly - 0.7) * 0.025 : 0.0;
        if (random.nextDouble() < vortices * weight * view.density()) {
            BlockPos ground = ground(level, player, chunkX * 16 + random.nextInt(16), chunkZ * 16 + random.nextInt(16));
            if (ground != null) vortex(level, random, view, Vec3.atBottomCenterOf(ground).add(0, 1.5 + random.nextDouble() * 1.5, 0));
        }
    }

    private static void tickPlumes(ClientLevel level, RandomSource random, View view, int budget) {
        Iterator<Plume> it = PLUMES.iterator();
        while (it.hasNext()) {
            Plume plume = it.next();
            int age = plume.age++;
            if (age >= plume.seam + plume.length) {
                it.remove();
                continue;
            }
            if (age < plume.seam) {
                seam(level, random, view, plume);
                continue;
            }
            erupt(level, random, view, plume, (float) (age - plume.seam) / plume.length, budget);
        }
    }

    /** The warning: a line of white sparks along the ground where it is about to come up. */
    private static void seam(ClientLevel level, RandomSource random, View view, Plume plume) {
        double along = (random.nextDouble() - 0.5) * 1.6;
        add(level, FieldParticle.create(level, plume.x + Math.cos(plume.angle) * along, plume.y + 0.02,
                plume.z + Math.sin(plume.angle) * along, 0, 0.01 + random.nextDouble() * 0.02, 0, Motion.RISE, Look.GLOW,
                FLUX_WHITE, 0.9f * view.alpha(), 0.06f, 8 + random.nextInt(6)));
    }

    /**
     * The column: soft azure body specks and bright core sparks from its base, fast enough to reach
     * its height (a speck slowing by 4% a tick covers about 21 times its launch speed in its life), the
     * body blooming once it slows; embers thrown off the hotter ones. It tapers as it dies.
     */
    private static void erupt(ClientLevel level, RandomSource random, View view, Plume plume, float progress, int budget) {
        double launch = plume.height / 21.0;
        int specks = (int) ((3 + plume.intensity * 9) * (1.0f - progress * 0.6f) * view.density());
        for (int i = 0; i < specks && spawnedThisTick < budget; i++) {
            boolean core = random.nextFloat() < 0.3f;
            double spread = core ? 0.12 : 0.35;
            double speed = launch * (0.85 + random.nextDouble() * 0.25);
            if (core) {
                add(level, FieldParticle.create(level, plume.x + (random.nextDouble() - 0.5) * spread, plume.y,
                        plume.z + (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * 0.01, speed,
                        (random.nextDouble() - 0.5) * 0.01, Motion.RISE, Look.GLOW,
                        plume.intensity > 0.7f ? FLUX_WHITE : FLUX_CORE, 0.95f * view.alpha(), 0.08f, 42 + random.nextInt(8)),
                        plume.ceiling, 0.0f);
            } else {
                int colour = plume.intensity > 0.45f ? FLUX : FLUX_DEEP;
                add(level, FieldParticle.create(level, plume.x + (random.nextDouble() - 0.5) * spread, plume.y,
                        plume.z + (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * 0.02, speed,
                        (random.nextDouble() - 0.5) * 0.02, Motion.RISE, Look.SOFT, colour,
                        0.32f * view.alpha(), 0.35f + plume.intensity * 0.35f, 46 + random.nextInt(14)),
                        plume.ceiling, 1.2f + plume.intensity * 1.6f);
            }
        }
        if (plume.intensity >= 0.5f && random.nextFloat() < 0.5f * view.density()) {
            double at = random.nextDouble() * Math.min(plume.height, plume.ceiling - plume.y) * 0.6;
            add(level, FieldParticle.create(level, plume.x, plume.y + at, plume.z, (random.nextDouble() - 0.5) * 0.25,
                    0.05 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.25, Motion.RISE, Look.GLOW,
                    FLUX_WHITE, 0.9f * view.alpha(), 0.05f, 16 + random.nextInt(12)));
        }
        if (plume.ceiling < Double.MAX_VALUE && random.nextFloat() < 0.6f) {
            // The membrane, lit from below where the plume meets it.
            double r = 1.0 + random.nextDouble() * 3.0;
            double a = random.nextDouble() * Mth.TWO_PI;
            add(level, FieldParticle.create(level, plume.x + Math.cos(a) * r, plume.ceiling, plume.z + Math.sin(a) * r,
                    Math.cos(a) * 0.01, 0, Math.sin(a) * 0.01, Motion.DRIFT, Look.SOFT, MEMBRANE, 0.18f * view.alpha(),
                    0.9f, 40 + random.nextInt(20)));
        }
    }

    // ---- the air ----

    /**
     * Round the player, what the chunk under each spot holds: a low azure haze and higher veils for
     * flux, dark ink seeping up and settling for anomaly, with bright specks snapping about in it.
     */
    private static void air(ClientLevel level, LocalPlayer player, RandomSource random, View view, FieldSurveyPayload field,
                            int budget) {
        int attempts = Math.round(5 * view.density());
        for (int attempt = 0; attempt < attempts && spawnedThisTick < budget; attempt++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 40.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 40.0;
            int dx = (Mth.floor(x) >> 4) - field.centreX();
            int dz = (Mth.floor(z) >> 4) - field.centreZ();
            if (Math.abs(dx) > field.radius() || Math.abs(dz) > field.radius()) continue;
            int i = field.index(dx, dz);
            float flux = MirrorAtmosphereClient.intensityOf(field.flux()[i]);
            float anomaly = MirrorAtmosphereClient.intensityOf(field.anomaly()[i]);
            double floor = player.getY() - 0.5;
            if (random.nextFloat() < (flux - 0.15f) * 1.35f) {
                boolean veil = random.nextFloat() < 0.25f;
                double y = veil ? floor + 4.0 + random.nextDouble() * 6.0 : floor + random.nextDouble() * (1.0 + flux * 2.5);
                add(level, FieldParticle.create(level, x, y, z, (random.nextDouble() - 0.5) * 0.01, 0.0,
                        (random.nextDouble() - 0.5) * 0.01, Motion.DRIFT, Look.SOFT, veil ? FLUX_DEEP : FLUX,
                        (0.05f + flux * 0.08f) * view.alpha(), veil ? 2.4f + random.nextFloat() * 1.6f : 1.2f + random.nextFloat() * 1.3f,
                        90 + random.nextInt(60)));
                if (random.nextFloat() < flux * 1.5f) {
                    add(level, FieldParticle.create(level, x, floor + random.nextDouble() * 4.0, z, 0, 0.003, 0, Motion.DRIFT,
                            Look.GLOW, FLUX_CORE, 0.6f * view.alpha(), 0.04f, 50 + random.nextInt(40)));
                }
            }
            if (random.nextFloat() < (anomaly - 0.15f) * 1.1f) {
                ink(level, random, view, x, floor + random.nextDouble() * (0.5 + anomaly * 2.0), z,
                        (random.nextDouble() - 0.5) * 0.008, -0.002, (random.nextDouble() - 0.5) * 0.008, 0.9f + anomaly * 1.4f);
                if (random.nextFloat() < anomaly) {
                    add(level, FieldParticle.create(level, x, floor + 0.3 + random.nextDouble() * 3.0, z, 0, 0, 0, Motion.JITTER,
                            Look.GLOW, random.nextBoolean() ? VIOLET : CRYSTAL, 0.85f * view.alpha(), 0.05f, 30 + random.nextInt(30)));
                }
            }
        }
    }

    /**
     * From High flux, sparks winking into existence on the floors round the player and fading away,
     * as if the ground itself were charged. More, and brighter, the hotter the chunk.
     */
    private static void sparkles(ClientLevel level, LocalPlayer player, RandomSource random, View view,
                                 FieldSurveyPayload field, int budget) {
        int attempts = Math.round(3 * view.density());
        for (int attempt = 0; attempt < attempts && spawnedThisTick < budget; attempt++) {
            int x = Mth.floor(player.getX() + (random.nextDouble() - 0.5) * 28.0);
            int z = Mth.floor(player.getZ() + (random.nextDouble() - 0.5) * 28.0);
            int dx = (x >> 4) - field.centreX();
            int dz = (z >> 4) - field.centreZ();
            if (Math.abs(dx) > field.radius() || Math.abs(dz) > field.radius()) continue;
            float flux = MirrorAtmosphereClient.intensityOf(field.flux()[field.index(dx, dz)]);
            if (random.nextFloat() >= (flux - 0.4f) * 2.4f) continue;
            BlockPos ground = ground(level, player, x, z);
            if (ground == null) continue;
            add(level, FieldParticle.create(level, ground.getX() + random.nextDouble(), ground.getY() + 0.03,
                    ground.getZ() + random.nextDouble(), 0, 0, 0, Motion.STILL, Look.GLOW,
                    flux > 0.75f && random.nextBoolean() ? FLUX_WHITE : FLUX_CORE, 0.9f * Math.max(0.6f, view.alpha()),
                    0.03f + random.nextFloat() * 0.04f, 12 + random.nextInt(18)));
        }
    }

    /** A wisp of anomaly: dark, dense, and slow to go. */
    private static void ink(ClientLevel level, RandomSource random, View view, double x, double y, double z,
                            double dx, double dy, double dz, float size) {
        add(level, FieldParticle.create(level, x, y, z, dx, dy, dz, Motion.DRIFT, Look.SOFT,
                random.nextBoolean() ? INK : INK_DEEP, (0.3f + random.nextFloat() * 0.2f) * Math.min(1.0f, view.alpha() * 1.6f),
                size, 80 + random.nextInt(60)));
    }

    private static void crack(ClientLevel level, RandomSource random, View view, Vec3 middle) {
        Vec3 direction = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
        double length = 0.8 + random.nextDouble() * 1.2;
        int life = 6 + random.nextInt(6);
        for (int i = 0; i <= 10; i++) {
            Vec3 at = middle.add(direction.scale((i / 10.0 - 0.5) * length));
            add(level, FieldParticle.create(level, at.x, at.y, at.z, 0, 0, 0, Motion.FLICKER, Look.GLOW, CRACK,
                    0.95f * view.alpha(), 0.04f, life));
        }
        ink(level, random, view, middle.x, middle.y, middle.z, 0, -0.002, 0, 1.4f);
    }

    /** Specks and ink falling in on a point from every side, the wrong way up. */
    private static void vortex(ClientLevel level, RandomSource random, View view, Vec3 centre) {
        for (int i = 0; i < 14; i++) {
            Vec3 from = centre.add((random.nextDouble() - 0.5) * 4.0, -0.5 - random.nextDouble() * 2.0,
                    (random.nextDouble() - 0.5) * 4.0);
            boolean dark = random.nextFloat() < 0.4f;
            FieldParticle speck = FieldParticle.create(level, from.x, from.y, from.z, 0, 0, 0, Motion.PULL,
                    dark ? Look.SOFT : Look.GLOW, dark ? INK_DEEP : (random.nextBoolean() ? VIOLET : CRYSTAL),
                    (dark ? 0.45f : 0.85f) * view.alpha(), dark ? 0.7f : 0.06f, 40);
            if (speck != null) add(level, speck.target(centre, 0.8));
        }
    }

    // ---- feeders and drains ----

    /**
     * A feeder exhales what it puts out: faster, higher and thicker the more it emits (on a log scale,
     * so a furnace simulator shows as a wisp and a generator as a fountain), azure for its flux with
     * dark violet ink for its share of anomaly. An anomaly-only source (a rift, a crystal) pours ink.
     * A drain pulls ink and specks in round it along spirals.
     */
    private static void source(ClientLevel level, RandomSource random, View view, FluxSources.Source source, double weight,
                               int budget) {
        double amount = source.flux() + source.anomaly();
        if (amount <= 0.0 || spawnedThisTick >= budget) return;
        double scale = Math.log10(1.0 + amount);
        double rate = Mth.clamp(0.3 + scale * 0.7, 0.2, 3.5) * weight * view.density();
        int specks = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
        double violetShare = source.anomaly() / amount;
        Vec3 centre = Vec3.atCenterOf(source.pos());
        for (int i = 0; i < specks && spawnedThisTick < budget; i++) {
            boolean violet = random.nextDouble() < violetShare;
            if (source.sink()) {
                Vec3 direction = new Vec3(random.nextDouble() - 0.5, random.nextDouble() * 0.5 - 0.1, random.nextDouble() - 0.5)
                        .normalize();
                Vec3 from = centre.add(direction.scale(3.0 + random.nextDouble() * (4.0 + scale * 2.0)));
                boolean dark = violet || random.nextFloat() < 0.5f;
                FieldParticle speck = FieldParticle.create(level, from.x, from.y, from.z, 0, 0, 0, Motion.PULL,
                        dark ? Look.SOFT : Look.GLOW, dark ? (random.nextBoolean() ? INK : INK_DEEP) : FLUX_CORE,
                        (dark ? 0.45f : 0.8f) * Math.min(1.0f, view.alpha() * 1.5f), dark ? 0.6f + random.nextFloat() * 0.6f : 0.05f, 70);
                if (speck != null) add(level, speck.target(centre, 0.9));
            } else if (source.flux() <= 0.0f) {
                // Anomaly only, from a tear or a crystal: ink welling out a little way and drifting off,
                // sinking as it goes. A little less often than exhaust, and a crystal half as often again,
                // so it broods rather than smokes.
                if (random.nextFloat() < 0.4f) continue;
                if (amount < CRYSTAL_SIZED && random.nextFloat() < 0.5f) continue;
                double angle = random.nextDouble() * Mth.TWO_PI;
                double out = 0.3 + random.nextDouble() * 0.6;
                double drift = 0.015 + Math.min(0.03, scale * 0.015);
                // From the whole height of a tear, not just its middle: a rift's source is big, a crystal's
                // small, so a crystal's ink stays close. Each wisp drifts up or down a little as well.
                double height = 0.4 + Math.min(1.0, scale * 0.6);
                ink(level, random, view, centre.x + Math.cos(angle) * out, centre.y + (random.nextDouble() * 2.0 - 1.0) * height,
                        centre.z + Math.sin(angle) * out, Math.cos(angle) * drift, (random.nextDouble() - 0.6) * 0.025,
                        Math.sin(angle) * drift, 0.6f + (float) scale * 0.5f);
                if (random.nextFloat() < 0.4f) {
                    add(level, FieldParticle.create(level, centre.x, centre.y, centre.z, 0, 0, 0, Motion.JITTER, Look.GLOW,
                            CRYSTAL, 0.9f * view.alpha(), 0.05f, 20 + random.nextInt(20)));
                }
            } else {
                double rise = 0.05 + scale * 0.03;
                double x = centre.x + (random.nextDouble() - 0.5) * 0.3;
                double z = centre.z + (random.nextDouble() - 0.5) * 0.3;
                double y = source.pos().getY() + 1.0;
                if (violet) {
                    // A small, faint trail beside the exhaust, so the flux still shows through it: two puffs
                    // for each share of anomaly.
                    for (int puff = 0; puff < 2; puff++) {
                        add(level, FieldParticle.create(level, x + (random.nextDouble() - 0.5) * 0.4, y,
                                z + (random.nextDouble() - 0.5) * 0.4, 0, rise * 0.5, 0, Motion.DRIFT, Look.SOFT,
                                random.nextBoolean() ? INK : INK_DEEP, 0.22f * Math.min(1.0f, view.alpha() * 1.6f),
                                0.25f + (float) scale * 0.08f, 40 + random.nextInt(20)));
                    }
                } else {
                    add(level, FieldParticle.create(level, x, y, z, 0, rise, 0, Motion.RISE, Look.GLOW,
                            random.nextBoolean() ? FLUX : FLUX_CORE, 0.8f * view.alpha(), 0.05f + (float) scale * 0.02f,
                            30 + random.nextInt(15)));
                    if (random.nextFloat() < 0.35f) {
                        add(level, FieldParticle.create(level, x, y, z, 0, rise * 0.8, 0, Motion.RISE, Look.SOFT, FLUX,
                                0.2f * view.alpha(), 0.3f + (float) scale * 0.15f, 40), Double.MAX_VALUE, 0.8f + (float) scale * 0.4f);
                    }
                }
            }
        }
    }

    // ---- helpers ----

    private static void add(ClientLevel level, @Nullable FieldParticle particle) {
        add(level, particle, Double.MAX_VALUE, 0.0f);
    }

    private static void add(ClientLevel level, @Nullable FieldParticle particle, double ceiling, float bloomSize) {
        if (particle == null) return;
        if (ceiling < Double.MAX_VALUE) particle.ceiling(ceiling);
        if (bloomSize > 0.0f) particle.bloom(bloomSize);
        Minecraft.getInstance().particleEngine.add(particle);
        spawnedThisTick++;
    }

    /** 1 near the player, easing down to 0 between {@link #FADE_START} and {@link #FADE_END} blocks out. */
    private static double fade(LocalPlayer player, double x, double z) {
        return fade(player, x, z, FADE_START, FADE_END);
    }

    private static double fade(LocalPlayer player, double x, double z, double start, double end) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= start) return 1.0;
        if (distance >= end) return 0.0;
        double t = (distance - start) / (end - start);
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }

    /** A floor near the player's height at (x, z): a sturdy top with room above it. */
    @Nullable
    private static BlockPos ground(ClientLevel level, LocalPlayer player, int x, int z) {
        int start = player.getBlockY() + 6;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, start, z);
        for (int y = start; y > start - 20; y--) {
            cursor.setY(y - 1);
            if (!level.isLoaded(cursor)) return null;
            if (level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.UP)) {
                cursor.setY(y);
                if (level.getBlockState(cursor).isAir()) return cursor.immutable();
            }
        }
        return null;
    }

    /** Specks a tick at most, by the particle setting and the config's density. */
    private static int budget(Minecraft minecraft) {
        ParticleStatus status = minecraft.options.particles().get();
        float setting = status == ParticleStatus.ALL ? 1.0f : status == ParticleStatus.DECREASED ? 0.5f : 0.15f;
        return Math.max(1, Math.round(BUDGET * setting * (float) Config.fieldVisionDensity()));
    }
}
