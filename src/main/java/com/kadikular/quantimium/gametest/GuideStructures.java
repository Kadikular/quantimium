package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.AnomaliteCrystalBlock;
import com.kadikular.quantimium.block.HarvestLaserBlock;
import com.kadikular.quantimium.block.RiftLensBlock;
import com.kadikular.quantimium.block.SuperpositionPodBlock;
import com.kadikular.quantimium.block.entity.UnfoldingArrayBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Not tests: the in-game guide's 3D multiblocks. Each builds a multiblock the way the tests do, lets it
 * form and light up, and saves the blocks that were placed as a structure the guide shows with
 * {@code [[structure:name]]}. Run only with {@code ./gradlew runGameTestServer -PexportStructures}, which
 * writes them to wiki/structures; tools/wiki/guideme.py copies them into the guide.
 */
public final class GuideStructures {

    /** Where the structures go; set by {@code -PexportStructures}. */
    public static final String DIRECTORY = System.getProperty("quantimium.exportStructures");

    private GuideStructures() {}

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void reactor(GameTestHelper helper) {
        export(helper, "reactor", h -> {
            ReactorTests.buildReactor(h, 2);
            BlockPos core = ReactorTests.CORE;
            h.setBlock(core.offset(3, 0, 3), ModBlocks.RING_EMITTER.get());
            h.setBlock(core.offset(-3, 0, -3), ModBlocks.RING_EMITTER.get());
            ReactorTests.bay(h, core.below().offset(2, 0, -1), Items.CRAFTING_TABLE);
            ReactorTests.bay(h, core.below().offset(-2, 0, 1), Items.FURNACE);
            h.getBlockEntity(core.below().offset(2, 0, -1), com.kadikular.quantimium.block.entity.CatalystBayBlockEntity.class)
                    .setCatalyst(3, new net.minecraft.world.item.ItemStack(Items.STONECUTTER));
            h.setBlock(core.below().south(5), ModBlocks.REACTOR_OUTPUT_PORT.get());
            h.setBlock(core.below().east(5), ModBlocks.REACTOR_ENERGY_PORT.get());
            h.setBlock(core.below().west(5), ModBlocks.REACTOR_MATERIALISER_PORT.get());
        });
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void foundry(GameTestHelper helper) {
        export(helper, "foundry", h -> FoldTests.buildFoundry(h, new BlockPos(8, 2, 8)));
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void foldChamber(GameTestHelper helper) {
        export(helper, "fold_chamber", h -> {
            BlockPos core = new BlockPos(8, 2, 1);
            FoldTests.buildChamber(h, core, Direction.NORTH, 9, 3, 9);
            FoldTests.buildFoundry(h, core.south(4));
        });
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void hall(GameTestHelper helper) {
        export(helper, "hall", h -> TestSupport.buildHall(h, new BlockPos(8, 2, 8), 4));
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void pod(GameTestHelper helper) {
        export(helper, "pod", h -> {
            BlockPos at = new BlockPos(8, 3, 8);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean edge = (dx == 0) != (dz == 0);
                    h.setBlock(at.below().offset(dx, 0, dz), edge ? ModBlocks.POD_PLATING.get() : ModBlocks.POD_CRADLE.get());
                }
            }
            BlockState lower = ModBlocks.SUPERPOSITION_POD.get().defaultBlockState().setValue(SuperpositionPodBlock.FACING, Direction.SOUTH);
            h.setBlock(at, lower);
            h.setBlock(at.above(), lower.setValue(SuperpositionPodBlock.HALF, DoubleBlockHalf.UPPER));
        });
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void unfoldingArray(GameTestHelper helper) {
        export(helper, "unfolding_array", h -> {
            BlockPos pad = new BlockPos(8, 2, 8);
            h.setBlock(pad, ModBlocks.UNFOLDING_ARRAY.get());
            for (BlockPos pylon : UnfoldingArrayBlockEntity.pylons(pad)) h.setBlock(pylon, ModBlocks.ARRAY_PYLON.get());
        });
    }

    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void harvester(GameTestHelper helper) {
        export(helper, "harvester", h -> {
            BlockPos laser = new BlockPos(3, 2, 8);
            h.setBlock(laser.east(4), ModBlocks.BUDDING_ANOMALITE.get());
            h.setBlock(laser.east(3), ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                    .setValue(AnomaliteCrystalBlock.FACING, Direction.WEST).setValue(AnomaliteCrystalBlock.AGE, 3));
            h.setBlock(laser.east(2), ModBlocks.RIFT_LENS.get().defaultBlockState().setValue(RiftLensBlock.AXIS, Direction.Axis.X));
            h.setBlock(laser, ModBlocks.HARVEST_LASER.get().defaultBlockState().setValue(HarvestLaserBlock.FACING, Direction.EAST));
            // Powered, so it's caught firing.
            TestSupport.fill(h.getBlockEntity(laser, com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.class).getEnergyStorage());
        });
    }

    /** Builds with {@code build}, waits for it to form, and saves every block that changed. */
    private static void export(GameTestHelper helper, String name, Consumer<GameTestHelper> build) {
        ServerLevel level = helper.getLevel();
        AABB area = helper.getBounds();
        BlockPos min = BlockPos.containing(area.minX, area.minY, area.minZ);
        BlockPos max = BlockPos.containing(area.maxX - 1, area.maxY - 1, area.maxZ - 1);
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) before.put(pos.immutable(), level.getBlockState(pos));
        build.accept(helper);
        helper.runAfterDelay(30, () -> {
            BlockPos lo = null;
            BlockPos hi = null;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (level.getBlockState(pos).equals(before.get(pos)) || level.getBlockState(pos).isAir()) continue;
                lo = lo == null ? pos.immutable() : BlockPos.min(lo, pos);
                hi = hi == null ? pos.immutable() : BlockPos.max(hi, pos);
            }
            helper.assertTrue(lo != null, name + ": nothing was built");
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, lo, hi.subtract(lo).offset(1, 1, 1), false, java.util.List.of(Blocks.STRUCTURE_VOID));
            // The test floor isn't part of the multiblock.
            CompoundTag tag = template.save(new CompoundTag());
            String snbt = NbtUtils.structureToSnbt(tag);
            try {
                Path file = Path.of(DIRECTORY, name + ".snbt");
                Files.createDirectories(file.getParent());
                Files.writeString(file, snbt);
            } catch (IOException e) {
                throw new IllegalStateException("couldn't write " + name, e);
            }
            Quantimium.LOGGER.info("Guide structure {}: {} blocks", name, template.getSize());
            helper.succeed();
        });
    }
}
