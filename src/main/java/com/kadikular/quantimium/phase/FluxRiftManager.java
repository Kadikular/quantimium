package com.kadikular.quantimium.phase;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.event.MirrorPhaseInteractions;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.MirrorMiteSpawner;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModSounds;
import com.kadikular.quantimium.network.FluxRiftStrikePayload;
import com.kadikular.quantimium.network.FluxRiftSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import com.kadikular.quantimium.flux.FluxSources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent flux rifts. A rift is born when an anomaly tear in an uncontained High+ field fails to
 * close, grows while the field stays hot, keeps a tear open nearby as the way in, and can only be
 * closed from the mirror by decohering its anchor with the lance.
 *
 * <p>Anomaly sets how fast it grows and flux sets how far — the same split as the rest of the mod:
 * flux is how active the world is, anomaly is how aggressive.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class FluxRiftManager {

    /** How much anomaly a rift shows bleeding each second through a Mirror Lens, per stage. Visual only. */
    private static final double VISIBLE_BLEED_PER_STAGE = 6.0;

    /** Persistent-data key naming the rift a mite was spat out by, so it can be recalled on collapse. */
    private static final String MITE_TAG = "quantimium:flux_rift";
    /** Beam contact within this window counts as "being drained" for regen and for fighting back. */
    private static final long DRAIN_WINDOW = 40L;
    /** Shorter window for the client's "being shot" effects, so they stop soon after the beam does. */
    private static final long FEEDBACK_WINDOW = 6L;
    /** Mites this close to a rift turn on whoever starts draining it. */
    private static final double PROVOKE_RANGE = 24.0;
    /** Breaching needs someone within this range, and counts its mites within it. */
    private static final double BREACH_AUDIENCE = 48.0;
    /** Phased players this close are thrown back to the real world when a rift collapses. */
    private static final double EJECT_RANGE = 24.0;
    private static final double REGEN_PER_TICK = 3.0;
    /** Pause between one of the rift's tears closing and the next opening, so it flickers rather than blinks. */
    private static final long TEAR_GAP_TICKS = 100L;
    /** Coherence changes every tick while channelled; clients only need it a few times a second. */
    private static final int SYNC_INTERVAL = 4;
    /**
     * Residue for collapsing a stage 1–4 rift. Nothing at stage 1, so a planted Rift Seed has to be
     * left to grow in a fed field before it pays; after that, triangular, so letting it grow further
     * is a real temptation.
     */
    private static final int[] RESIDUE = {0, 3, 6, 10};
    /** A stabilised rift is fed by its stabilisers rather than the field: ten minutes a stage, anywhere. */
    private static final double STABILISED_STAGE_SECONDS = 600.0;

    private static final Set<ServerLevel> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());

    private FluxRiftManager() {}

    public static Collection<FluxRift> rifts(ServerLevel level) {
        return data(level).rifts();
    }

    /** Called as an anomaly entry tear expires. Most close cleanly; in a hot, unshielded field some don't. */
    public static void considerFailedTear(ServerLevel level, BlockPos anchor) {
        if (!Config.fluxRiftsEnabled() || !level.isLoaded(anchor)) return;
        if (!FluxBand.HIGH.covers(QuantumFlux.chunkAnomalyBand(level, anchor))) return;
        if (QuantumFlux.chunkContained(level, anchor)) return;
        if (level.getRandom().nextDouble() >= Config.fluxRiftChance()) return;
        if (room(level, anchor) != Room.OPEN) return;
        spawn(level, anchor, 1);
    }

    /** Whether a new rift may open at {@code anchor}, and if not, why. */
    public enum Room { OPEN, TOO_CLOSE, CROWDED, FULL }

    /**
     * Rifts are limited where they are, not across the world: none closer than the spacing, and no
     * more than a few in one neighbourhood, so one base's rifts never stop another's. A cap on the
     * whole dimension stays as a safety net. The same for tears that fail and seeds that are planted.
     */
    public static Room room(ServerLevel level, BlockPos anchor) {
        Collection<FluxRift> rifts = data(level).rifts();
        if (rifts.size() >= Config.fluxRiftMax()) return Room.FULL;
        double spacingSq = (double) Config.fluxRiftSpacing() * Config.fluxRiftSpacing();
        double nearbySq = (double) Config.fluxRiftNearbyRadius() * Config.fluxRiftNearbyRadius();
        int nearby = 0;
        for (FluxRift other : rifts) {
            double distanceSq = other.anchor().distSqr(anchor);
            if (distanceSq < spacingSq) return Room.TOO_CLOSE;
            if (distanceSq < nearbySq) nearby++;
        }
        return nearby >= Config.fluxRiftMaxNearby() ? Room.CROWDED : Room.OPEN;
    }

    public static FluxRift spawn(ServerLevel level, BlockPos anchor, int stage) {
        FluxRift rift = new FluxRift(UUID.randomUUID(), anchor, level.getRandom().nextInt(), stage, 0.0);
        data(level).add(rift);
        markDirty(level);
        clearVolume(level, rift);
        return rift;
    }

    /** Whether any rift's solid body fills this cell. */
    public static boolean occupied(ServerLevel level, BlockPos cell) {
        FluxRiftData data = level.getExistingData(ModAttachments.FLUX_RIFTS).orElse(null);
        if (data == null) return false;
        for (FluxRift rift : data.rifts()) {
            if (FluxRift.occupies(rift.anchor(), rift.stage(), cell)) return true;
        }
        return false;
    }

    /**
     * Nothing can be built inside a rift. The block goes back in the placer's hand; the client's
     * guess at it is undone by the server's correction.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && occupied(level, event.getPos())) event.setCanceled(true);
    }

    /**
     * The wound breaks whatever fills the space it stands in, with drops, as it opens and as it
     * grows — and each second after, so a piston or falling sand cannot wall it in either. Leaves
     * fluids, unbreakable blocks and anything with a block entity alone: it should not eat a chest
     * or a machine, and a rift standing through one is the player's problem to move.
     */
    private static void clearVolume(ServerLevel level, FluxRift rift) {
        BlockPos anchor = rift.anchor();
        int stage = rift.stage();
        int reach = (int) Math.ceil(FluxRift.solidRadius(stage) + 0.5);
        int top = (int) Math.ceil(FluxRift.top(anchor, stage));
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int y = anchor.getY(); y < top; y++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    cell.set(anchor.getX() + dx, y, anchor.getZ() + dz);
                    if (!FluxRift.occupies(anchor, stage, cell) || !level.isLoaded(cell)) continue;
                    BlockState state = level.getBlockState(cell);
                    if (state.isAir() || state.hasBlockEntity() || state.getBlock() instanceof LiquidBlock
                            || state.getDestroySpeed(level, cell) < 0.0f) {
                        continue;
                    }
                    level.destroyBlock(cell, true);
                }
            }
        }
    }

    /**
     * The rift a Rift Anchor at {@code block} holds: one standing on it, or one or two blocks above
     * it within a block either side, so an anchor can be slipped under a natural rift without
     * having to hit its exact footing.
     */
    @Nullable
    public static FluxRift riftAbove(ServerLevel level, BlockPos block) {
        for (FluxRift rift : data(level).rifts()) {
            BlockPos anchor = rift.anchor();
            int dy = anchor.getY() - block.getY();
            if (dy >= 1 && dy <= 2 && Math.abs(anchor.getX() - block.getX()) <= 1
                    && Math.abs(anchor.getZ() - block.getZ()) <= 1) {
                return rift;
            }
        }
        return null;
    }

    /** A Rift Seed planted on an anchor: a stage 1 wound standing on it. */
    public static FluxRift seed(ServerLevel level, BlockPos block) {
        return spawn(level, block.above(), 1);
    }

    @Nullable
    public static FluxRift nearest(ServerLevel level, Vec3 pos, double range) {
        return nearest(level, pos, range, false);
    }

    /** With {@code wildOnly}, stabilised rifts are skipped: a Projector leaves someone's farm alone. */
    @Nullable
    public static FluxRift nearest(ServerLevel level, Vec3 pos, double range, boolean wildOnly) {
        FluxRift best = null;
        double bestSq = range * range;
        long now = level.getGameTime();
        for (FluxRift rift : data(level).rifts()) {
            if (wildOnly && rift.stabilised(now)) continue;
            double distanceSq = rift.centre().distanceToSqr(pos);
            if (distanceSq < bestSq) {
                best = rift;
                bestSq = distanceSq;
            }
        }
        return best;
    }

    public static void setStage(ServerLevel level, FluxRift rift, int stage) {
        rift.setStage(stage);
        markDirty(level);
        clearVolume(level, rift);
    }

    public static int clear(ServerLevel level) {
        List<FluxRift> all = new ArrayList<>(data(level).rifts());
        for (FluxRift rift : all) remove(level, rift);
        return all.size();
    }

    /**
     * One tick of lance contact. Only a phased player reaches the anchor; the real world holds just
     * the shadow, which is why a rift can only be closed from the other side.
     */
    /**
     * Drained by a Decoherence Projector rather than a player. Its residue drops at {@code dropAt},
     * since there is nobody to hand it to.
     */
    public static void drainUnattended(ServerLevel level, UUID id, double amount, BlockPos projector, Vec3 dropAt) {
        FluxRift rift = data(level).get(id);
        if (rift == null) return;
        rift.drain(amount, level.getGameTime());
        rift.drainedBy(projector, level.getGameTime());
        markDirty(level);
        if (!rift.collapsed()) return;
        remove(level, rift);
        int count = RESIDUE[Math.min(RESIDUE.length, rift.stage()) - 1];
        if (count > 0) {
            level.addFreshEntity(new ItemEntity(level, dropAt.x, dropAt.y, dropAt.z,
                    new ItemStack(ModItems.RIFT_RESIDUE.get(), count)));
        }
        ejectNear(level, rift);
    }

    /** A player swung at a rift. */
    public static void touched(ServerPlayer player, UUID id) {
        FluxRift rift = data(player.level()).get(id);
        if (rift != null) FluxRiftHazard.touched(player, rift);
    }

    public static void drain(ServerPlayer player, UUID id, double amount) {
        ServerLevel level = player.level();
        FluxRift rift = data(level).get(id);
        if (rift == null || !MirrorPhase.isPhased(player)) return;
        long now = level.getGameTime();
        // On the first touch of a beam, and every second while it holds, the rift's whole
        // neighbourhood of mites turns on the drainer — not only the ones it spits out.
        boolean started = !rift.beingDrained(now, DRAIN_WINDOW);
        if (started || now % 20 == 0) provokeMites(level, rift, player);
        rift.drain(amount, now);
        markDirty(level);
        if (rift.collapsed()) collapse(level, rift, player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            FluxRiftData data = level.getExistingData(ModAttachments.FLUX_RIFTS).orElse(null);
            if (data == null || data.isEmpty()) continue;
            long now = level.getGameTime();
            for (FluxRift rift : List.copyOf(data.rifts())) {
                if (rift.damage() > 0.0 && !rift.beingDrained(now, DRAIN_WINDOW)) {
                    rift.regenerate(REGEN_PER_TICK);
                    markDirty(level);
                }
                if (!level.isLoaded(rift.anchor())) continue;
                if (rift.beingDrained(now, FEEDBACK_WINDOW + 1) && !rift.beingDrained(now, FEEDBACK_WINDOW)) {
                    markDirty(level); // the beam just stopped; let clients drop the shot effects
                }
                if (rift.stabilised(now) != rift.wasStabilised()) {
                    rift.setWasStabilised(rift.stabilised(now));
                    markDirty(level); // clients draw a held rift calmer
                }
                FluxRiftHazard.tick(level, rift, now, tick % FluxRiftHazard.STRIKE_CHECK == 0);
                if (tick % 20 == 0) second(level, rift, now);
            }
        }
        if (tick % SYNC_INTERVAL == 0 && !DIRTY.isEmpty()) {
            for (ServerLevel level : List.copyOf(DIRTY)) syncLevel(level);
            DIRTY.clear();
        }
    }

    /** Levels outlive nothing across a singleplayer world reload; do not hold on to the old ones. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DIRTY.clear();
    }

    private static void second(ServerLevel level, FluxRift rift, long now) {
        BlockPos anchor = rift.anchor();
        clearVolume(level, rift);
        // Through a Mirror Lens every rift bleeds from its tear, held or not; only a wild one's
        // leak is real (below).
        FluxSources.show(level, BlockPos.containing(rift.centre()), 0.0, VISIBLE_BLEED_PER_STAGE * rift.stage());
        if (rift.stabilised(now)) {
            // Held: it grows on the stabilisers' power, and nothing comes through it.
            growHeld(level, rift);
            return;
        }
        boolean contained = QuantumFlux.chunkContained(level, anchor);
        if (!contained) {
            grow(level, rift);
            double leak = Config.fluxRiftLeakPerStage() * rift.stage();
            if (leak > 0.0) QuantumFlux.emitUnseen(level, anchor, 0.0, leak);
        }
        keepTearOpen(level, rift, now);
        if (rift.beingDrained(now, DRAIN_WINDOW)) fightBack(level, rift, now);
        lashProjector(level, rift, now);
        if (!contained) {
            breach(level, rift);
            VeiledManager.considerRelease(level, rift);
        }
    }

    /**
     * From stage 3 the wound is wide enough for mites to come through into the real world, where
     * everyone can see and fight them. Only with someone about to meet them, so an empty loaded
     * chunk does not stockpile an army; containment holds them back like everything else.
     */
    private static void breach(ServerLevel level, FluxRift rift) {
        int stage = rift.stage();
        if (stage < 3) return;
        if (level.getRandom().nextDouble() >= (stage >= 4 ? 0.15 : 0.075)) return;
        Player near = level.getNearestPlayer(rift.centre().x, rift.centre().y, rift.centre().z, BREACH_AUDIENCE,
                player -> !player.isSpectator());
        if (near == null) return;
        int breached = mitesOf(level, rift, BREACH_AUDIENCE, true).size();
        if (breached >= (stage >= 4 ? 4 : 2)) return;
        MirrorEndermite mite = MirrorMiteSpawner.spawnNear(level, rift.anchor(), spawned -> {
            spawned.setBreached(true);
            markSpawned(spawned, rift);
        });
        if (mite == null) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mite.getX(), mite.getY() + 0.2, mite.getZ(),
                30, 0.2, 0.2, 0.2, 0.1);
        mite.playSound(ModSounds.MITE_RECALL.get(), 1.0f, 0.7f);
        mite.setTarget(near);
    }

    private static void provokeMites(ServerLevel level, FluxRift rift, ServerPlayer drainer) {
        provokeMites(level, rift.anchor(), drainer);
    }

    /** Every mite near {@code centre} that can see the player turns on them: anyone lancing an anomaly. */
    public static void provokeMites(ServerLevel level, BlockPos centre, ServerPlayer drainer) {
        AABB around = new AABB(centre).inflate(PROVOKE_RANGE);
        for (MirrorEndermite mite : level.getEntities(ModEntities.MIRROR_ENDERMITE.get(), around,
                mite -> mite.isAlive() && mite.realTo(drainer))) {
            mite.provoke(drainer);
        }
    }

    /**
     * From stage 3 a rift fights a Projector directly: lightning down the beam, whatever the distance,
     * burning out its entire charge and knocking it out for a few seconds besides. A lone Projector can close the small
     * wounds on its own; the big ones need a player, or a well-powered array and a plan for the mites.
     */
    private static void lashProjector(ServerLevel level, FluxRift rift, long now) {
        int stage = rift.stage();
        if (stage < 3) return;
        BlockPos pos = rift.projector(now, DRAIN_WINDOW);
        if (pos == null || level.getRandom().nextDouble() >= 0.2 * (stage - 2)) return;
        if (!(level.getBlockEntity(pos) instanceof DecoherenceProjectorBlockEntity projector)) return;
        projector.overload(60);
        FluxRiftStrikePayload bolt = new FluxRiftStrikePayload(rift.centre(), projector.orb());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(projector.orb()) <= 64.0 * 64.0) PacketDistributor.sendToPlayer(player, bolt);
        }
    }

    /** Marks {@code mite} as {@code rift}'s: it counts against the rift's caps and goes when it closes. */
    public static void markSpawned(Entity mite, FluxRift rift) {
        mite.getPersistentData().putString(MITE_TAG, rift.id().toString());
    }

    /** {@code rift}'s mites within {@code range} of its anchor; with {@code breachedOnly}, those in the real world. */
    public static List<MirrorEndermite> mitesOf(ServerLevel level, FluxRift rift, double range, boolean breachedOnly) {
        String id = rift.id().toString();
        AABB around = new AABB(rift.anchor()).inflate(range);
        return level.getEntities(ModEntities.MIRROR_ENDERMITE.get(), around,
                mite -> (!breachedOnly || mite.isBreached()) && id.equals(mite.getPersistentData().getStringOr(MITE_TAG, "")));
    }

    /** Spat out by a rift: kept alive while its rift is, and recalled when it closes. */
    public static boolean spawnedByRift(Entity mite) {
        return !mite.getPersistentData().getStringOr(MITE_TAG, "").isEmpty();
    }

    /** A mite whose rift is gone — closed while it was unloaded, or cleared — has nothing holding it here. */
    public static boolean orphaned(ServerLevel level, Entity mite) {
        String tag = mite.getPersistentData().getStringOr(MITE_TAG, "");
        if (tag.isEmpty()) return false;
        try {
            return data(level).get(UUID.fromString(tag)) == null;
        } catch (IllegalArgumentException malformed) {
            return true;
        }
    }

    /**
     * Pops a mite back through into the mirror: particles imploding onto it and a portal pop, shown
     * to whoever could see it — everyone for a breached mite, only phased players otherwise.
     */
    public static void recall(ServerLevel level, MirrorEndermite mite) {
        double y = mite.getY() + 0.2;
        for (ServerPlayer player : level.players()) {
            if (!mite.realTo(player)) continue;
            level.sendParticles(player, ParticleTypes.PORTAL, false, false, mite.getX(), y, mite.getZ(),
                    40, 0.0, 0.0, 0.0, 1.0);
            level.sendParticles(player, ParticleTypes.REVERSE_PORTAL, false, false, mite.getX(), y, mite.getZ(),
                    12, 0.15, 0.15, 0.15, 0.05);
        }
        mite.playSound(ModSounds.MITE_RECALL.get(), 1.0f, 1.4f);
        mite.discard();
    }

    /** Containment freezes a rift but never closes it — that still takes a trip through the mirror. */
    private static void grow(ServerLevel level, FluxRift rift) {
        QuantumFlux.Neighbourhood field = QuantumFlux.chunk(level, rift.anchor());
        int ceiling = ceiling(field.fluxBand());
        if (rift.stage() >= ceiling) return;
        double seconds = Config.fluxRiftStageSeconds(field.anomalyBand());
        if (seconds <= 0.0) return;
        double growth = rift.growth() + 1.0 / seconds;
        if (growth < 1.0) {
            rift.setGrowth(growth);
            return;
        }
        rift.setStage(rift.stage() + 1);
        markDirty(level);
    }

    private static void growHeld(ServerLevel level, FluxRift rift) {
        if (rift.stage() >= FluxRift.MAX_STAGE) return;
        double growth = rift.growth() + 1.0 / STABILISED_STAGE_SECONDS;
        if (growth < 1.0) {
            rift.setGrowth(growth);
            return;
        }
        rift.setStage(rift.stage() + 1);
        markDirty(level);
    }

    /** How big the field lets a rift get. A quiet world cannot feed a wound past stage two. */
    private static int ceiling(FluxBand flux) {
        return switch (flux) {
            case LOW, MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL, SINGULARITY -> FluxRift.MAX_STAGE;
        };
    }

    private static void keepTearOpen(ServerLevel level, FluxRift rift, long now) {
        UUID tear = rift.tear();
        if (tear != null && WorldRiftManager.isOpen(level, tear)) return;
        if (tear != null) {
            rift.setTear(null);
            rift.setNextTearTick(now + TEAR_GAP_TICKS);
            return;
        }
        if (now < rift.nextTearTick()) return;
        UUID opened = WorldRiftManager.spawnEntryNear(level, rift.anchor());
        if (opened != null) {
            rift.setTear(opened);
        } else {
            rift.setNextTearTick(now + TEAR_GAP_TICKS);
        }
    }

    /** A rift being decohered spits mites out at whoever is channelling. Bigger rifts, more of them. */
    private static void fightBack(ServerLevel level, FluxRift rift, long now) {
        int stage = rift.stage();
        if (level.getRandom().nextDouble() >= 0.25 + 0.15 * stage) return;
        int nearby = mitesOf(level, rift, 20.0, false).size();
        if (nearby >= 1 + stage) return;
        // Left to a Projector, the rift throws its mites at the Projector, which has to turn and deal
        // with them: the drain stalls while it does.
        BlockPos projector = rift.projector(now, DRAIN_WINDOW);
        MirrorEndermite mite = MirrorMiteSpawner.spawnNear(level, projector != null ? projector : rift.anchor());
        if (mite == null) return;
        markSpawned(mite, rift);
        ServerPlayer channeller = nearestPhased(level, rift.centre(), 32.0);
        if (channeller != null) mite.provoke(channeller);
    }

    /**
     * The realms snap apart as the wound closes, and anyone standing in the mirror near it is thrown
     * back out — including the breaker, after the residue is in their hands.
     */
    private static void collapse(ServerLevel level, FluxRift rift, ServerPlayer breaker) {
        remove(level, rift);
        int count = RESIDUE[Math.min(RESIDUE.length, rift.stage()) - 1];
        ItemStack residue = new ItemStack(ModItems.RIFT_RESIDUE.get(), count);
        if (!breaker.getInventory().add(residue) && !residue.isEmpty()) {
            Vec3 at = rift.centre();
            ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, residue);
            MirrorPhaseInteractions.markMirrorDrop(drop, breaker);
            level.addFreshEntity(drop);
        }
        ejectNear(level, rift);
    }

    /** The realms snap apart as a wound closes: phased players near it are thrown home. */
    private static void ejectNear(ServerLevel level, FluxRift rift) {
        double rangeSq = EJECT_RANGE * EJECT_RANGE;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (!MirrorPhase.isPhased(player) || player.distanceToSqr(rift.centre()) > rangeSq) continue;
            MirrorPhase.exit(player, MirrorPhase.ExitReason.RIFT);
        }
    }

    /** Gone outright: no residue, no crack, no ejection. Its mites are still recalled. */
    public static void remove(ServerLevel level, FluxRift rift) {
        data(level).remove(rift.id());
        recallMites(level, rift);
        markDirty(level);
    }

    /**
     * Mites a rift spat out — in the mirror or breached into the world — pop back through when it
     * closes. Any that wandered out of this box are caught by {@link #orphaned} on their next tick.
     */
    private static void recallMites(ServerLevel level, FluxRift rift) {
        for (MirrorEndermite mite : mitesOf(level, rift, 64.0, false)) recall(level, mite);
    }

    @Nullable
    private static ServerPlayer nearestPhased(ServerLevel level, Vec3 pos, double range) {
        ServerPlayer best = null;
        double bestSq = range * range;
        for (ServerPlayer player : level.players()) {
            if (!MirrorPhase.isPhased(player)) continue;
            double distanceSq = player.distanceToSqr(pos);
            if (distanceSq < bestSq) {
                best = player;
                bestSq = distanceSq;
            }
        }
        return best;
    }

    private static void markDirty(ServerLevel level) {
        DIRTY.add(level);
    }

    public static void syncPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot(player.level()));
    }

    private static void syncLevel(ServerLevel level) {
        FluxRiftSyncPayload payload = snapshot(level);
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, payload);
    }

    private static FluxRiftSyncPayload snapshot(ServerLevel level) {
        FluxRiftData data = level.getExistingData(ModAttachments.FLUX_RIFTS).orElse(null);
        if (data == null || data.isEmpty()) return new FluxRiftSyncPayload(List.of());
        long now = level.getGameTime();
        List<FluxRiftSyncPayload.Snapshot> rifts = new ArrayList<>();
        for (FluxRift rift : data.rifts()) {
            rifts.add(new FluxRiftSyncPayload.Snapshot(rift.id(), rift.anchor(), rift.shapeSeed(),
                    rift.stage(), rift.coherence(), rift.beingDrained(now, FEEDBACK_WINDOW), rift.stabilised(now)));
        }
        return new FluxRiftSyncPayload(List.copyOf(rifts));
    }

    private static FluxRiftData data(ServerLevel level) {
        return level.getData(ModAttachments.FLUX_RIFTS);
    }
}
