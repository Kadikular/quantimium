package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.ContainmentHallStructure;
import com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.phase.VeiledManager;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** The Anomaly Containment Hall: what arms buy, and keeping an occupant held. See wiki: multiblocks/containment-hall. */
public final class HallTests {

    private static final BlockPos CENTRE = new BlockPos(8, 2, 8);

    private HallTests() {}

    // covers: hall.arms
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_arms")
    public static void armsSetDrawReachAndRating(GameTestHelper helper) {
        int[] fe = {180, 240, 300, 360};
        FluxBand[] rating = {FluxBand.HIGH, FluxBand.CRITICAL, FluxBand.CRITICAL, FluxBand.SINGULARITY};
        double[] reach = {17, 22, 27, 32};
        int[] ticks = {140, 115, 90, 65};
        for (int arms = 1; arms <= ContainmentHallStructure.MAX_ARMS; arms++) {
            ContainmentHallBlockEntity hall = TestSupport.buildHall(helper, CENTRE, arms);
            int i = arms - 1;
            helper.assertValueEqual(hall.armCount(), arms, "arm count");
            helper.assertValueEqual(hall.fePerTick(), fe[i], "FE/t with " + arms + " arms");
            helper.assertValueEqual(hall.shieldCap(), rating[i], "shield rating with " + arms + " arms");
            helper.assertValueEqual(hall.captureRange(), reach[i], "capture reach with " + arms + " arms");
            helper.assertValueEqual(hall.captureTicks(), ticks[i], "capture ticks with " + arms + " arms");
            helper.assertValueEqual(hall.pullPerSecond(), ContainmentHallBlockEntity.ANOMALY_PULL_PER_ARM * arms,
                    "anomaly pull with " + arms + " arms");
            helper.assertValueEqual(hall.capacity(), rating[i].ceiling(), "capacity with " + arms + " arms");
            helper.assertValueEqual(hall.fieldRadius(), arms >= 3 ? 2 : 1, "field reach with " + arms + " arms");
        }
        TestSupport.removeHall(helper, CENTRE);
        helper.succeed();
    }

    // covers: hall.capture_needs_residue
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_hold", timeoutTicks = 60)
    public static void cannotHoldWithoutResidue(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 0);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!hall.canHold(), "an empty hall should not be able to hold anything");
            hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get()));
            helper.assertTrue(hall.canHold(), "a powered hall with residue should be able to hold");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.residue.burn
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_hold", timeoutTicks = 60)
    public static void holdingBurnsResidue(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 2);
        hall.contain();
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(hall.getResidue().getStackInSlot(0).getCount(), 1,
                    "residue left after the first burn");
            int seconds = hall.residueSecondsLeft();
            int per = ContainmentHallBlockEntity.TICKS_PER_RESIDUE / 20;
            helper.assertTrue(seconds > per && seconds <= 2 * per,
                    "time left should be one burning residue plus one stored, got " + seconds + " s");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.residue.burn
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_hold", timeoutTicks = 60)
    public static void emptyHallBurnsNothing(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 2);
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(hall.getResidue().getStackInSlot(0).getCount(), 2, "residue in an empty hall");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.residue.input
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_hold", timeoutTicks = 60)
    public static void armsTakeResidueOnly(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 2, 0);
        for (BlockPos part : new BlockPos[] {CENTRE.north(2), CENTRE.north(3), CENTRE.east(3).above()}) {
            IItemHandler port = LegacyItems.legacy(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(part), null));
            helper.assertTrue(port != null, "arm block " + part + " should expose the hall's residue input");
            ItemStack left = port.insertItem(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), 2), false);
            helper.assertTrue(left.isEmpty(), "residue should go in through " + part);
            ItemStack dirt = new ItemStack(Items.DIRT);
            helper.assertTrue(port.insertItem(0, dirt, false).getCount() == 1, "anything else should be refused");
            helper.assertTrue(port.extractItem(0, 1, false).isEmpty(), "nothing should come back out of an arm");
        }
        helper.assertValueEqual(hall.getResidue().getStackInSlot(0).getCount(), 6, "residue in the hall");
        TestSupport.removeHall(helper, CENTRE);
        helper.succeed();
    }

    // covers: hall.residue.tesseract
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_tesseract", timeoutTicks = 80)
    public static void boundTesseractDrawsFromLinkedInventory(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 0);
        BlockPos chestPos = new BlockPos(2, 2, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), 3));
        ItemStack link = new ItemStack(ModItems.TESSERACT.get());
        link.set(ModDataComponents.BOUND_POS.get(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(chestPos)));
        link.set(ModDataComponents.BOUND_SIDE.get(), Direction.UP);
        link.set(ModDataComponents.BOUND_BLOCK.get(), BuiltInRegistries.BLOCK.getKey(Blocks.CHEST));
        hall.getResidue().setStackInSlot(0, link);
        hall.contain();
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(chest.getItem(0).getCount(), 2, "residue left in the linked chest");
            helper.assertValueEqual(hall.storedResidue(), 2, "residue the hall counts through the link");
            helper.assertTrue(hall.getResidue().getStackInSlot(0).is(ModItems.TESSERACT.get()), "the link stays in the slot");
            helper.assertTrue(!hall.isStarving(), "a hall fed through a link should not starve");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.starve
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_starve",
            timeoutTicks = ContainmentHallBlockEntity.STARVE_GRACE_TICKS + 100)
    public static void starvedHallReleasesAfterGrace(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 0);
        hall.contain();
        helper.runAfterDelay(5, () -> helper.assertTrue(hall.isStarving() && hall.isOccupied(),
                "an occupied hall with no residue should be starving, and still holding"));
        helper.runAfterDelay(ContainmentHallBlockEntity.STARVE_GRACE_TICKS - 40,
                () -> helper.assertTrue(hall.isOccupied(), "it should hold on through the grace"));
        helper.runAfterDelay(ContainmentHallBlockEntity.STARVE_GRACE_TICKS + 20, () -> {
            helper.assertTrue(!hall.isOccupied(), "past the grace, the occupant should have walked out");
            releasedCleanup(helper);
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.starve
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_hold", timeoutTicks = 60)
    public static void feedingEndsStarvation(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 0);
        hall.contain();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(hall.isStarving(), "it should be starving before it is fed");
            hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get()));
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!hall.isStarving() && hall.isOccupied(), "fed, it should stop starving and keep holding");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.power_loss
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_power", timeoutTicks = 300)
    public static void unpoweredHallReleasesAfterTenSeconds(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.buildHall(helper, CENTRE, 1);
        hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), 4));
        hall.contain();
        helper.runAfterDelay(160, () -> helper.assertTrue(hall.isOccupied(), "it should hold on through a short outage"));
        helper.runAfterDelay(220, () -> {
            helper.assertTrue(!hall.isOccupied(), "after ten seconds without power, the occupant should walk out");
            helper.assertValueEqual(hall.getResidue().getStackInSlot(0).getCount(), 4, "residue burnt without power");
            releasedCleanup(helper);
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.occupied_pull
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_occupied_pull", timeoutTicks = 60)
    public static void holdingOneStrengthensTheDrain(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 2, 4);
        double empty = hall.pullPerSecond();
        hall.contain();
        helper.assertValueEqual(hall.pullPerSecond(), empty * ContainmentHallBlockEntity.OCCUPIED_PULL_MULTIPLIER,
                "anomaly pull while holding one");
        TestSupport.removeHall(helper, CENTRE);
        helper.succeed();
    }

    // covers: veiled.held_dispels
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_dispels", timeoutTicks = 100)
    public static void aHeldOneDispelsOthersNearby(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 4);
        // Someone has to be near: the Veiled's world is left alone with nobody in it.
        TestSupport.player(helper, new BlockPos(2, 2, 2));
        ServerLevel level = helper.getLevel();
        Veiled captive = VeiledManager.release(level, helper.absolutePos(new BlockPos(8, 2, 2)));
        VeiledManager.contain(level, captive, hall);
        Veiled free = VeiledManager.release(level, helper.absolutePos(new BlockPos(15, 2, 15)));
        helper.assertTrue(free != null && free.isAlive(), "a second Veiled should appear");
        helper.assertTrue(!VeiledManager.canRelease(level, helper.absolutePos(new BlockPos(15, 2, 15))),
                "no rift should release one this close to a held one");
        // Its peace reaches the base and the land round it, not the whole region.
        BlockPos hallAt = helper.absolutePos(CENTRE);
        helper.assertTrue(VeiledManager.nearHeldOne(level, hallAt.offset(100, 0, 0)), "100 blocks out is within its peace");
        helper.assertTrue(!VeiledManager.nearHeldOne(level, hallAt.offset(150, 0, 0)), "150 blocks out is past it");
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(free.isRemoved(), "the free one should have been dispelled");
            helper.assertTrue(hall.isOccupied(), "the held one stays held");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    // covers: hall.cell
    @GameTest(template = TestSupport.FLOOR_17, batch = "hall_cell", timeoutTicks = 60)
    public static void itsCellSaysWhetherItCanHoldOne(GameTestHelper helper) {
        ContainmentHallBlockEntity hall = TestSupport.readyHall(helper, CENTRE, 1, 0);
        // Formed and powered once it has ticked, but with no residue to feed a guest on.
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(hall.cell(), ContainmentHallBlockEntity.Cell.HUNGRY, "cell with no residue");
            helper.assertTrue(!hall.canHold(), "a hungry hall takes nothing");
            hall.getResidue().setStackInSlot(0, new ItemStack(ModItems.RIFT_RESIDUE.get(), 2));
            helper.assertValueEqual(hall.cell(), ContainmentHallBlockEntity.Cell.WAITING, "cell once fed");
            helper.assertTrue(hall.canHold(), "a fed hall can hold one");
            TestSupport.removeHall(helper, CENTRE);
            helper.succeed();
        });
    }

    /** A released Veiled would haunt later tests; it cannot be killed, so it is removed. */
    private static void releasedCleanup(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(CENTRE)).inflate(8.0);
        helper.getLevel().getEntitiesOfClass(Veiled.class, area).forEach(TestSupport::discard);
    }
}
