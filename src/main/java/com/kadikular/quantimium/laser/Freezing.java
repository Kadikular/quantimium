package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModTags;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What a Freezing Lens holds still. Machines stop ticking while its light renews them, and start again two ticks after
 * it last did, as a Zeno field's pause does: nothing has to remember to let one go. Mobs stop thinking until a time
 * saved on them, so one unloaded mid-freeze thaws when it's next loaded; each point of damage they take thaws them
 * half a second sooner. Server thread only.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class Freezing {
    private Freezing() {}

    /** Ticks a machine stays frozen after the light last renewed it. */
    private static final long GRACE_TICKS = 2;
    /** Saved on a frozen mob: the game time it thaws, and whether it had no AI before (left so when it thaws). */
    private static final String UNTIL = "quantimium:frozen_until";
    private static final String HAD_NO_AI = "quantimium:frozen_had_no_ai";
    /** Ticks each point of damage takes off a mob's freeze. */
    private static final long THAW_PER_DAMAGE = 10;

    private static final Map<Level, Long2LongOpenHashMap> MACHINES = new HashMap<>();
    /** Frozen mobs in each level, by entity id, and when each thaws. */
    private static final Map<Level, Map<Integer, Long>> MOBS = new HashMap<>();
    /** How many levels have a frozen machine: the tick hook asks this first, for every machine in the world. */
    private static int machineLevels;

    // ---- machines ----

    /** Whether {@code be} can be frozen: it ticks, and a pack hasn't ruled it out. */
    public static boolean freezable(@Nullable BlockEntity be) {
        if (be == null || be.isRemoved() || be.getLevel() == null) return false;
        if (be.getBlockState().is(ModTags.NOT_FREEZABLE)) return false;
        return ticks(be);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> boolean ticks(BlockEntity be) {
        return be.getBlockState().getTicker(be.getLevel(), (BlockEntityType<T>) be.getType()) != null;
    }

    /** Holds the machine at {@code pos} still until two ticks from now. */
    public static void hold(ServerLevel level, BlockPos pos) {
        Long2LongOpenHashMap held = MACHINES.get(level);
        if (held == null) {
            held = new Long2LongOpenHashMap();
            MACHINES.put(level, held);
            machineLevels++;
        }
        held.put(pos.asLong(), level.getGameTime() + GRACE_TICKS);
    }

    /** Whether {@code be} is held still now. Nearly free while nothing anywhere is frozen. */
    public static boolean frozen(BlockEntity be) {
        if (machineLevels == 0) return false;
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return false;
        Long2LongOpenHashMap held = MACHINES.get(level);
        if (held == null) return false;
        long until = held.get(be.getBlockPos().asLong());
        return until != 0 && level.getGameTime() <= until;
    }

    // ---- mobs ----

    /** Whether a Freezing beam may freeze {@code entity}'s thinking: a mob, not one a pack has ruled out. */
    public static boolean freezable(LivingEntity entity) {
        return entity instanceof Mob && !entity.typeHolder().is(ModTags.NOT_FREEZABLE_ENTITIES);
    }

    /** How many ticks {@code mob} has left frozen; 0 if it isn't. */
    public static long left(Mob mob) {
        Map<Integer, Long> mobs = MOBS.get(mob.level());
        Long until = mobs == null ? null : mobs.get(mob.getId());
        return until == null ? 0 : Math.max(0, until - mob.level().getGameTime());
    }

    /** Freezes {@code mob} till {@code ticks} from now, or longer if it already was. */
    public static void freeze(Mob mob, long ticks) {
        long until = mob.level().getGameTime() + ticks;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(UNTIL)) data.putBoolean(HAD_NO_AI, mob.isNoAi());
        until = Math.max(until, data.getLongOr(UNTIL, 0L));
        data.putLong(UNTIL, until);
        mob.setNoAi(true);
        MOBS.computeIfAbsent(mob.level(), key -> new HashMap<>()).put(mob.getId(), until);
    }

    private static void thaw(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(UNTIL)) return;
        mob.setNoAi(data.getBooleanOr(HAD_NO_AI, false));
        data.remove(UNTIL);
        data.remove(HAD_NO_AI);
        mob.setTicksFrozen(0);
    }

    /** Frozen mobs thaw when their time is up; machines no light renews are let go. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long2LongOpenHashMap held = MACHINES.get(level);
        if (held != null && now % 20 == 0) {
            held.values().removeIf(until -> until < now);
            if (held.isEmpty()) {
                MACHINES.remove(level);
                machineLevels--;
            }
        }
        Map<Integer, Long> mobs = MOBS.get(level);
        if (mobs == null) return;
        Iterator<Map.Entry<Integer, Long>> it = mobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= entry.getValue()) {
                thaw(mob);
                it.remove();
                continue;
            }
            // It looks frozen: the powder-snow shiver, kept short of hurting it.
            if (mob.canFreeze()) mob.setTicksFrozen(Math.max(mob.getTicksFrozen(), mob.getTicksRequiredToFreeze() - 1));
        }
        if (mobs.isEmpty()) MOBS.remove(level);
    }

    /** One loaded again mid-freeze: back on the list, or thawed if its time ran out while it was away. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide()) return;
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(UNTIL)) return;
        long until = data.getLongOr(UNTIL, 0L);
        if (event.getLevel().getGameTime() >= until) thaw(mob);
        else MOBS.computeIfAbsent(event.getLevel(), key -> new HashMap<>()).put(mob.getId(), until);
    }

    /** Hit while frozen, it thaws sooner: half a second for each point of damage. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        Map<Integer, Long> mobs = MOBS.get(mob.level());
        Long until = mobs == null ? null : mobs.get(mob.getId());
        if (until == null) return;
        long sooner = until - (long) Math.ceil(event.getHealthDamage() * THAW_PER_DAMAGE);
        mobs.put(mob.getId(), sooner);
        mob.getPersistentData().putLong(UNTIL, sooner);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MACHINES.clear();
        MOBS.clear();
        machineLevels = 0;
    }
}
