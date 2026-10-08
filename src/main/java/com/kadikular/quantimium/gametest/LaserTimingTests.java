package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.block.entity.CreativeEnergyVoidBlockEntity;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LaserPrismBlockEntity;
import com.kadikular.quantimium.gametest.TickTiming.Scenario;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.laser.LaserNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick timings of laser networks: everything the level's laser manager does in a tick (rechecking
 * beams, moving power, settling), for a base's worth of beams. Each scenario runs alone in its batch,
 * so the level holds only its own nodes.
 */
public final class LaserTimingTests {

    private static final int Y = 2;

    private LaserTimingTests() {}

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_laser_grid_busy", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void gridBusy(GameTestHelper helper) {
        List<CreativeEnergyVoidBlockEntity> voids = new ArrayList<>();
        grid(helper, pos -> {
            helper.setBlock(pos, ModBlocks.CREATIVE_ENERGY_VOID.get());
            voids.add(helper.getBlockEntity(pos, CreativeEnergyVoidBlockEntity.class));
        });
        TickTiming.measure(helper, new Scenario("laser.grid_busy", "Laser networks",
                        "Four Emitter IIs into four prisms sharing 16 blocks that always take power, plus a line of 12 mirrors", 60),
                LaserNetworks.of(helper.getLevel()), () -> {
                    long taking = 0;
                    for (CreativeEnergyVoidBlockEntity drain : voids) taking += drain.taking();
                    return String.format(java.util.Locale.ROOT, "%,d FE/t delivered", taking);
                }, () -> {});
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_laser_grid_idle", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void gridIdle(GameTestHelper helper) {
        List<FoldCoreBlockEntity> cores = new ArrayList<>();
        grid(helper, pos -> {
            helper.setBlock(pos, ModBlocks.FOLD_CORE.get());
            cores.add(helper.getBlockEntity(pos, FoldCoreBlockEntity.class));
        });
        // Every block full: the network only checks.
        helper.onEachTick(() -> cores.forEach(core -> TestSupport.fill(core.getEnergyStorage())));
        TickTiming.measure(helper, new Scenario("laser.grid_idle", "Laser networks",
                        "As busy, with every block full: nothing asks for power", 40),
                LaserNetworks.of(helper.getLevel()), () -> cores.size() + " full blocks", () -> {});
    }

    /**
     * A base's grid: four Emitter IIs in a row, each into a prism linked to eight of 16 blocks, so the
     * prisms share them; and an Emitter III along a 12-mirror zigzag into one more.
     */
    private static void grid(GameTestHelper helper, java.util.function.Consumer<BlockPos> sink) {
        List<BlockPos> sinks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            sinks.add(new BlockPos(1 + 2 * i, Y, 13));
            sinks.add(new BlockPos(1 + 2 * i, Y + 3, 16));
        }
        sinks.forEach(sink);
        for (int i = 0; i < 4; i++) {
            int x = 2 + 4 * i;
            emitter(helper, new BlockPos(x, Y, 1), ModBlocks.LASER_EMITTER_2.get(), Direction.SOUTH);
            helper.setBlock(new BlockPos(x, Y, 7), ModBlocks.LASER_PRISM.get());
            LaserPrismBlockEntity prism = helper.getBlockEntity(new BlockPos(x, Y, 7), LaserPrismBlockEntity.class);
            for (int j = 0; j < 8; j++) prism.toggleLink(helper.absolutePos(sinks.get((2 * i + j) % sinks.size())));
        }
        // The zigzag, above: east into a column of two mirrors, down or up it, east to the next column.
        emitter(helper, new BlockPos(1, Y + 5, 1), ModBlocks.LASER_EMITTER_3.get(), Direction.EAST);
        for (int column = 0; column < 6; column++) {
            int x = 2 + 2 * column;
            boolean down = column % 2 == 0;
            mirror(helper, new BlockPos(x, Y + 5, down ? 1 : 11), down ? Direction.SOUTH : Direction.NORTH);
            mirror(helper, new BlockPos(x, Y + 5, down ? 11 : 1), Direction.EAST);
        }
        sink.accept(new BlockPos(14, Y + 5, 1));
    }

    private static void mirror(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.LASER_MIRROR.get().defaultBlockState().setValue(LaserMirrorBlock.FACING, facing));
    }

    private static void emitter(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(LaserEmitterBlock.FACING, facing));
        helper.setBlock(pos.relative(facing.getOpposite()), ModBlocks.CREATIVE_ENERGY_CELL.get());
    }
}
