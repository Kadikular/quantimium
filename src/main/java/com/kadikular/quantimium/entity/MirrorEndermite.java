package com.kadikular.quantimium.entity;

import net.minecraft.core.HolderLookup;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Endermite stand-in that only exists for Mirror Phase observers. Same size, texture, and sounds;
 * targeting, tracking, and collision are gated on {@link MirrorPhase}.
 */
public class MirrorEndermite extends Monster {

    private static final double OBSERVER_RANGE_SQUARED = 48.0 * 48.0;
    /**
     * Came through a flux rift into the real world. A breached mite is real to everyone — seen,
     * heard, hunted by and hunting players in either realm — until its rift closes and pulls it back.
     */
    private static final EntityDataAccessor<Boolean> BREACHED =
            SynchedEntityData.defineId(MirrorEndermite.class, EntityDataSerializers.BOOLEAN);
    /** How long draining a rift keeps a mite on the drainer after the last refresh. */
    private static final long PROVOKE_TICKS = 100L;

    /** Game time until which this mite hunts whoever provoked it, however calm the field. Not saved. */
    private long provokedUntil;

    public MirrorEndermite(EntityType<? extends MirrorEndermite> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BREACHED, false);
    }

    public boolean isBreached() {
        return this.entityData.get(BREACHED);
    }

    /** Set before the mite is added to the level: tracking decides who sees it as it joins. */
    public void setBreached(boolean breached) {
        this.entityData.set(BREACHED, breached);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag);
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag) {
        if (isBreached()) tag.putBoolean("Breached", true);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        loadLegacy(NbtCompat.read(in));
    }

    private void loadLegacy(CompoundTag tag) {
        setBreached(tag.getBooleanOr("Breached", false));
    }

    /**
     * Turn on {@code player}: someone is draining the rift. Overrides the calm-field passivity for a
     * few seconds, refreshed for as long as the draining goes on.
     */
    public void provoke(Player player) {
        this.setTarget(player);
        this.provokedUntil = this.level().getGameTime() + PROVOKE_TICKS;
    }

    private boolean provoked() {
        return this.level().getGameTime() < provokedUntil;
    }

    /** Whether this mite exists for {@code player}: always once breached, otherwise only in the mirror. */
    public boolean realTo(Player player) {
        return isBreached() || MirrorPhase.isPhased(player);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        // Hunting is gated on anomaly, not on flux: flux says how busy the mirror is, anomaly says
        // how hostile. Checked in canUse so a calm chunk costs one band lookup per scan instead of
        // one per candidate player. Retaliation is left alone above, so a mite you hit always fights
        // back — passive is meant to read as indifference, not as a punching bag.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true,
                (living, targetLevel) -> living instanceof Player player && realTo(player)) {
            @Override
            public boolean canUse() {
                return aggressive() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return aggressive() && super.canContinueToUse();
            }
        });
    }

    /**
     * True where the local anomaly is Medium or worse and not contained. Below that, or where
     * containment holds the field, the mites watch but ignore the living. A breached mite came
     * through a wound and is always hostile.
     */
    private boolean aggressive() {
        return isBreached()
                || !QuantumFlux.chunkContained(this.level(), this.blockPosition())
                && !FluxBand.LOW.covers(QuantumFlux.chunkAnomalyBand(this.level(), this.blockPosition()));
    }

    @Override
    public void tick() {
        this.yBodyRot = this.getYRot();
        super.tick();
    }

    @Override
    public void setYBodyRot(float offset) {
        this.setYRot(offset);
        super.setYBodyRot(offset);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.tickCount % 20 != 0) return;
        if (FluxRiftManager.orphaned(level, this)) {
            FluxRiftManager.recall(level, this);
            return;
        }
        refreshAnomalyEffects();
        // Suppressing anomaly should call off a hunt already in progress, not just prevent the next
        // one. Anything we are chasing because it hit us stays a target.
        if (!aggressive() && !provoked() && this.getTarget() != null
                && this.getTarget() != this.getLastHurtByMob()) {
            this.setTarget(null);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) return;
        for (int i = 0; i < 2; i++) {
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5),
                    (this.random.nextDouble() - 0.5) * 2.0,
                    -this.random.nextDouble(),
                    (this.random.nextDouble() - 0.5) * 2.0);
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (this.level().isClientSide() || this.isSilent() || sound == null || isBreached()) {
            super.playSound(sound, volume, pitch);
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        double range = sound.getRange(volume);
        double rangeSq = range * range;
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (!MirrorPhase.isPhased(player)) continue;
            if (player.distanceToSqr(this.getX(), this.getY(), this.getZ()) > rangeSq) continue;
            player.connection.send(new ClientboundSoundPacket(
                    holder, this.getSoundSource(), this.getX(), this.getY(), this.getZ(),
                    volume, pitch, seed));
        }
    }

    @Override
    public void checkDespawn() {
        // A breached mite, or any mite a rift spat out, stays until its rift pulls it back, observed or
        // not: otherwise a rift left to a Projector would have nothing to throw at it.
        if (!isBreached() && !FluxRiftManager.spawnedByRift(this) && !hasPhasedObserver()) {
            this.discard();
            return;
        }
        super.checkDespawn();
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return realTo(player);
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return !realTo(player);
    }

    @Override
    public boolean isPushable() {
        // Unphased players still occupy the same AABB on the server; staying unpushable
        // keeps them from getting shoved by a mob they cannot see.
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        if (entity instanceof Player player && !realTo(player)) return false;
        return super.canCollideWith(entity);
    }

    @Override
    public void push(Entity entity) {
        if (entity instanceof Player player && !realTo(player)) return;
        super.push(entity);
    }

    @Override
    protected void doPush(Entity entity) {
        if (entity instanceof Player player && !realTo(player)) return;
        super.doPush(entity);
    }

    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return realTo(player);
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    /** Only its loot table: a Trace, sometimes, for a player's kill (in the mirror, the killer's alone). */
    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMITE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.ENDERMITE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMITE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        this.playSound(SoundEvents.ENDERMITE_STEP, 0.15F, 1.0F);
    }

    /** High = Strength / Speed I, Critical = II, Singularity = III. Particles and icon stay off. */
    private void refreshAnomalyEffects() {
        int amplifier = QuantumFlux.chunkContained(this.level(), this.blockPosition()) ? -1
                : anomalyAmplifier(QuantumFlux.chunkAnomalyBand(this.level(), this.blockPosition()));
        if (amplifier < 0) {
            this.removeEffect(MobEffects.STRENGTH);
            this.removeEffect(MobEffects.SPEED);
            return;
        }
        applyHiddenEffect(MobEffects.STRENGTH, amplifier);
        applyHiddenEffect(MobEffects.SPEED, amplifier);
    }

    private void applyHiddenEffect(Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = this.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > 40) return;
        this.addEffect(new MobEffectInstance(effect, 200, amplifier, true, false, false));
    }

    private static int anomalyAmplifier(FluxBand anomaly) {
        return switch (anomaly) {
            case HIGH -> 0;
            case CRITICAL -> 1;
            case SINGULARITY -> 2;
            default -> -1;
        };
    }

    private boolean hasPhasedObserver() {
        for (Player player : this.level().players()) {
            if (MirrorPhase.isPhased(player) && this.distanceToSqr(player) <= OBSERVER_RANGE_SQUARED) {
                return true;
            }
        }
        return false;
    }
}
