package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LaserRelayBlock;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.util.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
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

    private static BlockState mirror(Direction mount, Direction input, Direction output) {
        return ModBlocks.LASER_MIRROR.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT, mount)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.INPUT, input).setValue(com.kadikular.quantimium.block.LaserMirrorBlock.OUTPUT, output);
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aMirrorTurnsLightBothWays(GameTestHelper helper) {
        // On the floor, turning between west and north: light from the west goes north, light from the north goes west.
        emitter(helper, new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        helper.setBlock(new BlockPos(6, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        FoldCoreBlockEntity north = sink(helper, new BlockPos(6, Y, 3));
        emitter(helper, new BlockPos(10, Y, 13), ModBlocks.LASER_EMITTER_1.get(), Direction.NORTH, true);
        helper.setBlock(new BlockPos(10, Y, 9), mirror(Direction.DOWN, Direction.SOUTH, Direction.EAST));
        FoldCoreBlockEntity east = sink(helper, new BlockPos(14, Y, 9));
        helper.succeedWhen(() -> {
            helper.assertTrue(north.getEnergyStorage().getEnergyStored() > 0, "turned from west to north");
            helper.assertTrue(east.getEnergyStorage().getEnergyStored() > 0, "and from south to east");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aMirrorTurnsLightFromItsOtherSideToo(GameTestHelper helper) {
        // Set to turn west and north, lit from the north: the light goes out west.
        emitter(helper, new BlockPos(6, Y, 2), ModBlocks.LASER_EMITTER_1.get(), Direction.SOUTH, true);
        helper.setBlock(new BlockPos(6, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        FoldCoreBlockEntity west = sink(helper, new BlockPos(1, Y, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(west.getEnergyStorage().getEnergyStored() > 0, "out of its other side");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 80)
    public static void aMirrorStopsLightFromItsBack(GameTestHelper helper) {
        // Turning west and north, lit from the east: it hits the back of the plate, and nothing goes on.
        emitter(helper, new BlockPos(12, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.WEST, true);
        helper.setBlock(new BlockPos(6, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        FoldCoreBlockEntity north = sink(helper, new BlockPos(6, Y, 3));
        FoldCoreBlockEntity west = sink(helper, new BlockPos(1, Y, 8));
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(north.getEnergyStorage().getEnergyStored(), 0, "nothing north");
            helper.assertValueEqual(west.getEnergyStorage().getEnergyStored(), 0, "nothing on through");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 160)
    public static void aPoweredMirrorIsOff(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        helper.setBlock(new BlockPos(6, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        FoldCoreBlockEntity north = sink(helper, new BlockPos(6, Y, 3));
        helper.setBlock(new BlockPos(6, Y, 9), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(north.getEnergyStorage().getEnergyStored(), 0, "a signal turns it off");
            helper.setBlock(new BlockPos(6, Y, 9), net.minecraft.world.level.block.Blocks.AIR);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(north.getEnergyStorage().getEnergyStored() > 0, "and back on without it");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 20)
    public static void aMirrorOnlyTurnsThroughARightAngle(GameTestHelper helper) {
        helper.assertTrue(com.kadikular.quantimium.block.LaserMirrorBlock.turns(Direction.DOWN, Direction.WEST, Direction.NORTH), "west to north, on the floor");
        helper.assertTrue(com.kadikular.quantimium.block.LaserMirrorBlock.turns(Direction.DOWN, Direction.UP, Direction.EAST), "from above, to the east");
        helper.assertFalse(com.kadikular.quantimium.block.LaserMirrorBlock.turns(Direction.DOWN, Direction.WEST, Direction.EAST), "not straight on");
        helper.assertTrue(com.kadikular.quantimium.block.LaserMirrorBlock.turns(Direction.DOWN, Direction.WEST, Direction.WEST), "flat, straight back");
        helper.assertFalse(com.kadikular.quantimium.block.LaserMirrorBlock.turns(Direction.DOWN, Direction.WEST, Direction.DOWN), "not into its mount");
        helper.assertFalse(com.kadikular.quantimium.block.LaserMirrorBlock.turns(Direction.DOWN, Direction.DOWN, Direction.NORTH), "not from its mount");
        helper.succeed();
    }

    // covers: laser.power
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void anEmitterPowersAMachineAcrossAGap(GameTestHelper helper) {
        emitter(helper, new BlockPos(3, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(12, Y, 8));
        helper.succeedWhen(() -> helper.assertTrue(core.getEnergyStorage().getEnergyStored() >= LaserTuning.Tier.ONE.rate * 10,
                "the core is charged by light: " + core.getEnergyStorage().getEnergyStored()));
    }

    private static void solar(GameTestHelper helper, BlockPos pos, Direction output) {
        helper.setBlock(pos, ModBlocks.SOLAR_EMITTER.get().defaultBlockState().setValue(com.kadikular.quantimium.block.SolarEmitterBlock.OUTPUT, output));
    }

    /**
     * Noon, clear skies: each of these tests has a batch of its own, since the time is the whole level's. What it
     * was is put back after, so later tests (a zombie that would burn) don't find it day.
     */
    private static long noon(GameTestHelper helper) {
        long was = helper.getLevel().getDefaultClockTime();
        helper.setTime(6000);
        helper.getLevel().resetWeatherCycle();
        return was;
    }

    // covers: laser.solar
    @GameTest(template = TestSupport.FLOOR_17, batch = "laser_solar_night", timeoutTicks = 200)
    public static void aSolarEmitterShinesByDayAndNotByNight(GameTestHelper helper) {
        long was = noon(helper);
        solar(helper, new BlockPos(6, Y, 8), Direction.EAST);
        FoldCoreBlockEntity core = sink(helper, new BlockPos(10, Y, 8));
        long[] at = new long[2];
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "charged in the sun");
            helper.setTime(18000);
        });
        helper.runAfterDelay(100, () -> at[0] = core.getEnergyStorage().getEnergyStored());
        helper.runAfterDelay(140, () -> {
            helper.assertValueEqual((long) core.getEnergyStorage().getEnergyStored(), at[0], "nothing at midnight");
            helper.setTime(was);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.solar
    @GameTest(template = TestSupport.FLOOR_17, batch = "laser_solar_roof", timeoutTicks = 100)
    public static void aSolarEmitterUnderARoofMakesNothing(GameTestHelper helper) {
        // A roof three up, open at the sides, shades it; glass over the other lets the sky through.
        long was = noon(helper);
        solar(helper, new BlockPos(4, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(4, Y + 3, 8), Blocks.STONE);
        solar(helper, new BlockPos(12, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(12, Y + 3, 8), Blocks.GLASS);
        helper.runAfterDelay(50, () -> {
            var roofed = helper.getBlockEntity(new BlockPos(4, Y, 8), com.kadikular.quantimium.block.entity.SolarEmitterBlockEntity.class);
            var glazed = helper.getBlockEntity(new BlockPos(12, Y, 8), com.kadikular.quantimium.block.entity.SolarEmitterBlockEntity.class);
            helper.assertValueEqual(roofed.sun(), 0, "no sun under a roof");
            helper.assertTrue(glazed.sun() > 0, "sun through glass");
            helper.setTime(was);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.solar
    @GameTest(template = TestSupport.FLOOR_17, batch = "laser_solar_chain", timeoutTicks = 200)
    public static void solarEmittersChainUpToEight(GameTestHelper helper) {
        // Three in a row give three's worth; ten in a row, only eight's.
        long was = noon(helper);
        for (int x = 1; x <= 3; x++) solar(helper, new BlockPos(x, Y, 3), Direction.EAST);
        FoldCoreBlockEntity three = sink(helper, new BlockPos(6, Y, 3));
        for (int x = 1; x <= 10; x++) solar(helper, new BlockPos(x, Y, 12), Direction.EAST);
        FoldCoreBlockEntity ten = sink(helper, new BlockPos(13, Y, 12));
        long[] before = new long[2];
        helper.runAfterDelay(60, () -> {
            before[0] = three.getEnergyStorage().getEnergyStored();
            before[1] = ten.getEnergyStorage().getEnergyStored();
        });
        helper.runAfterDelay(100, () -> {
            long gotThree = three.getEnergyStorage().getEnergyStored() - before[0];
            long gotTen = ten.getEnergyStorage().getEnergyStored() - before[1];
            helper.assertTrue(gotThree > 40L * 2 * LaserTuning.SOLAR_FE && gotThree <= 40L * 3 * LaserTuning.SOLAR_FE, "three's worth: " + gotThree);
            helper.assertTrue(gotTen > 40L * 7 * LaserTuning.SOLAR_FE && gotTen <= 40L * LaserTuning.SOLAR_CHAIN_MAX, "eight's worth at most: " + gotTen);
            // The first of a chain feeds the next: its beam shows lit, carrying what it makes.
            var first = LaserNetworks.of(helper.getLevel()).beamsOf(helper.absolutePos(new BlockPos(1, Y, 3)));
            helper.assertTrue(first.stream().anyMatch(beam -> beam.live() && beam.flow() > 0), "a feeding emitter's beam shows: " + first);
            helper.setTime(was);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.solar
    @GameTest(template = TestSupport.FLOOR_17, batch = "laser_solar_pass", timeoutTicks = 200)
    public static void aSolarEmitterPassesAnyLightUpToItsLimit(GameTestHelper helper) {
        // A Laser Emitter I (2,000 FE/t) shining through a Solar Emitter: only 160 FE/t gets out, its own included.
        // And a Solar into a mirror, into another Solar: the first's light gets through too.
        long was = noon(helper);
        emitter(helper, new BlockPos(1, Y, 3), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        solar(helper, new BlockPos(5, Y, 3), Direction.EAST);
        FoldCoreBlockEntity capped = sink(helper, new BlockPos(9, Y, 3));
        solar(helper, new BlockPos(2, Y, 12), Direction.EAST);
        helper.setBlock(new BlockPos(6, Y, 12), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        solar(helper, new BlockPos(6, Y, 9), Direction.NORTH);
        FoldCoreBlockEntity chained = sink(helper, new BlockPos(6, Y, 6));
        long[] before = new long[2];
        helper.runAfterDelay(60, () -> {
            before[0] = capped.getEnergyStorage().getEnergyStored();
            before[1] = chained.getEnergyStorage().getEnergyStored();
        });
        helper.runAfterDelay(100, () -> {
            long gotCapped = capped.getEnergyStorage().getEnergyStored() - before[0];
            long gotChained = chained.getEnergyStorage().getEnergyStored() - before[1];
            helper.assertTrue(gotCapped > 40L * (LaserTuning.SOLAR_CHAIN_MAX - 20) && gotCapped <= 40L * LaserTuning.SOLAR_CHAIN_MAX,
                    "no more than the limit through it: " + gotCapped);
            helper.assertTrue(gotChained > 40L * (LaserTuning.SOLAR_FE + LaserTuning.SOLAR_FE / 2), "both Solars' light, by the mirror: " + gotChained);
            helper.setTime(was);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.demand
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aBigBufferDoesntStarveTheMachineBesideIt(GameTestHelper helper) {
        // One emitter's 2,000 FE/t split two ways: a Quantum Crafter that would take it all, and a Decoherence Projector
        // that takes at most 1,000. Short of power, each gets a fair share: the Projector all it asks, not a third less.
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos relay = new BlockPos(8, Y, 8);
        helper.setBlock(relay, ModBlocks.LASER_RELAY.get().defaultBlockState().setValue(LaserRelayBlock.FACING, Direction.NORTH));
        helper.getBlockEntity(relay, com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class).addEmitter(Direction.SOUTH);
        helper.setBlock(new BlockPos(8, Y, 4), ModBlocks.QUANTUM_CRAFTER.get());
        var crafter = helper.getBlockEntity(new BlockPos(8, Y, 4), com.kadikular.quantimium.block.entity.QuantumCrafterBlockEntity.class);
        helper.setBlock(new BlockPos(8, Y, 12), ModBlocks.DECOHERENCE_PROJECTOR.get());
        var core = helper.getBlockEntity(new BlockPos(8, Y, 12), com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity.class);
        long[] before = new long[2];
        helper.runAfterDelay(40, () -> {
            before[0] = crafter.getEnergyStorage().getEnergyStored();
            before[1] = core.getEnergyStorage().getEnergyStored();
        });
        helper.runAfterDelay(60, () -> {
            long big = crafter.getEnergyStorage().getEnergyStored() - before[0];
            long small = core.getEnergyStorage().getEnergyStored() - before[1];
            helper.assertTrue(small >= 20L * com.kadikular.quantimium.block.entity.DecoherenceProjectorBlockEntity.MAX_RECEIVE * 9 / 10,
                    "the Projector got all it asks: " + small + " in 20 ticks, beside " + big);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.relay
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aRelayTurnsTheBeamAndTakesItsTax(GameTestHelper helper) {
        emitter(helper, new BlockPos(3, Y, 3), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        BlockPos mirror = new BlockPos(9, Y, 3);
        helper.setBlock(mirror, ModBlocks.LASER_RELAY.get().defaultBlockState().setValue(LaserRelayBlock.FACING, Direction.SOUTH));
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

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 20)
    public static void clickingANewSideOfAFlatMirrorTurnsItToWhereItFaced(GameTestHelper helper) {
        BlockPos at = new BlockPos(8, Y, 8);
        helper.setBlock(at, mirror(Direction.DOWN, Direction.NORTH, Direction.NORTH));
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(at);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), Direction.EAST, pos, false));
        var state = helper.getLevel().getBlockState(pos);
        helper.assertValueEqual(state.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.INPUT), Direction.EAST, "in by the side clicked");
        helper.assertValueEqual(state.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.OUTPUT), Direction.NORTH, "out where it faced");
        helper.succeed();
    }

    // covers: laser.relay, laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void noLightGetsInThroughABase(GameTestHelper helper) {
        // A Relay and a Laser Mirror each stood on glass, with an emitter shining through the glass into the base.
        emitter(helper, new BlockPos(3, Y, 3), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        helper.setBlock(new BlockPos(8, Y, 3), Blocks.GLASS);
        helper.setBlock(new BlockPos(9, Y, 3), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(LaserRelayBlock.FACING, Direction.SOUTH).setValue(LaserRelayBlock.MOUNT, Direction.WEST));
        FoldCoreBlockEntity relayed = sink(helper, new BlockPos(9, Y, 7));
        emitter(helper, new BlockPos(3, Y, 10), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        helper.setBlock(new BlockPos(8, Y, 10), Blocks.GLASS);
        helper.setBlock(new BlockPos(9, Y, 10), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT, Direction.WEST)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.INPUT, Direction.NORTH)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.OUTPUT, Direction.SOUTH));
        FoldCoreBlockEntity mirrored = sink(helper, new BlockPos(9, Y, 14));
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(relayed.getEnergyStorage().getEnergyStored(), 0, "nothing through the Relay's base");
            helper.assertValueEqual(mirrored.getEnergyStorage().getEnergyStored(), 0, "nothing through the Mirror's base");
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
            BlockState shown = helper.getBlockState(new BlockPos(8, Y, 8));
            helper.assertTrue(shown.getValue(LaserEmitterBlock.OUTPUTS.get(Direction.EAST)) && shown.getValue(LaserEmitterBlock.OUTPUTS.get(Direction.WEST))
                    && !shown.getValue(LaserEmitterBlock.OUTPUTS.get(Direction.NORTH)), "the block shows both its outputs");
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
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 20)
    public static void sneakClickingACavityEmitterAnywhereTakesItOff(GameTestHelper helper) {
        // Its south emitter, clicked on its top rather than its end, goes, as a Relay's does.
        BlockPos pos = new BlockPos(8, 2, 8);
        var mirror = cavityMirror(helper, pos, Direction.NORTH);
        helper.assertTrue(mirror.outputFaces().contains(Direction.SOUTH), "it starts with a south output");
        var player = TestSupport.player(helper, new BlockPos(8, 2, 11));
        player.setShiftKeyDown(true);
        BlockPos at = helper.absolutePos(pos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atLowerCornerOf(at).add(8 / 16.0, 11 / 16.0, 13 / 16.0), Direction.UP, at, false);
        helper.getLevel().getBlockState(at).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertFalse(mirror.outputFaces().contains(Direction.SOUTH), "the south emitter went");
        helper.succeed();
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
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 120)
    public static void aCavityGivesOutByTheOutputsItsSetTo(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        var far = cavityMirror(helper, new BlockPos(9, Y, 8), Direction.WEST);
        helper.assertFalse(far.toggleOutput(Direction.WEST), "never an output on its mirror");
        far.toggleOutput(Direction.EAST);
        far.toggleOutput(Direction.NORTH);
        FoldCoreBlockEntity side = sink(helper, new BlockPos(9, Y, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(side.getEnergyStorage().getEnergyStored() > 0, "what it holds leaves by its side output");
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            helper.setBlock(new BlockPos(9, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
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
        // Its one output, its back, turned off.
        helper.getBlockEntity(new BlockPos(9, Y, 8), com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity.class).toggleOutput(Direction.EAST);
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
    public static void aCavityMirrorAndALaserRelayMakeACavity(GameTestHelper helper) {
        // Only one end needs to be a Cavity Mirror: it holds the light, the Laser Relay holds none.
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(9, Y, 8), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.WEST));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(a.paired(), "paired with the Laser Relay");
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

    // covers: laser.cavity, laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 60)
    public static void aFlatLaserMirrorClosesACavity(GameTestHelper helper) {
        // A Laser Mirror set flat, facing back across the gap: the far end of the cavity. Turning, it isn't.
        var a = cavityMirror(helper, new BlockPos(5, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(9, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.WEST));
        var b = cavityMirror(helper, new BlockPos(5, Y, 12), Direction.EAST);
        helper.setBlock(new BlockPos(9, Y, 12), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(a.paired(), "paired with the flat mirror");
            helper.assertValueEqual(a.capacity(), 3 * LaserTuning.CAVITY_FE_PER_BLOCK, "three blocks of gap");
            helper.assertFalse(b.paired(), "a turning mirror closes nothing");
            helper.setBlock(new BlockPos(5, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror
    // Alone in its batch: other tests building and breaking would rebuild the network too.
    @GameTest(template = TestSupport.FLOOR_17, batch = "laser_settled", timeoutTicks = 200)
    public static void mirrorsLeaveTheNetworkSettled(GameTestHelper helper) {
        // One lit, one never lit: once the light has settled, nothing should keep the network rebuilding.
        emitter(helper, new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_1.get(), Direction.EAST, true);
        helper.setBlock(new BlockPos(6, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.NORTH));
        sink(helper, new BlockPos(6, Y, 3));
        helper.setBlock(new BlockPos(12, Y, 12), mirror(Direction.DOWN, Direction.SOUTH, Direction.EAST));
        long[] settled = {-1};
        helper.runAfterDelay(80, () -> settled[0] = LaserNetworks.of(helper.getLevel()).rebuilds());
        helper.runAfterDelay(180, () -> {
            helper.assertValueEqual(LaserNetworks.of(helper.getLevel()).rebuilds(), settled[0], "no rebuilds once settled");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lasers", timeoutTicks = 100)
    public static void aFlatMirrorSendsLightBack(GameTestHelper helper) {
        // Light from the north, turned east by one mirror, into a flat one facing west: it goes straight back west.
        emitter(helper, new BlockPos(4, Y, 2), ModBlocks.LASER_EMITTER_1.get(), Direction.SOUTH, true);
        helper.setBlock(new BlockPos(4, Y, 8), mirror(Direction.DOWN, Direction.NORTH, Direction.EAST));
        helper.setBlock(new BlockPos(10, Y, 8), mirror(Direction.DOWN, Direction.WEST, Direction.WEST));
        helper.succeedWhen(() -> {
            var beams = LaserNetworks.of(helper.getLevel()).beamsOf(helper.absolutePos(new BlockPos(10, Y, 8)));
            helper.assertTrue(beams.stream().anyMatch(beam -> beam.face() == Direction.WEST && beam.live()), "light leaves the flat mirror west: " + beams);
            TestSupport.finishLasers(helper);
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
