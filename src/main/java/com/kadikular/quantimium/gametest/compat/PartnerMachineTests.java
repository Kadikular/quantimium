package com.kadikular.quantimium.gametest.compat;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.gametest.ReactorTests;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.recipe.RecipeCompat;
import com.kadikular.quantimium.recipe.RecipeShape;
import com.kadikular.quantimium.recipe.RecipeShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

/**
 * The machines of the mods All the Mods 11 plays with, read through their recipe adapters
 * (data/quantimium/recipe_adapters) and made with in a Reactor. Each runs only with its mod loaded.
 */
public final class PartnerMachineTests {

    private static final String NS = Quantimium.MODID;

    private PartnerMachineTests() {}

    // covers: compat.partner_machines
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_enderio", timeoutTicks = 40)
    public static void enderioMachines(GameTestHelper helper) {
        if (skip(helper, "enderio")) return;
        makes(helper, "enderio:alloy_smelter", "enderio:conductive_alloy_ingot");
        makes(helper, "enderio:alloy_smelter", "minecraft:iron_ingot");
        makes(helper, "enderio:sag_mill", "minecraft:blaze_powder");
        makes(helper, "enderio:slice_and_splice", "enderio:ender_resonator");
        // Only what a SAG mill surely gives counts: a recipe that's all chances isn't offered.
        helper.assertTrue(shapes(helper, "enderio:sag_mill").stream().noneMatch(s -> s.id().getPath().equals("sag_milling/allium")),
                "an allium only might give dye");
        helper.succeed();
    }

    // covers: compat.partner_machines
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_energizedpower", timeoutTicks = 40)
    public static void energizedPowerMachines(GameTestHelper helper) {
        if (skip(helper, "energizedpower")) return;
        makes(helper, "energizedpower:crusher", "minecraft:andesite");
        makes(helper, "energizedpower:pulverizer", "minecraft:bone_meal");
        makes(helper, "energizedpower:compressor", "energizedpower:copper_plate");
        makes(helper, "energizedpower:alloy_furnace", "energizedpower:steel_ingot");
        makes(helper, "energizedpower:assembling_machine", "energizedpower:advanced_circuit");
        // The press mold only has to be there: it's still there after.
        makes(helper, "energizedpower:metal_press", "energizedpower:copper_wire");
        RecipeShape sawing = shape(helper, "energizedpower:sawmill", "minecraft:oak_planks");
        helper.assertTrue(sawing.outputs().stream().anyMatch(o -> o.is(item("energizedpower:sawdust"))), "sawdust comes with the planks");
        // A shard that grows into two would be an endless amethyst with instant crafting: not offered.
        helper.assertTrue(!com.kadikular.quantimium.recipe.CatalystResolver.isCatalystAllowed(stack("energizedpower:crystal_growth_chamber")),
                "no crystal growth chamber");
        helper.succeed();
    }

    // covers: compat.partner_machines
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_others", timeoutTicks = 40)
    public static void powahBeesAndMysticalAgriculture(GameTestHelper helper) {
        if (ModList.get().isLoaded("powah")) makes(helper, "powah:energizing_orb", "powah:crystal_blazing");
        if (ModList.get().isLoaded("productivebees")) makes(helper, "productivebees:centrifuge", "productivebees:wax");
        if (ModList.get().isLoaded("mysticalagriculture")) {
            makes(helper, "mysticalagriculture:seed_reprocessor", "mysticalagriculture:inferium_essence");
            makes(helper, "mysticalagriculture:infusion_altar", "mysticalagriculture:absorption_i_augment");
        }
        if (ModList.get().isLoaded("ironfurnaces")) makes(helper, "ironfurnaces:iron_furnace", "minecraft:glass");
        helper.succeed();
    }

    // covers: compat.bees_simulator
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_bees_sim", timeoutTicks = 40)
    public static void aHiveNeedsAnUpgradeToBeSimulated(GameTestHelper helper) {
        if (skip(helper, "productivebees")) return;
        var level = helper.getLevel();
        BlockPos at = new BlockPos(8, 2, 8);
        helper.setBlock(at, com.kadikular.quantimium.init.ModBlocks.QUANTUM_SIMULATOR.get());
        helper.setBlock(at.above(), BuiltInRegistries.BLOCK.getValue(Identifier.parse("productivebees:advanced_oak_beehive")));
        var simulator = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity.class);
        // Bare, its bees would fly out of the field: the Simulator won't take it.
        simulator.toggleEngage(null);
        helper.assertTrue(!simulator.isEngaged(), "a hive without an upgrade isn't simulated");
        // With a Simulator Upgrade they stay home.
        BlockPos hivePos = helper.absolutePos(at.above());
        var saved = level.getBlockEntity(hivePos).saveWithFullMetadata(level.registryAccess());
        net.minecraft.nbt.CompoundTag upgrade = new net.minecraft.nbt.CompoundTag();
        upgrade.putString("id", "productivelib:upgrade_simulator");
        upgrade.putInt("count", 1);
        saved.getCompoundOrEmpty("upgrades").getListOrEmpty("stacks").set(0, upgrade);
        level.setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(hivePos, level.getBlockState(hivePos),
                saved, level.registryAccess()));
        simulator.toggleEngage(null);
        helper.assertTrue(simulator.isEngaged(), "with a Simulator Upgrade it is");
        simulator.toggleEngage(null);
        helper.succeed();
    }

    private static boolean skip(GameTestHelper helper, String mod) {
        if (ModList.get().isLoaded(mod)) return false;
        helper.succeed();
        return true;
    }

    /** A fresh Reactor with {@code catalyst} in a bay makes one {@code output} from exactly what its recipe takes. */
    private static void makes(GameTestHelper helper, String catalyst, String output) {
        RecipeShape shape = shape(helper, catalyst, output);
        clear(helper);
        HorizonCoreBlockEntity horizon = ReactorTests.buildReactor(helper, 1);
        ReactorTests.bay(helper, ReactorTests.CORE.north(2), item(catalyst));
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        // One tick of upkeep powers its rings.
        BlockPos core = helper.absolutePos(ReactorTests.CORE);
        HorizonCoreBlockEntity.serverTick(helper.getLevel(), core, helper.getLevel().getBlockState(core), horizon);
        for (RecipeShape.Input input : shape.inputs()) horizon.take(ItemResource.of(first(input.ingredient())), input.count());
        for (Ingredient tool : shape.tools()) horizon.take(ItemResource.of(first(tool)), 1);
        ItemResource made = ItemResource.of(item(output));
        long before = horizon.getLedger().count(made);
        var result = horizon.request(made, 1);
        helper.assertTrue(result.planned(), catalyst + " makes " + output + ": " + result.problem());
        helper.assertTrue(horizon.getLedger().count(made) > before, catalyst + " made " + output);
        for (Ingredient tool : shape.tools()) {
            helper.assertValueEqual(horizon.getLedger().count(ItemResource.of(first(tool))), 1L, catalyst + " kept its tool");
        }
    }

    private static RecipeShape shape(GameTestHelper helper, String catalyst, String output) {
        Item wanted = item(output);
        for (RecipeShape shape : shapes(helper, catalyst)) {
            if (shape.primaryOutput().is(wanted)) return shape;
        }
        throw helper.assertionException(catalyst + " offers no recipe for " + output);
    }

    private static List<RecipeShape> shapes(GameTestHelper helper, String catalyst) {
        ItemStack stack = stack(catalyst);
        helper.assertTrue(com.kadikular.quantimium.recipe.CatalystResolver.isCatalystAllowed(stack), catalyst + " is a catalyst");
        return RecipeShapes.forCatalyst(helper.getLevel(), stack);
    }

    /** The test area is shared by every check in a test: clear the Reactor between them. */
    private static void clear(GameTestHelper helper) {
        BlockPos core = ReactorTests.CORE;
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-6, -1, -6), core.offset(6, 2, 6))) {
            helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
        }
    }

    private static ItemStack first(Ingredient ingredient) {
        return RecipeCompat.stacks(ingredient).getFirst();
    }

    private static ItemStack stack(String id) {
        return new ItemStack(item(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }
}
