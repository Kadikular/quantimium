package com.kadikular.quantimium.gametest.compat;

import com.kadikular.quantimium.gametest.GameTest;
import com.google.gson.JsonParser;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.gametest.CrafterTests;
import com.kadikular.quantimium.recipe.HnnFabricatorFamily;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import com.kadikular.quantimium.gametest.TestSupport;
import com.kadikular.quantimium.init.ModBlocks;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

import static com.kadikular.quantimium.gametest.compat.CompatGameTests.block;
import static com.kadikular.quantimium.gametest.compat.CompatGameTests.item;

/**
 * Hostile Neural Networks. HNN has no recipe types (its data models are a registry of their own),
 * so it works through the Simulator only: a Simulation Chamber turning prediction matrices into
 * predictions, and a Loot Fabricator turning predictions into drops. Items are built from JSON and
 * the fabricator's choice set by reflection, so there is no compile-time dependency on HNN.
 * Registered only with HNN loaded; see {@link CompatGameTests}.
 */
public final class HnnCompatTests {

    private static final String NS = Quantimium.MODID;
    private static final BlockPos AT = new BlockPos(4, 2, 4);
    /** A self-aware model: always predicts correctly and gains no more data, so every run is alike. */
    private static final int SELF_AWARE_DATA = 1254;

    private HnnCompatTests() {}

    // covers: compat.hnn.sim_chamber, simulator.learn_inputs
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "hnn_simulator", timeoutTicks = 1600)
    public static void aSimChamberMakesPredictions(GameTestHelper helper) {
        QuantumSimulatorBlockEntity simulator = engaged(helper, "hostilenetworks:sim_chamber");
        simulator.setBatchSize(2);
        // The model first: the chamber only takes a matrix once it holds one.
        simulator.getInventory().setStackInSlot(0, stack(helper,
                "{\"id\":\"hostilenetworks:data_model\",\"count\":1,\"components\":{"
                        + "\"hostilenetworks:data_model\":\"hostilenetworks:chicken\","
                        + "\"hostilenetworks:data\":" + SELF_AWARE_DATA + "}}"));
        simulator.getInventory().setStackInSlot(1, new ItemStack(item("hostilenetworks:prediction_matrix"), 16));
        helper.succeedWhen(() -> {
            helper.assertTrue(outputs(simulator, item("hostilenetworks:overworld_prediction")) > 0,
                    "no base drop yet, status " + simulator.getStatusCode());
            helper.assertTrue(outputs(simulator, item("hostilenetworks:prediction")) > 0, "no chicken prediction yet");
            helper.assertTrue(simulator.getInventory().getStackInSlot(0).is(item("hostilenetworks:data_model")),
                    "the data model is not used up");
        });
    }

    // covers: compat.hnn.loot_fabricator
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "hnn_simulator", timeoutTicks = 1600)
    public static void aLootFabricatorMakesItsChosenDrop(GameTestHelper helper) {
        TestSupport.track(helper);
        helper.setBlock(AT, ModBlocks.QUANTUM_SIMULATOR.get());
        QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(AT, QuantumSimulatorBlockEntity.class);
        TestSupport.fill(simulator.getEnergyStorage());
        helper.setBlock(AT.above(), block("hostilenetworks:loot_fabricator"));
        ItemStack prediction = stack(helper, "{\"id\":\"hostilenetworks:prediction\",\"count\":8,\"components\":{"
                + "\"hostilenetworks:data_model\":\"hostilenetworks:chicken\"}}");
        // The chicken model's drops are raw chicken (0) and feathers (1); choose feathers, as the GUI would.
        chooseDrop(TestSupport.blockEntity(helper, AT.above()), prediction, 1);
        simulator.toggleEngage(null);
        helper.assertTrue(simulator.isEngaged(), "the field should take the loot fabricator");
        simulator.getInventory().setStackInSlot(0, prediction);
        helper.succeedWhen(() -> {
            helper.assertTrue(outputs(simulator, Items.FEATHER) > 0, "no feathers yet, status " + simulator.getStatusCode());
            helper.assertValueEqual(outputs(simulator, Items.CHICKEN), 0, "raw chicken was not chosen");
        });
    }

    // covers: compat.hnn.crafter_fabricator
    @GameTest(template = TestSupport.FLOOR_9, templateNamespace = NS, batch = "hnn_crafter", timeoutTicks = 60)
    public static void theCrafterOffersEveryFabricatorDrop(GameTestHelper helper) {
        TestSupport.setField(helper, AT, 0.0, 0.0);
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, item("hostilenetworks:loot_fabricator"));
        CrafterTests.grid(crafter, stack(helper, "{\"id\":\"hostilenetworks:prediction\",\"count\":4,\"components\":{"
                + "\"hostilenetworks:data_model\":\"hostilenetworks:chicken\"}}"));
        QuantumCrafterBlockEntity chamber = CrafterTests.crafter(helper, AT.east(2), item("hostilenetworks:sim_chamber"));
        helper.runAfterDelay(5, () -> {
            // The chicken model's two drops, each its own ghost: 32 raw chicken, 24 feathers.
            ResolvedCraft chicken = CrafterTests.craftOf(crafter, Items.CHICKEN);
            ResolvedCraft feathers = CrafterTests.craftOf(crafter, Items.FEATHER);
            helper.assertTrue(chicken != null && feathers != null, "both drops should be offered");
            // 256 FE/t for the Fabricator's 60 ticks, times the multiplier.
            int cost = HnnFabricatorFamily.fabFePerTick() * HnnFabricatorFamily.FAB_TICKS * Config.instantCraftMultiplier();
            helper.assertValueEqual(feathers.feCost() / feathers.batchSize(), cost, "FE per fabrication");
            ItemStack taken = crafter.getAutomationItemHandler(null)
                    .extractItem(CrafterTests.ghostSlot(crafter, Items.FEATHER), 24, false);
            helper.assertValueEqual(taken.getCount(), 24, "feathers from one prediction");
            helper.assertValueEqual(crafter.getInventory().getStackInSlot(0).getCount(), 3, "predictions left");
            helper.assertValueEqual(chamber.getStatusCode(), QuantumCrafterBlockEntity.STATUS_DENIED,
                    "the Sim Chamber is not a catalyst");
            helper.succeed();
        });
    }

    // ---- helpers ----

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

    /** An item with HNN's components, read the way a datapack would write it. */
    private static ItemStack stack(GameTestHelper helper, String json) {
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                JsonParser.parseString(json)).getOrThrow();
    }

    /** {@code LootFabTileEntity.setFixedDrop(model, index)}, with the model taken from a prediction. */
    private static void chooseDrop(BlockEntity fabricator, ItemStack prediction, int index) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(
                Identifier.parse("hostilenetworks:data_model"));
        Object model = prediction.get(type);
        try {
            for (Method method : fabricator.getClass().getMethods()) {
                if (method.getName().equals("setFixedDrop") && method.getParameterCount() == 2) {
                    method.invoke(fabricator, model, index);
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not choose the fabricator's drop", e);
        }
        throw new AssertionError("LootFabTileEntity.setFixedDrop not found; has HNN changed?");
    }

    private static int outputs(QuantumSimulatorBlockEntity simulator, Item item) {
        int count = 0;
        for (int slot = QuantumSimulatorBlockEntity.OUTPUT_START; slot <= QuantumSimulatorBlockEntity.OUTPUT_END; slot++) {
            ItemStack stack = simulator.getInventory().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
}
