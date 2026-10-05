package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.HarvestLaserBlock;
import com.kadikular.quantimium.block.RiftLensBlock;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Veil Harvester: a Harvest Laser firing through a Rift Lens at a crystal beyond it. See wiki:
 * blocks/harvest_laser.
 */
public final class HarvesterTests {

    private static final BlockPos LASER = new BlockPos(3, 2, 8);

    private HarvesterTests() {}

    /**
     * Laser pointing east, a ring across x {@code ring} blocks out and a crystal of {@code age}
     * {@code crystal} blocks out pointing back, on Budding Anomalite.
     */
    private static HarvestLaserBlockEntity line(GameTestHelper helper, int ring, int crystal, int age) {
        helper.setBlock(LASER.east(crystal + 1), ModBlocks.BUDDING_ANOMALITE.get());
        helper.setBlock(LASER.east(crystal), ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(AnomaliteCrystalBlock.FACING, Direction.WEST).setValue(AnomaliteCrystalBlock.AGE, age));
        helper.setBlock(LASER.east(ring), ModBlocks.RIFT_LENS.get().defaultBlockState().setValue(RiftLensBlock.AXIS, Direction.Axis.X));
        helper.setBlock(LASER, ModBlocks.HARVEST_LASER.get().defaultBlockState().setValue(HarvestLaserBlock.FACING, Direction.EAST));
        HarvestLaserBlockEntity laser = helper.getBlockEntity(LASER, HarvestLaserBlockEntity.class);
        TestSupport.fill(laser.getEnergyStorage());
        return laser;
    }

    // covers: harvester.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_harvest", timeoutTicks = 400)
    public static void itShattersAFullCrystalForItsShards(GameTestHelper helper) {
        HarvestLaserBlockEntity laser = line(helper, 2, 3, 3);
        BlockPos ring = LASER.east(2);
        BlockPos crystal = LASER.east(3);
        helper.runAfterDelay(20, () -> {
            helper.assertBlockProperty(LASER, HarvestLaserBlock.ACTIVE, true);
            helper.assertBlockProperty(ring, RiftLensBlock.OPEN, true);
            helper.assertBlockProperty(crystal, AnomaliteCrystalBlock.AGE, 3);
        });
        helper.runAfterDelay(HarvestLaserBlockEntity.HARVEST_TICKS + 20, () -> {
            helper.assertBlockNotPresent(ModBlocks.ANOMALITE_CRYSTAL.get(), crystal);
            helper.assertTrue(laser.getOutput().getStackInSlot(0).is(ModItems.ANOMALITE_SHARD.get()), "shards");
            helper.assertValueEqual(laser.getOutput().getStackInSlot(0).getCount(), HarvestLaserBlockEntity.SHARDS, "shards");
            helper.assertBlockProperty(LASER, HarvestLaserBlock.ACTIVE, false);
            helper.assertBlockProperty(ring, RiftLensBlock.OPEN, false);
            helper.succeed();
        });
    }

    // covers: harvester.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_growing", timeoutTicks = 60)
    public static void itWaitsForTheCrystalToGrowFull(GameTestHelper helper) {
        HarvestLaserBlockEntity laser = line(helper, 1, 2, 2);
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.GROWING, "a stage short of full");
            helper.assertBlockProperty(LASER, HarvestLaserBlock.ACTIVE, false);
            helper.assertBlockProperty(LASER.east(1), RiftLensBlock.OPEN, false);
            helper.succeed();
        });
    }

    // covers: harvester.line
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_line", timeoutTicks = 80)
    public static void itNeedsAClearLineThroughTheRing(GameTestHelper helper) {
        // The furthest it reaches, the ring right up against the laser.
        HarvestLaserBlockEntity laser = line(helper, 1, HarvestLaserBlockEntity.REACH, 3);
        BlockPos ring = LASER.east(1);
        BlockPos between = LASER.east(3);
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.FIRING, "at full reach");
            helper.assertBlockProperty(ring, RiftLensBlock.OPEN, true);
            helper.setBlock(between, Blocks.STONE);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.BLOCKED, "stone between ring and crystal");
            helper.assertBlockProperty(ring, RiftLensBlock.OPEN, false);
            helper.setBlock(between, Blocks.AIR);
            // Turned the wrong way, the ring isn't on the line.
            helper.setBlock(ring, ModBlocks.RIFT_LENS.get().defaultBlockState().setValue(RiftLensBlock.AXIS, Direction.Axis.Z));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.NO_RING, "ring across the beam");
            // Moved to just before the crystal: anywhere between will do.
            helper.setBlock(ring, Blocks.AIR);
            helper.setBlock(LASER.east(5), ModBlocks.RIFT_LENS.get().defaultBlockState().setValue(RiftLensBlock.AXIS, Direction.Axis.X));
        });
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.FIRING, "ring beside the crystal");
            helper.assertBlockProperty(LASER.east(5), RiftLensBlock.OPEN, true);
            helper.destroyBlock(LASER);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertBlockProperty(LASER.east(5), RiftLensBlock.OPEN, false);
            helper.succeed();
        });
    }

    // covers: harvester.line
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_reach", timeoutTicks = 40)
    public static void itReachesNoFurtherThanSixBlocks(GameTestHelper helper) {
        HarvestLaserBlockEntity laser = line(helper, 3, HarvestLaserBlockEntity.REACH + 1, 3);
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.NO_CRYSTAL, "one past its reach");
            helper.succeed();
        });
    }

    // covers: harvester.line
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_side", timeoutTicks = 40)
    public static void itReachesACrystalFromTheSide(GameTestHelper helper) {
        // A crystal growing up out of a host below the line, not pointing back at the laser.
        HarvestLaserBlockEntity laser = line(helper, 2, 4, 3);
        BlockPos crystal = LASER.east(4);
        helper.setBlock(crystal.east(), Blocks.AIR);
        helper.setBlock(crystal.below(), ModBlocks.BUDDING_ANOMALITE.get());
        helper.setBlock(crystal, ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(AnomaliteCrystalBlock.FACING, Direction.UP).setValue(AnomaliteCrystalBlock.AGE, 3));
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.FIRING, "a crystal side-on");
            helper.succeed();
        });
    }

    // covers: harvester.lens
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_ring", timeoutTicks = 20)
    public static void theRingHoldsOnAndTurnsToTheLaser(GameTestHelper helper) {
        BlockPos low = new BlockPos(8, 2, 4);
        BlockPos high = low.above();
        BlockState ring = ModBlocks.RIFT_LENS.get().defaultBlockState();
        helper.setBlock(low, RiftLensBlock.turnedTo(ring, Direction.Axis.X, helper.getLevel(), helper.absolutePos(low)));
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.DOWN), true);
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.NORTH), false);
        // Stacked the same way, they hold each other.
        helper.setBlock(high, RiftLensBlock.turnedTo(ring, Direction.Axis.X, helper.getLevel(), helper.absolutePos(high)));
        helper.assertBlockProperty(high, RiftLensBlock.STRUTS.get(Direction.DOWN), true);
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.UP), true);
        // Never a strut along the beam, even to something solid.
        helper.setBlock(low.east(), Blocks.STONE);
        helper.assertBlockProperty(low, RiftLensBlock.STRUTS.get(Direction.EAST), false);

        // A laser placed with a ring on its line turns the ring to take the beam.
        BlockPos across = new BlockPos(8, 2, 12);
        helper.setBlock(across, ring.setValue(RiftLensBlock.AXIS, Direction.Axis.X));
        helper.setBlock(across.north(3), ModBlocks.HARVEST_LASER.get().defaultBlockState()
                .setValue(HarvestLaserBlock.FACING, Direction.SOUTH));
        helper.assertBlockProperty(across, RiftLensBlock.AXIS, Direction.Axis.Z);
        helper.succeed();
    }

    // covers: harvester.wrench
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_wrench", timeoutTicks = 20)
    public static void aWrenchTurnsTheLaserAndTheLens(GameTestHelper helper) {
        BlockPos laser = new BlockPos(4, 3, 4);
        helper.setBlock(laser, ModBlocks.HARVEST_LASER.get().defaultBlockState().setValue(HarvestLaserBlock.FACING, Direction.DOWN));
        for (Direction expected : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
                Direction.EAST, Direction.DOWN}) {
            HarvestLaserBlock.turn(helper.getLevel(), helper.absolutePos(laser), helper.getBlockState(laser));
            helper.assertBlockProperty(laser, HarvestLaserBlock.FACING, expected);
        }
        BlockPos lens = new BlockPos(10, 2, 4);
        helper.setBlock(lens, RiftLensBlock.turnedTo(ModBlocks.RIFT_LENS.get().defaultBlockState(), Direction.Axis.X,
                helper.getLevel(), helper.absolutePos(lens)));
        RiftLensBlock.turn(helper.getLevel(), helper.absolutePos(lens), helper.getBlockState(lens));
        helper.assertBlockProperty(lens, RiftLensBlock.AXIS, Direction.Axis.Y);
        // Lying flat, it has nothing below to hold on to any more.
        helper.assertBlockProperty(lens, RiftLensBlock.STRUTS.get(Direction.DOWN), false);
        RiftLensBlock.turn(helper.getLevel(), helper.absolutePos(lens), helper.getBlockState(lens));
        helper.assertBlockProperty(lens, RiftLensBlock.AXIS, Direction.Axis.Z);
        helper.assertBlockProperty(lens, RiftLensBlock.STRUTS.get(Direction.DOWN), true);
        helper.succeed();
    }

    private static final BlockPos HALL = new BlockPos(8, 2, 8);

    // covers: harvester.thread
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_thread", timeoutTicks = 120)
    public static void itDrawsThreadFromAHeldVeiledThroughTheGlass(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, HALL, 1, 16);
        hall.contain();
        // Seated on the hall's east wall at the middle of the glass, the laser right behind it.
        BlockPos lens = HALL.east(2).above(2);
        BlockPos laserAt = HALL.east(3).above(2);
        helper.setBlock(lens, RiftLensBlock.turnedTo(ModBlocks.RIFT_LENS.get().defaultBlockState(), Direction.Axis.X,
                helper.getLevel(), helper.absolutePos(lens)));
        helper.setBlock(laserAt, ModBlocks.HARVEST_LASER.get().defaultBlockState().setValue(HarvestLaserBlock.FACING, Direction.WEST));
        HarvestLaserBlockEntity laser = helper.getBlockEntity(laserAt, HarvestLaserBlockEntity.class);
        TestSupport.fill(laser.getEnergyStorage());
        helper.assertBlockProperty(lens, RiftLensBlock.NEGATIVE, RiftLensBlock.Seat.GLASS);
        helper.assertBlockProperty(lens, RiftLensBlock.POSITIVE, RiftLensBlock.Seat.NONE);

        // A second laser on the south wall waits its turn.
        BlockPos otherLens = HALL.south(2).above(1);
        BlockPos otherAt = HALL.south(4).above(1);
        helper.setBlock(otherLens, RiftLensBlock.turnedTo(ModBlocks.RIFT_LENS.get().defaultBlockState(), Direction.Axis.Z,
                helper.getLevel(), helper.absolutePos(otherLens)));
        helper.setBlock(otherAt, ModBlocks.HARVEST_LASER.get().defaultBlockState().setValue(HarvestLaserBlock.FACING, Direction.NORTH));
        HarvestLaserBlockEntity other = helper.getBlockEntity(otherAt, HarvestLaserBlockEntity.class);
        TestSupport.fill(other.getEnergyStorage());

        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.DRAWING, "drawing through the glass");
            helper.assertTrue(laser.isDrawing(), "its target is the Veiled");
            helper.assertValueEqual(other.line(), HarvestLaserBlockEntity.Line.HALL_BUSY, "one laser to a hall");
            helper.assertBlockProperty(lens, RiftLensBlock.OPEN, true);
            laser.nearlyDone(5);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(laser.getOutput().getStackInSlot(HarvestLaserBlockEntity.THREAD_SLOT).getCount(), 1, "a thread");
            helper.assertTrue(hall.isOccupied(), "the Veiled is still held");
            // Freed of the laser, the hall lets the other one have its turn.
            helper.destroyBlock(laserAt);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(other.line(), HarvestLaserBlockEntity.Line.DRAWING, "its turn now");
            TestSupport.removeHall(helper, HALL);
            helper.succeed();
        });
    }

    // covers: harvester.thread
    @GameTest(template = TestSupport.FLOOR_17, batch = "harvester_empty_hall", timeoutTicks = 40)
    public static void anEmptyHallsGlassIsJustAWall(GameTestHelper helper) {
        TestSupport.readyHall(helper, HALL, 1, 16);
        BlockPos lens = HALL.east(2).above(2);
        helper.setBlock(lens, ModBlocks.RIFT_LENS.get().defaultBlockState().setValue(RiftLensBlock.AXIS, Direction.Axis.X));
        helper.setBlock(HALL.east(4).above(2), ModBlocks.HARVEST_LASER.get().defaultBlockState().setValue(HarvestLaserBlock.FACING, Direction.WEST));
        HarvestLaserBlockEntity laser = helper.getBlockEntity(HALL.east(4).above(2), HarvestLaserBlockEntity.class);
        TestSupport.fill(laser.getEnergyStorage());
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(laser.line(), HarvestLaserBlockEntity.Line.BLOCKED, "nothing held, nothing to draw");
            TestSupport.removeHall(helper, HALL);
            helper.succeed();
        });
    }
}
