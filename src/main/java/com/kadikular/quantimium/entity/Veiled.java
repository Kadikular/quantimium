package com.kadikular.quantimium.entity;

import com.kadikular.quantimium.util.LegacyEnergy;
import net.minecraft.core.HolderLookup;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumEnergyHost;
import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.flux.AnomaliteEnergy;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModDamageTypes;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.item.DecoherenceLanceItem;
import com.kadikular.quantimium.item.MirrorLensItem;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.MirrorPhase;
import com.kadikular.quantimium.phase.VeiledManager;
import com.kadikular.quantimium.superposition.Recovery;
import com.kadikular.quantimium.superposition.SophonRegistry;
import com.kadikular.quantimium.superposition.Superposition;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The Veiled (placeholder name), in its Veiled form. Design: {@code VEILED_BRIEF.html}.
 *
 * <p>This entity is its <em>true position</em>, and it exists only in the mirror: phased players see,
 * hear and collide with it, nobody else does. The real world only ever gets personal sightings,
 * drawn on each player's client by {@link VeiledManager}; every sighting that ends steps this
 * body closer to what it wants. It cannot be killed. It can be driven off with the Decoherence Lance,
 * and pinned long enough beside a powered four-arm Containment Hall it is held there.
 */
public class Veiled extends PathfinderMob {

    public enum State { WANDER, PRESENCE, SHADOWING, AGGRAVATED }

    /** Only one per area; also how far a player keeps a free one alive. */
    public static final double AREA = 256.0;
    /** Mirror curiosity walks it in to about here, and no closer. */
    private static final double CURIOUS_STOP = 10.0;
    /** Closer than this and it backs off like a cat. */
    private static final double WARY = 8.0;
    /** Backed into this with nowhere to go, it strikes. */
    private static final double CORNERED = 3.5;
    /** How far one ended sighting moves its true position. */
    private static final double STEP = 10.0;
    /** Near enough to its target to start feeding. */
    private static final double ARRIVED = 14.0;
    /** Walking towards something and getting no nearer for this long, it stops walking and blinks. */
    private static final int STUCK_TICKS = 60;
    /** How long it has to spend at an unguarded double to take it. */
    public static final int TAKE_TICKS = 300;
    /** Machines within this of a player count as the player's base. */
    private static final int BASE_RADIUS = 32;
    private static final int MACHINE_FE_PER_TICK = 80;
    private static final int GEAR_FE_PER_TICK = 40;
    /** Total Lance contact, in ticks, that ends an aggravated spell. Need not be unbroken. */
    private static final int PIN_TICKS = 160;
    /** A beam this recent still holds it frozen. */
    private static final long BEAM_GRACE = 2L;
    /** Without a beam for this long, it loses interest. */
    private static final long LOSE_INTEREST = 600L;
    private static final double INTEREST_RANGE = 48.0;
    /** A 70° cone around where a player looks: outside it, and it is veiled. */
    private static final double VEILED_COS = Math.cos(Math.toRadians(35.0));
    /** A crosshair within about 10° of it counts as staring. */
    private static final double STARE_COS = Math.cos(Math.toRadians(10.0));

    /** 1 untouched, 0 fully pinned. Synced so the body can fade as the Lance wears it down. */
    private static final EntityDataAccessor<Float> COHERENCE =
            SynchedEntityData.defineId(Veiled.class, EntityDataSerializers.FLOAT);
    /** Once it has noticed someone staring, it keeps its attention on them for this long. */
    private static final long INTEREST_TICKS = 240L;
    /** Pin progress this far along and a hall within reach takes hold of it. */
    private static final float HALL_PULL_AT = 0.5f;
    /** The furthest any hall can reach; each hall's own reach depends on its arms. */
    private static final double HALL_REACH = 32.0;
    /** The hall that has hold of it, if any. Synced so clients can draw the field beam. */
    private static final EntityDataAccessor<Optional<BlockPos>> CAPTURE_HALL =
            SynchedEntityData.defineId(Veiled.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** 0–1 progress of being drawn into the hall. Synced: it fades as this climbs. */
    private static final EntityDataAccessor<Float> CAPTURE =
            SynchedEntityData.defineId(Veiled.class, EntityDataSerializers.FLOAT);
    /** How many Sophons it carries. Synced so each can be drawn circling it. */
    private static final EntityDataAccessor<Integer> CARRIED =
            SynchedEntityData.defineId(Veiled.class, EntityDataSerializers.INT);

    private State state = State.WANDER;
    @Nullable
    private UUID interestIn;
    private long interestUntil;
    private long cooldownUntil;
    @Nullable
    private BlockPos targetMachine;
    @Nullable
    private UUID targetPlayer;
    @Nullable
    private UUID aggressor;
    private int pinTicks;
    private long lastBeamTick = Long.MIN_VALUE / 2;
    private long strikeReadyAt;
    private long nextRetarget;
    /** Last tick a Decoherence Projector had its beam on it. */
    private long projectorBeamTick = Long.MIN_VALUE / 2;
    /** An unguarded body double it is after, by its Sophon; it wants those more than any machine. */
    @Nullable
    private UUID targetDouble;
    /** Ticks it has spent at that double. */
    private int feeding;
    /** Sophons of the doubles it has taken, carried until it is driven off, held, or leaves. */
    private final List<UUID> carried = new ArrayList<>();
    /** Last tick the player it shadows had their eyes on it. */
    private long lastWatched;
    /** Shadowing, unwatched for this long, it goes back to a double it wants. */
    private static final long SHADOW_FORGETS = 600L;
    /** Nearest it has got to what it is walking to, and how long since it last got nearer. */
    private double approachBest = Double.MAX_VALUE;
    private int approachStuck;

    public Veiled(EntityType<? extends Veiled> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(COHERENCE, 1.0f);
        builder.define(CAPTURE_HALL, Optional.empty());
        builder.define(CAPTURE, 0.0f);
        builder.define(CARRIED, 0);
    }

    /** Where the field beam comes from while a hall is drawing it in. */
    public Optional<BlockPos> captureHall() {
        return this.entityData.get(CAPTURE_HALL);
    }

    public float captureProgress() {
        return this.entityData.get(CAPTURE);
    }

    private void startCapture(ContainmentHallBlockEntity hall) {
        if (captureHall().isPresent()) return;
        this.entityData.set(CAPTURE_HALL, Optional.of(hall.getBlockPos().immutable()));
        this.entityData.set(CAPTURE, 0.0f);
        this.getNavigation().stop();
        this.playSound(ModSounds.VEILED_STRIKE.get(), 1.0f, 0.6f);
    }

    private void abortCapture() {
        this.entityData.set(CAPTURE_HALL, Optional.empty());
        this.entityData.set(CAPTURE, 0.0f);
    }

    /**
     * Held in the field beam: frozen, fading, drawn along it into the cell. The hall's arms set how
     * fast. If the hall loses its power or its room part-way, the hold breaks and the fight goes on.
     */
    private void captureTick(ServerLevel level, BlockPos hallPos) {
        if (!(level.getBlockEntity(hallPos) instanceof ContainmentHallBlockEntity hall) || !hall.canHold()) {
            abortCapture();
            return;
        }
        this.getNavigation().stop();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        this.getLookControl().setLookAt(Vec3.atCenterOf(hallPos.above(2)));
        float progress = captureProgress() + 1.0f / hall.captureTicks();
        if (this.tickCount % 10 == 0) {
            // A charge building as it is drawn in, rising in pitch as the hold tightens.
            this.playSound(ModSounds.RIFT_DRAIN.get(), 0.9f, 0.6f + progress * 0.8f);
        }
        if (progress >= 1.0f) {
            VeiledManager.contain(level, this, hall);
            return;
        }
        this.entityData.set(CAPTURE, progress);
    }

    /** How much of it is left, for the fade: 1 untouched, near 0 about to be held or driven off. */
    public float coherence() {
        return this.entityData.get(COHERENCE);
    }

    private void syncCoherence() {
        float value = 1.0f - Math.min(1.0f, pinTicks / (float) PIN_TICKS);
        if (Math.abs(value - coherence()) >= 0.01f || value == 1.0f) this.entityData.set(COHERENCE, value);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new WaryGoal());
        this.goalSelector.addGoal(2, new CuriousGoal());
        this.goalSelector.addGoal(3, new HoldGazeGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return state == State.WANDER && super.canUse();
            }
        });
    }

    public State state() {
        return state;
    }

    // ---- The mirror-only body, as for mirror mites ----

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return MirrorPhase.isPhased(player);
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return !MirrorPhase.isPhased(player);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        if (entity instanceof Player player && !MirrorPhase.isPhased(player)) return false;
        return super.canCollideWith(entity);
    }

    @Override
    public void push(Entity entity) {
        if (entity instanceof Player player && !MirrorPhase.isPhased(player)) return;
        super.push(entity);
    }

    @Override
    protected void doPush(Entity entity) {
        if (entity instanceof Player player && !MirrorPhase.isPhased(player)) return;
        super.doPush(entity);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** It can be driven off or held, never killed. */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Heard only from the mirror, like everything else about it. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (this.level().isClientSide() || this.isSilent() || sound == null) {
            super.playSound(sound, volume, pitch);
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        double rangeSq = Mth.square(sound.getRange(volume));
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (!MirrorPhase.isPhased(player) || player.distanceToSqr(this) > rangeSq) continue;
            player.connection.send(new ClientboundSoundPacket(holder, this.getSoundSource(),
                    this.getX(), this.getY(), this.getZ(), volume, pitch, seed));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    /** Being drawn in: motes torn off its body stream along the beam into the cell. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide()) return;
        Optional<BlockPos> hold = captureHall();
        if (hold.isEmpty()) return;
        Vec3 mouth = Vec3.atCenterOf(hold.get().above(2));
        int count = 2 + Math.round(captureProgress() * 4.0f);
        for (int i = 0; i < count; i++) {
            Vec3 from = new Vec3(this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6));
            // Reverse-portal motes cover about 30.5 × their speed over their life, accelerating.
            Vec3 speed = mouth.subtract(from).scale(1.0 / 30.5);
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, speed.x, speed.y, speed.z);
        }
    }

    // ---- The state machine ----

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        long now = level.getGameTime();
        if (this.tickCount % 20 == 0 && level.getNearestPlayer(this, AREA) == null) {
            // Nobody to haunt: gone, and it remembers nothing, so leaving the area is a real escape.
            this.discard();
            return;
        }
        if (now - projectorBeamTick <= BEAM_GRACE) {
            // Held in a Projector's beam: frozen where it stands, and nothing else happens.
            this.getNavigation().stop();
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
            return;
        }
        if (state != State.AGGRAVATED && pinTicks > 0 && this.tickCount % 3 == 0) {
            // Left alone, it pulls itself back together, slowly.
            pinTicks--;
            syncCoherence();
        }
        Optional<BlockPos> hold = captureHall();
        if (hold.isPresent()) {
            captureTick(level, hold.get());
            return;
        }
        if (this.tickCount % 5 == 0) noticeLensWatchers(level);
        switch (state) {
            case AGGRAVATED -> aggravatedTick(level, now);
            case PRESENCE -> presenceTick(level, now);
            case SHADOWING -> shadowTick(level, now);
            case WANDER -> wanderTick(level, now);
        }
    }

    /** The player it is following while shadowing, if any. */
    @Nullable
    public UUID shadowTarget() {
        return state == State.SHADOWING ? targetPlayer : null;
    }

    public boolean onCooldown() {
        return this.level().getGameTime() < cooldownUntil;
    }

    private void wanderTick(ServerLevel level, long now) {
        if (now < nextRetarget) return;
        nextRetarget = now + 100;
        retarget(level);
        if (onCooldown()) return;
        // A double it knows about, it goes after however far off: walking, or blinking past what is in the way.
        Vec3 prey = doublePosition(level);
        if (prey != null) {
            state = State.PRESENCE;
        } else if (targetMachine != null && distanceToSqr(Vec3.atCenterOf(targetMachine)) < ARRIVED * ARRIVED) {
            state = State.PRESENCE;
        } else if (targetMachine == null && targetPlayer != null) {
            // Only in the real world: in the mirror it can be seen, and is curious or wary instead.
            Player player = level.getPlayerByUUID(targetPlayer);
            if (player != null && !MirrorPhase.isPhased(player) && distanceTo(player) < ARRIVED) state = State.SHADOWING;
        }
    }

    /** Nearest player in the area; their nearest running Quantimium machine, if they have a base. */
    private void retarget(ServerLevel level) {
        Player player = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), AREA,
                p -> !p.isSpectator() && !((Player) p).isCreative());
        targetPlayer = player == null ? null : player.getUUID();
        targetMachine = player == null ? null : nearestMachine(level, player.blockPosition(), BASE_RADIUS);
        // A body with nobody in it, and nothing guarding it: it wants that more than any machine.
        if (targetDouble == null) targetDouble = nearestUnguardedDouble(level, position(), AREA);
    }

    /** The nearest unguarded double within {@code range} of {@code from}, by its Sophon, or null. */
    @Nullable
    public static UUID nearestUnguardedDouble(ServerLevel level, Vec3 from, double range) {
        UUID best = null;
        double bestSq = range * range;
        for (SophonRegistry.Sophon sophon : SophonRegistry.get(level.getServer()).all()) {
            SophonRegistry.Entry entry = sophon.entry();
            if (!SophonRegistry.unguarded(entry) || !entry.pod().get().dimension().equals(level.dimension())) continue;
            double distanceSq = Vec3.atBottomCenterOf(entry.pod().get().pos()).distanceToSqr(from);
            if (distanceSq < bestSq) {
                best = sophon.id();
                bestSq = distanceSq;
            }
        }
        return best;
    }

    /** Sets it on {@code sophon}'s double, as if it had found it itself. */
    public void huntDouble(UUID sophon) {
        targetDouble = sophon;
        feeding = 0;
        state = State.PRESENCE;
    }

    public List<UUID> carried() {
        return List.copyOf(carried);
    }

    /** How many Sophons it carries, on either side. */
    public int carriedCount() {
        return this.entityData.get(CARRIED);
    }

    private void syncCarried() {
        this.entityData.set(CARRIED, carried.size());
    }

    @Nullable
    private static BlockPos nearestMachine(ServerLevel level, BlockPos centre, int radius) {
        BlockPos best = null;
        double bestSq = (double) radius * radius;
        int chunkRadius = SectionPos.blockToSectionCoord(radius) + 1;
        int cx = SectionPos.blockToSectionCoord(centre.getX());
        int cz = SectionPos.blockToSectionCoord(centre.getZ());
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof QuantumEnergyHost)) continue;
                    double distanceSq = be.getBlockPos().distSqr(centre);
                    if (distanceSq < bestSq) {
                        best = be.getBlockPos();
                        bestSq = distanceSq;
                    }
                }
            }
        }
        return best;
    }

    /**
     * One ended sighting: the true position steps towards what it wants, stopping short of it.
     * Called by {@link VeiledManager} for any player's sighting, so a group draws it in faster.
     */
    public void advance() {
        if (state != State.WANDER || onCooldown()) return;
        ServerLevel level = (ServerLevel) this.level();
        retarget(level);
        Vec3 prey = doublePosition(level);
        Vec3 goal = prey != null ? prey : targetMachine != null ? Vec3.atCenterOf(targetMachine)
                : targetPlayer != null && level.getPlayerByUUID(targetPlayer) != null
                ? level.getPlayerByUUID(targetPlayer).position() : null;
        if (goal == null) return;
        Vec3 offset = goal.subtract(position());
        double distance = offset.horizontalDistance();
        double step = Math.min(STEP, distance - ARRIVED * 0.8);
        if (step <= 0.5) return;
        Vec3 flat = new Vec3(offset.x, 0.0, offset.z).normalize();
        Vec3 to = position().add(flat.scale(step));
        if (!blinkTo(level, to.x, to.z, 4)) return;
        nextRetarget = 0L;
    }

    private void presenceTick(ServerLevel level, long now) {
        if (targetDouble != null) {
            doubleTick(level, now);
            return;
        }
        if (targetMachine == null || !(level.getBlockEntity(targetMachine) instanceof QuantumEnergyHost)) {
            state = State.WANDER;
            return;
        }
        Vec3 machine = Vec3.atCenterOf(targetMachine);
        if (distanceToSqr(machine) > 9.0) {
            approach(level, machine);
            if (distanceToSqr(machine) > 40.0 * 40.0) state = State.WANDER;
            return;
        }
        resetApproach();
        this.getNavigation().stop();
        this.getLookControl().setLookAt(machine);
        AnomaliteEnergy.Access access = AnomaliteEnergy.find(level, targetMachine);
        if (access != null) access.extractFe(MACHINE_FE_PER_TICK);
        if (now % 20 == 0) {
            // The real world's only tell: the machine it has chosen stutters with violet motes.
            showEveryone(level, ParticleTypes.REVERSE_PORTAL, machine.add(0.0, 0.3, 0.0), 6, 0.35);
            QuantumFlux.emit(level, targetMachine, 0.0, 2.0);
        }
    }

    /** Where the double it is after stands, if it is still there to be taken. */
    @Nullable
    private Vec3 doublePosition(ServerLevel level) {
        if (targetDouble == null) return null;
        SophonRegistry.Entry entry = SophonRegistry.get(level.getServer()).entry(targetDouble);
        if (entry == null || !SophonRegistry.unguarded(entry) || !entry.pod().get().dimension().equals(level.dimension())) {
            targetDouble = null;
            feeding = 0;
            return null;
        }
        return Vec3.atBottomCenterOf(entry.pod().get().pos());
    }

    /**
     * At an unguarded double: it settles beside it, and violet pours off the body where everyone can see
     * it, until after {@value #TAKE_TICKS} ticks the body is gone and it carries the Sophon away.
     */
    private void doubleTick(ServerLevel level, long now) {
        Vec3 prey = doublePosition(level);
        if (prey == null) {
            state = State.WANDER;
            return;
        }
        // Watched from the mirror, it attends to the watcher and does nothing else; the double keeps.
        if (interest() != null) return;
        // A double in a pod is up on the cradle, behind glass, so near enough is measured across the ground.
        // Anything that keeps it further off than that, it blinks past (see approach).
        double across = Math.sqrt(Mth.square(prey.x - getX()) + Mth.square(prey.z - getZ()));
        boolean arrived = across <= 4.0 && Math.abs(prey.y - getY()) < 4.0;
        if (!arrived) {
            approach(level, prey);
            return;
        }
        resetApproach();
        this.getNavigation().stop();
        this.getLookControl().setLookAt(prey.add(0.0, 1.0, 0.0));
        feeding++;
        if (now % 20 == 0) {
            showEveryone(level, ParticleTypes.REVERSE_PORTAL, prey.add(0.0, 1.0, 0.0), 14, 0.4);
            SophonRegistry.Entry entry = SophonRegistry.get(level.getServer()).entry(targetDouble);
            ServerPlayer owner = entry == null ? null : level.getServer().getPlayerList().getPlayer(entry.owner());
            if (owner != null && feeding % 100 < 20) {
                owner.sendOverlayMessage(Component.translatable("message.quantimium.veiled.taking",
                        (int) prey.x, (int) prey.z).withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        if (feeding >= TAKE_TICKS) take(level, targetDouble);
    }

    /**
     * Walks towards {@code goal}. Getting no nearer for {@value #STUCK_TICKS} ticks (a wall, a door, a
     * sealed room), it does not look for a way round: it blinks in beside it. Walls do not hold it. It
     * never blinks in close to someone in the mirror, only walks on.
     */
    private void approach(ServerLevel level, Vec3 goal) {
        double distance = distanceToSqr(goal);
        if (distance < approachBest - 0.25) {
            approachBest = distance;
            approachStuck = 0;
        } else {
            approachStuck++;
        }
        if (this.tickCount % 10 == 0) this.getNavigation().moveTo(goal.x, goal.y, goal.z, 0.8);
        if (approachStuck < STUCK_TICKS) return;
        resetApproach();
        Vec3 before = position();
        // Every spot 2–3 blocks off it, in a random order. In a cramped room the walls, the cradle and the
        // pod turn most of them away, so a handful of random tries could all miss and leave it outside.
        // No further than (2, 2): a machine counts it arrived within 3 blocks of its centre.
        BlockPos centre = BlockPos.containing(goal);
        List<BlockPos> beside = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq >= 4 && distanceSq <= 8) beside.add(centre.offset(dx, 0, dz));
            }
        }
        Util.shuffle(beside, this.random);
        for (BlockPos column : beside) {
            double x = column.getX() + 0.5;
            double z = column.getZ() + 0.5;
            if (tooCloseToWatchers(level, x, z)) continue;
            for (int dy = 1; dy >= -2; dy--) {
                if (this.randomTeleport(x, Math.floor(goal.y) + dy, z, false)) {
                    this.getNavigation().stop();
                    flash(level, before);
                    flash(level, position());
                    this.playSound(ModSounds.VEILED_BLINK.get(), 1.0f, 1.0f);
                    return;
                }
            }
        }
    }

    /**
     * Whether a blink to ({@code x}, {@code z}) would land it within its wary distance of someone in the
     * mirror. It never blinks in on top of anyone: that would corner it, and cornered it strikes.
     */
    private boolean tooCloseToWatchers(ServerLevel level, double x, double z) {
        for (Player player : level.players()) {
            if (!MirrorPhase.isPhased(player) || player.isSpectator()) continue;
            if (Mth.square(player.getX() - x) + Mth.square(player.getZ() - z) < Mth.square(WARY + 1.0)) return true;
        }
        return false;
    }

    private void resetApproach() {
        approachBest = Double.MAX_VALUE;
        approachStuck = 0;
    }

    /** Takes the double: its body is gone, and it carries the Sophon. */
    private void take(ServerLevel level, UUID sophon) {
        SophonRegistry registry = SophonRegistry.get(level.getServer());
        SophonRegistry.Entry entry = registry.entry(sophon);
        targetDouble = null;
        feeding = 0;
        state = State.WANDER;
        if (entry == null || !SophonRegistry.unguarded(entry)) return;
        BlockPos at = entry.pod().get().pos();
        if (entry.inField()) {
            Entity body = entry.entity().map(level::getEntity).orElse(null);
            if (body instanceof FieldDouble) body.discard();
        } else if (level.getBlockEntity(at) instanceof SuperpositionPodBlockEntity pod && sophon.equals(pod.occupant())) {
            pod.releaseDouble();
        }
        registry.taken(sophon, entry.pod().get(), this.getUUID());
        carried.add(sophon);
        syncCarried();
        Superposition.podEffect(level, at, Superposition.EFFECT_ECHO);
        this.playSound(ModSounds.VEILED_STRIKE.get(), 1.0f, 0.5f);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entry.owner());
        if (owner != null) owner.sendSystemMessage(Component.translatable("message.quantimium.veiled.took",
                at.getX(), at.getZ()).withStyle(ChatFormatting.DARK_PURPLE));
        nextRetarget = level.getGameTime() + 200;
    }

    /**
     * Lets go of every Sophon it carries: they fall where it is, as knocked-out Sophons, for their
     * owners (or their Recovery pods) to pick up.
     */
    private void dropCarried(ServerLevel level) {
        if (carried.isEmpty()) return;
        SophonRegistry registry = SophonRegistry.get(level.getServer());
        GlobalPos here = GlobalPos.of(level.dimension(), blockPosition());
        for (UUID sophon : List.copyOf(carried)) {
            SophonRegistry.Entry entry = registry.entry(sophon);
            if (entry == null || !entry.taken()) continue;
            ItemEntity item = new ItemEntity(level, getX(), getY() + 0.5, getZ(), Superposition.sophonFor(level.getServer(), sophon));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
            registry.dropped(sophon, here);
            Recovery.afterKnockOut(level.getServer(), entry.owner(), sophon, null);
        }
        carried.clear();
        syncCarried();
        showEveryone(level, ParticleTypes.END_ROD, position().add(0.0, 1.0, 0.0), 20, 0.5);
    }

    /** Held in a hall or gone for want of anyone to haunt, it lets go of what it carries on the way out. */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED)
                && this.level() instanceof ServerLevel level) {
            dropCarried(level);
        }
        super.remove(reason);
    }

    private void shadowTick(ServerLevel level, long now) {
        ServerPlayer player = targetPlayer == null ? null : (ServerPlayer) level.getPlayerByUUID(targetPlayer);
        if (player == null || player.isSpectator() || distanceTo(player) > AREA || MirrorPhase.isPhased(player)) {
            // Stepping into the mirror ends the stalking: there it can be seen.
            state = State.WANDER;
            return;
        }
        if (observedBy(player)) lastWatched = now;
        // A body with nobody in it is what it wants most: once they stop watching it for long enough, it
        // leaves them for an unguarded double, Lens or no Lens.
        if (now % 100 == 0 && now - lastWatched > SHADOW_FORGETS) {
            if (targetDouble == null) targetDouble = nearestUnguardedDouble(level, position(), AREA);
            if (doublePosition(level) != null) {
                state = State.PRESENCE;
                return;
            }
        }
        // Someone watching it through a Lens holds its attention; otherwise it drifts to their base.
        if (now % 100 == 0 && !MirrorLensItem.isWorn(player)) {
            BlockPos machine = nearestMachine(level, player.blockPosition(), BASE_RADIUS);
            if (machine != null) {
                targetMachine = machine;
                state = State.PRESENCE;
                return;
            }
        }
        // A few blocks behind them, where they are not looking.
        Vec3 look = player.getViewVector(1.0f);
        Vec3 behind = player.position().subtract(new Vec3(look.x, 0.0, look.z).normalize().scale(6.0));
        double distance = distanceTo(player);
        if (distance > 24.0 && !observedBy(player)) {
            blinkTo(level, behind.x, behind.z, 6);
        } else if (distanceToSqr(behind) > 9.0 && this.tickCount % 10 == 0) {
            this.getNavigation().moveTo(behind.x, behind.y, behind.z, 1.1);
        }
        if (distance <= 8.0 && !observedBy(player)) drainGear(player, GEAR_FE_PER_TICK);
    }

    /** Charged items and armour, from anywhere in the inventory, bounded in total per tick. */
    private static void drainGear(ServerPlayer player, int budget) {
        int remaining = budget;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DecoherenceLanceItem) {
                // Receive-only through its capability, so bill it directly.
                int stored = DecoherenceLanceItem.energy(stack);
                int take = Math.min(stored, remaining);
                if (take > 0) {
                    DecoherenceLanceItem.setEnergy(stack, stored - take);
                    remaining -= take;
                }
                continue;
            }
            IEnergyStorage energy = LegacyEnergy.item(stack);
            if (energy == null || !energy.canExtract()) continue;
            remaining -= energy.extractEnergy(remaining, false);
        }
    }

    /**
     * Seen from the real world through a Mirror Lens: stared at like that, it is curious what can see
     * it, and follows them home. It lets them know with a sound only they hear. Only a free one,
     * wandering or feeding; one already stalking, fighting or driven off has other business.
     */
    private void noticeLensWatchers(ServerLevel level) {
        if ((state != State.WANDER && state != State.PRESENCE) || onCooldown()) return;
        for (ServerPlayer player : level.players()) {
            if (MirrorPhase.isPhased(player) || player.isSpectator() || !MirrorLensItem.isWorn(player)) continue;
            if (distanceTo(player) > INTEREST_RANGE || !staredAtBy(player)) continue;
            targetPlayer = player.getUUID();
            targetMachine = null;
            state = State.SHADOWING;
            lastWatched = level.getGameTime();
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.VEILED_STARE.get()), SoundSource.HOSTILE,
                    getX(), getEyeY(), getZ(), 0.8f, 0.9f, level.getRandom().nextLong()));
            return;
        }
    }

    /** Within a 70° cone of where the player looks, with nothing in between. */
    public boolean observedBy(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 toward = this.position().add(0.0, this.getBbHeight() * 0.6, 0.0).subtract(eye);
        if (toward.lengthSqr() < 1.0E-4) return true;
        if (player.getViewVector(1.0f).dot(toward.normalize()) < VEILED_COS) return false;
        return lineOfSight(player);
    }

    private boolean staredAtBy(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 toward = this.position().add(0.0, this.getBbHeight() * 0.6, 0.0).subtract(eye);
        if (player.getViewVector(1.0f).dot(toward.normalize()) < STARE_COS) return false;
        return lineOfSight(player);
    }

    private boolean lineOfSight(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = this.position().add(0.0, this.getBbHeight() * 0.6, 0.0);
        return this.level().clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    // ---- The Lance ----

    /**
     * One tick of Lance contact from a phased player. The first turns it on them; while the beam
     * holds it is frozen, and every tick of contact counts towards pinning it.
     */
    public void lance(ServerPlayer player) {
        if (captureHall().isPresent()) return;
        ServerLevel level = (ServerLevel) this.level();
        long now = level.getGameTime();
        if (state != State.AGGRAVATED || !player.getUUID().equals(aggressor)) {
            // Damage already done stays done: a second fight picks up where the first left off.
            state = State.AGGRAVATED;
            aggressor = player.getUUID();
        }
        if (now - lastBeamTick > 1 || this.tickCount % 20 == 0) {
            FluxRiftManager.provokeMites(level, this.blockPosition(), player);
        }
        lastBeamTick = now;
        pinTicks++;
        syncCoherence();
        if (pinTicks >= PIN_TICKS) finishPin(level);
    }

    /**
     * One tick of a Decoherence Projector's beam. Unlike the Lance there is nobody to turn on, so it
     * is frozen and worn down without being aggravated; past halfway a hall in reach takes hold, as
     * with a player, which is what makes a Projector beside a hall a trap.
     */
    public void decohere(ServerLevel level) {
        if (captureHall().isPresent()) return;
        projectorBeamTick = level.getGameTime();
        pinTicks++;
        syncCoherence();
        if (1.0f - coherence() >= HALL_PULL_AT) {
            ContainmentHallBlockEntity hall = VeiledManager.findHall(level, this.blockPosition(), HALL_REACH);
            if (hall != null) {
                startCapture(hall);
                return;
            }
        }
        if (pinTicks >= PIN_TICKS) finishPin(level);
    }

    public boolean frozen() {
        return state == State.AGGRAVATED && this.level().getGameTime() - lastBeamTick <= BEAM_GRACE;
    }

    private void aggravatedTick(ServerLevel level, long now) {
        ServerPlayer player = aggressor == null ? null : (ServerPlayer) level.getPlayerByUUID(aggressor);
        if (player == null || !MirrorPhase.isPhased(player) || player.isSpectator()
                || distanceTo(player) > INTEREST_RANGE || now - lastBeamTick > LOSE_INTEREST) {
            // Left the mirror, got away, or stopped fighting: it loses interest.
            driveOff(level);
            return;
        }
        this.getLookControl().setLookAt(player, 30.0f, 30.0f);
        if (now - lastBeamTick <= BEAM_GRACE) {
            this.getNavigation().stop();
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
            return;
        }
        // Worn past halfway, a powered hall in reach takes hold the moment the beam breaks.
        if (1.0f - coherence() >= HALL_PULL_AT) {
            ContainmentHallBlockEntity hall = VeiledManager.findHall(level, this.blockPosition(), HALL_REACH);
            if (hall != null) {
                startCapture(hall);
                return;
            }
        }
        // The beam broke: it comes straight for them, strikes, and blinks off to come again.
        if (this.tickCount % 5 == 0) this.getNavigation().moveTo(player, 1.6);
        if (distanceTo(player) <= 2.5 && now >= strikeReadyAt) {
            strike(level, player);
            strikeReadyAt = now + 30;
            blinkAway(level, player.position(), 6, 10);
        }
    }

    /** Fully pinned: a powered hall in reach takes hold; otherwise it folds away to the edges. */
    private void finishPin(ServerLevel level) {
        ContainmentHallBlockEntity hall = VeiledManager.findHall(level, this.blockPosition(), HALL_REACH);
        if (hall != null) {
            startCapture(hall);
            return;
        }
        tellWhyNoHall(level);
        driveOff(level);
    }

    /**
     * Pinned beside a hall that couldn't take it: the lancer is told why, so a hungry or dark hall
     * doesn't look like a broken one. Nothing is said with no hall near.
     */
    private void tellWhyNoHall(ServerLevel level) {
        if (aggressor == null || !(level.getPlayerByUUID(aggressor) instanceof ServerPlayer player)) return;
        ContainmentHallBlockEntity near = VeiledManager.nearestHall(level, this.blockPosition(), HALL_REACH);
        if (near == null) return;
        ContainmentHallBlockEntity.Cell cell = near.cell();
        // Waiting, but it wasn't found: the hall's arms don't reach this far.
        String why = cell == ContainmentHallBlockEntity.Cell.WAITING ? "reach" : cell.name().toLowerCase(Locale.ROOT);
        player.sendSystemMessage(Component.translatable("message.quantimium.veiled.escaped." + why)
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    /** Folded away to the edges, and nothing more from it until the base's anomaly lets it back. */
    public void driveOff(ServerLevel level) {
        FluxBand anomaly = QuantumFlux.chunkAnomalyBand(level, this.blockPosition());
        // Driven off, it lets go of the doubles it took, where it stood.
        dropCarried(level);
        targetDouble = null;
        feeding = 0;
        state = State.WANDER;
        aggressor = null;
        pinTicks = 0;
        syncCoherence();
        this.getNavigation().stop();
        // Heard where it stood: the blink that follows lands 32+ blocks away, out of earshot.
        this.playSound(ModSounds.VEILED_UNRAVEL.get(), 1.4f, 1.0f);
        flash(level, position());
        Player nearest = level.getNearestPlayer(this, AREA);
        Vec3 from = nearest != null ? nearest.position() : position();
        blinkAway(level, from, 32, 48);
        cooldownUntil = level.getGameTime() + cooldown(anomaly);
        nextRetarget = 0L;
    }

    /** A hot base calls it back sooner. */
    private static long cooldown(FluxBand anomaly) {
        return switch (anomaly) {
            case LOW -> 6000L;
            case MEDIUM -> 3600L;
            case HIGH -> 2400L;
            case CRITICAL -> 1200L;
            case SINGULARITY -> 600L;
        };
    }

    // ---- Reactions ----

    /** Damage, Blindness and Slowness. Its own damage type, so phased isolation lets it through. */
    private void strike(ServerLevel level, Player player) {
        this.playSound(ModSounds.VEILED_STRIKE.get(), 1.4f, 1.0f);
        player.hurt(level.damageSources().source(ModDamageTypes.VEILED), 4.0f);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false, true));
    }

    /** A random spot {@code min}–{@code max} blocks from {@code from}, with a flash for phased viewers. */
    private boolean blinkAway(ServerLevel level, Vec3 from, int min, int max) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = min + random.nextDouble() * (max - min);
            if (blinkTo(level, from.x + Math.cos(angle) * distance, from.z + Math.sin(angle) * distance, 6)) {
                return true;
            }
        }
        return false;
    }

    private boolean blinkTo(ServerLevel level, double x, double z, int verticalSearch) {
        boolean moved = blinkToQuietly(level, x, z, verticalSearch);
        if (moved) this.playSound(ModSounds.VEILED_BLINK.get(), 1.0f, 1.0f);
        return moved;
    }

    private boolean blinkToQuietly(ServerLevel level, double x, double z, int verticalSearch) {
        BlockPos column = BlockPos.containing(x, this.getY(), z);
        if (!level.isLoaded(column)) return false;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        int[] candidates = Math.abs(surface - this.getY()) <= verticalSearch * 2
                ? new int[] {surface} : new int[0];
        Vec3 before = position();
        for (int y : candidates) {
            if (this.randomTeleport(x, y, z, false)) {
                flash(level, before);
                flash(level, position());
                return true;
            }
        }
        // Underground or on a different storey: search near the current height instead.
        for (int dy = 0; dy <= verticalSearch; dy++) {
            for (int sign : new int[] {1, -1}) {
                if (this.randomTeleport(x, this.getY() + dy * sign, z, false)) {
                    flash(level, before);
                    flash(level, position());
                    return true;
                }
            }
        }
        return false;
    }

    private void flash(ServerLevel level, Vec3 at) {
        for (ServerPlayer player : level.players()) {
            if (!MirrorPhase.isPhased(player)) continue;
            level.sendParticles(player, ParticleTypes.REVERSE_PORTAL, false, false, at.x, at.y + 1.4, at.z,
                    24, 0.3, 0.8, 0.3, 0.06);
        }
    }

    private static void showEveryone(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
    }

    /** Released from a hall that lost power: it goes straight back to feeding, on the hall itself. */
    public void releasedFrom(BlockPos hall) {
        targetMachine = hall;
        state = State.PRESENCE;
    }

    // ---- Persistence ----

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag);
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag) {
        // Aggravation is not saved: a fight does not survive a reload.
        tag.putString("State", state == State.AGGRAVATED ? State.WANDER.name() : state.name());
        long remaining = cooldownUntil - this.level().getGameTime();
        if (remaining > 0) tag.putLong("Cooldown", remaining);
        if (targetMachine != null) NbtCompat.putPos(tag, "Machine", targetMachine);
        if (targetPlayer != null) tag.store("Player", net.minecraft.core.UUIDUtil.CODEC, targetPlayer);
        if (targetDouble != null) tag.store("Double", net.minecraft.core.UUIDUtil.CODEC, targetDouble);
        ListTag taken = new ListTag();
        for (UUID sophon : carried) taken.add(NbtCompat.uuidTag(sophon));
        if (!taken.isEmpty()) tag.put("Carried", taken);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        loadLegacy(NbtCompat.read(in));
    }

    private void loadLegacy(CompoundTag tag) {
        try {
            state = State.valueOf(tag.getStringOr("State", ""));
        } catch (IllegalArgumentException missing) {
            state = State.WANDER;
        }
        cooldownUntil = this.level().getGameTime() + tag.getLongOr("Cooldown", 0L);
        targetMachine = NbtCompat.getPos(tag, "Machine").orElse(null);
        targetPlayer = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Player") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Player") : null;
        targetDouble = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Double") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Double") : null;
        carried.clear();
        for (Tag sophon : tag.getListOrEmpty("Carried")) { UUID loaded = NbtCompat.uuidFrom(sophon); if (loaded != null) carried.add(loaded); };
        syncCarried();
    }

    // ---- Mirror behaviour ----

    /**
     * Too close: it backs away like a cat. With nowhere left to go and the player on top of it, it
     * strikes and blinks away. Never while aggravated, which has its own rules.
     */
    private class WaryGoal extends Goal {
        @Nullable
        private Player threat;

        WaryGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        /**
         * Goals otherwise tick every other tick, on odd or even ticks by entity id, so a
         * {@code tickCount % 10} check could never come true for half of all Veiled: curiosity
         * silently never walked it anywhere. Every tick, like the state machine.
         */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (state == State.AGGRAVATED) return false;
            threat = nearestPhased(WARY);
            return threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            return state != State.AGGRAVATED && threat != null && threat.isAlive()
                    && distanceTo(threat) < WARY + 2.0 && MirrorPhase.isPhased(threat);
        }

        @Override
        public void tick() {
            if (threat == null) return;
            ServerLevel level = (ServerLevel) level();
            long now = level.getGameTime();
            noticed(threat);
            getLookControl().setLookAt(threat, 30.0f, 30.0f);
            Vec3 away = DefaultRandomPos.getPosAway(Veiled.this, 16, 7, threat.position());
            boolean trapped = away == null || getNavigation().isStuck();
            if (distanceTo(threat) < CORNERED && trapped && now >= strikeReadyAt) {
                strike(level, threat);
                strikeReadyAt = now + 40;
                blinkAway(level, threat.position(), 12, 18);
                return;
            }
            if (away != null && (getNavigation().isDone() || tickCount % 10 == 0)) {
                getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            }
        }
    }

    /**
     * Stared at from the mirror, it comes closer, curious, and stops at a distance. It keeps coming
     * for a while after the stare ends: once it has noticed you, it does not simply forget.
     */
    private class CuriousGoal extends Goal {
        CuriousGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /**
         * Goals otherwise tick every other tick, on odd or even ticks by entity id, so a
         * {@code tickCount % 10} check could never come true for half of all Veiled: curiosity
         * silently never walked it anywhere. Every tick, like the state machine.
         */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (state != State.WANDER && state != State.PRESENCE) return false;
            for (Player player : level().players()) {
                if (!MirrorPhase.isPhased(player) || player.isSpectator()) continue;
                if (distanceTo(player) < INTEREST_RANGE && staredAtBy(player)) noticed(player);
            }
            Player watcher = interest();
            return watcher != null && distanceTo(watcher) > CURIOUS_STOP + 0.5;
        }

        @Override
        public boolean canContinueToUse() {
            if (state != State.WANDER && state != State.PRESENCE) return false;
            Player watcher = interest();
            if (watcher == null) return false;
            if (staredAtBy(watcher)) noticed(watcher);
            return distanceTo(watcher) > CURIOUS_STOP;
        }

        @Override
        public void tick() {
            Player watcher = interest();
            if (watcher == null) return;
            getLookControl().setLookAt(watcher, 20.0f, 20.0f);
            if (tickCount % 10 == 0) getNavigation().moveTo(watcher, 0.7);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** Arrived, or backing off: whichever it is doing, it does not take its eyes off them. */
    private class HoldGazeGoal extends Goal {
        HoldGazeGoal() {
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return state != State.AGGRAVATED && interest() != null;
        }

        @Override
        public void tick() {
            Player watcher = interest();
            if (watcher != null) getLookControl().setLookAt(watcher, 30.0f, 30.0f);
        }
    }

    /** Notices a player: attention stays on them for a while even after they look away. */
    private void noticed(Player player) {
        long now = level().getGameTime();
        if (!player.getUUID().equals(interestIn) || now >= interestUntil) {
            this.playSound(ModSounds.VEILED_STARE.get(), 0.7f, 1.0f);
        }
        interestIn = player.getUUID();
        interestUntil = now + INTEREST_TICKS;
    }

    @Nullable
    private Player interest() {
        if (interestIn == null || level().getGameTime() >= interestUntil) return null;
        Player player = level().getPlayerByUUID(interestIn);
        return player != null && MirrorPhase.isPhased(player) && !player.isSpectator() ? player : null;
    }

    @Nullable
    private Player nearestPhased(double range) {
        Player best = null;
        double bestSq = range * range;
        for (Player player : level().players()) {
            if (!MirrorPhase.isPhased(player) || player.isSpectator() || player.isCreative()) continue;
            double distanceSq = distanceToSqr(player);
            if (distanceSq < bestSq) {
                best = player;
                bestSq = distanceSq;
            }
        }
        return best;
    }
}
