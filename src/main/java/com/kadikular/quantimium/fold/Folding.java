package com.kadikular.quantimium.fold;

import com.kadikular.quantimium.block.FoldCoreBlock;
import com.kadikular.quantimium.block.FoldPylonBlock;
import com.kadikular.quantimium.block.FoldRailBlock;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.item.TesseractItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Folding a chamber's volume into a Folded Tesseract, and unfolding one back out.
 *
 * <p>Both directions check everything first and then happen in one go, inside one server tick: the
 * art shows the volume folding block by block, but nothing in the world is ever half folded.
 *
 * <p>Safety rules, v1: contents never cross a fold, so anything holding items or fluid refuses it, as
 * does anything mid-recipe, unbreakable or on {@link ModTags#UNFOLDABLE}. A fold never rotates: it
 * unfolds the way it was built, in any chamber with room for its blocks (see {@link #landing}).
 */
public final class Folding {

    /** Placed or removed without drops, block entity side effects or neighbour reactions; those come after, all at once. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

    private static final Direction[] SIDES_AND_NONE = {
            null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private Folding() {}

    /** A Singularity takes an empty chamber at least this many blocks every way. */
    public static final int SINGULARITY_MIN_SIDE = 9;
    /** Energy to collapse a Tesseract into a Singularity. */
    public static final int SINGULARITY_FE = 1_000_000;

    /** Energy to fold one block. Unfolding is free. */
    public static final int FE_PER_BLOCK = 2_000;

    /** A fold the chamber can make: whose controller, which adapter answers for it, and how many blocks go. */
    public record FoldPlan(FoldAdapter adapter, BlockPos controller, int blocks) {
        public int energy() {
            return blocks * FE_PER_BLOCK;
        }
    }

    /** Energy to unfold {@code folded}: none, the work was done folding it. */
    public static int unfoldEnergy(FoldedStructure folded) {
        return 0;
    }

    /** What {@link #planFold} found: a plan, or why not. */
    public record FoldCheck(FoldPlan plan, Component problem) {
        static FoldCheck refuse(Component problem) {
            return new FoldCheck(null, problem);
        }
    }

    /** Everything that must hold for {@code tesseract} to fold {@code chamber}'s volume. Changes nothing. */
    public static FoldCheck planFold(ServerLevel level, FoldChamber chamber, ItemStack tesseract) {
        if (!(tesseract.getItem() instanceof TesseractItem)) {
            return FoldCheck.refuse(Component.translatable("message.quantimium.fold.not_tesseract"));
        }
        GlobalPos bound = tesseract.get(ModDataComponents.BOUND_POS.get());
        if (bound == null) {
            return FoldCheck.refuse(isEmpty(level, chamber)
                    ? Component.translatable("message.quantimium.fold.singularity", SINGULARITY_MIN_SIDE)
                    : Component.translatable("message.quantimium.fold.unbound"));
        }
        if (!bound.dimension().equals(level.dimension()) || !chamber.contains(bound.pos())) {
            return FoldCheck.refuse(Component.translatable("message.quantimium.fold.bound_outside"));
        }
        BlockPos controller = bound.pos();
        BlockState controllerState = level.getBlockState(controller);
        Optional<FoldAdapter> found = FoldAdapters.forController(controllerState);
        if (found.isEmpty()) {
            return FoldCheck.refuse(Component.translatable("message.quantimium.fold.no_adapter",
                    controllerState.getBlock().getName()));
        }
        FoldAdapter adapter = found.get();
        Optional<Component> machine = adapter.refusal(level, controller);
        if (machine.isPresent()) return FoldCheck.refuse(machine.get());

        int blocks = 0;
        for (BlockPos pos : chamber.volume()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            Optional<Component> problem = cellRefusal(level, pos, state,
                    adapter.ownsPart(level, controller, pos));
            if (problem.isPresent()) return FoldCheck.refuse(problem.get());
            blocks++;
        }
        return new FoldCheck(new FoldPlan(adapter, controller.immutable(), blocks), null);
    }

    private static Optional<Component> cellRefusal(ServerLevel level, BlockPos pos, BlockState state, boolean owned) {
        if (state.getDestroySpeed(level, pos) < 0 || state.is(ModTags.UNFOLDABLE) || isFrame(state)) {
            return Optional.of(Component.translatable("message.quantimium.fold.immovable",
                    state.getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()));
        }
        // The machine's own parts often stand in for the controller's inventory; its adapter has
        // already answered for them.
        if (owned || !state.hasBlockEntity()) return Optional.empty();
        if (holdsSomething(level, pos)) {
            return Optional.of(Component.translatable("message.quantimium.fold.holds",
                    state.getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()));
        }
        return Optional.empty();
    }

    private static boolean isFrame(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FoldCoreBlock || block instanceof FoldPylonBlock || block instanceof FoldRailBlock;
    }

    /** Items, fluid or energy, from any side. */
    private static boolean holdsSomething(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container container && !container.isEmpty()) return true;
        for (Direction side : SIDES_AND_NONE) {
            if (holds(level.getCapability(Capabilities.Item.BLOCK, pos, side))) return true;
            if (holds(level.getCapability(Capabilities.Fluid.BLOCK, pos, side))) return true;
            EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
            if (energy != null && energy.getAmountAsLong() > 0) return true;
        }
        return false;
    }

    private static boolean holds(ResourceHandler<?> handler) {
        if (handler == null) return false;
        for (int index = 0; index < handler.size(); index++) {
            if (handler.getAmountAsLong(index) > 0) return true;
        }
        return false;
    }

    /**
     * Whether folding {@code stack} in {@code chamber} collapses it instead: an unbound (full)
     * Tesseract, with nothing in the volume to hold it open, in a chamber big enough every way.
     */
    public static boolean collapses(ServerLevel level, FoldChamber chamber, ItemStack stack) {
        return stack.is(ModItems.TESSERACT.get()) && stack.get(ModDataComponents.BOUND_POS.get()) == null
                && chamber.width() >= SINGULARITY_MIN_SIDE && chamber.height() >= SINGULARITY_MIN_SIDE
                && chamber.depth() >= SINGULARITY_MIN_SIDE && isEmpty(level, chamber);
    }

    /** The Singularity {@code stack} collapses into, or empty when it doesn't. */
    public static ItemStack collapse(ServerLevel level, FoldChamber chamber, ItemStack stack) {
        return collapses(level, chamber, stack) ? new ItemStack(ModItems.SINGULARITY.get()) : ItemStack.EMPTY;
    }

    private static boolean isEmpty(ServerLevel level, FoldChamber chamber) {
        for (BlockPos pos : chamber.volume()) {
            if (!level.getBlockState(pos).isAir()) return false;
        }
        return true;
    }

    /**
     * Folds the volume into a Folded Tesseract, or says why it can't. Checks again first: the chamber
     * seals for a moment before it folds, and anything could have changed in it.
     */
    public static FoldResult fold(ServerLevel level, FoldChamber chamber, ItemStack tesseract) {
        FoldCheck check = planFold(level, chamber, tesseract);
        if (check.plan() == null) return new FoldResult(ItemStack.EMPTY, check.problem());
        FoldAdapter adapter = check.plan().adapter();
        BlockPos controller = check.plan().controller();

        Block controllerBlock = level.getBlockState(controller).getBlock();
        CompoundTag kept = adapter.capture(level, controller);
        BlockPos corner = chamber.minCorner();
        List<FoldedStructure.Cell> cells = new ArrayList<>();
        List<BlockPos> taken = new ArrayList<>();
        for (BlockPos pos : chamber.volume()) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            Optional<CompoundTag> data = blockEntity == null ? Optional.empty()
                    : Optional.of(blockEntity.saveWithoutMetadata(level.registryAccess()));
            cells.add(new FoldedStructure.Cell(pos.subtract(corner), state, data));
            taken.add(pos);
        }
        for (BlockPos pos : taken) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
        }
        for (BlockPos pos : taken) settle(level, pos);

        ItemStack shell = tesseract.copyWithCount(1);
        FoldedStructure folded = new FoldedStructure(adapter.id(), chamber.sizeX(), chamber.height(), chamber.sizeZ(),
                controller.subtract(corner), cells, kept, shell);
        ItemStack result = new ItemStack(ModItems.FOLDED_TESSERACT.get());
        result.set(ModDataComponents.FOLDED.get(), folded);
        // What turns inside the shell, as a bound Tesseract shows its block.
        result.set(ModDataComponents.BOUND_BLOCK.get(), BuiltInRegistries.BLOCK.getKey(controllerBlock));
        return new FoldResult(result, null);
    }

    public record FoldResult(ItemStack stack, Component problem) {
        public boolean folded() {
            return !stack.isEmpty();
        }
    }

    /** Why {@code stack} can't unfold in {@code chamber}, or empty when it can. Changes nothing. */
    public static Optional<Component> checkUnfold(ServerLevel level, FoldChamber chamber, ItemStack stack) {
        FoldedStructure folded = stack.get(ModDataComponents.FOLDED.get());
        if (folded == null) return Optional.of(Component.translatable("message.quantimium.fold.empty_fold"));
        if (folded.adapterOrEmpty().isEmpty()) {
            return Optional.of(Component.translatable("message.quantimium.fold.adapter_missing",
                    folded.adapter().toString()));
        }
        Optional<BlockPos> landing = landing(chamber, folded);
        if (landing.isEmpty()) {
            return Optional.of(Component.translatable("message.quantimium.fold.too_small",
                    folded.contentDimensions(), chamber.worldDimensions()));
        }
        for (FoldedStructure.Cell cell : folded.cells()) {
            BlockPos pos = landing.get().offset(cell.offset());
            if (!level.getBlockState(pos).isAir()) {
                return Optional.of(Component.translatable("message.quantimium.fold.blocked",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return Optional.empty();
    }

    /**
     * Where the corner of the box {@code folded} came from lands in {@code chamber}, so that every
     * block sits in the volume and none on the frame; empty if nowhere does. Only the blocks need
     * room, not the box: a machine folded in a big chamber unfolds in any that still fits it.
     *
     * <p>The machine is centred east–west and north–south, and stands as high above the floor as it
     * did if it can, else as low as fits. A centred box's edges only ever meet a bigger one's edges,
     * but the machine is smaller than its box, so each placement is checked block by block.
     */
    static Optional<BlockPos> landing(FoldChamber chamber, FoldedStructure folded) {
        BlockPos min = folded.contentMin();
        BlockPos size = folded.contentSize();
        if (size.getX() > chamber.sizeX() || size.getY() > chamber.height() || size.getZ() > chamber.sizeZ()) {
            return Optional.empty();
        }
        List<Integer> heights = new ArrayList<>();
        if (min.getY() + size.getY() <= chamber.height()) heights.add(min.getY());
        for (int y = 0; y + size.getY() <= chamber.height(); y++) {
            if (!heights.contains(y)) heights.add(y);
        }
        int spareX = chamber.sizeX() - size.getX();
        int spareZ = chamber.sizeZ() - size.getZ();
        // Centred; on an odd spare block, try it either side.
        int[] xs = {spareX / 2, (spareX + 1) / 2};
        int[] zs = {spareZ / 2, (spareZ + 1) / 2};
        BlockPos corner = chamber.minCorner();
        for (int y : heights) {
            for (int x : xs) {
                for (int z : zs) {
                    BlockPos landing = corner.offset(x, y, z).subtract(min);
                    if (fits(chamber, folded, landing)) return Optional.of(landing);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean fits(FoldChamber chamber, FoldedStructure folded, BlockPos landing) {
        for (FoldedStructure.Cell cell : folded.cells()) {
            if (!chamber.contains(landing.offset(cell.offset()))) return false;
        }
        return true;
    }

    /**
     * Unfolds {@code stack} into the chamber and hands back the Tesseract it was folded with, bound
     * to the controller where it now stands. Empty, with nothing changed, when it can't unfold.
     */
    public static ItemStack unfold(ServerLevel level, FoldChamber chamber, ItemStack stack) {
        if (checkUnfold(level, chamber, stack).isPresent()) return ItemStack.EMPTY;
        FoldedStructure folded = stack.get(ModDataComponents.FOLDED.get());
        FoldAdapter adapter = folded.adapterOrEmpty().orElseThrow();

        BlockPos landing = landing(chamber, folded).orElseThrow();
        List<BlockPos> placed = new ArrayList<>();
        for (FoldedStructure.Cell cell : folded.cells()) {
            BlockPos pos = landing.offset(cell.offset());
            level.setBlock(pos, cell.state(), QUIET);
            placed.add(pos);
        }
        for (FoldedStructure.Cell cell : folded.cells()) {
            if (cell.data().isEmpty()) continue;
            BlockPos pos = landing.offset(cell.offset());
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) continue;
            blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
                    cell.data().get()));
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, cell.state(), cell.state(), Block.UPDATE_CLIENTS);
        }
        for (BlockPos pos : placed) settle(level, pos);

        BlockPos controller = landing.offset(folded.controller());
        adapter.reform(level, controller, folded.state());

        ItemStack tesseract = folded.tesseract().isEmpty()
                ? new ItemStack(ModItems.TESSERACT.get()) : folded.tesseract().copyWithCount(1);
        tesseract.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(level.dimension(), controller));
        tesseract.set(ModDataComponents.BOUND_BLOCK.get(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(controller).getBlock()));
        return tesseract;
    }

    /** The neighbour updates the quiet placement held back: shapes, then neighbour reactions. */
    private static void settle(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, state.getBlock(), null);
    }
}
