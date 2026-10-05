package com.kadikular.quantimium.block;

import com.kadikular.quantimium.block.multiblock.MultiblockParts;
import com.kadikular.quantimium.block.multiblock.PartClaim;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Fixed 3x3 plinth with one to four complete cardinal attunement arms. */
public final class QuantumFoundryStructure {
    /** Set on every part of a live foundry so the art can wake up once the field is attuned. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private QuantumFoundryStructure() {}

    /** Bit mask in north/east/south/west order, or zero when the mandatory plinth is incomplete. */
    public static int pillarMask(Level level, BlockPos controller) {
        return pillarMask(level, controller, PartClaim.of(level, controller));
    }

    public static int pillarMask(Level level, BlockPos controller, PartClaim claim) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos part = controller.offset(dx, 0, dz);
                if (!level.isLoaded(part)) return 0;
                if (dx == 0 && dz == 0) {
                    if (!level.getBlockState(part).is(ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get())) return 0;
                } else if (!level.getBlockState(part).is(ModBlocks.QUANTUM_FOUNDRY_PLINTH.get())
                        || !claim.mine(part)) {
                    return 0;
                }
            }
        }

        int mask = 0;
        for (int i = 0; i < 4; i++) {
            if (hasArm(level, controller, pillarDirection(level.getBlockState(controller), i), claim)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static boolean hasArm(Level level, BlockPos controller, Direction direction) {
        return hasArm(level, controller, direction, PartClaim.unchallenged(controller));
    }

    public static boolean hasArm(Level level, BlockPos controller, Direction direction, PartClaim claim) {
        BlockPos conduit = controller.relative(direction, 2);
        BlockPos pillar = controller.relative(direction, 3);
        BlockPos tank = pillar.above();
        return level.isLoaded(conduit) && level.isLoaded(pillar) && level.isLoaded(tank)
                && level.getBlockState(conduit)
                        .is(ModBlocks.QUANTUM_FOUNDRY_CONDUIT.get())
                && level.getBlockState(pillar)
                        .is(ModBlocks.QUANTUM_FOUNDRY_PILLAR.get())
                && level.getBlockState(tank)
                        .is(ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK.get())
                && claim.mine(conduit) && claim.mine(pillar) && claim.mine(tank);
    }

    public static boolean isPlinthPosition(BlockPos controller, BlockPos part) {
        return part.getY() == controller.getY()
                && Math.abs(part.getX() - controller.getX()) <= 1
                && Math.abs(part.getZ() - controller.getZ()) <= 1;
    }

    /** GUI order is controller-forward, right, back, left. */
    public static Direction pillarDirection(BlockState controller, int slot) {
        Direction front = controller.hasProperty(QuantumFoundryControllerBlock.FACING)
                ? controller.getValue(QuantumFoundryControllerBlock.FACING) : Direction.NORTH;
        return switch (slot & 3) {
            case 0 -> front;
            case 1 -> front.getClockWise();
            case 2 -> front.getOpposite();
            default -> front.getCounterClockWise();
        };
    }

    /** True when the position belongs to this foundry's layout, whatever is actually built there. */
    public static boolean coversPart(Level level, BlockPos controller, BlockPos part) {
        if (isPlinthPosition(controller, part)) return true;
        BlockState state = level.getBlockState(controller);
        for (int slot = 0; slot < 4; slot++) {
            Direction direction = pillarDirection(state, slot);
            BlockPos pillar = controller.relative(direction, 3);
            if (part.equals(controller.relative(direction, 2))
                    || part.equals(pillar)
                    || part.equals(pillar.above())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lights the plinth and every complete arm, and points each conduit along its own arm. Called
     * from the controller because only the controller knows which direction an arm runs in.
     */
    public static void applyPartStates(Level level, BlockPos controller, int mask) {
        applyPartStates(level, controller, mask, PartClaim.of(level, controller));
    }

    public static void applyPartStates(Level level, BlockPos controller, int mask, PartClaim claim) {
        boolean formed = mask != 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos part = controller.offset(dx, 0, dz);
                if (!claim.mine(part)) continue;
                setRingPlinth(level, part, formed, dx, dz, false);
                MultiblockParts.bindPlinth(level, part, controller, formed);
            }
        }

        BlockState controllerState = level.getBlockState(controller);
        for (int slot = 0; slot < 4; slot++) {
            Direction direction = pillarDirection(controllerState, slot);
            boolean arm = (mask & 1 << slot) != 0;
            BlockPos conduit = controller.relative(direction, 2);
            BlockPos pillar = controller.relative(direction, 3);
            BlockPos tank = pillar.above();
            // Never write to a part another controller owns, or the two fight every revalidation.
            if (claim.mine(conduit)) {
                setConduitAxis(level, conduit, direction.getAxis());
                setFormed(level, conduit, arm);
            }
            if (claim.mine(pillar)) setFormed(level, pillar, arm);
            if (claim.mine(tank)) setFormed(level, tank, arm);
        }
    }

    /**
     * Marks a plinth's place in a 3×3 so its top face can be drawn as part of one slab.
     *
     * <p>Shared by both machines because every ring in the mod is the same shape: the offsets from
     * the centre are all the geometry the art needs. One write for all three properties, so a
     * forming ring does not step through a frame of mismatched tops. Silently skips anything that is
     * not a plinth, which is how the controller in the middle of a base ring is left alone.
     */
    static void setRingPlinth(Level level, BlockPos pos, boolean formed, int dx, int dz,
                              boolean cap) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(QuantumFoundryPlinthBlock.ROLE)) {
            setFormed(level, pos, formed);
            return;
        }
        BlockState next = state
                .setValue(FORMED, formed)
                .setValue(QuantumFoundryPlinthBlock.ROLE, ringRole(dx, dz))
                .setValue(QuantumFoundryPlinthBlock.FACING, ringFacing(dx, dz))
                .setValue(QuantumFoundryPlinthBlock.CAP, cap);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
    }

    private static QuantumFoundryPlinthBlock.Role ringRole(int dx, int dz) {
        if (dx == 0 && dz == 0) return QuantumFoundryPlinthBlock.Role.CENTRE;
        if (dx != 0 && dz != 0) return QuantumFoundryPlinthBlock.Role.CORNER;
        return QuantumFoundryPlinthBlock.Role.EDGE;
    }

    /**
     * An edge faces outward; a corner names the rotation that carries the north-west piece onto it.
     * Either way the blockstate turns the art by 0/90/180/270 for north/east/south/west.
     */
    private static Direction ringFacing(int dx, int dz) {
        if (dx != 0 && dz != 0) {
            if (dz < 0) return dx < 0 ? Direction.NORTH : Direction.EAST;
            return dx > 0 ? Direction.SOUTH : Direction.WEST;
        }
        if (dx != 0) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (dz != 0) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return Direction.NORTH;
    }

    static void setFormed(Level level, BlockPos pos, boolean formed) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(FORMED) || state.getValue(FORMED) == formed) return;
        level.setBlock(pos, state.setValue(FORMED, formed), Block.UPDATE_CLIENTS);
    }

    static void setConduitAxis(Level level, BlockPos pos, Direction.Axis axis) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(QuantumFoundryConduitBlock.AXIS)
                || state.getValue(QuantumFoundryConduitBlock.AXIS) == axis) {
            return;
        }
        level.setBlock(pos, state.setValue(QuantumFoundryConduitBlock.AXIS, axis), Block.UPDATE_CLIENTS);
    }

}
