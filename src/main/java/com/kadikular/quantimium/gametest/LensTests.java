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

    private static void prism(GameTestHelper helper, BlockPos pos, BlockPos... targets) {
        helper.setBlock(pos, ModBlocks.LASER_PRISM.get());
        var prism = helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.LaserPrismBlockEntity.class);
        for (BlockPos target : targets) prism.toggleLink(helper.absolutePos(target));
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
        mount(helper, new BlockPos(3, Y, 6), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.AMPLIFY_LENS.get());
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
        mount(helper, new BlockPos(7, Y, 6), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.AMPLIFY_LENS.get());
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

    // covers: laser.light.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void aCavityOfHarvestLightWithNothingToCutOnlyLeaks(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get());
        var a = cavityAt(helper, new BlockPos(6, Y, 4), Direction.SOUTH);
        var b = cavityAt(helper, new BlockPos(6, Y, 9), Direction.NORTH);
        long[] at = {0, 0};
        helper.runAfterDelay(80, () -> {
            // Filled with harvest light; now take the emitter away and watch what the pair holds.
            helper.setBlock(new BlockPos(1, Y, 4), Blocks.AIR);
            helper.setBlock(new BlockPos(0, Y, 4), Blocks.AIR);
            at[0] = a.stored() + b.stored();
        });
        helper.runAfterDelay(140, () -> {
            at[1] = a.stored() + b.stored();
            long lost = at[0] - at[1];
            // 60 ticks of leak at most, rounded up each tick on each mirror.
            long leak = (long) Math.ceil(com.kadikular.quantimium.Config.cavityLeak(at[0]) * 60) + 120;
            helper.assertTrue(at[0] > 0, "it was filled");
            helper.assertTrue(a.held() != null, "with harvest light");
            helper.assertTrue(lost <= leak, "it only leaks: lost " + lost + " of " + at[0] + ", leak " + leak);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    private static com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity cavityAt(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.CAVITY_MIRROR.get().defaultBlockState().setValue(com.kadikular.quantimium.block.CavityMirrorBlock.FACING, facing));
        return helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.CavityMirrorBlockEntity.class);
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void withPickupItOnlyCutsWhatItCanCarryAway(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        for (int slot = 0; slot < CollectorBlockEntity.SLOTS; slot++) collector.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    private static ItemStack filterLens(com.kadikular.quantimium.laser.LensFilter filter) {
        ItemStack lens = new ItemStack(ModItems.FILTER_LENS.get());
        lens.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), filter);
        return lens;
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFilteredDamageBeamHurtsOnlyWhatItLets(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(new com.kadikular.quantimium.laser.LensFilter(java.util.List.of(), true,
                com.kadikular.quantimium.laser.LensFilter.Living.HOSTILE)));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(6, Y, 8));
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(9, Y, 8));
        float pigFull = pig.getHealth();
        float zombieFull = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < zombieFull || !zombie.isAlive(), "the zombie is hurt");
            helper.assertValueEqual(pig.getHealth(), pigFull, "the pig isn't");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFilteredHarvestBeamCutsOnlyWhatItLists(GameTestHelper helper) {
        // Listed: cobblestone. Stone drops it, so the stone goes; the dirt behind it doesn't.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.EMPTY.withEntry(0, new ItemStack(Items.COBBLESTONE))));
        helper.setBlock(new BlockPos(6, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.DIRT);
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, Y, 8));
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(7, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.amplify
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void plainHarvestCantCutObsidianAmplifiedItCan(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 4), Blocks.OBSIDIAN);
        emitter(helper, new BlockPos(1, Y, 10), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 10), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.AMPLIFY_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 10), Blocks.OBSIDIAN);
        helper.runAfterDelay(110, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(6, Y, 4));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, Y, 10));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.isolator
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anIsolatorLetsOnlyPowerThrough(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.ISOLATOR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.STONE);
        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, Y, 8));
            String light = String.join(" ", com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel())
                    .describe(helper.absolutePos(new BlockPos(5, Y, 8))).stream().map(net.minecraft.network.chat.Component::getString).toList());
            helper.assertTrue(light.contains("Plain light"), "plain light leaves it: " + light);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.splitter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aSplitterSendsLightOutOfEveryFaceSet(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        helper.setBlock(new BlockPos(5, Y, 6), ModBlocks.SPLITTER.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        var splitter = helper.getBlockEntity(new BlockPos(5, Y, 6), com.kadikular.quantimium.block.entity.SplitterBlockEntity.class);
        splitter.toggleOutput(Direction.SOUTH);
        helper.setBlock(new BlockPos(10, Y, 6), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(5, Y, 11), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(10, Y, 6), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "east");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(5, Y, 11), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "and south");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.mirror.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void aPulseMirrorLetsThroughOneAction(GameTestHelper helper) {
        // Harvest light through a pulse mirror, at a row of stone: one pulse, one block.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        var mirror = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity.class);
        while (mirror.mode() != com.kadikular.quantimium.laser.RelayMode.PULSE) mirror.cycleMode();
        for (int x = 7; x <= 9; x++) helper.setBlock(new BlockPos(x, Y, 8), Blocks.STONE);
        helper.runAfterDelay(30, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(7, Y, 8));
            mirror.onSignal(true);
            mirror.onSignal(false);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, Y, 8));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.blind
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void lightAtEyeHeightBlinds(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y + 1, 8), Direction.EAST, true);
        helper.setBlock(new BlockPos(12, Y + 1, 8), ModBlocks.FOLD_CORE.get());
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, Y, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "blinded");
            TestSupport.finishLasers(helper);
        });
    }

    private static com.kadikular.quantimium.block.entity.SenderBlockEntity sender(GameTestHelper helper, BlockPos pos, Direction facing, ItemStack... stock) {
        helper.setBlock(pos, ModBlocks.SENDER.get().defaultBlockState().setValue(com.kadikular.quantimium.block.SenderBlock.FACING, facing));
        var sender = helper.getBlockEntity(pos, com.kadikular.quantimium.block.entity.SenderBlockEntity.class);
        for (int i = 0; i < stock.length; i++) sender.getItems().setStackInSlot(i, stock[i]);
        return sender;
    }

    // covers: laser.lens.place, laser.sender
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aPlacementBeamPlacesWhatASenderSends(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        var sender = sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.COBBLESTONE, 4));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PLACE_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.DIRT);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(10, Y, 8));
            helper.assertTrue(sender.getItems().getStackInSlot(0).getCount() < 4, "taken from the Sender");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.place, laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void aWheatFieldCutRipeAndReplanted(GameTestHelper helper) {
        // Down onto farmland: Harvest with Pickup, filtered to fully grown, and Placement of seeds from the Sender.
        BlockPos crop = new BlockPos(8, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        helper.setBlock(new BlockPos(8, Y + 7, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(8, Y + 6, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.DOWN));
        CollectorBlockEntity collector = collector(helper, new BlockPos(8, Y + 5, 8), Direction.DOWN);
        sender(helper, new BlockPos(8, Y + 4, 8), Direction.DOWN, new ItemStack(Items.WHEAT_SEEDS, 8));
        LensMountBlockEntity mount = mount(helper, new BlockPos(8, Y + 3, 8), Direction.DOWN, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        // Fully grown only: ripe crops are cut, and the seeds it sows aren't.
        mount.getLensSlots().setStackInSlot(2, filterLens(new com.kadikular.quantimium.laser.LensFilter(java.util.List.of(), true,
                com.kadikular.quantimium.laser.LensFilter.Living.ANY, true)));
        mount.getLensSlots().setStackInSlot(3, new ItemStack(ModItems.PLACE_LENS.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(collector, Items.WHEAT) > 0, "the ripe wheat came back up the beam");
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertTrue(helper.getBlockState(crop).getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7, "and it's been replanted");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void anAreaPrismCutsEveryBlockInItsArea(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        for (int x = 9; x <= 11; x++) for (int z = 7; z <= 9; z++) helper.setBlock(new BlockPos(x, Y, z), Blocks.STONE);
        helper.assertTrue(prism.setArea(helper.absolutePos(new BlockPos(9, Y, 7)), helper.absolutePos(new BlockPos(11, Y, 9)))
                == com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.Placed.SET, "the area is set");
        helper.succeedWhen(() -> {
            for (int x = 9; x <= 11; x++) for (int z = 7; z <= 9; z++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, Y, z));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.sender
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aSenderShipsDownTheBeamToACollector(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.DIAMOND, 5));
        CollectorBlockEntity collector = collector(helper, new BlockPos(9, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(14, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.DIAMOND), 5, "the diamonds went down the beam");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.splitter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aSplitterFaceAddedByHandSendsLight(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        BlockPos at = new BlockPos(5, Y, 6);
        helper.setBlock(at, ModBlocks.SPLITTER.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(5, Y, 11), ModBlocks.FOLD_CORE.get());
        var player = TestSupport.player(helper, new BlockPos(5, Y, 9), net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        BlockPos abs = helper.absolutePos(at);
        var hit = new net.minecraft.world.phys.BlockHitResult(abs.getCenter().add(0, 0, 0.5), Direction.SOUTH, abs, false);
        helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);
        var splitter = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.SplitterBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(splitter.outputs().contains(Direction.SOUTH), "the face was added: " + splitter.outputs());
            helper.assertTrue(helper.getBlockEntity(new BlockPos(5, Y, 11), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0,
                    "and sends: " + splitter.syncedBeams());
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aFullyGrownFilterSparesWhatsStillGrowing(GameTestHelper helper) {
        BlockPos crop = new BlockPos(8, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 3));
        helper.setBlock(new BlockPos(8, Y + 5, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(8, Y + 4, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.DOWN));
        LensMountBlockEntity mount = mount(helper, new BlockPos(8, Y + 2, 8), Direction.DOWN, ModItems.HARVEST_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(new com.kadikular.quantimium.laser.LensFilter(java.util.List.of(), true,
                com.kadikular.quantimium.laser.LensFilter.Living.ANY, true)));
        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertBlockPresent(Blocks.FARMLAND, crop.below());
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anAreaPrismOnlyWorksWhereItCanSee(GameTestHelper helper) {
        // A wall of bedrock between the prism and one stone: that one stays.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(9, Y, 12), Blocks.STONE);
        for (int x = 6; x <= 8; x++) helper.setBlock(new BlockPos(x, Y, 11), Blocks.BEDROCK);
        for (int x = 6; x <= 8; x++) helper.setBlock(new BlockPos(x, Y, 10), Blocks.BEDROCK);
        prism.setArea(helper.absolutePos(new BlockPos(9, Y, 8)), helper.absolutePos(new BlockPos(9, Y, 12)));
        helper.runAfterDelay(90, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 8));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, Y, 12));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anAreaPrismBehindAnUnpoweredMirrorDoesNothing(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.COBBLESTONE, 16));
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        var mirror = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity.class);
        mirror.cycleMode();
        helper.assertTrue(mirror.mode() == com.kadikular.quantimium.laser.RelayMode.WITH_SIGNAL, "needs a signal");
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.PLACE_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(9, Y, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.setArea(helper.absolutePos(new BlockPos(11, Y, 7)), helper.absolutePos(new BlockPos(13, Y, 9)));
        helper.runAfterDelay(70, () -> {
            for (int x = 11; x <= 13; x++) for (int z = 7; z <= 9; z++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, Y, z));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.speed
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void speedLensesCutFaster(GameTestHelper helper) {
        // Two rows of stone: the one with three Speed lenses is through it while the plain one isn't.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get());
        emitter(helper, new BlockPos(1, Y, 10), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 10), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.SPEED_LENS.get(),
                ModItems.SPEED_LENS.get(), ModItems.SPEED_LENS.get());
        for (int x = 5; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, Y, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(x, Y, 10), Blocks.STONE);
        }
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 10));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, Y, 4));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.light.range
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 40)
    public static void fourRangeLensesTakeActionLightTo128(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.RANGE_LENS.get(),
                ModItems.RANGE_LENS.get(), ModItems.RANGE_LENS.get());
        // The fourth in a second mount: four slots hold Harvest and three.
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.RANGE_LENS.get());
        helper.setBlock(new BlockPos(16, Y, 8), Blocks.BEDROCK);
        helper.runAfterDelay(25, () -> {
            String text = String.join(" ", com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel())
                    .describe(helper.absolutePos(new BlockPos(5, Y, 8))).stream().map(net.minecraft.network.chat.Component::getString).toList());
            helper.assertTrue(text.contains("128 blocks a run"), "8 doubled four times: " + text);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void anUnlitAreaPrismShowsNoBeams(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, false);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        prism.setArea(helper.absolutePos(new BlockPos(9, Y, 7)), helper.absolutePos(new BlockPos(9, Y, 9)));
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(prism.syncedBeams().isEmpty(), "no light, no beams: " + prism.syncedBeams());
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void placementBehindAnUnpoweredMirrorStaysOffAfterAJoin(GameTestHelper helper) {
        // A: emitter, mirror needing a signal (none), Sender, Placement mount, into a join mirror.
        // B: emitter, Harvest + Pickup mount, into the same join. The join's light goes on into an Area Prism.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        helper.setBlock(new BlockPos(3, Y, 4), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        var off = helper.getBlockEntity(new BlockPos(3, Y, 4), com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity.class);
        off.cycleMode();
        sender(helper, new BlockPos(5, Y, 4), Direction.EAST, new ItemStack(Items.COBBLESTONE, 16));
        mount(helper, new BlockPos(7, Y, 4), Direction.EAST, ModItems.PLACE_LENS.get());
        emitter(helper, new BlockPos(9, Y, 0), Direction.SOUTH, true);
        mount(helper, new BlockPos(9, Y, 2), Direction.SOUTH, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        mirror(helper, new BlockPos(9, Y, 4), Direction.SOUTH);
        helper.setBlock(new BlockPos(9, Y, 7), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(9, Y, 7), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.setArea(helper.absolutePos(new BlockPos(11, Y, 9)), helper.absolutePos(new BlockPos(13, Y, 11)));
        helper.runAfterDelay(90, () -> {
            for (int x = 11; x <= 13; x++) for (int z = 9; z <= 11; z++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, Y, z));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.sender, laser.mirror.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aSenderBehindAnUnpoweredMirrorShipsNothing(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        helper.setBlock(new BlockPos(3, Y, 8), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        helper.getBlockEntity(new BlockPos(3, Y, 8), com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity.class).cycleMode();
        sender(helper, new BlockPos(5, Y, 8), Direction.EAST, new ItemStack(Items.DIAMOND, 5));
        CollectorBlockEntity collector = collector(helper, new BlockPos(9, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(13, Y, 8), Blocks.BEDROCK);
        helper.runAfterDelay(70, () -> {
            helper.assertValueEqual(count(collector, Items.DIAMOND), 0, "nothing shipped while the light's switched off");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.place
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void placementEmptiesABucketAndSendsItBack(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        var sender = sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.WATER_BUCKET));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PLACE_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(8, Y, 8));
            boolean empty = false;
            for (int slot = 0; slot < 9; slot++) empty |= sender.getItems().getStackInSlot(slot).is(Items.BUCKET);
            helper.assertTrue(empty, "the empty bucket came back to the Sender");
            TestSupport.finishLasers(helper);
            helper.setBlock(new BlockPos(8, Y, 8), Blocks.AIR);
        });
    }

    // covers: laser.mirror.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aSwitchedOffMirrorPassesNothingOn(GameTestHelper helper) {
        // One: emitter, Harvest + Speed mount, a mirror needing a signal (none), then a shared mirror into a prism.
        // Two: an emitter straight into the shared mirror. The prism's light must be plain, and cut nothing.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.SPEED_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 4), ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING, Direction.EAST));
        helper.getBlockEntity(new BlockPos(5, Y, 4), com.kadikular.quantimium.block.entity.LaserMirrorBlockEntity.class).cycleMode();
        emitter(helper, new BlockPos(8, Y, 0), Direction.SOUTH, true);
        mirror(helper, new BlockPos(8, Y, 4), Direction.SOUTH);
        BlockPos stone = new BlockPos(12, Y, 12);
        helper.setBlock(stone, Blocks.STONE);
        prism(helper, new BlockPos(8, Y, 8), stone);
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(Blocks.STONE, stone);
            String light = String.join(" ", com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel())
                    .describe(helper.absolutePos(new BlockPos(8, Y, 8))).stream().map(net.minecraft.network.chat.Component::getString).toList());
            helper.assertTrue(light.contains("Plain light"), "plain light from the prism: " + light);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void withPickupItCutsNothingItsCollectorsWouldRefuse(GameTestHelper helper) {
        // The Collector takes only sandstone: dirt from a grass block would be left lying, so it isn't cut.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        CollectorBlockEntity collector = collector(helper, new BlockPos(5, Y, 8), Direction.EAST);
        collector.setFilterSlot(0, new ItemStack(Items.SANDSTONE));
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.GRASS_BLOCK);
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(9, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aFilterLensSetInItsMountTakesEffect(GameTestHelper helper) {
        // Set to Fully grown through the mount's screen: the unripe wheat stays, where unset it was cut.
        BlockPos crop = new BlockPos(8, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 2));
        helper.setBlock(new BlockPos(8, Y + 5, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(8, Y + 4, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.DOWN));
        LensMountBlockEntity mount = mount(helper, new BlockPos(8, Y + 2, 8), Direction.DOWN, ModItems.HARVEST_LENS.get());
        mount.getLensSlots().setStackInSlot(1, new ItemStack(ModItems.FILTER_LENS.get()));
        var player = TestSupport.player(helper, new BlockPos(6, Y, 6));
        var menu = new com.kadikular.quantimium.menu.FilterLensMenu(1, player.getInventory(), mount, 1);
        menu.clickMenuButton(player, com.kadikular.quantimium.menu.FilterLensMenu.BUTTON_GROWN);
        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertTrue(com.kadikular.quantimium.item.FilterLensItem.filterOf(mount.getLensSlots().getStackInSlot(1)).grown(), "set on the lens in the mount");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.amplify, laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void anAmplifiedHitChargesUpOverTicks(GameTestHelper helper) {
        // An Emitter I sends 2,000 FE a tick; an amplified hit costs 2,000, more than arrives in one: it charges, then lands.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get(), ModItems.AMPLIFY_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.setArea(helper.absolutePos(new BlockPos(7, Y, 6)), helper.absolutePos(new BlockPos(10, Y + 1, 10)));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, Y, 8));
        float full = pig.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < full, "the pig was hit");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void aKeepCountSparesThatMany(GameTestHelper helper) {
        // Three pigs, keep two: one is hit, two aren't.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.EMPTY.withKeep(2)));
        java.util.List<Pig> pigs = new java.util.ArrayList<>();
        for (int x = 5; x <= 9; x += 2) pigs.add(helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(x, Y, 8)));
        float full = pigs.getFirst().getHealth();
        helper.runAfterDelay(100, () -> {
            long hurt = pigs.stream().filter(pig -> !pig.isAlive() || pig.getHealth() < full).count();
            helper.assertValueEqual(hurt, 1L, "only the one beyond the two kept");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.pickup, laser.light.cavity
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void pickupComesBackPastACavity(GameTestHelper helper) {
        // Harvest + Pickup light fills a cavity; the cavity's own light cuts the stone, and the drop comes
        // back past it to the Collector up the light that filled it.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), ModBlocks.CAVITY_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.CavityMirrorBlock.FACING, Direction.SOUTH));
        mirror(helper, new BlockPos(7, Y, 12), Direction.NORTH);
        helper.setBlock(new BlockPos(7, Y, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(7, Y, 0), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 1, "back past the cavity");
            helper.setBlock(new BlockPos(7, Y, 8), Blocks.AIR);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.phantom
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void phantomLightGoesThroughWallsToWhatItCharges(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.PHANTOM_LENS.get());
        for (int x = 5; x <= 7; x++) helper.setBlock(new BlockPos(x, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(10, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0,
                    "charged through three blocks of stone");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.phantom
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void phantomHarvestCutsOnlyTheOreInTheGround(GameTestHelper helper) {
        // Through stone to the ore behind it, with a Filter to ores: the ore goes, the stone round it stays.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PHANTOM_LENS.get());
        mount.getLensSlots().setStackInSlot(2, filterLens(com.kadikular.quantimium.laser.LensFilter.EMPTY.withEntry(0, new ItemStack(Items.COAL))));
        for (int x = 5; x <= 9; x++) helper.setBlock(new BlockPos(x, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, Y, 8));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, Y, 8));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    private static void rift(GameTestHelper helper, BlockPos pos, Direction.Axis axis) {
        helper.setBlock(pos, com.kadikular.quantimium.block.RiftLensBlock.turnedTo(ModBlocks.RIFT_LENS.get().defaultBlockState(), axis,
                helper.getLevel(), helper.absolutePos(pos)));
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void lightThroughARiftIsMirroredAndChargesNothing(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        rift(helper, new BlockPos(4, Y, 8), Direction.Axis.X);
        helper.setBlock(new BlockPos(9, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.runAfterDelay(60, () -> {
            var beams = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel()).beamsOf(helper.absolutePos(new BlockPos(4, Y, 8)));
            helper.assertTrue(!beams.isEmpty() && (beams.getFirst().marks() & com.kadikular.quantimium.laser.Light.MIRRORED) != 0,
                    "light leaves the rift in the mirror: " + beams);
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(9, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0,
                    "and charges nothing of the real world");
            helper.assertBlockProperty(new BlockPos(4, Y, 8), com.kadikular.quantimium.block.RiftLensBlock.OPEN, true);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void mirroredHarvestTakesACrystalApart(GameTestHelper helper) {
        // Through a rift, Harvest light takes a grown crystal apart for its shards, back up to the Collector.
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        CollectorBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        rift(helper, new BlockPos(7, Y, 8), Direction.Axis.X);
        helper.setBlock(new BlockPos(10, Y - 1, 8), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.FACING, Direction.UP)
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.AGE, 3));
        helper.setBlock(new BlockPos(13, Y, 8), Blocks.BEDROCK);
        // A crystal takes a while: an Emitter III's worth of light, so the test needn't wait 15 seconds.
        helper.setBlock(new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_3.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, ModItems.ANOMALITE_SHARD.get()), com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.SHARDS,
                    "the crystal's shards came back");
            helper.assertBlockNotPresent(ModBlocks.ANOMALITE_CRYSTAL.get(), new BlockPos(10, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void mirroredDamageHitsOnlyWhatsOfTheMirror(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        rift(helper, new BlockPos(5, Y, 8), Direction.Axis.X);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(7, Y, 8));
        var mite = helper.spawnWithNoFreeWill(com.kadikular.quantimium.init.ModEntities.MIRROR_ENDERMITE.get(), new BlockPos(10, Y, 8));
        float pigFull = pig.getHealth();
        float miteFull = mite.getHealth();
        helper.setBlock(new BlockPos(14, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(!mite.isAlive() || mite.getHealth() < miteFull, "the mirror mite is hit");
            helper.assertValueEqual(pig.getHealth(), pigFull, "the pig isn't");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lens_thread", timeoutTicks = 120)
    public static void mirroredPickupDrawsVeilThreadFromAHeldVeiled(GameTestHelper helper) {
        BlockPos hallAt = new BlockPos(8, 2, 8);
        var hall = TestSupport.readyHall(helper, hallAt, 1, 16);
        hall.contain();
        // West into the hall's east wall: emitter, Collector, a Pickup mount, then a rift seated on the glass.
        BlockPos row = hallAt.above(2);
        emitter(helper, row.east(7), Direction.WEST, true);
        CollectorBlockEntity collector = collector(helper, row.east(5), Direction.WEST);
        mount(helper, row.east(4), Direction.WEST, ModItems.PICKUP_LENS.get());
        rift(helper, row.east(2), Direction.Axis.X);
        helper.runAfterDelay(30, () -> com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel()).nearlyDrawn(5));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, ModItems.VEIL_THREAD.get()), 1, "a thread, up the beam");
            helper.assertTrue(hall.isOccupied(), "the Veiled is still held");
            TestSupport.finishLasers(helper);
            TestSupport.removeHall(helper, hallAt);
        });
    }
}
