package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.init.ModDamageTypes;
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
 * What one beam with lenses does, besides carry power: break the block it ends on, hurt what stands in
 * it, bring items back up it. It asks the network for the power each step costs, like any block the
 * beam charges, so it only works while the beam is live, and an emitter pays only while there's
 * something to do. What it has so far is kept while the network is rebuilt; a world reload starts it
 * over.
 */
public final class LensAction {

    /** Which beam: its node, and which of the node's beams. */
    public record Key(BlockPos node, int beam) {}

    private final Key key;
    private LaserBeam beam;
    private int lenses;
    /** Where what it brings back goes: every Collector up the beam, nearest first; the next takes what one can't. */
    private List<BlockPos> collectors = List.of();
    /** FE it's been given towards its next step. */
    private long stored;
    /** FE it wants this tick: what its next steps cost, less what it has. */
    private long want;
    private int harvestCooldown;
    /** What it saw in the beam at its last sweep, until the next. */
    private List<LivingEntity> targets = List.of();
    private List<ItemEntity> lying = List.of();

    LensAction(Key key) {
        this.key = key;
    }

    public Key key() {
        return key;
    }

    /** The beam as last traced, its lenses, and where its Collectors are. */
    void update(LaserBeam beam, int lenses, List<BlockPos> collectors) {
        this.beam = beam;
        this.lenses = lenses;
        this.collectors = List.copyOf(collectors);
    }

    int lenses() {
        return lenses;
    }

    long demand() {
        return Math.max(0, want - stored);
    }

    /** Takes up to {@code fe} towards what it wants; what it took. */
    long fill(long fe) {
        long took = Math.min(fe, demand());
        stored += took;
        return took;
    }

    /** Once a tick, after power has moved: does whatever its power now pays for, and works out what it wants next. */
    void act(ServerLevel level, LaserNetworks networks) {
        long time = level.getGameTime();
        Lens action = Lens.action(lenses);
        boolean pickup = Lens.has(lenses, Lens.PICKUP) && !collectors.isEmpty();
        long next = 0;
        if (harvestCooldown > 0) harvestCooldown--;
        if (action == Lens.HARVEST) {
            BlockPos target = harvestTarget(level);
            if (target != null) {
                long cost = harvestCost(level, target);
                if (stored >= cost && harvestCooldown <= 0) {
                    stored -= cost;
                    harvest(level, networks, target, pickup);
                    harvestCooldown = LaserTuning.HARVEST_COOLDOWN;
                } else {
                    next += cost;
                }
            }
        }
        // Sweeps for what's in the beam, staggered so beams don't all look on the same tick.
        int phase = Math.floorMod(key.hashCode() + (int) time, LaserTuning.DAMAGE_INTERVAL);
        if (action == Lens.DAMAGE) {
            if (phase == 0) {
                targets = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && onBeam(entity.getBoundingBox(), 0.25));
                for (LivingEntity entity : targets) {
                    if (stored < LaserTuning.DAMAGE_FE_PER_HIT) break;
                    if (entity.hurtServer(level, level.damageSources().source(ModDamageTypes.LASER), LaserTuning.DAMAGE_PER_HIT)) {
                        stored -= LaserTuning.DAMAGE_FE_PER_HIT;
                        // Killed, with Pickup: what it drops goes straight back up the beam, wherever it lands.
                        if (pickup && !entity.isAlive()) gatherDrops(level, networks, entity);
                    }
                }
            }
            next += targets.size() * LaserTuning.DAMAGE_FE_PER_HIT;
        }
        if (pickup) {
            if (phase == 0) {
                // Items are small: anything within nearly a block of the beam's line counts.
                lying = level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45));
                for (ItemEntity item : lying) {
                    if (stored < LaserTuning.PICKUP_FE_PER_STACK) break;
                    ItemStack left = networks.collect(collectors, item.getItem().copy());
                    if (left.getCount() == item.getItem().getCount()) continue;
                    stored -= LaserTuning.PICKUP_FE_PER_STACK;
                    if (left.isEmpty()) item.discard();
                    else item.setItem(left);
                }
            }
            next += lying.size() * LaserTuning.PICKUP_FE_PER_STACK;
        }
        want = next;
    }

    /** The block a Harvest lens's beam would break: what it ends on, within reach, if it can be broken. */
    @Nullable
    private BlockPos harvestTarget(ServerLevel level) {
        if (beam == null || beam.hit() == null || beam.length() >= LaserTuning.ACTION_REACH) return null;
        if (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK) return null;
        BlockPos pos = beam.hit();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return null;
        if (state.is(com.kadikular.quantimium.init.ModTags.LASER_UNBREAKABLE)) return null;
        return pos;
    }

    private static long harvestCost(ServerLevel level, BlockPos pos) {
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.ceil(Math.max(1.0f, hardness) * LaserTuning.HARVEST_FE_PER_HARDNESS);
    }

    /** Breaks it as a diamond pickaxe would, the drops going back up the beam with Pickup. */
    private void harvest(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean pickup) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE)));
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) {
            ItemStack left = pickup ? networks.collect(collectors, drop) : drop;
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        LaserNetworks.changed(level);
    }

    /** Items a kill just dropped, near where it fell: gathered up the beam, the same tick. */
    private void gatherDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.tickCount <= 1)) {
            ItemStack left = networks.collect(collectors, item.getItem().copy());
            if (left.isEmpty()) item.discard();
            else item.setItem(left);
        }
    }

    /** The part of the beam its action reaches: from the mount, up to {@link LaserTuning#ACTION_REACH} blocks along. */
    private Vec3[] reach() {
        Vec3[] ends = beam.ends();
        Vec3 along = ends[1].subtract(ends[0]);
        double length = along.length();
        if (length <= LaserTuning.ACTION_REACH || length == 0) return ends;
        return new Vec3[] {ends[0], ends[0].add(along.scale(LaserTuning.ACTION_REACH / length))};
    }

    /** Round the part of the beam it reaches, to look for things in it. */
    private AABB box() {
        Vec3[] ends = reach();
        return new AABB(ends[0], ends[1]).inflate(1.0);
    }

    /** Whether a box comes within {@code slack} of the beam's line. */
    private boolean onBeam(AABB box, double slack) {
        Vec3[] ends = reach();
        AABB near = box.inflate(slack);
        return near.clip(ends[0], ends[1]).isPresent() || near.contains(ends[0]) || near.contains(ends[1]);
    }
}
