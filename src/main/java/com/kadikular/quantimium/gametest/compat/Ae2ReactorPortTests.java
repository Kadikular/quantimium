package com.kadikular.quantimium.gametest.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.compat.ae2.Ae2Content;
import com.kadikular.quantimium.compat.ae2.ReactorMePortBlockEntity;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.gametest.ReactorTests;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static com.kadikular.quantimium.gametest.compat.CompatGameTests.block;
import static com.kadikular.quantimium.gametest.compat.CompatGameTests.item;

/**
 * The ME Superposition Port: a Reactor on an ME network, both ways, and a storage bus on its own
 * Materialiser Port that must not make it count or take anything twice.
 *
 * <p>Every test builds one Reactor with a crafting table in a bay and the port on the rim at
 * {@link #PORT}, with a creative energy cell beside it and a drive with a 1k cell beside that.
 */
public final class Ae2ReactorPortTests {

    private static final String NS = Quantimium.MODID;
    private static final BlockPos CORE = ReactorTests.CORE;
    private static final BlockPos PORT = CORE.below().east(5);
    private static final BlockPos CELL = PORT.east();
    private static final BlockPos DRIVE = CELL.east();
    private static final ItemResource LOG = ItemResource.of(Items.OAK_LOG);
    private static final AEItemKey LOG_KEY = AEItemKey.of(Items.OAK_LOG);
    private static final AEItemKey PLANKS_KEY = AEItemKey.of(Items.OAK_PLANKS);

    private Ae2ReactorPortTests() {}

    private static HorizonCoreBlockEntity reactorOnANetwork(GameTestHelper helper) {
        HorizonCoreBlockEntity horizon = ReactorTests.buildReactor(helper, 1);
        ReactorTests.bay(helper, CORE.below().north(2), Items.CRAFTING_TABLE);
        helper.setBlock(PORT, Ae2Content.REACTOR_ME_PORT.get());
        helper.setBlock(CELL, block("ae2:creative_energy_cell"));
        helper.setBlock(DRIVE, block("ae2:drive"));
        var drive = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(DRIVE), null));
        drive.insertItem(0, new ItemStack(item("ae2:item_storage_cell_1k")), false);
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        return horizon;
    }

    private static IGrid grid(GameTestHelper helper) {
        ReactorMePortBlockEntity port = helper.getBlockEntity(PORT, ReactorMePortBlockEntity.class);
        IGrid grid = port.getGridNode(Direction.UP) == null ? null : port.getGridNode(Direction.UP).getGrid();
        helper.assertTrue(grid != null && port.getGridNode(Direction.UP).isActive(), "the network is still booting");
        return grid;
    }

    // covers: reactor.me_port.storage
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_storage", timeoutTicks = 300)
    public static void whatTheReactorHoldsIsStorageOnTheNetwork(GameTestHelper helper) {
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        horizon.take(LOG, 3);
        horizon.recountNow();
        boolean[] taken = {false};
        helper.succeedWhen(() -> {
            var storage = grid(helper).getStorageService();
            if (!taken[0]) {
                helper.assertValueEqual(storage.getCachedInventory().get(LOG_KEY), 3L, "three logs on the network");
                helper.assertValueEqual(storage.getCachedInventory().get(PLANKS_KEY), 0L, "what they make isn't stock");
                helper.assertTrue(grid(helper).getCraftingService().isCraftable(PLANKS_KEY), "it's a pattern");
                long got = storage.getInventory().extract(LOG_KEY, 2, Actionable.MODULATE, IActionSource.empty());
                helper.assertValueEqual(got, 2L, "two taken through ME");
                taken[0] = true;
            }
            helper.assertValueEqual(horizon.getLedger().count(LOG), 1L, "one left in the horizon");
        });
    }

    // covers: reactor.me_port.inputs
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_inputs", timeoutTicks = 300)
    public static void theReactorMakesThingsFromTheNetworksStock(GameTestHelper helper) {
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        helper.setBlock(CORE.below().south(5), ModBlocks.REACTOR_OUTPUT_PORT.get());
        horizon.revalidate(helper.getLevel());
        boolean[] stocked = {false};
        helper.succeedWhen(() -> {
            var storage = grid(helper).getStorageService();
            if (!stocked[0]) {
                storage.getInventory().insert(LOG_KEY, 1, Actionable.MODULATE, IActionSource.empty());
                stocked[0] = true;
            }
            // Nothing held: the planks come of the network's log.
            helper.assertValueEqual(horizon.recountNow().count(ItemResource.of(Items.OAK_PLANKS)), 4L, "four planks counted");
            var made = horizon.request(ItemResource.of(Items.OAK_PLANKS), 4);
            helper.assertTrue(made.planned(), "four planks: " + made.problem());
            helper.assertValueEqual(storage.getInventory().getAvailableStacks().get(LOG_KEY), 0L, "the network's log used");
            helper.assertValueEqual(horizon.getLedger().count(LOG), 0L, "and not kept");
        });
    }

    // covers: reactor.me_port.inputs
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_matter", timeoutTicks = 300)
    public static void matterInTheNetworkIsObservedLikeMatterHeld(GameTestHelper helper) {
        // An empty Reactor, and Unrealised Matter in the network: it shows what the Matter could become.
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        boolean[] stocked = {false};
        Object[] plan = new Object[2];
        helper.succeedWhen(() -> {
            var storage = grid(helper).getStorageService();
            if (!stocked[0]) {
                storage.getInventory().insert(AEItemKey.of(com.kadikular.quantimium.init.ModItems.UNREALISED_MATTER.get()), 4,
                        Actionable.MODULATE, IActionSource.empty());
                stocked[0] = true;
            }
            horizon.revalidate(helper.getLevel());
            helper.assertTrue(horizon.recountNow().count(ItemResource.of(Items.RAW_IRON)) > 0, "raw iron on the list, from the network's Matter");
            helper.assertTrue(horizon.getLedger().isEmpty(), "nothing held");
            helper.assertTrue(planOnly(helper, plan, AEItemKey.of(Items.RAW_IRON), 4).missingItems().isEmpty(),
                    "and AE2 can craft four raw iron of four Matter");
        });
    }

    /**
     * Plans {@code amount} of {@code key} on the network, and once planned, runs it if nothing is
     * missing. Checks {@code done} once the job has finished; true then.
     */
    private static boolean craft(GameTestHelper helper, Object[] state, AEItemKey key, long amount, Runnable done) {
        IGrid grid = grid(helper);
        var crafting = grid.getCraftingService();
        // Requested as a machine on the network: AE2 finds patterns through the requester's own node.
        IActionSource source = IActionSource.ofMachine(helper.getBlockEntity(PORT, ReactorMePortBlockEntity.class));
        helper.assertTrue(!crafting.getCpus().isEmpty(), "the crafting CPU has not formed yet");
        helper.assertTrue(crafting.isCraftable(key), "the pattern isn't on the network yet");
        if (state[0] == null) {
            state[0] = crafting.beginCraftingCalculation(helper.getLevel(), () -> source, key, amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        }
        @SuppressWarnings("unchecked") var plan = (java.util.concurrent.Future<ICraftingPlan>) state[0];
        helper.assertTrue(plan.isDone(), "still planning");
        if (state[1] == null) {
            try {
                ICraftingPlan done1 = plan.get();
                helper.assertTrue(done1.missingItems().isEmpty(), "nothing missing: " + missing(done1));
                helper.assertTrue(crafting.submitJob(done1, null, null, true, source).successful(), "the job starts");
                state[1] = true;
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                throw new AssertionError(e);
            }
        }
        for (var cpu : crafting.getCpus()) helper.assertTrue(!cpu.isBusy(), "the job has not finished");
        done.run();
        return true;
    }

    private static String missing(ICraftingPlan plan) {
        StringBuilder out = new StringBuilder();
        for (var entry : plan.missingItems()) out.append(entry.getKey()).append(" x").append(entry.getLongValue()).append(' ');
        out.append("| patterns ").append(plan.patternTimes().size()).append(" used ");
        for (var entry : plan.usedItems()) out.append(entry.getKey()).append(" x").append(entry.getLongValue()).append(' ');
        out.append("| simulation ").append(plan.simulation()).append(" bytes ").append(plan.bytes());
        return out.toString();
    }

    private static ICraftingPlan planOnly(GameTestHelper helper, Object[] state, AEItemKey key, long amount) {
        IGrid grid = grid(helper);
        helper.assertTrue(grid.getCraftingService().isCraftable(key), "the pattern isn't on the network yet");
        if (state[0] == null) {
            IActionSource source = IActionSource.ofMachine(helper.getBlockEntity(PORT, ReactorMePortBlockEntity.class));
            state[0] = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(), () -> source, key, amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        }
        @SuppressWarnings("unchecked") var plan = (java.util.concurrent.Future<ICraftingPlan>) state[0];
        helper.assertTrue(plan.isDone(), "still planning");
        try {
            return plan.get();
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    // covers: reactor.me_port.patterns
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_exact", timeoutTicks = 300)
    public static void ae2KnowsExactlyHowManyItCanMake(GameTestHelper helper) {
        // One log held: four planks can be crafted, and eight are a log short.
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        horizon.take(LOG, 1);
        // Counted here and now: the counting thread may be busy with other Reactors' tests.
        horizon.recountNow();
        Object[] four = new Object[2];
        Object[] eight = new Object[2];
        helper.succeedWhen(() -> {
            ICraftingPlan fourPlan = planOnly(helper, four, PLANKS_KEY, 4);
            helper.assertTrue(fourPlan.missingItems().isEmpty(), "four planks need nothing more: " + missing(fourPlan));
            helper.assertTrue(!planOnly(helper, eight, PLANKS_KEY, 8).missingItems().isEmpty(), "eight are a log short");
        });
    }

    // covers: reactor.me_port.patterns
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_job", timeoutTicks = 400)
    public static void anAe2CraftingJobRunsTheReactorsRecipes(GameTestHelper helper) {
        // A log held and a log in the drive: a job for eight planks uses both, paid for by the Reactor.
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        helper.setBlock(CELL.north(), block("ae2:1k_crafting_storage"));
        horizon.take(LOG, 1);
        horizon.recountNow();
        boolean[] stocked = {false};
        Object[] job = new Object[2];
        helper.succeedWhen(() -> {
            var storage = grid(helper).getStorageService();
            if (!stocked[0]) {
                storage.getInventory().insert(LOG_KEY, 1, Actionable.MODULATE, IActionSource.empty());
                stocked[0] = true;
            }
            craft(helper, job, PLANKS_KEY, 8, () -> {
                helper.assertValueEqual(storage.getCachedInventory().get(PLANKS_KEY), 8L, "eight planks on the network");
                helper.assertValueEqual(storage.getCachedInventory().get(LOG_KEY), 0L, "both logs used");
                helper.assertTrue(horizon.getEnergyStorage().getEnergyStored() < HorizonCoreBlockEntity.ENERGY_CAPACITY,
                        "paid for by the Reactor");
            });
        });
    }

    // covers: reactor.me_port.modes
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_trees", timeoutTicks = 300)
    public static void aWholeTreeIsOnePatternAndStepsAreSeveral(GameTestHelper helper) {
        // 31 iron ingots held: an anvil is three iron blocks and four ingots. As a whole tree it's one
        // pattern; step by step, the blocks and the anvil are two.
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        horizon.take(ItemResource.of(Items.IRON_INGOT), 31);
        horizon.recountNow();
        AEItemKey anvil = AEItemKey.of(Items.ANVIL);
        Object[] trees = new Object[2];
        Object[] steps = new Object[2];
        helper.succeedWhen(() -> {
            ReactorMePortBlockEntity port = helper.getBlockEntity(PORT, ReactorMePortBlockEntity.class);
            if (steps[0] == null) {
                ICraftingPlan whole = planOnly(helper, trees, anvil, 1);
                helper.assertTrue(whole.missingItems().isEmpty(), "an anvil of 31 ingots: " + missing(whole));
                helper.assertValueEqual(whole.patternTimes().size(), 1, "one pattern, the whole tree");
                if (port.mode() == ReactorMePortBlockEntity.Mode.TREES) port.cycleMode(false);
            }
            helper.assertValueEqual(port.mode(), ReactorMePortBlockEntity.Mode.STEPS, "now step by step");
            helper.assertTrue(port.getAvailablePatterns().stream().noneMatch(p -> p instanceof com.kadikular.quantimium.compat.ae2.ReactorTreePattern),
                    "the trees are gone");
            ICraftingPlan stepwise = planOnly(helper, steps, anvil, 1);
            helper.assertTrue(stepwise.missingItems().isEmpty(), "still an anvil: " + missing(stepwise));
            helper.assertValueEqual(stepwise.patternTimes().size(), 2, "blocks, then the anvil");
        });
    }

    // covers: reactor.me_port.loops
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "ae2_me_port_loop", timeoutTicks = 300)
    public static void aStorageBusOnItsOwnMaterialiserPortCountsNothingTwice(GameTestHelper helper) {
        // The ME port, and on the same network a storage bus on one of the Reactor's Materialiser Ports.
        HorizonCoreBlockEntity horizon = reactorOnANetwork(helper);
        BlockPos materialiser = PORT.south();
        helper.setBlock(materialiser, ModBlocks.REACTOR_MATERIALISER_PORT.get());
        BlockPos busPos = CELL.south();
        var busItem = (appeng.api.parts.IPartItem<?>) item("ae2:storage_bus");
        var cableItem = (appeng.api.parts.IPartItem<?>) item("ae2:fluix_glass_cable");
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(busPos), null, null, cableItem);
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(busPos), Direction.WEST, null, busItem) != null,
                "the storage bus was placed");
        horizon.take(LOG, 1);
        int[] checks = {0};
        boolean[] made = {false};
        helper.succeedWhen(() -> {
            var storage = grid(helper).getStorageService();
            if (!made[0]) {
                horizon.revalidate(helper.getLevel());
                helper.assertTrue(horizon.isDarkPort(helper.absolutePos(materialiser)), "the bused Materialiser Port goes dark");
                var line = helper.getBlockEntity(materialiser, com.kadikular.quantimium.block.entity.ReactorPortBlockEntity.class)
                        .fluxMeterLine();
                helper.assertTrue(line != null && line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("message.quantimium.reactor_port.dark"), "and says so");
                helper.assertValueEqual(storage.getInventory().getAvailableStacks().get(LOG_KEY), 1L, "the log, seen once");
                // Recounted again and again, it never grows on itself.
                helper.assertValueEqual(horizon.recountNow().count(ItemResource.of(Items.OAK_PLANKS)), 4L, "four planks, not eight");
                helper.assertTrue(++checks[0] >= 5, "counting again");
                var result = horizon.request(ItemResource.of(Items.OAK_PLANKS), 4);
                helper.assertTrue(result.planned(), "four planks: " + result.problem());
                made[0] = true;
            }
            // One log became four planks, kept in the horizon; nothing more came from anywhere.
            helper.assertValueEqual(horizon.getLedger().count(LOG), 0L, "the log used");
            helper.assertValueEqual(horizon.getLedger().count(ItemResource.of(Items.OAK_PLANKS)), 4L, "four planks held");
            helper.assertValueEqual(horizon.getLedger().mass(), 4L, "and nothing else");
            helper.assertValueEqual(storage.getInventory().getAvailableStacks().get(PLANKS_KEY), 4L, "seen once on the network");
        });
    }
}
