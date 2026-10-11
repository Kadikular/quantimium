package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.init.ModDamageTypes;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
    /** Where what it brings back goes: the Collectors up its own stream's light, nearest first. */
    private List<BlockPos> collectors = List.of();
    /** Where what it places comes from: the Senders up its own stream's light, nearest first. */
    private List<BlockPos> senders = List.of();
    /** For an area prism's action: the blocks it works on, instead of the beam's end; null for a beam's. */
    @Nullable
    private net.minecraft.world.level.levelgen.structure.BoundingBox area;
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
    }

    public Key key() {
        return key;
    }

    void update(@Nullable LaserBeam beam, Light.Stream stream, List<BlockPos> collectors, List<BlockPos> senders,
                @Nullable net.minecraft.world.level.levelgen.structure.BoundingBox area, int reach, int marks, int solids) {
        this.reach = reach;
        this.marks = marks;
        this.solids = solids;
        this.beam = beam;
        this.stream = stream;
        this.collectors = List.copyOf(collectors);
        this.senders = List.copyOf(senders);
        if (!java.util.Objects.equals(this.area, area)) cursor = 0;
        this.area = area;
    }

    /** What it asks of the network this tick: only a cut in progress asks; the rest pay as they act. */
    long demand() {
        return want;
    }

    /** Takes up to {@code fe} into the cut; what it took. */
    long fill(long fe) {
        long took = Math.min(fe, want);
        if (took > 0 && key.action() == Lens.HARVEST) lastWorked = now;
        if (key.action() == Lens.DAMAGE) charged += took;
        else if (key.action() == Lens.HARVEST) cut += took;
        else if (key.action() == Lens.PICKUP) threadCut += took;
        want -= took;
        return took;
    }

    /** The level, while it acts: for sight lines from inside entity filters. */
    @Nullable
    private ServerLevel sightLevel;

    /** Once a tick, before blocks are charged: work done at once pays first, and a cut asks for its next share. */
    void act(ServerLevel level, LaserNetworks networks) {
        sightLevel = level;
        now = level.getGameTime();
        boolean pickup = stream.has(Lens.PICKUP) && !collectors.isEmpty();
        int phase = Math.floorMod(key.hashCode() + (int) level.getGameTime(), LaserTuning.DAMAGE_INTERVAL);
        switch (key.action()) {
            case HARVEST -> harvest(level, networks, pickup);
            case DAMAGE -> damage(level, networks, phase == 0, pickup);
            case PICKUP -> {
                // In the mirror, Pickup draws Veil Thread out of a held Veiled its light reaches.
                if (mirrored()) {
                    drawThread(level, networks);
                    return;
                }
                if (phase != 0 || collectors.isEmpty()) return;
                // Items are small: anything within nearly a block of the beam's line counts.
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45)
                        && (stream.filter() == null || stream.filter().lets(item.getItem())))) {
                    carry(networks, item, true);
                }
            }
            // Nothing in the mirror to place.
            case PLACE -> {
                if (!mirrored()) place(level, networks);
            }
            // Phantom light's upkeep: so much a tick for each block it passes through, while it's lit.
            case PHANTOM -> want = (long) solids * com.kadikular.quantimium.Config.phantomFePerBlock();
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
            for (LivingEntity entity : targets) {
                if (charged < hit) break;
                if (entity.hurtServer(level, level.damageSources().source(ModDamageTypes.LASER), LaserTuning.DAMAGE_PER_HIT * scale)) {
                    charged -= hit;
                    networks.workDone(this);
                    lastWorked = now;
                    // Killed, with Pickup: what it drops goes straight back up the beam, wherever it lands.
                    if (pickup && !entity.isAlive()) gatherDrops(level, networks, entity);
                }
            }
        }
        targets.removeIf(entity -> !entity.isAlive());
        want = Math.max(0, hit * targets.size() - charged);
    }

    // ---- placing ----

    /** Places a block a Sender up the beam sends, in the space where the beam ends (or the next free one in an area). */
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
            done = item.place(new net.minecraft.world.item.context.BlockPlaceContext(
                    level, null, net.minecraft.world.InteractionHand.MAIN_HAND, taken, at)).consumesAction();
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
        if (area == null) LaserNetworks.changed(level);
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
        if (stream.filter() != null && !stream.filter().lets(stack)) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            return bucket.getContent() != net.minecraft.world.level.material.Fluids.EMPTY
                    && level.getBlockState(at.getBlockPos().relative(at.getDirection())).canBeReplaced();
        }
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return false;
        var context = new net.minecraft.world.item.context.BlockPlaceContext(level, null, net.minecraft.world.InteractionHand.MAIN_HAND, stack.copyWithCount(1), at);
        if (!context.canPlace()) return false;
        BlockState state = item.getBlock().getStateForPlacement(context);
        return state != null && state.canSurvive(level, context.getClickedPos());
    }

    /** The next free space in the area something could be placed in: one resting on a block below it. */
    @Nullable
    private net.minecraft.world.phys.BlockHitResult nextAreaSpot(ServerLevel level, LaserNetworks networks) {
        if (now < restUntil) return null;
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (fruitless()) return null;
            if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) continue;
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

    /** The {@code index}th block of the area, row by row, bottom up. */
    private BlockPos areaPos(int index) {
        int x = area.getXSpan();
        int z = area.getZSpan();
        int i = Math.floorMod(index, areaVolume());
        return new BlockPos(area.minX() + i % x, area.minY() + i / (x * z), area.minZ() + (i / x) % z);
    }

    /** Puts power into cutting the block the beam ends on; breaks it when the cut is through. */
    private void harvest(ServerLevel level, LaserNetworks networks, boolean pickup) {
        if (cooldown > 0) cooldown--;
        BlockPos target = harvestTarget(level);
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
        // With Pickup, it only cuts what it can carry away: no room up the beam for the drops, it waits.
        if (pickup && !roomForDrops(level, networks, target)) {
            want = 0;
            return;
        }
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
     * the next that can be.
     */
    @Nullable
    private BlockPos harvestTarget(ServerLevel level) {
        if (area != null) {
            if (cutting != null && area.isInside(cutting) && cuttable(level, cutting) && inSight(level, cutting)) return cutting;
            if (now < restUntil) return null;
            for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
                BlockPos pos = areaPos(cursor);
                cursor = (cursor + 1) % areaVolume();
                if (level.isLoaded(pos) && cuttable(level, pos) && inSight(level, pos)) {
                    missed = 0;
                    return pos;
                }
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
                if (level.isLoaded(pos) && !level.getBlockState(pos).isAir() && cuttable(level, pos)) return pos;
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
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return false;
        if (state.is(ModTags.LASER_UNBREAKABLE)) return false;
        // Plain, it cuts as an iron pickaxe: what needs better waits for an Amplify lens.
        if (state.requiresCorrectToolForDrops() && !tool().isCorrectToolForDrops(state)) return false;
        return stream.filter() == null || stream.filter().lets(state, () -> Block.getDrops(state, level, pos, null, null, tool()));
    }

    /** What it cuts as: an iron pickaxe, or with an Amplify lens a diamond one. */
    private ItemStack tool() {
        return new ItemStack(stream.amps() > 0 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
    }

    private long harvestCost(ServerLevel level, BlockPos pos) {
        // A crystal takes as long to take apart as a Harvest Laser's does.
        if (mirrored()) {
            return (long) com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.FE_PER_TICK
                    * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.HARVEST_TICKS;
        }
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.ceil(Math.max(1.0f, hardness) * LaserTuning.HARVEST_FE_PER_HARDNESS);
    }

    /**
     * Whether the Collectors up the beam would take all of what breaking the block would drop: room for it,
     * and filters that let it in. With Pickup, nothing is cut that would be left lying.
     */
    private boolean roomForDrops(ServerLevel level, LaserNetworks networks, BlockPos pos) {
        for (ItemStack drop : drops(level, pos)) {
            if (!networks.roomForAll(collectors, drop)) return false;
        }
        return true;
    }

    /** Breaks it as its pickaxe would, the drops going back up the beam with Pickup. */
    private void breakBlock(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean pickup) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = new ArrayList<>(drops(level, pos));
        if (mirrored()) {
            // On the mirror's side: the real world hears only the light, and the tear leaks as a Harvest Laser's does.
            com.kadikular.quantimium.phase.OverlayBlockEdit.allow(() -> com.kadikular.quantimium.phase.MirrorSounds.breakQuietly(level, pos));
            com.kadikular.quantimium.phase.MirrorSounds.play(level, pos, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.9f);
            com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.leakFromTear(level, area == null ? beam.from() : key.node());
        } else {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.removeBlock(pos, false);
        }
        for (ItemStack drop : drops) {
            ItemStack left = drop;
            if (pickup && networks.roomFor(collectors, drop) && networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK)) {
                left = networks.collect(collectors, drop);
            }
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        // A beam now reaches on past where the block was; an area's beams don't change.
        if (area == null) LaserNetworks.changed(level);
    }

    /** What breaking it gives: a crystal its shards, as a Harvest Laser's; anything else, what its pickaxe would get. */
    private List<ItemStack> drops(ServerLevel level, BlockPos pos) {
        if (mirrored()) {
            return List.of(new ItemStack(com.kadikular.quantimium.init.ModItems.ANOMALITE_SHARD.get(),
                    com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.SHARDS));
        }
        return Block.getDrops(level.getBlockState(pos), level, pos, null, null, tool());
    }

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
        long whole = (long) com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK
                * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_TICKS;
        threadCut = Math.max(threadCut, whole - (long) ticksLeft * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK);
    }

    /**
     * Mirrored Pickup whose light reaches into a Containment Hall's cell, through its glass, draws Veil
     * Thread out of the Veiled held there, as a Harvest Laser does: one hall to one draw at a time, at the
     * same rate, the thread going up the beam to a Collector.
     */
    private void drawThread(ServerLevel level, LaserNetworks networks) {
        com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity hall = null;
        if (beam != null) {
            List<BlockPos> reached = new ArrayList<>(beam.path());
            if (beam.hit() != null) reached.add(beam.hit());
            for (BlockPos pos : reached) {
                hall = com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.hallHolding(level, pos);
                if (hall != null) break;
            }
        }
        ItemStack thread = new ItemStack(com.kadikular.quantimium.init.ModItems.VEIL_THREAD.get());
        if (hall == null || !networks.roomForAll(collectors, thread) || !hall.claimThread(key.node(), now)) {
            want = 0;
            return;
        }
        long whole = (long) com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK
                * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_TICKS;
        if (threadCut >= whole) {
            threadCut = 0;
            networks.collect(collectors, thread);
            networks.workDone(this);
            lastWorked = now;
            level.playSound(null, key.node(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.6f);
        }
        // Drawn at a Harvest Laser's pace: so much a tick, however much light there is.
        want = Math.min(com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK, whole - threadCut);
    }

    /** Items a kill just dropped, near where it fell. */
    private void gatherDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.tickCount <= 1)) {
            // All of it: the filter chose what to hit, not what it drops.
            carry(networks, item, false);
        }
    }

    /** Carries an item up the beam, if a Collector has room and the stream's sources can pay; with {@code filtered}, only what its filter lets. */
    private void carry(LaserNetworks networks, ItemEntity item, boolean filtered) {
        ItemStack stack = item.getItem();
        if (filtered && stream.filter() != null && !stream.filter().lets(stack)) return;
        if (!networks.roomFor(collectors, stack) || !networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK)) return;
        ItemStack left = networks.collect(collectors, stack.copy());
        networks.workDone(this);
        if (left.isEmpty()) item.discard();
        else item.setItem(left);
    }

    /** The cracks it draws on the block it's cutting: one set per action. */
    private int crackId() {
        return -1 - (key.hashCode() & 0x3FFFFFFF);
    }

    /** Whether the area prism can see {@code pos}: a clear line of light from it, ending there, within its light's reach. */
    private boolean inSight(ServerLevel level, BlockPos pos) {
        LaserBeam line = LaserBeam.traceTo(level, key.node(), pos, reach, marks);
        return pos.equals(line.hit());
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
        if (key.action() == Lens.DAMAGE && !targets.isEmpty()) return targets.getFirst().getBoundingBox().getCenter();
        return block.getCenter();
    }

    /** What an area prism's action is working on just now, for the beam it shows: or null. */
    @Nullable
    BlockPos working(ServerLevel level) {
        // Only while it's working: not while it waits for room, or for anything to do.
        if (area == null || level.getGameTime() - lastWorked > LaserTuning.PLACE_COOLDOWN) return null;
        return switch (key.action()) {
            case HARVEST -> cutting;
            case PLACE -> placed;
            // Where what it's hitting is now, so the beam follows it.
            case DAMAGE -> targets.isEmpty() ? null : BlockPos.containing(targets.getFirst().getBoundingBox().getCenter());
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
        if (area != null) return box.intersects(AABB.of(area)) && sightLevel != null
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
