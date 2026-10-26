package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
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

    /** A charged Emitter II, so the scene's beams are lit without a power source in the picture. */
    private static void chargedEmitter(GameTestHelper h, BlockPos pos, Direction facing) {
        h.setBlock(pos, ModBlocks.LASER_EMITTER_2.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserEmitterBlock.FACING, facing));
        TestSupport.fill(h.getBlockEntity(pos, com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity.class).getEnergyStorage());
    }

    /** A laser network: an emitter's light split by a Relay to power two machines. */
    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void laserNetwork(GameTestHelper helper) {
        export(helper, "laser_network", h -> {
            chargedEmitter(h, new BlockPos(2, 2, 6), Direction.EAST);
            BlockPos relay = new BlockPos(7, 2, 6);
            TestSupport.relay(h, relay, Direction.DOWN, Direction.EAST);
            h.getBlockEntity(relay, com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.class).addEmitter(Direction.SOUTH);
            h.setBlock(new BlockPos(11, 2, 6), ModBlocks.QUANTUM_CRAFTER.get());
            h.setBlock(new BlockPos(7, 2, 10), ModBlocks.QUANTUM_SIMULATOR.get());
        });
    }

    /** A logistics network: a controller's light through a storage, a provider and a requester, to a Lectern. */
    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void logistics(GameTestHelper helper) {
        export(helper, "logistics", h -> {
            BlockPos controller = new BlockPos(2, 2, 8);
            h.setBlock(controller, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState()
                    .setValue(com.kadikular.quantimium.block.LogisticsControllerBlock.FACING, Direction.EAST)
                    .setValue(com.kadikular.quantimium.block.LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH), false)
                    .setValue(com.kadikular.quantimium.block.LogisticsControllerBlock.OUTPUTS.get(Direction.EAST), true));
            h.getBlockEntity(controller, com.kadikular.quantimium.block.entity.LogisticsControllerBlockEntity.class).getEnergyStorage()
                    .setEnergy(com.kadikular.quantimium.laser.LaserTuning.LOGISTICS_CAPACITY);
            com.kadikular.quantimium.laser.LogisticsMember.Role[] roles = {com.kadikular.quantimium.laser.LogisticsMember.Role.STORAGE,
                    com.kadikular.quantimium.laser.LogisticsMember.Role.PROVIDER, com.kadikular.quantimium.laser.LogisticsMember.Role.REQUESTER};
            for (int i = 0; i < roles.length; i++) {
                BlockPos at = new BlockPos(4 + 2 * i, 2, 8);
                h.setBlock(at.below(), Blocks.CHEST);
                h.setBlock(at, ModBlocks.LOGISTICS_RECEPTACLE.get().defaultBlockState()
                        .setValue(com.kadikular.quantimium.block.LogisticsReceptacleBlock.FACING, Direction.DOWN));
                var receptacle = h.getBlockEntity(at, com.kadikular.quantimium.block.entity.LogisticsReceptacleBlockEntity.class);
                while (receptacle.role() != roles[i]) receptacle.cycleRole();
            }
            h.setBlock(new BlockPos(11, 2, 8), ModBlocks.LOGISTICS_LECTERN.get().defaultBlockState()
                    .setValue(com.kadikular.quantimium.block.LogisticsTerminalBlock.FACING, Direction.SOUTH));
        });
    }

    /** A wheat farm: light down through a collecting and a sending Laser Port to a mount that cuts ripe wheat and sows seed. */
    @GameTest(template = TestSupport.FLOOR_17, batch = "guide_structures", timeoutTicks = 60)
    public static void farm(GameTestHelper helper) {
        export(helper, "laser_farm", h -> {
            BlockPos crop = new BlockPos(8, 2, 8);
            h.setBlock(crop.below(), Blocks.FARMLAND);
            h.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 5));
            chargedEmitter(h, crop.above(5), Direction.DOWN);
            h.setBlock(crop.above(4), ModBlocks.LASER_PORT.get().defaultBlockState()
                    .setValue(com.kadikular.quantimium.block.LaserPortBlock.FACING, Direction.DOWN));
            h.setBlock(crop.above(3), ModBlocks.LASER_PORT.get().defaultBlockState()
                    .setValue(com.kadikular.quantimium.block.LaserPortBlock.FACING, Direction.DOWN));
            var sender = h.getBlockEntity(crop.above(3), com.kadikular.quantimium.block.entity.LaserPortBlockEntity.class);
            sender.cycleMode();
            sender.getItems().setStackInSlot(0, new net.minecraft.world.item.ItemStack(Items.WHEAT_SEEDS, 8));
            h.setBlock(crop.above(2), ModBlocks.LENS_MOUNT.get().defaultBlockState()
                    .setValue(com.kadikular.quantimium.block.LensMountBlock.FACING, Direction.DOWN));
            var mount = h.getBlockEntity(crop.above(2), com.kadikular.quantimium.block.entity.LensMountBlockEntity.class);
            net.minecraft.world.item.ItemStack ripe = new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.BLOCK_FILTER_LENS.get());
            ripe.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(),
                    com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(), true, true));
            mount.getLensSlots().setStackInSlot(0, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.HARVEST_LENS.get()));
            mount.getLensSlots().setStackInSlot(1, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.PICKUP_LENS.get()));
            mount.getLensSlots().setStackInSlot(2, ripe);
            mount.getLensSlots().setStackInSlot(3, new net.minecraft.world.item.ItemStack(com.kadikular.quantimium.init.ModItems.PLACE_LENS.get()));
        });
    }

    /**
     * Laser blocks send their beams only to clients, so a saved block has none and the guide would show unlit lasers.
     * Each one's beams go into its saved data, which it reads back on load; their two world positions, which would
     * point outside the guide's scene, are left out.
     */
    private static void keepBeams(ServerLevel level, BlockPos origin, CompoundTag structure) {
        for (net.minecraft.nbt.Tag entry : structure.getListOrEmpty("blocks")) {
            if (!(entry instanceof CompoundTag block) || block.getCompound("nbt").isEmpty()) continue;
            net.minecraft.nbt.ListTag at = block.getListOrEmpty("pos");
            BlockPos pos = origin.offset(at.getIntOr(0, 0), at.getIntOr(1, 0), at.getIntOr(2, 0));
            var entity = level.getBlockEntity(pos);
            if (entity == null) continue;
            CompoundTag beams = entity.getUpdateTag(level.registryAccess()).getCompoundOrEmpty("Beams");
            if (beams.isEmpty()) continue;
            CompoundTag local = beams.copy();
            for (net.minecraft.nbt.Tag beam : local.getListOrEmpty("List")) {
                if (beam instanceof CompoundTag one) {
                    one.remove("Aim");
                    one.remove("Hit");
                }
            }
            block.getCompound("nbt").ifPresent(nbt -> nbt.put("Beams", local));
        }
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
            keepBeams(level, lo, tag);
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
