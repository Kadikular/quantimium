package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.MaterialiserBlockEntity;
import com.kadikular.quantimium.block.entity.ObservationChamberBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import com.kadikular.quantimium.unrealised.Form;
import com.kadikular.quantimium.unrealised.Materialising;
import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.unrealised.MatterSteps;
import com.kadikular.quantimium.unrealised.PackOres;
import com.kadikular.quantimium.unrealised.Rarity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Unrealised 2.0 (game plan E): Matter collapses into the pack's own ores, by rarity, rarer in a
 * hotter field. See wiki: items/unrealised_matter.
 */
public final class UnrealisedTests {

    private UnrealisedTests() {}

    // covers: unrealised.pack_ores
    @GameTest(template = TestSupport.FLOOR_9)
    public static void matterCanBeAnyOreInThePackByItsRarity(GameTestHelper helper) {
        helper.assertTrue(rarityOf(helper, "iron") == Rarity.COMMON, "iron is common");
        helper.assertTrue(rarityOf(helper, "redstone") == Rarity.UNCOMMON, "redstone is uncommon");
        helper.assertTrue(rarityOf(helper, "diamond") == Rarity.RARE, "diamond is rare");
        helper.assertTrue(rarityOf(helper, "netherite_scrap") == Rarity.VERY_RARE, "netherite scrap is very rare");
        // An ore no tier names is very rare: an unknown ore never comes out as often as coal.
        helper.assertValueEqual(Rarity.of(Blocks.STONE.defaultBlockState()), Rarity.VERY_RARE, "an untiered block");

        // Its drop is what it gives mined: raw iron, not the ore block.
        PackOres.Ore iron = ore(helper, "iron");
        List<ItemStack> drops = PackOres.drops(helper.getLevel(), iron, helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(!drops.isEmpty() && drops.getFirst().is(Items.RAW_IRON), "iron ore collapses to raw iron: " + drops);
        helper.succeed();
    }

    // covers: unrealised.excluded
    @GameTest(template = TestSupport.FLOOR_9)
    public static void matterNeverBecomesAllTheModsEndgameOres(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("allthemodium")) {
            helper.succeed();
            return;
        }
        // All the Mods' endgame ores are where its progression goes: Matter mustn't skip it.
        for (String name : List.of("allthemodium", "vibranium", "unobtainium")) {
            helper.assertTrue(PackOres.byTag(Identifier.fromNamespaceAndPath("c", "ores/" + name)) == null, name + " is never Matter");
        }
        helper.assertTrue(PackOres.ores().stream().noneMatch(ore -> ore.state().is(Rarity.EXCLUDED)), "no excluded ore at all");
        helper.assertTrue(!PackOres.ores().isEmpty(), "other ores still are");
        helper.succeed();
    }

    // covers: unrealised.band_bias
    @GameTest(template = TestSupport.FLOOR_9)
    public static void aHotterFieldTurnsUpRarerOres(GameTestHelper helper) {
        for (FluxBand band : FluxBand.values()) {
            double total = 0.0;
            for (PackOres.Ore ore : PackOres.ores()) total += PackOres.chance(ore, band);
            helper.assertTrue(Math.abs(total - 1.0) < 1e-6, "chances in " + band + " add up to one: " + total);
        }
        PackOres.Ore diamond = ore(helper, "diamond");
        PackOres.Ore scrap = ore(helper, "netherite_scrap");
        helper.assertValueEqual(PackOres.chance(diamond, FluxBand.MEDIUM), 0.0, "no rare ores below High");
        helper.assertTrue(PackOres.chance(diamond, FluxBand.HIGH) > 0.0, "rare ores from High");
        helper.assertValueEqual(PackOres.chance(scrap, FluxBand.HIGH), 0.0, "no very rare ores below Critical");
        helper.assertTrue(PackOres.chance(scrap, FluxBand.SINGULARITY) > PackOres.chance(scrap, FluxBand.CRITICAL),
                "very rare ores more often at Singularity");
        // At Low, every roll is common.
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 500; i++) {
            PackOres.Ore rolled = PackOres.roll(random, FluxBand.LOW);
            helper.assertTrue(rolled != null && rolled.rarity() == Rarity.COMMON, "a Low roll should be common: " + rolled);
        }
        helper.succeed();
    }

    // covers: unrealised.history
    @GameTest(template = TestSupport.FLOOR_9, batch = "unrealised_history", timeoutTicks = 60)
    public static void matterGoesThroughACatalystUnrealisedAndRemembersIt(GameTestHelper helper) {
        QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, new BlockPos(4, 2, 4), Items.FURNACE);
        CrafterTests.grid(crafter, new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        Identifier smelting = MatterSteps.idOf(RecipeType.SMELTING);
        helper.runAfterDelay(5, () -> {
            ResolvedCraft smelt = matterCraft(crafter);
            helper.assertTrue(smelt != null, "a furnace should offer Matter, smelted but unobserved");
            helper.assertValueEqual(MatterHistory.of(smelt.primaryOutput()).steps(), List.of(smelting), "its history");
            helper.assertValueEqual(smelt.batchSize(), 4, "all four at once");
            ItemStack taken = crafter.getAutomationItemHandler(null)
                    .extractItem(CrafterTests.ghostSlot(crafter, ModItems.UNREALISED_MATTER.get()), 4, false);
            helper.assertValueEqual(taken.getCount(), 4, "four smelted Matter taken");
            helper.assertValueEqual(MatterHistory.of(taken).steps(), List.of(smelting), "taken with its history");

            // What it could become: smelted iron is raw iron or an ingot, and smelting again adds nothing.
            PackOres.Ore iron = ore(helper, "iron");
            List<Form> forms = MatterSteps.of(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                    new MatterHistory(List.of(smelting)), iron);
            helper.assertTrue(forms.stream().anyMatch(form -> form.item().is(Items.RAW_IRON))
                    && forms.stream().anyMatch(form -> form.item().is(Items.IRON_INGOT)), "smelted iron Matter is raw iron or an ingot: " + forms);
            helper.assertTrue(!MatterSteps.lastStepAdds(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                    new MatterHistory(List.of(smelting, smelting))), "smelting twice gives no ore a new form");

            // Put back, smelted Matter isn't offered another smelting.
            crafter.getInventory().setStackInSlot(0, taken);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(matterCraft(crafter) == null, "a step no ore can take is not offered");
                helper.succeed();
            });
        });
    }

    // covers: unrealised.partial_collapse
    @GameTest(template = TestSupport.FLOOR_9, batch = "unrealised_partial", timeoutTicks = 20)
    public static void aChamberCollapsesAHistoryAsFarAsItHolds(GameTestHelper helper) {
        TestSupport.clearField(helper);
        MatterHistory smelted = new MatterHistory(List.of(MatterSteps.idOf(RecipeType.SMELTING)));
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        List<? extends Integer> before = Config.STEP_HOLD_BY_BAND.get();
        try {
            // Never holding: it always collapses early, as the raw ore, with a Trace for the broken step.
            Config.STEP_HOLD_BY_BAND.set(List.of(0, 0, 0, 0, 0));
            for (int i = 0; i < 20; i++) {
                ObservationChamberBlockEntity.Collapse collapse = ObservationChamberBlockEntity.rollCollapse(helper.getLevel(), at, smelted);
                helper.assertTrue(collapse.broke(), "a step that never holds breaks");
                helper.assertTrue(collapse.products().stream().noneMatch(stack -> stack.is(Items.IRON_INGOT)),
                        "broken early, iron is still raw: " + collapse.products());
            }
            // Always holding: it collapses into any form the history reached, ingots included, and never breaks.
            Config.STEP_HOLD_BY_BAND.set(List.of(100, 100, 100, 100, 100));
            boolean ingot = false;
            for (int i = 0; i < 300 && !ingot; i++) {
                ObservationChamberBlockEntity.Collapse collapse = ObservationChamberBlockEntity.rollCollapse(helper.getLevel(), at, smelted);
                helper.assertTrue(!collapse.broke(), "a step that always holds never breaks");
                helper.assertValueEqual(collapse.products().size(), 1, "one form");
                ingot = collapse.products().getFirst().is(Items.IRON_INGOT);
            }
            helper.assertTrue(ingot, "some collapse should give an iron ingot");
        } finally {
            Config.STEP_HOLD_BY_BAND.set(before);
        }
        helper.succeed();
    }

    // covers: unrealised.materialise
    @GameTest(template = TestSupport.FLOOR_17, batch = "unrealised_materialise", timeoutTicks = 200)
    public static void theMaterialiserMakesTheChosenOreForTraceAndFlux(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        TestSupport.track(helper);
        helper.setBlock(at, ModBlocks.MATERIALISER.get());
        MaterialiserBlockEntity materialiser = helper.getBlockEntity(at, MaterialiserBlockEntity.class);
        TestSupport.fill(materialiser.getEnergyStorage());
        materialiser.getInventory().setStackInSlot(MaterialiserBlockEntity.MATTER_SLOT, new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        materialiser.getInventory().setStackInSlot(MaterialiserBlockEntity.TRACE_SLOT, new ItemStack(ModItems.QUANTIMIUM_TRACE.get(), 4));
        materialiser.setTarget(new Materialising.Target(Identifier.parse("c:ores/iron"), Identifier.parse("minecraft:raw_iron")));
        // Cold, it can't choose at all.
        materialiser.bandGate().update(helper.getLevel(), helper.absolutePos(at));
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(materialiser.getStatusCode(), MaterialiserBlockEntity.STATUS_NEEDS_FLUX, "status in a cold field");
            // Medium: commons can be chosen. Rares can't.
            heatAround(helper, at, 500.0);
            materialiser.bandGate().update(helper.getLevel(), helper.absolutePos(at));
            helper.assertValueEqual(materialiser.bandGate().band(), FluxBand.MEDIUM, "band");
            materialiser.setTarget(new Materialising.Target(Identifier.parse("c:ores/diamond"), Identifier.parse("minecraft:diamond")));
        });
        helper.runAfterDelay(4, () -> {
            helper.assertValueEqual(materialiser.getStatusCode(), MaterialiserBlockEntity.STATUS_NEEDS_FLUX, "a rare ore at Medium");
            materialiser.setTarget(new Materialising.Target(Identifier.parse("c:ores/iron"), Identifier.parse("minecraft:raw_iron")));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(materialiser.getInventory().getStackInSlot(MaterialiserBlockEntity.MATTER_SLOT).isEmpty(), "all four made");
            int iron = 0;
            for (int slot = MaterialiserBlockEntity.OUTPUT_START; slot <= MaterialiserBlockEntity.OUTPUT_END; slot++) {
                ItemStack out = materialiser.getInventory().getStackInSlot(slot);
                if (out.is(Items.RAW_IRON)) iron += out.getCount();
            }
            helper.assertValueEqual(iron, 4, "raw iron made");
            // A common at Medium is 1.2 / 1.6 = 0.75 Trace: three for four Matter, nothing over.
            helper.assertValueEqual(materialiser.getInventory().getStackInSlot(MaterialiserBlockEntity.TRACE_SLOT).getCount(), 1,
                    "Trace left");
            helper.assertTrue(materialiser.traceCredit() < 1e-6, "no credit left over: " + materialiser.traceCredit());
            // Smelted Matter can be chosen as any form its history made: raw iron or ingots.
            MatterHistory smelted = new MatterHistory(List.of(MatterSteps.idOf(RecipeType.SMELTING)));
            List<Materialising.Option> options = Materialising.options(helper.getLevel(), helper.absolutePos(at), smelted, FluxBand.MEDIUM);
            helper.assertTrue(options.stream().anyMatch(option -> option.ore().getPath().equals("ores/iron")
                    && option.display().is(Items.IRON_INGOT) && option.allowed()), "smelted iron is offered as ingots");
            helper.assertTrue(options.stream().anyMatch(option -> option.ore().getPath().equals("ores/iron")
                    && option.display().is(Items.RAW_IRON)), "and as raw iron");
            helper.assertTrue(options.stream().noneMatch(option -> option.rarity() == Rarity.VERY_RARE.ordinal()),
                    "very rare ores are never offered");
            // Copper ore drops two to five raw copper: the Materialiser shows and makes three, every time.
            Materialising.Target copper = new Materialising.Target(Identifier.parse("c:ores/copper"),
                    Identifier.parse("minecraft:raw_copper"));
            helper.assertTrue(options.stream().anyMatch(option -> copper.is(option.ore(), option.display())
                    && option.display().getCount() == 3), "copper is offered as three raw copper: "
                    + MatterSteps.of(helper.getLevel(), helper.absolutePos(at), smelted, PackOres.byTag(copper.ore())));
            double average = PackOres.expectedDrops(helper.getLevel(), PackOres.byTag(copper.ore())).getFirst().amount();
            helper.assertTrue(Math.abs(average - 3.5) < 0.1, "copper ore averages 3.5 raw copper: " + average);
            for (int i = 0; i < 5; i++) {
                helper.assertValueEqual(Materialising.make(helper.getLevel(), helper.absolutePos(at), MatterHistory.NONE, copper)
                        .getCount(), 3, "raw copper made");
            }
            TestSupport.clearField(helper);
        });
    }

    // covers: unrealised.on_demand
    @GameTest(template = TestSupport.FLOOR_17, batch = "unrealised_on_demand", timeoutTicks = 40)
    public static void withNoOutputChosenAutomationPullsAnyForm(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        TestSupport.track(helper);
        helper.setBlock(at, ModBlocks.MATERIALISER.get());
        MaterialiserBlockEntity materialiser = helper.getBlockEntity(at, MaterialiserBlockEntity.class);
        TestSupport.fill(materialiser.getEnergyStorage());
        materialiser.getInventory().setStackInSlot(MaterialiserBlockEntity.MATTER_SLOT, new ItemStack(ModItems.UNREALISED_MATTER.get(), 4));
        materialiser.getInventory().setStackInSlot(MaterialiserBlockEntity.TRACE_SLOT, new ItemStack(ModItems.QUANTIMIUM_TRACE.get(), 4));
        IItemHandler below = materialiser.getAutomationItemHandler(Direction.DOWN);
        materialiser.bandGate().update(helper.getLevel(), helper.absolutePos(at));
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(below.getSlots(), MaterialiserBlockEntity.TOTAL_SLOTS, "cold, it offers nothing");
            heatAround(helper, at, 500.0);
        });
        // The band is read once a second; the catalogue follows it.
        helper.runAfterDelay(24, () -> {
            helper.assertValueEqual(materialiser.getStatusCode(), MaterialiserBlockEntity.STATUS_ON_DEMAND, "status");
            int copper = -1;
            for (int slot = MaterialiserBlockEntity.TOTAL_SLOTS; slot < below.getSlots(); slot++) {
                if (below.getStackInSlot(slot).is(Items.RAW_COPPER)) copper = slot;
            }
            helper.assertTrue(copper >= 0, "raw copper is offered on demand");
            // A pipe asking how much it could take (a simulated pull, through the capability) spends nothing.
            IItemHandler piped = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                    helper.absolutePos(at), Direction.DOWN));
            int energy = materialiser.getEnergyStorage().getEnergyStored();
            helper.assertValueEqual(piped.extractItem(copper, 2, true).getCount(), 2, "raw copper a pipe is offered");
            helper.assertValueEqual(materialiser.getEnergyStorage().getEnergyStored(), energy, "FE after a pipe only asked");
            helper.assertValueEqual(materialiser.getInventory().getStackInSlot(MaterialiserBlockEntity.MATTER_SLOT).getCount(), 4,
                    "Matter after a pipe only asked");
            // One Matter makes three: a pull for two gets two, and the third waits in the outputs.
            ItemStack pulled = below.extractItem(copper, 2, false);
            helper.assertTrue(pulled.is(Items.RAW_COPPER) && pulled.getCount() == 2, "pulled: " + pulled);
            helper.assertValueEqual(materialiser.getInventory().getStackInSlot(MaterialiserBlockEntity.MATTER_SLOT).getCount(), 3,
                    "one Matter used");
            helper.assertTrue(materialiser.getInventory().getStackInSlot(MaterialiserBlockEntity.OUTPUT_START).is(Items.RAW_COPPER),
                    "the third raw copper waits in the outputs");
            // With an output chosen, automation sees only its own slots.
            materialiser.setTarget(new Materialising.Target(Identifier.parse("c:ores/iron"), Identifier.parse("minecraft:raw_iron")));
            helper.assertValueEqual(materialiser.getAutomationItemHandler(Direction.DOWN).getSlots(), MaterialiserBlockEntity.TOTAL_SLOTS,
                    "no catalogue with an output chosen");
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: unrealised.materialiser_tesseract
    @GameTest(template = TestSupport.FLOOR_17, batch = "unrealised_tesseract", timeoutTicks = 200)
    public static void theMaterialiserReadsMatterAndTraceThroughTesseracts(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = new BlockPos(8, 2, 8);
        BlockPos matterChest = new BlockPos(3, 2, 3);
        BlockPos traceChest = new BlockPos(13, 2, 3);
        helper.setBlock(matterChest, Blocks.CHEST);
        helper.setBlock(traceChest, Blocks.CHEST);
        IItemHandler matter = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(matterChest), null));
        IItemHandler trace = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(traceChest), null));
        // Raw Matter first, then some smelted: it reads the first history, and all of it, and only that.
        MatterHistory smelted = new MatterHistory(List.of(MatterSteps.idOf(RecipeType.SMELTING)));
        matter.insertItem(0, new ItemStack(ModItems.UNREALISED_MATTER.get(), 3), false);
        matter.insertItem(1, smelted.applyTo(new ItemStack(ModItems.UNREALISED_MATTER.get(), 2)), false);
        trace.insertItem(0, new ItemStack(ModItems.QUANTIMIUM_TRACE.get(), 4), false);

        TestSupport.track(helper);
        helper.setBlock(at, ModBlocks.MATERIALISER.get());
        MaterialiserBlockEntity materialiser = helper.getBlockEntity(at, MaterialiserBlockEntity.class);
        TestSupport.fill(materialiser.getEnergyStorage());
        materialiser.getInventory().setStackInSlot(MaterialiserBlockEntity.MATTER_SLOT, CrafterTests.link(helper, matterChest, Blocks.CHEST));
        materialiser.getInventory().setStackInSlot(MaterialiserBlockEntity.TRACE_SLOT, CrafterTests.link(helper, traceChest, Blocks.CHEST));
        heatAround(helper, at, 500.0);
        materialiser.bandGate().update(helper.getLevel(), helper.absolutePos(at));
        materialiser.setTarget(new Materialising.Target(Identifier.parse("c:ores/iron"), Identifier.parse("minecraft:raw_iron")));
        helper.assertValueEqual(materialiser.matterSeen().getCount(), 3, "raw Matter it can reach through the Tesseract");
        helper.succeedWhen(() -> {
            int iron = 0;
            for (int slot = MaterialiserBlockEntity.OUTPUT_START; slot <= MaterialiserBlockEntity.OUTPUT_END; slot++) {
                ItemStack out = materialiser.getInventory().getStackInSlot(slot);
                if (out.is(Items.RAW_IRON)) iron += out.getCount();
            }
            helper.assertValueEqual(iron, 3, "raw iron made");
            helper.assertTrue(matter.getStackInSlot(0).isEmpty(), "the raw Matter is used up");
            helper.assertValueEqual(matter.getStackInSlot(1).getCount(), 2, "the smelted Matter is left alone");
            // Three commons at Medium are 2.25 Trace: three taken from the other chest, 0.75 kept as credit.
            helper.assertValueEqual(trace.getStackInSlot(0).getCount(), 1, "Trace left in its chest");
            TestSupport.clearField(helper);
        });
    }

    private static void heatAround(GameTestHelper helper, BlockPos at, double flux) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) TestSupport.setField(helper, at.offset(dx * 16, 0, dz * 16), flux, 0.0);
        }
    }

    private static ResolvedCraft matterCraft(QuantumCrafterBlockEntity crafter) {
        for (ResolvedCraft craft : crafter.getPreview()) {
            if (craft.primaryOutput().is(ModItems.UNREALISED_MATTER.get())) return craft;
        }
        return null;
    }

    private static PackOres.Ore ore(GameTestHelper helper, String name) {
        for (PackOres.Ore ore : PackOres.ores()) {
            if (ore.tag().location().getPath().equals("ores/" + name)) return ore;
        }
        throw new AssertionError("no #c:ores/" + name + " among the pack's ores");
    }

    private static Rarity rarityOf(GameTestHelper helper, String name) {
        return ore(helper, name).rarity();
    }
}
