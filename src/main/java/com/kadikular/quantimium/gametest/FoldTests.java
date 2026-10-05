package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.FoldCoreBlock;
import com.kadikular.quantimium.block.FoldRailBlock;
import com.kadikular.quantimium.block.QuantumFoundryControllerBlock;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.fold.FoldChamber;
import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.fold.Folding;
import com.kadikular.quantimium.fold.FoundryFoldAdapter;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.init.ModRecipeTypes;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.recipe.CatalystResolver;
import com.kadikular.quantimium.recipe.CrafterPreview;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import com.kadikular.quantimium.recipe.foundry.FoundryIngredient;
import com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe;
import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.recipe.RecipeShapes;
import com.kadikular.quantimium.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;
import java.util.Optional;

/**
 * The Fold Chamber, proved on our own Quantum Foundry: the frame is found from its core, a formed
 * Foundry folds into a Folded Tesseract and back, the v1 safety rules refuse what they should, and
 * the Folded Tesseract is a Foundry catalyst in a Quantum Crafter.
 *
 * <p>The chamber is 7×7×7 unless a test says otherwise, its core at {@link #CORE} facing north; the Foundry stands one block above
 * its bottom layer, so its front arm reaches the front face without touching the bottom front edge.
 */
public final class FoldTests {

    private static final BlockPos CORE = new BlockPos(8, 2, 1);
    private static final int SIZE = 7;
    /** Lateral 0, one up, three back: the middle of the chamber's floor plus one. */
    private static final BlockPos CONTROLLER = new BlockPos(8, 3, 4);

    private FoldTests() {}

    // covers: fold.chamber_frame
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void aWholeFrameIsFoundFromItsCore(GameTestHelper helper) {
        buildChamber(helper, CORE, Direction.NORTH, SIZE, SIZE, SIZE);
        ServerLevel level = helper.getLevel();
        FoldChamber.Detection found = FoldChamber.detect(level, helper.absolutePos(CORE));
        helper.assertTrue(found.found(), "a whole 7³ frame should be found: " + found.problem());
        helper.assertValueEqual(found.chamber().dimensions(), "7×7×7", "chamber size");
        helper.assertTrue(found.chamber().contains(helper.absolutePos(CONTROLLER)), "the middle is in the volume");
        helper.assertFalse(found.chamber().contains(helper.absolutePos(CORE)), "the core is frame, not volume");

        // A rail up a back edge goes missing, and the frame is no longer whole.
        BlockPos backRail = new BlockPos(5, 4, 7);
        helper.assertBlockPresent(ModBlocks.FOLD_RAIL.get(), backRail);
        helper.setBlock(backRail, Blocks.AIR);
        helper.assertFalse(FoldChamber.detect(level, helper.absolutePos(CORE)).found(), "a gap in the frame");
        helper.succeed();
    }

    // covers: fold.chamber_frame
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void aFlatChamberFoldsAFoundry(GameTestHelper helper) {
        // 9 wide, 3 tall, 9 deep: built round a Foundry standing on the ground, level with the bottom
        // rails. Seven wide is too narrow at three tall: the side arms' tanks would sit on the top edges.
        BlockPos controller = CORE.south(4);
        buildChamber(helper, CORE, Direction.NORTH, 9, 3, 9);
        buildFoundry(helper, controller);
        ServerLevel level = helper.getLevel();
        FoldChamber.Detection found = FoldChamber.detect(level, helper.absolutePos(CORE));
        helper.assertTrue(found.found(), "a 9×3×9 frame should be found: " + found.problem());
        helper.assertValueEqual(found.chamber().dimensions(), "9×3×9", "chamber size");
        Folding.FoldResult result = Folding.fold(level, found.chamber(), boundTesseract(helper, controller));
        helper.assertTrue(result.folded(), "the Foundry should fold: " + result.problem());

        // A taller chamber would hold it too, but not a shallower one.
        ItemStack folded = result.stack();
        BlockPos core = found.chamber().core();
        helper.assertTrue(Folding.checkUnfold(level, new FoldChamber(core, Direction.NORTH, 9, 5, 9), folded).isEmpty(),
                "a taller chamber holds it");
        refuses(helper, Folding.checkUnfold(level, new FoldChamber(core, Direction.NORTH, 9, 3, 7), folded).orElse(null),
                "message.quantimium.fold.too_small");
        helper.assertFalse(Folding.unfold(level, found.chamber(), folded).isEmpty(), "it unfolds where it was");
        helper.assertTrue(helper.getBlockEntity(controller, QuantumFoundryBlockEntity.class).isFormed(), "formed again");
        helper.succeed();
    }

    // covers: fold.unfold_rules
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void aFoldNeedsRoomForItsBlocksNotItsBox(GameTestHelper helper) {
        // Folded in a roomy 13 × 5 × 13, the Foundry still only needs a 9 × 3 × 9 to stand in.
        BlockPos controller = CORE.south(6);
        buildChamber(helper, CORE, Direction.NORTH, 13, 5, 13);
        buildFoundry(helper, controller);
        ServerLevel level = helper.getLevel();
        FoldChamber big = FoldChamber.detect(level, helper.absolutePos(CORE)).chamber();
        helper.assertTrue(big != null, "a 13×5×13 frame");
        ItemStack folded = Folding.fold(level, big, boundTesseract(helper, controller)).stack();
        helper.assertFalse(folded.isEmpty(), "folded");

        BlockPos core = helper.absolutePos(CORE);
        refuses(helper, Folding.checkUnfold(level, new FoldChamber(core, Direction.NORTH, 7, 3, 7), folded)
                .orElse(null), "message.quantimium.fold.too_small");
        FoldChamber snug = new FoldChamber(core, Direction.NORTH, 9, 3, 9);
        helper.assertFalse(Folding.unfold(level, snug, folded).isEmpty(), "it unfolds in a 9×3×9");
        // Centred in the smaller box: its middle is four blocks back from the core.
        helper.assertTrue(helper.getBlockEntity(CORE.south(4), QuantumFoundryBlockEntity.class).isFormed(),
                "the Foundry stands in the middle of the smaller chamber, formed");
        helper.succeed();
    }

    // covers: fold.singularity
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void anEmptyChamberCollapsesAnUnboundTesseract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(CORE);
        ItemStack unbound = new ItemStack(ModItems.TESSERACT.get());
        FoldChamber big = new FoldChamber(core, Direction.NORTH, 9, 9, 9);
        // Nine tall reaches the test area's barrier roof, which isn't part of the test.
        big.forEachVolumeCell(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        helper.assertTrue(Folding.collapses(level, big, unbound), "an empty 9×9×9 collapses an unbound Tesseract");
        helper.assertTrue(Folding.collapse(level, big, unbound).is(ModItems.SINGULARITY.get()), "into a Singularity");

        FoldChamber small = new FoldChamber(core, Direction.NORTH, 9, 7, 9);
        helper.assertFalse(Folding.collapses(level, small, unbound), "seven tall is too small");
        refuses(helper, Folding.planFold(level, small, unbound).problem(), "message.quantimium.fold.singularity");

        helper.setBlock(new BlockPos(8, 4, 5), Blocks.STONE);
        helper.assertFalse(Folding.collapses(level, big, unbound), "something inside holds it open");
        helper.assertFalse(Folding.collapses(level, big, boundTesseract(helper, new BlockPos(8, 4, 5))), "a bound one folds");
        helper.succeed();
    }

    // covers: fold.fold_and_unfold
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 60)
    public static void aFormedFoundryFoldsAndUnfoldsAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoldChamber chamber = readyChamber(helper);
        ItemStack tesseract = boundTesseract(helper, CONTROLLER);

        Folding.FoldResult result = Folding.fold(level, chamber, tesseract);
        helper.assertTrue(result.folded(), "the Foundry should fold: " + result.problem());
        helper.assertBlockPresent(Blocks.AIR, CONTROLLER);
        helper.assertBlockPresent(Blocks.AIR, CONTROLLER.north(3).above());
        helper.assertBlockPresent(ModBlocks.FOLD_CORE.get(), CORE);

        ItemStack folded = result.stack();
        FoldedStructure structure = folded.get(ModDataComponents.FOLDED.get());
        helper.assertTrue(structure != null, "the Folded Tesseract holds a structure");
        helper.assertValueEqual(FoundryFoldAdapter.arms(structure.state()), 4, "arms kept");
        helper.assertValueEqual(structure.cells().size(), 9 + 4 * 3, "plinth, controller and four arms");

        ItemStack back = Folding.unfold(level, chamber, folded);
        helper.assertFalse(back.isEmpty(), "it should unfold where it was folded");
        helper.assertTrue(back.is(ModItems.TESSERACT.get()), "the Tesseract comes back");
        GlobalPos bound = back.get(ModDataComponents.BOUND_POS.get());
        helper.assertValueEqual(bound.pos(), helper.absolutePos(CONTROLLER), "bound to the controller");

        QuantumFoundryBlockEntity foundry = helper.getBlockEntity(CONTROLLER, QuantumFoundryBlockEntity.class);
        helper.assertTrue(foundry.isFormed(), "the Foundry forms again");
        helper.assertValueEqual(foundry.getPillarMask(), 0b1111, "all four arms");
        helper.succeed();
    }

    // covers: fold.core
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = FoldCoreBlockEntity.SEAL_TICKS + 60)
    public static void theCoreFoldsWhenToldAndPaysForIt(GameTestHelper helper) {
        readyChamber(helper);
        ServerPlayer player = TestSupport.player(helper, CORE.north());
        FoldCoreBlockEntity core = helper.getBlockEntity(CORE, FoldCoreBlockEntity.class);
        core.getSocket().setStackInSlot(0, boundTesseract(helper, CONTROLLER));

        // Seated, it only says what it would do; without power the button does nothing.
        core.start(player);
        helper.assertFalse(core.isSealing(), "no power, no fold");
        helper.assertValueEqual(core.getStatus(), FoldCoreBlockEntity.Status.NO_POWER, "status");
        int cost = (9 + 4 * 3) * Folding.FE_PER_BLOCK;
        core.getEnergyStorage().setEnergy(cost + 5);

        core.start(player);
        helper.assertTrue(core.isSealing(), "powered, it seals");
        helper.assertTrue(core.getSocket().extractItem(0, 1, true).isEmpty(), "the socket is locked while it seals");
        helper.runAfterDelay(FoldCoreBlockEntity.SEAL_TICKS + 5, () -> {
            helper.assertFalse(core.isSealing(), "done sealing");
            helper.assertTrue(core.getHeld().is(ModItems.FOLDED_TESSERACT.get()), "a Folded Tesseract in the core");
            helper.assertValueEqual(core.getEnergyStorage().getEnergyStored(), 5, "FE left after paying per block");
            helper.assertBlockPresent(Blocks.AIR, CONTROLLER);
            helper.assertBlockState(CORE, state -> state.getValue(QuantumFoundryStructure.FORMED),
                    state -> Component.literal("the core should be lit"));
            TestSupport.removePlayer(helper, player);
            helper.succeed();
        });
    }

    // covers: fold.refusals
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void theSafetyRulesRefuse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoldChamber chamber = readyChamber(helper);

        refuses(helper, Folding.planFold(level, chamber, new ItemStack(ModItems.TESSERACT.get())).problem(),
                "message.quantimium.fold.unbound");
        refuses(helper, Folding.planFold(level, chamber, boundTesseract(helper, CORE.north())).problem(),
                "message.quantimium.fold.bound_outside");

        // A chest with something in it, anywhere in the volume.
        BlockPos chestPos = new BlockPos(6, 2, 5);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.getBlockEntity(chestPos, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND));
        refuses(helper, Folding.planFold(level, chamber, boundTesseract(helper, CONTROLLER)).problem(),
                "message.quantimium.fold.holds");
        helper.getBlockEntity(chestPos, ChestBlockEntity.class).clearContent();
        helper.assertTrue(Folding.planFold(level, chamber, boundTesseract(helper, CONTROLLER)).plan() != null,
                "an empty chest folds along");

        helper.setBlock(new BlockPos(10, 2, 5), Blocks.BEDROCK);
        refuses(helper, Folding.planFold(level, chamber, boundTesseract(helper, CONTROLLER)).problem(),
                "message.quantimium.fold.immovable");
        helper.setBlock(new BlockPos(10, 2, 5), Blocks.AIR);

        QuantumFoundryBlockEntity foundry = helper.getBlockEntity(CONTROLLER, QuantumFoundryBlockEntity.class);
        foundry.getInventory().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        refuses(helper, Folding.planFold(level, chamber, boundTesseract(helper, CONTROLLER)).problem(),
                "message.quantimium.fold.controller_items");
        foundry.getInventory().setStackInSlot(0, ItemStack.EMPTY);

        // A plinth block short, and the Foundry isn't formed.
        helper.setBlock(CONTROLLER.east(), Blocks.AIR);
        refuses(helper, Folding.planFold(level, chamber, boundTesseract(helper, CONTROLLER)).problem(),
                "message.quantimium.fold.not_formed");
        helper.succeed();
    }

    // covers: fold.unfold_rules
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void unfoldingNeedsClearSpaceAndRoomEachWay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoldChamber chamber = readyChamber(helper);
        ItemStack folded = Folding.fold(level, chamber, boundTesseract(helper, CONTROLLER)).stack();
        helper.assertFalse(folded.isEmpty(), "folded");

        helper.setBlock(CONTROLLER, Blocks.STONE);
        refuses(helper, Folding.checkUnfold(level, chamber, folded).orElse(null), "message.quantimium.fold.blocked");
        helper.assertTrue(Folding.unfold(level, chamber, folded).isEmpty(), "nothing unfolds into a blocked cell");
        helper.assertBlockPresent(Blocks.STONE, CONTROLLER);
        helper.setBlock(CONTROLLER, Blocks.AIR);

        FoldChamber small = new FoldChamber(chamber.core(), Direction.NORTH, SIZE, SIZE, 5);
        refuses(helper, Folding.checkUnfold(level, small, folded).orElse(null), "message.quantimium.fold.too_small");

        // The same box with its core on the east edge instead: it unfolds just the same, the way it
        // was built, and the Foundry is back where it stood.
        FoldChamber turned = new FoldChamber(helper.absolutePos(new BlockPos(11, 2, 4)), Direction.EAST, SIZE, SIZE, SIZE);
        helper.assertValueEqual(turned.minCorner(), chamber.minCorner(), "the same box");
        helper.assertFalse(Folding.unfold(level, turned, folded).isEmpty(), "unfolds from another edge");
        helper.assertTrue(helper.getBlockEntity(CONTROLLER, QuantumFoundryBlockEntity.class).isFormed(),
                "the Foundry stands where it was, formed");
        helper.succeed();
    }

    // covers: fold.crafter_catalyst
    @GameTest(template = TestSupport.FLOOR_17, batch = "fold", timeoutTicks = 40)
    public static void aFoldedFoundryIsAFoundryCatalyst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FoldChamber chamber = readyChamber(helper);
        ItemStack folded = Folding.fold(level, chamber, boundTesseract(helper, CONTROLLER)).stack();

        helper.assertTrue(CatalystResolver.isCatalystAllowed(folded), "allowed as a catalyst");
        helper.assertTrue(CatalystResolver.resolve(folded).contains(ModRecipeTypes.FOUNDRY_TYPE.get()),
                "runs Foundry recipes");
        helper.assertValueEqual(CatalystResolver.requiredBand(folded), Config.crafterCatalystBand(3),
                "a multiblock's band");
        helper.assertFalse(CatalystResolver.isCatalystAllowed(new ItemStack(ModItems.FOLDED_TESSERACT.get())),
                "an empty Folded Tesseract is no catalyst");

        // Any Foundry recipe four arms can make, its ingredients in the grid, shows in the preview.
        RecipeHolder<?> holder = null;
        for (RecipeHolder<?> candidate : level.getServer().getRecipeManager().getRecipes()) {
            if (candidate.value() instanceof QuantumFoundryRecipe) {
                holder = candidate;
                break;
            }
        }
        helper.assertTrue(holder != null, "there are Foundry recipes");
        QuantumFoundryRecipe recipe = (QuantumFoundryRecipe) holder.value();
        ItemStackHandler grid = new ItemStackHandler(9);
        int slot = 0;
        for (FoundryIngredient ingredient : recipe.ingredients()) {
            ItemStack option = RecipeCompat.stacks(ingredient.ingredient()).getFirst();
            grid.setStackInSlot(slot++, option.copyWithCount(ingredient.count()));
        }
        List<ResolvedCraft> preview = new CrafterPreview().preview(level, helper.absolutePos(CORE), folded, grid, 9,
                Integer.MAX_VALUE);
        Identifier recipeId = holder.id().identifier();
        Optional<ResolvedCraft> craft = preview.stream().filter(c -> c.recipeId().equals(recipeId)).findFirst();
        helper.assertTrue(craft.isPresent(), "the Crafter offers " + recipeId);
        helper.assertTrue(craft.get().primaryOutput().is(recipe.result().getItem()), "its result");
        helper.assertTrue(RecipeShapes.forCatalyst(level, folded).stream().anyMatch(shape -> shape.id().equals(recipeId)),
                "and offers it as an ME pattern");
        // By id: the tag's constant lives with the AE2 compat, which may not be loaded.
        helper.assertTrue(folded.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                Identifier.fromNamespaceAndPath("quantimium", "superposition_crafter_catalysts"))),
                "the ME Superposition Crafter takes it");
        helper.succeed();
    }

    // ---- helpers ----

    private static void refuses(GameTestHelper helper, Component problem, String key) {
        helper.assertTrue(problem != null, "expected a refusal: " + key);
        String actual = problem.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : problem.getString();
        helper.assertValueEqual(actual, key, "refusal");
    }

    /** A size-7 chamber with a formed four-arm Foundry inside, empty and idle. */
    private static FoldChamber readyChamber(GameTestHelper helper) {
        buildChamber(helper, CORE, Direction.NORTH, SIZE, SIZE, SIZE);
        buildFoundry(helper, CONTROLLER);
        FoldChamber.Detection found = FoldChamber.detect(helper.getLevel(), helper.absolutePos(CORE));
        helper.assertTrue(found.found(), "chamber: " + found.problem());
        return found.chamber();
    }

    private static ItemStack boundTesseract(GameTestHelper helper, BlockPos relative) {
        ItemStack stack = new ItemStack(ModItems.TESSERACT.get());
        BlockPos pos = helper.absolutePos(relative);
        stack.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(helper.getLevel().dimension(), pos));
        stack.set(ModDataComponents.BOUND_SIDE.get(), Direction.UP);
        stack.set(ModDataComponents.BOUND_BLOCK.get(),
                BuiltInRegistries.BLOCK.getKey(helper.getLevel().getBlockState(pos).getBlock()));
        return stack;
    }

    /** A Fold Chamber frame, placed as the chamber would read it. */
    static void buildChamber(GameTestHelper helper, BlockPos core, Direction facing, int width, int height,
                             int depth) {
        helper.setBlock(core, ModBlocks.FOLD_CORE.get().defaultBlockState().setValue(FoldCoreBlock.FACING, facing));
        FoldChamber chamber = new FoldChamber(helper.absolutePos(core), facing, width, height, depth);
        // Placed by absolute position: GameTestHelper.relativePos turns an unrotated test's positions 180°.
        ServerLevel level = helper.getLevel();
        chamber.forEachFrameCell((pos, part, axis) -> {
            switch (part) {
                case PYLON -> level.setBlockAndUpdate(pos, ModBlocks.FOLD_PYLON.get().defaultBlockState());
                case RAIL -> level.setBlockAndUpdate(pos,
                        ModBlocks.FOLD_RAIL.get().defaultBlockState().setValue(FoldRailBlock.AXIS, axis));
                case CORE -> { }
            }
        });
    }

    /** A Quantum Foundry with all four arms, formed. */
    static QuantumFoundryBlockEntity buildFoundry(GameTestHelper helper, BlockPos controller) {
        helper.setBlock(controller, ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get().defaultBlockState()
                .setValue(QuantumFoundryControllerBlock.FACING, Direction.NORTH));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) helper.setBlock(controller.offset(dx, 0, dz), ModBlocks.QUANTUM_FOUNDRY_PLINTH.get());
            }
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(controller.relative(direction, 2), ModBlocks.QUANTUM_FOUNDRY_CONDUIT.get());
            helper.setBlock(controller.relative(direction, 3), ModBlocks.QUANTUM_FOUNDRY_PILLAR.get());
            helper.setBlock(controller.relative(direction, 3).above(), ModBlocks.QUANTUM_FOUNDRY_ATTUNEMENT_TANK.get());
        }
        QuantumFoundryBlockEntity foundry = helper.getBlockEntity(controller, QuantumFoundryBlockEntity.class);
        foundry.revalidateStructure();
        helper.assertTrue(foundry.isFormed(), "the Foundry should form");
        return foundry;
    }
}
