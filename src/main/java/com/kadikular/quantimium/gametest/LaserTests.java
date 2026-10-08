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
        });
    }
}
