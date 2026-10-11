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
 * beams, moving power, lens actions, settling), for a base's worth of beams. Each scenario runs alone in
 * its batch, and switches its lasers off when it's done: the manager is the level's, so lit lasers left
 * standing would be timed with the next.
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
                }, () -> TestSupport.finishLasers(helper));
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
                LaserNetworks.of(helper.getLevel()), () -> cores.size() + " full blocks", () -> TestSupport.finishLasers(helper));
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

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_laser_quarry", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void areaQuarry(GameTestHelper helper) {
        // An Emitter III through a Collector and a mount (Harvest, Amplify, Speed, Pickup) into an Area Prism
        // over an 8x1x8 floor of stone, put back as fast as it's cut; the Collector emptied each tick.
        emitter(helper, new BlockPos(1, Y + 2, 2), ModBlocks.LASER_EMITTER_3.get(), Direction.EAST);
        helper.setBlock(new BlockPos(3, Y + 2, 2), ModBlocks.COLLECTOR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.CollectorBlock.FACING, Direction.EAST));
        var collector = helper.getBlockEntity(new BlockPos(3, Y + 2, 2), com.kadikular.quantimium.block.entity.CollectorBlockEntity.class);
        helper.setBlock(new BlockPos(5, Y + 2, 2), ModBlocks.LENS_MOUNT.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LensMountBlock.FACING, Direction.EAST));
        var mount = helper.getBlockEntity(new BlockPos(5, Y + 2, 2), com.kadikular.quantimium.block.entity.LensMountBlockEntity.class);
        mount.getLensSlots().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.HARVEST_LENS.get()));
        mount.getLensSlots().setStackInSlot(1, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.AMPLIFY_LENS.get()));
        mount.getLensSlots().setStackInSlot(2, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.SPEED_LENS.get()));
        mount.getLensSlots().setStackInSlot(3, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.PICKUP_LENS.get()));
        helper.setBlock(new BlockPos(7, Y + 2, 2), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(7, Y + 2, 2), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.setArea(helper.absolutePos(new BlockPos(4, Y, 5)), helper.absolutePos(new BlockPos(11, Y, 12)));
        int[] cut = {0};
        helper.onEachTick(() -> {
            for (int x = 4; x <= 11; x++) {
                for (int z = 5; z <= 12; z++) {
                    BlockPos at = new BlockPos(x, Y, z);
                    if (helper.getBlockState(at).isAir()) {
                        helper.setBlock(at, net.minecraft.world.level.block.Blocks.STONE);
                        cut[0]++;
                    }
                }
            }
            for (int slot = 0; slot < 9; slot++) collector.getItems().setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        });
        TickTiming.measure(helper, new Scenario("laser.area_quarry", "Laser networks",
                        "An Area Prism quarrying an 8x8 floor of stone with Harvest, Amplify, Speed and Pickup, put back as fast as it's cut", 60),
                LaserNetworks.of(helper.getLevel()), () -> cut[0] + " blocks cut", () -> TestSupport.finishLasers(helper));
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "timing_laser_farm_idle", timeoutTicks = TickTiming.TIMEOUT_TICKS)
    public static void areaFarmWaiting(GameTestHelper helper) {
        // An Area Prism, filtered to fully grown, over a 9x9 field of wheat that never ripens: it looks, and finds nothing.
        for (int x = 4; x <= 12; x++) {
            for (int z = 4; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, Y - 1, z), net.minecraft.world.level.block.Blocks.FARMLAND);
                helper.setBlock(new BlockPos(x, Y, z), net.minecraft.world.level.block.Blocks.WHEAT);
            }
        }
        emitter(helper, new BlockPos(1, Y + 4, 8), ModBlocks.LASER_EMITTER_2.get(), Direction.EAST);
        helper.setBlock(new BlockPos(3, Y + 4, 8), ModBlocks.LENS_MOUNT.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LensMountBlock.FACING, Direction.EAST));
        var mount = helper.getBlockEntity(new BlockPos(3, Y + 4, 8), com.kadikular.quantimium.block.entity.LensMountBlockEntity.class);
        mount.getLensSlots().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.HARVEST_LENS.get()));
        var grown = new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.FILTER_LENS.get());
        grown.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), new com.kadikular.quantimium.laser.LensFilter(
                java.util.List.of(), true, com.kadikular.quantimium.laser.LensFilter.Living.ANY, true));
        mount.getLensSlots().setStackInSlot(1, grown);
        mount.getLensSlots().setStackInSlot(2, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.RANGE_LENS.get()));
        helper.setBlock(new BlockPos(8, Y + 4, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(8, Y + 4, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.setArea(helper.absolutePos(new BlockPos(4, Y, 4)), helper.absolutePos(new BlockPos(12, Y, 12)));
        Runnable restore = noRandomTicks(helper);
        TickTiming.measure(helper, new Scenario("laser.area_farm_waiting", "Laser networks",
                        "An Area Prism over 81 unripe crops, filtered to fully grown: looking, with nothing to cut", 40),
                LaserNetworks.of(helper.getLevel()), () -> "81 crops", () -> {
                    restore.run();
                    TestSupport.finishLasers(helper);
                });
    }

    /** Crops mustn't grow while it's timed: random ticks off, put back after. */
    private static Runnable noRandomTicks(GameTestHelper helper) {
        var rules = helper.getLevel().getGameRules();
        int was = rules.get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED);
        rules.set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 0, helper.getLevel().getServer());
        return () -> rules.set(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, was, helper.getLevel().getServer());
    }
}
