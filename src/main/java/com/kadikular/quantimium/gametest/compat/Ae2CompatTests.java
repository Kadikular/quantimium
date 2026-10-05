package com.kadikular.quantimium.gametest.compat;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.gametest.CrafterTests;
import com.kadikular.quantimium.gametest.FluxTests;
import com.kadikular.quantimium.gametest.BandRewards;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.kadikular.quantimium.compat.ae2.SuperpositionCrafterBlock;
import com.kadikular.quantimium.compat.ae2.SuperpositionCrafterBlockEntity;
import com.kadikular.quantimium.compat.ae2.SuperpositionPattern;
import com.kadikular.quantimium.recipe.FilterEntry;
import com.kadikular.quantimium.flux.FieldModel;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.kadikular.quantimium.gametest.compat.CompatGameTests.block;
import static com.kadikular.quantimium.gametest.compat.CompatGameTests.item;

import java.util.List;

/**
 * Applied Energistics 2. Its Charger and Inscriber recipes reach the Crafter through the data-driven
 * adapters in {@code data/quantimium/recipe_adapters}. Its machines are ticked by AE2's own grid,
 * which the Simulator cannot drive, so that is pinned down too. Registered only with AE2 loaded; see {@link CompatGameTests}.
 */
public final class Ae2CompatTests {

    private static final String NS = Quantimium.MODID;
    private static final BlockPos AT = new BlockPos(4, 2, 4);
    /** Both adapters set a flat 1,600 FE before the instant-craft multiplier. */
    private static final int ADAPTER_FE = 1_600;

    private Ae2CompatTests() {}

    // covers: compat.ae2.inscriber
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_inscriber", timeoutTicks = 60)
    public static void theInscriberKeepsItsPress(GameTestHelper helper) {
        TestSupport.setField(helper, AT, 0.0, 0.0);
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("ae2:inscriber"));
        CrafterTests.grid(crafter, new ItemStack(item("ae2:silicon"), 4), new ItemStack(item("ae2:silicon_press")));
        helper.runAfterDelay(5, () -> {
            Item printed = item("ae2:printed_silicon");
            ResolvedCraft print = CrafterTests.craftOf(crafter, printed);
            helper.assertTrue(print != null, "silicon and a silicon press should print silicon");
            helper.assertValueEqual(print.feCost() / print.batchSize(), ADAPTER_FE * Config.instantCraftMultiplier(),
                    "FE per print");
            crafter.getAutomationItemHandler(null).extractItem(CrafterTests.ghostSlot(crafter, printed), 1, false);
            helper.assertValueEqual(crafter.getInventory().getStackInSlot(0).getCount(), 3, "silicon left");
            helper.assertTrue(crafter.getInventory().getStackInSlot(1).is(item("ae2:silicon_press")),
                    "the press is not an ingredient and should stay in the grid");
            helper.succeed();
        });
    }

    // covers: compat.ae2.charger
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_charger", timeoutTicks = 60)
    public static void theChargerIsPricedByItsAdapter(GameTestHelper helper) {
        TestSupport.setField(helper, AT, 0.0, 0.0);
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("ae2:charger"));
        CrafterTests.grid(crafter, new ItemStack(item("ae2:certus_quartz_crystal"), 4));
        helper.runAfterDelay(5, () -> {
            ResolvedCraft charge = CrafterTests.craftOf(crafter, item("ae2:charged_certus_quartz_crystal"));
            helper.assertTrue(charge != null, "a certus crystal should be offered charged");
            helper.assertValueEqual(charge.feCost() / charge.batchSize(), ADAPTER_FE * Config.instantCraftMultiplier(),
                    "FE per charge");
            helper.succeed();
        });
    }

    // covers: compat.ae2.simulator
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_simulator", timeoutTicks = 600)
    public static void aSimulatedChargerReportsNoTick(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(AT, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(AT, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        helper.setBlock(AT.above(), block("ae2:charger"));
        simulator.toggleEngage(null);
        helper.assertTrue(simulator.isEngaged(), "the field should take the charger");
        simulator.getInventory().setStackInSlot(0, new ItemStack(item("ae2:certus_quartz_crystal"), 8));
        // AE2 runs its machines from its own grid ticker, not from block entity ticks, so there is
        // nothing for the simulator to drive. It should say so rather than sit there looking idle.
        helper.succeedWhen(() -> helper.assertValueEqual(simulator.getStatusCode(),
                PhantomMirrorEngine.STATUS_NO_TICKER, "status with an AE2 charger in the field"));
    }

    // ---- ME Superposition Crafter ----

    // covers: ae2.superposition.patterns
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_patterns", timeoutTicks = 60)
    public static void aFurnaceCatalystOffersItsSmeltingRecipes(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        helper.assertTrue(!crafter.getCatalyst().isItemValid(0, new ItemStack(Items.CRAFTING_TABLE)),
                "a crafting table is left to the Molecular Assembler");
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        helper.runAfterDelay(2, () -> {
            SuperpositionPattern smelt = patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON);
            helper.assertTrue(smelt != null, "a furnace should offer raw iron to iron ingot");
            helper.assertValueEqual(smelt.getInputs().length, 1, "inputs per smelt");
            helper.assertValueEqual(smelt.getInputs()[0].getMultiplier(), 1L, "raw iron per smelt");
            helper.assertTrue(crafter.patterns().size() > 10, "a furnace should offer every smelting recipe");
            helper.succeed();
        });
    }

    // covers: bands.me_crafter
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_band", timeoutTicks = 60)
    public static void belowItsCatalystsBandItOffersNothing(GameTestHelper helper) {
        // The shipped band rewards, with the furnace's tier needing Medium so vanilla will do; alone in
        // its batch, so the heat reaches no one else. Flattened again however it ends.
        TestSupport.clearField(helper);
        BandRewards.on();
        Config.CRAFTER_CATALYST_BANDS.set(List.of("medium", "medium", "high", "critical", "singularity"));
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.bandGate().update(helper.getLevel(), helper.absolutePos(AT));
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        helper.runAfterDelay(2, () -> guarded(helper, () -> {
            helper.assertTrue(crafter.patterns().isEmpty(), "a cold field should hide the furnace's crafts");
            helper.assertValueEqual(crafter.getStatusCode(), SuperpositionCrafterBlockEntity.STATUS_NEEDS_FLUX, "status");
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) TestSupport.setField(helper, AT.offset(dx * 16, 0, dz * 16), 500.0, 0.0);
            }
            crafter.bandGate().update(helper.getLevel(), helper.absolutePos(AT));
            crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        }, false));
        helper.runAfterDelay(4, () -> guarded(helper, () -> {
            helper.assertTrue(patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON) != null, "at Medium it should offer smelting");
            helper.succeed();
        }, true));
    }

    /** Runs a step of a band test; flattens the rewards and clears the field if it fails, or when {@code last}. */
    private static void guarded(GameTestHelper helper, Runnable step, boolean last) {
        try {
            step.run();
        } catch (RuntimeException | Error e) {
            BandRewards.flatten();
            TestSupport.clearField(helper);
            throw e;
        }
        if (last) {
            BandRewards.flatten();
            TestSupport.clearField(helper);
        }
    }

    // covers: ae2.superposition.filter
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_filter", timeoutTicks = 60)
    public static void theFilterAllowsOrDeniesOutputs(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        crafter.setFilterSlot(0, new ItemStack(Items.IRON_INGOT));
        helper.runAfterDelay(2, () -> {
            for (SuperpositionPattern pattern : crafter.patterns()) {
                helper.assertTrue(pattern.shape().primaryOutput().is(Items.IRON_INGOT),
                        "allow list: only iron ingots, found " + pattern.shape().primaryOutput());
            }
            helper.assertTrue(!crafter.patterns().isEmpty(), "iron ingots should still be offered");
            crafter.setFilterMode(SuperpositionCrafterBlockEntity.MODE_DENY);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON) == null, "deny list: no iron ingots");
            helper.assertTrue(crafter.patterns().size() > 10, "deny list: everything else");
            helper.succeed();
        });
    }

    // covers: ae2.superposition.push
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_push", timeoutTicks = 200)
    public static void aRunIsPaidForAndReturnedToTheNetwork(GameTestHelper helper) {
        TestSupport.clearField(helper);
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        // A small network: power, and a drive with a 1k cell for the results to land in.
        helper.setBlock(AT.east(), block("ae2:creative_energy_cell"));
        helper.setBlock(AT.west(), block("ae2:drive"));
        var drive = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(AT.west()), null));
        helper.assertTrue(drive != null, "the drive should take cells");
        helper.assertTrue(drive.insertItem(0, new ItemStack(item("ae2:item_storage_cell_1k")), false).isEmpty(),
                "the drive should take a 1k cell");
        int[] before = {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(crafter.getMainNode().isActive(), "the network is still booting");
            if (before[0] < 0) {
                before[0] = crafter.getEnergyStorage().getEnergyStored();
                SuperpositionPattern smelt = patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON);
                KeyCounter inputs = new KeyCounter();
                inputs.add(AEItemKey.of(Items.RAW_IRON), 1);
                helper.assertTrue(crafter.pushPattern(smelt, new KeyCounter[] {inputs}), "the crafter should take the run");
            }
            // 200 ticks of furnace burn at 10 FE, times 2.
            helper.assertValueEqual(before[0] - crafter.getEnergyStorage().getEnergyStored(), 4_000, "FE for one smelt");
            long ingots = crafter.getMainNode().getGrid().getStorageService().getCachedInventory()
                    .get(AEItemKey.of(Items.IRON_INGOT));
            helper.assertValueEqual(ingots, 1L, "iron ingots in the network");
        });
    }

    // covers: ae2.superposition.autocrafting
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_job", timeoutTicks = 300)
    public static void anAe2CraftingJobRunsThroughIt(GameTestHelper helper) {
        TestSupport.clearField(helper);
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        helper.setBlock(AT.east(), block("ae2:creative_energy_cell"));
        helper.setBlock(AT.west(), block("ae2:drive"));
        // A crafting CPU: one crafting storage block is a whole 1x1x1 cluster.
        helper.setBlock(AT.north(), block("ae2:1k_crafting_storage"));
        var drive = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(AT.west()), null));
        drive.insertItem(0, new ItemStack(item("ae2:item_storage_cell_1k")), false);

        IActionSource source = IActionSource.ofMachine(crafter);
        @SuppressWarnings("unchecked") java.util.concurrent.Future<ICraftingPlan>[] plan = new java.util.concurrent.Future[1];
        boolean[] submitted = {false};
        helper.succeedWhen(() -> {
            var grid = crafter.getMainNode().getGrid();
            helper.assertTrue(crafter.getMainNode().isActive() && grid != null, "the network is still booting");
            var storage = grid.getStorageService();
            var crafting = grid.getCraftingService();
            helper.assertTrue(!crafting.getCpus().isEmpty(), "the crafting CPU has not formed yet");
            if (plan[0] == null) {
                storage.getInventory().insert(AEItemKey.of(Items.RAW_IRON), 4, Actionable.MODULATE, source);
                helper.assertTrue(crafting.isCraftable(AEItemKey.of(Items.IRON_INGOT)),
                        "the network should see iron ingots as craftable");
                plan[0] = crafting.beginCraftingCalculation(helper.getLevel(), () -> source,
                        AEItemKey.of(Items.IRON_INGOT), 4, CalculationStrategy.REPORT_MISSING_ITEMS);
            }
            helper.assertTrue(plan[0].isDone(), "still planning");
            if (!submitted[0]) {
                try {
                    ICraftingPlan done = plan[0].get();
                    helper.assertTrue(done.missingItems().isEmpty(), "the plan should need nothing missing");
                    var result = crafting.submitJob(done, null, null, true, source);
                    helper.assertTrue(result.successful(), "the job should start");
                    submitted[0] = true;
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    throw new AssertionError(e);
                }
            }
            // The job itself must finish, not just the ingots turn up: results that reach the network
            // before AE2 is waiting for them land in storage and leave the job stuck.
            for (var cpu : crafting.getCpus()) {
                helper.assertTrue(!cpu.isBusy(), "the crafting job has not finished");
            }
            long ingots = storage.getCachedInventory().get(AEItemKey.of(Items.IRON_INGOT));
            helper.assertValueEqual(ingots, 4L, "iron ingots crafted");
            helper.assertValueEqual(storage.getCachedInventory().get(AEItemKey.of(Items.RAW_IRON)), 0L, "raw iron left");
        });
    }

    // covers: ae2.superposition.batch
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_batch", timeoutTicks = 300)
    public static void aBatchIsSeveralRunsInOnePattern(GameTestHelper helper) {
        TestSupport.clearField(helper);
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        crafter.setBatch(8);
        helper.setBlock(AT.east(), block("ae2:creative_energy_cell"));
        helper.setBlock(AT.west(), block("ae2:drive"));
        helper.setBlock(AT.north(), block("ae2:1k_crafting_storage"));
        LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(AT.west()), null))
                .insertItem(0, new ItemStack(item("ae2:item_storage_cell_1k")), false);

        IActionSource source = IActionSource.ofMachine(crafter);
        int[] before = {-1};
        @SuppressWarnings("unchecked") java.util.concurrent.Future<ICraftingPlan>[] plan = new java.util.concurrent.Future[1];
        boolean[] submitted = {false};
        helper.succeedWhen(() -> {
            SuperpositionPattern smelt = patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON);
            helper.assertTrue(smelt != null && smelt.batch() == 8, "the smelt pattern should be at 8x");
            helper.assertValueEqual(smelt.getInputs()[0].getMultiplier(), 8L, "raw iron per pattern run");
            helper.assertValueEqual(smelt.getOutputs().getFirst().amount(), 8L, "ingots per pattern run");
            var grid = crafter.getMainNode().getGrid();
            helper.assertTrue(crafter.getMainNode().isActive() && grid != null, "the network is still booting");
            var storage = grid.getStorageService();
            var crafting = grid.getCraftingService();
            helper.assertTrue(!crafting.getCpus().isEmpty(), "the crafting CPU has not formed yet");
            if (plan[0] == null) {
                before[0] = crafter.getEnergyStorage().getEnergyStored();
                storage.getInventory().insert(AEItemKey.of(Items.RAW_IRON), 8, Actionable.MODULATE, source);
                // Four asked for; a batch makes eight, and the other four go to storage.
                plan[0] = crafting.beginCraftingCalculation(helper.getLevel(), () -> source,
                        AEItemKey.of(Items.IRON_INGOT), 4, CalculationStrategy.REPORT_MISSING_ITEMS);
            }
            helper.assertTrue(plan[0].isDone(), "still planning");
            if (!submitted[0]) {
                try {
                    helper.assertTrue(crafting.submitJob(plan[0].get(), null, null, true, source).successful(), "the job should start");
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    throw new AssertionError(e);
                }
                submitted[0] = true;
            }
            for (var cpu : crafting.getCpus()) helper.assertTrue(!cpu.isBusy(), "the crafting job has not finished");
            helper.assertValueEqual(storage.getCachedInventory().get(AEItemKey.of(Items.IRON_INGOT)), 8L, "ingots from one batch");
            helper.assertValueEqual(before[0] - crafter.getEnergyStorage().getEnergyStored(), 8 * 4_000, "FE for eight smelts");
        });
    }

    // covers: ae2.superposition.link, ae2.superposition.ports
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_link", timeoutTicks = 200)
    public static void theCoreShowsTheNetworkLink(GameTestHelper helper) {
        superposition(helper, AT).getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(helper.getBlockState(AT).getValue(SuperpositionCrafterBlock.LINK),
                    SuperpositionCrafterBlock.Link.OFFLINE, "link with no network");
            helper.setBlock(AT.east(), block("ae2:creative_energy_cell"));
        });
        helper.succeedWhen(() -> {
            helper.assertValueEqual(helper.getBlockState(AT).getValue(SuperpositionCrafterBlock.LINK),
                    SuperpositionCrafterBlock.Link.ONLINE, "link on a powered network");
            helper.assertTrue(helper.getBlockState(AT).getValue(SuperpositionCrafterBlock.EAST), "a port plate facing the cell");
            helper.assertTrue(!helper.getBlockState(AT).getValue(SuperpositionCrafterBlock.WEST), "none on the open side");
        });
    }

    // covers: ae2.superposition.input_blacklist
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_inputs", timeoutTicks = 60)
    public static void blacklistedInputsAreNeverConsumed(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        int inputs = SuperpositionCrafterBlockEntity.INPUT_FILTER_START;
        crafter.setFilterSlot(inputs, new ItemStack(Items.IRON_ORE));
        crafter.setFilterSlot(inputs + 1, new ItemStack(Items.OAK_LOG));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(patternFor(crafter, Items.IRON_INGOT, Items.IRON_ORE) == null, "iron ore should never be smelted");
            helper.assertTrue(patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON) != null, "raw iron should still be");
            // Only the listed item leaves an ingredient: charcoal still burns from birch.
            helper.assertTrue(patternFor(crafter, Items.CHARCOAL, Items.OAK_LOG) == null, "oak logs are blacklisted");
            helper.assertTrue(patternFor(crafter, Items.CHARCOAL, Items.BIRCH_LOG) != null, "birch logs are not");
            helper.succeed();
        });
    }

    // covers: ae2.superposition.tag_entries
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_tags", timeoutTicks = 60)
    public static void anEntryCanStandForATag(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        int ore = SuperpositionCrafterBlockEntity.INPUT_FILTER_START;
        crafter.setFilterSlot(ore, new ItemStack(Items.IRON_ORE));
        crafter.cycleFilterTag(ore);
        helper.assertValueEqual(FilterEntry.tagOf(crafter.getFilter().getItem(ore)).map(t -> t.location().toString()).orElse("none"),
                "c:ores", "first tag of iron ore");
        // Outputs: only ingots, by tag.
        crafter.setFilterSlot(0, new ItemStack(Items.IRON_INGOT));
        while (FilterEntry.tagOf(crafter.getFilter().getItem(0)).map(t -> !t.location().toString().equals("c:ingots")).orElse(true)) {
            crafter.cycleFilterTag(0);
        }
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(patternFor(crafter, Items.GOLD_INGOT, Items.GOLD_ORE) == null, "#c:ores should keep gold ore out too");
            helper.assertTrue(patternFor(crafter, Items.GOLD_INGOT, Items.RAW_GOLD) != null, "raw gold still smelts to an ingot");
            for (SuperpositionPattern pattern : crafter.patterns()) {
                helper.assertTrue(pattern.shape().primaryOutput().is(net.minecraft.tags.ItemTags.create(
                        net.minecraft.resources.Identifier.parse("c:ingots"))),
                        "whitelist #c:ingots should offer only ingots, found " + pattern.shape().primaryOutput());
            }
            helper.succeed();
        });
    }

    // covers: ae2.superposition.filter_saved
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_saved", timeoutTicks = 40)
    public static void filterEntriesKeepTheirSlots(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        int slot = SuperpositionCrafterBlockEntity.INPUT_FILTER_START + 3;
        crafter.setFilterSlot(slot, new ItemStack(Items.IRON_ORE));
        crafter.cycleFilterTag(slot);
        var registries = helper.getLevel().registryAccess();
        var saved = crafter.saveWithFullMetadata(registries);
        crafter.getFilter().clearContent();
        crafter.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(crafter.getFilter().getItem(slot).is(Items.IRON_ORE), "the entry should come back in its own slot");
        helper.assertTrue(FilterEntry.tagOf(crafter.getFilter().getItem(slot)).isPresent(), "with its tag");
        helper.assertTrue(crafter.getFilter().getItem(0).isEmpty(), "and not slide into the outputs");
        helper.succeed();
    }

    // covers: ae2.superposition.input_whitelist
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_input_allow", timeoutTicks = 60)
    public static void anInputWhitelistUsesOnlyWhatIsListed(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        crafter.setInputMode(SuperpositionCrafterBlockEntity.MODE_ALLOW);
        crafter.setFilterSlot(SuperpositionCrafterBlockEntity.INPUT_FILTER_START, new ItemStack(Items.RAW_IRON));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON) != null, "raw iron is listed");
            helper.assertTrue(patternFor(crafter, Items.IRON_INGOT, Items.IRON_ORE) == null, "iron ore is not");
            helper.assertTrue(patternFor(crafter, Items.CHARCOAL, Items.OAK_LOG) == null, "nor are logs");
            // Every pattern offered takes raw iron and nothing else (a pack can have more than one).
            for (SuperpositionPattern pattern : crafter.patterns()) {
                for (var input : pattern.getInputs()) {
                    helper.assertTrue(input.isValid(AEItemKey.of(Items.RAW_IRON), null),
                            "only what raw iron alone makes: " + pattern.shape().id());
                }
            }
            helper.succeed();
        });
    }

    // covers: ae2.superposition.flux
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_flux", timeoutTicks = 200)
    public static void runsEmitFluxFromTheirCost(GameTestHelper helper) {
        TestSupport.clearField(helper);
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        crafter.getCatalyst().setStackInSlot(0, new ItemStack(Items.FURNACE));
        crafter.setBatch(64);
        helper.setBlock(AT.east(), block("ae2:creative_energy_cell"));
        long[] pushedAt = {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(crafter.getMainNode().isActive(), "the network is still booting");
            if (pushedAt[0] < 0) {
                TestSupport.clearField(helper);
                KeyCounter inputs = new KeyCounter();
                inputs.add(AEItemKey.of(Items.RAW_IRON), 64);
                helper.assertTrue(crafter.pushPattern(patternFor(crafter, Items.IRON_INGOT, Items.RAW_IRON),
                        new KeyCounter[] {inputs}), "the crafter should take the run");
                pushedAt[0] = helper.getTick();
            }
            helper.assertTrue(helper.getTick() > pushedAt[0] + 21, "flux is emitted once a second");
            // 64 smelts at 4,000 FE: 256,000 FE, 256 flux, times the field's gain, a fifth of it to the
            // crafter's chunk (in its pool or already eased into the field).
            double flux = QuantumFlux.chunkCommittedFlux(helper.getLevel(), helper.absolutePos(AT));
            double expected = 256.0 * FieldModel.EMISSION_GAIN * QuantumFlux.share(0, 0, QuantumFlux.SOURCE_RADIUS);
            helper.assertTrue(flux <= expected + 0.2 && flux > expected * 0.9, "flux in the crafter's chunk, got " + flux);
            TestSupport.clearField(helper);
        });
    }

    // covers: flux_meter.readouts
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_superposition_meter", timeoutTicks = 20)
    public static void theMeterReadsTheSuperpositionCrafter(GameTestHelper helper) {
        SuperpositionCrafterBlockEntity crafter = superposition(helper, AT);
        helper.assertValueEqual(FluxTests.meterKey(crafter), "item.quantimium.flux_meter.machine", "Superposition Crafter");
        helper.succeed();
    }

    // covers: compat.ae2.cables_through_crystals
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "ae2_crystal_cables", timeoutTicks = 20)
    public static void cablesArePlacedThroughCrystals(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.ANOMALITE_CRYSTAL.get());
        ServerPlayer player = TestSupport.player(helper, new BlockPos(4, 2, 1));
        helper.assertTrue(PartHelper.canPlacePartHost(player, helper.getLevel(), helper.absolutePos(at)),
                "AE2 should see a crystal as room for a cable");
        helper.assertTrue(!helper.getBlockState(at).canBeReplaced(Fluids.WATER), "water should not wash a crystal away");
        TestSupport.removePlayer(helper, player);
        helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
        helper.succeed();
    }

    // covers: reactor.materialiser_port.ae2
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_reactor_storage_bus", timeoutTicks = 300)
    public static void aStorageBusSeesEverythingTheReactorCanMake(GameTestHelper helper) {
        // A Reactor with a crafting table and a furnace, holding a log and three raw iron; a storage bus
        // on its Materialiser Port, on a network powered by a creative cell.
        BlockPos core = com.kadikular.quantimium.gametest.ReactorTests.CORE;
        var horizon = com.kadikular.quantimium.gametest.ReactorTests.buildReactor(helper, 1);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.north(2), Items.CRAFTING_TABLE);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.south(2), Items.FURNACE);
        BlockPos portPos = core.below().east(5);
        helper.setBlock(portPos, ModBlocks.REACTOR_MATERIALISER_PORT.get());
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.OAK_LOG), 1);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.RAW_IRON), 3);
        horizon.recountNow();

        BlockPos busPos = portPos.east();
        var level = helper.getLevel();
        var busItem = (appeng.api.parts.IPartItem<?>) item("ae2:storage_bus");
        var cableItem = (appeng.api.parts.IPartItem<?>) item("ae2:fluix_glass_cable");
        PartHelper.setPart(level, helper.absolutePos(busPos), null, null, cableItem);
        var bus = PartHelper.setPart(level, helper.absolutePos(busPos), net.minecraft.core.Direction.WEST, null, busItem);
        helper.setBlock(busPos.east(), block("ae2:creative_energy_cell"));
        helper.assertTrue(bus != null, "the storage bus was placed");

        IActionSource source = IActionSource.empty();
        boolean[] taken = {false};
        helper.succeedWhen(() -> {
            var node = bus.getGridNode();
            helper.assertTrue(node != null && node.isActive(), "the network is still booting");
            var grid = node.getGrid();
            var storage = grid.getStorageService();
            long pickaxes = storage.getCachedInventory().get(AEItemKey.of(Items.IRON_PICKAXE));
            if (!taken[0]) {
                helper.assertValueEqual(pickaxes, 1L, "a pickaxe the network can see");
                helper.assertTrue(storage.getCachedInventory().get(AEItemKey.of(Items.STICK)) >= 8, "sticks too");
                long got = storage.getInventory().extract(AEItemKey.of(Items.IRON_PICKAXE), 1, Actionable.MODULATE, source);
                helper.assertValueEqual(got, 1L, "taken through ME");
                taken[0] = true;
            }
            helper.assertValueEqual(horizon.getLedger().count(
                    net.neoforged.neoforge.transfer.item.ItemResource.of(Items.RAW_IRON)), 0L, "the raw iron went into it");
        });
    }

    // covers: reactor.planning.tools
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_reactor_inscriber", timeoutTicks = 40)
    public static void aReactorPrintsCircuitsAndKeepsThePress(GameTestHelper helper) {
        BlockPos core = com.kadikular.quantimium.gametest.ReactorTests.CORE;
        var horizon = com.kadikular.quantimium.gametest.ReactorTests.buildReactor(helper, 1);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.north(2), item("ae2:inscriber"));
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        var press = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:engineering_processor_press"));
        var circuit = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:printed_engineering_processor"));
        horizon.take(press, 1);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIAMOND), 3);
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(horizon.recountNow().count(circuit), 3L, "three circuits on the list");
            var made = horizon.request(circuit, 3);
            helper.assertTrue(made.planned(), "three printed: " + made.problem());
            helper.assertValueEqual(horizon.getLedger().count(press), 1L, "the press is still there");
            helper.assertValueEqual(horizon.getLedger().count(circuit), 3L, "three circuits, kept in the horizon");
            helper.succeed();
        });
    }

    // covers: reactor.planning.wear
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_reactor_knife", timeoutTicks = 40)
    public static void aCuttingKnifeWearsInsteadOfBeingUsedUp(GameTestHelper helper) {
        BlockPos core = com.kadikular.quantimium.gametest.ReactorTests.CORE;
        var horizon = com.kadikular.quantimium.gametest.ReactorTests.buildReactor(helper, 1);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.north(2), Items.CRAFTING_TABLE);
        helper.setBlock(core.below().south(5), ModBlocks.REACTOR_OUTPUT_PORT.get());
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        var knife = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:certus_quartz_cutting_knife"));
        var quartz = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:certus_quartz_crystal"));
        horizon.take(knife, 1);
        horizon.take(quartz, 40);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.IRON_INGOT), 128);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STICK), 64);
        var anchor = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:cable_anchor"));
        helper.runAfterDelay(2, () -> {
            var made = horizon.request(anchor, 30);
            helper.assertTrue(made.planned(), "anchors: " + made.problem());
            helper.assertValueEqual(horizon.getLedger().count(quartz), 40L, "no knives made from the quartz");
            helper.assertValueEqual(horizon.getLedger().count(knife), 0L, "the fresh knife isn't there as new");
            long worn = 0;
            for (var entry : horizon.getLedger().view().entrySet()) {
                if (entry.getKey().is(item("ae2:certus_quartz_cutting_knife"))) {
                    worn += entry.getValue();
                    helper.assertTrue(entry.getKey().toStack(1).getDamageValue() > 0, "it came back worn");
                }
            }
            helper.assertValueEqual(worn, 1L, "one knife, worn");
            // 8 crafts wore it; 50 more wear it out and need exactly one new knife, for 8 of its 50.
            var more = horizon.request(anchor, 200);
            helper.assertTrue(more.planned(), "more anchors: " + more.problem());
            long quartzLeft = horizon.getLedger().count(quartz);
            helper.assertTrue(quartzLeft < 40 && quartzLeft >= 37, "one new knife's quartz, not more: " + quartzLeft);
            long knives = 0;
            for (var entry : horizon.getLedger().view().entrySet()) {
                if (entry.getKey().is(item("ae2:certus_quartz_cutting_knife"))) {
                    knives += entry.getValue();
                    helper.assertValueEqual(entry.getKey().toStack(1).getDamageValue(), 8, "the new knife, 8 crafts in");
                }
            }
            helper.assertValueEqual(knives, 1L, "the old knife broke, the new one is kept");
            helper.succeed();
        });
    }

    // covers: reactor.counts.shared
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_reactor_charged", timeoutTicks = 40)
    public static void chargedQuartzIsTheSameQuartz(GameTestHelper helper) {
        BlockPos core = com.kadikular.quantimium.gametest.ReactorTests.CORE;
        var horizon = com.kadikular.quantimium.gametest.ReactorTests.buildReactor(helper, 1);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.north(2), Items.CRAFTING_TABLE);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.south(2), item("ae2:charger"));
        helper.setBlock(core.below().south(5), ModBlocks.REACTOR_OUTPUT_PORT.get());
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        var quartz = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:certus_quartz_crystal"));
        var knife = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:certus_quartz_cutting_knife"));
        var shovel = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:certus_quartz_shovel"));
        var anchor = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:cable_anchor"));
        horizon.take(quartz, 1);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.IRON_INGOT), 64);
        horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STICK), 64);
        helper.runAfterDelay(2, () -> {
            // Quartz a charger could charge is the same quartz, not more of it.
            var counts = horizon.recountNow();
            helper.assertValueEqual(counts.count(shovel), 1L, "one quartz, one shovel");
            helper.assertValueEqual(counts.count(knife), 0L, "a knife takes two");
            horizon.take(quartz, 1);
            counts = horizon.recountNow();
            helper.assertValueEqual(counts.count(shovel), 2L, "two quartz, two shovels");
            helper.assertValueEqual(counts.count(knife), 1L, "two quartz, one knife");
            // One craft of anchors wears one knife of many, and takes no other.
            horizon.take(knife, 187);
            var made = horizon.request(anchor, 4);
            helper.assertTrue(made.planned(), "anchors: " + made.problem());
            helper.assertValueEqual(horizon.getLedger().count(knife), 186L, "186 knives as new");
            helper.assertValueEqual(horizon.getLedger().count(quartz), 2L, "no knife made");
            helper.assertValueEqual(horizon.recountNow().count(knife), 187L, "186 on hand and one makeable");
            helper.succeed();
        });
    }

    // covers: reactor.planning.exact
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_reactor_components", timeoutTicks = 40)
    public static void storageComponentsCountAndMakeExactly(GameTestHelper helper) {
        BlockPos core = com.kadikular.quantimium.gametest.ReactorTests.CORE;
        var horizon = com.kadikular.quantimium.gametest.ReactorTests.buildReactor(helper, 1);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.north(2), Items.CRAFTING_TABLE);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.south(2), item("ae2:inscriber"));
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.offset(2, 0, 1), Items.FURNACE);
        com.kadikular.quantimium.gametest.ReactorTests.bay(helper, core.offset(-2, 0, 1), item("ae2:charger"));
        // What's made goes out, so it isn't counted again as stock.
        helper.setBlock(core.below().south(5), ModBlocks.REACTOR_OUTPUT_PORT.get());
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity.ENERGY_CAPACITY);
        java.util.Map<String, Integer> stock = new java.util.LinkedHashMap<>();
        stock.put("ae2:certus_quartz_crystal", 200);
        stock.put("ae2:charged_certus_quartz_crystal", 40);
        stock.put("ae2:fluix_crystal", 60);
        stock.put("minecraft:redstone", 300);
        stock.put("minecraft:gold_ingot", 60);
        stock.put("minecraft:iron_ingot", 60);
        stock.put("minecraft:diamond", 30);
        stock.put("ae2:silicon", 100);
        stock.put("minecraft:glass", 60);
        stock.put("minecraft:quartz", 60);
        stock.put("minecraft:glowstone_dust", 100);
        stock.put("ae2:logic_processor_press", 1);
        stock.put("ae2:calculation_processor_press", 1);
        stock.put("ae2:engineering_processor_press", 1);
        stock.put("ae2:silicon_press", 1);
        stock.forEach((id, n) -> horizon.take(net.neoforged.neoforge.transfer.item.ItemResource.of(item(id)), n));
        var component = net.neoforged.neoforge.transfer.item.ItemResource.of(item("ae2:cell_component_16k"));
        helper.runAfterDelay(2, () -> {
            long estimate = horizon.recountNow().count(component);
            // The truth: the most the planner will make, by trying.
            long most = 0;
            for (long n = 1; n <= estimate + 2; n++) {
                if (horizon.plan(horizon.getLedger().snapshot(), component, n).planned()) most = n;
                else break;
            }
            var made = horizon.request(component, 1);
            helper.assertTrue(made.planned(), "one 16k component: " + made.problem());
            long after = horizon.recountNow().count(component);
            long mostAfter = 0;
            for (long n = 1; n <= after + 2; n++) {
                if (horizon.plan(horizon.getLedger().snapshot(), component, n).planned()) mostAfter = n;
                else break;
            }
            Quantimium.LOGGER.info("16k components: estimated {}, really {}; after making one, estimated {}, really {}; steps {}",
                    estimate, most, after, mostAfter, made.plan().steps().size());
            helper.assertValueEqual(mostAfter, most - 1, "making one costs exactly one");
            // Charged quartz is held and made from the plain quartz held: the list mustn't count that twice.
            helper.assertValueEqual(estimate, most, "the list says what can really be made");
            helper.assertValueEqual(after, mostAfter, "and still does after making one");
            helper.succeed();
        });
    }

    private static SuperpositionCrafterBlockEntity superposition(GameTestHelper helper, BlockPos pos) {
        TestSupport.track(helper);
        helper.setBlock(pos, block("quantimium:me_superposition_crafter"));
        SuperpositionCrafterBlockEntity crafter = helper.getBlockEntity(pos, SuperpositionCrafterBlockEntity.class);
        TestSupport.fill(crafter.getEnergyStorage());
        return crafter;
    }

    /** The offered pattern that turns {@code input} into {@code output}, or null. */
    private static SuperpositionPattern patternFor(SuperpositionCrafterBlockEntity crafter, Item output, Item input) {
        for (SuperpositionPattern pattern : crafter.patterns()) {
            if (!pattern.shape().primaryOutput().is(output)) continue;
            for (var in : pattern.getInputs()) {
                if (in.isValid(AEItemKey.of(input), null)) return pattern;
            }
        }
        return null;
    }
}
