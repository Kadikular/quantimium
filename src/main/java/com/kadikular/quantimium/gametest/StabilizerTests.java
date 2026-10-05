package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import com.kadikular.quantimium.util.LegacyFluids;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.TesseractStabilizerBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.block.TesseractStabilizerBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tesseracts and the Tesseract Stabiliser: binding, docking, and a remote chest offered through the
 * Stabilizer's faces. See wiki: machines/tesseract-stabilizer.
 */
public final class StabilizerTests {

    private static final BlockPos STABILIZER = new BlockPos(4, 2, 4);
    private static final BlockPos CHEST = new BlockPos(1, 2, 1);

    private StabilizerTests() {}

    // covers: tesseract.bind
    @GameTest(template = TestSupport.FLOOR_9, batch = "tesseract_bind", timeoutTicks = 40)
    public static void sneakUseBindsToTheFaceClicked(GameTestHelper helper) {
        TestPlayer player = TestSupport.player(helper, STABILIZER, net.minecraft.world.level.GameType.CREATIVE);
        helper.setBlock(CHEST, Blocks.CHEST);
        ItemStack link = new ItemStack(ModItems.TESSERACT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, link);
        BlockPos at = helper.absolutePos(CHEST);

        player.setShiftKeyDown(true);
        link.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.EAST, at, false)));
        helper.assertTrue(EntangledLinks.isBound(link), "a sneak-use should bind it");
        GlobalPos bound = link.get(ModDataComponents.BOUND_POS.get());
        helper.assertValueEqual(bound.pos(), at, "bound position");
        helper.assertValueEqual(link.get(ModDataComponents.BOUND_SIDE.get()), Direction.EAST, "bound face");

        // Without sneaking, using it on a block does nothing.
        ItemStack other = new ItemStack(ModItems.TESSERACT.get());
        player.setShiftKeyDown(false);
        other.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.EAST, at, false)));
        helper.assertTrue(!EntangledLinks.isBound(other), "a plain use should not bind");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: stabilizer.dock
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_dock", timeoutTicks = 40)
    public static void onlyTheFullTesseractDocks(GameTestHelper helper) {
        TesseractStabilizerBlockEntity stabilizer = stabilizer(helper);
        ItemStack semi = new ItemStack(ModItems.SEMI_STABLE_TESSERACT.get());
        helper.assertTrue(!EntangledLinks.fitsStabilizer(semi), "a Semi-Stable Tesseract should not dock");
        helper.assertTrue(stabilizer.getInventory().insertItem(TesseractStabilizerBlockEntity.LINK_SLOT, semi, false)
                .getCount() == 1, "the dock should refuse it");
        ItemStack full = CrafterTests.link(helper, CHEST, Blocks.CHEST);
        helper.assertTrue(stabilizer.getInventory().insertItem(TesseractStabilizerBlockEntity.LINK_SLOT, full, false)
                .isEmpty(), "the dock should take a Tesseract");
        helper.assertValueEqual(stabilizer.getInventory().getSlotLimit(TesseractStabilizerBlockEntity.LINK_SLOT), 1, "dock size");
        helper.succeed();
    }

    // covers: stabilizer.proxy, stabilizer.status
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_proxy", timeoutTicks = 40)
    public static void itsFacesAreTheRemoteChest(GameTestHelper helper) {
        ChestBlockEntity chest = CrafterTests.chest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 10));
        TesseractStabilizerBlockEntity stabilizer = docked(helper);
        helper.assertValueEqual(stabilizer.resolveStatus(), TesseractStabilizerBlockEntity.STATUS_LINKED, "status");
        BlockPos at = helper.absolutePos(STABILIZER);

        IItemHandler side = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.NORTH));
        helper.assertTrue(side != null, "a side face should offer the chest");
        ItemStack out = side.extractItem(0, 4, false);
        helper.assertValueEqual(out.getCount(), 4, "ingots taken through the Stabilizer");
        helper.assertValueEqual(chest.getItem(0).getCount(), 6, "ingots left in the chest");
        helper.assertTrue(side.insertItem(1, new ItemStack(Items.REDSTONE, 3), false).isEmpty(),
                "things put in should go into the chest");
        helper.assertTrue(chest.getItem(1).is(Items.REDSTONE), "the redstone should be in the chest");

        helper.assertTrue(LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.UP)) == null,
                "the top should offer nothing");
        helper.succeed();
    }

    // covers: tesseract.bound_block, stabilizer.status
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_replaced", timeoutTicks = 40)
    public static void aReplacedTargetBreaksTheLink(GameTestHelper helper) {
        CrafterTests.chest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 10));
        TesseractStabilizerBlockEntity stabilizer = docked(helper);
        helper.setBlock(CHEST, Blocks.BARREL);
        helper.assertValueEqual(stabilizer.resolveStatus(), TesseractStabilizerBlockEntity.STATUS_UNLOADED,
                "status once the chest is a barrel");
        helper.assertTrue(LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(STABILIZER), Direction.NORTH)) == null, "a dead link should offer nothing");
        helper.succeed();
    }

    // covers: stabilizer.facing
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_facing", timeoutTicks = 40)
    public static void itFacesAwayFromWhatItWasPlacedOn(GameTestHelper helper) {
        // Placed against a machine's east side, it points east: base on the machine, open face out.
        ServerPlayer player = TestSupport.player(helper, new BlockPos(7, 2, 4));
        BlockPos machine = helper.absolutePos(new BlockPos(3, 2, 4));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(machine).add(0.5, 0, 0), Direction.EAST, machine, false);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                new ItemStack(ModBlocks.TESSERACT_STABILIZER.get()), hit);
        BlockState placed = ModBlocks.TESSERACT_STABILIZER.get().getStateForPlacement(context);
        helper.assertValueEqual(placed.getValue(TesseractStabilizerBlock.FACING), Direction.EAST, "facing on a machine's east side");
        TestSupport.removePlayer(helper, player);

        // Facing north, the north face is the open one and the top works like any other side.
        CrafterTests.chest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 10));
        TestSupport.track(helper);
        helper.setBlock(STABILIZER, ModBlocks.TESSERACT_STABILIZER.get().defaultBlockState()
                .setValue(TesseractStabilizerBlock.FACING, Direction.NORTH));
        TesseractStabilizerBlockEntity stabilizer = helper.getBlockEntity(STABILIZER, TesseractStabilizerBlockEntity.class);
        stabilizer.getInventory().setStackInSlot(TesseractStabilizerBlockEntity.LINK_SLOT,
                CrafterTests.link(helper, CHEST, Blocks.CHEST));
        BlockPos at = helper.absolutePos(STABILIZER);
        helper.assertTrue(LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.NORTH)) == null,
                "the open face should offer nothing");
        IItemHandler top = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.UP));
        helper.assertTrue(top != null, "the top should offer the chest when the open face is north");
        helper.assertValueEqual(top.extractItem(0, 2, false).getCount(), 2, "ingots taken through the top");
        helper.succeed();
    }

    // covers: stabilizer.sides
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_sides", timeoutTicks = 40)
    public static void anInFaceOnlyTakesThingsIn(GameTestHelper helper) {
        ChestBlockEntity chest = CrafterTests.chest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 10));
        TesseractStabilizerBlockEntity stabilizer = docked(helper);
        stabilizer.applySideConfigs(CrafterTests.with(stabilizer.getSideConfigs(), new SideConfig(Direction.NORTH,
                SideMode.INPUT, SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, false, false,
                SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false)));
        helper.runAfterDelay(1, () -> {
            IItemHandler north = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                    helper.absolutePos(STABILIZER), Direction.NORTH));
            helper.assertTrue(north.extractItem(0, 4, false).isEmpty(), "an In face should give nothing out");
            helper.assertValueEqual(chest.getItem(0).getCount(), 10, "ingots in the chest");
            helper.assertTrue(north.insertItem(1, new ItemStack(Items.REDSTONE), false).isEmpty(),
                    "an In face should still take things in");
            helper.assertTrue(LegacyFluids.legacy(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                    helper.absolutePos(STABILIZER), Direction.NORTH)) == null, "fluids are Off on that face");
            helper.succeed();
        });
    }

    // covers: stabilizer.auto_transfer, sides.auto
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_auto", timeoutTicks = 80)
    public static void autoOutputEmptiesTheRemoteChestIntoANeighbour(GameTestHelper helper) {
        ChestBlockEntity remote = CrafterTests.chest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 64));
        remote.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
        TesseractStabilizerBlockEntity stabilizer = docked(helper);
        ChestBlockEntity neighbour = CrafterTests.chest(helper, STABILIZER.east(), ItemStack.EMPTY);
        stabilizer.applySideConfigs(CrafterTests.with(stabilizer.getSideConfigs(), new SideConfig(Direction.EAST,
                SideMode.BOTH, SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, false, true,
                SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false)));
        // Once a second, on the world's clock, up to one stack: the first transfer moves exactly 64.
        int[] first = {0};
        helper.onEachTick(() -> {
            if (first[0] == 0) first[0] = count(neighbour);
        });
        helper.succeedWhen(() -> {
            helper.assertValueEqual(first[0], 64, "ingots moved by the first transfer");
            helper.assertValueEqual(count(neighbour), 128, "ingots moved by the second");
        });
    }

    // covers: stabilizer.auto_transfer, crafter.on_demand
    @GameTest(template = TestSupport.FLOOR_9, batch = "stabilizer_crafter", timeoutTicks = 100)
    public static void autoOutputCraftsFromALinkedCrafterIntoABarrel(GameTestHelper helper) {
        // A linked Crafter's outputs are crafted as they are taken: an export should craft, spend the
        // ingredients and land the result in the barrel beside the Stabilizer.
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, CHEST, Items.FURNACE);
        CrafterTests.grid(crafter, new ItemStack(Items.RAW_IRON, 8));
        TesseractStabilizerBlockEntity stabilizer = stabilizer(helper);
        stabilizer.getInventory().setStackInSlot(TesseractStabilizerBlockEntity.LINK_SLOT,
                CrafterTests.link(helper, CHEST, ModBlocks.QUANTUM_CRAFTER.get()));
        helper.setBlock(STABILIZER.east(), Blocks.BARREL);
        BarrelBlockEntity barrel = helper.getBlockEntity(STABILIZER.east(), BarrelBlockEntity.class);
        stabilizer.applySideConfigs(CrafterTests.with(stabilizer.getSideConfigs(), new SideConfig(Direction.EAST,
                SideMode.BOTH, SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, false, true,
                SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false)));
        helper.succeedWhen(() -> {
            int ingots = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                if (barrel.getItem(slot).is(Items.IRON_INGOT)) ingots += barrel.getItem(slot).getCount();
            }
            helper.assertTrue(ingots > 0, "the barrel should have ingots from the crafter");
            helper.assertValueEqual(crafter.getInventory().getStackInSlot(0).getCount() + ingots, 8,
                    "raw iron left plus ingots made");
        });
    }

    private static int count(ChestBlockEntity chest) {
        int count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) count += chest.getItem(slot).getCount();
        return count;
    }

    // ---- helpers ----

    private static TesseractStabilizerBlockEntity stabilizer(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(STABILIZER, ModBlocks.TESSERACT_STABILIZER.get());
        return helper.getBlockEntity(STABILIZER, TesseractStabilizerBlockEntity.class);
    }

    /** A Stabilizer with a Tesseract bound to the top of the chest at {@link #CHEST}. */
    private static TesseractStabilizerBlockEntity docked(GameTestHelper helper) {
        TesseractStabilizerBlockEntity stabilizer = stabilizer(helper);
        stabilizer.getInventory().setStackInSlot(TesseractStabilizerBlockEntity.LINK_SLOT,
                CrafterTests.link(helper, CHEST, Blocks.CHEST));
        return stabilizer;
    }
}
