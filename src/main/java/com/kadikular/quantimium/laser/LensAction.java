package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.init.ModDamageTypes;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One action one beam carries (see {@link Light.Stream}): breaking the block it ends on, hurting what
 * stands in it, or gathering items lying in it. Only the sources that pay for the stream pay for it,
 * and only for work done: a Harvest beam's power goes into cutting the block, a cut at a time, so a
 * block that goes before it's through takes the cut with it, as a player's mining would; a hit or an
 * item carried is paid for when it happens. It works only while the beam is lit. What it's done so far
 * is kept while the network is rebuilt; a world reload starts it over.
 */
public final class LensAction {

    /** Which: its node, which of the node's beams, and which action. */
    public record Key(BlockPos node, int beam, Lens action) {}

    private final Key key;
    private LaserBeam beam;
    private Light.Stream stream;
    /** Where what it brings back goes: the collecting Laser Ports up its own stream's light, nearest first. */
    private List<BlockPos> collectors = List.of();
    /** Where what it places comes from: the sending Laser Ports up its own stream's light, nearest first. */
    private List<BlockPos> senders = List.of();
    /** For an area prism's action: the blocks it works on, instead of the beam's end; null for a beam's. */
    @Nullable
    private net.minecraft.world.level.levelgen.structure.BoundingBox area;
    /** Which of the box's blocks are in the area: all of a picked area or a cube's; a sphere's, not its corners. */
    private java.util.function.Predicate<BlockPos> within = pos -> true;
    /** How far through the area it's looked, and the block it's found to work on next. */
    private int cursor;
    /** Blocks looked at in a row with nothing to do, and till when it rests after a whole fruitless sweep. */
    private int missed;
    private long restUntil;
    private int placeCooldown;
    /** For an area prism's action: the block it last placed, and until when it shows a beam there. */
    @Nullable
    private BlockPos placed;
    /** When it last did any work (a cut, a block placed, a hit): an area prism shows its beam only while it's working. */
    private long lastWorked = Long.MIN_VALUE;
    /** How far the light carrying it reaches: an area prism works only that far. */
    private int reach = LaserTuning.ACTION_REACH;
    /** The marks of the light carrying it ({@link Light#PHANTOM}, {@link Light#MIRRORED}). */
    private int marks;
    /** For Phantom light's upkeep: the solid blocks its beam passes through. */
    private int solids;
    private long now;

    /** The block being cut, what it costs, and the FE gone into it so far. */
    @Nullable
    private BlockPos cutting;
    /** Insert and Extract: ticks it waited last, longer while idle, shorter while busy; 0 before it first looks. */
    private int wait;
    /** The last block it saw along a clear line, and until when that's taken as still so. */
    @Nullable
    private BlockPos seen;
    private long seenUntil;
    /** Blocks it looked for and couldn't see, until when it won't look again. */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap unseen = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private long cutCost;
    private long cut;
    private int cooldown;
    /** FE the cut, or the next hits, want this tick. */
    private long want;
    /** FE drawn towards the next Veil Thread, by mirrored Pickup from a held Veiled. */
    private long threadCut;
    /** FE charged towards the next hits: a hit can cost more than a tick's light, so it charges up, and keeps what it has. */
    private long charged;
    /** What it saw in the beam at its last sweep. */
    private List<LivingEntity> targets = new ArrayList<>();

    LensAction(Key key) {
        this.key = key;
        // Insert and Extract look again and again for something to move: staggered from the start, so those made in the
        // same rebuild don't all look in the same tick.
        if (key.action() == Lens.INSERT || key.action() == Lens.EXTRACT) placeCooldown = Math.floorMod(key.hashCode(), LaserTuning.IDLE_RETRY);
    }

    /**
     * The wait after a look: idle, twice the last, up to {@link com.kadikular.quantimium.Config#laserIdleTicks()}; after a move, half the last, down
     * to its own pace. A little of its own offset keeps idle beams from lining up.
     */
    private int nextWait(boolean moved) {
        int pace = Math.max(1, LaserTuning.PLACE_COOLDOWN / stream.pace());
        int most = com.kadikular.quantimium.Config.laserIdleTicks();
        int last = wait <= 0 ? most : wait;
        wait = moved ? Math.max(pace, last / 2) : Math.min(most, Math.max(pace, last * 2));
        return moved ? wait : wait + Math.floorMod(key.hashCode(), 4);
    }

    public Key key() {
        return key;
    }

    void update(@Nullable LaserBeam beam, Light.Stream stream, List<BlockPos> collectors, List<BlockPos> senders,
                @Nullable net.minecraft.world.level.levelgen.structure.BoundingBox area, int reach, int marks, int solids) {
        // A new stream may cut with another tool, and let other things through its filter.
        if (!stream.equals(this.stream)) {
            tool = null;
            cuts.clear();
        }
        this.reach = reach;
        this.marks = marks;
        this.solids = solids;
        this.beam = beam;
        this.stream = stream;
        this.collectors = List.copyOf(collectors);
        this.senders = List.copyOf(senders);
        if (!java.util.Objects.equals(this.area, area)) {
            // A new area starts over; the same one, first seen since a reload, picks up where it was.
            if (this.area != null) cursor = 0;
            else cursorRead = false;
        }
        this.area = area;
    }

    /** Smelting and Crushing working on the world on this light: what they'd still change, Harvest leaves to them. */
    private List<Lens> worldSteps = List.of();

    void worldSteps(List<Lens> steps) {
        worldSteps = steps;
    }

    /**
     * What an action costs over its own price: twice, carried by Phantom light, on top of the light's upkeep for
     * the blocks it passes through.
     */
    int costFactor() {
        return (marks & Light.PHANTOM) != 0 && key.action() != Lens.PHANTOM ? 2 : 1;
    }

    /** What it asks of the network this tick: only a cut in progress asks; the rest pay as they act. */
    long demand() {
        return want * costFactor();
    }

    /** Takes up to {@code fe} into the cut; what it took. Phantom light's cuts take twice what they get. */
    long fill(long fe) {
        int factor = costFactor();
        long took = Math.min(fe, want * factor);
        long into = took / factor;
        if (into > 0 && key.action() == Lens.HARVEST) lastWorked = now;
        if (key.action() == Lens.DAMAGE || key.action() == Lens.HEAL || key.action() == Lens.FREEZE) charged += into;
        else if (key.action() == Lens.HARVEST) cut += into;
        else if (key.action() == Lens.PICKUP) threadCut += into;
        want -= into;
        return took;
    }

    /** Whether the area's cursor has been read back from the prism since this action was made. */
    private boolean cursorRead;

    /**
     * An Area Prism keeps where its actions had got to: read once, written back each second, so after a reload
     * it carries on rather than sweeping the area it already cleared.
     */
    private void keepCursor(ServerLevel level) {
        if (area == null || !(level.getBlockEntity(key.node()) instanceof com.kadikular.quantimium.block.entity.AreaPrismBlockEntity prism)) return;
        String name = key.action().name();
        if (prism.scanOrder() != scanOrder) {
            scanOrder = prism.scanOrder();
            columns = null;
            // A new order starts the area over; the order it was in, read back after a reload, carries on.
            if (cursorRead) cursor = 0;
        }
        if (!cursorRead) {
            cursorRead = true;
            cursor = Math.floorMod(prism.cursor(name), Math.max(1, areaVolume()));
        } else if (now % 20 == 0) {
            prism.setCursor(name, cursor);
        }
    }

    /** The level, while it acts: for sight lines from inside entity filters. */
    @Nullable
    private ServerLevel sightLevel;

    /** Once a tick, before blocks are charged: work done at once pays first, and a cut asks for its next share. */
    void act(ServerLevel level, LaserNetworks networks) {
        sightLevel = level;
        now = level.getGameTime();
        keepCursor(level);
        boolean pickup = stream.has(Lens.PICKUP) && !collectors.isEmpty();
        // With Void, what's freed is never left lying: carried where there's room, destroyed where there isn't.
        boolean voids = stream.has(Lens.VOID);
        int phase = Math.floorMod(key.hashCode() + (int) level.getGameTime(), LaserTuning.DAMAGE_INTERVAL);
        switch (key.action()) {
            case HARVEST -> harvest(level, networks, pickup);
            case DAMAGE -> damage(level, networks, phase == 0, pickup || voids);
            case HEAL -> {
                // Each Speed lens heals twice as often, up to four times: every 10, 5 or 2 ticks.
                int every = Math.max(1, LaserTuning.DAMAGE_INTERVAL / Math.min(LaserTuning.HEAL_FASTEST, stream.pace()));
                heal(level, networks, Math.floorMod(key.hashCode() + (int) level.getGameTime(), every) == 0, pickup || voids);
            }
            case FREEZE -> {
                if (!mirrored()) freeze(level, networks, phase == 0);
            }
            case PICKUP -> {
                // In the mirror, Pickup draws Veil Thread out of a held Veiled its light reaches.
                if (mirrored()) drawThread(level, networks);
                if (phase != 0 || collectors.isEmpty() && !voids) return;
                // Items are small: anything within nearly a block of the beam's line counts. Each light takes
                // only what lies on its own side.
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45)
                        && com.kadikular.quantimium.event.MirrorPhaseInteractions.mirrorOnly(item) == mirrored()
                        && (stream.filter() == null || stream.filter().lets(item.getItem())))) {
                    carry(networks, item, !FREED.contains(item));
                }
            }
            // Nothing in the mirror to place, fill or empty.
            case PLACE -> {
                if (!mirrored()) place(level, networks);
            }
            case INSERT, EXTRACT -> {
                if (!mirrored()) move(level, networks, key.action() == Lens.INSERT);
            }
            case INTERACT -> {
                if (!mirrored()) interact(level, networks);
            }
            case ACCELERATE -> {
                if (!mirrored()) accelerate(level, networks);
            }
            case SMELT, CRUSH -> {
                if (!mirrored()) processAt(level, networks);
            }
            // Phantom light's upkeep: so much a tick for each block it passes through, while it's lit.
            case PHANTOM -> want = cutCost = (long) solids * com.kadikular.quantimium.Config.phantomFePerBlock();
            default -> {}
        }
    }

    // ---- hitting ----

    /**
     * Charges up for the next hits while there's something to hit, and hits each sweep with what it's
     * charged: each Amplify lens doubles a hit, and what it costs, so a hit can take more than one tick's
     * light to charge. What's charged is kept, never lost, up to one round of hits.
     */
    private void damage(ServerLevel level, LaserNetworks networks, boolean sweep, boolean pickup) {
        int scale = 1 << stream.amps();
        long hit = LaserTuning.DAMAGE_FE_PER_HIT * scale;
        if (sweep) {
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive()
                    && onBeam(entity.getBoundingBox(), 0.25) && inMirror(entity) == mirrored()
                    && (stream.filter() == null || stream.filter().letsHit(entity)));
            // A Filter's keep count: that many are spared, and only those beyond it are hit. Always the same
            // ones spared (the oldest), so a hit is finished on one rather than spread over the herd.
            int keep = stream.filter() == null ? 0 : stream.filter().keep();
            if (keep > 0) found = found.stream().sorted(java.util.Comparator.comparingInt(LivingEntity::getId).reversed()).toList();
            targets = found.size() <= keep ? new ArrayList<>() : new ArrayList<>(found.subList(0, found.size() - keep));
            // With Harmonic lenses it hits as a hand holding Looting would, so what it kills drops more.
            LaserHand looter = stream.fortune() > 0 && !targets.isEmpty() ? lootingHand(level) : null;
            var source = looter == null ? level.damageSources().source(ModDamageTypes.LASER)
                    : level.damageSources().source(ModDamageTypes.LASER, looter);
            for (LivingEntity entity : targets) {
                if (charged < hit) break;
                if (entity.hurtServer(level, source, LaserTuning.DAMAGE_PER_HIT * scale)) {
                    charged -= hit;
                    networks.workDone(this);
                    lastWorked = now;
                    // Killed, with Pickup: what it drops goes straight back up the beam, wherever it lands.
                    if (pickup && !entity.isAlive()) gatherDrops(level, networks, entity);
                    // Smelting or Crushing, without Pickup: what it drops is cooked (or crushed) where it falls.
                    else if (!entity.isAlive() && processes()) processDrops(level, networks, entity);
                }
            }
            if (looter != null) looter.letGo();
        }
        targets.removeIf(entity -> !entity.isAlive());
        want = Math.max(0, hit * targets.size() - charged);
    }

    /**
     * Heals what's living in the beam each sweep, charged up as Damage's hits are; the undead it hurts instead,
     * as Instant Health does. What's whole is left alone and costs nothing.
     */
    private void heal(ServerLevel level, LaserNetworks networks, boolean sweep, boolean pickup) {
        int scale = 1 << stream.amps();
        long cost = LaserTuning.HEAL_FE_PER_SWEEP * scale;
        if (sweep) {
            targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive()
                    && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)
                    && (entity.isInvertedHealAndHarm() || entity.getHealth() < entity.getMaxHealth())
                    && onBeam(entity.getBoundingBox(), 0.25) && inMirror(entity) == mirrored()
                    && (stream.filter() == null || stream.filter().letsHit(entity))));
            for (LivingEntity entity : targets) {
                if (charged < cost) break;
                if (entity.isInvertedHealAndHarm()) {
                    if (!entity.hurtServer(level, level.damageSources().magic(), LaserTuning.HEAL_HARM_PER_SWEEP * scale)) continue;
                    if (pickup && !entity.isAlive()) gatherDrops(level, networks, entity);
                    else if (!entity.isAlive() && processes()) processDrops(level, networks, entity);
                } else {
                    entity.heal(LaserTuning.HEAL_PER_SWEEP * scale);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3,
                            entity.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                }
                charged -= cost;
                networks.workDone(this);
                lastWorked = now;
            }
        }
        targets.removeIf(entity -> !entity.isAlive() || !entity.isInvertedHealAndHarm() && entity.getHealth() >= entity.getMaxHealth());
        want = Math.max(0, cost * targets.size() - charged);
    }

    // ---- freezing ----

    /**
     * Each tick, holds the machines it's on still. Each sweep: freezes mobs in the beam in place (again once half their
     * time is gone), chills players with powder snow's freeze, kept short of hurting; turns water it ends on to ice, and
     * now and then lays snow on top of what it ends on. Through an Area Prism, the area's machines and things living
     * there, and a few of its blocks at random each sweep.
     */
    private void freeze(ServerLevel level, LaserNetworks networks, boolean sweep) {
        for (BlockPos pos : freezeTargets(level)) {
            if (!networks.canPay(this, LaserTuning.FREEZE_MACHINE_FE)) break;
            Freezing.hold(level, pos);
            networks.pay(this, LaserTuning.FREEZE_MACHINE_FE);
            lastWorked = now;
        }
        chill(networks);
        if (!sweep) {
            want = Math.max(0, freezeWanted() - charged);
            return;
        }
        targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive()
                && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && onBeam(entity.getBoundingBox(), 0.25) && inMirror(entity) == mirrored()
                && (stream.filter() == null || stream.filter().letsHit(entity))));
        for (LivingEntity entity : targets) {
            if (Freezing.freezable(entity)) {
                // A mob's freeze can cost more than a tick's light: charged up as Damage's hits are, and kept.
                net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) entity;
                if (Freezing.left(mob) > LaserTuning.FREEZE_MOB_TICKS / 2) continue;
                long cost = freezeCost(mob);
                if (charged < cost) continue;
                charged -= cost;
                Freezing.freeze(mob, LaserTuning.FREEZE_MOB_TICKS);
                networks.workDone(this);
                lastWorked = now;
            }
        }
        want = Math.max(0, freezeWanted() - charged);
        if (area == null) {
            if (beam != null && beam.hit() != null) freezeBlock(level, networks, beam.hit());
            return;
        }
        for (int i = 0; i < LaserTuning.FREEZE_AREA_BLOCKS * stream.pace(); i++) {
            BlockPos pos = new BlockPos(area.minX() + level.getRandom().nextInt(area.getXSpan()),
                    area.minY() + level.getRandom().nextInt(area.getYSpan()), area.minZ() + level.getRandom().nextInt(area.getZSpan()));
            if (within.test(pos) && level.isLoaded(pos) && !level.getBlockState(pos).isAir() && inSight(level, pos)) freezeBlock(level, networks, pos);
        }
    }

    /**
     * Each tick, players (and anything else living it doesn't freeze in place) it found in the beam get colder, as in
     * powder snow: slower the colder, but never so frozen it hurts.
     */
    private void chill(LaserNetworks networks) {
        for (LivingEntity entity : targets) {
            if (!entity.isAlive() || Freezing.freezable(entity) || !entity.canFreeze() || !onBeam(entity.getBoundingBox(), 0.25)) continue;
            if (!networks.canPay(this, LaserTuning.FREEZE_CHILL_FE)) return;
            networks.pay(this, LaserTuning.FREEZE_CHILL_FE);
            entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() - 1, entity.getTicksFrozen() + LaserTuning.FREEZE_CHILL_PER_TICK));
            lastWorked = now;
        }
    }

    /** FE to freeze {@code mob} in place. */
    private static long freezeCost(net.minecraft.world.entity.Mob mob) {
        return (long) Math.ceil(mob.getMaxHealth()) * LaserTuning.FREEZE_FE_PER_HEALTH;
    }

    /** What freezing the mobs it last saw that are due again would cost: what it charges up towards. */
    private long freezeWanted() {
        long wanted = 0;
        for (LivingEntity entity : targets) {
            if (entity.isAlive() && entity instanceof net.minecraft.world.entity.Mob mob && Freezing.freezable(mob)
                    && Freezing.left(mob) <= LaserTuning.FREEZE_MOB_TICKS / 2) wanted += freezeCost(mob);
        }
        return wanted;
    }

    /** Water to ice; on anything else, now and then, a layer of snow on top where it would lie. */
    private void freezeBlock(ServerLevel level, LaserNetworks networks, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // A pack's not-freezable blocks get neither ice nor snow.
        if (state.is(com.kadikular.quantimium.init.ModTags.NOT_FREEZABLE)) return;
        BlockState into;
        BlockPos at = pos;
        if (state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().isSource()) {
            into = net.minecraft.world.level.block.Blocks.ICE.defaultBlockState();
        } else {
            at = pos.above();
            into = net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState();
            if (level.getRandom().nextFloat() >= LaserTuning.FREEZE_SNOW_CHANCE || !level.getBlockState(at).isAir()
                    || !into.canSurvive(level, at)) return;
        }
        if (!networks.canPay(this, LaserTuning.FREEZE_BLOCK_FE)) return;
        networks.pay(this, LaserTuning.FREEZE_BLOCK_FE);
        level.setBlockAndUpdate(at, into);
        networks.workDone(this);
        lastWorked = now;
    }

    /** The machines it holds still: the one the beam ends on, or each in the area (looked for again every two seconds). */
    private List<BlockPos> freezeTargets(ServerLevel level) {
        if (area == null) {
            if (beam == null || beam.hit() == null || !Freezing.freezable(level.getBlockEntity(beam.hit()))) return List.of();
            return List.of(beam.hit());
        }
        if (now - machinesFound >= 40) {
            machinesFound = now;
            List<BlockPos> found = new ArrayList<>();
            for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
                for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                        if (area.isInside(pos) && within.test(pos) && Freezing.freezable(level.getBlockEntity(pos))) found.add(pos.immutable());
                    }
                }
            }
            machines = found;
        }
        return machines;
    }

    /** The laser's hand, at the beam's source, holding a blade with Looting for each Harmonic lens. */
    private LaserHand lootingHand(ServerLevel level) {
        LaserHand hand = LaserHand.of(level);
        BlockPos at = beam != null ? beam.from() : key.node();
        hand.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ItemStack blade = new ItemStack(Items.STICK);
        blade.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING), stream.fortune());
        hand.hold(blade);
        return hand;
    }

    // ---- interacting ----

    /**
     * Uses one thing a sending Laser Port up the beam sends (or an empty hand, with none) on what the beam ends on, as a
     * player would: set to use, a right click on the block (a door, a lever, an item on a block; an empty
     * bucket fills from the fluid it ends on); set to hit, a swing at the first thing living in the beam.
     * In an area, the same on what's living there, or on its blocks in turn. What's left in hand after goes
     * back up the beam to a collecting Laser Port, or to the sending Laser Port.
     */
    private void interact(ServerLevel level, LaserNetworks networks) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if ((beam == null && area == null) || (area != null && now < restUntil) || !networks.canPay(this, LaserTuning.PLACE_FE)) return;
        // It works with what the sending Laser Port holds where it is: the hand holds a copy, and only once using it has
        // changed it (eaten, filled, worn) is the real one taken and what it became sent on. Unused, nothing moves.
        // Each kind of thing the sending Laser Port holds in turn, till one is used: carrots for the pigs, wheat for the cows, shears.
        List<ItemStack> tried = new ArrayList<>();
        ItemStack sent;
        ItemStack after;
        List<ItemStack> extras;
        boolean done;
        LaserHand hand = handFor(level);
        do {
            sent = senders.isEmpty() ? ItemStack.EMPTY : networks.take(senders, stack -> lets(stack)
                    && tried.stream().noneMatch(seen -> ItemStack.isSameItemSameComponents(seen, stack)), 1, true);
            if (!sent.isEmpty()) tried.add(sent);
            ItemStack held = sent.copy();
            // A tool bound to an Entangled Dock is charged from it as it's used, wherever the dock is.
            if (!held.isEmpty()) com.kadikular.quantimium.block.entity.EntangledDockBlockEntity.topUp(level, held);
            hand.hold(held);
            int targets = stream.targets();
            done = stream.hits()
                    ? targets != LensUse.BLOCKS && swing(level, hand) || targets != LensUse.ENTITIES && mine(level, networks, hand)
                    : use(level, hand, held, targets);
            // Whatever it holds now: a filled bucket, what's left of a stack, a worn sword.
            after = hand.letGo();
            extras = List.copyOf(hand.getInventory().getNonEquipmentItems());
            hand.getInventory().clearContent();
        } while (!done && !sent.isEmpty() && !stream.hits() && tried.size() < LaserTuning.INTERACT_TRIES);
        if (!done) return;
        ItemStack used = sent;
        boolean changed = !ItemStack.matches(after, used) || extras.stream().anyMatch(extra -> !extra.isEmpty());
        if (changed && !used.isEmpty()) networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, used), 1, false);
        if (changed) {
            for (ItemStack extra : extras) settle(level, networks, extra, used);
            settle(level, networks, after, used);
        }
        networks.pay(this, LaserTuning.PLACE_FE);
        networks.workDone(this);
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        if (area == null) placed = beam.hit();
        lastWorked = now;
        // A door opened, a block changed: the beam may reach on, or not. An area's beams don't change.
        if (area == null) LaserNetworks.changed(level, key.node());
    }

    /**
     * Puts what the hand held back where it belongs: what's still what was sent (food nobody ate, a worn
     * sword) back to the sending Laser Port first; anything new (a filled bucket) up to a collecting Laser Port first; else the
     * other; else on the ground.
     */
    private void settle(ServerLevel level, LaserNetworks networks, ItemStack stack, ItemStack sent) {
        if (stack.isEmpty()) return;
        boolean same = stack.is(sent.getItem());
        ItemStack left = same ? networks.returnTo(senders, stack) : stack;
        if (!left.isEmpty() && !collectors.isEmpty()) left = networks.collect(collectors, left);
        if (!same && !left.isEmpty()) left = networks.returnTo(senders, left);
        if (!left.isEmpty()) Block.popResource(level, handSpot(), left);
    }

    /** Where the stand-in player stands: before what the beam ends on, or in an area, by what it's working on. */
    private BlockPos handSpot() {
        if (area != null) return placed != null && area.isInside(placed) ? placed.above() : key.node();
        return beam.hit() != null ? beam.hit().relative(beam.face().getOpposite()) : beam.from();
    }

    /**
     * A right click: with something in hand, on the first thing living in the beam that takes it; else on the block the
     * beam ends on. As far as {@code targets} lets it: blocks, living things, or both.
     */
    private boolean use(ServerLevel level, LaserHand hand, ItemStack held, int targets) {
        if (targets != LensUse.BLOCKS && !held.isEmpty() && useOnLiving(level, hand)) return true;
        if (targets == LensUse.ENTITIES) return false;
        if (area != null) return useInArea(level, hand, held);
        if (beam.hit() == null) return false;
        // Something light passes, just before the end (a lever, a button, a crop), is what it uses; else what it ends on.
        BlockPos before = beam.hit().relative(beam.face().getOpposite());
        BlockPos pos = beam.length() > 0 && !level.getBlockState(before).isAir() ? before : beam.hit();
        BlockState state = level.getBlockState(pos);
        // An empty bucket fills from a fluid's source.
        if (held.is(Items.BUCKET) && state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup && !state.getFluidState().isEmpty()) {
            ItemStack filled = pickup.pickupBlock(hand, level, pos, state);
            if (filled.isEmpty()) return false;
            hand.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, filled);
            return true;
        }
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).relative(beam.face().getOpposite(), 0.5),
                beam.face().getOpposite(), pos, false);
        net.minecraft.world.InteractionResult result = hand.gameMode.useItemOn(hand, level, held, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return result.consumesAction();
    }

    /**
     * Uses what's in hand on what's living in the beam, as a player's right click would: feeding, shearing,
     * milking. A Filter says on what (only grown ones, say). Never a villager, which would wait for a trade
     * nobody makes.
     */
    private boolean useOnLiving(ServerLevel level, LaserHand hand) {
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && entity != hand
                && !(entity instanceof net.minecraft.world.entity.player.Player) && !(entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)
                && onBeam(entity.getBoundingBox(), 0.25) && !inMirror(entity) && (stream.filter() == null || stream.filter().letsHit(entity)));
        for (LivingEntity entity : found) {
            Vec3 at = entity.getBoundingBox().getCenter().subtract(entity.position());
            if (hand.interactOn(entity, net.minecraft.world.InteractionHand.MAIN_HAND, at).consumesAction()) {
                touching(entity);
                return true;
            }
        }
        return false;
    }

    /** A right click on the next block of the area that takes one, from the side facing the prism; after a sweep with none, a rest. */
    private boolean useInArea(ServerLevel level, LaserHand hand, ItemStack held) {
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (within.test(pos) && level.isLoaded(pos) && !level.getBlockState(pos).isAir() && inSight(level, pos)) {
                net.minecraft.core.Direction face = net.minecraft.core.Direction.getApproximateNearest(Vec3.atCenterOf(key.node()).subtract(Vec3.atCenterOf(pos)));
                hand.setPos(pos.relative(face).getX() + 0.5, pos.relative(face).getY(), pos.relative(face).getZ() + 0.5);
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
                if (hand.gameMode.useItemOn(hand, level, held, net.minecraft.world.InteractionHand.MAIN_HAND, hit).consumesAction()) {
                    missed = 0;
                    placed = pos;
                    touched = null;
                    return true;
                }
            }
            if (fruitless()) return false;
        }
        return false;
    }

    /** What an area's Interact last worked on that moves: its beam follows it. */
    @Nullable
    private net.minecraft.world.entity.Entity touched;

    private void touching(net.minecraft.world.entity.Entity entity) {
        touched = entity;
        placed = entity.blockPosition();
    }

    /** A swing at the first thing living in the beam, with what's in hand. */
    private boolean swing(ServerLevel level, LaserHand hand) {
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && entity != hand
                && onBeam(entity.getBoundingBox(), 0.25) && !inMirror(entity) && (stream.filter() == null || stream.filter().letsHit(entity)));
        if (found.isEmpty()) return false;
        hand.attack(found.getFirst());
        touching(found.getFirst());
        return true;
    }

    /**
     * A left click on a block: mines what the beam ends on (or passes just before its end) as a player holding what the
     * hand holds would, its drops falling there, the tool wearing as a player's does. Paid as a Harvest lens's cut is,
     * by the block's hardness. Not in an area, nor in the mirror.
     */
    private boolean mine(ServerLevel level, LaserNetworks networks, LaserHand hand) {
        if (area != null || mirrored() || beam == null || beam.hit() == null) return false;
        BlockPos before = beam.hit().relative(beam.face().getOpposite());
        BlockPos pos = beam.length() > 0 && !level.getBlockState(before).isAir() ? before : beam.hit();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0 || state.is(ModTags.LASER_UNBREAKABLE)) return false;
        long cost = harvestCost(level, pos);
        if (!networks.canPay(this, cost + LaserTuning.PLACE_FE)) return false;
        if (!hand.gameMode.destroyBlock(pos)) return false;
        networks.pay(this, cost);
        return true;
    }

    // ---- acceleration ----

    /** In an area, the machines there it can speed up (or freeze), looked for again every so often; when it last looked. */
    private List<BlockPos> machines = List.of();
    private long machinesFound = Long.MIN_VALUE;

    /**
     * Speeds up the machine the beam ends on (in an area, each machine there): 2x, doubled for each Speed lens, up to
     * the configured most, as far as the light pays for each extra tick and {@link Acceleration}'s time budgets allow.
     */
    private void accelerate(ServerLevel level, LaserNetworks networks) {
        int multiplier = Math.min(com.kadikular.quantimium.Config.accelerationMax(), 2 << Math.max(0, stream.speed()));
        int wanted = multiplier - 1;
        long fe = com.kadikular.quantimium.Config.accelerationFePerExtraTick();
        for (BlockPos pos : accelerationTargets(level)) {
            // As many extra ticks as it can pay for, then as many as the budgets give.
            int afford = wanted;
            while (afford > 0 && fe > 0 && !networks.canPay(this, fe * afford)) afford--;
            if (afford <= 0) return;
            int done = Acceleration.tick(level, pos, afford, multiplier);
            if (done <= 0) continue;
            networks.pay(this, fe * done);
            networks.workDone(this);
            lastWorked = now;
        }
    }

    private List<BlockPos> accelerationTargets(ServerLevel level) {
        if (area == null) {
            if (beam == null || beam.hit() == null) return List.of();
            return List.of(beam.hit());
        }
        if (now - machinesFound >= 40) {
            machinesFound = now;
            List<BlockPos> found = new ArrayList<>();
            for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
                for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                        if (area.isInside(pos) && within.test(pos) && Acceleration.accelerable(level.getBlockEntity(pos))) found.add(pos.immutable());
                    }
                }
            }
            machines = found;
        }
        return machines;
    }

    /** A stand-in player at the beam, facing the way it goes. */
    private LaserHand handFor(ServerLevel level) {
        LaserHand hand = LaserHand.of(level);
        BlockPos at = handSpot();
        hand.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (beam == null) return hand;
        hand.setYRot(beam.face().toYRot());
        hand.setXRot(beam.face() == net.minecraft.core.Direction.UP ? -90 : beam.face() == net.minecraft.core.Direction.DOWN ? 90 : 0);
        return hand;
    }

    // ---- inventories ----

    /**
     * Insert: a stack of what the sending Laser Ports up the beam send, as much as fits, into the inventory the beam
     * ends on. Extract: a stack out of it, as much as the collecting Laser Ports up the beam have room for. In an
     * area, each inventory there in turn. A Filter says what; one stack each wait.
     */
    private void move(ServerLevel level, LaserNetworks networks, boolean in) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if (in ? senders.isEmpty() : collectors.isEmpty()) return;
        long most = LaserTuning.PICKUP_FE_PER_STACK + moveMost() * ((stream.has(Lens.SMELT) ? LaserTuning.SMELT_FE_PER_ITEM : 0)
                + (stream.has(Lens.CRUSH) ? LaserTuning.CRUSH_FE_PER_ITEM : 0));
        if (!networks.canPay(this, most)) return;
        BlockPos at = area == null ? inventoryAtEnd(level) : nextAreaInventory(level);
        if (at == null) return;
        net.minecraft.core.Direction side = area == null ? beam.face().getOpposite() : null;
        net.neoforged.neoforge.items.IItemHandler handler = com.kadikular.quantimium.util.LegacyItems.legacy(
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, at, side));
        if (handler == null) return;
        // What crushing and smelting on the way cost, or -1 for nothing moved.
        // What can go neither in nor back falls out by the inventory: before it, on a beam; on top of it, in an area.
        BlockPos spill = area == null ? beam.hit().relative(beam.face().getOpposite()) : at.above();
        long processing = in ? insertInto(level, networks, handler, spill) : extractFrom(level, networks, handler, spill);
        // Nothing to move (nothing it may take, or no room): it looks again in a while, longer the longer it's idle.
        if (processing < 0) {
            placeCooldown = nextWait(false);
            return;
        }
        networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK + processing);
        networks.workDone(this);
        // Busy, it speeds up: each move in a row halves the wait, down to its own pace.
        placeCooldown = nextWait(true);
        placed = at;
        lastWorked = now;
    }

    /**
     * Into the inventory: what a sending Laser Port sends, crushed and smelted on the way with those lenses. What that
     * cost, or -1 for none moved. What makes several things of one goes one at a time.
     */
    private long insertInto(ServerLevel level, LaserNetworks networks, net.neoforged.neoforge.items.IItemHandler handler, BlockPos spill) {
        ItemStack offered = networks.take(senders, stack -> lets(stack)
                && arriving(level, stack, 1).stream().allMatch(each -> fits(handler, each) == each.getCount()), moveMost(), true);
        if (offered.isEmpty()) return -1;
        Processing.Made made = made(level, offered);
        int count = 1;
        if (made.ofOne().size() == 1) {
            ItemStack one = made.ofOne().getFirst();
            count = fits(handler, times(one, offered.getCount())) / one.getCount();
        }
        if (count <= 0) return -1;
        ItemStack taken = networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, offered), count, false);
        if (taken.isEmpty()) return -1;
        for (ItemStack each : arriving(level, taken, taken.getCount())) {
            ItemStack left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, each, false);
            if (!left.isEmpty()) left = networks.returnTo(senders, left);
            if (!left.isEmpty()) Block.popResource(level, spill, left);
        }
        return made.fe() * taken.getCount();
    }

    /** Out of the inventory, up to a collecting Laser Port: crushed and smelted on the way with those lenses. What that cost, or -1 for none moved. */
    private long extractFrom(ServerLevel level, LaserNetworks networks, net.neoforged.neoforge.items.IItemHandler handler, BlockPos spill) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack there = handler.extractItem(slot, moveMost(), true);
            if (there.isEmpty() || !lets(there)) continue;
            Processing.Made made = made(level, there);
            int room;
            if (made.ofOne().size() == 1) {
                ItemStack one = made.ofOne().getFirst();
                ItemStack comes = times(one, there.getCount());
                if (!networks.roomFor(collectors, comes)) continue;
                room = (comes.getCount() - networks.simulateCollect(collectors, comes).getCount()) / one.getCount();
            } else {
                room = made.ofOne().stream().allMatch(each -> networks.roomForAll(collectors, each)) ? 1 : 0;
            }
            if (room <= 0) continue;
            ItemStack taken = handler.extractItem(slot, room, false);
            for (ItemStack each : arriving(level, taken, taken.getCount())) {
                ItemStack left = networks.collect(collectors, each);
                if (!left.isEmpty()) left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, left, false);
                if (!left.isEmpty()) Block.popResource(level, spill, left);
            }
            return made.fe() * taken.getCount();
        }
        return -1;
    }

    /** The most Insert or Extract moves at once: {@link LaserTuning#MOVE_ITEMS}, doubled for each Speed lens. */
    private int moveMost() {
        return LaserTuning.MOVE_ITEMS << stream.speed();
    }

    /** {@code count} of {@code stack} as it would arrive: crushed and smelted, with those lenses, where they can be. */
    private List<ItemStack> arriving(ServerLevel level, ItemStack stack, int count) {
        Processing.Made made = made(level, stack);
        return made.changed() ? Processing.times(made.ofOne(), count) : List.of(stack.copyWithCount(count));
    }

    /** How much of {@code stack} the inventory would take. */
    private static int fits(net.neoforged.neoforge.items.IItemHandler handler, ItemStack stack) {
        return stack.getCount() - net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, stack.copy(), true).getCount();
    }

    private boolean lets(ItemStack stack) {
        return stream.filter() == null || stream.filter().lets(stack);
    }

    /** The inventory a beam ends on, if it ends on one. */
    @Nullable
    private BlockPos inventoryAtEnd(ServerLevel level) {
        if (beam == null || beam.hit() == null || beam.end() == LaserBeam.End.OPEN) return null;
        return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, beam.hit(), beam.face().getOpposite()) == null
                ? null : beam.hit();
    }

    /** The next inventory in the area it can see, looking on from the last. */
    @Nullable
    private BlockPos nextAreaInventory(ServerLevel level) {
        if (now < restUntil) return null;
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (within.test(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) != null
                    && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, null) != null && inSight(level, pos)) {
                missed = 0;
                return pos;
            }
            if (fruitless()) return null;
        }
        return null;
    }

    // ---- placing ----

    /** Places a block a sending Laser Port up the beam sends, in the space where the beam ends (or the next free one in an area). */
    private void place(ServerLevel level, LaserNetworks networks) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if (senders.isEmpty()) return;
        long cost = area == null ? LaserTuning.PLACE_FE : Math.round(LaserTuning.PLACE_FE * LaserTuning.AREA_COST);
        if (!networks.canPay(this, cost)) return;
        net.minecraft.world.phys.BlockHitResult at = area == null ? placeHit(level) : nextAreaSpot(level, networks);
        if (at == null) return;
        cost += throughCost(level, at.getBlockPos().relative(at.getDirection()));
        if (!networks.canPay(this, cost)) return;
        ItemStack offered = networks.take(senders, stack -> placeable(level, stack, at), 1, true);
        if (offered.isEmpty()) return;
        ItemStack taken = networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, offered), 1, false);
        if (taken.isEmpty()) return;
        boolean done;
        if (taken.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            // A bucket of water or lava empties where the block would go, as a dispenser empties one; the bucket goes back.
            BlockPos spot = at.getBlockPos().relative(at.getDirection());
            done = bucket.emptyContents(null, level, spot, at, taken);
            if (done) {
                ItemStack empty = networks.returnTo(senders, new ItemStack(Items.BUCKET));
                if (!empty.isEmpty()) Block.popResource(level, spot, empty);
                taken = ItemStack.EMPTY;
            }
        } else if (taken.getItem() instanceof net.minecraft.world.item.BlockItem item) {
            done = item.place(new BeamPlaceContext(level, taken, at)).consumesAction();
        } else {
            done = false;
        }
        if (!done) {
            // It wouldn't go after all: back where it came from.
            ItemStack back = networks.returnTo(senders, taken);
            if (!back.isEmpty()) Block.popResource(level, at.getBlockPos().relative(at.getDirection()), back);
            return;
        }
        networks.pay(this, cost);
        networks.workDone(this);
        // Each Speed lens halves the wait for the next.
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        placed = at.getBlockPos().relative(at.getDirection());
        lastWorked = now;
        // A beam now ends on what it placed; an area's beams don't change.
        if (area == null) LaserNetworks.changed(level, key.node());
    }

    /** Where a beam places: against the block it ends on, from its side. */
    @Nullable
    private net.minecraft.world.phys.BlockHitResult placeHit(ServerLevel level) {
        if (beam == null || beam.hit() == null || (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK)) return null;
        BlockPos spot = beam.hit().relative(beam.face().getOpposite());
        if (!level.getBlockState(spot).canBeReplaced()) return null;
        return new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(beam.hit()), beam.face().getOpposite(), beam.hit(), false);
    }

    /** Whether {@code stack} would go in where {@code at} says, and the stream's filter lets it. */
    private boolean placeable(ServerLevel level, ItemStack stack, net.minecraft.world.phys.BlockHitResult at) {
        if (stream.filter() != null && !stream.filter().letsPlace(stack)) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            return bucket.getContent() != net.minecraft.world.level.material.Fluids.EMPTY
                    && level.getBlockState(at.getBlockPos().relative(at.getDirection())).canBeReplaced();
        }
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return false;
        var context = new BeamPlaceContext(level, stack.copyWithCount(1), at);
        if (!context.canPlace()) return false;
        try {
            BlockState state = item.getBlock().getStateForPlacement(context);
            return state != null && state.canSurvive(level, context.getClickedPos());
        } catch (RuntimeException e) {
            // Some block that can't be placed without a player to ask: a lens leaves it be.
            return false;
        }
    }

    /** The next free space in the area something could be placed in: one resting on a block below it. */
    @Nullable
    private net.minecraft.world.phys.BlockHitResult nextAreaSpot(ServerLevel level, LaserNetworks networks) {
        if (now < restUntil) return null;
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (fruitless()) return null;
            if (!within.test(pos) || !level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) continue;
            BlockPos below = pos.below();
            if (level.getBlockState(below).isAir() || !inSight(level, pos)) continue;
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(below), net.minecraft.core.Direction.UP, below, false);
            if (!networks.take(senders, stack -> placeable(level, stack, hit), 1, true).isEmpty()) {
                missed = 0;
                return hit;
            }
        }
        return null;
    }

    /** Counts one more block with nothing to do; after a whole sweep of them, it rests a while. Whether it's resting now. */
    private boolean fruitless() {
        if (++missed < areaVolume()) return false;
        missed = 0;
        restUntil = now + LaserTuning.AREA_REST;
        return true;
    }

    private int areaVolume() {
        return area == null ? 1 : area.getXSpan() * area.getYSpan() * area.getZSpan();
    }

    /** For an area prism's action: which blocks of its box are in its area. */
    void within(java.util.function.Predicate<BlockPos> within) {
        this.within = within;
    }

    /** The {@code index}th block of the area, row by row, bottom up. */
    /** The prism's order for its area, and the columns and layers it gives, worked out once for each. */
    private com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.ScanOrder scanOrder =
            com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.ScanOrder.NEAREST;
    @Nullable
    private int[] columns;
    @Nullable
    private int[] layers;
    @Nullable
    private net.minecraft.world.level.levelgen.structure.BoundingBox orderedArea;

    /**
     * The {@code index}th block of the area in the prism's order: a layer at a time (from the top, from the bottom,
     * or the prism's own height outwards), and in each layer the columns nearest the prism first.
     */
    private BlockPos areaPos(int index) {
        int x = area.getXSpan();
        int z = area.getZSpan();
        int i = Math.floorMod(index, areaVolume());
        if (columns == null || !area.equals(orderedArea)) orderArea();
        int column = columns[i % (x * z)];
        return new BlockPos(area.minX() + column % x, layers[i / (x * z)], area.minZ() + column / x);
    }

    private void orderArea() {
        int x = area.getXSpan();
        int z = area.getZSpan();
        BlockPos from = key.node();
        long[] keyed = new long[x * z];
        for (int c = 0; c < keyed.length; c++) {
            long dx = area.minX() + c % x - from.getX();
            long dz = area.minZ() + c / x - from.getZ();
            keyed[c] = (dx * dx + dz * dz) << 32 | c;
        }
        java.util.Arrays.sort(keyed);
        columns = new int[keyed.length];
        for (int c = 0; c < keyed.length; c++) columns[c] = (int) keyed[c];
        int height = area.getYSpan();
        layers = new int[height];
        for (int l = 0; l < height; l++) {
            layers[l] = switch (scanOrder) {
                case TOP_DOWN -> area.maxY() - l;
                case BOTTOM_UP -> area.minY() + l;
                case NEAREST -> nearestLayer(l);
            };
        }
        orderedArea = area;
    }

    /** The {@code l}th layer out from the prism's height, alternating above and below, clamped into the area. */
    private int nearestLayer(int l) {
        int y = Math.clamp(key.node().getY(), area.minY(), area.maxY());
        int above = area.maxY() - y;
        int below = y - area.minY();
        // Out in rings: 0, +1, -1, +2, -2... while both sides last, then whichever is left.
        int both = Math.min(above, below);
        if (l <= 2 * both) return l == 0 ? y : l % 2 == 1 ? y + (l + 1) / 2 : y - l / 2;
        int beyond = l - 2 * both;
        return above > below ? y + both + beyond : y - both - beyond;
    }

    /** Puts power into cutting the block the beam ends on; breaks it when the cut is through. */
    private void harvest(ServerLevel level, LaserNetworks networks, boolean pickup) {
        if (cooldown > 0) cooldown--;
        // Full up: it rests, longer each time it finds no room, as Insert and Extract do.
        if (now < fullUntil) {
            want = 0;
            return;
        }
        roomShort = false;
        BlockPos target = harvestTarget(level, networks, pickup);
        if (target == null && roomShort) full();
        if (target == null && !roomShort) said = Component.translatable("message.quantimium.spectroscope.harvest.idle");
        if (target == null || !target.equals(cutting)) {
            // A new block, or none: the cut on the old one is lost, as a player's would be.
            if (cutting != null) level.destroyBlockProgress(crackId(), cutting, -1);
            cutting = target;
            cut = 0;
            cutCost = target == null ? 0 : Math.round(harvestCost(level, target) * (1 + stream.amps()) * (area == null ? 1.0 : LaserTuning.AREA_COST))
                    + throughCost(level, target);
        }
        if (target == null) {
            want = 0;
            return;
        }
        // Smelting or Crushing before it on the light do their work on the block first: sandstone becomes glass, then it's cut.
        if (area == null && !worldSteps.isEmpty() && !level.getBlockState(target).getBlock().asItem().equals(Items.AIR)
                && Processing.ofOne(level, key.node(), new ItemStack(level.getBlockState(target).getBlock().asItem()), worldSteps).changed()) {
            want = 0;
            return;
        }
        // With Pickup, it only cuts what it can carry away: no room up the beam for the drops, it waits. Asked afresh
        // the tick it breaks, so what was found room for is what's there.
        if (pickup && !roomForDrops(level, networks, target, cut >= cutCost && cooldown <= 0)) {
            want = 0;
            full();
            return;
        }
        fullWait = 0;
        said = Component.translatable("message.quantimium.spectroscope.harvest.cutting", level.getBlockState(target).getBlock().getName(),
                cutCost <= 0 ? 0 : Math.min(100, cut * 100 / cutCost));
        if (cut >= cutCost && cooldown <= 0) {
            level.destroyBlockProgress(crackId(), target, -1);
            breakBlock(level, networks, target, pickup);
            networks.workDone(this);
            cooldown = LaserTuning.HARVEST_COOLDOWN / stream.pace();
            cutting = null;
            cut = 0;
            want = 0;
            return;
        }
        level.destroyBlockProgress(crackId(), target, (int) Math.min(9, cut * 10 / Math.max(1, cutCost)));
        want = Math.max(0, cutCost - cut);
    }

    /**
     * The block a Harvest beam would break: one light passes through just before its end (a crop on
     * farmland, grass), or else what it ends on, if it can be broken. In an area, the one being cut, or
     * the next that can be. With Pickup, an area or Phantom light passes over what it couldn't carry away.
     */
    @Nullable
    private BlockPos harvestTarget(ServerLevel level, LaserNetworks networks, boolean pickup) {
        if (area != null) {
            if (cutting != null && area.isInside(cutting) && cuttable(level, cutting) && inSight(level, cutting)) return cutting;
            if (now < restUntil) return null;
            int traces = LaserTuning.AREA_TRACES * stream.pace();
            for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
                BlockPos pos = areaPos(cursor);
                if (within.test(pos) && level.isLoaded(pos) && cuttable(level, pos)) {
                    // Out of traces for this tick: this one is looked at first next tick.
                    if (traces-- <= 0) return null;
                    if (inSight(level, pos)) {
                        if (!pickup || roomForDrops(level, networks, pos, false)) {
                            cursor = (cursor + 1) % areaVolume();
                            missed = 0;
                            return pos;
                        }
                        roomShort = true;
                    }
                }
                cursor = (cursor + 1) % areaVolume();
                if (fruitless()) return null;
            }
            return null;
        }
        if (beam == null) return null;
        // Phantom light goes through what it cuts: the first block on its way it may cut (the ore, with a Filter).
        // Mirrored light reaches the crystal it can see in the mirror, wherever on its way.
        if ((marks & Light.PHANTOM) != 0 || mirrored()) {
            if (mirrored() && beam.hit() != null && cuttable(level, beam.hit())) return beam.hit();
            for (BlockPos pos : beam.path()) {
                if (level.isLoaded(pos) && !level.getBlockState(pos).isAir() && cuttable(level, pos)
                        && (!pickup || roomForDrops(level, networks, pos, false))) return pos;
            }
            return null;
        }
        if (beam.hit() == null) return null;
        if (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK) return null;
        BlockPos before = beam.hit().relative(beam.face().getOpposite());
        if (beam.length() > 0 && !level.getBlockState(before).isAir() && cuttable(level, before)) return before;
        return cuttable(level, beam.hit()) ? beam.hit() : null;
    }

    /** Whether a Harvest beam may break the block at {@code pos}: in the mirror, only a fully grown Anomalite crystal. */
    private boolean cuttable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (mirrored()) {
            return state.is(com.kadikular.quantimium.init.ModBlocks.ANOMALITE_CRYSTAL.get())
                    && state.getValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.AGE) >= 3;
        }
        if (state.isAir() || state.hasBlockEntity()) return false;
        Boolean known = cuts.get(state);
        if (known != null) return known;
        boolean may = cuttableState(level, pos, state);
        if (cuts.size() < 512) cuts.put(state, may);
        return may;
    }

    /**
     * No room for what it would cut: rests half a second, then twice as long each time it's still full, up to
     * {@link com.kadikular.quantimium.Config#laserIdleTicks()}. Cutting again, it's back to looking every tick.
     */
    private void full() {
        fullWait = fullWait <= 0 ? 10 : Math.min(com.kadikular.quantimium.Config.laserIdleTicks(), fullWait * 2);
        fullUntil = now + fullWait;
        MutableComponent what = Component.empty();
        for (ItemStack stack : unfit) {
            if (!what.getSiblings().isEmpty()) what.append(", ");
            what.append(stack.getCount() + " ").append(stack.getHoverName());
        }
        said = Component.translatable("message.quantimium.spectroscope.harvest.full", what, String.format(java.util.Locale.ROOT, "%.1f", fullWait / 20.0));
    }

    /** What it last said it was doing, for the Spectroscope; what didn't fit, last it looked. */
    @Nullable
    private Component said;
    private List<ItemStack> unfit = List.of();

    @Nullable
    Component said() {
        return said;
    }

    /** How long it rests when full, and till when; whether this look passed over something for want of room. */
    private int fullWait;
    private long fullUntil;
    private boolean roomShort;

    /** Whether it may cut a block in {@code state}: the same answer wherever one stands, so it's kept. */
    private boolean cuttableState(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) < 0) return false;
        if (state.is(ModTags.LASER_UNBREAKABLE)) return false;
        // Plain, it cuts as an iron pickaxe: what needs better waits for an Amplify lens.
        if (state.requiresCorrectToolForDrops() && !tool(level).isCorrectToolForDrops(state)) return false;
        return stream.filter() == null || stream.filter().lets(state, () -> Block.getDrops(state, level, pos, null, null, tool(level)));
    }

    /** What it cuts as: an iron pickaxe, or with an Amplify lens a diamond one; with Silk Touch for Precision, Fortune for each Harmonic lens. */
    private ItemStack tool(ServerLevel level) {
        if (tool != null) return tool;
        tool = new ItemStack(stream.amps() > 0 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
        var enchantments = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (stream.has(Lens.PRECISION)) tool.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        if (stream.fortune() > 0) tool.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), stream.fortune());
        return tool;
    }

    /** What it cuts with, made once for its stream: asked for every block it looks at. */
    @Nullable
    private ItemStack tool;
    /** Whether it may cut each kind of block it has looked at, for its stream: an area's scan asks again and again. */
    private final java.util.Map<BlockState, Boolean> cuts = new java.util.HashMap<>();

    private long harvestCost(ServerLevel level, BlockPos pos) {
        // A crystal takes fifteen seconds to take apart.
        if (mirrored()) {
            return (long) MirrorHarvest.CRYSTAL_FE_PER_TICK
                    * MirrorHarvest.CRYSTAL_TICKS;
        }
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.ceil(Math.max(1.0f, hardness) * LaserTuning.HARVEST_FE_PER_HARDNESS);
    }

    /**
     * Whether the collecting Laser Ports up the beam would take all of what breaking the block would drop: room for it,
     * and filters that let it in. With Pickup, nothing is cut that would be left lying.
     */
    private boolean roomForDrops(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean fresh) {
        // With Void there's always room: what won't fit is destroyed.
        if (stream.has(Lens.VOID)) return true;
        // All of it at once: two stacks of seeds each fitting alone needn't both fit.
        List<ItemStack> carried = new ArrayList<>();
        for (ItemStack drop : likelyDrops(level, pos)) {
            for (ItemStack stack : processed(level, drop)) {
                boolean merged = false;
                for (ItemStack had : carried) {
                    if (ItemStack.isSameItemSameComponents(had, stack)) {
                        had.grow(stack.getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) carried.add(stack.copy());
            }
        }
        unfit = carried;
        if (carried.size() <= 1) return carried.isEmpty() || roomFor(networks, carried.getFirst(), fresh);
        // More than one thing: they're tried together, so two don't count on the same empty slot.
        if (now - roomAsked >= 10) {
            room.clear();
            roomTogether.clear();
            roomAsked = now;
        }
        List<RoomKey> keys = new ArrayList<>();
        for (ItemStack stack : carried) keys.add(new RoomKey(stack.getItem(), stack.getComponentsPatch(), stack.getCount()));
        Boolean known = fresh ? null : roomTogether.get(keys);
        if (known != null) return known;
        boolean fits = networks.roomForAllTogether(collectors, carried);
        roomTogether.put(keys, fits);
        return fits;
    }

    private final java.util.Map<List<RoomKey>, Boolean> roomTogether = new java.util.HashMap<>();

    /**
     * Whether the collecting Laser Ports would take all of {@code stack}: remembered half a second by item and count
     * (an area's scan and a cut ask again and again, and each ask fills the Ports' inventories for pretend), or
     * asked {@code fresh}.
     */
    private boolean roomFor(LaserNetworks networks, ItemStack stack, boolean fresh) {
        if (now - roomAsked >= 10) {
            room.clear();
            roomTogether.clear();
            roomAsked = now;
        }
        RoomKey key = new RoomKey(stack.getItem(), stack.getComponentsPatch(), stack.getCount());
        Boolean known = fresh ? null : room.get(key);
        if (known != null) return known;
        boolean fits = networks.roomForAll(collectors, stack);
        room.put(key, fits);
        return fits;
    }

    private record RoomKey(net.minecraft.world.item.Item item, net.minecraft.core.component.DataComponentPatch components, int count) {}

    private final java.util.Map<RoomKey, Boolean> room = new java.util.HashMap<>();
    private long roomAsked = Long.MIN_VALUE;

    /** Breaks it as its pickaxe would, the drops going back up the beam with Pickup. */
    private void breakBlock(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean pickup) {
        BlockState state = level.getBlockState(pos);
        // What was rolled to check for room is what it drops: rolled again, a crop could give more seeds than fit.
        List<ItemStack> drops = new ArrayList<>(pickup ? likelyDrops(level, pos) : drops(level, pos));
        rolledAt = null;
        if (mirrored()) {
            // On the mirror's side: the real world hears only the light, and the tear leaks as it always does.
            com.kadikular.quantimium.phase.OverlayBlockEdit.allow(() -> com.kadikular.quantimium.phase.MirrorSounds.breakQuietly(level, pos));
            com.kadikular.quantimium.phase.MirrorSounds.play(level, pos, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.9f);
            MirrorHarvest.leak(level, area == null ? beam.from() : key.node());
        } else {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.removeBlock(pos, false);
        }
        for (ItemStack drop : drops) {
            // Crushed and smelted on the way, picked up or not; with too little light for that, it comes as it is.
            List<ItemStack> out = processed(level, drop);
            long smelt = processCost(level, drop);
            if (smelt > 0 && !networks.canPay(this, smelt + (pickup ? LaserTuning.PICKUP_FE_PER_STACK : 0))) {
                out = List.of(drop);
                smelt = 0;
            }
            List<ItemStack> left = out;
            boolean voids = stream.has(Lens.VOID);
            // Voiding all, nothing is carried: it's all gone.
            if (stream.voidsAll()) continue;
            if (pickup && (voids || out.stream().allMatch(stack -> networks.roomFor(collectors, stack)))
                    && networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK + smelt)) {
                left = out.stream().map(stack -> networks.collect(collectors, stack)).toList();
            } else if (smelt > 0) {
                networks.pay(this, smelt);
            }
            // With Void, what no port took is gone.
            if (voids) continue;
            for (ItemStack stack : left) {
                if (stack.isEmpty()) continue;
                // Cut on the mirror's side, it falls there: only the mirror sees it, or can take it.
                if (mirrored()) com.kadikular.quantimium.event.MirrorPhaseInteractions.dropInMirror(level, pos, stack);
                else setDown(level, pos, stack);
            }
        }
        // A beam now reaches on past where the block was; an area's beams don't change.
        if (area == null) LaserNetworks.changed(level, key.node());
    }

    /** What breaking it gives: a crystal its shards; anything else, what its pickaxe would get. */
    private List<ItemStack> drops(ServerLevel level, BlockPos pos) {
        if (mirrored()) {
            return List.of(new ItemStack(com.kadikular.quantimium.init.ModItems.ANOMALITE_SHARD.get(),
                    MirrorHarvest.SHARDS));
        }
        // Unrealising: ore comes away as Unrealised Matter, one to a block, with no history yet.
        if (stream.has(Lens.UNREALISE) && com.kadikular.quantimium.Config.unrealisingLens()
                && level.getBlockState(pos).is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            return List.of(new ItemStack(com.kadikular.quantimium.init.ModItems.UNREALISED_MATTER.get()));
        }
        return Block.getDrops(level.getBlockState(pos), level, pos, null, null, tool(level));
    }

    /**
     * What breaking the block at {@code pos} will drop, for asking whether there's room for it: rolled for the block
     * as it stands, and again each second it waits (a luckier roll may fit), not every tick it's looked at. With
     * Pickup, the break drops what was rolled, so what was found room for is what comes.
     */
    private List<ItemStack> likelyDrops(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!pos.equals(rolledAt) || state != rolledState || now - rolledTick >= 20) {
            rolledAt = pos.immutable();
            rolledState = state;
            rolledTick = now;
            rolled = drops(level, pos);
        }
        return rolled;
    }

    private long rolledTick;

    @Nullable
    private BlockPos rolledAt;
    @Nullable
    private BlockState rolledState;
    private List<ItemStack> rolled = List.of();

    private boolean mirrored() {
        return (marks & Light.MIRRORED) != 0;
    }

    /** Whether {@code entity} is of the mirror: the Veiled, a mirror mite. Mirrored light hits only those; real light, everything else. */
    private static boolean inMirror(LivingEntity entity) {
        return entity instanceof com.kadikular.quantimium.entity.Veiled || entity instanceof com.kadikular.quantimium.entity.MirrorEndermite;
    }

    // ---- Veil Thread ----

    /** For tests: a thread being drawn is {@code ticksLeft} ticks of drawing from done. */
    void nearlyDrawn(int ticksLeft) {
        if (key.action() != Lens.PICKUP || !mirrored()) return;
        long whole = (long) MirrorHarvest.THREAD_FE_PER_TICK
                * MirrorHarvest.THREAD_TICKS;
        threadCut = Math.max(threadCut, whole - (long) ticksLeft * MirrorHarvest.THREAD_FE_PER_TICK);
    }

    /**
     * Mirrored Pickup whose light reaches into a Containment Hall's cell, through its glass, draws Veil
     * Thread out of the Veiled held there: one hall to one draw at a time, at the
     * same rate, the thread going up the beam to a collecting Laser Port.
     */
    private void drawThread(ServerLevel level, LaserNetworks networks) {
        com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity hall = null;
        if (beam != null) {
            List<BlockPos> reached = new ArrayList<>(beam.path());
            if (beam.hit() != null) reached.add(beam.hit());
            for (BlockPos pos : reached) {
                hall = MirrorHarvest.hallHolding(level, pos);
                if (hall != null) break;
            }
        }
        ItemStack thread = new ItemStack(com.kadikular.quantimium.init.ModItems.VEIL_THREAD.get());
        if (hall == null || !networks.roomForAll(collectors, thread) || !hall.claimThread(key.node(), now)) {
            want = 0;
            return;
        }
        long whole = (long) MirrorHarvest.THREAD_FE_PER_TICK
                * MirrorHarvest.THREAD_TICKS;
        if (threadCut >= whole) {
            threadCut = 0;
            networks.collect(collectors, thread);
            networks.workDone(this);
            lastWorked = now;
            level.playSound(null, key.node(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.6f);
        }
        // Drawn at a set pace: so much a tick, however much light there is.
        want = Math.min(MirrorHarvest.THREAD_FE_PER_TICK, whole - threadCut);
    }

    /**
     * Items light's actions freed and left lying (no room up the beam yet, or no power that tick): Pickup alongside
     * those actions goes on taking these, and only these. Server thread only; forgotten with the item.
     */
    private static final java.util.Set<ItemEntity> FREED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Items a kill just dropped, near where it fell: as far as they scatter on the tick it dies. */
    private void gatherDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(3.0),
                item -> item.isAlive() && item.tickCount <= 1)) {
            FREED.add(item);
            // All of it: the filter chose what to hit, not what it drops.
            carry(networks, item, false);
        }
    }

    /** Crushes and smelts the items a kill just dropped, where they lie. */
    private void processDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.tickCount <= 1)) {
            long cost = processCost(level, item.getItem());
            if (cost > 0 && networks.pay(this, cost)) replaceWith(level, item, processed(level, item.getItem()));
        }
    }

    /** Carries an item up the beam, if a collecting Laser Port has room and the stream's sources can pay; with {@code filtered}, only what its filter lets. */
    private void carry(LaserNetworks networks, ItemEntity item, boolean filtered) {
        ItemStack stack = item.getItem();
        if (filtered && stream.filter() != null && !stream.filter().lets(stack)) return;
        ServerLevel level = (ServerLevel) item.level();
        boolean voids = stream.has(Lens.VOID);
        // Voiding all, or Void with nowhere to carry it: destroyed where it lies, for nothing.
        if (stream.voidsAll() || voids && collectors.isEmpty()) {
            item.discard();
            return;
        }
        List<ItemStack> carried = processed(level, stack);
        if (!voids && !carried.stream().allMatch(each -> networks.roomFor(collectors, each))
                || !networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK + processCost(level, stack))) return;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack each : carried) {
            ItemStack rest = networks.collect(collectors, each.copy());
            if (!rest.isEmpty()) left.add(rest);
        }
        networks.workDone(this);
        // With Void, what no port had room for goes too.
        replaceWith(level, item, voids ? List.of() : left);
    }

    /**
     * Sets {@code stack} down in the middle of where the block was, still: thrown about as a broken block's drops are,
     * it lands off the beam, out of a Pickup's reach.
     */
    private static void setDown(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS)) return;
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, stack, 0, 0, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /** {@code item} becomes {@code stacks}, where it lies: gone with none, more items with more. */
    private static void replaceWith(ServerLevel level, ItemEntity item, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            item.discard();
            return;
        }
        item.setItem(stacks.getFirst());
        for (ItemStack rest : stacks.subList(1, stacks.size())) level.addFreshEntity(new ItemEntity(level, item.getX(), item.getY(), item.getZ(), rest));
    }

    // ---- crushing and smelting ----

    /** Whether this stream crushes or smelts what it frees or moves. */
    private boolean processes() {
        return stream.has(Lens.SMELT) || stream.has(Lens.CRUSH);
    }

    /** What one of {@code stack} becomes with this stream's Crushing and Smelting lenses: nothing, in the mirror. */
    private Processing.Made made(ServerLevel level, ItemStack stack) {
        if (!processes() || stack.isEmpty() || mirrored()) return new Processing.Made(List.of(stack.copyWithCount(1)), 0);
        return Processing.ofOne(level, key.node(), stack, stream.steps());
    }

    /**
     * What Crushing and Smelting lenses make of {@code stack} on its way: one, then the other, in their
     * lenses' order (see {@link Processing}), in stacks no bigger than they may be. What neither changes comes as it is, the same stack.
     */
    private List<ItemStack> processed(ServerLevel level, ItemStack stack) {
        Processing.Made made = made(level, stack);
        return made.changed() ? Processing.times(made.ofOne(), stack.getCount()) : List.of(stack);
    }

    /** Whether Smelting or Crushing alone could change the block at {@code pos}, as its item: one it may break, that its filter lets. */
    private boolean processable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null || state.is(ModTags.LASER_UNBREAKABLE)) return false;
        ItemStack as = new ItemStack(state.getBlock().asItem());
        return !as.isEmpty() && lets(as) && processCost(level, as) > 0;
    }

    /** FE for crushing and smelting {@code stack}: nothing when it comes as it is. */
    private long processCost(ServerLevel level, ItemStack stack) {
        return made(level, stack).fe() * stack.getCount();
    }

    /** {@code count} of what {@code one} is, a stack's worth at most. */
    private static ItemStack times(ItemStack one, int count) {
        return one.copyWithCount(Math.min(one.getMaxStackSize(), one.getCount() * count));
    }

    /**
     * Smelting or Crushing alone: what lies in the beam is done where it lies, a stack at a time; else the
     * block the beam ends on, as its item would be. A block it becomes goes in its place (sand becomes glass,
     * and the light goes on through; stone becomes cobblestone, then gravel, then sand); anything else drops
     * there (an ore's ingot, or its dust).
     */
    private void processAt(ServerLevel level, LaserNetworks networks) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if (beam == null || area != null || !networks.canPay(this, Math.min(LaserTuning.SMELT_FE_PER_ITEM, LaserTuning.CRUSH_FE_PER_ITEM))) return;
        if (!processLying(level, networks) && !processBlock(level, networks)) return;
        networks.workDone(this);
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        lastWorked = now;
    }

    private boolean processLying(ServerLevel level, LaserNetworks networks) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45)
                && !com.kadikular.quantimium.event.MirrorPhaseInteractions.mirrorOnly(item) && lets(item.getItem()))) {
            long cost = processCost(level, item.getItem());
            if (cost == 0 || !networks.pay(this, cost)) continue;
            replaceWith(level, item, processed(level, item.getItem()));
            return true;
        }
        return false;
    }

    private boolean processBlock(ServerLevel level, LaserNetworks networks) {
        BlockPos pos = null;
        // Phantom light goes through what it can't change, to the first block on its way it can.
        if ((marks & Light.PHANTOM) != 0) {
            for (BlockPos on : beam.path()) {
                if (level.isLoaded(on) && processable(level, on)) {
                    pos = on;
                    break;
                }
            }
        }
        if (pos == null) {
            if (beam.hit() == null || (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK)) return false;
            // As Harvest picks: something light passes just before the end (a glass block, a crop), else the end.
            BlockPos before = beam.hit().relative(beam.face().getOpposite());
            pos = beam.length() > 0 && !level.getBlockState(before).isAir() && processable(level, before) ? before : beam.hit();
            if (!processable(level, pos)) return false;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack as = new ItemStack(state.getBlock().asItem());
        long cost = processCost(level, as);
        if (!networks.pay(this, cost)) return false;
        List<ItemStack> out = processed(level, as);
        ItemStack first = out.getFirst();
        if (out.size() == 1 && first.getCount() == 1 && first.getItem() instanceof net.minecraft.world.item.BlockItem block) {
            level.setBlockAndUpdate(pos, block.getBlock().defaultBlockState());
        } else {
            level.removeBlock(pos, false);
            for (ItemStack each : out) Block.popResource(level, pos, each);
        }
        level.playSound(null, pos, stream.has(Lens.SMELT) ? net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH : net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.6f);
        LaserNetworks.changed(level, key.node());
        return true;
    }


    /** The cracks it draws on the block it's cutting: one set per action. */
    private int crackId() {
        return -1 - (key.hashCode() & 0x3FFFFFFF);
    }

    /** Whether the area prism can see {@code pos}: a clear line of light from it, ending there, within its light's reach. */
    private boolean inSight(ServerLevel level, BlockPos pos) {
        // A block it has just seen stays seen a little while: the block it's cutting is looked at every tick.
        if (pos.equals(seen) && now < seenUntil) return true;
        // One it couldn't see stays unseen a while too: an area's scan comes round to the same hidden blocks again and again.
        long key = pos.asLong();
        if (unseen.get(key) > now) return false;
        LaserBeam line = LaserBeam.traceTo(level, this.key.node(), pos, reach, marks);
        boolean sees = pos.equals(line.hit());
        if (sees) {
            seen = pos.immutable();
            seenUntil = now + LaserTuning.SIGHT_KEPT;
        } else {
            if (unseen.size() >= 4096) unseen.clear();
            unseen.put(key, now + LaserTuning.UNSEEN_KEPT);
        }
        return sees;
    }

    /** For tests: what cutting the block it's on costs in all; for Phantom upkeep, what this tick's costs. */
    long cutCost() {
        return cutCost;
    }

    /** For an area prism's Phantom light: what working on {@code pos} costs for the blocks it goes through to get there. */
    private long throughCost(ServerLevel level, BlockPos pos) {
        if (area == null || (marks & Light.PHANTOM) == 0) return 0;
        return (long) LaserBeam.traceTo(level, key.node(), pos, reach, marks).solidsThrough(level) * com.kadikular.quantimium.Config.phantomFePerBlock();
    }

    /** Exactly where in what it's working on the beam goes: the middle of what it's hitting, or of the block. */
    @Nullable
    net.minecraft.world.phys.Vec3 workingPoint(ServerLevel level) {
        BlockPos block = working(level);
        if (block == null) return null;
        if ((key.action() == Lens.DAMAGE || key.action() == Lens.HEAL || key.action() == Lens.FREEZE) && !targets.isEmpty()) return targets.getFirst().getBoundingBox().getCenter();
        if (key.action() == Lens.INTERACT && touched != null && touched.isAlive()) return touched.getBoundingBox().getCenter();
        return block.getCenter();
    }

    /** What an area prism's action is working on just now, for the beam it shows: or null. */
    @Nullable
    BlockPos working(ServerLevel level) {
        // Only while it's working: not while it waits for room, or for anything to do.
        if (area == null || level.getGameTime() - lastWorked > LaserTuning.PLACE_COOLDOWN) return null;
        return switch (key.action()) {
            case HARVEST -> cutting;
            case PLACE, INSERT, EXTRACT -> placed;
            case INTERACT -> touched != null && touched.isAlive() ? touched.blockPosition() : placed;
            // Where what it's hitting is now, so the beam follows it.
            case DAMAGE, HEAL -> targets.isEmpty() ? null : BlockPos.containing(targets.getFirst().getBoundingBox().getCenter());
            default -> null;
        };
    }

    /** Round the beam, or the area, to look for things in it. */
    private AABB box() {
        if (area != null) return AABB.of(area);
        Vec3[] ends = beam.ends();
        return new AABB(ends[0], ends[1]).inflate(1.0);
    }

    /** Whether a box comes within {@code slack} of the beam's line, or for an area, is in it. */
    private boolean onBeam(AABB box, double slack) {
        if (area != null) return box.intersects(AABB.of(area)) && within.test(BlockPos.containing(box.getCenter())) && sightLevel != null
                && inSight(sightLevel, BlockPos.containing(box.getCenter()));
        Vec3[] ends = beam.ends();
        AABB near = box.inflate(slack);
        return near.clip(ends[0], ends[1]).isPresent() || near.contains(ends[0]) || near.contains(ends[1]);
    }

    /** What this stream is and who pays: for the Spectroscope. */
    Light.Stream stream() {
        return stream;
    }

    List<BlockPos> collectors() {
        return collectors;
    }
}
