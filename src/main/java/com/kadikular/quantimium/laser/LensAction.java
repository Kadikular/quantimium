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

    void update(LaserBeam beam, Light.Stream stream, List<BlockPos> collectors) {
        this.beam = beam;
        this.stream = stream;
        this.collectors = List.copyOf(collectors);
    }

    /** What it asks of the network this tick: only a cut in progress asks; the rest pay as they act. */
    long demand() {
        return want;
    }

    /** Takes up to {@code fe} into the cut; what it took. */
    long fill(long fe) {
        long took = Math.min(fe, want);
        cut += took;
        want -= took;
        return took;
    }

    /** Once a tick, after power has moved. */
    void act(ServerLevel level, LaserNetworks networks) {
        boolean pickup = stream.has(Lens.PICKUP) && !collectors.isEmpty();
        int phase = Math.floorMod(key.hashCode() + (int) level.getGameTime(), LaserTuning.DAMAGE_INTERVAL);
        switch (key.action()) {
            case HARVEST -> harvest(level, networks, pickup);
            case DAMAGE -> {
                if (phase != 0) return;
                targets = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && onBeam(entity.getBoundingBox(), 0.25));
                for (LivingEntity entity : targets) {
                    if (!networks.pay(this, LaserTuning.DAMAGE_FE_PER_HIT)) break;
                    if (entity.hurtServer(level, level.damageSources().source(ModDamageTypes.LASER), LaserTuning.DAMAGE_PER_HIT)
                            && pickup && !entity.isAlive()) {
                        // Killed, with Pickup: what it drops goes straight back up the beam, wherever it lands.
                        gatherDrops(level, networks, entity);
                    }
                }
            }
            case PICKUP -> {
                if (phase != 0 || collectors.isEmpty()) return;
                // Items are small: anything within nearly a block of the beam's line counts.
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45))) {
                    carry(networks, item);
                }
            }
            default -> {}
        }
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
            cutCost = target == null ? 0 : harvestCost(level, target);
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
            cooldown = LaserTuning.HARVEST_COOLDOWN;
            cutting = null;
            cut = 0;
            want = 0;
            return;
        }
        level.destroyBlockProgress(crackId(), target, (int) Math.min(9, cut * 10 / Math.max(1, cutCost)));
        want = Math.max(0, cutCost - cut);
    }

    /** The block a Harvest beam would break: what it ends on, if it can be broken. */
    @Nullable
    private BlockPos harvestTarget(ServerLevel level) {
        if (beam == null || beam.hit() == null) return null;
        if (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK) return null;
        BlockPos pos = beam.hit();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return null;
        if (state.is(ModTags.LASER_UNBREAKABLE)) return null;
        return pos;
    }

    private static long harvestCost(ServerLevel level, BlockPos pos) {
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.ceil(Math.max(1.0f, hardness) * LaserTuning.HARVEST_FE_PER_HARDNESS);
    }

    /** Whether the Collectors up the beam have room for what breaking the block would drop. */
    private boolean roomForDrops(ServerLevel level, LaserNetworks networks, BlockPos pos) {
        for (ItemStack drop : Block.getDrops(level.getBlockState(pos), level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE))) {
            if (!networks.roomFor(collectors, drop)) return false;
        }
        return true;
    }

    /** Breaks it as a diamond pickaxe would, the drops going back up the beam with Pickup. */
    private void breakBlock(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean pickup) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE)));
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
        if (!networks.roomFor(collectors, stack) || !networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK)) return;
        ItemStack left = networks.collect(collectors, stack.copy());
        if (left.isEmpty()) item.discard();
        else item.setItem(left);
    }

    /** The cracks it draws on the block it's cutting: one set per action. */
    private int crackId() {
        return -1 - (key.hashCode() & 0x3FFFFFFF);
    }

    /** Round the beam, to look for things in it. */
    private AABB box() {
        Vec3[] ends = beam.ends();
        return new AABB(ends[0], ends[1]).inflate(1.0);
    }

    /** Whether a box comes within {@code slack} of the beam's line. */
    private boolean onBeam(AABB box, double slack) {
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
