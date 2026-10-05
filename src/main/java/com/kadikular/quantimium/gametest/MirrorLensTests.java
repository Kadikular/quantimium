package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.flux.FieldSurvey;
import com.kadikular.quantimium.flux.FluxSources;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModAttachments;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.network.FieldSurveyPayload;
import com.kadikular.quantimium.phase.VeiledManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * The Mirror Lens: what it lets a player see, and what that does to the Veiled. The visuals
 * themselves are client-side; these test what the server sends and decides. Wiki: items/mirror_lens.
 */
public final class MirrorLensTests {

    private MirrorLensTests() {}

    // covers: lens.survey, lens.sources
    @GameTest(template = TestSupport.FLOOR_17, batch = "lens_survey", timeoutTicks = 40)
    public static void wornItShowsTheFieldAndWhatFeedsIt(GameTestHelper helper) {
        BlockPos standing = new BlockPos(8, 2, 8);
        ServerPlayer player = TestSupport.player(helper, standing);
        helper.assertTrue(!FieldSurvey.canSee(player), "without a lens, the real world shows nothing");
        wearLens(player);
        helper.assertTrue(FieldSurvey.canSee(player), "wearing a lens, the field shows");

        TestSupport.clearField(helper);
        TestSupport.setField(helper, standing, 1_234.0, 56.0);
        FieldSurveyPayload survey = FieldSurvey.survey(player);
        int here = survey.index(0, 0);
        helper.assertTrue(Math.abs(survey.flux()[here] - 1_234.0) < 1.0, "flux of the player's chunk: " + survey.flux()[here]);
        helper.assertTrue(Math.abs(survey.anomaly()[here] - 56.0) < 1.0, "anomaly of the player's chunk: " + survey.anomaly()[here]);

        // A block putting flux into the field, and one drawing it out, both show up as sources.
        BlockPos emitter = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos sink = helper.absolutePos(new BlockPos(12, 2, 12));
        QuantumFlux.emit(helper.getLevel(), emitter, 50.0, 0.0);
        QuantumFlux.suppress(helper.getLevel(), sink, 100.0, QuantumFlux.SOURCE_RADIUS);
        List<FluxSources.Source> sources = FieldSurvey.survey(player).sources();
        helper.assertTrue(sources.stream().anyMatch(s -> s.pos().equals(emitter) && !s.sink() && s.flux() > 0),
                "the emitter should show as feeding the field: " + sources);
        helper.assertTrue(sources.stream().anyMatch(s -> s.pos().equals(sink) && s.sink()),
                "the suppressor should show as drawing it in: " + sources);

        TestSupport.setField(helper, standing, 0.0, 0.0);
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: lens.mirror_sources
    @GameTest(template = TestSupport.FLOOR_17, batch = "lens_mirror", timeoutTicks = 40)
    public static void inTheMirrorTheFieldShowsWithoutALens(GameTestHelper helper) {
        ServerPlayer player = TestSupport.phasedPlayer(helper, new BlockPos(8, 2, 8));
        helper.assertTrue(FieldSurvey.canSee(player), "in the mirror the field shows, lens or not");
        BlockPos emitter = helper.absolutePos(new BlockPos(4, 2, 4));
        QuantumFlux.emit(helper.getLevel(), emitter, 50.0, 12.5);
        helper.assertTrue(FieldSurvey.survey(player).sources().stream().anyMatch(s -> s.pos().equals(emitter)),
                "a machine's emission should reach a phased player's survey");
        TestSupport.clearField(helper);
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: projector.overload_burst
    @GameTest(template = TestSupport.FLOOR_9, batch = "lens_overload", timeoutTicks = 20)
    public static void aLashedProjectorBurstsAnomaly(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.track(helper);
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.DECOHERENCE_PROJECTOR.get());
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(at, DecoherenceProjectorBlockEntity.class);
        projector.overload(60);
        // Into the pools of the 3x3 round it, like any emission: a fifth to its own chunk.
        double anomaly = helper.getLevel().getChunkAt(helper.absolutePos(at)).getData(ModAttachments.CHUNK_FLUX).pendingAnomaly();
        double expected = DecoherenceProjectorBlockEntity.OVERLOAD_ANOMALY * QuantumFlux.share(0, 0, QuantumFlux.SOURCE_RADIUS);
        helper.assertTrue(Math.abs(anomaly - expected) < 0.01, "anomaly in its chunk: " + anomaly + ", expected " + expected);
        TestSupport.clearField(helper);
        helper.succeed();
    }

    // covers: lens.crystal_ink
    @GameTest(template = TestSupport.FLOOR_9, batch = "lens_crystal", timeoutTicks = 20)
    public static void aCrystalShowsItsInk(GameTestHelper helper) {
        TestSupport.clearField(helper);
        BlockPos at = new BlockPos(4, 2, 4);
        helper.setBlock(at, ModBlocks.ANOMALITE_CRYSTAL.get());
        BlockPos crystal = helper.absolutePos(at);
        helper.succeedWhen(() -> {
            helper.assertTrue(FluxSources.near(helper.getLevel(), crystal, 1.0, 4).stream()
                    .anyMatch(s -> s.pos().equals(crystal) && s.anomaly() > 0), "the crystal should show as a source");
            helper.assertTrue(QuantumFlux.chunk(helper.getLevel(), crystal).anomaly() == 0.0,
                    "showing its ink adds nothing to the field");
            helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
        });
    }

    // covers: anomalite.host_removed
    @GameTest(template = TestSupport.FLOOR_9, batch = "crystal_host", timeoutTicks = 20)
    public static void crystalsGoWithTheirHost(GameTestHelper helper) {
        TestSupport.track(helper);
        BlockPos host = new BlockPos(4, 2, 4);
        BlockPos crystal = host.above();
        helper.setBlock(host, ModBlocks.RIFT_STABILISER.get());
        helper.setBlock(crystal, ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(AnomaliteCrystalBlock.FACING, Direction.UP));
        // Still on its host, a machine's destroy call cannot take it.
        helper.assertTrue(!helper.getLevel().destroyBlock(helper.absolutePos(crystal), false),
                "a crystal on its host should refuse to be destroyed");
        helper.assertBlockPresent(ModBlocks.ANOMALITE_CRYSTAL.get(), crystal);
        // With the host gone it goes too, rather than floating where the host was.
        helper.setBlock(host, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertBlockNotPresent(ModBlocks.ANOMALITE_CRYSTAL.get(), crystal);
        helper.succeed();
    }

    // covers: lens.stalk
    @GameTest(template = TestSupport.FLOOR_17, batch = "lens_stalk", timeoutTicks = 100)
    public static void staredAtThroughALensItStalks(GameTestHelper helper) {
        // In the real world, not the mirror: through the lens it can be seen anyway.
        ServerPlayer player = TestSupport.player(helper, new BlockPos(8, 2, 15), GameType.CREATIVE);
        wearLens(player);
        Veiled veiled = helper.spawn(ModEntities.VEILED.get(), new BlockPos(8, 2, 3));
        helper.onEachTick(() -> TestSupport.aimAt(player,
                veiled.position().add(0.0, veiled.getBbHeight() * 0.55, 0.0)));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(veiled.state(), Veiled.State.SHADOWING, "state once stared at through a lens");
            helper.assertValueEqual(veiled.shadowTarget(), player.getUUID(), "who it follows");
            TestSupport.discard(veiled);
            TestSupport.removePlayer(helper, player);
        });
    }

    // covers: lens.sightings
    @GameTest(template = TestSupport.FLOOR_9, batch = "lens_sightings", timeoutTicks = 20)
    public static void throughALensSightingsComeTwiceAsOften(GameTestHelper helper) {
        ServerPlayer player = TestSupport.player(helper, new BlockPos(4, 2, 4));
        for (int i = 0; i < 50; i++) {
            long gap = VeiledManager.nextSightingGap(player, helper.getLevel().getRandom());
            helper.assertTrue(gap >= 900 && gap < 2400, "gap without a lens: " + gap);
        }
        wearLens(player);
        for (int i = 0; i < 50; i++) {
            long gap = VeiledManager.nextSightingGap(player, helper.getLevel().getRandom());
            helper.assertTrue(gap >= 450 && gap < 1200, "gap through a lens: " + gap);
        }
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    private static void wearLens(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.MIRROR_LENS.get()));
    }
}
