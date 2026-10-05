package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideConfigWrench;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.flux.FieldModel;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The Quantum Crafter and the Basic Quantum Crafter: catalysts, the preview, what a take commits and
 * what it costs, Tesseracts in the grid, and automation. See wiki: machines/quantum-crafter.
 *
 * <p>Crafts are committed the way a hopper commits them, by pulling from the automation handler; the
 * preview is rebuilt on the crafter's own tick, so every check waits a few ticks after filling it.
 * Costs assume the default instant-craft multiplier of 2 and a chunk with no anomaly, which the
 * tests that measure energy set up themselves.
 */
public final class CrafterTests {

    private static final BlockPos CRAFTER = new BlockPos(4, 2, 4);
    /** One vanilla smelt: 200 ticks of burn at 10 FE a tick, times the default multiplier of 2. */
    private static final int SMELT_FE = 4_000;
    /** A crafting-table craft: 100 FE base, times 2. */
    private static final int TABLE_FE = 200;

    private CrafterTests() {}

    // covers: crafter.catalyst
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_catalyst", timeoutTicks = 60)
    public static void catalystChoosesTheRecipes(GameTestHelper helper) {
        QuantumCrafterBlockEntity furnace = crafter(helper, CRAFTER, Items.FURNACE);
        grid(furnace, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.OAK_PLANKS, 8));
        QuantumCrafterBlockEntity table = crafter(helper, CRAFTER.east(2), Items.CRAFTING_TABLE);
        grid(table, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.OAK_PLANKS, 8));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(craftOf(furnace, Items.IRON_INGOT) != null, "a furnace catalyst should smelt raw iron");
            helper.assertTrue(craftOf(furnace, Items.STICK) == null, "a furnace catalyst should not craft sticks");
            helper.assertTrue(craftOf(table, Items.STICK) != null, "a crafting table catalyst should craft sticks");
            helper.assertTrue(craftOf(table, Items.IRON_INGOT) == null, "a crafting table should not smelt");
            helper.succeed();
        });
    }

    // covers: crafter.catalyst_gate
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_gate", timeoutTicks = 60)
    public static void catalystOffTheWhitelistIsDenied(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.DIRT);
        grid(crafter, new ItemStack(Items.RAW_IRON, 8));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(crafter.getStatusCode(), QuantumCrafterBlockEntity.STATUS_DENIED, "status");
            helper.assertTrue(crafter.getPreview().isEmpty(), "a denied catalyst should show no preview");
            helper.succeed();
        });
    }

    // covers: crafter.preview
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_pool", timeoutTicks = 60)
    public static void ingredientsArePooled(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.CRAFTING_TABLE);
        grid(crafter, new ItemStack(Items.IRON_INGOT, 9));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(craftOf(crafter, Items.IRON_BLOCK) != null,
                    "nine ingots in one slot should fill all nine positions of an iron block");
            helper.assertValueEqual(crafter.getStatusCode(), QuantumCrafterBlockEntity.STATUS_READY, "status");
            helper.succeed();
        });
    }

    // covers: crafter.batch
    @GameTest(template = TestSupport.FLOOR_17, batch = "crafter_batch", timeoutTicks = 60)
    public static void batchIsTheSmallestLimit(GameTestHelper helper) {
        TestSupport.clearField(helper);
        // Energy: 100,000 FE buys 25 smelts of the 64 raw iron.
        QuantumCrafterBlockEntity energy = crafter(helper, new BlockPos(2, 2, 8), Items.FURNACE);
        grid(energy, new ItemStack(Items.RAW_IRON, 64));
        energy.getEnergyStorage().setEnergy(100_000);
        // Ingredients: ten raw iron make ten ingots, however much energy there is.
        QuantumCrafterBlockEntity ingredients = crafter(helper, new BlockPos(8, 2, 8), Items.FURNACE);
        grid(ingredients, new ItemStack(Items.RAW_IRON, 10));
        // One stack: 128 raw iron still shows one stack of ingots.
        QuantumCrafterBlockEntity stack = crafter(helper, new BlockPos(14, 2, 8), Items.FURNACE);
        grid(stack, new ItemStack(Items.RAW_IRON, 64), new ItemStack(Items.RAW_IRON, 64));
        helper.runAfterDelay(5, () -> {
            ResolvedCraft capped = craftOf(energy, Items.IRON_INGOT);
            helper.assertTrue(capped != null, "the energy-limited crafter should still preview a smelt");
            helper.assertValueEqual(capped.batchSize(), 25, "runs 100,000 FE buys");
            helper.assertTrue(capped.energyCapped(), "the batch should be marked as limited by energy");
            helper.assertValueEqual(craftOf(ingredients, Items.IRON_INGOT).batchSize(), 10, "runs ten raw iron make");
            helper.assertValueEqual(craftOf(stack, Items.IRON_INGOT).batchSize(), 64, "runs capped at one stack");
            helper.succeed();
        });
    }

    // covers: crafter.cost
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_cost", timeoutTicks = 60)
    public static void craftsCostWhatTheRealProcessWould(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumCrafterBlockEntity furnace = crafter(helper, CRAFTER, Items.FURNACE);
        grid(furnace, new ItemStack(Items.RAW_IRON, 4));
        QuantumCrafterBlockEntity table = crafter(helper, CRAFTER.east(2), Items.CRAFTING_TABLE);
        grid(table, new ItemStack(Items.OAK_PLANKS, 2));
        helper.runAfterDelay(5, () -> {
            ResolvedCraft smelt = craftOf(furnace, Items.IRON_INGOT);
            helper.assertValueEqual(smelt.feCost() / smelt.batchSize(), SMELT_FE, "FE per smelt");
            ResolvedCraft sticks = craftOf(table, Items.STICK);
            helper.assertValueEqual(sticks.feCost() / sticks.batchSize(), TABLE_FE, "FE per crafting-table craft");
            helper.succeed();
        });
    }

    // covers: anomaly.surcharge
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_surcharge", timeoutTicks = 60)
    public static void anomalyRaisesTheCost(GameTestHelper helper) {
        // A neighbour's lingering containment would waive the surcharge.
        TestSupport.clearField(helper);
        // High anomaly: +50%.
        TestSupport.setField(helper, CRAFTER, 0.0, 1_500.0);
        QuantumCrafterBlockEntity furnace = crafter(helper, CRAFTER, Items.FURNACE);
        grid(furnace, new ItemStack(Items.RAW_IRON, 4));
        helper.runAfterDelay(5, () -> {
            ResolvedCraft smelt = craftOf(furnace, Items.IRON_INGOT);
            helper.assertValueEqual(smelt.feCost() / smelt.batchSize(), SMELT_FE * 3 / 2, "FE per smelt at High");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: crafter.collapse
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_collapse", timeoutTicks = 60)
    public static void aPullCommitsOnlyWhatItTakes(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.FURNACE);
        grid(crafter, new ItemStack(Items.RAW_IRON, 64));
        int before = crafter.getEnergyStorage().getEnergyStored();
        helper.runAfterDelay(5, () -> {
            IItemHandler hopper = crafter.getAutomationItemHandler(null);
            int slot = ghostSlot(crafter, Items.IRON_INGOT);
            ItemStack taken = hopper.extractItem(slot, 1, false);
            helper.assertTrue(taken.is(Items.IRON_INGOT) && taken.getCount() == 1, "the pull should hand over one ingot");
            helper.assertValueEqual(crafter.getInventory().getStackInSlot(0).getCount(), 63, "raw iron left");
            helper.assertValueEqual(before - crafter.getEnergyStorage().getEnergyStored(), SMELT_FE, "FE spent");
            helper.succeed();
        });
    }

    // covers: crafter.collapse
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_remainder", timeoutTicks = 60)
    public static void theRestOfARunIsKeptAsRealItems(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.CRAFTING_TABLE);
        grid(crafter, new ItemStack(Items.OAK_PLANKS, 2));
        helper.runAfterDelay(5, () -> {
            ItemStack taken = crafter.getAutomationItemHandler(null)
                    .extractItem(ghostSlot(crafter, Items.STICK), 1, false);
            helper.assertValueEqual(taken.getCount(), 1, "sticks handed over");
            helper.assertValueEqual(realOutputs(crafter, Items.STICK), 3, "sticks kept in the output grid");
            helper.assertTrue(crafter.getInventory().getStackInSlot(0).isEmpty(), "both planks should be used");
            helper.succeed();
        });
    }

    // covers: crafter.preferred
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_lock", timeoutTicks = 60)
    public static void theLockKeepsThePreviewToTheLastRecipe(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.CRAFTING_TABLE);
        grid(crafter, new ItemStack(Items.OAK_PLANKS, 32));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(crafter.getPreview().size() > 1, "planks should offer several crafts");
            crafter.getAutomationItemHandler(null).extractItem(ghostSlot(crafter, Items.STICK), 4, false);
            crafter.setRecipeLocked(true);
        });
        helper.runAfterDelay(10, () -> {
            List<ResolvedCraft> preview = crafter.getPreview();
            helper.assertTrue(!preview.isEmpty(), "the locked recipe should still be offered");
            for (ResolvedCraft craft : preview) {
                helper.assertTrue(craft.primaryOutput().is(Items.STICK),
                        "locked, only the last recipe taken should be shown, found " + craft.primaryOutput());
            }
            helper.succeed();
        });
    }

    // covers: crafter.flux
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_flux", timeoutTicks = 60)
    public static void craftsEmitFluxFromTheirCost(GameTestHelper helper) {
        TestSupport.clearField(helper);
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.FURNACE);
        grid(crafter, new ItemStack(Items.RAW_IRON, 25));
        helper.runAfterDelay(5, () -> {
            crafter.getAutomationItemHandler(null).extractItem(ghostSlot(crafter, Items.IRON_INGOT), 25, false);
            // 25 smelts, 100,000 FE: 100 flux, times the field's gain, a fifth of it to the crafter's chunk
            // (in its pool or already eased into the field).
            double flux = QuantumFlux.chunkCommittedFlux(helper.getLevel(), helper.absolutePos(CRAFTER));
            double expected = 100.0 * FieldModel.EMISSION_GAIN * QuantumFlux.share(0, 0, QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(flux <= expected + 0.01 && flux > expected * 0.9, "flux in the crafter's chunk, got " + flux);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: crafter.tesseract
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_tesseract", timeoutTicks = 80)
    public static void aTesseractAddsItsInventoryToThePool(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 2, 1);
        ChestBlockEntity chest = chest(helper, chestPos, new ItemStack(Items.RAW_IRON, 16));
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.FURNACE);
        grid(crafter, link(helper, chestPos, Blocks.CHEST));
        helper.runAfterDelay(30, () -> {
            ResolvedCraft smelt = craftOf(crafter, Items.IRON_INGOT);
            helper.assertTrue(smelt != null, "the linked chest's raw iron should be offered");
            helper.assertValueEqual(smelt.batchSize(), 16, "runs from the linked chest");
            crafter.getAutomationItemHandler(null).extractItem(ghostSlot(crafter, Items.IRON_INGOT), 1, false);
            helper.assertValueEqual(chest.getItem(0).getCount(), 15, "raw iron left in the chest");
            helper.assertTrue(crafter.getInventory().getStackInSlot(0).is(ModItems.TESSERACT.get()),
                    "the Tesseract stays in the grid");
            helper.succeed();
        });
    }

    // covers: crafter.entangled_loop
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_loop", timeoutTicks = 80)
    public static void crafterLinkedInARingStop(GameTestHelper helper) {
        BlockPos other = CRAFTER.east(2);
        QuantumCrafterBlockEntity a = crafter(helper, CRAFTER, Items.CRAFTING_TABLE);
        QuantumCrafterBlockEntity b = crafter(helper, other, Items.CRAFTING_TABLE);
        grid(a, link(helper, other, ModBlocks.QUANTUM_CRAFTER.get()), new ItemStack(Items.OAK_PLANKS, 8));
        grid(b, link(helper, CRAFTER, ModBlocks.QUANTUM_CRAFTER.get()), new ItemStack(Items.OAK_PLANKS, 8));
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(a.getStatusCode(), QuantumCrafterBlockEntity.STATUS_ENTANGLED_LOOP, "first crafter");
            helper.assertValueEqual(b.getStatusCode(), QuantumCrafterBlockEntity.STATUS_ENTANGLED_LOOP, "second crafter");
            // Breaking the ring frees them.
            b.getInventory().setStackInSlot(0, ItemStack.EMPTY);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(a.getStatusCode() != QuantumCrafterBlockEntity.STATUS_ENTANGLED_LOOP,
                    "with the ring broken the first crafter should work again");
            helper.succeed();
        });
    }

    // covers: crafter.automation, sides.auto
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_auto_out", timeoutTicks = 80)
    public static void autoOutputCommitsAndPushes(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.FURNACE);
        grid(crafter, new ItemStack(Items.RAW_IRON, 8));
        ChestBlockEntity chest = chest(helper, CRAFTER.east(), ItemStack.EMPTY);
        Direction east = Direction.EAST;
        crafter.applySideConfigs(with(crafter.getSideConfigs(), new SideConfig(east, SideMode.BOTH,
                SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, false, true,
                SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false)));
        helper.runAfterDelay(50, () -> {
            int ingots = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(Items.IRON_INGOT)) ingots += chest.getItem(slot).getCount();
            }
            helper.assertTrue(ingots > 0, "auto output should have pushed smelted iron into the chest");
            helper.succeed();
        });
    }

    // covers: sides.modes, sides.masks
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_sides", timeoutTicks = 40)
    public static void faceModesAndSlotCells(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.FURNACE);
        // North: off. West: in, and only to grid slot 2.
        List<SideConfig> configs = with(crafter.getSideConfigs(), new SideConfig(Direction.NORTH, SideMode.DISABLED,
                SideConfig.ALL_ITEM_INPUTS, SideConfig.ALL_ITEM_OUTPUTS, false, false,
                SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false));
        configs = with(configs, new SideConfig(Direction.WEST, SideMode.INPUT, 0b10, SideConfig.ALL_ITEM_OUTPUTS,
                false, false, SideMode.DISABLED, SideConfig.ALL_FLUID_INPUTS, SideConfig.ALL_FLUID_OUTPUTS, false, false));
        crafter.applySideConfigs(configs);
        helper.runAfterDelay(2, () -> {
            BlockPos at = helper.absolutePos(CRAFTER);
            helper.assertTrue(LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.NORTH)) == null,
                    "an Off face should offer nothing");
            IItemHandler west = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.WEST));
            helper.assertTrue(west != null, "an In face should offer the grid");
            helper.assertValueEqual(west.insertItem(0, new ItemStack(Items.RAW_IRON), false).getCount(), 1,
                    "slot 1 is not one of the face's cells, so it should refuse");
            helper.assertTrue(west.insertItem(1, new ItemStack(Items.RAW_IRON), false).isEmpty(),
                    "slot 2 is the face's cell, so it should accept");
            helper.succeed();
        });
    }

    // covers: sides.wrench
    @GameTest(template = TestSupport.FLOOR_9, batch = "crafter_wrench", timeoutTicks = 40)
    public static void theWrenchCyclesAFacesItemMode(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = crafter(helper, CRAFTER, Items.FURNACE);
        ServerPlayer player = TestSupport.player(helper, CRAFTER.west(2));
        SideMode before = crafter.getSideConfigs().get(Direction.SOUTH.get3DDataValue()).itemMode();
        SideConfigWrench.tryCycle(crafter, Direction.SOUTH, false, player);
        SideMode after = crafter.getSideConfigs().get(Direction.SOUTH.get3DDataValue()).itemMode();
        helper.assertValueEqual(after, before.next(), "item mode after one click");
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: basic_crafter.coherence
    @GameTest(template = TestSupport.FLOOR_9, batch = "basic_coherence", timeoutTicks = 200)
    public static void basicCrafterRunsOnCoherence(GameTestHelper helper) {
        QuantumCrafterBlockEntity basic = basic(helper, CRAFTER, Items.FURNACE);
        grid(basic, new ItemStack(Items.RAW_IRON, 64));
        helper.runAfterDelay(5, () -> {
            ResolvedCraft smelt = craftOf(basic, Items.IRON_INGOT);
            helper.assertValueEqual(smelt.batchSize(), 32, "a batch never exceeds the coherence reserve");
            basic.getAutomationItemHandler(null).extractItem(ghostSlot(basic, Items.IRON_INGOT), 32, false);
            helper.assertValueEqual(basic.getCoherenceReserve(), 0, "reserve after 32 runs");
        });
        helper.runAfterDelay(8, () -> helper.assertValueEqual(basic.getStatusCode(),
                QuantumCrafterBlockEntity.STATUS_REPLENISHING, "status with the reserve used up"));
        // A full reserve returns over 100 ticks.
        helper.runAfterDelay(110, () -> {
            helper.assertValueEqual(basic.getCoherenceReserve(), 32, "reserve after 100 ticks");
            helper.succeed();
        });
    }

    // covers: basic_crafter.catalysts
    @GameTest(template = TestSupport.FLOOR_9, batch = "basic_catalysts", timeoutTicks = 60)
    public static void basicCrafterTakesVanillaWorkstationsOnly(GameTestHelper helper) {
        QuantumCrafterBlockEntity furnace = basic(helper, CRAFTER, Items.FURNACE);
        grid(furnace, new ItemStack(Items.RAW_IRON, 4));
        // Any whitelisted catalyst from outside the basic tag; AE2's charger when AE2 is loaded.
        Item advanced = BuiltInRegistries.ITEM.getOptional(Identifier.parse("ae2:charger")).orElse(null);
        QuantumCrafterBlockEntity other = advanced == null ? null : basic(helper, CRAFTER.east(2), advanced);
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(furnace.getStatusCode(), QuantumCrafterBlockEntity.STATUS_READY, "furnace in a basic crafter");
            if (other != null) {
                helper.assertValueEqual(other.getStatusCode(), QuantumCrafterBlockEntity.STATUS_DENIED,
                        "an advanced catalyst in a basic crafter");
            }
            helper.succeed();
        });
    }

    // ---- helpers ----

    public static QuantumCrafterBlockEntity crafter(GameTestHelper helper, BlockPos pos, Item catalyst) {
        return place(helper, pos, ModBlocks.QUANTUM_CRAFTER.get(), catalyst);
    }

    public static QuantumCrafterBlockEntity basic(GameTestHelper helper, BlockPos pos, Item catalyst) {
        return place(helper, pos, ModBlocks.BASIC_QUANTUM_CRAFTER.get(), catalyst);
    }

    private static QuantumCrafterBlockEntity place(GameTestHelper helper, BlockPos pos, Block block, Item catalyst) {
        TestSupport.track(helper);
        helper.setBlock(pos, block);
        QuantumCrafterBlockEntity crafter = helper.getBlockEntity(pos, QuantumCrafterBlockEntity.class);
        crafter.getInventory().setStackInSlot(QuantumCrafterBlockEntity.CATALYST_SLOT, new ItemStack(catalyst));
        TestSupport.fill(crafter.getEnergyStorage());
        return crafter;
    }

    public static void grid(QuantumCrafterBlockEntity crafter, ItemStack... stacks) {
        for (int slot = 0; slot < stacks.length; slot++) crafter.getInventory().setStackInSlot(slot, stacks[slot]);
    }

    /** The preview's craft whose first output is {@code item}, or null. */
    public static ResolvedCraft craftOf(QuantumCrafterBlockEntity crafter, Item item) {
        for (ResolvedCraft craft : crafter.getPreview()) {
            if (craft.primaryOutput().is(item)) return craft;
        }
        return null;
    }

    /** The output slot showing a ghost of {@code item}; fails the test if there is none. */
    public static int ghostSlot(QuantumCrafterBlockEntity crafter, Item item) {
        for (int slot = QuantumCrafterBlockEntity.OUTPUT_START; slot <= QuantumCrafterBlockEntity.OUTPUT_END; slot++) {
            if (crafter.getGhost(slot).is(item)) return slot;
        }
        throw new AssertionError("no ghost of " + item + " in the preview");
    }

    private static int realOutputs(QuantumCrafterBlockEntity crafter, Item item) {
        int count = 0;
        for (int slot = QuantumCrafterBlockEntity.OUTPUT_START; slot <= QuantumCrafterBlockEntity.OUTPUT_END; slot++) {
            ItemStack stack = crafter.getInventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    public static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos, ItemStack contents) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos, ChestBlockEntity.class);
        if (!contents.isEmpty()) chest.setItem(0, contents);
        return chest;
    }

    /** A Tesseract bound to the top of the block at {@code pos}, as sneak-using it there would. */
    public static ItemStack link(GameTestHelper helper, BlockPos pos, Block block) {
        ItemStack link = new ItemStack(ModItems.TESSERACT.get());
        link.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));
        link.set(ModDataComponents.BOUND_SIDE.get(), Direction.UP);
        link.set(ModDataComponents.BOUND_BLOCK.get(), BuiltInRegistries.BLOCK.getKey(block));
        return link;
    }

    public static List<SideConfig> with(List<SideConfig> configs, SideConfig replacement) {
        List<SideConfig> out = new ArrayList<>(configs);
        out.set(replacement.side().get3DDataValue(), replacement);
        return out;
    }
}
