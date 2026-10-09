package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.util.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Lasers, phase one: emitters send power as light to blocks that take it, only what's asked for;
 * mirrors turn and refocus it, at a small tax; anything solid in the way stops it; a run reaches only
 * so far; the flash emitter burns fuel; redstone switches emitters.
 *
 * <p>Each test puts a creative energy cell behind an emitter (unless it burns fuel) and a Fold Core,
 * which holds ten million FE and takes it on any face, where the light should arrive.
 */
public final class LaserTests {

    private static final int Y = 2;

    private LaserTests() {}

    private static LaserEmitterBlockEntity emitter(GameTestHelper helper, BlockPos pos, Block block, Direction facing, boolean powered) {
        helper.setBlock(pos, block.defaultBlockState().setValue(LaserEmitterBlock.FACING, facing));
        if (powered) helper.setBlock(pos.relative(facing.getOpposite()), ModBlocks.CREATIVE_ENERGY_CELL.get());
        return helper.getBlockEntity(pos, LaserEmitterBlockEntity.class);
    }

    private static FoldCoreBlockEntity sink(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.FOLD_CORE.get());
        return helper.getBlockEntity(pos, FoldCoreBlockEntity.class);
    }

    // covers: laser.power
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void anEmitterPowersAMachineAcrossAGap(GameTestHelper helper) {
        emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(12, Y, 8));
        helper.succeedWhen(() -> helper.assertTrue(core.getEnergyStorage().getEnergyStored() >= LaserTuning.Tier.ONE.rate * 10,
                "the core is charged by light: " + core.getEnergyStorage().getEnergyStored()));
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aMirrorTurnsTheBeamAndTakesItsTax(GameTestHelper helper) {
        emitter(helper, new BlockPos(3, Y, 3), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos mirror = new BlockPos(9, Y, 3);
        helper.setBlock(mirror, ModBlocks.LASER_MIRROR.get().defaultBlockState().setValue(LaserMirrorBlock.FACING, Direction.SOUTH));
        FoldCoreBlockEntity core = sink(helper, new BlockPos(9, Y, 12));
        long[] before = {-1};
        helper.runAfterDelay(40, () -> before[0] = core.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(60, () -> {
            long gained = core.getEnergyStorage().getEnergyStored() - before[0];
            helper.assertTrue(gained > 0, "the light turned the corner");
            // 20 ticks at the emitter's full rate, less the mirror's one percent.
            long full = 20L * LaserTuning.Tier.ONE.rate;
            helper.assertTrue(gained < full && gained >= full * (1.0 - LaserTuning.HOP_TAX) - LaserTuning.Tier.ONE.rate,
                    "the mirror takes its tax: " + gained + " of " + full);
            helper.assertTrue(LaserNetworks.of(helper.getLevel()).throughput(helper.absolutePos(mirror)) > 0, "light passes the mirror");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.blocked
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 120)
    public static void somethingSolidInTheWayStopsTheLight(GameTestHelper helper) {
        emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(12, Y, 8));
        // Glass lets light through; stone doesn't.
        helper.setBlock(new BlockPos(6, Y, 8), Blocks.GLASS);
        long[] at = {0};
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "through the glass");
            helper.setBlock(new BlockPos(8, Y, 8), Blocks.STONE);
        });
        // Within a recheck, the beam stops at the stone.
        helper.runAfterDelay(30 + LaserNetworks.RECHECK_TICKS + 5, () -> at[0] = core.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(30 + LaserNetworks.RECHECK_TICKS + 25, () -> {
            helper.assertValueEqual((long) core.getEnergyStorage().getEnergyStored(), at[0], "nothing gets past the stone");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.range
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aRunReachesOnlySoFar(GameTestHelper helper) {
        // The flash emitter reaches eight blocks; this core is ten away.
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.FLASH_EMITTER.get(), Direction.EAST, true);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(2 + LaserTuning.Tier.FLASH.range + 2, Y, 8));
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(core.getEnergyStorage().getEnergyStored(), 0, "out of reach");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.flash
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void theFlashEmitterBurnsFuel(GameTestHelper helper) {
        LaserEmitterBlockEntity flash = emitter(helper, new BlockPos(3, Y, 8), ModBlocks.FLASH_EMITTER.get(), Direction.EAST, false);
        flash.getFuel().setStackInSlot(0, new ItemStack(Items.COAL, 2));
        FoldCoreBlockEntity core = sink(helper, new BlockPos(8, Y, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(flash.getFuel().getStackInSlot(0).getCount() < 2, "a coal burning");
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "and its light charging the core");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.demand
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void withNothingToChargeItSendsNothing(GameTestHelper helper) {
        // Pointed at open air: it keeps its power, but for its small idle cost.
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.UP, false);
        emitter.getEnergyStorage().setEnergy(100_000);
        helper.runAfterDelay(40, () -> {
            int left = emitter.getEnergyStorage().getEnergyStored();
            helper.assertTrue(left >= 100_000 - 41 * LaserTuning.Tier.ONE.idle, "only the idle cost: " + left);
            helper.assertValueEqual(emitter.sending(), 0L, "sending nothing");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void redstoneSwitchesAnEmitter(GameTestHelper helper) {
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(12, Y, 8));
        while (emitter.redstone() != RedstoneMode.WITHOUT_SIGNAL) emitter.cycleRedstone();
        helper.setBlock(new BlockPos(3, Y + 1, 8), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(core.getEnergyStorage().getEnergyStored(), 0, "off while powered");
            helper.setBlock(new BlockPos(3, Y + 1, 8), Blocks.AIR);
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "on again without the signal");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.outputs
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void oneEmitterCanShineFromSeveralFaces(GameTestHelper helper) {
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(8, Y, 8), ModBlocks.LASER_EMITTER_2.get(), Direction.EAST, false);
        helper.setBlock(new BlockPos(8, Y - 1, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        emitter.toggleOutput(Direction.WEST);
        FoldCoreBlockEntity east = sink(helper, new BlockPos(14, Y, 8));
        FoldCoreBlockEntity west = sink(helper, new BlockPos(2, Y, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(east.getEnergyStorage().getEnergyStored() > 0, "east");
            helper.assertTrue(west.getEnergyStorage().getEnergyStored() > 0, "and west");
            TestSupport.finishLasers(helper);
        });
    }

    private static com.kadikular.quantimium.block.entity.LaserPrismBlockEntity prism(GameTestHelper helper, BlockPos pos, BlockPos... targets) {
        helper.setBlock(pos, ModBlocks.LASER_PRISM.get());
        var prism = helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.class);
        for (BlockPos target : targets) prism.toggleLink(helper.absolutePos(target));
        return prism;
    }

    // covers: laser.prism.split
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aPrismSendsLightToEachBlockItsLinkedTo(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos a = new BlockPos(13, Y, 12);
        BlockPos b = new BlockPos(12, Y + 3, 3);
        prism(helper, new BlockPos(7, Y, 8), a, b);
        FoldCoreBlockEntity first = sink(helper, a);
        FoldCoreBlockEntity second = sink(helper, b);
        helper.succeedWhen(() -> {
            helper.assertTrue(first.getEnergyStorage().getEnergyStored() > 0, "light at an angle to one");
            helper.assertTrue(second.getEnergyStorage().getEnergyStored() > 0, "and up and across to the other");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.prism.join
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aPrismJoinsTwoEmittersIntoOneBeam(GameTestHelper helper) {
        // Two emitters into one prism: the core gets more than either could send alone.
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        emitter(helper, new BlockPos(8, Y, 2), ModBlocks.LASER_EMITTER_1.get(), Direction.SOUTH, true);
        BlockPos target = new BlockPos(14, Y, 14);
        prism(helper, new BlockPos(8, Y, 8), target);
        FoldCoreBlockEntity core = sink(helper, target);
        long[] before = {-1};
        helper.runAfterDelay(40, () -> before[0] = core.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(60, () -> {
            long gained = core.getEnergyStorage().getEnergyStored() - before[0];
            helper.assertTrue(gained > 20L * LaserTuning.Tier.ONE.rate * 1.5, "both emitters' light: " + gained);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.prism.split
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void somethingOnAPrismsLineStopsIt(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos target = new BlockPos(12, Y, 12);
        prism(helper, new BlockPos(8, Y, 8), target);
        helper.setBlock(new BlockPos(10, Y, 10), Blocks.STONE);
        FoldCoreBlockEntity core = sink(helper, target);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(core.getEnergyStorage().getEnergyStored(), 0, "the stone on the diagonal stops it");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.prism.links
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 20)
    public static void aPrismLinksSoFarAndSoMany(GameTestHelper helper) {
        var prism = prism(helper, new BlockPos(8, Y, 8));
        BlockPos at = helper.absolutePos(new BlockPos(8, Y, 8));
        helper.assertValueEqual(prism.toggleLink(at), com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.Link.SELF, "not itself");
        helper.assertValueEqual(prism.toggleLink(at.east(LaserTuning.PRISM_RANGE + 1)),
                com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.Link.TOO_FAR, "not out of reach");
        for (int i = 1; i <= LaserTuning.PRISM_LINKS; i++) {
            helper.assertValueEqual(prism.toggleLink(at.north(i)), com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.Link.ADDED, "link " + i);
        }
        helper.assertValueEqual(prism.toggleLink(at.south(1)), com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.Link.FULL, "no more");
        helper.assertValueEqual(prism.toggleLink(at.north(1)), com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.Link.REMOVED, "unlinked");
        TestSupport.finishLasers(helper);
        helper.succeed();
    }

    private static com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity cavityMirror(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.CAVITY_MIRROR.get().defaultBlockState().setValue(com.kadikular.quantimium.block.CavityMirrorBlock.FACING, facing));
        return helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity.class);
    }

    // covers: laser.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 200)
    public static void aCavityStoresLightAndGivesItBack(GameTestHelper helper) {
        // An emitter fills one mirror of a four-block cavity; the core behind its back is charged from it,
        // even after the emitter is switched off.
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(5, Y, 3), ModBlocks.LASER_EMITTER_1.get(), Direction.SOUTH, true);
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        var b = cavityMirror(helper, new BlockPos(10, Y, 8), Direction.WEST);
        long[] held = {0};
        long[] charged = {0};
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(a.paired() && b.paired(), "the two mirrors face each other across the gap");
            helper.assertValueEqual(a.capacity(), 4 * LaserTuning.CAVITY_FE_PER_BLOCK, "four blocks of gap");
            helper.assertTrue(a.stored() > 0, "light held: " + a.stored());
            held[0] = a.stored();
            // Off goes the emitter, and on goes a core behind the mirror's back.
            while (emitter.redstone() != RedstoneMode.WITHOUT_SIGNAL) emitter.cycleRedstone();
            helper.setBlock(new BlockPos(5, Y + 1, 3), Blocks.REDSTONE_BLOCK);
            sink(helper, new BlockPos(1, Y, 8));
        });
        helper.runAfterDelay(120, () -> {
            FoldCoreBlockEntity core = helper.getBlockEntity(new BlockPos(1, Y, 8), FoldCoreBlockEntity.class);
            charged[0] = core.getEnergyStorage().getEnergyStored();
            helper.assertTrue(charged[0] > 0, "the core charged from the cavity alone");
            helper.assertTrue(a.stored() < held[0], "the cavity gave it");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.cavity, laser.cavity.leak
    @GameTest(template = TestSupport.FLOOR_17, batch = "laser_cavity_leak", timeoutTicks = 100)
    public static void aCavityLeaksAndABrokenOneLetsItsLightGo(GameTestHelper helper) {
        TestSupport.finishLasers(helper);
        // Leaking is off unless the config turns it on: on, here, alone in its batch.
        double leakWas = com.kadikular.quantimium.Config.CAVITY_LEAK.get();
        com.kadikular.quantimium.Config.CAVITY_LEAK.set(0.00002);
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        cavityMirror(helper, new BlockPos(10, Y, 8), Direction.WEST);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(a.paired(), "paired");
            a.fill(20_000);
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(a.stored() + helper.getBlockEntity(new BlockPos(10, Y, 8),
                    com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity.class).stored() < 20_000, "it leaks");
            var chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(5, Y, 8)));
            double before = chunk.getData(com.kadikular.quantimium.init.ModAttachments.CHUNK_FLUX).pendingFlux();
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            double after = chunk.getData(com.kadikular.quantimium.init.ModAttachments.CHUNK_FLUX).pendingFlux();
            helper.assertTrue(after > before, "broken, its light goes into the field: " + before + " to " + after);
            // The other one lets its light go too: break it before clearing, so none is left for the next test.
            helper.setBlock(new BlockPos(10, Y, 8), Blocks.AIR);
            com.kadikular.quantimium.Config.CAVITY_LEAK.set(leakWas);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.photodetector
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aPhotodetectorReadsWhatPassesUntaxed(GameTestHelper helper) {
        emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos detector = new BlockPos(7, Y, 8);
        helper.setBlock(detector, ModBlocks.PHOTODETECTOR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.PhotodetectorBlock.FACING, Direction.EAST));
        FoldCoreBlockEntity core = sink(helper, new BlockPos(12, Y, 8));
        long[] before = {-1};
        helper.runAfterDelay(40, () -> before[0] = core.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(60, () -> {
            var reader = helper.getBlockEntity(detector, com.kadikular.quantimium.block.entity.PhotodetectorBlockEntity.class);
            // Its reading is the last second's average, which may still be catching up with a beam just lit.
            helper.assertTrue(reader.passing() > 0, "light passing");
            helper.assertValueEqual(reader.signal(), com.kadikular.quantimium.block.entity.PhotodetectorBlockEntity.signalFor(reader.passing()),
                    "the signal for what passes");
            helper.assertValueEqual(helper.getLevel().getSignal(helper.absolutePos(detector.above()), Direction.UP) > 0
                    || helper.getLevel().hasNeighborSignal(helper.absolutePos(detector.above())), true, "a redstone signal");
            long gained = core.getEnergyStorage().getEnergyStored() - before[0];
            helper.assertValueEqual(gained, 20L * LaserTuning.Tier.ONE.rate, "nothing taken on the way");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.share
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void twoEmittersIntoOneBlockShareIt(GameTestHelper helper) {
        // A projector takes 1,000 FE/t: either emitter alone could fill it, but both share the load.
        LaserEmitterBlockEntity west = emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        LaserEmitterBlockEntity north = emitter(helper, new BlockPos(8, Y, 3), ModBlocks.LASER_EMITTER_1.get(), Direction.SOUTH, true);
        helper.setBlock(new BlockPos(8, Y, 8), ModBlocks.DECOHERENCE_PROJECTOR.get());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(west.sending() > 0 && north.sending() > 0, "both send: " + west.sending() + " and " + north.sending());
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.live
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aPoweredEmitterShinesDimlyWithNothingToCharge(GameTestHelper helper) {
        LaserEmitterBlockEntity on = emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        LaserEmitterBlockEntity empty = emitter(helper, new BlockPos(3, Y, 12), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, false);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(on.syncedBeams().stream().allMatch(com.kadikular.quantimium.laser.LaserBeam::live), "powered: its light is on, checking");
            helper.assertValueEqual(on.sending(), 0L, "though it sends nothing");
            helper.assertTrue(empty.syncedBeams().stream().noneMatch(com.kadikular.quantimium.laser.LaserBeam::live), "no power: no light");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.photodetector
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aPhotodetectorDoesNotRefocus(GameTestHelper helper) {
        // A flash emitter reaches 8 blocks. Through a photodetector 3 blocks out, the light goes on for
        // the 4 it has left, not a fresh 32: a core 6 blocks past the detector gets nothing.
        emitter(helper, new BlockPos(1, Y, 8), ModBlocks.FLASH_EMITTER.get(), Direction.EAST, true);
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.PHOTODETECTOR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.PhotodetectorBlock.FACING, Direction.EAST));
        FoldCoreBlockEntity near = sink(helper, new BlockPos(9, Y, 8));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(near.getEnergyStorage().getEnergyStored() > 0, "within what's left");
            helper.setBlock(new BlockPos(9, Y, 8), Blocks.AIR);
            sink(helper, new BlockPos(12, Y, 8));
        });
        helper.runAfterDelay(55, () -> {
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(12, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0,
                    "beyond it");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.prism.links
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aPrismLetsGoOfABlockThatsGone(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos target = new BlockPos(12, Y, 12);
        var prism = prism(helper, new BlockPos(7, Y, 8), target);
        sink(helper, target);
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(prism.targets().size(), 1, "linked");
            helper.setBlock(target, Blocks.AIR);
        });
        helper.runAfterDelay(20 + LaserNetworks.RECHECK_TICKS + 5, () -> {
            helper.assertValueEqual(prism.targets().size(), 0, "the link to a block that's gone is let go");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void bothMirrorsOfACavityHoldItsLight(GameTestHelper helper) {
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        var b = cavityMirror(helper, new BlockPos(10, Y, 8), Direction.WEST);
        helper.runAfterDelay(25, () -> a.fill(20_000));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(b.stored() > 5_000, "the light bounces to the other mirror: " + b.stored());
            helper.assertTrue(Math.abs(a.stored() - b.stored()) < 100, "and evens out");
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            helper.setBlock(new BlockPos(10, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void anEmitterFillsACavityThatsGivingToABlock(GameTestHelper helper) {
        // Emitter into a cavity whose back feeds a void that never fills: the emitter still fills the
        // cavity, and pays only for what its own light reaches.
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        cavityMirror(helper, new BlockPos(9, Y, 8), Direction.WEST);
        helper.setBlock(new BlockPos(13, Y, 8), ModBlocks.CREATIVE_ENERGY_VOID.get());
        var drain = helper.getBlockEntity(new BlockPos(13, Y, 8), com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity.class);
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(emitter.sending() > 0, "the emitter sends into the cavity: " + emitter.sending());
            helper.assertTrue(drain.taking() > 0, "the cavity gives out of its back: " + drain.taking());
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            helper.setBlock(new BlockPos(9, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aCavityWithItsOutputOffOnlyStores(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        cavityMirror(helper, new BlockPos(9, Y, 8), Direction.WEST);
        helper.setBlock(new BlockPos(13, Y, 8), ModBlocks.CREATIVE_ENERGY_VOID.get());
        var drain = helper.getBlockEntity(new BlockPos(13, Y, 8), com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity.class);
        helper.getBlockEntity(new BlockPos(9, Y, 8), com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity.class).toggleOutput();
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(a.stored() > 0, "it fills");
            helper.assertValueEqual(drain.taking(), 0L, "and gives nothing out");
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            helper.setBlock(new BlockPos(9, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.prism.links
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aPrismLetsGoOfGrassThatsGone(GameTestHelper helper) {
        // Light passes through grass and through air alike, so only the block being gone shows it.
        BlockPos target = new BlockPos(12, Y, 12);
        var prism = prism(helper, new BlockPos(7, Y, 8));
        helper.setBlock(target.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(target, Blocks.SHORT_GRASS);
        prism.toggleLink(helper.absolutePos(target));
        helper.runAfterDelay(15, () -> {
            helper.assertValueEqual(prism.targets().size(), 1, "linked");
            helper.setBlock(target, Blocks.AIR);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(prism.targets().size(), 0, "let go");
            helper.succeed();
        });
    }

    // covers: laser.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aCavityMirrorAndALaserMirrorMakeACavity(GameTestHelper helper) {
        // Only one end needs to be a Cavity Mirror: it holds the light, the Laser Mirror holds none.
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(9, Y, 8), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.WEST));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(a.paired(), "paired with the Laser Mirror");
            helper.assertValueEqual(a.capacity(), 3 * LaserTuning.CAVITY_FE_PER_BLOCK, "three blocks of gap");
            a.fill(20_000);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(a.stored() > 19_000, "it keeps what it holds: " + a.stored());
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void anUnpairedCavityMirrorPassesLightOn(GameTestHelper helper) {
        // Alone, light reaching it leaves by its front, as a Laser Mirror's does.
        emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        var mirror = cavityMirror(helper, new BlockPos(7, Y, 8), Direction.NORTH);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(7, Y, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(!mirror.paired(), "nothing faces it");
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "light out of its front");
            TestSupport.finishLasers(helper);
        });
    }
}
