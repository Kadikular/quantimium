package com.kadikular.quantimium.block.multiblock;

import com.kadikular.quantimium.block.entity.QuantumFoundryPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared plumbing for the multiblocks built out of Foundry parts.
 *
 * <p>Parts have no block entity of their own (the plinth aside), so there is nowhere to record who
 * owns them. Ownership is therefore derived: among the controllers whose layout covers a part, the
 * one with the lowest packed position wins. Every controller computes the same answer without
 * talking to the others, so a shared part cannot flicker between two owners.
 *
 * <p>Ownership deliberately ignores whether a controller is formed. Letting it depend on formed
 * state would be circular — being formed depends on owning your arms — and could oscillate. The
 * cost is that a half-built neighbour can hold an arm hostage, which is visible in the other
 * machine's arm count and fixed by moving one block.
 */
public final class MultiblockParts {

    /** Horizontal reach of the widest layout: an arm runs three blocks out. */
    private static final int REACH_XZ = 3;
    /** A part can sit up to one block above its controller (attunement tank) ... */
    private static final int REACH_UP = 1;
    /** ... and a controller up to five below one (containment cap, with a block of slack). */
    private static final int REACH_DOWN = 5;

    private MultiblockParts() {}

    /** Wakes every controller whose layout includes the changed block. */
    public static void notifyNearby(Level level, BlockPos changed) {
        if (level.isClientSide()) return;
        for (MultiblockController controller : controllersAround(level, changed)) {
            if (controller.coversPart(changed)) {
                controller.revalidateStructure();
            }
        }
    }

    /**
     * Controllers other than {@code self} that could contend for the same parts. Usually empty, so
     * callers can skip ownership checks entirely on the fast path.
     */
    public static List<MultiblockController> rivals(Level level, BlockPos self) {
        List<MultiblockController> rivals = new ArrayList<>();
        // A rival sharing one of our parts can be a full reach beyond that part, so the search box
        // is the sum of both layouts rather than just our own.
        for (MultiblockController controller : controllers(level, self,
                REACH_XZ * 2, REACH_DOWN, REACH_UP + REACH_DOWN)) {
            if (!controller.controllerPos().equals(self)) rivals.add(controller);
        }
        return rivals;
    }

    /** True when one of {@code rivals} outranks {@code self} for this part. */
    public static boolean claimedByRival(List<MultiblockController> rivals, BlockPos self, BlockPos part) {
        for (MultiblockController rival : rivals) {
            if (!rival.coversPart(part)) continue;
            if (rival.controllerPos().asLong() < self.asLong()) return true;
        }
        return false;
    }

    /** Points a plinth at its host, or lets it go. No-op for anything that is not a plinth. */
    public static void bindPlinth(Level level, BlockPos plinth, BlockPos host, boolean bind) {
        if (!level.isLoaded(plinth)) return;
        if (!(level.getBlockEntity(plinth) instanceof QuantumFoundryPartBlockEntity part)) return;
        if (bind) {
            part.bindHost(host);
        } else {
            part.releaseHost(host);
        }
    }

    private static List<MultiblockController> controllersAround(Level level, BlockPos around) {
        return controllers(level, around, REACH_XZ, REACH_DOWN, REACH_UP);
    }

    /**
     * Controllers with a block entity inside the given box around {@code around}.
     *
     * <p>Walks the block entity maps of the overlapping chunks rather than probing every position.
     * The box for a rival search is 13×13×12, and asking the world for each of those 2028 positions
     * in turn cost more idle time than everything else these machines do put together. A chunk's map
     * holds a handful of entries, and there are at most nine chunks to look at.
     */
    private static List<MultiblockController> controllers(Level level, BlockPos around,
                                                         int xz, int down, int up) {
        int minX = around.getX() - xz;
        int maxX = around.getX() + xz;
        int minY = around.getY() - down;
        int maxY = around.getY() + up;
        int minZ = around.getZ() - xz;
        int maxZ = around.getZ() + xz;

        List<MultiblockController> found = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(minX); cx <= SectionPos.blockToSectionCoord(maxX); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(minZ); cz <= SectionPos.blockToSectionCoord(maxZ); cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (Map.Entry<BlockPos, BlockEntity> entry
                        : level.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof MultiblockController controller)) continue;
                    BlockPos pos = entry.getKey();
                    if (pos.getX() < minX || pos.getX() > maxX) continue;
                    if (pos.getY() < minY || pos.getY() > maxY) continue;
                    if (pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    found.add(controller);
                }
            }
        }
        return found;
    }
}
