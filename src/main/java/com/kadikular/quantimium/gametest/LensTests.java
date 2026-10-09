package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.CollectorBlock;
import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.CollectorBlockEntity;
import com.kadikular.quantimium.block.entity.FoldCoreBlockEntity;
import com.kadikular.quantimium.block.entity.LaserEmitterBlockEntity;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Lasers, phase two: lenses in a Lens Mount give the light leaving it an action (harvest, damage) and
 * modifiers (pickup), which add up along mounts in a row. The beam still carries power; its actions
 * spend some of it, and only while it's lit. A Collector up the beam catches what Pickup brings back.
 */
public final class LensTests {

    private static final int Y = 2;

    private LensTests() {}

    private static LaserEmitterBlockEntity emitter(GameTestHelper helper, BlockPos pos, Direction facing, boolean powered) {
        helper.setBlock(pos, ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, facing));
        if (powered) helper.setBlock(pos.relative(facing.getOpposite()), ModBlocks.CREATIVE_ENERGY_CELL.get());
        return helper.getBlockEntity(pos, LaserEmitterBlockEntity.class);
    }

    private static LensMountBlockEntity mount(GameTestHelper helper, BlockPos pos, Direction facing, Item... lenses) {
        helper.setBlock(pos, ModBlocks.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, facing));
        LensMountBlockEntity mount = helper.getBlockEntity(pos, LensMountBlockEntity.class);
        for (int i = 0; i < lenses.length; i++) mount.getLensSlots().setStackInSlot(i, new ItemStack(lenses[i]));
        return mount;
    }

    private static CollectorBlockEntity collector(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.COLLECTOR.get().defaultBlockState().setValue(CollectorBlock.FACING, facing));
        return helper.getBlockEntity(pos, CollectorBlockEntity.class);
    }

    private static int count(CollectorBlockEntity collector, Item item) {
        int total = 0;
        for (int slot = 0; slot < CollectorBlockEntity.SLOTS; slot++) {
            ItemStack stack = collector.getItems().getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    // covers: laser.lens.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aHarvestLensBreaksWhatTheBeamEndsOn(GameTestHelper helper) {
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        BlockPos stone = new BlockPos(9, Y, 8);
        helper.setBlock(stone, Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, stone);
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(stone)).inflate(2),
                    item -> item.getItem().is(Items.COBBLESTONE)).isEmpty(), "it dropped cobblestone");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void aHarvestLensNeedsALitBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, false);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        BlockPos stone = new BlockPos(9, Y, 8);
        helper.setBlock(stone, Blocks.STONE);
        helper.runAfterDelay(50, () -> {
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.succeed();
        });
    }

    // covers: laser.lens.mount
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aLensedBeamStillCarriesPower(GameTestHelper helper) {
        // A Harvest lens doesn't break a machine, and the beam charges it as before.
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.DAMAGE_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), ModBlocks.FOLD_CORE.get());
        FoldCoreBlockEntity core = helper.getBlockEntity(new BlockPos(9, Y, 8), FoldCoreBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.FOLD_CORE.get(), new BlockPos(9, Y, 8));
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "charged through the lenses");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pickup, laser.collector
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupBringsWhatItBreaksBackToACollector(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 1, "the cobblestone came back up the beam");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.mount, laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void mountsInARowAddTheirLensesUp(GameTestHelper helper) {
        // Pickup in the first mount, Harvest in the second: the light leaving the second does both.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 1, "harvested and brought back");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupAloneGathersItemsLyingInTheBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        ItemEntity item = new ItemEntity(helper.getLevel(), helper.absoluteVec(new BlockPos(10, Y, 8).getCenter()).x,
                helper.absolutePos(new BlockPos(10, Y, 8)).getY() + 0.5, helper.absoluteVec(new BlockPos(10, Y, 8).getCenter()).z,
                new ItemStack(Items.DIAMOND, 3));
        item.setDeltaMovement(0, 0, 0);
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.DIAMOND), 3, "the diamonds came back up the beam");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.damage
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aDamageLensHurtsWhatStandsInTheBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(9, Y, 8));
        float full = pig.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < full, "the pig was hurt");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupFillsTheNextCollectorWhenOneIsFull(GameTestHelper helper) {
        // Two emitters, each through its own Collector, joined at a mirror: when one Collector is full,
        // what Pickup brings back goes up the other side.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity west = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        emitter(helper, new BlockPos(6, Y, 1), Direction.SOUTH, true);
        CollectorBlockEntity north = collector(helper, new BlockPos(6, Y, 4), Direction.SOUTH);
        helper.setBlock(new BlockPos(6, Y, 8), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        mount(helper, new BlockPos(8, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        for (CollectorBlockEntity full : java.util.List.of(west, north)) {
            for (int slot = 0; slot < CollectorBlockEntity.SLOTS; slot++) full.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        }
        // Room in the west one only: whichever side is tried first, the diamonds must end up there.
        west.getItems().setStackInSlot(4, ItemStack.EMPTY);
        ItemEntity item = new ItemEntity(helper.getLevel(), helper.absoluteVec(new BlockPos(12, Y, 8).getCenter()).x,
                helper.absolutePos(new BlockPos(12, Y, 8)).getY() + 0.3, helper.absoluteVec(new BlockPos(12, Y, 8).getCenter()).z,
                new ItemStack(Items.DIAMOND, 2));
        item.setDeltaMovement(0, 0, 0);
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(west, Items.DIAMOND), 2, "the diamonds went up the side with room");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.damage
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aDamageLensReachesOnlySoFar(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        // Twelve blocks along: past the action's reach, in the beam all the same.
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(15, Y, 8));
        float full = pig.getHealth();
        helper.runAfterDelay(70, () -> {
            helper.assertValueEqual(pig.getHealth(), full, "out of reach, unhurt");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.prism.keep
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void aPrismSetToKeepLinksKeepsThem(GameTestHelper helper) {
        BlockPos target = new BlockPos(12, Y, 12);
        helper.setBlock(new BlockPos(7, Y, 8), ModBlocks.LASER_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(7, Y, 8), com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.class);
        prism.toggleKeepsLinks();
        helper.setBlock(target, Blocks.STONE);
        prism.toggleLink(helper.absolutePos(target));
        helper.runAfterDelay(10, () -> helper.setBlock(target, Blocks.AIR));
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(prism.targets().size(), 1, "still aiming at the spot");
            helper.succeed();
        });
    }

    private static void mirror(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.LASER_MIRROR.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, facing));
    }

    // covers: laser.light.range
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void aMirrorRefocusesToItsSourcesRange(GameTestHelper helper) {
        // A Flash Emitter reaches 8 a run; through a mirror its light reaches 8 again, not the mirror's 32.
        helper.setBlock(new BlockPos(1, Y, 2), ModBlocks.FLASH_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(1, Y, 1), ModBlocks.CREATIVE_ENERGY_CELL.get());
        mirror(helper, new BlockPos(1, Y, 6), Direction.EAST);
        helper.setBlock(new BlockPos(9, Y, 6), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(13, Y, 6), Blocks.AIR);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(9, Y, 6), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0,
                    "7 blocks on: within the flash's 8");
            helper.setBlock(new BlockPos(9, Y, 6), Blocks.AIR);
            helper.setBlock(new BlockPos(12, Y, 6), ModBlocks.FOLD_CORE.get());
        });
        helper.runAfterDelay(55, () -> {
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(12, Y, 6), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0,
                    "10 blocks on: beyond it");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.light.range
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void lightWithAnActionReachesEightARangeLensDoublesIt(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(14, Y, 4), Blocks.STONE);
        emitter(helper, new BlockPos(1, Y, 10), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 10), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.RANGE_LENS.get());
        helper.setBlock(new BlockPos(14, Y, 10), Blocks.STONE);
        // Lit beams end inside the test, never in a neighbour's.
        helper.setBlock(new BlockPos(16, Y, 10), Blocks.BEDROCK);
        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(14, Y, 4));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(14, Y, 10));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.light.join
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void harvestAndDamageLightJoinedBothWork(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 6), Direction.EAST, ModItems.HARVEST_LENS.get());
        emitter(helper, new BlockPos(6, Y, 1), Direction.SOUTH, true);
        mount(helper, new BlockPos(6, Y, 3), Direction.SOUTH, ModItems.DAMAGE_LENS.get());
        mirror(helper, new BlockPos(6, Y, 6), Direction.EAST);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(9, Y, 6));
        float full = pig.getHealth();
        helper.setBlock(new BlockPos(12, Y, 6), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(12, Y, 6));
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < full, "and the pig was hurt");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.light.payers
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void onlyTheSourcesBehindALensPayForIt(GameTestHelper helper) {
        // Harvest light from the west joins plain light from the north: only the west emitter pays to cut.
        LaserEmitterBlockEntity west = emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 6), Direction.EAST, ModItems.HARVEST_LENS.get());
        LaserEmitterBlockEntity north = emitter(helper, new BlockPos(6, Y, 1), Direction.SOUTH, true);
        mirror(helper, new BlockPos(6, Y, 6), Direction.EAST);
        // A row of obsidian: long cuts, so both would be sending for a while if both paid.
        for (int x = 8; x <= 13; x++) helper.setBlock(new BlockPos(x, Y, 6), Blocks.OBSIDIAN);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(west.sending() > 0, "the harvest light's own emitter pays: " + west.sending());
            helper.assertValueEqual(north.sending(), 0L, "the plain light's doesn't");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.light.payers
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void anActionAddedAfterAJoinIsPaidForByBoth(GameTestHelper helper) {
        LaserEmitterBlockEntity west = emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        LaserEmitterBlockEntity north = emitter(helper, new BlockPos(5, Y, 1), Direction.SOUTH, true);
        mirror(helper, new BlockPos(5, Y, 6), Direction.EAST);
        mount(helper, new BlockPos(7, Y, 6), Direction.EAST, ModItems.HARVEST_LENS.get());
        for (int x = 9; x <= 14; x++) helper.setBlock(new BlockPos(x, Y, 6), Blocks.OBSIDIAN);
        helper.succeedWhen(() -> {
            helper.assertTrue(west.sending() > 0 && north.sending() > 0, "both pay: " + west.sending() + " and " + north.sending());
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.light.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void aCavityFilledWithHarvestLightGivesItOut(GameTestHelper helper) {
        // Harvest light fills a cavity; the cavity's own light, out of its back, breaks the stone, and the
        // cavity pays for it.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 4), ModBlocks.CAVITY_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.CavityMirrorBlock.FACING, Direction.SOUTH));
        mirror(helper, new BlockPos(6, Y, 8), Direction.NORTH);
        var cavity = helper.getBlockEntity(new BlockPos(6, Y, 4), com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity.class);
        helper.setBlock(new BlockPos(6, Y, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(6, Y, 0), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(cavity.held() != null && cavity.held().streams().stream()
                    .anyMatch(stream -> stream.action() == com.kadikular.quantimium.laser.Lens.HARVEST), "it holds harvest light");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, Y, 1));
            helper.setBlock(new BlockPos(6, Y, 4), Blocks.AIR);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupBeforeDamageStillGathersItsDrops(GameTestHelper helper) {
        // Pickup stays on the light: Damage added after it gets it too.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.DAMAGE_LENS.get());
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(9, Y, 8));
        zombie.setHealth(1.0f);
        helper.succeedWhen(() -> {
            helper.assertTrue(!zombie.isAlive(), "killed");
            helper.assertTrue(count(collector, Items.ROTTEN_FLESH) > 0 || helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(9, Y, 8))).inflate(3)).isEmpty(), "its drops came back, if it dropped any");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.collector.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aCollectorsFilterSendsTheRestOnUp(GameTestHelper helper) {
        // The nearer Collector catches only stone; the cobblestone goes on up to the farther one.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity far = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        CollectorBlockEntity near = collector(helper, new BlockPos(5, Y, 8), Direction.EAST);
        near.setFilterSlot(0, new ItemStack(Items.STONE));
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(far, Items.COBBLESTONE), 1, "past the filter, to the farther one");
            helper.assertValueEqual(count(near, Items.COBBLESTONE), 0, "not caught by the nearer");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.spectroscope
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 40)
    public static void theSpectroscopeReadsWhatLightCarries(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.RANGE_LENS.get());
        helper.setBlock(new BlockPos(16, Y, 8), Blocks.BEDROCK);
        helper.runAfterDelay(25, () -> {
            var lines = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel()).describe(helper.absolutePos(new BlockPos(3, Y, 8)));
            String text = String.join("\n", lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
            helper.assertTrue(text.contains("16 blocks a run"), "reach, doubled: " + text);
            helper.assertTrue(text.contains("Harvest Lens"), "its action: " + text);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }
}
