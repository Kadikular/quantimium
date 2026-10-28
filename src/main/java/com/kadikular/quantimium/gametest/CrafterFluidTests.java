package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserTankBlock;
import com.kadikular.quantimium.block.entity.LaserTankBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.recipe.GenericItemFamily;
import com.kadikular.quantimium.recipe.IngredientPool;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Fluids in the Quantum Crafter: a recipe taking a fluid draws it from the Crafter's input tanks or a tank a Tesseract
 * in its grid is bound to; one making a fluid fills its output tanks. Recipes here are stand-ins, as another mod's
 * machine keeps them (a fluid ingredient field in, a fluid stack field out), run through the matching a real one gets.
 */
public final class CrafterFluidTests {

    private CrafterFluidTests() {}

    /** Stone and some fluid make cobblestone, and some lava besides. */
    static final class Soak implements Recipe<SingleRecipeInput> {
        final SizedFluidIngredient fluidInput;
        final FluidStackTemplate fluidOutput;

        Soak(SizedFluidIngredient fluidInput, FluidStackTemplate fluidOutput) {
            this.fluidInput = fluidInput;
            this.fluidOutput = fluidOutput;
        }

        @Override
        public boolean matches(SingleRecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(SingleRecipeInput input) {
            return new ItemStack(Items.COBBLESTONE);
        }

        @Override
        public boolean showNotification() {
            return false;
        }

        @Override
        public String group() {
            return "";
        }

        @Override
        public PlacementInfo placementInfo() {
            return PlacementInfo.create(Ingredient.of(Items.STONE));
        }

        @Override
        public List<RecipeDisplay> display() {
            return List.of(new ShapelessCraftingRecipeDisplay(List.of(), new SlotDisplay.ItemSlotDisplay(Items.COBBLESTONE), SlotDisplay.Empty.INSTANCE));
        }

        @Override
        public boolean isSpecial() {
            return true;
        }

        @Override
        public RecipeBookCategory recipeBookCategory() {
            return RecipeBookCategories.CRAFTING_MISC;
        }

        @Override
        public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
            return RecipeType.BLASTING;
        }
    }

    private static RecipeHolder<Soak> soak(net.minecraft.world.level.material.Fluid in, int amount) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("quantimium", "test/soak")),
                new Soak(SizedFluidIngredient.of(in, amount), new FluidStackTemplate(Fluids.LAVA, 100)));
    }

    private static Optional<ResolvedCraft> match(GameTestHelper helper, QuantumCrafterBlockEntity crafter, RecipeHolder<Soak> recipe) {
        IngredientPool pool = IngredientPool.build(helper.getLevel(), crafter.getBlockPos(), crafter.getInventory(), QuantumCrafterBlockEntity.INPUT_SLOTS);
        return GenericItemFamily.match(helper.getLevel().registryAccess(), recipe, pool).map(craft -> craft.withRuns(1));
    }

    private static int stone(QuantumCrafterBlockEntity crafter) {
        int count = 0;
        for (int slot = 0; slot < QuantumCrafterBlockEntity.INPUT_SLOTS; slot++) {
            if (crafter.getInventory().getStackInSlot(slot).is(Items.STONE)) count += crafter.getInventory().getStackInSlot(slot).getCount();
        }
        return count;
    }

    // covers: crafter.fluids
    @com.kadikular.quantimium.gametest.GameTest(template = TestSupport.FLOOR_17, batch = "crafter_fluids", timeoutTicks = 20)
    public static void aRecipeTakesFluidFromItsInputTanksAndFillsItsOutputTanks(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(4, 2, 4), Items.BLAST_FURNACE);
        CrafterTests.grid(crafter, new ItemStack(Items.STONE, 4));
        helper.assertTrue(match(helper, crafter, soak(Fluids.WATER, 250)).isEmpty(), "no water, no craft");

        crafter.inputTanks().fill(new FluidStack(Fluids.WATER, 1_000), false);
        ResolvedCraft craft = match(helper, crafter, soak(Fluids.WATER, 250)).orElseThrow(() -> new AssertionError("matched with water in its tank"));
        ItemStack made = crafter.commitCraft(craft, 0);
        helper.assertTrue(made.is(Items.COBBLESTONE), "the cobblestone handed over");
        helper.assertValueEqual(stone(crafter), 3, "a stone used");
        helper.assertValueEqual(crafter.inputTanks().get(0).getAmount(), 750, "250 mB of water used");
        helper.assertTrue(crafter.outputTanks().get(0).getFluid().isSame(Fluids.LAVA) && crafter.outputTanks().get(0).getAmount() == 100,
                "100 mB of lava in its output tank");

        // A pipe fills its input tanks and drains its output tanks.
        var pipe = net.neoforged.neoforge.fluids.capability.IFluidHandler.of(crafter.fluidAutomation());
        helper.assertValueEqual(pipe.fill(new FluidStack(Fluids.WATER, 500), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE),
                500, "a pipe fills the input tanks");
        helper.assertValueEqual(crafter.inputTanks().get(0).getAmount(), 1_250, "into the water's tank");
        FluidStack drained = pipe.drain(1_000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.getFluid().isSame(Fluids.LAVA) && drained.getAmount() == 100, "and drains the output tanks, not the inputs");

        // A bucket clicked on the lava among its outputs fills from the output tanks.
        crafter.outputTanks().fill(new FluidStack(Fluids.LAVA, 1_500), false);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var menu = new com.kadikular.quantimium.menu.QuantumCrafterMenu(1, player.getInventory(), crafter, crafter.getData());
        menu.setCarried(new ItemStack(Items.BUCKET));
        menu.clickMenuButton(player, com.kadikular.quantimium.menu.QuantumCrafterMenu.BUTTON_FLUID);
        helper.assertTrue(menu.getCarried().is(Items.LAVA_BUCKET), "a lava bucket in hand");
        helper.assertValueEqual(crafter.outputTanks().get(0).getAmount(), 500, "a bucket's worth out of the output tank");
        helper.succeed();
    }

    // covers: crafter.fluids
    @com.kadikular.quantimium.gametest.GameTest(template = TestSupport.FLOOR_17, batch = "crafter_fluids", timeoutTicks = 20)
    public static void aRecipeTakesFluidFromATankATesseractIsBoundTo(GameTestHelper helper) {
        BlockPos tankAt = new BlockPos(8, 2, 4);
        helper.setBlock(tankAt, ModBlocks.LASER_TANK.get().defaultBlockState().setValue(LaserTankBlock.FACING, Direction.EAST));
        LaserTankBlockEntity tank = helper.getBlockEntity(tankAt, LaserTankBlockEntity.class);
        tank.tanks().set(0, new FluidStack(Fluids.LAVA, 2_000));
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(4, 2, 4), Items.BLAST_FURNACE);
        CrafterTests.grid(crafter, new ItemStack(Items.STONE, 4), CrafterTests.link(helper, tankAt, ModBlocks.LASER_TANK.get()));
        ResolvedCraft craft = match(helper, crafter, soak(Fluids.LAVA, 500)).orElseThrow(() -> new AssertionError("matched with lava through the link"));
        helper.assertTrue(crafter.commitCraft(craft, 0).is(Items.COBBLESTONE), "made");
        helper.assertValueEqual(tank.tanks().get(0).getAmount(), 1_500, "500 mB drawn from the linked tank");
        helper.succeed();
    }

    // covers: crafter.fluids
    @com.kadikular.quantimium.gametest.GameTest(template = TestSupport.FLOOR_17, batch = "crafter_fluids", timeoutTicks = 20)
    public static void aCraftWaitsWhileItsOutputTanksAreFull(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(4, 2, 4), Items.BLAST_FURNACE);
        CrafterTests.grid(crafter, new ItemStack(Items.STONE, 4));
        crafter.inputTanks().fill(new FluidStack(Fluids.WATER, 1_000), false);
        crafter.outputTanks().set(0, new FluidStack(Fluids.LAVA, QuantumCrafterBlockEntity.TANK_CAPACITY));
        crafter.outputTanks().set(1, new FluidStack(Fluids.WATER, QuantumCrafterBlockEntity.TANK_CAPACITY));
        ResolvedCraft craft = match(helper, crafter, soak(Fluids.WATER, 250)).orElseThrow();
        helper.assertTrue(crafter.commitCraft(craft, 0).isEmpty(), "nothing made");
        helper.assertValueEqual(stone(crafter), 4, "no stone used");
        helper.assertValueEqual(crafter.inputTanks().get(0).getAmount(), 1_000, "no water used");
        helper.succeed();
    }
}
