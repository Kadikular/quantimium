package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.reactor.ReactorCounter;
import com.kadikular.quantimium.reactor.ReactorPlanner;
import com.kadikular.quantimium.reactor.ReactorRecipes;
import com.kadikular.quantimium.recipe.RecipeShape;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Reactor's counts: what its screen and Materialiser Port show it can make. Small hand-made
 * recipe sets pin the rules down; large ones time it.
 *
 * <p>Vanilla items stand in for the shapes of real recipes. Glowstone dust plays iron dust.
 */
public final class ReactorCountTests {

    private static final Item INGOT = Items.IRON_INGOT;
    private static final Item DUST = Items.GLOWSTONE_DUST;
    private static int nextId;

    private ReactorCountTests() {}

    // covers: reactor.counts.loops
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void matterIsNeverCountedBackIntoAFormItCameFrom(GameTestHelper helper) {
        // 100 ingots and 25 dust, each turning into the other: 125 of each, never 150.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(
                recipe(INGOT, 1, DUST, 1), recipe(DUST, 1, INGOT, 1), recipe(INGOT, 3, Items.BUCKET, 1)));
        ReactorCounter.Counts counts = count(recipes, Map.of(INGOT, 100L, DUST, 25L));
        helper.assertValueEqual(counts.count(of(INGOT)), 125L, "ingots");
        helper.assertValueEqual(counts.count(of(DUST)), 125L, "dust");
        helper.assertValueEqual(counts.count(of(Items.BUCKET)), 41L, "buckets: three ingots each, from all 125");
        helper.assertValueEqual(counts.groups(), 1, "one loop group");
        helper.succeed();
    }

    // covers: reactor.counts.loops
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void whatFlowsIntoALoopCountsAsRealThere(GameTestHelper helper) {
        // Ten raw iron smelt to ingots: real ingots now, so they turn into dust too.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(
                recipe(INGOT, 1, DUST, 1), recipe(DUST, 1, INGOT, 1), recipe(Items.RAW_IRON, 1, INGOT, 1)));
        ReactorCounter.Counts counts = count(recipes, Map.of(INGOT, 100L, DUST, 25L, Items.RAW_IRON, 10L));
        helper.assertValueEqual(counts.count(of(INGOT)), 135L, "ingots");
        helper.assertValueEqual(counts.count(of(DUST)), 135L, "dust");
        helper.assertValueEqual(counts.count(of(Items.RAW_IRON)), 10L, "raw iron, held");
        helper.succeed();
    }

    // covers: reactor.counts.loops
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void aLoopThatGainsCountsOnlyWhatsReal(GameTestHelper helper) {
        // One ingot makes two dust and one dust makes an ingot: a duplication loop, never used.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(recipe(INGOT, 1, DUST, 2), recipe(DUST, 1, INGOT, 1)));
        ReactorCounter.Counts counts = count(recipes, Map.of(INGOT, 10L, DUST, 4L));
        helper.assertValueEqual(counts.count(of(INGOT)), 10L, "ingots: only the real ones");
        helper.assertValueEqual(counts.count(of(DUST)), 4L, "dust: only the real ones");
        helper.assertValueEqual(counts.gaining().size(), 1, "reported");
        helper.succeed();
    }

    // covers: reactor.counts.shared
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void ingredientsFromOneSourceShareIt(GameTestHelper helper) {
        // A bar of one ingot and one dust draws on the same 125 iron twice: 62, not 125.
        ReactorRecipes loop = ReactorRecipes.of(List.of(), List.of(recipe(INGOT, 1, DUST, 1), recipe(DUST, 1, INGOT, 1),
                shape(List.of(input(INGOT, 1), input(DUST, 1)), Items.IRON_BARS, 1)));
        helper.assertValueEqual(count(loop, Map.of(INGOT, 100L, DUST, 25L)).count(of(Items.IRON_BARS)), 62L, "bars");

        // A wooden pickaxe is three planks and two sticks, and the sticks are planks too: four planks,
        // one log, a pickaxe. Three logs make three, though planks alone would say four.
        ReactorRecipes wood = ReactorRecipes.of(List.of(), List.of(
                recipe(Items.OAK_LOG, 1, Items.OAK_PLANKS, 4),
                recipe(Items.OAK_PLANKS, 2, Items.STICK, 4),
                shape(List.of(input(Items.OAK_PLANKS, 3), input(Items.STICK, 2)), Items.WOODEN_PICKAXE, 1)));
        ReactorCounter.Counts counts = count(wood, Map.of(Items.OAK_LOG, 3L));
        helper.assertValueEqual(counts.count(of(Items.OAK_PLANKS)), 12L, "planks");
        helper.assertValueEqual(counts.count(of(Items.STICK)), 24L, "sticks");
        helper.assertValueEqual(counts.count(of(Items.WOODEN_PICKAXE)), 3L, "pickaxes");
        helper.succeed();
    }

    // covers: reactor.counts.shared
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void anAnvilCountsItsIronOnce(GameTestHelper helper) {
        // 40 ingots and 5 blocks are 85 iron; an anvil is 3 blocks and 4 ingots, 31 iron: two anvils.
        // Counting blocks and ingots apart would say three.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(
                recipe(INGOT, 9, Items.IRON_BLOCK, 1), recipe(Items.IRON_BLOCK, 1, INGOT, 9),
                shape(List.of(input(Items.IRON_BLOCK, 3), input(INGOT, 4)), Items.ANVIL, 1)));
        ReactorCounter.Counts counts = count(recipes, Map.of(INGOT, 40L, Items.IRON_BLOCK, 5L));
        helper.assertValueEqual(counts.count(of(INGOT)), 85L, "ingots, every block undone");
        helper.assertValueEqual(counts.count(of(Items.IRON_BLOCK)), 9L, "blocks, every ingot packed");
        helper.assertValueEqual(counts.count(of(Items.ANVIL)), 2L, "anvils");

        // And the planner agrees: two, and not a third.
        ReactorPlanner.Result two = ReactorPlanner.plan(recipes, resources(Map.of(INGOT, 40L, Items.IRON_BLOCK, 5L)),
                of(Items.ANVIL), 2);
        helper.assertTrue(two.planned(), "two anvils: " + two.problem());
        helper.assertFalse(ReactorPlanner.plan(recipes, resources(Map.of(INGOT, 40L, Items.IRON_BLOCK, 5L)),
                of(Items.ANVIL), 3).planned(), "not three");
        helper.succeed();
    }

    // covers: reactor.planning
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void aContainerARecipeGivesBackItselfIsNotGivenTwice(GameTestHelper helper) {
        // A machine that takes a milk bucket and gives a cake and the empty bucket: one bucket, not two.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(new ReactorRecipes.Producer(
                new RecipeShape(id(), List.of(input(Items.MILK_BUCKET, 1)),
                        List.of(new ItemStack(Items.CAKE), new ItemStack(Items.BUCKET)), 100), 0)));
        ReactorPlanner.Result result = ReactorPlanner.plan(recipes, resources(Map.of(Items.MILK_BUCKET, 1L)), of(Items.CAKE), 1);
        helper.assertTrue(result.planned(), "a cake: " + result.problem());
        helper.assertValueEqual(result.plan().leftovers().get(of(Items.BUCKET)), 1L, "one bucket back");
        helper.succeed();
    }

    // covers: reactor.planning.choice
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void theLeastWastefulRecipeIsUsed(GameTestHelper helper) {
        // A crafting table makes four stairs from six stone; a stonecutter one from each. Cheaper in FE
        // per stair, the table still wastes half a stone a stair: the stonecutter is used.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(
                recipe(Items.STONE, 6, Items.STONE_STAIRS, 4),
                new ReactorRecipes.Producer(new RecipeShape(id(), List.of(input(Items.STONE, 1)),
                        List.of(new ItemStack(Items.STONE_STAIRS, 1)), 400), 1)));
        ReactorPlanner.Result result = ReactorPlanner.plan(recipes, resources(Map.of(Items.STONE, 6L)), of(Items.STONE_STAIRS), 4);
        helper.assertTrue(result.planned(), "four stairs: " + result.problem());
        helper.assertValueEqual(result.plan().steps().getFirst().bay(), 1, "the stonecutter's bay");
        helper.assertValueEqual(result.plan().consumed().get(of(Items.STONE)), 4L, "four stone, not six");
        helper.assertValueEqual(count(recipes, Map.of(Items.STONE, 6L)).count(of(Items.STONE_STAIRS)), 6L, "six on the list");
        helper.succeed();
    }

    // covers: reactor.planning.tools
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void aPressIsNeededButNeverUsedUp(GameTestHelper helper) {
        // An Inscriber-style recipe: a gold ingot under a press (a piston stands in) makes a circuit
        // (a comparator stands in). The press stays; it doesn't limit how many.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(
                new ReactorRecipes.Producer(new RecipeShape(id(), List.of(input(Items.GOLD_INGOT, 1)),
                        List.of(new ItemStack(Items.COMPARATOR, 1)), 100, List.of(Ingredient.of(Items.PISTON))), 0),
                recipe(Items.OBSIDIAN, 1, Items.PISTON, 1)));
        helper.assertValueEqual(count(recipes, Map.of(Items.GOLD_INGOT, 5L)).count(of(Items.COMPARATOR)), 0L,
                "no circuits without a press");
        ReactorCounter.Counts withPress = count(recipes, Map.of(Items.GOLD_INGOT, 5L, Items.PISTON, 1L));
        helper.assertValueEqual(withPress.count(of(Items.COMPARATOR)), 5L, "five circuits, one press");
        ReactorPlanner.Result five = ReactorPlanner.plan(recipes, resources(Map.of(Items.GOLD_INGOT, 5L, Items.PISTON, 1L)),
                of(Items.COMPARATOR), 5);
        helper.assertTrue(five.planned(), "five circuits: " + five.problem());
        helper.assertFalse(five.plan().consumed().containsKey(of(Items.PISTON)), "the press isn't used up");

        // No press, but one can be made: it's made once, and kept.
        ReactorPlanner.Result made = ReactorPlanner.plan(recipes, resources(Map.of(Items.GOLD_INGOT, 5L, Items.OBSIDIAN, 1L)),
                of(Items.COMPARATOR), 5);
        helper.assertTrue(made.planned(), "a press made for the job: " + made.problem());
        helper.assertValueEqual(made.plan().leftovers().get(of(Items.PISTON)), 1L, "and kept");
        helper.succeed();
    }

    // covers: reactor.counts.reachable
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void onlyWhatCanBeMadeNowIsShown(GameTestHelper helper) {
        // Hoppers need iron and a chest; with no wood, there's no chest and no hopper.
        ReactorRecipes recipes = ReactorRecipes.of(List.of(), List.of(
                recipe(Items.OAK_PLANKS, 8, Items.CHEST, 1),
                shape(List.of(input(INGOT, 5), input(Items.CHEST, 1)), Items.HOPPER, 1),
                recipe(INGOT, 3, Items.BUCKET, 1)));
        ReactorCounter.Counts noWood = count(recipes, Map.of(INGOT, 20L));
        helper.assertValueEqual(noWood.count(of(Items.HOPPER)), 0L, "no hopper without wood");
        helper.assertValueEqual(noWood.count(of(Items.BUCKET)), 6L, "buckets");
        ReactorCounter.Counts wood = count(recipes, Map.of(INGOT, 20L, Items.OAK_PLANKS, 16L));
        helper.assertValueEqual(wood.count(of(Items.CHEST)), 2L, "two chests");
        helper.assertValueEqual(wood.count(of(Items.HOPPER)), 2L, "two hoppers: chests run out first");
        // Two ingots short of a third bucket's worth: not shown at all.
        ReactorCounter.Counts few = count(recipes, Map.of(INGOT, 2L));
        helper.assertFalse(few.counts().containsKey(of(Items.BUCKET)), "less than one bucket's worth isn't shown");
        helper.succeed();
    }

    // covers: reactor.counts.speed
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts_speed", timeoutTicks = 200)
    public static void fiftyThousandRecipesCountQuickly(GameTestHelper helper) {
        // Random recipes over every registered item, with loops on purpose: a large pack's worst case.
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item != Items.AIR) items.add(item);
        });
        Random random = new Random(42);
        List<ReactorRecipes.Producer> producers = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            List<RecipeShape.Input> inputs = new ArrayList<>();
            int count = 1 + random.nextInt(4);
            for (int j = 0; j < count; j++) inputs.add(input(items.get(random.nextInt(items.size())), 1 + random.nextInt(4)));
            producers.add(new ReactorRecipes.Producer(new RecipeShape(id(), inputs,
                    List.of(new ItemStack(items.get(random.nextInt(items.size())), 1 + random.nextInt(4))), 100), 0));
        }
        Map<Item, Long> stock = new LinkedHashMap<>();
        for (int i = 0; i < 1_000; i++) stock.put(items.get(random.nextInt(items.size())), 1L + random.nextInt(10_000));

        ReactorRecipes recipes = ReactorRecipes.of(List.of(), producers);
        long built = System.nanoTime();
        recipes.graph();
        long buildMs = (System.nanoTime() - built) / 1_000_000;
        ReactorCounter.count(recipes.graph(), resources(stock)); // warm up
        long best = Long.MAX_VALUE;
        ReactorCounter.Counts counts = null;
        for (int run = 0; run < 5; run++) {
            counts = ReactorCounter.count(recipes.graph(), resources(stock));
            best = Math.min(best, counts.nanos());
        }
        long countMs = best / 1_000_000;
        Quantimium.LOGGER.info("Reactor counts, 50k random recipes over {} items: graph {} ms, count {} ms; {} reachable, {} groups, {} counted",
                items.size(), buildMs, countMs, counts.reachable(), counts.groups(), counts.counts().size());
        helper.assertTrue(countMs < 250 * TestSupport.timingSlack(), "counting took " + countMs + " ms");
        helper.succeed();
    }

    // covers: reactor.counts.speed
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts_speed", timeoutTicks = 400)
    public static void thePacksOwnRecipesCountQuickly(GameTestHelper helper) {
        // Every vanilla workstation and a folded Foundry, over the dev pack's real recipes, with a
        // base's worth of stock: 256 of every log, ingot, gem, dust and stone the pack tags.
        List<ItemStack> catalysts = List.of(new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.FURNACE),
                new ItemStack(Items.BLAST_FURNACE), new ItemStack(Items.SMOKER), new ItemStack(Items.STONECUTTER),
                ReactorTests.foldedFoundry(4));
        long built = System.nanoTime();
        ReactorRecipes recipes = ReactorRecipes.build(helper.getLevel(), catalysts);
        recipes.graph();
        long buildMs = (System.nanoTime() - built) / 1_000_000;
        Map<Item, Long> stock = new LinkedHashMap<>();
        for (String tag : List.of("minecraft:logs", "c:ingots", "c:gems", "c:dusts", "c:cobblestones", "c:stones",
                "c:sands", "c:raw_materials", "c:ores", "minecraft:wool", "c:dyes")) {
            BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    Identifier.parse(tag))).forEach(holder -> stock.put(holder.value(), 256L));
        }
        ReactorCounter.count(recipes.graph(), resources(stock)); // warm up
        long best = Long.MAX_VALUE;
        ReactorCounter.Counts counts = null;
        for (int run = 0; run < 5; run++) {
            counts = ReactorCounter.count(recipes.graph(), resources(stock));
            best = Math.min(best, counts.nanos());
        }
        long countMs = best / 1_000_000;
        Quantimium.LOGGER.info("Reactor counts, the pack's recipes ({} through {} catalysts), {} kinds held: build {} ms, count {} ms; "
                        + "{} reachable, {} groups, {} counted, gaining loops {}",
                recipes.graph().size(), catalysts.size(), stock.size(), buildMs, countMs, counts.reachable(), counts.groups(),
                counts.counts().size(), counts.gaining());
        helper.assertTrue(countMs < 100 * TestSupport.timingSlack(), "counting took " + countMs + " ms");
        helper.succeed();
    }

    // covers: reactor.traces
    @GameTest(template = TestSupport.FLOOR_9, batch = "reactor_counts", timeoutTicks = 20)
    public static void thePlinthTracesMatchTheirArt(GameTestHelper helper) {
        // {x, z, north edge, west edge} as tools/reactor_traces.py's preview works them out at y 64.
        int[][] expected = {{-37, -200, 0, 2}, {-37, -7, 1, 1}, {-37, 0, 0, 1}, {-37, 3, 0, 0}, {-37, 99999, 0, 0}, {-1, -200, 2, 0}, {-1, -7, 0, 1}, {-1, 0, 0, 0}, {-1, 3, 0, 1}, {-1, 99999, 1, 1}, {0, -200, 0, 0}, {0, -7, 0, 1}, {0, 0, 2, 0}, {0, 3, 0, 1}, {0, 99999, 0, 1}, {5, -200, 1, 0}, {5, -7, 0, 1}, {5, 0, 0, 0}, {5, 3, 0, 0}, {5, 99999, 2, 0}, {123456, -200, 0, 0}, {123456, -7, 0, 0}, {123456, 0, 0, 1}, {123456, 3, 0, 0}, {123456, 99999, 0, 1}};
        for (int[] row : expected) {
            net.minecraft.world.level.block.state.BlockState state = com.kadikular.quantimium.reactor.ReactorTraces.at(
                    com.kadikular.quantimium.init.ModBlocks.REACTOR_PLINTH.get().defaultBlockState(),
                    new net.minecraft.core.BlockPos(row[0], 64, row[1]));
            helper.assertValueEqual(state.getValue(com.kadikular.quantimium.reactor.ReactorTraces.NORTH), row[2],
                    "north edge at " + row[0] + ", " + row[1]);
            helper.assertValueEqual(state.getValue(com.kadikular.quantimium.reactor.ReactorTraces.WEST), row[3],
                    "west edge at " + row[0] + ", " + row[1]);
        }
        helper.succeed();
    }

    // ---- helpers ----

    static ReactorCounter.Counts count(ReactorRecipes recipes, Map<Item, Long> stock) {
        return ReactorCounter.count(recipes.graph(), resources(stock));
    }

    static Map<ItemResource, Long> resources(Map<Item, Long> stock) {
        Map<ItemResource, Long> out = new LinkedHashMap<>();
        stock.forEach((item, amount) -> out.merge(ItemResource.of(item), amount, Long::sum));
        return out;
    }

    static ItemResource of(Item item) {
        return ItemResource.of(item);
    }

    static ReactorRecipes.Producer recipe(Item in, int count, Item out, int made) {
        return shape(List.of(input(in, count)), out, made);
    }

    static ReactorRecipes.Producer shape(List<RecipeShape.Input> inputs, Item out, int made) {
        return new ReactorRecipes.Producer(new RecipeShape(id(), inputs, List.of(new ItemStack(out, made)), 100), 0);
    }

    static RecipeShape.Input input(Item item, int count) {
        return new RecipeShape.Input(Ingredient.of(item), count);
    }

    private static Identifier id() {
        return Identifier.fromNamespaceAndPath(Quantimium.MODID, "test/" + nextId++);
    }
}
