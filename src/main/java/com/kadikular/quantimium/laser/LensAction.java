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
    /** Where what it brings back goes: the nearest Collector up the beam, or null for none. */
    @Nullable
    private BlockPos collector;
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

    /** The beam as last traced, its lenses, and where its Collector is. */
    void update(LaserBeam beam, int lenses, @Nullable BlockPos collector) {
        this.beam = beam;
        this.lenses = lenses;
        this.collector = collector;
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
        boolean pickup = Lens.has(lenses, Lens.PICKUP);
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
                targets = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && onBeam(entity.getBoundingBox()));
                for (LivingEntity entity : targets) {
                    if (stored < LaserTuning.DAMAGE_FE_PER_HIT) break;
                    if (entity.hurtServer(level, level.damageSources().source(ModDamageTypes.LASER), LaserTuning.DAMAGE_PER_HIT)) {
                        stored -= LaserTuning.DAMAGE_FE_PER_HIT;
                    }
                }
            }
            next += targets.size() * LaserTuning.DAMAGE_FE_PER_HIT;
        }
        if (pickup && collector != null) {
            if (phase == 0) {
                lying = level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox()));
                for (ItemEntity item : lying) {
                    if (stored < LaserTuning.PICKUP_FE_PER_STACK) break;
                    ItemStack left = networks.collect(collector, item.getItem().copy());
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
        if (beam == null || beam.hit() == null || beam.length() >= LaserTuning.HARVEST_REACH) return null;
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
            ItemStack left = pickup && collector != null ? networks.collect(collector, drop) : drop;
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        LaserNetworks.changed(level);
    }

    /** Round the whole beam, to look for things in it. */
    private AABB box() {
        Vec3[] ends = beam.ends();
        return new AABB(ends[0], ends[1]).inflate(0.5);
    }

    /** Whether a box touches the beam's line, give or take a little. */
    private boolean onBeam(AABB box) {
        Vec3[] ends = beam.ends();
        return box.inflate(0.25).clip(ends[0], ends[1]).isPresent() || box.contains(ends[0]) || box.contains(ends[1]);
    }
}
