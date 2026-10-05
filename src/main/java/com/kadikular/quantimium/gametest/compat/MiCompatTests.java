package com.kadikular.quantimium.gametest.compat;

import com.kadikular.quantimium.gametest.GameTest;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.simulation.PhantomMirrorEngine;
import com.kadikular.quantimium.block.entity.simulation.SimulatorFluidTanks;
import com.kadikular.quantimium.gametest.CrafterTests;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.recipe.CraftEnergy;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import com.kadikular.quantimium.unrealised.Form;
import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.unrealised.MatterSteps;
import com.kadikular.quantimium.unrealised.PackOres;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

import static com.kadikular.quantimium.gametest.compat.CompatGameTests.block;
import static com.kadikular.quantimium.gametest.compat.CompatGameTests.fluid;
import static com.kadikular.quantimium.gametest.compat.CompatGameTests.item;

/**
 * Modern Industrialization. The mod reads MI's recipes and prices its EU through reflection and
 * MI's own config, and powers MI machines through MI's energy classes, so an MI update can break
 * any of it without a compile error. These run real MI machines and recipes to catch that.
 *
 * <p>The multiblock tests pin down today's behaviour rather than a settled design: the Crafter runs
 * an Electric Blast Furnace's recipes without its structure, and the Simulator cannot run an EBF at
 * all. Registered only with MI loaded; see {@link CompatGameTests}.
 */
public final class MiCompatTests {

    private static final String NS = Quantimium.MODID;
    private static final BlockPos AT = new BlockPos(4, 2, 4);

    private MiCompatTests() {}

    // ---- The Crafter ----

    // covers: compat.mi.crafter_recipes
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_crafter_macerator", timeoutTicks = 60)
    public static void maceratorRecipesArePricedFromTheirEu(GameTestHelper helper) {
        TestSupport.setField(helper, AT, 0.0, 0.0);
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("modern_industrialization:electric_macerator"));
        CrafterTests.grid(crafter, new ItemStack(Items.RAW_IRON, 8));
        helper.runAfterDelay(5, () -> {
            ResolvedCraft dust = CrafterTests.craftOf(crafter, item("modern_industrialization:iron_dust"));
            helper.assertTrue(dust != null, "an electric macerator should grind raw iron to dust");
            // raw_metal.json: 2 EU/t for 100 ticks.
            helper.assertValueEqual(dust.feCost() / dust.batchSize(), euCost(2, 100), "FE per grind");
            // The second dust only comes half the time, so the crafter leaves it out.
            helper.assertValueEqual(dust.outputs().size(), 1, "outputs per grind");
            helper.assertValueEqual(dust.outputs().getFirst().getCount() / dust.batchSize(), 1, "dust per grind");
            helper.succeed();
        });
    }

    // covers: compat.mi.crafter_multiblock
    // covers: unrealised.forms
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_matter_forms", timeoutTicks = 20)
    public static void maceratedThenSmeltedMatterMakesIngotsFromItsDust(GameTestHelper helper) {
        // Every form a history could make is kept; where a step reaches an item again, the latest route wins,
        // so macerated-then-smelted iron gives the ingots of its dust, not of its raw ore.
        MatterHistory history = new MatterHistory(List.of(Identifier.parse("modern_industrialization:macerator"),
                MatterSteps.idOf(RecipeType.SMELTING)));
        PackOres.Ore iron = PackOres.ores().stream().filter(ore -> ore.tag().location().getPath().equals("ores/iron"))
                .findFirst().orElseThrow();
        List<Form> forms = MatterSteps.of(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), history, iron);
        Form dust = forms.stream().filter(form -> form.item().is(item("modern_industrialization:iron_dust"))).findFirst().orElse(null);
        Form ingot = forms.stream().filter(form -> form.item().is(Items.IRON_INGOT)).findFirst().orElse(null);
        helper.assertTrue(forms.stream().anyMatch(form -> form.item().is(Items.RAW_IRON)), "raw iron is still a form: " + forms);
        helper.assertTrue(dust != null, "iron dust is a form: " + forms);
        helper.assertTrue(ingot != null, "iron ingots are a form: " + forms);
        helper.assertTrue(Math.abs(ingot.amount() - dust.amount()) < 1e-9, "ingots, one per dust: " + ingot + " " + dust);
        helper.succeed();
    }

    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_crafter_ebf", timeoutTicks = 60)
    public static void anEbfCatalystNeedsNoStructure(GameTestHelper helper) {
        // Off by default since the Fold Chamber; a pack can still turn it on.
        boolean before = Config.STRUCTURELESS_MULTIBLOCKS.get();
        Config.STRUCTURELESS_MULTIBLOCKS.set(true);
        TestSupport.setField(helper, AT, 0.0, 0.0);
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("modern_industrialization:electric_blast_furnace"));
        CrafterTests.grid(crafter, new ItemStack(item("modern_industrialization:uncooked_steel_dust"), 4));
        helper.runAfterDelay(5, () -> {
            Config.STRUCTURELESS_MULTIBLOCKS.set(before);
            ResolvedCraft steel = CrafterTests.craftOf(crafter, item("modern_industrialization:steel_ingot"));
            helper.assertTrue(steel != null, "with the switch on, the controller alone should make steel");
            // blast_furnace/steel.json: 2 EU/t for 600 ticks.
            helper.assertValueEqual(steel.feCost() / steel.batchSize(), euCost(2, 600), "FE per steel ingot");
            helper.succeed();
        });
    }

    // covers: compat.mi.crafter_structureless_switch
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_crafter_ebf_switch", timeoutTicks = 60)
    public static void packsCanRequireTheStructure(GameTestHelper helper) {
        // With structurelessMultiblocks off, a multiblock's controller is refused as a catalyst.
        boolean before = Config.STRUCTURELESS_MULTIBLOCKS.get();
        Config.STRUCTURELESS_MULTIBLOCKS.set(false);
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("modern_industrialization:electric_blast_furnace"));
        CrafterTests.grid(crafter, new ItemStack(item("modern_industrialization:uncooked_steel_dust"), 4));
        helper.runAfterDelay(5, () -> {
            Config.STRUCTURELESS_MULTIBLOCKS.set(before);
            helper.assertValueEqual(crafter.getStatusCode(), QuantumCrafterBlockEntity.STATUS_DENIED, "an EBF controller alone");
            helper.succeed();
        });
    }

    // covers: compat.mi.crafter_fluids
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_crafter_fluids", timeoutTicks = 60)
    public static void recipesWithFluidInputsAreLeftOut(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("modern_industrialization:chemical_reactor"));
        CrafterTests.grid(crafter, new ItemStack(Items.COPPER_BLOCK, 4), new ItemStack(item("modern_industrialization:wax"), 4));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(CrafterTests.craftOf(crafter, Items.WAXED_COPPER_BLOCK) != null,
                    "waxing (items only) should be offered");
            // Oxidising copper needs oxygen; the crafter has no tanks, so it cannot offer it.
            helper.assertTrue(CrafterTests.craftOf(crafter, Items.EXPOSED_COPPER) == null,
                    "oxidation (needs oxygen) should not be offered");
            helper.succeed();
        });
    }

    // covers: compat.mi.crafter_gate
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_crafter_gate", timeoutTicks = 60)
    public static void theForgeHammerIsBlacklistedAndBoilersMakeNothing(GameTestHelper helper) {
        QuantumCrafterBlockEntity hammer = CrafterTests.crafter(helper, AT, item("modern_industrialization:forge_hammer"));
        CrafterTests.grid(hammer, new ItemStack(Items.RAW_IRON, 4));
        QuantumCrafterBlockEntity boiler = CrafterTests.crafter(helper, AT.east(2), item("modern_industrialization:bronze_boiler"));
        CrafterTests.grid(boiler, new ItemStack(Items.COAL, 4));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(hammer.getStatusCode(), QuantumCrafterBlockEntity.STATUS_DENIED, "forge hammer");
            // Whitelisted, but a boiler has no recipes of its own to make instantly.
            helper.assertValueEqual(boiler.getStatusCode(), QuantumCrafterBlockEntity.STATUS_NO_RECIPE, "bronze boiler");
            helper.succeed();
        });
    }

    // ---- The Simulator ----

    // covers: compat.mi.simulator_power, simulator.power_ports
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_simulator", timeoutTicks = 1200)
    public static void anElectricMaceratorIsPoweredThroughItsEnergyPort(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "modern_industrialization:electric_macerator");
        simulator.setBatchSize(2);
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 16));
        int before = simulator.getEnergyStorage().getEnergyStored();
        // It only grinds if the simulator's FE reaches it as EU.
        helper.succeedWhen(() -> {
            helper.assertTrue(outputs(simulator, item("modern_industrialization:iron_dust")) > 0, "no dust yet");
            helper.assertTrue(simulator.getEnergyStorage().getEnergyStored() < before, "it should have paid for the EU");
        });
    }

    // covers: compat.mi.simulator_steam
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_simulator", timeoutTicks = 3600)
    public static void aBoilerTurnsWaterIntoSteam(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "modern_industrialization:bronze_boiler");
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.COAL, 32));
        simulator.getFluidTanks().setFluid(0, new FluidStack(Fluids.WATER, 16_000));
        // A boiler heats up before it boils, so this waits on the steam, not on a tick count.
        helper.succeedWhen(() -> {
            helper.assertTrue(outputFluid(simulator, fluid("modern_industrialization:steam")) > 0, "no steam yet");
            helper.assertTrue(simulator.getFluidTanks().getFluid(0).getAmount() < 16_000, "water should be used");
        });
    }

    // covers: compat.mi.simulator_fluid_in
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_simulator", timeoutTicks = 1200)
    public static void aChemicalReactorUsesItsFluid(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "modern_industrialization:chemical_reactor");
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.COPPER_BLOCK, 8));
        simulator.getFluidTanks().setFluid(0, new FluidStack(fluid("modern_industrialization:oxygen"), 4_000));
        // oxidation/copper_block.json: a copper block and 100 mB of oxygen make exposed copper.
        helper.succeedWhen(() -> {
            helper.assertTrue(outputs(simulator, Items.EXPOSED_COPPER) > 0, "no exposed copper yet");
            helper.assertTrue(simulator.getFluidTanks().getFluid(0).getAmount() < 4_000, "oxygen should be used");
        });
    }

    // covers: compat.mi.simulator_fluid_out
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_simulator", timeoutTicks = 1200)
    public static void aMixerTakesAndMakesFluids(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "modern_industrialization:electric_mixer");
        simulator.getInventory().setStackInSlot(0, new ItemStack(Items.SUGAR, 32));
        simulator.getFluidTanks().setFluid(0, new FluidStack(Fluids.WATER, 8_000));
        // oil/mixer/sugar_solution.json: 8 sugar and 1,000 mB of water make 1,000 mB of sugar solution.
        helper.succeedWhen(() -> {
            helper.assertTrue(outputFluid(simulator, fluid("modern_industrialization:sugar_solution")) > 0,
                    "no sugar solution yet");
            helper.assertTrue(simulator.getFluidTanks().getFluid(0).getAmount() < 8_000, "water should be used");
        });
    }

    // covers: compat.mi.simulator_fluid_out
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_simulator", timeoutTicks = 2000)
    public static void anElectrolyzerSplitsWater(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "modern_industrialization:electrolyzer");
        simulator.getFluidTanks().setFluid(0, new FluidStack(Fluids.WATER, 12_000));
        // electrolyzer/water.json: 3,000 mB of water make 2,000 mB of hydrogen and 1,000 mB of oxygen.
        helper.succeedWhen(() -> {
            helper.assertTrue(outputFluid(simulator, fluid("modern_industrialization:hydrogen")) > 0, "no hydrogen yet");
            helper.assertTrue(outputFluid(simulator, fluid("modern_industrialization:oxygen")) > 0, "no oxygen yet");
        });
    }

    // covers: compat.mi.simulator_multiblock
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "mi_simulator", timeoutTicks = 600)
    public static void anEbfControllerAloneMakesNothing(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "modern_industrialization:electric_blast_furnace");
        simulator.getInventory().setStackInSlot(0, new ItemStack(item("modern_industrialization:uncooked_steel_dust"), 8));
        // Taken out of the world the controller has no structure, so it never forms. A simulator
        // built around a whole multiblock is an idea, not a feature; until then, nothing is made.
        helper.runAfterDelay(500, () -> {
            helper.assertValueEqual(outputs(simulator, item("modern_industrialization:steel_ingot")), 0, "steel made");
            helper.assertTrue(simulator.getStatusCode() != PhantomMirrorEngine.STATUS_WORKING,
                    "an unformed EBF should not report working");
            helper.succeed();
        });
    }

    // ---- helpers ----

    /** What the crafter charges for an MI recipe: total EU, at MI's FE-per-EU, times the multiplier. */
    private static int euCost(int euPerTick, int ticks) {
        return euPerTick * ticks * CraftEnergy.fePerEu() * Config.instantCraftMultiplier();
    }

    private static QuantumSimulatorBlockEntity engaged(GameTestHelper helper, String machine) {
        TestSupport.track(helper);
        helper.setBlock(AT, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(AT, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        helper.setBlock(AT.above(), block(machine));
        simulator.toggleEngage(null);
        helper.assertTrue(simulator.isEngaged(), "the field should take the " + machine);
        return simulator;
    }

    private static int outputs(QuantumSimulatorBlockEntity simulator, Item item) {
        int count = 0;
        for (int slot = QuantumSimulatorBlockEntity.OUTPUT_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END; slot++) {
            ItemStack stack = simulator.getInventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int outputFluid(QuantumSimulatorBlockEntity simulator, Fluid fluid) {
        int amount = 0;
        for (int i = 0; i < SimulatorFluidTanks.OUTPUT_TANKS; i++) {
            FluidStack stack = simulator.getFluidTanks().getFluid(SimulatorFluidTanks.outputIndex(i));
            if (stack.is(fluid)) amount += stack.getAmount();
        }
        return amount;
    }
}
