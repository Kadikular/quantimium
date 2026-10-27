package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LaserPortBlock;
import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.laser.IsolatorSettings;
import com.kadikular.quantimium.laser.RedstoneSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;

import java.util.List;

/** The Redstone Lens, and the Isolator Lens's power and redstone switches. */
public final class RedstoneLensTests {

    private static final int Y = 2;

    private RedstoneLensTests() {}

    private static void emitter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(pos.west(), ModBlocks.CREATIVE_ENERGY_CELL.get());
    }

    private static void mount(GameTestHelper helper, BlockPos pos, ItemStack... lenses) {
        helper.setBlock(pos, ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
        LensMountBlockEntity mount = helper.getBlockEntity(pos, LensMountBlockEntity.class);
        for (int i = 0; i < lenses.length; i++) mount.getLensSlots().setStackInSlot(i, lenses[i]);
    }

    private static ItemStack redstone(RedstoneSettings settings) {
        ItemStack lens = new ItemStack(ModItems.REDSTONE_LENS.get());
        lens.set(ModDataComponents.REDSTONE.get(), settings);
        return lens;
    }

    private static ItemStack isolator(IsolatorSettings settings) {
        ItemStack lens = new ItemStack(ModItems.ISOLATOR_LENS.get());
        lens.set(ModDataComponents.ISOLATOR.get(), settings);
        return lens;
    }

    private static BlockPos lamp(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.REDSTONE_LAMP);
        return pos;
    }

    private static boolean lit(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(RedstoneLampBlock.LIT);
    }

    // covers: laser.lens.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 140)
    public static void aRedstoneLensLightsALampAndLetsGo(GameTestHelper helper) {
        // Always on: the lamp it ends on lights, from the air in front of it. Its mount gone, the signal goes too.
        emitter(helper, new BlockPos(1, Y, 4));
        mount(helper, new BlockPos(3, Y, 4), redstone(RedstoneSettings.DEFAULT));
        BlockPos lamp = lamp(helper, new BlockPos(7, Y, 4));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(lit(helper, lamp), "lit by the lens");
            helper.assertBlockPresent(ModBlocks.LASER_SIGNAL.get(), new BlockPos(6, Y, 4));
            helper.setBlock(new BlockPos(3, Y, 4), Blocks.STONE);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertFalse(lit(helper, lamp), "dark once the lens is gone");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, Y, 4));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aRedstoneLensSwitchesAFocusDownTheLight(GameTestHelper helper) {
        // Two Laser Focuses whose emitter listens for a signal: the one a Redstone Lens's light reaches lets light on.
        for (int z : new int[] {2, 9}) {
            emitter(helper, new BlockPos(1, Y, z));
            if (z == 2) mount(helper, new BlockPos(3, Y, z), redstone(RedstoneSettings.DEFAULT));
            else mount(helper, new BlockPos(3, Y, z));
            TestSupport.relay(helper, new BlockPos(6, Y, z), Direction.DOWN, Direction.SOUTH).cycleMode();
        }
        helper.setBlock(new BlockPos(6, Y, 5), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(6, Y, 12), ModBlocks.FOLD_CORE.get());
        var switched = helper.getBlockEntity(new BlockPos(6, Y, 5), FoldCoreBlockEntity.class);
        var unswitched = helper.getBlockEntity(new BlockPos(6, Y, 12), FoldCoreBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(switched.getEnergyStorage().getEnergyStored() > 0, "switched on by the lens's light");
            helper.assertValueEqual(unswitched.getEnergyStorage().getEnergyStored(), 0, "no signal, no light");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aRedstoneLensCountsUpTheLight(GameTestHelper helper) {
        // A Laser Port up the light holds 10 dirt: "5 or more" lights its lamp, "fewer than 5" doesn't.
        int row = 0;
        for (RedstoneSettings.Mode mode : List.of(RedstoneSettings.Mode.AT_LEAST, RedstoneSettings.Mode.BELOW)) {
            int z = 3 + row++ * 6;
            emitter(helper, new BlockPos(1, Y, z));
            helper.setBlock(new BlockPos(3, Y, z), ModBlocks.LASER_PORT.get().defaultBlockState().setValue(LaserPortBlock.FACING, Direction.EAST));
            helper.getBlockEntity(new BlockPos(3, Y, z), LaserPortBlockEntity.class).getItems().setStackInSlot(0, new ItemStack(Items.DIRT, 10));
            mount(helper, new BlockPos(5, Y, z), redstone(new RedstoneSettings(mode, 15, new ItemStack(Items.DIRT), 5)));
            lamp(helper, new BlockPos(9, Y, z));
        }
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(lit(helper, new BlockPos(9, Y, 3)), "10 is 5 or more");
            helper.assertFalse(lit(helper, new BlockPos(9, Y, 9)), "10 isn't fewer than 5");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aRedstoneLensSendsOnItsBlocksSignal(GameTestHelper helper) {
        // Sending on: a lamp lit while a redstone block sits on the lens's mount, dark once it's gone.
        emitter(helper, new BlockPos(1, Y, 8));
        mount(helper, new BlockPos(3, Y, 8), redstone(RedstoneSettings.DEFAULT.withMode(RedstoneSettings.Mode.FORWARD)));
        BlockPos lamp = lamp(helper, new BlockPos(9, Y, 8));
        helper.setBlock(new BlockPos(3, Y + 1, 8), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(lit(helper, lamp), "lit while the mount has a signal");
            helper.setBlock(new BlockPos(3, Y + 1, 8), Blocks.AIR);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(lit(helper, lamp), "dark without");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.isolator
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anIsolatorLensCanStopPowerAndRedstone(GameTestHelper helper) {
        // Power off: the block past it gets nothing. Redstone stopped (as it is unset): a Redstone Lens before it lights
        // nothing; set to pass redstone, it does.
        emitter(helper, new BlockPos(1, Y, 2));
        mount(helper, new BlockPos(3, Y, 2), isolator(IsolatorSettings.POWER_ONLY.withPower(false)));
        helper.setBlock(new BlockPos(7, Y, 2), ModBlocks.FOLD_CORE.get());
        var unpowered = helper.getBlockEntity(new BlockPos(7, Y, 2), FoldCoreBlockEntity.class);
        emitter(helper, new BlockPos(1, Y, 7));
        mount(helper, new BlockPos(3, Y, 7), redstone(RedstoneSettings.DEFAULT), isolator(IsolatorSettings.POWER_ONLY));
        BlockPos stopped = lamp(helper, new BlockPos(7, Y, 7));
        emitter(helper, new BlockPos(1, Y, 12));
        mount(helper, new BlockPos(3, Y, 12), redstone(RedstoneSettings.DEFAULT), isolator(IsolatorSettings.POWER_ONLY.withRedstone(true)));
        BlockPos passed = lamp(helper, new BlockPos(7, Y, 12));
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(unpowered.getEnergyStorage().getEnergyStored(), 0, "no power past it");
            helper.assertFalse(lit(helper, stopped), "redstone stopped");
            helper.assertTrue(lit(helper, passed), "redstone passed");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }
}
