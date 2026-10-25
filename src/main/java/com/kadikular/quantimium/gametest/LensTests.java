package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.block.LaserPortBlock;
import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.block.LensMountBlock;
import com.kadikular.quantimium.block.entity.LaserPortBlockEntity;
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
 * spend some of it, and only while it's lit. A Laser Port up the beam catches what Pickup brings back.
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

    private static LaserPortBlockEntity collector(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.LASER_PORT.get().defaultBlockState().setValue(LaserPortBlock.FACING, facing));
        return helper.getBlockEntity(pos, LaserPortBlockEntity.class);
    }

    private static int count(LaserPortBlockEntity collector, Item item) {
        int total = 0;
        for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) {
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

    // covers: laser.lens.pickup, laser.port.collect
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupBringsWhatItBreaksBackToACollector(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 1, "the cobblestone came back up the beam");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.port.collect
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aCollectorExportingToAChestPassesItStraightOn(GameTestHelper helper) {
        // A collecting Laser Port with a chest above it, its top face set to push out: what Pickup brings back goes
        // straight into the chest, not into its own slots first.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(3, Y + 1, 8), Blocks.CHEST);
        var configs = new java.util.ArrayList<>(collector.getSideConfigs());
        var up = configs.get(Direction.UP.get3DDataValue());
        configs.set(Direction.UP.get3DDataValue(), new com.kadikular.quantimium.block.entity.simulation.SideConfig(up.side(), up.itemMode(),
                up.itemInputMask(), up.itemOutputMask(), false, true, up.fluidMode(), up.fluidInputMask(), up.fluidOutputMask(), false, false));
        collector.applySideConfigs(configs);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            var chest = helper.getBlockEntity(new BlockPos(3, Y + 1, 8), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            int inChest = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.COBBLESTONE)) inChest += chest.getItem(i).getCount();
            helper.assertValueEqual(inChest, 1, "the cobblestone went into the chest");
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 0, "not into the port");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.void
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void voidWithPickupCutsEvenWhenThePortIsFull(GameTestHelper helper) {
        // Harvest, Pickup and Void, with the only Laser Port full: the stone is cut all the same, and its cobblestone,
        // with nowhere to go, is destroyed rather than left lying.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) collector.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.VOID_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 8));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(9, Y, 8))).inflate(3)).isEmpty(),
                    "nothing left lying");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.void
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void voidAllDestroysEvenWhatAPortHasRoomFor(GameTestHelper helper) {
        // Harvest, Pickup and a Void lens set to void all, an empty Laser Port up the beam: the stone is cut, and its
        // cobblestone goes nowhere, not even into the port.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        ItemStack lens = new ItemStack(ModItems.VOID_LENS.get());
        com.kadikular.quantimium.item.VoidLensItem.toggle(lens);
        mount.getLensSlots().setStackInSlot(2, lens);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 8));
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 0, "nothing in the port");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(9, Y, 8))).inflate(3)).isEmpty(),
                    "nothing left lying");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.void
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void voidWithoutPickupDestroysWhatHarvestCuts(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.VOID_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 8));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(9, Y, 8))).inflate(3)).isEmpty(),
                    "its cobblestone is gone");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.mount, laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void mountsInARowAddTheirLensesUp(GameTestHelper helper) {
        // Pickup in the first mount, Harvest in the second: Pickup before any action gathers whatever lies in the
        // beam, what Harvest cuts included.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
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
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
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

    // covers: laser.lens.heal
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aHealingLensHealsTheLivingAndHurtsTheUndead(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HEALING_LENS.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(9, Y, 8));
        pig.setHealth(2.0f);
        net.minecraft.world.entity.monster.zombie.Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(11, Y, 8));
        float whole = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() > 2.0f, "the pig was healed");
            helper.assertTrue(!zombie.isAlive() || zombie.getHealth() < whole, "the zombie was hurt");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.heal
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aHealingLensLeavesTheWholeAlone(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HEALING_LENS.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(9, Y, 8));
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "still whole");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.freeze
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFreezingLensTurnsWaterToIce(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.FREEZING_LENS.get());
        BlockPos water = new BlockPos(9, Y, 8);
        // Fenced in glass, which light goes through, so it can't spread before the light gets there.
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(water.relative(side), Blocks.GLASS);
        helper.setBlock(water, Blocks.WATER);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.ICE, water);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 140)
    public static void anEmitterSwitchedOnBySignalLightsItsLenses(GameTestHelper helper) {
        // Set to run with a signal, it stays dark till given one; given one later, its lenses work.
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        emitter.cycleRedstone();
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.FREEZING_LENS.get());
        BlockPos water = new BlockPos(9, Y, 8);
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(water.relative(side), Blocks.GLASS);
        helper.setBlock(water, Blocks.WATER);
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.WATER, water);
            helper.setBlock(new BlockPos(2, Y, 7), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.ICE, water);
            TestSupport.finishLasers(helper);
        }));
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void harvestWithPickupCutsOnlyWhatAllFits(GameTestHelper helper) {
        // Room for one more seed and plenty of wheat: a ripe crop drops 1 to 4 seeds, so it's cut only when it rolls
        // one, and no seed is ever left on the ground. Replanted ripe whenever it's cut.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) collector.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        collector.getItems().setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 63));
        collector.getItems().setStackInSlot(1, new ItemStack(Items.WHEAT, 1));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        BlockPos crop = new BlockPos(9, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        // Light goes through a crop: it cuts the one just before what it ends on.
        helper.setBlock(crop.east(), Blocks.STONE);
        var ripe = Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
        helper.setBlock(crop, ripe);
        helper.onEachTick(() -> {
            if (helper.getBlockState(crop).isAir()) helper.setBlock(crop, ripe);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(crop)).inflate(3)).isEmpty(),
                    "nothing left on the ground");
        });
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(count(collector, Items.WHEAT) > 1, "it did cut, when the seeds fit");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.pickup, laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 300)
    public static void anAreaHarvestWithPickupGathersAWheatField(GameTestHelper helper) {
        // An Area Prism over a 3x3 field of ripe wheat, Harvest and Pickup, into an empty Laser Port: all of it comes back.
        emitter(helper, new BlockPos(1, Y + 3, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y + 3, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y + 3, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(8, Y + 3, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(8, Y + 3, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        var ripe = Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, Y - 1, z), Blocks.FARMLAND);
                helper.setBlock(new BlockPos(x, Y, z), ripe);
            }
        }
        prism.setArea(helper.absolutePos(new BlockPos(7, Y, 7)), helper.absolutePos(new BlockPos(9, Y, 9)));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.WHEAT), 9, "the whole field came back");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void harvestWithPickupCutsSandstone(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        for (int x = 9; x <= 11; x++) helper.setBlock(new BlockPos(x, Y, 8), Blocks.SANDSTONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.SANDSTONE), 3, "all three cut and brought back");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.freeze
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFreezingLensStopsTheMachineItEndsOn(GameTestHelper helper) {
        // A hopper over a chest, holding cobblestone: frozen, it passes nothing down.
        emitter(helper, new BlockPos(2, Y + 1, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y + 1, 8), Direction.EAST, ModItems.FREEZING_LENS.get());
        BlockPos hopper = new BlockPos(9, Y + 1, 8);
        helper.setBlock(hopper.below(), Blocks.CHEST);
        helper.setBlock(hopper, Blocks.HOPPER);
        var held = helper.getBlockEntity(hopper, net.minecraft.world.level.block.entity.HopperBlockEntity.class);
        // Filled once the light holds it: a hopper moves its first item within a tick of getting it.
        helper.runAfterDelay(20, () -> held.setItem(0, new ItemStack(Items.COBBLESTONE, 16)));
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(held.getItem(0).getCount(), 16, "nothing went down while it was frozen");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.freeze
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFreezingLensHoldsAMobStillAndItThaws(GameTestHelper helper) {
        emitter(helper, new BlockPos(2, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.FREEZING_LENS.get());
        // Penned in glass along the beam (light goes through glass), so it can't wander out before it's frozen.
        for (int x = 8; x <= 10; x++) {
            helper.setBlock(new BlockPos(x, Y, 7), Blocks.GLASS);
            helper.setBlock(new BlockPos(x, Y, 9), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.GLASS);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(9, Y, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.isNoAi(), "the pig was frozen in place");
            long left = com.kadikular.quantimium.laser.Freezing.left(pig);
            helper.assertTrue(left > 0, "for a while");
            TestSupport.finishLasers(helper);
            // Each point of damage thaws it half a second sooner.
            pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 8.0f);
            helper.assertTrue(com.kadikular.quantimium.laser.Freezing.left(pig) <= left - 80, "damage thawed it sooner");
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void pickupFillsTheNextCollectorWhenOneIsFull(GameTestHelper helper) {
        // Two emitters, each through its own Laser Port, joined at a mirror: when one Laser Port is full,
        // what Pickup brings back goes up the other side.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity west = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        emitter(helper, new BlockPos(6, Y, 1), Direction.SOUTH, true);
        LaserPortBlockEntity north = collector(helper, new BlockPos(6, Y, 4), Direction.SOUTH);
        helper.setBlock(new BlockPos(6, Y, 8), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        mount(helper, new BlockPos(8, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        for (LaserPortBlockEntity full : java.util.List.of(west, north)) {
            for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) full.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
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

    private static void relay(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.LASER_RELAY.get().defaultBlockState().setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, facing));
    }

    private static void splitter(GameTestHelper helper, BlockPos pos, Direction input, Direction output) {
        helper.setBlock(pos, ModBlocks.LASER_SPLITTER.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT, Direction.DOWN)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.INPUT, input)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.OUTPUT, output));
    }

    // covers: laser.splitter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aLaserSplitterSendsLightOnAndAround(GameTestHelper helper) {
        // Light from the west: on east, and turned north.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        splitter(helper, new BlockPos(6, Y, 8), Direction.WEST, Direction.NORTH);
        helper.setBlock(new BlockPos(6, Y, 3), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(12, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(6, Y, 3), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "turned north");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(12, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "and on east");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.splitter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void aLaserSplitterJoinsLightOutOfItsTurnSideOnly(GameTestHelper helper) {
        // Input side west, turn side north. Harvest light in from the west, Damage light from behind the turn side (from
        // the south): they leave north together, and nothing goes on east.
        splitter(helper, new BlockPos(6, Y, 8), Direction.WEST, Direction.NORTH);
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        emitter(helper, new BlockPos(6, Y, 14), Direction.NORTH, true);
        mount(helper, new BlockPos(6, Y, 12), Direction.NORTH, ModItems.DAMAGE_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 2), Blocks.BEDROCK);
        helper.runAfterDelay(40, () -> {
            var beams = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel()).beamsOf(helper.absolutePos(new BlockPos(6, Y, 8)));
            helper.assertTrue(beams.stream().anyMatch(beam -> beam.face() == Direction.NORTH
                    && com.kadikular.quantimium.laser.Lens.has(beam.lenses(), com.kadikular.quantimium.laser.Lens.HARVEST)
                    && com.kadikular.quantimium.laser.Lens.has(beam.lenses(), com.kadikular.quantimium.laser.Lens.DAMAGE)), "joined, leaving north: " + beams);
            helper.assertTrue(beams.stream().noneMatch(beam -> beam.face() != Direction.NORTH), "and only north: " + beams);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.splitter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 20)
    public static void sneakClickingASplittersInputSwapsItsSides(GameTestHelper helper) {
        BlockPos at = new BlockPos(6, Y, 8);
        splitter(helper, at, Direction.WEST, Direction.NORTH);
        var player = TestSupport.player(helper, new BlockPos(4, Y, 8), net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        BlockPos abs = helper.absolutePos(at);
        var hit = new net.minecraft.world.phys.BlockHitResult(abs.getCenter().add(-0.5, 0, 0), Direction.WEST, abs, false);
        helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);
        var state = helper.getLevel().getBlockState(abs);
        helper.assertTrue(state.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.OUTPUT) == Direction.WEST
                && state.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.INPUT) == Direction.NORTH, "swapped: " + state);
        helper.succeed();
    }

    // covers: laser.splitter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aLaserSplitterStopsLightFromItsOtherSides(GameTestHelper helper) {
        // Lit only from the east (behind its input side): nothing gets through.
        splitter(helper, new BlockPos(6, Y, 8), Direction.WEST, Direction.NORTH);
        emitter(helper, new BlockPos(12, Y, 8), Direction.WEST, true);
        helper.setBlock(new BlockPos(6, Y, 3), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(1, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(6, Y, 3), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0, "nothing north");
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(1, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0, "nothing west");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.mirror
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void lightAtAMirrorsBackDoesntJoinWhatItTurns(GameTestHelper helper) {
        // Harvest light turned west to north; Damage light hitting the mirror's back from the east is stopped, not joined.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        BlockPos at = new BlockPos(6, Y, 8);
        helper.setBlock(at, ModBlocks.LASER_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.MOUNT, Direction.DOWN)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.INPUT, Direction.WEST)
                .setValue(com.kadikular.quantimium.block.LaserMirrorBlock.OUTPUT, Direction.NORTH));
        emitter(helper, new BlockPos(12, Y, 8), Direction.WEST, true);
        mount(helper, new BlockPos(10, Y, 8), Direction.WEST, ModItems.DAMAGE_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 2), Blocks.BEDROCK);
        helper.runAfterDelay(40, () -> {
            var beams = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel()).beamsOf(helper.absolutePos(at));
            helper.assertTrue(beams.stream().anyMatch(beam -> beam.face() == Direction.NORTH
                    && com.kadikular.quantimium.laser.Lens.has(beam.lenses(), com.kadikular.quantimium.laser.Lens.HARVEST)), "the Harvest light turns north: " + beams);
            helper.assertFalse(beams.stream().anyMatch(beam -> com.kadikular.quantimium.laser.Lens.has(beam.lenses(), com.kadikular.quantimium.laser.Lens.DAMAGE)),
                    "the Damage light at its back isn't joined: " + beams);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }


    // covers: laser.lens.speed, laser.lens.harvest
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void eachSpeedLensCutsSooner(GameTestHelper helper) {
        // Rows of dirt cut with none, one and two Speed lenses: the wait between blocks halves with each.
        int[][] broke = new int[3][4];
        for (int n = 0; n < 3; n++) {
            int z = 2 + n * 5;
            emitter(helper, new BlockPos(1, Y, z), Direction.EAST, true);
            Item[] lenses = new Item[1 + n];
            lenses[0] = ModItems.HARVEST_LENS.get();
            for (int k = 1; k <= n; k++) lenses[k] = ModItems.SPEED_LENS.get();
            mount(helper, new BlockPos(3, Y, z), Direction.EAST, lenses);
            for (int x = 4; x < 8; x++) helper.setBlock(new BlockPos(x, Y, z), Blocks.DIRT);
        }
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            for (int n = 0; n < 3; n++) for (int x = 4; x < 8; x++)
                if (broke[n][x - 4] == 0 && helper.getBlockState(new BlockPos(x, Y, 2 + n * 5)).isAir()) broke[n][x - 4] = tick[0];
        });
        helper.runAfterDelay(100, () -> {
            int[] gap = new int[3];
            for (int n = 0; n < 3; n++) {
                helper.assertTrue(broke[n][3] > 0, n + " Speed lenses cut all four: " + java.util.Arrays.toString(broke[n]));
                gap[n] = (broke[n][3] - broke[n][0]) / 3;
            }
            helper.assertTrue(gap[1] < gap[0] && gap[2] < gap[1], "each Speed lens cuts sooner: " + java.util.Arrays.toString(gap));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.place
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aPlacementBeamPlacesABlockThatFacesItsPlacer(GameTestHelper helper) {
        // A Logistics Controller faces whoever looks at it: placed by a beam, it faces back up the beam.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(ModItems.LOGISTICS_CONTROLLER_ITEM.get()));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PLACE_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.DIRT);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.LOGISTICS_CONTROLLER.get(), new BlockPos(10, Y, 8));
            Direction facing = helper.getBlockState(new BlockPos(10, Y, 8)).getValue(com.kadikular.quantimium.block.LogisticsControllerBlock.FACING);
            helper.assertTrue(facing == Direction.WEST, "faces up the beam: " + facing);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.light.range
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void aMirrorRefocusesToItsSourcesRange(GameTestHelper helper) {
        // A Flash Emitter reaches 8 a run; through a mirror its light reaches 8 again, not the mirror's 32.
        helper.setBlock(new BlockPos(1, Y, 2), ModBlocks.FLASH_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(1, Y, 1), ModBlocks.CREATIVE_ENERGY_CELL.get());
        relay(helper, new BlockPos(1, Y, 6), Direction.EAST);
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
        relay(helper, new BlockPos(6, Y, 6), Direction.EAST);
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
        relay(helper, new BlockPos(6, Y, 6), Direction.EAST);
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
        relay(helper, new BlockPos(5, Y, 6), Direction.EAST);
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
        relay(helper, new BlockPos(6, Y, 8), Direction.NORTH);
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
        // Pickup before Damage gathers whatever lies in the beam: the drops too.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.DAMAGE_LENS.get());
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(9, Y, 8));
        zombie.setHealth(1.0f);
        helper.succeedWhen(() -> {
            helper.assertTrue(!zombie.isAlive(), "killed");
            // Its own drop only: other mods in the test pack (Mystical Agriculture's essence) add drops of their own.
            var lying = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(9, Y, 8))).inflate(3),
                    item -> item.getItem().is(Items.ROTTEN_FLESH));
            helper.assertTrue(count(collector, Items.ROTTEN_FLESH) > 0 || lying.isEmpty(), "its drops came back, if it dropped any: "
                    + lying.stream().map(item -> item.getItem() + " at " + helper.relativeVec(item.position()) + " age " + item.tickCount).toList());
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.port.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aCollectorsFilterSendsTheRestOnUp(GameTestHelper helper) {
        // The nearer Laser Port catches only stone; the cobblestone goes on up to the farther one.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity far = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        LaserPortBlockEntity near = collector(helper, new BlockPos(5, Y, 8), Direction.EAST);
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
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) collector.getItems().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(9, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    private static ItemStack filterLens(com.kadikular.quantimium.laser.LensFilter filter) {
        // The lens of the part the filter sets.
        ItemStack lens = new ItemStack(switch (filter.parts()) {
            case com.kadikular.quantimium.laser.LensFilter.BLOCKS -> ModItems.BLOCK_FILTER_LENS.get();
            case com.kadikular.quantimium.laser.LensFilter.ENTITIES -> ModItems.ENTITY_FILTER_LENS.get();
            default -> ModItems.FILTER_LENS.get();
        });
        lens.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(), filter);
        return lens;
    }

    // covers: laser.isolator
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anUnsetIsolatorLensStopsTheActionsBeforeIt(GameTestHelper helper) {
        // Harvest, then an Isolator Lens: the light past it carries power only, so the stone stays.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.ISOLATOR_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 8), Blocks.STONE);
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, Y, 8));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.isolator
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 20)
    public static void anIsolatorLensPassesWhatItsSetTo(GameTestHelper helper) {
        var settings = com.kadikular.quantimium.laser.IsolatorSettings.POWER_ONLY;
        helper.assertTrue(!settings.passes(com.kadikular.quantimium.laser.Lens.HARVEST) && !settings.passes(com.kadikular.quantimium.laser.Lens.AMPLIFY),
                "unset, it passes neither actions nor modifiers");
        var noHarvest = settings.withActions(true).withModifiers(true).withEntry(0, new ItemStack(ModItems.HARVEST_LENS.get()));
        helper.assertTrue(!noHarvest.passes(com.kadikular.quantimium.laser.Lens.HARVEST), "a listed lens, denied, stops");
        helper.assertTrue(noHarvest.passes(com.kadikular.quantimium.laser.Lens.DAMAGE) && noHarvest.passes(com.kadikular.quantimium.laser.Lens.AMPLIFY),
                "the rest pass");
        var onlyHarvest = noHarvest.withAllow(true);
        helper.assertTrue(onlyHarvest.passes(com.kadikular.quantimium.laser.Lens.HARVEST) && !onlyHarvest.passes(com.kadikular.quantimium.laser.Lens.DAMAGE),
                "allowed, only the listed lens passes");
        helper.succeed();
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 20)
    public static void filterLensesEachSetTheirOwnPart(GameTestHelper helper) {
        // An Item lens and a Block lens on one beam both apply; a later Item lens replaces only the items.
        var items = com.kadikular.quantimium.laser.LensFilter.items(java.util.List.of(new ItemStack(Items.COAL)), true);
        var blocks = com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(new ItemStack(Items.STONE)), true, false);
        var both = blocks.over(items);
        helper.assertTrue(both.lets(new ItemStack(Items.COAL)) && !both.lets(new ItemStack(Items.DIRT)), "the Item lens still filters items");
        helper.assertTrue(both.lets(Blocks.STONE.defaultBlockState(), java.util.List::of)
                && !both.lets(Blocks.DIRT.defaultBlockState(), java.util.List::of), "the Block lens filters blocks");
        var later = com.kadikular.quantimium.laser.LensFilter.items(java.util.List.of(new ItemStack(Items.DIRT)), true).over(both);
        helper.assertTrue(later.lets(new ItemStack(Items.DIRT)) && !later.lets(new ItemStack(Items.COAL)), "a later Item lens replaces the items");
        helper.assertTrue(!later.lets(Blocks.DIRT.defaultBlockState(), java.util.List::of), "and leaves the blocks");
        helper.succeed();
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFilteredDamageBeamHurtsOnlyWhatItLets(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.entities(com.kadikular.quantimium.laser.LensFilter.HOSTILE, false, 0)));
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
    public static void anEntityLensListsMobsBySpawnEgg(GameTestHelper helper) {
        // A cow's egg listed: the cow behind the pig is hurt, the pig isn't.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.entities(
                java.util.List.of(new ItemStack(Items.COW_SPAWN_EGG)), true, 0, false, 0)));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(6, Y, 8));
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, Y, 8));
        float pigFull = pig.getHealth();
        float cowFull = cow.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.getHealth() < cowFull || !cow.isAlive(), "the cow is hurt");
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
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(new ItemStack(Items.COBBLESTONE)), true, false)));
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
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
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

    // covers: laser.relay
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aRelaySendsLightOutOfEveryFaceSet(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        relay(helper, new BlockPos(5, Y, 6), Direction.EAST);
        var relay = helper.getBlockEntity(new BlockPos(5, Y, 6), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
        relay.toggleOutput(Direction.SOUTH);
        helper.setBlock(new BlockPos(10, Y, 6), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(5, Y, 11), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(10, Y, 6), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "east");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(5, Y, 11), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "and south");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.relay.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void aPulseMirrorLetsThroughOneAction(GameTestHelper helper) {
        // Harvest light through a pulse mirror, at a row of stone: one pulse, one block.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        var mirror = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
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

    /** A Laser Port set to send, holding {@code stock}. */
    private static LaserPortBlockEntity sender(GameTestHelper helper, BlockPos pos, Direction facing, ItemStack... stock) {
        LaserPortBlockEntity sender = collector(helper, pos, facing);
        sender.cycleMode();
        for (int i = 0; i < stock.length; i++) sender.getItems().setStackInSlot(i, stock[i]);
        return sender;
    }

    // covers: laser.lens.place, laser.port.send
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aPlacementBeamPlacesWhatASenderSends(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        var sender = sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.COBBLESTONE, 4));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PLACE_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.DIRT);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(10, Y, 8));
            helper.assertTrue(sender.getItems().getStackInSlot(0).getCount() < 4, "taken from the sending port");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void harvestWithPickupBringsBackOnlyWhatItCuts(GameTestHelper helper) {
        // A flower lies in the beam: Harvest with Pickup brings back the stone it cuts, and leaves the flower be.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.STONE);
        var flower = helper.spawnItem(Items.POPPY, new net.minecraft.world.phys.Vec3(8.5, Y + 0.2, 8.5));
        flower.setNoPickUpDelay();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(count(collector, Items.COBBLESTONE) > 0, "the cut stone came back");
            helper.assertValueEqual(count(collector, Items.POPPY), 0, "the flower didn't");
            helper.assertTrue(flower.isAlive(), "it still lies there");
            flower.discard();
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.pickup
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void pickupBeforeHarvestGathersWhatElseLiesInTheBeam(GameTestHelper helper) {
        // Pickup, then Harvest, in one mount: a tree farm's leaves decaying in the beam. The Pickup before Harvest
        // gathers what lies there (the flower) as well as what Harvest cuts.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.STONE);
        var flower = helper.spawnItem(Items.POPPY, new net.minecraft.world.phys.Vec3(8.5, Y + 0.2, 8.5));
        flower.setNoPickUpDelay();
        helper.succeedWhen(() -> {
            helper.assertTrue(count(collector, Items.COBBLESTONE) > 0, "the cut stone came back");
            helper.assertValueEqual(count(collector, Items.POPPY), 1, "and the flower");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.isolator, laser.port.collect
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anIsolatorLensStopsALaserPortBeyondIt(GameTestHelper helper) {
        // A Laser Port, then a mount with an unset Isolator Lens, then Harvest with Pickup: what's cut can't go back
        // past the Isolator to the port (with no port to carry it to, Harvest drops it where it was cut).
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.ISOLATOR_LENS.get());
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(count(collector, Items.COBBLESTONE), 0, "nothing came back past it");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 20)
    public static void filterLensesSetAlikeAreTheSameItem(GameTestHelper helper) {
        // Two Filter Lenses set to the same items, each made apart (as one sent to a client and back is): the same
        // item, so they stack and a network finds one when asked for the other.
        java.util.function.Supplier<ItemStack> make = () -> {
            ItemStack lens = new ItemStack(ModItems.FILTER_LENS.get());
            lens.set(com.kadikular.quantimium.init.ModDataComponents.LENS_FILTER.get(),
                    com.kadikular.quantimium.laser.LensFilter.items(java.util.List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.STONE)), true));
            return lens;
        };
        helper.assertTrue(ItemStack.isSameItemSameComponents(make.get(), make.get()), "they stack");
        helper.assertTrue(net.neoforged.neoforge.transfer.item.ItemResource.of(make.get()).equals(net.neoforged.neoforge.transfer.item.ItemResource.of(make.get())),
                "one is found for the other");
        helper.succeed();
    }

    // covers: laser.lens.place, laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aBlockFilterLensPicksWhatPlacementPlaces(GameTestHelper helper) {
        // Dirt first in the sender, but the Block Filter lists only cobblestone: cobblestone goes down.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        var sender = sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.DIRT, 4), new ItemStack(Items.COBBLESTONE, 4));
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PLACE_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(new ItemStack(Items.COBBLESTONE)), true, false)));
        helper.setBlock(new BlockPos(11, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(10, Y, 8));
            helper.assertValueEqual(sender.getItems().getStackInSlot(0).getCount(), 4, "the dirt stays");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.place, laser.lens.filter
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void aWheatFieldCutRipeAndReplanted(GameTestHelper helper) {
        // Down onto farmland: Harvest with Pickup, filtered to fully grown, and Placement of seeds from the sending Laser Port.
        BlockPos crop = new BlockPos(8, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        helper.setBlock(new BlockPos(8, Y + 7, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(8, Y + 6, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.DOWN));
        LaserPortBlockEntity collector = collector(helper, new BlockPos(8, Y + 5, 8), Direction.DOWN);
        sender(helper, new BlockPos(8, Y + 4, 8), Direction.DOWN, new ItemStack(Items.WHEAT_SEEDS, 8));
        LensMountBlockEntity mount = mount(helper, new BlockPos(8, Y + 3, 8), Direction.DOWN, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        // Fully grown only: ripe crops are cut, and the seeds it sows aren't.
        mount.getLensSlots().setStackInSlot(2, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(), true, true)));
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

    // covers: laser.port.send
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aSenderShipsDownTheBeamToACollector(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.DIAMOND, 5));
        LaserPortBlockEntity collector = collector(helper, new BlockPos(9, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(14, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.DIAMOND), 5, "the diamonds went down the beam");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.relay
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aRelayFaceAddedByHandSendsLight(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        BlockPos at = new BlockPos(5, Y, 6);
        relay(helper, at, Direction.EAST);
        helper.setBlock(new BlockPos(5, Y, 11), ModBlocks.FOLD_CORE.get());
        var player = TestSupport.player(helper, new BlockPos(5, Y, 9), net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        BlockPos abs = helper.absolutePos(at);
        // A sneaking click on the south side of its focus puts an emitter there.
        var hit = new net.minecraft.world.phys.BlockHitResult(abs.getCenter().add(0, 0, 2 / 16.0), Direction.SOUTH, abs, false);
        helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);
        var relay = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(relay.outputs().contains(Direction.SOUTH), "the face was added: " + relay.outputs());
            helper.assertTrue(relay.getBlockState().getValue(com.kadikular.quantimium.block.LaserRelayBlock.OUTPUTS.get(Direction.SOUTH)), "and shows");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(5, Y, 11), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0,
                    "and sends: " + relay.syncedBeams());
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.mount
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void lightOnlyGoesStraightThroughALensMount(GameTestHelper helper) {
        // Shining east: light from the south, at its side, stops on it.
        emitter(helper, new BlockPos(6, Y, 12), Direction.NORTH, true);
        mount(helper, new BlockPos(6, Y, 8), Direction.EAST);
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(10, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0,
                    "nothing in from the side");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.relay
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aRelayEmittersLensIsOnlyItsOwn(GameTestHelper helper) {
        // A Harvest lens in the east emitter: the stone east is cut, the stone south isn't.
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        relay(helper, new BlockPos(5, Y, 6), Direction.EAST);
        var relay = helper.getBlockEntity(new BlockPos(5, Y, 6), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
        relay.addEmitter(Direction.SOUTH);
        relay.setLens(Direction.EAST, new ItemStack(ModItems.HARVEST_LENS.get()));
        helper.setBlock(new BlockPos(9, Y, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(5, Y, 9), Blocks.STONE);
        helper.runAfterDelay(80, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 6));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, Y, 9));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.relay
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 40)
    public static void takingAwayARelayEmitterDropsItsLens(GameTestHelper helper) {
        relay(helper, new BlockPos(5, Y, 6), Direction.EAST);
        var relay = helper.getBlockEntity(new BlockPos(5, Y, 6), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
        relay.addEmitter(Direction.NORTH);
        relay.setLens(Direction.NORTH, new ItemStack(ModItems.RANGE_LENS.get()));
        helper.assertTrue(relay.removeEmitter(Direction.NORTH), "taken away");
        helper.runAfterDelay(2, () -> {
            var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(5, Y, 6))).inflate(2),
                    item -> item.getItem().is(ModItems.RANGE_LENS.get()));
            helper.assertFalse(dropped.isEmpty(), "its lens dropped");
            dropped.forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    // covers: laser.relay.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void eachRelayEmitterListensToRedstoneItsOwnWay(GameTestHelper helper) {
        // East needs a signal, south runs without one: with no signal, only south gets light.
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        relay(helper, new BlockPos(5, Y, 6), Direction.EAST);
        var relay = helper.getBlockEntity(new BlockPos(5, Y, 6), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
        relay.addEmitter(Direction.SOUTH);
        relay.cycleMode(Direction.EAST);
        helper.assertTrue(relay.modeOf(Direction.EAST) == com.kadikular.quantimium.laser.RelayMode.WITH_SIGNAL, "east needs a signal");
        helper.setBlock(new BlockPos(10, Y, 6), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(5, Y, 11), ModBlocks.FOLD_CORE.get());
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(10, Y, 6), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0, "east off");
            helper.assertTrue(helper.getBlockEntity(new BlockPos(5, Y, 11), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0, "south on");
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.relay
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void lightCantComeUpThroughARelaysBase(GameTestHelper helper) {
        // On the floor: light from below hits its base and stops.
        emitter(helper, new BlockPos(5, Y, 6), Direction.UP, true);
        relay(helper, new BlockPos(5, Y + 3, 6), Direction.EAST);
        helper.setBlock(new BlockPos(9, Y + 3, 6), ModBlocks.FOLD_CORE.get());
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(helper.getBlockEntity(new BlockPos(9, Y + 3, 6), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored(), 0,
                    "nothing through the base");
            TestSupport.finishLasers(helper);
            helper.succeed();
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
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(), true, true)));
        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertBlockPresent(Blocks.FARMLAND, crop.below());
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void anAreaPrismWorksNearestFirst(GameTestHelper helper) {
        // An area running past the prism: the stone level with it goes before the one at the far end.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        helper.setBlock(new BlockPos(8, Y, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.STONE);
        prism.setArea(helper.absolutePos(new BlockPos(8, Y, 1)), helper.absolutePos(new BlockPos(8, Y, 15)));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, Y, 8));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, Y, 2));
            TestSupport.finishLasers(helper);
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

    // covers: laser.relay.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anAreaPrismBehindAnUnpoweredMirrorDoesNothing(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.COBBLESTONE, 16));
        helper.setBlock(new BlockPos(5, Y, 8), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        var mirror = helper.getBlockEntity(new BlockPos(5, Y, 8), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
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
    public static void rangeLensesStopAtTheMostLightReaches(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.RANGE_LENS.get(),
                ModItems.RANGE_LENS.get(), ModItems.RANGE_LENS.get());
        // The fourth in a second mount: four slots hold Harvest and three.
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.RANGE_LENS.get());
        helper.setBlock(new BlockPos(16, Y, 8), Blocks.BEDROCK);
        helper.runAfterDelay(25, () -> {
            String text = String.join(" ", com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel())
                    .describe(helper.absolutePos(new BlockPos(5, Y, 8))).stream().map(net.minecraft.network.chat.Component::getString).toList());
            // 8 doubled four times would be 128: it stops at the configured most.
            int most = com.kadikular.quantimium.Config.maxLaserRange();
            helper.assertTrue(text.contains(Math.min(128, most) + " blocks a run"), "8 doubled, up to " + most + ": " + text);
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

    // covers: laser.relay.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void placementBehindAnUnpoweredMirrorStaysOffAfterAJoin(GameTestHelper helper) {
        // A: emitter, mirror needing a signal (none), Laser Port, Placement mount, into a join mirror.
        // B: emitter, Harvest + Pickup mount, into the same join. The join's light goes on into an Area Prism.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        helper.setBlock(new BlockPos(3, Y, 4), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        var off = helper.getBlockEntity(new BlockPos(3, Y, 4), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class);
        off.cycleMode();
        sender(helper, new BlockPos(5, Y, 4), Direction.EAST, new ItemStack(Items.COBBLESTONE, 16));
        mount(helper, new BlockPos(7, Y, 4), Direction.EAST, ModItems.PLACE_LENS.get());
        emitter(helper, new BlockPos(9, Y, 0), Direction.SOUTH, true);
        mount(helper, new BlockPos(9, Y, 2), Direction.SOUTH, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        relay(helper, new BlockPos(9, Y, 4), Direction.SOUTH);
        helper.setBlock(new BlockPos(9, Y, 7), ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(new BlockPos(9, Y, 7), com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.setArea(helper.absolutePos(new BlockPos(11, Y, 9)), helper.absolutePos(new BlockPos(13, Y, 11)));
        helper.runAfterDelay(90, () -> {
            for (int x = 11; x <= 13; x++) for (int z = 9; z <= 11; z++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, Y, z));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.port.send, laser.relay.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void aSenderBehindAnUnpoweredMirrorShipsNothing(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        helper.setBlock(new BlockPos(3, Y, 8), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        helper.getBlockEntity(new BlockPos(3, Y, 8), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class).cycleMode();
        sender(helper, new BlockPos(5, Y, 8), Direction.EAST, new ItemStack(Items.DIAMOND, 5));
        LaserPortBlockEntity collector = collector(helper, new BlockPos(9, Y, 8), Direction.EAST);
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
            helper.assertTrue(empty, "the empty bucket came back to the sending port");
            TestSupport.finishLasers(helper);
            helper.setBlock(new BlockPos(8, Y, 8), Blocks.AIR);
        });
    }

    // covers: laser.relay.redstone
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aSwitchedOffMirrorPassesNothingOn(GameTestHelper helper) {
        // One: emitter, Harvest + Speed mount, a mirror needing a signal (none), then a shared mirror into a prism.
        // Two: an emitter straight into the shared mirror. The prism's light must be plain, and cut nothing.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.SPEED_LENS.get());
        helper.setBlock(new BlockPos(5, Y, 4), ModBlocks.LASER_RELAY.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.LaserRelayBlock.FACING, Direction.EAST));
        helper.getBlockEntity(new BlockPos(5, Y, 4), com.kadikular.quantimium.block.entity.LaserRelayBlockEntity.class).cycleMode();
        emitter(helper, new BlockPos(8, Y, 0), Direction.SOUTH, true);
        relay(helper, new BlockPos(8, Y, 4), Direction.SOUTH);
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
        // The Laser Port takes only sandstone: dirt from a grass block would be left lying, so it isn't cut.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        LaserPortBlockEntity collector = collector(helper, new BlockPos(5, Y, 8), Direction.EAST);
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
        // Set to Ripe only through the mount's screen: the unripe wheat stays, where unset it was cut.
        BlockPos crop = new BlockPos(8, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 2));
        helper.setBlock(new BlockPos(8, Y + 5, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(8, Y + 4, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.DOWN));
        LensMountBlockEntity mount = mount(helper, new BlockPos(8, Y + 2, 8), Direction.DOWN, ModItems.HARVEST_LENS.get());
        mount.getLensSlots().setStackInSlot(1, new ItemStack(ModItems.BLOCK_FILTER_LENS.get()));
        var player = TestSupport.player(helper, new BlockPos(6, Y, 6));
        var menu = new com.kadikular.quantimium.menu.FilterLensMenu(1, player.getInventory(), com.kadikular.quantimium.menu.LensHolder.mount(mount, 1));
        menu.clickMenuButton(player, com.kadikular.quantimium.menu.FilterLensMenu.BUTTON_RIPE);
        helper.runAfterDelay(70, () -> {
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertTrue(com.kadikular.quantimium.item.FilterLensItem.filterOf(mount.getLensSlots().getStackInSlot(1)).ripe(), "set on the lens in the mount");
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
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.entities(0, false, 2)));
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
        // back past it to the collecting Laser Port up the light that filled it.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), ModBlocks.CAVITY_MIRROR.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.CavityMirrorBlock.FACING, Direction.SOUTH));
        relay(helper, new BlockPos(7, Y, 12), Direction.NORTH);
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
        mount.getLensSlots().setStackInSlot(2, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(new ItemStack(Items.COAL)), true, false)));
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

    // covers: laser.lens.phantom
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void phantomPickupPassesOverWhatItCantCarry(GameTestHelper helper) {
        // A Laser Port for dirt only: the sandstone first in the way stays, and the dirt behind it is cut.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        collector.setFilterSlot(0, new ItemStack(Items.DIRT));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.PHANTOM_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.SANDSTONE);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.DIRT);
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.DIRT), 1, "the dirt came back");
            helper.assertBlockPresent(Blocks.SANDSTONE, new BlockPos(7, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.phantom
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void phantomLightPaysForEachBlockItPassesThrough(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.PHANTOM_LENS.get());
        for (int x = 5; x <= 7; x++) helper.setBlock(new BlockPos(x, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.FOLD_CORE.get());
        long each = com.kadikular.quantimium.Config.phantomFePerBlock();
        helper.succeedWhen(() -> {
            long[] upkeep = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel())
                    .costs(helper.absolutePos(new BlockPos(3, Y, 8)), 0, com.kadikular.quantimium.laser.Lens.PHANTOM);
            helper.assertValueEqual(upkeep[0], 3 * each, "three stones' worth a tick");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.phantom, laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void phantomAreaHarvestPaysForTheGroundItCutsThrough(GameTestHelper helper) {
        // Coal under three stones, from a prism above: its cut costs an area's cutting and three stones' passing.
        emitter(helper, new BlockPos(1, Y + 4, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y + 4, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PHANTOM_LENS.get());
        mount.getLensSlots().setStackInSlot(2, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(new ItemStack(Items.COAL)), true, false)));
        BlockPos at = new BlockPos(8, Y + 4, 8);
        helper.setBlock(at, ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.cycleShape();
        prism.setRadius(4);
        for (int y = Y + 1; y <= Y + 3; y++) helper.setBlock(new BlockPos(8, y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.COAL_ORE);
        long each = com.kadikular.quantimium.Config.phantomFePerBlock();
        long cutting = Math.round(3 * com.kadikular.quantimium.laser.LaserTuning.HARVEST_FE_PER_HARDNESS * com.kadikular.quantimium.laser.LaserTuning.AREA_COST);
        helper.succeedWhen(() -> {
            long[] cut = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel())
                    .costs(helper.absolutePos(at), -1, com.kadikular.quantimium.laser.Lens.HARVEST);
            helper.assertValueEqual(cut[0], cutting + 3 * each, "cutting the coal, and three stones' passing");
            TestSupport.finishLasers(helper);
        });
    }

    private static void rift(GameTestHelper helper, BlockPos pos, Direction.Axis axis) {
        helper.setBlock(pos, com.kadikular.quantimium.block.RiftLensBlock.turnedTo(ModBlocks.RIFT_LENS.get().defaultBlockState(), axis,
                helper.getLevel(), helper.absolutePos(pos)));
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 130)
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
            // The emitter gone, the light stops and the tear closes.
            helper.setBlock(new BlockPos(1, Y, 8), Blocks.AIR);
        });
        helper.runAfterDelay(110, () -> {
            helper.assertBlockProperty(new BlockPos(4, Y, 8), com.kadikular.quantimium.block.RiftLensBlock.OPEN, false);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.blocked
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void realLightGoesThroughAnomalite(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        helper.setBlock(new BlockPos(6, Y, 8), ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.FACING, Direction.UP)
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.AGE, 3));
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(new BlockPos(10, Y, 8), FoldCoreBlockEntity.class).getEnergyStorage().getEnergyStored() > 0,
                    "charged past the crystal");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void mirroredHarvestTakesACrystalApart(GameTestHelper helper) {
        // Through a rift, Harvest light takes a grown crystal apart for its shards, back up to the collecting Laser Port.
        LaserEmitterBlockEntity emitter = emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
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
            helper.assertValueEqual(count(collector, ModItems.ANOMALITE_SHARD.get()), com.kadikular.quantimium.laser.MirrorHarvest.SHARDS,
                    "the crystal's shards came back");
            helper.assertBlockNotPresent(ModBlocks.ANOMALITE_CRYSTAL.get(), new BlockPos(10, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.rift
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void mirroredHarvestWithoutPickupDropsInTheMirror(GameTestHelper helper) {
        // No Pickup: the shards fall on the mirror's side, for a phased player only.
        helper.setBlock(new BlockPos(1, Y, 8), ModBlocks.LASER_EMITTER_3.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(0, Y, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        rift(helper, new BlockPos(5, Y, 8), Direction.Axis.X);
        helper.setBlock(new BlockPos(8, Y - 1, 8), ModBlocks.FOLD_CORE.get());
        helper.setBlock(new BlockPos(8, Y, 8), ModBlocks.ANOMALITE_CRYSTAL.get().defaultBlockState()
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.FACING, Direction.UP)
                .setValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.AGE, 3));
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            var shards = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(8, Y, 8))).inflate(2),
                    item -> item.getItem().is(ModItems.ANOMALITE_SHARD.get()));
            helper.assertFalse(shards.isEmpty(), "the shards fell");
            for (var shard : shards) {
                helper.assertTrue(com.kadikular.quantimium.event.MirrorPhaseInteractions.mirrorOnly(shard), "on the mirror's side");
            }
            TestSupport.finishLasers(helper);
            shards.forEach(net.minecraft.world.entity.Entity::discard);
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
        // West into the hall's east wall: emitter, Laser Port, a Pickup mount, then a rift seated on the glass.
        BlockPos row = hallAt.above(2);
        emitter(helper, row.east(7), Direction.WEST, true);
        LaserPortBlockEntity collector = collector(helper, row.east(5), Direction.WEST);
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

    // covers: laser.port.mode
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void aPortSetToBothFeedsSeedsBackDown(GameTestHelper helper) {
        // One block for both: it catches the harvest, and its seeds go back down to be sown.
        BlockPos crop = new BlockPos(8, Y, 8);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        helper.setBlock(new BlockPos(8, Y + 6, 8), ModBlocks.CREATIVE_ENERGY_CELL.get());
        helper.setBlock(new BlockPos(8, Y + 5, 8), ModBlocks.LASER_EMITTER_1.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.DOWN));
        LaserPortBlockEntity both = collector(helper, new BlockPos(8, Y + 4, 8), Direction.DOWN);
        both.cycleMode();
        both.cycleMode();
        helper.assertTrue(both.mode() == com.kadikular.quantimium.block.entity.LaserPortBlockEntity.Mode.BOTH, "set to both");
        helper.assertTrue(helper.getBlockState(new BlockPos(8, Y + 4, 8)).getValue(LaserPortBlock.MODE) == LaserPortBlockEntity.Mode.BOTH, "and shows it");
        LensMountBlockEntity mount = mount(helper, new BlockPos(8, Y + 2, 8), Direction.DOWN, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(),
                ModItems.PLACE_LENS.get());
        mount.getLensSlots().setStackInSlot(3, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(), true, true)));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(both, Items.WHEAT) > 0, "the wheat came back");
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertTrue(helper.getBlockState(crop).getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7, "sown again from what came back");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.insert
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anInsertBeamFillsTheChestItEndsOn(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.IRON_INGOT, 20));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.INSERT_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.CHEST);
        helper.succeedWhen(() -> {
            var chest = helper.getBlockEntity(new BlockPos(9, Y, 8), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            helper.assertValueEqual(chest.countItem(Items.IRON_INGOT), 20, "all of it, in the chest");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.extract
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anExtractBeamEmptiesTheChestBackUp(GameTestHelper helper) {
        // Filtered to gold: the gold comes back to the collecting Laser Port; the iron stays.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.EXTRACT_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.items(java.util.List.of(new ItemStack(Items.GOLD_INGOT)), true)));
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.CHEST);
        var chest = helper.getBlockEntity(new BlockPos(9, Y, 8), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 8));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.GOLD_INGOT), 8, "the gold came back");
            helper.assertValueEqual(chest.countItem(Items.IRON_INGOT), 8, "the iron stayed");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.accelerate
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses_accelerate", timeoutTicks = 170)
    public static void anAccelerationBeamSmeltsFaster(GameTestHelper helper) {
        // A furnace takes 200 ticks to smelt raw iron. At the end of an Acceleration beam (2x) it's done well before.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.ACCELERATION_LENS.get());
        BlockPos at = new BlockPos(8, Y, 8);
        helper.setBlock(at, Blocks.FURNACE);
        var furnace = helper.getBlockEntity(at, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        helper.succeedWhen(() -> {
            helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "smelted already");
            var shown = com.kadikular.quantimium.laser.Acceleration.shown(helper.getLevel(), helper.absolutePos(at));
            helper.assertTrue(shown != null && shown.multiplier() == 2, "it says it's going 2x: " + shown);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.accelerate
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses_accelerate_budget", timeoutTicks = 100)
    public static void anAccelerationBeamKeepsToTheTickBudget(GameTestHelper helper) {
        // With a tick budget smaller than one of the furnace's ticks, it's given none after the first, and says the
        // budget held it back. Chests (no ticker) and Quantimium's own machines are never sped up.
        var budget = com.kadikular.quantimium.Config.ACCELERATION_BLOCK_MICROS;
        int was = budget.get();
        budget.set(1);
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.ACCELERATION_LENS.get());
        BlockPos at = new BlockPos(8, Y, 8);
        helper.setBlock(at, Blocks.FURNACE);
        var furnace = helper.getBlockEntity(at, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        helper.setBlock(new BlockPos(8, Y + 2, 8), Blocks.CHEST);
        helper.assertTrue(!com.kadikular.quantimium.laser.Acceleration.accelerable(helper.getBlockEntity(new BlockPos(8, Y + 2, 8),
                net.minecraft.world.level.block.entity.ChestBlockEntity.class)), "a chest doesn't tick");
        helper.assertTrue(!com.kadikular.quantimium.laser.Acceleration.accelerable(helper.getBlockEntity(new BlockPos(1, Y, 8),
                LaserEmitterBlockEntity.class)), "nor is a Quantimium machine sped up");
        helper.runAfterDelay(60, () -> {
            budget.set(was);
            var shown = com.kadikular.quantimium.laser.Acceleration.shown(helper.getLevel(), helper.absolutePos(at));
            helper.assertTrue(shown != null && shown.budgeted() && shown.multiplier() == 1 && shown.asked() == 2,
                    "held to 1x of 2x by the budget: " + shown);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void anInteractBeamSetToHitMinesWithItsTool(GameTestHelper helper) {
        // An Interact lens set to hit, on blocks, with an iron pickaxe sent down the beam: it mines the stone as a
        // player would, the cobblestone dropping there, and the pickaxe comes back worn.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        var sender = sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.IRON_PICKAXE));
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y, 8), Direction.EAST);
        ItemStack lens = new ItemStack(ModItems.INTERACT_LENS.get());
        com.kadikular.quantimium.item.InteractLensItem.toggle(lens);
        com.kadikular.quantimium.item.InteractLensItem.cycleTargets(lens);
        mount.getLensSlots().setStackInSlot(0, lens);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(9, Y, 8));
            ItemStack pick = sender.getItems().getStackInSlot(0);
            helper.assertTrue(pick.is(Items.IRON_PICKAXE) && pick.getDamageValue() > 0, "the pickaxe is back, worn: " + pick);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anInteractBeamOnLivingThingsOnlyLeavesALeverBe(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST);
        ItemStack lens = new ItemStack(ModItems.INTERACT_LENS.get());
        com.kadikular.quantimium.item.InteractLensItem.cycleTargets(lens);
        com.kadikular.quantimium.item.InteractLensItem.cycleTargets(lens);
        mount.getLensSlots().setStackInSlot(0, lens);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        helper.runAfterDelay(60, () -> {
            helper.assertBlockProperty(new BlockPos(8, Y, 8), net.minecraft.world.level.block.LeverBlock.POWERED, false);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anInteractBeamFlipsALever(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(new BlockPos(8, Y, 8), net.minecraft.world.level.block.LeverBlock.POWERED, true);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 80)
    public static void anInteractBeamFillsABucket(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        sender(helper, new BlockPos(5, Y, 8), Direction.EAST, new ItemStack(Items.BUCKET));
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.WATER);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.WATER_BUCKET), 1, "a bucket of water, back up the beam");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anInteractBeamSetToHitSwingsTheSword(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        var sender = sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.DIAMOND_SWORD));
        ItemStack lens = new ItemStack(ModItems.INTERACT_LENS.get());
        com.kadikular.quantimium.item.InteractLensItem.toggle(lens);
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y, 8), Direction.EAST);
        mount.getLensSlots().setStackInSlot(0, lens);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, Y, 8));
        float full = pig.getHealth();
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.isAlive() || full - pig.getHealth() >= 6.0f, "hit as hard as a diamond sword: " + pig.getHealth());
            boolean back = false;
            for (int slot = 0; slot < 9; slot++) back |= sender.getItems().getStackInSlot(slot).is(Items.DIAMOND_SWORD);
            helper.assertTrue(back, "the sword went back to the sending port");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anInteractBeamFeedsOnlyTheGrownCow(GameTestHelper helper) {
        // A Filter for grown ones: the calf in front is passed over, the cow behind it is fed.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.WHEAT, 4));
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.entities(0, true, 0)));
        var calf = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, Y, 8));
        calf.setAge(-24000);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(10, Y, 8));
        helper.setBlock(new BlockPos(13, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.isInLove(), "the cow was fed");
            helper.assertTrue(calf.getAge() <= -23900, "the calf wasn't: " + calf.getAge());
            TestSupport.finishLasers(helper);
            calf.discard();
            cow.discard();
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anInteractBeamShearsASheep(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.SHEARS));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        var sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(9, Y, 8));
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(sheep.isSheared(), "sheared");
            TestSupport.finishLasers(helper);
            sheep.discard();
        });
    }

    // covers: laser.lens.interact, laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void anAreaInteractFeedsTheHerd(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y + 2, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y + 2, 8), Direction.EAST, new ItemStack(Items.WHEAT, 4));
        mount(helper, new BlockPos(5, Y + 2, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        BlockPos at = new BlockPos(9, Y + 2, 8);
        helper.setBlock(at, ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.cycleShape();
        prism.setRadius(3);
        var one = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, Y, 6));
        var two = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(11, Y, 10));
        helper.succeedWhen(() -> {
            helper.assertTrue(one.isInLove() && two.isInLove(), "both fed");
            TestSupport.finishLasers(helper);
            one.discard();
            two.discard();
        });
    }

    // covers: laser.lens.interact
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anInteractBeamLeavesFedAnimalsAlone(GameTestHelper helper) {
        // One pig waiting to breed again, one already in love: neither takes a carrot, and the carrots stay in the
        // Laser Port rather than going on up to the collecting Laser Port.
        emitter(helper, new BlockPos(0, Y + 2, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(2, Y + 2, 8), Direction.EAST);
        var sender = sender(helper, new BlockPos(3, Y + 2, 8), Direction.EAST, new ItemStack(Items.CARROT, 4));
        LensMountBlockEntity mount = mount(helper, new BlockPos(5, Y + 2, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        mount.getLensSlots().setStackInSlot(1, filterLens(com.kadikular.quantimium.laser.LensFilter.entities(0, true, 0)));
        BlockPos at = new BlockPos(9, Y + 2, 8);
        helper.setBlock(at, ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.cycleShape();
        prism.setRadius(3);
        Pig resting = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, Y, 6));
        resting.setAge(6000);
        Pig loving = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(11, Y, 10));
        loving.setInLoveTime(600);
        helper.runAfterDelay(80, () -> {
            int carrots = 0;
            for (int slot = 0; slot < 9; slot++) carrots += sender.getItems().getStackInSlot(slot).is(Items.CARROT) ? sender.getItems().getStackInSlot(slot).getCount() : 0;
            helper.assertValueEqual(carrots, 4, "no carrot eaten");
            helper.assertValueEqual(count(collector, Items.CARROT), 0, "none went to the collecting port");
            TestSupport.finishLasers(helper);
            resting.discard();
            loving.discard();
            helper.succeed();
        });
    }

    // covers: laser.lens.interact, laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void anAreaInteractTriesEachThingItsSent(GameTestHelper helper) {
        // Carrots first in the sending Laser Port, which only the pig wants: the cow still gets wheat, the sheep shorn.
        emitter(helper, new BlockPos(1, Y + 2, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y + 2, 8), Direction.EAST, new ItemStack(Items.CARROT, 4), new ItemStack(Items.WHEAT, 4),
                new ItemStack(Items.SHEARS));
        mount(helper, new BlockPos(5, Y + 2, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        BlockPos at = new BlockPos(9, Y + 2, 8);
        helper.setBlock(at, ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.cycleShape();
        prism.setRadius(3);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, Y, 6));
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(11, Y, 10));
        var sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(10, Y, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.isInLove(), "the pig had a carrot");
            helper.assertTrue(cow.isInLove(), "the cow had wheat");
            helper.assertTrue(sheep.isSheared(), "the sheep was shorn");
            TestSupport.finishLasers(helper);
            pig.discard();
            cow.discard();
            sheep.discard();
        });
    }

    // covers: laser.lens.interact, laser.port.mode
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void anInteractBeamShearsFromACollectorSetToBoth(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity both = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        both.cycleMode();
        both.cycleMode();
        both.getItems().setStackInSlot(0, new ItemStack(Items.SHEARS));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        var sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(9, Y, 8));
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(sheep.isSheared(), "sheared");
            helper.assertValueEqual(count(both, Items.SHEARS), 1, "the shears are back");
            TestSupport.finishLasers(helper);
            sheep.discard();
        });
    }

    // covers: laser.lens.interact, laser.port.mode, laser.area_prism
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void anAreaInteractFeedsFromACollectorSetToBoth(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y + 2, 8), Direction.EAST, true);
        LaserPortBlockEntity both = collector(helper, new BlockPos(3, Y + 2, 8), Direction.EAST);
        both.cycleMode();
        both.cycleMode();
        both.getItems().setStackInSlot(0, new ItemStack(Items.WHEAT, 8));
        mount(helper, new BlockPos(5, Y + 2, 8), Direction.EAST, ModItems.INTERACT_LENS.get());
        BlockPos at = new BlockPos(9, Y + 2, 8);
        helper.setBlock(at, ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.cycleShape();
        prism.setRadius(3);
        var cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, Y, 6));
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.isInLove(), "fed");
            TestSupport.finishLasers(helper);
            cow.discard();
        });
    }

    // covers: laser.lens.order
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void aFilterChangesOnlyTheLensesBeforeIt(GameTestHelper helper) {
        // Filter (coal only), then Harvest: the filter came first, so Harvest cuts the stone anyway.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LensMountBlockEntity mount = mount(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount.getLensSlots().setStackInSlot(0, filterLens(com.kadikular.quantimium.laser.LensFilter.blocks(java.util.List.of(new ItemStack(Items.COAL)), true, false)));
        mount.getLensSlots().setStackInSlot(1, new ItemStack(ModItems.HARVEST_LENS.get()));
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void smeltingWithoutPickupDropsGlass(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.SMELTING_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.SAND);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(7, Y, 8))).inflate(2));
            helper.assertTrue(items.stream().anyMatch(item -> item.getItem().is(Items.GLASS)), "glass on the ground: " + items);
            helper.assertTrue(items.stream().noneMatch(item -> item.getItem().is(Items.SAND)), "no sand");
            TestSupport.finishLasers(helper);
            items.forEach(ItemEntity::discard);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void smeltingAloneTurnsSandToGlassInPlace(GameTestHelper helper) {
        // Glass lets the light on, so the next sand is smelted too.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.SMELTING_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.SAND);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.SAND);
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(7, Y, 8));
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(8, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void smeltingInsertFillsTheChestWithIngots(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.RAW_IRON, 20));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.INSERT_LENS.get(), ModItems.SMELTING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.CHEST);
        helper.succeedWhen(() -> {
            var chest = helper.getBlockEntity(new BlockPos(9, Y, 8), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            helper.assertValueEqual(chest.countItem(Items.IRON_INGOT), 20, "ingots, in the chest");
            helper.assertValueEqual(chest.countItem(Items.RAW_IRON), 0, "no raw iron");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void smeltingExtractBringsIngotsBackUp(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.EXTRACT_LENS.get(), ModItems.SMELTING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.CHEST);
        var chest = helper.getBlockEntity(new BlockPos(9, Y, 8), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 20));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.IRON_INGOT), 20, "ingots, up the beam");
            helper.assertValueEqual(chest.countItem(Items.RAW_IRON), 0, "the chest emptied");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void smeltingDamageCooksWhatItKills(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get(), ModItems.SMELTING_LENS.get());
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(7, Y, 8));
        // One hit's worth of health: a hit's knockback could carry it out of the beam.
        pig.setHealth(1.0f);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertFalse(pig.isAlive(), "the pig is dead");
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(7, Y, 8))).inflate(3));
            helper.assertTrue(items.stream().anyMatch(item -> item.getItem().is(Items.COOKED_PORKCHOP)), "cooked: " + items);
            helper.assertTrue(items.stream().noneMatch(item -> item.getItem().is(Items.PORKCHOP)), "nothing raw");
            TestSupport.finishLasers(helper);
            items.forEach(ItemEntity::discard);
        });
    }

    // covers: laser.lens.precision
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void precisionCutsWithSilkTouch(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.PRECISION_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.STONE), 1, "stone, not cobblestone");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.harmonic
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void harmonicCutsWithFortune(GameTestHelper helper) {
        // Fortune III on eight diamond ore: plain cutting gives exactly one a block, so any more is Fortune's.
        // Fortune is chance: each ore gives no bonus 2 times in 5, so all eight do about once in 1,500 runs.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.HARMONIC_LENS.get(),
                ModItems.SPEED_LENS.get());
        mount(helper, new BlockPos(7, Y, 8), Direction.EAST, ModItems.HARMONIC_LENS.get(), ModItems.HARMONIC_LENS.get(), ModItems.RANGE_LENS.get());
        for (int x = 8; x <= 15; x++) helper.setBlock(new BlockPos(x, Y, 8), Blocks.DIAMOND_ORE);
        helper.succeedWhen(() -> {
            for (int x = 8; x <= 15; x++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, Y, 8));
            int diamonds = count(collector, Items.DIAMOND);
            helper.assertTrue(diamonds > 8, "more than plain cutting gives: " + diamonds);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.harmonic
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void harmonicDamageHitsWithLooting(GameTestHelper helper) {
        // Hit as by a hand holding Looting: the kill is the laser's, so what drops is looted.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.DAMAGE_LENS.get(), ModItems.HARMONIC_LENS.get());
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, Y, 8));
        helper.setBlock(new BlockPos(12, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getLastHurtByPlayer() instanceof com.kadikular.quantimium.laser.LaserHand,
                    "hit by the laser's hand: " + zombie.getLastHurtByPlayer());
            helper.assertTrue(zombie.getLastHurtByPlayer().getMainHandItem().isEmpty(), "which lets go after");
            TestSupport.finishLasers(helper);
            zombie.discard();
        });
    }

    // covers: laser.lens.unrealise
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void unrealisingTurnsOreIntoMatter(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.UNREALISING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.COAL_ORE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, ModItems.UNREALISED_MATTER.get()), 1, "Unrealised Matter, not coal");
            helper.assertValueEqual(count(collector, Items.COAL), 0, "no coal");
            TestSupport.finishLasers(helper);
        });
    }

    private static ItemStack firstOf(LaserPortBlockEntity collector, Item item) {
        for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) {
            ItemStack stack = collector.getItems().getStackInSlot(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void smeltingHarvestCutsSandIntoGlass(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.SMELTING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.SAND);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.GLASS), 1, "glass, not sand");
            helper.assertValueEqual(count(collector, Items.SAND), 0, "no sand");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void smeltingPickupSmeltsWhatLiesInTheBeam(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.SMELTING_LENS.get());
        BlockPos at = new BlockPos(10, Y, 8);
        ItemEntity iron = new ItemEntity(helper.getLevel(), helper.absoluteVec(at.getCenter()).x, helper.absolutePos(at).getY() + 0.5,
                helper.absoluteVec(at.getCenter()).z, new ItemStack(Items.RAW_IRON, 64));
        iron.setDeltaMovement(0, 0, 0);
        iron.setNoGravity(true);
        helper.getLevel().addFreshEntity(iron);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.IRON_INGOT), 64, "a stack of ingots, from a stack of raw iron");
            helper.assertValueEqual(count(collector, Items.RAW_IRON), 0, "no raw iron");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void smeltingGivesUnrealisedMatterItsSmelting(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(),
                ModItems.UNREALISING_LENS.get(), ModItems.SMELTING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            ItemStack matter = firstOf(collector, ModItems.UNREALISED_MATTER.get());
            helper.assertFalse(matter.isEmpty(), "Unrealised Matter came back");
            helper.assertValueEqual(com.kadikular.quantimium.unrealised.MatterHistory.of(matter).steps(),
                    java.util.List.of(com.kadikular.quantimium.unrealised.MatterSteps.idOf(net.minecraft.world.item.crafting.RecipeType.SMELTING)),
                    "smelted, still unrealised");
            TestSupport.finishLasers(helper);
        });
    }

    private static ItemStack floating(GameTestHelper helper, BlockPos at, ItemStack stack) {
        ItemEntity item = new ItemEntity(helper.getLevel(), helper.absoluteVec(at.getCenter()).x, helper.absolutePos(at).getY() + 0.5,
                helper.absoluteVec(at.getCenter()).z, stack);
        item.setDeltaMovement(0, 0, 0);
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        return stack;
    }

    // covers: laser.lens.crushing
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void crushingHarvestBringsBackGravel(GameTestHelper helper) {
        // Stone cuts to cobblestone, and that crushes to gravel on the way up.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(), ModItems.CRUSHING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.GRAVEL), 1, "gravel, not cobblestone");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void crushingAloneGrindsStoneDownToSand(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.CRUSHING_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.STONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.SAND, new BlockPos(7, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void crushingTurnsRawOreToDust(GameTestHelper helper) {
        // Raw iron has no crushing of its own: the pack's crushers make it dust.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.CRUSHING_LENS.get());
        floating(helper, new BlockPos(10, Y, 8), new ItemStack(Items.RAW_IRON, 4));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.RAW_IRON), 0, "no raw iron came back");
            boolean dust = false;
            for (int slot = 0; slot < LaserPortBlockEntity.SLOTS; slot++) {
                ItemStack stack = collector.getItems().getStackInSlot(slot);
                dust |= net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("dust");
            }
            helper.assertTrue(dust, "dust came back");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing, laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void crushingThenSmeltingMakesIngots(GameTestHelper helper) {
        // Crushing, then Smelting: raw iron to dust to ingots.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.CRUSHING_LENS.get(), ModItems.SMELTING_LENS.get());
        floating(helper, new BlockPos(10, Y, 8), new ItemStack(Items.RAW_IRON, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(collector, Items.IRON_INGOT) >= 4, "ingots came back");
            helper.assertValueEqual(count(collector, Items.RAW_IRON), 0, "no raw iron");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing, laser.lens.order
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void smeltingThenCrushingFollowsTheLenses(GameTestHelper helper) {
        // Crushing, then Smelting: cobblestone crushes to gravel, which won't smelt. Smelting, then Crushing:
        // cobblestone smelts to stone, which crushes back to cobblestone.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        LaserPortBlockEntity crushFirst = collector(helper, new BlockPos(3, Y, 4), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 4), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.CRUSHING_LENS.get(), ModItems.SMELTING_LENS.get());
        floating(helper, new BlockPos(10, Y, 4), new ItemStack(Items.COBBLESTONE, 4));
        emitter(helper, new BlockPos(1, Y, 12), Direction.EAST, true);
        LaserPortBlockEntity smeltFirst = collector(helper, new BlockPos(3, Y, 12), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 12), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.SMELTING_LENS.get(), ModItems.CRUSHING_LENS.get());
        floating(helper, new BlockPos(10, Y, 12), new ItemStack(Items.COBBLESTONE, 4));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(crushFirst, Items.GRAVEL), 4, "crushed first: gravel");
            helper.assertValueEqual(count(smeltFirst, Items.COBBLESTONE), 4, "smelted first: stone, crushed back to cobblestone");
            helper.assertValueEqual(count(smeltFirst, Items.GRAVEL), 0, "and no gravel");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing, laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void eachCrushingLensIsAnotherStep(GameTestHelper helper) {
        // Crushing, Crushing, Smelting: cobblestone to gravel, to sand, to glass.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get(), ModItems.CRUSHING_LENS.get(),
                ModItems.CRUSHING_LENS.get(), ModItems.SMELTING_LENS.get());
        floating(helper, new BlockPos(10, Y, 8), new ItemStack(Items.COBBLESTONE, 4));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.GLASS), 4, "glass, from cobblestone");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing, laser.lens.phantom
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 100)
    public static void phantomCrushingReachesPastWhatItCantCrush(GameTestHelper helper) {
        // Through dirt it can do nothing with, to the cobblestone behind it.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.CRUSHING_LENS.get(), ModItems.PHANTOM_LENS.get());
        helper.setBlock(new BlockPos(6, Y, 8), Blocks.DIRT);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(10, Y, 8), ModBlocks.FOLD_CORE.get());
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.GRAVEL, new BlockPos(8, Y, 8));
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(6, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing, laser.lens.smelting
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void crushingAndSmeltingBeforeHarvestWorkTheBlockFirst(GameTestHelper helper) {
        // Crushing, Smelting, Harvest, Precision: they change no action before them, so they work on the sandstone
        // itself, to sand, to glass; only then is it cut, whole, with Silk Touch, and the glass comes back.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.CRUSHING_LENS.get(), ModItems.SMELTING_LENS.get(),
                ModItems.HARVEST_LENS.get(), ModItems.PRECISION_LENS.get());
        mount(helper, new BlockPos(6, Y, 8), Direction.EAST, ModItems.PICKUP_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.SANDSTONE);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count(collector, Items.GLASS), 1, "the glass came back");
            helper.assertValueEqual(count(collector, Items.SANDSTONE) + count(collector, Items.SAND), 0, "never cut as sandstone or sand");
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void crushingBeforeHarvestWorksTheBlockHarvestWaitsOn(GameTestHelper helper) {
        // Glass just before the bedrock the beam ends on: Harvest picks the glass, so Crushing must work on the
        // glass too, not the bedrock, or Harvest waits for it forever.
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 8), Direction.EAST, ModItems.CRUSHING_LENS.get(), ModItems.HARVEST_LENS.get());
        helper.setBlock(new BlockPos(7, Y, 8), Blocks.GLASS);
        helper.setBlock(new BlockPos(8, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, Y, 8));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.insert, laser.lens.speed
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 60)
    public static void anInsertBeamMovesEightAtATime(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        sender(helper, new BlockPos(3, Y, 8), Direction.EAST, new ItemStack(Items.IRON_INGOT, 64));
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.INSERT_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.CHEST);
        var chest = helper.getBlockEntity(new BlockPos(9, Y, 8), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        helper.runAfterDelay(45, () -> {
            int moved = chest.countItem(Items.IRON_INGOT);
            helper.assertTrue(moved > 0 && moved % 8 == 0 && moved <= 40, "eight a move: " + moved);
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.lens.crushing, laser.light.join
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 160)
    public static void crushingAndSmeltingFromTwoBeamsCrushFirst(GameTestHelper helper) {
        // Smelting from one side, Crushing from the other, joined at a mirror: nothing says which comes first,
        // so it crushes first, and the cobblestone the light ends on becomes gravel.
        emitter(helper, new BlockPos(1, Y, 6), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 6), Direction.EAST, ModItems.SMELTING_LENS.get());
        emitter(helper, new BlockPos(6, Y, 1), Direction.SOUTH, true);
        mount(helper, new BlockPos(6, Y, 3), Direction.SOUTH, ModItems.CRUSHING_LENS.get());
        relay(helper, new BlockPos(6, Y, 6), Direction.EAST);
        helper.setBlock(new BlockPos(10, Y, 6), Blocks.COBBLESTONE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.GRAVEL, new BlockPos(10, Y, 6));
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.lens.crushing
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void crushingGivesUnrealisedMatterItsCrushing(GameTestHelper helper) {
        emitter(helper, new BlockPos(1, Y, 8), Direction.EAST, true);
        LaserPortBlockEntity collector = collector(helper, new BlockPos(3, Y, 8), Direction.EAST);
        mount(helper, new BlockPos(5, Y, 8), Direction.EAST, ModItems.HARVEST_LENS.get(), ModItems.PICKUP_LENS.get(),
                ModItems.UNREALISING_LENS.get(), ModItems.CRUSHING_LENS.get());
        helper.setBlock(new BlockPos(9, Y, 8), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(10, Y, 8), Blocks.BEDROCK);
        helper.succeedWhen(() -> {
            ItemStack matter = firstOf(collector, ModItems.UNREALISED_MATTER.get());
            helper.assertFalse(matter.isEmpty(), "Unrealised Matter came back");
            var steps = com.kadikular.quantimium.unrealised.MatterHistory.of(matter).steps();
            helper.assertTrue(steps.size() == 1 && com.kadikular.quantimium.Config.crushingRecipeTypes().contains(steps.getFirst().toString()),
                    "crushed by one of the pack's crushers, still unrealised: " + steps);
            TestSupport.finishLasers(helper);
        });
    }

    // covers: laser.area_prism.shape
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 120)
    public static void anAreaPrismSphereLeavesItsBoxsCorners(GameTestHelper helper) {
        // Up off the floor, so the sphere holds only the two stones.
        emitter(helper, new BlockPos(1, Y + 4, 8), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y + 4, 8), Direction.EAST, ModItems.HARVEST_LENS.get());
        BlockPos at = new BlockPos(8, Y + 4, 8);
        helper.setBlock(at, ModBlocks.AREA_PRISM.get());
        var prism = helper.getBlockEntity(at, com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.class);
        prism.cycleShape();
        prism.cycleShape();
        prism.setRadius(3);
        helper.assertTrue(prism.shape() == com.kadikular.quantimium.block.entity.AreaPrismBlockEntity.Shape.SPHERE, "a sphere");
        helper.setBlock(at.east(3), Blocks.STONE);
        helper.setBlock(at.east(3).south(3), Blocks.STONE);
        helper.runAfterDelay(100, () -> {
            helper.assertBlockPresent(Blocks.AIR, at.east(3));
            helper.assertBlockPresent(Blocks.STONE, at.east(3).south(3));
            TestSupport.finishLasers(helper);
            helper.succeed();
        });
    }

    // covers: laser.network.incremental
    @GameTest(template = TestSupport.FLOOR_17, batch = "lenses", timeoutTicks = 200)
    public static void cuttingReworksOnlyItsOwnNetwork(GameTestHelper helper) {
        // A Harvest beam cutting a column of stone east along z = 4; a plain beam charging a Fold Core along z = 12.
        emitter(helper, new BlockPos(1, Y, 4), Direction.EAST, true);
        mount(helper, new BlockPos(3, Y, 4), Direction.EAST, ModItems.HARVEST_LENS.get());
        for (int x = 6; x <= 8; x++) helper.setBlock(new BlockPos(x, Y, 4), Blocks.STONE);
        emitter(helper, new BlockPos(1, Y, 12), Direction.EAST, true);
        helper.setBlock(new BlockPos(6, Y, 12), ModBlocks.FOLD_CORE.get());
        var core = helper.getBlockEntity(new BlockPos(6, Y, 12), com.kadikular.quantimium.block.entity.FoldCoreBlockEntity.class);
        var networks = com.kadikular.quantimium.laser.LaserNetworks.of(helper.getLevel());
        long partialBefore = networks.partialRebuilds();
        helper.succeedWhen(() -> {
            for (int x = 6; x <= 8; x++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, Y, 4));
            helper.assertTrue(networks.partialRebuilds() > partialBefore, "each cut reworked only its own network");
            helper.assertTrue(core.getEnergyStorage().getEnergyStored() > 0, "the other network charged on meanwhile");
            TestSupport.finishLasers(helper);
        });
    }
}
