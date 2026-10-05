package com.kadikular.quantimium.client.renderer;

import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only facade picker for Unrealised Ore.
 *
 * <p>A facade is chosen per <em>vein</em>, not per block: everything reachable from a block through a
 * shared face or edge is one vein, so a pocket wears a single ore look instead of a scatter of ten.
 * The cluster is walked once on first sight and every member is mapped to the same key, so the search
 * cost is paid once per vein rather than once per block per mesh build.
 *
 * <p>The reshuffle happens the moment a vein leaves the player's view: its facade is rerolled and its
 * sections are queued for a rebuild while nobody is looking, so turning back reveals a different ore
 * with no visible pop. Lookups run on mesh-builder threads, hence the concurrent maps.
 */
public final class UnrealisedOreFacadeCache {

    /** Bounds on the vein search, so a pathological cluster cannot stall a mesh build. */
    private static final int MAX_VEIN_BLOCKS = 96;
    private static final int MAX_VEIN_RADIUS = 6;

    /** A vein counts as watched inside this range and roughly ahead of the camera. */
    private static final double WATCH_DISTANCE = 96.0;
    private static final double WATCH_DOT = 0.35;
    /** Anything this far away is dropped outright; it will be rediscovered on the next mesh build. */
    private static final double FORGET_DISTANCE = 192.0;
    private static final int SWEEP_INTERVAL_TICKS = 5;

    /** Ores found in stone, used at and above Y=0. */
    private static final List<BlockState> FALLBACK_STONE_FACADES = List.of(
            Blocks.COAL_ORE.defaultBlockState(),
            Blocks.IRON_ORE.defaultBlockState(),
            Blocks.COPPER_ORE.defaultBlockState(),
            Blocks.GOLD_ORE.defaultBlockState(),
            Blocks.REDSTONE_ORE.defaultBlockState(),
            Blocks.LAPIS_ORE.defaultBlockState(),
            Blocks.DIAMOND_ORE.defaultBlockState()
    );

    /** The deepslate set, so a vein below Y=0 never looks like it is sitting in the wrong rock. */
    private static final List<BlockState> FALLBACK_DEEPSLATE_FACADES = List.of(
            Blocks.DEEPSLATE_COAL_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(),
            Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()
    );

    private static final class Vein {
        volatile BlockState facade;
        final long[] members;
        final BlockPos min;
        final BlockPos max;
        boolean watched;

        Vein(BlockState facade, long[] members, BlockPos min, BlockPos max) {
            this.facade = facade;
            this.members = members;
            this.min = min;
            this.max = max;
        }

        Vec3 centre() {
            return new Vec3((min.getX() + max.getX()) / 2.0 + 0.5,
                    (min.getY() + max.getY()) / 2.0 + 0.5,
                    (min.getZ() + max.getZ()) / 2.0 + 0.5);
        }
    }

    private static final Map<Long, Long> MEMBERSHIP = new ConcurrentHashMap<>();
    private static final Map<Long, Vein> VEINS = new ConcurrentHashMap<>();

    private UnrealisedOreFacadeCache() {}

    public static BlockState facadeFor(BlockGetter level, BlockPos pos) {
        Long key = MEMBERSHIP.get(pos.asLong());
        Vein vein = key == null ? null : VEINS.get(key);
        if (vein != null) return vein.facade;
        return mapVein(level, pos).facade;
    }

    /**
     * Walks the connected ore and gives the whole cluster one identity. Re-walking on every cache miss
     * is deliberate: a block placed against an existing vein joins it and inherits its look, rather
     * than wearing a facade of its own until the next reshuffle.
     */
    private static Vein mapVein(BlockGetter level, BlockPos origin) {
        Set<Long> cluster = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        cluster.add(origin.asLong());
        visited.add(origin.asLong());
        queue.add(origin.immutable());

        long canonical = origin.asLong();
        int minX = origin.getX(), minY = origin.getY(), minZ = origin.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        BlockState inherited = null;

        while (!queue.isEmpty() && cluster.size() < MAX_VEIN_BLOCKS) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (cluster.size() >= MAX_VEIN_BLOCKS) continue;

                        BlockPos neighbour = current.offset(dx, dy, dz);
                        if (Math.abs(neighbour.getX() - origin.getX()) > MAX_VEIN_RADIUS
                                || Math.abs(neighbour.getY() - origin.getY()) > MAX_VEIN_RADIUS
                                || Math.abs(neighbour.getZ() - origin.getZ()) > MAX_VEIN_RADIUS) {
                            continue;
                        }
                        long packed = neighbour.asLong();
                        if (!visited.add(packed)) continue;
                        if (!level.getBlockState(neighbour).is(ModBlocks.UNREALISED_ORE.get())) continue;

                        cluster.add(packed);
                        queue.add(neighbour);
                        if (packed < canonical) canonical = packed;
                        minX = Math.min(minX, neighbour.getX());
                        minY = Math.min(minY, neighbour.getY());
                        minZ = Math.min(minZ, neighbour.getZ());
                        maxX = Math.max(maxX, neighbour.getX());
                        maxY = Math.max(maxY, neighbour.getY());
                        maxZ = Math.max(maxZ, neighbour.getZ());

                        if (inherited == null) {
                            Long known = MEMBERSHIP.get(packed);
                            Vein existing = known == null ? null : VEINS.get(known);
                            if (existing != null) inherited = existing.facade;
                        }
                    }
                }
            }
        }

        long[] members = new long[cluster.size()];
        int index = 0;
        for (long member : cluster) {
            members[index++] = member;
        }

        BlockState facade = inherited != null ? inherited : pickFacade(BlockPos.of(canonical));
        Vein vein = new Vein(facade, members, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        VEINS.put(canonical, vein);
        for (long member : members) {
            MEMBERSHIP.put(member, canonical);
        }
        return vein;
    }

    /** Rerolls veins the moment they leave view, and forgets ones the player has walked away from. */
    public static void onClientTick() {
        if (VEINS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.levelRenderer == null) return;
        if (minecraft.level.getGameTime() % SWEEP_INTERVAL_TICKS != 0) return;

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Vector3f look = new Vector3f(camera.forwardVector());

        Iterator<Map.Entry<Long, Vein>> iterator = VEINS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Vein> entry = iterator.next();
            Vein vein = entry.getValue();
            Vec3 offset = vein.centre().subtract(eye);
            double distance = offset.length();

            if (distance > FORGET_DISTANCE) {
                for (long member : vein.members) {
                    MEMBERSHIP.remove(member, entry.getKey());
                }
                iterator.remove();
                continue;
            }

            boolean watched = isWatched(offset, distance, look);
            if (vein.watched && !watched) {
                vein.facade = pickFacade(BlockPos.of(entry.getKey()));
                rebuildSections(minecraft, vein);
            }
            vein.watched = watched;
        }
    }

    /** Queues just the sections the vein touches, rather than every block position it spans. */
    private static void rebuildSections(Minecraft minecraft, Vein vein) {
        int minSectionX = SectionPos.blockToSectionCoord(vein.min.getX());
        int minSectionY = SectionPos.blockToSectionCoord(vein.min.getY());
        int minSectionZ = SectionPos.blockToSectionCoord(vein.min.getZ());
        int maxSectionX = SectionPos.blockToSectionCoord(vein.max.getX());
        int maxSectionY = SectionPos.blockToSectionCoord(vein.max.getY());
        int maxSectionZ = SectionPos.blockToSectionCoord(vein.max.getZ());

        for (int x = minSectionX; x <= maxSectionX; x++) {
            for (int y = minSectionY; y <= maxSectionY; y++) {
                for (int z = minSectionZ; z <= maxSectionZ; z++) {
                    minecraft.levelRenderer.setSectionDirty(x, y, z);
                }
            }
        }
    }

    private static boolean isWatched(Vec3 offset, double distance, Vector3f look) {
        if (distance > WATCH_DISTANCE) return false;
        if (distance < 4.0) return true; // Standing in it counts; the dot product is meaningless up close.
        Vec3 direction = offset.scale(1.0 / distance);
        return direction.x * look.x() + direction.y * look.y() + direction.z * look.z() > WATCH_DOT;
    }

    public static void clear() {
        VEINS.clear();
        MEMBERSHIP.clear();
    }

    private static BlockState pickFacade(BlockPos pos) {
        boolean deepslate = pos.getY() < 0;
        Optional<HolderSet.Named<Block>> configured = BuiltInRegistries.BLOCK.get(deepslate
                ? ModTags.UNREALISED_ORE_FACADES_DEEPSLATE
                : ModTags.UNREALISED_ORE_FACADES_STONE);
        List<BlockState> palette = configured
                .map(tag -> tag.stream().map(holder -> holder.value().defaultBlockState()).toList())
                .filter(states -> !states.isEmpty())
                .orElse(deepslate ? FALLBACK_DEEPSLATE_FACADES : FALLBACK_STONE_FACADES);
        RandomSource random = RandomSource.create(pos.asLong() * 0x9E3779B97F4A7C15L ^ System.nanoTime());
        return palette.get(random.nextInt(palette.size()));
    }
}
