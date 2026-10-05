package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.entity.CatalystBayBlockEntity;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.block.entity.ReactorPortBlockEntity;
import com.kadikular.quantimium.reactor.ReactorPlanner;
import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.fold.FoundryFoldAdapter;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.reactor.ReactorStructure;
import com.kadikular.quantimium.Quantimium;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Quantimium Reactor: its footprint, its rings and capacity, and taking items into the horizon.
 *
 * <p>Every test builds the same reactor: the Horizon Core at {@link #CORE}, a disc of plinth under it,
 * one emitter pair east and west, and an Input port on the rim to the north.
 */
public final class ReactorTests {

    public static final BlockPos CORE = new BlockPos(8, 3, 8);
    static final BlockPos INPUT = CORE.below().north(5);
    static final BlockPos OUTPUT = CORE.below().south(5);

    private ReactorTests() {}

    // covers: reactor.structure
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 40)
    public static void aPlinthAndOnePairOfEmittersMakeAReactor(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(core.isFormed(), "formed: " + core.getLayout().problem());
        helper.assertValueEqual(core.rings(), 1, "rings");
        helper.assertValueEqual(core.getLayout().ports().size(), 1, "the Input port");

        // A second and a third pair each add a ring, and each ring quadruples what it holds.
        helper.setBlock(CORE.north(4), ModBlocks.RING_EMITTER.get());
        helper.setBlock(CORE.south(4), ModBlocks.RING_EMITTER.get());
        core.revalidate(level);
        helper.assertValueEqual(core.rings(), 2, "two pairs");
        helper.assertValueEqual(core.capacity(), 4 * HorizonCoreBlockEntity.BASE_CAPACITY, "capacity with two rings");

        // A gap in the plinth, and it isn't a reactor.
        helper.setBlock(CORE.below().offset(2, 0, 2), Blocks.STONE);
        core.revalidate(level);
        helper.assertFalse(core.isFormed(), "a hole in the plinth");
        helper.succeed();
    }

    // covers: reactor.input
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 40)
    public static void anInputPortFeedsTheHorizonOnlyWhenPowered(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        ResourceHandler<ItemResource> input = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(INPUT), null);
        helper.assertTrue(input != null, "the Input port takes items");

        // Unpowered, the rings are down and it takes nothing.
        helper.assertValueEqual(insert(input, Items.IRON_INGOT, 10, true), 0, "taken unpowered");

        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(2, () -> {
            // A simulated insert (rolled back) adds nothing; a committed one does.
            helper.assertValueEqual(insert(input, Items.IRON_INGOT, 10, false), 10, "offered");
            helper.assertValueEqual(core.getLedger().mass(), 0L, "nothing from a simulation");
            helper.assertValueEqual(insert(input, Items.IRON_INGOT, 10, true), 10, "taken");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.IRON_INGOT)), 10L, "in the ledger");
            helper.succeed();
        });
    }

    // covers: reactor.planning
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 40)
    public static void itMakesAPickaxeThroughTwoMachines(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        bay(helper, CORE.east(2), Items.CRAFTING_TABLE);
        bay(helper, CORE.west(2), Items.FURNACE);
        helper.setBlock(OUTPUT, ModBlocks.REACTOR_OUTPUT_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        core.getLedger().add(ItemResource.of(Items.OAK_LOG), 1);
        core.getLedger().add(ItemResource.of(Items.RAW_IRON), 3);

        helper.runAfterDelay(2, () -> {
            // Log to planks to sticks on the table, raw iron to ingots in the furnace, then the pickaxe.
            ReactorPlanner.Result made = core.request(ItemResource.of(Items.IRON_PICKAXE), 1);
            helper.assertTrue(made.planned(), "a pickaxe from a log and raw iron: " + made.problem());
            ReactorPortBlockEntity output = helper.getBlockEntity(OUTPUT, ReactorPortBlockEntity.class);
            helper.assertTrue(output.getOutput().getStackInSlot(0).is(Items.IRON_PICKAXE), "delivered to the Output port");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.RAW_IRON)), 0L, "raw iron used");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.OAK_LOG)), 0L, "the log used");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.OAK_PLANKS)), 2L, "two planks over");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.STICK)), 2L, "two sticks over");
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() < HorizonCoreBlockEntity.ENERGY_CAPACITY, "paid in FE");

            // Nothing left to make another from: refused, and nothing changes.
            long mass = core.getLedger().mass();
            ReactorPlanner.Result again = core.request(ItemResource.of(Items.IRON_PICKAXE), 1);
            helper.assertFalse(again.planned(), "no more raw iron");
            helper.assertValueEqual(core.getLedger().mass(), mass, "a refused plan takes nothing");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: reactor.counts.live
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor_live", timeoutTicks = 2000)
    public static void theCoreKeepsCountOfWhatItCanMake(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        bay(helper, CORE.east(2), Items.CRAFTING_TABLE);
        bay(helper, CORE.west(2), Items.FURNACE);
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(2, () -> {
            core.take(ItemResource.of(Items.OAK_LOG), 1);
            core.take(ItemResource.of(Items.RAW_IRON), 3);
        });
        // Within a recount or two, off the server thread, it knows a pickaxe can be made, and how
        // many planks; a pickaxe it can't afford twice shows once.
        helper.succeedWhen(() -> {
            helper.assertValueEqual(core.getCounts().count(ItemResource.of(Items.IRON_PICKAXE)), 1L, "pickaxes ("
                    + core.getCounts().counts().size() + " counted, ledger v" + core.ledgerVersion() + ", mass "
                    + core.getLedger().mass() + ", formed " + core.isFormed() + ", recipes " + core.getRecipes().outputs().size() + ")");
            helper.assertValueEqual(core.getCounts().count(ItemResource.of(Items.OAK_PLANKS)), 4L, "planks");
            helper.assertValueEqual(core.getCounts().count(ItemResource.of(Items.IRON_INGOT)), 3L, "ingots");
        });
    }

    // covers: reactor.materialiser_port
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 100)
    public static void theMaterialiserPortOffersEverythingAndMakesWhatsTaken(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        bay(helper, CORE.north(2), Items.CRAFTING_TABLE);
        bay(helper, CORE.south(2), Items.FURNACE);
        BlockPos portPos = CORE.below().east(5);
        helper.setBlock(portPos, ModBlocks.REACTOR_MATERIALISER_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        helper.runAfterDelay(2, () -> {
            core.take(ItemResource.of(Items.OAK_LOG), 1);
            core.take(ItemResource.of(Items.RAW_IRON), 3);
        });
        helper.runAfterDelay(4, () -> {
            core.recountNow();
            ResourceHandler<ItemResource> port = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                    helper.absolutePos(portPos), null);
            helper.assertTrue(port != null, "the Materialiser Port has an item handler");
            ItemResource pickaxe = ItemResource.of(Items.IRON_PICKAXE);
            long shown = 0;
            for (int i = 0; i < port.size(); i++) {
                if (port.getResource(i).equals(pickaxe)) shown = port.getAmountAsLong(i);
            }
            helper.assertValueEqual(shown, 1L, "a pickaxe on offer");

            // Simulated: a plan, nothing used.
            long mass = core.getLedger().mass();
            helper.assertValueEqual(extract(port, Items.IRON_PICKAXE, 1, false), 1, "simulated");
            helper.assertValueEqual(core.getLedger().mass(), mass, "nothing used by a simulation");

            // Asking for more ingots than can be made gives what can: three.
            helper.assertValueEqual(extract(port, Items.IRON_INGOT, 5, false), 3, "trimmed to what can be made");

            // Committed: made, the inputs gone.
            helper.assertValueEqual(extract(port, Items.IRON_PICKAXE, 1, true), 1, "taken");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.RAW_IRON)), 0L, "raw iron used");
            helper.assertValueEqual(extract(port, Items.IRON_PICKAXE, 1, true), 0, "no second pickaxe");

            // Putting in works too.
            helper.assertValueEqual(insert(port, Items.DIAMOND, 4, true), 4, "taken in");
            helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.DIAMOND)), 4L, "in the ledger");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: reactor.counts.speed
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor_speed", timeoutTicks = 200)
    public static void aFullMaterialiserPortIsCheapToRead(GameTestHelper helper) {
        // Every vanilla workstation and a base's worth of stock: hundreds of slots for a storage bus to read.
        HorizonCoreBlockEntity core = buildReactor(helper, 3);
        Item[] stations = {Items.CRAFTING_TABLE, Items.FURNACE, Items.BLAST_FURNACE, Items.SMOKER, Items.STONECUTTER};
        BlockPos[] bays = {CORE.north(2), CORE.south(2), CORE.east(2), CORE.west(2), CORE.offset(2, 0, -1)};
        for (int i = 0; i < stations.length; i++) bay(helper, bays[i], stations[i]);
        BlockPos portPos = CORE.below().east(5);
        helper.setBlock(portPos, ModBlocks.REACTOR_MATERIALISER_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        for (String tag : List.of("minecraft:logs", "c:ingots", "c:gems", "c:dusts", "c:cobblestones", "c:stones",
                "c:sands", "c:raw_materials", "minecraft:wool", "c:dyes")) {
            BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse(tag)))
                    .forEach(holder -> core.take(ItemResource.of(holder.value()), 256));
        }
        helper.runAfterDelay(4, () -> {
            core.recountNow();
            ResourceHandler<ItemResource> port = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                    helper.absolutePos(portPos), null);
            int slots = port.size();
            helper.assertTrue(slots > 200, "hundreds of slots: " + slots);
            // What a storage bus does each time it looks: every slot's item and amount.
            long start = System.nanoTime();
            long sum = 0;
            for (int scan = 0; scan < 100; scan++) {
                for (int i = 0; i < port.size(); i++) {
                    if (!port.getResource(i).isEmpty()) sum += port.getAmountAsLong(i);
                }
            }
            double perScan = (System.nanoTime() - start) / 100.0 / 1000.0;
            // And taking something several steps away: a piston, from logs, cobblestone, iron and redstone.
            long planStart = System.nanoTime();
            int pistons = extract(port, Items.PISTON, 16, false);
            double planMs = (System.nanoTime() - planStart) / 1_000_000.0;
            Quantimium.LOGGER.info("Reactor Materialiser Port: {} slots, {} µs a scan, a simulated take of {} pistons {} ms (sum {})",
                    slots, String.format("%.1f", perScan), pistons, String.format("%.2f", planMs), sum);
            helper.assertTrue(perScan < 500 * TestSupport.timingSlack(), "a scan took " + perScan + " µs");
            helper.assertTrue(pistons == 16, "16 pistons, simulated: " + pistons);
            helper.assertTrue(planMs < 50 * TestSupport.timingSlack(), "planning took " + planMs + " ms");
            helper.succeed();
        });
    }

    // covers: reactor.singularity_holds
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 40)
    public static void theSingularityHoldsTheHorizon(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        helper.assertTrue(core.isFormed(), "formed with a Singularity seated");
        core.take(ItemResource.of(Items.IRON_INGOT), 500);
        core.take(ItemResource.of(Items.DIAMOND), 7);

        // Taken out, the Singularity carries all of it, and the cage is empty: no reactor.
        net.minecraft.world.item.ItemStack singularity = core.unseat();
        helper.assertValueEqual(com.kadikular.quantimium.item.SingularityItem.mass(singularity), 507L, "in the Singularity");
        helper.assertValueEqual(core.getLedger().mass(), 0L, "the cage holds nothing");
        helper.assertFalse(core.isFormed(), "no Singularity, no reactor");

        // Seated again, everything is back.
        helper.assertTrue(core.seat(singularity), "seated");
        helper.assertTrue(singularity.isEmpty(), "the stack went in");
        helper.assertValueEqual(core.getLedger().count(ItemResource.of(Items.IRON_INGOT)), 500L, "ingots back");
        helper.assertTrue(core.isFormed(), "a reactor again");

        // Broken, the core lets it go, with everything in it.
        helper.setBlock(CORE, net.minecraft.world.level.block.Blocks.AIR);
        java.util.List<net.minecraft.world.entity.item.ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(CORE)).inflate(2));
        long mass = dropped.stream().filter(e -> e.getItem().is(ModItems.SINGULARITY.get()))
                .mapToLong(e -> com.kadikular.quantimium.item.SingularityItem.mass(e.getItem())).sum();
        helper.assertValueEqual(mass, 507L, "the dropped Singularity holds it all");
        dropped.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    static int extract(ResourceHandler<ItemResource> handler, net.minecraft.world.item.Item item, int amount, boolean commit) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = handler.extract(ItemResource.of(item), amount, transaction);
            if (commit) transaction.commit();
            return taken;
        }
    }

    // covers: reactor.matter
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 40)
    public static void unrealisedMatterIsObservedIntoWhatsNeeded(GameTestHelper helper) {
        // Only Matter and a furnace: iron ingots come from Matter observed as raw iron, then smelted.
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        bay(helper, CORE.east(2), Items.FURNACE);
        helper.setBlock(OUTPUT, ModBlocks.REACTOR_OUTPUT_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        ItemResource matter = ItemResource.of(ModItems.UNREALISED_MATTER.get());
        core.take(matter, 10);
        helper.runAfterDelay(2, () -> {
            core.revalidate(helper.getLevel());
            var counts = core.recountNow();
            helper.assertTrue(counts.count(ItemResource.of(Items.RAW_IRON)) > 0, "raw iron on the list, from Matter");
            helper.assertTrue(counts.count(ItemResource.of(Items.IRON_INGOT)) > 0, "and ingots, through the furnace");
            ReactorPlanner.Result made = core.request(ItemResource.of(Items.IRON_INGOT), 3);
            helper.assertTrue(made.planned(), "three ingots from Matter: " + made.problem());
            helper.assertTrue(core.getLedger().count(matter) < 10, "Matter was observed for them");
            ReactorPortBlockEntity output = helper.getBlockEntity(OUTPUT, ReactorPortBlockEntity.class);
            helper.assertValueEqual(output.getOutput().getStackInSlot(0).getCount(), 3, "three ingots delivered");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: reactor.planning
    @GameTest(template = TestSupport.FLOOR_17, batch = "reactor", timeoutTicks = 40)
    public static void aFoldedFoundryTakesItsTurnInAChain(GameTestHelper helper) {
        HorizonCoreBlockEntity core = buildReactor(helper, 1);
        bay(helper, CORE.west(2), Items.FURNACE);
        helper.setBlock(CORE.east(2), ModBlocks.CATALYST_BAY.get());
        helper.getBlockEntity(CORE.east(2), CatalystBayBlockEntity.class).setCatalyst(foldedFoundry(4));
        helper.setBlock(OUTPUT, ModBlocks.REACTOR_OUTPUT_PORT.get());
        core.revalidate(helper.getLevel());
        core.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        core.getLedger().add(ItemResource.of(Items.SAND), 2);
        core.getLedger().add(ItemResource.of(ModItems.ANOMALITE_SHARD.get()), 1);

        helper.runAfterDelay(2, () -> {
            // Sand to glass in the furnace, then glass and a shard to attuned glass in the folded Foundry.
            ReactorPlanner.Result made = core.request(ItemResource.of(ModItems.QUANTUM_ATTUNED_GLASS_ITEM.get()), 2);
            helper.assertTrue(made.planned(), "attuned glass from sand: " + made.problem());
            helper.assertValueEqual(made.plan().steps().size(), 2, "two steps, two machines");
            helper.assertValueEqual(core.getLedger().mass(), 0L, "all of it used");
            ReactorPortBlockEntity output = helper.getBlockEntity(OUTPUT, ReactorPortBlockEntity.class);
            helper.assertValueEqual(output.getOutput().getStackInSlot(0).getCount(), 2, "two attuned glass delivered");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    /** A Folded Tesseract standing for a Foundry with {@code arms} arms, as a catalyst only. */
    static net.minecraft.world.item.ItemStack foldedFoundry(int arms) {
        net.minecraft.nbt.CompoundTag state = new net.minecraft.nbt.CompoundTag();
        state.putInt(FoundryFoldAdapter.ARMS, arms);
        net.minecraft.world.item.ItemStack folded = new net.minecraft.world.item.ItemStack(ModItems.FOLDED_TESSERACT.get());
        folded.set(ModDataComponents.FOLDED.get(), new FoldedStructure(FoundryFoldAdapter.ID, 9, 3, 9, BlockPos.ZERO,
                java.util.List.of(), state, new net.minecraft.world.item.ItemStack(ModItems.TESSERACT.get())));
        return folded;
    }

    static int insert(ResourceHandler<ItemResource> handler, net.minecraft.world.item.Item item, int amount, boolean commit) {
        try (Transaction transaction = Transaction.openRoot()) {
            int taken = handler.insert(ItemResource.of(item), amount, transaction);
            if (commit) transaction.commit();
            return taken;
        }
    }

    /** A reactor with {@code pairs} emitter pairs and an Input port, checked once. */
    public static HorizonCoreBlockEntity buildReactor(GameTestHelper helper, int pairs) {
        ServerLevel level = helper.getLevel();
        BlockPos core = helper.absolutePos(CORE);
        ReactorStructure.forEachPlinthCell(core, pos -> level.setBlockAndUpdate(pos, ModBlocks.REACTOR_PLINTH.get().defaultBlockState()));
        helper.setBlock(INPUT, ModBlocks.REACTOR_INPUT_PORT.get());
        helper.setBlock(CORE, ModBlocks.HORIZON_CORE.get());
        if (pairs >= 1) {
            emitter(helper, CORE.east(4));
            emitter(helper, CORE.west(4));
        }
        if (pairs >= 2) {
            emitter(helper, CORE.north(4));
            emitter(helper, CORE.south(4));
        }
        HorizonCoreBlockEntity horizon = helper.getBlockEntity(CORE, HorizonCoreBlockEntity.class);
        horizon.seat(new net.minecraft.world.item.ItemStack(ModItems.SINGULARITY.get()));
        horizon.revalidate(level);
        return horizon;
    }

    public static void bay(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item catalyst) {
        helper.setBlock(pos, ModBlocks.CATALYST_BAY.get());
        helper.getBlockEntity(pos, CatalystBayBlockEntity.class).setCatalyst(new net.minecraft.world.item.ItemStack(catalyst));
    }

    private static void emitter(GameTestHelper helper, BlockPos pos) {
        Block block = ModBlocks.RING_EMITTER.get();
        helper.setBlock(pos, block);
    }
}
