package com.kadikular.quantimium.block;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.multiblock.MultiblockParts;
import com.kadikular.quantimium.block.multiblock.PartClaim;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A 3x3 plinth base, three layers of hollow Quantum Attuned Glass, a 3x3 plinth cap, and one to
 * four cardinal attunement arms borrowed from the Foundry.
 *
 * <p>The interior is a single 1x1x3 column: the cell you can see the occupant in. Arms are read in
 * absolute compass order rather than relative to a facing, because the hall has no front — nothing
 * about it is asymmetric and nothing maps arms to slots.
 */
public final class ContainmentHallStructure {

    /** Glass layers between the base and the cap. */
    public static final int WALL_HEIGHT = 3;
    /** Height of the cap above the controller. */
    public static final int CAP_OFFSET = WALL_HEIGHT + 1;
    public static final int MAX_ARMS = 4;

    private static final Direction[] ARMS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private ContainmentHallStructure() {}

    public static Direction armDirection(int slot) {
        return ARMS[slot & 3];
    }

    /** Bit mask of complete arms, or zero when the cell itself is not built. */
    public static int armMask(Level level, BlockPos controller, PartClaim claim) {
        if (!shellComplete(level, controller, claim)) return 0;
        int mask = 0;
        for (int slot = 0; slot < MAX_ARMS; slot++) {
            if (QuantumFoundryStructure.hasArm(level, controller, armDirection(slot), claim)) {
                mask |= 1 << slot;
            }
        }
        return mask;
    }

    /**
     * The residue input of the hall whose arm {@code part} belongs to, or null.
     *
     * <p>Arm blocks carry no block entity, so this works back from the part to each place its
     * controller could stand and asks that hall whether it counts the arm on that side. Asking the
     * hall rather than the blocks means an arm claimed by a Foundry, or by another hall, never answers.
     */
    @Nullable
    public static ResourceHandler<ItemResource> residuePortAt(Level level, BlockPos part, BlockState state) {
        int reach;
        BlockPos from = part;
        if (state.is(ModBlocks.QUANTUM_FOUNDRY_CONDUIT.get())) {
            reach = 2;
        } else if (state.is(ModBlocks.QUANTUM_FOUNDRY_PILLAR.get())) {
            reach = 3;
        } else if (state.is(ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK.get())) {
            reach = 3;
            from = part.below();
        } else {
            return null;
        }
        for (int slot = 0; slot < MAX_ARMS; slot++) {
            BlockPos controller = from.relative(armDirection(slot), -reach);
            if (!level.isLoaded(controller)) continue;
            if (level.getBlockEntity(controller) instanceof ContainmentHallBlockEntity hall
                    && (hall.getArmMask() & 1 << slot) != 0) {
                return hall.residuePort();
            }
        }
        return null;
    }

    /** Pipes cache capabilities by position, so tell them when an arm starts or stops answering. */
    public static void invalidateArmCapabilities(Level level, BlockPos controller, int mask) {
        for (int slot = 0; slot < MAX_ARMS; slot++) {
            if ((mask & 1 << slot) == 0) continue;
            Direction direction = armDirection(slot);
            BlockPos pillar = controller.relative(direction, 3);
            level.invalidateCapabilities(controller.relative(direction, 2));
            level.invalidateCapabilities(pillar);
            level.invalidateCapabilities(pillar.above());
        }
    }

    public static boolean shellComplete(Level level, BlockPos controller, PartClaim claim) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean centre = dx == 0 && dz == 0;

                BlockPos base = controller.offset(dx, 0, dz);
                if (!level.isLoaded(base)) return false;
                if (centre) {
                    if (!level.getBlockState(base).is(ModBlocks.ANOMALY_CONTAINMENT_HALL.get())) return false;
                } else if (!isPlinth(level, base, claim)) {
                    return false;
                }

                BlockPos cap = controller.offset(dx, CAP_OFFSET, dz);
                if (!isPlinth(level, cap, claim)) return false;

                for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                    BlockPos wall = controller.offset(dx, dy, dz);
                    if (!level.isLoaded(wall)) return false;
                    if (centre) {
                        if (!cellClear(level, wall)) return false;
                    } else if (!level.getBlockState(wall).is(ModBlocks.QUANTUM_ATTUNED_GLASS.get())
                            || !claim.mine(wall)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Whether a block in the cell counts as empty. The cell has to stay clear: it is what the field
     * is holding.
     *
     * <p>Anything without collision passes, because mirror-world vines happily creep through glass
     * into the bore and a formed hall would otherwise break the moment one appeared — and then it
     * could not power the field that clears them. {@link #clearCell} vaporises them instead.
     */
    public static boolean cellClear(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Re-queues every block of the hall for a light check.
     *
     * <p>Nothing here changes, which is the point: the light engine is being asked to recompute a
     * volume whose stored values may predate the blocks in it. Cheap enough to spend on a formed
     * hall once per chunk load, and the only way to clear a shadow that was written to disk.
     */
    public static void relight(ServerLevel level, BlockPos controller) {
        LevelLightEngine lighting = level.getChunkSource().getLightEngine();
        for (int dy = 0; dy <= CAP_OFFSET; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    lighting.checkBlock(controller.offset(dx, dy, dz));
                }
            }
        }
    }

    /** Burns the intruders named above out of the cell. Server side, called while formed. */
    public static void clearCell(Level level, BlockPos controller) {
        for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
            BlockPos pos = controller.above(dy);
            if (level.getBlockState(pos).isAir()) continue;
            // No drops: the field does not politely uproot things.
            level.destroyBlock(pos, false);
        }
    }

    /**
     * The face of a shell pane that looks in towards the cell axis.
     *
     * <p>For the four wall centres that is the face onto open air, which is the one worth hiding.
     * For a corner it resolves to a neighbouring pane instead — already culled, so dropping it
     * costs nothing and saves the block needing a separate corner model.
     */
    private static Direction inwardFace(int dx, int dz) {
        if (dx == 0) return dz < 0 ? Direction.SOUTH : Direction.NORTH;
        if (dz == 0) return dx < 0 ? Direction.EAST : Direction.WEST;
        return cornerFace(dx, dz);
    }

    /**
     * Rotation for a corner pane, chosen to stand its pillar in the corner nearest the cell.
     *
     * <p>Each answer also happens to be a face shared with a neighbouring pane, so the model's
     * missing face still lands somewhere culling had already emptied. That is the whole reason the
     * rotation can be spent on the pillar: a corner has no face onto the cell to hide.
     */
    private static Direction cornerFace(int dx, int dz) {
        if (dz < 0) return dx > 0 ? Direction.WEST : Direction.SOUTH;
        return dx > 0 ? Direction.NORTH : Direction.EAST;
    }

    /** One write for all pane properties, so a forming shell does not flicker through states. */
    private static void setShellPane(Level level, BlockPos pos, boolean formed, Direction inward,
                                     boolean corner) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.QUANTUM_ATTUNED_GLASS.get())) return;
        BlockState next = state
                .setValue(QuantumFoundryStructure.FORMED, formed)
                .setValue(QuantumAttunedGlassBlock.OPEN_FACE, inward)
                .setValue(QuantumAttunedGlassBlock.CORNER, corner && formed);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
    }

    private static boolean isPlinth(Level level, BlockPos pos, PartClaim claim) {
        return level.isLoaded(pos)
                && level.getBlockState(pos).is(ModBlocks.QUANTUM_FOUNDRY_PLINTH.get())
                && claim.mine(pos);
    }

    /** Base ring and cap: the blocks that proxy power and the GUI to the controller. */
    public static boolean isPlinthPosition(BlockPos controller, BlockPos part) {
        if (Math.abs(part.getX() - controller.getX()) > 1
                || Math.abs(part.getZ() - controller.getZ()) > 1) {
            return false;
        }
        int dy = part.getY() - controller.getY();
        if (dy == CAP_OFFSET) return true;
        return dy == 0 && !(part.getX() == controller.getX() && part.getZ() == controller.getZ());
    }

    public static boolean coversPart(BlockPos controller, BlockPos part) {
        if (Math.abs(part.getX() - controller.getX()) <= 1
                && Math.abs(part.getZ() - controller.getZ()) <= 1) {
            int dy = part.getY() - controller.getY();
            if (dy >= 0 && dy <= CAP_OFFSET) return true;
        }
        for (int slot = 0; slot < MAX_ARMS; slot++) {
            Direction direction = armDirection(slot);
            BlockPos pillar = controller.relative(direction, 3);
            if (part.equals(controller.relative(direction, 2))
                    || part.equals(pillar)
                    || part.equals(pillar.above())) {
                return true;
            }
        }
        return false;
    }

    /** Lights the plinths and arms, points each conduit outward, and binds the plinths to the host. */
    public static void applyPartStates(Level level, BlockPos controller, int mask, PartClaim claim) {
        boolean formed = mask != 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy : new int[] {0, CAP_OFFSET}) {
                    BlockPos plinth = controller.offset(dx, dy, dz);
                    if (!isPlinthPosition(controller, plinth) || !claim.mine(plinth)) continue;
                    QuantumFoundryStructure.setRingPlinth(level, plinth, formed, dx, dz, dy == CAP_OFFSET);
                    MultiblockParts.bindPlinth(level, plinth, controller, formed);
                }
            }
        }

        // The shell's own panes switch to the borderless texture, so a live cell reads as one sheet
        // of glass rather than a stack of individually framed panes.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Direction inward = inwardFace(dx, dz);
                boolean corner = dx != 0 && dz != 0;
                for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                    BlockPos pane = controller.offset(dx, dy, dz);
                    if (claim.mine(pane)) setShellPane(level, pane, formed, inward, corner);
                }
            }
        }

        for (int slot = 0; slot < MAX_ARMS; slot++) {
            Direction direction = armDirection(slot);
            boolean arm = (mask & 1 << slot) != 0;
            BlockPos conduit = controller.relative(direction, 2);
            BlockPos pillar = controller.relative(direction, 3);
            BlockPos tank = pillar.above();
            if (claim.mine(conduit)) {
                QuantumFoundryStructure.setConduitAxis(level, conduit, direction.getAxis());
                QuantumFoundryStructure.setFormed(level, conduit, arm);
            }
            if (claim.mine(pillar)) QuantumFoundryStructure.setFormed(level, pillar, arm);
            if (claim.mine(tank)) QuantumFoundryStructure.setFormed(level, tank, arm);
        }
    }
}
