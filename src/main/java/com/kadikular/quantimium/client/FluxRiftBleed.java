package com.kadikular.quantimium.client;

import com.kadikular.quantimium.flux.MirrorFloraData;
import com.kadikular.quantimium.flux.MirrorFloraSpawner;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mirror flora bleeding into the real world around a stage 2+ rift: the veil is thinning, and grey
 * mirror growth shows through for everyone, phased or not.
 *
 * <p>Worked out on the client rather than sent. Every client already has the terrain, the anchor,
 * the stage and the seed, and the selection is a pure hash of position, so all of them grow the same
 * plants with no server cost and nothing on the wire. It is scenery — render-only records exactly
 * like ordinary mirror flora, nothing another mod or an air check can see — so nothing needs it to
 * be authoritative. Surfaces follow {@link MirrorFloraSpawner}'s rules, so it grows where mirror
 * flora would, and never on live machinery.
 */
public final class FluxRiftBleed {

    /** One bled plant and how far out from the rift it stands, for growing the patch outwards. */
    public record Bloom(MirrorFloraData.Entry entry, float distance) {}

    private static final int BELOW = 4;
    private static final int ABOVE = 6;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private FluxRiftBleed() {}

    /** Radius the bleed spreads to at each stage; corruption reaches a couple of blocks past it. */
    public static float radius(int stage) {
        return switch (stage) {
            // Just a little at stage 1, so even a fresh wound does not read as a portal.
            case 1 -> 2.0f;
            case 2 -> 4.0f;
            case 3 -> 7.0f;
            case 4 -> 10.0f;
            default -> 0.0f;
        };
    }

    /** Nearest first, so a visible radius only has to walk the front of the list. */
    public static List<Bloom> scan(ClientLevel level, BlockPos anchor, int seed, int stage) {
        float radius = radius(stage);
        List<Bloom> blooms = new ArrayList<>();
        if (radius <= 0.0f) return blooms;
        int reach = (int) Math.ceil(radius);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                float distance = (float) Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) continue;
                int x = anchor.getX() + dx;
                int z = anchor.getZ() + dz;
                if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
                // Thick at the wound, thinning to a scatter at the edge.
                float chance = 0.08f + 0.38f * (1.0f - distance / radius);
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    cursor.set(x, anchor.getY() + dy, z);
                    if (!level.getBlockState(cursor).isAir()) continue;
                    MirrorFloraData.Entry entry = plant(level, cursor.immutable(), seed, chance);
                    if (entry != null) blooms.add(new Bloom(entry, distance));
                }
            }
        }
        blooms.sort(Comparator.comparingDouble(Bloom::distance));
        return blooms;
    }

    /** Floor first, then walls, then ceiling: one record per position, as in the chunk data. */
    private static MirrorFloraData.Entry plant(ClientLevel level, BlockPos pos, int seed, float chance) {
        if (roll(pos, seed, 0) < chance) {
            // No tall grass: two blocks of it round a rift walls off the view of the thing you are fighting.
            MirrorFloraData.Kind kind = MirrorFloraSpawner.floorKind(pos);
            if (kind == MirrorFloraData.Kind.TALL_GRASS) kind = MirrorFloraData.Kind.SHORT_GRASS;
            BlockState plant = MirrorFloraSpawner.plantState(kind);
            if (MirrorFloraSpawner.supportsFloor(level, pos.below(), plant)) {
                return MirrorFloraData.Entry.floor(pos, kind);
            }
        }
        MirrorFloraData.Entry vine = null;
        for (Direction face : HORIZONTAL) {
            if (roll(pos, seed, 1 + face.ordinal()) >= chance * 0.45f) continue;
            if (!MirrorFloraSpawner.supportsVine(level, pos, face)) continue;
            vine = vine == null ? MirrorFloraData.Entry.vine(pos, face) : vine.withFace(face);
        }
        if (vine != null) return vine;
        if (roll(pos, seed, 9) < chance * 0.4f) {
            BlockState roots = ModBlocks.MIRROR_HANGING_ROOTS.get().defaultBlockState();
            if (MirrorFloraSpawner.supportsCeiling(level, pos.above(), roots)) {
                return MirrorFloraData.Entry.floor(pos, MirrorFloraData.Kind.HANGING_ROOTS);
            }
        }
        return null;
    }

    /** Stable 0–1 per position and salt, so rescans and other clients agree. */
    private static float roll(BlockPos pos, int seed, int salt) {
        long hash = seed * 0x9E3779B97F4A7C15L + salt;
        hash ^= pos.asLong() * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 31;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 29;
        return (hash >>> 40) / (float) (1L << 24);
    }
}
