package com.kadikular.quantimium.gametest;

import net.minecraft.core.Direction;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.RiftStabiliserBlock;
import com.kadikular.quantimium.menu.RiftAnchorMenu;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity;
import com.kadikular.quantimium.block.entity.RiftAnchorBlockEntity;
import com.kadikular.quantimium.block.entity.RiftStabiliserBlockEntity;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.flux.FluxSources;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Flux rifts and the stabilised rift. Wiki: mechanics/flux-rifts, multiblocks/stabilised-rift.
 *
 * <p>Rifts live in a per-dimension list and write the field around them, so each test runs in its
 * own batch and removes its rift before it succeeds.
 */
public final class RiftTests {

    private static final BlockPos ANCHOR = new BlockPos(8, 2, 8);
    private static final BlockPos[] RING = {
            new BlockPos(5, 2, 8), new BlockPos(11, 2, 8), new BlockPos(8, 2, 5), new BlockPos(8, 2, 11)};

    private RiftTests() {}

    // covers: rift.seed_wild
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_seed_wild", timeoutTicks = 40)
    public static void aSeedTakesOnlyWhereAnomalyCanFeedIt(GameTestHelper helper) {
        TestSupport.clearField(helper);
        ServerPlayer player = TestSupport.player(helper, new BlockPos(2, 2, 2), GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RIFT_SEED.get(), 2));
        BlockPos floor = new BlockPos(8, 1, 8);
        ServerLevel level = helper.getLevel();

        // A cold chunk: it fizzles, and the seed is kept.
        helper.assertTrue(plant(helper, player, floor) == InteractionResult.FAIL, "a cold chunk should refuse the seed");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "seeds kept after a fizzle");
        helper.assertTrue(FluxRiftManager.riftAbove(level, helper.absolutePos(floor)) == null, "no rift in a cold chunk");

        // Medium anomaly, uncontained: a wild stage 1 rift, standing on the block it was used on.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) TestSupport.setField(helper, floor.offset(dx * 16, 1, dz * 16), 0.0, 500.0);
        }
        helper.assertTrue(plant(helper, player, floor) == InteractionResult.CONSUME, "a fed chunk should take the seed");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "one seed used");
        FluxRift rift = FluxRiftManager.riftAbove(level, helper.absolutePos(floor));
        helper.assertTrue(rift != null && rift.stage() == 1 && !rift.stabilised(level.getGameTime()), "a wild stage 1 rift");

        // Another beside it shies away: planted rifts keep the same spacing as the others.
        helper.assertTrue(plant(helper, player, floor.east(4)) == InteractionResult.FAIL, "too close to the first");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "seed kept when too close");

        FluxRiftManager.remove(level, rift);
        TestSupport.clearField(helper);
        TestSupport.removePlayer(helper, player);
        helper.succeed();
    }

    // covers: rift.local_cap
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_local_cap", timeoutTicks = 20)
    public static void riftsAreLimitedByNeighbourhoodNotByWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = clearOfRifts(helper, helper.absolutePos(new BlockPos(8, 2, 8)));
        // Three rifts round here, each past the spacing: the neighbourhood is full.
        List<FluxRift> opened = new ArrayList<>();
        for (int i = 1; i <= 3; i++) opened.add(FluxRiftManager.spawn(level, here.offset(70 * i, 40, 0), 1));
        try {
            helper.assertValueEqual(FluxRiftManager.room(level, here), FluxRiftManager.Room.CROWDED, "room here, with three nearby");
            helper.assertValueEqual(FluxRiftManager.room(level, here.offset(210, 40, 30)), FluxRiftManager.Room.TOO_CLOSE,
                    "room beside one of them");
            // Far enough away, another base has room of its own.
            helper.assertValueEqual(FluxRiftManager.room(level, here.offset(0, 0, 2_000)), FluxRiftManager.Room.OPEN,
                    "room across the world");
        } finally {
            for (FluxRift rift : opened) FluxRiftManager.remove(level, rift);
        }
        helper.succeed();
    }

    /**
     * The first spot at or beyond {@code from}, in steps of 10,000 blocks towards -z, with no rift near enough to
     * count as a neighbour (or to sit within the 2,000 blocks this test looks across). Leftovers from other tests'
     * rifts would otherwise turn a CROWDED room into TOO_CLOSE, and padding can't cover that radius.
     */
    private static BlockPos clearOfRifts(GameTestHelper helper, BlockPos from) {
        ServerLevel level = helper.getLevel();
        double clear = Config.fluxRiftNearbyRadius() + 2_500.0;
        BlockPos spot = from;
        for (int i = 0; i < 100; i++) {
            BlockPos candidate = spot;
            if (FluxRiftManager.rifts(level).stream().noneMatch(rift -> rift.anchor().distSqr(candidate) < clear * clear)) {
                return spot;
            }
            spot = spot.offset(0, 0, -10_000);
        }
        helper.fail("no spot clear of rifts to test in");
        return spot;
    }

    /** {@code player} uses the Rift Seed in hand on the top of {@code floor}. */
    private static InteractionResult plant(GameTestHelper helper, ServerPlayer player, BlockPos floor) {
        BlockPos at = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).add(0.0, 0.5, 0.0), Direction.UP, at, false);
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    // ---- Stabilised rift ----

    /** An anchor, a seeded stage 1 rift on it, and {@code count} charged stabilisers round it. */
    private static FluxRift array(GameTestHelper helper, int count) {
        helper.setBlock(ANCHOR, ModBlocks.RIFT_ANCHOR.get());
        for (int i = 0; i < count; i++) {
            helper.setBlock(RING[i], ModBlocks.RIFT_STABILISER.get());
            RiftStabiliserBlockEntity stabiliser = helper.getBlockEntity(RING[i], RiftStabiliserBlockEntity.class);
            TestSupport.fill(stabiliser.getEnergyStorage());
        }
        return TestSupport.seed(helper, ANCHOR);
    }

    private static void teardown(GameTestHelper helper, FluxRift rift) {
        TestSupport.removeRift(helper, rift);
        helper.setBlock(ANCHOR, Blocks.AIR);
    }

    // covers: stabilised.hold
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_hold", timeoutTicks = 60)
    public static void threeStabilisersHoldARift(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        RiftAnchorBlockEntity anchor = helper.getBlockEntity(ANCHOR, RiftAnchorBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(rift.stabilised(helper.getLevel().getGameTime()), "three powered stabilisers should hold it");
            helper.assertValueEqual(anchor.holdingCount(), 3, "stabilisers paying");
            teardown(helper, rift);
        });
    }

    // covers: stabilised.hold
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_two", timeoutTicks = 80)
    public static void twoStabilisersNeitherHoldNorPay(GameTestHelper helper) {
        FluxRift rift = array(helper, 2);
        RiftStabiliserBlockEntity first = helper.getBlockEntity(RING[0], RiftStabiliserBlockEntity.class);
        int before = first.getEnergyStorage().getEnergyStored();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!rift.stabilised(helper.getLevel().getGameTime()), "two stabilisers should not hold it");
            helper.assertValueEqual(first.getEnergyStorage().getEnergyStored(), before, "energy a lone pair spends");
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.hold
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_cost", timeoutTicks = 60)
    public static void stabilisersPayByStage(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        RiftStabiliserBlockEntity first = helper.getBlockEntity(RING[0], RiftStabiliserBlockEntity.class);
        helper.runAfterDelay(10, () -> {
            int before = first.getEnergyStorage().getEnergyStored();
            helper.runAfterDelay(20, () -> {
                int spent = before - first.getEnergyStorage().getEnergyStored();
                helper.assertValueEqual(spent, 20 * RiftStabiliserBlockEntity.fePerTick(1), "FE one stabiliser spends in 20 ticks at stage 1");
                helper.assertValueEqual(RiftStabiliserBlockEntity.fePerTick(4), 220, "FE/t per stabiliser at stage 4");
                teardown(helper, rift);
                helper.succeed();
            });
        });
    }

    /** Places a charged stabiliser. */
    private static RiftStabiliserBlockEntity stabiliserAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.RIFT_STABILISER.get());
        RiftStabiliserBlockEntity stabiliser = helper.getBlockEntity(pos, RiftStabiliserBlockEntity.class);
        TestSupport.fill(stabiliser.getEnergyStorage());
        return stabiliser;
    }

    private static boolean paying(RiftStabiliserBlockEntity stabiliser) {
        return stabiliser.beamRift() != null;
    }

    // covers: stabilised.limit
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_seven", timeoutTicks = 80)
    public static void sevenStabilisersSixPayTheSeventhIdles(GameTestHelper helper) {
        helper.setBlock(ANCHOR, ModBlocks.RIFT_ANCHOR.get());
        BlockPos[] spots = {new BlockPos(5, 2, 8), new BlockPos(11, 2, 8), new BlockPos(8, 2, 5), new BlockPos(8, 2, 11),
                new BlockPos(5, 2, 5), new BlockPos(11, 2, 11), new BlockPos(2, 2, 2)};
        List<RiftStabiliserBlockEntity> all = new ArrayList<>();
        for (BlockPos spot : spots) all.add(stabiliserAt(helper, spot));
        FluxRift rift = TestSupport.seed(helper, ANCHOR);
        helper.runAfterDelay(30, () -> {
            RiftAnchorBlockEntity anchor = helper.getBlockEntity(ANCHOR, RiftAnchorBlockEntity.class);
            helper.assertValueEqual(anchor.holdingCount(), RiftAnchorBlockEntity.MAX_STABILISERS, "stabilisers paying");
            long beaming = all.stream().filter(RiftTests::paying).count();
            helper.assertValueEqual(beaming, (long) RiftAnchorBlockEntity.MAX_STABILISERS, "stabilisers showing beams");
            RiftStabiliserBlockEntity seventh = all.get(6);
            int before = seventh.getEnergyStorage().getEnergyStored();
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(!paying(seventh), "the farthest, seventh stabiliser should stand idle");
                helper.assertValueEqual(seventh.getEnergyStorage().getEnergyStored(), before, "energy the idle seventh spends");
                teardown(helper, rift);
                helper.succeed();
            });
        });
    }

    // covers: stabilised.reach
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_reach", timeoutTicks = 60)
    public static void stabilisersReachSixBlocksOut(GameTestHelper helper) {
        helper.setBlock(ANCHOR, ModBlocks.RIFT_ANCHOR.get());
        // Six out on each axis and on the diagonal: the edge of reach, which should still count.
        RiftStabiliserBlockEntity[] edge = {stabiliserAt(helper, new BlockPos(2, 2, 8)), stabiliserAt(helper, new BlockPos(14, 2, 8)),
                stabiliserAt(helper, new BlockPos(14, 2, 14))};
        RiftStabiliserBlockEntity beyond = stabiliserAt(helper, new BlockPos(8, 2, 15));
        FluxRift rift = TestSupport.seed(helper, ANCHOR);
        helper.runAfterDelay(30, () -> {
            for (RiftStabiliserBlockEntity stabiliser : edge) {
                helper.assertTrue(paying(stabiliser), "a stabiliser six blocks out should hold: " + stabiliser.getBlockPos());
            }
            helper.assertTrue(!paying(beyond), "seven blocks out is beyond reach");
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.sight
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_sight", timeoutTicks = 60)
    public static void aStabiliserBehindAnotherStillSees(GameTestHelper helper) {
        helper.setBlock(ANCHOR, ModBlocks.RIFT_ANCHOR.get());
        // Three in a row on one side: the far ones look past the near ones' pillars.
        RiftStabiliserBlockEntity[] row = {stabiliserAt(helper, new BlockPos(5, 2, 8)), stabiliserAt(helper, new BlockPos(4, 2, 8)),
                stabiliserAt(helper, new BlockPos(3, 2, 8))};
        FluxRift rift = TestSupport.seed(helper, ANCHOR);
        helper.runAfterDelay(30, () -> {
            for (RiftStabiliserBlockEntity stabiliser : row) {
                helper.assertTrue(paying(stabiliser), "a stabiliser behind another should still reach the rift: " + stabiliser.getBlockPos());
            }
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.sight
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_blocked", timeoutTicks = 60)
    public static void aWalledOffStabiliserGivesUpItsSlot(GameTestHelper helper) {
        helper.setBlock(ANCHOR, ModBlocks.RIFT_ANCHOR.get());
        // Six that can see, all nearer than the seventh, and a seventh behind a stone wall nearest of all.
        BlockPos[] clear = {new BlockPos(4, 2, 8), new BlockPos(12, 2, 8), new BlockPos(8, 2, 4), new BlockPos(8, 2, 12),
                new BlockPos(4, 2, 4), new BlockPos(12, 2, 12)};
        List<RiftStabiliserBlockEntity> seeing = new ArrayList<>();
        for (BlockPos spot : clear) seeing.add(stabiliserAt(helper, spot));
        RiftStabiliserBlockEntity walled = stabiliserAt(helper, new BlockPos(10, 2, 6));
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(new BlockPos(9, y, 7), Blocks.STONE);
        }
        FluxRift rift = TestSupport.seed(helper, ANCHOR);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!paying(walled), "a walled-off stabiliser cannot hold");
            for (RiftStabiliserBlockEntity stabiliser : seeing) {
                helper.assertTrue(paying(stabiliser), "the blocked one should not take a slot from " + stabiliser.getBlockPos());
            }
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.sight_crystals
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_crystals", timeoutTicks = 60)
    public static void crystalsOnTheStabilisersDoNotBlockSight(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        helper.runAfterDelay(10, () -> {
            // A full-grown crystal on top of each stabiliser, right where its beam leaves for the rift.
            for (int i = 0; i < 3; i++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(RING[i].above()), ModBlocks.ANOMALITE_CRYSTAL.get()
                        .defaultBlockState().setValue(AnomaliteCrystalBlock.FACING, Direction.UP)
                        .setValue(AnomaliteCrystalBlock.AGE, 3));
            }
        });
        helper.runAfterDelay(40, () -> {
            RiftAnchorBlockEntity anchor = helper.getBlockEntity(ANCHOR, RiftAnchorBlockEntity.class);
            for (int i = 0; i < 3; i++) {
                helper.assertBlockPresent(ModBlocks.ANOMALITE_CRYSTAL.get(), RING[i].above());
                RiftStabiliserBlockEntity stabiliser = helper.getBlockEntity(RING[i], RiftStabiliserBlockEntity.class);
                helper.assertTrue(paying(stabiliser), "a crystal on a stabiliser should not block its sight: " + RING[i]);
            }
            helper.assertValueEqual(anchor.blindCount(), 0, "stabilisers that cannot see the rift");
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.output
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_output", timeoutTicks = 80)
    public static void outputScalesWithStageAndStabilisers(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        RiftAnchorBlockEntity anchor = helper.getBlockEntity(ANCHOR, RiftAnchorBlockEntity.class);
        helper.runAfterDelay(10, () -> {
            long before = anchor.work();
            helper.runAfterDelay(40, () -> {
                helper.assertValueEqual(anchor.work() - before, 40L * rift.stage() * 3, "work in 40 ticks at stage 1 on three stabilisers");
                // Stage 4 on four makes one every four minutes: the rate the wiki quotes.
                helper.assertValueEqual(RiftAnchorBlockEntity.WORK_PER_RESIDUE / (4L * 4L), 4_800L, "ticks per residue at stage 4 on four");
                teardown(helper, rift);
                helper.succeed();
            });
        });
    }

    // covers: flux_meter.readouts
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_meter", timeoutTicks = 40)
    public static void theMeterReadsTheRiftArray(GameTestHelper helper) {
        BlockPos lonely = new BlockPos(2, 2, 2);
        helper.setBlock(lonely, ModBlocks.RIFT_ANCHOR.get());
        FluxRift rift = array(helper, 3);
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(FluxTests.meterKey(TestSupport.blockEntity(helper, lonely)),
                    "item.quantimium.flux_meter.rift_anchor.empty", "anchor with no rift");
            helper.assertValueEqual(FluxTests.meterKey(TestSupport.blockEntity(helper, ANCHOR)),
                    "item.quantimium.flux_meter.rift_anchor.held", "anchor holding its rift");
            helper.assertValueEqual(FluxTests.meterKey(TestSupport.blockEntity(helper, RING[0])),
                    "item.quantimium.flux_meter.rift_stabiliser.beaming", "paying stabiliser");
            helper.setBlock(lonely, Blocks.AIR);
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.flux, lens.rift_bleed
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_flux", timeoutTicks = 80)
    public static void stabilisersEmitFluxAndTheTearShowsItsBleed(GameTestHelper helper) {
        TestSupport.clearField(helper);
        FluxRift rift = array(helper, 3);
        helper.runAfterDelay(45, () -> {
            // Three stabilisers at stage 1: 100 FE/t each, emitted once a second as flux.
            double flux = QuantumFlux.chunkFlux(helper.getLevel(), helper.absolutePos(ANCHOR));
            helper.assertTrue(flux > 0.0, "paying stabilisers should raise the chunk's flux");
            // Held, the rift adds nothing to the field, but a lens still sees it bleeding from its tear.
            BlockPos tear = BlockPos.containing(rift.centre());
            helper.assertTrue(FluxSources.near(helper.getLevel(), tear, 1.0, 4).stream()
                            .anyMatch(s -> s.pos().equals(tear) && s.anomaly() > 0 && !s.sink()),
                    "the tear should show as a source");
            teardown(helper, rift);
            TestSupport.clearField(helper);
            helper.succeed();
        });
    }

    // covers: stabilised.gui
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_gui", timeoutTicks = 60)
    public static void anchorMenuReadsTheArrayAndHandsOverResidue(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        RiftAnchorBlockEntity anchor = helper.getBlockEntity(ANCHOR, RiftAnchorBlockEntity.class);
        ServerPlayer player = TestSupport.player(helper, new BlockPos(8, 2, 2));
        helper.runAfterDelay(10, () -> {
            RiftAnchorMenu menu = new RiftAnchorMenu(0, player.getInventory(), anchor, anchor.getData());
            helper.assertValueEqual(menu.getStatusCode(), RiftAnchorBlockEntity.STATUS_HELD, "status");
            helper.assertValueEqual(menu.getStage(), 1, "rift stage");
            helper.assertValueEqual(menu.getHolding(), 3, "stabilisers holding");
            helper.assertValueEqual(menu.getWorkPerTick(), 3, "work per tick at stage 1 on three");
            helper.assertTrue(Math.abs(menu.getResiduePerHour() - 2.8125) < 1e-9, "residue an hour at stage 1 on three");
            helper.assertTrue(menu.getSecondsToNext() > 0, "time to the next residue while held");

            ItemStack residue = new ItemStack(ModItems.RIFT_RESIDUE.get(), RiftAnchorBlockEntity.OUTPUT_CAPACITY);
            anchor.getOutput().setStackInSlot(0, residue);
            helper.assertValueEqual(menu.getStatusCode(), RiftAnchorBlockEntity.STATUS_FULL, "status with the slot full");
            helper.assertValueEqual(menu.getSecondsToNext(), -1, "no countdown while full");
            helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(ModItems.RIFT_RESIDUE.get())),
                    "residue cannot be put back into the anchor");
            menu.quickMoveStack(player, 0);
            helper.assertTrue(anchor.getOutput().getStackInSlot(0).isEmpty(), "shift-click empties the slot");
            helper.assertValueEqual(player.getInventory().countItem(ModItems.RIFT_RESIDUE.get()),
                    RiftAnchorBlockEntity.OUTPUT_CAPACITY, "residue in the player's inventory");
            TestSupport.removePlayer(helper, player);
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.beaming
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_beaming", timeoutTicks = 80)
    public static void stabilisersLightWhileBeaming(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        helper.runAfterDelay(10, () -> {
            for (int i = 0; i < 3; i++) {
                helper.assertBlockProperty(RING[i], RiftStabiliserBlock.BEAMING, true);
            }
            TestSupport.removeRift(helper, rift);
            helper.runAfterDelay(5, () -> {
                for (int i = 0; i < 3; i++) {
                    helper.assertBlockProperty(RING[i], RiftStabiliserBlock.BEAMING, false);
                }
                helper.setBlock(ANCHOR, Blocks.AIR);
                helper.succeed();
            });
        });
    }

    // covers: stabilised.safe
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_safe", timeoutTicks = 140)
    public static void heldRiftOpensNoTearAndLeaksNothing(GameTestHelper helper) {
        TestSupport.clearField(helper);
        FluxRift rift = array(helper, 3);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(rift.tear() == null, "a held rift should keep no tear open");
            double anomaly = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(ANCHOR));
            // A trace at most: the one second it leaked before the array took hold, easing in from the pool.
            helper.assertTrue(anomaly < 1.0, "a held rift should leak no anomaly, found " + anomaly);
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.failure
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_failure", timeoutTicks = 160)
    public static void heldRiftGoesWildWhenPowerFails(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(rift.stabilised(helper.getLevel().getGameTime()), "it should be held first");
            for (int i = 0; i < 3; i++) {
                RiftStabiliserBlockEntity stabiliser = helper.getBlockEntity(RING[i], RiftStabiliserBlockEntity.class);
                stabiliser.getEnergyStorage().setEnergy(0);
            }
        });
        helper.runAfterDelay(40, () -> helper.assertTrue(rift.stabilised(helper.getLevel().getGameTime()),
                "a flicker is not a failure: still held a second later"));
        helper.runAfterDelay(20 + FluxRift.STABILISED_WINDOW + 20, () -> {
            helper.assertTrue(!rift.stabilised(helper.getLevel().getGameTime()), "unpowered, it should be wild again");
            helper.assertValueEqual(rift.stage(), 1, "stage after failing");
            teardown(helper, rift);
            helper.succeed();
        });
    }

    // covers: stabilised.growth
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_growth", timeoutTicks = 80)
    public static void heldRiftGrowsWhateverTheField(GameTestHelper helper) {
        TestSupport.setField(helper, ANCHOR, 0.0, 0.0);
        FluxRift rift = array(helper, 3);
        rift.setGrowth(0.9995);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(rift.stage(), 2, "stage after its growth completes, in a cold field");
            teardown(helper, rift);
        });
    }

    // covers: stabilised.projector_ignores, rift.projector
    @GameTest(template = TestSupport.FLOOR_17, batch = "stabilised_projector", timeoutTicks = 100)
    public static void projectorLeavesHeldRiftAlone(GameTestHelper helper) {
        FluxRift rift = array(helper, 3);
        BlockPos projectorPos = new BlockPos(2, 2, 2);
        helper.setBlock(projectorPos, ModBlocks.DECOHERENCE_PROJECTOR.get());
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(projectorPos, DecoherenceProjectorBlockEntity.class);
        TestSupport.fill(projector.getEnergyStorage());
        projector.getCells().setStackInSlot(0, new ItemStack(ModItems.ANOMALITE_CELL.get(), 16));
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(rift.damage(), 0.0, "coherence a Projector took from a held rift");
            teardown(helper, rift);
            helper.setBlock(projectorPos, Blocks.AIR);
            helper.succeed();
        });
    }

    // ---- Wild rifts ----

    // covers: rift.projector
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_projector", timeoutTicks = 100)
    public static void projectorDrainsAWildRift(GameTestHelper helper) {
        FluxRift rift = TestSupport.rift(helper, ANCHOR.above(), 1);
        BlockPos projectorPos = new BlockPos(2, 2, 2);
        helper.setBlock(projectorPos, ModBlocks.DECOHERENCE_PROJECTOR.get());
        DecoherenceProjectorBlockEntity projector = helper.getBlockEntity(projectorPos, DecoherenceProjectorBlockEntity.class);
        TestSupport.fill(projector.getEnergyStorage());
        projector.getCells().setStackInSlot(0, new ItemStack(ModItems.ANOMALITE_CELL.get(), 16));
        helper.succeedWhen(() -> {
            helper.assertTrue(rift.damage() >= 20 * DecoherenceProjectorBlockEntity.RIFT_DRAIN_PER_TICK,
                    "a Projector should wear a wild rift down, took " + rift.damage());
            TestSupport.removeRift(helper, rift);
            helper.setBlock(projectorPos, Blocks.AIR);
        });
    }

    // covers: rift.breach
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_breach", timeoutTicks = 20)
    public static void aRiftCountsItsOwnMites(GameTestHelper helper) {
        // The caps on what a rift lets through count only its own mites; any it can't see, it keeps spawning.
        FluxRift rift = TestSupport.rift(helper, ANCHOR.above(), 4);
        FluxRift other = TestSupport.rift(helper, new BlockPos(2, 3, 2), 1);
        ServerLevel level = helper.getLevel();
        MirrorEndermite breached = helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(12, 2, 8));
        breached.setBreached(true);
        FluxRiftManager.markSpawned(breached, rift);
        MirrorEndermite inMirror = helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(4, 2, 8));
        FluxRiftManager.markSpawned(inMirror, rift);
        MirrorEndermite someoneElses = helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(8, 2, 12));
        someoneElses.setBreached(true);
        FluxRiftManager.markSpawned(someoneElses, other);
        helper.spawn(ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(8, 2, 4));
        helper.assertValueEqual(FluxRiftManager.mitesOf(level, rift, 48.0, false).size(), 2, "the rift's own mites");
        helper.assertValueEqual(FluxRiftManager.mitesOf(level, rift, 48.0, true).size(), 1, "the rift's breached mites");
        helper.assertTrue(FluxRiftManager.spawnedByRift(inMirror), "a marked mite is a rift's");
        TestSupport.removeRift(helper, rift);
        TestSupport.removeRift(helper, other);
        helper.succeed();
    }

    // covers: rift.collapse
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_collapse", timeoutTicks = 40)
    public static void collapsePaysResidueByStage(GameTestHelper helper) {
        TestSupport.track(helper);
        ServerLevel level = helper.getLevel();
        // Nothing at stage 1: a planted seed has to grow before it pays.
        int[] expected = {0, 3, 6, 10};
        List<FluxRift> rifts = new ArrayList<>();
        for (int stage = 1; stage <= 4; stage++) {
            BlockPos at = helper.absolutePos(new BlockPos(2 + (stage - 1) * 4, 2, 2));
            FluxRift rift = FluxRiftManager.spawn(level, at, stage);
            rifts.add(rift);
            Vec3 drop = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2 + (stage - 1) * 4, 2, 12)));
            FluxRiftManager.drainUnattended(level, rift.id(), 1.0e9, helper.absolutePos(ANCHOR), drop);
        }
        helper.runAfterDelay(2, () -> {
            for (int stage = 1; stage <= 4; stage++) {
                FluxRift rift = rifts.get(stage - 1);
                helper.assertTrue(FluxRiftManager.rifts(level).stream().noneMatch(r -> r.id().equals(rift.id())),
                        "a stage " + stage + " rift should be gone once collapsed");
                AABB near = new AABB(helper.absolutePos(new BlockPos(2 + (stage - 1) * 4, 2, 12))).inflate(1.5);
                int count = level.getEntitiesOfClass(ItemEntity.class, near, e -> e.getItem().is(ModItems.RIFT_RESIDUE.get()))
                        .stream().mapToInt(e -> e.getItem().getCount()).sum();
                helper.assertValueEqual(count, expected[stage - 1], "residue from a stage " + stage + " rift");
            }
            helper.succeed();
        });
    }

    // covers: rift.solid
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_solid", timeoutTicks = 40)
    public static void riftClearsItsSpaceButSparesBlockEntities(GameTestHelper helper) {
        BlockPos base = ANCHOR.above();
        BlockPos stone = base.east();
        BlockPos chest = base.north().above();
        BlockPos outside = base.east(2);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(outside, Blocks.STONE);
        FluxRift rift = TestSupport.rift(helper, base, 3);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(FluxRiftManager.occupied(level, helper.absolutePos(stone)), "the cell beside it is inside a stage 3 rift");
        helper.assertTrue(!FluxRiftManager.occupied(level, helper.absolutePos(outside)), "two blocks out is outside it");
        helper.assertBlockPresent(Blocks.AIR, stone);
        helper.assertBlockPresent(Blocks.CHEST, chest);
        helper.assertBlockPresent(Blocks.STONE, outside);
        TestSupport.removeRift(helper, rift);
        helper.succeed();
    }

    // covers: rift.leak
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_leak", timeoutTicks = 100)
    public static void wildRiftLeaksAnomaly(GameTestHelper helper) {
        TestSupport.setField(helper, ANCHOR, 0.0, 0.0);
        FluxRift rift = TestSupport.rift(helper, ANCHOR.above(), 2);
        // A rift leaks once a second, and game tests no longer start on a whole second, so how many leaks fit in
        // a fixed 50 ticks varies; wait for the anomaly to build instead.
        helper.succeedWhen(() -> {
            double anomaly = QuantumFlux.chunkAnomaly(helper.getLevel(), helper.absolutePos(ANCHOR));
            helper.assertTrue(anomaly > 0.3, "a stage 2 rift should leak anomaly into its chunk, found " + anomaly);
            TestSupport.removeRift(helper, rift);
        });
    }

    // covers: rift.growth
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_growth", timeoutTicks = 80)
    public static void wildRiftGrowsInAHotField(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.setField(helper, ANCHOR, 20_000.0, 2_000.0);
        FluxRift rift = TestSupport.rift(helper, ANCHOR.above(), 1);
        rift.setGrowth(0.9995);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(rift.stage(), 2, "stage after growing at High anomaly and Critical flux");
            TestSupport.removeRift(helper, rift);
        });
    }

    // covers: rift.contained, containment.shield
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_contained", timeoutTicks = 80)
    public static void containmentFreezesARift(GameTestHelper helper) {
        // Contained, as a hall would hold it: 2,000 capacity over its 3×3, for 1,000 flux.
        TestSupport.clearField(helper);
        TestSupport.setField(helper, ANCHOR, 1_000.0, 500.0);
        BlockPos held = helper.absolutePos(ANCHOR);
        helper.onEachTick(() -> {
            if (helper.getLevel().getGameTime() % 20L == 0L) QuantumFlux.contain(helper.getLevel(), held, 2_000.0, 1);
        });
        FluxRift rift = TestSupport.rift(helper, ANCHOR.above(), 1);
        // Armed once the containment has marked the chunk, not in the tick before it first does.
        helper.runAfterDelay(5, () -> rift.setGrowth(0.9995));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(QuantumFlux.chunkContained(helper.getLevel(), helper.absolutePos(ANCHOR)), "its chunk should be contained");
            helper.assertValueEqual(rift.stage(), 1, "stage of a rift in a contained chunk");
            TestSupport.removeRift(helper, rift);
            TestSupport.setField(helper, ANCHOR, 0.0, 0.0);
            helper.succeed();
        });
    }

    // covers: rift.growth
    @GameTest(template = TestSupport.FLOOR_17, batch = "rift_ceiling", timeoutTicks = 80)
    public static void lowFluxCapsGrowthAtStageTwo(GameTestHelper helper) {
        TestSupport.clearField(helper);
        TestSupport.setField(helper, ANCHOR, 50.0, 2_000.0);
        FluxRift rift = TestSupport.rift(helper, ANCHOR.above(), 2);
        rift.setGrowth(0.9995);
        helper.runAfterDelay(50, () -> {
            helper.assertValueEqual(rift.stage(), 2, "stage at Low flux, which caps a rift at 2");
            TestSupport.removeRift(helper, rift);
            helper.succeed();
        });
    }
}
