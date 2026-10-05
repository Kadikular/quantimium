package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity;
import com.kadikular.quantimium.block.entity.QuantumSimulatorBlockEntity;
import com.kadikular.quantimium.block.entity.ZenoFieldControllerBlockEntity;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.recipe.ResolvedCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Running hot pays: machines do more in a hotter field. Each test switches the shipped rewards on
 * (the test server runs with them flattened, see {@link BandRewards}), heats its plot to a band,
 * reads the machine at once, before the field has time to couple into anomaly, and flattens them
 * again. Each runs alone in its batch, so its heat reaches no one else's plot.
 * See wiki: concepts/flux-and-anomaly.
 */
public final class BandRewardTests {

    private static final BlockPos AT = new BlockPos(8, 2, 8);

    private BandRewardTests() {}

    // covers: bands.crafter
    @GameTest(template = TestSupport.FLOOR_17, batch = "band_crafter", timeoutTicks = 40)
    public static void theCrafterNeedsTheBandItsCatalystsNeedAndPaysLessHotter(GameTestHelper helper) {
        rewards(helper, () -> {
            // The furnace family needs Medium here, so the test needs nothing but vanilla.
            Config.CRAFTER_CATALYST_BANDS.set(List.of("medium", "medium", "high", "critical", "singularity"));
            QuantumCrafterBlockEntity crafter = CrafterTests.crafter(helper, AT, Items.FURNACE);
            CrafterTests.grid(crafter, new ItemStack(Items.RAW_IRON, 8));

            heat(helper, 0.0);
            read(helper, crafter);
            helper.assertValueEqual(crafter.getStatusCode(), QuantumCrafterBlockEntity.STATUS_NEEDS_FLUX, "status in a cold field");
            helper.assertTrue(crafter.getPreview().isEmpty(), "nothing should be offered below the catalyst's band");

            heat(helper, 500.0);
            read(helper, crafter);
            ResolvedCraft medium = CrafterTests.craftOf(crafter, Items.IRON_INGOT);
            helper.assertTrue(medium != null, "a furnace should smelt at Medium");

            heat(helper, 3_000.0);
            read(helper, crafter);
            ResolvedCraft high = CrafterTests.craftOf(crafter, Items.IRON_INGOT);
            helper.assertTrue(high != null, "a furnace should still smelt at High");
            double ratio = (double) high.feCost() / medium.feCost();
            helper.assertTrue(Math.abs(ratio - 0.8) < 0.02, "High's tax should be 80% of Medium's: " + ratio);
        });
    }

    // covers: bands.simulator
    @GameTest(template = TestSupport.FLOOR_17, batch = "band_simulator", timeoutTicks = 40)
    public static void theSimulatorRunsBiggerBatchesHotter(GameTestHelper helper) {
        rewards(helper, () -> {
            TestSupport.track(helper);
            helper.setBlock(AT, ModBlocks.QUANTUM_SIMULATOR.get());
            QuantumSimulatorBlockEntity simulator = helper.getBlockEntity(AT, QuantumSimulatorBlockEntity.class);
            simulator.setBatchSize(32);
            double[] fields = {0.0, 500.0, 3_000.0, 30_000.0, 300_000.0};
            int[] batches = {2, 4, 8, 16, 32};
            for (int i = 0; i < fields.length; i++) {
                heat(helper, fields[i]);
                simulator.bandGate().update(helper.getLevel(), helper.absolutePos(AT));
                helper.assertValueEqual(simulator.getBatchSize(), batches[i], "batch at " + simulator.bandGate().band());
            }
            helper.assertValueEqual(simulator.getChosenBatchSize(), 32, "the batch chosen is kept");
        });
    }

    // covers: bands.zeno
    @GameTest(template = TestSupport.FLOOR_17, batch = "band_zeno", timeoutTicks = 40)
    public static void zenoAcceleratesFasterHotterAndCheaperAtSingularity(GameTestHelper helper) {
        rewards(helper, () -> {
            TestSupport.track(helper);
            helper.setBlock(AT, ModBlocks.ZENO_FIELD_CONTROLLER.get());
            ZenoFieldControllerBlockEntity zeno = helper.getBlockEntity(AT, ZenoFieldControllerBlockEntity.class);
            zeno.setFactor(16);
            double[] fields = {0.0, 500.0, 3_000.0, 30_000.0};
            int[] factors = {2, 4, 8, 16};
            for (int i = 0; i < fields.length; i++) {
                heat(helper, fields[i]);
                zeno.bandGate().update(helper.getLevel(), helper.absolutePos(AT));
                helper.assertValueEqual(zeno.runningFactor(), factors[i], "rate at " + zeno.bandGate().band());
            }
            int usual = ZenoFieldControllerBlockEntity.accelerateCost(4, 16);
            int singularity = ZenoFieldControllerBlockEntity.accelerateCost(4, 16, FluxBand.SINGULARITY);
            helper.assertValueEqual(singularity, (int) Math.ceil(usual * 0.75), "Singularity's cost");
        });
    }

    /** Runs {@code test} with the shipped rewards on, then flattens them and clears the field, pass or fail. */
    private static void rewards(GameTestHelper helper, Runnable test) {
        TestSupport.clearField(helper);
        BandRewards.on();
        try {
            test.run();
        } finally {
            BandRewards.flatten();
            TestSupport.clearField(helper);
        }
        helper.succeed();
    }

    /** Sets every chunk round the machine to {@code flux}, so the reading blended between them is that. */
    private static void heat(GameTestHelper helper, double flux) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) TestSupport.setField(helper, AT.offset(dx * 16, 0, dz * 16), flux, 0.0);
        }
    }

    /** The Crafter reads the band once a second; a test reads it now and reprices. */
    private static void read(GameTestHelper helper, QuantumCrafterBlockEntity crafter) {
        crafter.bandGate().update(helper.getLevel(), helper.absolutePos(AT));
        crafter.rebuildPreview();
    }
}
