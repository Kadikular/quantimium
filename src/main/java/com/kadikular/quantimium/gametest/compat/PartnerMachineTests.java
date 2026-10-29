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

    // covers: crafter.fluids
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_energizedpower_fluids", timeoutTicks = 80)
    public static void energizedPowerFluidMachinesInTheCrafter(GameTestHelper helper) {
        if (skip(helper, "energizedpower")) return;
        var transposer = fluidCrafter(helper, 2, "energizedpower:fluid_transposer", new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4_000));
        com.kadikular.quantimium.gametest.CrafterTests.grid(transposer, new ItemStack(net.minecraft.world.item.Items.BLACK_CONCRETE_POWDER, 4));
        var freezer = fluidCrafter(helper, 5, "energizedpower:fluid_freezer", new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4_000));
        // Its tanks filled after it's first looked, with nothing in its grid: it looks again.
        var solidifier = fluidCrafter(helper, 8, "energizedpower:stone_solidifier");
        var liquefier = fluidCrafter(helper, 11, "energizedpower:stone_liquefier");
        com.kadikular.quantimium.gametest.CrafterTests.grid(liquefier, new ItemStack(net.minecraft.world.item.Items.ANDESITE, 4));
        helper.runAfterDelay(5, () -> {
            solidifier.inputTanks().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4_000), false);
            solidifier.inputTanks().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 4_000), false);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(com.kadikular.quantimium.gametest.CrafterTests.craftOf(transposer, net.minecraft.world.item.Items.BLACK_CONCRETE) != null, "concrete, filled with water");
            helper.assertTrue(com.kadikular.quantimium.gametest.CrafterTests.craftOf(freezer, net.minecraft.world.item.Items.ICE) != null, "ice, frozen from water alone");
            helper.assertTrue(com.kadikular.quantimium.gametest.CrafterTests.craftOf(solidifier, net.minecraft.world.item.Items.ANDESITE) != null, "andesite, from water and lava");
            helper.assertTrue(liquefier.getPreview().stream().anyMatch(craft -> craft.outputs().isEmpty()
                    && craft.fluidOutputs().stream().anyMatch(fluid -> fluid.getFluid().isSame(net.minecraft.world.level.material.Fluids.LAVA))), "lava, from andesite");
            helper.succeed();
        });
    }

    // covers: crafter.fluid_sides
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_energizedpower_fluid_pulls", timeoutTicks = 60)
    public static void aPipeTakesTheFluidAPreviewMakes(GameTestHelper helper) {
        if (skip(helper, "energizedpower")) return;
        var liquefier = fluidCrafter(helper, 2, "energizedpower:stone_liquefier");
        com.kadikular.quantimium.gametest.CrafterTests.grid(liquefier, new ItemStack(net.minecraft.world.item.Items.ANDESITE, 4));
        helper.runAfterDelay(10, () -> {
            var level = helper.getLevel();
            var down = net.minecraft.core.Direction.DOWN;
            var pipe = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, liquefier.getBlockPos(), down);
            var lava = net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.LAVA);
            helper.assertTrue(pipe != null && net.neoforged.neoforge.transfer.ResourceHandlerUtil.contains(pipe, lava), "the lava it would make shows on its face");
            // Asked and called off: nothing made.
            try (var asked = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                helper.assertTrue(pipe.extract(lava, 100, asked) == 100, "100 mB would come out");
            }
            helper.assertValueEqual(andesite(liquefier), 4, "no andesite used by asking");
            try (var taken = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                helper.assertValueEqual(pipe.extract(lava, 100, taken), 100, "100 mB taken");
                taken.commit();
            }
            helper.assertTrue(andesite(liquefier) < 4, "andesite melted for it");

            // A face set to take in only gives nothing out; a face with fluids off has no tanks.
            var configs = new java.util.ArrayList<>(liquefier.getSideConfigs());
            configs.set(down.get3DDataValue(), configs.get(down.get3DDataValue()).withFluidMode(com.kadikular.quantimium.block.entity.simulation.SideMode.INPUT));
            liquefier.applySideConfigs(configs);
            var input = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, liquefier.getBlockPos(), down);
            try (var none = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                helper.assertValueEqual(input.extract(lava, 100, none), 0, "nothing out of an input face");
            }
            configs.set(down.get3DDataValue(), configs.get(down.get3DDataValue()).withFluidMode(com.kadikular.quantimium.block.entity.simulation.SideMode.DISABLED));
            liquefier.applySideConfigs(configs);
            helper.assertTrue(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, liquefier.getBlockPos(), down) == null,
                    "no tanks on a face with fluids off");
            helper.succeed();
        });
    }

    // covers: crafter.fluid_sides
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_energizedpower_fluid_auto", timeoutTicks = 80)
    public static void autoOutputPushesTheFluidsItMakes(GameTestHelper helper) {
        if (skip(helper, "energizedpower")) return;
        // A recipe making only lava, auto output on for items and fluids: the lava goes out as lava, into the tanks of a
        // Crafter beside it (and nothing breaks looking for an item it makes).
        var liquefier = fluidCrafter(helper, 2, "energizedpower:stone_liquefier");
        com.kadikular.quantimium.gametest.CrafterTests.grid(liquefier, new ItemStack(net.minecraft.world.item.Items.ANDESITE, 8));
        var beside = fluidCrafter(helper, 3, "energizedpower:stone_solidifier");
        var east = net.minecraft.core.Direction.EAST;
        var configs = new java.util.ArrayList<>(liquefier.getSideConfigs());
        configs.set(east.get3DDataValue(), new com.kadikular.quantimium.block.entity.simulation.SideConfig(east,
                com.kadikular.quantimium.block.entity.simulation.SideMode.BOTH, com.kadikular.quantimium.block.entity.simulation.SideConfig.ALL_ITEM_INPUTS,
                com.kadikular.quantimium.block.entity.simulation.SideConfig.ALL_ITEM_OUTPUTS, false, true,
                com.kadikular.quantimium.block.entity.simulation.SideMode.BOTH, com.kadikular.quantimium.block.entity.simulation.SideConfig.ALL_FLUID_INPUTS,
                com.kadikular.quantimium.block.entity.simulation.SideConfig.ALL_FLUID_OUTPUTS, false, true));
        liquefier.applySideConfigs(configs);
        helper.runAfterDelay(70, () -> {
            var got = beside.inputTanks().get(0);
            helper.assertTrue(got.getFluid().isSame(net.minecraft.world.level.material.Fluids.LAVA) && got.getAmount() > 0, "lava pushed into the next Crafter: " + got);
            helper.assertTrue(andesite(liquefier) < 8, "andesite melted for it");
            helper.succeed();
        });
    }

    private static int andesite(com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity crafter) {
        int count = 0;
        for (int slot = 0; slot < com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity.INPUT_SLOTS; slot++) {
            ItemStack stack = crafter.getInventory().getStackInSlot(slot);
            if (stack.is(net.minecraft.world.item.Items.ANDESITE)) count += stack.getCount();
        }
        return count;
    }

    // covers: crafter.fluids
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_energizedpower_linked_fluids", timeoutTicks = 60)
    public static void aStoneSolidifierDrawsOnLinkedTanks(GameTestHelper helper) {
        if (skip(helper, "energizedpower")) return;
        BlockPos waterAt = new BlockPos(8, 2, 4), lavaAt = new BlockPos(10, 2, 4);
        for (BlockPos at : new BlockPos[] {waterAt, lavaAt}) {
            helper.setBlock(at, com.kadikular.quantimium.init.ModBlocks.LASER_TANK.get().defaultBlockState()
                    .setValue(com.kadikular.quantimium.block.LaserTankBlock.FACING, net.minecraft.core.Direction.EAST));
        }
        var water = helper.getBlockEntity(waterAt, com.kadikular.quantimium.block.entity.LaserTankBlockEntity.class);
        var lava = helper.getBlockEntity(lavaAt, com.kadikular.quantimium.block.entity.LaserTankBlockEntity.class);
        water.tanks().set(0, new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 8_000));
        lava.tanks().set(0, new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 8_000));
        var crafter = fluidCrafter(helper, 2, "energizedpower:stone_solidifier");
        com.kadikular.quantimium.gametest.CrafterTests.grid(crafter,
                com.kadikular.quantimium.gametest.CrafterTests.link(helper, waterAt, com.kadikular.quantimium.init.ModBlocks.LASER_TANK.get()),
                com.kadikular.quantimium.gametest.CrafterTests.link(helper, lavaAt, com.kadikular.quantimium.init.ModBlocks.LASER_TANK.get()));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(com.kadikular.quantimium.gametest.CrafterTests.craftOf(crafter, net.minecraft.world.item.Items.ANDESITE) != null,
                    "andesite offered from the linked tanks");
            ItemStack made = crafter.takeGhost(com.kadikular.quantimium.gametest.CrafterTests.ghostId(crafter, net.minecraft.world.item.Items.ANDESITE));
            helper.assertTrue(made.is(net.minecraft.world.item.Items.ANDESITE), "andesite taken: " + made);
            helper.assertTrue(water.tanks().get(0).getAmount() < 8_000 && lava.tanks().get(0).getAmount() < 8_000, "water and lava drawn from them");
            helper.succeed();
        });
    }

    // covers: reactor.fluids
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_energizedpower_reactor_fluids", timeoutTicks = 40)
    public static void aReactorMakesFromHeldFluids(GameTestHelper helper) {
        if (skip(helper, "energizedpower")) return;
        clear(helper);
        HorizonCoreBlockEntity horizon = ReactorTests.buildReactor(helper, 1);
        ReactorTests.bay(helper, ReactorTests.CORE.north(2), item("energizedpower:stone_solidifier"));
        horizon.revalidate(helper.getLevel());
        horizon.getEnergyStorage().setEnergy(HorizonCoreBlockEntity.ENERGY_CAPACITY);
        BlockPos core = helper.absolutePos(ReactorTests.CORE);
        HorizonCoreBlockEntity.serverTick(helper.getLevel(), core, helper.getLevel().getBlockState(core), horizon);
        var water = com.kadikular.quantimium.recipe.FluidEntry.key(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER));
        var lava = com.kadikular.quantimium.recipe.FluidEntry.key(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.LAVA));
        horizon.take(water, 4_000);
        horizon.take(lava, 4_000);
        var result = horizon.request(ItemResource.of(net.minecraft.world.item.Items.ANDESITE), 1);
        helper.assertTrue(result.planned(), "andesite from held water and lava: " + result.problem());
        helper.assertTrue(horizon.getLedger().count(water) < 4_000 && horizon.getLedger().count(lava) < 4_000, "water and lava used");
        helper.succeed();
    }

    // covers: crafter.fluids
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_productivebees_bottler", timeoutTicks = 60)
    public static void theBottlerInTheCrafter(GameTestHelper helper) {
        if (skip(helper, "productivebees")) return;
        var honey = BuiltInRegistries.FLUID.getValue(Identifier.parse("productivebees:honey"));
        var bottler = fluidCrafter(helper, 2, "productivebees:bottler", new net.neoforged.neoforge.fluids.FluidStack(honey, 4_000));
        com.kadikular.quantimium.gametest.CrafterTests.grid(bottler, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 4));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(com.kadikular.quantimium.gametest.CrafterTests.craftOf(bottler, net.minecraft.world.item.Items.HONEY_BOTTLE) != null, "honey bottled");
            helper.succeed();
        });
    }

    /** A Quantum Crafter at {@code x} with {@code catalyst}, its input tanks holding {@code fluids}. */
    private static com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity fluidCrafter(GameTestHelper helper, int x, String catalyst,
                                                                                                  net.neoforged.neoforge.fluids.FluidStack... fluids) {
        var crafter = com.kadikular.quantimium.gametest.CrafterTests.crafter(helper, new BlockPos(x, 2, 4), item(catalyst));
        for (var fluid : fluids) crafter.inputTanks().fill(fluid, false);
        return crafter;
    }

    // covers: compat.partner_machines
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_ftbic", timeoutTicks = 40)
    public static void ftbIndustrialContraptionsMachines(GameTestHelper helper) {
        if (skip(helper, "ftbic")) return;
        makes(helper, "ftbic:macerator", "ftbic:mixed_metal_blend");
        makes(helper, "ftbic:advanced_macerator", "ftbic:mixed_metal_blend");
        makes(helper, "ftbic:compressor", "minecraft:blaze_rod");
        makes(helper, "ftbic:centrifuge", "minecraft:magenta_dye");
        makes(helper, "ftbic:alloy_smelter", "ftbic:advanced_alloy");
        makes(helper, "ftbic:powered_furnace", "minecraft:glass");
        makes(helper, "ftbic:iron_furnace", "minecraft:glass");
        // Only what a macerator surely gives counts: resin that only might come off a log isn't offered.
        helper.assertTrue(shapes(helper, "ftbic:macerator").stream().noneMatch(s -> s.id().getPath().endsWith("sticky_resin_from_log")),
                "resin only might come off a log");
        // Each machine its own recipes: a macerator doesn't compress.
        helper.assertTrue(shapes(helper, "ftbic:macerator").stream().noneMatch(s -> s.id().getPath().startsWith("compressing/")),
                "a macerator doesn't compress");
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

    // covers: anomalite.host
    @GameTest(template = TestSupport.FLOOR_17, templateNamespace = NS, batch = "partner_crystal_host", timeoutTicks = 60)
    public static void aCrystalFeedsOnAFillOnlyMachine(GameTestHelper helper) {
        // Energized Power's machines let power in but never out: a crystal on one still feeds, taking nothing.
        if (skip(helper, "energizedpower")) return;
        com.kadikular.quantimium.gametest.TestSupport.clearField(helper);
        net.minecraft.core.BlockPos machine = new net.minecraft.core.BlockPos(8, 2, 8);
        helper.setBlock(machine, BuiltInRegistries.BLOCK.getValue(Identifier.parse("energizedpower:pulverizer")));
        var energy = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, helper.absolutePos(machine), null);
        helper.assertTrue(energy != null, "it takes power");
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            energy.insert(1_000, transaction);
            transaction.commit();
        }
        helper.setBlock(machine.above(), com.kadikular.quantimium.init.ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.FACING, net.minecraft.core.Direction.UP)
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.AGE, 0));
        helper.runAfterDelay(40, () -> {
            var crystal = helper.getBlockEntity(machine.above(), com.kadikular.quantimium.block.entity.AnomaliteCrystalBlockEntity.class);
            var host = com.kadikular.quantimium.flux.AnomaliteEnergy.find(helper.getLevel(), helper.absolutePos(machine));
            helper.assertTrue(crystal.wasRecentlyFed(helper.getLevel().getGameTime()), "it feeds on the machine's charge: host "
                    + (host == null ? "none" : host.storedFe() + " FE, drainable " + host.canExtract()) + ", stored " + energy.getAmountAsLong());
            helper.assertTrue(energy.getAmountAsLong() > 0, "and leaves it be");
            helper.succeed();
        });
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
