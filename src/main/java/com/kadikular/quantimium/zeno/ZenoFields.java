package com.kadikular.quantimium.zeno;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The Zeno fields holding still right now, for the random-tick hook to ask about.
 *
 * <p>A pausing controller renews its entry every tick it is powered, and an entry lapses two ticks
 * after its last renewal. Nothing has to remember to take a field down: a controller that is broken,
 * unloaded, out of power, switched to accelerate, or taken into a simulator's field simply stops
 * renewing. Random ticks run on the server thread, and so does everything that touches this.
 */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class ZenoFields {

    /** Ticks an entry outlives its last renewal. */
    private static final long GRACE_TICKS = 2;

    private record Field(int radius, long expires) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Field>> PAUSED = new HashMap<>();

    private ZenoFields() {}

    /** Holds the cube of {@code radius} around {@code centre} still until two ticks from now. */
    public static void renew(ServerLevel level, BlockPos centre, int radius) {
        PAUSED.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .put(centre.immutable(), new Field(radius, level.getGameTime() + GRACE_TICKS));
    }

    /** Whether this level has any pausing field at all, live or just expired. */
    public static boolean any(ServerLevel level) {
        Map<BlockPos, Field> fields = PAUSED.get(level.dimension());
        return fields != null && !fields.isEmpty();
    }

    /** Whether a live pausing field covers {@code pos}. Cheap when there are no fields at all. */
    public static boolean isPaused(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Field> fields = PAUSED.get(level.dimension());
        if (fields == null || fields.isEmpty()) return false;
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Field>> it = fields.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Field> entry = it.next();
            Field field = entry.getValue();
            if (now > field.expires()) {
                it.remove();
                continue;
            }
            BlockPos centre = entry.getKey();
            if (Math.abs(pos.getX() - centre.getX()) <= field.radius()
                    && Math.abs(pos.getY() - centre.getY()) <= field.radius()
                    && Math.abs(pos.getZ() - centre.getZ()) <= field.radius()) {
                return true;
            }
        }
        return false;
    }

    /** What an accelerating field does with a block's extra ticks, from #zeno_ignored and #zeno_slowed. */
    public enum TickRule { NORMAL, IGNORED, SLOWED }

    /**
     * Each block's rule, worked out on first sight. A tag lookup hashes the tag's id every time, and
     * a busy field asks thousands of times a tick; an identity lookup on the block is nearly free.
     */
    private static final Map<Block, TickRule> RULES = new IdentityHashMap<>();

    public static TickRule tickRule(BlockState state) {
        TickRule rule = RULES.get(state.getBlock());
        if (rule == null) {
            rule = state.is(ModTags.ZENO_IGNORED) ? TickRule.IGNORED
                    : state.is(ModTags.ZENO_SLOWED) ? TickRule.SLOWED : TickRule.NORMAL;
            RULES.put(state.getBlock(), rule);
        }
        return rule;
    }

    /** A datapack reload can change the tags. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        RULES.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PAUSED.clear();
        RULES.clear();
    }
}
