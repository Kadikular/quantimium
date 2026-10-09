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
    private int placeCooldown;
    /** For an area prism's action: the block it last placed, and until when it shows a beam there. */
    @Nullable
    private BlockPos placed;
    /** When it last did any work (a cut, a block placed, a hit): an area prism shows its beam only while it's working. */
    private long lastWorked = Long.MIN_VALUE;
    /** How far the light carrying it reaches: an area prism works only that far. */
    private int reach = LaserTuning.ACTION_REACH;
    private long now;

    /** The block being cut, what it costs, and the FE gone into it so far. */
    @Nullable
    private BlockPos cutting;
    private long cutCost;
    private long cut;
    private int cooldown;
    /** FE the cut wants this tick. */
    private long want;
    /** What it saw in the beam at its last sweep. */
    private List<LivingEntity> targets = List.of();

    LensAction(Key key) {
        this.key = key;
    }

    public Key key() {
        return key;
    }

    void update(@Nullable LaserBeam beam, Light.Stream stream, List<BlockPos> collectors, List<BlockPos> senders,
                @Nullable net.minecraft.world.level.levelgen.structure.BoundingBox area, int reach) {
        this.reach = reach;
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
        if (took > 0) lastWorked = now;
        cut += took;
        want -= took;
        return took;
    }

    /** The level, while it acts: for sight lines from inside entity filters. */
    @Nullable
    private ServerLevel sightLevel;

    /** Once a tick, after power has moved. */
    void act(ServerLevel level, LaserNetworks networks) {
        sightLevel = level;
        now = level.getGameTime();
        boolean pickup = stream.has(Lens.PICKUP) && !collectors.isEmpty();
        int phase = Math.floorMod(key.hashCode() + (int) level.getGameTime(), LaserTuning.DAMAGE_INTERVAL);
        switch (key.action()) {
            case HARVEST -> harvest(level, networks, pickup);
            case DAMAGE -> {
                if (phase != 0) return;
                targets = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && onBeam(entity.getBoundingBox(), 0.25)
                        && (stream.filter() == null || stream.filter().letsHit(entity)));
                // Each Amplify lens doubles the hit, and what it costs.
                int scale = 1 << stream.amps();
                for (LivingEntity entity : targets) {
                    if (!networks.pay(this, LaserTuning.DAMAGE_FE_PER_HIT * scale)) break;
                    if (entity.hurtServer(level, level.damageSources().source(ModDamageTypes.LASER), LaserTuning.DAMAGE_PER_HIT * scale)) {
                        networks.workDone(this);
                        lastWorked = now;
                        // Killed, with Pickup: what it drops goes straight back up the beam, wherever it lands.
                        if (pickup && !entity.isAlive()) gatherDrops(level, networks, entity);
                    }
                }
            }
            case PICKUP -> {
                if (phase != 0 || collectors.isEmpty()) return;
                // Items are small: anything within nearly a block of the beam's line counts.
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45)
                        && (stream.filter() == null || stream.filter().lets(item.getItem())))) {
                    carry(networks, item);
                }
            }
            case PLACE -> place(level, networks);
            default -> {}
        }
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
        ItemStack offered = networks.take(senders, stack -> placeable(level, stack, at), 1, true);
        if (offered.isEmpty()) return;
        ItemStack taken = networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, offered), 1, false);
        if (taken.isEmpty() || !(taken.getItem() instanceof net.minecraft.world.item.BlockItem item)) return;
        net.minecraft.world.InteractionResult result = item.place(new net.minecraft.world.item.context.BlockPlaceContext(
                level, null, net.minecraft.world.InteractionHand.MAIN_HAND, taken, at));
        if (!result.consumesAction()) {
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
        LaserNetworks.changed(level);
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
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return false;
        if (stream.filter() != null && !stream.filter().lets(stack)) return false;
        var context = new net.minecraft.world.item.context.BlockPlaceContext(level, null, net.minecraft.world.InteractionHand.MAIN_HAND, stack.copyWithCount(1), at);
        if (!context.canPlace()) return false;
        BlockState state = item.getBlock().getStateForPlacement(context);
        return state != null && state.canSurvive(level, context.getClickedPos());
    }

    /** The next free space in the area something could be placed in: one resting on a block below it. */
    @Nullable
    private net.minecraft.world.phys.BlockHitResult nextAreaSpot(ServerLevel level, LaserNetworks networks) {
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) continue;
            BlockPos below = pos.below();
            if (level.getBlockState(below).isAir() || !inSight(level, pos)) continue;
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(below), net.minecraft.core.Direction.UP, below, false);
            if (!networks.take(senders, stack -> placeable(level, stack, hit), 1, true).isEmpty()) return hit;
        }
        return null;
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
            cutCost = target == null ? 0 : Math.round(harvestCost(level, target) * (1 + stream.amps()) * (area == null ? 1.0 : LaserTuning.AREA_COST));
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
            for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
                BlockPos pos = areaPos(cursor);
                cursor = (cursor + 1) % areaVolume();
                if (level.isLoaded(pos) && cuttable(level, pos) && inSight(level, pos)) return pos;
            }
            return null;
        }
        if (beam == null || beam.hit() == null) return null;
        if (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK) return null;
        BlockPos before = beam.hit().relative(beam.face().getOpposite());
        if (beam.length() > 0 && !level.getBlockState(before).isAir() && cuttable(level, before)) return before;
        return cuttable(level, beam.hit()) ? beam.hit() : null;
    }

    /** Whether a Harvest beam may break the block at {@code pos}. */
    private boolean cuttable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return false;
        if (state.is(ModTags.LASER_UNBREAKABLE)) return false;
        // Plain, it cuts as an iron pickaxe: what needs better waits for an Amplify lens.
        if (state.requiresCorrectToolForDrops() && !tool().isCorrectToolForDrops(state)) return false;
        return stream.filter() == null || stream.filter().lets(state, Block.getDrops(state, level, pos, null, null, tool()));
    }

    /** What it cuts as: an iron pickaxe, or with an Amplify lens a diamond one. */
    private ItemStack tool() {
        return new ItemStack(stream.amps() > 0 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
    }

    private static long harvestCost(ServerLevel level, BlockPos pos) {
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.ceil(Math.max(1.0f, hardness) * LaserTuning.HARVEST_FE_PER_HARDNESS);
    }

    /**
     * Whether the Collectors up the beam have room for what breaking the block would drop. A drop every
     * Collector's filter turns away doesn't hold the cut up: it falls where the block was.
     */
    private boolean roomForDrops(ServerLevel level, LaserNetworks networks, BlockPos pos) {
        for (ItemStack drop : Block.getDrops(level.getBlockState(pos), level, pos, null, null, tool())) {
            if (networks.anyAccepts(collectors, drop) && !networks.roomFor(collectors, drop)) return false;
        }
        return true;
    }

    /** Breaks it as its pickaxe would, the drops going back up the beam with Pickup. */
    private void breakBlock(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean pickup) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, null, tool()));
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) {
            ItemStack left = drop;
            if (pickup && networks.roomFor(collectors, drop) && networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK)) {
                left = networks.collect(collectors, drop);
            }
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        LaserNetworks.changed(level);
    }

    /** Items a kill just dropped, near where it fell. */
    private void gatherDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.tickCount <= 1)) {
            carry(networks, item);
        }
    }

    /** Carries an item up the beam, if a Collector has room and the stream's sources can pay. */
    private void carry(LaserNetworks networks, ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stream.filter() != null && !stream.filter().lets(stack)) return;
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
        LaserBeam line = LaserBeam.traceTo(level, key.node(), pos, reach);
        return pos.equals(line.hit());
    }

    /** What an area prism's action is working on just now, for the beam it shows: or null. */
    @Nullable
    BlockPos working(ServerLevel level) {
        // Only while it's working: not while it waits for room, or for anything to do.
        if (area == null || level.getGameTime() - lastWorked > LaserTuning.PLACE_COOLDOWN) return null;
        return switch (key.action()) {
            case HARVEST -> cutting;
            case PLACE -> placed;
            case DAMAGE -> targets.isEmpty() ? null : targets.getFirst().blockPosition();
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
