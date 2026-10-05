package com.kadikular.quantimium.client.particle;

import com.kadikular.quantimium.client.renderer.QuantumRenderTypes;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.RandomSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * One speck of the field made visible, full bright, fading in and out over its life. It is either a
 * {@link Look#GLOW} spark or a {@link Look#SOFT} wisp, and moves one of a few ways:
 * <ul>
 *   <li>{@link Motion#RISE}: up, slowing, as in a plume or a machine's exhaust. A blooming one spreads
 *   and swells once it has slowed, like the head of a column; under a containment ceiling it flattens
 *   and spreads instead of rising past it.
 *   <li>{@link Motion#DRIFT}: a slow wander from its starting drift, haze and ink in the air.
 *   <li>{@link Motion#JITTER}: still, but snapping a little way every few ticks: anomaly.
 *   <li>{@link Motion#PULL}: drawn in to a point, faster as it closes, spiralling if given a swirl.
 *   <li>{@link Motion#FLICKER}: still, blinking: a crack in the air.
 *   <li>{@link Motion#STILL}: where it was put, just fading in and out: a spark on the floor.
 * </ul>
 */
public class FieldParticle extends SingleQuadParticle {

    public enum Motion { RISE, DRIFT, JITTER, PULL, FLICKER, STILL }

    /**
     * Soft wisps blend without writing depth. Written, the clear edge of a wisp in front hides every
     * wisp drawn after it behind that edge, and the rift behind them all: clouds read as cut-outs.
     * Solid blocks still hide them, since depth is still tested.
     */
    public static final SingleQuadParticle.Layer SOFT_LAYER =
            new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, QuantumRenderTypes.SOFT_PARTICLE);

    public enum Look { GLOW, SOFT }

    @Nullable
    private static SpriteSet glowSprites;
    @Nullable
    private static SpriteSet softSprites;

    private final Motion motion;
    private final Look look;
    private final float peakAlpha;
    private double ceiling = Double.MAX_VALUE;
    private boolean bloom;
    /** A blooming speck starts to spread once it has slowed to this: 30% of its launch speed. */
    private double bloomBelow = 0.05;
    private float maxSize;
    private double swirl;
    @Nullable
    private Vec3 target;

    private FieldParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                          Motion motion, Look look, float r, float g, float b, float alpha, float size, int lifetime) {
        super(level, x, y, z, sprites(look).first());
        this.motion = motion;
        this.look = look;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.peakAlpha = alpha;
        this.alpha = 0.0f;
        this.quadSize = size;
        this.maxSize = size;
        this.lifetime = lifetime;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = motion == Motion.RISE ? 0.96f : 0.985f;
        pickSprite();
    }

    /** A new speck, or null before the sprites are loaded. Add it with the particle engine. */
    @Nullable
    public static FieldParticle create(ClientLevel level, double x, double y, double z, double dx, double dy,
                                       double dz, Motion motion, Look look, int rgb, float alpha, float size,
                                       int lifetime) {
        if ((look == Look.GLOW ? glowSprites : softSprites) == null) return null;
        return new FieldParticle(level, x, y, z, dx, dy, dz, motion, look, ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, alpha, size, lifetime);
    }

    /** Rising specks stop here and spread sideways, as a plume does under a containment field. */
    public FieldParticle ceiling(double y) {
        this.ceiling = y;
        return this;
    }

    /** Once slowed, spread out and swell to {@code grownSize}: the head of a column blooming. */
    public FieldParticle bloom(float grownSize) {
        this.bloom = true;
        this.bloomBelow = Math.max(0.03, this.yd * 0.3);
        this.maxSize = Math.max(quadSize, grownSize);
        return this;
    }

    /** Where a pulled speck is drawn to, and how hard it circles on the way (0 for straight in). */
    public FieldParticle target(Vec3 target, double swirl) {
        this.target = target;
        this.swirl = swirl;
        return this;
    }

    private static SpriteSet sprites(Look look) {
        return java.util.Objects.requireNonNull(look == Look.GLOW ? glowSprites : softSprites, "field sprites not loaded");
    }

    private void pickSprite() {
        setSpriteFromAge(sprites(look));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }
        if (look == Look.GLOW) pickSprite();
        float life = (float) this.age / this.lifetime;
        // In quickly, out slowly.
        float fade = life < 0.12f ? life / 0.12f : 1.0f - (life - 0.12f) / 0.88f;
        this.alpha = peakAlpha * Mth.clamp(fade, 0.0f, 1.0f);

        switch (motion) {
            case RISE -> {
                if (this.y >= ceiling && this.yd > 0.0) {
                    // Flattened against the field: the rise turns into a spread along its underside.
                    double spread = 0.03 + Math.abs(this.yd) * 0.5;
                    double angle = this.random.nextDouble() * Math.PI * 2.0;
                    this.xd += Math.cos(angle) * spread;
                    this.zd += Math.sin(angle) * spread;
                    this.yd = 0.0;
                    this.y = ceiling;
                    this.bloom = true;
                }
                if (bloom && this.yd < bloomBelow) {
                    this.xd += (this.random.nextDouble() - 0.5) * 0.012;
                    this.zd += (this.random.nextDouble() - 0.5) * 0.012;
                    this.quadSize = Math.min(maxSize, this.quadSize * 1.04f);
                }
                move(this.xd, this.yd, this.zd);
                this.xd *= this.friction;
                this.yd *= this.friction;
                this.zd *= this.friction;
            }
            case DRIFT -> {
                this.xd += (this.random.nextDouble() - 0.5) * 0.0015;
                this.yd += (this.random.nextDouble() - 0.5) * 0.0008;
                this.zd += (this.random.nextDouble() - 0.5) * 0.0015;
                move(this.xd, this.yd, this.zd);
                this.xd *= this.friction;
                this.yd *= this.friction;
                this.zd *= this.friction;
            }
            case JITTER -> {
                if (this.random.nextInt(4) == 0) {
                    setPos(this.x + (this.random.nextDouble() - 0.5) * 0.5, this.y + (this.random.nextDouble() - 0.5) * 0.4,
                            this.z + (this.random.nextDouble() - 0.5) * 0.5);
                    this.xo = this.x;
                    this.yo = this.y;
                    this.zo = this.z;
                }
            }
            case PULL -> {
                if (target == null) break;
                Vec3 to = target.subtract(this.x, this.y, this.z);
                double distance = to.length();
                if (distance < 0.2) {
                    remove();
                    return;
                }
                double speed = Math.min(distance, 0.03 + 0.35 * life * life);
                Vec3 in = to.scale(1.0 / distance);
                // Round the vertical axis, fading out as it closes so it lands rather than orbits.
                Vec3 around = new Vec3(-in.z, 0.0, in.x).scale(swirl * Math.min(1.0, distance / 3.0));
                Vec3 step = in.add(around).normalize().scale(speed);
                move(step.x, step.y, step.z);
                // Swallowed: it thins as it nears the mouth.
                if (distance < 1.5) this.alpha *= (float) (distance / 1.5);
            }
            case FLICKER -> this.alpha = this.random.nextInt(3) == 0 ? 0.0f : this.alpha;
            // Fades in and out evenly: a spark coming and going, not a puff.
            case STILL -> this.alpha = peakAlpha * Mth.sin(life * Mth.PI);
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return look == Look.SOFT ? SOFT_LAYER : SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    /** Registered for {@code field_glow} and {@code field_soft}; keeps their sprites for {@link #create}. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        private final Look look;

        private Provider(Look look) {
            this.look = look;
        }

        public static Provider glow(SpriteSet sprites) {
            glowSprites = sprites;
            return new Provider(Look.GLOW);
        }

        public static Provider soft(SpriteSet sprites) {
            softSprites = sprites;
            return new Provider(Look.SOFT);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz, RandomSource random) {
            return new FieldParticle(level, x, y, z, dx, dy, dz, Motion.DRIFT, look, 0.2f, 0.52f, 1.0f, 0.8f,
                    look == Look.SOFT ? 0.5f : 0.05f, 40);
        }
    }
}
